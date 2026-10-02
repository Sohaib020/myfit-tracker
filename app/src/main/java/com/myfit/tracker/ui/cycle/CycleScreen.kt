package com.myfit.tracker.ui.cycle

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.CycleDay
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.CycleMath
import com.myfit.tracker.domain.CyclePhase
import com.myfit.tracker.domain.CyclePrediction
import com.myfit.tracker.domain.CycleToday
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassProgressBar
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val DISCLAIMER = "Predictions are estimates from your logs, not medical advice or contraception."
private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)

private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

/** Everything the screen and tile derive from the logged rows. */
private class CycleState(rows: List<CycleDay>, val today: LocalDate) {
    val byDate: Map<LocalDate, CycleDay> = rows.mapNotNull { r -> parseDate(r.localDate)?.let { it to r } }.toMap()
    val flows: Map<LocalDate, Int> = byDate.mapNotNull { (d, r) -> r.flow?.let { d to it } }.toMap()
    val bbt: Map<LocalDate, Double> = byDate.mapNotNull { (d, r) -> r.bbtC?.let { d to it } }.toMap()
    val otherLogs: Set<LocalDate> = byDate.filter { (_, r) ->
        r.symptoms.isNotBlank() || r.mood != null || r.notes.isNotBlank() || r.ovulationTest != null || r.mucus != null || r.bbtC != null || r.pillTaken != null
    }.keys
    val pred: CyclePrediction = CycleMath.predict(flows, today)
    val now: CycleToday = CycleMath.today(pred, today)
    val shifts: List<LocalDate> = CycleMath.bbtShifts(bbt)
}

@Composable
fun CycleScreen(container: AppContainer) {
    val nav = LocalNav.current
    val settings = LocalSettings.current
    val toaster = LocalToaster.current
    // Lives here (not in the intro) so the result still arrives after the intro is swapped out.
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res ->
        if (res.any { it in container.healthSync.cyclePermissions }) container.write {
            val n = runCatching { CycleHealth.importFromOthers(container) }.getOrDefault(0)
            if (n > 0) withContext(Dispatchers.Main) { toaster.show("Imported $n days from Health Connect") }
        }
    }
    val requestHc: () -> Unit = {
        if (container.healthSync.isAvailable) {
            runCatching { hcLauncher.launch(container.healthSync.cyclePermissions) }
                .onFailure { toaster.show("Couldn't open Health Connect") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar("Cycle", { nav.pop() }, "Private · stays on this phone")
            if (!settings.cycleEnabled) {
                CycleIntro(
                    onEnable = {
                        container.write { container.settings.setCycle(true) }
                        requestHc()
                    },
                    onNotNow = {
                        container.write { container.settings.setCycle(false) }
                        nav.pop()
                    },
                )
            } else {
                CycleContent(container, requestHc)
            }
        }
    }
}

// ------------------------------------------------------------------ intro / opt-in

@Composable
private fun CycleIntro(onEnable: () -> Unit, onNotNow: () -> Unit) {
    val th = LocalFitTheme.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard {
                IconBubble(Duo.CalendarMonth, CycleColors.period, 52.dp)
                Spacer(Modifier.height(14.dp))
                Text("Track your cycle, privately", style = FitType.title, color = th.text)
                Spacer(Modifier.height(6.dp))
                Caption("Log your period and how you feel. MyFit learns your pattern and adjusts training and food tips to each phase.")
                Spacer(Modifier.height(14.dp))
                IntroRow(Duo.CalendarMonth, CycleColors.period, "Next period & fertile window", "Calendar estimates that get better as you log.")
                IntroRow(Duo.EditNote, CycleColors.ovulation, "Flow, symptoms & mood", "Plus optional ovulation tests, temperature and pill.")
                IntroRow(Duo.FitnessCenter, th.accent, "Phase-aware tips", "Practical training and desi food ideas for each phase.")
            }
        }
        item {
            GlassCard {
                CardHeader(Duo.Lock, "Your privacy", th.success)
                Spacer(Modifier.height(10.dp))
                IntroRow(Duo.Lock, th.success, "Stays on this phone", "Never shown to friends, used in leaderboards, or uploaded by MyFit.")
                IntroRow(Duo.Sync, th.water, "Health Connect is optional", "Sync with apps like Samsung Health or Flo only if you allow it.")
                IntroRow(Duo.Info, th.warning, "Not contraception", "Predictions are estimates and must not be used to prevent pregnancy.")
            }
        }
        item {
            Column(Modifier.fillMaxWidth()) {
                AccentButton("Turn on cycle tracking", onEnable, Modifier.fillMaxWidth(), icon = Duo.Check)
                Spacer(Modifier.height(10.dp))
                GlassButton("Not now", onNotNow, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Caption("You can turn it off any time from this screen.", Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun IntroRow(icon: ImageVector, color: Color, title: String, sub: String) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, color, 30.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.body, color = th.text)
            Caption(sub)
        }
    }
}

// ------------------------------------------------------------------ main content

@Composable
private fun CycleContent(container: AppContainer, requestHc: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val rows by remember { container.db.cycleDao().observeAll() }.collectAsState(initial = null)
    val today = remember { Clock.today() }
    val state = remember(rows, today) { CycleState(rows.orEmpty(), today) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var sheetDate by remember { mutableStateOf<LocalDate?>(null) }
    var hcGranted by remember { mutableStateOf<Set<String>?>(null) }
    val hcAvailable = remember { container.healthSync.isAvailable }

    // Keep the predicted start where the reminder system can read it.
    val loaded = rows != null
    val nextKey = state.pred.nextStart?.toString()
    LaunchedEffect(loaded, nextKey) {
        if (loaded) withContext(Dispatchers.IO) { runCatching { CyclePrefs.setNextPeriodStart(ctx, nextKey) } }
    }
    // Merge periods logged in other apps (never overwrites local logs).
    LaunchedEffect(Unit) {
        val g = withContext(Dispatchers.IO) { CycleHealth.granted(container) }
        hcGranted = g
        if (CycleHealth.hasAny(container, g)) {
            val n = withContext(Dispatchers.IO) { runCatching { CycleHealth.importFromOthers(container) }.getOrDefault(0) }
            if (n > 0) toaster.show("Imported $n days from Health Connect")
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TodayCard(state) }
            item {
                val todayFlow = state.flows[today] ?: 0
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (todayFlow < 2) {
                        AccentButton("Period started today", {
                            container.write { CycleStore.periodStartedToday(container) }
                            toaster.show("Period logged for today")
                        }, Modifier.weight(1f), icon = Duo.Drop, height = 50.dp)
                    }
                    GlassButton("Log today", { sheetDate = today }, Modifier.weight(if (todayFlow < 2) 0.7f else 1f), icon = Duo.EditNote, height = 50.dp)
                }
            }
            item {
                GlassCard(padding = 14.dp) {
                    CycleCalendar(
                        month = month,
                        onMonth = { month = it },
                        flows = state.flows,
                        otherLogs = state.otherLogs,
                        pred = state.pred,
                        bbtShiftDays = state.shifts.toSet(),
                        today = today,
                        onDay = { sheetDate = it },
                    )
                    if (month != YearMonth.from(today)) {
                        Spacer(Modifier.height(8.dp))
                        GlassButton("Back to today", { month = YearMonth.from(today) }, Modifier.fillMaxWidth(), height = 40.dp)
                    }
                }
            }
            item { PredictionCard(state) }
            item { InsightsCard(state) }
            item { RemindersCard() }
            if (hcAvailable) item {
                val g = hcGranted
                GlassCard {
                    CardHeader(Duo.Sync, "Health Connect", th.water) {
                        if (g != null && CycleHealth.hasAny(container, g)) DataBadge(DataKind.RECORDED)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (g != null && CycleHealth.hasAny(container, g)) {
                        Caption("Connected. Flow you log here is shared with Health Connect, and periods from apps like Samsung Health or Flo fill in days you haven't logged.")
                    } else {
                        Caption("Optional: bring in periods from apps like Samsung Health or Flo, and share what you log here.")
                        Spacer(Modifier.height(10.dp))
                        GlassButton("Connect", requestHc, Modifier.fillMaxWidth(), icon = Duo.Link, height = 44.dp)
                    }
                }
            }
            item {
                GlassCard {
                    CardHeader(Duo.Lock, "Privacy", th.success)
                    Spacer(Modifier.height(6.dp))
                    Caption("Your cycle data stays on this phone. It's never shown to friends, used in leaderboards, or uploaded by MyFit (Health Connect only if you connect it).")
                    Spacer(Modifier.height(4.dp))
                    Caption(DISCLAIMER, color = th.textFaint)
                    Spacer(Modifier.height(10.dp))
                    GlassButton("Turn off cycle tracking", {
                        container.write { container.settings.setCycle(false) }
                        toaster.show("Cycle tracking off — your logs are kept on this phone")
                    }, Modifier.fillMaxWidth(), height = 44.dp)
                }
            }
        }

        val sd = sheetDate
        if (sd != null) {
            CycleLogSheet(container, sd, state.byDate[sd], onClose = { sheetDate = null })
        }
    }
}

// ------------------------------------------------------------------ cards

private fun phaseTip(p: CyclePhase): String = when (p) {
    CyclePhase.MENSTRUAL ->
        "Go by feel: walks, yoga or lighter lifts are great, and training as normal is fine if you feel good. " +
            "Top up iron with palak, chana, daal or lean beef — add lemon or tomatoes to help absorb it."
    CyclePhase.FOLLICULAR ->
        "Energy often climbs now — a good window for heavier strength work and chasing PBs. " +
            "Keep protein steady: eggs, chicken, daal, dahi."
    CyclePhase.OVULATION ->
        "Many people feel strongest around now. Warm up well before heavy or jumpy sessions, " +
            "stay hydrated and keep meals balanced — roti, sabzi and a protein."
    CyclePhase.LUTEAL ->
        "Cravings and a bigger appetite are normal. Plan protein + fibre snacks like chana chaat, roasted chana " +
            "or dahi with fruit; enjoy dates in moderation. Drink plenty of water and go easy on salty snacks."
}

private fun phaseColor(p: CyclePhase?, fallback: Color): Color = when (p) {
    CyclePhase.MENSTRUAL -> CycleColors.period
    CyclePhase.FOLLICULAR -> Color(0xFF4FB3FF)
    CyclePhase.OVULATION -> CycleColors.ovulation
    CyclePhase.LUTEAL -> Color(0xFFFFA24C)
    null -> fallback
}

private fun untilText(days: Int): String = when {
    days > 1 -> "Period in ~$days days"
    days == 1 -> "Period expected tomorrow"
    days == 0 -> "Period expected today"
    else -> "Period ${-days} day${if (-days == 1) "" else "s"} later than predicted"
}

@Composable
private fun TodayCard(s: CycleState) {
    val th = LocalFitTheme.current
    val t = s.now
    val color = phaseColor(t.phase, th.accent)
    GlassCard {
        CardHeader(Duo.Female, "Today", color) {
            if (t.phase != null && (t.phaseFromTypical || t.phase == CyclePhase.OVULATION)) DataBadge(DataKind.ESTIMATED)
        }
        Spacer(Modifier.height(12.dp))
        val day = t.cycleDay
        val phase = t.phase
        if (day == null || phase == null) {
            Text("Log your period to start", style = FitType.title, color = th.text)
            Spacer(Modifier.height(4.dp))
            Caption("Tap \"Period started today\" or any day on the calendar. Log 2 periods to unlock predictions.")
        } else {
            TodayDetails(t, day, phase, color)
        }
    }
}

@Composable
private fun TodayDetails(t: CycleToday, day: Int, phase: CyclePhase, color: Color) {
    val th = LocalFitTheme.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (t.periodDay != null) "Period day ${t.periodDay}" else "Cycle day $day", style = FitType.metric, color = th.text)
        }
        Spacer(Modifier.height(2.dp))
        Text(phase.label + " phase", style = FitType.section, color = color)
        val until = t.daysUntilNext
        if (until != null && t.periodDay == null) {
            Spacer(Modifier.height(2.dp))
            Caption(untilText(until))
            if (until < 0) Caption("Stress, travel, illness and sleep changes can all shift a cycle.", color = th.textFaint)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Duo.AutoAwesome, null, tint = color, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
            Text(phaseTip(phase), style = FitType.body, color = th.text)
        }
        if (t.phaseFromTypical) {
            Spacer(Modifier.height(8.dp))
            Caption("Phase is based on a typical 28-day cycle until you've logged two periods.", color = th.textFaint)
        }
    }
}

@Composable
private fun PredictionCard(s: CycleState) {
    val th = LocalFitTheme.current
    val p = s.pred
    GlassCard {
        CardHeader(Duo.CalendarMonth, "Predictions", CycleColors.period) {
            if (p.hasPredictions) DataBadge(DataKind.ESTIMATED)
        }
        Spacer(Modifier.height(10.dp))
        val next = p.nextStart
        if (next == null) {
            Text("Log 2 periods to unlock predictions", style = FitType.section, color = th.text)
            Spacer(Modifier.height(4.dp))
            Caption(
                if (p.periods.isEmpty()) "Mark the days you bleed — predictions start once a full cycle is logged."
                else "One period logged. Predictions appear when your next period starts."
            )
        } else {
            val from = next.minusDays(p.rangeDays.toLong())
            val to = next.plusDays(p.rangeDays.toLong())
            PredRow(CycleColors.period, "Next period", "${next.format(shortFmt)}  (${from.format(shortFmt)} – ${to.format(shortFmt)})")
            val fs = p.fertileStart
            val fe = p.fertileEnd
            val ov = p.ovulation
            if (fs != null && fe != null && ov != null) {
                PredRow(CycleColors.fertile, "Fertile window (estimate)", "${fs.format(shortFmt)} – ${fe.format(shortFmt)}")
                PredRow(CycleColors.ovulation, "Ovulation (estimate)", "around ${ov.format(shortFmt)}")
            }
            Spacer(Modifier.height(6.dp))
            Caption("Calendar estimate: ovulation is assumed ~14 days before your next period. It's unreliable when cycles are irregular.", color = th.textFaint)
        }
        val shift = s.shifts.lastOrNull()
        if (shift != null && ChronoUnit.DAYS.between(shift, s.today) in 0..45) {
            Spacer(Modifier.height(10.dp))
            PredRow(CycleColors.ovulation, "Temperature shift", "from ${shift.format(shortFmt)}")
            Caption("Your temperature rose and stayed up — ovulation likely happened around ${shift.minusDays(1).format(shortFmt)}. This confirms after the fact; it can't predict.")
        }
        if (p.irregular) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Duo.Info, null, tint = th.warning, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Your cycles look irregular. That's common and can be caused by stress, travel or sleep — " +
                        "but if it continues, consider talking to a doctor (for example about PCOS).",
                    style = FitType.body, color = th.text,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption(DISCLAIMER, color = th.textFaint)
    }
}

@Composable
private fun PredRow(color: Color, label: String, value: String) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconDot(color)
        Spacer(Modifier.width(10.dp))
        Text(label, style = FitType.body, color = th.textDim, modifier = Modifier.weight(1f))
        Text(value, style = FitType.label, color = th.text)
    }
}

@Composable
private fun IconDot(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(10.dp)) { drawCircle(color) }
}

@Composable
private fun InsightsCard(s: CycleState) {
    val th = LocalFitTheme.current
    val p = s.pred
    val cycles = remember(p) { p.periods.zipWithNext { a, b -> a.start to ChronoUnit.DAYS.between(a.start, b.start).toInt() }.takeLast(6) }
    val topSymptoms = remember(s) {
        s.byDate.values.flatMap { it.symptomList() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(3)
    }
    GlassCard {
        CardHeader(Duo.Insights, "Insights", th.sleep)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(p.meanCycle?.let { "${Math.round(it)} days" } ?: "—", style = FitType.title, color = th.text)
                Caption("Average cycle")
            }
            Column(Modifier.weight(1f)) {
                Text("${Math.round(p.meanPeriod)} days", style = FitType.title, color = if (p.periodLengthLogged) th.text else th.textDim)
                Caption(if (p.periodLengthLogged) "Average period" else "Average period (default)")
            }
        }
        if (cycles.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Recent cycles", style = FitType.label, color = th.textDim)
            Spacer(Modifier.height(6.dp))
            cycles.forEach { (start, len) ->
                val outlier = len < CycleMath.MIN_VALID || len > CycleMath.MAX_VALID
                val c = if (outlier || len < 21 || len > 35) th.warning else CycleColors.period
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(start.format(shortFmt), style = FitType.caption, color = th.textDim, modifier = Modifier.width(52.dp))
                    GlassProgressBar((len / 45f).coerceIn(0.05f, 1f), c, Modifier.weight(1f), height = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (outlier) "$len d*" else "$len d", style = FitType.label, color = th.text,
                        modifier = Modifier.width(44.dp), maxLines = 1, overflow = TextOverflow.Clip,
                    )
                }
            }
            if (cycles.any { it.second < CycleMath.MIN_VALID || it.second > CycleMath.MAX_VALID }) {
                Caption("* Unusually short or long — left out of the averages (a missed log is a common cause).", color = th.textFaint)
            }
        }
        if (topSymptoms.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Most common symptoms", style = FitType.label, color = th.textDim)
            Spacer(Modifier.height(4.dp))
            topSymptoms.forEach { (k, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(CycleOptions.symptomLabel(k), style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    Caption("$n day${if (n == 1) "" else "s"}")
                }
            }
        }
        if (cycles.isEmpty() && topSymptoms.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Caption("Insights build up as you log periods and symptoms.")
        }
    }
}

@Composable
private fun RemindersCard() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    var periodOn by remember { mutableStateOf(CyclePrefs.periodReminder(ctx)) }
    var pillOn by remember { mutableStateOf(CyclePrefs.pillOn(ctx)) }
    var pillTime by remember { mutableIntStateOf(CyclePrefs.pillTime(ctx)) }
    GlassCard {
        CardHeader(Duo.Bell, "Reminders", th.warning)
        Spacer(Modifier.height(6.dp))
        ToggleRow("Period reminder", "2 days before your predicted start", periodOn) {
            periodOn = it; CyclePrefs.setPeriodReminder(ctx, it)
        }
        ToggleRow("Daily pill reminder", if (pillOn) "Every day at the time below" else null, pillOn) {
            pillOn = it; CyclePrefs.setPillOn(ctx, it)
        }
        if (pillOn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption("Time", Modifier.weight(1f))
                MinuteOfDayChip(pillTime) { pillTime = it; CyclePrefs.setPillTime(ctx, it) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Caption("Reminders show as notifications when notifications are allowed for MyFit.", color = th.textFaint)
    }
}

// ------------------------------------------------------------------ dashboard tile

@Composable
fun CycleTile(container: AppContainer, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val enabled = LocalSettings.current.cycleEnabled
    GlassCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Duo.CalendarMonth, CycleColors.period, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text("Cycle", style = FitType.label, color = th.textDim, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        if (!enabled) {
            Text("Set up", style = FitType.title, color = th.text, maxLines = 1)
            Text("Cycle tracking — tap to set up", style = FitType.caption, color = th.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            CycleTileBody(container)
        }
    }
}

@Composable
private fun CycleTileBody(container: AppContainer) {
    val th = LocalFitTheme.current
    val rows by remember { container.db.cycleDao().observeAll() }.collectAsState(initial = null)
    val today = remember { Clock.today() }
    val s = remember(rows, today) { CycleState(rows.orEmpty(), today) }
    val t = s.now
    val day = t.cycleDay
    val phase = t.phase
    val periodDay = t.periodDay
    if (rows == null) {
        Text("—", style = FitType.title, color = th.text)
        Text(" ", style = FitType.caption)
    } else if (day == null || phase == null) {
        Text("Log period", style = FitType.title, color = th.text, maxLines = 1)
        Text("Tap to log your first day", style = FitType.caption, color = th.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
    } else if (periodDay != null) {
        Text("Period day $periodDay", style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(phase.label + " phase", style = FitType.caption, color = CycleColors.period, maxLines = 1)
    } else {
        Text("Day $day", style = FitType.title, color = th.text, maxLines = 1)
        val until = t.daysUntilNext
        val sub = when {
            until == null -> phase.label
            until > 0 -> "${phase.label} · period in ~$until d"
            until == 0 -> "${phase.label} · period due today"
            else -> "${phase.label} · period ${-until} d late"
        }
        Text(sub, style = FitType.caption, color = phaseColor(phase, th.textDim), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
