package com.myfit.tracker.ui.train

import com.myfit.tracker.ui.theme.Duo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateContentSize
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
import com.myfit.tracker.ui.components.clickableNoRipple
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
import androidx.compose.ui.draw.clipToBounds

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TrainScreen(container: AppContainer, bottomPad: Int, embedded: Boolean = false, onBrowsePlans: () -> Unit = {}) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val active by container.workoutRepo.inProgress.collectAsState(initial = null)
    val templates by container.workoutRepo.templates.collectAsState(initial = emptyList())
    val recent by remember { container.workoutRepo.recentViews(20) }.collectAsState(initial = emptyList())
    val follow by com.myfit.tracker.ui.programs.ProgramEngine.follow.collectAsState()
    val ver by com.myfit.tracker.ui.programs.ProgramLib.version.collectAsState()
    val plan = remember(follow, ver) { com.myfit.tracker.ui.programs.ProgramLib.byId(ctx, follow?.id) }
    // program days already appear in the plan card; keep "My workout days" for the user's own
    val myDays = remember(templates, follow) { templates.filter { t -> follow?.tpl?.contains(t.template.id) != true } }

    fun start(block: suspend () -> Long) {
        if (active != null) { toaster.show("Finish or discard your current workout first"); nav.push(Overlay.Gym(active!!.id)); return }
        scope.launch { val id = block(); nav.push(Overlay.Gym(id)) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp + com.myfit.tracker.ui.components.LocalTopInset.current, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!embedded) item {
            Column(Modifier.statusBarsPadding().padding(top = com.myfit.tracker.ui.components.TopBarSpace)) {
                Text("Train", style = FitType.display, color = th.text)
            }
        }
        active?.let { w -> item(key = "resume") { ResumeCard(container, w.id) { nav.push(Overlay.Gym(w.id)) } } }
        if (active == null) item(key = "hero") {
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("What are we training?", style = FitType.title, color = th.text)
                            Caption("Tap a muscle — your workout builds itself.")
                        }
                        Pip(PipMood.HAPPY, size = 56.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_DAYS.forEach { (label, tg) ->
                            com.myfit.tracker.ui.theme.GlassChip(label, false, { nav.push(Overlay.DayBuilder(tg.joinToString(",") { it.name })) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentButton("Build my day", { nav.push(Overlay.DayBuilder()) }, Modifier.weight(1f), icon = Duo.AutoAwesome, height = 48.dp)
                        GlassButton("Empty workout", { start { container.workoutRepo.startEmpty() } }, Modifier.weight(1f), icon = Duo.PlayArrow, height = 48.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableNoRipple { nav.push(Overlay.Stopwatch) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Duo.Timer, null, tint = th.accentBright, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Walk, run, cycle or sport — start an activity timer", style = FitType.label, color = th.accentBright)
                    }
                }
            }
        }

        item(key = "plan") {
            val p = plan
            val f = follow
            if (p != null && f != null) PlanCard(container, p, f, active?.id)
            else Glass(Modifier.fillMaxWidth(), onClick = { nav.push(Overlay.PlanBuilder) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.myfit.tracker.ui.components.IconBubble(Duo.CalendarMonth, th.accent, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Make me a weekly plan", style = FitType.section, color = th.text)
                        Caption("Pick your goal and days — get a plan that progresses week by week.")
                        Spacer(Modifier.height(4.dp))
                        Text("Or browse ready-made programs", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple(onBrowsePlans).padding(vertical = 4.dp))
                    }
                    Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
                }
            }
        }

        item(key = "progress") { ProgressStrip(recent) { nav.push(Overlay.TrainProgress) } }

        item(key = "daysHead") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("My workout days", Modifier.weight(1f))
                GlassButton("New day", { nav.push(Overlay.DayBuilder()) }, icon = Duo.Add, height = 40.dp)
            }
        }
        if (myDays.isEmpty()) item(key = "daysEmpty") {
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Save the days you repeat", style = FitType.section, color = th.text)
                    Caption("Build a day like \"Chest & Biceps\" once, then start it with one tap every week.")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Build a day", { nav.push(Overlay.DayBuilder()) }, Modifier.weight(1f), icon = Duo.AutoAwesome, height = 44.dp)
                        GlassButton("Start from scratch", { nav.push(Overlay.TemplateEditor(null)) }, Modifier.weight(1f), icon = Duo.Edit, height = 44.dp)
                    }
                }
            }
        }
        items(myDays, key = { "t" + it.template.id }) { t ->
            TemplateCard(t,
                onStart = { start { container.workoutRepo.startFromTemplate(t.template.id) } },
                onEdit = { nav.push(Overlay.TemplateEditor(t.template.id)) },
                onDuplicate = { container.write { container.workoutRepo.duplicateTemplate(t.template.id) }; toaster.show("Duplicated") },
                onArchive = { val id = t.template.id; container.write { container.workoutRepo.archiveTemplate(id) }; toaster.show("Deleted \"${t.template.name}\"", "Undo") { container.write { container.workoutRepo.unarchiveTemplate(id) } } },
                onRemoveItem = { itemId -> container.write { container.workoutRepo.removeFromTemplate(t.template.id, itemId) } },
                onAddItems = { nav.push(Overlay.PickExercises(templateId = t.template.id)) },
            )
        }

        item(key = "histHead") { SectionTitle("History") }
        if (recent.isEmpty()) item(key = "histEmpty") { Caption("Finished workouts appear here.", Modifier.padding(start = 6.dp)) }
        items(recent, key = { "w" + it.workout.id }) { w ->
            HistoryRow(w, onOpen = { nav.push(Overlay.WorkoutDetail(w.workout.id)) }, onRepeat = { start { container.workoutRepo.repeatWorkout(w.workout.id) } })
        }
    }
}

@Composable
private fun PlanCard(container: AppContainer, p: com.myfit.tracker.ui.programs.Program, f: com.myfit.tracker.ui.programs.Follow, activeId: Long?) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val done by remember(f) { com.myfit.tracker.ui.programs.ProgramEngine.sessionsDone(container, f) }.collectAsState(0)
    val pos = com.myfit.tracker.ui.programs.ProgramEngine.position(p, done)
    Glass(Modifier.fillMaxWidth(), onClick = { nav.push(Overlay.ProgramDetail(p.id)) }) {
        Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.horizontalGradient(listOf(th.accent.copy(alpha = 0.4f), th.accent.copy(alpha = 0.05f)))) })
        Column(Modifier.padding(16.dp)) {
            Text("YOUR PLAN", style = FitType.overline, color = th.text)
            Text(p.name, style = FitType.title, color = th.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Caption(if (pos.finished) "Completed — all ${p.sessions} sessions" else "Week ${pos.week} of ${p.weeks} · ${p.phase(pos.week).label} · session ${done + 1} of ${p.sessions}", color = th.text)
            Spacer(Modifier.height(10.dp))
            com.myfit.tracker.ui.components.GlassProgressBar((done.toFloat() / p.sessions).coerceIn(0f, 1f), th.accentBright)
            if (!pos.finished) {
                val day = p.days[pos.day]
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Up next: ${day.name}", style = FitType.section, color = th.text)
                        Caption("${day.items.size} exercises · ~${p.mins} min")
                    }
                    AccentButton("Start", {
                        if (activeId != null) { toaster.show("Finish or discard your current workout first"); nav.push(Overlay.Gym(activeId)) }
                        else scope.launch { nav.push(Overlay.Gym(com.myfit.tracker.ui.programs.ProgramEngine.startSession(container, p, f, pos))) }
                    }, icon = Duo.PlayArrow, height = 46.dp)
                }
            }
        }
    }
}

@Composable
private fun ProgressStrip(recent: List<WorkoutView>, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val today = java.time.LocalDate.now()
    fun inRange(w: WorkoutView, from: Long, to: Long) = runCatching { java.time.LocalDate.parse(w.workout.localDate) }.getOrNull()
        ?.let { d -> val ago = java.time.temporal.ChronoUnit.DAYS.between(d, today); ago in from..to } == true
    val week = recent.filter { inRange(it, 0, 6) }
    val last = recent.filter { inRange(it, 7, 13) }
    val vol = week.sumOf { it.totals.volumeKg ?: 0.0 }
    val lastVol = last.sumOf { it.totals.volumeKg ?: 0.0 }
    Glass(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            com.myfit.tracker.ui.components.IconBubble(Duo.Insights, th.success, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Progress & PRs", style = FitType.section, color = th.text)
                Caption(
                    "This week: ${week.size} workout${if (week.size == 1) "" else "s"} · ${week.sumOf { it.totals.sets }} sets" +
                        (if (vol > 0) " · ${Fmt.weight(vol, u.weight, 0)}" else "") +
                        (if (lastVol > 0 && vol > 0) ((vol - lastVol) / lastVol * 100).toInt().let { p -> if (p >= 0) " (▲$p%)" else " (▼${-p}%)" } else ""),
                )
            }
            Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
        }
    }
}

@Composable
private fun ResumeCard(container: AppContainer, workoutId: Long, onResume: () -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val v by remember(workoutId) { container.workoutRepo.workoutView(workoutId) }.collectAsState(null)
    val now by produceState(Clock.now()) { while (true) { androidx.compose.runtime.withFrameMillis { }; value = Clock.now(); delay(1000) } }
    val w = v ?: return
    val elapsed = now - w.workout.startedAt
    // left open for hours (forgot to finish): offer to save it at the last set, or discard it
    val stale = elapsed > 4 * 3_600_000L
    Glass(Modifier.fillMaxWidth(), onClick = onResume) {
        Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.horizontalGradient(listOf(th.accent.copy(alpha = 0.55f), th.accent.copy(alpha = 0.1f)))) })
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).drawBehind { drawCircle(th.accentBright) }, contentAlignment = Alignment.Center) {
                    Icon(Duo.FitnessCenter, null, tint = th.onAccent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (stale) "STILL OPEN" else "IN PROGRESS", style = FitType.overline, color = th.text)
                    Text(w.workout.name, style = FitType.title, color = th.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Caption("${mmss(elapsed / 1000)} · ${w.totals.sets} sets · ${w.exercises.size} exercises", color = th.text)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (stale) {
                Caption("This workout has been open for ${elapsed / 3_600_000} hours. Did you forget to finish it?", color = th.text)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton("Save it", {
                        scope.launch {
                            val last = w.exercises.flatMap { it.sets }.maxOfOrNull { it.completedAt }
                            container.workoutRepo.finish(workoutId, w.workout.name, w.workout.notes)
                            // end time = the last set (not hours later), so duration and calories stay right
                            val end = (last ?: w.workout.startedAt) + 5 * 60_000L
                            runCatching { container.workoutRepo.updateWorkoutMeta(workoutId, w.workout.name, w.workout.notes, w.workout.startedAt, minOf(end, now)) }
                        }
                    }, Modifier.weight(1f), icon = Duo.Check, height = 44.dp)
                    GlassButton("Discard", { scope.launch { container.workoutRepo.discard(workoutId) } }, Modifier.weight(1f), icon = Duo.DeleteOutline, height = 44.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Keep going instead", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple(onResume).padding(vertical = 4.dp))
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton("Resume", onResume, Modifier.weight(1f), icon = Duo.PlayArrow, height = 44.dp)
                GlassButton("Add exercise", { nav.push(Overlay.PickExercises(workoutId = workoutId)) }, Modifier.weight(1f), icon = Duo.Add, height = 44.dp)
            }
        }
    }
}

@Composable
private fun TemplateCard(t: TemplateView, onStart: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onArchive: () -> Unit, onRemoveItem: (Long) -> Unit = {}, onAddItems: () -> Unit = {}) {
    val th = LocalFitTheme.current
    var menu by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    Glass(Modifier.fillMaxWidth().animateContentSize(), onClick = { open = !open }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.template.name, style = FitType.title, color = th.text)
                    Caption("${t.items.size} exercises · ${t.items.sumOf { it.first.targetSets }} sets")
                }
                Box {
                    GlassIconButton(Duo.MoreVert, { menu = true }, size = 40.dp)
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Edit") }, { menu = false; onEdit() }, leadingIcon = { Icon(Duo.Edit, null) })
                        DropdownMenuItem({ Text("Duplicate") }, { menu = false; onDuplicate() }, leadingIcon = { Icon(Duo.ContentCopy, null) })
                        DropdownMenuItem({ Text("Delete template", color = th.danger) }, { menu = false; onArchive() }, leadingIcon = { Icon(Duo.DeleteOutline, null, tint = th.danger) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).height(48.dp).clipToBounds()) {
                    // as many faces as fit in the space left of Start (each overlaps the last by 14 dp), then "+N"
                    val fit = (((maxWidth - 48.dp - 30.dp) / 34.dp).toInt() + 1).coerceIn(1, 5)
                    val shown = t.items.take(fit)
                    shown.forEachIndexed { i, (_, ex) ->
                        ExerciseImage(ex, Modifier.offset(x = (i * 34).dp).size(48.dp).clip(CircleShape))
                    }
                    if (t.items.size > shown.size) Caption("+${t.items.size - shown.size}", Modifier.offset(x = ((shown.size - 1) * 34 + 54).dp).align(Alignment.CenterStart))
                }
                Spacer(Modifier.width(8.dp))
                AccentButton("Start", onStart, icon = Duo.PlayArrow, height = 46.dp)
            }
            Spacer(Modifier.height(8.dp))
            if (!open) {
                Caption(t.items.joinToString(" · ") { it.second.name })
                Spacer(Modifier.height(6.dp))
                Text("Edit exercises", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple { open = true }.padding(vertical = 4.dp))
            }
            else {
                t.items.forEach { (item, ex) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseImage(ex, Modifier.size(36.dp).clip(CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = FitType.label, color = th.text)
                            Caption("${item.targetSets} sets" + (item.targetRepsMin?.let { mn -> " · $mn" + (item.targetRepsMax?.takeIf { it != mn }?.let { "–$it" } ?: "") + " reps" } ?: ""))
                        }
                        GlassIconButton(Duo.Close, { onRemoveItem(item.id) }, size = 34.dp, tint = th.danger)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("Add exercise", onAddItems, Modifier.weight(1f), icon = Duo.Add, height = 42.dp)
                    GlassButton("Full editor", onEdit, Modifier.weight(1f), icon = Duo.Edit, height = 42.dp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableNoRipple(onArchive).padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Duo.DeleteOutline, null, tint = th.danger, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Delete template", style = FitType.label, color = th.danger)
                }
            }
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
