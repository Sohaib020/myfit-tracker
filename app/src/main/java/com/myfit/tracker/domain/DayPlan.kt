package com.myfit.tracker.domain

import android.content.Context
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.TargetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * R34 "Plan my day". Everything here is worked out on the phone from what was actually recorded — no network.
 * The only optional online part is a one-line coach tip ([DayPlans.tip]) that is cached per day.
 */
enum class PlanKind { MOVE, FUEL, RECOVER }
enum class PlanMetric { STEPS, WORKOUT, KCAL, PROTEIN, WATER, SLEEP, WEIGH, MIND, NONE }

data class PlanItem(val kind: PlanKind, val metric: PlanMetric, val title: String, val detail: String, val target: Double? = null)

data class DayPlan(val date: String, val headline: String, val why: String, val items: List<PlanItem>, val tip: String? = null)

/** What was recorded on one day. Null = nothing recorded (never treated as zero). */
data class DayStats(
    val date: LocalDate,
    val steps: Long? = null,
    val sleepMin: Double? = null,
    val waterMl: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val workouts: Int = 0,
    val trainMin: Long = 0,
    val weightKg: Double? = null,
    val weighed: Boolean = false,
    val checkedIn: Boolean = false,
)

data class DayTargets(
    val steps: Double, val sleepMin: Double, val waterMl: Double, val kcal: Double?, val protein: Double?, val weeklyWorkouts: Double?,
)

object DayPlans {
    private val _history = MutableStateFlow<Map<String, DayPlan>>(emptyMap())
    /** Saved plans by date key (newest 90 days). */
    val history: StateFlow<Map<String, DayPlan>> = _history
    @Volatile private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "dayplans.json")

    suspend fun load(ctx: Context) = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        loaded = true
        _history.value = runCatching {
            val o = JSONObject(file(ctx).readText())
            o.keys().asSequence().associateWith { k -> fromJson(o.getJSONObject(k)) }
        }.getOrDefault(emptyMap())
    }

    suspend fun save(ctx: Context, plan: DayPlan) = withContext(Dispatchers.IO) {
        load(ctx)
        val keep = (_history.value + (plan.date to plan)).entries.sortedByDescending { it.key }.take(90).associate { it.key to it.value }
        _history.value = keep
        runCatching {
            val o = JSONObject(); keep.forEach { (k, v) -> o.put(k, toJson(v)) }
            val f = file(ctx); val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString()); tmp.renameTo(f)
        }
    }

    // ------------------------------------------------------------------ morning story (once per day, after 4 am)
    private const val PREF = "dayplan"
    fun storyDue(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!sp.getBoolean("story_on", true)) return false
        val now = java.time.LocalTime.now()
        if (now.hour < 4) return false
        return sp.getString("story_shown", null) != Clock.dateKey(Clock.today())
    }
    fun markStoryShown(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("story_shown", Clock.dateKey(Clock.today())).apply()
    fun storyEnabled(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("story_on", true)
    fun setStoryEnabled(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("story_on", on).apply()

    // ------------------------------------------------------------------ data
    suspend fun targets(c: AppContainer, d: LocalDate): DayTargets {
        val t = c.profileRepo.targets.first()
        val w = c.logRepo.weightsAll().first().maxByOrNull { it.loggedAt }?.weightKg
        return DayTargets(
            Targets.on(t, TargetType.STEPS, d) ?: 8000.0,
            Targets.on(t, TargetType.SLEEP_MIN, d) ?: 480.0,
            Targets.on(t, TargetType.WATER_ML, d) ?: ((w ?: 70.0) * 35).coerceIn(1800.0, 4000.0),
            Targets.on(t, TargetType.CALORIES, d), Targets.on(t, TargetType.PROTEIN_G, d), Targets.on(t, TargetType.WEEKLY_WORKOUTS, d),
        )
    }

    /** One batch of queries for a whole range of days. */
    suspend fun stats(c: AppContainer, from: LocalDate, to: LocalDate): Map<LocalDate, DayStats> = withContext(Dispatchers.IO) {
        fun key(d: LocalDate) = Clock.dateKey(d)
        val hc = c.healthRepo.dailyRange(from, to).first().associateBy { it.localDate }
        val phone = runCatching { c.healthRepo.phoneDaily(from).first() }.getOrDefault(emptyMap())
        val manual = c.logRepo.activityRange(from, to).first().groupBy { it.localDate }
        val hcSleep = c.healthRepo.sleepRange(from, to).first().groupBy { it.localDate }.mapValues { (_, l) -> l.maxOf { (it.endAt - it.startAt) / 60_000.0 } }
        val mSleep = c.logRepo.sleepRange(from, to).first().mapNotNull { e -> SleepCalc.minutes(e.startAt, e.endAt)?.let { e.localDate to it.toDouble() } }
            .groupBy { it.first }.mapValues { (_, l) -> l.sumOf { it.second } }
        val water = c.logRepo.waterRange(from, to).first().groupBy { it.localDate }.mapValues { (_, l) -> l.sumOf { it.amountMl } }
        val food = c.nutritionRepo.dailyTotals(from, to).first().filter { it.kcal > 0 }.associateBy { it.date }
        val ws = c.workoutRepo.completedRange(key(from), key(to)).first().groupBy { it.localDate }
        val weights = c.logRepo.weightsAll().first().groupBy { it.localDate }
        val checkIns = runCatching { c.db.moodDao().observeSince(key(from)).first() }.getOrDefault(emptyList()).map { it.localDate }.toSet()
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { d ->
            val k = key(d)
            val man = manual[k]?.filter { it.steps != null }?.map { StepsCalc.Entry(it.steps!!, it.isDayTotal, it.loggedAt, it.id) }?.let { StepsCalc.dayTotal(it) }
            val dayWs = ws[k].orEmpty()
            DayStats(
                d,
                StepsSource.pick(hc[k]?.steps, man?.toLong(), phone[k])?.steps,
                mSleep[k] ?: hcSleep[k],
                water[k],
                food[k]?.kcal, food[k]?.protein,
                dayWs.size, dayWs.sumOf { w -> w.endedAt?.let { (it - w.startedAt) / 60_000 } ?: 0L },
                weights[k]?.map { it.weightKg }?.average(), weights[k] != null, k in checkIns,
            )
        }
    }

    /** 0–100: how close a day got to its goals, over the goals that had data. Null if nothing was recorded. */
    fun score(s: DayStats, t: DayTargets): Int? {
        val parts = buildList {
            s.steps?.let { add((it / t.steps).coerceAtMost(1.0)) }
            s.sleepMin?.let { add((it / t.sleepMin).coerceAtMost(1.0)) }
            s.waterMl?.let { add((it / t.waterMl).coerceAtMost(1.0)) }
            if (s.kcal != null && t.kcal != null) add((1.0 - kotlin.math.abs(s.kcal - t.kcal) / t.kcal).coerceIn(0.0, 1.0))
            if (s.protein != null && t.protein != null) add((s.protein / t.protein).coerceAtMost(1.0))
            if (s.workouts > 0) add(1.0)
        }
        return if (parts.isEmpty()) null else (parts.average() * 100).toInt()
    }

    // ------------------------------------------------------------------ the rules
    fun build(today: LocalDate, y: DayStats?, week: List<DayStats>, t: DayTargets, profile: com.myfit.tracker.data.db.UserProfile?, nextWorkout: String?, weekDone: Int): DayPlan {
        val items = mutableListOf<PlanItem>()
        val reasons = mutableListOf<Pair<Double, String>>()   // (shortfall 0..1, focus text)
        val bit = 1 shl (today.dayOfWeek.value - 1)
        val planned = profile?.let { it.workoutDaysMask and bit != 0 } ?: false
        val shortSleep = y?.sleepMin?.let { it < t.sleepMin - 60 } == true
        val hardYesterday = (y?.trainMin ?: 0) >= 50

        // MOVE ─ workout
        when {
            planned && shortSleep -> items += PlanItem(PlanKind.MOVE, PlanMetric.WORKOUT, nextWorkout?.let { "Lighter $it" } ?: "Lighter workout",
                "Short night — keep today's sets but drop the weight ~10% and stop 2 reps before failure.")
            planned -> items += PlanItem(PlanKind.MOVE, PlanMetric.WORKOUT, nextWorkout ?: "Workout day",
                if (nextWorkout != null) "Next in your rotation. Warm up 5 min first." else "It's one of your training days — 30–45 min is plenty.")
            hardYesterday -> items += PlanItem(PlanKind.MOVE, PlanMetric.NONE, "Active recovery", "Big session yesterday — a 20-min walk and 10 min of mobility.")
            t.weeklyWorkouts != null && weekDone < t.weeklyWorkouts -> items += PlanItem(PlanKind.MOVE, PlanMetric.WORKOUT, "Bonus session (optional)",
                "${weekDone} of ${t.weeklyWorkouts.toInt()} workouts this week — 20 min of calisthenics would count.")
            else -> items += PlanItem(PlanKind.MOVE, PlanMetric.NONE, "Rest day", "Rest is training too. Stretch for 10 minutes.")
        }
        // MOVE ─ steps: build back up gradually if yesterday fell well short
        val avgSteps = week.mapNotNull { it.steps }.takeIf { it.size >= 3 }?.average()
        val ySteps = y?.steps
        val stepsTarget = when {
            ySteps != null && ySteps < t.steps * 0.6 -> round500(maxOf(ySteps * 1.25, t.steps * 0.7)).coerceAtMost(t.steps)
            else -> t.steps
        }
        items += PlanItem(PlanKind.MOVE, PlanMetric.STEPS, "${Fmt.int(stepsTarget)} steps",
            when {
                ySteps == null -> "A 10-min walk after each meal gets you most of the way."
                ySteps < t.steps * 0.6 -> "Yesterday ${Fmt.int(ySteps)}. Step up gradually — a walk after lunch and dinner."
                ySteps >= t.steps -> "You hit ${Fmt.int(ySteps)} yesterday — keep the streak."
                else -> "Yesterday ${Fmt.int(ySteps)} — one extra 15-min walk closes the gap."
            }, stepsTarget)
        if (ySteps != null) reasons += (1.0 - ySteps / t.steps).coerceIn(0.0, 1.0) to "move more"

        // FUEL
        if (t.kcal != null) {
            val over = y?.kcal?.let { it - t.kcal }
            items += PlanItem(PlanKind.FUEL, PlanMetric.KCAL, "${Fmt.int(t.kcal)} ${EnergyUnit.label}",
                when {
                    over == null -> "Log meals as you go so the numbers stay honest."
                    over > t.kcal * 0.1 -> "Yesterday ran ~${Fmt.int(over)} over — keep dinner light, no need to skip meals."
                    over < -t.kcal * 0.25 -> "Yesterday was ${Fmt.int(-over)} under — eat enough to recover."
                    else -> "Yesterday was on target. Same again."
                }, t.kcal)
            y?.kcal?.let { reasons += (kotlin.math.abs(it - t.kcal) / t.kcal).coerceIn(0.0, 1.0) to "eat to target" }
        }
        if (t.protein != null) {
            val low = y?.protein?.let { it < t.protein * 0.8 } == true
            items += PlanItem(PlanKind.FUEL, PlanMetric.PROTEIN, "${Fmt.int(t.protein)} g protein",
                if (low) "Yesterday ${Fmt.int(y!!.protein!!)} g — add eggs, yogurt or daal at breakfast." else "Spread it over 3–4 meals.", t.protein)
            y?.protein?.let { reasons += (1.0 - it / t.protein).coerceIn(0.0, 1.0) to "hit protein" }
        }
        val water = if (planned || hardYesterday) t.waterMl + 500 else t.waterMl
        items += PlanItem(PlanKind.FUEL, PlanMetric.WATER, "%.1f L water".format(water / 1000.0),
            if (planned) "Training day: +500 ml. A glass before each meal." else "A glass when you wake up and one before each meal.", water)
        y?.waterMl?.let { reasons += (1.0 - it / t.waterMl).coerceIn(0.0, 1.0) to "drink more water" }

        // RECOVER
        val bed = profile?.sleepTimeMin?.let { (it - if (shortSleep) 30 else 0).mod(1440) }
        items += PlanItem(PlanKind.RECOVER, PlanMetric.SLEEP, "${Fmt.duration(t.sleepMin.toLong())} sleep",
            when {
                shortSleep && bed != null -> "Last night ${Fmt.duration(y!!.sleepMin!!.toLong())}. Lights out by ${hhmm(bed)} tonight."
                bed != null -> "Wind down from ${hhmm((bed - 30).mod(1440))}, lights out ${hhmm(bed)}."
                else -> "Screens off 30 minutes before bed."
            }, t.sleepMin)
        y?.sleepMin?.let { reasons += (1.0 - it / t.sleepMin).coerceIn(0.0, 1.0) to "sleep more" }
        val lastWeigh = week.lastOrNull { it.weighed }?.date
        if (lastWeigh == null || lastWeigh.isBefore(today.minusDays(3))) items += PlanItem(PlanKind.RECOVER, PlanMetric.WEIGH, "Weigh in", "Morning, after the bathroom, before food — same time each week.")
        items += PlanItem(PlanKind.RECOVER, PlanMetric.MIND, "2-min check-in", "How you feel helps pace your training.")

        val worst = reasons.maxByOrNull { it.first }
        val score = y?.let { score(it, t) }
        val headline = when {
            y == null || score == null -> "Let's build a great day"
            score >= 85 -> "Strong yesterday — keep it rolling"
            worst != null && worst.first > 0.25 -> "Today's focus: ${worst.second}"
            else -> "Solid base — small wins today"
        }
        val why = when {
            score == null -> "Nothing recorded yesterday, so this plan uses your goals."
            else -> "Yesterday scored $score/100" + (avgSteps?.let { " · ${Fmt.int(it)} steps/day this week" } ?: "")
        }
        return DayPlan(Clock.dateKey(today), headline, why, items)
    }

    /** Builds (or returns the saved) plan for today. */
    suspend fun today(c: AppContainer, force: Boolean = false): Triple<DayPlan, DayStats?, DayTargets> {
        load(c.app)
        val today = Clock.today()
        val t = targets(c, today)
        val st = stats(c, today.minusDays(7), today.minusDays(1))
        val y = st[today.minusDays(1)]
        val saved = _history.value[Clock.dateKey(today)]
        if (saved != null && !force) return Triple(saved, y, t)
        val profile = c.profileRepo.profile.first()
        val templates = c.workoutRepo.templates.first()
        val last = c.workoutRepo.recentViews(1).first().firstOrNull()
        val idx = templates.indexOfFirst { it.template.id == last?.workout?.templateId }
        val next = if (templates.isEmpty()) null else templates[(idx + 1).mod(templates.size)].template.name
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val weekDone = c.workoutRepo.completedRange(Clock.dateKey(monday), Clock.dateKey(today)).first().size
        val plan = build(today, y, st.values.sortedBy { it.date }, t, profile, next, weekDone)
        save(c.app, plan)
        return Triple(plan, y, t)
    }

    /** Optional one-line coach tip from the online AI; cached into the saved plan. Silent on failure. */
    suspend fun tip(c: AppContainer, plan: DayPlan): DayPlan {
        if (plan.tip != null || !tried.add(plan.date)) return plan
        val text = runCatching {
            kotlinx.coroutines.withTimeout(9_000) {
                c.aiRouter.text(
                    "You are a friendly fitness coach. Reply with ONE short motivating sentence (max 22 words), no emojis, no medical claims.",
                    "Today's plan: ${plan.headline}. " + plan.items.joinToString("; ") { it.title } + ". ${plan.why}.", perProviderMs = 6_000,
                )
            }.trim().lineSequence().firstOrNull()?.trim('"', ' ')?.take(160)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return plan
        return plan.copy(tip = text).also { save(c.app, it) }
    }

    private val tried = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun round500(v: Double) = (Math.round(v / 500.0) * 500).toDouble()
    private fun hhmm(min: Int): String {
        val h = min / 60; val m = min % 60
        return java.time.LocalTime.of(h, m).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
    }

    private fun toJson(p: DayPlan) = JSONObject().put("date", p.date).put("headline", p.headline).put("why", p.why).put("tip", p.tip)
        .put("items", JSONArray().apply {
            p.items.forEach { i -> put(JSONObject().put("k", i.kind.name).put("m", i.metric.name).put("t", i.title).put("d", i.detail).put("v", i.target ?: JSONObject.NULL)) }
        })

    private fun fromJson(o: JSONObject): DayPlan {
        val a = o.getJSONArray("items")
        return DayPlan(o.getString("date"), o.optString("headline"), o.optString("why"),
            (0 until a.length()).map { a.getJSONObject(it) }.map { i ->
                PlanItem(runCatching { PlanKind.valueOf(i.getString("k")) }.getOrDefault(PlanKind.MOVE), runCatching { PlanMetric.valueOf(i.getString("m")) }.getOrDefault(PlanMetric.NONE),
                    i.optString("t"), i.optString("d"), if (i.isNull("v")) null else i.optDouble("v"))
            }, o.optString("tip").takeIf { it.isNotBlank() && it != "null" })
    }
}
