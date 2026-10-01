package com.myfit.tracker.ui.activity

import com.myfit.tracker.ui.theme.Duo

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.HcDaily
import com.myfit.tracker.data.db.HcSession
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.health.HealthSync
import com.myfit.tracker.health.PhoneSteps
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayScaffold
import com.myfit.tracker.ui.components.Sparkline
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.format.DateTimeFormatter
import java.util.Locale

fun sessionIcon(type: Int): ImageVector {
    val n = HealthSync.exerciseName(type).lowercase()
    return when {
        "run" in n || "jog" in n -> Duo.DirectionsRun
        "walk" in n || "hik" in n -> Duo.DirectionsWalk
        "bik" in n || "cycl" in n -> Duo.DirectionsBike
        "swim" in n -> Duo.Pool
        "strength" in n || "weight" in n || "calisthenics" in n -> Duo.FitnessCenter
        "yoga" in n || "pilates" in n || "stretch" in n -> Duo.SelfImprovement
        else -> Duo.SportsGymnastics
    }
}

fun sessionTitle(s: HcSession) = s.title?.takeIf { it.isNotBlank() } ?: HealthSync.exerciseName(s.exerciseType)

@Composable
fun ActivityScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val ctx = LocalContext.current
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    val hs = container.healthSync
    val today = Clock.today()
    var granted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var syncing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val week by remember(today) { container.healthRepo.dailyRange(today.minusDays(6), today) }.collectAsState(initial = emptyList())
    val month by remember(today) { container.healthRepo.dailyRange(today.minusDays(29), today) }.collectAsState(initial = emptyList())
    val sessions by remember(today) { container.healthRepo.sessionsRange(today.minusDays(29), today) }.collectAsState(initial = emptyList())
    val sleeps by remember(today) { container.healthRepo.sleepRange(today.minusDays(6), today) }.collectAsState(initial = emptyList())
    val phone by remember(today) { container.healthRepo.phoneDaily(today.minusDays(6)) }.collectAsState(initial = emptyMap())
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())

    fun sync(days: Int) {
        if (syncing) return
        syncing = true
        scope.launch {
            val r = hs.sync(days)
            container.settings.setLastHealthSync(Clock.now(), r.message)
            runCatching { PhoneSteps.snapshot(ctx, container.db) }
            syncing = false
            toaster.show(r.message)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res ->
        granted = res
        if (res.isNotEmpty()) sync(if (hs.historyPermission in res) 90 else 30)
    }
    val activityPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scope.launch { PhoneSteps.snapshot(ctx, container.db); toaster.show("Phone step counter enabled") }
        refresh++
    }
    LaunchedEffect(refresh) {
        granted = hs.granted()
        if (granted.isNotEmpty() && (settings.lastHealthSync == null || Clock.now() - settings.lastHealthSync > 10 * 60_000)) sync(7)
        runCatching { PhoneSteps.snapshot(ctx, container.db) }
    }
    val connected = granted.any { it in hs.dataPermissions }

    OverlayScaffold("Activity & heart", { nav.pop() }, settings.lastHealthSync?.let { "Synced ${agoText(it)}" } ?: "Not synced yet", actions = {
        if (connected) GlassIconButton(Duo.Sync, { sync(7) })
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!hs.isAvailable) {
                GlassCard {
                    CardHeader(Duo.HealthAndSafety, "Health Connect needed", th.warning)
                    Spacer(Modifier.height(8.dp))
                    Caption(if (hs.needsUpdate) "Health Connect needs an update from the Play Store." else "Health Connect isn't available on this phone. Steps can still be counted with the phone sensor below.")
                    if (hs.needsUpdate) { Spacer(Modifier.height(10.dp)); GlassButton("Open Play Store", {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }, height = 44.dp) }
                }
            } else if (!connected || granted.size < hs.allPermissions.size) {
                ConnectCard(connected, onConnect = { permLauncher.launch(hs.allPermissions) }, onSettings = {
                    runCatching { ctx.startActivity(hs.settingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }

            StepsWeekCard(week, phone, Targets.on(targets, TargetType.STEPS, today), connected)
            HeartCard(month, week)
            if (sleeps.isNotEmpty()) WatchSleepCard(sleeps)
            SessionsCard(sessions)
            PhoneSensorCard(onEnable = {
                if (Build.VERSION.SDK_INT >= 29) activityPerm.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                else scope.launch { PhoneSteps.snapshot(ctx, container.db) }
            }, enabled = PhoneSteps.hasPermission(ctx), hasSensor = PhoneSteps.hasSensor(ctx))
            WatchCard()
            if (settings.lastHealthSyncMsg.isNotBlank()) Caption("Last sync: ${settings.lastHealthSyncMsg}", color = th.textFaint)
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun agoText(t: Long): String {
    val m = (Clock.now() - t) / 60_000
    return when { m < 1 -> "just now"; m < 60 -> "${m} min ago"; m < 1440 -> "${m / 60} h ago"; else -> "${m / 1440} d ago" }
}

@Composable
private fun ConnectCard(partly: Boolean, onConnect: () -> Unit, onSettings: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Watch, if (partly) "Allow the remaining data" else "Connect Samsung Health & watch", th.accentBright)
        Spacer(Modifier.height(10.dp))
        Text("Automatic steps, distance, floors, calories, heart rate, HRV, blood oxygen, sleep stages and every workout your Galaxy Watch or Samsung Health detects — walks, runs, treadmill, cycling, swimming, strength and more.",
            style = FitType.body, color = th.textDim)
        Spacer(Modifier.height(10.dp))
        Caption("One-time check in Samsung Health: Settings → Health Connect → allow Samsung Health to share all data. MyFit only reads; it never changes Samsung Health.")
        Spacer(Modifier.height(12.dp))
        AccentButton(if (partly) "Review permissions" else "Connect Health Connect", onConnect, Modifier.fillMaxWidth(), icon = Duo.HealthAndSafety)
        Spacer(Modifier.height(8.dp))
        GlassButton("Open Health Connect settings", onSettings, Modifier.fillMaxWidth(), height = 44.dp)
    }
}

@Composable
private fun StepsWeekCard(week: List<HcDaily>, phone: Map<String, Long>, target: Double?, connected: Boolean) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = week.associateBy { it.localDate }
    val values = days.map { d -> byDay[Clock.dateKey(d)]?.steps ?: phone[Clock.dateKey(d)] }
    val withData = values.filterNotNull()
    GlassCard {
        CardHeader(Duo.DirectionsWalk, "Steps · last 7 days", th.steps) { if (withData.isNotEmpty()) DataBadge(DataKind.RECORDED) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(values.last()?.let { Fmt.int(it) } ?: "—", style = FitType.display, color = th.text)
            Spacer(Modifier.width(6.dp))
            Caption("today" + (target?.let { " · target ${Fmt.int(it)}" } ?: ""), Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        val maxV = ((withData.maxOrNull() ?: 0L).toDouble()).coerceAtLeast(target ?: 1.0).coerceAtLeast(1.0)
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val n = values.size
            val gap = size.width * 0.04f
            val bw = (size.width - gap * (n - 1)) / n
            val chartH = size.height - 18.dp.toPx()
            target?.let { t ->
                val y = chartH * (1 - (t / maxV)).toFloat()
                drawLine(th.textFaint, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            values.forEachIndexed { i, v ->
                val x = i * (bw + gap)
                if (v == null) {
                    drawRoundRect(th.textFaint.copy(alpha = 0.25f), Offset(x, chartH - 6.dp.toPx()), Size(bw, 6.dp.toPx()), CornerRadius(6f))
                } else {
                    val h = (chartH * (v / maxV)).toFloat().coerceAtLeast(4.dp.toPx())
                    val met = target != null && v >= target
                    drawRoundRect(
                        Brush.verticalGradient(listOf(if (met) th.success else th.steps.copy(alpha = 0.9f), th.steps.copy(alpha = 0.35f)), chartH - h, chartH),
                        Offset(x, chartH - h), Size(bw, h), CornerRadius(12f),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d -> Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.US), style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        Spacer(Modifier.height(8.dp))
        Caption(when {
            withData.isEmpty() && !connected -> "No step data yet. Connect Health Connect or enable the phone sensor."
            withData.isEmpty() -> "No step data synced for this week yet."
            else -> "Average ${Fmt.int(withData.average().toLong())} on ${withData.size}/7 days with data · grey = no data (not zero)"
        })
    }
}

@Composable
private fun HeartCard(month: List<HcDaily>, week: List<HcDaily>) {
    val th = LocalFitTheme.current
    val t = week.lastOrNull { it.localDate == Clock.dateKey(Clock.today()) }
    val rhr = month.map { it.restingHr?.toDouble() }
    if (month.none { it.restingHr != null || it.avgHr != null || it.hrvMs != null || it.spo2Pct != null }) return
    GlassCard {
        CardHeader(Duo.Favorite, "Heart", th.danger) { DataBadge(DataKind.RECORDED) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Resting", t?.restingHr?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
            Stat("Today avg", t?.avgHr?.let { "$it bpm" } ?: "—", Modifier.weight(1f))
            Stat("Range", if (t?.minHr != null && t.maxHr != null) "${t.minHr}–${t.maxHr}" else "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("HRV", t?.hrvMs?.let { "${Fmt.trim(it, 0)} ms" } ?: month.lastOrNull { it.hrvMs != null }?.let { "${Fmt.trim(it.hrvMs!!, 0)} ms*" } ?: "—", Modifier.weight(1f))
            Stat("SpO₂", t?.spo2Pct?.let { "${Fmt.trim(it, 0)} %" } ?: month.lastOrNull { it.spo2Pct != null }?.let { "${Fmt.trim(it.spo2Pct!!, 0)} %*" } ?: "—", Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
        }
        if (rhr.count { it != null } >= 2) {
            Spacer(Modifier.height(12.dp))
            Caption("Resting heart rate · 30 days")
            Sparkline(rhr, th.danger, Modifier.fillMaxWidth().height(60.dp))
        }
        Spacer(Modifier.height(6.dp))
        Caption("* most recent reading, not today. These are wearable measurements for personal tracking, not medical readings.", color = th.textFaint)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) { Caption(label); Text(value, style = FitType.section, color = th.text) }
}

@Composable
private fun WatchSleepCard(sleeps: List<com.myfit.tracker.data.db.HcSleep>) {
    val th = LocalFitTheme.current
    val last = sleeps.maxByOrNull { it.endAt } ?: return
    val total = (last.endAt - last.startAt) / 60_000
    GlassCard {
        CardHeader(Duo.Bedtime, "Sleep from your watch", th.sleep) { Caption(HealthSync.sourceLabel(last.sourcePackage)) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.duration(total), style = FitType.display, color = th.text)
            Spacer(Modifier.width(8.dp))
            Caption("${Fmt.clock(Clock.minuteOfDay(last.startAt, last.zoneId))}–${Fmt.clock(Clock.minuteOfDay(last.endAt, last.zoneId))} · ${last.localDate}", Modifier.padding(bottom = 6.dp))
        }
        val parts = listOf("Deep" to (last.deepMin to th.sleep), "REM" to (last.remMin to th.fat), "Light" to (last.lightMin to th.water), "Awake" to (last.awakeMin to th.warning))
        val staged = parts.mapNotNull { (l, p) -> p.first?.let { Triple(l, it, p.second) } }.filter { it.second > 0 }
        if (staged.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val sum = staged.sumOf { it.second }.toFloat()
            Canvas(Modifier.fillMaxWidth().height(16.dp)) {
                var x = 0f
                staged.forEach { (_, m, c) -> val w = size.width * (m / sum); drawRoundRect(c, Offset(x, 0f), Size(w - 3f, size.height), CornerRadius(8f)); x += w }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { staged.forEach { (l, m, _) -> Caption("$l ${Fmt.duration(m)}") } }
        }
    }
}

@Composable
private fun SessionsCard(sessions: List<HcSession>) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.US)
    GlassCard {
        CardHeader(Duo.DirectionsRun, "Detected activities · 30 days", th.accentBright)
        Spacer(Modifier.height(10.dp))
        if (sessions.isEmpty()) { Caption("Walks, runs, treadmill sessions and workouts from your watch or Samsung Health will appear here after a sync."); return@GlassCard }
        sessions.take(40).forEach { s ->
            val segs = remember(s.segments) { runCatching { JSONArray(s.segments) }.getOrNull() }
            Glass(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    IconBubble(sessionIcon(s.exerciseType), th.accentBright, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sessionTitle(s), style = FitType.section, color = th.text)
                        Caption(Clock.zoned(s.startAt, s.zoneId).format(fmt) + " · " + mmss((s.endAt - s.startAt) / 1000) + " · " + HealthSync.sourceLabel(s.sourcePackage))
                        val bits = listOfNotNull(
                            s.distanceM?.takeIf { it > 0 }?.let { Fmt.distance(it, u.distance) + if ("treadmill" in HealthSync.exerciseName(s.exerciseType).lowercase()) " (estimate)" else "" },
                            s.steps?.takeIf { it > 0 }?.let { "${Fmt.int(it)} steps" },
                            s.activeKcal?.takeIf { it > 0 }?.let { "${Fmt.int(it.toLong())} kcal (est.)" },
                            s.avgHr?.let { "avg ${it} bpm" }, s.maxHr?.let { "max $it" },
                        )
                        if (bits.isNotEmpty()) Caption(bits.joinToString(" · "), color = th.text)
                        if (segs != null && segs.length() > 0) {
                            val reps = (0 until segs.length()).map { segs.getJSONObject(it) }.filter { it.optInt("reps") > 0 }
                                .groupBy { HealthSync.segmentName(it.optInt("type")) }
                                .map { (n, l) -> "$n ${l.joinToString("/") { it.optInt("reps").toString() }}" }
                            if (reps.isNotEmpty()) Caption("Watch-counted reps: " + reps.joinToString(" · "), color = th.accentBright)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneSensorCard(onEnable: () -> Unit, enabled: Boolean, hasSensor: Boolean) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Sensors, "Phone step counter", th.steps) { Caption(if (enabled) "On" else "Off") }
        Spacer(Modifier.height(8.dp))
        Caption(when {
            !hasSensor -> "This phone has no hardware step counter."
            enabled -> "Counting in the background as a backup. Used only on days Samsung Health/Health Connect has no steps, so steps are never counted twice."
            else -> "Backup counter using the phone's own sensor, for days when Samsung Health isn't recording."
        })
        if (hasSensor && !enabled) { Spacer(Modifier.height(10.dp)); GlassButton("Enable phone step counter", onEnable, height = 44.dp) }
    }
}

@Composable
private fun WatchCard() {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Watch, "Galaxy Watch", th.fat)
        Spacer(Modifier.height(8.dp))
        Caption("Your watch syncs to Samsung Health, which publishes to Health Connect, which MyFit reads — every 30 minutes in the background and whenever you open this screen. Everything the watch detects (auto-detected walks/runs, workouts you start on the watch, counted reps, heart rate, sleep stages, SpO₂) comes through this path.")
        Spacer(Modifier.height(6.dp))
        Caption("A MyFit app running on the watch itself (log sets from your wrist) is a separate Wear OS build — planned for a later phase.", color = th.textFaint)
    }
}
