package com.myfit.tracker.ui.game

import android.content.Context
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.arena.loadDays
import com.myfit.tracker.ui.pip.BuddyChar
import com.myfit.tracker.ui.pip.Champion
import com.myfit.tracker.ui.pip.Champions
import com.myfit.tracker.ui.pip.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * R25 Game Mode — the champions' home. Pip stays the AI buddy; with Game Mode on, your champion is your hero:
 * a Home hero card powered by today's goals, stats built from your real last 7 days, looks earned with stars +
 * milestones, and (next round) Arena battles. Off = a clean tracker; turning it on plays a short intro.
 */
object GameMode {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("game_mode", Context.MODE_PRIVATE)

    val on = MutableStateFlow(false)
    val champ = MutableStateFlow(BuddyChar(Champion.BLAZE))
    /** Set to play the full-screen "Game Mode on" intro (hosted at the root). */
    val intro = MutableStateFlow(false)
    @Volatile private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        on.value = p(c).getBoolean("on", false)
        val saved = BuddyChar.parse(p(c).getString("champ", "blaze_street"))
        champ.value = saved.champion?.let { if (Champions.owns(c, it, saved.tier)) saved else null } ?: BuddyChar(Champion.BLAZE)
        loaded = true
    }

    fun setOn(c: Context, v: Boolean) {
        load(c)
        p(c).edit().putBoolean("on", v).putBoolean("asked", true).apply()
        on.value = v
        if (v) intro.value = true
    }

    fun asked(c: Context) = p(c).getBoolean("asked", false)
    fun markAsked(c: Context) = p(c).edit().putBoolean("asked", true).apply()

    fun choose(c: Context, b: BuddyChar) {
        p(c).edit().putString("champ", b.id).apply(); champ.value = b
    }

    /** Each champion's sport: training that matches it earns bonus stars (next round) and shows on its card. */
    fun sport(ch: Champion): Pair<Stat, String> = when (ch) {
        Champion.BLAZE, Champion.NOVA, Champion.HOWL, Champion.SABA -> Stat.STAMINA to "running and walking"
        Champion.TITAN, Champion.ONYX, Champion.KODIAK, Champion.TAURUS, Champion.LUNA -> Stat.POWER to "strength training"
        Champion.KAIRO, Champion.ARIA, Champion.REX -> Stat.AGILITY to "boxing, HIIT and mixed training"
        Champion.RAZE, Champion.FROST, Champion.VALOR -> Stat.FOCUS to "showing up every day"
    }
}

enum class Stat(val label: String, val from: String) {
    POWER("Power", "strength sets this week"),
    STAMINA("Stamina", "steps and active minutes"),
    AGILITY("Agility", "active days and workout variety"),
    FOCUS("Focus", "your daily streak"),
}

/** Your champion's stats (0–100), all from the last 7 days of real activity, plus milestone totals. */
data class ChampStats(
    val power: Int, val stamina: Int, val agility: Int, val focus: Int,
    val streak: Int, val totalWorkouts: Int, val activeDays: Int,
) {
    val rating: Int get() = (power + stamina + agility + focus) / 4
    fun of(s: Stat) = when (s) { Stat.POWER -> power; Stat.STAMINA -> stamina; Stat.AGILITY -> agility; Stat.FOCUS -> focus }

    companion object {
        val EMPTY = ChampStats(0, 0, 0, 0, 0, 0, 0)

        suspend fun compute(c: AppContainer): ChampStats = withContext(Dispatchers.IO) {
            val today = Clock.today()
            val days = runCatching { loadDays(c, today.minusDays(120), today) }.getOrDefault(emptyList())
            val week = days.filter { !it.date.isBefore(today.minusDays(6)) }
            val threshold = com.myfit.tracker.ui.arena.dayThreshold
            // workouts this week (sets + variety) and all time (milestones)
            val views = runCatching { c.workoutRepo.recentViews(60).first() }.getOrDefault(emptyList())
            val weekViews = views.filter { !java.time.LocalDate.parse(it.workout.localDate).isBefore(today.minusDays(6)) }
            val sets = weekViews.sumOf { v -> v.exercises.sumOf { it.sets.size } }
            val variety = weekViews.flatMap { v -> v.exercises.map { it.exercise.id } }.distinct().size
            val total = runCatching { c.workoutRepo.completedRange("2000-01-01", Clock.dateKey(today)).first().size }.getOrDefault(0)
            val workoutDays = views.map { it.workout.localDate }.toSet()
            fun active(d: com.myfit.tracker.ui.arena.Day) = d.steps >= threshold || d.activeMin >= 20 || d.date.toString() in workoutDays
            val activeWeek = week.count { active(it) }
            // streak: consecutive active days ending today (or yesterday, so the day isn't "lost" before you move)
            val byDate = days.associateBy { it.date }
            var streak = 0; var d = today
            if (byDate[d]?.let { active(it) } != true) d = d.minusDays(1)
            while (byDate[d]?.let { active(it) } == true || d.toString() in workoutDays) { streak++; d = d.minusDays(1) }
            val avgSteps = if (week.isEmpty()) 0.0 else week.sumOf { it.steps }.toDouble() / 7.0
            val activeMin = week.sumOf { it.activeMin }.toDouble()
            // one day can't carry the week: steps and minutes are capped per day before averaging
            val cappedSteps = week.sumOf { minOf(it.steps, 25_000L) }.toDouble() / 7.0
            ChampStats(
                power = (sets / 60.0 * 100).toInt().coerceIn(0, 100),
                stamina = (minOf(avgSteps, cappedSteps) / 10_000.0 * 70 + minOf(activeMin, 300.0) / 150.0 * 30).toInt().coerceIn(0, 100),
                agility = (activeWeek / 7.0 * 70 + minOf(variety, 15) / 15.0 * 30).toInt().coerceIn(0, 100),
                focus = (streak / 14.0 * 100).toInt().coerceIn(0, 100),
                streak = streak, totalWorkouts = total, activeDays = days.count { active(it) },
            )
        }
    }
}

/** Epic and Legendary looks need a milestone as well as stars, so a look proves effort. */
object Milestones {
    fun need(t: Tier): String? = when (t) {
        Tier.STREET -> null
        Tier.EPIC -> "a 14-day streak or 20 workouts"
        Tier.LEGENDARY -> "a 30-day streak or 60 workouts"
    }
    fun met(t: Tier, s: ChampStats): Boolean = when (t) {
        Tier.STREET -> true
        Tier.EPIC -> s.streak >= 14 || s.totalWorkouts >= 20
        Tier.LEGENDARY -> s.streak >= 30 || s.totalWorkouts >= 60
    }
}
