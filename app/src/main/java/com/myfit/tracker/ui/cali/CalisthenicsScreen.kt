package com.myfit.tracker.ui.cali

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.WorkoutTemplateExercise
import com.myfit.tracker.domain.Cali
import com.myfit.tracker.domain.FormGuide
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassDialog
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SHORT = mapOf("push" to "Push", "pull" to "Pull", "row" to "Row", "dip" to "Dip", "legs" to "Legs", "hinge" to "Hinge", "core" to "Core", "hand" to "HS")
private val TILE_BG = Brush.verticalGradient(listOf(Color(0xFF1D2131), Color(0xFF10131B)))

/**
 * R31 Calisthenics: a skill tree of bodyweight progressions that levels up from the sets you log.
 * Level test → current step per chain → today's auto-built session → master a step (3 × goal) → next unlocks.
 */
@Composable
fun CalisthenicsScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val data = remember { Cali.data(ctx) }
    val st by Cali.state.collectAsState()
    val active by container.workoutRepo.inProgress.collectAsState(initial = null)
    var exercises by remember { mutableStateOf<Map<String, Exercise>>(emptyMap()) }
    var history by remember { mutableStateOf<List<SetRow>>(emptyList()) }
    var party by remember { mutableStateOf<List<Cali.Unlock>>(emptyList()) }
    var open by remember { mutableStateOf<Pair<Cali.Chain, Int>?>(null) }
    var test by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Cali.read(ctx)
        withContext(Dispatchers.IO) {
            val keys = data.chains.flatMap { c -> c.steps.map { it.key } }
            val ex = container.workoutRepo.exercisesByKeys(keys)
            val rows = runCatching { container.workoutRepo.allHistory().first() }.getOrDefault(emptyList())
            Cali.sync(ctx, rows, ex.values.associate { it.uuid to it.id })
            val unlocks = Cali.takePending(ctx)
            exercises = ex; history = rows
            if (unlocks.isNotEmpty()) { party = unlocks; com.myfit.tracker.domain.BadgeEngine.refresh(container, force = true) }
        }
    }
    val s = st ?: return

    fun startWith(name: String, build: suspend () -> Long) {
        if (active != null) { toaster.show("Finish or discard your current workout first"); nav.push(Overlay.Gym(active!!.id)); return }
        scope.launch { val id = build(); nav.push(Overlay.Gym(id)) }
    }
    fun startSession() = startWith("Calisthenics") {
        val items = Cali.session(ctx, s).mapNotNull { it2 ->
            val e = exercises[it2.step.key] ?: return@mapNotNull null
            WorkoutTemplateExercise(templateId = 0, exerciseId = e.id, position = 0, targetSets = 3,
                targetRepsMin = if (it2.step.hold) null else it2.step.start, targetRepsMax = if (it2.step.hold) null else it2.step.goal,
                targetWeightKg = null, targetDurationSec = if (it2.step.hold) it2.step.start.toLong() else null,
                restSeconds = Cali.REST, supersetGroup = it2.group, notes = it2.step.cue)
        }
        val tid = container.workoutRepo.saveTemplate(Cali.templateId(ctx), "Calisthenics session",
            "Auto-built from your skill tree. Warm up 5 min first: wrist circles, arm swings, scap pulls, bodyweight squats. Supersets: do A1, rest 90 s, A2, rest 90 s — 3 rounds.", items)
        Cali.setTemplateId(ctx, tid)
        container.workoutRepo.startFromTemplate(tid)
    }
    fun practice(step: Cali.Step) {
        val e = exercises[step.key] ?: run { toaster.show("Exercise not found — reopen the app to finish setup"); return }
        startWith(step.name) { val w = container.workoutRepo.startEmpty(step.name); container.workoutRepo.addExercise(w, e.id); w }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Calisthenics", { nav.pop() }, "Bodyweight skills that level up with you")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "head") { Overview(data, s) }
            if (!s.placed) item(key = "test") { LevelTestCard(onTest = { test = true }, onSkip = { Cali.place(ctx, emptyMap()) }) }
            item(key = "today") { TodayCard(container, data, s, onStart = { startSession() }, onGear = { Cali.setGear(ctx, it) }) }
            items(data.chains, key = { it.id }) { c -> ChainCard(c, s, history, exercises) { open = c to it } }
            item(key = "how") { HowItWorks(onRetest = { test = true }) }
        }
    }

    val o = open
    GlassSheet(visible = o != null, onDismiss = { open = null }) {
        if (o != null) StepSheet(o.first, o.second, s, history, exercises[o.first.steps[o.second].key],
            onCurrent = { Cali.setCurrent(ctx, o.first.id, o.second); toaster.show("${o.first.steps[o.second].name} is now your ${o.first.name} step") },
            onPractice = { open = null; practice(o.first.steps[o.second]) },
            onHowTo = { exercises[o.first.steps[o.second].key]?.let { open = null; nav.push(Overlay.ExerciseDetail(it.id)) } })
    }
    GlassSheet(visible = test, onDismiss = { test = false }) {
        if (test) LevelTest(
            onDone = { v -> Cali.place(ctx, v); test = false; toaster.show("Placed — your session is ready") },
            onCamera = {
                test = false
                startWith("Level test") {
                    val w = container.workoutRepo.startEmpty("Calisthenics level test")
                    listOf("Pushups", "Pullups", "Bodyweight_Squat", "Plank").forEach { k -> exercises[k]?.let { container.workoutRepo.addExercise(w, it.id) } }
                    w
                }
            })
    }
    if (party.isNotEmpty()) UnlockDialog(party) { party = emptyList() }
}

// ------------------------------------------------------------------ overview

@Composable
private fun Overview(data: Cali.Data, s: Cali.State) {
    val th = LocalFitTheme.current
    val total = data.chains.sumOf { it.steps.size }
    val done = data.chains.sumOf { c -> c.steps.count { it.key in s.mastered } }
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Skill tree", style = FitType.title, color = th.text)
                    Caption("$done of $total steps mastered · ${data.chains.count { s.done(it) }} of ${data.chains.size} chains complete")
                }
                Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(progress = { done / total.toFloat() }, modifier = Modifier.fillMaxSize(),
                        color = th.accent, trackColor = th.textFaint.copy(alpha = 0.2f), strokeWidth = 5.dp)
                    Text("${(done * 100 / total.coerceAtLeast(1))}%", style = FitType.label, color = th.text)
                }
            }
            Spacer(Modifier.height(12.dp))
            // one coloured bar per chain: how far along you are
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                data.chains.forEach { c ->
                    val f = (s.cur(c) + if (s.done(c)) 1 else 0) / c.steps.size.toFloat()
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(c.color).copy(alpha = 0.14f))) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(f.coerceIn(0.06f, 1f)).align(Alignment.BottomCenter)
                                .background(Brush.verticalGradient(listOf(Color(c.color), Color(c.color).copy(alpha = 0.55f)))))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(SHORT[c.id] ?: c.name.take(5), style = FitType.overline.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                            color = th.textDim, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelTestCard(onTest: () -> Unit, onSkip: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), tint = th.accent.copy(alpha = 0.14f)) {
        Column(Modifier.padding(16.dp)) {
            Text("Find your level", style = FitType.section, color = th.text)
            Spacer(Modifier.height(4.dp))
            Caption("Two minutes: max push-ups, pull-ups, squats and a plank. We'll start every chain at the right step so nothing is too easy or too hard.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentButton("Take the level test", onTest, Modifier.weight(1f), height = 46.dp)
                GlassButton("Start from step 1", onSkip, height = 46.dp)
            }
        }
    }
}

// ------------------------------------------------------------------ today's session

@Composable
private fun TodayCard(container: AppContainer, data: Cali.Data, s: Cali.State, onStart: () -> Unit, onGear: (Set<String>) -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val items = remember(s) { Cali.session(ctx, s) }
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("TODAY'S SESSION", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(2.dp))
            Text("${items.size} moves · 3 sets each · about ${(items.size * 3 * (40 + Cali.REST) / 2 / 60).coerceAtLeast(20)} min", style = FitType.section, color = th.text)
            Caption("Supersets pair moves that don't tire the same muscles. 90 s rest. Do it 3 times a week.")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip("Pull-up bar", "bar" in s.gear, { onGear(if ("bar" in s.gear) s.gear - "bar" else s.gear + "bar") })
                GlassChip("Dip bars / rings", "dip" in s.gear, { onGear(if ("dip" in s.gear) s.gear - "dip" else s.gear + "dip") })
            }
            Spacer(Modifier.height(10.dp))
            var lastGroup: Int? = -1
            items.forEach { it2 ->
                val g = it2.group
                val label = g?.let { "${'A' + it - 1}${if (lastGroup == g) 2 else 1}" } ?: "•"
                lastGroup = g
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = FitType.label, color = Color(it2.chain.color), modifier = Modifier.width(28.dp))
                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(TILE_BG)) {
                        CaliFigure(it2.step, Color(it2.chain.color), Modifier.fillMaxSize().padding(4.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(it2.step.name, style = FitType.body, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption("${it2.chain.name} · ${it2.step.target()}${if (it2.step.hold) "" else " reps"}")
                    }
                }
            }
            if (items.isEmpty()) Caption("Turn on the gear you have to build a session.")
            Spacer(Modifier.height(12.dp))
            AccentButton("Start session", onStart, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, enabled = items.isNotEmpty(), height = 50.dp)
        }
    }
}

// ------------------------------------------------------------------ chains

@Composable
private fun ChainCard(c: Cali.Chain, s: Cali.State, history: List<SetRow>, exercises: Map<String, Exercise>, onStep: (Int) -> Unit) {
    val th = LocalFitTheme.current
    val col = Color(c.color)
    val cur = s.cur(c)
    val step = c.steps[cur]
    val last = remember(history, exercises, step) { exercises[step.key]?.let { Cali.sessions(history, it.id, step.hold).firstOrNull() } }
    Glass(Modifier.fillMaxWidth(), tint = col.copy(alpha = 0.07f)) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = FitType.section, color = th.text)
                    Caption(c.sub)
                }
                Text(if (s.done(c)) "Complete" else "Step ${cur + 1}/${c.steps.size}", style = FitType.label, color = if (s.done(c)) th.success else col)
            }
            Spacer(Modifier.height(10.dp))
            // current step, big
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickableNoRipple { onStep(cur) },
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)).background(TILE_BG).border(1.dp, col.copy(alpha = 0.5f), RoundedCornerShape(18.dp))) {
                    CaliFigure(step, col, Modifier.fillMaxSize().padding(8.dp), animate = true)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NOW", style = FitType.overline, color = col)
                    Text(step.name, style = FitType.section, color = th.text, maxLines = 2)
                    Caption("Work at ${step.target()}${if (step.hold) "" else " reps"} · unlock at ${step.goalText()}${if (step.hold) "" else ""}")
                    val p = progressTo(step, last)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = col, trackColor = th.textFaint.copy(alpha = 0.2f))
                    Spacer(Modifier.height(3.dp))
                    Caption(last?.let { l -> "Last time: " + l.joinToString(" · ") { if (step.hold) "${it}s" else "$it" } } ?: "Not logged yet", color = th.textFaint)
                }
            }
            Spacer(Modifier.height(12.dp))
            // the whole path
            val ls = rememberLazyListState(initialFirstVisibleItemIndex = (cur - 1).coerceAtLeast(0))
            LazyRow(state = ls, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                items(c.steps.size) { i ->
                    val stt = when { c.steps[i].key in s.mastered -> 2; i == cur -> 1; i < cur -> 2; else -> 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (i > 0) Box(Modifier.width(14.dp).height(2.dp).background(if (stt > 0) col.copy(alpha = 0.7f) else th.textFaint.copy(alpha = 0.3f)))
                        StepNode(c.steps[i], col, stt, i + 1) { onStep(i) }
                    }
                }
            }
        }
    }
}

/** 0..1 toward the goal from the last logged session (average of its best three sets). */
private fun progressTo(step: Cali.Step, last: List<Int>?): Float {
    if (last.isNullOrEmpty()) return 0f
    val best3 = last.sortedDescending().take(3)
    return (best3.sumOf { minOf(it, step.goal) } / (3f * step.goal)).coerceIn(0f, 1f)
}

/** A step on the path: 2 = mastered (check), 1 = current (glowing), 0 = ahead (dimmed, still in colour). */
@Composable
private fun StepNode(step: Cali.Step, col: Color, state: Int, n: Int, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val glow = if (state == 1) rememberInfiniteTransition(label = "node").animateFloat(0.35f, 0.9f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g").value else 0f
    Column(Modifier.width(78.dp).clickableNoRipple(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp)
            .drawBehind { if (state == 1) drawCircle(Brush.radialGradient(listOf(col.copy(alpha = glow * 0.55f), Color.Transparent)), radius = size.minDimension * 0.75f) }
            .clip(RoundedCornerShape(16.dp)).background(TILE_BG)
            .border(if (state == 1) 2.dp else 1.dp, when (state) { 2 -> col.copy(alpha = 0.8f); 1 -> col; else -> Color.White.copy(alpha = 0.12f) }, RoundedCornerShape(16.dp))) {
            CaliFigure(step, col, Modifier.fillMaxSize().padding(6.dp).graphicsLayer { alpha = if (state == 0) 0.55f else 1f })
            if (state == 2) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp).clip(CircleShape).background(col), contentAlignment = Alignment.Center) {
                Icon(Duo.Check, null, tint = Color.Black, modifier = Modifier.size(11.dp))
            }
            if (state == 0) Icon(Duo.Lock, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(12.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(step.name, style = FitType.caption, color = if (state == 0) th.textDim else th.text, maxLines = 2, textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 28.dp))
    }
}

// ------------------------------------------------------------------ step detail

@Composable
private fun StepSheet(c: Cali.Chain, i: Int, s: Cali.State, history: List<SetRow>, ex: Exercise?, onCurrent: () -> Unit, onPractice: () -> Unit, onHowTo: () -> Unit) {
    val th = LocalFitTheme.current
    val step = c.steps[i]
    val col = Color(c.color)
    val sess = remember(history, ex) { ex?.let { Cali.sessions(history, it.id, step.hold) }.orEmpty() }
    val pattern = remember(step) { FormGuide.of(ex?.name ?: step.name, ex?.primaryMuscle.orEmpty(), ex?.measurementType.orEmpty()) }
    val cam = FormGuide.cameraCounts(pattern) || FormGuide.cameraHolds(pattern)
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)).background(TILE_BG)) {
            CaliFigure(step, col, Modifier.fillMaxSize().padding(18.dp), animate = true)
            Text("${c.name.uppercase()} · STEP ${i + 1}", style = FitType.overline, color = col, modifier = Modifier.padding(14.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(step.name, style = FitType.title, color = th.text)
        Spacer(Modifier.height(2.dp))
        Caption(step.cue)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("Work at", step.target() + if (step.hold) "" else " reps", Modifier.weight(1f))
            Fact("Unlock next", step.goalText() + if (step.hold) "" else " reps", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("Gear", step.equipment, Modifier.weight(1f))
            Fact("Camera coach", if (cam) (if (FormGuide.cameraHolds(pattern)) "Times the hold" else "Counts reps") else "Voice & timer", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        val status = when {
            step.key in s.mastered -> "Mastered ✓"
            i == s.cur(c) -> "Your current step"
            i > s.cur(c) -> "Locked — master the steps before it, or set it as current if you can already do it"
            else -> "Behind you"
        }
        Text(status, style = FitType.label, color = if (step.key in s.mastered) th.success else th.textDim)
        if (sess.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("RECENT SESSIONS", style = FitType.overline, color = th.textDim)
            sess.take(4).forEach { l ->
                val ok = l.count { it >= step.goal } >= 3
                Caption((if (ok) "✓  " else "•  ") + l.joinToString(" · ") { if (step.hold) "${it}s" else "$it" }, color = if (ok) th.success else th.text)
            }
        }
        Spacer(Modifier.height(14.dp))
        AccentButton("Practise now", onPractice, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 48.dp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (i != s.cur(c)) GlassButton("Make current", onCurrent, Modifier.weight(1f), height = 46.dp)
            GlassButton("How to", onHowTo, Modifier.weight(1f), icon = Duo.Info, height = 46.dp)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(th.text.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 9.dp)) {
        Text(label.uppercase(), style = FitType.overline, color = th.textDim)
        Spacer(Modifier.height(2.dp))
        Text(value, style = FitType.label, color = th.text, maxLines = 2)
    }
}

// ------------------------------------------------------------------ level test

@Composable
private fun LevelTest(onDone: (Map<String, Int>) -> Unit, onCamera: () -> Unit) {
    val th = LocalFitTheme.current
    val v = remember { mutableStateMapOf("pushups" to 0, "pullups" to 0, "hang" to 0, "squats" to 0, "plank" to 0) }
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())) {
        Text("Level test", style = FitType.title, color = th.text)
        Caption("Do each one fresh, with good form, and stop when your form breaks. Rest 2–3 minutes between them. Enter your best numbers.")
        Spacer(Modifier.height(12.dp))
        Counter("Push-ups", "max in one set", v["pushups"]!!, 1) { v["pushups"] = it }
        Counter("Pull-ups", "max in one set, from a dead hang", v["pullups"]!!, 1) { v["pullups"] = it }
        if (v["pullups"] == 0) Counter("Dead hang", "seconds", v["hang"]!!, 5) { v["hang"] = it }
        Counter("Squats", "max in one set", v["squats"]!!, 5) { v["squats"] = it }
        Counter("Plank", "seconds", v["plank"]!!, 10) { v["plank"] = it }
        Spacer(Modifier.height(10.dp))
        AccentButton("Place me", { onDone(v.toMap()) }, Modifier.fillMaxWidth(), height = 48.dp)
        Spacer(Modifier.height(8.dp))
        GlassButton("Count them with the camera first", onCamera, Modifier.fillMaxWidth(), icon = Duo.Camera, height = 46.dp)
        Spacer(Modifier.height(6.dp))
        Caption("You can move any chain to another step later. Placement is a starting point, not a score.", color = th.textFaint)
    }
}

@Composable
private fun Counter(label: String, hint: String, value: Int, step: Int, onChange: (Int) -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = FitType.body, color = th.text)
            Caption(hint)
        }
        GlassIconButton(Duo.Remove, { onChange((value - step).coerceAtLeast(0)) }, size = 38.dp)
        Text("$value", style = FitType.metric, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
        GlassIconButton(Duo.Add, { onChange((value + step).coerceAtMost(999)) }, size = 38.dp)
    }
}

// ------------------------------------------------------------------ unlocks + help

@Composable
private fun UnlockDialog(list: List<Cali.Unlock>, onDismiss: () -> Unit) {
    val th = LocalFitTheme.current
    val u = list.first()
    val col = Color(u.chain.color)
    GlassDialog(onDismiss, col) {
        Text(if (u.to == null) "CHAIN COMPLETE" else "NEW SKILL UNLOCKED", style = FitType.overline, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(180.dp).clip(RoundedCornerShape(26.dp)).background(TILE_BG)) {
            CaliFigure(u.to ?: u.from, col, Modifier.fillMaxSize().padding(14.dp), animate = true)
        }
        Spacer(Modifier.height(12.dp))
        Text(u.to?.name ?: u.chain.name, style = FitType.title, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text("You mastered ${u.from.name} (${u.from.goalText()}${if (u.from.hold) "" else " reps"}).", style = FitType.body, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        if (list.size > 1) Text("+ ${list.size - 1} more: " + list.drop(1).joinToString { it.to?.name ?: it.chain.name }, style = FitType.caption,
            color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(6.dp))
        Text("★ Stars added to your Arena level", style = FitType.label, color = Color(0xFFFFD34D))
        Spacer(Modifier.height(14.dp))
        AccentButton("Let's go", onDismiss, Modifier.fillMaxWidth(), height = 46.dp)
    }
}

@Composable
private fun HowItWorks(onRetest: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("How progress works", style = FitType.section, color = th.text)
            Spacer(Modifier.height(6.dp))
            listOf(
                "Train your current step for 3 sets in the work range.",
                "When one workout logs 3 sets at the unlock number, the step is mastered and the next one opens.",
                "Add a rep (or a few seconds) whenever you can. Clean form beats extra reps.",
                "Each mastered step earns Arena stars and counts toward calisthenics badges.",
            ).forEachIndexed { n, t -> Caption("${n + 1}.  $t", Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(10.dp))
            GlassButton("Retake the level test", onRetest, Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
}

// ------------------------------------------------------------------ Train tab entry

/** R31 Train tab card: your current pull / push / handstand steps and overall progress; opens the skill tree. */
@Composable
fun CalisthenicsTrainCard(onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val data = remember { Cali.data(ctx) }
    val st by Cali.state.collectAsState()
    LaunchedEffect(Unit) { if (Cali.state.value == null) Cali.read(ctx) }
    val s = st
    val total = data.chains.sumOf { it.steps.size }
    val done = s?.let { x -> data.chains.sumOf { c -> c.steps.count { it.key in x.mastered } } } ?: 0
    Glass(Modifier.fillMaxWidth(), onClick = onOpen, tint = Color(0xFF3FA9FF).copy(alpha = 0.08f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CALISTHENICS", style = FitType.overline, color = th.textDim)
                Text("Skill tree", style = FitType.section, color = th.text)
                Spacer(Modifier.height(2.dp))
                Caption(if (s?.placed == true) "$done of $total steps mastered · today's session ready" else "8 chains from wall push-ups to the front lever. Take the 2-minute level test.")
            }
            Spacer(Modifier.width(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pull", "push", "hand").forEach { id ->
                    val c = data.chain(id) ?: return@forEach
                    Box(Modifier.size(width = 46.dp, height = 62.dp).clip(RoundedCornerShape(12.dp)).background(TILE_BG)
                        .border(1.dp, Color(c.color).copy(alpha = 0.45f), RoundedCornerShape(12.dp))) {
                        CaliFigure(c.steps[s?.cur(c) ?: 0], Color(c.color), Modifier.fillMaxSize().padding(4.dp))
                    }
                }
            }
        }
    }
}
