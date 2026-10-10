package com.myfit.tracker.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.myfit.tracker.ai.voice.ElevenLabs
import com.myfit.tracker.ai.voice.PcmPlayer
import com.myfit.tracker.ai.voice.VoicePack
import com.myfit.tracker.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Pip's voice, hybrid:
 *  1. ElevenLabs (if you add a key and have credits) — most realistic, streams in ~0.3 s, speaks Urdu.
 *  1b. Azure neural voices (free 500k characters/month) — natural English and real Urdu.
 *  2. On-device neural voice (Supertonic, after the one-time voice-pack download) — instant, free,
 *     offline; Urdu replies are spoken through its Hindustani voice.
 *  3. The phone's own text-to-speech as a last resort.
 * Only the reply text ever leaves the phone, and only for option 1.
 */
class PipVoice(private val context: Context, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val pack = VoicePack(context)
    private val eleven = ElevenLabs()
    private val azure = com.myfit.tracker.ai.voice.AzureTts()
    @Volatile private var azureOff = false
    private var job: Job? = null
    private var player: PcmPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    /** ElevenLabs disabled for this app session after a permanent/quota error. */
    @Volatile private var elevenOff = false

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    /** 0..1 loudness of what's playing — drives Pip's mouth. */
    val level = MutableStateFlow(0f)
    /** Which engine spoke last ("ElevenLabs", "On-device", "Phone voice") — shown in settings. */
    val lastEngine = MutableStateFlow<String?>(null)
    val lastError = MutableStateFlow<String?>(null)

    /** Warm up the on-device engine while the chat is open, so the first reply is instant. */
    fun prepare() { scope.launch { if (pack.ready) pack.engine() } }

    /** Free the on-device engine's memory when the chat closes. */
    fun release() { stop(); scope.launch { pack.release() } }

    fun installPack() = pack.install(scope)

    fun resetEleven() { elevenOff = false; azureOff = false; lastError.value = null }

    suspend fun checkEleven(key: String) = eleven.check(key)

    /**
     * @param text what's shown (English or Roman Urdu)
     * @param ur same reply in Urdu script (speech only), [hi] in Devanagari (speech only)
     */
    private val focus by lazy { com.myfit.tracker.ai.voice.Focus(context, transient = true) { stop() } }

    /**
     * A human voice other than Pip's (the trainer / nutritionist). R17: they use the same natural on-device voice pack
     * as Pip, each with its own speaker — trainer: energetic M1 / confident F4; nutritionist: warm M5 / kind F5 —
     * falling back to the phone's voice for Urdu or before the pack is downloaded.
     */
    data class Persona(val male: Boolean, val trainer: Boolean = true) {
        /** Speaker index in the Supertonic voice pack (voice.bin is F1–F5 = 0–4, M1–M5 = 5–9). */
        val sid: Int get() = when { trainer && male -> 5; trainer -> 3; male -> 9; else -> 4 }
        val speed: Float get() = if (trainer) 1.08f else 1.0f
        fun azure(urdu: Boolean) = when {
            urdu && male -> "ur-PK-AsadNeural"
            urdu -> "ur-PK-UzmaNeural"
            male -> "en-US-AndrewNeural"
            else -> "en-US-AvaNeural"
        }
    }

    fun speak(text: String, ur: String? = null, hi: String? = null, force: Boolean = false, persona: Persona? = null) {
        stop()
        val en = clean(text)
        if (en.isBlank()) return
        job = scope.launch {
            val s = settings.settings.first()
            if (!s.pipVoice && !force) return@launch
            if (!focus.request()) return@launch          // a call is in progress: stay quiet
            _speaking.value = true
            if (persona != null) {
                val urdu = ur != null
                val t = if (urdu) clean(ur!!) else en
                var done = false
                // offline pack only (owner's choice): natural voice, no network; Urdu has no pack voice → phone voice
                if (!urdu && pack.ready) done = runCatching { viaOnDevice(en, "en", persona.sid, persona.speed) }.getOrDefault(false)
                if (!done && isActive) { viaPhone(t, urdu, persona); return@launch }
            } else run {
                val urdu = ur != null
                var done = false
                if (s.voiceEngine == 0 && s.elevenKeyEff.isNotBlank() && !elevenOff) {
                    done = runCatching { viaEleven(s.elevenKeyEff, if (urdu) clean(ur!!) else en, urdu) }.getOrElse { e ->
                        if (e is ElevenLabs.Failure) {
                            lastError.value = if (e.quota) "ElevenLabs credits used up — using the on-device voice." else "ElevenLabs: ${e.message}"
                            if (e.permanent || e.quota) elevenOff = true
                        }
                        false
                    }
                }
                if (!done && isActive && s.voiceEngine == 0 && s.azureKeyEff.isNotBlank() && s.azureRegionEff.isNotBlank() && !azureOff) {
                    done = runCatching { viaAzure(s.azureKeyEff, s.azureRegionEff, if (urdu) clean(ur!!) else en, urdu) }.getOrElse { e ->
                        if (e is java.net.UnknownHostException) azureOff = true
                        if (e is com.myfit.tracker.ai.voice.AzureTts.Failure) {
                            this@PipVoice.lastError.value = "Azure voice: ${e.message}"
                            if (e.permanent || e.quota) azureOff = true
                        }
                        false
                    }
                }
                if (!done && isActive && s.voiceEngine != 2 && pack.ready) {
                    val (txt, lang) = when {
                        hi != null -> clean(hi) to "hi"
                        urdu -> "" to "ur"          // no Devanagari copy → phone voice handles Urdu
                        else -> en to "en"
                    }
                    if (txt.isNotBlank()) done = runCatching { viaOnDevice(txt, lang) }.getOrDefault(false)
                }
                if (!done && isActive) {
                    viaPhone(if (urdu) clean(ur!!) else en, urdu)
                    return@launch        // phone engine reports its own speaking state
                }
            }
            _speaking.value = false
            level.value = 0f
            focus.abandon()
        }
    }

    fun stop() {
        runCatching { focus.abandon() }
        job?.cancel(); job = null
        player?.abort(); player = null
        runCatching { tts?.stop() }
        _speaking.value = false
        level.value = 0f
    }

    // ---------------------------------------------------------------- engines

    private suspend fun viaEleven(key: String, text: String, urdu: Boolean): Boolean {
        val p = PcmPlayer(ElevenLabs.RATE, level).also { player = it }
        var got = 0
        try {
            eleven.stream(key, text.take(1200), urdu) { buf, n -> got += n; p.writeBytes(buf, n) }
        } catch (e: Throwable) {
            if (got == 0) { p.abort(); throw e }   // nothing played yet → let the next engine speak
        }
        if (got == 0) { p.abort(); return false }
        p.finish()
        lastEngine.value = "ElevenLabs"
        return true
    }

    private suspend fun viaAzure(key: String, region: String, text: String, urdu: Boolean, voiceName: String? = null): Boolean {
        val p = PcmPlayer(com.myfit.tracker.ai.voice.AzureTts.RATE, level).also { player = it }
        var got = 0
        try {
            azure.stream(key, region, text.take(1500), urdu, voiceName) { buf, n -> got += n; p.writeBytes(buf, n) }
        } catch (e: Throwable) {
            if (got == 0) { p.abort(); throw e }
        }
        if (got == 0) { p.abort(); return false }
        p.finish()
        lastEngine.value = "Azure"
        return true
    }

    /** Sentence-by-sentence: the next sentence is synthesised while the current one plays. */
    private suspend fun viaOnDevice(text: String, lang: String, sid: Int = com.myfit.tracker.ai.voice.VoicePack.VOICE_SID, speed: Float = 1.04f): Boolean {
        val engine = pack.engine() ?: return false
        val parts = sentences(text)
        if (parts.isEmpty()) return false
        val ch = Channel<FloatArray>(capacity = 2)
        var rate = 44_100
        val first = pack.synth(engine, parts[0], lang, sid, speed).also { rate = it.second }.first
        val p = PcmPlayer(rate, level).also { player = it }
        val producer = scope.launch {
            try {
                for (i in 1 until parts.size) { if (!isActive) break; ch.send(pack.synth(engine, parts[i], lang, sid, speed).first) }
            } finally { ch.close() }
        }
        try {
            p.writeFloats(first)
            for (chunk in ch) { if (p.aborted) break; p.writeFloats(chunk) }
            p.finish()
        } finally {
            if (producer.isActive) producer.cancelAndJoin()
        }
        lastEngine.value = "On-device"
        return true
    }

    private fun viaPhone(text: String, urdu: Boolean, persona: Persona? = null) {
        val engine = tts
        if (engine != null && ttsReady) { say(engine, text, urdu, persona); return }
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            val e = tts ?: return@TextToSpeech
            if (!ttsReady) { _speaking.value = false; return@TextToSpeech }
            e.setPitch(1.2f); e.setSpeechRate(1.02f)
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { _speaking.value = true; level.value = 0.6f }
                override fun onDone(id: String?) { if (id?.startsWith("last") == true) { _speaking.value = false; level.value = 0f } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { _speaking.value = false; level.value = 0f }
            })
            say(e, text, urdu, persona)
        }
    }

    private fun say(e: TextToSpeech, text: String, urdu: Boolean, persona: Persona? = null) {
        runCatching {
            if (persona != null) {
                e.setPitch(if (persona.male) 0.95f else 1.05f); e.setSpeechRate(1.05f)
                val loc = if (urdu) Locale("ur", "PK") else Locale.US
                if (e.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) e.language = loc else e.language = Locale.US
                val want = if (persona.male) "male" else "female"
                e.voices?.filter { it.locale.language == e.language.language && !it.isNetworkConnectionRequired }
                    ?.maxByOrNull { v -> val n = v.name.lowercase(); (if (want in n && !(want == "male" && "female" in n)) 5 else 0) + v.quality / 100 }
                    ?.let { e.voice = it }
                _speaking.value = true
                val parts = sentences(text)
                parts.forEachIndexed { i, p -> e.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == parts.lastIndex) "last$i" else "pip$i") }
                lastEngine.value = "Phone voice"
                return
            }
            e.setPitch(1.2f); e.setSpeechRate(1.02f)
            val loc = if (urdu) Locale("ur", "PK") else Locale.US
            if (e.isLanguageAvailable(loc) >= TextToSpeech.LANG_AVAILABLE) e.language = loc else e.language = Locale.US
            if (!urdu) e.voices?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
                ?.maxByOrNull { v -> val n = v.name.lowercase(); (if ("female" in n) 4 else 0) + (if (listOf("sfg", "tpf", "iob", "tpc").any { it in n }) 3 else 0) + v.quality / 100 }
                ?.let { e.voice = it }
        }
        _speaking.value = true
        val parts = sentences(text)
        parts.forEachIndexed { i, p ->
            e.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == parts.lastIndex) "last$i" else "pip$i")
        }
        lastEngine.value = "Phone voice"
    }

    companion object {
        /** Markdown and emoji removed so Pip reads sentences, not symbols. */
        fun clean(s: String): String = s
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace(Regex("""[*_`#>~|]+"""), "")
            .replace(Regex("""(?m)^\s*[-•]\s+"""), "")
            .replace(Regex("""[\x{1F000}-\x{1FAFF}\x{2600}-\x{27BF}\x{FE0F}\x{200D}\x{2B50}\x{2705}]"""), "")
            .replace("≈", "about ")
            .replace(Regex("""\s*\n+\s*"""), ". ")
            .replace(Regex("""\.\s*\."""), ".")
            .replace(Regex("""\s+"""), " ")
            .trim()

        /** Splits into sentence-sized pieces (merging very short ones) for fast first audio. */
        fun sentences(text: String): List<String> {
            val raw = text.split(Regex("""(?<=[.!?।۔؟])\s+""")).map { it.trim() }.filter { it.isNotEmpty() }
            val out = mutableListOf<String>()
            for (s in raw) {
                if (out.isNotEmpty() && (out.last().length < 28 || s.length < 12) && out.last().length + s.length < 220) out[out.lastIndex] = out.last() + " " + s
                else out += s
            }
            // keep the very first piece short so speech starts quickly
            if (out.isNotEmpty() && out[0].length > 160) {
                val f = out[0]
                val cut = f.lastIndexOf(',', 120).takeIf { it > 30 } ?: f.lastIndexOf(' ', 120).takeIf { it > 30 }
                if (cut != null) { out[0] = f.substring(0, cut + 1).trim(); out.add(1, f.substring(cut + 1).trim()) }
            }
            return out
        }
    }
}
