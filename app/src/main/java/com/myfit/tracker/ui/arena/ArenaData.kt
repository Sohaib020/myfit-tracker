package com.myfit.tracker.ui.arena

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/**
 * Arena cast — original 3D characters rendered from Pip's plush rig (assets/arena/<id>.webp + clips).
 * [unlock] = Arena level at which the character can be picked as your partner.
 */
enum class Mascot(val id: String, val label: String, val tagline: String, val color: Color, val accent: Color, val unlock: Int) {
    PIP("pip", "Pip", "Your buddy — never misses a day", Color(0xFFA9E9CF), Color(0xFF1C4157), 1),
    MOTU("motu", "Motu the Panda", "Slow and steady wins", Color(0xFFF4F4F4), Color(0xFFE2445C), 1),
    KAMI("kami", "Kami the Camel", "Endurance for the long haul", Color(0xFFE8C07D), Color(0xFFE0662B), 3),
    CHAKOR("chakor", "Chakor the Partridge", "Pakistan's national bird — loyal to the end", Color(0xFFC9B7A0), Color(0xFFC8102E), 4),
    TAJ("taj", "Taj the Markhor", "King of the mountains", Color(0xFFC9A27A), Color(0xFF0E8F4A), 6),
    KHARGOSH("khargosh", "Khargosh the Hare", "Quick feet, desert heart", Color(0xFFD6B98C), Color(0xFFE84393), 8),
    ZARA("zara", "Zara the Snow Leopard", "Sprints, climbs, never gives up", Color(0xFFE6E8EC), Color(0xFF2E6FD8), 10),
    BHALU("bhalu", "Bhalu the Brown Bear", "Big strength, bigger hugs", Color(0xFF8A5A3C), Color(0xFFF28C28), 12),
    LOMRI("lomri", "Lomri the Red Fox", "Clever pacing wins races", Color(0xFFE2752C), Color(0xFF2C7BE5), 14),
    SHAHEEN("shaheen", "Shaheen the Falcon", "Fast and focused", Color(0xFF8FA3B8), Color(0xFFF2B42E), 15),
    NEVLA("nevla", "Nevla the Mongoose", "Fearless and quick", Color(0xFFA89B85), Color(0xFFC0392B), 18),
    BULHAN("bulhan", "Bulhan the Indus Dolphin", "Swims the Indus, rare and mighty", Color(0xFFA7B4C2), Color(0xFF00A3A3), 20),
    KALA("kala", "Kala the Blackbuck", "Leaps over every limit", Color(0xFF4A3226), Color(0xFFF2B42E), 22),
    ULLU("ullu", "Ullu the Owl", "Wise rest, wise training", Color(0xFF9C7A55), Color(0xFF34495E), 25),
    SAKEEN("sakeen", "Sakeen the Ibex", "Born on the cliffs of Karakoram", Color(0xFFB59870), Color(0xFF6A4BC4), 28),
    BHOORI("bhoori", "Bhoori the Buffalo", "Power of the Punjab plains", Color(0xFF4A4E57), Color(0xFFE94B3C), 32),
    SEHI("sehi", "Sehi the Porcupine", "Sharp focus, soft heart", Color(0xFF7A6250), Color(0xFF16A085), 36),
    GOGI("gogi", "Gogi the Gharial", "Patient hunter of the river", Color(0xFF6B8F4E), Color(0xFFD35400), 40),
    MONAL("monal", "Monal the Pheasant", "Shines brightest at altitude", Color(0xFF2E8B7A), Color(0xFF8E44AD), 45),
    MOR("mor", "Mor the Peacock", "Shows off every PR", Color(0xFF1F6FD1), Color(0xFF2DBE60), 50),
    YAKU("yaku", "Yaku the Yak", "Thrives where others stop", Color(0xFF4E3B30), Color(0xFF1ABC9C), 55),
}

enum class ArenaMetric(val label: String, val unit: String) { STEPS("Steps", "steps"), ACTIVE("Active minutes", "min"), DISTANCE("Distance", "km"), WORKOUTS("Workouts", "workouts"), ACTIVE_DAYS("Active days", "days") }

enum class Period { WEEK, MONTH }

/** Checkpoints at these fractions of the goal; stars for reaching each. Total 7 stars per challenge (+2 for finishing early). */
val CHECKPOINTS = listOf(0.25 to 1, 0.5 to 1, 0.75 to 2, 1.0 to 3)
const val EARLY_BONUS = 2

data class ArenaChallenge(
    val id: String, val title: String, val blurb: String, val period: Period, val metric: ArenaMetric, val goal: Double,
    val mascot: Mascot, val from: LocalDate, val to: LocalDate, val scene: Scene,
)

/** Your recent baseline, so goals are a stretch but reachable (≈10 % above what you already do). */
data class Baseline(val steps: Double, val activeMin: Double, val distKm: Double, val workoutsPerWeek: Double)

fun baseline(days: List<Day>, today: LocalDate): Baseline {
    val recent = days.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(28)) && (it.steps > 0 || it.activeMin > 0) }
    if (recent.size < 3) return Baseline(5000.0, 20.0, 3.5, 2.0)
    return Baseline(
        recent.map { it.steps }.average().coerceAtLeast(2500.0),
        recent.map { it.activeMin }.average().coerceAtLeast(10.0),
        (recent.map { it.distanceM }.average() / 1000.0).coerceAtLeast(1.8),
        (recent.sumOf { it.workouts } / (recent.size / 7.0)).coerceAtLeast(1.0),
    )
}

private fun nice(v: Double, step: Double) = (Math.round(v / step) * step).coerceAtLeast(step)

/** Weekly & monthly challenges for the period containing [today], scaled to your baseline. Same ids all period long. */
fun currentChallenges(today: LocalDate = Clock.today(), b: Baseline): List<ArenaChallenge> {
    val ws = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); val we = ws.plusDays(6)
    val ms = today.withDayOfMonth(1); val me = today.with(TemporalAdjusters.lastDayOfMonth())
    val mdays = me.dayOfMonth
    val wk = "w$ws"; val mo = "m$ms"
    val wSteps = nice((b.steps * 7 * 1.1).coerceIn(17_500.0, 90_000.0), 2500.0)
    val wActive = nice((b.activeMin * 7 * 1.15).coerceIn(60.0, 300.0), 10.0)
    val dayBar = nice((b.steps * 0.85).coerceIn(3000.0, 12_000.0), 500.0)
    val mSteps = nice((b.steps * mdays * 1.05).coerceIn(75_000.0, 400_000.0), 5000.0)
    val mDist = nice((b.distKm * mdays * 1.05).coerceIn(25.0, 250.0), 5.0)
    val mWork = nice((b.workoutsPerWeek * mdays / 7.0 * 1.1).coerceIn(4.0, 24.0), 1.0)
    return listOf(
        ArenaChallenge("$wk-steps", "${fmtK(wSteps)} Step Week", "Zara is sprinting to ${fmtK(wSteps)} steps by Sunday. Keep up!", Period.WEEK, ArenaMetric.STEPS, wSteps, Mascot.ZARA, ws, we, Scene.MARGALLA),
        ArenaChallenge("$wk-active", "Get Moving: ${wActive.toInt()} min", "Shaheen's racing for ${wActive.toInt()} active minutes — anything that gets your heart up.", Period.WEEK, ArenaMetric.ACTIVE, wActive, Mascot.SHAHEEN, ws, we, Scene.CLIFTON),
        ArenaChallenge("$wk-days", "Never Miss a Day", "Hit ${fmtK(dayBar)} steps on 4 days this week with Pip.", Period.WEEK, ArenaMetric.ACTIVE_DAYS, 4.0, Mascot.PIP, ws, we, Scene.SHALIMAR),
        ArenaChallenge("$mo-steps", "${fmtK(mSteps)} Month", "Kami's long haul: ${fmtK(mSteps)} steps this month.", Period.MONTH, ArenaMetric.STEPS, mSteps, Mascot.KAMI, ms, me, Scene.DESERT),
        ArenaChallenge("$mo-dist", "Mountain Trek: ${mDist.toInt()} km", "Taj climbs ${mDist.toInt()} km this month — walking, running and cycling all count.", Period.MONTH, ArenaMetric.DISTANCE, mDist, Mascot.TAJ, ms, me, Scene.K2),
        ArenaChallenge("$mo-work", "${mWork.toInt()} Workouts", "Motu wants ${mWork.toInt()} workouts this month. Slow and steady!", Period.MONTH, ArenaMetric.WORKOUTS, mWork, Mascot.MOTU, ms, me, Scene.CITY),
    ).also { dayThreshold = dayBar }
}

/** Steps that make a day "active" for the Never-Miss-a-Day challenge (set with the current challenges). */
var dayThreshold: Double = 5000.0

fun fmtK(v: Double): String = if (v >= 1000) (if (v % 1000 == 0.0) "${(v / 1000).toLong()}K" else "%.1fK".format(v / 1000)) else v.toLong().toString()

data class Day(val date: LocalDate, val steps: Long, val activeMin: Long, val distanceM: Double, val workouts: Int)

fun value(days: List<Day>, m: ArenaMetric, from: LocalDate, to: LocalDate): Double {
    val r = days.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
    return when (m) {
        ArenaMetric.STEPS -> r.sumOf { it.steps }.toDouble()
        ArenaMetric.ACTIVE -> r.sumOf { it.activeMin }.toDouble()
        ArenaMetric.DISTANCE -> r.sumOf { it.distanceM } / 1000.0
        ArenaMetric.WORKOUTS -> r.sumOf { it.workouts }.toDouble()
        ArenaMetric.ACTIVE_DAYS -> r.count { it.steps >= dayThreshold }.toDouble()
    }
}

fun dayValue(d: Day, m: ArenaMetric): Double = when (m) {
    ArenaMetric.STEPS -> d.steps.toDouble(); ArenaMetric.ACTIVE -> d.activeMin.toDouble(); ArenaMetric.DISTANCE -> d.distanceM / 1000.0
    ArenaMetric.WORKOUTS -> d.workouts.toDouble(); ArenaMetric.ACTIVE_DAYS -> if (d.steps >= dayThreshold) 1.0 else 0.0
}

/** Device-recorded days (Health Connect), falling back to the local daily cache when HC isn't connected. */
suspend fun loadDays(c: AppContainer, from: LocalDate, to: LocalDate): List<Day> {
    val auto = runCatching { c.healthSync.autoDays(from, to) }.getOrDefault(emptyList())
    if (auto.isNotEmpty()) return auto.map { Day(it.date, it.steps, it.activeMin, it.distanceM, it.workouts) }
    val local = runCatching { c.db.healthDao().daily(Clock.dateKey(from), Clock.dateKey(to)) }.getOrDefault(emptyList())
    return local.map { Day(LocalDate.parse(it.localDate), it.steps ?: 0, 0, it.distanceM ?: ((it.steps ?: 0) * 0.75), 0) }
}

// ------------------------------------------------------------------ journeys

/** Track scenery themes (drawn in code; see Track.kt). */
enum class Scene(val sky: List<Color>, val hills: List<Color>, val ground: Color, val road: Color) {
    SHALIMAR(listOf(Color(0xFF9FE3FF), Color(0xFFE9FBFF)), listOf(Color(0xFF7FCB8A), Color(0xFF4FAF6A)), Color(0xFF64B96F), Color(0xFFE9D7B5)),
    MARGALLA(listOf(Color(0xFF86C9FF), Color(0xFFDFF3FF)), listOf(Color(0xFF6FA88A), Color(0xFF3E8A64)), Color(0xFF4F9C68), Color(0xFFD9C8A4)),
    CLIFTON(listOf(Color(0xFFFFC38A), Color(0xFFFFEBD3)), listOf(Color(0xFF4DB6E8), Color(0xFF2D8CC4)), Color(0xFFF3D9A5), Color(0xFFE6E0D4)),
    DESERT(listOf(Color(0xFFFFD08A), Color(0xFFFFF1D6)), listOf(Color(0xFFF0B46A), Color(0xFFD9914A)), Color(0xFFEFC58A), Color(0xFFC98B55)),
    K2(listOf(Color(0xFF7FB8FF), Color(0xFFE8F2FF)), listOf(Color(0xFFDCE6F2), Color(0xFF9BB0C8)), Color(0xFFEFF4FA), Color(0xFFB9A88F)),
    CITY(listOf(Color(0xFF9AB6FF), Color(0xFFF0E6FF)), listOf(Color(0xFF8E9BC7), Color(0xFF6A78A8)), Color(0xFF8FC48E), Color(0xFF5C6270)),
}

object ArenaPrefs {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("arena_prefs", Context.MODE_PRIVATE)
    fun journey(c: Context): Pair<String, LocalDate>? = p(c).getString("journey", null)?.let { id -> p(c).getString("journey_start", null)?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { id to it } } }
    fun startJourney(c: Context, id: String) = p(c).edit().putString("journey", id).putString("journey_start", Clock.today().toString()).apply()
    fun stopJourney(c: Context) = p(c).edit().remove("journey").remove("journey_start").apply()
    fun finished(c: Context): Set<String> = p(c).getStringSet("journeys_done", emptySet())!!
    fun markFinished(c: Context, id: String) = p(c).edit().putStringSet("journeys_done", finished(c) + id).apply()
    fun partner(c: Context): Mascot = Mascot.entries.firstOrNull { it.id == p(c).getString("partner", "pip") } ?: Mascot.PIP
    fun setPartner(c: Context, m: Mascot) = p(c).edit().putString("partner", m.id).apply()
    fun raced(c: Context): Set<String> = p(c).getStringSet("raced", emptySet())!!
    fun setRaced(c: Context, cid: String, on: Boolean) = p(c).edit().putStringSet("raced", if (on) raced(c) + cid else raced(c) - cid).apply()
    fun seenLevel(c: Context) = p(c).getInt("seen_level", 1)
    fun setSeenLevel(c: Context, v: Int) = p(c).edit().putInt("seen_level", v).apply()
    fun seenStars(c: Context) = p(c).getInt("seen_stars", -1)
    fun setSeenStars(c: Context, v: Int) = p(c).edit().putInt("seen_stars", v).apply()
}

@Suppress("unused") private fun r(v: Double) = v.roundToLong()
