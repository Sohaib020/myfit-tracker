package com.myfit.tracker.ui.vitals

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fingertip photoplethysmography (PPG) maths for the camera heart-rate estimate.
 *
 * Input: brightness of the fingertip (lit by the flash) per camera frame with its timestamp.
 * Each heartbeat pushes a little more blood into the fingertip, which absorbs more light,
 * so brightness dips once per beat. We resample to a steady rate, remove slow drift, smooth
 * away noise (together roughly a 0.7–3.5 Hz band-pass = 42–210 bpm), then count beats two
 * independent ways (peak intervals and autocorrelation). A result is only reported when both
 * agree and the beat-to-beat intervals are consistent.
 */
object Ppg {
    const val FS = 30.0                 // resample rate, Hz

    data class Result(val bpm: Int?, val quality: Double, val reason: String?)

    /** Linear resample of (t seconds, v) onto a uniform FS grid. */
    fun resample(t: DoubleArray, v: DoubleArray): DoubleArray {
        if (t.size < 2) return DoubleArray(0)
        val dur = t.last() - t.first()
        if (dur <= 0.0) return DoubleArray(0)
        val n = (dur * FS).toInt()
        val out = DoubleArray(n)
        var j = 0
        for (i in 0 until n) {
            val tt = t.first() + i / FS
            while (j < t.size - 2 && t[j + 1] < tt) j++
            val t0 = t[j]; val t1 = t[j + 1]
            val f = if (t1 > t0) ((tt - t0) / (t1 - t0)).coerceIn(0.0, 1.0) else 0.0
            out[i] = v[j] + (v[j + 1] - v[j]) * f
        }
        return out
    }

    /** Centered moving average with window w (odd preferred). */
    fun movingAverage(x: DoubleArray, w: Int): DoubleArray {
        if (x.isEmpty() || w <= 1) return x.copyOf()
        val half = w / 2
        val prefix = DoubleArray(x.size + 1)
        for (i in x.indices) prefix[i + 1] = prefix[i] + x[i]
        return DoubleArray(x.size) { i ->
            val a = (i - half).coerceAtLeast(0)
            val b = (i + half).coerceAtMost(x.size - 1)
            (prefix[b + 1] - prefix[a]) / (b - a + 1)
        }
    }

    /**
     * Band-pass: subtract a ~1.4 s moving average (removes drift below ~0.7 Hz), then smooth twice with a
     * ~0.13 s window (attenuates noise above ~3.5 Hz). Inverted so peaks = heartbeats.
     */
    fun filter(uniform: DoubleArray): DoubleArray {
        if (uniform.size < 10) return DoubleArray(0)
        val trend = movingAverage(uniform, (FS * 1.4).toInt() or 1)
        val hp = DoubleArray(uniform.size) { trend[it] - uniform[it] }
        val w = (FS * 0.13).roundToInt().coerceAtLeast(3) or 1
        return movingAverage(movingAverage(hp, w), w)
    }

    private fun std(x: DoubleArray): Double {
        if (x.isEmpty()) return 0.0
        val m = x.average()
        return sqrt(x.sumOf { (it - m) * (it - m) } / x.size)
    }

    /** Indices of beat peaks: local maxima above a small threshold, at least 0.28 s apart (≤ ~210 bpm). */
    fun peaks(f: DoubleArray): List<Int> {
        if (f.size < 5) return emptyList()
        val minGap = (FS * 0.28).toInt()
        val thr = 0.2 * std(f)
        val half = (minGap / 2).coerceAtLeast(2)
        val out = ArrayList<Int>()
        for (i in half until f.size - half) {
            val v = f[i]
            if (v <= thr) continue
            var isMax = true
            for (k in i - half..i + half) if (k != i && f[k] > v) { isMax = false; break }
            if (!isMax) continue
            if (out.isNotEmpty() && i - out.last() < minGap) {
                if (v > f[out.last()]) out[out.lastIndex] = i
            } else out += i
        }
        return out
    }

    /** Best autocorrelation lag between 42 and 200 bpm → (bpm, normalised correlation 0..1). */
    fun autocorrBpm(f: DoubleArray): Pair<Double, Double>? {
        if (f.size < FS * 6) return null
        val m = f.average()
        val x = DoubleArray(f.size) { f[it] - m }
        val e0 = x.sumOf { it * it }
        if (e0 <= 1e-9) return null
        val minLag = (FS * 60.0 / 200.0).toInt()
        val maxLag = (FS * 60.0 / 42.0).toInt().coerceAtMost(x.size / 2)
        var bestLag = -1; var best = 0.0
        val r = DoubleArray(maxLag + 2)
        for (lag in minLag..maxLag + 1) {
            if (lag >= x.size) break
            var s = 0.0
            for (i in 0 until x.size - lag) s += x[i] * x[i + lag]
            r[lag] = s / e0 * x.size / (x.size - lag)
        }
        for (lag in minLag + 1..maxLag) {
            if (r[lag] > r[lag - 1] && r[lag] >= r[lag + 1] && r[lag] > best) { best = r[lag]; bestLag = lag }
        }
        if (bestLag < 0) return null
        return 60.0 * FS / bestLag to best.coerceIn(0.0, 1.0)
    }

    /** Full estimate from the whole recording. */
    fun estimate(t: DoubleArray, v: DoubleArray): Result {
        val u = resample(t, v)
        if (u.size < FS * 15) return Result(null, 0.0, "Not enough signal — keep your finger on the lens for the whole 30 seconds.")
        // skip the first 2 s (exposure settling)
        val f = filter(u.copyOfRange((FS * 2).toInt(), u.size))
        val pk = peaks(f)
        if (pk.size < 8) return Result(null, 0.0, "We couldn't find a steady pulse. Press a little lighter and stay very still.")
        val intervals = pk.zipWithNext { a, b -> (b - a) / FS }
        val sorted = intervals.sorted()
        val median = sorted[sorted.size / 2]
        val consistent = intervals.count { abs(it - median) <= 0.25 * median }.toDouble() / intervals.size
        val peakBpm = 60.0 / median
        val ac = autocorrBpm(f)
        val agree = ac != null && abs(ac.first - peakBpm) <= 0.12 * peakBpm
        val quality = consistent * 0.6 + (ac?.second ?: 0.0) * 0.4
        return when {
            peakBpm < 40 || peakBpm > 200 -> Result(null, quality, "That reading looks off. Try again, keeping your finger still.")
            consistent < 0.65 || !agree || (ac?.second ?: 0.0) < 0.25 ->
                Result(null, quality, "The signal was too noisy to trust. Rest your hand on a table, cover the lens fully and try again.")
            else -> {
                val bpm = if (ac != null) (peakBpm + ac.first) / 2 else peakBpm
                Result(bpm.roundToInt(), quality, null)
            }
        }
    }

    /** Rough live estimate (for display only while measuring) over the last ~10 s. */
    fun liveBpm(t: DoubleArray, v: DoubleArray): Int? {
        val u = resample(t, v)
        if (u.size < FS * 8) return null
        val f = filter(u)
        val ac = autocorrBpm(f) ?: return null
        return if (ac.second >= 0.3) ac.first.roundToInt() else null
    }
}
