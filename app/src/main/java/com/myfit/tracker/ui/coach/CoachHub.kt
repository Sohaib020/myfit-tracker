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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.domain.FormGuide
import com.myfit.tracker.ui.components.Caption
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
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(cp.name, { nav.pop() }, "Your personal trainer")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(th.accent.copy(alpha = 0.3f), th.accent.copy(alpha = 0.04f))))) {
                    Row(Modifier.fillMaxSize()) {
                        CoachFigure(cp.look.id, "stand", Modifier.weight(1f).fillMaxHeight().padding(top = 10.dp), container.pipVoice.level)
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
            item {
                SectionTitle("What the camera can watch")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormGuide.cameraList.forEach { (_, label) -> GlassChip(label, false, {}) }
                }
                Spacer(Modifier.height(6.dp))
                Caption("Everything else is coached with voice, tempo and timing. Camera frames never leave your phone.", color = th.textFaint)
            }
            item { ProTeamCard(container) }
            item { GlassButton("Open ${cp.nName} — nutrition coach", { nav.replace(Overlay.Nutritionist) }, Modifier.fillMaxWidth(), icon = Duo.ForkKnife, height = 46.dp) }
        }
    }
}
