package com.myfit.tracker.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.myfit.tracker.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pip's voice. Realistic Gemini speech (bright, youthful "Leda" voice) when online voice is on and a
 * key is set — only the reply text is sent. Otherwise, or if that fails, the phone's own
 * text-to-speech with a slightly raised pitch. [speaking] drives Pip's lip-sync.
 */
class PipVoice(private val context: Context, private val settings: SettingsStore) {
    private val gemini = Gemini(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var track: AudioTrack? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsModel: String? = null
    private var onlineBroken = false

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    fun speak(raw: String) {
        stop()
        val text = clean(raw)
        if (text.isBlank()) return
        job = scope.launch {
            val s = settings.settings.first()
            if (!s.pipVoice) return@launch
            _speaking.value = true
            val done = if (s.pipVoiceOnline && s.geminiKey.isNotBlank() && s.onlineAi && !onlineBroken) {
                runCatching { speakOnline(s.geminiKey, text.take(900)) }.getOrElse { e ->
                    if (e is Gemini.ApiError && e.code in listOf(400, 401, 403, 404)) onlineBroken = true
                    false
                }
            } else false
            if (!done && isActive) speakLocal(text) else _speaking.value = false
        }
    }

    fun stop() {
        job?.cancel(); job = null
        runCatching { track?.pause(); track?.flush(); track?.release() }
        track = null
        runCatching { tts?.stop() }
        _speaking.value = false
    }

    private suspend fun speakOnline(key: String, text: String): Boolean {
        val model = ttsModel ?: gemini.ttsModels(key).firstOrNull()?.also { ttsModel = it } ?: return false
        val (pcm, rate) = gemini.speak(key, model, text, VOICE, "Say in a bright, cheerful, youthful and friendly voice, like an upbeat cartoon buddy")
        if (!scope.isActive || pcm.isEmpty()) return false
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, 8192) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        var off = 0
        val chunk = 4096
        while (off < pcm.size) {
            if (job?.isActive == false || track !== t) return true
            val n = t.write(pcm, off, minOf(chunk, pcm.size - off))
            if (n <= 0) break
            off += n
        }
        // wait for the buffer to drain, then finish
        val frames = pcm.size / 2
        while (track === t && runCatching { t.playbackHeadPosition }.getOrDefault(frames) < frames && job?.isActive != false) {
            kotlinx.coroutines.delay(40)
        }
        if (track === t) { runCatching { t.stop(); t.release() }; track = null }
        _speaking.value = false
        return true
    }

    private fun speakLocal(text: String) {
        val engine = tts
        if (engine != null && ttsReady) { say(engine, text); return }
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            val e = tts ?: return@TextToSpeech
            if (!ttsReady) { _speaking.value = false; return@TextToSpeech }
            runCatching {
                e.language = Locale.US
                // prefer a female-sounding English voice when the engine lists one
                e.voices?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                    ?.sortedByDescending { v ->
                        val n = v.name.lowercase()
                        (if ("female" in n) 4 else 0) + (if (listOf("sfg", "tpf", "iob", "tpc", "smtf").any { it in n }) 3 else 0) + (if (v.locale.country == "US") 1 else 0) + v.quality / 100
                    }?.firstOrNull()?.let { e.voice = it }
            }
            e.setPitch(1.22f)
            e.setSpeechRate(1.03f)
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _speaking.value = true }
                override fun onDone(id: String?) { _speaking.value = false }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { _speaking.value = false }
            })
            say(e, text)
        }
    }

    private fun say(e: TextToSpeech, text: String) {
        _speaking.value = true
        // long replies go in sentence-sized pieces (engines limit utterance length)
        val parts = text.split(Regex("(?<=[.!?])\\s+")).fold(mutableListOf<String>()) { acc, s ->
            if (acc.isNotEmpty() && acc.last().length + s.length < 350) acc[acc.lastIndex] = acc.last() + " " + s else acc += s
            acc
        }
        parts.forEachIndexed { i, p -> e.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "pip$i") }
    }

    companion object {
        /** Gemini prebuilt voice: youthful, bright. */
        const val VOICE = "Leda"

        /** Markdown and emoji removed so Pip reads sentences, not symbols. */
        fun clean(s: String): String = s
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace(Regex("""[*_`#>~|]+"""), "")
            .replace(Regex("""(?m)^\s*[-•]\s+"""), "")
            .replace(Regex("""[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}\x{200D}\x{2B50}\x{2705}]"""), "")
            .replace("≈", "about ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
