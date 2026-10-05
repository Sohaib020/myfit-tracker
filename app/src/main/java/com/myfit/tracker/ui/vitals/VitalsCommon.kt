package com.myfit.tracker.ui.vitals

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.health.VitalsReader
import com.myfit.tracker.ui.components.ChartPoint
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val dayShort = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val timeShort get() = com.myfit.tracker.domain.ClockFmt.f()

/** "Today 14:05", "Yesterday 22:10", "3 Oct 08:00". */
internal fun fmtWhen(at: Instant): String {
    val z = at.atZone(Clock.zone())
    val d = z.toLocalDate()
    val today = Clock.today()
    val day = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(dayShort)
    }
    return day + " " + z.toLocalTime().format(timeShort)
}

internal fun fmtWhenMs(ms: Long): String = fmtWhen(Instant.ofEpochMilli(ms))

internal fun fmtDay(d: LocalDate): String = when (d) {
    Clock.today() -> "Today"
    Clock.today().minusDays(1) -> "Yesterday"
    else -> d.format(dayShort)
}

internal fun dayPoints(days: List<VitalsReader.DayStat>, pick: (VitalsReader.DayStat) -> Double = { it.avg }): List<ChartPoint> =
    days.map { ChartPoint(it.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), pick(it), it.date.format(dayShort)) }

/** Average of the last 7 days vs the whole window — only when there are ≥5 days this week and ≥10 overall. */
internal fun weekVsWindow(days: List<VitalsReader.DayStat>): Pair<Double, Double>? {
    val today = Clock.today()
    val week = days.filter { !it.date.isBefore(today.minusDays(6)) }
    if (week.size < 5 || days.size < 10) return null
    return week.map { it.avg }.average() to days.map { it.avg }.average()
}

/** "Resting HR this week 58 bpm, 4 bpm above your 30-day average." */
internal fun trendSentence(label: String, unit: String, days: List<VitalsReader.DayStat>, decimals: Int = 0, closeBand: Double = 1.5): String? {
    val (w, m) = weekVsWindow(days) ?: return null
    val diff = w - m
    val wTxt = num(w, decimals)
    return if (abs(diff) < closeBand) "$label this week $wTxt $unit, in line with your 30-day average."
    else "$label this week $wTxt $unit, ${num(abs(diff), decimals)} $unit ${if (diff > 0) "above" else "below"} your 30-day average."
}

internal fun num(v: Double, decimals: Int = 0): String =
    if (decimals == 0) v.roundToInt().toString() else String.format(Locale.getDefault(), "%.${decimals}f", v)

/** Friendly name for a data source package. */
internal fun appName(pkg: String?): String = if (pkg == null) "Daily summary" else VitalsReader.appLabel(pkg)

/** Icon for a source app: watch brands get a watch, scales a scale, everything else a sensor. */
internal fun brandIcon(pkg: String): ImageVector = when {
    pkg.contains("withings") -> Duo.MonitorWeight
    pkg.contains("oura") -> Duo.RadioButtonUnchecked
    pkg.contains("google.android.apps.fitness") -> Duo.DirectionsWalk
    pkg.contains("healthconnect") || pkg.contains("healthdata") -> Duo.HealthAndSafety
    pkg.startsWith("com.myfit") -> Duo.FitnessCenter
    pkg.contains("shealth") || pkg.contains("fitbit") || pkg.contains("garmin") || pkg.contains("xiaomi") || pkg.contains("mi.health") ||
        pkg.contains("huami") || pkg.contains("polar") || pkg.contains("whoop") -> Duo.Watch
    else -> Duo.Sensors
}

internal fun brandColor(pkg: String, th: FitTheme): Color = when {
    pkg.contains("shealth") -> th.water
    pkg.contains("google") -> th.steps
    pkg.contains("fitbit") -> Color(0xFF00B0B9)
    pkg.contains("garmin") -> Color(0xFF3A7BD5)
    pkg.contains("oura") -> th.sleep
    pkg.contains("whoop") -> th.warning
    pkg.contains("withings") -> th.fat
    pkg.contains("xiaomi") || pkg.contains("mi.health") -> th.protein
    pkg.startsWith("com.myfit") -> th.accent
    else -> th.textDim
}
