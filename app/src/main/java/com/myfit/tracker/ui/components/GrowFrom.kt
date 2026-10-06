package com.myfit.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.myfit.tracker.ui.theme.OpenOrigin

/**
 * iOS-style "app open". One rectangle R(t) animates from the tapped card to the full screen; the new screen is
 * scaled uniformly so its width always equals R's width, placed at R's top-left, and clipped to R's height with
 * rounded corners — so the window and its content move together (no half-scaled frames).
 */
@Composable
fun GrowFrom(content: @Composable BoxScope.() -> Unit) {
    val origin = remember { OpenOrigin.take() }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 420f)) }
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) { 26.dp.toPx() }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val t = p.value.coerceIn(0f, 1f)
            val w = size.width; val h = size.height
            if (w <= 0f || h <= 0f) return@graphicsLayer
            val from = origin ?: Rect(w * 0.08f, h * 0.18f, w * 0.92f, h * 0.82f)
            val r = Rect(lerp(from.left, 0f, t), lerp(from.top, 0f, t), lerp(from.right, w, t), lerp(from.bottom, h, t))
            val s = (r.width / w).coerceIn(0.05f, 1f)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = s; scaleY = s
            translationX = r.left; translationY = r.top
            alpha = if (origin == null) (t * 2.5f).coerceIn(0f, 1f) else (t * 6f).coerceIn(0f, 1f)
            val visibleH = (r.height / s).coerceAtMost(h)
            shape = ClipShape(Rect(0f, 0f, w, visibleH), corner / s * (1f - t))
            clip = t < 0.999f
        }.blockTouchesBelow(),
        content = content,
    )
}

private class ClipShape(private val r: Rect, private val c: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(r, CornerRadius(c)))
}
