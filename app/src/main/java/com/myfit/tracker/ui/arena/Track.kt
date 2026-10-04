package com.myfit.tracker.ui.arena

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.ui.theme.FitType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A checkpoint on a track: where it sits (0..1), its stars, and an optional label (journey stop names). */
data class Checkpoint(val at: Float, val stars: Int, val label: String? = null)

private val GOLD = Color(0xFFFFC83D)
private val GOLD_DEEP = Color(0xFFF29E0C)

/**
 * Scenic race track: themed backdrop, a winding road from the start line to a finish arch, checkpoint flags
 * with collectable stars, an optional pace ghost and your character running along it (animated 3D clip).
 */
@Composable
fun ScenicTrack(
    scene: Scene, progress: Float, checkpoints: List<Checkpoint>, runner: Mascot,
    modifier: Modifier = Modifier, height: Dp = 210.dp, pace: Float? = null, accent: Color = runner.accent, animateIn: Boolean = true,
) {
    var started by remember { mutableStateOf(!animateIn) }
    LaunchedEffect(Unit) { started = true }
    val prog by animateFloatAsState(if (started) progress.coerceIn(0f, 1f) else 0f, tween(1600), label = "track")
    val inf = rememberInfiniteTransition(label = "scene")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing)), label = "drift")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pulse")
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(22.dp))) {
        val w = with(density) { maxWidth.toPx() }; val h = with(density) { maxHeight.toPx() }
        val road = remember(w, h) { roadPath(w, h) }
        val pm = remember(road) { PathMeasure().apply { setPath(road, false) } }
        val runnerSize = (maxHeight * 0.36f)
        Canvas(Modifier.fillMaxSize()) {
            backdrop(scene, drift)
            // road: shadow, edge, surface, centre dashes
            drawPath(road, Color.Black.copy(alpha = 0.18f), style = Stroke(size.height * 0.15f, cap = StrokeCap.Round))
            drawPath(road, Color.White.copy(alpha = 0.85f), style = Stroke(size.height * 0.13f, cap = StrokeCap.Round))
            drawPath(road, scene.road, style = Stroke(size.height * 0.11f, cap = StrokeCap.Round))
            // travelled part glows in the runner's colour
            val done = Path(); pm.getSegment(0f, pm.length * prog, done, true)
            drawPath(done, accent.copy(alpha = 0.85f), style = Stroke(size.height * 0.05f, cap = StrokeCap.Round))
            drawPath(road, Color.White.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f), -drift * 400f)))
            // start line
            val s0 = pm.getPosition(0f); checker(s0, size.height * 0.11f)
            // finish arch
            finishArch(pm.getPosition(pm.length), accent)
            // checkpoints
            checkpoints.forEach { cp ->
                if (cp.at <= 0f || cp.at >= 0.999f) return@forEach
                val p = pm.getPosition(pm.length * cp.at)
                val got = prog >= cp.at - 1e-4
                flag(p, accent, got)
                star(Offset(p.x + 10.dp.toPx(), p.y - 40.dp.toPx() - (if (got) pulse * 3.dp.toPx() else 0f)), 9.dp.toPx(), got, pulse)
            }
            // stars waiting at the finish
            val fin = pm.getPosition(pm.length)
            val finCp = checkpoints.lastOrNull { it.at >= 0.999f }
            if (finCp != null) star(Offset(fin.x, fin.y - 62.dp.toPx() - pulse * 3.dp.toPx()), 11.dp.toPx(), prog >= 0.999f, pulse)
            // pace ghost
            if (pace != null && pace in 0.02f..0.98f) {
                val g = pm.getPosition(pm.length * pace)
                drawCircle(Color.White.copy(alpha = 0.55f), 9.dp.toPx(), g)
                drawCircle(Color.White, 9.dp.toPx(), g, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))))
            }
        }
        // checkpoint labels (journey stops)
        checkpoints.forEach { cp ->
            val lbl = cp.label ?: return@forEach
            val p = pm.getPosition(pm.length * cp.at.coerceIn(0f, 1f))
            Text(lbl, style = FitType.overline.copy(fontSize = 8.sp, letterSpacing = 0.4.sp), color = Color.White,
                modifier = Modifier.offset { IntOffset((p.x - 24.dp.toPx()).roundToInt().coerceIn(0, (w - 60.dp.toPx()).roundToInt().coerceAtLeast(0)), (p.y + 8.dp.toPx()).roundToInt()) }
                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 4.dp, vertical = 1.dp))
        }
        // the runner
        val me = pm.getPosition(pm.length * prog)
        val rs = with(density) { runnerSize.toPx() }
        Box(Modifier.offset { IntOffset((me.x - rs / 2).roundToInt(), (me.y - rs * 0.92f).roundToInt()) }) {
            CastAnim(runner, if (prog >= 0.999f) CastClip.CHEER else CastClip.RUN, runnerSize)
        }
    }
}

/** Winding road from bottom-left to the upper right, in rough perspective. */
private fun roadPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.08f, h * 0.86f)
    cubicTo(w * 0.40f, h * 0.98f, w * 0.52f, h * 0.70f, w * 0.38f, h * 0.62f)
    cubicTo(w * 0.22f, h * 0.53f, w * 0.40f, h * 0.40f, w * 0.62f, h * 0.48f)
    cubicTo(w * 0.80f, h * 0.55f, w * 0.86f, h * 0.42f, w * 0.88f, h * 0.36f)
}

private fun DrawScope.backdrop(sc: Scene, drift: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(sc.sky, 0f, h * 0.6f))
    // sun / glow
    val sun = Offset(w * 0.82f, h * 0.16f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0f)), sun, h * 0.22f), h * 0.22f, sun)
    drawCircle(Color(0xFFFFF6D8), h * 0.07f, sun)
    // clouds drift across
    for (k in 0..2) {
        val x = ((drift + k / 3f) % 1f) * (w + 160f) - 80f
        val y = h * (0.10f + 0.07f * k)
        val c = Color.White.copy(alpha = 0.85f)
        drawOval(c, Offset(x, y), Size(64f, 22f)); drawOval(c, Offset(x + 18f, y - 12f), Size(40f, 28f)); drawOval(c, Offset(x + 36f, y - 4f), Size(44f, 22f))
    }
    when (sc) {
        Scene.K2 -> {
            peaks(sc.hills[1], h * 0.52f, listOf(0.05f to 0.22f, 0.30f to 0.10f, 0.55f to 0.26f, 0.80f to 0.14f, 1.05f to 0.3f), snow = true)
            peaks(sc.hills[0], h * 0.60f, listOf(-0.05f to 0.40f, 0.20f to 0.30f, 0.45f to 0.42f, 0.72f to 0.34f, 1.0f to 0.44f), snow = true)
        }
        Scene.MARGALLA -> { hills(sc.hills[0], h * 0.42f, 0.10f, 1.3f); hills(sc.hills[1], h * 0.52f, 0.08f, 2.1f); trees(h * 0.55f, Color(0xFF2E7A4F)) }
        Scene.CLIFTON -> {
            drawRect(Brush.verticalGradient(listOf(sc.hills[0], sc.hills[1]), h * 0.40f, h * 0.62f), Offset(0f, h * 0.40f), Size(w, h * 0.22f))
            for (k in 0..5) { val y = h * (0.44f + k * 0.03f); val off = (drift * 300f + k * 40f) % 60f
                drawLine(Color.White.copy(alpha = 0.35f), Offset(-60f + off, y), Offset(w, y), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 38f))) }
        }
        Scene.DESERT -> { hills(sc.hills[0], h * 0.48f, 0.07f, 0.9f); hills(sc.hills[1], h * 0.56f, 0.06f, 1.4f); palms(h * 0.56f) }
        Scene.CITY -> skyline(sc.hills[1], h * 0.58f)
        Scene.SHALIMAR -> { hills(sc.hills[0], h * 0.50f, 0.05f, 1.2f); arch(Offset(w * 0.18f, h * 0.53f), h * 0.20f); trees(h * 0.58f, Color(0xFF2F8F4E)) }
    }
    // ground
    val gTop = h * 0.56f
    drawRect(Brush.verticalGradient(listOf(sc.ground, sc.ground.copy(red = sc.ground.red * 0.85f, green = sc.ground.green * 0.85f, blue = sc.ground.blue * 0.85f)), gTop, h), Offset(0f, gTop), Size(w, h - gTop))
}

private fun DrawScope.hills(c: Color, base: Float, amp: Float, freq: Float) {
    val w = size.width; val h = size.height
    val p = Path().apply {
        moveTo(0f, h); lineTo(0f, base)
        var x = 0f; while (x <= w) { lineTo(x, base - h * amp * (0.6f + 0.4f * sin(x / w * PI.toFloat() * 2 * freq).toFloat())); x += 8f }
        lineTo(w, h); close()
    }
    drawPath(p, c)
}

private fun DrawScope.peaks(c: Color, base: Float, tops: List<Pair<Float, Float>>, snow: Boolean) {
    val w = size.width; val h = size.height
    tops.forEach { (x, y) ->
        val cx = x * w; val top = h * y; val half = w * 0.18f
        val p = Path().apply { moveTo(cx - half, base); lineTo(cx, top); lineTo(cx + half, base); close() }
        drawPath(p, c)
        if (snow) {
            val sh = (base - top) * 0.28f
            val s = Path().apply { moveTo(cx - half * sh / (base - top), top + sh); lineTo(cx, top); lineTo(cx + half * sh / (base - top), top + sh)
                lineTo(cx + half * 0.12f, top + sh * 0.8f); lineTo(cx, top + sh * 1.05f); lineTo(cx - half * 0.12f, top + sh * 0.8f); close() }
            drawPath(s, Color.White)
        }
    }
}

private fun DrawScope.trees(base: Float, c: Color) {
    val w = size.width
    listOf(0.05f, 0.14f, 0.70f, 0.93f).forEach { x ->
        val cx = x * w
        drawRect(Color(0xFF6B4A2E), Offset(cx - 2f, base - 14f), Size(4f, 16f))
        drawCircle(c, 13f, Offset(cx, base - 22f)); drawCircle(c.copy(alpha = 0.85f), 10f, Offset(cx - 8f, base - 16f)); drawCircle(c.copy(alpha = 0.85f), 10f, Offset(cx + 8f, base - 16f))
    }
}

private fun DrawScope.palms(base: Float) {
    val w = size.width
    listOf(0.12f, 0.74f).forEach { x ->
        val cx = x * w
        drawLine(Color(0xFF8A5A2B), Offset(cx, base), Offset(cx + 6f, base - 40f), 5f, StrokeCap.Round)
        for (k in 0 until 5) {
            val a = (-160 + k * 35) * PI / 180
            drawLine(Color(0xFF3E9A55), Offset(cx + 6f, base - 40f), Offset(cx + 6f + (cos(a) * 22).toFloat(), base - 40f + (sin(a) * 14).toFloat() + 6f), 4f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.skyline(c: Color, base: Float) {
    val w = size.width; var x = 0f; var k = 0
    while (x < w) {
        val bw = 22f + (k * 37 % 19); val bh = 30f + (k * 53 % 46)
        drawRect(c.copy(alpha = 0.9f - (k % 3) * 0.12f), Offset(x, base - bh), Size(bw, bh))
        for (wy in 0 until (bh / 10).toInt() - 1) drawRect(Color(0xFFFFF1B8).copy(alpha = 0.5f), Offset(x + 5f, base - bh + 6f + wy * 10f), Size(4f, 4f))
        x += bw + 4f; k++
    }
}

private fun DrawScope.arch(at: Offset, hh: Float) {
    val c = Color(0xFFC05A3E); val ww = hh * 0.9f
    drawRect(c, Offset(at.x - ww / 2, at.y - hh), Size(ww, hh))
    val p = Path().apply { moveTo(at.x - ww * 0.25f, at.y); lineTo(at.x - ww * 0.25f, at.y - hh * 0.5f); quadraticBezierTo(at.x, at.y - hh * 0.9f, at.x + ww * 0.25f, at.y - hh * 0.5f); lineTo(at.x + ww * 0.25f, at.y); close() }
    drawPath(p, Color(0xFF5A2A1E).copy(alpha = 0.55f))
    drawCircle(Color(0xFFF2E3C0), ww * 0.16f, Offset(at.x, at.y - hh - ww * 0.06f))
}

private fun DrawScope.checker(at: Offset, width: Float) {
    val n = 6; val cell = width / n
    for (i in 0 until n) for (j in 0..1) drawRect(if ((i + j) % 2 == 0) Color.White else Color(0xFF222222), Offset(at.x - width / 2 + i * cell, at.y - cell + j * cell), Size(cell, cell))
}

private fun DrawScope.finishArch(at: Offset, accent: Color) {
    val hh = 46.dp.toPx(); val ww = 40.dp.toPx(); val post = 4.dp.toPx()
    drawRoundRect(Color.White, Offset(at.x - ww / 2 - post / 2, at.y - hh), Size(post, hh), CornerRadius(post))
    drawRoundRect(Color.White, Offset(at.x + ww / 2 - post / 2, at.y - hh), Size(post, hh), CornerRadius(post))
    drawRoundRect(accent, Offset(at.x - ww / 2 - 4.dp.toPx(), at.y - hh - 6.dp.toPx()), Size(ww + 8.dp.toPx(), 13.dp.toPx()), CornerRadius(4.dp.toPx()))
    for (i in 0 until 8) drawRect(if (i % 2 == 0) Color.White else Color(0xFF222222), Offset(at.x - ww / 2 + i * ww / 8, at.y - hh - 2.dp.toPx()), Size(ww / 8, 4.dp.toPx()))
}

private fun DrawScope.flag(at: Offset, accent: Color, got: Boolean) {
    val hh = 28.dp.toPx()
    drawLine(Color.White, at, Offset(at.x, at.y - hh), 2.5.dp.toPx(), StrokeCap.Round)
    val p = Path().apply { moveTo(at.x, at.y - hh); lineTo(at.x + 15.dp.toPx(), at.y - hh + 5.dp.toPx()); lineTo(at.x, at.y - hh + 10.dp.toPx()); close() }
    drawPath(p, if (got) accent else Color.White.copy(alpha = 0.8f))
    drawCircle(Color.Black.copy(alpha = 0.18f), 4.dp.toPx(), at)
}

/** Five-point star; collected = gold with a soft glow, waiting = white outline. */
fun DrawScope.star(c: Offset, r: Float, filled: Boolean, glow: Float = 0f) {
    val p = Path()
    for (i in 0 until 10) {
        val a = -PI / 2 + i * PI / 5; val rr = if (i % 2 == 0) r else r * 0.46f
        val x = c.x + (cos(a) * rr).toFloat(); val y = c.y + (sin(a) * rr).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    if (filled) {
        drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.55f + 0.25f * glow), GOLD.copy(alpha = 0f)), c, r * 2.2f), r * 2.2f, c)
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE27A), GOLD, GOLD_DEEP), c.y - r, c.y + r))
        rotate(0f, c) { drawPath(p, Color.White.copy(alpha = 0.7f), style = Stroke(r * 0.12f)) }
    } else {
        drawPath(p, Color.Black.copy(alpha = 0.15f))
        drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(r * 0.16f))
    }
}
