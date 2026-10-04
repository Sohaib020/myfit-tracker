package com.myfit.tracker.ui.arena

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Clips each cast member has (Pip reuses his own animation set). */
enum class CastClip(val file: String, val pip: String) { CHEER("cheer", "cheer"), RUN("run", "jog"), SAD("sad", "sad"), WAVE("wave", "wave") }

private fun portraitPath(m: Mascot) = if (m == Mascot.PIP) "pip/look/look_06_06.webp" else "buddy/${m.id}/portrait.webp"
// the sharp 448 px buddy packs cover cheer and wave; the small running loop stays in assets/arena
private fun clipPath(m: Mascot, c: CastClip) = when {
    m == Mascot.PIP -> "pip/${c.pip}.webp"
    c == CastClip.CHEER -> "buddy/${m.id}/celebrate.webp"
    c == CastClip.WAVE -> "buddy/${m.id}/wave.webp"
    c == CastClip.SAD -> "buddy/${m.id}/thinking.webp"
    else -> "arena/${m.id}_${c.file}.webp"
}

private object PortraitCache {
    val lru = android.util.LruCache<String, ImageBitmap>(24)
}

/** Still 3D portrait of a cast member (falls back to the drawn face if the art isn't bundled). */
@Composable
fun CastImage(m: Mascot, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false) {
    val ctx = LocalContext.current
    val path = portraitPath(m)
    // keyed on the path: switching character swaps the image immediately (cached ones need no reload)
    val bmp by produceState(PortraitCache.lru.get(path), path) {
        value = PortraitCache.lru.get(path) ?: withContext(Dispatchers.IO) {
            runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
        }?.also { PortraitCache.lru.put(path, it) }
    }
    val b = bmp
    Box(modifier.size(size)) {
        if (b != null) Image(b, m.label, Modifier.size(size), alpha = if (dim) 0.35f else 1f,
            colorFilter = if (dim) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null)
        else MascotFace(m, size, happy = !dim)
    }
}

/** Animated 3D clip (loops by default). On Android 8 (no animated WebP decoder) shows the still portrait. */
@Composable
fun CastAnim(m: Mascot, clip: CastClip, size: Dp, modifier: Modifier = Modifier, loop: Boolean = true) {
    if (Build.VERSION.SDK_INT < 28) { CastImage(m, size, modifier); return }
    val ctx = LocalContext.current
    val path = clipPath(m, clip)
    val ok = remember(path) { runCatching { ctx.assets.openFd(path).close(); true }.getOrElse { runCatching { ctx.assets.open(path).close(); true }.getOrDefault(false) } }
    if (!ok) { CastImage(m, size, modifier); return }
    val drawable by produceState<android.graphics.drawable.Drawable?>(null, path) {
        value = withContext(Dispatchers.IO) { runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.assets, path)) }.getOrNull() }
    }
    val d = drawable
    if (d == null) { CastImage(m, size, modifier); return }
    DisposableEffect(d) {
        (d as? AnimatedImageDrawable)?.let { it.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0; it.start() }
        onDispose { (d as? AnimatedImageDrawable)?.stop() }
    }
    AndroidView({ c -> ImageView(c).apply { scaleType = ImageView.ScaleType.FIT_CENTER } }, modifier.size(size), update = { it.setImageDrawable(d) })
}
