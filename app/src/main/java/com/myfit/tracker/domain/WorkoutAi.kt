package com.myfit.tracker.domain

import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.WorkoutPlanner.Day
import com.myfit.tracker.domain.WorkoutPlanner.Equip
import com.myfit.tracker.domain.WorkoutPlanner.Goal
import com.myfit.tracker.domain.WorkoutPlanner.Item
import com.myfit.tracker.domain.WorkoutPlanner.Level
import com.myfit.tracker.domain.WorkoutPlanner.Target
import org.json.JSONObject

/**
 * Hybrid workout AI. The rule-based [WorkoutPlanner] always produces a sound day instantly (works offline); this layer
 * lets the user ask for changes in plain words ("no barbell", "shorter", "more biceps", "my knee hurts, no jumping")
 * and asks the online AI (or the offline brain) to adjust it. The AI may only use exercises from the planner's
 * catalog list for the chosen muscles, and every number is clamped, so a bad answer can never produce nonsense —
 * anything invalid is dropped and the rule-based day is kept.
 */
object WorkoutAi {

    data class Result(val day: Day, val note: String?)

    suspend fun tune(
        container: AppContainer, base: Day, targets: List<Target>, goal: Goal, equip: Equip, level: Level, minutes: Int, request: String,
    ): Result {
        // a request that names muscles changes the targets first (locally), so the AI gets the right exercise list
        val asked = WorkoutPlanner.targetsFromText(request)
        val tg = if (asked.isNotEmpty() && asked.toSet() != targets.toSet()) asked else targets
        val start = if (tg != targets) WorkoutPlanner.day(tg, goal, equip, level, minutes) else base
        val allowed = WorkoutPlanner.allowedKeys(tg, equip, level)
        val names = container.workoutRepo.exercisesByKeys(allowed.map { it.first }).mapValues { it.value.name }
        val list = allowed.filter { it.first in names }.joinToString("\n") { (k, m) -> "$k | ${names[k]} | ${m.label}" }
        val current = start.items.joinToString("\n") { "${it.key} | ${it.sets} sets | ${it.reps} | rest ${it.rest}s" }
        val system = """
            You are an experienced strength coach inside a fitness app. Adjust the workout to the user's request.
            Rules:
            - Use ONLY exercise keys from the ALLOWED list (exact spelling). Never invent keys.
            - Keep it about $minutes minutes, goal: ${goal.label}, level: ${level.label}, equipment: ${equip.label}.
            - reps is a string: "8-12", "10", "45s" (timed) or "max". sets 1-6. rest seconds 20-240.
            - If the request mentions pain or injury, avoid exercises that load that area and keep it gentle; do not give medical advice.
            - Reply with ONLY minified JSON, no prose, no code fences:
              {"name":"short day name","note":"one short sentence on what you changed","items":[{"k":"key","s":3,"r":"8-12","rest":90}]}
        """.trimIndent()
        val user = "ALLOWED (key | name | muscle):\n$list\n\nCURRENT WORKOUT:\n$current\n\nREQUEST: $request"
        val raw = container.aiRouter.text(system, user)
        parse(raw, start, allowed.map { it.first }.toSet())?.let { return it }
        if (start !== base) return Result(start, "Rebuilt for ${WorkoutPlanner.suggestName(tg)}.")
        throw IllegalStateException("The AI's answer didn't fit — your workout is unchanged. Try asking differently.")
    }

    private fun parse(raw: String, fallback: Day, allowed: Set<String>): Result? = runCatching {
        val json = raw.substringAfter('{', "").let { "{" + it }.substringBeforeLast('}') + "}"
        val o = JSONObject(json)
        val a = o.getJSONArray("items")
        val seen = HashSet<String>()
        val items = (0 until a.length()).mapNotNull { i ->
            val x = a.getJSONObject(i)
            val k = x.optString("k").trim()
            if (k !in allowed || !seen.add(k)) return@mapNotNull null
            val m = WorkoutPlanner.muscleOf(k) ?: return@mapNotNull null
            val r = x.optString("r", "10").trim().lowercase().replace('–', '-').replace(" ", "")
            val reps = when {
                r == "max" -> r
                Regex("""\d{1,2}-\d{1,2}""").matches(r) -> r
                Regex("""\d{1,2}""").matches(r) -> r
                Regex("""\d{1,3}s""").matches(r) -> r
                Regex("""\d{1,2}m""").matches(r) -> r
                else -> "8-12"
            }
            Item(k, x.optInt("s", 3).coerceIn(1, 6), reps, x.optInt("rest", 90).coerceIn(20, 240), WorkoutPlanner.isMain(k), m)
        }
        if (items.size < 2) return null
        val name = o.optString("name").trim().take(40).ifBlank { fallback.name }
        Result(Day(name, fallback.focus, items), o.optString("note").trim().take(160).ifBlank { null })
    }.getOrNull()
}
