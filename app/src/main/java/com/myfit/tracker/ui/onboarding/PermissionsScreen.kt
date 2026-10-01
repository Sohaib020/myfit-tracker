package com.myfit.tracker.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.health.PhoneSteps
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

/** Runtime permissions MyFit uses, asked together in one system dialog. */
fun runtimePermissions(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun hasPerm(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

/**
 * Shown once, right after setup (and once for existing users): one tap asks for everything MyFit can
 * read automatically — Samsung Health / Galaxy Watch data through Health Connect, the phone's step
 * sensor, the camera for food photos and notifications for rest timers.
 */
@Composable
fun PermissionsScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hs = container.healthSync
    var step by remember { mutableIntStateOf(0) }        // 0 intro, 1 asking, 2 done
    var hcGranted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableIntStateOf(0) }

    fun finish() {
        scope.launch {
            runCatching { PhoneSteps.snapshot(ctx, container.db) }
            if (hcGranted.any { it in hs.dataPermissions }) {
                val r = hs.sync(if (hs.historyPermission in hcGranted) 90 else 30)
                container.settings.setLastHealthSync(Clock.now(), r.message)
            }
            container.settings.setPermsAsked(true)
        }
    }

    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++; step = 2; finish()
    }
    val health = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res ->
        hcGranted = res
        runtime.launch(runtimePermissions())
    }
    LaunchedEffect(refresh) { hcGranted = hs.granted() }

    fun askAll() {
        step = 1
        if (hs.isAvailable) runCatching { health.launch(hs.allPermissions) }.onFailure { runtime.launch(runtimePermissions()) }
        else runtime.launch(runtimePermissions())
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Pip(PipMood.WAVE, size = 120.dp, interactive = false)
        Spacer(Modifier.height(10.dp))
        Text("Let MyFit track for you", style = FitType.title, color = th.text)
        Spacer(Modifier.height(6.dp))
        Caption("One tap and MyFit fills itself in — steps, workouts, sleep, heart rate and weigh-ins arrive automatically. Everything stays on this phone.")
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermRow(Duo.Watch, th.accent, "Samsung Health & Galaxy Watch",
                "Steps, workouts, sleep, heart rate, SpO₂, weight & body fat, water — via Health Connect",
                hcGranted.any { it in hs.dataPermissions }, hs.isAvailable)
            PermRow(Duo.Footprints, th.steps, "Phone step counter", "Counts steps even without the watch",
                Build.VERSION.SDK_INT < 29 || hasPerm(ctx, Manifest.permission.ACTIVITY_RECOGNITION), PhoneSteps.hasSensor(ctx))
            PermRow(Duo.Camera, th.protein, "Camera", "Snap meals for instant calories & macros", hasPerm(ctx, Manifest.permission.CAMERA), true)
            if (Build.VERSION.SDK_INT >= 33)
                PermRow(Duo.Bell, th.warning, "Notifications", "Rest-timer alerts and reminders", hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS), true)
        }
        Spacer(Modifier.height(14.dp))
        if (!hs.isAvailable) {
            Caption(if (hs.needsUpdate) "Health Connect needs an update before watch data can flow in." else "Health Connect isn't available on this phone, so watch data can't be read.", color = th.warning)
            if (hs.needsUpdate) {
                Spacer(Modifier.height(6.dp))
                Text("Update Health Connect", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(6.dp))
            }
        } else {
            Caption("Tip: Samsung Health shares with Health Connect automatically on recent versions. If watch data doesn't appear, open Samsung Health → Settings → Health Connect and allow it once.", color = th.textFaint)
        }
        Spacer(Modifier.height(18.dp))
        when (step) {
            2 -> AccentButton("All set — let's go", { finish() }, Modifier.fillMaxWidth(), icon = Duo.Check)
            else -> AccentButton(if (step == 1) "Asking…" else "Allow everything", { askAll() }, Modifier.fillMaxWidth(), icon = Duo.Check, enabled = step == 0)
        }
        Spacer(Modifier.height(10.dp))
        Text("Not now", style = FitType.label, color = th.textDim, modifier = Modifier.clickableNoRipple {
            scope.launch { container.settings.setPermsAsked(true) }
        }.padding(10.dp))
    }
}

@Composable
private fun PermRow(icon: ImageVector, color: androidx.compose.ui.graphics.Color, title: String, sub: String, granted: Boolean, available: Boolean) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, color, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = FitType.section, color = if (available) th.text else th.textDim)
                Caption(if (available) sub else "Not available on this phone")
            }
            if (granted) Icon(Duo.CheckCircle, "Allowed", tint = th.success, modifier = Modifier.size(24.dp))
        }
    }
}
