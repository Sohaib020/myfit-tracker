package com.myfit.tracker.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.MuscleGroup
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.SetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.UnitPrefs
import com.myfit.tracker.domain.WorkoutCalc
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayScaffold
import com.myfit.tracker.ui.components.Sparkline
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings

/** One past session of an exercise, derived from raw sets. */
data class Session(val workoutId: Long, val date: String, val startedAt: Long, val sets: List<SetRow>)

fun sessionsOf(rows: List<SetRow>): List<Session> =
    rows.groupBy { it.workoutId }.map { (id, s) -> Session(id, s.first().workoutLocalDate, s.first().workoutStartedAt, s) }.sortedBy { it.startedAt }

/** "70 kg × 10", "12 reps (+10 kg)", "1:30", "5.00 km · 25:00" … per measurement type. */
fun formatSet(m: String, s: SetRow, u: UnitPrefs): String = formatSetValues(m, s.weightKg, s.reps, s.durationSec, s.distanceM, u)

fun formatSetValues(m: String, w: Double?, r: Int?, d: Long?, dist: Double?, u: UnitPrefs): String = when (m) {
    MeasurementType.WEIGHT_REPS -> "${w?.let { Fmt.weight(it, u.weight, 2) } ?: "—"} × ${r ?: "—"}"
    MeasurementType.BODYWEIGHT_REPS -> "${r ?: "—"} reps" + (w?.takeIf { it > 0 }?.let { " (+${Fmt.weight(it, u.weight, 2)})" } ?: "")
    MeasurementType.ASSISTED_REPS -> "${r ?: "—"} reps" + (w?.takeIf { it > 0 }?.let { " (−${Fmt.weight(it, u.weight, 2)} assist)" } ?: "")
    MeasurementType.REPS_ONLY -> "${r ?: "—"} reps"
    MeasurementType.DURATION -> d?.let { mmss(it) } ?: "—"
    MeasurementType.WEIGHT_DURATION -> "${w?.let { Fmt.weight(it, u.weight, 2) } ?: "—"} · ${d?.let { mmss(it) } ?: "—"}"
    MeasurementType.DISTANCE_DURATION -> "${dist?.let { Fmt.distance(it, u.distance) } ?: "—"} · ${d?.let { mmss(it) } ?: "—"}"
    else -> "—"
}

fun mmss(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)

fun setTypeShort(t: String) = when (t) {
    SetType.WARMUP -> "W"; SetType.DROP -> "D"; SetType.FAILURE -> "F"; SetType.AMRAP -> "A"; SetType.ASSISTED -> "AS"; else -> ""
}

/** Best set per measurement type. For loaded lifts: heaviest weight, ties broken by reps. */
fun bestSet(m: String, rows: List<SetRow>): SetRow? = when (m) {
    MeasurementType.WEIGHT_REPS, MeasurementType.WEIGHT_DURATION ->
        rows.filter { (it.weightKg ?: 0.0) > 0 }.maxWithOrNull(compareBy<SetRow> { it.weightKg }.thenBy { it.reps ?: 0 })
    MeasurementType.BODYWEIGHT_REPS, MeasurementType.REPS_ONLY, MeasurementType.ASSISTED_REPS ->
        rows.filter { it.reps != null }.maxWithOrNull(compareBy<SetRow> { it.reps }.thenBy { it.weightKg ?: 0.0 })
    MeasurementType.DURATION -> rows.filter { it.durationSec != null }.maxByOrNull { it.durationSec!! }
    MeasurementType.DISTANCE_DURATION -> rows.filter { it.distanceM != null }.maxByOrNull { it.distanceM!! }
    else -> null
}

/** Per-session headline number used for the progress graph. */
fun sessionMetric(m: String, s: Session): Double? = when (m) {
    MeasurementType.WEIGHT_REPS, MeasurementType.WEIGHT_DURATION -> s.sets.mapNotNull { it.weightKg }.maxOrNull()
    MeasurementType.BODYWEIGHT_REPS, MeasurementType.REPS_ONLY, MeasurementType.ASSISTED_REPS -> s.sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
    MeasurementType.DURATION -> s.sets.mapNotNull { it.durationSec }.maxOrNull()?.toDouble()
    MeasurementType.DISTANCE_DURATION -> s.sets.mapNotNull { it.distanceM }.maxOrNull()
    else -> null
}

fun metricLabel(m: String) = when (m) {
    MeasurementType.WEIGHT_REPS, MeasurementType.WEIGHT_DURATION -> "Top weight per session"
    MeasurementType.DURATION -> "Longest set per session"
    MeasurementType.DISTANCE_DURATION -> "Longest distance per session"
    else -> "Most reps per session"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(container: AppContainer, exerciseId: Long) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val u = LocalSettings.current.units
    val ex by remember(exerciseId) { container.exerciseRepo.observe(exerciseId) }.collectAsState(initial = null)
    val history by remember(exerciseId) { container.workoutRepo.exerciseHistory(exerciseId) }.collectAsState(initial = emptyList())
    val e = ex ?: return
    val sessions = remember(history) { sessionsOf(history) }
    val best = remember(history, e.measurementType) { bestSet(e.measurementType, history) }
    var notes by remember(e.id) { mutableStateOf(e.personalNotes) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        Box {
            ExerciseImage(e, Modifier.fillMaxWidth().height(300.dp), animate = true)
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                GlassIconButton(Duo.ArrowBack, { nav.pop() })
                Spacer(Modifier.weight(1f))
                GlassIconButton(Duo.Edit, { nav.push(Overlay.ExerciseEditor(e.id)) })
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(e.name, style = FitType.display.copy(fontSize = FitType.title.fontSize * 1.35f), color = th.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(e.primaryMuscle, true, {})
                GlassChip(equipmentLabel(e.equipment), false, {}, icon = equipmentIcon(e))
                if (e.level.isNotBlank()) GlassChip(e.level.replaceFirstChar { it.uppercase() }, false, {})
                GlassChip(measurementLabel(e.measurementType), false, {})
            }
            if (e.secondaryMuscles.isNotBlank()) Caption("Muscles worked: ${e.secondaryMuscles.replace(",", ", ")}")

            // ---- your history
            GlassCard {
                CardHeader(Duo.EmojiEvents, "Your record", th.warning) {
                    if (history.isNotEmpty()) DataBadge(DataKind.RECORDED)
                }
                Spacer(Modifier.height(12.dp))
                if (history.isEmpty()) {
                    Caption("Not logged yet. Your sessions, best set and progress graph appear here.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Stat("Sessions", "${sessions.size}")
                        Stat("Sets", "${history.size}")
                        best?.let { Stat("Best set", formatSet(e.measurementType, it, u)) }
                    }
                    val est = history.filter { it.setType != SetType.WARMUP }.mapNotNull { WorkoutCalc.estimated1Rm(it.weightKg, it.reps) }.maxOrNull()
                    if (e.measurementType == MeasurementType.WEIGHT_REPS && est != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DataBadge(DataKind.ESTIMATED)
                            Spacer(Modifier.width(8.dp))
                            Caption("Best estimated 1RM ${Fmt.weight(est, u.weight)} (Epley, sets of 1–12 reps). Not a lifted weight.")
                        }
                    }
                    val metrics = sessions.takeLast(12).map { sessionMetric(e.measurementType, it) }
                    if (metrics.count { it != null } >= 2) {
                        Spacer(Modifier.height(14.dp))
                        Caption(metricLabel(e.measurementType) + " · last ${metrics.size} sessions")
                        Spacer(Modifier.height(6.dp))
                        Sparkline(metrics, th.accentBright, Modifier.fillMaxWidth().height(80.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    sessions.takeLast(5).reversed().forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(s.date, style = FitType.caption, color = th.textDim, modifier = Modifier.width(92.dp))
                            Text(s.sets.joinToString("   ") { (setTypeShort(it.setType).let { t -> if (t.isEmpty()) "" else "$t " }) + formatSet(e.measurementType, it, u) },
                                style = FitType.caption, color = th.text)
                        }
                    }
                }
            }

            // ---- how to
            if (e.instructions.isNotBlank()) {
                GlassCard {
                    Text("How to do it", style = FitType.section, color = th.text)
                    Spacer(Modifier.height(10.dp))
                    e.instructions.split("\n").filter { it.isNotBlank() }.forEachIndexed { i, line ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = FitType.label, color = th.accentBright)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(line, style = FitType.body, color = th.textDim)
                        }
                    }
                    if (!e.isCustom) { Spacer(Modifier.height(8.dp)); Caption("Photos & instructions: free-exercise-db (public domain).") }
                }
            }

            // ---- personal notes
            GlassCard {
                Text("Personal notes", style = FitType.section, color = th.text)
                Spacer(Modifier.height(8.dp))
                NotesField(notes, { notes = it }, "Seat height, grip, cues that work for you…")
                if (notes != e.personalNotes) {
                    Spacer(Modifier.height(10.dp))
                    AccentButton("Save notes", { container.write { container.exerciseRepo.setNotes(e.id, notes.trim()) }; toaster.show("Notes saved") }, Modifier.fillMaxWidth(), height = 48.dp)
                }
            }

            if (e.archivedAt == null) GlassButton("Archive exercise", {
                container.write { container.exerciseRepo.archive(e.id) }; toaster.show("Archived — history is kept"); nav.pop()
            }, Modifier.fillMaxWidth(), icon = Duo.Archive)
            else GlassButton("Restore exercise", { container.write { container.exerciseRepo.unarchive(e.id) } }, Modifier.fillMaxWidth(), icon = Duo.Unarchive)
            Caption("Exercises are never deleted, so your history always stays intact. Archiving hides it from lists.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val th = LocalFitTheme.current
    Column {
        Text(label, style = FitType.caption, color = th.textDim)
        Text(value, style = FitType.section, color = th.text)
    }
}

// ------------------------------------------------------------------ custom / edit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditorScreen(container: AppContainer, exerciseId: Long?) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    var loaded by remember { mutableStateOf(exerciseId == null) }
    var base by remember { mutableStateOf<Exercise?>(null) }
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var secondary by remember { mutableStateOf(setOf<String>()) }
    var equipment by remember { mutableStateOf("barbell") }
    var mtype by remember { mutableStateOf(MeasurementType.WEIGHT_REPS) }
    var instructions by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var used by remember { mutableStateOf(0) }
    LaunchedEffect(exerciseId) {
        if (exerciseId != null) container.exerciseRepo.get(exerciseId)?.let { e ->
            base = e; name = e.name; muscle = e.primaryMuscle; secondary = e.secondaryMuscles.split(",").filter { it.isNotBlank() }.toSet()
            equipment = e.equipment; mtype = e.measurementType; instructions = e.instructions; notes = e.personalNotes
            used = container.exerciseRepo.usageCount(e.id); loaded = true
        }
    }
    OverlayScaffold(if (exerciseId == null) "New exercise" else "Edit exercise", { nav.pop() }) {
        if (!loaded) return@OverlayScaffold
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NotesField(name, { name = it.take(80) }, "Exercise name")
            Label("Main muscle group")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleGroup.all.forEach { g -> GlassChip(g, muscle == g, { muscle = g }) }
            }
            Label("Also works (optional)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (MuscleGroup.all - muscle - MuscleGroup.CARDIO - MuscleGroup.OTHER).forEach { g ->
                    GlassChip(g, g in secondary, { secondary = if (g in secondary) secondary - g else secondary + g })
                }
            }
            Label("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                equipmentOptions.forEach { q -> GlassChip(equipmentLabel(q), equipment == q, { equipment = q }) }
            }
            Label("How it's measured")
            if (used > 0) Caption("Locked: this exercise already has $used logged session(s). Changing it would reinterpret your history.", color = th.warning)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allMeasurementTypes.forEach { m -> GlassChip(measurementLabel(m), mtype == m, { if (used == 0) mtype = m }) }
            }
            Label("Instructions (optional)")
            NotesField(instructions, { instructions = it }, "One step per line")
            Label("Personal notes (optional)")
            NotesField(notes, { notes = it })
            AccentButton("Save exercise", {
                val now = Clock.now()
                val e = (base ?: Exercise(name = "", primaryMuscle = muscle, measurementType = mtype, isCustom = true, createdAt = now, updatedAt = now)).copy(
                    name = name.trim(), primaryMuscle = muscle, secondaryMuscles = secondary.joinToString(","), equipment = equipment,
                    measurementType = mtype, instructions = instructions.trim(), personalNotes = notes.trim(),
                )
                container.write {
                    if (base == null) container.exerciseRepo.createCustom(e)
                    else container.exerciseRepo.update(e).onFailure { toaster.show(it.message ?: "Couldn't save") }
                }
                toaster.show("Saved ${e.name}"); nav.pop()
            }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, style = FitType.label, color = LocalFitTheme.current.textDim)

