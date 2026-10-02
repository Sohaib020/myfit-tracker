package com.myfit.tracker.domain

import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.TargetType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Rule-based achievements computed ONLY from the user's own records. Nothing is estimated or invented:
 * a badge is earned when the recorded data meets a fixed rule. Earned state (with dates) is kept in
 * filesDir/badges.json so a badge stays earned even if old entries are later deleted.
 *
 * Evaluation is cheap-ish (a few Room queries) and runs only on demand: app open / dashboard resume
 * (throttled) and after logging ([refresh] with force = true). Never continuously.
 */
enum class Tier(val label: String) { BRONZE("Bronze"), SILVER("Silver"), GOLD("Gold") }

enum class BadgeShape { HEX, SHIELD }

/** Icon keys — mapped to Duo icons in the UI so this file stays Compose-free. */
enum class BadgeIcon { WORKOUT, STEPS, WATER, FOOD, SUPPLEMENT, MIND, TROPHY, STAR, FLAG, WEIGHT, CALENDAR, FLAME, FOOTPRINTS }

/** Colour keys — mapped to theme colours in the UI. */
enum class BadgeHue { ACCENT, STEPS, WATER, PROTEIN, CARBS, FAT, SLEEP, SUCCESS, WARNING }

enum class BadgeGroup(val label: String) { STREAK("Streaks"), MILESTONE("Milestones"), SOCIAL("Arena") }

data class BadgeDef(
    val id: String,
    val title: String,
    val group: BadgeGroup,
    val shape: BadgeShape,
    val icon: BadgeIcon,
    val hue: BadgeHue,
    val tiers: List<Pair<Tier, Long>>,        // ascending thresholds
    val unit: String,                          // "days", "weeks", "workouts", "steps", …
    val rule: String,                          // plain-language rule shown on the detail sheet
    val goal: (Long) -> String,                // "Hit your step goal 7 days in a row"
)

data class BadgeProgress(
    val def: BadgeDef,
    val value: Long,                           // best-ever value of the metric (from data)
    val tier: Tier?,                           // highest tier earned (stored or from data)
    val earnedAt: Map<Tier, Long>,             // when each tier was first earned
    val next: Pair<Tier, Long>?,               // next tier to aim for
) {
    val earned get() = tier != null
    val progress: Float get() {
        val n = next ?: return 1f
        val prevT = def.tiers.lastOrNull { it.first.ordinal < n.first.ordinal }?.second ?: 0L
        val span = (n.second - prevT).coerceAtLeast(1)
        return ((value - prevT).toFloat() / span).coerceIn(0f, 1f)
    }
}

data class StreakNow(val key: String, val label: String, val icon: BadgeIcon, val hue: BadgeHue, val current: Int, val best: Int, val unit: String)

data class BadgeState(
    val items: List<BadgeProgress>,
    val streaks: List<StreakNow>,
    val celebrate: List<BadgeProgress>,        // newly earned, not yet celebrated
    val evaluatedAt: Long,
) {
    val earnedCount get() = items.count { it.earned }
    fun countOf(t: Tier) = items.count { it.tier == t }
}

object BadgeEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _state = MutableStateFlow<BadgeState?>(null)
    val state: StateFlow<BadgeState?> = _state
    @Volatile private var lastRun = 0L
    private const val THROTTLE_MS = 90_000L

    private fun days(n: Long) = if (n == 1L) "1 day" else "$n days"
    private fun weeks(n: Long) = if (n == 1L) "1 week" else "$n weeks"
    private fun fmt(n: Long) = "%,d".format(java.util.Locale.US, n)

    val DEFS: List<BadgeDef> = listOf(
        // ---------------------------------------------------------------- streaks (best ever)
        BadgeDef("streak_workout_weeks", "Weekly Warrior", BadgeGroup.STREAK, BadgeShape.HEX, BadgeIcon.WORKOUT, BadgeHue.ACCENT,
            listOf(Tier.BRONZE to 2L, Tier.SILVER to 4L, Tier.GOLD to 12L), "weeks",
            "Weeks (Mon–Sun) in a row where you finished at least your weekly workout target (or your planned workout days, if no target is set).",
        ) { "Hit your weekly workout target ${weeks(it)} in a row" },
        BadgeDef("streak_steps", "Step Streak", BadgeGroup.STREAK, BadgeShape.HEX, BadgeIcon.STEPS, BadgeHue.STEPS,
            listOf(Tier.BRONZE to 3L, Tier.SILVER to 7L, Tier.GOLD to 30L), "days",
            "Consecutive days where your recorded steps reached that day's step goal.",
        ) { "Reach your step goal ${days(it)} in a row" },
        BadgeDef("streak_water", "Hydration Hero", BadgeGroup.STREAK, BadgeShape.HEX, BadgeIcon.WATER, BadgeHue.WATER,
            listOf(Tier.BRONZE to 3L, Tier.SILVER to 7L, Tier.GOLD to 30L), "days",
            "Consecutive days where the water you logged reached that day's water goal.",
        ) { "Reach your water goal ${days(it)} in a row" },
        BadgeDef("streak_food", "Food Logger", BadgeGroup.STREAK, BadgeShape.HEX, BadgeIcon.FOOD, BadgeHue.PROTEIN,
            listOf(Tier.BRONZE to 3L, Tier.SILVER to 7L, Tier.GOLD to 30L), "days",
            "Consecutive days with at least one food item logged in the food diary.",
        ) { "Log your food ${days(it)} in a row" },
        BadgeDef("streak_supplements", "Supplement Steady", BadgeGroup.STREAK, BadgeShape.HEX, BadgeIcon.SUPPLEMENT, BadgeHue.SUCCESS,
            listOf(Tier.BRONZE to 7L, Tier.SILVER to 30L, Tier.GOLD to 90L), "days",
            "Consecutive days where you ticked off every supplement you were tracking that day.",
        ) { "Take all your supplements ${days(it)} in a row" },
        BadgeDef("streak_mind", "Mindful Days", BadgeGroup.STREAK, BadgeShape.HEX, BadgeIcon.MIND, BadgeHue.SLEEP,
            listOf(Tier.BRONZE to 3L, Tier.SILVER to 7L, Tier.GOLD to 30L), "days",
            "Consecutive days with at least one breathing or meditation session.",
        ) { "Be mindful ${days(it)} in a row" },
        // ---------------------------------------------------------------- milestones
        BadgeDef("first_workout", "First Rep", BadgeGroup.MILESTONE, BadgeShape.SHIELD, BadgeIcon.FLAG, BadgeHue.ACCENT,
            listOf(Tier.BRONZE to 1L), "workouts", "Finish your first workout in MyFit.",
        ) { "Finish your first workout" },
        BadgeDef("workouts", "Iron Habit", BadgeGroup.MILESTONE, BadgeShape.SHIELD, BadgeIcon.WORKOUT, BadgeHue.ACCENT,
            listOf(Tier.BRONZE to 10L, Tier.SILVER to 50L, Tier.GOLD to 100L), "workouts", "Completed workouts recorded in MyFit.",
        ) { "Finish $it workouts" },
        BadgeDef("prs", "Record Breaker", BadgeGroup.MILESTONE, BadgeShape.SHIELD, BadgeIcon.TROPHY, BadgeHue.WARNING,
            listOf(Tier.BRONZE to 1L, Tier.SILVER to 10L, Tier.GOLD to 50L), "PRs",
            "Personal records: a session where you beat your previous best for an exercise (estimated 1RM not counted; first-ever sessions don't count).",
        ) { if (it == 1L) "Set your first PR" else "Set $it PRs" },
        BadgeDef("step_week", "100K Week", BadgeGroup.MILESTONE, BadgeShape.SHIELD, BadgeIcon.FOOTPRINTS, BadgeHue.STEPS,
            listOf(Tier.GOLD to 100_000L), "steps", "Record 100,000 steps or more within one calendar week (Mon–Sun).",
        ) { "Walk ${fmt(it)} steps in one week" },
        BadgeDef("total_steps", "Million Stepper", BadgeGroup.MILESTONE, BadgeShape.SHIELD, BadgeIcon.STEPS, BadgeHue.STEPS,
            listOf(Tier.BRONZE to 100_000L, Tier.SILVER to 500_000L, Tier.GOLD to 1_000_000L), "steps", "Total steps recorded in MyFit, all time.",
        ) { "Walk ${fmt(it)} steps in total" },
        BadgeDef("days_logged", "Consistency", BadgeGroup.MILESTONE, BadgeShape.SHIELD, BadgeIcon.CALENDAR, BadgeHue.CARBS,
            listOf(Tier.BRONZE to 7L, Tier.SILVER to 30L, Tier.GOLD to 100L), "days",
            "Days with anything logged: water, weight, food, a workout, sleep, steps you entered, a mind session or a supplement.",
        ) { "Log something on ${days(it)}" },
        BadgeDef("weight_goal", "Goal Getter", BadgeGroup.MILESTONE, BadgeShape.SHIELD, BadgeIcon.WEIGHT, BadgeHue.FAT,
            listOf(Tier.GOLD to 1L), "goal", "A logged weight reached the target weight set in your profile.",
        ) { "Reach your target weight" },
        // ---------------------------------------------------------------- arena (only if you use it)
        BadgeDef("arena_joined", "Arena Rookie", BadgeGroup.SOCIAL, BadgeShape.HEX, BadgeIcon.STAR, BadgeHue.WATER,
            listOf(Tier.BRONZE to 1L), "challenges", "Join a challenge in the Arena (needs you to be signed in).",
        ) { "Join an Arena challenge" },
        BadgeDef("arena_won", "Arena Champion", BadgeGroup.SOCIAL, BadgeShape.HEX, BadgeIcon.TROPHY, BadgeHue.WARNING,
            listOf(Tier.GOLD to 1L), "wins", "Finish first in an Arena challenge that has ended.",
        ) { "Win an Arena challenge" },
    )

    /** Fire-and-forget evaluation. Throttled unless [force]. */
    fun refresh(container: AppContainer, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRun < THROTTLE_MS && _state.value != null) return
        lastRun = now
        scope.launch { runCatching { evaluate(container) } }
    }

    /** Marks the given badges' current tiers as celebrated (the celebration shows once). */
    fun markCelebrated(container: AppContainer, items: List<BadgeProgress>) {
        scope.launch {
            lock.withLock {
                val store = Store.load(container.filesDir)
                items.forEach { p -> p.tier?.let { store.celebrated.add("${p.def.id}:${it.name}") } }
                store.save(container.filesDir)
                _state.value = _state.value?.copy(celebrate = emptyList())
            }
        }
    }

    // ------------------------------------------------------------------ persistence
    private class Store(
        val earned: MutableMap<String, MutableMap<Tier, Long>>,
        val celebrated: MutableSet<String>,
        val arenaChecked: MutableSet<String>,
        var arenaJoined: Boolean,
        var arenaWon: Boolean,
        val isNew: Boolean,
    ) {
        fun save(dir: File) {
            val o = JSONObject()
            val e = JSONObject()
            earned.forEach { (id, m) -> e.put(id, JSONObject().apply { m.forEach { (t, at) -> put(t.name, at) } }) }
            o.put("earned", e)
            o.put("celebrated", JSONArray(celebrated.toList()))
            o.put("arenaChecked", JSONArray(arenaChecked.toList()))
            o.put("arenaJoined", arenaJoined)
            o.put("arenaWon", arenaWon)
            runCatching {
                val tmp = File(dir, "badges.json.tmp")
                tmp.writeText(o.toString())
                tmp.renameTo(File(dir, "badges.json"))
            }
        }

        companion object {
            fun load(dir: File): Store {
                val f = File(dir, "badges.json")
                val o = runCatching { JSONObject(f.readText()) }.getOrNull()
                    ?: return Store(mutableMapOf(), mutableSetOf(), mutableSetOf(), false, false, isNew = true)
                val earned = mutableMapOf<String, MutableMap<Tier, Long>>()
                o.optJSONObject("earned")?.let { e ->
                    e.keys().forEach { id ->
                        val m = mutableMapOf<Tier, Long>()
                        e.optJSONObject(id)?.let { t -> t.keys().forEach { k -> runCatching { m[Tier.valueOf(k)] = t.getLong(k) } } }
                        earned[id] = m
                    }
                }
                fun arr(k: String) = mutableSetOf<String>().apply { o.optJSONArray(k)?.let { a -> for (i in 0 until a.length()) add(a.optString(i)) } }
                return Store(earned, arr("celebrated"), arr("arenaChecked"), o.optBoolean("arenaJoined"), o.optBoolean("arenaWon"), isNew = false)
            }
        }
    }

    // ------------------------------------------------------------------ streak helpers
    /** (current, best) run of consecutive days in [set]. Today counts if present; otherwise the run may end yesterday. */
    fun dayStreak(set: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
        if (set.isEmpty()) return 0 to 0
        var cur = 0
        var d = if (today in set) today else today.minusDays(1)
        while (d in set && cur < 5000) { cur++; d = d.minusDays(1) }
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (x in set.sorted()) {
            run = if (prev != null && x == prev.plusDays(1)) run + 1 else 1
            if (run > best) best = run
            prev = x
        }
        return cur to maxOf(best, cur)
    }

    /** Same, over Monday-dated weeks. The current week counts only once it qualifies. */
    fun weekStreak(weeks: Set<LocalDate>, thisWeek: LocalDate): Pair<Int, Int> {
        if (weeks.isEmpty()) return 0 to 0
        var cur = 0
        var w = if (thisWeek in weeks) thisWeek else thisWeek.minusWeeks(1)
        while (w in weeks && cur < 1000) { cur++; w = w.minusWeeks(1) }
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (x in weeks.sorted()) {
            run = if (prev != null && x == prev.plusWeeks(1)) run + 1 else 1
            if (run > best) best = run
            prev = x
        }
        return cur to maxOf(best, cur)
    }

    private fun monday(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // ------------------------------------------------------------------ evaluation
    suspend fun evaluate(c: AppContainer): BadgeState = lock.withLock {
        val today = Clock.today()
        val from = today.minusDays(1100)
        val fk = Clock.dateKey(from); val tk = Clock.dateKey(today)
        val db = c.db
        val targets = runCatching { c.profileRepo.targets.first() }.getOrDefault(emptyList())
        val profile = runCatching { c.profileRepo.profile.first() }.getOrNull()

        // workouts
        val workouts = runCatching { c.workoutRepo.completedRange(fk, tk).first() }.getOrDefault(emptyList())
        val workoutDates = workouts.mapNotNull { runCatching { Clock.parse(it.localDate) }.getOrNull() }
        val weekCounts = workoutDates.groupingBy { monday(it) }.eachCount()
        val plannedDays = profile?.workoutDaysMask?.let { Integer.bitCount(it and 0x7F) }?.takeIf { it > 0 }
        val okWeeks = weekCounts.filter { (wk, n) ->
            val target = Targets.on(targets, TargetType.WEEKLY_WORKOUTS, wk)?.toInt()?.takeIf { it > 0 } ?: plannedDays ?: 1
            n >= target
        }.keys

        // steps (same source priority as the dashboard: Health Connect → manual → phone sensor; never summed)
        val hcDaily = runCatching { c.healthRepo.dailyRange(from, today).first() }.getOrDefault(emptyList()).associateBy { Clock.parse(it.localDate) }
        val manualSteps = runCatching { c.logRepo.activityRange(from, today).first() }.getOrDefault(emptyList()).filter { it.steps != null }
            .groupBy { Clock.parse(it.localDate) }
            .mapValues { (_, l) -> StepsCalc.dayTotal(l.map { StepsCalc.Entry(it.steps!!, it.isDayTotal, it.loggedAt, it.id) })?.toLong() }
        val phone = runCatching { c.healthRepo.phoneDaily(from).first() }.getOrDefault(emptyMap()).mapKeys { Clock.parse(it.key) }
        val steps: Map<LocalDate, Long> = (hcDaily.keys + manualSteps.keys + phone.keys).mapNotNull { d ->
            StepsSource.pick(hcDaily[d]?.steps, manualSteps[d], phone[d])?.let { d to it.steps }
        }.toMap()
        val stepGoalDays = steps.filter { (d, v) -> Targets.on(targets, TargetType.STEPS, d)?.let { it > 0 && v >= it } == true }.keys
        val totalSteps = steps.values.sum()
        val bestWeekSteps = steps.entries.groupBy({ monday(it.key) }, { it.value }).values.maxOfOrNull { it.sum() } ?: 0L

        // water
        val water = runCatching { c.logRepo.waterRange(from, today).first() }.getOrDefault(emptyList())
            .groupBy({ Clock.parse(it.localDate) }, { it.amountMl }).mapValues { it.value.sum() }
        val waterGoalDays = water.filter { (d, ml) -> Targets.on(targets, TargetType.WATER_ML, d)?.let { it > 0 && ml >= it } == true }.keys

        // food
        val foodDays = runCatching { db.nutritionDao().dailyTotals(fk, tk).first() }.getOrDefault(emptyList())
            .mapNotNull { runCatching { Clock.parse(it.date) }.getOrNull() }.toSet()

        // supplements: a day counts when every supplement that existed (and wasn't archived) that day was taken
        val supps = runCatching { db.supplementDao().observeAllRaw().first() }.getOrDefault(emptyList())
        val suppLogs = runCatching { db.supplementLogDao().observeSince(fk).first() }.getOrDefault(emptyList()).filter { it.taken }
        val takenBy = suppLogs.groupBy({ Clock.parse(it.localDate) }, { it.supplementId }).mapValues { it.value.toSet() }
        val suppDays = takenBy.filter { (d, ids) ->
            val due = supps.filter { s ->
                !Clock.localDateOf(s.createdAt).isAfter(d) && (s.archivedAt == null || Clock.localDateOf(s.archivedAt).isAfter(d))
            }.map { it.id }
            due.isNotEmpty() && ids.containsAll(due)
        }.keys

        // mind
        val mindDays = runCatching { db.mindDao().observeDays().first() }.getOrDefault(emptyList())
            .mapNotNull { runCatching { Clock.parse(it) }.getOrNull() }.toSet()

        // PRs (non-estimate records; one per exercise per session)
        val prCount = runCatching {
            val history = c.workoutRepo.allHistory().first()
            val types = db.exerciseDao().observeAll().first().associate { it.id to it.measurementType }
            history.groupBy { it.exerciseId }.flatMap { (id, rows) ->
                types[id]?.let { m -> Records.events(id, m, rows) } ?: emptyList()
            }.filter { !it.isEstimate }.map { it.exerciseId to it.workoutId }.toSet().size
        }.getOrDefault(0)

        // weight goal
        val weights = runCatching { c.logRepo.weightsAll().first() }.getOrDefault(emptyList())
        val weightGoal = profile?.targetWeightKg?.let { target ->
            val start = profile.startWeightKg
            val after = weights.filter { it.loggedAt >= profile.createdAt }
            when {
                target < start -> after.any { it.weightKg <= target }
                target > start -> after.any { it.weightKg >= target }
                else -> false
            }
        } ?: false

        // days with anything logged
        val sleepDays = runCatching { c.logRepo.sleepRange(from, today).first() }.getOrDefault(emptyList()).map { Clock.parse(it.localDate) }
        val loggedDays = HashSet<LocalDate>().apply {
            addAll(water.keys); addAll(foodDays); addAll(workoutDates); addAll(mindDays); addAll(takenBy.keys); addAll(sleepDays)
            addAll(manualSteps.keys); weights.forEach { runCatching { add(Clock.parse(it.localDate)) } }
        }.count { !it.isAfter(today) }.toLong()

        val store = Store.load(c.filesDir)

        // arena (network; only if signed in, with a short timeout; results cached in the store)
        runCatching {
            if (!(store.arenaJoined && store.arenaWon) && c.social.user.value != null) {
                withTimeoutOrNull(6000) {
                    val chs = c.social.myChallenges()
                    if (chs.isNotEmpty()) store.arenaJoined = true
                    if (!store.arenaWon) {
                        for (ch in chs.filter { it.end < tk && it.id !in store.arenaChecked }.take(5)) {
                            val rows = c.social.standings(ch)
                            val top = rows.firstOrNull()
                            if (top != null && top.me && top.value > 0 && rows.count { it.value == top.value } == 1) store.arenaWon = true
                            store.arenaChecked.add(ch.id)
                        }
                    }
                }
            }
        }

        val sw = weekStreak(okWeeks, monday(today))
        val ss = dayStreak(stepGoalDays, today)
        val sWater = dayStreak(waterGoalDays, today)
        val sf = dayStreak(foodDays, today)
        val sSupp = dayStreak(suppDays, today)
        val sm = dayStreak(mindDays, today)

        val values: Map<String, Long> = mapOf(
            "streak_workout_weeks" to sw.second.toLong(),
            "streak_steps" to ss.second.toLong(),
            "streak_water" to sWater.second.toLong(),
            "streak_food" to sf.second.toLong(),
            "streak_supplements" to sSupp.second.toLong(),
            "streak_mind" to sm.second.toLong(),
            "first_workout" to workouts.size.toLong(),
            "workouts" to workouts.size.toLong(),
            "prs" to prCount.toLong(),
            "step_week" to bestWeekSteps,
            "total_steps" to totalSteps,
            "days_logged" to loggedDays,
            "weight_goal" to if (weightGoal) 1L else 0L,
            "arena_joined" to if (store.arenaJoined) 1L else 0L,
            "arena_won" to if (store.arenaWon) 1L else 0L,
        )

        val now = System.currentTimeMillis()
        val newly = ArrayList<BadgeProgress>()
        val items = DEFS.map { def ->
            val v = values[def.id] ?: 0L
            val stored = store.earned.getOrPut(def.id) { mutableMapOf() }
            def.tiers.filter { v >= it.second }.forEach { (t, _) -> if (t !in stored) stored[t] = now }
            val tier = stored.keys.maxByOrNull { it.ordinal }
            val next = def.tiers.firstOrNull { tier == null || it.first.ordinal > tier.ordinal }
            BadgeProgress(def, v, tier, stored.toMap(), next).also { p ->
                if (tier != null && "${def.id}:${tier.name}" !in store.celebrated) newly += p
            }
        }

        // First run: don't throw a parade for history — celebrate at most the 3 best, mark the rest seen.
        val celebrate = if (store.isNew) {
            val pick = newly.sortedWith(compareByDescending<BadgeProgress> { it.tier!!.ordinal }.thenBy { DEFS.indexOf(it.def) }).take(3)
            newly.filter { it !in pick }.forEach { p -> store.celebrated.add("${p.def.id}:${p.tier!!.name}") }
            pick
        } else newly
        store.save(c.filesDir)

        val streaks = listOf(
            StreakNow("workout", "Workout weeks", BadgeIcon.WORKOUT, BadgeHue.ACCENT, sw.first, sw.second, "wk"),
            StreakNow("steps", "Step goal", BadgeIcon.STEPS, BadgeHue.STEPS, ss.first, ss.second, "d"),
            StreakNow("water", "Water goal", BadgeIcon.WATER, BadgeHue.WATER, sWater.first, sWater.second, "d"),
            StreakNow("food", "Food logged", BadgeIcon.FOOD, BadgeHue.PROTEIN, sf.first, sf.second, "d"),
            StreakNow("supps", "Supplements", BadgeIcon.SUPPLEMENT, BadgeHue.SUCCESS, sSupp.first, sSupp.second, "d"),
            StreakNow("mind", "Mindful", BadgeIcon.MIND, BadgeHue.SLEEP, sm.first, sm.second, "d"),
        )
        val st = BadgeState(items, streaks, celebrate, now)
        _state.value = st
        lastRun = now
        st
    }
}
