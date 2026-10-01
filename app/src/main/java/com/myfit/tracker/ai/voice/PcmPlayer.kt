package com.myfit.tracker.ai.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt

/**
 * Streams 16-bit mono PCM to the speaker. Starts playing on the first chunk (no waiting for the
 * whole clip), fades the first few milliseconds in, and ends cleanly: pads with silence, lets the
 * buffer drain, then releases — no click or "radio" crackle at the end.
 */
class PcmPlayer(private val rate: Int, private val level: MutableStateFlow<Float>) {
    private val track: AudioTrack
    private var written = 0L
    private var fadedIn = false
    @Volatile var aborted = false
        private set

    init {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(min * 2, rate / 5 * 2))   // ~200 ms
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
    }

    /** Blocking write (natural back-pressure). */
    fun write(samples: ShortArray, count: Int = samples.size) {
        if (aborted || count <= 0) return
        if (!fadedIn) {
            val n = minOf(count, rate / 100)          // 10 ms fade-in
            for (i in 0 until n) samples[i] = (samples[i] * (i.toFloat() / n)).toInt().toShort()
            fadedIn = true
        }
        var sum = 0.0
        for (i in 0 until count) { val v = samples[i] / 32768.0; sum += v * v }
        level.value = (sqrt(sum / count) * 4.0).toFloat().coerceIn(0f, 1f)
        var off = 0
        while (off < count && !aborted) {
            val n = track.write(samples, off, count - off)
            if (n <= 0) break
            off += n
        }
        written += count
    }

    fun writeFloats(samples: FloatArray) {
        val out = ShortArray(samples.size)
        for (i in samples.indices) out[i] = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        write(out)
    }

    /** Bytes from a little-endian s16 stream (ElevenLabs). Handles an odd trailing byte via [carry]. */
    private var carry: Int = -1
    fun writeBytes(buf: ByteArray, len: Int) {
        var i = 0
        val out = ShortArray((len + 1) / 2 + 1)
        var n = 0
        if (carry >= 0 && len > 0) { out[n++] = ((buf[0].toInt() shl 8) or carry).toShort(); i = 1; carry = -1 }
        while (i + 1 < len) { out[n++] = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort(); i += 2 }
        if (i < len) carry = buf[i].toInt() and 0xFF
        write(out, n)
    }

    /** Pads with silence, waits until everything has actually been heard, then releases. */
    suspend fun finish() {
        if (aborted) return
        write(ShortArray(rate / 6))                    // ~160 ms of silence
        level.value = 0f
        runCatching { track.stop() }                  // MODE_STREAM: plays what's buffered, then stops
        val total = written
        var waited = 0
        while (!aborted && waited < 3000 && runCatching { track.playbackHeadPosition.toLong() }.getOrDefault(total) < total) {
            delay(30); waited += 30
        }
        runCatching { track.release() }
    }

    fun abort() {
        if (aborted) return
        aborted = true
        level.value = 0f
        runCatching { track.pause(); track.flush(); track.release() }
    }
}
