package com.myfit.tracker.ui.gym

import com.myfit.tracker.ui.theme.Duo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetType
import com.myfit.tracker.domain.DistanceUnit
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.UnitPrefs
import com.myfit.tracker.domain.Units
import com.myfit.tracker.domain.WeightUnit
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

val setTypeOptions = listOf(
    SetType.WORKING to "Working", SetType.WARMUP to "Warm-up", SetType.DROP to "Drop",
    SetType.FAILURE to "Failure", SetType.AMRAP to "AMRAP", SetType.ASSISTED to "Assisted",
)

/** All inputs for one set, chosen by how the exercise is measured. */
@Composable
fun SetInputs(m: String, d: Draft, onChange: (Draft) -> Unit, u: UnitPrefs, stepKg: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (m) {
            MeasurementType.WEIGHT_REPS -> {
                WeightField("Weight", d.weightKg, u, stepKg) { onChange(d.copy(weightKg = it)) }
                IntField("Reps", d.reps, 1) { onChange(d.copy(reps = it)) }
            }
            MeasurementType.BODYWEIGHT_REPS -> {
                IntField("Reps", d.reps, 1) { onChange(d.copy(reps = it)) }
                WeightField("Added weight (optional)", d.weightKg, u, stepKg, allowEmpty = true) { onChange(d.copy(weightKg = it)) }
            }
            MeasurementType.ASSISTED_REPS -> {
                IntField("Reps", d.reps, 1) { onChange(d.copy(reps = it)) }
                WeightField("Assistance (optional)", d.weightKg, u, stepKg, allowEmpty = true) { onChange(d.copy(weightKg = it)) }
            }
            MeasurementType.REPS_ONLY -> IntField("Reps", d.reps, 1) { onChange(d.copy(reps = it)) }
            MeasurementType.DURATION -> DurationField(d.durationSec) { onChange(d.copy(durationSec = it)) }
            MeasurementType.WEIGHT_DURATION -> {
                WeightField("Weight", d.weightKg, u, stepKg, allowEmpty = true) { onChange(d.copy(weightKg = it)) }
                DurationField(d.durationSec) { onChange(d.copy(durationSec = it)) }
            }
            MeasurementType.DISTANCE_DURATION -> {
                DistanceField(d.distanceM, u.distance) { onChange(d.copy(distanceM = it)) }
                DurationField(d.durationSec) { onChange(d.copy(durationSec = it)) }
            }
        }
    }
}

@Composable
private fun BigStepper(label: String, value: String, unit: String?, onMinus: () -> Unit, onPlus: () -> Unit, onTapValue: () -> Unit, height: Dp = 84.dp, sub: String? = null) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    Column {
        Text(label.uppercase(), style = FitType.overline, color = th.textDim, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Glass(Modifier.fillMaxWidth().height(height), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton(Duo.Remove) { tick(); onMinus() }
                Box(Modifier.weight(1f).fillMaxSize().clickableNoRipple(onTapValue), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedContent(value, transitionSpec = {
                            (slideInVertically(spring(0.7f, 600f)) { -it / 2 } + fadeIn()).togetherWith(slideOutVertically { it / 2 } + fadeOut())
                        }, label = "stepVal") { v ->
                            Text(v, style = FitType.display.copy(fontSize = FitType.display.fontSize * 1.15f), color = th.text, textAlign = TextAlign.Center)
                        }
                        if (unit != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(unit, style = FitType.section, color = th.textDim, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    if (sub != null) Text(sub, style = FitType.label, color = th.textFaint)
                    }
                }
                StepButton(Duo.Add) { tick(); onPlus() }
            }
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val cb = androidx.compose.runtime.rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.85f else 1f, spring(0.5f, 700f), label = "stepPress")
    // tap = one step; hold = repeats, getting faster the longer it's held
    Glass(Modifier.size(64.dp).graphicsLayerScale(scale).pointerInput(Unit) {
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                cb.value()
                val job = launch {
                    delay(420)
                    var gap = 170L
                    while (true) { cb.value(); delay(gap); gap = (gap * 0.86).toLong().coerceAtLeast(45L) }
                }
                waitForUpOrCancellation()
                job.cancel(); pressed = false
            }
        }
    }, shape = CircleShape) {
        Icon(icon, null, tint = th.text, modifier = Modifier.align(Alignment.Center).size(30.dp))
    }
}

private fun Modifier.graphicsLayerScale(s: Float) = this.then(androidx.compose.ui.Modifier.graphicsLayer { scaleX = s; scaleY = s })

/** Weight in canonical kg; steps snap to plate increments in the display unit. */
@Composable
fun WeightField(label: String, kg: Double?, u: UnitPrefs, stepKg: Double, allowEmpty: Boolean = false, onChange: (Double?) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    // kg: the chosen step (1 kg by default); lb: 5 lb, a common plate jump. Typing still accepts decimals (22.5).
    val step = if (u.weight == WeightUnit.KG) stepKg else 5.0
    val display = kg?.let { Units.kgTo(it, u.weight) }
    fun snap(dir: Int) {
        val cur = display ?: 0.0
        val next = ((floor(cur / step + 1e-9) + if (dir > 0) 1 else if (cur % step > 1e-6) 0 else -1) * step).coerceAtLeast(0.0)
        onChange(if (allowEmpty && next == 0.0) null else Units.toKg(next, u.weight))
    }
    // the other unit, small underneath (lb ↔ kg)
    val other = if (u.weight == WeightUnit.KG) WeightUnit.entries.firstOrNull { it != WeightUnit.KG } else WeightUnit.KG
    val sub = if (kg != null && kg > 0 && other != null) "≈ ${Fmt.trim(Units.kgTo(kg, other), 1)} ${other.label}" else null
    BigStepper(label, display?.let { Fmt.trim(it, 2) } ?: "—", u.weight.label, { snap(-1) }, { snap(1) }, { typing = true }, sub = sub)
    if (typing) NumberPadDialog(label, display?.let { Fmt.trim(it, 2) } ?: "", u.weight.label, true, onDismiss = { typing = false }) { s ->
        onChange(s.toDoubleOrNull()?.let { Units.toKg(it, u.weight) }); typing = false
    }
}

@Composable
fun IntField(label: String, v: Int?, step: Int, onChange: (Int?) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    BigStepper(label, v?.toString() ?: "—", null, { onChange(((v ?: 0) - step).coerceAtLeast(0)) }, { onChange((v ?: 0) + step) }, { typing = true })
    if (typing) NumberPadDialog(label, v?.toString() ?: "", "", false, onDismiss = { typing = false }) { s -> onChange(s.toIntOrNull()); typing = false }
}

@Composable
fun DistanceField(m: Double?, du: DistanceUnit, onChange: (Double?) -> Unit) {
    var typing by remember { mutableStateOf(false) }
    val disp = m?.let { Units.mTo(it, du) }
    BigStepper("Distance", disp?.let { Fmt.num(it, 2) } ?: "—", du.label,
        { onChange(((disp ?: 0.0) - 0.1).coerceAtLeast(0.0).let { Units.toM((it * 100).roundToInt() / 100.0, du) }) },
        { onChange(((disp ?: 0.0) + 0.1).let { Units.toM((it * 100).roundToInt() / 100.0, du) }) },
        { typing = true })
    if (typing) NumberPadDialog("Distance", disp?.let { Fmt.trim(it, 2) } ?: "", du.label, true, onDismiss = { typing = false }) { s ->
        onChange(s.toDoubleOrNull()?.let { Units.toM(it, du) }); typing = false
    }
}

/** Duration with ±15 s steps, typed mm:ss, and a stopwatch that fills the value when stopped. */
@Composable
fun DurationField(sec: Long?, onChange: (Long?) -> Unit) {
    val th = LocalFitTheme.current
    var typing by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(200) } }
    val shown = if (running) ((now - startedAt) / 1000) else sec
    BigStepper("Duration", shown?.let { mmss(it) } ?: "—", null,
        { if (!running) onChange(((sec ?: 0) - 15).coerceAtLeast(0)) }, { if (!running) onChange((sec ?: 0) + 15) }, { if (!running) typing = true })
    Spacer(Modifier.height(4.dp))
    GlassChip(if (running) "Stop stopwatch" else "Start stopwatch", running, {
        if (running) { running = false; onChange((System.currentTimeMillis() - startedAt) / 1000) }
        else { startedAt = System.currentTimeMillis(); now = startedAt; running = true }
    }, icon = if (running) Duo.Stop else Duo.PlayArrow)
    if (typing) NumberPadDialog("Duration (seconds, or m:ss)", sec?.let { mmss(it) } ?: "", "", true, allowColon = true, onDismiss = { typing = false }) { s ->
        onChange(parseDuration(s)); typing = false
    }
    if (running) Caption("Timing… tap Stop when you finish the set.", color = th.accentBright)
}

fun parseDuration(s: String): Long? {
    val t = s.trim()
    if (t.isEmpty()) return null
    val parts = t.split(":").map { it.toLongOrNull() ?: return null }
    return when (parts.size) { 1 -> parts[0]; 2 -> parts[0] * 60 + parts[1]; 3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]; else -> null }
}

@Composable
fun NumberPadDialog(
    title: String, initial: String, unit: String, decimal: Boolean, allowColon: Boolean = false,
    onDismiss: () -> Unit, onDone: (String) -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (allowColon) {
                androidx.compose.material3.OutlinedTextField(v, { s -> v = s.filter { it.isDigit() || it == ':' }.take(8) }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            } else NumberInput(v, { v = it }, unit, decimal = decimal)
        },
        confirmButton = { TextButton({ onDone(v) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SetTypeRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(setTypeOptions) { (k, l) -> GlassChip(l, selected == k, { onSelect(k) }) }
    }
}

@Composable
fun RpeRow(rpe: Double?, onSelect: (Double?) -> Unit) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("RPE", style = FitType.label, color = th.textDim, modifier = Modifier.width(40.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)) { r ->
                GlassChip(Fmt.trim(r, 1), rpe == r, { onSelect(if (rpe == r) null else r) })
            }
        }
    }
}
