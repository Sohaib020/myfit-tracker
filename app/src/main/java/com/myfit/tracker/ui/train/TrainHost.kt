package com.myfit.tracker.ui.train

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.TopBarSpace
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.fadeTopEdge
import com.myfit.tracker.ui.exercises.ExercisesScreen
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme

/** Train tab: workouts (templates, Gym Mode, history) and the exercise library, behind one segmented switch. */
@Composable
fun TrainHost(container: AppContainer, bottomPad: Int) {
    val th = LocalFitTheme.current
    var seg by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = TopBarSpace, bottom = 6.dp)) {
            Text("Train", style = FitType.display, color = th.text)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Segmented(listOf("Workouts", "Exercises"), seg) { seg = it }
        }
        Box(Modifier.fillMaxWidth().weight(1f).fadeTopEdge()) {
            if (seg == 0) TrainScreen(container, bottomPad, embedded = true)
            else Box(Modifier.padding(horizontal = 0.dp)) { ExercisesScreen(container, bottomPad, embedded = true) }
        }
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth().height(44.dp), shape = CircleShape) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val w = maxWidth / labels.size
            val x by animateDpAsState(w * selected, spring(0.8f, 500f), label = "seg")
            Box(
                Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape)
                    .drawBehind { drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent))) },
            )
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { i, l ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickableNoRipple { onSelect(i) }, contentAlignment = Alignment.Center) {
                        Text(l, style = FitType.label, color = if (i == selected) th.onAccent else th.textDim)
                    }
                }
            }
        }
    }
}
