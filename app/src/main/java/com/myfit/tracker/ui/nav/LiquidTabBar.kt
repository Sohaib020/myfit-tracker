package com.myfit.tracker.ui.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalBackdrop
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.drawBackdrop
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class TabItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Liquid glass tab bar: a refractive lens (magnified backdrop + chromatic rim) springs between
 * tabs, stretches with its own velocity, and can be dragged with a finger like iOS 26/27.
 */
@Composable
fun LiquidTabBar(
    items: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val th = LocalFitTheme.current
    val backdrop = LocalBackdrop.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(selected.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (dragging) 1.12f else 1f, spring(0.5f, 400f), label = "lensGrow")

    LaunchedEffect(selected) {
        if (!dragging) pos.animateTo(selected.toFloat(), spring(0.62f, 380f))
    }

    Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Glass(Modifier.weight(1f).height(68.dp), shape = RoundedCornerShape(34.dp), blur = 22.dp) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(5.dp)) {
                val itemW = maxWidth / items.size
                val itemWPx = with(LocalDensity.current) { itemW.toPx() }
                val stretch = 1f + (abs(pos.velocity) * 0.06f).coerceAtMost(0.35f)
                val lensPos = remember { mutableStateOf(Offset.Zero) }

                // Lens
                Box(
                    Modifier
                        .offset(x = itemW * pos.value)
                        .width(itemW)
                        .fillMaxHeight()
                        .graphicsLayer { scaleX = stretch * grow; scaleY = (2f - stretch) * grow }
                        .onGloballyPositioned { lensPos.value = it.positionInRoot() }
                        .shadow(10.dp, CircleShape, ambientColor = th.accent, spotColor = th.accent)
                        .clip(RoundedCornerShape(30.dp))
                        .drawBehind {
                            val p = lensPos.value
                            // refraction: the world behind the lens, magnified
                            scale(1.35f, pivot = Offset(size.width / 2, size.height / 2)) {
                                translate(-p.x, -p.y) {
                                    drawBackdrop(backdrop.theme, backdrop.image, backdrop.time.floatValue, backdrop.rootSize.width, backdrop.rootSize.height)
                                }
                            }
                            drawRect(th.accent.copy(alpha = if (th.isLight) 0.12f else 0.22f))
                            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.Transparent), 0f, size.height * 0.6f))
                            // chromatic rim like Apple's liquid glass
                            val rr = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                            drawRoundRect(
                                Brush.sweepGradient(
                                    listOf(Color(0xFFFF3B6B), Color(0xFFFFD34D), Color.White, Color(0xFF7A5CFF), Color(0xFFFF3B6B)),
                                    Offset(size.width / 2, size.height / 2),
                                ),
                                cornerRadius = rr, style = Stroke(2.2.dp.toPx()), alpha = 0.8f,
                            )
                            drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = rr, style = Stroke(0.8.dp.toPx()))
                        }
                )

                Row(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(items.size, itemWPx) {
                            detectTapGestures { o ->
                                val i = (o.x / itemWPx).toInt().coerceIn(0, items.lastIndex)
                                tick(); onSelect(i)
                            }
                        }
                        .pointerInput(items.size, itemWPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { o ->
                                    dragging = true
                                    scope.launch { pos.animateTo((o.x / itemWPx - 0.5f).coerceIn(0f, items.lastIndex.toFloat()), spring(0.7f, 800f)) }
                                },
                                onDragEnd = {
                                    val i = pos.value.roundToInt().coerceIn(0, items.lastIndex)
                                    dragging = false
                                    scope.launch { pos.animateTo(i.toFloat(), spring(0.55f, 380f)) }
                                    if (i != selected) { tick(); onSelect(i) }
                                },
                                onDragCancel = {
                                    dragging = false
                                    scope.launch { pos.animateTo(selected.toFloat(), spring(0.6f, 380f)) }
                                },
                            ) { change, dx ->
                                change.consume()
                                val next = (pos.value + dx / itemWPx).coerceIn(-0.15f, items.lastIndex + 0.15f)
                                scope.launch { pos.snapTo(next) }
                            }
                        }
                ) {
                    items.forEachIndexed { i, item ->
                        val closeness = (1f - abs(pos.value - i)).coerceIn(0f, 1f)
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            val tint = lerpColor(th.textDim, if (th.isLight) th.accent else Color.White, closeness)
                            Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer {
                                val s = 1f + 0.14f * closeness
                                scaleX = s; scaleY = s
                            })
                            Text(item.label, style = FitType.caption.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = tint, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        Box(Modifier.width(12.dp))
        // Quick add orb
        val orbScale by animateFloatAsState(1f, label = "orb")
        Box(
            Modifier
                .size(62.dp)
                .graphicsLayer { scaleX = orbScale; scaleY = orbScale }
                .shadow(18.dp, CircleShape, ambientColor = th.accent, spotColor = th.accent)
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(Brush.verticalGradient(listOf(th.accentBright, th.accent)))
                    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), 0f, size.height * 0.55f))
                    drawCircle(Color.White.copy(alpha = 0.55f), style = Stroke(1.2.dp.toPx()))
                }
                .pointerInput(Unit) { detectTapGestures { tick(); onQuickAdd() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, "Quick add", tint = th.onAccent, modifier = Modifier.size(30.dp))
        }
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
)
