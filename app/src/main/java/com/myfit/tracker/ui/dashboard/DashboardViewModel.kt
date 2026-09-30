package com.myfit.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.DailyCheckIn
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.data.db.UserProfile
import com.myfit.tracker.data.db.WeightEntry
import com.myfit.tracker.data.repo.DayLog
import com.myfit.tracker.domain.Change
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.SleepCalc
import com.myfit.tracker.domain.Stats
import com.myfit.tracker.domain.StepsCalc
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.domain.WindowStat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import com.myfit.tracker.data.repo.TemplateView
import com.myfit.tracker.data.repo.WorkoutView
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Emits the local calendar date and re-emits when it rolls over (midnight, travel, DST). */
val todayFlow = flow {
    while (true) { emit(Clock.today()); delay(20_000) }
}.distinctUntilChanged()

data class GoalStatus(val label: String, val met: Boolean?, val detail: String)

data class DashState(
    val loaded: Boolean = false,
    val today: LocalDate = Clock.today(),
    val profile: UserProfile? = null,
    val day: DayLog = DayLog(Clock.today()),
    // body
    val latestWeight: WeightEntry? = null,
    val todayWeightMean: Double? = null,
    val avg7: WindowStat = WindowStat(null, 0, 7),
    val change7: Change? = null,
    val change30: Change? = null,
    val weightSpark: List<Double?> = emptyList(),
    // hydration
    val waterMl: Double? = null,
    val waterTarget: Double? = null,
    // recovery
    val sleepMin: Long? = null,
    val sleepQuality: Int? = null,
    val sleepTarget: Double? = null,
    val sleepAvg7: WindowStat = WindowStat(null, 0, 7),
    // activity
    val steps: Int? = null,
    val stepTarget: Double? = null,
    val activeMin: Int? = null,
    val distanceM: Double? = null,
    // check-in (latest of the day)
    val checkIn: DailyCheckIn? = null,
    // targets for future cards
    val calorieTarget: Double? = null,
    val proteinTarget: Double? = null,
    val goals: List<GoalStatus> = emptyList(),
    // training
    val workout: WorkoutToday = WorkoutToday(),
)

data class WorkoutToday(
    val active: WorkoutView? = null,
    val done: List<WorkoutView> = emptyList(),
    val plannedDay: Boolean = false,
    val next: TemplateView? = null,        // template after the one you used most recently
    val weeklyDone: Int = 0,               // completed workouts Mon–today
    val weeklyTarget: Double? = null,
)

private data class Training(val active: WorkoutView?, val done: List<WorkoutView>, val templates: List<TemplateView>, val last: WorkoutView?, val weekCount: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(c: AppContainer) : ViewModel() {

    val state: StateFlow<DashState> = todayFlow.flatMapLatest { today ->
        val week = today.minusDays(6)
        val dayF = c.logRepo.day(today)
        val sleepWeekF = c.logRepo.sleepRange(week, today)
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val trainingF = combine(
            c.workoutRepo.inProgress.flatMapLatest { w -> if (w == null) flowOf(null) else c.workoutRepo.workoutView(w.id) },
            c.workoutRepo.dayViews(Clock.dateKey(today)),
            c.workoutRepo.templates,
            c.workoutRepo.recentViews(1),
            c.workoutRepo.completedRange(Clock.dateKey(monday), Clock.dateKey(today)),
        ) { active, dayViews, templates, last, week -> Training(active, dayViews.filter { it.workout.status == "COMPLETED" }, templates, last.firstOrNull(), week.size) }
        combine(
            c.profileRepo.profile, c.profileRepo.targets, dayF, c.logRepo.weightsAll(), sleepWeekF,
        ) { profile, targets, day, weights, sleepWeek ->
            build(today, profile, targets, day, weights, sleepWeek.mapNotNull { e -> SleepCalc.minutes(e.startAt, e.endAt)?.let { Clock.parse(e.localDate) to it.toDouble() } })
        }.combine(trainingF) { st, tr ->
            val templates = tr.templates
            val lastIdx = templates.indexOfFirst { it.template.id == tr.last?.workout?.templateId }
            val next = if (templates.isEmpty()) null else templates[(lastIdx + 1).mod(templates.size)]
            val bit = 1 shl (today.dayOfWeek.value - 1)
            st.copy(workout = st.workout.copy(
                active = tr.active, done = tr.done,
                plannedDay = st.profile?.let { it.workoutDaysMask and bit != 0 } ?: false,
                next = next, weeklyDone = tr.weekCount,
            ))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashState())

    private fun build(
        today: LocalDate,
        profile: UserProfile?,
        targets: List<Targets.Row>,
        day: DayLog,
        weights: List<WeightEntry>,
        sleepWeek: List<Pair<LocalDate, Double>>,
    ): DashState {
        val daily = Stats.dailyMeans(weights.map { Clock.parse(it.localDate) to it.weightKg })
        val spark = (13 downTo 0).map { daily[today.minusDays(it.toLong())] }

        val water = if (day.water.isEmpty()) null else day.water.sumOf { it.amountMl }
        val sleepMinutes = day.sleep.mapNotNull { SleepCalc.minutes(it.startAt, it.endAt) }.takeIf { it.isNotEmpty() }?.sum()
        val steps = StepsCalc.dayTotal(day.activity.filter { it.steps != null }.map { StepsCalc.Entry(it.steps!!, it.isDayTotal, it.loggedAt, it.id) })
        val activeMin = day.activity.mapNotNull { it.activeMinutes }.takeIf { it.isNotEmpty() }?.sum()
        val dist = day.activity.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum()

        val tWater = Targets.on(targets, TargetType.WATER_ML, today)
        val tSteps = Targets.on(targets, TargetType.STEPS, today)
        val tSleep = Targets.on(targets, TargetType.SLEEP_MIN, today)

        val goals = buildList {
            if (tWater != null) add(GoalStatus("Water", water?.let { it >= tWater }, if (water == null) "Not logged yet" else "${(water / tWater * 100).toInt()}%"))
            if (tSteps != null) add(GoalStatus("Steps", steps?.let { it >= tSteps }, if (steps == null) "Not logged yet" else "${(steps / tSteps * 100).toInt()}%"))
            if (tSleep != null) add(GoalStatus("Sleep", sleepMinutes?.let { it >= tSleep }, if (sleepMinutes == null) "Not logged yet" else "${(sleepMinutes / tSleep * 100).toInt()}%"))
            add(GoalStatus("Weigh-in", if (day.weight.isNotEmpty()) true else null, if (day.weight.isEmpty()) "Not logged yet" else "Logged"))
            add(GoalStatus("Check-in", if (day.checkIns.isNotEmpty()) true else null, if (day.checkIns.isEmpty()) "Not logged yet" else "Logged"))
        }

        return DashState(
            loaded = true,
            today = today,
            profile = profile,
            day = day,
            latestWeight = weights.maxByOrNull { it.loggedAt },
            todayWeightMean = daily[today],
            avg7 = Stats.windowAverage(daily, today, 7),
            change7 = Stats.windowChange(daily, today, 7, 7, 3),
            change30 = Stats.windowChange(daily, today, 7, 30, 3),
            weightSpark = spark,
            waterMl = water,
            waterTarget = tWater,
            sleepMin = sleepMinutes,
            sleepQuality = day.sleep.lastOrNull()?.quality,
            sleepTarget = tSleep,
            sleepAvg7 = Stats.windowAverage(Stats.dailySums(sleepWeek), today, 7),
            steps = steps,
            stepTarget = tSteps,
            activeMin = activeMin,
            distanceM = dist,
            checkIn = day.checkIns.maxByOrNull { it.loggedAt },
            calorieTarget = Targets.on(targets, TargetType.CALORIES, today),
            proteinTarget = Targets.on(targets, TargetType.PROTEIN_G, today),
            goals = goals,
            workout = WorkoutToday(weeklyTarget = Targets.on(targets, TargetType.WEEKLY_WORKOUTS, today)),
        )
    }
}
