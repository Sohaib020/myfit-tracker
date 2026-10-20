package com.myfit.tracker.domain

import android.content.Context
import com.myfit.tracker.data.db.Experience
import com.myfit.tracker.data.db.Sex
import com.myfit.tracker.data.db.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * R36 onboarding answers that aren't part of UserProfile (main goal, the follow-up answer, focus areas, body shape,
 * pace) + the "personalised plan" numbers built from them. Stored on the phone only.
 */
object PlanProfile {
    const val LOSE = "lose"; const val BUILD = "build"; const val FIT = "fit"; const val GAIN = "gain"
    val FOCUS = listOf("Back", "Chest", "Arms", "Abs", "Glutes", "Legs")

    data class Extras(
        val goal: String? = null,            // LOSE / BUILD / FIT / GAIN
        val followUp: String? = null,        // tried-before result or muscle-building goal
        val focus: Set<String> = emptySet(), // subset of FOCUS ("Full body" = all)
        val bodyLevel: Int = 3,              // 1 (very lean) … 6 (obese)
        val pace: Int = 1,                   // 0 slow · 1 steady · 2 fast
    )

    private fun sp(c: Context) = c.getSharedPreferences("plan_profile", Context.MODE_PRIVATE)

    fun load(c: Context): Extras = runCatching {
        val o = JSONObject(sp(c).getString("x", null) ?: return Extras())
        val f = o.optJSONArray("focus") ?: JSONArray()
        Extras(o.optString("goal").ifBlank { null }, o.optString("follow").ifBlank { null },
            (0 until f.length()).map { f.getString(it) }.toSet(), o.optInt("body", 3), o.optInt("pace", 1))
    }.getOrDefault(Extras())

    fun save(c: Context, e: Extras) {
        val o = JSONObject().put("goal", e.goal ?: "").put("follow", e.followUp ?: "").put("focus", JSONArray(e.focus.toList()))
            .put("body", e.bodyLevel).put("pace", e.pace)
        sp(c).edit().putString("x", o.toString()).apply()
    }

    /** Body-fat range shown on the body-shape slider, by level (men / women differ). */
    fun bodyFatLabel(level: Int, female: Boolean): String {
        val m = listOf("<12%", "12–16%", "17–21%", "22–27%", "28–35%", ">35%")
        val f = listOf("<18%", "18–23%", "24–29%", "30–35%", "36–42%", ">42%")
        return (if (female) f else m)[(level - 1).coerceIn(0, 5)]
    }
    fun bodyLevelName(level: Int) = listOf("Very lean", "Athletic", "Average", "Soft", "Overweight", "Obese")[(level - 1).coerceIn(0, 5)]

    /** kg per week for the prediction (always a gentle, commonly recommended range — labelled as an estimate). */
    fun kgPerWeek(losing: Boolean, pace: Int): Double = if (losing) listOf(0.25, 0.5, 0.75)[pace.coerceIn(0, 2)] else listOf(0.15, 0.25, 0.4)[pace.coerceIn(0, 2)]

    /** Date the target weight would be reached at that pace, or null if there's nothing to change. */
    fun predict(fromKg: Double, toKg: Double?, pace: Int, today: LocalDate = LocalDate.now()): LocalDate? {
        if (toKg == null) return null
        val diff = toKg - fromKg
        if (kotlin.math.abs(diff) < 0.3) return null
        val weeks = kotlin.math.abs(diff) / kgPerWeek(diff < 0, pace)
        return today.plusDays(kotlin.math.ceil(weeks * 7).toLong())
    }

    fun bmi(kg: Double, cm: Double): Double = if (cm <= 0) 0.0 else kg / ((cm / 100) * (cm / 100))
    fun bmiClass(b: Double) = when { b < 18.5 -> "Underweight"; b < 25 -> "Healthy"; b < 30 -> "Overweight"; else -> "Obese" }

    /** Everything the "Your plan is ready" card shows. */
    data class Summary(
        val heightCm: Double, val weightKg: Double, val age: Int, val bmi: Double, val bmiClass: String,
        val goalLabel: String, val targetShape: String, val ability: String,
        val workouts: Int, val weeks: Int, val perWeek: Int, val intensity: Int, // 1..3
        val anaerobic: Int, val aerobic: Int, val focus: List<String>, val female: Boolean,
    )

    fun summary(p: UserProfile, weightKg: Double, e: Extras): Summary {
        val perWeek = Integer.bitCount(p.workoutDaysMask).coerceIn(1, 7)
        val weeks = when (e.goal) { LOSE, GAIN -> 6; BUILD -> 8; else -> 4 }
        val intensity = when (p.experience) { Experience.BEGINNER -> 1; Experience.ADVANCED -> 3; else -> 2 }
        val (an, ae) = when (e.goal) { LOSE -> 40 to 60; BUILD -> 70 to 30; GAIN -> 75 to 25; else -> 55 to 45 }
        val target = when (e.goal) { LOSE -> if (e.bodyLevel >= 5) "Average" else "Athletic"; BUILD -> "Muscular"; GAIN -> "Fuller, stronger"; else -> "Fit & toned" }
        val b = bmi(weightKg, p.heightCm)
        return Summary(
            p.heightCm, weightKg, p.age, b, bmiClass(b), goalLabel(e.goal), target,
            when (p.experience) { Experience.BEGINNER -> "Beginner"; Experience.ADVANCED -> "Advanced"; else -> "Intermediate" },
            perWeek * weeks, weeks, perWeek, intensity, an, ae,
            if (e.focus.isEmpty() || e.focus.size == FOCUS.size) listOf("Full body") else FOCUS.filter { it in e.focus },
            p.sex == Sex.FEMALE,
        )
    }

    fun goalLabel(g: String?) = when (g) { LOSE -> "Lose weight"; BUILD -> "Build muscle"; GAIN -> "Gain weight"; FIT -> "Get fit & healthy"; else -> "Get fit & healthy" }

    /** The profile's goal list (used by the rest of the app) for a main goal. */
    fun legacyGoals(g: String?): Set<String> = when (g) {
        LOSE -> setOf("Lose Fat", "General Health"); BUILD -> setOf("Build Muscle", "Gain Strength")
        GAIN -> setOf("Build Muscle", "Maintain Shape"); else -> setOf("General Health", "Improve Endurance")
    }
}
