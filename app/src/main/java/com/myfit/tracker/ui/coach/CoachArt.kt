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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            (Coach.art(ctx, look, pose) ?: Coach.art(ctx, look, "stand") ?: Coach.art(ctx, look, "portrait"))?.let { CoachBitmaps.load(ctx, it) }
        }
    }
    return img
}

/** Round avatar with a ring that pulses with the voice while the character talks. */
@Composable
fun CoachPortrait(look: String, speaking: Boolean, size: Dp, modifier: Modifier = Modifier, level: MutableStateFlow<Float>? = null) {
    val th = LocalFitTheme.current
    val img = rememberCoachImage(look, "portrait")
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
fun CoachFigure(look: String, pose: String, modifier: Modifier = Modifier, level: MutableStateFlow<Float>? = null, spotlight: Boolean = true) {
    val th = LocalFitTheme.current
    val lv by (level ?: remember0).collectAsState()
    val t = rememberInfiniteTransition(label = "breath")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "b")
    val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "s")
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (spotlight) Canvas(Modifier.fillMaxSize()) {
            drawOval(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.35f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.92f), radius = size.width * 0.5f),
                topLeft = Offset(size.width * 0.12f, size.height * 0.84f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.14f))
            drawCircle(Brush.radialGradient(listOf(th.accentBright.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.45f), radius = size.width * 0.6f),
                radius = size.width * 0.6f, center = Offset(size.width / 2, size.height * 0.45f))
        }
        AnimatedContent(pose, Modifier.fillMaxSize(), transitionSpec = {
            (scaleIn(spring(0.55f, 380f), initialScale = 0.9f) + slideInVertically(spring(0.6f, 400f)) { it / 14 } + fadeIn(tween(160)))
                .togetherWith(fadeOut(tween(120)))
        }, label = "pose") { p ->
            val img = rememberCoachImage(look, p)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
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
}
