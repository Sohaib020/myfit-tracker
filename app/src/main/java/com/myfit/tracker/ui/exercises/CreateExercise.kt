package com.myfit.tracker.ui.exercises

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.MuscleGroup
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Create / edit exercise, laid out like the big gym apps: a picture of the equipment at the top, then one tappable
 * row per property (Equipment, Primary muscle, Other muscles, Exercise type) that opens a picker sheet. The equipment
 * picker shows a 3D render of each piece of kit.
 */

private object EquipArt {
    private val cache = HashMap<String, ImageBitmap?>()
    suspend fun load(c: android.content.Context, path: String): ImageBitmap? = cache[path] ?: withContext(Dispatchers.IO) {
        runCatching { c.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()
    }.also { synchronized(cache) { if (cache.size > 40) cache.clear(); cache[path] = it } }
}

@Composable
fun EquipmentImage(equipment: String, modifier: Modifier, iconSize: Dp = 28.dp) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val path = equipmentArt(equipment)
    val img by produceState<ImageBitmap?>(null, path) { value = EquipArt.load(ctx, path) }
    Box(modifier.background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
        val b = img
        if (b != null) Image(b, equipmentLabel(equipment), Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
        else Icon(Duo.FitnessCenter, null, tint = th.accent, modifier = Modifier.size(iconSize))
    }
}

private enum class Pick { NONE, EQUIP, MUSCLE, OTHER, TYPE }

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
    var used by remember { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf(Pick.NONE) }
    LaunchedEffect(exerciseId) {
        if (exerciseId != null) container.exerciseRepo.get(exerciseId)?.let { e ->
            base = e; name = e.name; muscle = e.primaryMuscle; secondary = e.secondaryMuscles.split(",").filter { it.isNotBlank() }.toSet()
            equipment = e.equipment; mtype = e.measurementType; instructions = e.instructions; notes = e.personalNotes
            used = container.exerciseRepo.usageCount(e.id); loaded = true
        }
    }

    fun save() {
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
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar(if (exerciseId == null) "Create exercise" else "Edit exercise", { nav.pop() })
            if (!loaded) return@Column
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EquipmentImage(equipment, Modifier.size(132.dp).clip(CircleShape).clickableNoRipple { pick = Pick.EQUIP }, 44.dp)
                }
                NotesField(name, { name = it.take(80) }, "Exercise name")
                Glass(Modifier.fillMaxWidth()) {
                    Column {
                        PropRow("Equipment", equipmentLabel(equipment)) { pick = Pick.EQUIP }
                        Divider()
                        PropRow("Primary muscle group", muscle) { pick = Pick.MUSCLE }
                        Divider()
                        PropRow("Other muscles", if (secondary.isEmpty()) "Optional" else secondary.joinToString(", ")) { pick = Pick.OTHER }
                        Divider()
                        PropRow("Exercise type", measurementLabel(mtype), locked = used > 0) { if (used == 0) pick = Pick.TYPE else toaster.show("Locked — this exercise already has $used logged session(s)") }
                    }
                }
                if (used > 0) Caption("Exercise type is locked because changing it would reinterpret your logged history.", Modifier.padding(horizontal = 6.dp))
                NotesField(instructions, { instructions = it }, "How to do it (optional, one step per line)")
                NotesField(notes, { notes = it }, "Personal notes — seat height, grip… (optional)")
                AccentButton("Save exercise", { save() }, Modifier.fillMaxWidth(), icon = Duo.Check, height = 52.dp, enabled = name.isNotBlank())
                Spacer(Modifier.height(24.dp))
            }
        }

        GlassSheet(pick != Pick.NONE, { pick = Pick.NONE }) {
            when (pick) {
                Pick.EQUIP -> {
                    SheetTitle("Equipment")
                    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        equipmentPickerOptions.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { q ->
                                    val sel = q == equipment
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                                        .background(if (sel) th.accent.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent)
                                        .clickableNoRipple { equipment = q; pick = Pick.NONE }.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        EquipmentImage(q, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                                        Spacer(Modifier.height(6.dp))
                                        Text(if (q == "body only") "None" else equipmentLabel(q), style = FitType.caption, color = th.text, maxLines = 2,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                Pick.MUSCLE -> {
                    SheetTitle("Primary muscle group")
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        MuscleGroup.all.forEach { g -> CheckRow(g, null, g == muscle) { muscle = g; secondary = secondary - g; pick = Pick.NONE } }
                    }
                }
                Pick.OTHER -> {
                    SheetTitle("Other muscles")
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        (MuscleGroup.all - muscle - MuscleGroup.CARDIO - MuscleGroup.OTHER).forEach { g ->
                            CheckRow(g, null, g in secondary) { secondary = if (g in secondary) secondary - g else secondary + g }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AccentButton("Done", { pick = Pick.NONE }, Modifier.fillMaxWidth(), height = 48.dp)
                }
                Pick.TYPE -> {
                    SheetTitle("Exercise type")
                    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                        allMeasurementTypes.forEach { m -> CheckRow(measurementLabel(m), measurementHint(m), m == mtype) { mtype = m; pick = Pick.NONE } }
                    }
                }
                Pick.NONE -> {}
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetTitle(t: String) {
    Text(t, style = FitType.title, color = LocalFitTheme.current.text, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(LocalFitTheme.current.text.copy(alpha = 0.08f)))
}

@Composable
private fun PropRow(label: String, value: String, locked: Boolean = false, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FitType.caption, color = th.textDim)
            Text(value, style = FitType.label, color = th.text, maxLines = 2)
        }
        Icon(if (locked) Duo.Lock else Duo.KeyboardArrowRight, null, tint = th.textDim, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CheckRow(label: String, hint: String?, selected: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickableNoRipple(onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FitType.body, color = th.text)
            if (!hint.isNullOrBlank()) Caption(hint)
        }
        Spacer(Modifier.width(8.dp))
        Icon(if (selected) Duo.CheckCircle else Duo.RadioButtonUnchecked, null, tint = if (selected) th.accentBright else th.textFaint)
    }
}
