package com.myfit.tracker.ui.gym

import com.myfit.tracker.ui.theme.Duo

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.SetType
import com.myfit.tracker.data.repo.WorkoutExerciseView
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.gym.Alerts
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.exercises.ExerciseBrowser
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.exercises.formatSet
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.ui.exercises.muscleColor
import com.myfit.tracker.ui.exercises.setTypeShort
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.delay

@Composable
fun GymModeScreen(container: AppContainer, workoutId: Long) {
    val th = LocalFitTheme.current
    val s = LocalSettings.current
    val u = s.units
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val ctx = LocalContext.current
    val vm: GymViewModel = viewModel(key = "gym-$workoutId", factory = GymViewModel.Factory(container, workoutId))
    val view by vm.view.collectAsStateWithLifecycle()
    val rest = container.restTimer.state
    var showPicker by remember { mutableStateOf(false) }
    var editSet by remember { mutableStateOf<Pair<SetRow, WorkoutExerciseView>?>(null) }
    var celebrate by remember { mutableStateOf<String?>(null) }
    var coach by remember { mutableStateOf(false) }

    // keep the screen awake while training
    val hostView = LocalView.current
    DisposableEffect(s.keepScreenOn) {
        hostView.keepScreenOn = s.keepScreenOn
        onDispose { hostView.keepScreenOn = false }
    }
    // Android 13+: ask once for notification permission so rest alerts work in the background
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

    val now by produceState(System.currentTimeMillis()) { while (true) { androidx.compose.runtime.withFrameMillis { }; value = System.currentTimeMillis(); delay(500) } }
    BackHandler { if (showPicker) showPicker = false else nav.pop() }

    val v = view ?: return
    val list = v.exercises
    val cur = list.firstOrNull { it.we.id == vm.currentWeId } ?: list.firstOrNull()
    LaunchedEffect(list.map { it.we.id }) {
        if (vm.currentWeId == null || list.none { it.we.id == vm.currentWeId }) list.firstOrNull()?.let { vm.select(it.we.id) }
        list.forEach { vm.ensureLoaded(it) }
    }

    // rest-over alert while the app is open (fires once per timer)
    val restEnds = rest?.endsAt
    LaunchedEffect(restEnds) {
        if (restEnds != null) {
            val wait = restEnds - System.currentTimeMillis()
            if (wait > 0) { delay(wait); if (container.restTimer.state?.endsAt == restEnds) Alerts.restOver(ctx, s.restSound, s.restVibrate) }
        }
    }

    if (showPicker) {
        val picked = remember { mutableStateListOf<Long>() }
        Column(Modifier.fillMaxSize()) {
            ExerciseBrowser(
                container = container,
                header = { OverlayTopBar("Add exercises", { showPicker = false }, "Tap to select, in the order you'll do them") },
                bottomPad = 16,
                onOpen = {},
                selected = picked,
                onToggle = { e -> if (e.id in picked) picked.remove(e.id) else picked.add(e.id) },
                onConfirm = { vm.addExercises(picked.toList()); showPicker = false },
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---------------- header
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Duo.KeyboardArrowDown, { nav.pop() })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.workout.name, style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val t = v.totals
                    val secs = (now - v.workout.startedAt) / 1000
                    val bodyKg by remember { container.logRepo.latestWeight() }.collectAsState(null)
                    val burn = com.myfit.tracker.domain.Burn.kcal(com.myfit.tracker.domain.Burn.GYM_MET, bodyKg?.weightKg ?: 70.0, secs)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mmss(secs), style = FitType.label, color = th.accentBright)
                        Text("  ·  ~${burn.toInt()} ${com.myfit.tracker.domain.EnergyUnit.label} · ${t.sets} sets" + (t.volumeKg?.let { " · ${Fmt.weight(it, u.weight, 0)}" } ?: ""), style = FitType.caption, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                AccentButton("Finish", { nav.replace(Overlay.FinishWorkout(workoutId)) }, height = 44.dp, icon = Duo.Check)
            }
            // ---------------- exercise strip
            ExerciseStrip(list, cur?.we?.id, vm, { showPicker = true })

            if (cur == null) {
                EmptyWorkout { showPicker = true }
                return@Column
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ExerciseHeaderCard(cur, list, vm, container, now, onRemoved = { ok -> if (ok) toaster.show("Exercise removed") })
                PreviousCard(cur, vm, u)
                TodaySets(cur, u, vm.history[cur.exercise.id]) { row -> editSet = row to cur }
                CoachEntry { coach = true }
                CurrentSetCard(cur, list, vm, container, now) { res ->
                    res.error?.let { toaster.show(it) }
                    res.beatBest?.let { celebrate = it }
                }
                Spacer(Modifier.height(if (rest != null) 190.dp else 40.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        // ---------------- rest timer
        AnimatedVisibility(
            rest != null, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.75f, 380f)) { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            val r = container.restTimer.state
            if (r != null) RestPanel(r.startedAt, r.endsAt, now, r.label,
                onAdd = { container.restTimer.add(15, s.restSound, s.restVibrate) },
                onSkip = { vm.skipRest() })
        }

        // ---------------- celebration (live comparison with your recorded history)
        AnimatedVisibility(celebrate != null, modifier = Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            LaunchedEffect(celebrate) { delay(2600); celebrate = null }
            Glass(Modifier.padding(32.dp), shape = RoundedCornerShape(32.dp), onClick = { celebrate = null }) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Pip(PipMood.PROUD, size = 120.dp)
                    Icon(Duo.EmojiEvents, null, tint = th.warning, modifier = Modifier.size(28.dp))
                    Text("New best!", style = FitType.title, color = th.text)
                    Caption(celebrate ?: "")
                }
            }
        }

        // ---------------- edit set
        GlassSheet(visible = editSet != null, onDismiss = { editSet = null }) {
            val es = editSet
            if (es != null) EditSetContent(es.first, es.second, vm, u, s.weightStepKg) { editSet = null }
        }

        // ---------------- pro trainer
        if (coach && cur != null) com.myfit.tracker.ui.coach.CoachSetOverlay(cur, list, vm, container) { coach = false }
    }
}

/** Opens the pro trainer for this exercise. */
@Composable
private fun CoachEntry(onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    remember { com.myfit.tracker.domain.Coach.load(ctx); 0 }
    val cp by com.myfit.tracker.domain.Coach.prefs.collectAsState()
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), onClick = onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            com.myfit.tracker.ui.coach.CoachPortrait(cp.look.id, false, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Train this with ${cp.name}", style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Caption(if (cp.camera) "Camera form-check · voice counting" else "Voice, tempo and form cues")
            }
            Icon(Duo.KeyboardArrowRight, null, tint = th.accentBright)
        }
    }
}

@Composable
private fun EmptyWorkout(onAdd: () -> Unit) {
    val th = LocalFitTheme.current
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(PipMood.WAVE, size = 130.dp)
        Spacer(Modifier.height(12.dp))
        Text("Empty workout", style = FitType.title, color = th.text)
        Caption("Add the first exercise to start logging sets.")
        Spacer(Modifier.height(20.dp))
        AccentButton("Add exercises", onAdd, icon = Duo.Add)
    }
}

@Composable
private fun ExerciseStrip(list: List<WorkoutExerciseView>, current: Long?, vm: GymViewModel, onAdd: () -> Unit) {
    val th = LocalFitTheme.current
    val state = rememberLazyListState()
    val idx = list.indexOfFirst { it.we.id == current }
    LaunchedEffect(idx) { if (idx >= 0) state.animateScrollToItem(idx) }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        itemsIndexed(list, key = { _, e -> e.we.id }) { i, e ->
            val sel = e.we.id == current
            val target = vm.targets[e.exercise.id]?.targetSets
            Glass(Modifier.width(132.dp).height(64.dp), shape = RoundedCornerShape(22.dp), onClick = { vm.select(e.we.id) }, pressScale = 0.92f) {
                if (sel) Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.verticalGradient(listOf(th.accentBright.copy(alpha = 0.9f), th.accent))) })
                Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExerciseImage(e.exercise, Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(e.exercise.name, style = FitType.caption, color = if (sel) th.onAccent else th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val ss = e.we.supersetGroup?.let { g -> "${'A' + (g - 1) % 26}${list.filter { it.we.supersetGroup == g }.indexOf(e) + 1} · " } ?: ""
                        Text(ss + "${e.sets.size}${target?.let { "/$it" } ?: ""} sets", style = FitType.overline, color = if (sel) th.onAccent else th.textDim)
                    }
                }
            }
        }
        item {
            Glass(Modifier.size(64.dp), shape = RoundedCornerShape(22.dp), onClick = onAdd, pressScale = 0.9f) {
                Icon(Duo.Add, "Add exercise", tint = th.text, modifier = Modifier.align(Alignment.Center).size(28.dp))
            }
        }
    }
}

@Composable
private fun ExerciseHeaderCard(cur: WorkoutExerciseView, list: List<WorkoutExerciseView>, vm: GymViewModel, container: AppContainer, now: Long, onRemoved: (Boolean) -> Unit) {
    var confirmRemove by remember { mutableStateOf(false) }
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val settings = LocalSettings.current
    var menu by remember { mutableStateOf(false) }
    var editNote by remember(cur.exercise.id) { mutableStateOf(false) }
    var note by remember(cur.exercise.id, cur.exercise.personalNotes) { mutableStateOf(cur.exercise.personalNotes) }
    Glass(Modifier.fillMaxWidth()) {
      Column {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(cur.exercise, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)), animate = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cur.exercise.name.uppercase(), style = FitType.section, color = th.text, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).drawBehind { drawCircle(muscleColor(cur.exercise.primaryMuscle)) })
                    Spacer(Modifier.width(6.dp))
                    Caption(cur.exercise.primaryMuscle + (cur.exercise.equipment.takeIf { it.isNotBlank() && it != "other" }?.let { " · " + it.replaceFirstChar { c -> c.uppercase() } } ?: ""))
                    if (cur.we.supersetGroup != null) { Spacer(Modifier.width(8.dp)); Caption("Superset", color = th.accentBright) }
                }
                // time spent on this exercise: from when you opened it (or its first set) until now / its last set
                val since = vm.exerciseStart(cur)
                if (since != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Duo.Timer, null, tint = th.accentBright, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Caption("${mmss(((now - since) / 1000).coerceAtLeast(0))} on this exercise", color = th.accentBright)
                }
                vm.targets[cur.exercise.id]?.let { t ->
                    val reps = listOfNotNull(t.targetRepsMin, t.targetRepsMax).distinct().joinToString("–")
                    Caption("Target ${t.targetSets} sets" + (if (reps.isNotEmpty()) " × $reps" else "") + " · rest ${t.restSeconds}s")
                }
            }
            Box {
                GlassIconButton(Duo.MoreVert, { menu = true }, size = 40.dp)
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Exercise details") }, { menu = false; nav.push(Overlay.ExerciseDetail(cur.exercise.id)) }, leadingIcon = { Icon(Duo.Info, null) })
                    DropdownMenuItem({ Text("Move up") }, { menu = false; vm.move(cur.we.id, -1) }, leadingIcon = { Icon(Duo.ArrowUpward, null) })
                    DropdownMenuItem({ Text("Move down") }, { menu = false; vm.move(cur.we.id, 1) }, leadingIcon = { Icon(Duo.ArrowDownward, null) })
                    DropdownMenuItem(
                        { Text(if (cur.we.supersetGroup != null) "Remove from superset" else "Superset with next") },
                        { menu = false; vm.toggleSuperset(cur, list) },
                        leadingIcon = { Icon(if (cur.we.supersetGroup != null) Duo.LinkOff else Duo.Link, null) },
                    )
                    DropdownMenuItem({ Text("Start rest timer") }, { menu = false; vm.startRest(cur, settings) }, leadingIcon = { Icon(Duo.Timer, null) })
                    DropdownMenuItem({ Text("Remove exercise", color = th.danger) }, {
                        menu = false
                        if (cur.sets.isEmpty()) vm.remove(cur.we.id, onRemoved) else confirmRemove = true
                    }, leadingIcon = { Icon(Duo.DeleteOutline, null, tint = th.danger) })
                }
            }
        }
        // added by mistake? a visible way out (logged sets are only removed after asking)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End) {
            Text("Remove this exercise", style = FitType.caption, color = th.danger, modifier = Modifier.clickableNoRipple {
                if (cur.sets.isEmpty()) vm.remove(cur.we.id, onRemoved) else confirmRemove = true
            }.padding(vertical = 4.dp))
        }
        if (confirmRemove) androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${cur.exercise.name}?") },
            text = { Text("It has ${cur.sets.size} logged set${if (cur.sets.size == 1) "" else "s"}. They'll be removed from this workout too.") },
            confirmButton = { androidx.compose.material3.TextButton({ confirmRemove = false; vm.remove(cur.we.id, onRemoved, withSets = true) }) { Text("Remove", color = th.danger) } },
            dismissButton = { androidx.compose.material3.TextButton({ confirmRemove = false }) { Text("Keep") } },
        )
        // sticky note: stays with the exercise across every workout (seat height, grip, cues…)
        if (editNote) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            com.myfit.tracker.ui.entries.NotesField(note, { note = it.take(300) }, "e.g. seat on 4, narrow grip, keep elbows in")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Cancel", { note = cur.exercise.personalNotes; editNote = false }, Modifier.weight(1f), height = 40.dp)
                AccentButton("Save note", { val n = note.trim(); container.write { container.exerciseRepo.setNotes(cur.exercise.id, n) }; editNote = false }, Modifier.weight(1f), height = 40.dp)
            }
        } else Row(
            Modifier.fillMaxWidth().clickableNoRipple { editNote = true }.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Duo.EditNote, null, tint = if (note.isBlank()) th.textFaint else th.warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(note.ifBlank { "Add a note for this exercise" }, style = FitType.caption, color = if (note.isBlank()) th.textFaint else th.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
      }
    }
}

@Composable
private fun PreviousCard(cur: WorkoutExerciseView, vm: GymViewModel, u: com.myfit.tracker.domain.UnitPrefs) {
    val th = LocalFitTheme.current
    val h = vm.history[cur.exercise.id]
    val m = cur.exercise.measurementType
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Duo.History, null, tint = th.textDim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("PREVIOUS WORKOUT" + (h?.lastDate?.let { " · $it" } ?: ""), style = FitType.overline, color = th.textDim)
            }
            Spacer(Modifier.height(8.dp))
            when {
                h == null -> Caption("Loading…")
                h.lastSession.isEmpty() -> Caption("First time logging this exercise — today sets your baseline.")
                else -> h.lastSession.forEach { r ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text("${r.setNumber}", style = FitType.label, color = th.textFaint, modifier = Modifier.width(22.dp))
                        Text(formatSet(m, r, u), style = FitType.section, color = th.text)
                        val t = setTypeShort(r.setType)
                        if (t.isNotEmpty()) { Spacer(Modifier.width(6.dp)); Caption(t) }
                    }
                }
            }
            h?.best?.let { b ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Duo.EmojiEvents, null, tint = th.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Caption("Best recorded: ${formatSet(m, b, u)} · ${b.workoutLocalDate} · ${h.sessions} session${if (h.sessions == 1) "" else "s"}")
                }
            }
        }
    }
}

@Composable
private fun TodaySets(cur: WorkoutExerciseView, u: com.myfit.tracker.domain.UnitPrefs, h: ExerciseHistory?, onEdit: (SetRow) -> Unit) {
    val th = LocalFitTheme.current
    if (cur.sets.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("TODAY", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(start = 6.dp))
        cur.sets.forEach { r ->
            Glass(Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), onClick = { onEdit(r) }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).drawBehind { drawCircle(if (r.setType == SetType.WARMUP) th.textFaint else th.success) }, contentAlignment = Alignment.Center) {
                        Text(setTypeShort(r.setType).ifEmpty { "${r.setNumber}" }, style = FitType.label, color = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(formatSet(cur.exercise.measurementType, r, u), style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                    setDelta(r, h?.lastSession?.firstOrNull { it.setNumber == r.setNumber }, u)?.let { (txt, up) ->
                        Text(txt, style = FitType.label, color = when (up) { true -> th.success; false -> th.warning; null -> th.textDim })
                        Spacer(Modifier.width(8.dp))
                    }
                    r.rpe?.let { Caption("RPE ${Fmt.trim(it)}"); Spacer(Modifier.width(8.dp)) }
                    r.restSec?.let { Caption("rest ${mmss(it)}") }
                }
            }
        }
        Caption("Arrows compare each set with the same set last time. Tap a set to correct or delete it.")
    }
}

@Composable
private fun CurrentSetCard(cur: WorkoutExerciseView, list: List<WorkoutExerciseView>, vm: GymViewModel, container: AppContainer, now: Long, onResult: (GymViewModel.CompleteResult) -> Unit) {
    val th = LocalFitTheme.current
    val s = LocalSettings.current
    val d = vm.drafts[cur.we.id] ?: Draft()
    val setNo = cur.sets.size + 1
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("SET $setNo", style = FitType.hero.copy(fontSize = FitType.hero.fontSize * 0.8f), color = th.text)
                Spacer(Modifier.width(10.dp))
                if (d.source.isNotEmpty()) Caption(d.source, Modifier.padding(bottom = 8.dp))
            }
            SetTypeRow(d.setType) { t -> vm.update(cur.we.id) { it.copy(setType = t) } }
            SetInputs(cur.exercise.measurementType, d, { nd -> vm.update(cur.we.id) { nd } }, s.units, s.weightStepKg)
            if (cur.exercise.measurementType in listOf(MeasurementType.DURATION, MeasurementType.DISTANCE_DURATION, MeasurementType.WEIGHT_DURATION)) {
                // set timer: times a plank / hold / interval and fills the duration in for you
                val started = vm.setTimerStart[cur.we.id]
                if (started == null) GlassButton("Start set timer", { vm.setTimerStart[cur.we.id] = System.currentTimeMillis() }, Modifier.fillMaxWidth(), icon = Duo.Timer, height = 48.dp)
                else AccentButton("Stop · ${mmss(((now - started) / 1000).coerceAtLeast(0))}", {
                    val sec = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(1)
                    vm.setTimerStart.remove(cur.we.id)
                    vm.update(cur.we.id) { it.copy(durationSec = sec, source = "Timed") }
                }, Modifier.fillMaxWidth(), icon = Duo.Stop, height = 48.dp)
            }
            RpeRow(d.rpe) { r -> vm.update(cur.we.id) { it.copy(rpe = r) } }
            AccentButton(
                if (vm.busy) "Saving…" else "COMPLETE SET",
                { vm.complete(cur, list, s, onResult) },
                Modifier.fillMaxWidth(), icon = Duo.Check, height = 72.dp, enabled = !vm.busy,
            )
            if (cur.exercise.measurementType == MeasurementType.WEIGHT_REPS) Caption("Volume counts weight × reps of non-warm-up sets.")
        }
    }
}

@Composable
private fun RestPanel(startedAt: Long, endsAt: Long, now: Long, label: String, onAdd: () -> Unit, onSkip: () -> Unit) {
    val th = LocalFitTheme.current
    val total = (endsAt - startedAt).coerceAtLeast(1)
    val left = endsAt - now
    val over = left < 0
    Glass(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), shape = RoundedCornerShape(34.dp), blur = 30.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(if (over) 1f else (left.toFloat() / total), if (over) th.success else th.accentBright, size = 92.dp, stroke = 9.dp) {
                Text(if (over) "+${mmss(-left / 1000)}" else mmss((left + 999) / 1000), style = FitType.title, color = th.text)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(if (over) "Rest over — go!" else "Resting", style = FitType.section, color = if (over) th.success else th.text)
                Caption("Next: $label", Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("+15 s", onAdd, height = 40.dp)
                    GlassButton(if (over) "Done" else "Skip", onSkip, height = 40.dp)
                }
            }
        }
    }
}

@Composable
private fun EditSetContent(row: SetRow, ev: WorkoutExerciseView, vm: GymViewModel, u: com.myfit.tracker.domain.UnitPrefs, stepKg: Double, close: () -> Unit) {
    val th = LocalFitTheme.current
    var d by remember(row.setId) { mutableStateOf(Draft(row.setType, row.weightKg, row.reps, row.durationSec, row.distanceM, row.rpe)) }
    var confirm by remember { mutableStateOf(false) }
    Text("Edit set ${row.setNumber}", style = FitType.title, color = th.text, modifier = Modifier.padding(vertical = 8.dp))
    Caption("${ev.exercise.name} · originally ${formatSet(ev.exercise.measurementType, row, u)}")
    Spacer(Modifier.height(12.dp))
    SetTypeRow(d.setType) { d = d.copy(setType = it) }
    Spacer(Modifier.height(12.dp))
    SetInputs(ev.exercise.measurementType, d, { d = it }, u, stepKg)
    Spacer(Modifier.height(10.dp))
    RpeRow(d.rpe) { d = d.copy(rpe = it) }
    Spacer(Modifier.height(18.dp))
    val err = vm.validate(ev.exercise.measurementType, d)
    AccentButton("Save correction", { vm.editSet(row.setId, d); close() }, Modifier.fillMaxWidth(), enabled = err == null)
    Spacer(Modifier.height(10.dp))
    GlassButton("Delete set", { confirm = true }, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline)
    if (confirm) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete set ${row.setNumber}?") },
        text = { Text("Remaining sets are renumbered and all totals recalculate.") },
        confirmButton = { androidx.compose.material3.TextButton({ vm.deleteSet(row.setId); confirm = false; close() }) { Text("Delete", color = th.danger) } },
        dismissButton = { androidx.compose.material3.TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

/** "▲2.5 kg", "+2 reps", "=" vs the same set number last session; null when there's nothing to compare. */
private fun setDelta(r: SetRow, prev: SetRow?, u: com.myfit.tracker.domain.UnitPrefs): Pair<String, Boolean?>? {
    if (prev == null || r.setType == SetType.WARMUP) return null
    val dw = (r.weightKg ?: 0.0) - (prev.weightKg ?: 0.0)
    val dr = (r.reps ?: 0) - (prev.reps ?: 0)
    val dd = (r.durationSec ?: 0L) - (prev.durationSec ?: 0L)
    return when {
        r.weightKg != null && prev.weightKg != null && kotlin.math.abs(dw) >= 0.05 ->
            (if (dw > 0) "▲" else "▼") + Fmt.weight(kotlin.math.abs(dw), u.weight, 1) to (dw > 0)
        r.reps != null && prev.reps != null && dr != 0 -> (if (dr > 0) "+$dr" else "$dr") + " reps" to (dr > 0)
        r.durationSec != null && prev.durationSec != null && dd != 0L -> (if (dd > 0) "+" else "−") + mmss(kotlin.math.abs(dd)) to (dd > 0)
        r.reps != null || r.durationSec != null -> "=" to null
        else -> null
    }
}
