package com.myfit.tracker.ui.mind

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class Ambient(val label: String) { RAIN("Rain"), OCEAN("Ocean"), HUM("Soft hum"), SILENCE("Silence") }

/**
 * Procedurally generated ambient sound (no audio assets). Synthesis runs on its own background
 * thread and writes to a streaming [AudioTrack]. Call [stop] to fade out and release.
 */
class AmbientPlayer(context: android.content.Context? = null) {
    private val focus = context?.let { com.myfit.tracker.ai.voice.Focus(it, transient = false) { stop() } }
    @Volatile private var running = false
    @Volatile private var gen = 0
    @Volatile var volume: Float = 0.6f
    private var current: Ambient = Ambient.SILENCE

    /** Starts (or cross-fades to) [k]. Silence stops playback. Safe to call from the main thread. */
    fun play(k: Ambient) {
        if (k == Ambient.SILENCE) { stop(); current = k; return }
        if (running && k == current) return
        if (focus != null && !focus.request()) return     // e.g. during a phone call
        current = k
        gen += 1
        val g = gen
        running = true
        val t = Thread({ loop(k, g) }, "mind-ambient")
        t.isDaemon = true
        t.start()
    }

    fun stop() {
        running = false
        gen += 1
        focus?.abandon()
    }

    private fun loop(k: Ambient, g: Int) {
        val rate = 22050
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 4096) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        val n = 1024
        val buf = ShortArray(n)
        val rnd = Random(System.nanoTime())
        var gain = 0f                      // smoothed master gain (fade in/out, volume changes)
        var sampleIdx = 0L
        // rain state
        var lp1 = 0f; var lp2 = 0f
        var dropAmp = 0f; var dropPhase = 0.0; var dropFreq = 2000.0
        // ocean state
        var brown = 0f; var oceanLp = 0f
        // hum state
        var p1 = 0.0; var p2 = 0.0; var p3 = 0.0; var p4 = 0.0
        val twoPi = 2 * PI
        try {
            track.play()
            while (true) {
                val alive = running && g == gen
                val target = if (alive) volume.coerceIn(0f, 1f) else 0f
                for (i in 0 until n) {
                    gain += (target - gain) * 0.00008f        // ~0.5 s glide at 22 kHz
                    val t = sampleIdx.toDouble() / rate
                    var s = 0f
                    when (k) {
                        Ambient.RAIN -> {
                            val w = rnd.nextFloat() * 2f - 1f
                            lp1 += (w - lp1) * 0.35f            // soft hiss
                            lp2 += (lp1 - lp2) * 0.08f          // deeper body
                            val swell = 0.85f + 0.15f * sin(twoPi * 0.05 * t).toFloat()
                            s = (lp1 * 0.22f + lp2 * 0.55f) * swell
                            // random droplets: tiny decaying pings
                            if (dropAmp < 0.01f && rnd.nextFloat() < 0.0009f) {
                                dropAmp = 0.10f + rnd.nextFloat() * 0.18f
                                dropFreq = 1400.0 + rnd.nextDouble() * 2600.0
                                dropPhase = 0.0
                            }
                            if (dropAmp > 0.001f) {
                                dropPhase += twoPi * dropFreq / rate
                                dropFreq *= 0.99985                // slight downward chirp
                                s += (sin(dropPhase).toFloat() * dropAmp)
                                dropAmp *= 0.9975f
                            }
                        }
                        Ambient.OCEAN -> {
                            val w = rnd.nextFloat() * 2f - 1f
                            brown = (brown + w * 0.02f) * 0.998f
                            oceanLp += (brown - oceanLp) * 0.15f
                            // slow waves ~ every 9 s, with a sharper crest
                            val wave = ((sin(twoPi * t / 9.0 - PI / 2) + 1.0) / 2.0).toFloat()
                            val env = 0.18f + 0.82f * wave * wave
                            val hiss = (rnd.nextFloat() * 2f - 1f) * 0.05f * wave * wave * wave
                            s = (oceanLp * 1.5f) * env + hiss
                        }
                        Ambient.HUM -> {
                            p1 += twoPi * 110.0 / rate
                            p2 += twoPi * 164.81 / rate
                            p3 += twoPi * 220.6 / rate
                            p4 += twoPi * 329.2 / rate
                            val trem = 0.8f + 0.2f * sin(twoPi * 0.11 * t).toFloat()
                            val shimmer = 0.5f + 0.5f * sin(twoPi * 0.037 * t).toFloat()
                            s = (sin(p1).toFloat() * 0.30f + sin(p2).toFloat() * 0.18f +
                                sin(p3).toFloat() * 0.12f + sin(p4).toFloat() * 0.05f * shimmer) * trem * 0.6f
                            if (p1 > 1e6) { p1 %= twoPi; p2 %= twoPi; p3 %= twoPi; p4 %= twoPi }
                        }
                        Ambient.SILENCE -> s = 0f
                    }
                    val v = (s * gain).coerceIn(-1f, 1f)
                    buf[i] = (v * 30000f).toInt().toShort()
                    sampleIdx++
                }
                if (track.write(buf, 0, n) < 0) break
                if (!alive && gain < 0.0015f) break
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { track.pause(); track.flush() }
            runCatching { track.release() }
        }
    }
}

/** Player tied to the composition: fades out and releases when the caller leaves. */
@Composable
fun rememberAmbientPlayer(): AmbientPlayer {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = remember { AmbientPlayer(ctx) }
    DisposableEffect(p) { onDispose { p.stop() } }
    return p
}
