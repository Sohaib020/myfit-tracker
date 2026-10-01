package com.myfit.tracker.ui.dashboard

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.prefs.DashCard
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import com.myfit.tracker.ui.theme.rememberTick
import kotlin.math.roundToInt

private fun iconFor(c: DashCard): ImageVector = when (c) {
    DashCard.PIP -> Duo.AutoAwesome; DashCard.SNAP -> Duo.Camera; DashCard.WORKOUT -> Duo.FitnessCenter
    DashCard.RINGS -> Duo.TrackChanges; DashCard.NUTRITION -> Duo.ForkKnife; DashCard.BODY -> Duo.MonitorWeight
    DashCard.HYDRATION -> Duo.WaterDrop; DashCard.RECOVERY -> Duo.Bedtime; DashCard.STEPS -> Duo.DirectionsWalk
    DashCard.CHECKIN -> Duo.Mood; DashCard.GOALS -> Duo.Flag
}

private fun colorFor(c: DashCard, th: FitTheme): Color = when (c) {
    DashCard.PIP -> th.accentBright; DashCard.SNAP -> th.accent; DashCard.WORKOUT -> th.accent
    DashCard.RINGS -> th.success; DashCard.NUTRITION -> th.protein; DashCard.BODY -> th.fat
    DashCard.HYDRATION -> th.water; DashCard.RECOVERY -> th.sleep; DashCard.STEPS -> th.steps
    DashCard.CHECKIN -> th.warning; DashCard.GOALS -> th.carbs
}

/** Reorder and show/hide dashboard cards. Long-press a row and drag, or use the arrows. */
@Composable
fun ArrangeDashScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val settings = LocalSettings.current
    val tick = rememberTick()
    val order = remember { mutableStateListOf<DashCard>().apply { addAll(settings.dashOrder) } }
    val shown = remember { mutableStateListOf<DashCard>().apply { addAll(settings.dashCards) } }
    var pipOn by remember { androidx.compose.runtime.mutableStateOf(settings.pipEnabled) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 72.dp.toPx() }   // row height + spacing

    fun save() {
        container.write {
            container.settings.setDashOrder(order.toList())
            container.settings.setDashCards(shown.toSet())
            container.settings.setPip(pipOn)
        }
        toaster.show("Dashboard updated"); nav.pop()
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Arrange dashboard", { nav.pop() }, "Long-press and drag · switch cards on or off")
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), enabled = dragIdx < 0).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            order.forEachIndexed { i, c ->
                val dragging = i == dragIdx
                val on = if (c == DashCard.PIP) pipOn && c in shown else c in shown
                Glass(
                    Modifier.fillMaxWidth().height(64.dp)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragDy else 0f
                            val sc = if (dragging) 1.03f else 1f; scaleX = sc; scaleY = sc
                            shadowElevation = if (dragging) 16f else 0f
                        }
                        .pointerInput(c) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { tick(); dragIdx = order.indexOf(c); dragDy = 0f },
                                onDragEnd = { dragIdx = -1; dragDy = 0f },
                                onDragCancel = { dragIdx = -1; dragDy = 0f },
                                onDrag = { ch, d ->
                                    ch.consume()
                                    dragDy += d.y
                                    val cur = dragIdx
                                    if (cur < 0) return@detectDragGesturesAfterLongPress
                                    val steps = (dragDy / rowPx).roundToInt()
                                    val target = (cur + steps).coerceIn(0, order.lastIndex)
                                    if (target != cur) {
                                        val item = order.removeAt(cur); order.add(target, item)
                                        dragDy -= (target - cur) * rowPx
                                        dragIdx = target
                                        tick()
                                    }
                                },
                            )
                        },
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Duo.LinearScale, "Drag", tint = th.textFaint, modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = 90f })
                        Spacer(Modifier.width(10.dp))
                        IconBubble(iconFor(c), colorFor(c, th), 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.label, style = FitType.section, color = if (on) th.text else th.textDim, modifier = Modifier.weight(1f))
                        Icon(Duo.ArrowUpward, "Move up", tint = if (i > 0) th.textDim else th.textFaint.copy(alpha = 0.3f),
                            modifier = Modifier.size(34.dp).padding(6.dp).clickableNoRipple { if (i > 0) { val it0 = order.removeAt(i); order.add(i - 1, it0); tick() } })
                        Icon(Duo.ArrowDownward, "Move down", tint = if (i < order.lastIndex) th.textDim else th.textFaint.copy(alpha = 0.3f),
                            modifier = Modifier.size(34.dp).padding(6.dp).clickableNoRipple { if (i < order.lastIndex) { val it0 = order.removeAt(i); order.add(i + 1, it0); tick() } })
                        Switch(
                            on, { v ->
                                if (c == DashCard.PIP) { pipOn = v; if (v && c !in shown) shown.add(c) }
                                else if (v) shown.add(c) else shown.remove(c)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = th.accent, checkedThumbColor = th.onAccent, uncheckedTrackColor = th.textFaint.copy(alpha = 0.3f), uncheckedBorderColor = Color.Transparent),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Caption("Reset puts every card back in the default order.", Modifier.padding(horizontal = 4.dp))
            Text("Reset to default", style = FitType.label, color = th.accentBright, modifier = Modifier.padding(4.dp).clickableNoRipple {
                order.clear(); order.addAll(DashCard.entries); shown.clear(); shown.addAll(DashCard.entries); pipOn = true
            })
        }
        AccentButton("Save layout", { save() }, Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp), icon = Duo.Check)
    }
}
