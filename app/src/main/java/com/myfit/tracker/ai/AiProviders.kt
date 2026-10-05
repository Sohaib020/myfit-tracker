package com.myfit.tracker.ai

import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.prefs.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible chat/vision client used for the backup AIs (Groq, OpenRouter, Mistral).
 * Model names are discovered from each provider's /models list, so retired models drop out by themselves.
 */
class OpenAiCompat(val id: String, val label: String, private val base: String, private val headers: Map<String, String> = emptyMap()) {

    class AiError(val code: Int, msg: String) : Exception(msg)

    private data class Lists(val chat: List<String>, val vision: List<String>, val at: Long)
    @Volatile private var lists: Lists? = null
    @Volatile var workingVision: String? = null
    @Volatile var workingChat: String? = null

    private fun open(path: String, key: String, method: String, timeout: Int): HttpURLConnection =
        (URL(if (AiProxy.isProxy(key)) AiProxy.url(id, java.net.URL(base).path + path) else base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = minOf(10_000, timeout)
            readTimeout = timeout
            if (AiProxy.isProxy(key)) AiProxy.authorize(this) else setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    private fun read(c: HttpURLConnection): Pair<Int, String> {
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return code to body
    }

    private fun err(body: String) = runCatching {
        val o = JSONObject(body)
        o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: o.optString("message").takeIf { it.isNotBlank() } ?: o.optString("detail")
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(160)

    /** (chat models best-first, vision models best-first) for this key. Cached for 6 h. */
    private suspend fun models(key: String): Lists = withContext(Dispatchers.IO) {
        lists?.takeIf { System.currentTimeMillis() - it.at < 6 * 3600_000L }?.let { return@withContext it }
        val c = open("/models", key, "GET", 15_000)
        val (code, body) = read(c)
        if (code !in 200..299) throw AiError(code, err(body))
        val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
        val all = (0 until arr.length()).map { arr.getJSONObject(it) }
        val l = when (id) {
            "openrouter" -> {
                val free = all.filter { m ->
                    val idS = m.optString("id")
                    val p = m.optJSONObject("pricing")
                    idS.endsWith(":free") || (p != null && p.optString("prompt") == "0" && p.optString("completion") == "0")
                }
                fun mods(m: JSONObject) = m.optJSONObject("architecture")?.optJSONArray("input_modalities")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                val textOk = free.filter { "text" in mods(it) || mods(it).isEmpty() }
                val vis = free.filter { "image" in mods(it) }
                fun score(m: JSONObject): Int {
                    val n = m.optString("id").lowercase()
                    return listOf("gemini", "llama-4", "qwen", "deepseek", "gpt-oss", "mistral", "gemma").indexOfFirst { it in n }.let { if (it < 0) 99 else it }
                }
                Lists(textOk.sortedWith(compareBy<JSONObject> { score(it) }.thenByDescending { it.optInt("context_length") }).map { it.optString("id") },
                    vis.sortedWith(compareBy<JSONObject> { score(it) }.thenByDescending { it.optInt("context_length") }).map { it.optString("id") }, System.currentTimeMillis())
            }
            "mistral" -> {
                fun cap(m: JSONObject, k: String) = m.optJSONObject("capabilities")?.optBoolean(k, false) == true
                val chat = all.filter { cap(it, "completion_chat") }.map { it.optString("id") }.distinct()
                val vis = all.filter { cap(it, "vision") }.map { it.optString("id") }.distinct()
                fun r(n: String) = when { n == "mistral-small-latest" -> 0; n == "mistral-medium-latest" -> 1; n.startsWith("pixtral") && n.endsWith("latest") -> 2; n.endsWith("latest") -> 3; else -> 5 }
                Lists(chat.sortedBy { r(it) }, vis.sortedBy { r(it) }, System.currentTimeMillis())
            }
            else -> { // groq: no modality info in /models, so rank by name
                val ids = all.map { it.optString("id") }.filter { n ->
                    listOf("whisper", "guard", "tts", "playai", "orpheus", "compound", "prompt-guard", "distil").none { it in n.lowercase() }
                }
                val visRx = Regex("""(vision|scout|maverick|llama-4|qwen.*vl|qwen3\.\d|gemma-3|-vl)""", RegexOption.IGNORE_CASE)
                fun chatRank(n: String) = listOf("llama-3.3-70b", "gpt-oss-120b", "llama-4-maverick", "qwen3", "llama-4-scout", "gpt-oss-20b", "kimi", "llama").indexOfFirst { it in n.lowercase() }.let { if (it < 0) 99 else it }
                Lists(ids.sortedBy { chatRank(it) }, ids.filter { visRx.containsMatchIn(it) }, System.currentTimeMillis())
            }
        }
        lists = l
        l
    }

    private suspend fun post(key: String, req: JSONObject, timeout: Int): String = withContext(Dispatchers.IO) {
        val c = open("/chat/completions", key, "POST", timeout)
        c.doOutput = true
        c.outputStream.use { it.write(req.toString().toByteArray()) }
        val (code, body) = read(c)
        if (code !in 200..299) throw AiError(code, err(body))
        val msg = JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message") ?: throw AiError(code, "Empty reply")
        val content = msg.opt("content")
        val text = when (content) {
            is String -> content
            is JSONArray -> (0 until content.length()).joinToString("") { content.optJSONObject(it)?.optString("text") ?: "" }
            else -> ""
        }
        text.replace(Regex("""(?s)<think>.*?</think>"""), "").trim().ifEmpty { throw AiError(code, "Empty reply") }
    }

    suspend fun chat(key: String, system: String, history: List<Pair<String, String>>, maxTokens: Int = 1600): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (role, text) -> msgs.put(JSONObject().put("role", if (role == "model") "assistant" else "user").put("content", text)) }
        val candidates = (listOfNotNull(workingChat) + models(key).chat).distinct().take(3)
        if (candidates.isEmpty()) throw AiError(404, "$label has no chat model available for this key")
        var last: Exception? = null
        for (m in candidates) {
            try {
                val out = post(key, JSONObject().put("model", m).put("messages", msgs).put("temperature", 0.7).put("max_tokens", maxTokens), 40_000)
                workingChat = m
                return out
            } catch (e: AiError) {
                last = e
                if (e.code == 401 || e.code == 403) throw e
                if (e.code in 500..504) delay(800)
            }
        }
        throw last ?: AiError(0, "$label failed")
    }

    suspend fun vision(key: String, prompt: String, jpeg: ByteArray, json: Boolean = true, timeout: Int = 60_000): String {
        val b64 = android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
        val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", content))
        val candidates = (listOfNotNull(workingVision) + models(key).vision).distinct().take(3)
        if (candidates.isEmpty()) throw AiError(404, "$label has no image model available for this key")
        var last: Exception? = null
        for (m in candidates) {
            for (useJson in if (json) listOf(true, false) else listOf(false)) {
                try {
                    val req = JSONObject().put("model", m).put("messages", msgs).put("temperature", 0.2).put("max_tokens", 1500)
                    if (useJson) req.put("response_format", JSONObject().put("type", "json_object"))
                    val out = post(key, req, timeout)
                    workingVision = m
                    return out
                } catch (e: AiError) {
                    last = e
                    if (e.code == 401 || e.code == 403) throw e
                    // JSON mode not supported with images → retry the same model without it
                    if (useJson && e.code == 400 && (e.message ?: "").contains("json", true)) continue
                    break
                }
            }
        }
        throw last ?: AiError(0, "$label failed")
    }

    fun reset() { lists = null; workingChat = null; workingVision = null }
}

/**
 * Picks which AI answers: Gemini first (unless you chose another), then Groq, OpenRouter and Mistral —
 * each only if its key exists. A slow, busy or out-of-quota provider is skipped automatically.
 */
class AiRouter(private val c: AppContainer) {
    val gemini = Gemini(c.app)
    val groq = OpenAiCompat("groq", "Groq", "https://api.groq.com/openai/v1")
    val openRouter = OpenAiCompat("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", mapOf("HTTP-Referer" to "https://github.com/Sohaib020/myfit-tracker", "X-Title" to "MyFit Tracker"))
    val mistral = OpenAiCompat("mistral", "Mistral", "https://api.mistral.ai/v1")

    /** Provider that produced the most recent answer (shown in chat/settings). */
    @Volatile var lastProvider: String? = null

    data class Avail(val id: String, val label: String, val key: String)

    fun available(s: AppSettings): List<Avail> = listOf(
        Avail("gemini", "Gemini", s.geminiKeyEff), Avail("groq", "Groq", s.groqKeyEff),
        Avail("openrouter", "OpenRouter", s.openRouterKeyEff), Avail("mistral", "Mistral", s.mistralKeyEff),
    ).filter { it.key.isNotBlank() }

    /** Ordered chain. `fast` puts the quickest provider (Groq) first — used for live camera labels. */
    fun chain(s: AppSettings, fast: Boolean = false): List<Avail> {
        val a = available(s)
        val pref = when {
            fast -> listOf("groq", "gemini", "openrouter", "mistral")
            s.aiPrimary != "auto" -> listOf(s.aiPrimary) + listOf("gemini", "groq", "openrouter", "mistral").filter { it != s.aiPrimary }
            else -> listOf("gemini", "groq", "openrouter", "mistral")
        }
        return pref.mapNotNull { id -> a.firstOrNull { it.id == id } }
    }

    fun compat(id: String) = when (id) { "groq" -> groq; "openrouter" -> openRouter; else -> mistral }

    /** Vision request across providers. Gemini models are tried via its own client. */
    suspend fun vision(prompt: String, jpeg: ByteArray, fast: Boolean = false, perProviderMs: Long = 45_000): String {
        val s = c.settings.settings.first()
        val chain = chain(s, fast)
        if (chain.isEmpty()) throw IllegalStateException("Food photos need an online AI key (Gemini, Groq, OpenRouter or Mistral). Add one in Settings → AI.")
        val errors = mutableListOf<String>()
        for (p in chain) {
            try {
                val out = withTimeout(perProviderMs) {
                    if (p.id == "gemini") geminiVision(p.key, s.geminiModel, prompt, jpeg, fast)
                    else compat(p.id).vision(p.key, prompt, jpeg, timeout = (perProviderMs - 2000).toInt().coerceAtLeast(8000))
                }
                lastProvider = p.label
                return out
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                errors += "${p.label}: too slow"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errors += "${p.label}: ${shortMsg(e)}"
            }
        }
        android.util.Log.w("AiRouter", "vision failed: " + errors.joinToString("; "))
        val noNet = errors.isNotEmpty() && errors.all { it.contains("resolve host", true) || it.contains("Unable to resolve", true) || it.contains("failed to connect", true) }
        throw IllegalStateException(if (noNet) "No internet connection right now. Check Wi-Fi or mobile data and try again — or search foods instead."
            else "The online AI is busy right now. Try again in a moment, or search foods instead.")
    }

    private suspend fun geminiVision(key: String, saved: String, prompt: String, jpeg: ByteArray, fast: Boolean): String {
        val models = buildList { if (saved.isNotBlank()) add(saved); addAll(gemini.rankedModels(key)) }.distinct().take(if (fast) 1 else 3)
        var last: Exception? = null
        for (m in models) {
            try { return gemini.generateVision(key, m, prompt, jpeg) } catch (e: Gemini.ApiError) {
                last = e
                if (e.code == 400 && e.message?.contains("API key", true) == true) throw e
                if (e.code in 500..504 && !fast) delay(1200)
            }
        }
        throw last ?: IllegalStateException("No Gemini model")
    }

    /** Chat with a non-Gemini provider (Gemini chat keeps its own robust fallback in PipBrain). */
    suspend fun chatCompat(id: String, key: String, system: String, turn: List<Pair<String, String>>): String =
        withTimeout(45_000) { compat(id).chat(key, system, turn) }

    /** Settings "Test" button for a backup provider: returns the model that answered. */
    suspend fun test(id: String, key: String): String {
        val p = compat(id)
        p.reset()
        p.chat(key, "Reply with one short friendly sentence.", listOf("user" to "Say hi as Pip, a fitness buddy."), 60)
        return p.workingChat ?: "ok"
    }

    companion object {
        fun shortMsg(e: Throwable): String = when (e) {
            is kotlinx.coroutines.TimeoutCancellationException -> "too slow"
            is OpenAiCompat.AiError -> when (e.code) { 429 -> "quota/rate limit"; 401, 403 -> "key rejected"; in 500..599 -> "busy"; else -> (e.message ?: "error").take(80) }
            is Gemini.ApiError -> when (e.code) { 429 -> "quota"; in 500..599 -> "busy"; else -> (e.message ?: "error").take(80) }
            else -> (e.message ?: e.javaClass.simpleName).take(80)
        }
    }
}
