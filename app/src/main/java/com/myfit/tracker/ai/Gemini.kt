package com.myfit.tracker.ai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Minimal Gemini REST client (no SDK). Sends the Android package + signing-certificate headers so
 * the API key can be restricted to this app in Google Cloud Console.
 */
class Gemini(private val context: Context) {

    class ApiError(val code: Int, msg: String) : Exception(msg)

    private val base = "https://generativelanguage.googleapis.com/v1beta"

    /** SHA-1 of this APK's signing certificate, formatted for the X-Android-Cert header. */
    val certSha1: String by lazy {
        runCatching {
            val pm = context.packageManager
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            } else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            val der = sigs!!.first().toByteArray()
            MessageDigest.getInstance("SHA-1").digest(der).joinToString("") { "%02X".format(it) }
        }.getOrDefault("")
    }

    private fun open(url: String, key: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            setRequestProperty("x-goog-api-key", key)
            setRequestProperty("X-Android-Package", context.packageName)
            if (certSha1.isNotEmpty()) setRequestProperty("X-Android-Cert", certSha1)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

    private fun readBody(c: HttpURLConnection): Pair<Int, String> {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return code to body
    }

    private fun errorMessage(body: String) = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrDefault(body.take(200))

    /** Picks the best available "flash" model for this key (fast and inexpensive). */
    suspend fun pickModel(key: String): String = withContext(Dispatchers.IO) {
        val c = open("$base/models?pageSize=200", key, "GET")
        val (code, body) = readBody(c)
        if (code !in 200..299) throw ApiError(code, errorMessage(body))
        val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
        val names = (0 until models.length()).map { models.getJSONObject(it) }
            .filter { m -> (m.optJSONArray("supportedGenerationMethods")?.let { a -> (0 until a.length()).any { a.getString(it) == "generateContent" } } == true) }
            .map { it.getString("name").removePrefix("models/") }
        val prefs = listOf("gemini-2.5-flash", "gemini-flash-latest", "gemini-2.0-flash", "gemini-2.5-flash-lite", "gemini-1.5-flash")
        prefs.firstOrNull { it in names }
            ?: names.filter { "flash" in it && "image" !in it && "tts" !in it && "live" !in it && "exp" !in it }.maxOrNull()
            ?: names.firstOrNull { "gemini" in it } ?: throw ApiError(404, "No text model available for this key")
    }

    /** One chat turn. `history` is (role, text) with role "user" or "model". */
    suspend fun generate(key: String, model: String, system: String, history: List<Pair<String, String>>, noThinking: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val contents = JSONArray()
            history.forEach { (role, text) ->
                contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
            val gen = JSONObject().put("temperature", 0.7).put("maxOutputTokens", 900)
            if (noThinking && "flash" in model) gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            val req = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", gen)
            val c = open("$base/models/$model:generateContent", key, "POST")
            c.doOutput = true
            c.outputStream.use { it.write(req.toString().toByteArray()) }
            val (code, body) = readBody(c)
            if (code == 400 && noThinking && body.contains("thinking", ignoreCase = true)) {
                return@withContext generate(key, model, system, history, noThinking = false)
            }
            if (code !in 200..299) throw ApiError(code, errorMessage(body))
            val cand = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
                ?: throw ApiError(code, JSONObject(body).optJSONObject("promptFeedback")?.optString("blockReason")?.let { "Blocked: $it" } ?: "Empty reply")
            val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
            (0 until parts.length()).mapNotNull { parts.getJSONObject(it).optString("text").takeIf { t -> t.isNotBlank() } }.joinToString("").trim()
                .ifEmpty { throw ApiError(code, "Empty reply (${cand.optString("finishReason")})") }
        }

    companion object {
        fun systemPrompt(unitsLine: String) = """
You are Pip, the cheerful little buddy inside "MyFit Tracker", a private fitness logbook app. You look like a glossy peach mochi with a green sprout.
Personality: playful yet professional, warm, encouraging, concise and practical. At most 2 emoji per reply.
Rules you must follow:
1. For the user's personal numbers, use ONLY the "User data" block in the message. If it doesn't contain what's needed, say there isn't enough recorded data. Never guess or invent the user's numbers.
2. Describe patterns as observations, never as causes.
3. No medical diagnoses. For pain, injury, symptoms, medication or medical conditions, say to check with a qualified professional.
4. General fitness, training and nutrition knowledge is welcome — give specific, actionable guidance.
5. Keep replies under 150 words unless the user asks for detail. Use short bullet lists for plans.
6. $unitsLine
""".trim()
    }
}
