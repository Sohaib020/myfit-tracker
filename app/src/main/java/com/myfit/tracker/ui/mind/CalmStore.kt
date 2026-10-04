package com.myfit.tracker.ui.mind

import android.content.Context
import com.myfit.tracker.domain.Clock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A completed PHQ-9 (depression) or GAD-7 (anxiety) self-check. [item9] = PHQ-9's last item (thoughts of self-harm). */
data class Assessment(val kind: String, val date: LocalDate, val score: Int, val item9: Int = 0)
data class SosRun(val date: LocalDate, val before: Int, val after: Int)
data class ThoughtRecord(val date: LocalDate, val situation: String, val thought: String, val before: Int, val balanced: String, val after: Int)

/** Everything in the Anxiety & Depression tab stays on this phone (private SharedPreferences, never uploaded). */
object CalmStore {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("calm_store", Context.MODE_PRIVATE)
    private fun arr(c: Context, k: String) = runCatching { JSONArray(p(c).getString(k, "[]")) }.getOrDefault(JSONArray())
    private fun put(c: Context, k: String, a: JSONArray, keep: Int = 200) {
        val out = JSONArray(); val from = (a.length() - keep).coerceAtLeast(0)
        for (i in from until a.length()) out.put(a.get(i))
        p(c).edit().putString(k, out.toString()).apply()
    }

    fun assessments(c: Context): List<Assessment> = arr(c, "assess").let { a -> (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i); runCatching { Assessment(o.getString("k"), LocalDate.parse(o.getString("d")), o.getInt("s"), o.optInt("i9")) }.getOrNull() } }
    fun addAssessment(c: Context, x: Assessment) = put(c, "assess", arr(c, "assess").put(JSONObject().put("k", x.kind).put("d", x.date.toString()).put("s", x.score).put("i9", x.item9)))

    fun sos(c: Context): List<SosRun> = arr(c, "sos").let { a -> (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i); runCatching { SosRun(LocalDate.parse(o.getString("d")), o.getInt("b"), o.getInt("a")) }.getOrNull() } }
    fun addSos(c: Context, before: Int, after: Int) = put(c, "sos", arr(c, "sos").put(JSONObject().put("d", Clock.today().toString()).put("b", before).put("a", after)))

    fun thoughts(c: Context): List<ThoughtRecord> = arr(c, "thought").let { a -> (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i); runCatching { ThoughtRecord(LocalDate.parse(o.getString("d")), o.getString("s"), o.getString("t"), o.getInt("b"), o.getString("bal"), o.getInt("a")) }.getOrNull() } }
    fun addThought(c: Context, t: ThoughtRecord) = put(c, "thought", arr(c, "thought").put(JSONObject().put("d", t.date.toString()).put("s", t.situation).put("t", t.thought).put("b", t.before).put("bal", t.balanced).put("a", t.after)), 60)

    /** Today's activity plan (behavioural activation): list of (activity, done). */
    fun plan(c: Context, d: LocalDate = Clock.today()): List<Pair<String, Boolean>> = arr(c, "plan_$d").let { a -> (0 until a.length()).map { i -> val o = a.getJSONObject(i); o.getString("t") to o.getBoolean("x") } }
    fun setPlan(c: Context, items: List<Pair<String, Boolean>>, d: LocalDate = Clock.today()) =
        p(c).edit().putString("plan_$d", JSONArray().apply { items.forEach { put(JSONObject().put("t", it.first).put("x", it.second)) } }.toString()).apply()
    fun activeDays(c: Context, back: Int = 30): Int = (0 until back).count { k -> plan(c, Clock.today().minusDays(k.toLong())).any { it.second } }

    fun worries(c: Context): List<String> = arr(c, "worry").let { a -> (0 until a.length()).map { a.getString(it) } }
    fun setWorries(c: Context, w: List<String>) = p(c).edit().putString("worry", JSONArray(w).toString()).apply()
    fun worryTime(c: Context) = p(c).getString("worry_time", "18:00")!!
    fun setWorryTime(c: Context, t: String) = p(c).edit().putString("worry_time", t).apply()

    fun gratitude(c: Context, d: LocalDate = Clock.today()): List<String> = arr(c, "grat_$d").let { a -> (0 until a.length()).map { a.getString(it) } }
    fun setGratitude(c: Context, g: List<String>, d: LocalDate = Clock.today()) = p(c).edit().putString("grat_$d", JSONArray(g).toString()).apply()
}

// ------------------------------------------------------------------ questionnaires (PHQ-9 & GAD-7, Pfizer — free to use)

val PHQ9 = listOf(
    "Little interest or pleasure in doing things",
    "Feeling down, depressed, or hopeless",
    "Trouble falling or staying asleep, or sleeping too much",
    "Feeling tired or having little energy",
    "Poor appetite or overeating",
    "Feeling bad about yourself — or that you are a failure or have let yourself or your family down",
    "Trouble concentrating on things, such as reading or watching TV",
    "Moving or speaking so slowly that other people could have noticed — or being so fidgety or restless that you've been moving around a lot more than usual",
    "Thoughts that you would be better off dead, or of hurting yourself",
)
val GAD7 = listOf(
    "Feeling nervous, anxious, or on edge",
    "Not being able to stop or control worrying",
    "Worrying too much about different things",
    "Trouble relaxing",
    "Being so restless that it's hard to sit still",
    "Becoming easily annoyed or irritable",
    "Feeling afraid, as if something awful might happen",
)
val ANSWERS = listOf("Not at all", "Several days", "More than half the days", "Nearly every day")

/** Standard severity bands (Kroenke 2001; Spitzer 2006). */
fun band(kind: String, s: Int): String = if (kind == "phq9") when (s) {
    in 0..4 -> "Minimal"; in 5..9 -> "Mild"; in 10..14 -> "Moderate"; in 15..19 -> "Moderately severe"; else -> "Severe"
} else when (s) { in 0..4 -> "Minimal"; in 5..9 -> "Mild"; in 10..14 -> "Moderate"; else -> "Severe" }
