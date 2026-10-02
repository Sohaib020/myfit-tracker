package com.myfit.tracker.ui.mind

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Palette slot used by patterns/scripts. */
fun hueColor(th: FitTheme, hue: Int): Color = when (hue) {
    1 -> th.sleep
    2 -> th.water
    3 -> th.success
    4 -> th.warning
    else -> th.accent
}

/** Colour for a 1..5 mood. */
fun moodColor(m: Int): Color = when (m) {
    1 -> Color(0xFFFF6B6B)
    2 -> Color(0xFFFF9F5A)
    3 -> Color(0xFFFFCF5A)
    4 -> Color(0xFF7ED68A)
    else -> Color(0xFF5AD1C8)
}

fun lighten(c: Color, f: Float): Color =
    Color(c.red + (1f - c.red) * f, c.green + (1f - c.green) * f, c.blue + (1f - c.blue) * f, c.alpha)

/** Hand-drawn face for a 1..5 mood (no emoji font dependency). [dim] draws it muted (unselected). */
@Composable
fun MoodFace(mood: Int, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false) {
    val base = moodColor(mood)
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val a = if (dim) 0.38f else 1f
        drawCircle(
            Brush.radialGradient(
                listOf(lighten(base, 0.35f).copy(alpha = a), base.copy(alpha = a)),
                center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.5f,
            ), r, c,
        )
        // soft top highlight
        drawCircle(Color.White.copy(alpha = 0.18f * a), r * 0.55f, Offset(c.x - r * 0.25f, c.y - r * 0.4f))
        val ink = Color(0xFF2A2340).copy(alpha = 0.85f * a)
        val eyeY = c.y - r * 0.18f
        val eyeDx = r * 0.32f
        val eyeR = r * 0.09f
        if (mood >= 5) {
            // happy closed eyes ^ ^
            for (sx in listOf(-1f, 1f)) {
                val p = Path().apply {
                    moveTo(c.x + sx * eyeDx - r * 0.13f, eyeY + r * 0.04f)
                    quadraticTo(c.x + sx * eyeDx, eyeY - r * 0.14f, c.x + sx * eyeDx + r * 0.13f, eyeY + r * 0.04f)
                }
                drawPath(p, ink, style = Stroke(r * 0.08f, cap = StrokeCap.Round))
            }
        } else {
            drawCircle(ink, eyeR, Offset(c.x - eyeDx, eyeY))
            drawCircle(ink, eyeR, Offset(c.x + eyeDx, eyeY))
        }
        if (mood == 1) {
            // worried brows
            drawLine(ink, Offset(c.x - eyeDx - r * 0.14f, eyeY - r * 0.16f), Offset(c.x - eyeDx + r * 0.1f, eyeY - r * 0.26f), r * 0.07f, StrokeCap.Round)
            drawLine(ink, Offset(c.x + eyeDx + r * 0.14f, eyeY - r * 0.16f), Offset(c.x + eyeDx - r * 0.1f, eyeY - r * 0.26f), r * 0.07f, StrokeCap.Round)
        }
        // mouth: curvature from frown (-) to smile (+)
        val curve = when (mood) { 1 -> -0.22f; 2 -> -0.11f; 3 -> 0f; 4 -> 0.14f; else -> 0.24f }
        val my = c.y + r * 0.34f
        val mw = r * (if (mood == 3) 0.30f else 0.40f)
        val mouth = Path().apply {
            moveTo(c.x - mw, my - curve * r * 0.4f)
            quadraticTo(c.x, my + curve * r * 1.1f, c.x + mw, my - curve * r * 0.4f)
        }
        drawPath(mouth, ink, style = Stroke(r * 0.09f, cap = StrokeCap.Round))
        if (mood >= 4) {
            val blush = Color(0xFFFF7A9A).copy(alpha = 0.28f * a)
            drawCircle(blush, r * 0.13f, Offset(c.x - r * 0.55f, c.y + r * 0.14f))
            drawCircle(blush, r * 0.13f, Offset(c.x + r * 0.55f, c.y + r * 0.14f))
        }
    }
}

/**
 * Breathing orb. [scale] 0..1 is how "full" the breath is; [progress] 0..1 is overall session progress
 * drawn as a thin ring; [phaseFrac] 0..1 progress within the current phase (small orbiting dot).
 */
@Composable
fun BreathingOrb(scale: Float, progress: Float, phaseFrac: Float, color: Color, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f
        val ringR = maxR * 0.96f
        val minOrb = maxR * 0.42f
        val maxOrb = maxR * 0.84f
        val r = minOrb + (maxOrb - minOrb) * scale.coerceIn(0f, 1f)
        val light = lighten(color, 0.55f)
        // outer glow halos
        for (i in 3 downTo 1) {
            val gr = r * (1f + i * 0.11f)
            drawCircle(color.copy(alpha = 0.06f + 0.04f * scale), gr, c)
        }
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.85f), light, color, color.copy(alpha = 0.55f)),
                center = Offset(c.x - r * 0.25f, c.y - r * 0.3f), radius = r * 1.35f,
            ), r, c,
        )
        // inner sheen
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = Offset(c.x - r * 0.3f, c.y - r * 0.45f), radius = r * 0.6f),
            r * 0.6f, Offset(c.x - r * 0.3f, c.y - r * 0.45f),
        )
        drawCircle(Color.White.copy(alpha = 0.35f), r, c, style = Stroke(1.2.dp.toPx()))
        // progress ring
        val track = if (th.isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f)
        val sw = 3.dp.toPx()
        drawCircle(track, ringR, c, style = Stroke(sw))
        drawArc(
            Brush.sweepGradient(listOf(light, color, light), c),
            -90f, 360f * progress.coerceIn(0f, 1f), false,
            topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2, ringR * 2),
            style = Stroke(sw, cap = StrokeCap.Round),
        )
        // dot travelling around the orb within the phase
        val ang = (-PI / 2 + 2 * PI * phaseFrac.coerceIn(0f, 1f)).toFloat()
        val dr = r * 1.12f
        drawCircle(Color.White.copy(alpha = 0.9f), 3.5.dp.toPx(), Offset(c.x + cos(ang) * dr, c.y + sin(ang) * dr))
    }
}

/** Slow, soft gradient blobs. [t] is seconds of play time (frozen while paused). */
@Composable
fun DriftingBlobs(time: () -> Float, colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val t = time()          // read in the draw phase: only redraws, never recomposes
        val w = size.width; val h = size.height
        colors.forEachIndexed { i, col ->
            val sp = 0.035f + i * 0.012f
            val ph = i * 2.1f
            val x = w * (0.5f + 0.32f * sin(t * sp * 2f * PI.toFloat() + ph))
            val y = h * (0.45f + 0.28f * cos(t * sp * 1.4f * PI.toFloat() + ph * 1.3f))
            val r = minOf(w, h) * (0.45f + 0.08f * sin(t * 0.05f + i))
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.42f), col.copy(alpha = 0f)), Offset(x, y), r), r, Offset(x, y))
        }
    }
}

/** Semicircle stress gauge (0..100). */
@Composable
fun StressGauge(score: Int, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Canvas(modifier) {
        val sw = 12.dp.toPx()
        val r = minOf(size.width / 2f, size.height) - sw
        val c = Offset(size.width / 2f, size.height - sw / 2f)
        val tl = Offset(c.x - r, c.y - r)
        val track = if (th.isLight) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.09f)
        drawArc(track, 180f, 180f, false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
        val steps = 60
        for (i in 0 until steps) {
            val f = i / steps.toFloat()
            if (f > score / 100f) break
            val col = when {
                f < 0.5f -> lerpColor(th.success, th.warning, f / 0.5f)
                else -> lerpColor(th.warning, th.danger, (f - 0.5f) / 0.5f)
            }
            drawArc(col, 180f + 180f * f, 180f / steps + 0.6f, false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = if (i == 0) StrokeCap.Round else StrokeCap.Butt))
        }
        val a = (PI + PI * (score / 100f)).toFloat()
        val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        drawCircle(Color.White, sw * 0.62f, p)
        drawCircle(Color.Black.copy(alpha = 0.15f), sw * 0.62f, p, style = Stroke(1.dp.toPx()))
    }
}

fun lerpColor(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t)
}
