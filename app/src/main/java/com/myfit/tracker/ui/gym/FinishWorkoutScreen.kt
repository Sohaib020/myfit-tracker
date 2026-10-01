package com.myfit.tracker.ui.gym

import com.myfit.tracker.ui.theme.Duo

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.UnitPrefs
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayScaffold
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.exercises.formatSet
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings

@Composable
fun FinishWorkoutScreen(container: AppContainer, workoutId: Long) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val u = LocalSettings.current.units
    val v by remember(workoutId) { container.workoutRepo.workoutView(workoutId) }.collectAsState(null)
    val w = v ?: return
    var name by remember { mutableStateOf(w.workout.name) }
    var notes by remember { mutableStateOf(w.workout.notes) }
    var asTemplate by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf(w.workout.name) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val t = w.totals
    val durSec = (Clock.now() - w.workout.startedAt) / 1000

    OverlayScaffold("Finish workout", { nav.replace(Overlay.Gym(workoutId)) }, "Back returns to Gym Mode") {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pip(if (t.sets > 0) PipMood.PROUD else PipMood.CONCERNED, size = 90.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (t.sets > 0) "Great session!" else "No sets logged", style = FitType.title, color = th.text)
                    Caption(if (t.sets > 0) "Here's exactly what you recorded." else "There's nothing to save yet — keep training or discard.")
                }
            }
            SummaryGrid(t, durSec, u)
            val setKey = w.exercises.sumOf { it.sets.size }
            val prs by androidx.compose.runtime.produceState(emptyList<Pair<String, com.myfit.tracker.domain.Records.Pr>>(), setKey) {
                value = w.exercises.filter { it.sets.isNotEmpty() }.flatMap { e ->
                    val past = container.workoutRepo.exerciseHistoryExcluding(e.exercise.id, workoutId)
                    com.myfit.tracker.domain.Records.events(e.exercise.id, e.exercise.measurementType, past + e.sets)
                        .filter { it.workoutId == workoutId }.map { e.exercise.name to it }
                }
            }
            com.myfit.tracker.ui.exercises.NewRecordsCard(prs)
            GlassCard {
                Text("What you did", style = FitType.section, color = th.text)
                Spacer(Modifier.height(8.dp))
                w.exercises.filter { it.sets.isNotEmpty() }.forEach { e ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseImage(e.exercise, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.exercise.name, style = FitType.label, color = th.text)
                            Caption(e.sets.joinToString("  ·  ") { formatSet(e.exercise.measurementType, it, u) })
                        }
                    }
                }
                val skipped = w.exercises.count { it.sets.isEmpty() }
                if (skipped > 0) Caption("$skipped planned exercise${if (skipped == 1) "" else "s"} with no sets won't be saved.")
            }
            NotesField(name, { name = it.take(60) }, "Workout name")
            NotesField(notes, { notes = it }, "How did it feel? (optional)")
            GlassCard {
                ToggleRow("Save as a template", "Reuse this exercise order and set counts next time.", asTemplate) { asTemplate = it }
                if (asTemplate) { Spacer(Modifier.height(6.dp)); NotesField(templateName, { templateName = it.take(60) }, "Template name") }
            }
            AccentButton("Save workout", {
                container.restTimer.stop()?.let { (id, sec) -> container.write { container.workoutRepo.recordRest(id, sec) } }
                val tName = templateName.trim()
                container.write {
                    container.workoutRepo.finish(workoutId, name.trim(), notes.trim())
                    if (asTemplate && tName.isNotEmpty()) container.workoutRepo.templateFromWorkout(workoutId, tName)
                }
                toaster.show("Saved · ${t.sets} sets" + (t.volumeKg?.let { " · ${Fmt.weight(it, u.weight, 0)}" } ?: ""))
                nav.clear()
            }, Modifier.fillMaxWidth(), icon = Duo.Check, enabled = t.sets > 0)
            GlassButton("Discard workout", { confirmDiscard = true }, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline)
            Spacer(Modifier.height(20.dp))
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this workout?") },
        text = { Text("Its ${t.sets} logged set(s) won't count in any history, totals or records.") },
        confirmButton = { TextButton({
            container.restTimer.stop(); container.write { container.workoutRepo.discard(workoutId) }
            confirmDiscard = false; toaster.show("Workout discarded"); nav.clear()
        }) { Text("Discard", color = th.danger) } },
        dismissButton = { TextButton({ confirmDiscard = false }) { Text("Keep") } },
    )
}

@Composable
fun SummaryGrid(t: com.myfit.tracker.domain.WorkoutCalc.Totals, durSec: Long, u: UnitPrefs) {
    val th = LocalFitTheme.current
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SUMMARY", style = FitType.overline, color = th.textDim, modifier = Modifier.weight(1f))
            DataBadge(DataKind.CALCULATED)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Cell("Duration", mmss(durSec), Modifier.weight(1f))
            Cell("Exercises", "${t.exercises}", Modifier.weight(1f))
            Cell("Sets", "${t.sets}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Cell("Reps", Fmt.int(t.reps), Modifier.weight(1f))
            Cell("Volume", t.volumeKg?.let { Fmt.weight(it, u.weight, 0) } ?: "—", Modifier.weight(1f))
            Cell("Avg rest", t.avgRestSec?.let { mmss(it.toLong()) } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Caption("Volume = weight × reps of non-warm-up weighted sets. Rest = time actually spent on the rest timer.")
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Text(label, style = FitType.caption, color = th.textDim)
        Text(value, style = FitType.title, color = th.text)
    }
}

