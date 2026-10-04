package com.myfit.tracker.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

private fun mb(b: Long) = "%.0f MB".format(b / 1_048_576.0)

/** Banner shown on top of the app when a new build is available / downloading / ready to install. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val s by AppUpdater.state.collectAsState()
    var hidden by remember { mutableIntStateOf(AppUpdater.dismissed(ctx)) }
    val build = when (val x = s) { is AppUpdater.State.Available -> x.r.build; is AppUpdater.State.Ready -> x.r.build; is AppUpdater.State.Downloading -> x.r.build; else -> 0 }
    AnimatedVisibility(build > 0 && build != hidden, modifier, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }) {
        UpdateCard(s, compact = true) { AppUpdater.dismiss(ctx, build); hidden = build }
    }
}

@Composable
private fun UpdateCard(s: AppUpdater.State, compact: Boolean, onLater: (() -> Unit)? = null) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    GlassCard(padding = 14.dp) {
        when (s) {
            is AppUpdater.State.Available -> {
                Text("Update available · build ${s.r.build}", style = FitType.section, color = th.text)
                if (s.r.notes.isNotBlank()) Caption(s.r.notes.lines().take(if (compact) 2 else 8).joinToString("\n"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton("Download${if (s.r.sizeBytes > 0) " · " + mb(s.r.sizeBytes) else ""}", { scope.launch { AppUpdater.download(ctx, s.r) } }, Modifier.weight(1f), height = 42.dp)
                    if (onLater != null) GlassButton("Later", onLater, height = 42.dp)
                }
            }
            is AppUpdater.State.Downloading -> {
                Text("Downloading build ${s.r.build}…", style = FitType.section, color = th.text)
                Spacer(Modifier.height(8.dp))
                val f = if (s.total > 0) (s.done.toFloat() / s.total).coerceIn(0f, 1f) else 0f
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(th.textFaint.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.02f)).clip(CircleShape).background(th.accent))
                }
                Caption("${mb(s.done)} of ${if (s.total > 0) mb(s.total) else "…"}")
            }
            is AppUpdater.State.Ready -> {
                Text("Build ${s.r.build} is ready to install", style = FitType.section, color = th.text)
                Caption(if (AppUpdater.canInstall(ctx)) "Android will ask you to confirm — your data stays as it is." else "First, allow MyFit to install updates (one-time), then tap Install again.")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton("Install update", { if (AppUpdater.canInstall(ctx)) AppUpdater.install(ctx, s.file) else AppUpdater.openInstallPermission(ctx) }, Modifier.weight(1f), height = 42.dp)
                    if (onLater != null) GlassButton("Later", onLater, height = 42.dp)
                }
            }
            is AppUpdater.State.Checking -> Text("Checking for updates…", style = FitType.label, color = th.textDim)
            is AppUpdater.State.UpToDate -> Text("You're on the latest build (${AppUpdater.current}).", style = FitType.label, color = th.success)
            is AppUpdater.State.Failed -> Text(s.msg, style = FitType.label, color = th.warning)
            AppUpdater.State.Idle -> Text("Build ${AppUpdater.current}", style = FitType.label, color = th.textDim)
        }
    }
}

/** Settings section: current build, check now, auto-download on Wi-Fi, download page. */
@Composable
fun AppUpdatesSection() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by AppUpdater.state.collectAsState()
    var auto by remember { mutableStateOf(AppUpdater.autoDownload(ctx)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("APP UPDATES", style = FitType.overline, color = th.textDim)
        UpdateCard(s, compact = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Check now", { scope.launch { AppUpdater.check(ctx, force = true) } }, Modifier.weight(1f), height = 42.dp)
            GlassButton("Download page", {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(AppUpdater.SITE)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }, Modifier.weight(1f), height = 42.dp)
        }
        ToggleRow("Download updates automatically", "Only on Wi-Fi; you still tap Install", auto) { auto = it; AppUpdater.setAutoDownload(ctx, it) }
        Caption("Updates come from MyFit's GitHub releases. Android asks you to confirm each install — nothing installs silently.", color = th.textFaint)
    }
    @Suppress("UNUSED_VARIABLE") val unused = Alignment.Center
}
