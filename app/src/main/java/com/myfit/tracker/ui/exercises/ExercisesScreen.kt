package com.myfit.tracker.ui.exercises

import com.myfit.tracker.ui.theme.Duo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.clickableNoRipple
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.MuscleGroup
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSearchField
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme

/** Exercises tab. */
@Composable
fun ExercisesScreen(container: AppContainer, bottomPad: Int, embedded: Boolean = false) {
    val nav = LocalNav.current
    val toaster = com.myfit.tracker.ui.components.LocalToaster.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val active by container.workoutRepo.inProgress.collectAsState(initial = null)
    val templates by container.workoutRepo.templates.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf<Exercise?>(null) }
    ExerciseBrowser(
        container = container,
        header = { Header(embedded, onCustom = { nav.push(Overlay.ExerciseEditor(null)) }, onArchive = { nav.push(Overlay.Archive) }) },
        bottomPad = bottomPad,
        onOpen = { nav.push(Overlay.ExerciseDetail(it.id)) },
        onAdd = { ex ->
            adding = ex
        },
    )
    val ex = adding
    com.myfit.tracker.ui.components.GlassSheet(visible = ex != null, onDismiss = { adding = null }) {
        if (ex != null) {
            val th = LocalFitTheme.current
            Text("Add ${ex.name}", style = FitType.title, color = th.text)
            val w = active
            Caption(if (w != null) "Add it to your running workout, a template, or hide it." else "No workout is running. Start one with it, add it to a template, or hide it.")
            Spacer(Modifier.height(12.dp))
            if (w != null) com.myfit.tracker.ui.theme.AccentButton("Add to ${w.name}", {
                adding = null
                scope.launch { container.workoutRepo.addExercise(w.id, ex.id); toaster.show("Added to ${w.name}", "Open") { nav.push(Overlay.Gym(w.id)) } }
            }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 50.dp)
            else com.myfit.tracker.ui.theme.AccentButton("Start a workout with it", {
                adding = null
                scope.launch {
                    val id = container.workoutRepo.startEmpty()
                    container.workoutRepo.addExercise(id, ex.id)
                    nav.push(Overlay.Gym(id))
                }
            }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 50.dp)
            Spacer(Modifier.height(8.dp))
            GlassButton("Hide from library", {
                adding = null
                scope.launch { container.exerciseRepo.archive(ex.id); toaster.show("${ex.name} hidden", "Undo") { scope.launch { container.exerciseRepo.unarchive(ex.id) } } }
            }, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline, height = 44.dp)
            if (templates.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("ADD TO A TEMPLATE", style = FitType.overline, color = th.textDim)
                Spacer(Modifier.height(6.dp))
                templates.forEach { t ->
                    GlassButton(t.template.name, {
                        adding = null
                        scope.launch { container.workoutRepo.addToTemplate(t.template.id, listOf(ex.id)); toaster.show("Added to ${t.template.name}") }
                    }, Modifier.fillMaxWidth().padding(vertical = 3.dp), icon = Duo.Add, height = 44.dp)
                }
            }
        }
    }
}

@Composable
private fun Header(embedded: Boolean, onCustom: () -> Unit, onArchive: () -> Unit) {
    val th = LocalFitTheme.current
    Row(if (embedded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().statusBarsPadding().padding(top = com.myfit.tracker.ui.components.TopBarSpace), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (!embedded) Text("Exercises", style = FitType.display, color = th.text)
            Caption("876 exercises with photos · public-domain library + your own")
        }
        com.myfit.tracker.ui.theme.GlassIconButton(Duo.Inventory2, onArchive)
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        GlassButton("Custom", onCustom, icon = Duo.Add, height = 44.dp)
    }
}

/**
 * Searchable, filterable grid. In pick mode (`selected` + `onToggle` given) cards toggle selection
 * and a confirm bar appears.
 */
@Composable
fun ExerciseBrowser(
    container: AppContainer,
    header: @Composable () -> Unit,
    bottomPad: Int,
    onOpen: (Exercise) -> Unit,
    selected: List<Long>? = null,
    onToggle: ((Exercise) -> Unit)? = null,
    onConfirm: (() -> Unit)? = null,
    onAdd: ((Exercise) -> Unit)? = null,
) {
    val th = LocalFitTheme.current
    val all by container.exerciseRepo.active.collectAsState(initial = emptyList())
    val used by container.workoutRepo.usedExerciseIds.collectAsState(initial = emptySet())
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    val ctxH = androidx.compose.ui.platform.LocalContext.current
    remember { com.myfit.tracker.domain.HealthProfile.load(ctxH); 0 }
    val showUnsuitable by com.myfit.tracker.domain.HealthProfile.showUnsuitable.collectAsState()
    val healthSel by com.myfit.tracker.domain.HealthProfile.selected.collectAsState()
    val allMatches = remember(all, query, muscle, equipment) {
        all.filter { (muscle == null || it.primaryMuscle == muscle) && (equipment == null || it.equipment == equipment) && it.matches(query) }
            .sortedWith(compareByDescending<Exercise> { it.isCustom }.thenBy { it.name })
    }
    // exercises that don't suit your health profile are hidden unless you choose to see them (with a warning)
    val filtered = remember(allMatches, showUnsuitable, healthSel) {
        if (showUnsuitable) allMatches else allMatches.filter { it.judge().verdict != com.myfit.tracker.domain.HealthProfile.Verdict.AVOID }
    }
    val hiddenCount = allMatches.size - filtered.size
    val recent = remember(all, used) { all.filter { it.id in used }.sortedBy { it.name } }
    val pickMode = onToggle != null
    var preview by remember { mutableStateOf<Exercise?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp + com.myfit.tracker.ui.components.LocalTopInset.current, bottom = (bottomPad + if (pickMode) 80 else 0).dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) { header() }
            item(span = { GridItemSpan(2) }) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    GlassSearchField(query, { query = it }, "Search — e.g. \"bench db\", \"lat pull\"")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { GlassChip("All", muscle == null, { muscle = null }) }
                        items(MuscleGroup.all) { g ->
                            GlassChip(g, muscle == g, { muscle = if (muscle == g) null else g })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { GlassChip("Any equipment", equipment == null, { equipment = null }) }
                        items(equipmentOptions) { e -> GlassChip(equipmentLabel(e), equipment == e, { equipment = if (equipment == e) null else e }) }
                    }
                }
            }
            if (query.isBlank() && muscle == null && equipment == null && recent.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Duo.History, null, tint = th.textDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("YOU'VE LOGGED THESE", style = FitType.overline, color = th.textDim)
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recent, key = { it.id }) { ex ->
                                MiniExerciseCard(ex, selected?.contains(ex.id) == true) { if (pickMode) onToggle!!(ex) else onOpen(ex) }
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Caption("${filtered.size} exercises", Modifier.weight(1f))
                    if (hiddenCount > 0 || (showUnsuitable && com.myfit.tracker.domain.HealthProfile.active.isNotEmpty()))
                        Text(if (showUnsuitable) "Hide ones that don't suit me" else "$hiddenCount hidden for your health · Show", style = FitType.caption, color = th.accentBright,
                            modifier = Modifier.clickableNoRipple { com.myfit.tracker.domain.HealthProfile.setShowUnsuitable(ctxH, !showUnsuitable) }.padding(4.dp))
                }
            }
            items(filtered, key = { it.id }) { ex ->
                ExerciseCard(
                    ex, logged = ex.id in used,
                    selectedIndex = selected?.indexOf(ex.id)?.takeIf { it >= 0 },
                    onClick = { if (pickMode) onToggle!!(ex) else onOpen(ex) },
                    onAdd = if (pickMode) null else onAdd?.let { f -> { f(ex) } },
                    onPreview = if (pickMode) ({ preview = ex }) else null,
                )
            }
        }
        // "How to": big looping demo + muscles + steps, without leaving the picker
        com.myfit.tracker.ui.components.GlassSheet(visible = preview != null, onDismiss = { preview = null }) {
            val ex = preview
            if (ex != null) {
                ExerciseImage(ex, Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(24.dp)), animate = true, periodMs = 900)
                Spacer(Modifier.height(12.dp))
                Text(ex.name, style = FitType.title, color = th.text)
                Caption(listOfNotNull(ex.primaryMuscle, ex.secondaryMuscles.takeIf { it.isNotBlank() }?.let { "also $it" }, equipmentLabel(ex.equipment).takeIf { ex.equipment.isNotBlank() }).joinToString(" · "))
                if (ex.instructions.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    ex.instructions.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.take(8).forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text("${i + 1}.", style = FitType.label, color = th.accentBright, modifier = Modifier.width(22.dp))
                            Text(step, style = FitType.body, color = th.text)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                val sel = selected?.contains(ex.id) == true
                AccentButton(if (sel) "Selected ✓ — tap to remove" else "Select this exercise", { onToggle?.invoke(ex); preview = null }, Modifier.fillMaxWidth(), icon = if (sel) Duo.Close else Duo.Check)
            }
        }
        if (pickMode && onConfirm != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = bottomPad.dp)) {
                AccentButton(
                    if (selected.isNullOrEmpty()) "Select exercises" else "Add ${selected.size} exercise${if (selected.size == 1) "" else "s"}",
                    onConfirm, Modifier.fillMaxWidth(), icon = Duo.Check, enabled = !selected.isNullOrEmpty(),
                )
            }
        }
    }
}

@Composable
fun ExerciseCard(ex: Exercise, logged: Boolean, selectedIndex: Int?, onClick: () -> Unit, onAdd: (() -> Unit)? = null, onPreview: (() -> Unit)? = null) {
    val th = LocalFitTheme.current
    val mc = muscleColor(ex.primaryMuscle)
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column {
            Box {
                // thumbnails play the movement (start ↔ end frame); cards are slightly out of step so the grid feels alive
                ExerciseImage(ex, Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    animate = true, periodMs = 950L + (ex.id % 5) * 110L)
                if (selectedIndex != null) {
                    Box(Modifier.matchParentSize().drawBehind { drawRect(th.accent.copy(alpha = 0.45f)) })
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).drawBehind { drawCircle(th.accent) },
                        contentAlignment = Alignment.Center,
                    ) { Text("${selectedIndex + 1}", style = FitType.label, color = th.onAccent) }
                }
                if (onAdd != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape)
                            .drawBehind { drawCircle(Brush.verticalGradient(listOf(th.accentBright, th.accent))) }
                            .clickableNoRipple(onAdd),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Duo.Add, "Add to workout", tint = th.onAccent, modifier = Modifier.size(20.dp)) }
                }
                if (onPreview != null) {
                    Row(
                        Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(12.dp))
                            .drawBehind { drawRect(Color.Black.copy(alpha = 0.6f)) }.clickableNoRipple(onPreview).padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Duo.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("How to", style = FitType.overline, color = Color.White)
                    }
                }
                if (logged) {
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).drawBehind { drawRect(Color.Black.copy(alpha = 0.55f)) }.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("LOGGED", style = FitType.overline, color = Color.White)
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(ex.name, style = FitType.label, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(34.dp))
                val hj = ex.judge()
                if (!hj.ok) com.myfit.tracker.ui.food.HealthBadge(hj)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).drawBehind { drawCircle(mc) })
                    Spacer(Modifier.width(5.dp))
                    Text(ex.primaryMuscle, style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
                    Icon(equipmentIcon(ex), null, tint = th.textDim, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniExerciseCard(ex: Exercise, selected: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.width(120.dp), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Column {
            Box {
                ExerciseImage(ex, Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)), animate = true, periodMs = 1000L + (ex.id % 4) * 120L)
                if (selected) Box(Modifier.matchParentSize().drawBehind { drawRect(th.accent.copy(alpha = 0.45f)) })
            }
            Text(ex.name, style = FitType.caption, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp).height(30.dp))
        }
    }
}


/** Does this exercise suit the user's health profile (knee pain, pregnancy, back pain…)? */
fun Exercise.judge(): com.myfit.tracker.domain.HealthProfile.Judgement =
    com.myfit.tracker.domain.HealthProfile.judgeExercise(com.myfit.tracker.domain.ExerciseTags.of(name, primaryMuscle, equipment, category, level, mechanic))
