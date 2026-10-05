package com.myfit.tracker.ui.arena

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable

/*
 * Flat vector illustrations for the journeys (Samsung Health style): soft gradient sky, layered hills and one clean
 * landmark silhouette per route. Drawn in code — sharp on every screen and no image files.
 */

private data class Palette(val skyTop: Color, val skyBottom: Color, val sun: Color, val far: Color, val mid: Color, val near: Color, val ink: Color, val accent: Color)

private val PALETTES = mapOf(
    "lahore" to Palette(Color(0xFFFF9F7A), Color(0xFFFFE2C2), Color(0xFFFFF4D6), Color(0xFFF2B48C), Color(0xFFE08E64), Color(0xFFC9714B), Color(0xFF8A3A2B), Color(0xFFFFF6EC)),
    "k2" to Palette(Color(0xFF7DB7F5), Color(0xFFE4F1FF), Color(0xFFFFFFFF), Color(0xFFC7DCF3), Color(0xFF93B5DB), Color(0xFF5E83AD), Color(0xFF3D5C82), Color(0xFFFFFFFF)),
    "kkh" to Palette(Color(0xFF79D3C8), Color(0xFFE6FAF6), Color(0xFFFFFBE6), Color(0xFFA9D8CF), Color(0xFF6FA7A3), Color(0xFF4C7E80), Color(0xFF2F4A4F), Color(0xFF3CC6C0)),
    "arafat" to Palette(Color(0xFFFFC27A), Color(0xFFFFF0D4), Color(0xFFFFF8E8), Color(0xFFF0D2A0), Color(0xFFE2B677), Color(0xFFC9955A), Color(0xFF7A5A36), Color(0xFFFFFFFF)),
    "inca" to Palette(Color(0xFF8ED9B6), Color(0xFFEAFBF1), Color(0xFFFFFDE8), Color(0xFFB4E2C6), Color(0xFF6FB98C), Color(0xFF3F8A60), Color(0xFF2A5E42), Color(0xFFC8B596)),
    "greatwall" to Palette(Color(0xFFFFB38A), Color(0xFFFFE8D6), Color(0xFFFFF5E1), Color(0xFFF0B892), Color(0xFFD98E5F), Color(0xFFB5683F), Color(0xFF7E4A2E), Color(0xFFE9C9A7)),
    "paris" to Palette(Color(0xFFA996F5), Color(0xFFFFDDEE), Color(0xFFFFF0F6), Color(0xFFD7C3F2), Color(0xFFB9A3E6), Color(0xFF8E78C9), Color(0xFF39345F), Color(0xFF8FB8FF)),
    "london" to Palette(Color(0xFF8EAEF5), Color(0xFFE5EDFF), Color(0xFFFFFFFF), Color(0xFFC3D2F2), Color(0xFF97ABDA), Color(0xFF6A80B5), Color(0xFF2B3757), Color(0xFF8FB8FF)),
)

fun journeyAccent(id: String): Color = PALETTES[id]?.near ?: Color(0xFF2CC9A7)
fun journeySky(id: String): List<Color> = PALETTES[id]?.let { listOf(it.skyTop, it.skyBottom) } ?: listOf(Color(0xFF2CC9A7), Color(0xFF0C1414))

@Composable
fun JourneyCoverArt(id: String, modifier: Modifier) {
    Canvas(modifier) { journeyScene(id) }
}

/** Draws the whole cover scene for journey [id] into the current canvas. */
fun DrawScope.journeyScene(id: String) {
    val p = PALETTES[id] ?: PALETTES.getValue("lahore")
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(p.skyTop, p.skyBottom)))
    // sun + soft halo
    val sun = Offset(w * 0.78f, h * 0.26f)
    drawCircle(Brush.radialGradient(listOf(p.sun.copy(alpha = 0.55f), p.sun.copy(alpha = 0f)), sun, h * 0.32f), h * 0.32f, sun)
    drawCircle(p.sun, h * 0.09f, sun)
    // two drifting clouds
    cloud(Offset(w * 0.18f, h * 0.2f), h * 0.05f, Color.White.copy(alpha = 0.75f))
    cloud(Offset(w * 0.55f, h * 0.12f), h * 0.035f, Color.White.copy(alpha = 0.6f))
    when (id) {
        "k2", "kkh" -> peaks(p, id == "k2")
        else -> hills(p.far, h * 0.62f, 0.9f, 0.6f)
    }
    when (id) {
        "lahore" -> lahore(p)
        "k2" -> {}
        "kkh" -> road(p)
        "arafat" -> arafat(p)
        "inca" -> inca(p)
        "greatwall" -> greatWall(p)
        "paris" -> eiffel(p)
        "london" -> bigBen(p)
    }
    // foreground band
    hills(p.near, h * 0.86f, 0.6f, 1.4f)
}

private fun DrawScope.cloud(c: Offset, r: Float, col: Color) {
    // one path (union of shapes) so overlapping parts don't show seams
    val p = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
        addOval(androidx.compose.ui.geometry.Rect(c.x + r * 1.2f - r * 1.3f, c.y - r * 0.3f - r * 1.3f, c.x + r * 1.2f + r * 1.3f, c.y - r * 0.3f + r * 1.3f))
        addOval(androidx.compose.ui.geometry.Rect(c.x + r * 1.4f, c.y - r, c.x + r * 3.4f, c.y + r))
        addRoundRect(androidx.compose.ui.geometry.RoundRect(c.x - r * 0.2f, c.y - r * 0.1f, c.x + r * 2.8f, c.y + r, r / 2, r / 2))
    }
    drawPath(p, col)
}

/** A smooth band of hills whose top sits around [baseY]. */
private fun DrawScope.hills(col: Color, baseY: Float, amp: Float, freq: Float) {
    val w = size.width; val h = size.height
    val path = Path().apply {
        moveTo(0f, h); lineTo(0f, baseY)
        val n = 4
        for (i in 0 until n) {
            val x0 = w * i / n; val x1 = w * (i + 1) / n
            val up = if (i % 2 == 0) -1f else 1f
            cubicTo(x0 + (x1 - x0) * 0.3f, baseY + up * h * 0.06f * amp, x0 + (x1 - x0) * 0.7f, baseY - up * h * 0.05f * amp * freq, x1, baseY)
        }
        lineTo(w, h); close()
    }
    drawPath(path, col)
}

private fun DrawScope.mountain(peakX: Float, peakY: Float, halfW: Float, base: Float, col: Color, snow: Color?) {
    val p = Path().apply { moveTo(peakX - halfW, base); lineTo(peakX, peakY); lineTo(peakX + halfW, base); close() }
    drawPath(p, col)
    if (snow != null) {
        val s = (base - peakY) * 0.28f
        val cap = Path().apply {
            moveTo(peakX, peakY); lineTo(peakX + halfW * s / (base - peakY), peakY + s)
            lineTo(peakX + halfW * s / (base - peakY) * 0.35f, peakY + s * 0.8f); lineTo(peakX, peakY + s * 1.05f)
            lineTo(peakX - halfW * s / (base - peakY) * 0.4f, peakY + s * 0.78f); lineTo(peakX - halfW * s / (base - peakY), peakY + s); close()
        }
        drawPath(cap, snow)
    }
    // shaded right face
    val shade = Path().apply { moveTo(peakX, peakY); lineTo(peakX + halfW, base); lineTo(peakX + halfW * 0.15f, base); close() }
    drawPath(shade, Color.Black.copy(alpha = 0.08f))
}

private fun DrawScope.peaks(p: Palette, big: Boolean) {
    val w = size.width; val h = size.height
    mountain(w * 0.15f, h * 0.38f, w * 0.28f, h * 0.8f, p.far, Color.White.copy(alpha = 0.85f))
    mountain(w * 0.82f, h * 0.34f, w * 0.3f, h * 0.8f, p.far, Color.White.copy(alpha = 0.85f))
    if (big) mountain(w * 0.48f, h * 0.12f, w * 0.34f, h * 0.84f, p.mid, Color.White)
    else mountain(w * 0.5f, h * 0.28f, w * 0.3f, h * 0.82f, p.mid, Color.White)
    hills(p.mid.copy(alpha = 0.8f), h * 0.74f, 0.5f, 1f)
}

private fun DrawScope.road(p: Palette) {
    val w = size.width; val h = size.height
    // turquoise Attabad lake
    drawOval(p.accent, Offset(w * 0.05f, h * 0.7f), Size(w * 0.42f, h * 0.08f))
    val road = Path().apply {
        moveTo(w * 0.62f, h); cubicTo(w * 0.35f, h * 0.9f, w * 0.85f, h * 0.82f, w * 0.6f, h * 0.74f)
        cubicTo(w * 0.45f, h * 0.7f, w * 0.62f, h * 0.66f, w * 0.55f, h * 0.62f)
    }
    drawPath(road, p.ink, style = Stroke(h * 0.06f, cap = StrokeCap.Round))
    drawPath(road, Color.White.copy(alpha = 0.85f), style = Stroke(h * 0.008f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(h * 0.03f, h * 0.03f))))
}

private fun DrawScope.lahore(p: Palette) {
    val w = size.width; val h = size.height
    val base = h * 0.78f
    val cx = w * 0.42f
    // prayer hall
    drawRect(p.ink, Offset(cx - w * 0.2f, base - h * 0.16f), Size(w * 0.4f, h * 0.16f))
    // arches
    for (i in 0 until 5) {
        val ax = cx - w * 0.16f + i * w * 0.08f
        drawRoundRect(p.accent.copy(alpha = 0.35f), Offset(ax - w * 0.02f, base - h * 0.11f), Size(w * 0.04f, h * 0.11f), CornerRadius(w * 0.02f))
    }
    // three domes (white marble)
    fun dome(x: Float, r: Float) {
        drawCircle(p.accent, r, Offset(x, base - h * 0.16f - r * 0.55f))
        drawRect(p.accent, Offset(x - r, base - h * 0.16f - r * 0.55f), Size(r * 2, r * 0.55f))
        drawLine(p.accent, Offset(x, base - h * 0.16f - r * 1.55f), Offset(x, base - h * 0.16f - r * 2.0f), h * 0.008f)
    }
    dome(cx, w * 0.07f); dome(cx - w * 0.13f, w * 0.045f); dome(cx + w * 0.13f, w * 0.045f)
    // four minarets
    listOf(-0.27f, 0.27f).forEach { d ->
        val mx = cx + w * d
        drawRect(p.ink, Offset(mx - w * 0.018f, base - h * 0.42f), Size(w * 0.036f, h * 0.42f))
        drawRect(p.ink, Offset(mx - w * 0.028f, base - h * 0.3f), Size(w * 0.056f, h * 0.015f))
        drawCircle(p.accent, w * 0.022f, Offset(mx, base - h * 0.43f))
    }
    // Minar-e-Pakistan in the distance
    val tx = w * 0.84f
    val t = Path().apply { moveTo(tx - w * 0.03f, base); lineTo(tx - w * 0.008f, base - h * 0.4f); lineTo(tx + w * 0.008f, base - h * 0.4f); lineTo(tx + w * 0.03f, base); close() }
    drawPath(t, p.ink.copy(alpha = 0.7f))
}

private fun DrawScope.arafat(p: Palette) {
    val w = size.width; val h = size.height
    // Jabal al-Rahmah with its white pillar
    val hill = Path().apply { moveTo(w * 0.45f, h * 0.8f); cubicTo(w * 0.55f, h * 0.45f, w * 0.75f, h * 0.45f, w * 0.88f, h * 0.8f); close() }
    drawPath(hill, p.near)
    drawRect(p.accent, Offset(w * 0.655f, h * 0.38f), Size(w * 0.016f, h * 0.14f))
    // rows of white tents (Mina)
    for (row in 0 until 2) for (i in 0 until 7) {
        val x = w * (0.04f + i * 0.065f + row * 0.03f); val y = h * (0.72f + row * 0.07f)
        val tent = Path().apply { moveTo(x, y); lineTo(x + w * 0.03f, y - h * 0.06f); lineTo(x + w * 0.06f, y); close() }
        drawPath(tent, p.accent); drawPath(tent, p.ink.copy(alpha = 0.15f), style = Stroke(h * 0.003f))
    }
}

private fun DrawScope.inca(p: Palette) {
    val w = size.width; val h = size.height
    // Huayna Picchu
    val peak = Path().apply { moveTo(w * 0.5f, h * 0.8f); cubicTo(w * 0.58f, h * 0.2f, w * 0.7f, h * 0.18f, w * 0.78f, h * 0.35f); cubicTo(w * 0.85f, h * 0.5f, w * 0.9f, h * 0.7f, w * 0.95f, h * 0.8f); close() }
    drawPath(peak, p.near)
    // terraces
    for (i in 0 until 5) {
        val y = h * (0.62f + i * 0.04f)
        drawRoundRect(p.mid, Offset(w * (0.06f + i * 0.02f), y), Size(w * (0.44f - i * 0.03f), h * 0.025f), CornerRadius(h * 0.01f))
    }
    // stone ruins
    for (i in 0 until 4) drawRect(p.accent, Offset(w * (0.12f + i * 0.08f), h * 0.54f), Size(w * 0.05f, h * 0.07f))
    for (i in 0 until 4) { val x = w * (0.12f + i * 0.08f); val roof = Path().apply { moveTo(x - w * 0.005f, h * 0.54f); lineTo(x + w * 0.025f, h * 0.5f); lineTo(x + w * 0.055f, h * 0.54f); close() }; drawPath(roof, p.ink) }
}

private fun DrawScope.greatWall(p: Palette) {
    val w = size.width; val h = size.height
    hills(p.mid, h * 0.66f, 1.2f, 0.8f)
    val wall = Path().apply {
        moveTo(-w * 0.05f, h * 0.8f); cubicTo(w * 0.15f, h * 0.55f, w * 0.3f, h * 0.75f, w * 0.45f, h * 0.58f)
        cubicTo(w * 0.6f, h * 0.42f, w * 0.75f, h * 0.68f, w * 1.05f, h * 0.5f)
    }
    drawPath(wall, p.ink, style = Stroke(h * 0.035f, cap = StrokeCap.Round))
    drawPath(wall, p.accent, style = Stroke(h * 0.012f, cap = StrokeCap.Round))
    // watchtowers along the wall
    listOf(0.06f to 0.68f, 0.45f to 0.55f, 0.82f to 0.56f).forEach { (fx, fy) ->
        drawRect(p.ink, Offset(w * fx - w * 0.03f, h * fy - h * 0.1f), Size(w * 0.06f, h * 0.1f))
        drawRect(p.accent.copy(alpha = 0.6f), Offset(w * fx - w * 0.01f, h * fy - h * 0.065f), Size(w * 0.02f, h * 0.03f))
    }
}

private fun DrawScope.eiffel(p: Palette) {
    val w = size.width; val h = size.height
    val cx = w * 0.4f; val base = h * 0.82f; val top = h * 0.1f
    // the Seine
    drawRoundRect(p.accent.copy(alpha = 0.7f), Offset(0f, h * 0.8f), Size(w, h * 0.04f), CornerRadius(h * 0.02f))
    val legs = Path().apply {
        moveTo(cx - w * 0.16f, base); cubicTo(cx - w * 0.06f, h * 0.6f, cx - w * 0.03f, h * 0.35f, cx - w * 0.012f, top + h * 0.08f)
        lineTo(cx + w * 0.012f, top + h * 0.08f); cubicTo(cx + w * 0.03f, h * 0.35f, cx + w * 0.06f, h * 0.6f, cx + w * 0.16f, base)
        lineTo(cx + w * 0.09f, base); quadraticTo(cx, h * 0.62f, cx - w * 0.09f, base); close()
    }
    drawPath(legs, p.ink)
    drawRect(p.ink, Offset(cx - w * 0.09f, h * 0.6f), Size(w * 0.18f, h * 0.02f))
    drawRect(p.ink, Offset(cx - w * 0.05f, h * 0.38f), Size(w * 0.1f, h * 0.015f))
    drawLine(p.ink, Offset(cx, top + h * 0.08f), Offset(cx, top), h * 0.008f)
    // Sacré-Cœur on a far hill
    drawCircle(Color.White.copy(alpha = 0.85f), w * 0.04f, Offset(w * 0.82f, h * 0.55f))
    drawRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.77f, h * 0.55f), Size(w * 0.1f, h * 0.07f))
}

private fun DrawScope.bigBen(p: Palette) {
    val w = size.width; val h = size.height
    val cx = w * 0.32f; val base = h * 0.8f
    drawRoundRect(p.accent.copy(alpha = 0.6f), Offset(0f, h * 0.79f), Size(w, h * 0.05f), CornerRadius(h * 0.02f))
    // Palace of Westminster
    drawRect(p.ink.copy(alpha = 0.85f), Offset(cx + w * 0.05f, base - h * 0.14f), Size(w * 0.5f, h * 0.14f))
    for (i in 0 until 8) drawRect(p.ink.copy(alpha = 0.85f), Offset(cx + w * (0.07f + i * 0.06f), base - h * 0.18f), Size(w * 0.012f, h * 0.05f))
    // Elizabeth Tower
    drawRect(p.ink, Offset(cx - w * 0.045f, base - h * 0.48f), Size(w * 0.09f, h * 0.48f))
    drawCircle(Color(0xFFFFF4D6), w * 0.032f, Offset(cx, base - h * 0.42f))
    drawCircle(p.ink, w * 0.004f, Offset(cx, base - h * 0.42f))
    val spire = Path().apply { moveTo(cx - w * 0.05f, base - h * 0.48f); lineTo(cx, base - h * 0.68f); lineTo(cx + w * 0.05f, base - h * 0.48f); close() }
    drawPath(spire, p.ink)
    // London Eye
    val eye = Offset(w * 0.86f, base - h * 0.22f)
    drawCircle(p.ink.copy(alpha = 0.6f), h * 0.2f, eye, style = Stroke(h * 0.01f))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        drawLine(p.ink.copy(alpha = 0.4f), eye, Offset(eye.x + (kotlin.math.cos(a) * h * 0.2f).toFloat(), eye.y + (kotlin.math.sin(a) * h * 0.2f).toFloat()), h * 0.004f)
    }
}

