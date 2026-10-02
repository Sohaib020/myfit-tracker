package com.myfit.tracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Menstrual cycle maths. Pure functions only (no Android, no Room) so they can be unit tested.
 *
 * Flow codes match CycleDay.flow: 0 none, 1 spotting, 2 light, 3 medium, 4 heavy.
 * Everything here is a calendar estimate from the user's own logs — never a diagnosis and never contraception.
 */

/** One logged period: first real-flow day to the last bleeding day (inclusive). */
data class Period(val start: LocalDate, val end: LocalDate) {
    val length: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    operator fun contains(d: LocalDate): Boolean = !d.isBefore(start) && !d.isAfter(end)
}

enum class CyclePhase(val label: String) {
    MENSTRUAL("Menstrual"),
    FOLLICULAR("Follicular"),
    OVULATION("Ovulation (est.)"),
    LUTEAL("Luteal"),
}

data class CyclePrediction(
    val periods: List<Period>,
    /** Most recent (up to 6) cycle lengths in days, oldest first, outliers included. */
    val recentLengths: List<Int>,
    /** recentLengths without outliers (<15 or >60 days) — what the averages use. */
    val usedLengths: List<Int>,
    val meanCycle: Double?,
    val sdCycle: Double?,
    /** Mean of completed logged period lengths (default 5 when nothing logged yet). */
    val meanPeriod: Double,
    val periodLengthLogged: Boolean,
    /** Predicted next period start (null until at least one completed cycle is logged). */
    val nextStart: LocalDate?,
    /** ± days around [nextStart]. */
    val rangeDays: Int,
    val ovulation: LocalDate?,
    val fertileStart: LocalDate?,
    val fertileEnd: LocalDate?,
    val irregular: Boolean,
    /** Predicted bleeding days for the next few cycles (today or later only). */
    val predictedPeriodDays: Set<LocalDate>,
    /** Predicted fertile-window days for the current and next few cycles. */
    val fertileDays: Set<LocalDate>,
    /** Estimated ovulation days for the current and next few cycles. */
    val ovulationDays: Set<LocalDate>,
) {
    val hasPredictions: Boolean get() = nextStart != null
}

data class CycleToday(
    /** Day N of the current cycle (1 = first period day), null when nothing is logged. */
    val cycleDay: Int?,
    val phase: CyclePhase?,
    /** Days until the predicted next start; negative = that many days late. Null without predictions. */
    val daysUntilNext: Int?,
    /** Day N of the period when currently menstruating, else null. */
    val periodDay: Int?,
    /** True when the phase was guessed from a typical 28-day cycle (no personal prediction yet). */
    val phaseFromTypical: Boolean,
)

object CycleMath {
    const val MIN_VALID = 15
    const val MAX_VALID = 60
    const val DEFAULT_PERIOD = 5
    private const val TYPICAL_CYCLE = 28

    private fun days(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()

    /**
     * Groups bleeding days into periods. Days with flow ≥ 1 that are at most one empty day apart
     * belong to the same run; a run only counts as a period when it has at least one day of real
     * flow (≥ 2), and the period starts on that first real-flow day — spotting alone never starts one.
     */
    fun periods(flows: Map<LocalDate, Int>): List<Period> {
        val bleed = flows.filter { it.value >= 1 }.keys.sorted()
        if (bleed.isEmpty()) return emptyList()
        val runs = ArrayList<MutableList<LocalDate>>()
        for (d in bleed) {
            val last = runs.lastOrNull()?.lastOrNull()
            if (last != null && days(last, d) <= 2) runs.last().add(d) else runs.add(mutableListOf(d))
        }
        return runs.mapNotNull { run ->
            val start = run.firstOrNull { (flows[it] ?: 0) >= 2 } ?: return@mapNotNull null
            Period(start, run.last())
        }
    }

    fun mean(xs: List<Int>): Double? = if (xs.isEmpty()) null else xs.average()

    /** Sample standard deviation (0 for a single value). */
    fun sd(xs: List<Int>): Double? {
        if (xs.isEmpty()) return null
        if (xs.size == 1) return 0.0
        val m = xs.average()
        return sqrt(xs.sumOf { (it - m) * (it - m) } / (xs.size - 1))
    }

    /** Irregular when lengths vary by more than 8 days, or any cycle falls outside 21–35 days. */
    fun isIrregular(lengths: List<Int>): Boolean {
        if (lengths.isEmpty()) return false
        val spread = (lengths.maxOrNull() ?: 0) - (lengths.minOrNull() ?: 0)
        return spread > 8 || lengths.any { it < 21 || it > 35 }
    }

    fun predict(flows: Map<LocalDate, Int>, today: LocalDate): CyclePrediction {
        val ps = periods(flows)
        val allLengths = ps.zipWithNext { a, b -> days(a.start, b.start) }
        val recent = allLengths.takeLast(6)
        val used = recent.filter { it in MIN_VALID..MAX_VALID }
        val meanC = mean(used)
        val sdC = sd(used)

        // Period length: completed periods only (one still going on yesterday/today is unfinished).
        val completed = ps.filter { it.end.isBefore(today.minusDays(1)) }.takeLast(6)
        val meanP = mean(completed.map { it.length }) ?: DEFAULT_PERIOD.toDouble()
        val periodLen = meanP.roundToInt().coerceIn(1, 10)

        val lastStart = ps.lastOrNull()?.start
        val step = meanC?.roundToInt()
        val nextStart = if (lastStart != null && step != null) lastStart.plusDays(step.toLong()) else null
        val range = max(2, (sdC ?: 0.0).roundToInt())
        val ovulation = nextStart?.minusDays(14)

        val predicted = HashSet<LocalDate>()
        val fertile = HashSet<LocalDate>()
        val ovuls = HashSet<LocalDate>()
        if (nextStart != null && step != null) {
            // If the predicted start has long passed without a log, roll forward so the calendar stays useful.
            var first: LocalDate = nextStart
            while (days(first, today) > step) first = first.plusDays(step.toLong())
            for (k in 0 until 3) {
                val s = first.plusDays(k.toLong() * step)
                for (i in 0 until periodLen) {
                    val d = s.plusDays(i.toLong())
                    if (!d.isBefore(today)) predicted += d
                }
                val ov = s.minusDays(14)
                ovuls += ov
                for (i in -5..1) fertile += ov.plusDays(i.toLong())
            }
        }
        // Never paint predictions over days the user actually logged as bleeding.
        val logged = flows.filter { it.value >= 1 }.keys
        predicted.removeAll(logged)

        return CyclePrediction(
            periods = ps,
            recentLengths = recent,
            usedLengths = used,
            meanCycle = meanC,
            sdCycle = sdC,
            meanPeriod = meanP,
            periodLengthLogged = completed.isNotEmpty(),
            nextStart = nextStart,
            rangeDays = range,
            ovulation = ovulation,
            fertileStart = ovulation?.minusDays(5),
            fertileEnd = ovulation?.plusDays(1),
            irregular = isIrregular(recent),
            predictedPeriodDays = predicted,
            fertileDays = fertile,
            ovulationDays = ovuls,
        )
    }

    fun today(p: CyclePrediction, today: LocalDate): CycleToday {
        val last = p.periods.lastOrNull { !it.start.isAfter(today) }
            ?: return CycleToday(null, null, p.nextStart?.let { days(today, it) }, null, false)
        val cycleDay = days(last.start, today) + 1
        val periodLen = p.meanPeriod.roundToInt().coerceAtLeast(1)
        // Menstruating if today is inside the logged period, or it was still going yesterday
        // and we haven't passed the usual period length yet.
        val menstruating = today in last ||
            (!last.end.isBefore(today.minusDays(1)) && cycleDay <= periodLen)
        val until = p.nextStart?.let { days(today, it) }
        if (menstruating) return CycleToday(cycleDay, CyclePhase.MENSTRUAL, until, cycleDay, p.nextStart == null)

        val personal = p.meanCycle
        val typical = p.nextStart == null || personal == null
        // ovulation ≈ 14 days before the next start, expressed as a cycle day
        val ovDay = (personal?.roundToInt() ?: TYPICAL_CYCLE) - 14 + 1
        val phase = when {
            cycleDay < ovDay - 2 -> CyclePhase.FOLLICULAR
            cycleDay <= ovDay + 1 -> CyclePhase.OVULATION
            else -> CyclePhase.LUTEAL
        }
        return CycleToday(cycleDay, phase, until, null, typical)
    }

    /**
     * Basal body temperature shift: three consecutive days each ≥ 0.2 °C above the mean of the
     * previous 6 days (at least 4 readings needed in that window). Returns the first day of each
     * sustained rise; ovulation most likely happened the day before. Confirmation only — it can't predict.
     */
    fun bbtShifts(bbt: Map<LocalDate, Double>): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        for (d in bbt.keys.sorted()) {
            val t0 = bbt[d] ?: continue
            val t1 = bbt[d.plusDays(1)] ?: continue
            val t2 = bbt[d.plusDays(2)] ?: continue
            val prev = (1..6).mapNotNull { bbt[d.minusDays(it.toLong())] }
            if (prev.size < 4) continue
            val base = prev.average()
            if (t0 >= base + 0.2 && t1 >= base + 0.2 && t2 >= base + 0.2) {
                val lastShift = out.lastOrNull()
                if (lastShift == null || days(lastShift, d) > 10) out += d
            }
        }
        return out
    }
}
