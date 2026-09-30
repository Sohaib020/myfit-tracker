package com.myfit.tracker.ui.pip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Pip — MyFit's glass-blob buddy with a little sprout on top. */
enum class PipMood { HAPPY, EXCITED, SLEEPY, THINKING, CONCERNED, WAVE, PROUD, NEUTRAL }

private data class Particle(val born: Long, val angle: Float, val speed: Float, val kind: Int, val spin: Float)

@Composable
fun Pip(
    mood: PipMood,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    onTap: (() -> Unit)? = null,
) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val inf = rememberInfiniteTransition(label = "pip")
    val speed = when (mood) { PipMood.EXCITED -> 0.55f; PipMood.SLEEPY -> 2.2f; else -> 1f }
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween((1600 * speed).toInt(), easing = LinearEasing)), label = "bob")
    val wob by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "wob")
    val wave by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(380), RepeatMode.Reverse), label = "wave")
    val sway by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "sway")

    // blink loop
    val blink = remember { Animatable(0f) }
    LaunchedEffect(mood) {
        while (true) {
            delay(Random.nextLong(1800, 4800))
            if (mood != PipMood.SLEEPY) {
                blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110))
                if (Random.nextInt(4) == 0) { delay(120); blink.animateTo(1f, tween(60)); blink.animateTo(0f, tween(100)) }
            }
        }
    }
    // idle glance
    val glance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2500, 6000))
            glance.animateTo(Random.nextFloat() * 2f - 1f, spring(0.6f, 120f))
        }
    }
    // tap squash + joy
    val squash = remember { Animatable(1f) }
    var joy by remember { mutableStateOf(0L) }
    val joyAmt by animateFloatAsState(if (joy > 0) 1f else 0f, tween(300), label = "joy")
    val particles = remember { mutableStateListOf<Particle>() }
    var now by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { t -> now = t; if (particles.isNotEmpty()) particles.removeAll { t - it.born > 1100L } }
    }
    LaunchedEffect(joy) { if (joy > 0) { delay(1600); joy = 0 } }

    val effectiveMood = if (joy > 0) PipMood.EXCITED else mood

    Box(
        modifier.size(size).clickableNoRipple {
            tick()
            scope.launch {
                squash.snapTo(0.78f)
                squash.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow))
            }
            joy = System.nanoTime()
            val t0 = now
            repeat(9) {
                particles += Particle(t0, Random.nextFloat() * 360f, 0.6f + Random.nextFloat() * 0.8f, Random.nextInt(3), Random.nextFloat() * 2 - 1)
            }
            if (particles.size > 40) particles.removeRange(0, particles.size - 40)
            onTap?.invoke()
        }
    ) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val cx = w / 2f
            val bobY = sin(bob * 2 * PI.toFloat()) * w * (if (effectiveMood == PipMood.EXCITED) 0.06f else 0.03f)
            val breathe = 1f + 0.025f * sin(bob * 2 * PI.toFloat() + 1f)
            val sq = squash.value
            val sx = (2f - sq) * breathe
            val sy = sq * breathe
            val r = w * 0.34f
            val cy = w * 0.56f + bobY

            // floor shadow
            drawOval(
                Color.Black.copy(alpha = 0.22f - bobY / w),
                Offset(cx - r * 0.8f, w * 0.93f), Size(r * 1.6f, w * 0.05f),
            )

            withTransform({ scale(sx, sy, Offset(cx, cy + r)) }) {
                // sprout
                sprout(cx, cy - r * 0.92f, r, sway, th.success)
                // body
                val body = blobPath(cx, cy, r, wob)
                drawPath(body, Brush.radialGradient(
                    listOf(th.accentBright, th.accent, th.accent.darken(0.45f)),
                    Offset(cx - r * 0.35f, cy - r * 0.4f), r * 1.6f,
                ))
                // inner glass depth
                drawPath(body, Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), Offset(cx, cy - r * 0.2f), r * 1.2f))
                drawPath(body, Color.White.copy(alpha = 0.45f), style = Stroke(w * 0.012f))
                // specular highlights
                drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)), cy - r * 0.9f, cy - r * 0.25f),
                    Offset(cx - r * 0.62f, cy - r * 0.86f), Size(r * 0.75f, r * 0.5f))
                drawCircle(Color.White.copy(alpha = 0.85f), r * 0.07f, Offset(cx + r * 0.45f, cy - r * 0.5f))

                if (effectiveMood == PipMood.WAVE) arm(cx + r * 0.95f, cy, r, wave, th.accent)

                face(effectiveMood, cx, cy, r, blink.value, glance.value, joyAmt, wob)
            }

            // particles: hearts / sparkles / stars
            val life = 1100L
            particles.forEach { p ->
                val t = ((now - p.born).toFloat() / life).coerceIn(0f, 1f)
                val dist = r * (0.6f + 1.4f * t * p.speed)
                val a = Math.toRadians(p.angle.toDouble())
                val px = cx + cos(a).toFloat() * dist
                val py = cy - r * 0.2f + sin(a).toFloat() * dist - t * r * 0.6f
                val alpha = 1f - t
                val s = r * (0.16f + 0.08f * p.speed)
                when (p.kind) {
                    0 -> heart(px, py, s, Color(0xFFFF5C8A).copy(alpha = alpha))
                    1 -> sparkle(px, py, s, Color.White.copy(alpha = alpha), p.spin * t * 180f)
                    else -> sparkle(px, py, s * 0.8f, th.accentBright.copy(alpha = alpha), p.spin * t * 180f)
                }
            }

            // mood extras
            when (effectiveMood) {
                PipMood.SLEEPY -> {
                    for (i in 0..2) {
                        val t = ((bob + i / 3f) % 1f)
                        val zx = cx + r * (0.7f + t * 0.5f); val zy = cy - r * (0.9f + t * 0.9f)
                        drawZ(zx, zy, r * (0.14f + i * 0.04f), Color.White.copy(alpha = (1f - t) * 0.9f))
                    }
                }
                PipMood.THINKING -> {
                    for (i in 0..2) {
                        val on = ((bob * 3f).toInt() % 3) >= i
                        drawCircle(Color.White.copy(alpha = if (on) 0.9f else 0.3f), r * (0.06f + i * 0.025f), Offset(cx + r * (0.85f + i * 0.28f), cy - r * (0.85f + i * 0.3f)))
                    }
                }
                PipMood.EXCITED, PipMood.PROUD -> {
                    for (i in 0..3) {
                        val ang = wob + i * (PI.toFloat() / 2f)
                        sparkle(cx + cos(ang) * r * 1.35f, cy + sin(ang) * r * 1.1f, r * 0.12f, Color.White.copy(alpha = 0.8f), ang * 60f)
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

private fun blobPath(cx: Float, cy: Float, r: Float, ph: Float): Path {
    val p = Path()
    val n = 64
    for (i in 0..n) {
        val th = (i.toFloat() / n) * 2f * PI.toFloat()
        val rr = r * (1f + 0.035f * sin(3f * th + ph) + 0.02f * sin(5f * th - ph * 1.3f))
        // slightly flatter bottom → sits like a gumdrop
        val x = cx + cos(th) * rr * 1.04f
        val y = cy + sin(th) * rr * (if (sin(th) > 0) 0.9f else 1f)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun DrawScope.sprout(x: Float, y: Float, r: Float, sway: Float, leaf: Color) {
    rotate(sway * 10f, Offset(x, y + r * 0.1f)) {
        val stem = Path().apply { moveTo(x, y + r * 0.12f); quadraticBezierTo(x + r * 0.05f, y - r * 0.12f, x, y - r * 0.26f) }
        drawPath(stem, Color(0xFF3E8E3A), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
        fun leafAt(dir: Float) {
            val l = Path().apply {
                moveTo(x, y - r * 0.24f)
                quadraticBezierTo(x + dir * r * 0.18f, y - r * 0.5f, x + dir * r * 0.4f, y - r * 0.34f)
                quadraticBezierTo(x + dir * r * 0.2f, y - r * 0.18f, x, y - r * 0.24f)
                close()
            }
            drawPath(l, Brush.linearGradient(listOf(leaf.copy(alpha = 1f), Color(0xFF2E7D32)), Offset(x, y - r * 0.5f), Offset(x + dir * r * 0.4f, y - r * 0.2f)))
            drawPath(l, Color.White.copy(alpha = 0.35f), style = Stroke(r * 0.02f))
        }
        leafAt(-1f); leafAt(1f)
    }
}

private fun DrawScope.arm(x: Float, y: Float, r: Float, wave: Float, c: Color) {
    rotate(-35f + wave * 25f, Offset(x - r * 0.1f, y)) {
        drawOval(Brush.verticalGradient(listOf(c.copy(alpha = 1f), c.darken(0.3f))), Offset(x - r * 0.12f, y - r * 0.55f), Size(r * 0.28f, r * 0.6f))
        drawOval(Color.White.copy(alpha = 0.35f), Offset(x - r * 0.12f, y - r * 0.55f), Size(r * 0.28f, r * 0.6f), style = Stroke(r * 0.025f))
    }
}

private fun DrawScope.face(mood: PipMood, cx: Float, cy: Float, r: Float, blink: Float, glance: Float, joy: Float, ph: Float) {
    val eyeY = cy - r * 0.08f
    val eyeDx = r * 0.36f
    val ew = r * 0.3f; val eh = r * 0.36f
    val lookX = glance * r * 0.06f + when (mood) { PipMood.THINKING -> -r * 0.06f; else -> 0f }
    val lookY = when (mood) { PipMood.THINKING -> -r * 0.07f; PipMood.SLEEPY -> r * 0.04f; else -> 0f }
    val lid = when (mood) { PipMood.SLEEPY -> 0.62f; PipMood.CONCERNED -> 0.15f; else -> 0f }.coerceAtLeast(blink)
    val happySquint = mood == PipMood.EXCITED || mood == PipMood.PROUD

    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * eyeDx
        if (happySquint) {
            // ^ ^ eyes
            val a = Path().apply { moveTo(ex - ew * 0.5f, eyeY + eh * 0.12f); quadraticBezierTo(ex, eyeY - eh * 0.5f, ex + ew * 0.5f, eyeY + eh * 0.12f) }
            drawPath(a, Color(0xFF1A1014), style = Stroke(r * 0.085f, cap = StrokeCap.Round))
        } else {
            val h = eh * (1f - lid)
            drawOval(Color.White, Offset(ex - ew / 2, eyeY - h / 2 + eh * lid * 0.25f), Size(ew, h.coerceAtLeast(r * 0.035f)))
            if (h > eh * 0.25f) {
                val pr = ew * 0.34f
                val pc = Offset(ex + lookX, eyeY + lookY + eh * lid * 0.25f)
                drawCircle(Color(0xFF1A1014), pr, pc)
                drawCircle(Color.White, pr * 0.38f, Offset(pc.x - pr * 0.3f, pc.y - pr * 0.35f))
                drawCircle(Color.White.copy(alpha = 0.7f), pr * 0.16f, Offset(pc.x + pr * 0.35f, pc.y + pr * 0.3f))
            }
        }
        if (mood == PipMood.CONCERNED) {
            drawLine(Color(0xFF1A1014), Offset(ex - side * ew * 0.55f, eyeY - eh * 0.72f), Offset(ex + side * ew * 0.4f, eyeY - eh * 0.9f), r * 0.05f, StrokeCap.Round)
        }
        // blush
        drawOval(Color(0xFFFF7FA5).copy(alpha = 0.35f + 0.3f * joy + if (mood == PipMood.PROUD) 0.2f else 0f),
            Offset(cx + side * r * 0.6f - r * 0.14f, cy + r * 0.2f), Size(r * 0.28f, r * 0.14f))
    }

    // mouth
    val my = cy + r * 0.32f
    val ink = Color(0xFF1A1014)
    when (mood) {
        PipMood.HAPPY, PipMood.WAVE, PipMood.NEUTRAL -> {
            val m = Path().apply { moveTo(cx - r * 0.16f, my - r * 0.02f); quadraticBezierTo(cx, my + r * (if (mood == PipMood.NEUTRAL) 0.06f else 0.16f), cx + r * 0.16f, my - r * 0.02f) }
            drawPath(m, ink, style = Stroke(r * 0.06f, cap = StrokeCap.Round))
        }
        PipMood.EXCITED, PipMood.PROUD -> {
            val m = Path().apply {
                moveTo(cx - r * 0.2f, my - r * 0.04f)
                quadraticBezierTo(cx, my + r * 0.34f, cx + r * 0.2f, my - r * 0.04f)
                close()
            }
            drawPath(m, Color(0xFF3A0A12))
            drawOval(Color(0xFFFF6B81), Offset(cx - r * 0.09f, my + r * 0.08f), Size(r * 0.18f, r * 0.1f))
        }
        PipMood.SLEEPY -> drawOval(ink, Offset(cx - r * 0.06f, my), Size(r * 0.12f, r * (0.08f + 0.04f * sin(ph * 2))))
        PipMood.THINKING -> drawLine(ink, Offset(cx - r * 0.1f, my + r * 0.03f), Offset(cx + r * 0.12f, my - r * 0.02f), r * 0.055f, StrokeCap.Round)
        PipMood.CONCERNED -> {
            val m = Path().apply { moveTo(cx - r * 0.14f, my + r * 0.08f); quadraticBezierTo(cx, my - r * 0.06f + sin(ph * 3) * r * 0.02f, cx + r * 0.14f, my + r * 0.08f) }
            drawPath(m, ink, style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.heart(x: Float, y: Float, s: Float, c: Color) {
    val p = Path().apply {
        moveTo(x, y + s * 0.35f)
        cubicTo(x - s * 0.9f, y - s * 0.25f, x - s * 0.35f, y - s * 0.9f, x, y - s * 0.35f)
        cubicTo(x + s * 0.35f, y - s * 0.9f, x + s * 0.9f, y - s * 0.25f, x, y + s * 0.35f)
        close()
    }
    drawPath(p, c)
}

private fun DrawScope.sparkle(x: Float, y: Float, s: Float, c: Color, rot: Float) {
    rotate(rot, Offset(x, y)) {
        val p = Path().apply {
            moveTo(x, y - s); quadraticBezierTo(x, y, x + s, y); quadraticBezierTo(x, y, x, y + s)
            quadraticBezierTo(x, y, x - s, y); quadraticBezierTo(x, y, x, y - s); close()
        }
        drawPath(p, c)
    }
}

private fun DrawScope.drawZ(x: Float, y: Float, s: Float, c: Color) {
    val p = Path().apply { moveTo(x - s / 2, y - s / 2); lineTo(x + s / 2, y - s / 2); lineTo(x - s / 2, y + s / 2); lineTo(x + s / 2, y + s / 2) }
    drawPath(p, c, style = Stroke(s * 0.22f, cap = StrokeCap.Round))
}
