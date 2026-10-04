package com.myfit.tracker.ui.arena

import android.content.Context
import com.myfit.tracker.domain.Clock
import java.time.LocalDate

/** Arena tiers (every few levels), each fronted by a cast member. */
enum class Tier(val label: String, val from: Int, val mascot: Mascot) {
    ROOKIE("Rookie", 1, Mascot.MOTU), WALKER("Walker", 4, Mascot.CHAKOR), JOGGER("Jogger", 8, Mascot.KHARGOSH),
    EXPLORER("Explorer", 12, Mascot.KAMI), TREKKER("Trekker", 16, Mascot.TAJ), CLIMBER("Climber", 20, Mascot.SAKEEN),
    RUNNER("Runner", 25, Mascot.LOMRI), SPRINTER("Sprinter", 30, Mascot.ZARA), CHAMPION("Champion", 36, Mascot.BHOORI),
    FALCON("Falcon", 42, Mascot.SHAHEEN), HERO("Hero", 48, Mascot.MOR), LEGEND("Legend", 55, Mascot.YAKU),
}

data class Level(val n: Int, val tier: Tier, val into: Int, val span: Int, val total: Int) {
    val frac: Float get() = if (span <= 0) 1f else into.toFloat() / span
}

data class Award(val id: String, val stars: Int, val title: String, val date: LocalDate)

data class Badge(val id: String, val title: String, val desc: String, val emoji: String, val earned: Boolean)

const val MAX_LEVEL = 60

/**
 * Stars → levels. Each star event is written once to a ledger (id-keyed), so stars are never double-counted
 * and are never lost if data later changes. Goals are frozen per period the first time they're shown.
 */
object ArenaProgress {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("arena_progress", Context.MODE_PRIVATE)

    /** Stars needed to go from level n to n+1 (gentle curve: 10, 12, 14 …; level 60 ≈ 4,000 stars). */
    fun stepFor(n: Int) = 10 + 2 * (n - 1)
    fun startOf(n: Int): Int = (1 until n).sumOf { stepFor(it) }

    fun level(total: Int): Level {
        var n = 1
        while (n < MAX_LEVEL && total >= startOf(n + 1)) n++
        val tier = Tier.entries.last { n >= it.from }
        val s = startOf(n)
        return if (n >= MAX_LEVEL) Level(n, tier, 1, 1, total) else Level(n, tier, total - s, stepFor(n), total)
    }

    fun ledger(c: Context): List<Award> = p(c).getStringSet("ledger", emptySet())!!.mapNotNull { e ->
        val parts = e.split('|'); if (parts.size < 4) return@mapNotNull null
        runCatching { Award(parts[0], parts[1].toInt(), parts[3], LocalDate.parse(parts[2])) }.getOrNull()
    }.sortedByDescending { it.date }

    fun total(c: Context) = ledger(c).sumOf { it.stars }

    private fun add(c: Context, a: List<Award>): List<Award> {
        if (a.isEmpty()) return a
        val cur = p(c).getStringSet("ledger", emptySet())!!
        val have = cur.map { it.substringBefore('|') }.toHashSet()
        val fresh = a.filter { it.id !in have }.distinctBy { it.id }
        if (fresh.isNotEmpty()) p(c).edit().putStringSet("ledger", cur + fresh.map { "${it.id}|${it.stars}|${it.date}|${it.title.replace('|', '/')}" }).apply()
        return fresh
    }

    /** Award a one-off event (e.g. a duel win). Returns true if it was new. */
    fun grant(c: Context, id: String, stars: Int, title: String) = add(c, listOf(Award(id, stars, title, Clock.today()))).isNotEmpty()

    /** Freeze each challenge's goal (and the active-day bar) for its whole period. */
    fun frozen(c: Context, list: List<ArenaChallenge>): List<ArenaChallenge> {
        val e = p(c).edit(); var dirty = false
        val out = list.map { ch ->
            val k = "goal:${ch.id}"
            val g = p(c).getFloat(k, -1f)
            if (g < 0) { e.putFloat(k, ch.goal.toFloat()); dirty = true; ch } else ch.copy(goal = g.toDouble())
        }
        list.firstOrNull { it.metric == ArenaMetric.ACTIVE_DAYS }?.let { d ->
            val k = "bar:${d.id}"; val bar = p(c).getFloat(k, -1f)
            if (bar < 0) { e.putFloat(k, dayThreshold.toFloat()); dirty = true } else dayThreshold = bar.toDouble()
        }
        if (dirty) e.apply()
        return out
    }

    /** Previous period's challenges, if their goals were frozen while they ran (so we can still award them). */
    private fun previous(c: Context, today: LocalDate, b: Baseline): List<ArenaChallenge> {
        val prevW = currentChallenges(today.minusWeeks(1), b).filter { it.period == Period.WEEK }
        val prevM = currentChallenges(today.withDayOfMonth(1).minusDays(1), b).filter { it.period == Period.MONTH }
        return (prevW + prevM).mapNotNull { ch -> p(c).getFloat("goal:${ch.id}", -1f).takeIf { it > 0 }?.let { ch.copy(goal = it.toDouble()) } }
    }

    /** Checkpoints reached for a challenge, as (index, fraction, stars). */
    fun checkpoints(ch: ArenaChallenge, v: Double) = CHECKPOINTS.mapIndexed { i, (f, s) -> Triple(i, f, s) }.filter { v >= ch.goal * it.second - 1e-9 }

    /** Day on which the goal was first met, or null. */
    fun completedOn(ch: ArenaChallenge, days: List<Day>): LocalDate? {
        var acc = 0.0
        days.filter { !it.date.isBefore(ch.from) && !it.date.isAfter(ch.to) }.sortedBy { it.date }.forEach { d ->
            acc += dayValue(d, ch.metric); if (acc >= ch.goal) return d.date
        }
        return null
    }

    /** Scan recent data and record any stars earned. Returns the new awards (for the celebration). */
    fun sync(c: Context, days: List<Day>, today: LocalDate, current: List<ArenaChallenge>, b: Baseline): List<Award> {
        val a = ArrayList<Award>()
        // daily step stars: 3K · 6K · 10K
        days.filter { !it.date.isAfter(today) }.forEach { d ->
            listOf(3000L to "3K steps", 6000L to "6K steps", 10_000L to "10K steps").forEachIndexed { i, (t, label) ->
                if (d.steps >= t) a += Award("d:${d.date}:$i", 1, "$label on ${d.date.dayOfMonth} ${d.date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}", d.date)
            }
        }
        // challenge checkpoints (+ early-finish bonus)
        val keepBar = dayThreshold
        (current + previous(c, today, b)).forEach { ch ->
            if (ch.metric == ArenaMetric.ACTIVE_DAYS) p(c).getFloat("bar:${ch.id}", -1f).takeIf { it > 0 }?.let { dayThreshold = it.toDouble() }
            val v = value(days, ch.metric, ch.from, minOf(ch.to, today))
            checkpoints(ch, v).forEach { (i, f, s) ->
                a += Award("${ch.id}:cp$i", s, if (f >= 1.0) "Completed ${ch.title}" else "${(f * 100).toInt()}% checkpoint · ${ch.title}", minOf(ch.to, today))
            }
            val done = completedOn(ch, days)
            if (done != null && done.isBefore(ch.to)) a += Award("${ch.id}:early", EARLY_BONUS, "Finished early · ${ch.title}", done)
            dayThreshold = keepBar
        }
        // journey stops
        ArenaPrefs.journey(c)?.let { (jid, start) ->
            Journeys.firstOrNull { it.id == jid }?.let { j ->
                val km = days.filter { !it.date.isBefore(start) }.sumOf { it.distanceM } / 1000.0
                j.stops.forEachIndexed { i, (name, at) ->
                    if (i > 0 && km >= at) a += Award("j:$jid:$start:$i", if (i == j.stops.lastIndex) 6 else 2, if (i == j.stops.lastIndex) "Finished ${j.title}" else "Reached $name", today)
                }
            }
        }
        return add(c, a)
    }

    // ------------------------------------------------------------------ badges

    fun badges(c: Context, days: List<Day>): List<Badge> {
        val l = ledger(c); val ids = l.map { it.id }
        val completed = ids.count { it.endsWith(":cp3") }
        val early = ids.count { it.endsWith(":early") }
        val journeys = ArenaPrefs.finished(c)
        val lv = level(l.sumOf { it.stars }).n
        val sorted = days.sortedBy { it.date }
        var run = 0; var best = 0
        sorted.forEach { d -> run = if (d.steps >= 5000) run + 1 else 0; best = maxOf(best, run) }
        val tenK = ids.any { it.startsWith("d:") && it.endsWith(":2") }
        return listOf(
            Badge("first", "First Star", "Earn your first star", "⭐", l.isNotEmpty()),
            Badge("tenk", "10K Club", "Walk 10,000 steps in a day", "👟", tenK),
            Badge("ch1", "Challenger", "Complete a weekly or monthly challenge", "🏅", completed >= 1),
            Badge("ch5", "Unstoppable", "Complete 5 challenges", "🔥", completed >= 5),
            Badge("ch15", "Machine", "Complete 15 challenges", "⚙️", completed >= 15),
            Badge("early", "Early Bird", "Finish a challenge before its last day", "🐦", early >= 1),
            Badge("streak7", "On a Roll", "5,000+ steps 7 days in a row", "📆", best >= 7),
            Badge("streak30", "Iron Will", "5,000+ steps 30 days in a row", "🛡️", best >= 30),
            Badge("journey", "Explorer", "Finish any journey", "🧭", journeys.isNotEmpty()),
            Badge("k2", "Summit", "Reach K2 Base Camp", "🏔️", "k2" in journeys),
            Badge("kkh", "Silk Road", "Walk the Karakoram Highway", "🛣️", "kkh" in journeys),
            Badge("garden", "Green Thumb", "Grow all 10 flowers in Pip's Garden", "🌸", ids.any { it.startsWith("g:garden") }),
            Badge("ghost", "Ghostbuster", "Beat your ghost 5 times", "👻", ids.count { it.startsWith("g:ghost") } >= 5),
            Badge("duel", "Champion", "Win a duel or team battle", "🏆", ids.any { it.startsWith("duel:") }),
            Badge("lv5", "Rising Star", "Reach level 5", "🌟", lv >= 5),
            Badge("lv10", "Elite", "Reach level 10", "💎", lv >= 10),
            Badge("lv20", "Falcon Class", "Reach level 20", "🦅", lv >= 20),
            Badge("lv30", "Tier Master", "Reach level 30", "👑", lv >= 30),
            Badge("lv45", "Elite Falcon", "Reach level 45", "🦚", lv >= 45),
            Badge("lv60", "Living Legend", "Reach level 60", "🏔️", lv >= 60),
            Badge("ch30", "Unbreakable", "Complete 30 challenges", "💪", completed >= 30),
            Badge("early5", "Speed Demon", "Finish 5 challenges early", "⚡", early >= 5),
            Badge("alljourneys", "World Explorer", "Finish every journey", "🌍", Journeys.all { it.id in journeys }),
            Badge("streak100", "Centurion", "5,000+ steps 100 days in a row", "💯", best >= 100),
            Badge("twentyk", "20K Day", "Walk 20,000 steps in a day", "🚀", days.any { it.steps >= 20_000 }),
            Badge("stars1k", "Star Collector", "Collect 1,000 stars", "🌠", l.sumOf { it.stars } >= 1000),
            Badge("cast10", "Zookeeper", "Unlock 10 characters", "🐾", Mascot.entries.count { lv >= it.unlock } >= 10),
            Badge("castall", "Full Cast", "Unlock every character", "🦁", Mascot.entries.all { lv >= it.unlock }),
            Badge("race1", "Photo Finish", "Be first among friends to finish a challenge", "📸", ids.any { it.startsWith("race:") }),
            Badge("race5", "Pack Leader", "Win 5 friend races", "🐺", ids.count { it.startsWith("race:") } >= 5),
            Badge("duel5", "Undefeated", "Win 5 duels or battles", "🥇", ids.count { it.startsWith("duel:") } >= 5),
        )
    }
}
