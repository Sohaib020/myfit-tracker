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

    // R32: one hero, three clear tabs — Today (what to do now), Skill tree (one chain at a time), Progress
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var chainSel by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("push") }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Calisthenics", { nav.pop() }, "Bodyweight skills that level up with you")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item(key = "hero") { CaliHero(data, s, onStart = { startSession() }, onTree = { tab = 1 }) }
            item(key = "tabs") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Today", "Skill tree", "Progress").forEachIndexed { i, t -> GlassChip(t, tab == i, { tab = i }) }
                }
            }
            when (tab) {
                0 -> {
                    if (!s.placed) item(key = "test") { LevelTestCard(onTest = { test = true }, onSkip = { Cali.place(ctx, emptyMap()) }) }
                    item(key = "today") { TodayCard(container, data, s, onStart = { startSession() }, onGear = { Cali.setGear(ctx, it) }, onStep = { c, i -> open = c to i }) }
                }
                1 -> {
                    item(key = "chains") {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(data.chains, key = { "c" + it.id }) { c ->
                                ChainChip(c, s, chainSel == c.id) { chainSel = c.id }
                            }
                        }
                    }
                    val c = data.chain(chainSel) ?: data.chains.first()
                    item(key = "chainHead_${c.id}") { ChainHeader(c, s) }
                    items(c.steps.size, key = { "st_${c.id}_$it" }) { i ->
                        PathStep(c, i, s, history, exercises, last = i == c.steps.lastIndex) { open = c to i }
                    }
                }
                else -> {
                    item(key = "overview") { Overview(data, s) }
                    item(key = "badges") { CaliBadges(data, s) }
                    item(key = "how") { HowItWorks(onRetest = { test = true }) }
                }
            }
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
private fun TodayCard(container: AppContainer, data: Cali.Data, s: Cali.State, onStart: () -> Unit, onGear: (Set<String>) -> Unit, onStep: (Cali.Chain, Int) -> Unit = { _, _ -> }) {
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
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickableNoRipple { onStep(it2.chain, it2.chain.steps.indexOf(it2.step)) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = FitType.label, color = Color(it2.chain.color), modifier = Modifier.width(28.dp))
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(TILE_BG)) {
                        CaliArt(it2.step, Color(it2.chain.color), Modifier.fillMaxSize().padding(2.dp))
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
                    CaliArt(step, col, Modifier.fillMaxSize().padding(4.dp), animate = true)
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
            CaliArt(step, col, Modifier.fillMaxSize().padding(3.dp).graphicsLayer { alpha = if (state == 0) 0.55f else 1f })
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
            CaliArt(step, col, Modifier.fillMaxSize().padding(10.dp), animate = true)
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
            CaliArt(u.to ?: u.from, col, Modifier.fillMaxSize().padding(8.dp), animate = true)
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

/** R32 Train tab hero: your coach performing your current skill, level, progress and one tap into the skill tree. */
@Composable
fun CalisthenicsTrainCard(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val data = remember { Cali.data(ctx) }
    val st by Cali.state.collectAsState()
    LaunchedEffect(Unit) { if (Cali.state.value == null) Cali.read(ctx) }
    val s = st
    val total = data.chains.sumOf { it.steps.size }
    val done = s?.let { x -> data.chains.sumOf { c -> c.steps.count { it.key in x.mastered } } } ?: 0
    val pull = data.chain("pull")!!
    val step = pull.steps[s?.cur(pull) ?: 0]
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF0F2A44), Color(0xFF101423), Color(0xFF241534))))
        .clickableNoRipple(onOpen)) {
        Box(Modifier.align(Alignment.CenterEnd).size(200.dp).drawBehind {
            drawCircle(Brush.radialGradient(listOf(Color(pull.color).copy(alpha = 0.5f), Color.Transparent)), radius = size.minDimension / 2)
        })
        CaliArt(step, Color(pull.color), Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(180.dp).padding(vertical = 8.dp), animate = true)
        Column(Modifier.align(Alignment.CenterStart).padding(18.dp).width(190.dp)) {
            Text("CALISTHENICS", style = FitType.overline, color = Color(0xFF7FC8FF))
            Spacer(Modifier.height(4.dp))
            Text("Master your bodyweight", style = FitType.title, color = Color.White, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Caption(if (s?.placed == true) "${levelOf(done)} · $done of $total skills · session ready" else "8 skill chains, from wall push-ups to the front lever", color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (s?.placed == true) "Open skill tree" else "Find my level", style = FitType.label, color = Color.White)
                Spacer(Modifier.width(6.dp))
                Icon(Duo.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ R32 hero, chain path, badges

/** Level from steps mastered: a simple, honest label (no estimates). */
private fun levelOf(done: Int) = when { done >= 40 -> "Elite"; done >= 25 -> "Advanced"; done >= 12 -> "Intermediate"; done >= 4 -> "Novice"; else -> "Beginner" }

@Composable
private fun CaliHero(data: Cali.Data, s: Cali.State, onStart: () -> Unit, onTree: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val total = data.chains.sumOf { it.steps.size }
    val done = data.chains.sumOf { c -> c.steps.count { it.key in s.mastered } }
    // spotlight: the hardest current step across the upper-body chains
    val spot = remember(s) { listOf("pull", "push", "hand", "dip").mapNotNull { id -> data.chain(id)?.let { it to it.steps[s.cur(it)] } }.maxByOrNull { (c, st) -> c.steps.indexOf(st) } }
    val dl by CaliPacks.progress.collectAsState()
    Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(30.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF14233B), Color(0xFF0E1220), Color(0xFF1B1430))))) {
        // soft light behind the coach
        Box(Modifier.align(Alignment.CenterEnd).size(260.dp).drawBehind {
            drawCircle(Brush.radialGradient(listOf((spot?.first?.color?.let { Color(it) } ?: th.accent).copy(alpha = 0.45f), Color.Transparent)), radius = size.minDimension / 2)
        })
        if (spot != null) CaliArt(spot.second, Color(spot.first.color), Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(220.dp).padding(vertical = 10.dp), animate = true)
        Column(Modifier.align(Alignment.TopStart).padding(18.dp).width(170.dp)) {
            Text("CALISTHENICS", style = FitType.overline, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(4.dp))
            Text(levelOf(done), style = FitType.title, color = Color.White)
            Caption("$done of $total steps mastered", color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(10.dp))
            spot?.let { (c, st) ->
                Text("NOW TRAINING", style = FitType.overline, color = Color(c.color))
                Text(st.name, style = FitType.section, color = Color.White, maxLines = 2)
            }
            dl?.let { Caption("Downloading your coach's moves · ${(it * 100).toInt()}%", Modifier.padding(top = 6.dp), color = Color.White.copy(alpha = 0.7f)) }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(16.dp).width(200.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton("Start", onStart, Modifier.weight(1f), icon = Duo.PlayArrow, height = 42.dp)
            GlassButton("Tree", onTree, Modifier.weight(1f), height = 42.dp)
        }
    }
}

@Composable
private fun ChainChip(c: Cali.Chain, s: Cali.State, selected: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val col = Color(c.color)
    Column(Modifier.width(74.dp).clip(RoundedCornerShape(18.dp))
        .background(if (selected) col.copy(alpha = 0.22f) else th.text.copy(alpha = 0.05f))
        .border(if (selected) 1.5.dp else 0.dp, if (selected) col else Color.Transparent, RoundedCornerShape(18.dp))
        .clickableNoRipple(onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(TILE_BG)) {
            CaliArt(c.steps[s.cur(c)], col, Modifier.fillMaxSize().padding(2.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(SHORT[c.id] ?: c.name, style = FitType.label, color = if (selected) th.text else th.textDim, maxLines = 1)
        Text("${s.cur(c) + if (s.done(c)) 1 else 0}/${c.steps.size}", style = FitType.caption, color = col)
    }
}

@Composable
private fun ChainHeader(c: Cali.Chain, s: Cali.State) {
    val th = LocalFitTheme.current
    val col = Color(c.color)
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(c.name, style = FitType.title, color = th.text)
        Caption(c.sub + " · " + if (s.done(c)) "chain complete" else "step ${s.cur(c) + 1} of ${c.steps.size}")
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { (s.cur(c) + if (s.done(c)) 1 else 0) / c.steps.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = col, trackColor = th.textFaint.copy(alpha = 0.2f))
    }
}

/** One step on the vertical path: done (check), current (big, animated, progress to unlock), or ahead (dimmed). */
@Composable
private fun PathStep(c: Cali.Chain, i: Int, s: Cali.State, history: List<SetRow>, exercises: Map<String, Exercise>, last: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val col = Color(c.color)
    val step = c.steps[i]
    val cur = s.cur(c)
    val state = when { step.key in s.mastered || i < cur -> 2; i == cur -> 1; else -> 0 }
    val big = state == 1
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        // rail
        Column(Modifier.width(26.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(if (big) 20.dp else 14.dp).clip(CircleShape).background(when (state) { 2 -> col; 1 -> col; else -> th.textFaint.copy(alpha = 0.35f) }),
                contentAlignment = Alignment.Center) {
                if (state == 2) Icon(Duo.Check, null, tint = Color.Black, modifier = Modifier.size(10.dp))
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(if (state == 2) col.copy(alpha = 0.7f) else th.textFaint.copy(alpha = 0.25f)))
        }
        Spacer(Modifier.width(8.dp))
        Glass(Modifier.weight(1f).padding(bottom = 10.dp), onClick = onClick, tint = if (big) col.copy(alpha = 0.10f) else null) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (big) 112.dp else 64.dp).clip(RoundedCornerShape(16.dp)).background(TILE_BG)
                    .graphicsLayer { alpha = if (state == 0) 0.6f else 1f }) {
                    CaliArt(step, col, Modifier.fillMaxSize().padding(2.dp), animate = big)
                    if (state == 0) Icon(Duo.Lock, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(12.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(when (state) { 2 -> "MASTERED"; 1 -> "NOW"; else -> "STEP ${i + 1}" }, style = FitType.overline, color = if (state == 0) th.textFaint else col)
                    Text(step.name, style = if (big) FitType.section else FitType.body, color = if (state == 0) th.textDim else th.text, maxLines = 2)
                    Caption("${step.target()}${if (step.hold) "" else " reps"} · unlock at ${step.goalText()}")
                    if (big) {
                        val lastS = remember(history, exercises, step) { exercises[step.key]?.let { Cali.sessions(history, it.id, step.hold).firstOrNull() } }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { progressTo(step, lastS) }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = col, trackColor = th.textFaint.copy(alpha = 0.2f))
                        Caption(lastS?.let { l -> "Last time: " + l.joinToString(" · ") { if (step.hold) "${it}s" else "$it" } } ?: "Not logged yet", color = th.textFaint)
                    }
                }
            }
        }
    }
}

/** The six calisthenics badges with what each tier needs (values from your logged sets only). */
@Composable
private fun CaliBadges(data: Cali.Data, s: Cali.State) {
    val th = LocalFitTheme.current
    val m = s.mastered
    fun best(id: String) = data.chain(id)?.let { Cali.best(it, m) + 1 } ?: 0
    val rows = listOf(
        Triple("Skill Climber", m.size, listOf(3, 15, 35)),
        Triple("Chain Master", data.chains.count { it.steps.last().key in m }, listOf(1, 3, 8)),
        Triple("Bar Raiser", best("pull"), listOf(5, 6, 8)),
        Triple("Upside Down", best("hand"), listOf(3, 5, 6)),
        Triple("Lever Up", best("core"), listOf(5, 6, 9)),
        Triple("One-Leg Wonder", best("legs"), listOf(4, 5, 6)),
    )
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Calisthenics badges", style = FitType.section, color = th.text)
            Caption("Bronze · Silver · Gold — earned from the sets you log")
            Spacer(Modifier.height(8.dp))
            rows.forEach { (name, v, tiers) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    listOf(Color(0xFFCD7F32), Color(0xFFC0C6D0), Color(0xFFFFC83D)).forEachIndexed { k, col ->
                        val on = v >= tiers[k]
                        Box(Modifier.padding(start = 6.dp).size(18.dp).clip(CircleShape).background(if (on) col else th.textFaint.copy(alpha = 0.2f)))
                    }
                }
            }
        }
    }
}
