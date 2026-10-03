package com.myfit.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.roundToInt

/** Screen positions of things Pip's tour points at (registered with [tourTarget]). */
object TourTargets {
    val rects = mutableStateMapOf<String, Rect>()
}

fun Modifier.tourTarget(id: String): Modifier = onGloballyPositioned { TourTargets.rects[id] = it.boundsInRoot() }

data class TourStep(val target: String?, val title: String, val text: String, val mood: PipMood = PipMood.HAPPY, val before: (() -> Unit)? = null)

/** Rect for a target; "tab:N" is the N-th of 5 equal slots in the dock. */
private fun rectFor(id: String?): Rect? {
    if (id == null) return null
    if (id.startsWith("tab:")) {
        val d = TourTargets.rects["dock"] ?: return null
        val i = id.removePrefix("tab:").toIntOrNull() ?: return null
        val w = d.width / 5f
        return Rect(d.left + w * i, d.top, d.left + w * (i + 1), d.bottom)
    }
    return TourTargets.rects[id]
}

/**
 * Pip's first-run tour: the screen dims, a soft spotlight frames one real control at a time,
 * and Pip explains it in a speech bubble. Tap anywhere or "Next" to continue; "Skip" ends it.
 */
@Composable
fun PipTour(steps: List<TourStep>, onDone: () -> Unit) {
    val th = LocalFitTheme.current
    val density = LocalDensity.current
    var i by remember { mutableIntStateOf(0) }
    val step = steps[i]
    LaunchedEffect(i) { step.before?.invoke() }
    val target = rectFor(step.target)
    // animate the spotlight between targets
    val anim = remember { Animatable(0f) }
    var from by remember { mutableStateOf<Rect?>(null) }
    var to by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(i, target) {
        from = currentSpot(from, to, anim.value); to = target
        anim.snapTo(0f); anim.animateTo(1f, spring(0.85f, 260f))
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(300)) }
    var box by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val pad = with(density) { 8.dp.toPx() }
    fun next() { if (i < steps.lastIndex) i++ else onDone() }

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(i) { detectTapGestures(onTap = { next() }) }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = appear.value }) {
            drawRect(Color.Black.copy(alpha = 0.66f))
            val spot = currentSpot(from, to, anim.value)
            if (spot != null) {
                val r = Rect(spot.left - pad, spot.top - pad, spot.right + pad, spot.bottom + pad)
                val cr = CornerRadius(minOf(r.height / 2f, 30.dp.toPx()))
                drawRoundRect(Color.Black, r.topLeft, r.size, cr, blendMode = BlendMode.Clear)
                drawRoundRect(Color.White.copy(alpha = 0.85f), r.topLeft, r.size, cr, style = Stroke(2.dp.toPx()))
            }
        }
        // Pip + bubble: below the target if it's in the top half, otherwise above it; centred when there's no target
        val bubbleW = with(density) { 300.dp.toPx() }
        val h = box.height.toFloat()
        val w = box.width.toFloat()
        var card by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        val y = when {
            target == null -> (h - card.height) / 2f
            target.center.y < h / 2f -> target.bottom + pad * 3
            else -> target.top - pad * 3 - card.height
        }.coerceIn(with(density) { 40.dp.toPx() }, (h - card.height - with(density) { 24.dp.toPx() }).coerceAtLeast(0f))
        val x = ((target?.center?.x ?: (w / 2f)) - minOf(bubbleW, w - 32f) / 2f).coerceIn(with(density) { 16.dp.toPx() }, (w - minOf(bubbleW, w - 32f) - with(density) { 16.dp.toPx() }).coerceAtLeast(0f))
        Column(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.width(300.dp).onSizeChanged { card = it }
                .graphicsLayer { alpha = appear.value },
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Pip(step.mood, size = 84.dp, interactive = false)
                Spacer(Modifier.width(6.dp))
                Glass(Modifier.weight(1f), shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp), tint = if (th.isLight) Color.White.copy(alpha = 0.92f) else Color(0xEE1E2026)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(step.title, style = FitType.section, color = th.text)
                        Spacer(Modifier.height(4.dp))
                        Text(step.text, style = FitType.caption, color = th.textDim)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Skip tour", style = FitType.label, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.clickableNoRipple(onDone).padding(8.dp))
                Spacer(Modifier.weight(1f))
                Text("${i + 1}/${steps.size}", style = FitType.caption, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.width(10.dp))
                AccentButton(if (i == steps.lastIndex) "Let's go" else "Next", { next() }, height = 42.dp)
            }
        }
    }
}

private fun currentSpot(a: Rect?, b: Rect?, t: Float): Rect? = when {
    b == null -> null
    a == null -> b
    else -> Rect(a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t, a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t)
}

