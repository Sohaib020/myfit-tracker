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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import com.myfit.tracker.ui.components.clickableNoRipple
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
                Caption(if (AppUpdater.canInstall(ctx)) "Installs automatically when you leave the app — your data stays as it is." else "First, allow MyFit to install updates (one-time), then tap Install again.")
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
        ToggleRow("Download updates automatically", "Only on Wi-Fi", auto) { auto = it; AppUpdater.setAutoDownload(ctx, it) }
        var autoI by remember { mutableStateOf(AppUpdater.autoInstall(ctx)) }
        ToggleRow("Install updates automatically", "When you leave the app — no tap needed after the first update", autoI) { autoI = it; AppUpdater.setAutoInstall(ctx, it) }
        Caption("Android asks you to confirm the first update once; after that updates install on their own.", color = th.textFaint)
    }
    @Suppress("UNUSED_VARIABLE") val unused = Alignment.Center
}

/** 0..1 while an update downloads, 1 when it's ready, null otherwise — the Me pill shows this as a ring. */
@Composable
fun rememberUpdateProgress(): Float? {
    val s by AppUpdater.state.collectAsState()
    return when (val x = s) {
        is AppUpdater.State.Downloading -> if (x.total > 0) (x.done.toFloat() / x.total).coerceIn(0f, 1f) else 0.02f
        is AppUpdater.State.Ready -> 1f
        else -> null
    }
}

/**
 * Dynamic-Island-style update notice: a dark pill drops in at the top, shows what's happening, then shrinks with a
 * spring into the Me pill (top-left), which keeps a progress ring until the update installs.
 */
@Composable
fun UpdateIsland(statusTop: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by AppUpdater.state.collectAsState()
    val key = when (val x = s) { is AppUpdater.State.Downloading -> "d${x.r.build}"; is AppUpdater.State.Ready -> "r${x.r.build}"; is AppUpdater.State.Available -> "a${x.r.build}"; else -> null }
    val p = remember { androidx.compose.animation.core.Animatable(1f) }       // 0 = expanded island, 1 = tucked into the Me pill
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        if (key == null || key == shown) return@LaunchedEffect
        if (key.startsWith("a") && AppUpdater.dismissed(ctx).toString() == key.drop(1)) return@LaunchedEffect
        shown = key
        p.snapTo(1f)
        p.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 260f))
        kotlinx.coroutines.delay(if (key.startsWith("a")) 6000 else 2600)
        p.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 180f))
    }
    if (p.value >= 0.999f || key == null) return
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val t = p.value
        fun lerp(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp) = a + (b - a) * t
        val w = lerp(maxWidth - 40.dp, 40.dp); val h = lerp(66.dp, 40.dp)
        val x = lerp(20.dp, 21.dp); val y = lerp(statusTop + 8.dp, statusTop + 15.dp)
        Box(Modifier.offset(x = x, y = y).size(w, h).graphicsLayerAlpha(if (t > 0.85f) (1f - t) / 0.15f else 1f)
            .clip(RoundedCornerShape(h / 2)).background(androidx.compose.ui.graphics.Color(0xF2000000))
            .then(if (key.startsWith("a")) Modifier.clickableNoRippleCompat { (s as? AppUpdater.State.Available)?.let { r -> scope.launch { AppUpdater.download(ctx, r.r) } } } else Modifier)) {
            val contentAlpha = (1f - t * 2.2f).coerceIn(0f, 1f)
            if (contentAlpha > 0f) Row(Modifier.fillMaxSize().padding(horizontal = 16.dp).graphicsLayerAlpha(contentAlpha), verticalAlignment = Alignment.CenterVertically) {
                val prog = when (val z = s) { is AppUpdater.State.Downloading -> if (z.total > 0) z.done.toFloat() / z.total else 0f; is AppUpdater.State.Ready -> 1f; else -> null }
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        if (prog != null) drawArc(androidx.compose.ui.graphics.Color(0xFF3DDC84), -90f, 360f * prog, false, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    androidx.compose.material3.Icon(com.myfit.tracker.ui.theme.Duo.ArrowDownward, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val title = when (val z = s) {
                        is AppUpdater.State.Downloading -> "Updating MyFit · ${((if (z.total > 0) z.done.toFloat() / z.total else 0f) * 100).toInt()}%"
                        is AppUpdater.State.Ready -> "Update ready"
                        is AppUpdater.State.Available -> "New update · build ${z.r.build}"
                        else -> ""
                    }
                    Text(title, style = FitType.label, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
                    Text(when (s) {
                        is AppUpdater.State.Ready -> "Installs when you leave the app"
                        is AppUpdater.State.Available -> "Tap to download"
                        else -> "Downloading in the background"
                    }, style = FitType.caption, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
            }
        }
    }
}

private fun Modifier.graphicsLayerAlpha(a: Float) = this.then(Modifier.graphicsLayer { alpha = a })
private fun Modifier.clickableNoRippleCompat(onClick: () -> Unit) = this.clickableNoRipple(onClick)
