package com.myfit.tracker.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.SetType
import com.myfit.tracker.data.repo.WorkoutExerciseView
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.DateTimeRow
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayScaffold
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.exercises.formatSet
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.ui.exercises.setTypeShort
import com.myfit.tracker.ui.gym.Draft
import com.myfit.tracker.ui.gym.RpeRow
import com.myfit.tracker.ui.gym.SetInputs
import com.myfit.tracker.ui.gym.SetTypeRow
import com.myfit.tracker.ui.gym.SummaryGrid
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun WorkoutDetailScreen(container: AppContainer, workoutId: Long) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val v by remember(workoutId) { container.workoutRepo.workoutView(workoutId) }.collectAsState(null)
    val w = v ?: return
    var name by remember(w.workout.id) { mutableStateOf(w.workout.name) }
    var notes by remember(w.workout.id) { mutableStateOf(w.workout.notes) }
    var start by remember(w.workout.id) { mutableStateOf(w.workout.startedAt) }
    var end by remember(w.workout.id) { mutableStateOf(w.workout.endedAt ?: w.workout.startedAt) }
    var edit by remember { mutableStateOf<Pair<SetRow, WorkoutExerciseView>?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val metaChanged = name != w.workout.name || notes != w.workout.notes || start != w.workout.startedAt || end != (w.workout.endedAt ?: w.workout.startedAt)

    Box(Modifier.fillMaxSize()) {
        OverlayScaffold(w.workout.name, { nav.pop() }, w.workout.localDate) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryGrid(w.totals, ((w.workout.endedAt ?: Clock.now()) - w.workout.startedAt) / 1000, s.units)
                w.exercises.forEach { e ->
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseImage(e.exercise, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.exercise.name, style = FitType.section, color = th.text)
                                val vol = e.sets.filter { it.setType != SetType.WARMUP }.mapNotNull { r -> r.weightKg?.let { wk -> r.reps?.let { wk * it } } }
                                if (e.exercise.measurementType == "WEIGHT_REPS" && vol.isNotEmpty()) Caption("Volume ${Fmt.weight(vol.sum(), s.units.weight, 0)}")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        e.sets.forEach { r ->
                            Glass(Modifier.fillMaxWidth().padding(vertical = 3.dp).height(46.dp), shape = RoundedCornerShape(16.dp), onClick = { edit = r to e }) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(24.dp).clip(CircleShape).drawBehind { drawCircle(if (r.setType == SetType.WARMUP) th.textFaint else th.success) }, contentAlignment = Alignment.Center) {
                                        Text(setTypeShort(r.setType).ifEmpty { "${r.setNumber}" }, style = FitType.caption, color = Color.White)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(formatSet(e.exercise.measurementType, r, s.units), style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                                    r.restSec?.let { Caption("rest ${mmss(it)}") }
                                }
                            }
                        }
                    }
                }
                GlassCard {
                    Text("Details", style = FitType.section, color = th.text)
                    Spacer(Modifier.height(8.dp))
                    NotesField(name, { name = it.take(60) }, "Workout name")
                    Spacer(Modifier.height(8.dp))
                    NotesField(notes, { notes = it }, "Notes")
                    Spacer(Modifier.height(8.dp))
                    DateTimeRow("Started", start, { start = it })
                    Spacer(Modifier.height(6.dp))
                    DateTimeRow("Finished", end, { end = it })
                    if (end <= start) Caption("Finish must be after start.", color = th.danger)
                    if (metaChanged) {
                        Spacer(Modifier.height(10.dp))
                        AccentButton("Save changes", {
                            container.write { container.workoutRepo.updateWorkoutMeta(workoutId, name.trim().ifEmpty { w.workout.name }, notes.trim(), start, end) }
                            toaster.show("Workout updated")
                        }, Modifier.fillMaxWidth(), enabled = end > start, icon = Icons.Rounded.Save)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Repeat", {
                        scope.launch {
                            val active = container.workoutRepo.inProgress.first()
                            if (active != null) { toaster.show("Finish or discard your current workout first"); nav.replace(Overlay.Gym(active.id)) }
                            else nav.replace(Overlay.Gym(container.workoutRepo.repeatWorkout(workoutId)))
                        }
                    }, Modifier.weight(1f), icon = Icons.Rounded.Replay)
                    GlassButton("Save as template", {
                        container.write { container.workoutRepo.templateFromWorkout(workoutId, w.workout.name) }; toaster.show("Template created")
                    }, Modifier.weight(1f))
                }
                GlassButton("Delete workout", { confirmDelete = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteOutline)
                Spacer(Modifier.height(20.dp))
            }
        }

        GlassSheet(visible = edit != null, onDismiss = { edit = null }) {
            val es = edit
            if (es != null) {
                val (row, e) = es
                var d by remember(row.setId) { mutableStateOf(Draft(row.setType, row.weightKg, row.reps, row.durationSec, row.distanceM, row.rpe)) }
                var confirm by remember { mutableStateOf(false) }
                Text("Correct set ${row.setNumber}", style = FitType.title, color = th.text, modifier = Modifier.padding(vertical = 8.dp))
                Caption("${e.exercise.name} · recorded as ${formatSet(e.exercise.measurementType, row, s.units)}")
                Spacer(Modifier.height(12.dp)); SetTypeRow(d.setType) { d = d.copy(setType = it) }
                Spacer(Modifier.height(12.dp)); SetInputs(e.exercise.measurementType, d, { d = it }, s.units, s.weightStepKg)
                Spacer(Modifier.height(10.dp)); RpeRow(d.rpe) { d = d.copy(rpe = it) }
                Spacer(Modifier.height(16.dp))
                AccentButton("Save correction", {
                    container.write { container.workoutRepo.updateSet(row.setId, d.setType, d.weightKg, d.reps, d.durationSec, d.distanceM, d.rpe, "") }
                    toaster.show("Set corrected — totals recalculated"); edit = null
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                GlassButton("Delete set", { confirm = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.DeleteOutline)
                if (confirm) AlertDialog(
                    onDismissRequest = { confirm = false },
                    title = { Text("Delete set ${row.setNumber}?") },
                    text = { Text("Remaining sets are renumbered and all totals recalculate.") },
                    confirmButton = { TextButton({ container.write { container.workoutRepo.deleteSet(row.setId) }; confirm = false; edit = null }) { Text("Delete", color = th.danger) } },
                    dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
                )
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this workout?") },
        text = { Text("It will be removed from history, totals and records.") },
        confirmButton = { TextButton({ container.write { container.workoutRepo.deleteWorkout(workoutId) }; confirmDelete = false; toaster.show("Workout deleted"); nav.pop() }) { Text("Delete", color = th.danger) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}
