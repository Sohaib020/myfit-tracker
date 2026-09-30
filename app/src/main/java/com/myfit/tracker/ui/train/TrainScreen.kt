package com.myfit.tracker.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.repo.TemplateView
import com.myfit.tracker.data.repo.WorkoutView
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrainScreen(container: AppContainer, bottomPad: Int) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val active by container.workoutRepo.inProgress.collectAsState(initial = null)
    val templates by container.workoutRepo.templates.collectAsState(initial = emptyList())
    val recent by remember { container.workoutRepo.recentViews(20) }.collectAsState(initial = emptyList())

    fun start(block: suspend () -> Long) {
        if (active != null) { toaster.show("Finish or discard your current workout first"); nav.push(Overlay.Gym(active!!.id)); return }
        scope.launch { val id = block(); nav.push(Overlay.Gym(id)) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 8.dp, end = 62.dp)) {
                Text("Train", style = FitType.display, color = th.text)
                Caption("Templates, Gym Mode and your workout history.")
            }
        }
        active?.let { w -> item { ResumeCard(container, w.id) { nav.push(Overlay.Gym(w.id)) } } }
        if (active == null) item {
            Glass(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pip(PipMood.HAPPY, size = 72.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ready when you are", style = FitType.section, color = th.text)
                        Caption("Start from a template below, or go freestyle.")
                        Spacer(Modifier.height(10.dp))
                        AccentButton("Empty workout", { start { container.workoutRepo.startEmpty() } }, icon = Icons.Rounded.PlayArrow, height = 46.dp)
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Templates", Modifier.weight(1f))
                GlassButton("New", { nav.push(Overlay.TemplateEditor(null)) }, icon = Icons.Rounded.Add, height = 40.dp)
            }
        }
        if (templates.isEmpty()) item {
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("No templates yet", style = FitType.section, color = th.text)
                    Caption("Build your own, or add a Push / Pull / Legs starter set (exercises and set targets only — no weights, those come from your own logs).")
                    Spacer(Modifier.height(12.dp))
                    GlassButton("Add Push / Pull / Legs", {
                        container.write { val n = container.workoutRepo.createStarterTemplates(); toaster.show("Added $n templates") }
                    }, icon = Icons.Rounded.AutoAwesome, height = 44.dp)
                }
            }
        }
        items(templates, key = { "t" + it.template.id }) { t ->
            TemplateCard(t,
                onStart = { start { container.workoutRepo.startFromTemplate(t.template.id) } },
                onEdit = { nav.push(Overlay.TemplateEditor(t.template.id)) },
                onDuplicate = { container.write { container.workoutRepo.duplicateTemplate(t.template.id) }; toaster.show("Duplicated") },
                onArchive = { container.write { container.workoutRepo.archiveTemplate(t.template.id) }; toaster.show("Template archived") },
            )
        }

        item { SectionTitle("History") }
        if (recent.isEmpty()) item { Caption("Finished workouts appear here.", Modifier.padding(start = 6.dp)) }
        items(recent, key = { "w" + it.workout.id }) { w ->
            HistoryRow(w, onOpen = { nav.push(Overlay.WorkoutDetail(w.workout.id)) }, onRepeat = { start { container.workoutRepo.repeatWorkout(w.workout.id) } })
        }
    }
}

@Composable
private fun ResumeCard(container: AppContainer, workoutId: Long, onResume: () -> Unit) {
    val th = LocalFitTheme.current
    val v by remember(workoutId) { container.workoutRepo.workoutView(workoutId) }.collectAsState(null)
    val now by produceState(Clock.now()) { while (true) { value = Clock.now(); delay(1000) } }
    val w = v ?: return
    Glass(Modifier.fillMaxWidth(), onClick = onResume) {
        Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.horizontalGradient(listOf(th.accent.copy(alpha = 0.55f), th.accent.copy(alpha = 0.1f)))) })
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).drawBehind { drawCircle(th.accentBright) }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.FitnessCenter, null, tint = th.onAccent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("IN PROGRESS", style = FitType.overline, color = th.text)
                Text(w.workout.name, style = FitType.title, color = th.text)
                Caption("${mmss((now - w.workout.startedAt) / 1000)} · ${w.totals.sets} sets · ${w.exercises.size} exercises", color = th.text)
            }
            AccentButton("Resume", onResume, height = 44.dp)
        }
    }
}

@Composable
private fun TemplateCard(t: TemplateView, onStart: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onArchive: () -> Unit) {
    val th = LocalFitTheme.current
    var menu by remember { mutableStateOf(false) }
    Glass(Modifier.fillMaxWidth(), onClick = onEdit) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.template.name, style = FitType.title, color = th.text)
                    Caption("${t.items.size} exercises · ${t.items.sumOf { it.first.targetSets }} sets")
                }
                Box {
                    GlassIconButton(Icons.Rounded.MoreVert, { menu = true }, size = 40.dp)
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Edit") }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                        DropdownMenuItem({ Text("Duplicate") }, { menu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
                        DropdownMenuItem({ Text("Archive") }, { menu = false; onArchive() }, leadingIcon = { Icon(Icons.Rounded.Inventory2, null) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(48.dp)) {
                    t.items.take(5).forEachIndexed { i, (_, ex) ->
                        ExerciseImage(ex, Modifier.offset(x = (i * 34).dp).size(48.dp).clip(CircleShape))
                    }
                    if (t.items.size > 5) Caption("+${t.items.size - 5}", Modifier.offset(x = (5 * 34 + 6).dp).align(Alignment.CenterStart))
                }
                AccentButton("Start", onStart, icon = Icons.Rounded.PlayArrow, height = 46.dp)
            }
            Spacer(Modifier.height(8.dp))
            Caption(t.items.joinToString(" · ") { it.second.name }, )
        }
    }
}

@Composable
fun HistoryRow(w: WorkoutView, onOpen: () -> Unit, onRepeat: () -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val t = w.totals
    val dur = w.workout.endedAt?.let { (it - w.workout.startedAt) / 1000 }
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = onOpen) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.workout.name, style = FitType.section, color = th.text)
                Caption("${w.workout.localDate} · ${dur?.let { mmss(it) } ?: "—"} · ${t.sets} sets" + (t.volumeKg?.let { " · ${Fmt.weight(it, u.weight, 0)}" } ?: ""))
                Caption(w.exercises.joinToString(", ") { it.exercise.name }, color = th.textFaint)
            }
            GlassButton("Repeat", onRepeat, height = 38.dp)
        }
    }
}
