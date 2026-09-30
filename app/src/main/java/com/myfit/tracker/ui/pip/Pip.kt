package com.myfit.tracker.ui.pip

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pip v2 — a glossy peach "mochi" buddy with a sprout that blooms when you do well.
 * Everything is drawn in code (no image assets), so it scales perfectly and animates at 60–120 fps.
 */
enum class PipMood { HAPPY, EXCITED, SLEEPY, THINKING, CONCERNED, WAVE, PROUD, NEUTRAL, LOVE, TALKING, CURIOUS, CELEBRATE }

private enum class Act { NONE, WAVE, CURL, SIP, STRETCH, HOP, LOOK, DANCE }

private data class Fx(val born: Float, val x: Float, val y: Float, val vx: Float, val vy: Float, val kind: Int, val spin: Float, val hue: Int)

// Pip's signature palette (theme-independent so Pip is always Pip; the aura follows the theme)
private val SKIN_LIGHT = Color(0xFFFFE0CC)
private val SKIN = Color(0xFFFF9E86)
private val SKIN_DEEP = Color(0xFFF2675E)
private val BELLY = Color(0xFFFFEFE4)
private val INK = Color(0xFF2A1A2E)
private val CHEEK = Color(0xFFFF6F91)
private val LEAF = Color(0xFF5DDB8B)
private val LEAF_DEEP = Color(0xFF239B5C)
private val confettiColors = listOf(Color(0xFFFF5C8A), Color(0xFFFFD34D), Color(0xFF4FC3FF), Color(0xFF7CFFB2), Color(0xFFB57CFF))

@Composable
fun Pip(
    mood: PipMood,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    onTap: (() -> Unit)? = null,
    talking: Boolean = false,
    interactive: Boolean = true,
    idleActions: Boolean = true,
) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val curTap by rememberUpdatedState(onTap)

    var t by remember { mutableFloatStateOf(0f) }
    val fx = remember { mutableStateListOf<Fx>() }

    // blinking
    var blinkAt by remember { mutableFloatStateOf(2f) }
    // interactions
    val squash = remember { Animatable(1f) }
    var giggleUntil by remember { mutableFloatStateOf(-1f) }
    var petting by remember { mutableStateOf(false) }
    var hugging by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var lastHeart by remember { mutableFloatStateOf(0f) }
    // idle actions
    var act by remember { mutableStateOf(Act.NONE) }
    var actStart by remember { mutableFloatStateOf(0f) }
    var nextAct by remember { mutableFloatStateOf(3.5f) }
    val look = remember { Animatable(0f) }

    fun burst(kind: Int, n: Int, cx: Float, cy: Float) {
        repeat(n) {
            val a = Random.nextFloat() * (2 * PI).toFloat()
            val sp = 0.35f + Random.nextFloat() * 0.6f
            fx += Fx(t, cx, cy, cos(a) * sp, sin(a) * sp - 0.5f, kind, Random.nextFloat() * 2 - 1, Random.nextInt(5))
        }
        if (fx.size > 60) fx.removeRange(0, fx.size - 60)
    }

    val effMood = when {
        hugging -> PipMood.LOVE
        petting -> PipMood.LOVE
        t < giggleUntil -> PipMood.EXCITED
        talking -> PipMood.TALKING
        else -> mood
    }
    val moodNow by rememberUpdatedState(effMood)
    val idleOn by rememberUpdatedState(idleActions)

    // One frame loop drives time, blinks, idle actions and ambient particles (all outside the draw pass).
    LaunchedEffect(Unit) {
        var start = -1L
        while (true) withFrameMillis { ms ->
            if (start < 0) start = ms
            t = (ms - start) / 1000f
            if (fx.isNotEmpty()) fx.removeAll { t - it.born > 1.6f }
            if (t > blinkAt + 0.18f) blinkAt = t + 2f + Random.nextFloat() * 3.5f
            val canIdle = idleOn && moodNow in setOf(PipMood.HAPPY, PipMood.NEUTRAL, PipMood.WAVE, PipMood.PROUD) && !petting && !hugging
            if (act != Act.NONE && t - actStart > actDuration(act)) { act = Act.NONE; nextAct = t + 4f + Random.nextFloat() * 5f }
            if (act == Act.NONE && canIdle && t > nextAct) { act = listOf(Act.WAVE, Act.CURL, Act.SIP, Act.STRETCH, Act.HOP, Act.LOOK).random(); actStart = t }
            if (moodNow == PipMood.CELEBRATE && Random.nextFloat() < 0.25f && fx.size < 50) burst(2, 1, 0.5f, 0.15f)
            if (moodNow == PipMood.PROUD && Random.nextFloat() < 0.05f && fx.size < 20) burst(1, 1, 0.5f, 0.4f)
        }
    }

    Box(
        modifier
            .size(size)
            .then(if (!interactive) Modifier else Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { o -> pointer = o; tryAwaitRelease(); pointer = null; hugging = false },
                        onTap = {
                            tick()
                            scope.launch { squash.snapTo(0.8f); squash.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow)) }
                            giggleUntil = t + 1.4f
                            burst(0, 7, 0.5f, 0.45f)
                            curTap?.invoke()
                        },
                        onLongPress = {
                            tick(); hugging = true
                            scope.launch { squash.animateTo(0.86f, tween(180)); squash.animateTo(1f, spring(0.35f, 300f)) }
                            burst(3, 1, 0.5f, 0.35f); burst(0, 6, 0.5f, 0.4f)
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> pointer = o; petting = true },
                        onDragEnd = { petting = false; pointer = null },
                        onDragCancel = { petting = false; pointer = null },
                    ) { ch, _ ->
                        pointer = ch.position
                        if (t - lastHeart > 0.35f) { lastHeart = t; burst(0, 1, ch.position.x / this.size.width, ch.position.y / this.size.height) }
                    }
                })
    ) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val forced = when (effMood) { PipMood.WAVE -> Act.WAVE; PipMood.CELEBRATE -> Act.DANCE; else -> null }
            val a = forced ?: act
            val ap = if (forced != null) ((t % 2f) / 2f) else ((t - actStart) / actDuration(a)).coerceIn(0f, 1f)

            drawPip(
                w = w, t = t, mood = effMood, act = a, ap = ap, squash = squash.value,
                blink = ((t - blinkAt) / 0.18f).let { if (it in 0f..1f) sin(it * PI.toFloat()) else 0f },
                pointer = pointer, petting = petting, hugging = hugging, aura = th.accent,
            )

            // ---- particles
            fx.forEach { p ->
                val age = (t - p.born) / 1.6f
                val px = (p.x + p.vx * age * 0.5f) * w
                val py = (p.y + p.vy * age * 0.5f + age * age * 0.25f) * w
                val alpha = (1f - age).coerceIn(0f, 1f)
                val s = w * (0.055f + 0.02f * abs(p.vx))
                when (p.kind) {
                    0 -> heart(px, py, s, CHEEK.copy(alpha = alpha))
                    1 -> sparkle(px, py, s, Color.White.copy(alpha = alpha), p.spin * age * 300f)
                    2 -> drawRect(confettiColors[p.hue].copy(alpha = alpha), Offset(px, py), Size(s * 0.7f, s * 0.35f))
                    else -> heart(px, py - age * w * 0.2f, w * 0.14f * (0.6f + age), CHEEK.copy(alpha = alpha))
                }
            }
        }
    }
}

private fun actDuration(a: Act) = when (a) { Act.WAVE -> 2.2f; Act.CURL -> 3.2f; Act.SIP -> 2.8f; Act.STRETCH -> 2.4f; Act.HOP -> 1.1f; Act.LOOK -> 2.4f; Act.DANCE -> 2f; Act.NONE -> 1f }

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
private fun ease(f: Float) = (1 - cos(f * PI.toFloat())) / 2f
/** 0→1→0 envelope with short ramps, for actions. */
private fun env(p: Float, ramp: Float = 0.18f) = when { p < ramp -> ease(p / ramp); p > 1 - ramp -> ease((1 - p) / ramp); else -> 1f }

private fun DrawScope.drawPip(
    w: Float, t: Float, mood: PipMood, act: Act, ap: Float, squash: Float, blink: Float,
    pointer: Offset?, petting: Boolean, hugging: Boolean, aura: Color,
) {
    val cx = w / 2
    val R = w * 0.30f
    val e = env(ap)
    // ---- body motion
    val breathe = sin(t * 2.2f) * 0.018f
    val hopY = when (act) {
        Act.HOP -> -sin(ap * PI.toFloat()) * w * 0.12f
        Act.DANCE -> -abs(sin(t * 6f)) * w * 0.05f
        else -> 0f
    } + when (mood) { PipMood.EXCITED -> -abs(sin(t * 7f)) * w * 0.05f; PipMood.SLEEPY -> sin(t * 1.2f) * w * 0.01f; else -> sin(t * 2.2f) * w * 0.012f }
    val stretch = if (act == Act.STRETCH) e * 0.12f else 0f
    val sy = (squash + breathe + stretch) * (if (act == Act.HOP && ap > 0.85f) 0.9f else 1f)
    val sx = (2f - squash) - breathe * 0.6f - stretch * 0.5f
    val lean = when (act) { Act.DANCE -> sin(t * 6f) * 10f; Act.LOOK -> sin(ap * 2 * PI.toFloat()) * 6f; else -> 0f } +
        when (mood) { PipMood.CURIOUS -> 10f; PipMood.THINKING -> -5f; else -> 0f }
    val baseY = w * 0.60f
    val cy = baseY + hopY

    // ---- aura & shadow
    val pulse = 0.5f + 0.5f * sin(t * 1.6f)
    drawCircle(Brush.radialGradient(listOf(aura.copy(alpha = 0.30f + 0.12f * pulse), Color.Transparent), Offset(cx, cy), R * 2.1f), R * 2.1f, Offset(cx, cy))
    val shadowScale = 1f + hopY / (w * 0.25f)
    drawOval(Color.Black.copy(alpha = 0.22f * shadowScale.coerceIn(0.4f, 1f)), Offset(cx - R * 0.85f * shadowScale, baseY + R * 0.98f), Size(R * 1.7f * shadowScale, R * 0.22f))

    withTransform({ rotate(lean, Offset(cx, baseY + R)); scale(sx, sy, Offset(cx, cy + R)) }) {
        // ---- feet
        val stepL = if (act == Act.DANCE) max(0f, sin(t * 6f)) * R * 0.12f else 0f
        val stepR = if (act == Act.DANCE) max(0f, -sin(t * 6f)) * R * 0.12f else 0f
        foot(cx - R * 0.42f, cy + R * 0.88f - stepL, R)
        foot(cx + R * 0.42f, cy + R * 0.88f - stepR, R)

        // ---- arms behind body edges (drawn before body for a tucked look when resting)
        val (la, ra) = armAngles(mood, act, ap, e, t, hugging)
        arm(cx - R * 0.93f, cy + R * 0.12f, R, la, left = true)
        arm(cx + R * 0.93f, cy + R * 0.12f, R, ra, left = false)

        // ---- sprout
        sprout(cx, cy - R * 0.95f, R, t, mood)

        // ---- body
        val body = bodyPath(cx, cy, R, t, mood)
        drawPath(body, Brush.radialGradient(listOf(SKIN_LIGHT, SKIN, SKIN_DEEP), Offset(cx - R * 0.4f, cy - R * 0.5f), R * 2.0f))
        drawOval(BELLY.copy(alpha = 0.55f), Offset(cx - R * 0.55f, cy + R * 0.05f), Size(R * 1.1f, R * 0.8f))
        drawPath(body, Brush.radialGradient(listOf(Color.Transparent, SKIN_DEEP.copy(alpha = 0.35f)), Offset(cx, cy - R * 0.1f), R * 1.3f))
        drawPath(body, Color.White.copy(alpha = 0.55f), style = Stroke(w * 0.010f))
        // glossy highlights
        drawOval(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)), cy - R * 0.95f, cy - R * 0.35f),
            Offset(cx - R * 0.66f, cy - R * 0.9f), Size(R * 0.78f, R * 0.46f))
        drawCircle(Color.White.copy(alpha = 0.9f), R * 0.065f, Offset(cx + R * 0.5f, cy - R * 0.55f))
        drawCircle(Color.White.copy(alpha = 0.6f), R * 0.035f, Offset(cx + R * 0.62f, cy - R * 0.42f))

        // ---- face
        face(cx, cy, R, t, mood, act, ap, blink, pointer, petting, w)

        // ---- props in the right hand
        val rightHand = handPos(cx + R * 0.93f, cy + R * 0.12f, R, ra, left = false)
        when (act) {
            Act.CURL -> dumbbell(rightHand.x, rightHand.y, R, ra)
            Act.SIP -> bottle(rightHand.x, rightHand.y, R, ra)
            else -> Unit
        }
        // ---- mood accessories
        when (mood) {
            PipMood.SLEEPY -> nightcap(cx, cy - R * 0.9f, R, t)
            PipMood.CONCERNED -> sweat(cx + R * 0.72f, cy - R * 0.45f + (t % 1.6f) * R * 0.25f, R)
            PipMood.PROUD, PipMood.CELEBRATE -> crown(cx, cy - R * 1.02f, R, t)
            else -> Unit
        }
    }

    // ---- floating extras (not squashed)
    when (mood) {
        PipMood.SLEEPY -> for (i in 0..2) {
            val f = ((t * 0.35f) + i / 3f) % 1f
            drawZ(cx + R * (0.85f + f * 0.6f), cy - R * (1.0f + f * 1.1f), R * (0.16f + i * 0.05f), Color.White.copy(alpha = (1f - f) * 0.9f))
        }
        PipMood.THINKING -> for (i in 0..2) {
            val on = ((t * 2.5f).toInt() % 4) > i
            drawCircle(Color.White.copy(alpha = if (on) 0.95f else 0.3f), R * (0.07f + i * 0.03f), Offset(cx + R * (0.95f + i * 0.28f), cy - R * (0.95f + i * 0.32f)))
        }
        PipMood.CELEBRATE -> for (i in 0..1) {
            val f = ((t * 0.6f) + i * 0.5f) % 1f
            note(cx + (if (i == 0) -1 else 1) * R * (1.0f + f * 0.3f), cy - R * (0.6f + f * 1.0f), R * 0.22f, Color.White.copy(alpha = 1f - f))
        }
        else -> Unit
    }
    if (petting) for (i in 0..2) {
        val f = ((t * 1.4f) + i / 3f) % 1f
        drawArc(Color.White.copy(alpha = (1f - f) * 0.7f), -40f, 80f, false, Offset(cx + R * (1.05f + f * 0.25f) - R * 0.2f, cy - R * 0.25f - R * 0.2f), Size(R * 0.4f, R * 0.4f), style = Stroke(R * 0.05f, cap = StrokeCap.Round))
    }
}

private fun bodyPath(cx: Float, cy: Float, r: Float, t: Float, mood: PipMood): Path {
    val p = Path()
    val n = 72
    val wob = if (mood == PipMood.EXCITED || mood == PipMood.CELEBRATE) 0.03f else 0.018f
    for (i in 0..n) {
        val th = (i.toFloat() / n) * 2f * PI.toFloat()
        val rr = r * (1f + wob * sin(3f * th + t * 2.4f) + wob * 0.6f * sin(5f * th - t * 1.7f))
        val x = cx + cos(th) * rr * 1.08f
        val s = sin(th)
        // mochi silhouette: rounder top, wider & flatter bottom
        val y = cy + s * rr * (if (s > 0) 0.86f else 1.0f)
        val xw = x + (x - cx) * (if (s > 0) 0.08f * s else 0f)
        if (i == 0) p.moveTo(xw, y) else p.lineTo(xw, y)
    }
    p.close()
    return p
}

private fun DrawScope.foot(x: Float, y: Float, r: Float) {
    drawOval(Brush.verticalGradient(listOf(SKIN, SKIN_DEEP), y - r * 0.1f, y + r * 0.2f), Offset(x - r * 0.24f, y - r * 0.08f), Size(r * 0.48f, r * 0.26f))
}

/** Arm angles in degrees (0 = pointing down, positive = raised outward). */
private fun armAngles(mood: PipMood, act: Act, ap: Float, e: Float, t: Float, hugging: Boolean): Pair<Float, Float> {
    if (hugging) return -70f to -70f
    val restL = 18f + sin(t * 2.2f) * 4f
    val restR = 18f + sin(t * 2.2f + 1f) * 4f
    return when (act) {
        Act.WAVE -> restL to lerp(restR, 150f + sin(t * 14f) * 22f, e)
        Act.CURL -> restL to lerp(restR, 35f + (0.5f + 0.5f * sin(ap * 6 * PI.toFloat() - PI.toFloat() / 2)) * 95f, e)
        Act.SIP -> restL to lerp(restR, 125f, e)
        Act.STRETCH -> lerp(restL, 170f, e) to lerp(restR, 170f, e)
        Act.DANCE -> (80f + sin(t * 6f) * 60f) to (80f - sin(t * 6f) * 60f)
        Act.HOP -> lerp(restL, 70f, sin(ap * PI.toFloat())) to lerp(restR, 70f, sin(ap * PI.toFloat()))
        else -> when (mood) {
            PipMood.EXCITED, PipMood.CELEBRATE -> (140f + sin(t * 10f) * 20f) to (140f - sin(t * 10f) * 20f)
            PipMood.PROUD -> 55f to 55f
            PipMood.THINKING -> restL to 118f
            PipMood.CONCERNED -> 8f to 8f
            PipMood.LOVE -> -40f to -40f
            PipMood.SLEEPY -> 4f to 4f
            else -> restL to restR
        }
    }
}

private fun handPos(sx: Float, sy: Float, r: Float, angleDeg: Float, left: Boolean): Offset {
    val a = Math.toRadians(angleDeg.toDouble()).toFloat()
    val len = r * 0.5f
    val dir = if (left) -1f else 1f
    return Offset(sx + dir * sin(a) * len, sy + cos(a) * len)
}

private fun DrawScope.arm(sx: Float, sy: Float, r: Float, angleDeg: Float, left: Boolean) {
    val h = handPos(sx, sy, r, angleDeg, left)
    drawLine(Brush.linearGradient(listOf(SKIN, SKIN_DEEP), Offset(sx, sy), h), Offset(sx, sy), h, r * 0.26f, StrokeCap.Round)
    drawCircle(SKIN_LIGHT.copy(alpha = 0.6f), r * 0.07f, Offset(h.x - r * 0.03f, h.y - r * 0.03f))
}

private fun DrawScope.sprout(x: Float, y: Float, r: Float, t: Float, mood: PipMood) {
    val droop = when (mood) { PipMood.SLEEPY -> 35f; PipMood.CONCERNED -> 25f; else -> 0f }
    val perk = when (mood) { PipMood.EXCITED, PipMood.CELEBRATE, PipMood.PROUD -> -12f; else -> 0f }
    val spin = if (mood == PipMood.CELEBRATE) sin(t * 5f) * 25f else sin(t * 1.6f) * 8f
    rotate(spin, Offset(x, y + r * 0.1f)) {
        val stem = Path().apply { moveTo(x, y + r * 0.12f); quadraticBezierTo(x + r * 0.06f, y - r * 0.12f, x, y - r * 0.28f) }
        drawPath(stem, LEAF_DEEP, style = Stroke(r * 0.075f, cap = StrokeCap.Round))
        for (dir in listOf(-1f, 1f)) {
            rotate(dir * (droop + perk) * -1f, Offset(x, y - r * 0.26f)) {
                val l = Path().apply {
                    moveTo(x, y - r * 0.26f)
                    quadraticBezierTo(x + dir * r * 0.2f, y - r * 0.56f, x + dir * r * 0.46f, y - r * 0.38f)
                    quadraticBezierTo(x + dir * r * 0.22f, y - r * 0.18f, x, y - r * 0.26f); close()
                }
                drawPath(l, Brush.linearGradient(listOf(LEAF, LEAF_DEEP), Offset(x, y - r * 0.56f), Offset(x + dir * r * 0.46f, y - r * 0.2f)))
                drawPath(l, Color.White.copy(alpha = 0.35f), style = Stroke(r * 0.02f))
            }
        }
        // a tiny flower blooms on the sprout when Pip is proud
        if (mood == PipMood.PROUD || mood == PipMood.CELEBRATE) {
            val fy = y - r * 0.34f
            for (k in 0 until 5) {
                val a = k * 72f + t * 40f
                val rad = Math.toRadians(a.toDouble())
                drawCircle(Color(0xFFFFC2D6), r * 0.075f, Offset(x + cos(rad).toFloat() * r * 0.09f, fy + sin(rad).toFloat() * r * 0.09f))
            }
            drawCircle(Color(0xFFFFD34D), r * 0.06f, Offset(x, fy))
        }
    }
}

private fun DrawScope.face(cx: Float, cy: Float, r: Float, t: Float, mood: PipMood, act: Act, ap: Float, blink: Float, pointer: Offset?, petting: Boolean, w: Float) {
    val eyeY = cy - r * 0.08f
    val dx = r * 0.38f
    val ew = r * 0.34f; val eh = r * 0.42f
    // where the pupils look
    val (lx, ly) = when {
        pointer != null -> Pair(((pointer.x - cx) / w).coerceIn(-0.5f, 0.5f) * r * 0.22f, ((pointer.y - eyeY) / w).coerceIn(-0.5f, 0.5f) * r * 0.2f)
        act == Act.LOOK -> Pair(sin(ap * 2 * PI.toFloat()) * r * 0.1f, 0f)
        act == Act.SIP -> Pair(r * 0.05f, -r * 0.05f)
        mood == PipMood.THINKING -> Pair(-r * 0.07f, -r * 0.09f)
        else -> Pair(sin(t * 0.7f) * r * 0.03f, 0f)
    }
    val closedHappy = petting || mood == PipMood.EXCITED || (act == Act.SIP && ap in 0.3f..0.75f)
    val lidBase = when (mood) { PipMood.SLEEPY -> 1f; PipMood.CONCERNED -> 0.12f; PipMood.NEUTRAL -> 0.08f; else -> 0f }
    val lid = max(lidBase, blink)

    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * dx
        val big = if (mood == PipMood.CURIOUS && side > 0) 1.2f else 1f
        when {
            mood == PipMood.LOVE && !petting -> heart(ex, eyeY + r * 0.02f, r * 0.2f * (1f + 0.08f * sin(t * 8f)), CHEEK)
            mood == PipMood.PROUD || mood == PipMood.CELEBRATE -> sparkle(ex, eyeY, r * 0.2f, Color(0xFFFFD34D), t * 60f).also {
                drawCircle(Color.White, r * 0.04f, Offset(ex - r * 0.05f, eyeY - r * 0.05f))
            }
            closedHappy -> {
                val a = Path().apply { moveTo(ex - ew * 0.5f, eyeY + eh * 0.1f); quadraticBezierTo(ex, eyeY - eh * 0.55f, ex + ew * 0.5f, eyeY + eh * 0.1f) }
                drawPath(a, INK, style = Stroke(r * 0.085f, cap = StrokeCap.Round))
            }
            lid >= 0.98f -> {
                val a = Path().apply { moveTo(ex - ew * 0.45f, eyeY); quadraticBezierTo(ex, eyeY + eh * 0.35f, ex + ew * 0.45f, eyeY) }
                drawPath(a, INK, style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            }
            else -> {
                val h = eh * big
                val ww = ew * big
                drawOval(Color.White, Offset(ex - ww / 2, eyeY - h / 2), Size(ww, h))
                val pr = ww * 0.40f
                val pc = Offset(ex + lx, eyeY + ly + r * 0.02f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF5A3B6E), INK), Offset(pc.x, pc.y + pr * 0.3f), pr * 1.2f), pr, pc)
                drawCircle(Color.White, pr * 0.42f, Offset(pc.x - pr * 0.32f, pc.y - pr * 0.36f))
                drawCircle(Color.White.copy(alpha = 0.8f), pr * 0.18f, Offset(pc.x + pr * 0.36f, pc.y + pr * 0.3f))
                // eyelid (skin) coming down
                if (lid > 0.01f) drawRect(SKIN, Offset(ex - ww, eyeY - h / 2 - 2f), Size(ww * 2, h * lid + 2f))
                if (lid > 0.01f) drawLine(INK.copy(alpha = 0.6f), Offset(ex - ww * 0.5f, eyeY - h / 2 + h * lid), Offset(ex + ww * 0.5f, eyeY - h / 2 + h * lid), r * 0.035f, StrokeCap.Round)
            }
        }
        // brows
        val browY = eyeY - eh * 0.72f
        when (mood) {
            PipMood.CONCERNED -> drawLine(INK, Offset(ex - side * ew * 0.55f, browY + r * 0.04f), Offset(ex + side * ew * 0.35f, browY - r * 0.08f), r * 0.055f, StrokeCap.Round)
            PipMood.CURIOUS -> if (side > 0) drawLine(INK, Offset(ex - ew * 0.4f, browY - r * 0.1f), Offset(ex + ew * 0.4f, browY - r * 0.16f), r * 0.05f, StrokeCap.Round)
            PipMood.THINKING -> drawLine(INK, Offset(ex - ew * 0.4f, browY - r * 0.02f * side), Offset(ex + ew * 0.4f, browY + r * 0.02f * side), r * 0.05f, StrokeCap.Round)
            else -> Unit
        }
        // cheeks
        val blush = when { petting -> 0.85f; mood == PipMood.LOVE || mood == PipMood.PROUD -> 0.7f; mood == PipMood.EXCITED -> 0.6f; else -> 0.42f }
        drawOval(CHEEK.copy(alpha = blush), Offset(cx + side * r * 0.62f - r * 0.15f, cy + r * 0.18f), Size(r * 0.3f, r * 0.15f))
    }

    // mouth
    val my = cy + r * 0.3f
    when {
        petting -> { // :3
            val m = Path().apply {
                moveTo(cx - r * 0.14f, my); quadraticBezierTo(cx - r * 0.07f, my + r * 0.09f, cx, my)
                quadraticBezierTo(cx + r * 0.07f, my + r * 0.09f, cx + r * 0.14f, my)
            }
            drawPath(m, INK, style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        }
        mood == PipMood.TALKING -> {
            val open = (0.35f + 0.65f * abs(sin(t * 13f))) * r * 0.18f
            drawOval(Color(0xFF4A1426), Offset(cx - r * 0.12f, my - r * 0.02f), Size(r * 0.24f, open))
            drawOval(Color(0xFFFF7A93), Offset(cx - r * 0.07f, my - r * 0.02f + open * 0.5f), Size(r * 0.14f, open * 0.45f))
        }
        mood == PipMood.EXCITED || mood == PipMood.CELEBRATE || mood == PipMood.PROUD || mood == PipMood.LOVE -> {
            val m = Path().apply { moveTo(cx - r * 0.2f, my - r * 0.04f); quadraticBezierTo(cx, my + r * 0.36f, cx + r * 0.2f, my - r * 0.04f); close() }
            drawPath(m, Color(0xFF4A1426))
            drawOval(Color(0xFFFF7A93), Offset(cx - r * 0.09f, my + r * 0.08f), Size(r * 0.18f, r * 0.1f))
            drawRect(Color.White.copy(alpha = 0.9f), Offset(cx - r * 0.11f, my - r * 0.035f), Size(r * 0.22f, r * 0.04f))
        }
        mood == PipMood.SLEEPY -> drawOval(INK, Offset(cx - r * 0.05f, my), Size(r * 0.1f, r * (0.07f + 0.04f * sin(t * 1.3f))))
        mood == PipMood.CURIOUS -> drawCircle(INK, r * 0.06f, Offset(cx + r * 0.02f, my + r * 0.03f), style = Stroke(r * 0.045f))
        mood == PipMood.CONCERNED -> {
            val m = Path().apply { moveTo(cx - r * 0.15f, my + r * 0.07f); cubicTo(cx - r * 0.07f, my - r * 0.02f, cx + r * 0.05f, my + r * 0.12f, cx + r * 0.15f, my + r * 0.04f) }
            drawPath(m, INK, style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        }
        mood == PipMood.THINKING -> drawLine(INK, Offset(cx - r * 0.1f, my + r * 0.04f), Offset(cx + r * 0.12f, my), r * 0.05f, StrokeCap.Round)
        act == Act.SIP && ap in 0.3f..0.75f -> drawCircle(INK, r * 0.045f, Offset(cx + r * 0.05f, my + r * 0.02f))
        act == Act.CURL -> { // effort face
            val m = Path().apply { moveTo(cx - r * 0.14f, my + r * 0.02f); lineTo(cx + r * 0.14f, my + r * 0.02f) }
            drawPath(m, INK, style = Stroke(r * 0.055f, cap = StrokeCap.Round))
            drawRect(Color.White, Offset(cx - r * 0.1f, my - r * 0.01f), Size(r * 0.2f, r * 0.03f))
        }
        else -> {
            val m = Path().apply { moveTo(cx - r * 0.15f, my - r * 0.01f); quadraticBezierTo(cx, my + r * (if (mood == PipMood.NEUTRAL) 0.07f else 0.17f), cx + r * 0.15f, my - r * 0.01f) }
            drawPath(m, INK, style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.dumbbell(x: Float, y: Float, r: Float, angle: Float) {
    rotate(-angle * 0.3f, Offset(x, y)) {
        drawLine(Color(0xFF6B6B7A), Offset(x - r * 0.22f, y), Offset(x + r * 0.22f, y), r * 0.06f, StrokeCap.Round)
        for (s in listOf(-1f, 1f)) drawRoundRect(Color(0xFF3A3A48), Offset(x + s * r * 0.22f - r * 0.07f, y - r * 0.13f), Size(r * 0.14f, r * 0.26f), androidx.compose.ui.geometry.CornerRadius(r * 0.04f))
    }
}

private fun DrawScope.bottle(x: Float, y: Float, r: Float, angle: Float) {
    rotate(180f - angle * 0.9f, Offset(x, y)) {
        drawRoundRect(Color(0xFF4FC3FF).copy(alpha = 0.85f), Offset(x - r * 0.09f, y - r * 0.05f), Size(r * 0.18f, r * 0.34f), androidx.compose.ui.geometry.CornerRadius(r * 0.06f))
        drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(x - r * 0.05f, y), Size(r * 0.04f, r * 0.24f), androidx.compose.ui.geometry.CornerRadius(r * 0.02f))
        drawRect(Color(0xFF2A7FB8), Offset(x - r * 0.05f, y + r * 0.29f), Size(r * 0.1f, r * 0.07f))
    }
}

private fun DrawScope.nightcap(x: Float, y: Float, r: Float, t: Float) {
    val tipX = x + r * 0.55f + sin(t * 1.3f) * r * 0.05f
    val cap = Path().apply { moveTo(x - r * 0.5f, y + r * 0.1f); quadraticBezierTo(x, y - r * 0.5f, tipX, y - r * 0.15f); lineTo(x + r * 0.45f, y + r * 0.12f); close() }
    drawPath(cap, Brush.linearGradient(listOf(Color(0xFF6C7BFF), Color(0xFF3D3FB8)), Offset(x, y - r * 0.4f), Offset(x, y + r * 0.1f)))
    drawRoundRect(Color.White, Offset(x - r * 0.52f, y + r * 0.04f), Size(r * 1.0f, r * 0.14f), androidx.compose.ui.geometry.CornerRadius(r * 0.07f))
    drawCircle(Color.White, r * 0.1f, Offset(tipX, y - r * 0.15f))
}

private fun DrawScope.crown(x: Float, y: Float, r: Float, t: Float) {
    val c = Path().apply {
        moveTo(x - r * 0.28f, y); lineTo(x - r * 0.3f, y - r * 0.24f); lineTo(x - r * 0.14f, y - r * 0.1f); lineTo(x, y - r * 0.3f)
        lineTo(x + r * 0.14f, y - r * 0.1f); lineTo(x + r * 0.3f, y - r * 0.24f); lineTo(x + r * 0.28f, y); close()
    }
    drawPath(c, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFB020)), y - r * 0.3f, y))
    drawCircle(Color(0xFFFF5C8A), r * 0.035f, Offset(x, y - r * 0.08f))
    sparkle(x + r * 0.34f, y - r * 0.3f, r * 0.07f * (0.6f + 0.4f * sin(t * 5f)), Color.White, t * 90f)
}

private fun DrawScope.sweat(x: Float, y: Float, r: Float) {
    val d = Path().apply { moveTo(x, y - r * 0.12f); quadraticBezierTo(x + r * 0.1f, y + r * 0.02f, x, y + r * 0.06f); quadraticBezierTo(x - r * 0.1f, y + r * 0.02f, x, y - r * 0.12f); close() }
    drawPath(d, Color(0xFF8FD8FF))
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.02f, Offset(x - r * 0.025f, y - r * 0.01f))
}

private fun DrawScope.note(x: Float, y: Float, s: Float, c: Color) {
    drawCircle(c, s * 0.28f, Offset(x, y))
    drawLine(c, Offset(x + s * 0.26f, y), Offset(x + s * 0.26f, y - s), s * 0.12f)
    drawLine(c, Offset(x + s * 0.26f, y - s), Offset(x + s * 0.6f, y - s * 0.8f), s * 0.12f)
}

internal fun DrawScope.heart(x: Float, y: Float, s: Float, c: Color) {
    val p = Path().apply {
        moveTo(x, y + s * 0.35f)
        cubicTo(x - s * 0.9f, y - s * 0.25f, x - s * 0.35f, y - s * 0.9f, x, y - s * 0.35f)
        cubicTo(x + s * 0.35f, y - s * 0.9f, x + s * 0.9f, y - s * 0.25f, x, y + s * 0.35f)
        close()
    }
    drawPath(p, c)
}

internal fun DrawScope.sparkle(x: Float, y: Float, s: Float, c: Color, rot: Float) {
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

