package com.myfit.tracker.ui.train

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.SetType
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.WorkoutCalc
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/*
 * Training progress for a week, a month or 6 months — only honest numbers computed from logged sets:
 * workouts, working sets, volume (weight × reps, warm-ups excluded), sets per muscle per week and estimated
 * 1-rep-max trends (Epley, labelled as an estimate). Each figure is compared with the period before.
 */

private enum class Span(val label: String, val days: Long, val buckets: Int, val bucketDays: Long) {
    WEEK("Week", 7, 7, 1), MONTH("Month", 28, 4, 7), HALF("6 months", 182, 26, 7)
}

private data class Lift(val ex: Exercise, val now: Double, val before: Double?, val sessions: Int)
private data class Stats(
    val workouts: Int, val sets: Int, val volume: Double, val pWorkouts: Int, val pSets: Int, val pVolume: Double,
    val bars: List<Pair<String, Double>>, val muscles: List<Pair<String, Double>>, val lifts: List<Lift>, val weeks: Double,
)

private fun compute(rows: List<SetRow>, ex: Map<Long, Exercise>, span: Span): Stats {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val start = today.minusDays(span.days - 1)
    val pStart = start.minusDays(span.days)
    fun day(r: SetRow) = Instant.ofEpochMilli(r.workoutStartedAt).atZone(zone).toLocalDate()
    val work = rows.filter { it.setType != SetType.WARMUP }
    val cur = work.filter { !day(it).isBefore(start) }
    val prev = work.filter { val d = day(it); !d.isBefore(pStart) && d.isBefore(start) }
    fun vol(l: List<SetRow>) = l.sumOf { r ->
        val m = ex[r.exerciseId]?.measurementType
        if (m == MeasurementType.WEIGHT_REPS && (r.weightKg ?: 0.0) > 0 && (r.reps ?: 0) > 0) r.weightKg!! * r.reps!! else 0.0
    }
    val bars = (0 until span.buckets).map { i ->
        val bStart = today.minusDays(span.bucketDays * (span.buckets - i) - 1)
        val bEnd = bStart.plusDays(span.bucketDays - 1)
        val label = if (span.bucketDays == 1L) bStart.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        else if (span == Span.HALF) (if (i % 4 == 0) bStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) else "")
        else "${bStart.dayOfMonth}/${bStart.monthValue}"
        label to vol(cur.filter { val d = day(it); !d.isBefore(bStart) && !d.isAfter(bEnd) })
    }
    val weeks = span.days / 7.0
    val muscle = HashMap<String, Double>()
    cur.forEach { r ->
        val e = ex[r.exerciseId] ?: return@forEach
        if (e.primaryMuscle.isNotBlank() && e.primaryMuscle != "Cardio") muscle.merge(e.primaryMuscle, 1.0, Double::plus)
        e.secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotBlank() && it != e.primaryMuscle && it != "Cardio" }
            .forEach { muscle.merge(it, 0.5, Double::plus) }
    }
    fun best(l: List<SetRow>) = l.mapNotNull { WorkoutCalc.estimated1Rm(it.weightKg, it.reps) }.maxOrNull()
    val lifts = cur.filter { ex[it.exerciseId]?.measurementType == MeasurementType.WEIGHT_REPS }.groupBy { it.exerciseId }
        .mapNotNull { (id, l) ->
            val e = ex[id] ?: return@mapNotNull null
            val now = best(l) ?: return@mapNotNull null
            Lift(e, now, best(prev.filter { it.exerciseId == id }), l.map { it.workoutId }.distinct().size)
        }.sortedByDescending { it.sessions }.take(6)
    return Stats(cur.map { it.workoutId }.distinct().size, cur.size, vol(cur), prev.map { it.workoutId }.distinct().size, prev.size, vol(prev),
        bars, muscle.entries.map { it.key to it.value / weeks }.sortedByDescending { it.second }, lifts, weeks)
}

@Composable
fun TrainProgressScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val u = LocalSettings.current.units
    var spanI by rememberSaveable { mutableIntStateOf(0) }
    val span = Span.entries[spanI]
    val rows by remember { container.workoutRepo.allHistory() }.collectAsState(initial = null)
    val exList by container.exerciseRepo.everything.collectAsState(initial = emptyList())
    val stats by produceState<Stats?>(null, rows, exList, span) {
        val r = rows ?: return@produceState
        val m = exList.associateBy { it.id }
        value = withContext(Dispatchers.Default) { compute(r, m, span) }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Training progress", { nav.pop() }, "From your logged sets only")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Span.entries.forEachIndexed { i, s -> GlassChip(s.label, i == spanI, { spanI = i }, Modifier.weight(1f)) }
                }
            }
            val s = stats
            if (s == null) { item { Caption("Loading…", Modifier.padding(6.dp)) }; return@LazyColumn }
            if (s.sets == 0 && s.pSets == 0) item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No workouts in this period yet", style = FitType.section, color = th.text)
                        Caption("Finish a gym workout and your progress shows up here.")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("Workouts", "${s.workouts}", delta(s.workouts.toDouble(), s.pWorkouts.toDouble()), Modifier.weight(1f))
                    Metric("Working sets", "${s.sets}", delta(s.sets.toDouble(), s.pSets.toDouble()), Modifier.weight(1f))
                    Metric("Volume", if (s.volume > 0) Fmt.weight(s.volume, u.weight, 0) else "—", delta(s.volume, s.pVolume), Modifier.weight(1.2f))
                }
            }
            item { Caption("Compared with the ${span.label.lowercase()} before. Volume = weight × reps, warm-ups excluded.", Modifier.padding(horizontal = 6.dp)) }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Volume " + if (span.bucketDays == 1L) "per day" else "per week", style = FitType.label, color = th.text)
                        Spacer(Modifier.height(10.dp))
                        Bars(s.bars)
                    }
                }
            }
            if (s.muscles.isNotEmpty()) {
                item { SectionTitle("Sets per muscle · weekly average") }
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val max = (s.muscles.maxOf { it.second }).coerceAtLeast(20.0)
                            s.muscles.forEach { (m, v) ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(m, style = FitType.label, color = th.text, modifier = Modifier.width(92.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(th.text.copy(alpha = 0.08f))) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).clip(CircleShape)
                                            .background(if (v >= 10) th.accentBright else th.accent.copy(alpha = 0.6f)))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(Fmt.trim(v, 1), style = FitType.label, color = th.textDim, modifier = Modifier.width(36.dp))
                                }
                            }
                            Caption("Secondary muscles count as half a set. Around 10–20 hard sets a week per muscle is a common range for growth.")
                        }
                    }
                }
            }
            if (s.lifts.isNotEmpty()) {
                item { SectionTitle("Strength · estimated 1-rep max") }
                items(s.lifts, key = { it.ex.id }) { l ->
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), onClick = { nav.push(Overlay.ExerciseDetail(l.ex.id)) }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(l.ex.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Caption("${l.sessions} session${if (l.sessions == 1) "" else "s"} · est. from your best set")
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(Fmt.weight(l.now, u.weight, 1), style = FitType.section, color = th.text)
                                val d = l.before?.let { l.now - it }
                                Caption(when {
                                    d == null -> "new this period"
                                    d > 0.05 -> "▲ " + Fmt.weight(d, u.weight, 1)
                                    d < -0.05 -> "▼ " + Fmt.weight(-d, u.weight, 1)
                                    else -> "same as before"
                                }, color = when { d == null -> null; d > 0.05 -> th.success; d < -0.05 -> th.warning; else -> null })
                            }
                        }
                    }
                }
                item { Caption("Estimates (Epley formula) from sets of 1–12 reps — not a weight you've actually lifted.", Modifier.padding(horizontal = 6.dp)) }
            }
            item {
                Glass(Modifier.fillMaxWidth(), onClick = { nav.push(Overlay.Records) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("All personal records", style = FitType.label, color = th.text, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Icon(com.myfit.tracker.ui.theme.Duo.KeyboardArrowRight, null, tint = th.textDim)
                    }
                }
            }
        }
    }
}

private fun delta(now: Double, before: Double): Pair<String, Boolean?>? = when {
    before <= 0.0 && now <= 0.0 -> null
    before <= 0.0 -> "new" to true
    else -> ((now - before) / before * 100).roundToInt().let { p -> (if (p > 0) "▲ $p%" else if (p < 0) "▼ ${-p}%" else "same") to (if (p > 0) true else if (p < 0) false else null) }
}

@Composable
private fun Metric(label: String, value: String, d: Pair<String, Boolean?>?, modifier: Modifier) {
    val th = LocalFitTheme.current
    Glass(modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(12.dp)) {
            Caption(label)
            com.myfit.tracker.ui.components.FitText(value, FitType.title, th.text)
            if (d != null) Caption(d.first, color = when (d.second) { true -> th.success; false -> th.warning; null -> null })
        }
    }
}

@Composable
private fun Bars(bars: List<Pair<String, Double>>) {
    val th = LocalFitTheme.current
    val max = (bars.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val n = bars.size
        val gap = size.width / n * 0.28f
        val w = size.width / n - gap
        bars.forEachIndexed { i, (_, v) ->
            val h = (v / max * size.height).toFloat().coerceAtLeast(if (v > 0) 4f else 2f)
            drawRoundRect(if (v > 0) th.accentBright else th.text.copy(alpha = 0.1f), Offset(i * (w + gap) + gap / 2, size.height - h), Size(w, h), CornerRadius(w / 3, w / 3))
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        bars.forEach { (l, _) -> Text(l, style = FitType.overline, color = th.textDim, modifier = Modifier.weight(1f), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
