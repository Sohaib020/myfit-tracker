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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
 * Pip v3 — the mint plush buddy: soft mint body, navy striped sweatband, curly antenna, cream belly,
 * navy sneakers, big glossy eyes and pink cheeks. Drawn fully in code as a 2.5D figure: every face
 * feature, the belly and the headband sit on a 3D ellipsoid, so Pip really turns its head toward your
 * finger, looks up while thinking and can spin all the way round.
 */
enum class PipMood {
    HAPPY, EXCITED, SLEEPY, THINKING, CONCERNED, WAVE, PROUD, NEUTRAL, LOVE, TALKING, CURIOUS, CELEBRATE,
    TRAIN, HYDRATE, FUEL, LETS_GO, SURPRISED, WINK, DANCE, SPIN, FLEX, LAUGH,
}

/** Reactions Pip picks from on each tap (never the same one twice in a row). */
private val TAP_REACTIONS = listOf(
    PipMood.WAVE, PipMood.LAUGH, PipMood.WINK, PipMood.SURPRISED, PipMood.DANCE, PipMood.SPIN, PipMood.FLEX,
    PipMood.LETS_GO, PipMood.LOVE, PipMood.CELEBRATE, PipMood.HYDRATE, PipMood.TRAIN, PipMood.FUEL, PipMood.EXCITED,
)
private val IDLE_ACTS = listOf(PipMood.WAVE, PipMood.HYDRATE, PipMood.WINK, PipMood.DANCE, PipMood.CURIOUS, PipMood.FLEX)

private fun reactionLength(m: PipMood) = when (m) {
    PipMood.SPIN -> 1.3f; PipMood.CELEBRATE -> 2.2f; PipMood.DANCE -> 2.6f; PipMood.LAUGH -> 1.9f
    PipMood.SURPRISED -> 1.5f; PipMood.TRAIN, PipMood.HYDRATE, PipMood.FUEL -> 2.6f; else -> 2.1f
}

private data class Fx(val born: Float, val x: Float, val y: Float, val vx: Float, val vy: Float, val kind: Int, val spin: Float, val hue: Int)

// ---- Pip's palette (theme-independent so Pip is always Pip; the aura follows the theme)
private val MINT_HI = Color(0xFFE4FFF1)
private val MINT = Color(0xFFA6EDCB)
private val MINT_MID = Color(0xFF7FDDB2)
private val MINT_DEEP = Color(0xFF4FB98F)
private val CREAM = Color(0xFFFFF4DC)
private val CREAM_SHADE = Color(0xFFF1DDB5)
private val NAVY = Color(0xFF1E2A5A)
private val NAVY_HI = Color(0xFF3A4C8E)
private val EYE = Color(0xFF131A3A)
private val EYE_BLUE = Color(0xFF3B5BA8)
private val CHEEK = Color(0xFFFF8FAB)
private val MOUTH = Color(0xFF6B2238)
private val TONGUE = Color(0xFFFF7E98)
private val WHITE = Color.White
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
    var blinkAt by remember { mutableFloatStateOf(2f) }
    val squash = remember { Animatable(1f) }
    var petting by remember { mutableStateOf(false) }
    var hugging by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var lastHeart by remember { mutableFloatStateOf(0f) }
    var reaction by remember { mutableStateOf<PipMood?>(null) }
    var reactionStart by remember { mutableFloatStateOf(0f) }
    var lastReaction by remember { mutableStateOf<PipMood?>(null) }
    var idleAct by remember { mutableStateOf<PipMood?>(null) }
    var idleStart by remember { mutableFloatStateOf(0f) }
    var nextIdle by remember { mutableFloatStateOf(4f) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var boxW by remember { mutableFloatStateOf(1f) }

    fun burst(kind: Int, n: Int, cx: Float, cy: Float, spread: Float = 1f) {
        repeat(n) {
            val a = Random.nextFloat() * (2 * PI).toFloat()
            val sp = (0.35f + Random.nextFloat() * 0.6f) * spread
            fx += Fx(t, cx, cy, cos(a) * sp, sin(a) * sp - 0.5f, kind, Random.nextFloat() * 2 - 1, Random.nextInt(5))
        }
        if (fx.size > 70) fx.removeRange(0, fx.size - 70)
    }

    fun startReaction(m: PipMood) {
        reaction = m; reactionStart = t; lastReaction = m; idleAct = null
        when (m) {
            PipMood.CELEBRATE -> burst(2, 26, 0.5f, 0.25f, 1.3f)
            PipMood.LOVE -> { burst(3, 1, 0.5f, 0.3f); burst(0, 6, 0.5f, 0.4f) }
            PipMood.SURPRISED -> burst(6, 1, 0.78f, 0.12f, 0.05f)
            PipMood.SPIN, PipMood.FLEX, PipMood.LETS_GO, PipMood.WINK -> burst(1, 6, 0.5f, 0.4f)
            PipMood.LAUGH -> burst(1, 3, 0.5f, 0.35f)
            else -> Unit
        }
    }

    val active = reaction?.takeIf { t - reactionStart < reactionLength(it) }
    val effMood = when {
        hugging || petting -> PipMood.LOVE
        active != null -> active
        talking -> PipMood.TALKING
        idleAct != null -> idleAct!!
        else -> mood
    }
    val moodNow by rememberUpdatedState(effMood)
    val baseMood by rememberUpdatedState(mood)
    val idleOn by rememberUpdatedState(idleActions)

    // One frame loop: time, blinks, head turning, idle acts and ambient particles (never in the draw pass).
    LaunchedEffect(Unit) {
        var start = -1L
        var prev = 0f
        while (true) withFrameMillis { ms ->
            if (start < 0) start = ms
            t = (ms - start) / 1000f
            val dt = (t - prev).coerceIn(0f, 0.05f); prev = t
            if (fx.isNotEmpty()) fx.removeAll { t - it.born > 1.7f }
            if (t > blinkAt + 0.16f) blinkAt = t + 1.8f + Random.nextFloat() * 3.4f

            // idle acts only when nothing else is going on
            val m = moodNow
            val calm = baseMood in setOf(PipMood.HAPPY, PipMood.NEUTRAL, PipMood.WAVE, PipMood.PROUD)
            if (idleAct != null && t - idleStart > 2.4f) { idleAct = null; nextIdle = t + 4f + Random.nextFloat() * 5f }
            if (idleAct == null && idleOn && calm && reaction?.let { t - reactionStart < reactionLength(it) } != true &&
                !petting && !hugging && pointer == null && t > nextIdle
            ) { idleAct = IDLE_ACTS.random(); idleStart = t }

            // where Pip looks (yaw = turn, pitch = nod)
            val p = pointer
            val (yT, pT) = when {
                p != null -> ((p.x / boxW - 0.5f) * 1.5f).coerceIn(-0.75f, 0.75f) to ((0.45f - p.y / boxW) * 0.9f).coerceIn(-0.35f, 0.35f)
                m == PipMood.THINKING -> -0.32f to 0.22f
                m == PipMood.CURIOUS -> 0.35f + sin(t * 1.5f) * 0.05f to 0.08f
                m == PipMood.SLEEPY -> 0f to -0.18f
                m == PipMood.CONCERNED -> 0.12f to -0.1f
                m == PipMood.DANCE -> sin(t * 5f) * 0.3f to 0f
                else -> sin(t * 0.45f) * 0.2f + sin(t * 1.3f) * 0.05f to sin(t * 0.6f) * 0.06f
            }
            val k = min(1f, dt * if (p != null) 10f else 4f)
            yaw += (yT - yaw) * k; pitch += (pT - pitch) * k

            when (m) {
                PipMood.CELEBRATE -> if (Random.nextFloat() < 0.22f && fx.size < 60) burst(2, 1, 0.5f, 0.1f)
                PipMood.PROUD, PipMood.EXCITED -> if (Random.nextFloat() < 0.05f && fx.size < 20) burst(1, 1, 0.5f, 0.35f)
                PipMood.DANCE -> if (Random.nextFloat() < 0.05f) burst(4, 1, if (Random.nextBoolean()) 0.2f else 0.8f, 0.3f, 0.3f)
                PipMood.LOVE -> if (Random.nextFloat() < 0.04f) burst(0, 1, 0.5f, 0.3f, 0.6f)
                else -> Unit
            }
        }
    }

    Box(
        modifier
            .size(size)
            .onSizeChanged { boxW = it.width.toFloat().coerceAtLeast(1f) }
            .then(if (!interactive) Modifier else Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { o -> pointer = o; tryAwaitRelease(); pointer = null; hugging = false },
                        onTap = {
                            tick()
                            scope.launch { squash.snapTo(0.8f); squash.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow)) }
                            val next = TAP_REACTIONS.filter { it != lastReaction }.random()
                            startReaction(next)
                            curTap?.invoke()
                        },
                        onLongPress = {
                            tick(); hugging = true
                            scope.launch { squash.animateTo(0.86f, tween(180)); squash.animateTo(1f, spring(0.35f, 300f)) }
                            burst(3, 1, 0.5f, 0.3f); burst(0, 6, 0.5f, 0.4f)
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
            val prog = when {
                active != null -> ((t - reactionStart) / reactionLength(active)).coerceIn(0f, 1f)
                idleAct != null && effMood == idleAct -> ((t - idleStart) / 2.4f).coerceIn(0f, 1f)
                else -> -1f   // looping mood
            }
            val blink = ((t - blinkAt) / 0.16f).let { if (it in 0f..1f) sin(it * PI.toFloat()) else 0f }
            drawPip(w, t, effMood, prog, squash.value, blink, yaw, pitch, petting, hugging, talking, th.accent)

            fx.forEach { p ->
                val age = (t - p.born) / 1.7f
                val px = (p.x + p.vx * age * 0.5f) * w
                val py = (p.y + p.vy * age * 0.5f + age * age * 0.25f) * w
                val alpha = (1f - age).coerceIn(0f, 1f)
                val s = w * (0.055f + 0.02f * abs(p.vx))
                when (p.kind) {
                    0 -> heart(px, py, s, CHEEK.copy(alpha = alpha))
                    1 -> sparkle(px, py, s, WHITE.copy(alpha = alpha), p.spin * age * 300f)
                    2 -> rotate(p.spin * age * 500f, Offset(px, py)) { drawRect(confettiColors[p.hue].copy(alpha = alpha), Offset(px, py), Size(s * 0.8f, s * 0.38f)) }
                    3 -> heart(px, py - age * w * 0.2f, w * 0.14f * (0.6f + age), CHEEK.copy(alpha = alpha))
                    4 -> note(px, p.y * w - age * w * 0.3f, w * 0.07f, WHITE.copy(alpha = alpha))
                    6 -> bang(p.x * w, p.y * w - age * w * 0.05f, w * 0.12f * (0.8f + 0.4f * min(1f, age * 5f)), Color(0xFFFFD34D).copy(alpha = alpha))
                    else -> Unit
                }
            }
        }
    }
}

// ============================================================ geometry

private fun ease(f: Float) = (1 - cos(f.coerceIn(0f, 1f) * PI.toFloat())) / 2f
private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
/** 0→1→0 envelope with short ramps. */
private fun env(p: Float, ramp: Float = 0.16f) = when { p < 0f -> 1f; p < ramp -> ease(p / ramp); p > 1 - ramp -> ease((1 - p) / ramp); else -> 1f }

/** Maps points on Pip's ellipsoid body (longitude, latitude in radians) to the screen. */
private class Globe(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val yaw: Float, val pitch: Float) {
    fun at(lon: Float, lat: Float): Offset {
        val a = lon + yaw
        val x = cx + rx * sin(a) * cos(lat)
        val y = cy - ry * (sin(lat) * cos(pitch) + cos(lat) * cos(a) * sin(pitch))
        return Offset(x, y)
    }
    /** > 0 when the point faces the viewer. */
    fun z(lon: Float, lat: Float): Float {
        val a = lon + yaw
        return cos(a) * cos(lat) * cos(pitch) - sin(lat) * sin(pitch)
    }
    /** Horizontal foreshortening of a small feature at this spot. */
    fun fx(lon: Float, lat: Float) = abs(cos(lon + yaw)).coerceIn(0.05f, 1f)
}

private enum class Eyes { OPEN, HAPPY, SLEEP, WINK, BIG, HEART, DETERMINED, STAR }
private enum class Mouth { SMILE, BIG_SMILE, OPEN, O, FLAT, WORRY, CAT, SMALL, GRIN, TONGUE }
private enum class Brows { NONE, DETERMINED, WORRIED, RAISED, THINK }
private enum class Prop { NONE, DUMBBELL, BOTTLE, BOWL, PILLOW, HEART, THUMB, CHIN, STAR }

private class Pose(
    val armL: Float, val armR: Float,
    val eyes: Eyes, val mouth: Mouth, val brows: Brows,
    val hop: Float = 0f, val lean: Float = 0f, val spin: Float = 0f,
    val prop: Prop = Prop.NONE, val blush: Float = 0.5f, val shake: Float = 0f, val flexL: Boolean = false, val flexR: Boolean = false,
)

private fun pose(m: PipMood, t: Float, p: Float, hugging: Boolean): Pose {
    val e = env(p)
    val restL = 14f + sin(t * 2.1f) * 4f
    val restR = 14f + sin(t * 2.1f + 1f) * 4f
    val loop = if (p < 0f) (t % 2f) / 2f else p
    if (hugging) return Pose(-62f, -62f, Eyes.HAPPY, Mouth.CAT, Brows.NONE, prop = Prop.HEART, blush = 0.95f)
    return when (m) {
        PipMood.HAPPY, PipMood.NEUTRAL -> Pose(restL, restR, Eyes.OPEN, if (m == PipMood.NEUTRAL) Mouth.SMALL else Mouth.SMILE, Brows.NONE)
        PipMood.WAVE -> Pose(restL, lerp(restR, 150f + sin(t * 13f) * 24f, e), Eyes.OPEN, Mouth.BIG_SMILE, Brows.NONE, lean = -4f * e)
        PipMood.TRAIN -> Pose(restL, lerp(restR, 40f + (0.5f + 0.5f * sin(t * 6f - 1.6f)) * 100f, e), Eyes.DETERMINED, Mouth.GRIN, Brows.DETERMINED, prop = Prop.DUMBBELL, blush = 0.6f)
        PipMood.CELEBRATE -> {
            val jump = if (p < 0f) abs(sin(t * 4f)) else sin((p * 2f % 1f) * PI.toFloat())
            Pose(150f + sin(t * 12f) * 15f, 150f - sin(t * 12f) * 15f, Eyes.HAPPY, Mouth.OPEN, Brows.NONE, hop = jump * 0.16f, blush = 0.75f)
        }
        PipMood.EXCITED -> Pose(135f + sin(t * 10f) * 20f, 135f - sin(t * 10f) * 20f, Eyes.STAR, Mouth.BIG_SMILE, Brows.NONE, hop = abs(sin(t * 7f)) * 0.06f, blush = 0.7f)
        PipMood.THINKING -> Pose(restL, -115f, Eyes.OPEN, Mouth.SMALL, Brows.THINK, lean = -4f, prop = Prop.CHIN)
        PipMood.LOVE -> Pose(-60f, -60f, Eyes.HAPPY, Mouth.SMILE, Brows.NONE, prop = Prop.HEART, blush = 0.95f, lean = sin(t * 2f) * 4f)
        PipMood.HYDRATE -> Pose(restL, lerp(restR, if (loop in 0.35f..0.75f) 128f else 95f, e), if (loop in 0.35f..0.75f) Eyes.HAPPY else Eyes.WINK, if (loop in 0.35f..0.75f) Mouth.O else Mouth.SMILE, Brows.NONE, prop = Prop.BOTTLE)
        PipMood.FUEL -> Pose(-48f, -48f, Eyes.OPEN, Mouth.BIG_SMILE, Brows.NONE, prop = Prop.BOWL, hop = abs(sin(t * 5f)) * 0.02f)
        PipMood.SLEEPY -> Pose(-58f, -58f, Eyes.SLEEP, Mouth.SMALL, Brows.NONE, prop = Prop.PILLOW, lean = sin(t * 1.1f) * 5f + 6f, blush = 0.55f)
        PipMood.LETS_GO -> Pose(restL, lerp(restR, 100f, e), Eyes.WINK, Mouth.TONGUE, Brows.NONE, prop = Prop.THUMB, hop = sin(loop * PI.toFloat()) * 0.04f)
        PipMood.LAUGH -> Pose(-30f, -30f, Eyes.HAPPY, Mouth.OPEN, Brows.NONE, shake = 1f, lean = sin(t * 18f) * 3f, blush = 0.8f, hop = abs(sin(t * 16f)) * 0.015f)
        PipMood.WINK -> Pose(restL, lerp(restR, 60f, e), Eyes.WINK, Mouth.TONGUE, Brows.NONE, lean = 6f * e)
        PipMood.SURPRISED -> Pose(lerp(restL, 110f, e), lerp(restR, 110f, e), Eyes.BIG, Mouth.O, Brows.RAISED, hop = sin(min(1f, p * 3f) * PI.toFloat()).coerceAtLeast(0f) * 0.08f)
        PipMood.DANCE -> Pose(80f + sin(t * 6f) * 60f, 80f - sin(t * 6f) * 60f, Eyes.HAPPY, Mouth.BIG_SMILE, Brows.NONE, hop = abs(sin(t * 6f)) * 0.05f, lean = sin(t * 6f) * 11f)
        PipMood.SPIN -> Pose(lerp(restL, 95f, e), lerp(restR, 95f, e), Eyes.HAPPY, Mouth.BIG_SMILE, Brows.NONE, spin = if (p < 0f) (t % 1.3f) / 1.3f else ease(p), hop = sin(max(0f, p) * PI.toFloat()) * 0.06f)
        PipMood.FLEX -> Pose(lerp(restL, 115f, e), lerp(restR, 115f, e), Eyes.DETERMINED, Mouth.GRIN, Brows.DETERMINED, flexL = e > 0.5f, flexR = e > 0.5f, blush = 0.7f)
        PipMood.PROUD -> Pose(lerp(restL, 40f, 1f), 40f, Eyes.HAPPY, Mouth.BIG_SMILE, Brows.NONE, prop = Prop.STAR, blush = 0.7f)
        PipMood.CONCERNED -> Pose(-18f, -18f, Eyes.OPEN, Mouth.WORRY, Brows.WORRIED)
        PipMood.CURIOUS -> Pose(restL, 30f, Eyes.OPEN, Mouth.O, Brows.RAISED, lean = 9f)
        PipMood.TALKING -> Pose(restL + sin(t * 3f) * 8f, restR + 20f + sin(t * 2.4f) * 16f, Eyes.OPEN, Mouth.SMILE, Brows.NONE)
    }
}

// ============================================================ drawing

private fun DrawScope.drawPip(
    w: Float, t: Float, mood: PipMood, prog: Float, squash: Float, blink: Float,
    yaw0: Float, pitch0: Float, petting: Boolean, hugging: Boolean, talking: Boolean, aura: Color,
) {
    val ps = pose(mood, t, prog, hugging)
    val R = w * 0.29f
    val cx = w / 2 + if (ps.shake > 0f) sin(t * 40f) * w * 0.006f else 0f
    val baseY = w * 0.56f
    val breathe = sin(t * 2.1f) * 0.016f
    val hop = ps.hop * w
    val cy = baseY - hop + sin(t * 2.1f) * w * 0.006f
    val yaw = yaw0 + ps.spin * 2f * PI.toFloat()
    val g = Globe(cx, cy, R, R * 1.05f, yaw, pitch0)
    val sy = squash + breathe
    val sx = (2f - squash) - breathe * 0.6f

    // ---- aura & ground shadow
    val pulse = 0.5f + 0.5f * sin(t * 1.6f)
    drawCircle(Brush.radialGradient(listOf(aura.copy(alpha = 0.26f + 0.1f * pulse), Color.Transparent), Offset(cx, cy), R * 2.1f), R * 2.1f, Offset(cx, cy))
    val sh = (1f - ps.hop * 3f).coerceIn(0.45f, 1f)
    drawOval(Color.Black.copy(alpha = 0.22f * sh), Offset(cx - R * 0.9f * sh, baseY + R * 1.28f), Size(R * 1.8f * sh, R * 0.22f))

    withTransform({ rotate(ps.lean, Offset(cx, baseY + R * 1.2f)); scale(sx, sy, Offset(cx, cy + R * 1.2f)) }) {
        val body = bodyPath(cx, cy, R, t)

        // ---- shoulders / arms: behind or in front depending on how Pip is turned
        val shL = g.at(-1.38f, -0.22f); val shR = g.at(1.38f, -0.22f)
        val zL = g.z(-1.38f, -0.22f); val zR = g.z(1.38f, -0.22f)
        val armLFront = zL > -0.05f || ps.armL < 0f && zL > -0.4f
        val armRFront = zR > -0.05f || ps.armR < 0f && zR > -0.4f

        // headband tails at the back (peek out when Pip faces you)
        bandTails(g, R, t, behind = true)
        if (!armLFront) arm(shL, R, ps.armL, left = true, flex = ps.flexL, shade = true)
        if (!armRFront) { arm(shR, R, ps.armR, left = false, flex = ps.flexR, shade = true); propFor(ps, t, handPos(shR, R, ps.armR, false), R, false) }

        // ---- legs & sneakers
        val step = if (mood == PipMood.DANCE) sin(t * 6f) else 0f
        leg(g.at(-0.5f, -1.05f), R, yaw, lift = max(0f, step) * R * 0.14f)
        leg(g.at(0.5f, -1.05f), R, yaw, lift = max(0f, -step) * R * 0.14f)

        // ---- body (soft plush shading follows the turn)
        val lightC = g.at(-0.55f, 0.55f)
        drawPath(body, Brush.radialGradient(listOf(MINT_HI, MINT, MINT_MID, MINT_DEEP), lightC, R * 2.3f))
        clipPath(body) {
            // belly patch lives on the globe
            val bz = g.z(0f, -0.5f)
            if (bz > -0.2f) {
                val bc = g.at(0f, -0.5f)
                val bw = R * 1.12f * g.fx(0f, -0.5f); val bh = R * 0.86f
                drawOval(Brush.radialGradient(listOf(CREAM, CREAM, CREAM_SHADE), Offset(bc.x - bw * 0.15f, bc.y - bh * 0.2f), bw * 0.8f), Offset(bc.x - bw / 2, bc.y - bh / 2), Size(bw, bh))
                // stitch line
                drawOval(CREAM_SHADE.copy(alpha = 0.7f), Offset(bc.x - bw / 2 + 2f, bc.y - bh / 2 + 2f), Size(bw - 4f, bh - 4f), style = Stroke(R * 0.018f))
            }
            // edge occlusion for roundness
            drawPath(body, Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, MINT_DEEP.copy(alpha = 0.45f)), Offset(cx - sin(yaw) * R * 0.2f, cy - R * 0.15f), R * 1.4f))
            headband(g, R)
            face(g, R, t, mood, ps, blink, petting, talking)
            // soft specular (fabric sheen)
            val hl = g.at(-0.62f, 0.72f)
            drawOval(Brush.radialGradient(listOf(WHITE.copy(alpha = 0.55f), Color.Transparent), hl, R * 0.45f), Offset(hl.x - R * 0.45f, hl.y - R * 0.3f), Size(R * 0.9f, R * 0.6f))
        }
        // fuzzy rim
        drawPath(body, WHITE.copy(alpha = 0.45f), style = Stroke(w * 0.008f))
        drawPath(body, MINT_DEEP.copy(alpha = 0.25f), style = Stroke(w * 0.003f))

        // ---- curly antenna on top
        antenna(g, R, t, mood)

        // ---- front arms + props
        if (armLFront) arm(shL, R, ps.armL, left = true, flex = ps.flexL, shade = false)
        if (armRFront) arm(shR, R, ps.armR, left = false, flex = ps.flexR, shade = false)
        // two-handed props sit between the hands in front of the belly
        when (ps.prop) {
            Prop.HEART -> heartProp(g.at(0f, -0.32f), R, t)
            Prop.BOWL -> bowlProp(g.at(0f, -0.42f), R, t)
            Prop.PILLOW -> pillowProp(g.at(0f, -0.4f), R)
            Prop.STAR -> if (g.z(0.3f, -0.45f) > 0f) medal(g.at(0.3f, -0.45f), R, t)
            else -> if (armRFront) propFor(ps, t, handPos(shR, R, ps.armR, false), R, true)
        }
        // re-draw the hands over two-handed props so Pip is holding them
        if (ps.prop == Prop.HEART || ps.prop == Prop.BOWL || ps.prop == Prop.PILLOW) {
            if (armLFront) hand(handPos(shL, R, ps.armL, true), R)
            if (armRFront) hand(handPos(shR, R, ps.armR, false), R)
        }
        bandTails(g, R, t, behind = false)

        // ---- mood accessories on the body
        when (mood) {
            PipMood.CONCERNED -> sweat(g.at(0.75f, 0.35f).let { Offset(it.x, it.y + (t % 1.6f) * R * 0.25f) }, R)
            PipMood.LAUGH -> { tear(g.at(-0.62f, 0.02f), R, t); tear(g.at(0.62f, 0.02f), R, t + 0.4f) }
            else -> Unit
        }
    }

    // ---- floating extras (not squashed)
    when (mood) {
        PipMood.SLEEPY -> for (i in 0..2) {
            val f = ((t * 0.35f) + i / 3f) % 1f
            drawZ(cx + R * (0.85f + f * 0.6f), cy - R * (1.1f + f * 1.1f), R * (0.16f + i * 0.05f), WHITE.copy(alpha = (1f - f) * 0.9f))
        }
        PipMood.THINKING -> {
            val bob = sin(t * 3f) * R * 0.05f
            question(cx + R * 1.05f, cy - R * 1.25f + bob, R * 0.42f, WHITE.copy(alpha = 0.95f))
            for (i in 0..1) drawCircle(WHITE.copy(alpha = 0.7f), R * (0.05f + i * 0.03f), Offset(cx + R * (0.62f + i * 0.18f), cy - R * (0.78f + i * 0.2f)))
        }
        PipMood.CELEBRATE, PipMood.DANCE -> for (i in 0..1) {
            val f = ((t * 0.6f) + i * 0.5f) % 1f
            note(cx + (if (i == 0) -1 else 1) * R * (1.1f + f * 0.3f), cy - R * (0.6f + f * 1.0f), R * 0.22f, WHITE.copy(alpha = 1f - f))
        }
        PipMood.SURPRISED -> bang(cx + R * 1.05f, cy - R * 1.2f, R * 0.5f, Color(0xFFFFD34D))
        PipMood.WINK, PipMood.LETS_GO -> sparkle(cx - R * 1.0f, cy - R * 0.9f, R * 0.16f * (0.7f + 0.3f * sin(t * 6f)), WHITE, t * 90f)
        else -> Unit
    }
    if (petting) for (i in 0..2) {
        val f = ((t * 1.4f) + i / 3f) % 1f
        drawArc(WHITE.copy(alpha = (1f - f) * 0.7f), -40f, 80f, false, Offset(cx + R * (1.05f + f * 0.25f) - R * 0.2f, cy - R * 0.45f), Size(R * 0.4f, R * 0.4f), style = Stroke(R * 0.05f, cap = StrokeCap.Round))
    }
}

/** Plush bean silhouette: round head, slightly wider soft bottom, gentle fabric wobble. */
private fun bodyPath(cx: Float, cy: Float, r: Float, t: Float): Path {
    val p = Path()
    val n = 80
    for (i in 0..n) {
        val a = (i.toFloat() / n) * 2f * PI.toFloat()
        val wob = 1f + 0.012f * sin(3f * a + t * 2.2f) + 0.008f * sin(5f * a - t * 1.6f)
        val s = sin(a)
        val xr = r * wob * (if (s > 0) 1f + 0.07f * s else 1f)
        val yr = r * 1.05f * wob * (if (s > 0) 1.1f else 1f)
        val x = cx + cos(a) * xr
        val y = cy + s * yr
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private fun DrawScope.headband(g: Globe, r: Float) {
    val top = 0.62f; val bot = 0.40f
    val band = Path()
    val n = 24
    // front-facing half of the band, from one silhouette edge to the other
    for (i in 0..n) {
        val a = -PI.toFloat() / 2 + PI.toFloat() * i / n - g.yaw
        val pt = g.at(a, top)
        if (i == 0) band.moveTo(pt.x - r * 0.04f, pt.y) else band.lineTo(pt.x + if (i == n) r * 0.04f else 0f, pt.y)
    }
    for (i in n downTo 0) {
        val a = -PI.toFloat() / 2 + PI.toFloat() * i / n - g.yaw
        val pt = g.at(a, bot)
        band.lineTo(pt.x + (if (i == n) r * 0.06f else if (i == 0) -r * 0.06f else 0f), pt.y)
    }
    band.close()
    val c = g.at(-g.yaw * 0.3f, (top + bot) / 2)
    drawPath(band, Brush.verticalGradient(listOf(NAVY_HI, NAVY, NAVY), c.y - r * 0.2f, c.y + r * 0.15f))
    // two light stripes along the band
    for (lat in listOf(top - 0.055f, bot + 0.055f)) {
        val s = Path()
        for (i in 0..n) {
            val a = -PI.toFloat() / 2 + PI.toFloat() * i / n - g.yaw
            val pt = g.at(a, lat)
            if (i == 0) s.moveTo(pt.x, pt.y) else s.lineTo(pt.x, pt.y)
        }
        drawPath(s, WHITE.copy(alpha = 0.85f), style = Stroke(r * 0.032f))
    }
    // center stripe in mint
    val m = Path()
    for (i in 0..n) {
        val a = -PI.toFloat() / 2 + PI.toFloat() * i / n - g.yaw
        val pt = g.at(a, (top + bot) / 2)
        if (i == 0) m.moveTo(pt.x, pt.y) else m.lineTo(pt.x, pt.y)
    }
    drawPath(m, MINT.copy(alpha = 0.9f), style = Stroke(r * 0.03f))
    // soft top sheen
    val sheen = g.at(-0.5f, top - 0.03f)
    drawCircle(WHITE.copy(alpha = 0.18f), r * 0.1f, sheen)
}

/** Knot tails of the sweatband at the back of the head — they peek out and swing when Pip turns. */
private fun DrawScope.bandTails(g: Globe, r: Float, t: Float, behind: Boolean) {
    val kl = PI.toFloat() * 0.75f
    val z = g.z(kl, 0.5f)
    val front = z > 0.15f
    if (behind == front) return
    val k = g.at(kl, 0.5f)
    val side = if (sin(kl + g.yaw) >= 0) 1f else -1f
    val swing = sin(t * 3f) * 6f
    for ((i, len) in listOf(0.46f, 0.38f).withIndex()) {
        rotate(side * (25f + i * 22f) + swing, k) {
            val tail = Path().apply {
                moveTo(k.x, k.y - r * 0.06f)
                quadraticBezierTo(k.x + side * r * len * 0.6f, k.y - r * 0.12f, k.x + side * r * len, k.y + r * 0.02f)
                quadraticBezierTo(k.x + side * r * len * 0.55f, k.y + r * 0.08f, k.x, k.y + r * 0.06f)
                close()
            }
            drawPath(tail, NAVY)
            drawPath(tail, WHITE.copy(alpha = 0.35f), style = Stroke(r * 0.015f))
        }
    }
    if (front) drawCircle(NAVY_HI, r * 0.08f, k)
}

private fun DrawScope.antenna(g: Globe, r: Float, t: Float, mood: PipMood) {
    val root = g.at(0f, 1.25f)
    val sway = sin(t * 2.3f) * 10f + when (mood) {
        PipMood.EXCITED, PipMood.CELEBRATE, PipMood.DANCE -> sin(t * 9f) * 18f
        PipMood.SLEEPY, PipMood.CONCERNED -> 28f
        PipMood.SURPRISED -> -8f
        else -> 0f
    } - g.yaw.let { sin(it) } * 20f
    rotate(sway, root) {
        val tipY = root.y - r * 0.42f
        val stem = Path().apply {
            moveTo(root.x, root.y + r * 0.04f)
            cubicTo(root.x - r * 0.05f, root.y - r * 0.15f, root.x + r * 0.08f, root.y - r * 0.3f, root.x + r * 0.02f, tipY)
        }
        drawPath(stem, MINT_DEEP, style = Stroke(r * 0.1f, cap = StrokeCap.Round))
        drawPath(stem, MINT, style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        // the curl: a little spiral
        val curl = Path()
        val c0 = Offset(root.x + r * 0.12f, tipY + r * 0.02f)
        val turns = 1.35f
        val steps = 34
        for (i in 0..steps) {
            val f = i.toFloat() / steps
            val ang = PI.toFloat() + f * turns * 2f * PI.toFloat()
            val rad = r * 0.13f * (1f - f * 0.72f)
            val pt = Offset(c0.x + cos(ang) * rad, c0.y + sin(ang) * rad)
            if (i == 0) curl.moveTo(root.x + r * 0.02f, tipY) else curl.lineTo(pt.x, pt.y)
        }
        drawPath(curl, MINT_DEEP, style = Stroke(r * 0.1f, cap = StrokeCap.Round))
        drawPath(curl, MINT, style = Stroke(r * 0.055f, cap = StrokeCap.Round))
        drawCircle(WHITE.copy(alpha = 0.6f), r * 0.022f, Offset(c0.x - r * 0.09f, c0.y - r * 0.05f))
    }
}

private fun handPos(sh: Offset, r: Float, angleDeg: Float, left: Boolean): Offset {
    val a = Math.toRadians(angleDeg.toDouble()).toFloat()
    val len = r * 0.46f
    val dir = if (left) -1f else 1f
    return Offset(sh.x + dir * sin(a) * len, sh.y + cos(a) * len)
}

private fun DrawScope.arm(sh: Offset, r: Float, angleDeg: Float, left: Boolean, flex: Boolean, shade: Boolean) {
    val h = handPos(sh, r, angleDeg, left)
    val base = if (shade) MINT_DEEP else MINT_MID
    drawLine(Brush.linearGradient(listOf(MINT, base), sh, h), sh, h, r * 0.3f, StrokeCap.Round)
    if (flex) {
        // bicep bump
        val mid = Offset((sh.x + h.x) / 2, (sh.y + h.y) / 2)
        val dir = if (left) -1f else 1f
        drawCircle(Brush.radialGradient(listOf(MINT_HI, MINT_MID), Offset(mid.x, mid.y - r * 0.08f), r * 0.2f), r * 0.16f, Offset(mid.x + dir * r * 0.02f, mid.y - r * 0.1f))
    }
    hand(h, r)
}

private fun DrawScope.hand(h: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(MINT_HI, MINT, MINT_MID), Offset(h.x - r * 0.04f, h.y - r * 0.05f), r * 0.2f), r * 0.16f, h)
}

private fun DrawScope.leg(hip: Offset, r: Float, yaw: Float, lift: Float) {
    val foot = Offset(hip.x, hip.y + r * 0.34f - lift)
    drawLine(MINT_MID, hip, foot, r * 0.3f, StrokeCap.Round)
    // sneaker: navy upper, white sole, toe points where Pip faces
    val toe = sin(yaw) * r * 0.12f
    val sw = r * 0.46f; val shh = r * 0.26f
    val sx = foot.x - sw / 2 + toe
    drawRoundRect(WHITE, Offset(sx - r * 0.02f, foot.y + shh * 0.45f), Size(sw + r * 0.04f, shh * 0.42f), CornerRadius(shh * 0.3f))
    drawRoundRect(Brush.verticalGradient(listOf(NAVY_HI, NAVY), foot.y - shh * 0.35f, foot.y + shh * 0.6f), Offset(sx, foot.y - shh * 0.35f), Size(sw, shh * 0.9f), CornerRadius(shh * 0.45f))
    // laces / stripe
    drawLine(WHITE.copy(alpha = 0.9f), Offset(sx + sw * 0.3f, foot.y - shh * 0.05f), Offset(sx + sw * 0.62f, foot.y - shh * 0.05f), r * 0.03f, StrokeCap.Round)
    drawLine(WHITE.copy(alpha = 0.9f), Offset(sx + sw * 0.32f, foot.y + shh * 0.12f), Offset(sx + sw * 0.6f, foot.y + shh * 0.12f), r * 0.03f, StrokeCap.Round)
    drawCircle(WHITE.copy(alpha = 0.35f), r * 0.04f, Offset(sx + sw * 0.25f, foot.y - shh * 0.18f))
}

private fun DrawScope.face(g: Globe, r: Float, t: Float, mood: PipMood, ps: Pose, blink: Float, petting: Boolean, talking: Boolean) {
    val eyeLat = 0.12f; val eyeLon = 0.36f
    for (side in listOf(-1f, 1f)) {
        val lon = side * eyeLon
        val z = g.z(lon, eyeLat)
        if (z <= 0.02f) continue
        val fade = (z / 0.25f).coerceIn(0f, 1f)
        val c = g.at(lon, eyeLat)
        val fxs = g.fx(lon, eyeLat)
        val big = when (ps.eyes) { Eyes.BIG -> 1.22f; else -> if (mood == PipMood.CURIOUS && side > 0) 1.15f else 1f }
        val ew = r * 0.30f * fxs * big; val eh = r * 0.40f * big
        val style = when {
            petting -> Eyes.HAPPY
            ps.eyes == Eyes.WINK && side < 0 -> Eyes.HAPPY
            ps.eyes == Eyes.WINK -> Eyes.OPEN
            else -> ps.eyes
        }
        when (style) {
            Eyes.HAPPY -> {
                val a = Path().apply { moveTo(c.x - ew * 0.5f, c.y + eh * 0.08f); quadraticBezierTo(c.x, c.y - eh * 0.55f, c.x + ew * 0.5f, c.y + eh * 0.08f) }
                drawPath(a, EYE.copy(alpha = fade), style = Stroke(r * 0.08f, cap = StrokeCap.Round))
            }
            Eyes.SLEEP -> {
                val a = Path().apply { moveTo(c.x - ew * 0.45f, c.y); quadraticBezierTo(c.x, c.y + eh * 0.35f, c.x + ew * 0.45f, c.y) }
                drawPath(a, EYE.copy(alpha = fade), style = Stroke(r * 0.065f, cap = StrokeCap.Round))
            }
            Eyes.HEART -> heart(c.x, c.y, r * 0.2f * (1f + 0.08f * sin(t * 8f)), CHEEK.copy(alpha = fade))
            Eyes.STAR -> { sparkle(c.x, c.y, r * 0.2f, Color(0xFFFFD34D).copy(alpha = fade), t * 60f); drawCircle(WHITE.copy(alpha = fade), r * 0.04f, Offset(c.x - r * 0.05f, c.y - r * 0.05f)) }
            else -> {
                val lid = blink
                val h = eh * (1f - lid * 0.92f)
                // big glossy eye: deep navy with a blue glow at the bottom and two highlights
                drawOval(Brush.verticalGradient(listOf(EYE, EYE, EYE_BLUE), c.y - h / 2, c.y + h / 2), Offset(c.x - ew / 2, c.y - h / 2), Size(ew, h), alpha = fade)
                if (lid < 0.6f) {
                    val look = Offset(-sin(g.yaw) * 0f, 0f)
                    drawOval(WHITE.copy(alpha = fade), Offset(c.x - ew * 0.34f + look.x, c.y - h * 0.36f), Size(ew * 0.42f, h * 0.36f))
                    drawCircle(WHITE.copy(alpha = 0.85f * fade), ew * 0.1f, Offset(c.x + ew * 0.2f, c.y + h * 0.22f))
                    drawCircle(WHITE.copy(alpha = 0.5f * fade), ew * 0.05f, Offset(c.x + ew * 0.05f, c.y + h * 0.3f))
                }
                if (style == Eyes.DETERMINED) {
                    // lower lid pushed up a bit — focused look
                    drawRect(MINT.copy(alpha = fade), Offset(c.x - ew, c.y + h * 0.28f), Size(ew * 2, h * 0.3f))
                }
            }
        }
        // brows
        val browY = c.y - eh * 0.75f
        val bw = ew * 0.8f
        when (ps.brows) {
            Brows.DETERMINED -> drawLine(EYE.copy(alpha = fade), Offset(c.x - side * bw * 0.6f, browY - r * 0.05f), Offset(c.x + side * bw * 0.4f, browY + r * 0.04f), r * 0.055f, StrokeCap.Round)
            Brows.WORRIED -> drawLine(EYE.copy(alpha = fade), Offset(c.x - side * bw * 0.6f, browY + r * 0.04f), Offset(c.x + side * bw * 0.4f, browY - r * 0.06f), r * 0.05f, StrokeCap.Round)
            Brows.RAISED -> drawArc(EYE.copy(alpha = fade), 200f, 140f, false, Offset(c.x - bw / 2, browY - r * 0.1f), Size(bw, r * 0.14f), style = Stroke(r * 0.045f, cap = StrokeCap.Round))
            Brows.THINK -> if (side > 0) drawLine(EYE.copy(alpha = fade), Offset(c.x - bw * 0.5f, browY - r * 0.07f), Offset(c.x + bw * 0.5f, browY - r * 0.12f), r * 0.045f, StrokeCap.Round)
            Brows.NONE -> Unit
        }
        // cheeks
        val cz = g.z(side * 0.64f, -0.1f)
        if (cz > 0f) {
            val cc = g.at(side * 0.64f, -0.1f)
            val cw = r * 0.3f * g.fx(side * 0.64f, -0.1f)
            val blush = if (petting) 0.9f else ps.blush
            drawOval(Brush.radialGradient(listOf(CHEEK.copy(alpha = blush), CHEEK.copy(alpha = 0f)), cc, cw * 0.7f), Offset(cc.x - cw / 2, cc.y - r * 0.09f), Size(cw, r * 0.18f))
        }
    }

    // ---- mouth
    val mz = g.z(0f, -0.14f)
    if (mz <= 0.05f) return
    val m = g.at(0f, -0.14f)
    val f = g.fx(0f, -0.14f)
    val mx = m.x; val my = m.y
    val mouth = if (petting) Mouth.CAT else ps.mouth
    if (talking && mouth !in setOf(Mouth.O, Mouth.CAT)) {
        val open = (0.3f + 0.7f * abs(sin(t * 12f) * sin(t * 7.3f + 1f))) * r * 0.2f
        drawOval(MOUTH, Offset(mx - r * 0.12f * f, my - r * 0.02f), Size(r * 0.24f * f, open + r * 0.03f))
        drawOval(TONGUE, Offset(mx - r * 0.07f * f, my - r * 0.02f + open * 0.55f), Size(r * 0.14f * f, open * 0.45f + r * 0.01f))
        return
    }
    when (mouth) {
        Mouth.CAT -> {
            val p = Path().apply {
                moveTo(mx - r * 0.14f * f, my); quadraticBezierTo(mx - r * 0.07f * f, my + r * 0.09f, mx, my)
                quadraticBezierTo(mx + r * 0.07f * f, my + r * 0.09f, mx + r * 0.14f * f, my)
            }
            drawPath(p, EYE, style = Stroke(r * 0.045f, cap = StrokeCap.Round))
        }
        Mouth.OPEN, Mouth.BIG_SMILE -> {
            val depth = if (mouth == Mouth.OPEN) 0.38f else 0.3f
            val p = Path().apply { moveTo(mx - r * 0.2f * f, my - r * 0.04f); quadraticBezierTo(mx, my + r * depth, mx + r * 0.2f * f, my - r * 0.04f); close() }
            drawPath(p, MOUTH)
            drawOval(TONGUE, Offset(mx - r * 0.09f * f, my + r * 0.07f), Size(r * 0.18f * f, r * 0.1f))
        }
        Mouth.O -> drawOval(MOUTH, Offset(mx - r * 0.07f * f, my - r * 0.02f), Size(r * 0.14f * f, r * (0.14f + 0.02f * sin(t * 4f))))
        Mouth.FLAT -> drawLine(EYE, Offset(mx - r * 0.1f * f, my + r * 0.03f), Offset(mx + r * 0.1f * f, my + r * 0.03f), r * 0.05f, StrokeCap.Round)
        Mouth.WORRY -> {
            val p = Path().apply { moveTo(mx - r * 0.14f * f, my + r * 0.07f); cubicTo(mx - r * 0.06f * f, my - r * 0.02f, mx + r * 0.05f * f, my + r * 0.11f, mx + r * 0.14f * f, my + r * 0.03f) }
            drawPath(p, EYE, style = Stroke(r * 0.045f, cap = StrokeCap.Round))
        }
        Mouth.SMALL -> {
            val p = Path().apply { moveTo(mx - r * 0.08f * f, my); quadraticBezierTo(mx, my + r * 0.07f, mx + r * 0.08f * f, my) }
            drawPath(p, EYE, style = Stroke(r * 0.045f, cap = StrokeCap.Round))
        }
        Mouth.GRIN -> {
            val p = Path().apply { moveTo(mx - r * 0.16f * f, my - r * 0.02f); quadraticBezierTo(mx, my + r * 0.2f, mx + r * 0.16f * f, my - r * 0.02f); close() }
            drawPath(p, MOUTH)
            drawRect(WHITE, Offset(mx - r * 0.13f * f, my - r * 0.015f), Size(r * 0.26f * f, r * 0.045f))
        }
        Mouth.TONGUE -> {
            val p = Path().apply { moveTo(mx - r * 0.15f * f, my - r * 0.02f); quadraticBezierTo(mx, my + r * 0.2f, mx + r * 0.15f * f, my - r * 0.02f); close() }
            drawPath(p, MOUTH)
            drawOval(TONGUE, Offset(mx - r * 0.02f * f, my + r * 0.04f), Size(r * 0.13f * f, r * 0.13f))
        }
        Mouth.SMILE -> {
            val p = Path().apply { moveTo(mx - r * 0.15f * f, my - r * 0.01f); quadraticBezierTo(mx, my + r * 0.17f, mx + r * 0.15f * f, my - r * 0.01f) }
            drawPath(p, EYE, style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        }
    }
}

// ============================================================ props

private fun DrawScope.propFor(ps: Pose, t: Float, h: Offset, r: Float, front: Boolean) {
    when (ps.prop) {
        Prop.DUMBBELL -> dumbbell(h.x, h.y, r)
        Prop.BOTTLE -> bottle(h.x, h.y, r, ps.armR)
        Prop.THUMB -> thumb(h, r)
        Prop.CHIN -> Unit
        else -> Unit
    }
}

private fun DrawScope.dumbbell(x: Float, y: Float, r: Float) {
    drawLine(Color(0xFF8A8FA3), Offset(x - r * 0.3f, y), Offset(x + r * 0.3f, y), r * 0.07f, StrokeCap.Round)
    for (s in listOf(-1f, 1f)) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4A4F66), Color(0xFF23263A)), y - r * 0.16f, y + r * 0.16f), Offset(x + s * r * 0.3f - r * 0.08f, y - r * 0.16f), Size(r * 0.16f, r * 0.32f), CornerRadius(r * 0.05f))
        drawRoundRect(Color(0xFF6A7090), Offset(x + s * r * 0.44f - r * 0.05f, y - r * 0.11f), Size(r * 0.1f, r * 0.22f), CornerRadius(r * 0.04f))
    }
    hand(Offset(x, y), r)
}

private fun DrawScope.bottle(x: Float, y: Float, r: Float, angle: Float) {
    rotate(180f - angle * 0.9f, Offset(x, y)) {
        drawRoundRect(Color(0xFF6FD3FF).copy(alpha = 0.9f), Offset(x - r * 0.11f, y - r * 0.06f), Size(r * 0.22f, r * 0.42f), CornerRadius(r * 0.08f))
        drawRoundRect(Color(0xFF2F9BE0), Offset(x - r * 0.11f, y + r * 0.1f), Size(r * 0.22f, r * 0.1f))
        drawRoundRect(WHITE.copy(alpha = 0.55f), Offset(x - r * 0.06f, y), Size(r * 0.04f, r * 0.3f), CornerRadius(r * 0.02f))
        drawRoundRect(NAVY, Offset(x - r * 0.06f, y + r * 0.36f), Size(r * 0.12f, r * 0.08f), CornerRadius(r * 0.02f))
    }
    hand(Offset(x, y), r)
}

private fun DrawScope.thumb(h: Offset, r: Float) {
    drawRoundRect(MINT_MID, Offset(h.x - r * 0.05f, h.y - r * 0.3f), Size(r * 0.1f, r * 0.22f), CornerRadius(r * 0.05f))
    drawRoundRect(MINT_HI, Offset(h.x - r * 0.03f, h.y - r * 0.28f), Size(r * 0.04f, r * 0.12f), CornerRadius(r * 0.02f))
    hand(h, r)
}

private fun DrawScope.heartProp(c: Offset, r: Float, t: Float) {
    val s = r * 0.42f * (1f + 0.06f * sin(t * 6f))
    heart(c.x, c.y, s, Color(0xFFFF5C8A))
    heart(c.x - s * 0.15f, c.y - s * 0.12f, s * 0.35f, WHITE.copy(alpha = 0.4f))
}

private fun DrawScope.bowlProp(c: Offset, r: Float, t: Float) {
    // salad: leaves + tomato + egg peeking out of a cream bowl
    val bw = r * 0.9f; val bh = r * 0.4f
    for (i in 0..4) {
        val a = -0.8f + i * 0.4f
        drawCircle(if (i % 2 == 0) Color(0xFF3DBE5E) else Color(0xFF7ADB6A), r * 0.14f, Offset(c.x + a * bw * 0.45f, c.y - bh * 0.1f - abs(a) * -r * 0.02f))
    }
    drawCircle(Color(0xFFFF5B4A), r * 0.08f, Offset(c.x + bw * 0.12f, c.y - bh * 0.3f))
    drawCircle(WHITE, r * 0.08f, Offset(c.x - bw * 0.14f, c.y - bh * 0.28f))
    drawCircle(Color(0xFFFFC53D), r * 0.04f, Offset(c.x - bw * 0.14f, c.y - bh * 0.28f))
    val bowl = Path().apply { moveTo(c.x - bw / 2, c.y); quadraticBezierTo(c.x, c.y + bh * 2f, c.x + bw / 2, c.y); close() }
    drawPath(bowl, Brush.verticalGradient(listOf(WHITE, CREAM_SHADE), c.y, c.y + bh))
    drawLine(NAVY.copy(alpha = 0.6f), Offset(c.x - bw * 0.4f, c.y + bh * 0.25f), Offset(c.x + bw * 0.4f, c.y + bh * 0.25f), r * 0.03f)
}

private fun DrawScope.pillowProp(c: Offset, r: Float) {
    val pw = r * 1.1f; val ph = r * 0.6f
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE9EEFF), Color(0xFFBFC9F2)), c.y - ph / 2, c.y + ph / 2), Offset(c.x - pw / 2, c.y - ph / 2), Size(pw, ph), CornerRadius(ph * 0.45f))
    drawLine(Color(0xFFA9B4E6), Offset(c.x - pw * 0.3f, c.y), Offset(c.x + pw * 0.3f, c.y), r * 0.025f, StrokeCap.Round)
}

private fun DrawScope.medal(c: Offset, r: Float, t: Float) {
    drawLine(Color(0xFF4F6BFF), Offset(c.x - r * 0.08f, c.y - r * 0.3f), c, r * 0.06f)
    drawLine(Color(0xFFFF5C8A), Offset(c.x + r * 0.08f, c.y - r * 0.3f), c, r * 0.06f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF0A0), Color(0xFFFFB020)), c, r * 0.16f), r * 0.14f, c)
    sparkle(c.x, c.y, r * 0.08f, WHITE, t * 60f)
}

private fun DrawScope.sweat(p: Offset, r: Float) {
    val (x, y) = p
    val d = Path().apply { moveTo(x, y - r * 0.12f); quadraticBezierTo(x + r * 0.1f, y + r * 0.02f, x, y + r * 0.06f); quadraticBezierTo(x - r * 0.1f, y + r * 0.02f, x, y - r * 0.12f); close() }
    drawPath(d, Color(0xFF8FD8FF))
    drawCircle(WHITE.copy(alpha = 0.8f), r * 0.02f, Offset(x - r * 0.025f, y - r * 0.01f))
}

private fun DrawScope.tear(p: Offset, r: Float, t: Float) {
    val f = (t * 1.4f) % 1f
    drawCircle(Color(0xFF8FD8FF).copy(alpha = 1f - f), r * 0.05f, Offset(p.x + (if (p.x > center.x) 1 else -1) * f * r * 0.3f, p.y - r * 0.05f + f * r * 0.1f))
}

private fun DrawScope.note(x: Float, y: Float, s: Float, c: Color) {
    drawCircle(c, s * 0.28f, Offset(x, y))
    drawLine(c, Offset(x + s * 0.26f, y), Offset(x + s * 0.26f, y - s), s * 0.12f)
    drawLine(c, Offset(x + s * 0.26f, y - s), Offset(x + s * 0.6f, y - s * 0.8f), s * 0.12f)
}

private fun DrawScope.question(x: Float, y: Float, s: Float, c: Color) {
    val p = Path().apply {
        moveTo(x - s * 0.28f, y - s * 0.25f)
        cubicTo(x - s * 0.28f, y - s * 0.62f, x + s * 0.32f, y - s * 0.62f, x + s * 0.3f, y - s * 0.26f)
        cubicTo(x + s * 0.28f, y - s * 0.02f, x, y - s * 0.02f, x, y + s * 0.18f)
    }
    drawPath(p, c, style = Stroke(s * 0.16f, cap = StrokeCap.Round))
    drawCircle(c, s * 0.09f, Offset(x, y + s * 0.42f))
}

private fun DrawScope.bang(x: Float, y: Float, s: Float, c: Color) {
    drawLine(c, Offset(x, y - s * 0.45f), Offset(x, y + s * 0.12f), s * 0.16f, StrokeCap.Round)
    drawCircle(c, s * 0.09f, Offset(x, y + s * 0.38f))
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

/** Maps a chat reply to the reaction Pip shows while/after saying it. */
fun moodForReply(text: String, question: String = ""): PipMood {
    val s = (text + " " + question).lowercase()
    fun has(vararg k: String) = k.any { s.contains(it) }
    return when {
        has("doctor", "medical", "injur", "pain", "careful", "sorry", "unfortunately", "couldn't", "can't") -> PipMood.CONCERNED
        has("pr!", "new record", "personal best", "congrat", "well done", "🎉", "you did it", "target reached") -> PipMood.CELEBRATE
        has("water", "hydrat", "drink") -> PipMood.HYDRATE
        has("protein", "meal", "breakfast", "lunch", "dinner", "calorie", "food", "eat", "snack", "recipe") -> PipMood.FUEL
        has("sleep", "rest day", "recover", "nap", "bed") -> PipMood.SLEEPY
        has("workout", "train", "bench", "squat", "deadlift", "sets", "reps", "gym", "lift") -> PipMood.TRAIN
        has("haha", "lol", "funny", "joke", "😂") -> PipMood.LAUGH
        has("great", "awesome", "nice", "proud", "💪", "keep it up", "amazing") -> PipMood.LETS_GO
        has("love", "thank", "❤") -> PipMood.LOVE
        has("?") && text.trim().endsWith("?") -> PipMood.CURIOUS
        else -> PipMood.HAPPY
    }
}
