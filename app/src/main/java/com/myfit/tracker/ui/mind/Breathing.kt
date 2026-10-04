package com.myfit.tracker.ui.mind

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

internal fun mmss(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private fun ease(f: Float): Float = ((1 - cos(PI * f.coerceIn(0f, 1f))) / 2).toFloat()

/** Where we are inside a breathing session at [ms]. */
private data class BreathPos(val cycle: Int, val index: Int, val frac: Float, val phaseLeftSec: Int, val scale: Float)

private fun breathPos(p: BreathPattern, ms: Long): BreathPos {
    val cycleMs = (p.cycleSec * 1000).toLong().coerceAtLeast(1)
    val cycle = (ms / cycleMs).toInt()
    var t = ms % cycleMs
    // target fullness at the END of each phase
    val ends = FloatArray(p.phases.size)
    var level = 0f
    p.phases.forEachIndexed { i, ph ->
        level = when (ph.kind) {
            BreathKind.IN -> if (p.phases.getOrNull(i + 1)?.kind == BreathKind.IN2) 0.8f else 1f
            BreathKind.IN2 -> 1f
            BreathKind.HOLD -> level
            BreathKind.OUT -> 0f
            BreathKind.HOLD_OUT -> 0f
        }
        ends[i] = level
    }
    p.phases.forEachIndexed { i, ph ->
        val d = (ph.sec * 1000).toLong()
        if (t < d || i == p.phases.lastIndex) {
            val f = if (d > 0) (t.toFloat() / d).coerceIn(0f, 1f) else 1f
            val start = if (i == 0) ends.last() else ends[i - 1]
            val end = ends[i]
            val scale = start + (end - start) * ease(f)
            val left = ceil(((d - t).coerceAtLeast(0)) / 1000.0).toInt()
            return BreathPos(cycle, i, f, left, scale)
        }
        t -= d
    }
    return BreathPos(cycle, 0, 0f, 0, 0f)
}

/** Hub card: pick a pattern and duration, then start. */
@Composable
fun BreathingCard(onStart: (BreathPattern, Int, Boolean) -> Unit) {
    val th = LocalFitTheme.current
    var sel by remember { mutableStateOf(BreathPatterns.first()) }
    var minutes by remember { mutableStateOf(3) }
    var voice by remember { mutableStateOf(false) }
    val tick = rememberTick()
    GlassCard {
        CardHeader(Duo.SelfImprovement, "Breathing", th.water)
        Spacer(Modifier.height(4.dp))
        Caption("Slow, guided breathing with a visual pacer.")
        Spacer(Modifier.height(12.dp))
        BreathPatterns.forEach { p ->
            val c = hueColor(th, p.hue)
            val on = p.id == sel.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (on) c.copy(alpha = 0.16f) else Color.Transparent)
                    .clickableNoRipple { tick(); sel = p }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(
                        Brush.radialGradient(listOf(lighten(c, 0.5f), c))
                    )
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = FitType.body, color = th.text)
                    Caption(p.rhythm)
                }
                Text(p.purpose, style = FitType.label, color = if (on) c else th.textDim)
            }
        }
        Spacer(Modifier.height(6.dp))
        Caption(sel.about)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 5, 10).forEach { m -> GlassChip("$m min", minutes == m, { minutes = m }, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(4.dp))
        ToggleRow("Voice cues", "Pip says “in”, “hold”, “out”", voice) { voice = it }
        Spacer(Modifier.height(8.dp))
        AccentButton("Start breathing", { onStart(sel, minutes, voice) }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 52.dp)
    }
}

/** Full-screen breathing session. [onClose] gets true when the user wants to log their mood next. */
@Composable
fun BreathingSession(container: AppContainer, pattern: BreathPattern, minutes: Int, voiceInit: Boolean, onClose: (Boolean) -> Unit) {
    val th = LocalFitTheme.current
    val color = hueColor(th, pattern.hue)
    val totalMs = minutes * 60_000L
    val startedAt = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Boolean?>(null) }
    var voice by remember { mutableStateOf(voiceInit) }
    val tick = rememberTick()
    val view = LocalView.current

    // Screen stays on only while the session is running.
    DisposableEffect(ended) {
        view.keepScreenOn = !ended
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { if (voice) container.pipVoice.stop() } }

    // Frame clock: runs only while playing.
    LaunchedEffect(paused, ended) {
        if (paused || ended) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsed = (elapsed + (now - last) / 1_000_000L).coerceAtMost(totalMs)
            last = now
            if (elapsed >= totalMs) { ended = true; break }
        }
    }

    val pos by remember(pattern) { derivedStateOf { breathPos(pattern, elapsed) } }
    val phaseKey by remember(pattern) { derivedStateOf { pos.cycle * 100 + pos.index } }
    LaunchedEffect(phaseKey, ended) {
        if (ended) return@LaunchedEffect
        tick()
        if (voice) pattern.phases.getOrNull(pos.index)?.let { container.pipVoice.speak(it.voice, force = true) }
    }
    LaunchedEffect(ended) {
        if (ended && saved == null) {
            val sec = elapsed / 1000
            saved = if (sec >= 30) {
                saveMindSession(container, "BREATHING", pattern.name, sec, startedAt)
                true
            } else false
            if (voice) container.pipVoice.stop()
        }
    }
    BackHandler { if (!ended) ended = true else onClose(false) }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(pattern.name, { if (!ended) ended = true else onClose(false) }, subtitle = "${pattern.rhythm} · $minutes min")
        Box(
            Modifier.fillMaxWidth().weight(1f).drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), Offset(size.width / 2, size.height * 0.42f), size.minDimension * 0.75f),
                    size.minDimension * 0.75f, Offset(size.width / 2, size.height * 0.42f),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            if (!ended) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Box(Modifier.fillMaxWidth().widthIn(max = 360.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        BreathingOrb(pos.scale, elapsed / totalMs.toFloat(), pos.frac, color, Modifier.fillMaxSize())
                        Text(
                            if (paused) "‖" else "${pos.phaseLeftSec}",
                            style = FitType.display, color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    val label = if (paused) "Paused" else pattern.phases.getOrNull(pos.index)?.label ?: ""
                    AnimatedContent(label, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) }, label = "phase") { l ->
                        Text(l, style = FitType.title, color = th.text, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(6.dp))
                    Caption("${mmss((totalMs - elapsed) / 1000)} left")
                }
            } else {
                SessionDone("mindful breathing", elapsed / 1000, saved, color) { onClose(it) }
            }
        }
        if (!ended) {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.myfit.tracker.ui.theme.GlassIconButton(
                    if (voice) Duo.VolumeUp else Duo.VolumeOff,
                    { voice = !voice; if (!voice) container.pipVoice.stop() },
                    size = 52.dp,
                )
                GlassButton(if (paused) "Resume" else "Pause", { paused = !paused; if (paused) container.pipVoice.stop() }, Modifier.weight(1f), icon = if (paused) Duo.PlayArrow else Duo.Timer)
                GlassButton("End", { ended = true }, Modifier.weight(1f), icon = Duo.Stop)
            }
        }
    }
}

@Composable
internal fun SessionDone(what: String, sec: Long, saved: Boolean?, color: Color, onClose: (Boolean) -> Unit) {
    val th = LocalFitTheme.current
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            BreathingOrb(0.55f, 1f, 0f, color, Modifier.fillMaxSize())
            com.myfit.tracker.ui.pip.Pip(com.myfit.tracker.ui.pip.PipMood.PROUD, size = 64.dp, interactive = false, idleActions = false)
        }
        Spacer(Modifier.height(16.dp))
        Text("Nicely done", style = FitType.title, color = th.text)
        Spacer(Modifier.height(4.dp))
        Caption(
            when (saved) {
                true -> "${mmss(sec)} of $what saved."
                false -> "Sessions under 30 seconds aren't saved."
                null -> ""
            },
        )
        Spacer(Modifier.height(20.dp))
        Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("How do you feel now?", style = FitType.section, color = th.text)
                Spacer(Modifier.height(10.dp))
                AccentButton("Mood", { onClose(true) }, Modifier.fillMaxWidth(), icon = Duo.Mood, height = 48.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        GlassButton("Done", { onClose(false) }, Modifier.fillMaxWidth(), icon = Duo.Check)
    }
}
