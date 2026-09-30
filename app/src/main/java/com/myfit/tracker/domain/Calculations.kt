package com.myfit.tracker.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The single place the app reads "now". Everything is stamped with the device zone at log time. */
object Clock {
    fun now(): Long = System.currentTimeMillis()
    fun zone(): ZoneId = ZoneId.systemDefault()
    fun today(): LocalDate = LocalDate.now(zone())
    fun localDateOf(epochMs: Long, zone: ZoneId = zone()): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    fun dateKey(d: LocalDate): String = d.toString()          // ISO yyyy-MM-dd, sorts correctly
    fun parse(key: String): LocalDate = LocalDate.parse(key)
    fun zoned(epochMs: Long, zoneId: String): ZonedDateTime =
        Instant.ofEpochMilli(epochMs).atZone(runCatching { ZoneId.of(zoneId) }.getOrDefault(zone()))
    fun minuteOfDay(epochMs: Long, zoneId: String): Int = zoned(epochMs, zoneId).let { it.hour * 60 + it.minute }
}

/** Result that carries its own completeness, so the UI can never present a partial number as complete. */
data class WindowStat(
    val value: Double?,         // null = no data at all in the window
    val daysWithData: Int,
    val daysInWindow: Int,
) {
    val isComplete get() = daysWithData == daysInWindow
    val hasData get() = value != null
}

data class Change(val delta: Double, val from: WindowStat, val to: WindowStat)

/** A (date, value) pair where value is null when nothing was recorded that day. */
data class DayValue(val date: LocalDate, val value: Double?)

object Stats {

    /** Mean of all values recorded on each day (multiple weigh-ins → one daily mean). */
    fun dailyMeans(points: List<Pair<LocalDate, Double>>): Map<LocalDate, Double> =
        points.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.average() }

    fun dailySums(points: List<Pair<LocalDate, Double>>): Map<LocalDate, Double> =
        points.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.sum() }

    /**
     * Average of daily values over the `days` calendar days ending at `end` (inclusive).
     * Days without data are EXCLUDED (never treated as zero) and reported via daysWithData.
     */
    fun windowAverage(daily: Map<LocalDate, Double>, end: LocalDate, days: Int): WindowStat {
        val start = end.minusDays(days.toLong() - 1)
        val vals = daily.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values
        return WindowStat(if (vals.isEmpty()) null else vals.average(), vals.size, days)
    }

    /**
     * Change between the `days`-day window ending at `end` and the same-size window ending
     * `lagDays` earlier. Requires at least `minDays` of data in BOTH windows, else null
     * ("Not enough data for this comparison").
     */
    fun windowChange(
        daily: Map<LocalDate, Double>, end: LocalDate, days: Int, lagDays: Int, minDays: Int,
    ): Change? {
        val now = windowAverage(daily, end, days)
        val before = windowAverage(daily, end.minusDays(lagDays.toLong()), days)
        if (now.daysWithData < minDays || before.daysWithData < minDays) return null
        return Change(now.value!! - before.value!!, before, now)
    }

    /** Trailing moving average series for charts; null where the window holds no data. */
    fun movingAverage(daily: Map<LocalDate, Double>, from: LocalDate, to: LocalDate, window: Int): List<DayValue> {
        val out = ArrayList<DayValue>()
        var d = from
        while (!d.isAfter(to)) {
            out += DayValue(d, windowAverage(daily, d, window).value)
            d = d.plusDays(1)
        }
        return out
    }

    fun daysCovered(dates: Collection<LocalDate>, end: LocalDate, days: Int): Int {
        val start = end.minusDays(days.toLong() - 1)
        return dates.toSet().count { !it.isBefore(start) && !it.isAfter(end) }
    }
}

/** Versioned targets: the value that applied on a given date (null if none was set yet). */
object Targets {
    data class Row(val type: String, val value: Double, val effectiveFrom: LocalDate, val id: Long)

    fun on(rows: List<Row>, type: String, date: LocalDate): Double? =
        rows.asSequence()
            .filter { it.type == type && !it.effectiveFrom.isAfter(date) }
            .maxWithOrNull(compareBy<Row> { it.effectiveFrom }.thenBy { it.id })
            ?.value
}

/** Steps: a "day total" entry replaces earlier totals; increments add on top of the latest total. */
object StepsCalc {
    data class Entry(val steps: Int, val isDayTotal: Boolean, val loggedAt: Long, val id: Long)

    fun dayTotal(entries: List<Entry>): Int? {
        if (entries.isEmpty()) return null
        val sorted = entries.sortedWith(compareBy<Entry> { it.loggedAt }.thenBy { it.id })
        val lastTotalIdx = sorted.indexOfLast { it.isDayTotal }
        val base = if (lastTotalIdx >= 0) sorted[lastTotalIdx].steps else 0
        val incs = sorted.drop(lastTotalIdx + 1).filter { !it.isDayTotal }.sumOf { it.steps }
        return base + incs
    }
}

object SleepCalc {
    /** Duration in minutes; null for an invalid interval (end before start). */
    fun minutes(startAt: Long, endAt: Long): Long? =
        if (endAt <= startAt) null else (endAt - startAt) / 60_000L
}

/**
 * Energy estimate for target *suggestions* only (Mifflin–St Jeor × activity factor).
 * Always labelled "estimate" in the UI; never stored as a measurement.
 */
object EnergyEstimate {
    fun bmr(weightKg: Double, heightCm: Double, age: Int, male: Boolean): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5 else -161

    fun activityFactor(level: String) = when (level) {
        "SEDENTARY" -> 1.2; "LIGHT" -> 1.375; "MODERATE" -> 1.55; "ACTIVE" -> 1.725; else -> 1.9
    }

    fun maintenance(weightKg: Double, heightCm: Double, age: Int, male: Boolean, level: String) =
        bmr(weightKg, heightCm, age, male) * activityFactor(level)
}
