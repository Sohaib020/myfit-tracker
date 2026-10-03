package com.myfit.tracker.ui.timeline

import com.myfit.tracker.ui.theme.Duo

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
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.myfit.tracker.ui.components.clickableNoRipple
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

/** Category a log entry belongs to (one expandable tile per category in the daily log). */
enum class LogGroup(val label: String) { FOOD("Food"), HYDRATION("Hydration"), WORKOUT("Workouts & activity"), WEIGHT("Weight & body"), SLEEP("Sleep"), STEPS("Steps"), CHECKIN("Check-ins"), NOTES("Notes") }

fun groupOf(key: String): LogGroup = when {
    key.startsWith("hs") || key.startsWith("s") -> LogGroup.SLEEP
    key.startsWith("hc") || key.startsWith("ws") || key.startsWith("we") -> LogGroup.WORKOUT
    key.startsWith("f") -> LogGroup.FOOD
    key.startsWith("h") -> LogGroup.HYDRATION
    key.startsWith("w") || key.startsWith("m") -> LogGroup.WEIGHT
    key.startsWith("a") -> LogGroup.STEPS
    key.startsWith("c") -> LogGroup.CHECKIN
    else -> LogGroup.NOTES
}

fun buildTimeline(d: DayLog, u: UnitPrefs, th: FitTheme): List<TimelineItem> = buildList {
    d.weight.forEach { add(TimelineItem("w${it.id}", it.loggedAt, it.zoneId, Duo.MonitorWeight, th.accentBright, "Weight", Fmt.weight(it.weightKg, u.weight, 2) + (it.bodyFatPct?.let { f -> " · ${Fmt.trim(f)} % fat" } ?: "") + (if (it.note.isNotBlank()) " · ${it.note}" else ""), Sheet.Weight(it.id))) }
    d.water.forEach { add(TimelineItem("h${it.id}", it.loggedAt, it.zoneId, Duo.WaterDrop, th.water, "Water", Fmt.volume(it.amountMl, u.volume), Sheet.Water(it.id))) }
    d.measurements.forEach { add(TimelineItem("m${it.id}", it.loggedAt, it.zoneId, Duo.Straighten, th.fat, MeasurementSite.label(it.type, it.customName), Fmt.length(it.valueCm, u.length), Sheet.Measurement(it.id))) }
    d.sleep.forEach { add(TimelineItem("s${it.id}", it.endAt, it.zoneId, Duo.Bedtime, th.sleep, "Sleep", (SleepCalc.minutes(it.startAt, it.endAt)?.let { m -> Fmt.duration(m) } ?: "invalid") + " · ${Fmt.clock(Clock.minuteOfDay(it.startAt, it.zoneId))}–${Fmt.clock(Clock.minuteOfDay(it.endAt, it.zoneId))}" + (it.quality?.let { q -> " · quality $q/10" } ?: ""), Sheet.Sleep(it.id))) }
    d.activity.forEach { add(TimelineItem("a${it.id}", it.loggedAt, it.zoneId, Duo.DirectionsWalk, th.steps, if (it.isDayTotal) "Steps (day total)" else "Steps added", listOfNotNull(it.steps?.let { s -> "${Fmt.int(s)} steps" }, it.distanceM?.let { m -> Fmt.distance(m, u.distance) }, it.activeMinutes?.let { m -> "$m active min" }).joinToString(" · "), Sheet.Steps(it.id))) }
    d.checkIns.forEach { add(TimelineItem("c${it.id}", it.loggedAt, it.zoneId, Duo.Mood, th.warning, "Check-in", listOfNotNull(it.energy?.let { v -> "energy $v" }, it.mood?.let { v -> "mood $v" }, it.stress?.let { v -> "stress $v" }).joinToString(" · ").ifEmpty { "notes only" }, Sheet.CheckIn(it.id))) }
    d.notes.forEach { add(TimelineItem("n${it.id}", it.loggedAt, it.zoneId, Duo.EditNote, th.textDim, "Note", it.text, Sheet.Note(it.id))) }
}.sortedBy { it.at }

fun workoutItems(ws: List<WorkoutView>, u: UnitPrefs, th: FitTheme): List<TimelineItem> = ws.flatMap { w ->
    val t = w.totals
    val ov = if (w.workout.status == WorkoutStatus.IN_PROGRESS) Overlay.Gym(w.workout.id) else Overlay.WorkoutDetail(w.workout.id)
    val summary = "${t.exercises} exercises · ${t.sets} sets · ${Fmt.int(t.reps)} reps" + (t.volumeKg?.let { " · ${Fmt.weight(it, u.weight, 0)}" } ?: "")
    listOfNotNull(
        TimelineItem("ws${w.workout.id}", w.workout.startedAt, w.workout.zoneId, Duo.FitnessCenter, th.accentBright,
            "Workout started", w.workout.name, null, ov),
        w.workout.endedAt?.let {
            TimelineItem("we${w.workout.id}", it, w.workout.zoneId, Duo.FitnessCenter, th.success,
                "Workout finished · ${mmss((it - w.workout.startedAt) / 1000)}", summary, null, ov)
        },
    )
}

@Composable
fun TimelineScreen(container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int, startDate: java.time.LocalDate? = null, onBack: (() -> Unit)? = null) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    var date by remember { mutableStateOf(startDate ?: Clock.today()) }
    val nav = LocalNav.current
    val day by remember(date) { container.logRepo.day(date) }.collectAsState(initial = DayLog(date))
    val workouts by remember(date) { container.workoutRepo.dayViews(Clock.dateKey(date)) }.collectAsState(initial = emptyList())
    val hcSessions by remember(date) { container.healthRepo.sessionsRange(date, date) }.collectAsState(initial = emptyList())
    val hcSleep by remember(date) { container.healthRepo.sleepRange(date, date) }.collectAsState(initial = emptyList())
    val food by remember(date) { container.nutritionRepo.itemsOn(date) }.collectAsState(initial = emptyList())
    val items = remember(day, workouts, hcSessions, hcSleep, food, u, th) {
        val detected = hcSessions.map { s ->
            TimelineItem("hc${s.id}", s.startAt, s.zoneId, com.myfit.tracker.ui.activity.sessionIcon(s.exerciseType), th.accentBright,
                com.myfit.tracker.ui.activity.sessionTitle(s) + " · " + com.myfit.tracker.health.HealthSync.sourceLabel(s.sourcePackage),
                listOfNotNull(mmss((s.endAt - s.startAt) / 1000), s.distanceM?.takeIf { it > 0 }?.let { Fmt.distance(it, u.distance) }, s.avgHr?.let { "avg $it bpm" }).joinToString(" · "),
                null, Overlay.Activity)
        } + hcSleep.map { s ->
            TimelineItem("hs${s.id}", s.endAt, s.zoneId, Duo.Bedtime, th.sleep, "Sleep · " + com.myfit.tracker.health.HealthSync.sourceLabel(s.sourcePackage),
                Fmt.duration((s.endAt - s.startAt) / 60_000) + " · ${Fmt.clock(Clock.minuteOfDay(s.startAt, s.zoneId))}–${Fmt.clock(Clock.minuteOfDay(s.endAt, s.zoneId))}", null, Overlay.Activity)
        } + food.map { f ->
            TimelineItem("f${f.id}", f.createdAt, java.time.ZoneId.systemDefault().id, Duo.ForkKnife, th.protein, f.foodName,
                "${Fmt.int(f.quantity * f.caloriesPerServing)} kcal · P ${Fmt.int(f.quantity * f.proteinPerServing)} · C ${Fmt.int(f.quantity * f.carbsPerServing)} · F ${Fmt.int(f.quantity * f.fatPerServing)} g",
                null, Overlay.Food(Clock.dateKey(date)))
        }
        (buildTimeline(day, u, th) + workoutItems(workouts, u, th) + detected).sortedBy { it.at }
    }
    val groups = remember(items) { items.groupBy { groupOf(it.key) }.toSortedMap(compareBy { it.ordinal }) }
    var expanded by remember(date) { mutableStateOf<LogGroup?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = if (onBack != null) 8.dp else com.myfit.tracker.ui.components.TopBarSpace)) {
                if (onBack != null) {
                    com.myfit.tracker.ui.theme.GlassIconButton(com.myfit.tracker.ui.theme.Duo.ArrowBack, onBack)
                    Spacer(Modifier.height(10.dp))
                }
                Text("Daily log", modifier = Modifier.padding(end = 62.dp), style = FitType.display, color = th.text)
                Caption("Tap a category to see its entries. Tap an entry to edit or delete.")
                Spacer(Modifier.height(14.dp))
                WeekStrip(date) { date = it }
            }
        }
        if (items.isEmpty()) {
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nothing recorded on ${date.pretty()}", style = FitType.section, color = th.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Caption("Missing days stay missing — they're never counted as zero.", Modifier.fillMaxWidth())
                    }
                }
            }
        }
        groups.forEach { (g, list) ->
            item(key = "g_" + g.name) {
                GroupTile(g, list, expanded == g, onToggle = { expanded = if (expanded == g) null else g }) { row ->
                    row.sheet?.let(open); row.overlay?.let { nav.push(it) }
                }
            }
        }
    }
}

@Composable
private fun GroupTile(g: LogGroup, list: List<TimelineItem>, open: Boolean, onToggle: () -> Unit, onRow: (TimelineItem) -> Unit) {
    val th = LocalFitTheme.current
    val first = list.first()
    val rot by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Glass(Modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickableNoRipple(onToggle).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBubble(first.icon, first.color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.label, style = FitType.section, color = th.text)
                    Caption(groupSummary(g, list))
                }
                Text("${list.size}", style = FitType.label, color = th.textDim)
                Spacer(Modifier.width(6.dp))
                androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rot })
            }
            if (open) Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { row -> EntryRow(row) { onRow(row) } }
            }
        }
    }
}

private fun groupSummary(g: LogGroup, list: List<TimelineItem>): String = when (g) {
    LogGroup.FOOD -> "${list.size} item${if (list.size == 1) "" else "s"} · " + list.sumOf { it.detail.substringBefore(" kcal").replace(",", "").toDoubleOrNull() ?: 0.0 }.let { "${Fmt.int(it)} kcal" }
    LogGroup.HYDRATION -> "${list.size} drink${if (list.size == 1) "" else "s"} · last at ${Fmt.clock(Clock.minuteOfDay(list.last().at, list.last().zoneId))}"
    else -> list.last().let { "${it.title} · ${it.detail}" }.take(70)
}

@Composable
private fun EntryRow(item: TimelineItem, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (th.isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f)).clickableNoRipple(onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.clock(Clock.minuteOfDay(item.at, item.zoneId)), style = FitType.label, color = th.textDim, modifier = Modifier.width(50.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = FitType.body, color = th.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (item.detail.isNotBlank()) Text(item.detail, style = FitType.caption, color = th.textDim, maxLines = 2)
        }
        androidx.compose.material3.Icon(Duo.Edit, null, tint = th.textFaint, modifier = Modifier.size(16.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WeekStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    val days = 400
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    // index 0 = oldest day, last = today
    fun indexOf(d: LocalDate) = (days - 1 - java.time.temporal.ChronoUnit.DAYS.between(d, today).toInt()).coerceIn(0, days - 1)
    val state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (indexOf(selected) - 3).coerceAtLeast(0))
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
    // a light haptic tick for every day that scrolls past
    androidx.compose.runtime.LaunchedEffect(state) {
        var last = state.firstVisibleItemIndex
        androidx.compose.runtime.snapshotFlow { state.firstVisibleItemIndex }.collect { if (it != last) { last = it; tick() } }
    }
    androidx.compose.runtime.LaunchedEffect(selected) {
        val i = indexOf(selected)
        val vis = state.layoutInfo.visibleItemsInfo
        if (vis.none { it.index == i } || vis.firstOrNull()?.index == i || vis.lastOrNull()?.index == i) state.animateScrollToItem((i - 3).coerceAtLeast(0))
    }
    androidx.compose.foundation.lazy.LazyRow(
        state = state, flingBehavior = fling,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(days) { i ->
            val d = today.minusDays((days - 1 - i).toLong())
            val sel = d == selected
            Glass(
                Modifier.width(46.dp).height(64.dp), shape = RoundedCornerShape(18.dp),
                onClick = { tick(); onSelect(d) }, pressScale = 0.9f,
            ) {
                if (sel) Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent))) })
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US), style = FitType.caption, color = if (sel) th.onAccent else th.textDim, maxLines = 1)
                    Text("${d.dayOfMonth}", style = FitType.label, color = if (sel) th.onAccent else th.text, maxLines = 1, softWrap = false)
                    if (d.dayOfMonth == 1 || d == today) Text(if (d == today) "today" else d.month.getDisplayName(TextStyle.SHORT, Locale.US), style = FitType.caption, color = if (sel) th.onAccent else th.accentBright, maxLines = 1)
                }
            }
        }
    }
}

/** The daily log as a full-screen page (opened from the calendar button on Home). */
@Composable
fun DayLogScreen(container: AppContainer, date: String?, open: (Sheet) -> Unit) {
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    TimelineScreen(container, open, 40, date?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }, onBack = { nav.pop() })
}
