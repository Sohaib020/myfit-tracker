package com.myfit.tracker.ai

import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.pip.PipMood
import kotlinx.coroutines.flow.first

/**
 * Routes a question: your data first (offline, exact), then Gemini for everything else (only if
 * enabled and a key is set), with a small question-specific data summary — never your full history.
 */
class PipBrain(private val c: AppContainer) {
    private val data = DataBrain(c)
    private val gemini = Gemini(c.app)

    data class Reply(val text: String, val source: String, val mood: PipMood, val shared: String? = null)

    /** What was sent with the most recent online answer (shown on request, for transparency). */
    var lastShared: String? = null
        private set

    suspend fun ask(question: String): Reply {
        c.healthRepo.addChat("user", question, "user")
        val r = runCatching { answer(question) }.getOrElse {
            Reply("Oops — something went wrong on my side (${it.message ?: "unknown error"}). Your data is safe; try again?", "local", PipMood.CONCERNED)
        }
        c.healthRepo.addChat("pip", r.text, r.source)
        return r
    }

    private suspend fun answer(q: String): Reply {
        data.answer(q)?.let { return Reply(it.text, "data", it.mood) }
        val s = c.settings.settings.first()
        if (s.geminiKey.isBlank() || !s.onlineAi) {
            return Reply(
                if (s.geminiKey.isBlank()) "That one needs my online brain, which isn't set up yet 🌱 Add your Gemini key in Me → Pip. Offline I can answer things like \"How many times did I train legs this month?\", \"Average sleep last week\" or \"How much did my bench improve?\""
                else "Online answers are switched off, and that's not something I can work out from your logs alone. You can turn online answers on in Me → Pip.",
                "local", PipMood.CURIOUS,
            )
        }
        val summary = data.summaryFor(q)
        lastShared = summary
        var model = s.geminiModel.ifBlank { "gemini-2.5-flash" }
        val history = c.healthRepo.lastChat(9).dropLast(1) // exclude the question just stored
            .filter { it.source != "local" }
            .takeLast(8).map { (if (it.role == "user") "user" else "model") to it.text }
        val turn = history + ("user" to "User data (only what's relevant; may be incomplete):\n$summary\n\nQuestion: $q")
        val system = Gemini.systemPrompt("Use the user's units: ${s.units.weight.label}, ${s.units.length.label}, ${s.units.volume.label}, ${s.units.distance.label}.")
        val text = try {
            gemini.generate(s.geminiKey, model, system, turn)
        } catch (e: Gemini.ApiError) {
            if (e.code == 404 || e.message?.contains("not found", true) == true) {
                model = gemini.pickModel(s.geminiKey)
                c.settings.setGeminiModel(model)
                gemini.generate(s.geminiKey, model, system, turn)
            } else throw Exception(friendly(e))
        }
        val mood = when {
            listOf("great", "awesome", "well done", "nice", "proud", "🎉", "💪").any { text.contains(it, true) } -> PipMood.EXCITED
            listOf("doctor", "professional", "careful", "injur").any { text.contains(it, true) } -> PipMood.CONCERNED
            else -> PipMood.HAPPY
        }
        return Reply(text, "online", mood, summary)
    }

    private fun friendly(e: Gemini.ApiError) = when (e.code) {
        400 -> if (e.message?.contains("API key", true) == true) "the Gemini key looks invalid" else "Gemini rejected the request: ${e.message}"
        401, 403 -> "the Gemini key isn't allowed (check key restrictions: package com.myfit.tracker)"
        429 -> "Gemini's free quota is used up for now — try again later"
        else -> "Gemini error ${e.code}: ${e.message}"
    }

    /** Settings "Test key" button. Returns the chosen model name. */
    suspend fun testKey(key: String): String {
        val m = gemini.pickModel(key)
        gemini.generate(key, m, "Reply with one short friendly sentence.", listOf("user" to "Say hi as Pip."))
        c.settings.setGeminiModel(m)
        return m
    }

    val certSha1: String get() = gemini.certSha1
}
