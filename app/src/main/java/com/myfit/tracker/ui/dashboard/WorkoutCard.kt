package com.myfit.tracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassProgressBar
import com.myfit.tracker.ui.exercises.ExerciseImage
import com.myfit.tracker.ui.exercises.mmss
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WorkoutCard(w: WorkoutToday, container: AppContainer) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val now by produceState(Clock.now()) { while (true) { value = Clock.now(); delay(1000) } }

    GlassCard(onClick = { w.active?.let { nav.push(Overlay.Gym(it.workout.id)) } }) {
        CardHeader(Duo.FitnessCenter, "Today's workout", th.accentBright) {
            w.weeklyTarget?.let { Caption("${w.weeklyDone}/${Fmt.int(it)} this week") }
        }
        Spacer(Modifier.height(12.dp))
        val a = w.active
        when {
            a != null -> {
                Text("IN PROGRESS", style = FitType.overline, color = th.accentBright)
                Text(a.workout.name, style = FitType.title, color = th.text)
                Caption("${mmss((now - a.workout.startedAt) / 1000)} · ${a.exercises.size} exercises · ${a.totals.sets} sets" +
                    (a.totals.volumeKg?.let { " · ${Fmt.weight(it, u.weight, 0)}" } ?: ""))
                Spacer(Modifier.height(12.dp))
                AccentButton("Resume Gym Mode", { nav.push(Overlay.Gym(a.workout.id)) }, icon = Duo.PlayArrow, height = 48.dp)
            }
            w.done.isNotEmpty() -> {
                w.done.forEach { d ->
                    val t = d.totals
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Duo.CheckCircle, null, tint = th.success, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.workout.name, style = FitType.section, color = th.text)
                            Caption("${d.workout.endedAt?.let { mmss((it - d.workout.startedAt) / 1000) } ?: "—"} · ${t.exercises} exercises · ${t.sets} sets · ${Fmt.int(t.reps)} reps" +
                                (t.volumeKg?.let { " · ${Fmt.weight(it, u.weight, 0)}" } ?: ""))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    w.done.first().let { d -> Box(Modifier.height(40.dp)) {
                        d.exercises.take(6).forEachIndexed { i, e -> ExerciseImage(e.exercise, Modifier.offset(x = (i * 28).dp).size(40.dp).clip(CircleShape)) }
                    } }
                }
            }
            else -> {
                Text(if (w.plannedDay) "Workout day" else "Rest day on your schedule", style = FitType.title, color = th.text)
                w.next?.let { n -> Caption("Next in rotation: ${n.template.name} · ${n.items.size} exercises") }
                    ?: Caption("Create a template in Train, or start an empty workout.")
                Spacer(Modifier.height(12.dp))
                AccentButton(w.next?.let { "Start ${it.template.name}" } ?: "Start workout", {
                    scope.launch {
                        val id = w.next?.let { container.workoutRepo.startFromTemplate(it.template.id) } ?: container.workoutRepo.startEmpty()
                        nav.push(Overlay.Gym(id))
                    }
                }, icon = Duo.PlayArrow, height = 48.dp)
            }
        }
        w.weeklyTarget?.takeIf { it > 0 }?.let { t ->
            Spacer(Modifier.height(12.dp))
            GlassProgressBar((w.weeklyDone / t).toFloat(), th.accentBright)
        }
    }
}
