package com.myfit.tracker.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LiquidGlass
import com.myfit.tracker.ui.theme.LocalBackdrop
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.drawBackdrop
import com.myfit.tracker.ui.theme.drawBaked
import com.myfit.tracker.ui.theme.realBlurSupported
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class TabItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Full-width liquid-glass dock. The dock itself shows the (blurred, refracted) page content that
 * scrolls beneath it. Press and hold anywhere, then slide: the selection turns into a clear glass
 * blob that pops out of the dock, magnifies and bends what's under it, stretches with its speed and
 * wobbles like jelly when released onto a tab.
 */
@Composable
fun LiquidTabBar(
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val th = LocalFitTheme.current
    val backdrop = LocalBackdrop.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pos = remember { Animatable(selected.toFloat()) }
    val pop = remember { Animatable(0f) }          // 0 = resting capsule, 1 = popped-out glass blob
    var holding by remember { mutableStateOf(false) }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val dockPos = remember { mutableStateOf(Offset.Zero) }
    val lensShader = remember { LiquidGlass.newShader() }

    LaunchedEffect(selected) {
        if (!holding) pos.animateTo(selected.toFloat(), spring(0.62f, 380f))
    }

    val dockTint = if (realBlurSupported) th.glassTint.copy(alpha = (th.glassTint.alpha * 1.45f + 0.06f).coerceAtMost(0.92f)) else th.glassFallback
    val inset = 5.dp

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(74.dp)
            .onGloballyPositioned { dockPos.value = it.positionInRoot() }
    ) {
        Glass(
            Modifier.fillMaxSize(), shape = RoundedCornerShape(37.dp), blur = 20.dp,
            tint = dockTint, seeContent = true, dispersion = 0.08f,
        ) {}

        BoxWithConstraints(Modifier.fillMaxSize().padding(inset)) {
            val itemW = maxWidth / items.size
            val itemWPx = with(density) { itemW.toPx() }
            val insetPx = with(density) { inset.toPx() }
            val last = items.lastIndex.toFloat()

            // ---------- lens ----------
            val p = pop.value
            val stretch = 1f + (abs(pos.velocity) * 0.05f).coerceAtMost(0.32f)
            Box(
                Modifier
                    .offset(x = itemW * pos.value)
                    .width(itemW)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val grow = 1f + 0.26f * p
                        scaleX = grow * stretch
                        scaleY = grow * (1f / stretch).coerceAtLeast(0.8f) * (1f + 0.06f * p)
                        translationY = -4.dp.toPx() * p.coerceIn(0f, 1f)
                    }
                    .shadow((6f + 14f * p.coerceIn(0f, 1f)).dp, RoundedCornerShape(30.dp), ambientColor = th.accent, spotColor = th.accent)
            ) {
                // refracted, magnified world under the blob
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val w = size.width; val h = size.height
                            val bez = minOf(w, h) * 0.42f
                            // the lens shader only runs while the blob is popped out (finger down)
                            renderEffect = if (p < 0.02f) null else LiquidGlass.effect(
                                lensShader, w, h, h / 2f, blurPx = 1.5f,
                                bezelPx = bez, strengthPx = bez * (0.35f + 0.5f * p.coerceIn(0f, 1f)),
                                dispersion = 0.06f + 0.22f * p.coerceIn(0f, 1f), highlight = 0.22f,
                            )
                            shape = RoundedCornerShape(50)
                            clip = true
                        }
                        .drawBehind {
                            val lx = dockPos.value.x + insetPx + itemWPx * pos.value
                            val ly = dockPos.value.y + insetPx
                            val mag = 1.18f + 0.22f * p.coerceIn(0f, 1f)
                            scale(mag, pivot = Offset(size.width / 2, size.height / 2)) {
                                translate(-lx, -ly) {
                                    val img = if (p < 0.02f) (backdrop.dockImg ?: backdrop.bgImg) else backdrop.bgImg
                                    val bl = if (p < 0.02f) (backdrop.dockLayer ?: backdrop.layer) else backdrop.layer
                                    if (img != null) drawBaked(img, backdrop.rootSize)
                                    else if (bl != null) drawLayer(bl)
                                    else drawBackdrop(backdrop.theme, backdrop.image, backdrop.theme.stillT, backdrop.rootSize.width, backdrop.rootSize.height)
                                }
                            }
                        }
                )
                // tint, sheen, rim
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        val q = p.coerceIn(0f, 1f)
                        val rr = CornerRadius(size.height / 2)
                        // resting: soft accent capsule; popped: nearly clear glass
                        drawRoundRect(th.accent.copy(alpha = (if (th.isLight) 0.16f else 0.26f) * (1f - 0.75f * q)), cornerRadius = rr)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f + 0.12f * q), Color.Transparent), 0f, size.height * 0.55f),
                            cornerRadius = rr,
                        )
                        // bottom inner glow (light collecting at the base of the drop)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f + 0.12f * q)), size.height * 0.6f, size.height),
                            cornerRadius = rr,
                        )
                        // chromatic rim only on the dock lens
                        drawRoundRect(
                            Brush.sweepGradient(
                                listOf(Color(0xFFFF3B6B), Color(0xFFFFD34D), Color.White, Color(0xFF52E0FF), Color(0xFF7A5CFF), Color(0xFFFF3B6B)),
                                Offset(size.width / 2, size.height / 2),
                            ),
                            cornerRadius = rr, style = Stroke((1.4f + 1.2f * q).dp.toPx()), alpha = 0.35f + 0.5f * q,
                        )
                        drawRoundRect(Color.White.copy(alpha = 0.55f), cornerRadius = rr, style = Stroke(0.8.dp.toPx()))
                    }
                )
            }

            // ---------- icons + gestures ----------
            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(items.size, itemWPx) {
                        fun target(x: Float) = (x / itemWPx - 0.5f).coerceIn(-0.12f, last + 0.12f)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            holding = true
                            var lastIdx = pos.value.roundToInt()
                            scope.launch { pop.animateTo(1f, spring(0.55f, 520f)) }
                            scope.launch { pos.animateTo(target(down.position.x), spring(0.75f, 900f)) }
                            var up = false
                            while (!up) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) { up = true; break }
                                if (ch.position != ch.previousPosition) {
                                    ch.consume()
                                    val t = target(ch.position.x)
                                    scope.launch { pos.animateTo(t, spring(0.82f, 1400f)) }
                                    val idx = t.roundToInt().coerceIn(0, items.lastIndex)
                                    if (idx != lastIdx) { lastIdx = idx; tick() }
                                }
                            }
                            holding = false
                            val i = if (up) pos.targetValue.roundToInt().coerceIn(0, items.lastIndex) else currentSelected
                            scope.launch { pos.animateTo(i.toFloat(), spring(0.5f, 360f)) }
                            // jelly wobble back into the dock
                            scope.launch { pop.animateTo(0f, spring(0.28f, 240f)) }
                            if (up && i != currentSelected) { tick(); currentOnSelect(i) }
                        }
                    }
            ) {
                items.forEachIndexed { i, item ->
                    val closeness = (1f - abs(pos.value - i)).coerceIn(0f, 1f)
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val tint = lerpColor(th.text.copy(alpha = 0.86f), if (th.isLight) th.accent else Color.White, closeness)
                        val lift = pop.value.coerceIn(0f, 1f) * closeness
                        Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(26.dp).graphicsLayer {
                            val s = 1f + 0.12f * closeness + 0.22f * lift
                            scaleX = s; scaleY = s
                            translationY = -3.dp.toPx() * lift
                        })
                        Text(
                            item.label,
                            style = FitType.caption.copy(fontSize = 11.sp, letterSpacing = 0.sp),
                            color = tint, maxLines = 1, softWrap = false,
                            modifier = Modifier.graphicsLayer { translationY = -2.dp.toPx() * lift },
                        )
                    }
                }
            }
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
)
