package com.myfit.tracker.ui.arena

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.health.HealthSync
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Original mascot cast for Arena (all designed for MyFit). */
enum class Mascot(val label: String, val tagline: String, val color: Color, val accent: Color) {
    PIP("Pip", "Your buddy — never misses a day", Color(0xFFA9E9CF), Color(0xFF1C4157)),
    ZARA("Zara the Snow Leopard", "Sprints, climbs, never gives up", Color(0xFFE6E8EC), Color(0xFF3A3F47)),
    TAJ("Taj the Markhor", "King of the mountains", Color(0xFFC9A27A), Color(0xFF5A3D24)),
    KAMI("Kami the Camel", "Endurance for the long haul", Color(0xFFE8C07D), Color(0xFF8A5A2B)),
    SHAHEEN("Shaheen the Falcon", "Fast and focused", Color(0xFF8FA3B8), Color(0xFFF2B33D)),
    MOTU("Motu the Panda", "Slow and steady wins", Color(0xFFF4F4F4), Color(0xFF222222)),
}

enum class ArenaMetric(val label: String, val unit: String) { STEPS("Steps", "steps"), ACTIVE("Active minutes", "min"), DISTANCE("Distance", "km"), WORKOUTS("Workouts", "workouts"), ACTIVE_DAYS("Active days", "days") }

enum class Period { WEEK, MONTH }

data class ArenaChallenge(
    val id: String, val title: String, val blurb: String, val period: Period, val metric: ArenaMetric, val goal: Double,
    val mascot: Mascot, val from: LocalDate, val to: LocalDate,
)

/** The built-in weekly & monthly challenges (same for everyone; progress is your own device-recorded data). */
fun currentChallenges(today: LocalDate = Clock.today()): List<ArenaChallenge> {
    val ws = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); val we = ws.plusDays(6)
    val ms = today.withDayOfMonth(1); val me = today.with(TemporalAdjusters.lastDayOfMonth())
    val wk = "w" + ws; val mo = "m" + ms
    return listOf(
        ArenaChallenge("$wk-steps", "50K Step Week", "Walk 50,000 steps by Sunday night.", Period.WEEK, ArenaMetric.STEPS, 50_000.0, Mascot.ZARA, ws, we),
        ArenaChallenge("$wk-active", "150 Active Minutes", "The WHO weekly target — any movement that raises your heart rate.", Period.WEEK, ArenaMetric.ACTIVE, 150.0, Mascot.SHAHEEN, ws, we),
        ArenaChallenge("$wk-days", "Never Miss a Day", "Hit 6,000+ steps on 5 days this week.", Period.WEEK, ArenaMetric.ACTIVE_DAYS, 5.0, Mascot.PIP, ws, we),
        ArenaChallenge("$mo-steps", "Quarter Million", "250,000 steps this month.", Period.MONTH, ArenaMetric.STEPS, 250_000.0, Mascot.KAMI, ms, me),
        ArenaChallenge("$mo-dist", "Climb K2 (in km)", "Cover 150 km this month — walking, running, cycling.", Period.MONTH, ArenaMetric.DISTANCE, 150.0, Mascot.TAJ, ms, me),
        ArenaChallenge("$mo-work", "12 Workouts", "Log or record 12 workouts this month.", Period.MONTH, ArenaMetric.WORKOUTS, 12.0, Mascot.MOTU, ms, me),
    )
}

data class Day(val date: LocalDate, val steps: Long, val activeMin: Long, val distanceM: Double, val workouts: Int)

fun value(days: List<Day>, m: ArenaMetric, from: LocalDate, to: LocalDate): Double {
    val r = days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
    return when (m) {
        ArenaMetric.STEPS -> r.sumOf { it.steps }.toDouble()
        ArenaMetric.ACTIVE -> r.sumOf { it.activeMin }.toDouble()
        ArenaMetric.DISTANCE -> r.sumOf { it.distanceM } / 1000.0
        ArenaMetric.WORKOUTS -> r.sumOf { it.workouts }.toDouble()
        ArenaMetric.ACTIVE_DAYS -> r.count { it.steps >= 6000 }.toDouble()
    }
}

fun dayValue(d: Day, m: ArenaMetric): Double = when (m) {
    ArenaMetric.STEPS -> d.steps.toDouble(); ArenaMetric.ACTIVE -> d.activeMin.toDouble(); ArenaMetric.DISTANCE -> d.distanceM / 1000.0
    ArenaMetric.WORKOUTS -> d.workouts.toDouble(); ArenaMetric.ACTIVE_DAYS -> if (d.steps >= 6000) 1.0 else 0.0
}

/** Device-recorded days (Health Connect), falling back to the local daily cache when HC isn't connected. */
suspend fun loadDays(c: AppContainer, from: LocalDate, to: LocalDate): List<Day> {
    val auto = runCatching { c.healthSync.autoDays(from, to) }.getOrDefault(emptyList())
    if (auto.isNotEmpty()) return auto.map { Day(it.date, it.steps, it.activeMin, it.distanceM, it.workouts) }
    val local = runCatching { c.db.healthDao().daily(Clock.dateKey(from), Clock.dateKey(to)) }.getOrDefault(emptyList())
    return local.map { Day(LocalDate.parse(it.localDate), it.steps ?: 0, 0, it.distanceM ?: ((it.steps ?: 0) * 0.75), 0) }
}

// ------------------------------------------------------------------ journeys

data class Journey(val id: String, val title: String, val place: String, val km: Double, val mascot: Mascot, val stops: List<Pair<String, Double>>, val path: List<Pair<Float, Float>>)

val Journeys = listOf(
    Journey("shalimar", "Shalimar Gardens Loop", "Lahore", 5.0, Mascot.PIP,
        listOf("Main gate" to 0.0, "Upper terrace" to 1.5, "Fountains" to 3.0, "Back to gate" to 5.0),
        listOf(0.1f to 0.8f, 0.3f to 0.4f, 0.6f to 0.25f, 0.85f to 0.45f, 0.7f to 0.8f, 0.35f to 0.9f)),
    Journey("margalla", "Margalla Trail 3", "Islamabad", 9.0, Mascot.ZARA,
        listOf("Trailhead" to 0.0, "Viewpoint" to 3.0, "Pir Sohawa" to 6.5, "Summit café" to 9.0),
        listOf(0.1f to 0.9f, 0.25f to 0.65f, 0.2f to 0.45f, 0.45f to 0.35f, 0.6f to 0.2f, 0.85f to 0.1f)),
    Journey("seaview", "Clifton to Sea View", "Karachi", 12.0, Mascot.SHAHEEN,
        listOf("Teen Talwar" to 0.0, "Bilawal House" to 3.0, "Do Darya" to 8.0, "Sea View" to 12.0),
        listOf(0.05f to 0.3f, 0.3f to 0.35f, 0.5f to 0.55f, 0.7f to 0.6f, 0.95f to 0.75f)),
    Journey("arafat", "Mina → Arafat → Muzdalifah", "Makkah", 25.0, Mascot.KAMI,
        listOf("Mina" to 0.0, "Masjid Namirah, Arafat" to 14.0, "Muzdalifah" to 21.0, "Back to Mina" to 25.0),
        listOf(0.1f to 0.5f, 0.35f to 0.35f, 0.6f to 0.25f, 0.85f to 0.4f, 0.65f to 0.65f, 0.3f to 0.7f)),
    Journey("k2", "K2 Base Camp Trek", "Gilgit-Baltistan", 90.0, Mascot.TAJ,
        listOf("Askole" to 0.0, "Paiju" to 22.0, "Urdukas" to 45.0, "Concordia" to 70.0, "K2 Base Camp" to 90.0),
        listOf(0.05f to 0.9f, 0.2f to 0.75f, 0.35f to 0.7f, 0.5f to 0.5f, 0.65f to 0.4f, 0.8f to 0.25f, 0.9f to 0.08f)),
    Journey("motorway", "Lahore → Islamabad (M-2)", "Punjab", 375.0, Mascot.MOTU,
        listOf("Lahore" to 0.0, "Sheikhupura" to 35.0, "Bhera" to 160.0, "Kallar Kahar" to 260.0, "Islamabad" to 375.0),
        listOf(0.1f to 0.9f, 0.25f to 0.75f, 0.4f to 0.6f, 0.55f to 0.45f, 0.7f to 0.35f, 0.9f to 0.1f)),
)

object ArenaPrefs {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("arena_prefs", Context.MODE_PRIVATE)
    fun journey(c: Context): Pair<String, LocalDate>? = p(c).getString("journey", null)?.let { id -> p(c).getString("journey_start", null)?.let { id to LocalDate.parse(it) } }
    fun startJourney(c: Context, id: String) = p(c).edit().putString("journey", id).putString("journey_start", Clock.today().toString()).apply()
    fun stopJourney(c: Context) = p(c).edit().remove("journey").remove("journey_start").apply()
    fun finished(c: Context): Set<String> = p(c).getStringSet("journeys_done", emptySet())!!
    fun markFinished(c: Context, id: String) = p(c).edit().putStringSet("journeys_done", finished(c) + id).apply()
    fun gardenBest(c: Context) = p(c).getInt("garden_best", 0)
    fun setGardenBest(c: Context, v: Int) = p(c).edit().putInt("garden_best", v).apply()
}

@Suppress("unused") private val keepHs = HealthSync::class
