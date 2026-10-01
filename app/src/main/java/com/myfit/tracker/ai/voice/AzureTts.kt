package com.myfit.tracker.ai.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Azure neural text-to-speech (free tier: 500,000 characters a month). Natural English and real
 * Urdu (ur-PK) voices. Audio is streamed back as raw 24 kHz PCM, so playback starts quickly.
 */
class AzureTts {
    class Failure(val code: Int, msg: String) : Exception(msg) {
        val permanent get() = code == 401 || code == 403 || code == 404
        val quota get() = code == 429
    }

    suspend fun stream(key: String, region: String, text: String, urdu: Boolean, onChunk: (ByteArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val voice = if (urdu) "ur-PK-UzmaNeural" else "en-US-AnaNeural"
        val lang = if (urdu) "ur-PK" else "en-US"
        val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val prosody = if (urdu) "<prosody rate=\"+4%\" pitch=\"+8%\">$esc</prosody>" else "<prosody rate=\"+2%\">$esc</prosody>"
        val ssml = "<speak version=\"1.0\" xml:lang=\"$lang\"><voice name=\"$voice\">$prosody</voice></speak>"
        val c = (URL("https://${region.trim().lowercase()}.tts.speech.microsoft.com/cognitiveservices/v1").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Ocp-Apim-Subscription-Key", key)
            setRequestProperty("Content-Type", "application/ssml+xml")
            setRequestProperty("X-Microsoft-OutputFormat", "raw-24khz-16bit-mono-pcm")
            setRequestProperty("User-Agent", "MyFitTracker")
        }
        c.outputStream.use { it.write(ssml.toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw Failure(code, when (code) { 401 -> "key rejected"; 429 -> "free monthly characters used up"; else -> "error $code ${err.take(80)}" })
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

    companion object { const val RATE = 24_000 }
}
