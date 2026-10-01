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
fun ExercisesScreen(container: AppContainer, bottomPad: Int) {
    val nav = LocalNav.current
    ExerciseBrowser(
        container = container,
        header = { Header(onCustom = { nav.push(Overlay.ExerciseEditor(null)) }, onArchive = { nav.push(Overlay.Archive) }) },
        bottomPad = bottomPad,
        onOpen = { nav.push(Overlay.ExerciseDetail(it.id)) },
    )
}

@Composable
private fun Header(onCustom: () -> Unit, onArchive: () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, end = 62.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Exercises", style = FitType.display, color = th.text)
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
) {
    val th = LocalFitTheme.current
    val all by container.exerciseRepo.active.collectAsState(initial = emptyList())
    val used by container.workoutRepo.usedExerciseIds.collectAsState(initial = emptySet())
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = remember(all, query, muscle, equipment) {
        all.filter { (muscle == null || it.primaryMuscle == muscle) && (equipment == null || it.equipment == equipment) && it.matches(query) }
            .sortedWith(compareByDescending<Exercise> { it.isCustom }.thenBy { it.name })
    }
    val recent = remember(all, used) { all.filter { it.id in used }.sortedBy { it.name } }
    val pickMode = onToggle != null

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = (bottomPad + if (pickMode) 80 else 0).dp),
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
            item(span = { GridItemSpan(2) }) { Caption("${filtered.size} exercises") }
            items(filtered, key = { it.id }) { ex ->
                ExerciseCard(
                    ex, logged = ex.id in used,
                    selectedIndex = selected?.indexOf(ex.id)?.takeIf { it >= 0 },
                    onClick = { if (pickMode) onToggle!!(ex) else onOpen(ex) },
                )
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
fun ExerciseCard(ex: Exercise, logged: Boolean, selectedIndex: Int?, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val mc = muscleColor(ex.primaryMuscle)
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = onClick) {
        Column {
            Box {
                ExerciseImage(ex, Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)))
                if (selectedIndex != null) {
                    Box(Modifier.matchParentSize().drawBehind { drawRect(th.accent.copy(alpha = 0.45f)) })
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).drawBehind { drawCircle(th.accent) },
                        contentAlignment = Alignment.Center,
                    ) { Text("${selectedIndex + 1}", style = FitType.label, color = th.onAccent) }
                }
                if (logged) {
                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(8.dp)).drawBehind { drawRect(Color.Black.copy(alpha = 0.55f)) }.padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("LOGGED", style = FitType.overline, color = Color.White)
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(ex.name, style = FitType.label, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(34.dp))
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
                ExerciseImage(ex, Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)))
                if (selected) Box(Modifier.matchParentSize().drawBehind { drawRect(th.accent.copy(alpha = 0.45f)) })
            }
            Text(ex.name, style = FitType.caption, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp).height(30.dp))
        }
    }
}
