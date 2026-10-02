package com.myfit.tracker.domain

import com.myfit.tracker.data.db.GlucoseReading
import com.myfit.tracker.data.db.GlucoseTag
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Blood-sugar maths. Everything is stored and computed in mg/dL; mmol/L is display only.
 * Nothing here diagnoses anything or suggests medication doses.
 */
object Glucose {
    const val MGDL_PER_MMOL = 18.016
    const val VERY_LOW = 54.0
    const val LOW = 70.0
    const val VERY_HIGH = 250.0
    const val DEFAULT_LOW = 70
    const val DEFAULT_HIGH = 180
    const val CGM_MIN_PER_DAY = 96.0
    const val CGM_MIN_DAYS = 14

    fun unitLabel(mmol: Boolean): String = if (mmol) "mmol/L" else "mg/dL"

    /** mg/dL → value in the user's unit (not rounded). */
    fun toUnit(mgdl: Double, mmol: Boolean): Double = if (mmol) mgdl / MGDL_PER_MMOL else mgdl

    /** Value typed in the user's unit → mg/dL. */
    fun toMgdl(value: Double, mmol: Boolean): Double = if (mmol) value * MGDL_PER_MMOL else value

    /** mg/dL integer, mmol/L one decimal. */
    fun format(mgdl: Double, mmol: Boolean): String =
        if (mmol) String.format(Locale.US, "%.1f", mgdl / MGDL_PER_MMOL) else mgdl.roundToInt().toString()

    fun formatWithUnit(mgdl: Double, mmol: Boolean): String = format(mgdl, mmol) + " " + unitLabel(mmol)

    enum class Band(val label: String) { VERY_LOW("Very low"), LOW("Low"), IN_RANGE("In range"), HIGH("High"), VERY_HIGH("Very high") }

    /** Low is always < 70 mg/dL (very low < 54); high is above the user's target; very high ≥ 250. */
    fun band(mgdl: Double, high: Int = DEFAULT_HIGH): Band = when {
        mgdl < VERY_LOW -> Band.VERY_LOW
        mgdl < LOW -> Band.LOW
        mgdl >= VERY_HIGH -> Band.VERY_HIGH
        mgdl > high -> Band.HIGH
        else -> Band.IN_RANGE
    }

    fun inRange(mgdl: Double, low: Int, high: Int): Boolean = mgdl >= low && mgdl <= high

    /** Sensible default tag for a reading taken at this minute of the day. */
    fun defaultTag(minuteOfDay: Int): String = when (minuteOfDay) {
        in 240 until 600 -> GlucoseTag.FASTING      // 04:00–09:59
        in 1260 until 1440, in 0 until 240 -> GlucoseTag.BEDTIME  // 21:00–03:59
        else -> GlucoseTag.RANDOM
    }

    fun tagLabel(tag: String): String = when (tag) {
        GlucoseTag.FASTING -> "Fasting"
        GlucoseTag.BEFORE_MEAL -> "Before meal"
        GlucoseTag.AFTER_MEAL -> "After meal"
        GlucoseTag.BEDTIME -> "Bedtime"
        GlucoseTag.CGM -> "Sensor"
        else -> "Random"
    }

    val manualTags = listOf(GlucoseTag.FASTING, GlucoseTag.BEFORE_MEAL, GlucoseTag.AFTER_MEAL, GlucoseTag.BEDTIME, GlucoseTag.RANDOM)

    /** Glucose Management Indicator (an estimate of A1c from sensor mean). */
    fun gmi(meanMgdl: Double): Double = 3.31 + 0.02392 * meanMgdl

    /** Time-in-range split, each value a percentage 0–100. */
    data class Tir(
        val veryLow: Double, val low: Double, val inRange: Double, val high: Double, val veryHigh: Double,
        val count: Int, val mean: Double,
    )

    fun tir(values: List<Double>, low: Int, high: Int): Tir? {
        if (values.isEmpty()) return null
        val n = values.size.toDouble()
        var vl = 0; var l = 0; var ir = 0; var h = 0; var vh = 0
        val lowEdge = low.toDouble().coerceAtLeast(VERY_LOW)
        val highEdge = high.toDouble().coerceAtMost(VERY_HIGH)
        values.forEach { v ->
            when {
                v < VERY_LOW -> vl++
                v < lowEdge -> l++
                v <= highEdge -> ir++
                v <= VERY_HIGH -> h++
                else -> vh++
            }
        }
        return Tir(vl * 100 / n, l * 100 / n, ir * 100 / n, h * 100 / n, vh * 100 / n, values.size, values.average())
    }

    /** Result of checking whether there's enough sensor data for time-in-range and GMI. */
    data class CgmCheck(
        val isCgm: Boolean,
        val enough: Boolean,
        val daysWithData: Int,
        val perDay: Double,
        val coverage: Double,
        val tir: Tir?,
        val gmi: Double?,
    )

    /**
     * Sensor readings (tag CGM) are treated as a CGM when they average ≥ 96 readings per day with data.
     * Time-in-range and GMI need ≥ 14 days with readings and ≥ 70% of the expected number of readings.
     */
    fun cgmCheck(readings: List<GlucoseReading>, todayKey: String, low: Int, high: Int): CgmCheck {
        val cgm = readings.filter { it.tag == GlucoseTag.CGM && it.deletedAt == null }
        if (cgm.isEmpty()) return CgmCheck(false, false, 0, 0.0, 0.0, null, null)
        val days = cgm.map { it.localDate }.distinct()
        val perDay = cgm.size.toDouble() / days.size
        val isCgm = perDay >= CGM_MIN_PER_DAY
        val expectedPerDay = if (perDay >= 200) 288.0 else 96.0
        val first = days.minOrNull() ?: todayKey
        val span = runCatching {
            java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(first), java.time.LocalDate.parse(todayKey)) + 1
        }.getOrDefault(days.size.toLong()).coerceAtLeast(1L)
        val coverage = cgm.size / (span * expectedPerDay)
        val enough = isCgm && days.size >= CGM_MIN_DAYS && coverage >= 0.7
        val t = if (enough) tir(cgm.map { it.mgdl }, low, high) else null
        return CgmCheck(isCgm, enough, days.size, perDay, coverage, t, t?.let { gmi(it.mean) })
    }

    /** Groups points into at most [maxPoints] time buckets (mean of each), so long sensor traces stay light to draw. */
    fun downsample(points: List<Pair<Long, Double>>, maxPoints: Int): List<Pair<Long, Double>> {
        if (points.size <= maxPoints || points.isEmpty()) return points
        val sorted = points.sortedBy { it.first }
        val start = sorted.first().first; val end = sorted.last().first
        val width = ((end - start) / maxPoints).coerceAtLeast(1L)
        return sorted.groupBy { (it.first - start) / width }.toSortedMap().values.map { b ->
            Pair(b.map { it.first }.average().toLong(), b.map { it.second }.average())
        }
    }
}
