package com.myfit.tracker.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * The shared "world" behind every glass surface. The same drawing is replayed (blurred, offset)
 * inside each glass panel, which is what gives real see-through glass on Android 12+.
 */
@Stable
class Backdrop {
    var theme by mutableStateOf(Themes.Kinetic)
    var image by mutableStateOf<ImageBitmap?>(null)
    val time = mutableFloatStateOf(0f)          // seconds, advanced by the root when animation is on
    var rootSize by mutableStateOf(Size.Zero)
    /** The backdrop recorded ONCE per frame; every glass surface replays this layer (cheap). */
    var layer: GraphicsLayer? = null
    /** Baked still images (theme at 1/3 size, card blur, dock blur). Null while a gentle theme is live. */
    var bgImg by mutableStateOf<ImageBitmap?>(null)
    var cardImg by mutableStateOf<ImageBitmap?>(null)
    var dockImg by mutableStateOf<ImageBitmap?>(null)
    /** The backdrop blurred with the dock's own (stronger) blur amount. */
    var dockLayer: GraphicsLayer? = null
    /** The backdrop pre-blurred once per frame (shared by every card, instead of one blur per card). */
    var blurLayer: GraphicsLayer? = null
}

val LocalBackdrop = staticCompositionLocalOf { Backdrop() }

private val flowerSeeds: List<Triple<Float, Float, Int>> = Random(7).let { r ->
    List(70) { Triple(r.nextFloat(), r.nextFloat(), r.nextInt(3)) }
}

fun DrawScope.drawBackdrop(theme: FitTheme, image: ImageBitmap?, t: Float, w: Float, h: Float) {
    if (w <= 0f || h <= 0f) return
    val full = Size(w, h)
    clipRect(0f, 0f, w, h) {
        if (image != null) {
            drawCover(image, w, h)
            drawRect(if (theme.isLight) Color(0x40FFFFFF) else Color(0x59000000), size = full)
            return@clipRect
        }
        if (UiStyle.flat) {
            // Flat design: calm solid background with a faint accent glow at the top — no art
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)), size = full)
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(theme.accent.copy(alpha = if (theme.isLight) 0.06f else 0.10f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w * 0.8f, 0f), radius = w * 0.9f), size = full)
            return@clipRect
        }
        if (ThemeShaders.draw(this, theme.id, t, w, h)) return@clipRect
        when (theme.art) {
            BackdropArt.AURORA -> aurora(theme, t, w, h)
            BackdropArt.GRID -> grid(theme, t, w, h)
            BackdropArt.WAVES -> waves(theme, t, w, h)
            BackdropArt.LANDSCAPE -> landscape(t, w, h)
            BackdropArt.FROST -> frost(theme, t, w, h)
            else -> drawAnimatedArt(theme, t, w, h)
        }
    }
}

private fun DrawScope.drawCover(img: ImageBitmap, w: Float, h: Float) {
    val s = max(w / img.width, h / img.height)
    val dw = img.width * s; val dh = img.height * s
    drawImage(
        img,
        srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f).roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
    )
}

private fun DrawScope.blob(c: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(
        Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = 0f)), center, radius),
        radius, center,
    )
}

private fun DrawScope.aurora(th: FitTheme, t: Float, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(th.bgTop, th.bgBottom), 0f, h), size = Size(w, h))
    blob(th.blobs[0], Offset(w * (0.15f + 0.12f * sin(t * 0.11f)), h * (0.12f + 0.06f * cos(t * 0.09f))), w * 1.0f, 0.75f)
    blob(th.blobs[1], Offset(w * (0.95f + 0.10f * cos(t * 0.07f)), h * (0.38f + 0.08f * sin(t * 0.08f))), w * 0.85f, 0.55f)
    blob(th.blobs[3], Offset(w * (0.25f + 0.15f * sin(t * 0.05f + 2f)), h * (0.78f + 0.05f * cos(t * 0.06f))), w * 0.9f, 0.6f)
    blob(th.blobs[2], Offset(w * 0.8f, h * 1.02f), w * 0.8f, 0.7f)
    // Faceted light planes (inspired by the PUMPD backdrop)
    val p1 = Path().apply { moveTo(w * 0.52f, 0f); lineTo(w, 0f); lineTo(w, h * 0.34f); close() }
    drawPath(p1, Color.White.copy(alpha = 0.035f))
    val p2 = Path().apply { moveTo(0f, h * 0.55f); lineTo(w * 0.9f, h * 0.42f); lineTo(0f, h * 0.9f); close() }
    drawPath(p2, Color.Black.copy(alpha = 0.16f))
    val p3 = Path().apply { moveTo(w, h * 0.6f); lineTo(w * 0.35f, h); lineTo(w, h); close() }
    drawPath(p3, Color.White.copy(alpha = 0.025f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(w / 2, h * 0.45f), max(w, h) * 0.85f), size = Size(w, h))
}

private fun DrawScope.grid(th: FitTheme, t: Float, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(th.bgTop, th.bgBottom), 0f, h), size = Size(w, h))
    val step = w / 9f
    var x = 0f
    while (x <= w) { drawLine(Color.White.copy(alpha = 0.04f), Offset(x, 0f), Offset(x, h), 1.5f); x += step }
    var y = 0f
    while (y <= h) { drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(w, y), 1.5f); y += step }
    // Sweeping ribbons
    val shift = sin(t * 0.08f) * w * 0.04f
    for (i in 0..1) {
        val path = Path().apply {
            moveTo(-w * 0.2f, h * (0.25f + i * 0.35f) + shift)
            cubicTo(w * 0.3f, h * (0.05f + i * 0.35f), w * 0.6f, h * (0.6f + i * 0.2f), w * 1.3f, h * (0.2f + i * 0.4f) - shift)
        }
        drawPath(path, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.0f)), Offset.Zero, Offset(w, h)), style = Stroke(width = w * 0.16f))
    }
    blob(th.accent, Offset(w * (0.1f + 0.08f * sin(t * 0.1f)), h * 0.92f), w * 0.7f, 0.12f)
    blob(th.blobs[0], Offset(w * 0.9f, h * 0.1f), w * 0.7f, 0.5f)
}

private fun DrawScope.waves(th: FitTheme, t: Float, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(th.bgTop, th.bgBottom), 0f, h), size = Size(w, h))
    blob(th.blobs[1], Offset(w * (0.2f + 0.15f * sin(t * 0.09f)), h * (0.3f + 0.1f * cos(t * 0.07f))), w * 0.9f, 0.55f)
    blob(th.blobs[3], Offset(w * (0.85f + 0.1f * cos(t * 0.06f)), h * 0.12f), w * 0.8f, 0.5f)
    blob(th.blobs[0], Offset(w * 0.6f, h * (0.85f + 0.05f * sin(t * 0.05f))), w * 1.0f, 0.6f)
    for (i in 0..2) {
        val base = h * (0.55f + i * 0.13f)
        val amp = h * 0.025f
        val ph = t * (0.25f + i * 0.08f) + i * 1.7f
        val path = Path().apply {
            moveTo(0f, base)
            var x = 0f
            while (x <= w) {
                lineTo(x, base + amp * sin((x / w) * 2f * PI.toFloat() * 1.3f + ph))
                x += w / 40f
            }
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(path, Color(0xFF6FA0FF).copy(alpha = 0.06f + i * 0.02f))
    }
    val r = Random(3)
    repeat(28) {
        val sx = r.nextFloat() * w; val sy = r.nextFloat() * h * 0.5f
        val tw = 0.3f + 0.3f * sin(t * (0.6f + r.nextFloat()) + it)
        drawCircle(Color.White.copy(alpha = tw.coerceIn(0f, 1f) * 0.6f), 1.6f + r.nextFloat() * 1.4f, Offset(sx, sy))
    }
}

private fun DrawScope.landscape(t: Float, w: Float, h: Float) {
    val horizon = h * 0.34f
    // base fill for the whole canvas — nothing underneath may ever show through
    drawRect(Brush.verticalGradient(listOf(Color(0xFF9ED9D4), Color(0xFF178F92), Color(0xFF2B5E1A)), 0f, h), size = Size(w, h))
    drawRect(Brush.verticalGradient(listOf(Color(0xFFD9F1EE), Color(0xFF9ED9D4)), 0f, horizon), size = Size(w, horizon))
    blob(Color(0xFFFFF6D8), Offset(w * 0.82f, h * 0.08f), w * 0.45f, 0.9f)
    // clouds drifting
    val cx = ((t * 6f) % (w * 1.4f)) - w * 0.2f
    for ((i, off) in listOf(0f, w * 0.55f).withIndex()) {
        val c = Offset((cx + off) % (w * 1.4f) - w * 0.1f, h * (0.07f + i * 0.06f))
        drawOval(Color.White.copy(alpha = 0.55f), Offset(c.x - w * 0.14f, c.y - h * 0.012f), Size(w * 0.28f, h * 0.028f))
        drawOval(Color.White.copy(alpha = 0.5f), Offset(c.x - w * 0.06f, c.y - h * 0.026f), Size(w * 0.14f, h * 0.035f))
    }
    // sea
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3CC9BC), Color(0xFF178F92), Color(0xFF0D6F78)), horizon, h * 0.75f), Offset(0f, horizon), Size(w, h * 0.75f - horizon))
    val r = Random(11)
    repeat(40) {
        val sx = r.nextFloat() * w; val sy = horizon + r.nextFloat() * h * 0.35f
        val a = 0.15f + 0.25f * sin(t * 1.3f + it * 0.7f)
        drawLine(Color.White.copy(alpha = a.coerceIn(0f, 0.5f)), Offset(sx, sy), Offset(sx + w * 0.04f, sy), 2f)
    }
    // sailboat
    val bx = w * 0.18f + sin(t * 0.05f) * w * 0.03f; val by = horizon + h * 0.07f
    drawPath(Path().apply { moveTo(bx, by); lineTo(bx, by - h * 0.05f); lineTo(bx + w * 0.03f, by - h * 0.004f); close() }, Color.White)
    drawLine(Color(0xFF6B4B3A), Offset(bx - w * 0.015f, by + 2f), Offset(bx + w * 0.035f, by + 2f), 4f)
    // cliff
    drawPath(Path().apply {
        moveTo(w * 0.42f, 0f); lineTo(w * 0.62f, 0f); lineTo(w * 0.7f, h * 0.2f); lineTo(w * 0.5f, h * 0.36f); lineTo(w * 0.4f, h * 0.2f); close()
    }, Brush.verticalGradient(listOf(Color(0xFF4F5B4E), Color(0xFF2F3A30)), 0f, h * 0.36f))
    // hills
    fun hill(y0: Float, y1: Float, top: Color, bottom: Color, sway: Float) {
        val p = Path().apply {
            moveTo(-w * 0.1f, h)
            lineTo(-w * 0.1f, y1 + h * 0.12f)
            cubicTo(w * 0.3f, y0 + sway, w * 0.6f, y1, w * 1.1f, y0 - h * 0.05f)
            lineTo(w * 1.1f, h); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(top, bottom), y0 - h * 0.05f, h))
    }
    val sway = sin(t * 0.4f) * 4f
    hill(h * 0.18f, h * 0.35f, Color(0xFFA8D65A), Color(0xFF5E9E32), sway)
    hill(h * 0.32f, h * 0.5f, Color(0xFF8CC24A), Color(0xFF3F7D24), sway * 1.5f)
    hill(h * 0.48f, h * 0.66f, Color(0xFF79B23C), Color(0xFF2B5E1A), sway * 2f)
    flowerSeeds.forEach { (fx, fy, k) ->
        val px = w * (0.35f + fx * 0.65f); val py = h * (0.55f + fy * 0.43f)
        val col = when (k) { 0 -> Color(0xFFF2B8D8); 1 -> Color(0xFFFFFFFF); else -> Color(0xFFD9A0F0) }
        drawCircle(col.copy(alpha = 0.85f), 3.5f + fy * 4f, Offset(px + sin(t + fx * 10f) * 1.5f, py))
    }
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent, Color.Black.copy(alpha = 0.18f)), 0f, h), size = Size(w, h))
}

private fun DrawScope.frost(th: FitTheme, t: Float, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(th.bgTop, th.bgBottom), 0f, h), size = Size(w, h))
    blob(th.blobs[0], Offset(w * (0.15f + 0.1f * sin(t * 0.08f)), h * 0.18f), w * 0.8f, 0.8f)
    blob(th.blobs[1], Offset(w * (0.9f + 0.08f * cos(t * 0.07f)), h * 0.42f), w * 0.8f, 0.8f)
    blob(th.blobs[2], Offset(w * 0.3f, h * (0.8f + 0.05f * sin(t * 0.05f))), w * 0.9f, 0.8f)
    blob(th.blobs[3], Offset(w * 0.9f, h * 0.95f), w * 0.6f, 0.7f)
}
