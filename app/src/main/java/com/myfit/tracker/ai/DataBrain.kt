package com.myfit.tracker.ai

import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetType
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.data.repo.WorkoutView
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.SleepCalc
import com.myfit.tracker.domain.StepsCalc
import com.myfit.tracker.domain.StepsSource
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.domain.UnitPrefs
import com.myfit.tracker.domain.Units
import com.myfit.tracker.domain.WorkoutCalc
import com.myfit.tracker.ui.pip.PipMood
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Ask My Data": answers questions ONLY from recorded data, offline, deterministically.
 * Every answer says how much data it rests on. Unknown questions return null (→ Gemini, if enabled).
 */
class DataBrain(private val c: AppContainer) {

    data class Answer(val text: String, val mood: PipMood)
    data class Range(val from: LocalDate, val to: LocalDate, val label: String) {
        val days get() = (to.toEpochDay() - from.toEpochDay() + 1).toInt()
        fun has(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
    }

    /** Everything the brain may look at, loaded once per question. */
    private class Snapshot(
        val weights: Map<LocalDate, Double>,          // daily mean kg
        val water: Map<LocalDate, Double>,            // daily ml
        val sleep: Map<LocalDate, Double>,            // minutes (manual first, else watch)
        val steps: Map<LocalDate, Long>,              // picked per StepsSource
        val restingHr: Map<LocalDate, Long>,
        val workouts: List<WorkoutView>,
        val targets: List<Targets.Row>,
        val firstDay: LocalDate,
    )

    private suspend fun load(): Snapshot {
        val today = Clock.today()
        val from = today.minusDays(730)
        val weights = c.logRepo.weightsAll().first()
            .groupBy({ Clock.parse(it.localDate) }, { it.weightKg }).mapValues { it.value.average() }
        val water = c.logRepo.waterRange(from, today).first().groupBy({ Clock.parse(it.localDate) }, { it.amountMl }).mapValues { it.value.sum() }
        val manualSleep = c.logRepo.sleepRange(from, today).first()
            .mapNotNull { e -> SleepCalc.minutes(e.startAt, e.endAt)?.let { Clock.parse(e.localDate) to it.toDouble() } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        val hcSleep = c.healthRepo.sleepRange(from, today).first().groupBy { Clock.parse(it.localDate) }
            .mapValues { (_, l) -> l.maxOf { (it.endAt - it.startAt) / 60_000.0 } }
        val hcDaily = c.healthRepo.dailyRange(from, today).first().associateBy { Clock.parse(it.localDate) }
        val manualSteps = c.logRepo.activityRange(from, today).first().filter { it.steps != null }
            .groupBy { Clock.parse(it.localDate) }
            .mapValues { (_, l) -> StepsCalc.dayTotal(l.map { StepsCalc.Entry(it.steps!!, it.isDayTotal, it.loggedAt, it.id) })?.toLong() }
        val phone = c.healthRepo.phoneDaily(from).first().mapKeys { Clock.parse(it.key) }
        val allDays = (hcDaily.keys + manualSteps.keys + phone.keys)
        val steps = allDays.mapNotNull { d -> StepsSource.pick(hcDaily[d]?.steps, manualSteps[d], phone[d])?.let { d to it.steps } }.toMap()
        val workouts = c.workoutRepo.recentViews(1000).first()
        val targets = c.profileRepo.targets.first()
        val profile = c.profileRepo.profile.first()
        return Snapshot(
            weights, water, hcSleep + manualSleep, steps,
            hcDaily.mapNotNull { (d, v) -> v.restingHr?.let { d to it } }.toMap(),
            workouts, targets,
            profile?.createdAt?.let { Clock.localDateOf(it) } ?: today.minusDays(30),
        )
    }

    // ------------------------------------------------------------------ periods
    fun parseRange(q: String, today: LocalDate = Clock.today()): Range? {
        val s = q.lowercase()
        Regex("""(?:last|past)\s+(\d{1,3})\s+days?""").find(s)?.let { val n = it.groupValues[1].toInt().coerceIn(1, 730); return Range(today.minusDays(n - 1L), today, "the last $n days") }
        return when {
            "today" in s -> Range(today, today, "today")
            "yesterday" in s -> today.minusDays(1).let { Range(it, it, "yesterday") }
            "this week" in s -> Range(today.with(DayOfWeek.MONDAY), today, "this week")
            "last week" in s -> today.with(DayOfWeek.MONDAY).minusWeeks(1).let { Range(it, it.plusDays(6), "last week") }
            "this month" in s -> Range(today.withDayOfMonth(1), today, "this month")
            "last month" in s -> today.withDayOfMonth(1).minusMonths(1).let { Range(it, it.plusMonths(1).minusDays(1), "last month") }
            "all time" in s || " ever" in s || "overall" in s -> Range(today.minusDays(730), today, "all recorded time")
            else -> Month.entries.firstOrNull { s.contains(it.getDisplayName(TextStyle.FULL, Locale.US).lowercase()) }?.let { m ->
                var start = LocalDate.of(today.year, m, 1)
                if (start.isAfter(today)) start = start.minusYears(1)
                Range(start, minOf(start.plusMonths(1).minusDays(1), today), m.getDisplayName(TextStyle.FULL, Locale.US) + " " + start.year)
            } ?: when {
                "week" in s -> Range(today.minusDays(6), today, "the last 7 days")
                "month" in s -> Range(today.minusDays(29), today, "the last 30 days")
                "year" in s -> Range(today.minusDays(364), today, "the last 365 days")
                else -> null
            }
        }
    }

    private fun <T : Number> inRange(m: Map<LocalDate, T>, r: Range) = m.filterKeys { r.has(it) }
    private fun coverage(n: Int, r: Range) = if (r.days == 1) "" else " (from $n of ${r.days} days with data)"

    // ------------------------------------------------------------------ answering
    suspend fun answer(question: String): Answer? {
        val q = question.lowercase().trim()
        if (q.isBlank()) return null
        val s = load()
        val u = c.settings.settings.first().units
        val today = Clock.today()
        val r = parseRange(q) ?: Range(today.minusDays(29), today, "the last 30 days")
        fun kw(vararg k: String) = k.any { it in q }

        return when {
            kw("compare") -> compare(s, u, Regex("""(\d{1,3})""").find(q)?.value?.toIntOrNull()?.coerceIn(7, 365) ?: 30)
            kw("how am i doing", "how's my day", "hows my day", "summary", "overview", "status") && !kw("week", "month") -> todaySummary(s, u)
            kw("protein", "calorie", "carb", " fat ", "macros", "eat", "food", "meal", "diet") && !kw("tip", "should", "how much protein", "idea", "suggest") ->
                Answer("Nutrition logging arrives in the next build, so there's no recorded food data yet — I won't guess. Once you log meals I'll track calories, protein, carbs, fat and fibre against your targets.", PipMood.THINKING)
            kw("step", "walk") && !kw("tip", "how to", "increase") -> steps(s, r)
            kw("weigh", "weight", "body weight") && !kw("lose", "gain", "tip", "how to") -> weight(s, r, u)
            kw("water", "hydrat", "drink") && !kw("tip", "how much should") -> water(s, r, u)
            kw("sleep") && !kw("tip", "how to", "better") -> sleep(s, r)
            kw("resting heart", "heart rate", "rhr", "pulse") -> heart(s, r)
            kw("best workout", "biggest workout", "hardest workout") -> bestWorkout(s, r, u)
            kw("volume") && !kw("what is") -> volume(s, r, u)
            (kw("pr", "prs", "personal record", "personal best", "records")) && findExercise(q, s) == null -> prCount(s, r)
            kw("improve", "progress", "best", "max", "strongest", "pr", "heaviest", "how much did my") -> findExercise(q, s)?.let { exerciseProgress(it, s, u) }
            kw("how many times", "how many workouts", "how often", "workouts", "trained", "train ", "gym", "sessions") && !kw("should", "plan", "program") -> workouts(q, s, r)
            else -> findExercise(q, s)?.takeIf { kw("my ", "i ") }?.let { exerciseProgress(it, s, u) }
        }
    }

    private fun todaySummary(s: Snapshot, u: UnitPrefs): Answer {
        val d = Clock.today()
        val lines = mutableListOf<String>()
        s.weights[d]?.let { lines += "⚖️ Weight: ${Fmt.weight(it, u.weight)}" }
        val tw = Targets.on(s.targets, TargetType.WATER_ML, d)
        lines += "💧 Water: " + (s.water[d]?.let { Fmt.volume(it, u.volume) + (tw?.let { t -> " of ${Fmt.volume(t, u.volume)}" } ?: "") } ?: "not logged yet")
        val ts = Targets.on(s.targets, TargetType.STEPS, d)
        lines += "👟 Steps: " + (s.steps[d]?.let { Fmt.int(it) + (ts?.let { t -> " of ${Fmt.int(t)}" } ?: "") } ?: "none recorded yet")
        lines += "😴 Sleep: " + (s.sleep[d]?.let { Fmt.duration(it.toLong()) } ?: "not recorded")
        val wToday = s.workouts.filter { it.workout.localDate == Clock.dateKey(d) }
        lines += "🏋️ Workout: " + if (wToday.isEmpty()) "none finished yet" else wToday.joinToString { "${it.workout.name} (${it.totals.sets} sets)" }
        val good = (tw != null && (s.water[d] ?: 0.0) >= tw) || (ts != null && (s.steps[d] ?: 0L) >= ts) || wToday.isNotEmpty()
        return Answer("Here's today so far:\n" + lines.joinToString("\n"), if (good) PipMood.PROUD else PipMood.HAPPY)
    }

    private fun steps(s: Snapshot, r: Range): Answer {
        val m = inRange(s.steps, r)
        if (m.isEmpty()) return Answer("No steps recorded for ${r.label}. Connect Samsung Health in Activity & heart, or enable the phone step counter.", PipMood.CONCERNED)
        if (r.days == 1) return Answer("You've recorded ${Fmt.int(m.values.first())} steps ${r.label}.", PipMood.HAPPY)
        val t = Targets.on(s.targets, TargetType.STEPS, r.to)
        val hit = t?.let { tt -> m.count { it.value >= tt } }
        val best = m.maxBy { it.value }
        return Answer("Average ${Fmt.int(m.values.average().toLong())} steps/day over ${r.label}${coverage(m.size, r)}. " +
            "Total ${Fmt.int(m.values.sum())}. Best day: ${Fmt.int(best.value)} on ${best.key}." +
            (hit?.let { " You hit your step target on $it of ${m.size} recorded days." } ?: ""), PipMood.HAPPY)
    }

    private fun weight(s: Snapshot, r: Range, u: UnitPrefs): Answer {
        val m = inRange(s.weights, r)
        if (m.isEmpty()) return Answer("No weigh-ins recorded for ${r.label}, so I can't say.", PipMood.CONCERNED)
        val sorted = m.toSortedMap()
        val first = sorted.entries.first(); val last = sorted.entries.last()
        val change = if (sorted.size >= 2) " From ${Fmt.weight(first.value, u.weight)} (${first.key}) to ${Fmt.weight(last.value, u.weight)} (${last.key}): ${Fmt.signed(Units.kgTo(last.value - first.value, u.weight))} ${u.weight.label}." else ""
        return Answer("Average weight over ${r.label}: ${Fmt.weight(m.values.average(), u.weight)}${coverage(m.size, r)}.$change " +
            "Single weigh-ins swing with water and food, so the average is the fairer number.", PipMood.THINKING)
    }

    private fun water(s: Snapshot, r: Range, u: UnitPrefs): Answer {
        val m = inRange(s.water, r)
        if (m.isEmpty()) return Answer("No water logged for ${r.label}.", PipMood.CONCERNED)
        if (r.days == 1) return Answer("You've logged ${Fmt.volume(m.values.first(), u.volume)} ${r.label}.", PipMood.HAPPY)
        val hit = m.count { (d, v) -> Targets.on(s.targets, TargetType.WATER_ML, d)?.let { v >= it } == true }
        return Answer("Average ${Fmt.volume(m.values.average(), u.volume)}/day over ${r.label}${coverage(m.size, r)}. " +
            "Target reached on $hit of ${m.size} logged days. Highest ${Fmt.volume(m.values.max(), u.volume)}, lowest ${Fmt.volume(m.values.min(), u.volume)}.",
            if (hit * 2 >= m.size) PipMood.PROUD else PipMood.HAPPY)
    }

    private fun sleep(s: Snapshot, r: Range): Answer {
        val m = inRange(s.sleep, r)
        if (m.isEmpty()) return Answer("No sleep recorded for ${r.label}.", PipMood.SLEEPY)
        if (r.days == 1) return Answer("You slept ${Fmt.duration(m.values.first().toLong())} (night ending ${r.label}).", PipMood.SLEEPY)
        val t = Targets.on(s.targets, TargetType.SLEEP_MIN, r.to)
        return Answer("Average sleep ${Fmt.duration(m.values.average().toLong())} over ${r.label}${coverage(m.size, r)}. " +
            "Shortest ${Fmt.duration(m.values.min().toLong())}, longest ${Fmt.duration(m.values.max().toLong())}." +
            (t?.let { tt -> " Target met on ${m.count { it.value >= tt }} of ${m.size} nights." } ?: ""), PipMood.SLEEPY)
    }

    private fun heart(s: Snapshot, r: Range): Answer {
        val m = inRange(s.restingHr, r)
        if (m.isEmpty()) return Answer("No resting heart rate synced for ${r.label}. It comes from your Galaxy Watch via Samsung Health.", PipMood.CONCERNED)
        return Answer("Resting heart rate averaged ${m.values.average().toLong()} bpm over ${r.label}${coverage(m.size, r)} (range ${m.values.min()}–${m.values.max()}). " +
            "Wearable readings for personal tracking — not a medical measurement.", PipMood.HAPPY)
    }

    private fun periodWorkouts(s: Snapshot, r: Range) = s.workouts.filter { r.has(Clock.parse(it.workout.localDate)) }

    private fun workouts(q: String, s: Snapshot, r: Range): Answer {
        val muscles = listOf("chest", "back", "shoulders", "biceps", "triceps", "legs", "glutes", "core", "cardio")
        val muscle = muscles.firstOrNull { q.contains(it) || (it == "legs" && q.contains("leg")) || (it == "shoulders" && q.contains("shoulder")) }
        val ws = periodWorkouts(s, r).let { l -> if (muscle == null) l else l.filter { w -> w.exercises.any { it.exercise.primaryMuscle.equals(muscle, true) && it.sets.isNotEmpty() } } }
        val what = muscle?.let { "trained $it" } ?: "completed a workout"
        if (ws.isEmpty()) return Answer("You haven't $what in ${r.label} — at least nothing's recorded.", PipMood.CONCERNED)
        val sets = ws.sumOf { w -> w.exercises.filter { muscle == null || it.exercise.primaryMuscle.equals(muscle, true) }.sumOf { it.sets.size } }
        val weeks = (r.days / 7.0).coerceAtLeast(1.0)
        return Answer("You $what ${ws.size} time${if (ws.size == 1) "" else "s"} in ${r.label} (${Fmt.num(ws.size / weeks, 1)}/week), with $sets ${muscle?.let { "$it " } ?: ""}sets. " +
            "Last: ${ws.first().workout.name} on ${ws.first().workout.localDate}.", PipMood.PROUD)
    }

    private fun volume(s: Snapshot, r: Range, u: UnitPrefs): Answer {
        val ws = periodWorkouts(s, r)
        val v = ws.mapNotNull { it.totals.volumeKg }
        if (v.isEmpty()) return Answer("No weighted sets recorded in ${r.label}, so there's no volume to report.", PipMood.CONCERNED)
        return Answer("Total volume in ${r.label}: ${Fmt.weight(v.sum(), u.weight, 0)} across ${ws.size} workouts (avg ${Fmt.weight(v.average(), u.weight, 0)}/workout). Warm-ups excluded.", PipMood.PROUD)
    }

    private fun bestWorkout(s: Snapshot, r: Range, u: UnitPrefs): Answer {
        val w = periodWorkouts(s, r).maxByOrNull { it.totals.volumeKg ?: 0.0 } ?: return Answer("No workouts recorded in ${r.label}.", PipMood.CONCERNED)
        val t = w.totals
        return Answer("By volume, your biggest workout in ${r.label} was ${w.workout.name} on ${w.workout.localDate}: " +
            "${t.sets} sets, ${Fmt.int(t.reps)} reps" + (t.volumeKg?.let { ", ${Fmt.weight(it, u.weight, 0)}" } ?: "") + ".", PipMood.PROUD)
    }

    /** Counts sets that exceeded the heaviest weight previously recorded for that exercise. */
    private fun prCount(s: Snapshot, r: Range): Answer {
        val best = HashMap<Long, Double>()
        val hits = mutableListOf<Pair<String, String>>()
        s.workouts.sortedBy { it.workout.startedAt }.forEach { w ->
            w.exercises.filter { it.exercise.measurementType == MeasurementType.WEIGHT_REPS }.forEach { e ->
                e.sets.filter { it.setType != SetType.WARMUP && (it.weightKg ?: 0.0) > 0 }.forEach { st ->
                    val prev = best[e.exercise.id]
                    if (prev != null && st.weightKg!! > prev && r.has(Clock.parse(w.workout.localDate))) hits += e.exercise.name to w.workout.localDate
                    if (prev == null || st.weightKg!! > prev) best[e.exercise.id] = st.weightKg!!
                }
            }
        }
        if (hits.isEmpty()) return Answer("No new weight PRs in ${r.label}. (A PR here = heavier than anything you'd recorded before for that exercise; first-ever sessions don't count.)", PipMood.THINKING)
        val byEx = hits.groupBy { it.first }.map { (n, l) -> "$n ×${l.size}" }
        return Answer("${hits.size} weight PR${if (hits.size == 1) "" else "s"} in ${r.label} 🏆 — ${byEx.joinToString(", ")}.", PipMood.CELEBRATE)
    }

    private data class ExHit(val id: Long, val name: String, val mt: String)

    private fun findExercise(q: String, s: Snapshot): ExHit? {
        val words = q.lowercase().replace(Regex("[^a-z0-9 ]"), " ").split(' ').filter { it.length >= 3 }
            .map { when (it) { "bench" -> "bench"; "squats" -> "squat"; "curls" -> "curl"; "rows" -> "row"; else -> it } }.toSet()
        val stop = setOf("how", "much", "did", "my", "the", "improve", "improved", "progress", "best", "what", "was", "this", "last", "month", "week", "max", "have", "strongest", "heaviest")
        val key = words - stop
        if (key.isEmpty()) return null
        val used = s.workouts.flatMap { w -> w.exercises.filter { it.sets.isNotEmpty() }.map { it.exercise } }.distinctBy { it.id }
        return used.map { e -> e to key.count { k -> e.name.lowercase().contains(k) } }
            .filter { it.second > 0 }.maxWithOrNull(compareBy<Pair<com.myfit.tracker.data.db.Exercise, Int>> { it.second }.thenBy { -it.first.name.length })
            ?.first?.let { ExHit(it.id, it.name, it.measurementType) }
    }

    private fun exerciseProgress(ex: ExHit, s: Snapshot, u: UnitPrefs): Answer {
        val sessions = s.workouts.sortedBy { it.workout.startedAt }.mapNotNull { w ->
            w.exercises.firstOrNull { it.exercise.id == ex.id && it.sets.isNotEmpty() }?.let { w.workout.localDate to it.sets.filter { st -> st.setType != SetType.WARMUP } }
        }.filter { it.second.isNotEmpty() }
        if (sessions.isEmpty()) return Answer("I don't have any recorded sets for ${ex.name} yet.", PipMood.CONCERNED)
        if (ex.mt != MeasurementType.WEIGHT_REPS) {
            val firstReps = sessions.first().second.mapNotNull { it.reps }.maxOrNull()
            val lastReps = sessions.last().second.mapNotNull { it.reps }.maxOrNull()
            return Answer("${ex.name}: ${sessions.size} sessions. Best set went from ${firstReps ?: "—"} reps (${sessions.first().first}) to ${lastReps ?: "—"} reps (${sessions.last().first}).", PipMood.PROUD)
        }
        fun top(l: List<com.myfit.tracker.data.db.SetRow>) = l.maxWithOrNull(compareBy<com.myfit.tracker.data.db.SetRow> { it.weightKg ?: 0.0 }.thenBy { it.reps ?: 0 })!!
        val f = top(sessions.first().second); val l = top(sessions.last().second)
        val all = sessions.flatMap { it.second }
        val best = top(all)
        val e1 = sessions.first().second.mapNotNull { WorkoutCalc.estimated1Rm(it.weightKg, it.reps) }.maxOrNull()
        val e2 = sessions.last().second.mapNotNull { WorkoutCalc.estimated1Rm(it.weightKg, it.reps) }.maxOrNull()
        fun fs(x: com.myfit.tracker.data.db.SetRow) = "${Fmt.weight(x.weightKg ?: 0.0, u.weight, 2)} × ${x.reps}"
        val grew = (l.weightKg ?: 0.0) > (f.weightKg ?: 0.0)
        return Answer(
            "${ex.name} — ${sessions.size} session${if (sessions.size == 1) "" else "s"}.\n" +
                "First top set: ${fs(f)} (${sessions.first().first})\nLatest top set: ${fs(l)} (${sessions.last().first})\n" +
                "Best recorded: ${fs(best)}" +
                (if (e1 != null && e2 != null && sessions.size > 1) "\nEstimated 1RM: ${Fmt.weight(e1, u.weight)} → ${Fmt.weight(e2, u.weight)} (${Fmt.signed(Units.kgTo(e2 - e1, u.weight))} ${u.weight.label}, estimate)" else ""),
            if (grew) PipMood.CELEBRATE else PipMood.THINKING,
        )
    }

    private fun compare(s: Snapshot, u: UnitPrefs, n: Int): Answer {
        val today = Clock.today()
        val cur = Range(today.minusDays(n - 1L), today, "last $n days")
        val prev = Range(today.minusDays(2L * n - 1), today.minusDays(n.toLong()), "previous $n days")
        fun <T : Number> avg(m: Map<LocalDate, T>, r: Range) = inRange(m, r).values.map { it.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        fun row(label: String, a: Double?, b: Double?, f: (Double) -> String) = "$label: ${b?.let(f) ?: "—"} → ${a?.let(f) ?: "—"}"
        val wc = periodWorkouts(s, cur); val wp = periodWorkouts(s, prev)
        val lines = listOf(
            row("Avg weight", avg(s.weights, cur), avg(s.weights, prev)) { Fmt.weight(it, u.weight) },
            "Workouts: ${wp.size} → ${wc.size}",
            "Sets: ${wp.sumOf { it.totals.sets }} → ${wc.sumOf { it.totals.sets }}",
            "Volume: ${Fmt.weight(wp.mapNotNull { it.totals.volumeKg }.sum(), u.weight, 0)} → ${Fmt.weight(wc.mapNotNull { it.totals.volumeKg }.sum(), u.weight, 0)}",
            row("Avg steps", avg(s.steps, cur), avg(s.steps, prev)) { Fmt.int(it.toLong()) },
            row("Avg sleep", avg(s.sleep, cur), avg(s.sleep, prev)) { Fmt.duration(it.toLong()) },
            row("Avg water", avg(s.water, cur), avg(s.water, prev)) { Fmt.volume(it, u.volume) },
        )
        val cov = "Data days (now/before) — weight ${inRange(s.weights, cur).size}/${inRange(s.weights, prev).size}, steps ${inRange(s.steps, cur).size}/${inRange(s.steps, prev).size}, sleep ${inRange(s.sleep, cur).size}/${inRange(s.sleep, prev).size}."
        return Answer("Previous $n days → last $n days:\n" + lines.joinToString("\n") + "\n$cov", PipMood.THINKING)
    }

    // ------------------------------------------------------------------ minimal context for online answers
    /** A short, question-specific summary. Never full history, never notes. */
    suspend fun summaryFor(question: String): String {
        val q = question.lowercase()
        val s = load()
        val p = c.profileRepo.profile.first()
        val u = c.settings.settings.first().units
        val today = Clock.today()
        val r30 = Range(today.minusDays(29), today, "30d")
        fun <T : Number> avg(m: Map<LocalDate, T>) = inRange(m, r30).values.map { it.toDouble() }.takeIf { it.isNotEmpty() }?.average()
        val out = mutableListOf<String>()
        if (p != null) out += "Goals: ${p.goals.ifBlank { "not set" }}; experience: ${p.experience.lowercase()}; sex: ${p.sex.lowercase()}; age: ${p.age}"
        val wantsBody = listOf("weight", "fat", "lose", "gain", "bulk", "cut", "protein", "calorie", "diet", "eat", "food", "meal").any { it in q }
        val wantsTrain = listOf("workout", "train", "exercise", "plan", "program", "split", "muscle", "gym", "strength", "lift", "rest day", "recover").any { it in q }
        val wantsSleep = listOf("sleep", "tired", "energy", "recover").any { it in q }
        val wantsSteps = listOf("step", "walk", "cardio", "run").any { it in q }
        if (wantsBody) {
            avg(s.weights)?.let { out += "Avg body weight (30d): ${Fmt.weight(it, u.weight)} from ${inRange(s.weights, r30).size} weigh-ins" }
            p?.targetWeightKg?.let { out += "Target weight: ${Fmt.weight(it, u.weight)}" }
            Targets.on(s.targets, TargetType.CALORIES, today)?.let { out += "Calorie target: ${Fmt.int(it)} kcal" }
            Targets.on(s.targets, TargetType.PROTEIN_G, today)?.let { out += "Protein target: ${Fmt.int(it)} g" }
        }
        if (wantsTrain) {
            val ws = periodWorkouts(s, r30)
            out += "Workouts (30d): ${ws.size}; muscle groups trained: " +
                ws.flatMap { w -> w.exercises.filter { it.sets.isNotEmpty() }.map { it.exercise.primaryMuscle } }.groupingBy { it }.eachCount().entries.joinToString { "${it.key} ${it.value}" }.ifEmpty { "none" }
            Targets.on(s.targets, TargetType.WEEKLY_WORKOUTS, today)?.let { out += "Planned workouts/week: ${Fmt.int(it)}" }
        }
        if (wantsSleep) avg(s.sleep)?.let { out += "Avg sleep (30d): ${Fmt.duration(it.toLong())} over ${inRange(s.sleep, r30).size} nights" }
        if (wantsSteps) avg(s.steps)?.let { out += "Avg steps (30d): ${Fmt.int(it.toLong())} over ${inRange(s.steps, r30).size} days" }
        out += "Units: ${u.weight.label}, ${u.length.label}, ${u.volume.label}, ${u.distance.label}"
        return out.joinToString("\n").take(900)
    }
}

