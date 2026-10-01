package com.myfit.tracker.ai

import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.pip.PipMood
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Routes a question: your data first (offline, exact), then Gemini for everything else (only if
 * enabled and a key is set), with a small question-specific data summary — never your full history.
 */
class PipBrain(private val c: AppContainer) {
    private val data = DataBrain(c)
    private val router get() = c.aiRouter
    private val gemini get() = router.gemini

    data class Reply(
        val text: String, val source: String, val mood: PipMood, val shared: String? = null,
        /** Speech-only versions of a Roman Urdu reply: Urdu script and Devanagari (never shown). */
        val speakUr: String? = null, val speakHi: String? = null,
    )

    /** What was sent with the most recent online answer (shown on request, for transparency). */
    var lastShared: String? = null
        private set

    /** A failure Pip can explain in plain words. `retryable` = worth trying again shortly. */
    class PipError(val userText: String, val retryable: Boolean) : Exception(userText)

    /**
     * @param addUserMessage false when retrying a question that is already in the chat.
     * Failed online answers are stored with source "error" so the chat can offer "Try again".
     */
    suspend fun ask(question: String, addUserMessage: Boolean = true): Reply {
        if (addUserMessage) c.healthRepo.addChat("user", question, "user")
        val r = try {
            answer(question)
        } catch (e: PipError) {
            Reply(e.userText, "error", PipMood.CONCERNED)
        } catch (e: Exception) {
            Reply("I couldn't reach Gemini (${e.message ?: "network error"}). Check your internet connection, then tap Try again. Your data is safe.", "error", PipMood.CONCERNED)
        }
        c.healthRepo.addChat("pip", r.text, r.source)
        return r
    }

    private suspend fun answer(q: String): Reply {
        data.answer(q)?.let { return Reply(it.text, "data", it.mood) }
        val s = c.settings.settings.first()
        val chain = router.chain(s)
        if (chain.isEmpty() || !s.onlineAi) {
            return Reply(
                if (chain.isEmpty()) "That one needs my online brain, which isn't set up yet 🌱 Add an AI key in Settings → AI. Offline I can answer things like \"How many times did I train legs this month?\", \"Average sleep last week\" or \"How much did my bench improve?\""
                else "Online answers are switched off, and that's not something I can work out from your logs alone. You can turn online answers on in Settings → Pip.",
                "local", PipMood.CURIOUS,
            )
        }
        val summary = data.summaryFor(q)
        lastShared = summary
        val history = c.healthRepo.lastChat(9).dropLast(1) // exclude the question just stored
            .filter { it.source != "local" && it.source != "error" }
            .takeLast(8).map { (if (it.role == "user") "user" else "model") to it.text }
        val turn = history + ("user" to "User data (only what's relevant; may be incomplete):\n$summary\n\nQuestion: $q")
        val system = Gemini.systemPrompt("Use the user's units: ${s.units.weight.label}, ${s.units.length.label}, ${s.units.volume.label}, ${s.units.distance.label}.")
        var firstError: PipError? = null
        var text: String? = null
        for (p in chain) {
            try {
                text = if (p.id == "gemini") generateWithFallback(p.key, s.geminiModel, system, turn, budgetMs = if (chain.size > 1) 18_000L else 45_000L)
                else router.chatCompat(p.id, p.key, system, turn)
                router.lastProvider = p.label
                break
            } catch (e: PipError) {
                if (firstError == null) firstError = e
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (e is kotlinx.coroutines.TimeoutCancellationException) { if (firstError == null) firstError = PipError("${p.label} was too slow. Tap Try again.", true) } else throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = PipError("${p.label} couldn't answer (${AiRouter.shortMsg(e)}). Tap Try again in a moment.", true)
            }
        }
        if (text == null) throw (firstError ?: PipError("No AI service answered. Tap Try again in a moment.", true))
        val (display, ur, hi) = splitSpeech(text)
        return Reply(display, "online", com.myfit.tracker.ui.pip.moodForReply(display, q), summary, ur, hi)
    }

    /**
     * Robust generation:
     *  - busy/unavailable (500/502/503/504): retry the same model with back-off, then try the next model;
     *  - quota (429): move to another model (quotas are per model);
     *  - retired/not found: try the model Google suggests, then the best models this key can use;
     *  - bad key / not allowed: stop and explain.
     * Remembers a new model only when the saved one is retired (a busy model stays the favourite).
     */
    private suspend fun generateWithFallback(key: String, saved: String, system: String, turn: List<Pair<String, String>>, budgetMs: Long = 45_000L): String {
        val tried = mutableSetOf<String>()
        val candidates = ArrayDeque<String>()
        if (saved.isNotBlank()) candidates.addLast(saved)
        var listed = false
        var busy = 0
        var quota = 0
        var lastError: Gemini.ApiError? = null
        val started = System.currentTimeMillis()
        var savedRetired = saved.isBlank()

        while (tried.size < 4 && System.currentTimeMillis() - started < budgetMs) {
            if (candidates.isEmpty()) {
                if (listed) break
                listed = true
                gemini.rankedModels(key).filter { it !in tried }.forEach { candidates.addLast(it) }
                if (candidates.isEmpty()) break
            }
            val m = candidates.removeFirst()
            if (!tried.add(m)) continue
            var attempt = 0
            while (true) {
                try {
                    val out = gemini.generate(key, m, system, turn)
                    if (m != saved && savedRetired) c.settings.setGeminiModel(m)
                    return out
                } catch (e: Gemini.ApiError) {
                    lastError = e
                    when {
                        e.code in listOf(500, 502, 503, 504) || e.message?.contains("overloaded", true) == true || e.message?.contains("high demand", true) == true -> {
                            if (attempt < 2) { delay(if (attempt == 0) 1_500L else 4_000L); attempt++; continue }
                            busy++; break      // this model stays busy → try another one
                        }
                        e.code == 429 -> { quota++; break }
                        gemini.isModelProblem(e) -> {
                            if (m == saved) savedRetired = true
                            gemini.suggestedModel(e)?.takeIf { it !in tried }?.let { candidates.addFirst(it) }
                            break
                        }
                        else -> throw PipError(friendly(e), retryable = false)
                    }
                }
            }
        }
        throw when {
            busy > 0 -> PipError("Gemini's servers are overloaded right now — Google says spikes like this are usually brief. I retried and tried ${tried.size} model${if (tried.size == 1) "" else "s"}. Tap Try again in a minute. (Not a problem with your key or your data.)", true)
            quota > 0 -> PipError("Your free Gemini quota is used up for the moment. It resets automatically — try again later. Questions about your own logs still work offline.", true)
            else -> PipError(lastError?.let { friendly(it) } ?: "I couldn't find a working Gemini model for this key.", true)
        }
    }

    /** Separates the hidden [[ur]] / [[hi]] speech lines Gemini appends to Roman Urdu replies. */
    private fun splitSpeech(raw: String): Triple<String, String?, String?> {
        val ur = Regex("""\[\[ur]]\s*(.+?)(?=\[\[hi]]|$)""", RegexOption.DOT_MATCHES_ALL).find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val hi = Regex("""\[\[hi]]\s*(.+)$""", RegexOption.DOT_MATCHES_ALL).find(raw)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        val cut = listOf(raw.indexOf("[[ur]]"), raw.indexOf("[[hi]]")).filter { it >= 0 }.minOrNull()
        val display = (if (cut != null) raw.substring(0, cut) else raw).trim()
        return Triple(display.ifBlank { raw.trim() }, ur, hi)
    }

    private fun friendly(e: Gemini.ApiError) = when (e.code) {
        400 -> if (e.message?.contains("API key", true) == true) "That Gemini key looks invalid. Check it in Settings → AI." else "Gemini rejected the request: ${e.message}"
        401, 403 -> "This Gemini key isn't allowed to be used here. If you restricted it, make sure it allows Android app com.myfit.tracker with the SHA-1 shown in Settings → AI."
        429 -> "Your free Gemini quota is used up for now — try again later."
        else -> "Gemini error ${e.code}: ${e.message}"
    }

    /** Settings "Test key" button. Returns the chosen model name. */
    suspend fun testKey(key: String): String {
        c.settings.setGeminiModel("")
        try {
            generateWithFallback(key, "", "Reply with one short friendly sentence.", listOf("user" to "Say hi as Pip."))
        } catch (e: PipError) {
            // key works but every model is busy → still report which model will be used
            if (!e.retryable) throw Exception(e.userText)
            val m = gemini.pickModel(key)
            c.settings.setGeminiModel(m)
            return "$m (Google is busy right now — the key itself is fine)"
        }
        return c.settings.settings.first().geminiModel
    }

    val certSha1: String get() = gemini.certSha1
}
