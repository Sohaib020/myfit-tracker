package com.myfit.tracker.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.FastingSession
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.reminders.ReminderScheduler
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.DateTimeRow
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.components.Stepper
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class FastPreset(val key: String, val label: String, val hours: Double)
private val FAST_PRESETS = listOf(
    FastPreset("12", "12:12", 12.0), FastPreset("14", "14:10", 14.0), FastPreset("16", "16:8", 16.0),
    FastPreset("18", "18:6", 18.0), FastPreset("20", "20:4", 20.0), FastPreset("omad", "OMAD 23:1", 23.0),
)
private const val RAMADAN = "Ramadan"
private const val HOUR_MS = 3_600_000L
private val startFmt get() = com.myfit.tracker.domain.ClockFmt.f("EEE d MMM, ")

/** Educational, deliberately modest stage labels. */
private fun stageOf(hours: Double): Pair<String, String> = when {
    hours < 4 -> "Fed state" to "Your body is still digesting and using your last meal."
    hours < 8 -> "Early fast" to "Blood sugar and insulin drift back towards baseline."
    hours < 12 -> "Post-absorptive" to "Your body starts leaning more on stored energy."
    hours < 18 -> "Fat-burning gear (approx.)" to "Fat use typically rises around here — timing varies from person to person."
    hours < 24 -> "Deeper fast" to "Ketone levels may begin to rise. This varies a lot between people."
    else -> "Extended fast" to "Long fasts aren't for everyone. Stop if you feel unwell and check with a health professional."
}

private fun durOf(f: FastingSession, now: Long): Long = ((f.endAt ?: now) - f.startAt).coerceAtLeast(0)
private fun pctOf(f: FastingSession, now: Long): Int =
    if (f.targetHours <= 0) 0 else (durOf(f, now) * 100.0 / (f.targetHours * HOUR_MS)).toInt()

private fun nextAtMinute(minOfDay: Int, after: Long): Long {
    val zone = Clock.zone()
    val d = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
    val t = d.atTime(minOfDay / 60, minOfDay % 60).atZone(zone).toInstant().toEpochMilli()
    return if (t > after) t else d.plusDays(1).atTime(minOfDay / 60, minOfDay % 60).atZone(zone).toInstant().toEpochMilli()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FastingContent(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val dao = container.db.fastingDao()
    val sessions by remember { dao.observeAll() }.collectAsState(initial = null)
    var preset by remember { mutableStateOf("16") }
    var customHours by remember { mutableIntStateOf(16) }
    var iftar by remember { mutableIntStateOf(18 * 60 + 30) }
    var notify by remember { mutableStateOf(true) }
    var editStart by remember { mutableStateOf<FastingSession?>(null) }

    val all = sessions
    val active = all?.firstOrNull { it.endAt == null }

    fun start() {
        val now = Clock.now()
        val target: Double = when (preset) {
            "custom" -> customHours.toDouble()
            RAMADAN -> ((nextAtMinute(iftar, now) - now).toDouble() / HOUR_MS).coerceAtLeast(0.25)
            else -> FAST_PRESETS.firstOrNull { it.key == preset }?.hours ?: 16.0
        }
        val st = Stamp.of(now)
        container.write {
            if (dao.activeNow() == null) {
                dao.insert(
                    FastingSession(
                        startAt = now, endAt = null, targetHours = target, zoneId = st.zoneId, localDate = st.localDate,
                        notes = if (preset == RAMADAN) RAMADAN else "", createdAt = now, updatedAt = now,
                    )
                )
            }
        }
        ReminderScheduler.setFastAlarm(ctx, if (notify) now + (target * HOUR_MS).toLong() else null)
        toaster.show("Fast started")
    }

    fun end(f: FastingSession) {
        val now = Clock.now()
        container.write { dao.update(f.copy(endAt = now, updatedAt = now)) }
        ReminderScheduler.setFastAlarm(ctx, null)
        toaster.show(if (pctOf(f, now) >= 100) "Fast complete — well done" else "Fast ended")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar("Fasting", { nav.pop() }, subtitle = "Time-restricted eating timer")
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (all == null) item { Caption("Loading…") }
                else if (active != null) item(key = "active_${active.id}") {
                    ActiveFastCard(active, onEnd = { end(active) }, onEditStart = { editStart = active })
                } else item(key = "start") {
                    GlassCard {
                        CardHeader(Duo.Timer, "Start a fast", th.accent)
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FAST_PRESETS.forEach { p -> GlassChip(p.label, preset == p.key, { preset = p.key }) }
                            GlassChip("Custom", preset == "custom", { preset = "custom" })
                            GlassChip("Ramadan fast", preset == RAMADAN, { preset = RAMADAN })
                        }
                        Spacer(Modifier.height(12.dp))
                        when (preset) {
                            "custom" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Fast for", style = FitType.label, color = th.textDim, modifier = Modifier.weight(1f))
                                Stepper(customHours, { customHours = it }, 1..72, label = { "$it h" })
                            }
                            RAMADAN -> Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Iftar time", style = FitType.label, color = th.textDim, modifier = Modifier.weight(1f))
                                    MinuteOfDayChip(iftar) { iftar = it }
                                }
                                Spacer(Modifier.height(6.dp))
                                Caption("Start when you finish sehri. The timer runs until iftar.")
                            }
                            else -> {
                                val p = FAST_PRESETS.firstOrNull { it.key == preset }
                                if (p != null) Caption("Fast ${fmtNum(p.hours)} h, eat within ${fmtNum(24 - p.hours)} h.")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        SwitchRow("Notify me when I reach my goal", null, notify, { notify = it })
                        Spacer(Modifier.height(8.dp))
                        AccentButton("Start fasting", { start() }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow)
                    }
                }

                val done = all.orEmpty().filter { it.endAt != null }
                if (done.isNotEmpty()) item(key = "stats") { FastStatsCard(done) }

                if (done.isNotEmpty()) item(key = "history") {
                    GlassCard {
                        CardHeader(Duo.History, "History", th.sleep)
                        Spacer(Modifier.height(6.dp))
                        done.take(40).forEach { f ->
                            val now = Clock.now()
                            val pct = pctOf(f, now)
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    val started = Instant.ofEpochMilli(f.startAt).atZone(Clock.zone()).format(startFmt)
                                    Text(
                                        "${fmtDur(durOf(f, now))} of ${fmtNum(Math.round(f.targetHours * 10) / 10.0)} h" + if (f.notes == RAMADAN) " · Ramadan" else "",
                                        style = FitType.body, color = th.text,
                                    )
                                    Caption("$started · $pct%", color = if (pct >= 100) th.success else th.textDim)
                                }
                                GlassIconButton(Duo.DeleteOutline, {
                                    container.write { dao.softDelete(f.id, Clock.now()) }
                                    toaster.show("Fast deleted")
                                }, size = 36.dp, tint = th.textDim)
                            }
                        }
                    }
                }

                item(key = "disclaimer") {
                    Caption(
                        "Fasting info here is general and approximate, not medical advice. If you're pregnant, diabetic, take medication or have a history of disordered eating, talk to a professional before fasting.",
                        Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }

        val es = editStart
        GlassSheet(visible = es != null, onDismiss = { editStart = null }) {
            if (es != null) EditStartForm(es) { newStart ->
                editStart = null
                container.write {
                    val st = Stamp.of(newStart)
                    dao.update(es.copy(startAt = newStart, localDate = st.localDate, zoneId = st.zoneId, updatedAt = Clock.now()))
                }
                val goalAt = newStart + (es.targetHours * HOUR_MS).toLong()
                ReminderScheduler.setFastAlarm(ctx, if (goalAt > Clock.now()) goalAt else null)
                toaster.show("Start time updated")
            }
        }
    }
}

@Composable
private fun ActiveFastCard(f: FastingSession, onEnd: () -> Unit, onEditStart: () -> Unit) {
    val th = LocalFitTheme.current
    // ticks once per second, and only while the screen is visible (collection stops below STARTED)
    val now by remember { flow { while (true) { emit(System.currentTimeMillis()); delay(1000) } } }
        .collectAsStateWithLifecycle(initialValue = System.currentTimeMillis())
    val elapsed = (now - f.startAt).coerceAtLeast(0)
    val targetMs = (f.targetHours * HOUR_MS).toLong().coerceAtLeast(1)
    val remaining = targetMs - elapsed
    val reached = remaining <= 0
    val (stage, stageInfo) = stageOf(elapsed.toDouble() / HOUR_MS)
    GlassCard {
        CardHeader(Duo.Timer, if (f.notes == RAMADAN) "Ramadan fast" else "Fasting", if (reached) th.success else th.accent)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ProgressRing(elapsed.toFloat() / targetMs, if (reached) th.success else th.accent, size = 220.dp, stroke = 14.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ELAPSED", style = FitType.overline, color = th.textDim)
                    Text(fmtClock(elapsed), style = FitType.display, color = th.text)
                    Text(
                        if (reached) "Goal reached · +${fmtDur(-remaining)}" else "${fmtDur(remaining)} left",
                        style = FitType.label, color = if (reached) th.success else th.textDim, textAlign = TextAlign.Center,
                    )
                    Text("${(elapsed * 100 / targetMs)}% of ${fmtNum(Math.round(f.targetHours * 10) / 10.0)} h", style = FitType.caption, color = th.textFaint)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(stage, style = FitType.section, color = th.text)
        Caption(stageInfo)
        Caption("Educational, approximate info.", color = th.textFaint)
        Spacer(Modifier.height(10.dp))
        val started = Instant.ofEpochMilli(f.startAt).atZone(Clock.zone()).format(startFmt)
        val ends = Instant.ofEpochMilli(f.startAt + targetMs).atZone(Clock.zone()).format(startFmt)
        Caption("Started $started · Goal $ends")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Edit start", onEditStart, Modifier.weight(1f), icon = Duo.Edit, height = 48.dp)
            AccentButton(if (reached) "Finish fast" else "End early", onEnd, Modifier.weight(1f), icon = Duo.Stop, height = 48.dp)
        }
    }
}

@Composable
private fun FastStatsCard(done: List<FastingSession>) {
    val th = LocalFitTheme.current
    val now = Clock.now()
    val today = Clock.today()
    val weekFrom = Clock.dateKey(today.minusDays(6))
    val week = done.filter { it.localDate >= weekFrom }
    val weekHours = week.sumOf { durOf(it, now) } / HOUR_MS.toDouble()
    val longest = done.maxOfOrNull { durOf(it, now) } ?: 0L
    val hitDays = done.filter { pctOf(it, now) >= 100 }.map { it.localDate }.toSet()
    var d: LocalDate = if (hitDays.contains(Clock.dateKey(today))) today else today.minusDays(1)
    var streak = 0
    while (hitDays.contains(Clock.dateKey(d)) && streak < 3650) { streak++; d = d.minusDays(1) }
    val completion = if (week.isEmpty()) null else week.map { pctOf(it, now).coerceAtMost(100) }.average().toInt()

    GlassCard {
        CardHeader(Duo.Insights, "This week", th.water)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell("Fasts", week.size.toString(), Modifier.weight(1f))
            StatCell("Hours", fmtNum(Math.round(weekHours * 10) / 10.0), Modifier.weight(1f))
            StatCell("Avg goal", completion?.let { "$it%" } ?: "–", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell("Longest", fmtDur(longest), Modifier.weight(1f))
            StatCell("Streak", if (streak == 1) "1 day" else "$streak days", Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Caption("Streak counts consecutive days with a fast that reached its goal.")
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Text(label.uppercase(), style = FitType.overline, color = th.textDim)
        Spacer(Modifier.height(2.dp))
        Text(value, style = FitType.title, color = th.text)
    }
}

@Composable
private fun EditStartForm(f: FastingSession, onSave: (Long) -> Unit) {
    val th = LocalFitTheme.current
    var start by remember(f.id) { mutableLongStateOf(f.startAt) }
    val valid = start < Clock.now()
    Text("Edit start time", style = FitType.title, color = th.text)
    Spacer(Modifier.height(12.dp))
    DateTimeRow("Started", start, { start = it })
    if (!valid) Caption("Start time can't be in the future.", Modifier.padding(top = 6.dp), color = th.warning)
    Spacer(Modifier.height(16.dp))
    AccentButton("Save", { onSave(start) }, Modifier.fillMaxWidth(), icon = Duo.Check, enabled = valid)
}
