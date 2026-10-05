package com.myfit.tracker.ui.programs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

/** One program: cover, what it is, how the weeks progress, every day's exercises, and Follow / Start / Stop. */
@Composable
fun ProgramDetailScreen(container: AppContainer, id: String) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val p = remember(id) { ProgramLib.byId(ctx, id) }
    if (p == null) { Column { OverlayTopBar("Program", { nav.pop() }); Caption("Program not found.", Modifier.padding(16.dp)) }; return }
    val follow by ProgramEngine.follow.collectAsState()
    val mine = follow?.id == p.id
    val done by remember(follow) { ProgramEngine.sessionsDone(container, follow?.takeIf { it.id == p.id }) }.collectAsState(0)
    val pos = ProgramEngine.position(p, done)
    val activeW by container.workoutRepo.inProgress.collectAsState(null)
    val ex by produceState<Map<String, Exercise>>(emptyMap(), p.id) { value = container.workoutRepo.exercisesByKeys(p.keys) }
    var viewWeek by remember(mine, pos.week) { mutableIntStateOf(if (mine) pos.week.coerceAtMost(p.weeks) else 1) }
    var confirmStop by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun startDay(day: Int) {
        val f = follow ?: return
        if (activeW != null) { toaster.show("Finish or discard your current workout first"); nav.push(Overlay.Gym(activeW!!.id)); return }
        scope.launch { nav.push(Overlay.Gym(ProgramEngine.startSession(container, p, f, pos, day))) }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(p.name, { nav.pop() }, p.tag)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ProgramCover(p, 200.dp, corner = 26.dp) }
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.desc, style = FitType.body, color = th.text)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Stat("${p.weeks}", "weeks", Modifier.weight(1f)); Stat("${p.dpw}", "days / week", Modifier.weight(1f))
                            Stat("~${p.mins}", "min", Modifier.weight(1f)); Stat(label(LEVELS, p.level), "level", Modifier.weight(1.3f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Caption("Goals: " + p.goals.joinToString(", ") { label(GOALS, it) } + " · Equipment: " + label(EQUIP, p.equip) +
                            (if (p.gender != "all") " · Designed for " + label(GENDERS, p.gender).lowercase() else ""))
                        Spacer(Modifier.height(4.dp))
                        Caption("Coach: ${p.coach.label}")
                    }
                }
            }
            item {
                if (!mine) {
                    AccentButton(if (follow != null) "Switch to this program" else "Start this program", {
                        if (busy) return@AccentButton
                        busy = true
                        scope.launch {
                            ProgramEngine.start(container, p)
                            toaster.show("Following ${p.name} — ${p.days.size} templates added to Workouts")
                            busy = false
                        }
                    }, Modifier.fillMaxWidth(), icon = Duo.Flag, height = 52.dp)
                    if (follow != null) Caption("You're following another program. Switching keeps all your workout history.", Modifier.padding(top = 6.dp, start = 6.dp))
                    if (ProgramLib.isCustom(p.id)) Text("Delete this plan", style = FitType.label, color = th.danger,
                        modifier = Modifier.padding(top = 4.dp).clickableNoRipple { ProgramLib.deleteCustom(ctx, p.id); toaster.show("Plan deleted"); nav.pop() }.padding(8.dp))
                } else Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (pos.finished) "Program complete!" else "Week ${pos.week} · ${p.phase(pos.week).label}", style = FitType.section, color = th.text)
                        Caption(if (pos.finished) "You finished all ${p.sessions} sessions. Run it again or pick a new challenge." else "Session ${done + 1} of ${p.sessions}")
                        Spacer(Modifier.height(10.dp))
                        com.myfit.tracker.ui.components.GlassProgressBar((done.toFloat() / p.sessions).coerceIn(0f, 1f), th.accentBright)
                        Spacer(Modifier.height(12.dp))
                        if (!pos.finished) AccentButton("Start ${p.days[pos.day].name}", { startDay(pos.day) }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 48.dp)
                        else AccentButton("Restart program", { scope.launch { ProgramEngine.start(container, p); toaster.show("Restarted ${p.name}") } }, Modifier.fillMaxWidth(), icon = Duo.Replay, height = 48.dp)
                        Spacer(Modifier.height(8.dp))
                        if (!confirmStop) Text("Stop program", style = FitType.label, color = th.danger, modifier = Modifier.align(Alignment.CenterHorizontally).clickableNoRipple { confirmStop = true }.padding(8.dp))
                        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassButton("Keep going", { confirmStop = false }, Modifier.weight(1f), height = 42.dp)
                            GlassButton("Stop", { scope.launch { ProgramEngine.stop(container); confirmStop = false; toaster.show("Stopped ${p.name}") } }, Modifier.weight(1f), height = 42.dp)
                        }
                    }
                }
            }
            item {
                SectionTitle("How it progresses")
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        p.phases.forEach { ph ->
                            val now = mine && pos.week in ph.from..ph.to
                            Row {
                                Box(Modifier.padding(top = 3.dp).size(10.dp).clip(CircleShape).background(if (now) th.accentBright else th.textFaint))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text((if (ph.from == ph.to) "Week ${ph.from}" else "Weeks ${ph.from}–${ph.to}") + " · " + ph.label + if (now) "  (you are here)" else "",
                                        style = FitType.label, color = th.text)
                                    if (ph.note.isNotBlank()) Caption(ph.note)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Week $viewWeek workouts", Modifier.weight(1f))
                    WeekStepper(viewWeek, p.weeks) { viewWeek = it }
                }
            }
            itemsIndexed(p.days, key = { i, _ -> "d$i" }) { i, day ->
                DayCard(p, day, i, viewWeek, ex,
                    isNext = mine && !pos.finished && i == pos.day && viewWeek == pos.week,
                    onStart = if (mine && !pos.finished) ({ startDay(i) }) else null,
                    onExercise = { e -> nav.push(Overlay.ExerciseDetail(e.id)) })
            }
            item { Caption("Weights are yours to choose — Gym Mode pre-fills from your last sessions. Targets update automatically each week.", Modifier.padding(horizontal = 6.dp)) }
        }
    }
}

@Composable
private fun Stat(v: String, l: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Text(v, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(l, style = FitType.caption, color = th.textDim, maxLines = 1)
    }
}

@Composable
private fun WeekStepper(week: Int, max: Int, onChange: (Int) -> Unit) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(th.text.copy(alpha = 0.07f)).clickableNoRipple { if (week > 1) onChange(week - 1) }, contentAlignment = Alignment.Center) {
            Icon(Duo.KeyboardArrowLeft, "Previous week", tint = if (week > 1) th.text else th.textFaint)
        }
        Text("$week / $max", style = FitType.label, color = th.text, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(th.text.copy(alpha = 0.07f)).clickableNoRipple { if (week < max) onChange(week + 1) }, contentAlignment = Alignment.Center) {
            Icon(Duo.KeyboardArrowRight, "Next week", tint = if (week < max) th.text else th.textFaint)
        }
    }
}

@Composable
private fun DayCard(p: Program, day: PDay, index: Int, week: Int, ex: Map<String, Exercise>, isNext: Boolean, onStart: (() -> Unit)?, onExercise: (Exercise) -> Unit) {
    val th = LocalFitTheme.current
    var open by remember { mutableStateOf(isNext) }
    val ph = p.phase(week)
    Glass(Modifier.fillMaxWidth().animateContentSize(), onClick = { open = !open }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(if (isNext) th.accent else th.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = FitType.section, color = if (isNext) th.onAccent else th.text)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(day.name + if (isNext) " · up next" else "", style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Caption(day.focus + " · ${day.items.size} exercises · ${day.items.sumOf { it.target(ph).sets }} sets")
                }
                Icon(if (open) Duo.KeyboardArrowDown else Duo.KeyboardArrowRight, null, tint = th.textDim)
            }
            if (open) {
                Spacer(Modifier.height(10.dp))
                day.items.forEach { it ->
                    val e = ex[it.k]
                    val t = it.target(ph)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickableNoRipple { e?.let(onExercise) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (e != null) ExerciseImage(e, Modifier.size(44.dp).clip(CircleShape))
                        else Box(Modifier.size(44.dp).clip(CircleShape).background(th.text.copy(alpha = 0.08f)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e?.name ?: it.k.replace('_', ' '), style = FitType.label, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Caption(t.text + (if (t.rest > 0) " · rest ${t.rest}s" else "") + if (it.main) " · main lift" else "")
                        }
                        Icon(Duo.KeyboardArrowRight, null, tint = th.textFaint, modifier = Modifier.size(18.dp))
                    }
                }
                if (onStart != null) {
                    Spacer(Modifier.height(8.dp))
                    if (isNext) AccentButton("Start this workout", onStart, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 44.dp)
                    else GlassButton("Do this one today instead", onStart, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 42.dp)
                }
            }
        }
    }
}
