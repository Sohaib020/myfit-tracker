package com.myfit.tracker.ui.train

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.WorkoutTemplateExercise
import com.myfit.tracker.domain.WorkoutAi
import com.myfit.tracker.domain.WorkoutPlanner
import com.myfit.tracker.domain.WorkoutPlanner.Equip
import com.myfit.tracker.domain.WorkoutPlanner.Goal
import com.myfit.tracker.domain.WorkoutPlanner.Item
import com.myfit.tracker.domain.WorkoutPlanner.Level
import com.myfit.tracker.domain.WorkoutPlanner.Target
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.programs.PItem
import com.myfit.tracker.ui.programs.ProgramEngine
import com.myfit.tracker.ui.programs.ProgramLib
import com.myfit.tracker.ui.programs.target
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

/* Day Builder + Plan Builder: "train chest & biceps today" or "make me a 4-day muscle plan" in a few taps.
 * WorkoutPlanner builds the day instantly (offline); WorkoutAi adjusts it from plain words when AI is available. */

/** Remembers the last choices so the next build starts where you left off. */
internal object PlannerPrefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("planner", Context.MODE_PRIVATE)
    fun goal(c: Context) = runCatching { Goal.valueOf(sp(c).getString("goal", null)!!) }.getOrDefault(Goal.MUSCLE)
    fun equip(c: Context) = runCatching { Equip.valueOf(sp(c).getString("equip", null)!!) }.getOrDefault(Equip.GYM)
    fun level(c: Context) = runCatching { Level.valueOf(sp(c).getString("level", null)!!) }.getOrDefault(Level.BEGINNER)
    fun minutes(c: Context) = sp(c).getInt("minutes", 45)
    fun days(c: Context) = sp(c).getInt("days", 3)
    fun save(c: Context, goal: Goal, equip: Equip, level: Level, minutes: Int, days: Int? = null) {
        sp(c).edit().putString("goal", goal.name).putString("equip", equip.name).putString("level", level.name).putInt("minutes", minutes)
            .apply { if (days != null) putInt("days", days) }.apply()
    }
}

/** Quick picks shown on the Train home; each opens the Day Builder already filled. */
val QUICK_DAYS: List<Pair<String, List<Target>>> = listOf(
    "Chest" to listOf(Target.CHEST), "Back" to listOf(Target.BACK), "Legs" to listOf(Target.LEGS, Target.GLUTES),
    "Shoulders" to listOf(Target.SHOULDERS), "Arms" to listOf(Target.ARMS), "Chest & Biceps" to listOf(Target.CHEST, Target.BICEPS),
    "Back & Triceps" to listOf(Target.BACK, Target.TRICEPS), "Push" to listOf(Target.CHEST, Target.SHOULDERS, Target.TRICEPS),
    "Pull" to listOf(Target.BACK, Target.BICEPS), "Full body" to listOf(Target.FULL), "Abs & core" to listOf(Target.CORE),
    "Glutes" to listOf(Target.GLUTES, Target.LEGS), "Cardio" to listOf(Target.CARDIO),
)

private fun repsText(i: Item) = when {
    i.reps == "max" -> "max reps"
    i.reps.endsWith("s") -> i.reps.dropLast(1) + " s"
    i.reps.endsWith("m") -> i.reps.dropLast(1) + " min"
    else -> i.reps.replace("-", "–") + " reps"
}

private fun restText(s: Int) = if (s < 60) "${s}s" else "${s / 60}:${"%02d".format(s % 60)}"

/** Template rows for a planned day (shared by Day Builder save and plans). */
internal fun Item.toTemplate(ex: Exercise, position: Int): WorkoutTemplateExercise {
    val t = PItem(key, sets, reps, rest, main).target(null)
    return WorkoutTemplateExercise(templateId = 0, exerciseId = ex.id, position = position, targetSets = t.sets, targetRepsMin = t.lo,
        targetRepsMax = t.hi, targetWeightKg = null, targetDurationSec = t.durSec?.toLong(), restSeconds = t.rest)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayBuilderScreen(container: AppContainer, preset: String?) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val active by container.workoutRepo.inProgress.collectAsState(initial = null)

    val targets = remember {
        mutableStateListOf<Target>().apply {
            addAll(preset?.split(',')?.mapNotNull { n -> Target.entries.firstOrNull { it.name == n } }.orEmpty().ifEmpty { listOf(Target.CHEST) })
        }
    }
    var goal by remember { mutableStateOf(PlannerPrefs.goal(ctx)) }
    var equip by remember { mutableStateOf(PlannerPrefs.equip(ctx)) }
    var level by remember { mutableStateOf(PlannerPrefs.level(ctx)) }
    var minutes by remember { mutableIntStateOf(PlannerPrefs.minutes(ctx)) }
    var variant by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var nameEdited by remember { mutableStateOf(false) }
    val items = remember { mutableStateListOf<Item>() }
    val ex = remember { mutableStateMapOf<String, Exercise>() }
    var ask by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiNote by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var skipRebuild by remember { mutableStateOf(false) }

    // instant rule-based build whenever the choices change
    LaunchedEffect(targets.toList(), goal, equip, level, minutes, variant) {
        if (skipRebuild) { skipRebuild = false; return@LaunchedEffect }
        if (targets.isEmpty()) { items.clear(); return@LaunchedEffect }
        val d = WorkoutPlanner.day(targets.toList(), goal, equip, level, minutes, variant)
        items.clear(); items.addAll(d.items)
        if (!nameEdited) name = WorkoutPlanner.suggestName(targets.toList())
        aiNote = null
    }
    // exercise rows (names, images) for whatever keys are on screen
    LaunchedEffect(items.map { it.key }) {
        val missing = items.map { it.key }.filter { it !in ex }
        if (missing.isNotEmpty()) ex.putAll(container.workoutRepo.exercisesByKeys(missing))
    }
    val shown = items.filter { it.key in ex }
    val totalSets = shown.sumOf { it.sets }
    val estMin = WorkoutPlanner.Day(name, "", shown).minutes

    fun toggle(t: Target) {
        if (t in targets) targets.remove(t) else {
            if (t == Target.FULL) targets.clear() else targets.remove(Target.FULL)
            if (t == Target.ARMS) { targets.remove(Target.BICEPS); targets.remove(Target.TRICEPS) }
            if (t == Target.BICEPS || t == Target.TRICEPS) targets.remove(Target.ARMS)
            targets.add(t)
        }
        variant = 0
    }

    fun save(start: Boolean) {
        if (saving || shown.isEmpty()) return
        if (start && active != null) { toaster.show("Finish or discard your current workout first"); nav.push(Overlay.Gym(active!!.id)); return }
        saving = true
        PlannerPrefs.save(ctx, goal, equip, level, minutes)
        scope.launch {
            val rows = shown.mapIndexed { i, it -> it.toTemplate(ex.getValue(it.key), i) }
            val focus = targets.joinToString(" · ") { it.label }
            val tid = container.workoutRepo.saveTemplate(null, name.trim().ifBlank { WorkoutPlanner.suggestName(targets.toList()) }.take(60),
                "$focus · ${goal.label} · ~$estMin min", rows)
            if (start) nav.replace(Overlay.Gym(container.workoutRepo.startFromTemplate(tid)))
            else { toaster.show("Saved to My workout days"); nav.pop() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Build a workout day", { nav.pop() }, "Pick muscles — it fills itself in")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("What are you training?", style = FitType.section, color = th.text)
                        Caption("Pick one or combine a few, e.g. Chest + Biceps.")
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Target.entries.forEach { t -> GlassChip(t.label, t in targets, { toggle(t) }) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Time", style = FitType.label, color = th.textDim)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(20, 30, 45, 60, 75, 90)) { m -> GlassChip("$m min", minutes == m, { minutes = m }) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Equipment", style = FitType.label, color = th.textDim)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(Equip.entries) { e -> GlassChip(e.label, equip == e, { equip = e }) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(if (more) "Hide goal & level" else "Goal: ${goal.label} · ${level.label}  ›", style = FitType.label, color = th.accentBright,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickableNoRipple { more = !more }.padding(vertical = 6.dp))
                        if (more) {
                            Spacer(Modifier.height(6.dp))
                            Text("Goal", style = FitType.label, color = th.textDim)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Goal.entries.forEach { g -> GlassChip(g.label, goal == g, { goal = g }) }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Level", style = FitType.label, color = th.textDim)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Level.entries.forEach { l -> GlassChip(l.label, level == l, { level = l }) }
                            }
                        }
                    }
                }
            }
            item {
                Glass(Modifier.fillMaxWidth().animateContentSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Duo.AutoAwesome, null, tint = th.accentBright, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ask the AI to change it", style = FitType.section, color = th.text)
                        }
                        Caption("e.g. \"no barbell\", \"more biceps\", \"knee-friendly\", \"make it 30 minutes\"")
                        Spacer(Modifier.height(10.dp))
                        NotesField(ask, { ask = it.take(200) }, "What should change?")
                        Spacer(Modifier.height(10.dp))
                        GlassButton(if (aiBusy) "Thinking…" else "Adjust with AI", {
                            if (aiBusy || ask.isBlank()) return@GlassButton
                            aiBusy = true; aiNote = null
                            scope.launch {
                                try {
                                    val base = WorkoutPlanner.Day(name, targets.joinToString(" · ") { it.label }, items.toList())
                                    val r = WorkoutAi.tune(container, base, targets.toList(), goal, equip, level, minutes, ask.trim())
                                    val newTargets = WorkoutPlanner.targetsFromText(ask)
                                    items.clear(); items.addAll(r.day.items)
                                    if (newTargets.isNotEmpty() && newTargets.toSet() != targets.toSet()) {
                                        // keep chips in sync without triggering a rule-based rebuild over the AI's answer
                                        skipRebuild = true; targets.clear(); targets.addAll(newTargets)
                                    }
                                    if (!nameEdited) name = r.day.name
                                    aiNote = r.note ?: "Updated."
                                    ask = ""
                                } catch (e: Exception) {
                                    aiNote = e.message ?: "The AI couldn't answer — your workout is unchanged."
                                } finally { aiBusy = false }
                            }
                        }, Modifier.fillMaxWidth(), icon = Duo.AutoAwesome, height = 46.dp)
                        aiNote?.let { Spacer(Modifier.height(8.dp)); Caption(it, color = th.text) }
                    }
                }
            }
            item {
                NotesField(name, { name = it.take(60); nameEdited = true }, "Name, e.g. Chest & Biceps")
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(if (shown.isEmpty()) "Exercises" else "${shown.size} exercises · $totalSets sets · ~$estMin min", Modifier.weight(1f))
                    if (targets.isNotEmpty()) GlassButton("Shuffle", { variant++ }, icon = Duo.Replay, height = 38.dp)
                }
            }
            if (targets.isEmpty()) item { Caption("Pick at least one muscle group above.", Modifier.padding(start = 6.dp)) }
            itemsIndexed(shown, key = { _, it -> it.key }) { _, it ->
                val e = ex[it.key] ?: return@itemsIndexed
                PlannedRow(it, e,
                    onSets = { s -> val i = items.indexOfFirst { x -> x.key == it.key }; if (i >= 0) items[i] = it.copy(sets = s) },
                    onSwap = {
                        val alts = WorkoutPlanner.alternatives(it.key, equip, level, items.map { x -> x.key }.toSet())
                        if (alts.isEmpty()) toaster.show("No other ${it.muscle.label.lowercase()} exercise for this equipment")
                        else {
                            val i = items.indexOfFirst { x -> x.key == it.key }
                            val next = alts.first()
                            if (i >= 0) items[i] = it.copy(key = next, main = WorkoutPlanner.isMain(next))
                        }
                    },
                    onRemove = { items.removeAll { x -> x.key == it.key } },
                    onOpen = { nav.push(Overlay.ExerciseDetail(e.id)) })
            }
            item {
                Spacer(Modifier.height(4.dp))
                AccentButton("Start now", { save(true) }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 52.dp, enabled = shown.isNotEmpty() && !saving)
                Spacer(Modifier.height(8.dp))
                GlassButton("Save to My workout days", { save(false) }, Modifier.fillMaxWidth(), icon = Duo.Save, height = 48.dp)
                Spacer(Modifier.height(8.dp))
                Caption("Weights pre-fill from your last sessions in Gym Mode. You can add, reorder or fine-tune exercises any time from My workout days → Edit.",
                    Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
private fun PlannedRow(it: Item, e: Exercise, onSets: (Int) -> Unit, onSwap: () -> Unit, onRemove: () -> Unit, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), onClick = onOpen) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExerciseImage(e, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, style = FitType.label, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Caption("${it.muscle.label}" + (if (it.main) " · main lift" else "") + " · rest ${restText(it.rest)}")
                }
                GlassIconButton(Duo.Sync, onSwap, size = 36.dp)
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Duo.Close, onRemove, size = 36.dp, tint = th.danger)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${it.sets} sets × ${repsText(it)}", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                GlassIconButton(Duo.Remove, { if (it.sets > 1) onSets(it.sets - 1) }, size = 34.dp)
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Duo.Add, { if (it.sets < 8) onSets(it.sets + 1) }, size = 34.dp)
            }
        }
    }
}

// ------------------------------------------------------------------ Plan Builder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanBuilderScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var goal by remember { mutableStateOf(PlannerPrefs.goal(ctx)) }
    var equip by remember { mutableStateOf(PlannerPrefs.equip(ctx)) }
    var level by remember { mutableStateOf(PlannerPrefs.level(ctx)) }
    var minutes by remember { mutableIntStateOf(PlannerPrefs.minutes(ctx)) }
    var days by remember { mutableIntStateOf(PlannerPrefs.days(ctx)) }
    var weeks by remember { mutableIntStateOf(8) }
    var busy by remember { mutableStateOf(false) }
    val follow by ProgramEngine.follow.collectAsState()

    val json = remember(goal, equip, level, minutes, days, weeks) {
        WorkoutPlanner.planJson(goal, days, equip, level, minutes, weeks, "pip", "my-" + System.currentTimeMillis())
    }
    val plan = remember(json) { ProgramLib.preview(json) }
    val ex = remember { mutableStateMapOf<String, Exercise>() }
    LaunchedEffect(plan.keys) {
        val missing = plan.keys.filter { it !in ex }
        if (missing.isNotEmpty()) ex.putAll(container.workoutRepo.exercisesByKeys(missing))
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Make my plan", { nav.pop() }, "A weekly plan that progresses for you")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionTitle("Your goal") }
            item {
                val wi = com.myfit.tracker.ui.components.LocalWindowInfo.current
                val cols = if (wi.width == com.myfit.tracker.ui.components.WindowInfo.Width.COMPACT) 2 else 3
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Goal.entries.chunked(cols).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { g ->
                                val sel = g == goal
                                Glass(Modifier.weight(1f).heightIn(min = 92.dp), shape = RoundedCornerShape(22.dp), onClick = { goal = g }) {
                                    if (sel) Box(Modifier.matchParentSize().clip(RoundedCornerShape(22.dp)).background(th.accent.copy(alpha = 0.32f)))
                                    Column(Modifier.padding(12.dp)) {
                                        Text(g.label, style = FitType.label, color = th.text, maxLines = 2)
                                        Spacer(Modifier.height(4.dp))
                                        Caption(g.blurb, color = if (sel) th.text else null)
                                    }
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Days per week", style = FitType.label, color = th.textDim)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (2..6).forEach { d -> GlassChip("$d", days == d, { days = d }, Modifier.weight(1f)) } }
                        Spacer(Modifier.height(12.dp))
                        Text("Time per workout", style = FitType.label, color = th.textDim)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf(30, 45, 60, 75, 90)) { m -> GlassChip("$m min", minutes == m, { minutes = m }) } }
                        Spacer(Modifier.height(12.dp))
                        Text("Equipment", style = FitType.label, color = th.textDim)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(Equip.entries) { e -> GlassChip(e.label, equip == e, { equip = e }) } }
                        Spacer(Modifier.height(12.dp))
                        Text("Experience", style = FitType.label, color = th.textDim)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Level.entries.forEach { l -> GlassChip(l.label, level == l, { level = l }) } }
                        Spacer(Modifier.height(12.dp))
                        Text("Length", style = FitType.label, color = th.textDim)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(4, 6, 8, 12).forEach { w -> GlassChip("$w weeks", weeks == w, { weeks = w }) } }
                    }
                }
            }
            item { SectionTitle("Your week · ${plan.dpw} workouts") }
            itemsIndexed(plan.days, key = { i, d -> "$i${d.name}" }) { i, d ->
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(CircleShape).background(th.accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", style = FitType.label, color = th.accentBright)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = FitType.section, color = th.text)
                                Caption("${d.focus} · ${d.items.size} exercises · ${d.items.sumOf { it.sets }} sets")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Caption(d.items.mapNotNull { ex[it.k]?.name }.joinToString(" · "))
                    }
                }
            }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("How it progresses", style = FitType.label, color = th.text)
                        plan.phases.forEach { ph ->
                            Caption((if (ph.from == ph.to) "Week ${ph.from}" else "Weeks ${ph.from}–${ph.to}") + " · ${ph.label}: ${ph.note}")
                        }
                    }
                }
            }
            item {
                AccentButton(if (busy) "Setting up…" else if (follow != null) "Switch to this plan" else "Start this plan", {
                    if (busy) return@AccentButton
                    busy = true
                    PlannerPrefs.save(ctx, goal, equip, level, minutes, days)
                    scope.launch {
                        val p = ProgramLib.saveCustom(ctx, json)
                        ProgramEngine.start(container, p)
                        toaster.show("Plan started — ${p.days.size} workout days added")
                        nav.replace(Overlay.ProgramDetail(p.id))
                    }
                }, Modifier.fillMaxWidth(), icon = Duo.Flag, height = 52.dp)
                if (follow != null) Caption("Switching keeps all your workout history.", Modifier.padding(top = 6.dp, start = 6.dp))
                Spacer(Modifier.height(6.dp))
                Caption("Each day becomes a workout you can edit. Targets update every week; weights come from your own logs.", Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

