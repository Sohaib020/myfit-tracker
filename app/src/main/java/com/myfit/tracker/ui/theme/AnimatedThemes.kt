package com.myfit.tracker.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * 16 animated backdrops. Each is a pure function of time `t` (seconds), so the same frame is
 * replayed identically inside every glass surface. Particle positions come from fixed seeds.
 */

private data class Seed(val x: Float, val y: Float, val s: Float, val p: Float)
private fun seeds(seed: Int, n: Int) = Random(seed).let { r -> List(n) { Seed(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.283f) } }
private val S_STARS = seeds(11, 140)
private val S_FALL = seeds(23, 46)
private val S_RAIN = seeds(37, 90)
private val S_BUBBLES = seeds(41, 34)
private val S_WINDOWS = seeds(53, 220)
private val S_BOKEH = seeds(61, 24)
private val S_TREES = seeds(71, 30)

private fun DrawScope.vgrad(top: Color, bottom: Color, w: Float, h: Float) =
    drawRect(Brush.verticalGradient(listOf(top, bottom), 0f, h), size = Size(w, h))

private fun DrawScope.glow(c: Color, center: Offset, r: Float, a: Float) =
    drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = 0f)), center, r), r, center)

private fun wrap(v: Float) = ((v % 1f) + 1f) % 1f

fun DrawScope.drawAnimatedArt(th: FitTheme, t: Float, w: Float, h: Float) {
    when (th.art) {
        BackdropArt.BOREALIS -> borealis(t, w, h)
        BackdropArt.OCEAN -> ocean(t, w, h)
        BackdropArt.SAKURA -> sakura(t, w, h)
        BackdropArt.NEON_CITY -> neonCity(t, w, h)
        BackdropArt.DUNES -> dunes(t, w, h, night = false)
        BackdropArt.DESERT_NIGHT -> dunes(t, w, h, night = true)
        BackdropArt.GALAXY -> galaxy(t, w, h)
        BackdropArt.FOREST -> forest(t, w, h)
        BackdropArt.LAVA -> lava(t, w, h)
        BackdropArt.RAIN -> rain(t, w, h, storm = false)
        BackdropArt.MONSOON -> rain(t, w, h, storm = true)
        BackdropArt.MINT -> mint(t, w, h)
        BackdropArt.GOLD -> gold(t, w, h)
        BackdropArt.ARCTIC -> arctic(t, w, h)
        BackdropArt.CYBER -> cyber(t, w, h)
        BackdropArt.LOTUS -> lotus(t, w, h)
        else -> vgrad(th.bgTop, th.bgBottom, w, h)
    }
}

private fun DrawScope.stars(t: Float, w: Float, h: Float, maxY: Float, n: Int = 110) {
    S_STARS.take(n).forEach { s ->
        val a = (0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.8f + s.s * 2f) + s.p))) * (0.4f + s.s * 0.6f)
        drawCircle(Color.White.copy(alpha = a), 0.8f + s.s * 2.2f, Offset(s.x * w, s.y * h * maxY))
    }
}

private fun DrawScope.borealis(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF020716), Color(0xFF06222B), w, h)
    stars(t, w, h, 0.7f)
    val bands = listOf(Color(0xFF3DFFB0) to 0.30f, Color(0xFF26C6DA) to 0.22f, Color(0xFF8A5CFF) to 0.18f)
    bands.forEachIndexed { i, (c, a) ->
        val base = h * (0.22f + i * 0.09f)
        val path = Path().apply {
            moveTo(0f, base)
            var x = 0f
            while (x <= w) {
                val y = base + sin(x / w * 5f + t * (0.35f + i * 0.1f) + i) * h * 0.05f + sin(x / w * 11f - t * 0.6f) * h * 0.015f
                lineTo(x, y); x += w / 36f
            }
            lineTo(w, base + h * 0.42f); lineTo(0f, base + h * 0.42f); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = 0f), c.copy(alpha = a), c.copy(alpha = 0f)), base - h * 0.05f, base + h * 0.42f))
    }
    // snowy horizon
    drawPath(Path().apply { moveTo(0f, h * 0.82f); cubicTo(w * 0.3f, h * 0.76f, w * 0.6f, h * 0.86f, w, h * 0.8f); lineTo(w, h); lineTo(0f, h); close() }, Color(0xFF0B1E2A))
}

private fun DrawScope.ocean(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF0B5E8A), Color(0xFF001220), w, h)
    // god rays
    for (i in 0..4) {
        val x = w * (0.1f + i * 0.22f) + sin(t * 0.3f + i) * w * 0.03f
        drawPath(Path().apply { moveTo(x, 0f); lineTo(x + w * 0.1f, 0f); lineTo(x + w * 0.35f, h); lineTo(x + w * 0.05f, h); close() },
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), 0f, h * 0.9f))
    }
    glow(Color(0xFF22D3EE), Offset(w * 0.5f, 0f), w * 0.9f, 0.25f)
    S_BUBBLES.forEach { b ->
        val y = h * (1f - wrap(b.y + t * (0.03f + b.s * 0.05f)))
        val x = b.x * w + sin(t * 1.2f + b.p) * 8f
        val r = 2f + b.s * 7f
        drawCircle(Color.White.copy(alpha = 0.22f), r, Offset(x, y), style = Stroke(1.4f))
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.25f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
    // seabed
    drawPath(Path().apply { moveTo(0f, h * 0.9f); cubicTo(w * 0.35f, h * 0.86f, w * 0.65f, h * 0.95f, w, h * 0.88f); lineTo(w, h); lineTo(0f, h); close() }, Color(0xFF02101A))
}

private fun DrawScope.petal(x: Float, y: Float, s: Float, rot: Float, c: Color) {
    rotate(rot, Offset(x, y)) {
        drawPath(Path().apply { moveTo(x, y - s); quadraticBezierTo(x + s * 0.9f, y - s * 0.2f, x, y + s); quadraticBezierTo(x - s * 0.9f, y - s * 0.2f, x, y - s); close() }, c)
    }
}

private fun DrawScope.sakura(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFFFFF1F5), Color(0xFFF8C6D6), w, h)
    glow(Color(0xFFFFFFFF), Offset(w * 0.8f, h * 0.12f), w * 0.6f, 0.8f)
    // branch
    val br = Path().apply { moveTo(-w * 0.05f, h * 0.12f); cubicTo(w * 0.25f, h * 0.05f, w * 0.4f, h * 0.2f, w * 0.72f, h * 0.1f) }
    drawPath(br, Color(0xFF6B3F3A), style = Stroke(w * 0.02f, cap = StrokeCap.Round))
    Random(5).let { r -> repeat(26) { val f = r.nextFloat(); val px = -w * 0.05f + f * w * 0.77f; val py = h * (0.12f - 0.06f * sin(f * 3f)) + (r.nextFloat() - 0.5f) * h * 0.06f
        drawCircle(Color(0xFFFF9EBB).copy(alpha = 0.9f), w * (0.018f + r.nextFloat() * 0.015f), Offset(px, py)) } }
    S_FALL.forEach { p ->
        val y = h * wrap(p.y + t * (0.025f + p.s * 0.03f))
        val x = w * wrap(p.x + sin(t * 0.6f + p.p) * 0.03f + t * 0.01f)
        petal(x, y, 5f + p.s * 7f, t * 60f * (p.s - 0.5f) + p.p * 57f, Color(0xFFFF7FA6).copy(alpha = 0.55f + p.s * 0.35f))
    }
}

private fun DrawScope.neonCity(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF12032E), Color(0xFF2A0845), w, h)
    glow(Color(0xFFFF2E97), Offset(w * 0.5f, h * 0.55f), w * 0.9f, 0.35f)
    glow(Color(0xFF00E5FF), Offset(w * 0.1f, h * 0.2f), w * 0.6f, 0.2f)
    stars(t, w, h, 0.4f, 40)
    // two skyline layers
    for (layer in 0..1) {
        val r = Random(90 + layer)
        var x = 0f
        val baseY = h * (0.62f + layer * 0.1f)
        val col = if (layer == 0) Color(0xFF1E0B3D) else Color(0xFF0C0420)
        while (x < w) {
            val bw = w * (0.06f + r.nextFloat() * 0.1f)
            val bh = h * (0.12f + r.nextFloat() * 0.28f) * (if (layer == 0) 1f else 0.8f)
            drawRect(col, Offset(x, baseY - bh), Size(bw, bh + h))
            if (layer == 1) {
                var wy = baseY - bh + 8f
                var rows = 0
                while (wy < baseY && rows < 12) {
                    rows++
                    var wx = x + 5f
                    while (wx < x + bw - 6f) {
                        val on = sin(wx * 0.37f + wy * 0.11f + (t * 0.5f).toInt() * 1.7f) > 0.35f
                        if (on) drawRect((if ((wx + wy).toInt() % 3 == 0) Color(0xFF00E5FF) else Color(0xFFFFC857)).copy(alpha = 0.7f), Offset(wx, wy), Size(4f, 5f))
                        wx += 14f
                    }
                    wy += 18f
                }
            }
            x += bw + 3f
        }
    }
    // neon sign flicker
    val flick = if (sin(t * 13f) > -0.85f) 1f else 0.25f
    drawRoundRect(Color(0xFFFF2E97).copy(alpha = 0.85f * flick), Offset(w * 0.62f, h * 0.44f), Size(w * 0.18f, h * 0.03f), CornerRadius(12f), style = Stroke(4f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA0C0420)), h * 0.75f, h), Offset(0f, h * 0.75f), Size(w, h * 0.25f))
}

private fun DrawScope.dunes(t: Float, w: Float, h: Float, night: Boolean) {
    if (night) {
        vgrad(Color(0xFF070B22), Color(0xFF26204A), w, h)
        stars(t, w, h, 0.6f)
        glow(Color(0xFFFFF4D6), Offset(w * 0.75f, h * 0.18f), w * 0.3f, 0.5f)
        drawCircle(Color(0xFFFFF3D1), w * 0.07f, Offset(w * 0.75f, h * 0.18f))
        drawCircle(Color(0xFF070B22).copy(alpha = 0.9f), w * 0.065f, Offset(w * 0.78f, h * 0.165f))
    } else {
        vgrad(Color(0xFFFF7E5F), Color(0xFFFEC680), w, h)
        val sy = h * (0.42f + 0.01f * sin(t * 0.2f))
        glow(Color(0xFFFFF1B8), Offset(w * 0.5f, sy), w * 0.6f, 0.8f)
        drawCircle(Color(0xFFFFE08A), w * 0.13f, Offset(w * 0.5f, sy))
    }
    val cols = if (night) listOf(Color(0xFF2E2754), Color(0xFF211C40), Color(0xFF15122D)) else listOf(Color(0xFFE9A15F), Color(0xFFD9814A), Color(0xFFB9603A))
    cols.forEachIndexed { i, c ->
        val base = h * (0.55f + i * 0.12f)
        val drift = sin(t * 0.05f * (i + 1)) * w * 0.05f
        drawPath(Path().apply {
            moveTo(-w * 0.1f, h); lineTo(-w * 0.1f, base)
            cubicTo(w * 0.25f + drift, base - h * 0.1f, w * 0.55f + drift, base + h * 0.06f, w * 1.1f, base - h * 0.05f)
            lineTo(w * 1.1f, h); close()
        }, Brush.verticalGradient(listOf(c.copy(red = minOf(1f, c.red * 1.15f)), c), base - h * 0.1f, h))
    }
}

private fun DrawScope.galaxy(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF05010F), Color(0xFF0B0320), w, h)
    val c = Offset(w * 0.5f, h * 0.45f)
    rotate(t * 2f, c) {
        glow(Color(0xFF8A2BE2), Offset(w * 0.35f, h * 0.35f), w * 0.7f, 0.45f)
        glow(Color(0xFFFF4FA3), Offset(w * 0.68f, h * 0.55f), w * 0.6f, 0.35f)
        glow(Color(0xFF3D7BFF), Offset(w * 0.45f, h * 0.7f), w * 0.55f, 0.3f)
    }
    S_STARS.forEachIndexed { i, s ->
        val ang = s.p + t * 0.02f * (1f + s.s)
        val rad = (0.1f + s.x * 0.9f) * w * 0.8f
        val x = c.x + cos(ang) * rad
        val y = c.y + sin(ang) * rad * 0.55f
        val a = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 2f + i))
        drawCircle(Color.White.copy(alpha = a * (0.3f + s.s * 0.7f)), 0.8f + s.s * 2f, Offset(x, y))
    }
    glow(Color(0xFFFFF0F8), c, w * 0.12f, 0.6f)
}

private fun DrawScope.forest(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF1E4A3A), Color(0xFF071710), w, h)
    glow(Color(0xFFE8FFE0), Offset(w * 0.3f, h * 0.15f), w * 0.7f, 0.35f)
    for (layer in 0..2) {
        val col = listOf(Color(0xFF2E5B48), Color(0xFF1B3E30), Color(0xFF0B2219))[layer]
        val base = h * (0.55f + layer * 0.14f)
        S_TREES.forEachIndexed { i, s ->
            if (i % 3 != layer) return@forEachIndexed
            val x = s.x * w * 1.2f - w * 0.1f
            val th = h * (0.18f + s.s * 0.16f) * (1f + layer * 0.2f)
            drawPath(Path().apply { moveTo(x, base - th); lineTo(x + th * 0.28f, base + 4f); lineTo(x - th * 0.28f, base + 4f); close() }, col)
        }
        drawRect(col, Offset(0f, base), Size(w, h - base))
        // mist between layers
        val mx = wrap(t * 0.01f * (layer + 1)) * w
        for (k in -1..1) drawOval(Color.White.copy(alpha = 0.06f), Offset(mx + k * w - w * 0.4f, base - h * 0.04f), Size(w * 0.9f, h * 0.07f))
    }
}

private fun DrawScope.lava(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF3A0B02), Color(0xFF120300), w, h)
    val cols = listOf(Color(0xFFFF7A1A), Color(0xFFFF3D2E), Color(0xFFFFB02E), Color(0xFFFF5A1F), Color(0xFFE8321C))
    for (i in 0 until 7) {
        val ph = t * (0.05f + i * 0.012f) + i * 0.37f
        val y = h * (1.1f - wrap(ph) * 1.25f)
        val x = w * (0.2f + 0.6f * (0.5f + 0.5f * sin(i * 1.7f + t * 0.1f)))
        val r = w * (0.12f + 0.06f * sin(i + t * 0.3f))
        drawCircle(Brush.radialGradient(listOf(cols[i % cols.size], cols[i % cols.size].copy(alpha = 0.0f)), Offset(x, y), r * 1.3f), r * 1.3f, Offset(x, y))
        drawOval(cols[i % cols.size].copy(alpha = 0.85f), Offset(x - r, y - r * (1f + 0.15f * sin(t + i))), Size(r * 2, r * 2 * (1f + 0.15f * sin(t + i))))
    }
    glow(Color(0xFFFF7A1A), Offset(w * 0.5f, h), w, 0.4f)
}

private fun DrawScope.rain(t: Float, w: Float, h: Float, storm: Boolean) {
    vgrad(if (storm) Color(0xFF2B3442) else Color(0xFF0D1428), if (storm) Color(0xFF0E131B) else Color(0xFF04070F), w, h)
    // clouds
    val cloudCol = if (storm) Color(0xFF3B4554) else Color(0xFF1A2440)
    for (i in 0..5) {
        val x = w * wrap(i * 0.21f + t * (0.006f + i * 0.001f)) * 1.4f - w * 0.2f
        drawOval(cloudCol.copy(alpha = 0.8f), Offset(x - w * 0.3f, h * (0.02f + (i % 3) * 0.05f)), Size(w * 0.6f, h * 0.14f))
    }
    if (!storm) glow(Color(0xFFFFC66E), Offset(w * 0.8f, h * 0.7f), w * 0.5f, 0.25f)
    if (storm) {
        val cyc = t % 7f
        if (cyc < 0.12f || cyc in 0.2f..0.28f) drawRect(Color.White.copy(alpha = 0.35f), size = Size(w, h))
    }
    val n = if (storm) 90 else 60
    val slant = if (storm) 0.25f else 0.1f
    S_RAIN.take(n).forEach { d ->
        val y = h * wrap(d.y + t * (0.6f + d.s * 0.6f))
        val x = w * wrap(d.x + t * 0.02f)
        val len = h * (0.02f + d.s * 0.03f)
        drawLine(Color(0xFFBFD4FF).copy(alpha = 0.18f + d.s * 0.25f), Offset(x, y), Offset(x - len * slant, y + len), 1.2f + d.s)
    }
    // droplets on the glass
    S_BUBBLES.take(18).forEach { b ->
        val y = h * wrap(b.y + t * 0.01f * b.s)
        drawCircle(Color.White.copy(alpha = 0.12f), 3f + b.s * 6f, Offset(b.x * w, y))
    }
}

private fun DrawScope.mint(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFFF1FFF9), Color(0xFFD5F5E8), w, h)
    glow(Color(0xFF7CE8C1), Offset(w * (0.2f + 0.1f * sin(t * 0.1f)), h * 0.2f), w * 0.8f, 0.55f)
    glow(Color(0xFFB8F0FF), Offset(w * 0.9f, h * (0.5f + 0.05f * cos(t * 0.08f))), w * 0.7f, 0.55f)
    glow(Color(0xFFD8FFC4), Offset(w * 0.4f, h * 0.9f), w * 0.8f, 0.55f)
    for (i in 0..2) {
        val base = h * (0.7f + i * 0.08f)
        drawPath(Path().apply {
            moveTo(0f, base); var x = 0f
            while (x <= w) { lineTo(x, base + sin(x / w * 6.28f + t * 0.4f + i) * h * 0.015f); x += w / 30f }
            lineTo(w, h); lineTo(0f, h); close()
        }, Color(0xFF7CE8C1).copy(alpha = 0.12f))
    }
}

private fun DrawScope.gold(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF1C1407), Color(0xFF070502), w, h)
    glow(Color(0xFFF5C451), Offset(w * 0.8f, h * 0.15f), w * 0.8f, 0.25f)
    for (i in 0 until 14) {
        val off = wrap(i / 14f + t * 0.02f) * (w + h) - h
        drawLine(Color(0xFFF5C451).copy(alpha = 0.05f + 0.04f * sin(t + i)), Offset(off, 0f), Offset(off + h, h), 18f)
    }
    S_BOKEH.forEach { b ->
        val a = 0.10f + 0.12f * (0.5f + 0.5f * sin(t * (0.5f + b.s) + b.p))
        val c = Offset(b.x * w, h * wrap(b.y - t * 0.005f * (1 + b.s)))
        drawCircle(Color(0xFFFFD77A).copy(alpha = a), 10f + b.s * 30f, c)
    }
}

private fun DrawScope.arctic(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF9FD8F5), Color(0xFF2C6F9A), w, h)
    glow(Color.White, Offset(w * 0.2f, h * 0.1f), w * 0.6f, 0.6f)
    // icebergs
    val bergs = listOf(Triple(0.15f, 0.62f, 0.25f), Triple(0.62f, 0.58f, 0.32f), Triple(0.9f, 0.66f, 0.18f))
    bergs.forEach { (x, y, s) ->
        val bx = w * x; val by = h * y + sin(t * 0.6f + x * 9f) * 3f
        drawPath(Path().apply { moveTo(bx - w * s * 0.5f, by); lineTo(bx - w * s * 0.2f, by - h * s * 0.45f); lineTo(bx + w * s * 0.05f, by - h * s * 0.3f); lineTo(bx + w * s * 0.25f, by - h * s * 0.5f); lineTo(bx + w * s * 0.5f, by); close() },
            Brush.verticalGradient(listOf(Color.White, Color(0xFFBFE6FA)), by - h * s * 0.5f, by))
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3E8DBA), Color(0xFF123B5A)), h * 0.64f, h), Offset(0f, h * 0.64f), Size(w, h * 0.36f))
    S_FALL.forEach { s ->
        val y = h * wrap(s.y + t * (0.02f + s.s * 0.03f))
        val x = w * wrap(s.x + sin(t * 0.5f + s.p) * 0.02f)
        drawCircle(Color.White.copy(alpha = 0.5f + s.s * 0.4f), 1.5f + s.s * 3f, Offset(x, y))
    }
}

private fun DrawScope.cyber(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF14002E), Color(0xFF3A0A5E), w, h)
    stars(t, w, h, 0.45f, 50)
    val sunC = Offset(w * 0.5f, h * 0.42f)
    val sr = w * 0.26f
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE259), Color(0xFFFF2E97)), sunC.y - sr, sunC.y + sr), sr, sunC)
    for (i in 0 until 6) {
        val y = sunC.y + sr * (0.1f + i * 0.15f)
        drawRect(Color(0xFF3A0A5E), Offset(sunC.x - sr, y), Size(sr * 2, 3f + i * 1.6f))
    }
    val horizon = h * 0.55f
    drawRect(Color(0xFF12002A), Offset(0f, horizon), Size(w, h - horizon))
    val lineCol = Color(0xFF00F0FF)
    for (i in -10..10) {
        drawLine(lineCol.copy(alpha = 0.45f), Offset(w / 2 + i * w * 0.02f, horizon), Offset(w / 2 + i * w * 0.2f, h), 2f)
    }
    val scroll = wrap(t * 0.25f)
    for (k in 0 until 10) {
        val f = (k + scroll) / 10f
        val y = horizon + (h - horizon) * f * f
        drawLine(lineCol.copy(alpha = 0.2f + 0.5f * f), Offset(0f, y), Offset(w, y), 2f)
    }
    glow(Color(0xFFFF2E97), Offset(w / 2, horizon), w * 0.8f, 0.3f)
}

private fun DrawScope.lotus(t: Float, w: Float, h: Float) {
    vgrad(Color(0xFF123E44), Color(0xFF051A1F), w, h)
    glow(Color(0xFF7FFFD4), Offset(w * 0.3f, h * 0.2f), w * 0.7f, 0.2f)
    // ripples
    listOf(Offset(0.3f, 0.35f), Offset(0.72f, 0.62f), Offset(0.45f, 0.82f)).forEachIndexed { i, o ->
        for (k in 0..2) {
            val f = wrap(t * 0.15f + k / 3f + i * 0.2f)
            drawOval(Color.White.copy(alpha = (1f - f) * 0.18f), Offset(o.x * w - f * w * 0.25f, o.y * h - f * w * 0.1f), Size(f * w * 0.5f, f * w * 0.2f), style = Stroke(2f))
        }
    }
    // lily pads + lotus
    listOf(Triple(0.2f, 0.55f, 0.11f), Triple(0.78f, 0.35f, 0.09f), Triple(0.62f, 0.8f, 0.13f)).forEachIndexed { i, (x, y, s) ->
        val c = Offset(w * x + sin(t * 0.3f + i) * 4f, h * y)
        drawArc(Color(0xFF2E8B57), 20f, 320f, true, Offset(c.x - w * s, c.y - w * s * 0.45f), Size(w * s * 2, w * s * 0.9f))
        if (i != 1) {
            for (p in 0 until 6) {
                val a = p * 60f - 90f + sin(t * 0.5f) * 3f
                rotate(a, c) { drawOval(Color(0xFFFF9FC0).copy(alpha = 0.9f), Offset(c.x - w * 0.018f, c.y - w * 0.07f), Size(w * 0.036f, w * 0.07f)) }
            }
            drawCircle(Color(0xFFFFE27A), w * 0.012f, c)
        }
    }
}

