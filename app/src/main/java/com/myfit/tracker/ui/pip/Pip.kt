package com.myfit.tracker.ui.pip

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pip v4 — a real 3D plush (modelled and lit in Blender from the character sheet) played back as
 * smooth pre-rendered animations. Pip tilts toward your finger in 2.5D, reacts to every tap with a
 * different emote, hugs when long-pressed, giggles when stroked and lip-syncs while talking.
 */
enum class PipMood {
    HAPPY, EXCITED, SLEEPY, THINKING, CONCERNED, WAVE, PROUD, NEUTRAL, LOVE, TALKING, CURIOUS, CELEBRATE,
    TRAIN, HYDRATE, FUEL, LETS_GO, SURPRISED, WINK, DANCE, SPIN, FLEX, LAUGH,
}

/** Rendered animations in assets/pip/<name>.webp. Loops repeat; the rest play once then return to idle. */
private val LOOPS = setOf("idle", "thinking", "love", "sleepy", "concerned", "dance", "train")

private fun animFor(m: PipMood) = when (m) {
    PipMood.HAPPY, PipMood.NEUTRAL, PipMood.TALKING -> "idle"
    PipMood.EXCITED, PipMood.CELEBRATE -> "celebrate"
    PipMood.SLEEPY -> "sleepy"
    PipMood.THINKING -> "thinking"
    PipMood.CONCERNED -> "concerned"
    PipMood.WAVE -> "wave"
    PipMood.PROUD, PipMood.LETS_GO -> "letsgo"
    PipMood.LOVE -> "love"
    PipMood.CURIOUS -> "curious"
    PipMood.TRAIN -> "train"
    PipMood.HYDRATE -> "hydrate"
    PipMood.FUEL -> "fuel"
    PipMood.SURPRISED -> "surprised"
    PipMood.WINK -> "wink"
    PipMood.DANCE -> "dance"
    PipMood.SPIN -> "spin"
    PipMood.FLEX -> "flex"
    PipMood.LAUGH -> "laugh"
}

private val TAP_REACTIONS = listOf("wave", "laugh", "wink", "surprised", "dance", "spin", "flex", "letsgo", "love", "celebrate", "hydrate", "train", "fuel", "curious")
private val IDLE_ACTS = listOf("wave", "wink", "hydrate", "curious", "flex")
private val CALM = setOf(PipMood.HAPPY, PipMood.NEUTRAL, PipMood.TALKING, PipMood.PROUD, PipMood.WAVE)
private val BUSY = setOf(PipMood.CELEBRATE, PipMood.EXCITED, PipMood.LOVE, PipMood.DANCE, PipMood.SLEEPY, PipMood.THINKING)

private data class Fx(val born: Float, val x: Float, val y: Float, val vx: Float, val vy: Float, val kind: Int, val spin: Float, val hue: Int)

private val CHEEK = Color(0xFFFF8FAB)
private val confettiColors = listOf(Color(0xFFFF5C8A), Color(0xFFFFD34D), Color(0xFF4FC3FF), Color(0xFF7CFFB2), Color(0xFFB57CFF))

/** Talking mouth frames (idle pose, mouth closed → wide open), decoded once and shared. */
private object TalkFrames {
    @Volatile var frames: List<ImageBitmap>? = null
    suspend fun load(ctx: android.content.Context): List<ImageBitmap> = frames ?: withContext(Dispatchers.IO) {
        (0..5).mapNotNull { i ->
            runCatching { ctx.assets.open("pip/talk/$i.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
        }
    }.also { if (it.isNotEmpty()) frames = it }
}

@Composable
fun Pip(
    mood: PipMood,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    onTap: (() -> Unit)? = null,
    talking: Boolean = false,
    interactive: Boolean = true,
    idleActions: Boolean = true,
    level: Float = 0.5f,
) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val curTap by rememberUpdatedState(onTap)

    var t by remember { mutableFloatStateOf(0f) }
    val fx = remember { mutableStateListOf<Fx>() }
    val squash = remember { Animatable(1f) }
    var reaction by remember { mutableStateOf<String?>(null) }
    var lastReaction by remember { mutableStateOf<String?>(null) }
    var baseDone by remember(mood) { mutableStateOf(false) }   // a play-once base clip has finished
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var petting by remember { mutableStateOf(false) }
    var hugging by remember { mutableStateOf(false) }
    var lastHeart by remember { mutableFloatStateOf(0f) }
    var boxPx by remember { mutableStateOf(IntSize(1, 1)) }
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    var talkFrames by remember { mutableStateOf(TalkFrames.frames) }
    var redraw by remember { mutableIntStateOf(0) }

    fun burst(kind: Int, n: Int, cx: Float, cy: Float, spread: Float = 1f) {
        repeat(n) {
            val a = Random.nextFloat() * (2 * PI).toFloat()
            val sp = (0.35f + Random.nextFloat() * 0.6f) * spread
            fx += Fx(t, cx, cy, cos(a) * sp, sin(a) * sp - 0.5f, kind, Random.nextFloat() * 2 - 1, Random.nextInt(5))
        }
        if (fx.size > 70) fx.removeRange(0, fx.size - 70)
    }

    fun react(name: String) {
        reaction = name; lastReaction = name
        when (name) {
            "celebrate" -> burst(2, 28, 0.5f, 0.3f, 1.3f)
            "love" -> { burst(3, 1, 0.5f, 0.35f); burst(0, 6, 0.5f, 0.45f) }
            "surprised" -> burst(6, 1, 0.8f, 0.12f, 0.02f)
            "spin", "flex", "letsgo", "wink" -> burst(1, 6, 0.5f, 0.4f)
            "laugh" -> burst(1, 3, 0.5f, 0.35f)
            else -> Unit
        }
    }

    val effMood = if (hugging || petting) PipMood.LOVE else mood
    val baseAnim = animFor(effMood).let { if (it !in LOOPS && baseDone) "idle" else it }
    val anim = reaction ?: baseAnim
    val showTalk = talking && reaction == null && anim == "idle" && talkFrames != null

    // ---- the animated clip currently playing
    var drawable by remember { mutableStateOf<Drawable?>(null) }
    var still by remember { mutableStateOf<ImageBitmap?>(null) }
    val loopNow = reaction == null && anim in LOOPS
    DisposableEffect(anim, loopNow) {
        var alive = true
        val clip = anim
        val job = scope.launch {
            val d = withContext(Dispatchers.IO) { if (Build.VERSION.SDK_INT >= 28) runCatching { decode(ctx, clip) }.getOrNull() else null }
            if (!alive) return@launch
            if (d != null && Build.VERSION.SDK_INT >= 28 && runCatching {
                start(d, loop = loopNow, onEnd = {
                    if (reaction == clip) reaction = null
                    else if (clip == baseAnim) baseDone = true
                }, invalidate = { redraw++ })
            }.isSuccess) {
                stopClip(drawable)
                drawable = d
            } else {
                still = withContext(Dispatchers.IO) {
                    runCatching { ctx.assets.open("pip/$clip.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
                }
                if (reaction == clip) { delay(1500); reaction = null }
            }
        }
        onDispose { alive = false; job.cancel() }
    }
    DisposableEffect(Unit) { onDispose { stopClip(drawable) } }
    LaunchedEffect(talking) { if (talking && talkFrames == null) talkFrames = TalkFrames.load(ctx) }

    // ---- light frame loop: particles, idle acts. Slows to a trickle when nothing needs frames.
    val idleOn by rememberUpdatedState(idleActions)
    val moodNow by rememberUpdatedState(effMood)
    LaunchedEffect(Unit) {
        var nextIdle = 6f + Random.nextFloat() * 4f
        var start = -1L
        while (true) {
            withFrameMillis { ms ->
                if (start < 0) start = ms
                t = (ms - start) / 1000f
                if (fx.isNotEmpty()) fx.removeAll { t - it.born > 1.7f }
                when (moodNow) {
                    PipMood.CELEBRATE, PipMood.EXCITED -> if (Random.nextFloat() < 0.12f && fx.size < 50) burst(2, 1, 0.5f, 0.08f)
                    PipMood.LOVE -> if (Random.nextFloat() < 0.04f) burst(0, 1, 0.5f, 0.35f, 0.6f)
                    PipMood.DANCE -> if (Random.nextFloat() < 0.05f) burst(4, 1, if (Random.nextBoolean()) 0.18f else 0.82f, 0.3f, 0.3f)
                    else -> Unit
                }
                if (idleOn && reaction == null && moodNow in CALM && pointer == null && t > nextIdle) {
                    react(IDLE_ACTS.random()); nextIdle = t + 8f + Random.nextFloat() * 6f
                }
            }
            if (fx.isEmpty() && pointer == null && !talking && moodNow !in BUSY) delay(160)
        }
    }
    LaunchedEffect(pointer) {
        val p = pointer
        val tx = if (p == null) 0f else ((p.y / boxPx.height - 0.45f) * -14f).coerceIn(-10f, 10f)
        val ty = if (p == null) 0f else ((p.x / boxPx.width - 0.5f) * 22f).coerceIn(-14f, 14f)
        launch { tiltX.animateTo(tx, spring(0.6f, 300f)) }
        tiltY.animateTo(ty, spring(0.6f, 300f))
    }

    Box(
        modifier
            .size(size)
            .onSizeChanged { boxPx = it }
            .then(if (!interactive) Modifier else Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { o -> pointer = o; tryAwaitRelease(); pointer = null; hugging = false },
                        onTap = {
                            tick()
                            scope.launch { squash.snapTo(0.86f); squash.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow)) }
                            react(TAP_REACTIONS.filter { it != lastReaction }.random())
                            curTap?.invoke()
                        },
                        onLongPress = {
                            tick(); hugging = true
                            scope.launch { squash.animateTo(0.9f, tween(180)); squash.animateTo(1f, spring(0.35f, 300f)) }
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
        // theme aura + soft contact shadow (the renders have a transparent background)
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            drawCircle(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.22f), Color.Transparent), Offset(w / 2, w * 0.55f), w * 0.55f), w * 0.55f, Offset(w / 2, w * 0.55f))
            drawOval(
                Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), Offset(w / 2, w * 0.94f), w * 0.26f),
                Offset(w * 0.24f, w * 0.905f), Size(w * 0.52f, w * 0.07f),
            )
        }
        // the plush, with a gentle 2.5D tilt toward the finger and a squash on tap
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val s = squash.value
                scaleX = 2f - s; scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0.92f)
                rotationX = tiltX.value; rotationY = tiltY.value
                cameraDistance = 12f * density
                if (showTalk) translationY = -abs(sin(t * 7f)) * this.size.height * 0.006f
            }
        ) {
            redraw // re-draw whenever the clip advances a frame
            val w = this.size.width.toInt(); val h = this.size.height.toInt()
            val talkF = talkFrames
            if (showTalk && talkF != null) {
                val lv = if (level < 0f) 0.25f + 0.6f * abs(sin(t * 11f) * sin(t * 4.3f + 1f)) else level
                val idx = (lv.coerceIn(0f, 1f) * (talkF.size - 1) + 0.4f).toInt().coerceIn(0, talkF.size - 1)
                drawImage(talkF[idx], dstOffset = IntOffset.Zero, dstSize = IntSize(w, h))
            } else {
                val d = drawable
                if (d != null) {
                    d.setBounds(0, 0, w, h)
                    drawIntoCanvas { d.draw(it.nativeCanvas) }
                } else still?.let { drawImage(it, dstOffset = IntOffset.Zero, dstSize = IntSize(w, h)) }
            }
        }
        // particles & floating symbols
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            when (effMood) {
                PipMood.SLEEPY -> for (i in 0..2) {
                    val f = ((t * 0.35f) + i / 3f) % 1f
                    drawZ(w * (0.70f + f * 0.18f), w * (0.30f - f * 0.22f), w * (0.05f + i * 0.015f), Color.White.copy(alpha = (1f - f) * 0.9f))
                }
                PipMood.THINKING -> question(w * 0.80f, w * (0.16f + 0.01f * sin(t * 3f)), w * 0.12f, Color.White.copy(alpha = 0.95f))
                else -> Unit
            }
            fx.forEach { p ->
                val age = (t - p.born) / 1.7f
                val px = (p.x + p.vx * age * 0.5f) * w
                val py = (p.y + p.vy * age * 0.5f + age * age * 0.25f) * w
                val alpha = (1f - age).coerceIn(0f, 1f)
                val s = w * (0.045f + 0.02f * abs(p.vx))
                when (p.kind) {
                    0 -> heart(px, py, s, CHEEK.copy(alpha = alpha))
                    1 -> sparkle(px, py, s, Color.White.copy(alpha = alpha), p.spin * age * 300f)
                    2 -> rotate(p.spin * age * 500f, Offset(px, py)) { drawRect(confettiColors[p.hue].copy(alpha = alpha), Offset(px, py), Size(s * 0.8f, s * 0.38f)) }
                    3 -> heart(px, py - age * w * 0.2f, w * 0.12f * (0.6f + age), CHEEK.copy(alpha = alpha))
                    4 -> note(px, p.y * w - age * w * 0.3f, w * 0.06f, Color.White.copy(alpha = alpha))
                    6 -> bang(p.x * w, p.y * w, w * 0.11f * (0.8f + 0.4f * minOf(1f, age * 5f)), Color(0xFFFFD34D).copy(alpha = alpha))
                    else -> Unit
                }
            }
        }
    }
}

/** End-callbacks of clips that were replaced; muted so a late "animation ended" can't act on the new clip. */
private val clipCallbacks = java.util.WeakHashMap<Drawable, ClipCallback>()

private class ClipCallback(val onEnd: () -> Unit) : Animatable2.AnimationCallback() {
    @Volatile var active = true
    override fun onAnimationEnd(drawable: Drawable?) { if (active) onEnd() }
}

private fun stopClip(d: Drawable?) {
    if (d == null || Build.VERSION.SDK_INT < 28) return
    // NB: never call clearAnimationCallbacks() — Android posts the end event and then reads the
    // callback list, which clearAnimationCallbacks() sets to null (crash on Android 14–16).
    clipCallbacks.remove(d)?.active = false
    (d as? AnimatedImageDrawable)?.let { runCatching { it.stop() } }
}

@RequiresApi(28)
private fun decode(ctx: android.content.Context, name: String): Drawable =
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.assets, "pip/$name.webp"))

@RequiresApi(28)
private fun start(d: Drawable, loop: Boolean, onEnd: () -> Unit, invalidate: () -> Unit) {
    if (d !is AnimatedImageDrawable) return
    d.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    d.callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) = invalidate()
        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) { handler.postAtTime(what, who, `when`) }
        override fun unscheduleDrawable(who: Drawable, what: Runnable) { handler.removeCallbacks(what, who) }
    }
    val cb = ClipCallback(onEnd)
    clipCallbacks[d] = cb
    d.registerAnimationCallback(cb)
    d.start()
}

// ------------------------------------------------------------------ 2D symbols

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
        has("pr!", "new record", "personal best", "congrat", "well done", "🎉", "you did it", "target reached", "mubarak", "shabash") -> PipMood.CELEBRATE
        has("water", "hydrat", "drink", "pani", "paani") -> PipMood.HYDRATE
        has("protein", "meal", "breakfast", "lunch", "dinner", "calorie", "food", "eat", "snack", "recipe", "khana", "nashta") -> PipMood.FUEL
        has("sleep", "rest day", "recover", "nap", "bed", "neend", "aaram") -> PipMood.SLEEPY
        has("workout", "train", "bench", "squat", "deadlift", "sets", "reps", "gym", "lift", "exercise", "warzish") -> PipMood.TRAIN
        has("haha", "lol", "funny", "joke", "😂") -> PipMood.LAUGH
        has("great", "awesome", "nice", "proud", "💪", "keep it up", "amazing", "zabardast", "bohat acha") -> PipMood.LETS_GO
        has("love", "thank", "❤", "shukriya") -> PipMood.LOVE
        text.trim().endsWith("?") -> PipMood.CURIOUS
        else -> PipMood.HAPPY
    }
}
