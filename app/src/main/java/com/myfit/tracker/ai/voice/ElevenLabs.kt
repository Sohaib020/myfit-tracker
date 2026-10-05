package com.myfit.tracker.ai.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * ElevenLabs streaming text-to-speech. Audio arrives as raw PCM while it's being generated, so Pip
 * starts talking ~0.3 s after the request. Only the reply text is sent.
 */
class ElevenLabs {
    class Failure(val code: Int, msg: String, val permanent: Boolean, val quota: Boolean) : Exception(msg)

    private val base = "https://api.elevenlabs.io/v1"
    private fun eUrl(key: String, u: String) = if (com.myfit.tracker.ai.AiProxy.isProxy(key)) com.myfit.tracker.ai.AiProxy.url("eleven", u.removePrefix("https://api.elevenlabs.io")) else u
    private var urduModel: String? = null

    /** Streams speech for [text]; calls [onChunk] with PCM s16le bytes at [RATE] Hz. */
    suspend fun stream(key: String, text: String, urdu: Boolean, onChunk: (ByteArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val model = if (urdu) urduModel ?: pickUrduModel(key).also { urduModel = it } else "eleven_flash_v2_5"
        val body = JSONObject()
            .put("text", text)
            .put("model_id", model)
            .put("voice_settings", JSONObject().put("stability", 0.5).put("similarity_boost", 0.75).put("use_speaker_boost", true))
        if (urdu) body.put("language_code", "ur")
        val c = (URL(eUrl(key, "$base/text-to-speech/$VOICE_ID/stream?output_format=pcm_$RATE")).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 20_000
            doOutput = true
            if (com.myfit.tracker.ai.AiProxy.isProxy(key)) com.myfit.tracker.ai.AiProxy.authorize(this) else setRequestProperty("xi-api-key", key)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/pcm")
        }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw failure(code, err)
        }
        c.inputStream.use { input ->
            val buf = ByteArray(4096)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                if (n > 0) onChunk(buf, n)
            }
        }
    }

    /** Checks the key and returns remaining characters this month (null if unknown). */
    suspend fun check(key: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val c = (URL(eUrl(key, "$base/user/subscription")).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 10_000
            if (com.myfit.tracker.ai.AiProxy.isProxy(key)) com.myfit.tracker.ai.AiProxy.authorize(this) else setRequestProperty("xi-api-key", key)
        }
        val code = c.responseCode
        if (code !in 200..299) throw failure(code, c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val used = j.optInt("character_count", -1); val limit = j.optInt("character_limit", -1)
        if (used < 0 || limit < 0) null else used to limit
    }

    /** The fastest model this account can use that speaks Urdu. */
    private fun pickUrduModel(key: String): String = runCatching {
        val c = (URL(eUrl(key, "$base/models")).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 10_000
            if (com.myfit.tracker.ai.AiProxy.isProxy(key)) com.myfit.tracker.ai.AiProxy.authorize(this) else setRequestProperty("xi-api-key", key)
        }
        if (c.responseCode !in 200..299) return@runCatching null
        val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { m ->
                m.optBoolean("can_do_text_to_speech", true) &&
                    (m.optJSONArray("languages")?.let { l -> (0 until l.length()).any { l.getJSONObject(it).optString("language_id") == "ur" } } == true)
            }
            .map { it.getString("model_id") }
            .sortedBy { id -> when { "flash" in id -> 0; "turbo" in id -> 1; "conversational" in id -> 2; "v3" in id -> 3; else -> 4 } }
            .firstOrNull()
    }.getOrNull() ?: "eleven_v3"

    private fun failure(code: Int, body: String): Failure {
        val msg = runCatching {
            val d = JSONObject(body).opt("detail")
            if (d is JSONObject) (d.optString("status") + ": " + d.optString("message")) else d?.toString()
        }.getOrNull() ?: body.take(160)
        val quota = code == 402 || code == 429 || msg.contains("quota", true) || msg.contains("credits", true)
        val permanent = code == 401 || code == 403 || msg.contains("unusual_activity", true) || msg.contains("invalid_api_key", true)
        return Failure(code, msg, permanent, quota)
    }

    companion object {
        const val RATE = 24_000
        /** Premade voice "Jessica — playful, bright, warm". */
        const val VOICE_ID = "cgSgspJ2msm6clMCkdW9"
    }
}
