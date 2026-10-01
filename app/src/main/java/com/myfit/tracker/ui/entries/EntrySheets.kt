package com.myfit.tracker.ui.entries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FitnessCenter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.DailyCheckIn
import com.myfit.tracker.data.db.MeasurementSite
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.SleepCalc
import com.myfit.tracker.domain.Units
import com.myfit.tracker.domain.VolumeUnit
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.DateTimeRow
import com.myfit.tracker.ui.components.GlassSegmented
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import java.time.LocalTime
import kotlin.math.roundToInt

/** Which logging sheet is open. `id == null` → new entry, otherwise edit that entry. */
sealed interface Sheet {
    data object QuickAdd : Sheet
    data class Weight(val id: Long? = null) : Sheet
    data class Water(val id: Long? = null) : Sheet
    data class Measurement(val id: Long? = null) : Sheet
    data class Sleep(val id: Long? = null) : Sheet
    data class Steps(val id: Long? = null) : Sheet
    data class CheckIn(val id: Long? = null) : Sheet
    data class Note(val id: Long? = null) : Sheet
    data object EditProfile : Sheet
    data object EditTargets : Sheet
}

@Composable
fun EntrySheetContent(sheet: Sheet, container: AppContainer, open: (Sheet?) -> Unit) {
    val close = { open(null) }
    when (sheet) {
        Sheet.QuickAdd -> QuickAddContent(container, open)
        is Sheet.Weight -> WeightForm(sheet.id, container, close)
        is Sheet.Water -> WaterForm(sheet.id, container, close)
        is Sheet.Measurement -> MeasurementForm(sheet.id, container, close)
        is Sheet.Sleep -> SleepForm(sheet.id, container, close)
        is Sheet.Steps -> StepsForm(sheet.id, container, close)
        is Sheet.CheckIn -> CheckInForm(sheet.id, container, close)
        is Sheet.Note -> NoteForm(sheet.id, container, close)
        Sheet.EditProfile -> com.myfit.tracker.ui.settings.EditProfileForm(container, close)
        Sheet.EditTargets -> com.myfit.tracker.ui.settings.EditTargetsForm(container, close)
    }
}

// ------------------------------------------------------------------ shared pieces

@Composable
fun FormHeader(title: String, icon: ImageVector, color: Color, editing: Boolean, onDelete: (() -> Unit)?) {
    val th = LocalFitTheme.current
    var confirm by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, color, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.title, color = th.text)
            Caption(if (editing) "Editing a saved entry" else "New entry · time is stamped automatically")
        }
        if (editing && onDelete != null) GlassIconButton(Duo.DeleteOutline, { confirm = true }, tint = th.danger)
    }
    Spacer(Modifier.height(12.dp))
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete this entry?") },
            text = { Text("It will be removed from your history and every total or average that used it will recalculate.") },
            confirmButton = { TextButton({ confirm = false; onDelete?.invoke() }) { Text("Delete", color = th.danger) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NotesField(value: String, onChange: (String) -> Unit, hint: String = "Note (optional)") {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(20.dp)) {
        BasicTextField(
            value, { onChange(it.take(1000)) },
            textStyle = FitType.body.copy(color = th.text),
            cursorBrush = SolidColor(th.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = FitType.body, color = th.textFaint); inner() } },
        )
    }
}

@Composable
private fun Gap(h: Int = 14) = Spacer(Modifier.height(h.dp))

private fun String.num(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() }

// ------------------------------------------------------------------ Quick add grid

private data class QuickItem(val label: String, val icon: ImageVector, val color: Color, val sheet: Sheet)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddContent(container: AppContainer, open: (Sheet?) -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val units = LocalSettings.current.units
    Text("Quick add", style = FitType.title, color = th.text, modifier = Modifier.padding(vertical = 8.dp))
    Caption("Everything is stamped with the exact time and your time zone.")
    Gap()
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    AccentButton("Start workout", {
        scope.launch {
            val active = container.workoutRepo.inProgress.first()
            val id = active?.id ?: container.workoutRepo.startEmpty()
            open(null); nav.push(com.myfit.tracker.ui.nav.Overlay.Gym(id))
        }
    }, Modifier.fillMaxWidth(), icon = Duo.FitnessCenter)
    Gap()
    // one-tap water
    Text("Water — one tap", style = FitType.label, color = th.textDim)
    Gap(8)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(250.0, 500.0, 750.0, 1000.0).forEach { ml ->
            GlassChip(Fmt.volume(ml, if (units.volume == VolumeUnit.L && ml < 1000) VolumeUnit.ML else units.volume), false, {
                container.write {
                    val id = container.logRepo.addWater(ml)
                    toaster.show("Added ${Fmt.volume(ml, units.volume)} of water", "Undo") { container.write { container.logRepo.deleteWater(id) } }
                }
                open(null)
            }, icon = Duo.WaterDrop)
        }
    }
    Gap(20)
    val items = listOf(
        QuickItem("Weight", Duo.MonitorWeight, th.accentBright, Sheet.Weight()),
        QuickItem("Water", Duo.WaterDrop, th.water, Sheet.Water()),
        QuickItem("Sleep", Duo.Bedtime, th.sleep, Sheet.Sleep()),
        QuickItem("Steps", Duo.DirectionsWalk, th.steps, Sheet.Steps()),
        QuickItem("Measure", Duo.Straighten, th.fat, Sheet.Measurement()),
        QuickItem("Check-in", Duo.Mood, th.warning, Sheet.CheckIn()),
        QuickItem("Note", Duo.EditNote, th.textDim, Sheet.Note()),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 4) {
        items.forEach { q ->
            Glass(Modifier.width(76.dp).height(92.dp), shape = RoundedCornerShape(24.dp), onClick = { open(q.sheet) }, pressScale = 0.9f) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBubble(q.icon, q.color, 40.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(q.label, style = FitType.caption, color = th.text)
                }
            }
        }
    }
    Gap()
    Caption("Food, supplements and progress photos arrive in the next builds.")
}

// ------------------------------------------------------------------ Weight

@Composable
private fun WeightForm(id: Long?, c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    var value by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var at by remember { mutableLongStateOf(Clock.now()) }
    val last by remember { c.logRepo.latestWeight() }.collectAsState(initial = null)
    LaunchedEffect(id) {
        if (id != null) c.logRepo.getWeight(id)?.let { e ->
            value = Fmt.trim(Units.kgTo(e.weightKg, units.weight), 2); fat = e.bodyFatPct?.let { Fmt.trim(it, 1) } ?: ""
            note = e.note; at = e.loggedAt
        }
    }
    FormHeader("Weight", Duo.MonitorWeight, th.accentBright, id != null) {
        c.write { c.logRepo.deleteWeight(id!!) }; toaster.show("Weight entry deleted"); close()
    }
    NumberInput(value, { value = it }, units.weight.label)
    if (id == null) last?.let { Gap(6); Caption("Last recorded: ${Fmt.weight(it.weightKg, units.weight)} · ${Clock.zoned(it.loggedAt, it.zoneId).toLocalDate()}") }
    Gap()
    Text("Body fat % (optional — e.g. from a smart scale)", style = FitType.label, color = th.textDim)
    Gap(6)
    NumberInput(fat, { fat = it }, "%", big = false)
    Gap()
    DateTimeRow("When", at, { at = it })
    Gap()
    NotesField(note, { note = it }, "e.g. after breakfast, new scale")
    Gap(20)
    val v = value.num()
    val f = fat.num()
    val valid = v != null && Units.toKg(v, units.weight) in 20.0..400.0 && (fat.isEmpty() || (f != null && f in 2.0..70.0))
    AccentButton(if (id == null) "Save weight" else "Save changes", {
        val kg = Units.toKg(v!!, units.weight)
        c.write {
            if (id == null) c.logRepo.addWeight(kg, f, note.trim(), at) else c.logRepo.updateWeight(id, kg, f, note.trim(), at)
        }
        toaster.show("Saved ${Fmt.weight(kg, units.weight, 2)}"); close()
    }, Modifier.fillMaxWidth(), enabled = valid)
    if (value.isNotEmpty() && !valid) { Gap(6); Caption("Check the values — weight must be realistic, body fat 2–70 %.", color = th.danger) }
}

// ------------------------------------------------------------------ Water

@Composable
private fun WaterForm(id: Long?, c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    val inputUnit = if (units.volume == VolumeUnit.FL_OZ) VolumeUnit.FL_OZ else VolumeUnit.ML
    var value by remember { mutableStateOf("") }
    var at by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(id) {
        if (id != null) c.logRepo.getWater(id)?.let { e -> value = Fmt.trim(Units.mlTo(e.amountMl, inputUnit), 1); at = e.loggedAt }
    }
    FormHeader("Water", Duo.WaterDrop, th.water, id != null) {
        c.write { c.logRepo.deleteWater(id!!) }; toaster.show("Water entry deleted"); close()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(250.0, 500.0, 750.0, 1000.0).forEach { ml ->
            GlassChip(Fmt.trim(Units.mlTo(ml, inputUnit), 1), value.num()?.let { Units.toMl(it, inputUnit) } == ml, { value = Fmt.trim(Units.mlTo(ml, inputUnit), 1) })
        }
    }
    Gap()
    NumberInput(value, { value = it }, inputUnit.label)
    Gap()
    DateTimeRow("When", at, { at = it })
    Gap(20)
    val ml = value.num()?.let { Units.toMl(it, inputUnit) }
    val valid = ml != null && ml > 0 && ml <= 5000
    AccentButton(if (id == null) "Add water" else "Save changes", {
        c.write { if (id == null) c.logRepo.addWater(ml!!, at) else c.logRepo.updateWater(id, ml!!, at) }
        toaster.show("Saved ${Fmt.volume(ml!!, units.volume)}"); close()
    }, Modifier.fillMaxWidth(), enabled = valid)
}

// ------------------------------------------------------------------ Measurements

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasurementForm(id: Long?, c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    var type by remember { mutableStateOf(MeasurementSite.WAIST) }
    var custom by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var at by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(id) {
        if (id != null) c.logRepo.getMeasurement(id)?.let { e ->
            type = e.type; custom = e.customName ?: ""; value = Fmt.trim(Units.cmTo(e.valueCm, units.length), 2); note = e.note; at = e.loggedAt
        }
    }
    FormHeader("Body measurement", Duo.Straighten, th.fat, id != null) {
        c.write { c.logRepo.deleteMeasurement(id!!) }; toaster.show("Measurement deleted"); close()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (MeasurementSite.all + MeasurementSite.CUSTOM).forEach { t ->
            GlassChip(MeasurementSite.label(t), type == t, { type = t })
        }
    }
    if (type == MeasurementSite.CUSTOM) {
        Gap(10)
        NotesField(custom, { custom = it.take(40) }, "Name, e.g. Calf")
    }
    Gap()
    NumberInput(value, { value = it }, units.length.label)
    Gap()
    DateTimeRow("When", at, { at = it })
    Gap()
    NotesField(note, { note = it })
    Gap(20)
    val v = value.num()
    val valid = v != null && Units.toCm(v, units.length) in 5.0..300.0 && (type != MeasurementSite.CUSTOM || custom.isNotBlank())
    AccentButton(if (id == null) "Save measurement" else "Save changes", {
        val cm = Units.toCm(v!!, units.length)
        val cn = if (type == MeasurementSite.CUSTOM) custom.trim() else null
        c.write { if (id == null) c.logRepo.addMeasurement(type, cn, cm, note.trim(), at) else c.logRepo.updateMeasurement(id, type, cn, cm, note.trim(), at) }
        toaster.show("Saved ${MeasurementSite.label(type, cn)} ${Fmt.length(cm, units.length)}"); close()
    }, Modifier.fillMaxWidth(), enabled = valid)
}

// ------------------------------------------------------------------ Sleep

@Composable
private fun SleepForm(id: Long?, c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val profile by c.profileRepo.profile.collectAsState(initial = null)
    val zone = Clock.zone()
    var start by remember { mutableLongStateOf(0L) }
    var end by remember { mutableLongStateOf(0L) }
    var quality by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(id, profile) {
        if (loaded) return@LaunchedEffect
        if (id != null) {
            c.logRepo.getSleep(id)?.let { e -> start = e.startAt; end = e.endAt; quality = e.quality; notes = e.notes }
            loaded = true
        } else {
            // Pre-fill from the profile's usual times — clearly editable, nothing is saved until confirmed.
            val today = Clock.today()
            val wake = profile?.wakeTimeMin ?: (7 * 60)
            val bed = profile?.sleepTimeMin ?: (23 * 60)
            val e = today.atTime(LocalTime.of(wake / 60, wake % 60)).atZone(zone).toInstant().toEpochMilli()
            val bedDay = if (bed > wake) today.minusDays(1) else today
            val s = bedDay.atTime(LocalTime.of(bed / 60, bed % 60)).atZone(zone).toInstant().toEpochMilli()
            end = minOf(e, Clock.now()); start = s
            if (profile != null) loaded = true
        }
    }
    FormHeader("Sleep", Duo.Bedtime, th.sleep, id != null) {
        c.write { c.logRepo.deleteSleep(id!!) }; toaster.show("Sleep entry deleted"); close()
    }
    DateTimeRow("Fell asleep", start, { start = it })
    Gap(10)
    DateTimeRow("Woke up", end, { end = it })
    Gap()
    val mins = SleepCalc.minutes(start, end)
    Glass(Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(if (mins == null) "End must be after start" else Fmt.duration(mins), style = FitType.title, color = if (mins == null) th.danger else th.text)
            Spacer(Modifier.width(8.dp))
            if (mins != null) Caption("calculated")
        }
    }
    Gap()
    RatingRow("Sleep quality", quality, { quality = it })
    Gap()
    NotesField(notes, { notes = it })
    Gap(20)
    val valid = mins != null && mins in 10..(20 * 60)
    AccentButton(if (id == null) "Save sleep" else "Save changes", {
        c.write { if (id == null) c.logRepo.addSleep(start, end, quality, notes.trim()) else c.logRepo.updateSleep(id, start, end, quality, notes.trim()) }
        toaster.show("Saved ${Fmt.duration(mins!!)} of sleep"); close()
    }, Modifier.fillMaxWidth(), enabled = valid)
    if (mins != null && !valid) { Gap(6); Caption("Sleep must be between 10 minutes and 20 hours.", color = th.danger) }
}

// ------------------------------------------------------------------ Steps

@Composable
private fun StepsForm(id: Long?, c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    var isTotal by remember { mutableStateOf(true) }
    var steps by remember { mutableStateOf("") }
    var dist by remember { mutableStateOf("") }
    var active by remember { mutableStateOf("") }
    var at by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(id) {
        if (id != null) c.logRepo.getActivity(id)?.let { e ->
            isTotal = e.isDayTotal; steps = e.steps?.toString() ?: ""; dist = e.distanceM?.let { Fmt.trim(Units.mTo(it, units.distance), 2) } ?: ""
            active = e.activeMinutes?.toString() ?: ""; at = e.loggedAt
        }
    }
    FormHeader("Steps & activity", Duo.DirectionsWalk, th.steps, id != null) {
        c.write { c.logRepo.deleteActivity(id!!) }; toaster.show("Activity entry deleted"); close()
    }
    GlassSegmented(listOf(true, false), isTotal, { if (it) "Today's total" else "Add steps" }, { isTotal = it }, Modifier.fillMaxWidth())
    Gap(6)
    Caption(if (isTotal) "Enter what your step counter shows now. It replaces earlier totals for today." else "Adds on top of today's latest total (e.g. a walk without your phone).")
    Gap()
    NumberInput(steps, { steps = it }, "steps", decimal = false)
    Gap()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Distance (optional)", style = FitType.label, color = th.textDim); Gap(6)
            NumberInput(dist, { dist = it }, units.distance.label, big = false)
        }
        Column(Modifier.weight(1f)) {
            Text("Active min (optional)", style = FitType.label, color = th.textDim); Gap(6)
            NumberInput(active, { active = it }, "min", decimal = false, big = false)
        }
    }
    Gap()
    DateTimeRow("When", at, { at = it })
    Gap(20)
    val st = steps.toIntOrNull()
    val dm = dist.num()?.let { Units.toM(it, units.distance) }
    val am = active.toIntOrNull()
    val valid = st != null && st in 0..200_000 && (dist.isEmpty() || dm != null) && (active.isEmpty() || am != null)
    AccentButton(if (id == null) "Save" else "Save changes", {
        c.write { if (id == null) c.logRepo.addActivity(st, isTotal, dm, am, null, at) else c.logRepo.updateActivity(id, st, isTotal, dm, am, null, at) }
        toaster.show("Saved ${Fmt.int(st!!)} steps"); close()
    }, Modifier.fillMaxWidth(), enabled = valid)
}

// ------------------------------------------------------------------ Daily check-in

private val faces = listOf("😫", "😣", "😟", "😕", "😐", "🙂", "😊", "😄", "😁", "🤩")

@Composable
fun RatingRow(label: String, value: Int?, onChange: (Int?) -> Unit, invert: Boolean = false) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                if (value != null) {
                    val face = faces[(if (invert) 11 - value else value) - 1]
                    Text(face, style = FitType.title)
                    Spacer(Modifier.width(8.dp))
                    Text("$value/10", style = FitType.section, color = th.text)
                    Spacer(Modifier.width(8.dp))
                    Text("clear", style = FitType.caption, color = th.textDim, modifier = Modifier.clickableNoRipple { onChange(null) }.padding(4.dp))
                } else Text("not rated", style = FitType.caption, color = th.textFaint)
            }
            Slider(
                value = (value ?: 5).toFloat(),
                onValueChange = { onChange(it.roundToInt().coerceIn(1, 10)) },
                valueRange = 1f..10f, steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = th.accentBright, activeTrackColor = if (value == null) th.textFaint else th.accent,
                    inactiveTrackColor = th.textFaint.copy(alpha = 0.3f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
private fun CheckInForm(id: Long?, c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var base by remember { mutableStateOf<DailyCheckIn?>(null) }
    var energy by remember { mutableStateOf<Int?>(null) }
    var mood by remember { mutableStateOf<Int?>(null) }
    var stress by remember { mutableStateOf<Int?>(null) }
    var sleepQ by remember { mutableStateOf<Int?>(null) }
    var sore by remember { mutableStateOf<Int?>(null) }
    var motiv by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    var at by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(id) {
        if (id != null) c.logRepo.getCheckIn(id)?.let { e ->
            base = e; energy = e.energy; mood = e.mood; stress = e.stress; sleepQ = e.sleepQuality; sore = e.soreness; motiv = e.motivation; notes = e.notes; at = e.loggedAt
        }
    }
    FormHeader("Daily check-in", Duo.Mood, th.warning, id != null) {
        c.write { c.logRepo.deleteCheckIn(id!!) }; toaster.show("Check-in deleted"); close()
    }
    Caption("Personal 1–10 ratings — how you feel, not a medical measure. Skip any you like.")
    Gap()
    RatingRow("Energy", energy, { energy = it }); Gap(10)
    RatingRow("Mood", mood, { mood = it }); Gap(10)
    RatingRow("Stress", stress, { stress = it }, invert = true); Gap(10)
    RatingRow("Sleep quality", sleepQ, { sleepQ = it }); Gap(10)
    RatingRow("Muscle soreness", sore, { sore = it }, invert = true); Gap(10)
    RatingRow("Motivation", motiv, { motiv = it }); Gap()
    DateTimeRow("When", at, { at = it })
    Gap()
    NotesField(notes, { notes = it })
    Gap(20)
    val any = listOf(energy, mood, stress, sleepQ, sore, motiv).any { it != null } || notes.isNotBlank()
    AccentButton(if (id == null) "Save check-in" else "Save changes", {
        val entry = (base ?: DailyCheckIn(energy = null, mood = null, stress = null, sleepQuality = null, soreness = null, motivation = null, loggedAt = 0, zoneId = "", localDate = "", createdAt = 0, updatedAt = 0))
            .copy(energy = energy, mood = mood, stress = stress, sleepQuality = sleepQ, soreness = sore, motivation = motiv, notes = notes.trim())
        c.write { if (id == null) c.logRepo.addCheckIn(entry, at) else c.logRepo.updateCheckIn(entry, at) }
        toaster.show("Check-in saved"); close()
    }, Modifier.fillMaxWidth(), enabled = any)
}

// ------------------------------------------------------------------ Note

@Composable
private fun NoteForm(id: Long?, c: AppContainer, close: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var text by remember { mutableStateOf("") }
    var at by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(id) { if (id != null) c.logRepo.getNote(id)?.let { e -> text = e.text; at = e.loggedAt } }
    FormHeader("Note", Duo.EditNote, th.textDim, id != null) {
        c.write { c.logRepo.deleteNote(id!!) }; toaster.show("Note deleted"); close()
    }
    NotesField(text, { text = it }, "What's worth remembering today?")
    Gap()
    DateTimeRow("When", at, { at = it })
    Gap(20)
    AccentButton(if (id == null) "Save note" else "Save changes", {
        c.write { if (id == null) c.logRepo.addNote(text.trim(), at) else c.logRepo.updateNote(id, text.trim(), at) }
        toaster.show("Note saved"); close()
    }, Modifier.fillMaxWidth(), enabled = text.isNotBlank())
}
