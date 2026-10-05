package com.myfit.tracker.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.health.PhoneSteps
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

/** Runtime permissions used by Settings → Health & permissions ("allow everything" there). */
fun runtimePermissions(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun hasPerm(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

private enum class PermPage { HEALTH, NOTIFY, STEPS }

/**
 * Shown once, right after setup: one clear page per permission, each with its own "Allow" / "Not now".
 *  1. Health Connect (Samsung Health / Galaxy Watch data) — its own page, like the big fitness apps.
 *  2. Notifications (gentle nudges, rest timer) — Android 13+.
 *  3. Physical activity (the phone's own step counter) — Android 10+.
 * The camera is asked only when you first use it (food photos, heart rate), never up front.
 */
@Composable
fun PermissionsScreen(container: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hs = container.healthSync
    val settings = com.myfit.tracker.ui.theme.LocalSettings.current
    val profile by container.profileRepo.profile.collectAsState(initial = null)
    val female = profile?.sex == com.myfit.tracker.data.db.Sex.FEMALE
    val diabetic = settings.glucoseEnabled || settings.diabetesType !in setOf("none", "unset")
    val askSet = remember(female, diabetic) {
        hs.allPermissions + (if (female) hs.cyclePermissions else emptySet()) + (if (diabetic) hs.glucosePermissions else emptySet())
    }
    val pages = remember {
        buildList {
            add(PermPage.HEALTH)
            if (Build.VERSION.SDK_INT >= 33) add(PermPage.NOTIFY)
            if (Build.VERSION.SDK_INT >= 29 && PhoneSteps.hasSensor(ctx)) add(PermPage.STEPS)
        }
    }
    var i by remember { mutableIntStateOf(0) }
    var hcGranted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var finishing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hcGranted = runCatching { hs.granted() }.getOrDefault(emptySet()) }

    fun finish() {
        if (finishing) return
        finishing = true
        scope.launch {
            runCatching { PhoneSteps.snapshot(ctx, container.db) }
            if (hcGranted.any { it in hs.dataPermissions }) {
                val r = hs.sync(if (hs.historyPermission in hcGranted) 90 else 30)
                container.settings.setLastHealthSync(Clock.now(), r.message)
            }
            container.settings.setPermsAsked(true)
        }
    }
    fun next() { if (i < pages.lastIndex) i++ else finish() }
    BackHandler(enabled = i > 0) { i-- }

    val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res -> hcGranted = res; next() }
    val single = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // progress dots
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
            pages.indices.forEach { k ->
                val th = LocalFitTheme.current
                Box(Modifier.padding(horizontal = 4.dp).size(width = if (k == i) 22.dp else 8.dp, height = 8.dp).clip(CircleShape)
                    .background(if (k <= i) th.accentBright else th.textFaint))
            }
        }
        AnimatedContent(i, Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(spring(0.85f, 300f)) { it * dir / 3 } + fadeIn(tween(250)))
                .togetherWith(slideOutHorizontally(tween(220)) { -it * dir / 3 } + fadeOut(tween(180)))
        }, label = "perm") { k ->
            when (pages[k]) {
                PermPage.HEALTH -> HealthConnectPage(
                    available = hs.isAvailable, needsUpdate = hs.needsUpdate,
                    granted = hcGranted.any { it in hs.dataPermissions },
                    female = female, diabetic = diabetic,
                    onConnect = { runCatching { health.launch(askSet) }.onFailure { next() } },
                    onUpdate = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    onSkip = { next() },
                )
                PermPage.NOTIFY -> SimplePermPage(
                    Duo.Bell, "Stay on track, gently",
                    "Soft reminders for water and movement (never more than 3 a day, never at night), your rest timer between sets and the weekly progress report.",
                    "Allow notifications", granted = Build.VERSION.SDK_INT >= 33 && hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS),
                    onAllow = { if (Build.VERSION.SDK_INT >= 33) single.launch(Manifest.permission.POST_NOTIFICATIONS) else next() },
                    onSkip = { next() },
                )
                PermPage.STEPS -> SimplePermPage(
                    Duo.Footprints, "Count steps with your phone",
                    "Even without a watch, MyFit can count your steps with the phone's own motion sensor. Nothing leaves your phone.",
                    "Allow step counting", granted = Build.VERSION.SDK_INT >= 29 && hasPerm(ctx, Manifest.permission.ACTIVITY_RECOGNITION),
                    onAllow = { if (Build.VERSION.SDK_INT >= 29) single.launch(Manifest.permission.ACTIVITY_RECOGNITION) else next() },
                    onSkip = { next() },
                )
            }
        }
    }
}

/** Health Connect page — a dedicated screen (hero mark, what it brings, Connect / Not now). */
@Composable
private fun HealthConnectPage(
    available: Boolean, needsUpdate: Boolean, granted: Boolean, female: Boolean, diabetic: Boolean,
    onConnect: () -> Unit, onUpdate: () -> Unit, onSkip: () -> Unit,
) {
    val th = LocalFitTheme.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        // our own mark: a heart linked to a watch (no third-party logo)
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(132.dp).clip(CircleShape).background(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.35f), Color.Transparent))))
            Box(Modifier.offset(x = (-18).dp).size(70.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF5A7A), Color(0xFFFF8A5C)))), contentAlignment = Alignment.Center) {
                Icon(Duo.Favorite, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Box(Modifier.offset(x = 22.dp, y = 16.dp).size(62.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(th.accentBright, th.accent)))
                .border(3.dp, th.bgBottom, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Icon(Duo.Watch, null, tint = th.onAccent, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Health Connect", style = FitType.display, color = th.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text("Let MyFit read the activity your phone, Samsung Health and Galaxy Watch already record — so your dashboard fills itself in.",
            style = FitType.body, color = th.textDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Benefit(Duo.Footprints, th.steps, "Steps, distance & calories")
            Benefit(Duo.FitnessCenter, th.accent, "Workouts from your watch")
            Benefit(Duo.Bedtime, th.sleep, "Sleep & heart rate")
            Benefit(Duo.MonitorWeight, th.fat, "Weight & body fat")
            if (female) Benefit(Duo.CalendarMonth, com.myfit.tracker.ui.cycle.CycleColors.period, "Cycle data (stays on this phone)")
            if (diabetic) Benefit(Duo.Drop, th.water, "Blood sugar from your meter or CGM")
        }
        Spacer(Modifier.height(16.dp))
        Caption("You choose exactly what to share on the next screen, and can change it any time in Health Connect settings.", Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp))
        when {
            !available && needsUpdate -> AccentButton("Update Health Connect", onUpdate, Modifier.fillMaxWidth())
            !available -> Caption("Health Connect isn't available on this phone — you can still log everything by hand.", color = th.warning)
            granted -> AccentButton("Connected — continue", onSkip, Modifier.fillMaxWidth(), icon = Duo.Check)
            else -> AccentButton("Enable Health Connect", onConnect, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        GlassButton(if (granted) "Next" else "Not now", onSkip, Modifier.fillMaxWidth(), height = 50.dp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Benefit(icon: ImageVector, color: Color, text: String) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SimplePermPage(icon: ImageVector, title: String, body: String, allow: String, granted: Boolean, onAllow: () -> Unit, onSkip: () -> Unit) {
    val th = LocalFitTheme.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(64.dp))
        Box(Modifier.size(120.dp).clip(CircleShape).background(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.32f), Color.Transparent))), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(th.accentBright, th.accent))), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = th.onAccent, modifier = Modifier.size(38.dp))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = FitType.display, color = th.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, style = FitType.body, color = th.textDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        if (granted) AccentButton("Allowed — continue", onSkip, Modifier.fillMaxWidth(), icon = Duo.Check)
        else AccentButton(allow, onAllow, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (!granted) GlassButton("Not now", onSkip, Modifier.fillMaxWidth(), height = 50.dp)
        Spacer(Modifier.height(20.dp))
    }
}
