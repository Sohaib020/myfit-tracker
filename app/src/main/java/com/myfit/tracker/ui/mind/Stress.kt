package com.myfit.tracker.ui.mind

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.HcDaily
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.Sparkline
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.roundToInt

data class StressEstimate(
    val score: Int?,            // null when there's not enough data
    val label: String,
    val hrvMs: Double?,
    val baselineMs: Double?,
    val day: String?,           // date of the HRV value used
    val daysWithHrv: Int,
    val recent: List<Double?>,  // last 14 days of scores (null = no HRV that day)
)

private fun median(v: List<Double>): Double {
    val s = v.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
}

/**
 * Monotonic mapping from HRV relative to your own baseline to a 0–100 score:
 * ratio ≥ 1.15 → ~20 (low), 1.0 → 45 (moderate), ≤ 0.75 → ~80 (high).
 */
fun stressScore(hrv: Double, baseline: Double): Int {
    if (hrv <= 0 || baseline <= 0) return 50
    val r = ln(hrv / baseline)
    val s = if (r >= 0) 45.0 - r / ln(1.15) * 25.0 else 45.0 + r / ln(0.75) * 35.0
    return s.coerceIn(5.0, 95.0).roundToInt()
}

fun stressLabel(score: Int): String = when {
    score < 35 -> "Low"
    score < 62 -> "Moderate"
    else -> "High"
}

suspend fun computeStress(container: AppContainer): StressEstimate {
    val today = Clock.today()
    val from = today.minusDays(44)
    val rows: List<HcDaily> = runCatching {
        container.db.healthDao().daily(Clock.dateKey(from), Clock.dateKey(today))
    }.getOrDefault(emptyList())
    val hrv = rows.mapNotNull { r -> r.hrvMs?.takeIf { it > 0 }?.let { r.localDate to it } }.toMap()
    val latest = hrv.keys.maxOrNull()
    // only use a "current" value from today or yesterday
    val fresh = latest?.takeIf { it >= Clock.dateKey(today.minusDays(1)) }
    fun baselineFor(day: LocalDate): Double? {
        val lo = Clock.dateKey(day.minusDays(30)); val hi = Clock.dateKey(day.minusDays(1))
        val vals = hrv.filterKeys { it in lo..hi }.values.toList()
        return if (vals.size >= 7) median(vals) else null
    }
    val recent = (13 downTo 0).map { i ->
        val d = today.minusDays(i.toLong())
        val v = hrv[Clock.dateKey(d)]
        val b = baselineFor(d)
        if (v != null && b != null) stressScore(v, b).toDouble() else null
    }
    val daysLast30 = hrv.keys.count { it >= Clock.dateKey(today.minusDays(30)) }
    if (fresh == null) return StressEstimate(null, "", null, null, latest, daysLast30, recent)
    val freshDay = runCatching { LocalDate.parse(fresh) }.getOrDefault(today)
    val base = baselineFor(freshDay)
    val v = hrv[fresh]
    if (base == null || v == null) return StressEstimate(null, "", v, null, fresh, daysLast30, recent)
    val sc = stressScore(v, base)
    return StressEstimate(sc, stressLabel(sc), v, base, fresh, daysLast30, recent)
}

@Composable
fun StressCard(est: StressEstimate?) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Pulse, "Stress", th.warning) { DataBadge(DataKind.ESTIMATED) }
        Spacer(Modifier.height(12.dp))
        when {
            est == null -> Caption("Reading heart-rate variability…")
            est.score != null -> {
                val col = when (est.label) { "Low" -> th.success; "Moderate" -> th.warning; else -> th.danger }
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.BottomCenter) {
                    StressGauge(est.score, Modifier.fillMaxWidth(0.8f).height(120.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                        Text("${est.score}", style = FitType.display, color = th.text)
                        Text(est.label, style = FitType.section, color = col)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Caption(if (est.day == Clock.dateKey(Clock.today())) "HRV today" else "HRV yesterday")
                        Text("${est.hrvMs?.roundToInt() ?: "—"} ms", style = FitType.section, color = th.text)
                    }
                    Column(Modifier.weight(1f)) {
                        Caption("Your 30-day baseline")
                        Text("${est.baselineMs?.roundToInt() ?: "—"} ms", style = FitType.section, color = th.text)
                    }
                }
                if (est.recent.count { it != null } >= 2) {
                    Spacer(Modifier.height(12.dp))
                    Caption("Last 14 days")
                    Spacer(Modifier.height(4.dp))
                    Sparkline(est.recent, th.warning, Modifier.fillMaxWidth().height(36.dp))
                }
                Spacer(Modifier.height(10.dp))
                Caption("Estimate from heart-rate variability compared with your own baseline. Illness, alcohol and poor sleep also lower HRV. Not a medical measurement.", color = th.textFaint)
            }
            est.daysWithHrv == 0 && est.day == null -> {
                Text("No HRV data yet", style = FitType.section, color = th.text)
                Spacer(Modifier.height(4.dp))
                Caption("To estimate stress, your watch must share heart-rate variability (HRV) with Health Connect. In Samsung Health, allow MyFit to read HRV.")
            }
            else -> {
                Text("Building your baseline", style = FitType.section, color = th.text)
                Spacer(Modifier.height(4.dp))
                Caption(
                    if (est.daysWithHrv < 8) "Needs at least 7 earlier days of HRV from your watch — ${est.daysWithHrv} so far."
                    else "No HRV from today or yesterday yet. Wear your watch to sleep for a fresh reading."
                )
            }
        }
    }
}
