package com.myfit.tracker.ui.vitals

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.health.VitalsReader
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.ChartPoint
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.ProgressChart
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.roundToInt

// =====================================================================================  Vitals

@Composable
fun VitalsScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val hs = container.healthSync
    val reader = remember { VitalsReader(container) }
    var refresh by remember { mutableIntStateOf(0) }
    val data by produceState<VitalsReader.All?>(null, refresh) { value = runCatching { reader.all() }.getOrNull() }
    val fromMs = remember { Clock.today().minusDays(29).atStartOfDay(Clock.zone()).toInstant().toEpochMilli() }
    val manualBp by remember(fromMs) { container.db.bloodPressureDao().observeSince(fromMs) }.collectAsState(initial = emptyList())
    var bpSheet by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { refresh++ }
    val askPerms: () -> Unit = { runCatching { permLauncher.launch(hs.allPermissions) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar("Vitals", { nav.pop() }, "Heart, oxygen, blood pressure & more") {
                GlassIconButton(Duo.Sync, { refresh++ })
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val d = data
                if (d == null) {
                    item { GlassCard { Caption("Reading your vitals…") } }
                } else {
                    if (!d.available) item {
                        GlassCard {
                            CardHeader(Duo.HealthAndSafety, "Health Connect not available", th.textDim)
                            Spacer(Modifier.height(8.dp))
                            Caption("Watch data comes through Health Connect, which isn't available on this phone (or needs an update from the Play Store). You can still log blood pressure and try the camera heart-rate estimate below.")
                        }
                    } else if (!d.anyPermitted) item {
                        GlassCard {
                            CardHeader(Duo.Watch, "Connect your watch", th.accent)
                            Spacer(Modifier.height(8.dp))
                            Caption("Allow MyFit to read heart rate, blood oxygen and other vitals from Health Connect. Your watch app (Samsung Health, Fitbit, Garmin…) needs to share with Health Connect too.")
                            Spacer(Modifier.height(12.dp))
                            AccentButton("Allow access", askPerms, Modifier.fillMaxWidth(), icon = Duo.Check, height = 48.dp)
                            Spacer(Modifier.height(8.dp))
                            GlassButton("Set up my watch or app", { nav.push(Overlay.Devices) }, Modifier.fillMaxWidth(), icon = Duo.Watch, height = 44.dp)
                        }
                    }

                    if (d.heartRate.latest != null || d.heartRate.daily.isNotEmpty() || d.resting.hasData) item { HeartRateCard(d) }
                    if (d.hrv.hasData) item { HrvCard(d.hrv) }
                    if (d.spo2.hasData) item { Spo2Card(d.spo2, d.spo2Night) }
                    if (d.breathing.hasData) item { BreathingCard(d.breathing) }
                    if (d.temperature.hasData) item { TemperatureCard(d.temperature) }
                    if (d.vo2.hasData) item { Vo2Card(d.vo2) }
                    item {
                        BloodPressureCard(container, mergeBp(manualBp, d.bloodPressure), d.bloodPressure != null) { bpSheet = true }
                    }
                    item {
                        GlassCard(onClick = { nav.push(Overlay.CameraHr) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBubble(Duo.Favorite, th.danger)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Measure with your camera", style = FitType.section, color = th.text)
                                    Caption("No watch? Get a heart-rate estimate with your fingertip · beta")
                                }
                                androidx.compose.material3.Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
                            }
                        }
                    }
                    if (d.available && d.anyPermitted) {
                        val missing = buildList {
                            if (d.heartRate.latest == null && d.heartRate.daily.isEmpty()) add("heart rate")
                            if (!d.hrv.hasData) add("HRV")
                            if (!d.spo2.hasData) add("blood oxygen")
                            if (!d.breathing.hasData) add("breathing rate")
                            if (!d.temperature.hasData) add("temperature")
                            if (!d.vo2.hasData) add("VO₂ max")
                        }
                        if (missing.isNotEmpty()) item {
                            GlassCard {
                                CardHeader(Duo.Info, "Not seeing everything?", th.water)
                                Spacer(Modifier.height(8.dp))
                                Caption("No recent ${missing.joinToString(", ")} data. Not every watch measures all of these, and some only measure while you sleep. Make sure your watch app shares with Health Connect and that MyFit is allowed to read it.")
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GlassButton("Devices", { nav.push(Overlay.Devices) }, Modifier.weight(1f), icon = Duo.Watch, height = 42.dp)
                                    GlassButton("Permissions", askPerms, Modifier.weight(1f), icon = Duo.Lock, height = 42.dp)
                                }
                            }
                        }
                    } else if (d.available) item {
                        GlassButton("Connected devices & apps", { nav.push(Overlay.Devices) }, Modifier.fillMaxWidth(), icon = Duo.Watch, height = 44.dp)
                    }
                    item {
                        Caption(
                            "Readings come from your devices and are shown as recorded. MyFit doesn't diagnose anything — if a number worries you or you feel unwell, talk to a doctor.",
                            Modifier.padding(horizontal = 6.dp), color = th.textFaint,
                        )
                    }
                }
            }
        }
        BpEntrySheet(container, bpSheet) { bpSheet = false }
    }
}

/** Shared layout: header, big value, when/source line, optional chart, notes. */
@Composable
private fun VitalCard(
    icon: ImageVector,
    title: String,
    color: Color,
    value: String,
    unit: String,
    whenSource: String,
    chart: List<ChartPoint>,
    format: (Double) -> String,
    chartTitle: String,
    notes: List<Pair<String, Color?>>,
    extra: @Composable () -> Unit = {},
) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(icon, title, color) { DataBadge(DataKind.RECORDED) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = FitType.metric, color = th.text)
            Spacer(Modifier.width(6.dp))
            Text(unit, style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 4.dp))
        }
        if (whenSource.isNotEmpty()) Caption(whenSource)
        extra()
        if (chart.size >= 2) {
            Spacer(Modifier.height(12.dp))
            Text(chartTitle, style = FitType.label, color = th.textDim)
            Spacer(Modifier.height(4.dp))
            ProgressChart(chart, color, format, Modifier.fillMaxWidth())
        }
        notes.forEach { (t, c) ->
            Spacer(Modifier.height(8.dp))
            Caption(t, color = c)
        }
    }
}

private fun whenSource(r: VitalsReader.Reading?, days: List<VitalsReader.DayStat>): String = when {
    r != null -> fmtWhen(r.at) + " · " + appName(r.pkg)
    days.isNotEmpty() -> fmtDay(days.last().date) + " · daily summary from Health Connect"
    else -> ""
}

private fun latestValue(s: VitalsReader.Series): Double? = s.latest?.value ?: s.days.lastOrNull()?.avg

@Composable
private fun HeartRateCard(d: VitalsReader.All) {
    val th = LocalFitTheme.current
    val hr = d.heartRate
    val rest = d.resting
    val restVal = latestValue(rest)
    val big = hr.latest?.value ?: restVal ?: hr.daily.lastOrNull()?.avg
    val src = when {
        hr.latest != null -> whenSource(hr.latest, emptyList())
        rest.hasData -> "Resting · " + whenSource(rest.latest, rest.days)
        else -> whenSource(null, hr.daily)
    }
    val restingChart = rest.days.size >= 2
    val notes = buildList<Pair<String, Color?>> {
        trendSentence("Resting HR", "bpm", rest.days)?.let { add(it to th.text) }
        weekVsWindow(rest.days)?.let { (w, m) ->
            if (w - m >= 3) add("A higher resting heart rate than usual can follow poor sleep, stress, alcohol, illness or hard training." to null)
        }
        add("Resting heart rate for adults is typically 60–100 bpm (often lower if you're fit)." to th.textFaint)
    }
    VitalCard(
        Duo.Favorite, "Heart rate", th.danger,
        big?.roundToInt()?.toString() ?: "—", "bpm", src,
        if (restingChart) dayPoints(rest.days) else dayPoints(hr.daily),
        { "${it.roundToInt()} bpm" },
        if (restingChart) "Resting heart rate · 30 days" else "Daily average heart rate · 30 days",
        notes,
    ) {
        val today = hr.today
        if (today != null || restVal != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (today != null) MiniStat("Today", "${today.min.roundToInt()}–${today.max.roundToInt()} bpm", "avg ${today.avg.roundToInt()}", Modifier.weight(1f))
                if (restVal != null) MiniStat("Resting", "${restVal.roundToInt()} bpm", rest.latest?.let { fmtWhen(it.at) } ?: rest.days.lastOrNull()?.let { fmtDay(it.date) } ?: "", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Text(label.uppercase(), style = FitType.overline, color = th.textDim)
        Text(value, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotEmpty()) Caption(sub)
    }
}

@Composable
private fun HrvCard(s: VitalsReader.Series) {
    val th = LocalFitTheme.current
    val notes = buildList<Pair<String, Color?>> {
        trendSentence("HRV", "ms", s.days)?.let { add(it to th.text) }
        add("HRV (RMSSD) varies a lot between people — compare with your own usual. Higher than your usual usually means better recovery." to null)
    }
    VitalCard(
        Duo.Pulse, "Heart rate variability", th.sleep,
        latestValue(s)?.roundToInt()?.toString() ?: "—", "ms", whenSource(s.latest, s.days),
        dayPoints(s.days), { "${it.roundToInt()} ms" }, "Daily average · 30 days", notes,
    )
}

@Composable
private fun Spo2Card(s: VitalsReader.Series, night: Double?) {
    val th = LocalFitTheme.current
    val latest = latestValue(s)
    val notes = buildList<Pair<String, Color?>> {
        if (latest != null && latest <= 90.0) {
            add("A reading of 90% or lower can be serious. Re-check while keeping still. If it stays this low, or you're short of breath, confused or have chest pain, seek medical care now." to th.danger)
        }
        val week = s.days.filter { !it.date.isBefore(Clock.today().minusDays(6)) }
        if (week.size >= 5 && week.count { it.avg < 95.0 } >= 4) {
            add("Your readings have been below 95% on most days this week. If that continues, it's worth talking to a doctor." to th.warning)
        }
        add("Typical: 95–100%. Watch readings can dip briefly during sleep or if the strap is loose." to th.textFaint)
    }
    VitalCard(
        Duo.Drop, "Blood oxygen (SpO₂)", th.water,
        latest?.let { num(it, 0) } ?: "—", "%", whenSource(s.latest, s.days),
        dayPoints(s.days), { "${num(it, 1)} %" }, "Daily average · 30 days", notes,
    ) {
        if (night != null) {
            Spacer(Modifier.height(10.dp))
            MiniStat("Last night", "${num(night, 1)} %", "average while you slept")
        }
    }
}

@Composable
private fun BreathingCard(s: VitalsReader.Series) {
    val th = LocalFitTheme.current
    val notes = buildList<Pair<String, Color?>> {
        trendSentence("Breathing rate", "breaths/min", s.days, decimals = 1, closeBand = 0.8)?.let { add(it to th.text) }
        add("At rest, adults usually breathe 12–20 times a minute. Watches mostly measure it during sleep." to th.textFaint)
    }
    VitalCard(
        Duo.Cloud, "Breathing rate", th.steps,
        latestValue(s)?.let { num(it, 1) } ?: "—", "breaths/min", whenSource(s.latest, s.days),
        dayPoints(s.days), { "${num(it, 1)} /min" }, "Daily average · 30 days", notes,
    )
}

@Composable
private fun TemperatureCard(s: VitalsReader.Series) {
    val th = LocalFitTheme.current
    val c = latestValue(s)
    val notes = buildList<Pair<String, Color?>> {
        if (c != null && c >= 38.0) add("That's a fever (38 °C / 100.4 °F or above). Rest, drink fluids, and see a doctor if it's very high, lasts more than a few days or you feel very unwell." to th.warning)
        add("Typical body temperature is about 36.1–37.2 °C (97–99 °F)." to th.textFaint)
    }
    VitalCard(
        Duo.Flame, "Body temperature", th.protein,
        c?.let { num(it, 1) } ?: "—", "°C" + (c?.let { " · ${num(it * 9 / 5 + 32, 1)} °F" } ?: ""), whenSource(s.latest, s.days),
        dayPoints(s.days), { "${num(it, 1)} °C" }, "Daily average · 30 days", notes,
    )
}

@Composable
private fun Vo2Card(s: VitalsReader.Series) {
    val th = LocalFitTheme.current
    VitalCard(
        Duo.DirectionsRun, "VO₂ max", th.accent,
        latestValue(s)?.let { num(it, 1) } ?: "—", "ml/kg/min", whenSource(s.latest, s.days),
        dayPoints(s.days), { num(it, 1) }, "Last 90 days",
        listOf("An estimate of your aerobic fitness, worked out by your watch from runs or brisk walks with heart rate. Higher is fitter; it changes slowly over weeks." to null),
    )
}

// =====================================================================================  Camera HR / Devices

@Composable
fun CameraHeartRateScreen(container: AppContainer) {
    CameraHrContent(container)
}

@Composable
fun DevicesScreen(container: AppContainer) {
    DevicesContent(container)
}

// =====================================================================================  Dashboard tile

@Composable
fun VitalsTile(container: AppContainer, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val today = remember { Clock.today() }
    val days by remember { container.db.healthDao().observeDaily(Clock.dateKey(today.minusDays(6)), Clock.dateKey(today)) }
        .collectAsState(initial = emptyList())
    val restDay = days.lastOrNull { it.restingHr != null }
    val hrDay = days.lastOrNull { it.avgHr != null }
    val spo2Day = days.lastOrNull { it.spo2Pct != null }
    GlassCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Duo.Favorite, th.danger, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text("Vitals", style = FitType.label, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        val bpm = restDay?.restingHr ?: hrDay?.avgHr
        if (bpm == null && spo2Day == null) {
            Text("—", style = FitType.title, color = th.textFaint)
            OneLine("Connect your watch")
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(bpm?.toString() ?: "—", style = FitType.title, color = th.text)
                Spacer(Modifier.width(4.dp))
                Text(if (restDay != null) "bpm rest" else "bpm", style = FitType.caption, color = th.textDim, modifier = Modifier.padding(bottom = 3.dp))
            }
            OneLine(spo2Day?.spo2Pct?.let { "SpO₂ ${it.roundToInt()}%" } ?: "Heart rate")
        }
    }
}

@Composable
private fun OneLine(text: String) {
    Text(text, style = FitType.caption, color = LocalFitTheme.current.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
