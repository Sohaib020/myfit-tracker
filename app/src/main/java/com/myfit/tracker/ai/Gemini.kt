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
        (URL(if (AiProxy.isProxy(key)) AiProxy.url("gemini", url.removePrefix("https://generativelanguage.googleapis.com")) else url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 45_000
            if (AiProxy.isProxy(key)) AiProxy.authorize(this) else setRequestProperty("x-goog-api-key", key)
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

    /**
     * All text models this key can call, best first: newest stable "flash" (fast, cheap) → the
     * "flash-latest" alias → other flash/lite → anything Gemini. Nothing is hardcoded, so retired
     * models drop out automatically.
     */
    @Volatile private var rankedCache: Triple<String, Long, List<String>>? = null

    /** Cached for 6 hours per key — listing models costs a network round trip on every photo otherwise. */
    suspend fun rankedModels(key: String): List<String> {
        rankedCache?.let { (k, at, l) -> if (k == key && System.currentTimeMillis() - at < 6 * 3_600_000L && l.isNotEmpty()) return l }
        return rankedModelsFresh(key).also { rankedCache = Triple(key, System.currentTimeMillis(), it) }
    }

    private suspend fun rankedModelsFresh(key: String): List<String> = withContext(Dispatchers.IO) {
        val c = open("$base/models?pageSize=300", key, "GET")
        val (code, body) = readBody(c)
        if (code !in 200..299) throw ApiError(code, errorMessage(body))
        val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
        val names = (0 until models.length()).map { models.getJSONObject(it) }
            .filter { m -> m.optJSONArray("supportedGenerationMethods")?.let { a -> (0 until a.length()).any { a.getString(it) == "generateContent" } } == true }
            .map { it.getString("name").removePrefix("models/") }
            .filter { n -> "gemini" in n && listOf("image", "tts", "audio", "live", "embedding", "vision", "robotics", "computer-use").none { it in n } }
        fun version(n: String) = Regex("""gemini-(\d+(?:\.\d+)?)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        fun rank(n: String): Int = when {
            "flash" in n && "lite" !in n && "preview" !in n && "exp" !in n && "latest" !in n -> 0
            n == "gemini-flash-latest" -> 1
            "flash" in n && "preview" !in n && "exp" !in n -> 2
            "flash" in n -> 3
            else -> 4
        }
        names.sortedWith(compareBy<String> { rank(it) }.thenByDescending { version(it) }.thenBy { it.length })
            .ifEmpty { throw ApiError(404, "No Gemini text model is available for this key") }
    }

    suspend fun pickModel(key: String): String = rankedModels(key).first()

    /** Model name Google suggests in a "retired / update to models/X" error, if any. */
    fun suggestedModel(e: ApiError): String? = Regex("""models/([a-z0-9][a-z0-9.\-]*)""").findAll(e.message ?: "")
        .map { it.groupValues[1].trimEnd('.') }.lastOrNull()

    fun isModelProblem(e: ApiError): Boolean {
        val m = (e.message ?: "").lowercase()
        return e.code == 404 || listOf("not found", "no longer available", "deprecated", "not supported", "update your code", "is not available", "retired").any { it in m }
    }

    /** One chat turn. `history` is (role, text) with role "user" or "model". */
    suspend fun generate(key: String, model: String, system: String, history: List<Pair<String, String>>, noThinking: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val contents = JSONArray()
            history.forEach { (role, text) ->
                contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
            val gen = JSONObject().put("temperature", 0.7).put("maxOutputTokens", 1600)
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

    /** One image + instruction → JSON text (used for food photos). */
    suspend fun generateVision(key: String, model: String, prompt: String, jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val parts = JSONArray()
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP))))
            .put(JSONObject().put("text", prompt))
        val gen = JSONObject().put("temperature", 0.2).put("maxOutputTokens", 2000).put("responseMimeType", "application/json")
        if ("flash" in model) gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        val req = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts))).put("generationConfig", gen)
        val c = open("$base/models/$model:generateContent", key, "POST")
        c.readTimeout = 60_000
        c.doOutput = true
        c.outputStream.use { it.write(req.toString().toByteArray()) }
        val (code, body) = readBody(c)
        if (code == 400 && body.contains("thinking", ignoreCase = true)) {
            gen.remove("thinkingConfig")
            val c2 = open("$base/models/$model:generateContent", key, "POST"); c2.readTimeout = 60_000; c2.doOutput = true
            c2.outputStream.use { it.write(req.toString().toByteArray()) }
            val (code2, body2) = readBody(c2)
            if (code2 !in 200..299) throw ApiError(code2, errorMessage(body2))
            return@withContext textOf(code2, body2)
        }
        if (code !in 200..299) throw ApiError(code, errorMessage(body))
        textOf(code, body)
    }

    private fun textOf(code: Int, body: String): String {
        val cand = JSONObject(body).optJSONArray("candidates")?.optJSONObject(0) ?: throw ApiError(code, "Empty reply")
        val parts = cand.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim()
    }

    companion object {
        fun systemPrompt(unitsLine: String) = """
${com.myfit.tracker.ui.pip.Buddy.persona()}
Personality: playful yet professional, warm, encouraging, concise and practical. At most 2 emoji per reply.
Rules you must follow:
1. For the user's personal numbers, use ONLY the "User data" block in the message. If it doesn't contain what's needed, say there isn't enough recorded data. Never guess or invent the user's numbers.
2. Describe patterns as observations, never as causes.
3. No medical diagnoses. For pain, injury, symptoms, medication or medical conditions, say to check with a qualified professional.
4. General fitness, training and nutrition knowledge is welcome — give specific, actionable guidance.
5. Keep replies under 150 words unless the user asks for detail. Use short bullet lists for plans.
6. $unitsLine
7. Language: if the user writes in Urdu or Roman Urdu, reply in natural Roman Urdu. Then, so the app can speak it, add exactly two more lines at the very end:
[[ur]] the same reply written in Urdu script
[[hi]] the same reply written in Devanagari script (same Hindustani words, pronounced the Urdu way, with nukta letters like ज़ ख़ ग़ फ़ क़)
No emoji, bullets or markdown in those two lines. For English questions reply in English and do NOT add these lines.
""".trim()
    }
}
