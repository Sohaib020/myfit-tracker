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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.myfit.tracker.ui.theme.OpenOrigin

/**
 * iOS-style "app open": the screen grows out of the card that was tapped — its window expands from the
 * card's rounded rectangle to the full screen while the content scales up from the card's size.
 * Runs once when the screen first appears. Without a recent tap it rises gently from the centre.
 */
@Composable
fun GrowFrom(content: @Composable BoxScope.() -> Unit) {
    val origin = remember { OpenOrigin.take() }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 340f)) }
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val t = p.value
            val o = origin
            if (o != null && size.width > 0f) {
                // content starts at the card's scale, centred on the card, and grows to full screen
                val s0 = (o.width / size.width).coerceIn(0.2f, 1f)
                val sc = lerp(s0, 1f, t.coerceIn(0f, 1f))
                scaleX = sc; scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                    (o.center.x / size.width).coerceIn(0f, 1f), (o.center.y / size.height).coerceIn(0f, 1f),
                )
            } else {
                val sc = lerp(0.94f, 1f, t.coerceIn(0f, 1f)); scaleX = sc; scaleY = sc
            }
            alpha = (t * 3f).coerceIn(0f, 1f)
            // window: card rect → full screen (in the layer's own, unscaled coordinates)
            val tt = t.coerceIn(0f, 1f)
            val from = o?.let { r ->
                // undo the content scale so the window lines up with the card on screen
                val s0 = scaleX.coerceAtLeast(0.01f)
                val ox = transformOrigin.pivotFractionX * size.width; val oy = transformOrigin.pivotFractionY * size.height
                Rect(ox + (r.left - ox) / s0, oy + (r.top - oy) / s0, ox + (r.right - ox) / s0, oy + (r.bottom - oy) / s0)
            } ?: Rect(0f, 0f, size.width, size.height)
            val rect = Rect(lerp(from.left, 0f, tt), lerp(from.top, 0f, tt), lerp(from.right, size.width, tt), lerp(from.bottom, size.height, tt))
            val c = lerp(corner / scaleX.coerceAtLeast(0.2f), 0f, tt)
            this.shape = RectShape(rect, c)
            clip = tt < 0.999f
        },
        content = content,
    )
}

private class RectShape(private val r: Rect, private val c: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(r, CornerRadius(c)))
}
