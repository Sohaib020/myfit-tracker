package com.myfit.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalCardAccent
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.PI
import kotlin.math.sin

/*
 * Neon glass bento — the R17 tile language for Train and Progress.
 * A tile is a glass card with: a slow-orbiting light along its border (sweep gradient in the tile's colour),
 * a breathing glow in one corner, and a gradient "orb" icon that floats. Everything is drawn (no images),
 * costs a single infinite transition per tile, and stops when the tile leaves the screen.
 */

/** A pair of colours for a tile: [a] the bright neon, [b] its deeper partner (orb bottom, glow edge). */
data class Neon(val a: Color, val b: Color) {
    companion object {
        fun of(c: Color) = Neon(lerp(c, Color.White, 0.18f), lerp(c, Color(0xFF1A1033), 0.35f))
        val Lime = Neon(Color(0xFFC6FF3D), Color(0xFF3FAF2A))
        val Crimson = Neon(Color(0xFFFF4D6A), Color(0xFFB0123A))
        val Cyan = Neon(Color(0xFF4DE3FF), Color(0xFF1A6DFF))
        val Violet = Neon(Color(0xFFB98CFF), Color(0xFF5B2DDB))
        val Amber = Neon(Color(0xFFFFC94D), Color(0xFFE2670E))
        val Mint = Neon(Color(0xFF5CFFC2), Color(0xFF0FA37A))
        val Pink = Neon(Color(0xFFFF7AD9), Color(0xFFB0249B))
        val Blue = Neon(Color(0xFF6EA8FF), Color(0xFF2A44D6))
    }
}

@Composable
fun NeonTile(
    neon: Neon,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: Dp = 26.dp,
    glowAt: Alignment = Alignment.TopEnd,
    phase: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = rememberInfiniteTransition(label = "neon")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "spin")
    val breath by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    Glass(modifier, shape = RoundedCornerShape(corner), onClick = onClick, pressScale = 0.95f) {
        val holder = LocalCardAccent.current
        SideEffect { if (holder != null && holder.value != neon.a) holder.value = neon.a }
        // corner glow
        Box(Modifier.matchParentSize().drawBehind {
            val c = when (glowAt) {
                Alignment.TopStart -> Offset(0f, 0f); Alignment.BottomStart -> Offset(0f, size.height)
                Alignment.BottomEnd -> Offset(size.width, size.height); else -> Offset(size.width, 0f)
            }
            val r = size.maxDimension * (0.62f + 0.08f * breath)
            drawCircle(Brush.radialGradient(listOf(neon.a.copy(alpha = 0.26f + 0.08f * breath), neon.b.copy(alpha = 0.06f), Color.Transparent), c, r), r, c)
        })
        // orbiting border light: a sweep gradient whose shader matrix turns, so the light runs round the rounded edge
        Box(Modifier.matchParentSize().drawWithContent {
            drawContent()
            val cr = corner.toPx()
            val sw = 1.4.dp.toPx()
            val deg = (spin + phase * 360f) % 360f
            val sh = android.graphics.SweepGradient(center.x, center.y,
                intArrayOf(0, neon.a.copy(alpha = 0.95f).toArgbInt(), 0, 0, neon.b.copy(alpha = 0.6f).toArgbInt(), 0),
                floatArrayOf(0f, 0.1f, 0.22f, 0.6f, 0.7f, 0.82f))
            sh.setLocalMatrix(android.graphics.Matrix().apply { setRotate(deg, center.x, center.y) })
            drawRoundRect(androidx.compose.ui.graphics.ShaderBrush(sh), Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), CornerRadius(cr, cr), style = Stroke(sw))
        })
        content()
    }
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

/** Gradient orb icon (the "web3" look): glossy sphere, inner highlight, soft halo, gentle float. */
@Composable
fun IconOrb(icon: ImageVector, neon: Neon, size: Dp = 46.dp, float: Boolean = true, phase: Float = 0f) {
    val t = rememberInfiniteTransition(label = "orb")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "bob")
    val dy = if (float) sin((bob + phase) * 2 * PI).toFloat() * 2.2f else 0f
    Box(Modifier.size(size).graphicsLayer { translationY = dy * density }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            // halo
            drawCircle(Brush.radialGradient(listOf(neon.a.copy(alpha = 0.45f), Color.Transparent), center, r * 1.5f), r * 1.5f)
            // body
            drawCircle(Brush.linearGradient(listOf(neon.a, neon.b), Offset(0f, 0f), Offset(this.size.width, this.size.height)), r * 0.92f)
            // rim light + gloss
            drawCircle(Color.White.copy(alpha = 0.35f), r * 0.92f, style = Stroke(1.dp.toPx()))
            drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), r * 0.25f, r * 0.95f),
                Offset(r * 0.42f, r * 0.2f), Size(r * 1.16f, r * 0.7f))
        }
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.46f))
    }
}

/** A number that counts up when it first appears (and re-animates when it changes). */
@Composable
fun CountUp(value: Double, format: (Double) -> String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    FitText(format(a.value.toDouble()), style, color, modifier)
}

/** Little sparkline for tiles (last values, newest right). */
@Composable
fun Spark(values: List<Double>, neon: Neon, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) { grow.snapTo(0f); grow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val mn = values.min(); val mx = values.max(); val span = (mx - mn).takeIf { it > 1e-6 } ?: 1.0
        val n = values.size
        val pts = values.mapIndexed { i, v -> Offset(i * size.width / (n - 1), (size.height - ((v - mn) / span * size.height * 0.85 + size.height * 0.075)).toFloat()) }
        val shown = (pts.size * grow.value).toInt().coerceIn(2, pts.size)
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x, pts[0].y); for (i in 1 until shown) lineTo(pts[i].x, pts[i].y) }
        val area = androidx.compose.ui.graphics.Path().apply { addPath(path); lineTo(pts[shown - 1].x, size.height); lineTo(pts[0].x, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(neon.a.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, Brush.horizontalGradient(listOf(neon.b, neon.a)), style = Stroke(2.2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(neon.a, 3.2.dp.toPx(), pts[shown - 1])
    }
}

/** Overline-style tile label that reads well on any tile colour. */
@Composable
fun TileLabel(text: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Text(text.uppercase(), style = com.myfit.tracker.ui.theme.FitType.overline, color = th.textDim, modifier = modifier, maxLines = 1)
}
