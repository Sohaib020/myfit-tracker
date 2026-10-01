package com.myfit.tracker.ui.train

import com.myfit.tracker.ui.theme.Duo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.myfit.tracker.data.db.WorkoutTemplateExercise
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Units
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.OverlayScaffold
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.Stepper
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.exercises.ExerciseBrowser
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings

private data class Item(
    val key: Long,
    val exerciseId: Long,
    val sets: Int,
    val repsMin: String,
    val repsMax: String,
    val weight: String,          // display unit text, empty = no target
    val rest: Int,
    val superset: Boolean,       // linked with the NEXT item
    val notes: String,
)

@Composable
fun TemplateEditorScreen(container: AppContainer, templateId: Long?) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val u = LocalSettings.current.units
    val exercises by container.exerciseRepo.all.collectAsState(initial = emptyList())
    val exMap = remember(exercises) { exercises.associateBy { it.id } }
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<Item>() }
    var loaded by remember { mutableStateOf(templateId == null) }
    var picking by remember { mutableStateOf(false) }
    var nextKey by remember { mutableStateOf(1L) }

    LaunchedEffect(templateId) {
        if (templateId != null) {
            val t = container.workoutRepo.getTemplate(templateId)
            val its = container.workoutRepo.getTemplateItems(templateId)
            name = t?.name ?: ""; notes = t?.notes ?: ""
            items.clear()
            its.forEachIndexed { i, it ->
                items += Item(i + 1L, it.exerciseId, it.targetSets, it.targetRepsMin?.toString() ?: "", it.targetRepsMax?.toString() ?: "",
                    it.targetWeightKg?.let { w -> Fmt.trim(Units.kgTo(w, u.weight), 2) } ?: "", it.restSeconds,
                    superset = it.supersetGroup != null && its.getOrNull(i + 1)?.supersetGroup == it.supersetGroup, notes = it.notes)
            }
            nextKey = its.size + 1L
            loaded = true
        }
    }
    BackHandler(picking) { picking = false }

    if (picking) {
        val picked = remember { mutableStateListOf<Long>() }
        ExerciseBrowser(
            container = container,
            header = { OverlayTopBar("Add exercises", { picking = false }, "Tap in the order you'll do them") },
            bottomPad = 16, onOpen = {}, selected = picked,
            onToggle = { e -> if (e.id in picked) picked.remove(e.id) else picked.add(e.id) },
            onConfirm = {
                picked.forEach { id ->
                    val ex = exMap[id]
                    val timed = ex != null && ex.measurementType in listOf(MeasurementType.DURATION, MeasurementType.DISTANCE_DURATION, MeasurementType.WEIGHT_DURATION)
                    items += Item(nextKey++, id, 3, if (timed) "" else "8", if (timed) "" else "12", "", 90, false, "")
                }
                picking = false
            },
        )
        return
    }

    OverlayScaffold(if (templateId == null) "New template" else "Edit template", { nav.pop() }) {
        if (!loaded) return@OverlayScaffold
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NotesField(name, { name = it.take(60) }, "Template name, e.g. Chest + Triceps")
            NotesField(notes, { notes = it }, "Notes (optional)")
            if (items.isEmpty()) Caption("No exercises yet.")
            items.forEachIndexed { i, it ->
                val ex = exMap[it.exerciseId]
                if (ex != null) ItemCard(i, it, ex, u, isLast = i == items.lastIndex,
                    onChange = { n -> items[i] = n },
                    onMove = { d -> val j = i + d; if (j in items.indices) { val a = items[i]; items[i] = items[j]; items[j] = a } },
                    onRemove = { items.removeAt(i) })
            }
            GlassButton("Add exercises", { picking = true }, Modifier.fillMaxWidth(), icon = Duo.Add)
            AccentButton("Save template", {
                var group = 0
                val out = items.mapIndexed { i, it ->
                    val prevLinked = i > 0 && items[i - 1].superset
                    val g: Int? = when {
                        it.superset && !prevLinked -> { group++; group }
                        prevLinked -> group
                        else -> null
                    }
                    WorkoutTemplateExercise(
                        templateId = templateId ?: 0, exerciseId = it.exerciseId, position = i, targetSets = it.sets,
                        targetRepsMin = it.repsMin.toIntOrNull(), targetRepsMax = it.repsMax.toIntOrNull(),
                        targetWeightKg = it.weight.toDoubleOrNull()?.let { w -> Units.toKg(w, u.weight) },
                        restSeconds = it.rest, supersetGroup = g, notes = it.notes,
                    )
                }
                container.write { container.workoutRepo.saveTemplate(templateId, name.trim(), notes.trim(), out) }
                toaster.show("Template saved"); nav.pop()
            }, Modifier.fillMaxWidth(), enabled = name.isNotBlank() && items.isNotEmpty())
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ItemCard(
    index: Int, it: Item, ex: Exercise, u: com.myfit.tracker.domain.UnitPrefs, isLast: Boolean,
    onChange: (Item) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit,
) {
    val th = LocalFitTheme.current
    val timed = ex.measurementType in listOf(MeasurementType.DURATION, MeasurementType.DISTANCE_DURATION, MeasurementType.WEIGHT_DURATION)
    val weighted = ex.measurementType == MeasurementType.WEIGHT_REPS
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${index + 1}", style = FitType.title, color = th.accentBright, modifier = Modifier.width(26.dp))
                ExerciseImage(ex, Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(10.dp))
                Text(ex.name, style = FitType.label, color = th.text, modifier = Modifier.weight(1f), maxLines = 2)
                GlassIconButton(Duo.ArrowUpward, { onMove(-1) }, size = 34.dp)
                Spacer(Modifier.width(4.dp))
                GlassIconButton(Duo.ArrowDownward, { onMove(1) }, size = 34.dp)
                Spacer(Modifier.width(4.dp))
                GlassIconButton(Duo.Close, onRemove, size = 34.dp, tint = th.danger)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sets", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                Stepper(it.sets, { v -> onChange(it.copy(sets = v)) }, 1..12)
            }
            if (!timed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Reps", style = FitType.body, color = th.text, modifier = Modifier.width(56.dp))
                NumberInput(it.repsMin, { v -> onChange(it.copy(repsMin = v)) }, "min", Modifier.weight(1f), decimal = false, big = false)
                NumberInput(it.repsMax, { v -> onChange(it.copy(repsMax = v)) }, "max", Modifier.weight(1f), decimal = false, big = false)
            }
            if (weighted) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Target weight", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                NumberInput(it.weight, { v -> onChange(it.copy(weight = v)) }, u.weight.label, Modifier.width(150.dp), big = false)
            }
            if (weighted) Caption("Optional. Leave empty to pre-fill from your last session instead.")
            Text("Rest", style = FitType.label, color = th.textDim)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(30, 45, 60, 90, 120, 150, 180, 240)) { r -> GlassChip(if (r < 60) "${r}s" else "${r / 60}:${"%02d".format(r % 60)}", it.rest == r, { onChange(it.copy(rest = r)) }) }
            }
            if (!isLast) GlassChip(if (it.superset) "Superset with next ✓" else "Superset with next", it.superset, { onChange(it.copy(superset = !it.superset)) }, icon = Duo.Link)
        }
    }
}
