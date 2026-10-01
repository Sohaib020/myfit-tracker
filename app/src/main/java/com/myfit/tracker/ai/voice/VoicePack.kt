package com.myfit.tracker.ai.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device neural voice pack: Supertonic 3 (int8) via sherpa-onnx. Downloaded once (~130 MB),
 * unpacked while downloading, then everything runs offline. Generates speech several times faster
 * than real time, so Pip can start talking almost immediately.
 */
class VoicePack(private val context: Context) {
    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val dir = File(context.filesDir, "voice/$MODEL")
    private val marker = File(dir, ".complete")
    private val _state = MutableStateFlow<State>(if (marker.exists()) State.Ready else State.Missing)
    val state: StateFlow<State> = _state
    private var job: Job? = null
    private var tts: OfflineTts? = null
    private val lock = Mutex()

    val ready get() = marker.exists()

    fun install(scope: CoroutineScope) {
        if (job?.isActive == true || ready) return
        job = scope.launch(Dispatchers.IO) {
            _state.value = State.Downloading(0f)
            runCatching {
                dir.deleteRecursively(); dir.mkdirs()
                val c = (URL(URL_PACK).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
                }
                if (c.responseCode !in 200..299) error("Download failed (HTTP ${c.responseCode})")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: 129_000_000L
                var read = 0L
                var lastPost = 0L
                val counting = object : FilterInputStream(c.inputStream) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = super.read(b, off, len)
                        if (n > 0) {
                            read += n
                            if (read - lastPost > 512_000) { lastPost = read; _state.value = State.Downloading((read.toFloat() / total).coerceIn(0f, 0.99f)) }
                        }
                        return n
                    }
                }
                TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(counting, 1 shl 16))).use { tar ->
                    while (true) {
                        ensureActive()
                        val e = tar.nextEntry ?: break
                        val name = e.name.substringAfter('/', "")
                        if (name.isEmpty() || e.isDirectory) continue
                        val out = File(dir, name)
                        if (!out.canonicalPath.startsWith(dir.canonicalPath)) continue   // no path tricks
                        out.parentFile?.mkdirs()
                        out.outputStream().use { o -> copy(tar, o) }
                    }
                }
                listOf(DP, TE, VE, VOC, "tts.json", "unicode_indexer.bin", "voice.bin").forEach {
                    if (!File(dir, it).exists()) error("Voice pack is incomplete ($it missing)")
                }
                marker.writeText("ok")
                _state.value = State.Ready
            }.onFailure { e ->
                dir.deleteRecursively()
                _state.value = State.Failed(e.message ?: "Download failed — check your connection and try again.")
            }
        }
    }

    private fun copy(i: InputStream, o: java.io.OutputStream) {
        val buf = ByteArray(1 shl 16)
        while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n) }
    }

    fun cancel() { job?.cancel(); if (!ready) _state.value = State.Missing }

    fun delete() {
        release(); cancel(); dir.deleteRecursively(); _state.value = State.Missing
    }

    /** Loads the engine (≈1 s, once per chat session). */
    suspend fun engine(): OfflineTts? = lock.withLock {
        if (!ready) return null
        tts ?: withContext(Dispatchers.IO) {
            runCatching {
                val p = dir.absolutePath
                OfflineTts(
                    null,
                    OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            supertonic = OfflineTtsSupertonicModelConfig(
                                durationPredictor = "$p/$DP", textEncoder = "$p/$TE", vectorEstimator = "$p/$VE",
                                vocoder = "$p/$VOC", ttsJson = "$p/tts.json", unicodeIndexer = "$p/unicode_indexer.bin",
                                voiceStyle = "$p/voice.bin",
                            ),
                            numThreads = 4,
                            provider = "cpu",
                        ),
                    ),
                )
            }.getOrNull()
        }.also { tts = it }
    }

    /** Synthesises one sentence. [lang] is "en" or "hi". */
    fun synth(engine: OfflineTts, text: String, lang: String): Pair<FloatArray, Int> {
        val cfg = GenerationConfig(sid = VOICE_SID, speed = 1.04f, numSteps = 5, extra = mapOf("lang" to lang))
        val a = engine.generateWithConfig(text, cfg)
        return a.samples to a.sampleRate
    }

    fun release() {
        runCatching { tts?.release() }
        tts = null
    }

    companion object {
        const val MODEL = "sherpa-onnx-supertonic-3-tts-int8-2026-05-11"
        const val URL_PACK = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$MODEL.tar.bz2"
        const val DP = "duration_predictor.int8.onnx"
        const val TE = "text_encoder.int8.onnx"
        const val VE = "vector_estimator.int8.onnx"
        const val VOC = "vocoder.int8.onnx"
        /** Voice style F2 — "bright, cheerful, playful and youthful". */
        const val VOICE_SID = 1
        const val SIZE_MB = 130
    }
}
