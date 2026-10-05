package com.myfit.tracker.domain

import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetType
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** The Sunday report: last 7 days vs the 7 before, from what was actually recorded. Null fields = nothing recorded. */
data class WeekReport(
    val from: LocalDate, val to: LocalDate,
    val workouts: Int, val prevWorkouts: Int,
    val sets: Int, val volumeKg: Double, val prevVolumeKg: Double, val trainMin: Long,
    val steps: Long?, val prevSteps: Long?, val bestStepsDay: Pair<LocalDate, Long>?, val stepDaysAtGoal: Int,
    val sleepAvgMin: Double?, val waterAvgMl: Double?, val kcalAvg: Double?, val proteinAvg: Double?, val daysFoodLogged: Int,
    val weightChangeKg: Double?,
) {
    val stepsAvg: Long? get() = steps?.let { it / 7 }
}

object WeeklyReport {
    suspend fun build(c: AppContainer, to: LocalDate = Clock.today()): WeekReport {
        val from = to.minusDays(6)
        val pFrom = from.minusDays(7); val pTo = from.minusDays(1)
        // training
        val rows = c.workoutRepo.allHistory().first()
        val ex = c.exerciseRepo.everything.first().associateBy { it.id }
        fun inRange(d: String, a: LocalDate, b: LocalDate) = runCatching { LocalDate.parse(d) }.getOrNull()?.let { !it.isBefore(a) && !it.isAfter(b) } == true
        val cur = rows.filter { inRange(it.workoutLocalDate, from, to) && it.setType != SetType.WARMUP }
        val prev = rows.filter { inRange(it.workoutLocalDate, pFrom, pTo) && it.setType != SetType.WARMUP }
        fun vol(l: List<com.myfit.tracker.data.db.SetRow>) = l.sumOf { r ->
            if (ex[r.exerciseId]?.measurementType == MeasurementType.WEIGHT_REPS && (r.weightKg ?: 0.0) > 0 && (r.reps ?: 0) > 0) r.weightKg!! * r.reps!! else 0.0
        }
        val ws = c.workoutRepo.completedRange(Clock.dateKey(from), Clock.dateKey(to)).first()
        val pws = c.workoutRepo.completedRange(Clock.dateKey(pFrom), Clock.dateKey(pTo)).first()
        val trainMin = ws.sumOf { w -> w.endedAt?.let { (it - w.startedAt) / 60_000 } ?: 0L }
        // steps (Health Connect daily totals)
        val daily = c.healthRepo.dailyRange(from, to).first().filter { it.steps != null }
        val pDaily = c.healthRepo.dailyRange(pFrom, pTo).first().filter { it.steps != null }
        val targets = c.profileRepo.targets.first()
        val stepGoal = Targets.on(targets, com.myfit.tracker.data.db.TargetType.STEPS, to)
        // sleep, water, food, weight
        val sleep = c.healthRepo.sleepRange(from, to).first().groupBy { it.localDate }.mapValues { (_, l) -> l.maxOf { (it.endAt - it.startAt) / 60_000.0 } }
        val manualSleep = c.logRepo.sleepRange(from, to).first().mapNotNull { e -> SleepCalc.minutes(e.startAt, e.endAt)?.let { e.localDate to it.toDouble() } }
            .groupBy { it.first }.mapValues { (_, l) -> l.sumOf { it.second } }
        val nights = (sleep + manualSleep).values
        val water = c.logRepo.waterRange(from, to).first().groupBy { it.localDate }.mapValues { (_, l) -> l.sumOf { it.amountMl } }
        val food = c.nutritionRepo.dailyTotals(from, to).first().filter { it.kcal > 0 }
        val weights = c.logRepo.weightsAll().first()
        val wNow = weights.filter { inRange(it.localDate, from, to) }.map { it.weightKg }
        val wPrev = weights.filter { inRange(it.localDate, pFrom, pTo) }.map { it.weightKg }
        return WeekReport(
            from, to, ws.size, pws.size, cur.size, vol(cur), vol(prev), trainMin,
            daily.takeIf { it.isNotEmpty() }?.sumOf { it.steps!! }, pDaily.takeIf { it.isNotEmpty() }?.sumOf { it.steps!! },
            daily.maxByOrNull { it.steps!! }?.let { LocalDate.parse(it.localDate) to it.steps!! },
            if (stepGoal != null) daily.count { it.steps!! >= stepGoal } else 0,
            nights.takeIf { it.isNotEmpty() }?.average(), water.values.takeIf { it.isNotEmpty() }?.average(),
            food.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average(), food.takeIf { it.isNotEmpty() }?.map { it.protein }?.average(), food.size,
            if (wNow.isNotEmpty() && wPrev.isNotEmpty()) wNow.average() - wPrev.average() else null,
        )
    }
}
