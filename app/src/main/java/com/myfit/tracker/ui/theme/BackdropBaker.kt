package com.myfit.tracker.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.ceil

/**
 * Renders the theme once into small bitmaps: the backdrop and its two blurred versions. Everything
 * on screen then just draws these images — no shaders or blurs run per frame.
 */
object BackdropBaker {
    private const val K = 3f   // backdrops are soft, so 1/3 resolution is visually identical

    suspend fun bake(
        gfx: GraphicsContext, density: Density, theme: FitTheme, image: ImageBitmap?, full: Size,
        cardBlurPx: Float, dockBlurPx: Float, blurOk: Boolean,
    ): Triple<ImageBitmap, ImageBitmap?, ImageBitmap?> {
        val k = if (image != null) 2f else K
        val sw = full.width / k; val sh = full.height / k
        val sz = IntSize(ceil(sw).toInt().coerceAtLeast(1), ceil(sh).toInt().coerceAtLeast(1))
        val base = gfx.createGraphicsLayer()
        try {
            base.record(density, LayoutDirection.Ltr, sz) { drawBackdrop(theme, image, theme.stillT, sw, sh) }
            val bg = base.toImageBitmap()
            if (!blurOk) return Triple(bg, null, null)
            suspend fun blurred(px: Float): ImageBitmap? {
                if (px / k <= 0.5f) return bg
                val l = gfx.createGraphicsLayer()
                return try {
                    l.renderEffect = BlurEffect(px / k, px / k, TileMode.Clamp)
                    l.record(density, LayoutDirection.Ltr, sz) { drawImage(bg) }
                    l.toImageBitmap()
                } finally { gfx.releaseGraphicsLayer(l) }
            }
            return Triple(bg, blurred(cardBlurPx), blurred(dockBlurPx))
        } finally { gfx.releaseGraphicsLayer(base) }
    }
}

/** Draws a baked (small) backdrop image stretched over [full] (root coordinates). */
fun DrawScope.drawBaked(img: ImageBitmap, full: Size) {
    drawImage(
        img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset.Zero, dstSize = IntSize(ceil(full.width).toInt(), ceil(full.height).toInt()),
    )
}
