package com.myfit.tracker.ui.coach

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.withContext

private object CoachBitmaps {
    private val cache = android.util.LruCache<String, ImageBitmap>(8)
    fun load(c: android.content.Context, path: String): ImageBitmap? = cache.get(path) ?: runCatching {
        c.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
    }.getOrNull()?.also { cache.put(path, it) }
}

@Composable
private fun rememberCoachImage(look: String, pose: String): ImageBitmap? {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, look, pose) {
        value = withContext(Dispatchers.IO) {
            (Coach.art(ctx, look, pose) ?: Coach.art(ctx, look, "portrait"))?.let { CoachBitmaps.load(ctx, it) }
        }
    }
    return img
}

/** Round portrait with a soft glow ring that pulses while the coach talks. */
@Composable
fun CoachPortrait(look: String, speaking: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val img = rememberCoachImage(look, "portrait")
    val t = rememberInfiniteTransition(label = "coach")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "p")
    val ring = if (speaking) 2.dp + 3.dp * pulse else 2.dp
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(th.accent.copy(alpha = 0.35f), th.accent.copy(alpha = 0.08f))))
            .border(ring, th.accentBright.copy(alpha = if (speaking) 0.9f else 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        else Icon(Duo.Person, null, tint = th.accentBright, modifier = Modifier.size(size * 0.5f))
    }
}

/** Full-figure coach (stand / demo / cheer), breathing gently; crossfades when the pose changes. */
@Composable
fun CoachFigure(look: String, pose: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val t = rememberInfiniteTransition(label = "breath")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1900), RepeatMode.Reverse), label = "b")
    Crossfade(pose, modifier, animationSpec = tween(450), label = "pose") { p ->
        val img = rememberCoachImage(look, p)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if (img != null) Image(img, null, Modifier.fillMaxSize().graphicsLayer {
                scaleY = 1f + 0.012f * b; scaleX = 1f + 0.006f * b; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            }, contentScale = ContentScale.Fit)
            else Icon(Duo.SportsGymnastics, null, tint = th.accentBright.copy(alpha = 0.6f), modifier = Modifier.size(96.dp).align(Alignment.Center))
        }
    }
}
