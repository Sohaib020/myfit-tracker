package com.myfit.tracker.ui.mind

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSegmented
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class MedChoice(val script: MedScript, val minutes: Int, val urdu: Boolean, val ambient: Ambient)

/** Hub card: choose a guided session. */
@Composable
fun MeditationCard(onStart: (MedChoice) -> Unit) {
    val th = LocalFitTheme.current
    var sel by remember { mutableStateOf(MedScripts.first()) }
    var minutes by remember { mutableIntStateOf(5) }
    var urdu by remember { mutableStateOf(false) }
    var ambient by remember { mutableStateOf(Ambient.RAIN) }
    val tick = rememberTick()
    GlassCard {
        CardHeader(Duo.Bedtime, "Guided meditation", th.sleep)
        Spacer(Modifier.height(4.dp))
        Caption("Pip guides you, fully offline.")
        Spacer(Modifier.height(12.dp))
        MedScripts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { s ->
                    val c = hueColor(th, s.hue)
                    val on = s.id == sel.id
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 76.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(c.copy(alpha = if (on) 0.42f else 0.14f), c.copy(alpha = if (on) 0.16f else 0.04f))
                                )
                            )
                            .clickableNoRipple { tick(); sel = s }
                            .padding(12.dp),
                    ) {
                        Text(s.title, style = FitType.body, color = th.text)
                        Spacer(Modifier.height(2.dp))
                        Caption(s.subtitle)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 5, 10).forEach { m -> GlassChip("$m min", minutes == m, { minutes = m }, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(12.dp))
        GlassSegmented(listOf(false, true), urdu, { if (it) "اردو · Urdu" else "English" }, { urdu = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Caption("Background")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Ambient.entries.forEach { a -> GlassChip(a.label, ambient == a, { ambient = a }) }
        }
        Spacer(Modifier.height(14.dp))
        AccentButton("Begin · ${sel.title}", { onStart(MedChoice(sel, minutes, urdu, ambient)) }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 52.dp)
    }
}

private fun estSpeakMs(l: MedLine, urdu: Boolean): Long = 1200L + (if (urdu) l.ro.length else l.en.length) * 65L

/** Full-screen guided session. [onClose] gets true when the user wants to log their mood next. */
@Composable
fun MeditationSession(container: AppContainer, choice: MedChoice, onClose: (Boolean) -> Unit) {
    val th = LocalFitTheme.current
    val script = choice.script
    val color = hueColor(th, script.hue)
    val urdu = choice.urdu
    val targetMs = choice.minutes * 60_000L
    val voice = container.pipVoice
    val startedAt = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    var paused by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Boolean?>(null) }
    var lineIdx by remember { mutableIntStateOf(-1) }
    var ambient by remember { mutableStateOf(choice.ambient) }
    var volume by remember { mutableFloatStateOf(0.55f) }
    val player = rememberAmbientPlayer()
    val speaking by voice.speaking.collectAsState()
    val level by voice.level.collectAsState()
    val view = LocalView.current

    DisposableEffect(ended) {
        view.keepScreenOn = !ended
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { voice.stop() } }

    // Play clock (also drives the drifting blobs) — runs only while playing.
    LaunchedEffect(paused, ended) {
        if (paused || ended) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            elapsed += (now - last) / 1_000_000L
            last = now
        }
    }

    // Ambient sound follows play state.
    LaunchedEffect(ambient, paused, ended) {
        if (paused || ended) player.stop() else player.play(ambient)
    }
    LaunchedEffect(volume) { player.volume = volume }

    // Script runner.
    LaunchedEffect(Unit) {
        suspend fun waitPlaying(ms: Long) {
            var left = ms
            while (left > 0 && !ended) {
                delay(100)
                if (!paused) left -= 100
            }
        }
        suspend fun speakLine(l: MedLine) {
            while (!ended) {
                while (paused && !ended) delay(200)
                if (ended) return
                if (urdu) voice.speak(l.ro, ur = l.ur, force = true) else voice.speak(l.en, force = true)
                val started = withTimeoutOrNull(4_000) { voice.speaking.first { it } } != null
                var interrupted = false
                if (started) {
                    withTimeoutOrNull(60_000) {
                        while (voice.speaking.value && !paused && !ended) delay(150)
                    }
                    if (paused) interrupted = true
                } else {
                    // no voice available — give time to read the line instead
                    waitPlaying(estSpeakMs(l, urdu))
                }
                if (!interrupted) return
                voice.stop()
            }
        }

        val lines = script.lines
        lines.forEachIndexed { i, l ->
            if (ended) return@LaunchedEffect
            lineIdx = i
            speakLine(l)
            if (i < lines.lastIndex) {
                val future = lines.drop(i + 1).sumOf { estSpeakMs(it, urdu) }
                val weights = lines.subList(i, lines.lastIndex).sumOf { it.pause.toDouble() }.coerceAtLeast(0.1)
                val budget = targetMs - elapsed - future - 8_000L
                val silence = (budget * (l.pause / weights)).toLong().coerceIn(2_500L, 120_000L)
                waitPlaying(silence)
            }
        }
        // Gentle close: let the ambience linger briefly, then finish.
        waitPlaying((targetMs - elapsed).coerceIn(4_000L, 20_000L))
        ended = true
    }

    LaunchedEffect(ended) {
        if (ended && saved == null) {
            voice.stop()
            player.stop()
            val sec = elapsed / 1000
            saved = if (sec >= 30) {
                saveMindSession(container, "MEDITATION", script.title, sec, startedAt)
                true
            } else false
        }
    }
    BackHandler { if (!ended) ended = true else onClose(false) }

    val secs by remember { derivedStateOf { elapsed / 1000 } }
    val blobColors = remember(color) { listOf(color, th.sleep, th.water, lighten(color, 0.4f)) }

    Box(Modifier.fillMaxSize()) {
        DriftingBlobs({ elapsed / 1000f }, blobColors, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar(script.title, { if (!ended) ended = true else onClose(false) }, subtitle = "${choice.minutes} min · ${if (urdu) "Urdu" else "English"}")
            if (ended) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    SessionDone("meditation", elapsed / 1000, saved, color) { onClose(it) }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Pip(if (speaking && !paused) PipMood.NEUTRAL else PipMood.MEDITATE, size = 92.dp, talking = speaking && !paused, interactive = false, idleActions = false, level = level)
                    Spacer(Modifier.height(24.dp))
                    val l = script.lines.getOrNull(lineIdx)
                    val text = when {
                        paused -> "Paused"
                        l == null -> "Settle in…"
                        urdu -> l.ro
                        else -> l.en
                    }
                    AnimatedContent(text, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) }, label = "line") { t ->
                        Text(t, style = FitType.title, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.heightIn(min = 72.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Caption("${mmss(secs)} / ${mmss(targetMs / 1000)}")
                }
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ambient.entries.forEach { a -> GlassChip(a.label, ambient == a, { ambient = a }) }
                    }
                    if (ambient != Ambient.SILENCE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Duo.VolumeOff, null, tint = th.textDim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                volume, { volume = it }, Modifier.weight(1f), valueRange = 0f..1f,
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = color, inactiveTrackColor = th.textFaint.copy(alpha = 0.3f)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Duo.VolumeUp, null, tint = th.textDim, modifier = Modifier.size(18.dp))
                        }
                    } else Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(if (paused) "Resume" else "Pause", {
                            paused = !paused
                            if (paused) voice.stop()
                        }, Modifier.weight(1f), icon = if (paused) Duo.PlayArrow else Duo.Timer)
                        GlassButton("End", { ended = true }, Modifier.weight(1f), icon = Duo.Stop)
                    }
                }
            }
        }
    }
}
