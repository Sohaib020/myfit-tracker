package com.myfit.tracker.ui.timeline

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MeasurementSite
import com.myfit.tracker.data.repo.DayLog
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.SleepCalc
import com.myfit.tracker.domain.StepsCalc
import com.myfit.tracker.domain.UnitPrefs
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.pretty
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.data.repo.WorkoutView
import com.myfit.tracker.data.db.WorkoutStatus
import androidx.compose.material.icons.rounded.FitnessCenter
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class TimelineItem(
    val key: String, val at: Long, val zoneId: String, val icon: ImageVector, val color: Color,
    val title: String, val detail: String, val sheet: Sheet?, val overlay: Overlay? = null,
)

fun buildTimeline(d: DayLog, u: UnitPrefs, th: FitTheme): List<TimelineItem> = buildList {
    d.weight.forEach { add(TimelineItem("w${it.id}", it.loggedAt, it.zoneId, Icons.Rounded.MonitorWeight, th.accentBright, "Weight", Fmt.weight(it.weightKg, u.weight, 2) + (it.bodyFatPct?.let { f -> " · ${Fmt.trim(f)} % fat" } ?: "") + (if (it.note.isNotBlank()) " · ${it.note}" else ""), Sheet.Weight(it.id))) }
    d.water.forEach { add(TimelineItem("h${it.id}", it.loggedAt, it.zoneId, Icons.Rounded.WaterDrop, th.water, "Water", Fmt.volume(it.amountMl, u.volume), Sheet.Water(it.id))) }
    d.measurements.forEach { add(TimelineItem("m${it.id}", it.loggedAt, it.zoneId, Icons.Rounded.Straighten, th.fat, MeasurementSite.label(it.type, it.customName), Fmt.length(it.valueCm, u.length), Sheet.Measurement(it.id))) }
    d.sleep.forEach { add(TimelineItem("s${it.id}", it.endAt, it.zoneId, Icons.Rounded.Bedtime, th.sleep, "Sleep", (SleepCalc.minutes(it.startAt, it.endAt)?.let { m -> Fmt.duration(m) } ?: "invalid") + " · ${Fmt.clock(Clock.minuteOfDay(it.startAt, it.zoneId))}–${Fmt.clock(Clock.minuteOfDay(it.endAt, it.zoneId))}" + (it.quality?.let { q -> " · quality $q/10" } ?: ""), Sheet.Sleep(it.id))) }
    d.activity.forEach { add(TimelineItem("a${it.id}", it.loggedAt, it.zoneId, Icons.Rounded.DirectionsWalk, th.steps, if (it.isDayTotal) "Steps (day total)" else "Steps added", listOfNotNull(it.steps?.let { s -> "${Fmt.int(s)} steps" }, it.distanceM?.let { m -> Fmt.distance(m, u.distance) }, it.activeMinutes?.let { m -> "$m active min" }).joinToString(" · "), Sheet.Steps(it.id))) }
    d.checkIns.forEach { add(TimelineItem("c${it.id}", it.loggedAt, it.zoneId, Icons.Rounded.Mood, th.warning, "Check-in", listOfNotNull(it.energy?.let { v -> "energy $v" }, it.mood?.let { v -> "mood $v" }, it.stress?.let { v -> "stress $v" }).joinToString(" · ").ifEmpty { "notes only" }, Sheet.CheckIn(it.id))) }
    d.notes.forEach { add(TimelineItem("n${it.id}", it.loggedAt, it.zoneId, Icons.Rounded.EditNote, th.textDim, "Note", it.text, Sheet.Note(it.id))) }
}.sortedBy { it.at }

fun workoutItems(ws: List<WorkoutView>, u: UnitPrefs, th: FitTheme): List<TimelineItem> = ws.flatMap { w ->
    val t = w.totals
    val ov = if (w.workout.status == WorkoutStatus.IN_PROGRESS) Overlay.Gym(w.workout.id) else Overlay.WorkoutDetail(w.workout.id)
    val summary = "${t.exercises} exercises · ${t.sets} sets · ${Fmt.int(t.reps)} reps" + (t.volumeKg?.let { " · ${Fmt.weight(it, u.weight, 0)}" } ?: "")
    listOfNotNull(
        TimelineItem("ws${w.workout.id}", w.workout.startedAt, w.workout.zoneId, Icons.Rounded.FitnessCenter, th.accentBright,
            "Workout started", w.workout.name, null, ov),
        w.workout.endedAt?.let {
            TimelineItem("we${w.workout.id}", it, w.workout.zoneId, Icons.Rounded.FitnessCenter, th.success,
                "Workout finished · ${mmss((it - w.workout.startedAt) / 1000)}", summary, null, ov)
        },
    )
}

@Composable
fun TimelineScreen(container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    var date by remember { mutableStateOf(Clock.today()) }
    val nav = LocalNav.current
    val day by remember(date) { container.logRepo.day(date) }.collectAsState(initial = DayLog(date))
    val workouts by remember(date) { container.workoutRepo.dayViews(Clock.dateKey(date)) }.collectAsState(initial = emptyList())
    val hcSessions by remember(date) { container.healthRepo.sessionsRange(date, date) }.collectAsState(initial = emptyList())
    val hcSleep by remember(date) { container.healthRepo.sleepRange(date, date) }.collectAsState(initial = emptyList())
    val items = remember(day, workouts, hcSessions, hcSleep, u, th) {
        val detected = hcSessions.map { s ->
            TimelineItem("hc${s.id}", s.startAt, s.zoneId, com.myfit.tracker.ui.activity.sessionIcon(s.exerciseType), th.accentBright,
                com.myfit.tracker.ui.activity.sessionTitle(s) + " · " + com.myfit.tracker.health.HealthSync.sourceLabel(s.sourcePackage),
                listOfNotNull(mmss((s.endAt - s.startAt) / 1000), s.distanceM?.takeIf { it > 0 }?.let { Fmt.distance(it, u.distance) }, s.avgHr?.let { "avg $it bpm" }).joinToString(" · "),
                null, Overlay.Activity)
        } + hcSleep.map { s ->
            TimelineItem("hs${s.id}", s.endAt, s.zoneId, Icons.Rounded.Bedtime, th.sleep, "Sleep · " + com.myfit.tracker.health.HealthSync.sourceLabel(s.sourcePackage),
                Fmt.duration((s.endAt - s.startAt) / 60_000) + " · ${Fmt.clock(Clock.minuteOfDay(s.startAt, s.zoneId))}–${Fmt.clock(Clock.minuteOfDay(s.endAt, s.zoneId))}", null, Overlay.Activity)
        }
        (buildTimeline(day, u, th) + workoutItems(workouts, u, th) + detected).sortedBy { it.at }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
                Text("Daily log", style = FitType.display, color = th.text)
                Caption("Every entry, in the order it happened. Tap one to edit or delete.")
                Spacer(Modifier.height(14.dp))
                WeekStrip(date) { date = it }
            }
        }
        item { Coverage(day) }
        if (items.isEmpty()) {
            item {
                Glass(Modifier.fillMaxWidth().height(110.dp)) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nothing recorded on ${date.pretty()}", style = FitType.section, color = th.text)
                        Caption("Missing days stay missing — they're never counted as zero.")
                    }
                }
            }
        }
        items(items, key = { it.key }) { row -> TimelineRow(row) { row.sheet?.let(open); row.overlay?.let { nav.push(it) } } }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val th = LocalFitTheme.current
    val monday = selected.with(DayOfWeek.MONDAY)
    val today = Clock.today()
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, { onSelect(selected.minusWeeks(1)) }, size = 36.dp)
        Row(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { i ->
                val d = monday.plusDays(i.toLong())
                val sel = d == selected
                val future = d.isAfter(today)
                Glass(
                    Modifier.width(40.dp).height(62.dp), shape = RoundedCornerShape(20.dp),
                    onClick = if (future) null else ({ onSelect(d) }), pressScale = 0.9f,
                ) {
                    if (sel) Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent))) })
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US), style = FitType.caption, color = if (sel) th.onAccent else th.textDim)
                        Text("${d.dayOfMonth}", style = FitType.section, color = when { sel -> th.onAccent; future -> th.textFaint; else -> th.text })
                        if (d == today) Box(Modifier.size(4.dp).drawBehind { drawCircle(if (sel) th.onAccent else th.accentBright) })
                    }
                }
            }
        }
        GlassIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, { if (selected.plusWeeks(1) <= today) onSelect(selected.plusWeeks(1)) else onSelect(today) }, size = 36.dp)
    }
}

@Composable
private fun Coverage(d: DayLog) {
    val th = LocalFitTheme.current
    val steps = StepsCalc.dayTotal(d.activity.filter { it.steps != null }.map { StepsCalc.Entry(it.steps!!, it.isDayTotal, it.loggedAt, it.id) })
    val parts = listOf(
        "Weight" to d.weight.isNotEmpty(), "Water" to d.water.isNotEmpty(), "Sleep" to d.sleep.isNotEmpty(),
        "Steps" to (steps != null), "Check-in" to d.checkIns.isNotEmpty(),
    )
    Glass(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text("DATA RECORDED THIS DAY", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                parts.forEach { (l, ok) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(12.dp).drawBehind {
                            if (ok) drawCircle(th.success) else drawCircle(th.textFaint, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        })
                        Spacer(Modifier.height(4.dp))
                        Caption(l, color = if (ok) th.text else th.textFaint)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.clock(Clock.minuteOfDay(item.at, item.zoneId)), style = FitType.label, color = th.textDim, modifier = Modifier.width(46.dp))
            IconBubble(item.icon, item.color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = FitType.section, color = th.text)
                Text(item.detail, style = FitType.caption, color = th.textDim, maxLines = 2)
            }
        }
    }
}
