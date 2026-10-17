package com.myfit.tracker.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.domain.FormGuide
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import kotlinx.coroutines.launch
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme

/** The trainer's home: who they are, what the camera can watch, how a coached set works, and one button to start. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachHub(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    remember { Coach.load(ctx); 0 }
    val cp by Coach.prefs.collectAsState()
    val active by container.workoutRepo.inProgress.collectAsState(initial = null)
    // R21: the coach waves hello, then settles into a relaxed idle
    var heroPose by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("wave") }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(3400); heroPose = "idle" }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(cp.name, { nav.pop() }, "Your personal trainer")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                com.myfit.tracker.ui.theme.Glass(Modifier.fillMaxWidth().height(260.dp), shape = RoundedCornerShape(30.dp), tint = th.accent.copy(alpha = 0.16f)) {
                    Row(Modifier.fillMaxSize()) {
                        CoachStage(cp.look.id, heroPose, Modifier.weight(1f).fillMaxHeight().padding(top = 10.dp), container.pipVoice.level)
                        Column(Modifier.weight(1.1f).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.Center) {
                            Text("Hey, I'm ${cp.name}!", style = FitType.title, color = th.text)
                            Spacer(Modifier.height(6.dp))
                            Caption("I explain every exercise, count you in, pace or count your reps, fix your form and push you through the last ones.", color = th.textDim)
                            Spacer(Modifier.height(12.dp))
                            AccentButton(if (active != null) "Coach my workout" else "Pick a workout", {
                                val a = active
                                if (a != null) nav.replace(Overlay.Gym(a.id)) else nav.replace(Overlay.DayBuilder())
                            }, icon = Duo.PlayArrow, height = 46.dp)
                        }
                    }
                }
            }
            item {
                GlassCard {
                    Text("HOW A COACHED SET WORKS", style = FitType.overline, color = th.textDim)
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        Triple(Duo.Info, "Setup & angles", "Where to stand, what to brace and the joint angles to hit."),
                        Triple(Duo.Camera, "Camera check", "Prop your phone 2–3 m away — I check you're in frame and at the right angle."),
                        Triple(Duo.Timer, "Count-in & reps", "3-2-1, then I pace every rep or count it from the camera."),
                        Triple(Duo.EmojiEvents, "Score & rest", "Each rep gets a form score; I save the set and coach your rest."),
                    ).forEachIndexed { i, (ic, t, d) ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(th.accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text("${i + 1}", style = FitType.label, color = th.accentBright) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(t, style = FitType.label, color = th.text); Caption(d) }
                        }
                    }
                }
            }
            item { CameraExercises(container) }
            item { ProTeamCard(container) }
            item { GlassButton("Open ${cp.nName} — nutrition coach", { nav.replace(Overlay.Nutritionist) }, Modifier.fillMaxWidth(), icon = Duo.ForkKnife, height = 46.dp) }
        }
    }
}


/** Camera-coach groups: which movement patterns the camera follows, by body area. */
private val CAM_GROUPS = listOf(
    "Legs & glutes" to listOf(FormGuide.Pattern.SQUAT, FormGuide.Pattern.LUNGE, FormGuide.Pattern.HINGE, FormGuide.Pattern.GLUTE_BRIDGE, FormGuide.Pattern.CALF_RAISE, FormGuide.Pattern.WALL_SIT),
    "Push & arms" to listOf(FormGuide.Pattern.PUSHUP, FormGuide.Pattern.OVERHEAD, FormGuide.Pattern.CURL, FormGuide.Pattern.TRICEP, FormGuide.Pattern.RAISE, FormGuide.Pattern.ROW),
    "Core" to listOf(FormGuide.Pattern.SITUP, FormGuide.Pattern.PLANK, FormGuide.Pattern.SIDE_PLANK, FormGuide.Pattern.MOUNTAIN_CLIMBER),
    "Cardio" to listOf(FormGuide.Pattern.JUMPING_JACK, FormGuide.Pattern.HIGH_KNEES, FormGuide.Pattern.BURPEE),
)

/**
 * R26: the exercises the camera can coach, shown on their own and organised by body area → movement → exercises.
 * Tap an exercise to start a camera-coached workout with it (or add it to the one you're doing).
 */
@Composable
private fun CameraExercises(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val all by container.exerciseRepo.active.collectAsState(initial = emptyList())
    val active by container.workoutRepo.inProgress.collectAsState(initial = null)
    // pattern -> camera-ready exercises (classified once per catalogue change)
    val byPattern = remember(all) {
        all.groupBy { FormGuide.of(it.name, it.primaryMuscle, it.measurementType) }
            .filterKeys { FormGuide.cameraCounts(it) || FormGuide.cameraHolds(it) }
            .mapValues { (_, v) -> v.sortedWith(compareBy({ it.isCustom }, { it.name.length }, { it.name })) }
    }
    var group by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<FormGuide.Pattern?>(null) }
    val label = remember { FormGuide.cameraList.toMap() }
    com.myfit.tracker.ui.theme.Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.myfit.tracker.ui.components.IconBubble(Duo.Camera, th.accentBright, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Camera coach exercises", style = FitType.section, color = th.text)
                    Caption("${byPattern.values.sumOf { it.size }} exercises I can watch — reps counted, form scored, holds timed")
                }
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CAM_GROUPS.size) { i -> GlassChip(CAM_GROUPS[i].first, group == i, { group = i; open = null }) }
            }
            Spacer(Modifier.height(10.dp))
            CAM_GROUPS[group].second.forEach { pat ->
                val list = byPattern[pat].orEmpty()
                if (list.isEmpty()) return@forEach
                val expanded = open == pat
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(th.text.copy(alpha = 0.05f))) {
                    Row(Modifier.fillMaxWidth().clickableNoRipple { open = if (expanded) null else pat }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label[pat] ?: pat.name, style = FitType.label, color = th.text)
                            Caption(if (FormGuide.cameraCounts(pat)) "Counts reps · scores form · ${list.size} exercises" else "Times your hold · checks form · ${list.size} exercises")
                        }
                        androidx.compose.material3.Icon(Duo.KeyboardArrowDown, null, tint = th.textDim, modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f })
                    }
                    if (expanded) list.take(12).forEach { e ->
                        Row(Modifier.fillMaxWidth().clickableNoRipple {
                            scope.launch {
                                val w = active?.id ?: container.workoutRepo.startEmpty(e.name)
                                container.workoutRepo.addExercise(w, e.id)
                                nav.replace(Overlay.Gym(w))
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.myfit.tracker.ui.exercises.ExerciseImage(e, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = FitType.body, color = th.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Caption(FormGuide.requiredView(pat).let { v -> "Camera: " + v.name.lowercase().replace('_', ' ') + " view" })
                            }
                            androidx.compose.material3.Icon(Duo.PlayArrow, null, tint = th.accentBright, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Caption("Everything else is coached with voice, tempo and timing. Camera frames never leave your phone.", color = th.textFaint)
        }
    }
}
