package com.myfit.tracker.ui.coach

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

private object CoachBitmaps {
    private val cache = android.util.LruCache<String, ImageBitmap>(12)
    fun load(c: android.content.Context, path: String): ImageBitmap? = cache.get(path) ?: runCatching {
        c.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
    }.getOrNull()?.also { cache.put(path, it) }
}

@Composable
private fun rememberCoachImage(look: String, pose: String): ImageBitmap? {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, look, pose) {
        value = withContext(Dispatchers.IO) {
            (Coach.art(ctx, look, pose) ?: (if (pose == "face") Coach.art(ctx, look, "portrait") else null) ?: Coach.art(ctx, look, "stand") ?: Coach.art(ctx, look, "portrait"))?.let { CoachBitmaps.load(ctx, it) }
        }
    }
    return img
}

/** Round avatar with a ring that pulses with the voice while the character talks. */
@Composable
fun CoachPortrait(look: String, speaking: Boolean, size: Dp, modifier: Modifier = Modifier, level: MutableStateFlow<Float>? = null) {
    val th = LocalFitTheme.current
    val img = rememberCoachImage(look, "face")
    val lv by (level ?: remember0).collectAsState()
    val t = rememberInfiniteTransition(label = "coach")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "p")
    val amp = if (speaking) maxOf(lv, pulse * 0.6f) else 0f
    Box(
        modifier.size(size).graphicsLayer { scaleX = 1f + amp * 0.05f; scaleY = 1f + amp * 0.05f }.clip(CircleShape)
            .background(Brush.verticalGradient(listOf(th.accent.copy(alpha = 0.45f), th.accent.copy(alpha = 0.10f))))
            .border(2.dp + 3.dp * amp, th.accentBright.copy(alpha = if (speaking) 0.95f else 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        else Icon(Duo.Person, null, tint = th.accentBright, modifier = Modifier.size(size * 0.5f))
    }
}

private val remember0 = MutableStateFlow(0f)

/**
 * Full-figure character on a soft spotlight. Idles with a breathing sway, bobs with the voice while talking, and
 * pops into each new pose with a springy entrance.
 */
@Composable
fun CoachFigure(look: String, pose: String, modifier: Modifier = Modifier, level: MutableStateFlow<Float>? = null, spotlight: Boolean = true,
                tapToEmote: Boolean = true) {
    val th = LocalFitTheme.current
    // tap → a random fitting emote (never the same twice in a row), played once, then back to the screen's pose
    var tapPose by androidx.compose.runtime.remember(look) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var lastEmote by androidx.compose.runtime.remember(look) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val shown = tapPose ?: pose
    val lv by (level ?: remember0).collectAsState()
    val t = rememberInfiniteTransition(label = "breath")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "b")
    val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "s")
    val onTap = { if (tapPose == null) { val e = CoachEmotes.pick(look, lastEmote); lastEmote = e; tapPose = e } }
    // stills (no pack yet / Android 8) have no end callback: hold the emote briefly, then return
    val ctx0 = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(tapPose) {
        if (tapPose != null && (android.os.Build.VERSION.SDK_INT < 28 || !CoachPacks.isInstalled(ctx0, look))) { kotlinx.coroutines.delay(2200); tapPose = null }
        else if (tapPose != null) { kotlinx.coroutines.delay(8000); tapPose = null }   // safety: never stay stuck on an emote
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (spotlight) Canvas(Modifier.fillMaxSize()) {
            drawOval(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.92f), radius = size.width * 0.5f),
                topLeft = Offset(size.width * 0.12f, size.height * 0.84f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.14f))
            drawCircle(Brush.radialGradient(listOf(th.accentBright.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.45f), radius = size.width * 0.6f),
                radius = size.width * 0.6f, center = Offset(size.width / 2, size.height * 0.45f))
        }
        val ctx = LocalContext.current
        val packs by CoachPacks.installed.collectAsState()
        // first appearance of this coach: fetch the animated moves in the background (the still shows meanwhile)
        androidx.compose.runtime.LaunchedEffect(look) { if (!CoachPacks.isInstalled(ctx, look)) CoachPacks.ensure(ctx, look) }
        val video = androidx.compose.runtime.remember(look, shown, packs) {
            if (android.os.Build.VERSION.SDK_INT >= 28) CoachPacks.clip(ctx, look, shown)?.takeIf { it.name.endsWith(".mp4") } else null
        }
        if (video != null) {
            // R26: one video view for every move — switching keeps the last frame up until the next move's first frame,
            // so there's no blank flash between moves
            val once = tapPose != null && shown == tapPose
            CoachClip(video, Modifier.fillMaxSize().graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f); translationY = -lv * 6f },
                loop = !once, onEnd = { if (tapPose == shown) tapPose = null })
        } else AnimatedContent(shown, Modifier.fillMaxSize(), transitionSpec = {
            (scaleIn(spring(0.55f, 380f), initialScale = 0.96f) + fadeIn(tween(180)))
                .togetherWith(fadeOut(tween(140)))
        }, label = "pose") { p ->
            val clip = androidx.compose.runtime.remember(look, p, packs) {
                if (android.os.Build.VERSION.SDK_INT >= 28) CoachPacks.clip(ctx, look, p) else null
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                if (clip != null) {
                    // the animation carries its own breathing and motion; only bob gently with the voice
                    val once = tapPose != null && p == tapPose
                    CoachClip(clip, Modifier.fillMaxSize().graphicsLayer { transformOrigin = TransformOrigin(0.5f, 1f); translationY = -lv * 6f },
                        loop = !once, onEnd = { if (tapPose == p) tapPose = null })
                } else {
                    val img = rememberCoachImage(look, p)
                    if (img != null) Image(img, null, Modifier.fillMaxSize().graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = 1f + 0.014f * b + lv * 0.03f; scaleX = 1f + 0.006f * b
                        rotationZ = sway * 0.8f
                        translationY = -lv * 10f
                    }, contentScale = ContentScale.Fit)
                    else Icon(Duo.SportsGymnastics, null, tint = th.accentBright.copy(alpha = 0.6f), modifier = Modifier.size(96.dp).align(Alignment.Center))
                }
            }
        }
        // touch layer on top of the animation (an AndroidView underneath would otherwise swallow the tap)
        if (tapToEmote) Box(Modifier.fillMaxSize().pointerInput(look) { detectTapGestures { onTap() } })
    }
}


/**
 * Plays a looping animated WebP (Android 9+). An ImageView hosts the AnimatedImageDrawable in its own render node,
 * which avoids the stale-bounds double image some Android 11 MediaTek phones showed when drawing into Compose.
 */
@Composable
internal fun CoachClip(file: java.io.File, modifier: Modifier, loop: Boolean = true, onEnd: (() -> Unit)? = null) {
    val end = androidx.compose.runtime.rememberUpdatedState(onEnd)
    if (file.name.endsWith(".mp4")) {
        // R26: 720p / 50 fps video clip with alpha (hardware decoded)
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { c -> AlphaVideoView(c) },
            modifier = modifier,
            update = { v -> v.onEnd = { end.value?.invoke() }; v.play(file, loop) },
        )
        return
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { c -> BottomFitImageView(c) },
        modifier = modifier,
        update = { iv ->
            val key = file.path + loop
            if (iv.tag != key && android.os.Build.VERSION.SDK_INT >= 28) {
                iv.tag = key
                runCatching {
                    val d = android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(file))
                    iv.setImageDrawable(d)
                    (d as? android.graphics.drawable.AnimatedImageDrawable)?.apply {
                        repeatCount = if (loop) android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE else 0
                        if (!loop) registerAnimationCallback(object : android.graphics.drawable.Animatable2.AnimationCallback() {
                            override fun onAnimationEnd(drawable: android.graphics.drawable.Drawable?) { end.value?.invoke() }
                        })
                        start()
                    }
                }
            }
        },
        onRelease = { iv ->
            if (android.os.Build.VERSION.SDK_INT >= 28) (iv.drawable as? android.graphics.drawable.AnimatedImageDrawable)?.clearAnimationCallbacks()
            ((iv.drawable as? android.graphics.drawable.Animatable))?.stop(); iv.setImageDrawable(null)
        },
    )
}

/** Emotes a tap can trigger, per coach kind. */
object CoachEmotes {
    private val TRAINER = listOf("wave", "thumbsup", "fistpump", "cheer", "victory", "clap", "rally", "jump")
    private val NUTRI = listOf("wave", "thumbsup", "cheer", "clap", "nod", "happy")
    fun list(look: String) = if (look.startsWith("n")) NUTRI else TRAINER
    fun pick(look: String, last: String?): String = list(look).filter { it != last }.random()
}

/** Scales the frame to fit the box and stands it on the bottom edge, centred horizontally (feet on the floor). */
private class BottomFitImageView(c: android.content.Context) : android.widget.ImageView(c) {
    init { scaleType = ScaleType.MATRIX }
    private fun fit() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat(); val dh = d.intrinsicHeight.toFloat()
        val vw = (width - paddingLeft - paddingRight).toFloat(); val vh = (height - paddingTop - paddingBottom).toFloat()
        if (dw <= 0f || dh <= 0f || vw <= 0f || vh <= 0f) return
        val s = minOf(vw / dw, vh / dh)
        imageMatrix = android.graphics.Matrix().apply { setScale(s, s); postTranslate((vw - dw * s) / 2f, vh - dh * s) }
    }
    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean = super.setFrame(l, t, r, b).also { fit() }
    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) { super.setImageDrawable(drawable); fit() }
}

/**
 * R29: a glowing neon disc the character stands on (coaches, nutritionist, champions — 3D and clips alike). The
 * character is centred and lifted so its feet land on the disc's centre; the disc breathes softly.
 */
@Composable
fun OnPlatform(color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "platform")
    val glow by t.animateFloat(0.75f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val discW = minOf(maxWidth * 0.86f, maxHeight * 0.62f)
        val discH = discW * 0.24f
        Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = discH * 0.10f).size(discW, discH)) {
            val w = size.width; val h = size.height
            // light pooling on the floor
            drawOval(Brush.radialGradient(listOf(color.copy(alpha = 0.55f * glow), color.copy(alpha = 0.18f * glow), Color.Transparent),
                Offset(w / 2, h / 2), w / 2), Offset.Zero, androidx.compose.ui.geometry.Size(w, h))
            // the disc top
            drawOval(Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.35f))),
                Offset(w * 0.12f, h * 0.22f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.56f))
            // bright neon rim
            drawOval(color.copy(alpha = 0.95f * glow), Offset(w * 0.12f, h * 0.22f), androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.56f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.2.dp.toPx()))
            drawOval(Color.White.copy(alpha = 0.55f * glow), Offset(w * 0.2f, h * 0.27f), androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.42f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            // contact shadow under the feet
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), Offset(w / 2, h * 0.5f), w * 0.2f),
                Offset(w * 0.3f, h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.24f))
        }
        Box(Modifier.fillMaxSize().padding(bottom = discH * 0.48f), contentAlignment = Alignment.BottomCenter, content = content)
    }
}
