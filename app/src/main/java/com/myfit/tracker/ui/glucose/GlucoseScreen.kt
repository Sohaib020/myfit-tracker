package com.myfit.tracker.ui.glucose

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.GlucoseReading
import com.myfit.tracker.data.db.GlucoseTag
import com.myfit.tracker.data.db.MealCarbs
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Glucose
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.ChartPoint
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSegmented
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.ProgressChart
import com.myfit.tracker.ui.components.DateTimeRow
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
private val Orange = Color(0xFFFF8A3D)

/** Colour for a reading: low red/orange, in range green, high amber, very high red. */
internal fun bandColor(th: FitTheme, b: Glucose.Band): Color = when (b) {
    Glucose.Band.VERY_LOW -> th.danger
    Glucose.Band.LOW -> Orange
    Glucose.Band.IN_RANGE -> th.success
    Glucose.Band.HIGH -> th.warning
    Glucose.Band.VERY_HIGH -> th.danger
}

internal fun agoText(ms: Long, now: Long = Clock.now()): String {
    val m = ((now - ms) / 60_000L).coerceAtLeast(0L)
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / 1440} days ago"
    }
}

private fun timeOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(Clock.zone()).format(timeFmt)

private fun startOfDayMs(daysAgo: Long): Long =
    Clock.today().minusDays(daysAgo).atStartOfDay(Clock.zone()).toInstant().toEpochMilli()

// =================================================================== screen

@Composable
fun GlucoseScreen(container: AppContainer) {
    val nav = LocalNav.current
    val settings = LocalSettings.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val cfg = GlucoseConfigStore.flow(ctx).collectAsState().value ?: GlucoseConfig()
    var showSetup by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var permTick by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }

    suspend fun runImport(quiet: Boolean) {
        if (importing) return
        importing = true
        val n = GlucoseHc.importRecent(container, 30)
        runCatching { container.glucoseFamily.upload(container.app) }
        importing = false
        if (!quiet || n > 0) toaster.show(if (n > 0) "Imported $n sensor readings" else "No new readings in Health Connect")
    }

    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { _ ->
        permTick++
        scope.launch { runImport(quiet = true) }
    }
    fun requestHc() {
        if (container.healthSync.isAvailable) runCatching { permLauncher.launch(container.healthSync.glucosePermissions) }
        else toaster.show("Health Connect isn't available on this phone")
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar("Blood sugar", { nav.pop() }, subtitle = if (settings.glucoseEnabled) DiabetesType.label(cfg.type) + " · " + Glucose.unitLabel(cfg.mmol) else null) {
                if (settings.glucoseEnabled) GlassIconButton(Duo.Tune, { showSetup = true })
            }
            if (!settings.glucoseEnabled) {
                GlucoseIntro(onTurnOn = {
                    scope.launch { container.settings.setGlucose(true) }
                    requestHc()
                    showSetup = true
                })
            } else {
                GlucoseMain(
                    container = container, cfg = cfg, permTick = permTick, importing = importing,
                    onLog = { showLog = true }, onImport = { scope.launch { runImport(quiet = false) } },
                    onConnect = { requestHc() }, onSetup = { showSetup = true },
                )
            }
        }
        LaunchedEffect(settings.glucoseEnabled) {
            if (settings.glucoseEnabled) {
                if (!GlucoseConfigStore.get(ctx).setupDone) showSetup = true
                runImport(quiet = true)
            }
        }
        GlucoseSetupSheet(
            visible = showSetup, cfg = cfg, container = container,
            onDismiss = { showSetup = false },
            onSave = { GlucoseConfigStore.save(ctx, it.copy(setupDone = true)); showSetup = false },
            onTurnOff = {
                showSetup = false
                scope.launch { container.settings.setGlucose(false) }
            },
            onConnect = { requestHc() },
        )
        LogReadingSheet(visible = showLog, cfg = cfg, container = container, permTick = permTick, onDismiss = { showLog = false })
    }
}

// =================================================================== intro

@Composable
private fun GlucoseIntro(onTurnOn: () -> Unit) {
    val th = LocalFitTheme.current
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard {
                CardHeader(Duo.Drop, "Track your blood sugar", th.danger)
                Spacer(Modifier.height(10.dp))
                Text("For type 1, type 2, prediabetes and gestational diabetes.", style = FitType.body, color = th.text)
                Spacer(Modifier.height(8.dp))
                Bullet("Log finger-stick readings with a tag (fasting, before or after a meal, bedtime).")
                Bullet("Import sensor (CGM) data from Health Connect for time in range.")
                Bullet("See how your carbs line up with your readings.")
                Bullet("Keep a list of your medicines and record doses.")
                Bullet("Share a PDF summary with your doctor or nurse.")
                Spacer(Modifier.height(14.dp))
                AccentButton("Turn on", onTurnOn, Modifier.fillMaxWidth(), icon = Duo.Check)
            }
        }
        item {
            GlassCard {
                CardHeader(Duo.Lock, "Private and careful", th.accent)
                Spacer(Modifier.height(8.dp))
                Caption("Your data stays on your phone. MyFit is not medical advice, doesn't diagnose anything, and never suggests insulin or medicine doses. Always follow the plan from your care team.")
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    val th = LocalFitTheme.current
    Row(Modifier.padding(vertical = 3.dp)) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(th.accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = FitType.body, color = th.textDim)
    }
}

// =================================================================== main content

@Composable
private fun GlucoseMain(
    container: AppContainer,
    cfg: GlucoseConfig,
    permTick: Int,
    importing: Boolean,
    onLog: () -> Unit,
    onImport: () -> Unit,
    onConnect: () -> Unit,
    onSetup: () -> Unit,
) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val todayKey = Clock.dateKey(Clock.today())
    val from90 = remember(todayKey) { startOfDayMs(89) }
    val startToday = remember(todayKey) { startOfDayMs(0) }
    val fromKey90 = remember(todayKey) { Clock.dateKey(Clock.today().minusDays(89)) }
    val readings = remember(from90) { container.db.glucoseDao().observeSince(from90) }.collectAsState(initial = null).value
    val latest = remember { container.db.glucoseDao().observeLatest() }.collectAsState(initial = null).value
    val carbs = remember(fromKey90, todayKey) { container.db.glucoseDao().dailyCarbs(fromKey90, todayKey) }.collectAsState(initial = emptyList()).value
        .associate { it.date to it.carbs }
    val mealsToday = remember(startToday) { container.db.glucoseDao().mealCarbs(startToday - 3 * 3_600_000L, startToday + 86_400_000L) }
        .collectAsState(initial = emptyList()).value
    val canRead by produceState(false, permTick) { value = GlucoseHc.canRead(container) }
    var range by remember { mutableIntStateOf(14) }
    var reportDays by remember { mutableIntStateOf(14) }
    var building by remember { mutableStateOf(false) }

    val all = readings ?: emptyList()
    val today = all.filter { it.localDate == todayKey }

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- latest
        item { StatusHero(latest, cfg, onLog) }
        if (latest != null && latest.mgdl < Glucose.LOW) item { LowBanner(latest) }
        item { MeaningStrip(cfg) }

        // ---- today
        item {
            TodayCard(today, cfg, carbs[todayKey], mealsToday,
                onDelete = { r ->
                    container.write { container.db.glucoseDao().softDelete(r.id, Clock.now()) }
                    toaster.show("Reading deleted", "Undo") { container.write { container.db.glucoseDao().restore(r.id, Clock.now()) } }
                })
        }

        // ---- chart + stats
        item {
            val from = startOfDayMs(range.toLong() - 1)
            val inWindow = all.filter { it.takenAt >= from }
            TrendCard(inWindow, cfg, range) { range = it }
        }

        // ---- family
        item { FamilyCard(container) }
        // ---- medicines
        item { MedsSummaryCard(container) { nav.push(Overlay.Meds) } }
        // ---- report
        item {
            GlassCard {
                CardHeader(Duo.Send, "Report for your doctor", th.water)
                Spacer(Modifier.height(10.dp))
                Caption("A designed PDF with blood sugar, blood pressure & heart rate, weight, activity, sleep and your medicines — ready to send to your doctor.")
                Spacer(Modifier.height(10.dp))
                GlassSegmented(listOf(14, 30), reportDays, { "Last $it days" }, { reportDays = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                AccentButton(if (building) "Building…" else "Share report (PDF)", {
                    if (building) return@AccentButton
                    building = true
                    scope.launch {
                        val f = runCatching { com.myfit.tracker.ui.report.DoctorReport.build(container, reportDays, cfg) }.getOrNull()
                        building = false
                        if (f == null) toaster.show("Couldn't build the report")
                        else runCatching { com.myfit.tracker.ui.report.DoctorReport.share(ctx, f) }.onFailure { toaster.show("No app found to share the PDF") }
                    }
                }, Modifier.fillMaxWidth(), icon = Duo.Send, enabled = !building, height = 48.dp)
            }
        }

        item { Text("More tools", style = FitType.section, color = th.text, modifier = Modifier.padding(start = 6.dp, top = 8.dp)) }
        // ---- CGM
        item {
            val check = remember(all, cfg.low, cfg.high, todayKey) {
                val from30 = startOfDayMs(29)
                Glucose.cgmCheck(all.filter { it.takenAt >= from30 }, todayKey, cfg.low, cfg.high)
            }
            val lastSensor = all.lastOrNull { it.tag == GlucoseTag.CGM }
            CgmCard(check, lastSensor, canRead, importing, onImport, onConnect)
        }

        // ---- food link
        item { CarbsCard(all, carbs, cfg) }


        // ---- HbA1c, reminders, Ramadan
        item { HbA1cCard(container, cfg) }
        item { CheckRemindersCard(cfg) }
        item { RamadanCard(container, cfg) }

        // ---- settings
        item {
            GlassCard(onClick = onSetup) {
                CardHeader(Duo.Tune, "Targets & units", th.accent) { Text(">", style = FitType.section, color = th.textDim) }
                Spacer(Modifier.height(6.dp))
                Caption("Target ${Glucose.format(cfg.low.toDouble(), cfg.mmol)}–${Glucose.format(cfg.high.toDouble(), cfg.mmol)} ${Glucose.unitLabel(cfg.mmol)} · ${DiabetesType.label(cfg.type)}")
            }
        }
        item {
            Caption("MyFit is not medical advice and never suggests insulin or medicine doses. Follow the plan from your care team. Your readings stay on this phone.", Modifier.padding(horizontal = 6.dp))
        }
    }
}

// =================================================================== cards

@Composable
private fun LatestCard(latest: GlucoseReading?, cfg: GlucoseConfig, onLog: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Drop, "Latest reading", th.danger)
        Spacer(Modifier.height(12.dp))
        if (latest == null) {
            Text("No readings yet", style = FitType.title, color = th.text)
            Caption("Log a finger-stick reading or connect a sensor below.")
        } else {
            val band = Glucose.band(latest.mgdl, cfg.high)
            val col = bandColor(th, band)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Glucose.format(latest.mgdl, cfg.mmol), style = FitType.display.copy(fontSize = FitType.display.fontSize * 1.4f, lineHeight = FitType.display.lineHeight * 1.4f), color = col)
                Spacer(Modifier.width(6.dp))
                Text(Glucose.unitLabel(cfg.mmol), style = FitType.section, color = th.textDim, modifier = Modifier.padding(bottom = 8.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(band.label, style = FitType.label, color = col)
                }
            }
            val src = if (latest.source == "HEALTH_CONNECT") " · " + GlucoseHc.sourceLabel(latest.sourcePackage) else ""
            Caption("${agoText(latest.takenAt)} · ${timeOf(latest.takenAt)} · ${Glucose.tagLabel(latest.tag)}$src")
        }
        Spacer(Modifier.height(14.dp))
        AccentButton("Log reading", onLog, Modifier.fillMaxWidth(), icon = Duo.Add, height = 48.dp)
    }
}

@Composable
private fun LowBanner(r: GlucoseReading) {
    val th = LocalFitTheme.current
    val severe = r.mgdl < Glucose.VERY_LOW
    val col = if (severe) th.danger else Orange
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(col.copy(alpha = 0.2f)).padding(16.dp)) {
        Row {
            IconBubble(Duo.Info, col, 30.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (severe) "Very low blood sugar" else "Low blood sugar", style = FitType.section, color = th.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (severe) "Very low: treat it now. Take 15–20 g fast sugar (e.g. 4 tsp sugar in water, a small glass of juice) and recheck in 15 min. If you feel very unwell, are confused or can't swallow, call for help immediately (999/911 or your local emergency number) — don't stay alone."
                    else "Low: take 15 g fast sugar (e.g. 3 tsp sugar in water, ½ glass juice), recheck in 15 min. If you feel very unwell or can't swallow, get help immediately.",
                    style = FitType.body, color = th.text,
                )
                Spacer(Modifier.height(4.dp))
                Caption("Reading taken ${agoText(r.takenAt)}. Follow your own care plan if it says something different.")
            }
        }
    }
}

@Composable
private fun TodayCard(today: List<GlucoseReading>, cfg: GlucoseConfig, carbsToday: Double?, meals: List<MealCarbs>, onDelete: (GlucoseReading) -> Unit) {
    val th = LocalFitTheme.current
    val manual = today.filter { it.tag != GlucoseTag.CGM }.sortedByDescending { it.takenAt }
    val sensor = today.filter { it.tag == GlucoseTag.CGM }
    GlassCard {
        CardHeader(Duo.ViewTimeline, "Today", th.accent) {
            Text(carbsToday?.let { "${it.roundToInt()} g carbs" } ?: "No food logged", style = FitType.label, color = th.carbs)
        }
        Spacer(Modifier.height(8.dp))
        if (today.isEmpty()) Caption("No readings today yet.")
        if (sensor.isNotEmpty()) {
            val inR = sensor.count { Glucose.inRange(it.mgdl, cfg.low, cfg.high) } * 100 / sensor.size
            Caption("Sensor: ${sensor.size} readings · ${Glucose.format(sensor.minOf { it.mgdl }, cfg.mmol)}–${Glucose.format(sensor.maxOf { it.mgdl }, cfg.mmol)} ${Glucose.unitLabel(cfg.mmol)} · $inR% in range")
            Spacer(Modifier.height(6.dp))
        }
        manual.forEach { r ->
            val col = bandColor(th, Glucose.band(r.mgdl, cfg.high))
            val meal = if (r.tag == GlucoseTag.AFTER_MEAL)
                meals.filter { it.eatenAt <= r.takenAt && r.takenAt - it.eatenAt <= 3 * 3_600_000L }.maxByOrNull { it.eatenAt } else null
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(timeOf(r.takenAt), style = FitType.label, color = th.textDim, modifier = Modifier.width(46.dp))
                Text(Glucose.format(r.mgdl, cfg.mmol), style = FitType.title, color = th.text)
                Spacer(Modifier.width(8.dp))
                MeaningPill(r.mgdl, cfg)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(Glucose.tagLabel(r.tag), style = FitType.label, color = th.textDim)
                    if (meal != null) Caption("${meal.carbs.roundToInt()} g carbs at ${timeOf(meal.eatenAt)}", color = th.carbs)
                    if (r.notes.isNotBlank()) Caption(r.notes, color = th.textFaint)
                }
                GlassIconButton(Duo.DeleteOutline, { onDelete(r) }, size = 34.dp, tint = th.textDim)
            }
        }
    }
}

@Composable
private fun TrendCard(rs: List<GlucoseReading>, cfg: GlucoseConfig, range: Int, onRange: (Int) -> Unit) {
    val th = LocalFitTheme.current
    val mmol = cfg.mmol
    val u = Glucose.unitLabel(mmol)
    GlassCard {
        CardHeader(Duo.Insights, "Your last $range days", th.water)
        Spacer(Modifier.height(6.dp))
        Text(summarySentence(rs, cfg), style = FitType.body, color = th.text)
        Spacer(Modifier.height(10.dp))
        GlassSegmented(listOf(7, 14, 30, 90), range, { "${it}d" }, onRange, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        if (rs.size < 2) {
            Caption(if (rs.isEmpty()) "No readings in the last $range days." else "Log one more reading to see a chart.")
        } else {
            val points = remember(rs, mmol) {
                val raw = rs.map { it.takenAt to it.mgdl }
                Glucose.downsample(raw, 160).map { (t, v) ->
                    val z = Instant.ofEpochMilli(t).atZone(Clock.zone())
                    ChartPoint(t, Glucose.toUnit(v, mmol), z.format(dayFmt) + " " + z.format(timeFmt), pr = v < Glucose.LOW)
                }
            }
            ProgressChart(points, th.water, { v -> if (mmol) String.format(Locale.US, "%.1f", v) else v.roundToInt().toString() }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Caption("Your target is ${Glucose.format(cfg.low.toDouble(), mmol)}–${Glucose.format(cfg.high.toDouble(), mmol)} $u. Below ${Glucose.format(Glucose.LOW, mmol)} is low (highlighted), ${Glucose.format(Glucose.VERY_HIGH, mmol)}+ is very high." +
                if (rs.size > 160) " Long sensor traces are shown as averages." else "")
        }
        if (rs.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val manual = rs.filter { it.tag != GlucoseTag.CGM }
            val byTag = manual.groupBy { it.tag }
            val lows = rs.count { it.mgdl < Glucose.LOW }
            Row(Modifier.fillMaxWidth()) {
                StatCell("Average", Glucose.format(rs.map { it.mgdl }.average(), mmol), u, Modifier.weight(1f))
                StatCell("Readings", rs.size.toString(), null, Modifier.weight(1f))
                StatCell("Lows", lows.toString(), null, Modifier.weight(1f), if (lows > 0) Orange else null)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                StatCell("Fasting avg", byTag[GlucoseTag.FASTING]?.let { l -> Glucose.format(l.map { it.mgdl }.average(), mmol) } ?: "—", null, Modifier.weight(1f))
                StatCell("After meal avg", byTag[GlucoseTag.AFTER_MEAL]?.let { l -> Glucose.format(l.map { it.mgdl }.average(), mmol) } ?: "—", null, Modifier.weight(1f))
                StatCell("Finger in range", if (manual.isEmpty()) "—" else "${manual.count { Glucose.inRange(it.mgdl, cfg.low, cfg.high) } * 100 / manual.size}%", null, Modifier.weight(1f))
            }
            val others = listOf(GlucoseTag.BEFORE_MEAL, GlucoseTag.BEDTIME, GlucoseTag.RANDOM).mapNotNull { t ->
                byTag[t]?.let { l -> "${Glucose.tagLabel(t)} ${Glucose.format(l.map { it.mgdl }.average(), mmol)} (${l.size})" }
            }
            if (others.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Caption("Averages: " + others.joinToString(" · ")) }
            Spacer(Modifier.height(4.dp))
            Caption("Fasting target is usually ${Glucose.format(80.0, mmol)}–${Glucose.format(130.0, mmol)} $u — check yours with your care team.")
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, unit: String?, modifier: Modifier, color: Color? = null) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Text(label.uppercase(), style = FitType.overline, color = th.textDim)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = FitType.title, color = color ?: th.text)
            if (unit != null) { Spacer(Modifier.width(3.dp)); Text(unit, style = FitType.caption, color = th.textDim, modifier = Modifier.padding(bottom = 3.dp)) }
        }
    }
}

@Composable
private fun CgmCard(check: Glucose.CgmCheck, lastSensor: GlucoseReading?, canRead: Boolean, importing: Boolean, onImport: () -> Unit, onConnect: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Sensors, "Sensor (CGM)", th.sleep) { if (check.tir != null) DataBadge(DataKind.CALCULATED) }
        Spacer(Modifier.height(10.dp))
        val tir = check.tir
        val gmi = check.gmi
        if (tir != null && gmi != null) {
            Text("${tir.inRange.roundToInt()}% in range", style = FitType.title, color = th.text)
            Spacer(Modifier.height(8.dp))
            TirBar(tir)
            Spacer(Modifier.height(8.dp))
            TirLegend("Very low (<54)", tir.veryLow, th.danger)
            TirLegend("Low (54–69)", tir.low, Orange)
            TirLegend("In range", tir.inRange, th.success)
            TirLegend("High (to 250)", tir.high, th.warning)
            TirLegend("Very high (>250)", tir.veryHigh, Color(0xFFB0323C))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(Locale.US, "%.1f%%", gmi), style = FitType.title, color = th.text)
                Spacer(Modifier.width(8.dp))
                DataBadge(DataKind.ESTIMATED)
            }
            Caption("Estimated A1c (GMI) — may differ from your lab A1c. Based on ${check.daysWithData} days of sensor data.")
        } else {
            Text(if (check.daysWithData == 0) "No sensor data yet" else "Needs 14 days of sensor data", style = FitType.section, color = th.text)
            Caption(
                when {
                    check.daysWithData == 0 -> "Time in range and estimated A1c appear once a sensor shares 14 days of data through Health Connect."
                    !check.isCgm -> "Imported readings look like occasional meter readings (${check.perDay.roundToInt()} a day), not a continuous sensor."
                    else -> "${check.daysWithData} of 14 days so far (${(check.coverage * 100).roundToInt()}% of expected readings; 70% needed)."
                }
            )
        }
        if (lastSensor != null) { Spacer(Modifier.height(6.dp)); Caption("Last sensor reading ${agoText(lastSensor.takenAt)} from ${GlucoseHc.sourceLabel(lastSensor.sourcePackage)}.") }
        Spacer(Modifier.height(12.dp))
        if (canRead) GlassButton(if (importing) "Importing…" else "Import from Health Connect", onImport, Modifier.fillMaxWidth(), icon = Duo.Sync, height = 46.dp)
        else GlassButton("Connect Health Connect", onConnect, Modifier.fillMaxWidth(), icon = Duo.Link, height = 46.dp)
        Spacer(Modifier.height(8.dp))
        Caption("Works with apps that share glucose to Health Connect: xDrip+, Juggluco, Dexcom (3-hour delay), Samsung Health. LibreLink may not share.")
    }
}

@Composable
private fun TirBar(t: Glucose.Tir) {
    val th = LocalFitTheme.current
    val parts = listOf(t.veryLow to th.danger, t.low to Orange, t.inRange to th.success, t.high to th.warning, t.veryHigh to Color(0xFFB0323C))
        .filter { it.first > 0.0 }
    Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))) {
        parts.forEach { (pct, c) -> Box(Modifier.weight(pct.toFloat().coerceAtLeast(0.5f)).fillMaxHeight().background(c)) }
    }
}

@Composable
private fun TirLegend(label: String, pct: Double, c: Color) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
        Text(label, style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
        Text(if (pct > 0 && pct < 1) "<1%" else "${pct.roundToInt()}%", style = FitType.label, color = th.text)
    }
}

@Composable
private fun CarbsCard(all: List<GlucoseReading>, carbs: Map<String, Double>, cfg: GlucoseConfig) {
    val th = LocalFitTheme.current
    val days = (0L until 7L).map { Clock.today().minusDays(it) }
    val byDay = remember(all) { all.groupBy { it.localDate } }
    GlassCard {
        CardHeader(Duo.ForkKnife, "Carbs & readings", th.carbs)
        Spacer(Modifier.height(8.dp))
        Caption("Carbs from your food diary next to that day's average blood sugar.")
        Spacer(Modifier.height(6.dp))
        days.forEach { d ->
            val k = Clock.dateKey(d)
            val rs = byDay[k]
            val avg = rs?.map { it.mgdl }?.average()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (d == Clock.today()) "Today" else d.format(dayFmt), style = FitType.label, color = th.textDim, modifier = Modifier.weight(1f))
                Text(carbs[k]?.let { "${it.roundToInt()} g" } ?: "—", style = FitType.label, color = th.carbs, modifier = Modifier.width(64.dp))
                if (avg != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(bandColor(th, Glucose.band(avg, cfg.high))))
                    Spacer(Modifier.width(6.dp))
                }
                Text(avg?.let { "${Glucose.format(it, cfg.mmol)} avg (${rs?.size ?: 0})" } ?: "no readings", style = FitType.label, color = th.text, modifier = Modifier.width(110.dp))
            }
        }
    }
}

// =================================================================== setup sheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlucoseSetupSheet(
    visible: Boolean,
    cfg: GlucoseConfig,
    container: AppContainer,
    onDismiss: () -> Unit,
    onSave: (GlucoseConfig) -> Unit,
    onTurnOff: () -> Unit,
    onConnect: () -> Unit,
) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var type by remember(visible) { mutableStateOf(cfg.type) }
    var mmol by remember(visible) { mutableStateOf(cfg.mmol) }
    var lowTxt by remember(visible) { mutableStateOf(Glucose.format(cfg.low.toDouble(), cfg.mmol)) }
    var highTxt by remember(visible) { mutableStateOf(Glucose.format(cfg.high.toDouble(), cfg.mmol)) }
    var writeHc by remember(visible) { mutableStateOf(cfg.writeHc) }
    val canWrite by produceState(false, visible) { value = GlucoseHc.canWrite(container) }

    fun switchUnit(toMmol: Boolean) {
        if (toMmol == mmol) return
        val l = lowTxt.toDoubleOrNull()?.let { Glucose.toMgdl(it, mmol) }
        val h = highTxt.toDoubleOrNull()?.let { Glucose.toMgdl(it, mmol) }
        mmol = toMmol
        if (l != null) lowTxt = Glucose.format(l, toMmol)
        if (h != null) highTxt = Glucose.format(h, toMmol)
    }

    GlassSheet(visible = visible, onDismiss = onDismiss) {
        Text("Blood sugar setup", style = FitType.title, color = th.text)
        Spacer(Modifier.height(14.dp))
        Text("Diabetes type", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DiabetesType.all.forEach { t -> GlassChip(DiabetesType.label(t), type == t, { type = t }) }
        }
        Spacer(Modifier.height(16.dp))
        Text("Unit", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(8.dp))
        GlassSegmented(listOf(false, true), mmol, { if (it) "mmol/L" else "mg/dL" }, { switchUnit(it) }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Target range", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberInput(lowTxt, { lowTxt = it }, "low", Modifier.weight(1f), decimal = mmol, big = false)
            NumberInput(highTxt, { highTxt = it }, "high", Modifier.weight(1f), decimal = mmol, big = false)
        }
        Spacer(Modifier.height(6.dp))
        Caption("Common targets: ${Glucose.format(70.0, mmol)}–${Glucose.format(180.0, mmol)} overall, fasting ${Glucose.format(80.0, mmol)}–${Glucose.format(130.0, mmol)} ${Glucose.unitLabel(mmol)}. Use the targets your care team gave you.")
        Spacer(Modifier.height(12.dp))
        if (canWrite) ToggleRow("Also save my readings to Health Connect", "So other health apps can see them", writeHc) { writeHc = it }
        else GlassButton("Connect Health Connect", onConnect, Modifier.fillMaxWidth(), icon = Duo.Link, height = 46.dp)
        Spacer(Modifier.height(16.dp))
        AccentButton("Save", {
            val l = lowTxt.toDoubleOrNull()?.let { Glucose.toMgdl(it, mmol).roundToInt() }
            val h = highTxt.toDoubleOrNull()?.let { Glucose.toMgdl(it, mmol).roundToInt() }
            if (l == null || h == null || l < 54 || h > 300 || l >= h) {
                toaster.show("Please enter a target range between ${Glucose.format(54.0, mmol)} and ${Glucose.format(300.0, mmol)}")
            } else onSave(cfg.copy(type = type, mmol = mmol, low = l, high = h, writeHc = writeHc && canWrite))
        }, Modifier.fillMaxWidth(), icon = Duo.Check)
        Spacer(Modifier.height(10.dp))
        GlassButton("Turn off blood sugar tracking", onTurnOff, Modifier.fillMaxWidth(), height = 46.dp)
        Spacer(Modifier.height(6.dp))
        Caption("Turning off hides this feature; your saved readings are kept on this phone.")
    }
}

// =================================================================== log sheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogReadingSheet(visible: Boolean, cfg: GlucoseConfig, container: AppContainer, permTick: Int, onDismiss: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var value by remember(visible) { mutableStateOf("") }
    var at by remember(visible) { mutableStateOf(Clock.now()) }
    var tag by remember(visible) { mutableStateOf(Glucose.defaultTag(Clock.minuteOfDay(Clock.now(), Clock.zone().id))) }
    var notes by remember(visible) { mutableStateOf("") }
    val canWrite by produceState(false, visible, permTick) { this.value = GlucoseHc.canWrite(container) }
    var writeHc by remember(visible, canWrite) { mutableStateOf(cfg.writeHc && canWrite) }

    GlassSheet(visible = visible, onDismiss = onDismiss) {
        Text("Add my sugar reading", style = FitType.title, color = th.text)
        Spacer(Modifier.height(10.dp))
        val typed = value.toDoubleOrNull()?.let { Glucose.toMgdl(it, cfg.mmol) }?.takeIf { it in 10.0..700.0 }
        val meaning = typed?.let { meaningOf(it, cfg) }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background((meaning?.color ?: th.text).copy(alpha = 0.1f)).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(value.ifEmpty { "0" }, fontSize = 46.sp, fontWeight = FontWeight.Bold, color = if (value.isEmpty()) th.textFaint else th.text)
            Spacer(Modifier.width(8.dp))
            Text(Glucose.unitLabel(cfg.mmol), style = FitType.section, color = th.textDim, modifier = Modifier.weight(1f))
            if (meaning != null) Text(meaning.word, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(meaning.color).padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        BigKeypad(value, cfg.mmol) { value = it }
        Spacer(Modifier.height(12.dp))
        Text("When did you check?", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Glucose.manualTags.forEach { t -> GlassChip(Glucose.tagLabel(t), tag == t, { tag = t }) }
        }
        Spacer(Modifier.height(12.dp))
        DateTimeRow("Time", at, { at = it })
        Spacer(Modifier.height(12.dp))
        NotesField(notes, { notes = it }, "Note (optional) — e.g. felt shaky, after a walk")
        if (canWrite) {
            Spacer(Modifier.height(6.dp))
            ToggleRow("Save to Health Connect", null, writeHc) { writeHc = it }
        }
        Spacer(Modifier.height(16.dp))
        AccentButton("Save", {
            val v = value.toDoubleOrNull()
            val mg = v?.let { Glucose.toMgdl(it, cfg.mmol) }
            if (mg == null || mg < 10 || mg > 700) {
                toaster.show("Enter a reading between ${Glucose.format(10.0, cfg.mmol)} and ${Glucose.format(700.0, cfg.mmol)} ${Glucose.unitLabel(cfg.mmol)}")
                return@AccentButton
            }
            if (at > Clock.now() + 5 * 60_000L) { toaster.show("That time is in the future"); return@AccentButton }
            val st = Stamp.of(at)
            val now = Clock.now()
            val r = GlucoseReading(
                mgdl = mg, tag = tag, source = "MANUAL", takenAt = st.at, zoneId = st.zoneId, localDate = st.localDate,
                notes = notes.trim(), createdAt = now, updatedAt = now,
            )
            val alsoHc = writeHc && canWrite
            container.write {
                val id = container.db.glucoseDao().insert(r)
                if (alsoHc) GlucoseHc.write(container, r.copy(id = id))
                runCatching { container.glucoseFamily.upload(container.app) }
            }
            val band = Glucose.band(mg, cfg.high)
            toaster.show(
                when (band) {
                    Glucose.Band.VERY_LOW, Glucose.Band.LOW -> "Saved — that's low. Treat it and recheck in 15 min."
                    else -> "Saved ${Glucose.formatWithUnit(mg, cfg.mmol)}"
                }
            )
            onDismiss()
        }, Modifier.fillMaxWidth(), icon = Duo.Check)
    }
}

// =================================================================== meds + tile

@Composable
fun MedsScreen(container: AppContainer) {
    MedsContent(container)
}

/** Half-width dashboard tile. */
@Composable
fun GlucoseTile(container: AppContainer, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val ctx = LocalContext.current
    val cfg = GlucoseConfigStore.flow(ctx).collectAsState().value ?: GlucoseConfig()
    GlassCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Duo.Drop, th.danger, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text("Blood sugar", style = FitType.label, color = th.textDim, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        if (!settings.glucoseEnabled) {
            Text("Set up", style = FitType.title, color = th.text)
            TileLine("Tap to set up")
        } else {
            GlucoseTileBody(container, cfg)
        }
    }
}

@Composable
private fun GlucoseTileBody(container: AppContainer, cfg: GlucoseConfig) {
    val th = LocalFitTheme.current
    run {
        val todayKey = Clock.dateKey(Clock.today())
        val startToday = remember(todayKey) { startOfDayMs(0) }
        val latest = remember { container.db.glucoseDao().observeLatest() }.collectAsState(initial = null).value
        val today = remember(startToday) { container.db.glucoseDao().observeSince(startToday) }.collectAsState(initial = emptyList()).value
        if (latest == null) {
            Text("—", style = FitType.title, color = th.text)
            TileLine("No readings yet")
        } else {
            val col = bandColor(th, Glucose.band(latest.mgdl, cfg.high))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(6.dp))
                Text(Glucose.format(latest.mgdl, cfg.mmol), style = FitType.title, color = th.text)
                Spacer(Modifier.width(4.dp))
                Text(Glucose.unitLabel(cfg.mmol), style = FitType.caption, color = th.textDim)
            }
            TileLine(agoText(latest.takenAt))
            if (today.isNotEmpty()) {
                val pct = today.count { Glucose.inRange(it.mgdl, cfg.low, cfg.high) } * 100 / today.size
                TileLine("In range today $pct%")
            }
        }
    }
}

@Composable
private fun TileLine(text: String) {
    Text(text, style = FitType.caption, color = LocalFitTheme.current.textDim, maxLines = 1)
}
