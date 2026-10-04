package com.myfit.tracker.ui.activity

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Burn
import com.myfit.tracker.domain.EnergyUnit
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayScaffold
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** A running activity stopwatch that survives leaving the screen (and the app being closed). */
object ActivityClock {
    private const val P = "activity_clock"
    var activity by mutableStateOf<String?>(null); private set
    var startedAt by mutableLongStateOf(0L); private set      // 0 = paused / not running
    var accumulated by mutableLongStateOf(0L); private set
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        val p = c.getSharedPreferences(P, Context.MODE_PRIVATE)
        activity = p.getString("a", null); startedAt = p.getLong("s", 0L); accumulated = p.getLong("acc", 0L); loaded = true
    }
    private fun save(c: Context) = c.getSharedPreferences(P, Context.MODE_PRIVATE).edit()
        .putString("a", activity).putLong("s", startedAt).putLong("acc", accumulated).apply()

    val running get() = startedAt > 0L
    val active get() = activity != null
    fun elapsedMs(now: Long = System.currentTimeMillis()) = accumulated + if (running) (now - startedAt).coerceAtLeast(0) else 0

    fun start(c: Context, id: String) { activity = id; accumulated = 0; startedAt = System.currentTimeMillis(); save(c) }
    fun pause(c: Context) { if (running) { accumulated = elapsedMs(); startedAt = 0; save(c) } }
    fun resume(c: Context) { if (!running && active) { startedAt = System.currentTimeMillis(); save(c) } }
    fun reset(c: Context) { activity = null; startedAt = 0; accumulated = 0; save(c) }
}

private fun hms(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StopwatchScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    LaunchedEffect(Unit) { ActivityClock.load(ctx) }
    val latest by container.logRepo.latestWeight().collectAsState(null)
    val profile by container.profileRepo.profile.collectAsState(null)
    val kg = latest?.weightKg ?: profile?.startWeightKg ?: 70.0
    var pick by remember { mutableStateOf(ActivityClock.activity ?: "walk") }
    var summary by remember { mutableStateOf<Pair<Burn.Activity, Long>?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ActivityClock.running) { while (ActivityClock.running) { now = System.currentTimeMillis(); delay(250) } ; now = System.currentTimeMillis() }
    // keep the screen awake while the clock runs
    val view = LocalView.current
    DisposableEffect(ActivityClock.running) { view.keepScreenOn = ActivityClock.running; onDispose { view.keepScreenOn = false } }

    OverlayScaffold("Activity", { nav.pop() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val sum = summary
            AnimatedContent(Triple(sum != null, ActivityClock.active, 0), transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(160))) }, label = "sw") { (showSum, active, _) ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when {
                        showSum && sum != null -> Summary(sum.first, sum.second, kg,
                            onSave = {
                                val (a, ms) = sum
                                val kcal = Burn.kcal(a.met, kg, ms / 1000)
                                container.write { container.logRepo.addActivity(null, false, null, (ms / 60_000).toInt().coerceAtLeast(1), kcal) }
                                toaster.show("Saved · ${a.name} ${hms(ms)} · ${kcal.roundToInt()} ${EnergyUnit.label}")
                                summary = null; nav.pop()
                            },
                            onDiscard = { summary = null })
                        !active -> {
                            Text("What are you doing?", style = FitType.section, color = th.text)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Burn.activities.forEach { a -> GlassChip(a.name, pick == a.id, { pick = a.id }) }
                            }
                            val a = Burn.byId(pick)!!
                            Caption("${Burn.intensity(a.met)} · about ${Burn.kcal(a.met, kg, 3600).roundToInt()} ${EnergyUnit.label}/hour at your weight")
                            Spacer(Modifier.height(8.dp))
                            AccentButton("Start", { ActivityClock.start(ctx, pick) }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow)
                        }
                        else -> {
                            val a = Burn.byId(ActivityClock.activity) ?: Burn.activities.first()
                            val ms = ActivityClock.elapsedMs(now)
                            val kcal = Burn.kcal(a.met, kg, ms / 1000)
                            Dial(ms, ActivityClock.running, a.name, kcal)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (ActivityClock.running) GlassButton("Pause", { ActivityClock.pause(ctx) }, Modifier.weight(1f), icon = Duo.Pause)
                                else GlassButton("Resume", { ActivityClock.resume(ctx) }, Modifier.weight(1f), icon = Duo.PlayArrow)
                                AccentButton("Finish", {
                                    ActivityClock.pause(ctx)
                                    val total = ActivityClock.elapsedMs()
                                    ActivityClock.reset(ctx)
                                    if (total < 60_000) toaster.show("Less than a minute — not saved") else summary = a to total
                                }, Modifier.weight(1f), icon = Duo.Check)
                            }
                            Caption("Calories are an estimate: MET ${a.met} × your weight × time (Compendium of Physical Activities).", Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dial(ms: Long, running: Boolean, name: String, kcal: Double) {
    val th = LocalFitTheme.current
    val sweep by animateFloatAsState(((ms / 1000) % 60) / 60f, tween(240), label = "dial")
    Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 14.dp.toPx()
            val inset = sw / 2
            val sz = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            drawArc(th.text.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
            drawArc(th.accentBright, -90f, 360f * sweep, false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name.uppercase(), style = FitType.overline, color = th.textDim)
            Text(hms(ms), style = FitType.display.copy(fontSize = 56.sp), color = th.text)
            Text("${kcal.roundToInt()} ${EnergyUnit.label}", style = FitType.title, color = th.accentBright)
            if (!running) Caption("Paused")
        }
    }
}

@Composable
private fun Summary(a: Burn.Activity, ms: Long, kg: Double, onSave: () -> Unit, onDiscard: () -> Unit) {
    val th = LocalFitTheme.current
    val sec = ms / 1000
    val kcal = Burn.kcal(a.met, kg, sec)
    Text("Nice work!", style = FitType.display, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(hms(ms) to "Time", "${kcal.roundToInt()}" to EnergyUnit.label, Burn.intensity(a.met) to "Intensity").forEach { (v, l) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, style = FitType.title, color = th.text)
                    Caption(l)
                }
            }
        }
    }
    GlassCard {
        Text("What it did for your body", style = FitType.section, color = th.text)
        Spacer(Modifier.height(8.dp))
        Burn.effects(a, a.met, sec, kcal).forEach { e ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", style = FitType.body, color = th.accentBright)
                Spacer(Modifier.width(8.dp))
                Text(e, style = FitType.body, color = th.text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Caption("Estimates based on MET values and your weight, not a medical measurement.")
    }
    AccentButton("Save to today", onSave, Modifier.fillMaxWidth(), icon = Duo.Check)
    GlassButton("Discard", onDiscard, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline)
}
