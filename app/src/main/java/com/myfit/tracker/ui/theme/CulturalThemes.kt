package com.myfit.tracker.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * R20 Cultural themes: fifteen countries drawn in the Carbon & Lime / Web Crimson language — a deep two-tone
 * gradient, one fine full-screen pattern that lights up toward the top-right glow, the two soft light ribbons and
 * the accent glow bottom-left — with the country's own craft pattern instead of the grid and one quiet hero motif.
 * Every pattern is original geometry (no flags, emblems, logos or text). Backgrounds are baked once, so the extra
 * detail costs nothing per frame.
 */
object CulturalThemes {
    data class Info(val country: String, val region: String)

    private val W = Color(0xFFFFFFFF); private val WD = Color(0xBFFFFFFF); private val WF = Color(0x73FFFFFF)
    private val meta = HashMap<String, Info>()

    private fun c(
        id: String, name: String, country: String, region: String, top: Long, bottom: Long, glow: Long, deep: Long, side: Long,
        accent: Long, bright: Long, on: Long, tint: Long, fallback: Long, water: Long? = null, protein: Long? = null,
    ): FitTheme {
        meta[id] = Info(country, region)
        return FitTheme(
            id = id, name = name, isLight = false, art = BackdropArt.CULTURE, bgTop = Color(top), bgBottom = Color(bottom),
            blobs = listOf(Color(glow), Color(deep), Color(top), Color(side)), accent = Color(accent), accentBright = Color(bright),
            onAccent = Color(on), text = W, textDim = WD, textFaint = WF, glassTint = Color(tint), glassFallback = Color(fallback),
            rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        ).let { t -> t.copy(water = water?.let { Color(it) } ?: t.water, protein = protein?.let { Color(it) } ?: t.protein) }
    }

    const val SOUTH_ASIA = "South Asia"
    const val MENA = "Middle East & North Africa"
    const val EAST_ASIA = "East Asia"
    const val EU_AM = "Europe & Americas"

    val all: List<FitTheme> by lazy {
        listOf(
            c("cult_pk", "Lahore Jaali", "Pakistan", SOUTH_ASIA, 0xFF06201A, 0xFF020A08, 0xFF0E8A5A, 0xFF0B3A2C, 0xFF3FE09A,
                0xFF2BD47D, 0xFF8AF0BC, 0xFF00261A, 0x149FFFD0, 0xF0071A15),
            c("cult_in", "Jaipur Mandala", "India", SOUTH_ASIA, 0xFF1E0A1E, 0xFF08040C, 0xFFFF7A1A, 0xFF5A1A4A, 0xFFE0218A,
                0xFFFF9933, 0xFFFFC27A, 0xFF2A1200, 0x1AFFC890, 0xF0160A16, protein = 0xFFFF5C8A),
            c("cult_bd", "Dhaka Jamdani", "Bangladesh", SOUTH_ASIA, 0xFF05241A, 0xFF020B08, 0xFF0A6B4A, 0xFF113A2E, 0xFFF42A41,
                0xFFF4364C, 0xFFFF7A88, 0xFFFFFFFF, 0x1AFFA0AA, 0xF0061A14),
            c("cult_sa", "Najdi Sadu", "Saudi Arabia", MENA, 0xFF0A1F14, 0xFF040A07, 0xFFC9A227, 0xFF1E4A30, 0xFF2F8F5B,
                0xFFD9B44A, 0xFFF0D58A, 0xFF221800, 0x14FFE9A8, 0xF00A1A12),
            c("cult_ae", "Desert Pearl", "UAE", MENA, 0xFF17110E, 0xFF060504, 0xFFF0D2B8, 0xFF4A3426, 0xFFC98B5E,
                0xFFEFC3A0, 0xFFFFE2CC, 0xFF2A160A, 0x1AFFE6D2, 0xF0150F0C),
            c("cult_tr", "Iznik Tile", "Türkiye", MENA, 0xFF061433, 0xFF020714, 0xFF1F6FE0, 0xFF0E2A66, 0xFF2EC4B6,
                0xFF2EC4B6, 0xFF8AEBE1, 0xFF00221F, 0x1A9FE8FF, 0xF0081530, protein = 0xFFFF5A5F),
            c("cult_eg", "Nile Lapis", "Egypt", MENA, 0xFF0A1640, 0xFF03061A, 0xFFF2C14E, 0xFF1A2F80, 0xFF3355CC,
                0xFFF2C14E, 0xFFFFDD8A, 0xFF221700, 0x1AFFE6A8, 0xF00A1336, water = 0xFF5A86FF),
            c("cult_ma", "Fez Zellige", "Morocco", MENA, 0xFF04201F, 0xFF020B0B, 0xFF0F7A72, 0xFF0B3533, 0xFFE76F51,
                0xFFF08A5D, 0xFFFFB892, 0xFF2A0E00, 0x1AFFC9AE, 0xF0061A19, water = 0xFF2EC4B6),
            c("cult_jp", "Kyoto Seigaiha", "Japan", EAST_ASIA, 0xFF0B0F1A, 0xFF030408, 0xFFE8432F, 0xFF1A2440, 0xFF3A4A7A,
                0xFFFF4B3E, 0xFFFF8A80, 0xFFFFFFFF, 0x1AFFB0A8, 0xF00C1018),
            c("cult_kr", "Celadon Moon", "South Korea", EAST_ASIA, 0xFF081A22, 0xFF02080B, 0xFF4FB8A0, 0xFF103A44, 0xFFB9E8D8,
                0xFF7FD8BE, 0xFFB8F0E0, 0xFF002A20, 0x149FFFE8, 0xF0081820, protein = 0xFFFF6B6B),
            c("cult_cn", "Lacquer & Gold", "China", EAST_ASIA, 0xFF2A0606, 0xFF0C0202, 0xFFFFB020, 0xFF6A0E0E, 0xFFD62828,
                0xFFFFC53D, 0xFFFFDD85, 0xFF2A1A00, 0x1AFFD8A0, 0xF0220808),
            c("cult_gb", "Highland Tartan", "United Kingdom", EU_AM, 0xFF0A1A14, 0xFF04080A, 0xFF3D5A8A, 0xFF13332A, 0xFFA77BFF,
                0xFFB48CFF, 0xFFD7C2FF, 0xFF1C0A3A, 0x1AD7C2FF, 0xF00A1714, protein = 0xFFE0314B),
            c("cult_it", "Amalfi Majolica", "Italy", EU_AM, 0xFF061B3A, 0xFF020818, 0xFF2A7FD4, 0xFF0D2E5E, 0xFFFFE15D,
                0xFFFFE15D, 0xFFFFF0A0, 0xFF231C00, 0x1AFFF3B0, 0xF0081834, water = 0xFF4EA8FF),
            c("cult_mx", "Papel Picado", "Mexico", EU_AM, 0xFF1A0820, 0xFF07030C, 0xFFE4007C, 0xFF3A1050, 0xFF00B3A4,
                0xFFFF3D9A, 0xFFFF8CC4, 0xFFFFFFFF, 0x1AFFB0D8, 0xF0160A1C, water = 0xFF00C2B2),
            c("cult_br", "Rio Waves", "Brazil", EU_AM, 0xFF04201A, 0xFF010A08, 0xFF00A86B, 0xFF0A3A2C, 0xFFFFC300,
                0xFFFFC300, 0xFFFFDD66, 0xFF231A00, 0x14FFF0A8, 0xF0061A15, water = 0xFF2F80FF),
        )
    }

    val regions = listOf(SOUTH_ASIA, MENA, EAST_ASIA, EU_AM)

    fun info(id: String): Info? { if (meta.isEmpty()) all.size; return meta[id] }
}

// ======================================================================================= drawing

/** Pattern brightness: 0 far away → 1 right under the top-right glow (same falloff as the dot-matrix gym theme). */
private fun lit(x: Float, y: Float, w: Float, h: Float): Float =
    1f - (Offset(x - w * 0.85f, y - h * 0.1f).getDistance() / max(w, h)).coerceIn(0f, 1f)

/** Ink for a pattern element at (x, y): white that warms into the accent near the glow. */
private fun ink(th: FitTheme, x: Float, y: Float, w: Float, h: Float, base: Float = 0.035f, extra: Float = 0.075f): Color {
    val l = lit(x, y, w, h)
    return lerp(Color.White, th.accentBright, (l * l * 0.7f)).copy(alpha = base + extra * l * l)
}

private fun polar(c: Offset, r: Float, a: Float) = Offset(c.x + r * cos(a), c.y + r * sin(a))

/** A petal from radius [r0] to [r1] along angle [a], [dw] radians wide at its base. */
private fun petal(c: Offset, a: Float, r0: Float, r1: Float, dw: Float): Path = Path().apply {
    val b1 = polar(c, r0, a - dw); val tip = polar(c, r1, a); val b2 = polar(c, r0, a + dw)
    val m = (r0 + r1) / 2f
    val c1 = polar(c, m, a - dw * 1.25f); val c2 = polar(c, m, a + dw * 1.25f)
    moveTo(b1.x, b1.y); quadraticTo(c1.x, c1.y, tip.x, tip.y); quadraticTo(c2.x, c2.y, b2.x, b2.y)
}

/** Regular star: [n] points, outer radius [r], inner radius [ri], rotated by [rot]. */
private fun star(c: Offset, n: Int, r: Float, ri: Float, rot: Float = 0f): Path = Path().apply {
    for (k in 0 until n * 2) {
        val p = polar(c, if (k % 2 == 0) r else ri, rot + (PI.toFloat() / n) * k)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun square(c: Offset, r: Float, rot: Float): Path = Path().apply {
    for (k in 0..3) { val p = polar(c, r, rot + PI.toFloat() / 2 * k); if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

internal fun DrawScope.culture(th: FitTheme, t: Float, w: Float, h: Float) {
    val bg = Brush.verticalGradient(listOf(th.bgTop, th.bgBottom), 0f, h)
    drawRect(bg, size = Size(w, h))
    blob(th.blobs[0], Offset(w * 0.9f, h * 0.1f), w * 0.75f, 0.42f)          // the glow the pattern lights up toward
    when (th.id) {
        "cult_pk" -> jaali(th, w, h)
        "cult_in" -> mandala(th, w, h)
        "cult_bd" -> jamdani(th, w, h)
        "cult_sa" -> sadu(th, w, h)
        "cult_ae" -> pearl(th, w, h)
        "cult_tr" -> iznik(th, w, h)
        "cult_eg" -> nile(th, w, h)
        "cult_ma" -> zellige(th, w, h)
        "cult_jp" -> seigaiha(th, bg, w, h)
        "cult_kr" -> celadon(th, w, h)
        "cult_cn" -> lacquer(th, w, h)
        "cult_gb" -> tartan(th, w, h)
        "cult_it" -> majolica(th, w, h)
        "cult_mx" -> picado(th, w, h)
        "cult_br" -> rio(th, w, h)
    }
    ribbons(th, t, w, h)
    blob(th.accent, Offset(w * (0.1f + 0.08f * sin(t * 0.1f)), h * 0.92f), w * 0.7f, 0.13f)
    blob(th.blobs[3], Offset(w * 0.12f, h * 0.48f), w * 0.5f, 0.09f)
    // keep the middle calm so cards and text always read cleanly
    drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), Offset(w / 2, h * 0.5f), max(w, h) * 0.55f), size = Size(w, h))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), Offset(w / 2, h * 0.45f), max(w, h) * 0.9f), size = Size(w, h))
}

// ---------------------------------------------------------------- South Asia

/** Pakistan — Mughal jaali: 8-point stars in a carved-marble lattice, linked by small diamonds. */
private fun DrawScope.jaali(th: FitTheme, w: Float, h: Float) {
    val s = w / 6f
    val sw = max(1.2f, w * 0.0035f)
    var j = 0
    while (j * s < h + s) {
        for (i in 0..6) {
            val c = Offset(i * s, j * s)
            val col = ink(th, c.x, c.y, w, h)
            drawPath(star(c, 8, s * 0.42f, s * 0.29f, PI.toFloat() / 8), col, style = Stroke(sw))
            drawPath(square(c, s * 0.2f, 0f), col.copy(alpha = col.alpha * 0.8f), style = Stroke(sw))
            val d = Offset(c.x + s / 2, c.y + s / 2)
            drawPath(square(d, s * 0.14f, 0f), ink(th, d.x, d.y, w, h), style = Stroke(sw))
            // lattice bars between the stars
            drawLine(col.copy(alpha = col.alpha * 0.6f), Offset(c.x + s * 0.42f, c.y), Offset(c.x + s * 0.58f, c.y), sw)
            drawLine(col.copy(alpha = col.alpha * 0.6f), Offset(c.x, c.y + s * 0.42f), Offset(c.x, c.y + s * 0.58f), sw)
        }
        j++
    }
    // hero: one large lit star medallion bottom-right
    val m = Offset(w * 0.92f, h * 0.8f)
    drawPath(star(m, 8, w * 0.42f, w * 0.3f, PI.toFloat() / 8), th.accent.copy(alpha = 0.10f), style = Stroke(w * 0.008f))
    drawPath(star(m, 8, w * 0.3f, w * 0.21f, 0f), Color.White.copy(alpha = 0.05f), style = Stroke(w * 0.005f))
}

/** India — block-print bandhani dots and a large lotus mandala rising from the corner. */
private fun DrawScope.mandala(th: FitTheme, w: Float, h: Float) {
    val g = w / 14f
    var y = g / 2; var row = 0
    while (y < h) {
        var x = if (row % 2 == 0) g / 2 else g
        while (x < w) {
            val col = ink(th, x, y, w, h, 0.05f, 0.09f)
            val r = max(1f, w * 0.0028f)
            drawCircle(col, r, Offset(x, y))
            for (k in 0..3) drawCircle(col.copy(alpha = col.alpha * 0.6f), r * 0.7f, polar(Offset(x, y), g * 0.18f, PI.toFloat() / 2 * k + PI.toFloat() / 4))
            x += g
        }
        y += g * 0.87f; row++
    }
    fun mandalaAt(c: Offset, R: Float, a: Float) {
        val sw = max(1.2f, R * 0.008f)
        for (ring in 0..5) {
            val r0 = R * (0.12f + ring * 0.15f); val r1 = r0 + R * 0.16f
            val n = 8 + ring * 4
            val col = (if (ring % 2 == 0) th.accentBright else Color.White).copy(alpha = a * (1f - ring * 0.1f))
            for (k in 0 until n) drawPath(petal(c, 2 * PI.toFloat() / n * (k + ring * 0.5f), r0, r1, PI.toFloat() / n * 0.8f), col, style = Stroke(sw))
            drawCircle(col.copy(alpha = col.alpha * 0.6f), r0, c, style = Stroke(sw * 0.7f))
            if (ring == 3) for (k in 0 until n * 2) drawCircle(col, sw * 1.4f, polar(c, r1 + R * 0.03f, 2 * PI.toFloat() / (n * 2) * k))
        }
        drawCircle(th.accent.copy(alpha = a * 1.4f), R * 0.06f, c)
    }
    mandalaAt(Offset(w * 0.95f, h * 0.82f), w * 0.7f, 0.11f)
    mandalaAt(Offset(w * 0.02f, h * 0.1f), w * 0.32f, 0.06f)
}

/** Bangladesh — jamdani diamond lattice with woven buti flowers, and a low red sun. */
private fun DrawScope.jamdani(th: FitTheme, w: Float, h: Float) {
    val d = w / 6f
    val sw = max(1.2f, w * 0.003f)
    var k = -h
    while (k < w + h) {
        drawLine(Color.White.copy(alpha = 0.035f), Offset(k, 0f), Offset(k + h, h), sw)
        drawLine(Color.White.copy(alpha = 0.035f), Offset(k + h, 0f), Offset(k, h), sw)
        k += d
    }
    // buti flowers at every crossing
    var m = -20
    while (m < 20) {
        var n = 0
        while (n < 40) {
            val x = (m + n) * d / 2f; val y = (n - m) * d / 2f
            if (x in -d..w + d && y in -d..h + d) {
                val col = ink(th, x, y, w, h, 0.05f, 0.1f)
                for (q in 0..3) drawPath(petal(Offset(x, y), PI.toFloat() / 2 * q, d * 0.04f, d * 0.2f, 0.45f), col, style = Fill)
                drawCircle(col, d * 0.035f, Offset(x, y))
            }
            n++
        }
        m++
    }
    // hero: the red sun, set slightly toward the hoist like the flag's disc
    val sun = Offset(w * 0.36f, h * 0.27f)
    blob(th.accent, sun, w * 0.55f, 0.18f)
    drawCircle(th.accent.copy(alpha = 0.22f), w * 0.2f, sun)
    drawCircle(Color.White.copy(alpha = 0.06f), w * 0.2f, sun, style = Stroke(w * 0.004f))
}

// ---------------------------------------------------------------- Middle East & North Africa

/** Saudi Arabia — Najdi sadu weaving: bands of triangles and diamonds, one band woven in gold. */
private fun DrawScope.sadu(th: FitTheme, w: Float, h: Float) {
    val bh = w / 4.5f
    val p = w / 9f
    val sw = max(1.2f, w * 0.0032f)
    var b = 0
    var y0 = bh * 0.3f
    while (y0 < h) {
        val gold = b == 6
        val mid = y0 + bh / 2
        val col = if (gold) th.accent.copy(alpha = 0.2f) else ink(th, w * 0.7f, mid, w, h, 0.04f, 0.08f)
        if (b % 2 == 0 || gold) drawRect((if (gold) th.accent else Color.White).copy(alpha = if (gold) 0.05f else 0.018f), Offset(0f, y0), Size(w, bh))
        // saw-tooth edges
        val top = Path(); val bot = Path()
        var x = 0f
        top.moveTo(0f, y0 + bh * 0.18f); bot.moveTo(0f, y0 + bh * 0.82f)
        while (x < w + p) {
            top.lineTo(x + p / 2, y0); top.lineTo(x + p, y0 + bh * 0.18f)
            bot.lineTo(x + p / 2, y0 + bh); bot.lineTo(x + p, y0 + bh * 0.82f)
            x += p
        }
        drawPath(top, col, style = Stroke(sw)); drawPath(bot, col, style = Stroke(sw))
        // diamond chain
        x = p / 2
        while (x < w + p) {
            val dm = Path().apply { moveTo(x, mid - bh * 0.2f); lineTo(x + p * 0.38f, mid); lineTo(x, mid + bh * 0.2f); lineTo(x - p * 0.38f, mid); close() }
            if (gold) drawPath(dm, th.accent.copy(alpha = 0.1f))
            drawPath(dm, col, style = Stroke(sw))
            drawCircle(col, sw * 1.2f, Offset(x, mid))
            x += p
        }
        y0 += bh * 1.35f; b++
    }
}

/** UAE — mashrabiya of interlocking circles over rose-gold dunes, with one pearl moon. */
private fun DrawScope.pearl(th: FitTheme, w: Float, h: Float) {
    val s = w / 5f
    val sw = max(1.1f, w * 0.0028f)
    var j = 0
    while (j * s / 2 < h + s) {
        val y = j * s / 2
        var x = if (j % 2 == 0) 0f else s / 2
        while (x < w + s) { drawCircle(ink(th, x, y, w, h, 0.03f, 0.07f), s / 2, Offset(x, y), style = Stroke(sw)); x += s }
        j++
    }
    // dunes
    for (k in 0..2) {
        val top = h * (0.74f + k * 0.08f)
        val p = Path().apply {
            moveTo(0f, top + h * 0.04f)
            cubicTo(w * 0.3f, top - h * 0.06f, w * 0.55f, top + h * 0.05f, w, top - h * 0.02f * (k + 1))
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(th.blobs[3].copy(alpha = 0.16f + k * 0.05f), th.bgBottom.copy(alpha = 0.9f)), top - h * 0.06f, h))
        drawPath(Path().apply { moveTo(0f, top + h * 0.04f); cubicTo(w * 0.3f, top - h * 0.06f, w * 0.55f, top + h * 0.05f, w, top - h * 0.02f * (k + 1)) },
            th.accentBright.copy(alpha = 0.14f - k * 0.03f), style = Stroke(w * 0.004f))
    }
    val moon = Offset(w * 0.8f, h * 0.17f)
    blob(th.accentBright, moon, w * 0.35f, 0.22f)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), th.accentBright.copy(alpha = 0.25f)), Offset(moon.x - w * 0.02f, moon.y - w * 0.02f), w * 0.09f), w * 0.075f, moon)
}

/** Türkiye — Iznik rosette tiles with a single tall tulip. */
private fun DrawScope.iznik(th: FitTheme, w: Float, h: Float) {
    val s = w / 4.5f
    val sw = max(1.2f, w * 0.003f)
    var j = 0
    while (j * s < h + s) {
        var i = 0
        while (i * s < w + s) {
            val c = Offset(i * s, j * s)
            val col = ink(th, c.x, c.y, w, h, 0.04f, 0.09f)
            for (k in 0..7) drawPath(petal(c, PI.toFloat() / 4 * k, s * 0.1f, s * 0.34f, 0.28f), col, style = Stroke(sw))
            drawCircle(col, s * 0.07f, c, style = Stroke(sw))
            val d = Offset(c.x + s / 2, c.y + s / 2)
            drawPath(star(d, 4, s * 0.16f, s * 0.05f, 0f), ink(th, d.x, d.y, w, h, 0.035f, 0.07f), style = Stroke(sw))
            i++
        }
        j++
    }
    // hero tulip, bottom-right
    val o = Offset(w * 0.8f, h * 0.7f); val u = h * 0.2f
    fun P(x: Float, y: Float) = Offset(o.x + x * u, o.y + y * u)
    fun path(vararg pts: Offset) = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        var q = 1
        while (q + 2 < pts.size) { cubicTo(pts[q].x, pts[q].y, pts[q + 1].x, pts[q + 1].y, pts[q + 2].x, pts[q + 2].y); q += 3 }
    }
    val red = th.protein
    val center = path(P(0f, 0f), P(-0.28f, -0.2f), P(-0.18f, -0.75f), P(0f, -1f), P(0.18f, -0.75f), P(0.28f, -0.2f), P(0f, 0f))
    val left = path(P(0f, 0f), P(-0.45f, -0.05f), P(-0.55f, -0.5f), P(-0.42f, -0.85f), P(-0.3f, -0.55f), P(-0.15f, -0.3f), P(0f, 0f))
    val right = path(P(0f, 0f), P(0.45f, -0.05f), P(0.55f, -0.5f), P(0.42f, -0.85f), P(0.3f, -0.55f), P(0.15f, -0.3f), P(0f, 0f))
    val leaf = path(P(-0.02f, 0.7f), P(-0.4f, 0.6f), P(-0.6f, 0.3f), P(-0.55f, 0.1f), P(-0.35f, 0.35f), P(-0.2f, 0.55f), P(-0.02f, 0.7f))
    for (pp in listOf(left, right)) { drawPath(pp, th.accent.copy(alpha = 0.08f)); drawPath(pp, th.accent.copy(alpha = 0.2f), style = Stroke(w * 0.005f)) }
    drawPath(center, red.copy(alpha = 0.12f)); drawPath(center, red.copy(alpha = 0.3f), style = Stroke(w * 0.005f))
    drawPath(leaf, th.accent.copy(alpha = 0.07f)); drawPath(leaf, th.accent.copy(alpha = 0.16f), style = Stroke(w * 0.004f))
    drawPath(Path().apply { moveTo(o.x, o.y); quadraticTo(o.x + 0.06f * u, o.y + 0.6f * u, o.x - 0.05f * u, o.y + 1.3f * u) }, th.accent.copy(alpha = 0.18f), style = Stroke(w * 0.005f))
}

/** Egypt — Nile water-ripple rows, a rayed sun disc and three stepped pyramids on the horizon. */
private fun DrawScope.nile(th: FitTheme, w: Float, h: Float) {
    val row = w / 11f; val p = w / 16f
    val sw = max(1.1f, w * 0.0028f)
    var y = row / 2
    while (y < h * 0.86f) {
        val path = Path(); var x = 0f
        path.moveTo(0f, y)
        while (x < w + p) { path.lineTo(x + p / 2, y - row * 0.12f); path.lineTo(x + p, y); x += p }
        drawPath(path, ink(th, w * 0.75f, y, w, h, 0.03f, 0.08f), style = Stroke(sw))
        y += row
    }
    // sun disc with rays
    val sun = Offset(w * 0.78f, h * 0.15f); val r = w * 0.11f
    blob(th.accent, sun, w * 0.5f, 0.22f)
    for (k in 0 until 20) {
        val a = 2 * PI.toFloat() / 20 * k
        val a0 = polar(sun, r * 1.3f, a); val a1 = polar(sun, r * 3.4f, a)
        drawLine(Brush.linearGradient(listOf(th.accentBright.copy(alpha = 0.22f), Color.Transparent), a0, a1), a0, a1, w * 0.006f)
    }
    drawCircle(th.accent.copy(alpha = 0.32f), r, sun)
    drawCircle(Color.White.copy(alpha = 0.18f), r, sun, style = Stroke(w * 0.004f))
    // pyramids
    val base = h * 0.92f
    for ((cx, hw) in listOf(w * 0.28f to w * 0.3f, w * 0.68f to w * 0.22f, w * 0.95f to w * 0.14f)) {
        val apex = Offset(cx, base - hw * 1.05f)
        val tri = Path().apply { moveTo(cx - hw, base); lineTo(apex.x, apex.y); lineTo(cx + hw, base); close() }
        drawPath(tri, Brush.horizontalGradient(listOf(th.accent.copy(alpha = 0.12f), th.bgBottom.copy(alpha = 0.6f)), cx - hw, cx + hw))
        drawPath(tri, th.accentBright.copy(alpha = 0.18f), style = Stroke(w * 0.004f))
        for (q in 1..6) { val fy = q / 7f; val yy = apex.y + (base - apex.y) * fy; val half = hw * fy; drawLine(th.accentBright.copy(alpha = 0.06f), Offset(cx - half, yy), Offset(cx + half, yy), sw) }
    }
}

/** Morocco — zellige 8-point stars in alternating glaze, framed by a horseshoe arch. */
private fun DrawScope.zellige(th: FitTheme, w: Float, h: Float) {
    val s = w / 4.5f
    val sw = max(1.2f, w * 0.003f)
    var j = 0
    while (j * s < h + s) {
        var i = 0
        while (i * s < w + s) {
            val c = Offset(i * s, j * s)
            val col = ink(th, c.x, c.y, w, h, 0.045f, 0.09f)
            val glaze = (if ((i + j) % 2 == 0) th.accent else th.water).copy(alpha = 0.035f + 0.05f * lit(c.x, c.y, w, h))
            val a = square(c, s * 0.36f, 0f); val b = square(c, s * 0.36f, PI.toFloat() / 4)
            drawPath(a, glaze); drawPath(b, glaze)
            drawPath(a, col, style = Stroke(sw)); drawPath(b, col, style = Stroke(sw))
            drawPath(star(c, 8, s * 0.16f, s * 0.08f, 0f), col, style = Stroke(sw))
            val d = Offset(c.x + s / 2, c.y + s / 2)
            drawPath(square(d, s * 0.1f, PI.toFloat() / 4), ink(th, d.x, d.y, w, h), style = Stroke(sw))
            i++
        }
        j++
    }
    // hero: horseshoe arch over the top of the screen
    fun arch(aw: Float, top: Float, spring: Float, col: Color, width: Float) {
        val cx = w / 2
        drawPath(Path().apply {
            moveTo(cx - aw * 0.86f, h * 1.02f); lineTo(cx - aw * 0.86f, spring)
            cubicTo(cx - aw * 1.18f, spring - (spring - top) * 0.55f, cx - aw * 0.45f, top + (spring - top) * 0.05f, cx, top)
            cubicTo(cx + aw * 0.45f, top + (spring - top) * 0.05f, cx + aw * 1.18f, spring - (spring - top) * 0.55f, cx + aw * 0.86f, spring)
            lineTo(cx + aw * 0.86f, h * 1.02f)
        }, col, style = Stroke(width))
    }
    arch(w * 0.5f, h * 0.05f, h * 0.36f, th.accent.copy(alpha = 0.16f), w * 0.01f)
    arch(w * 0.56f, h * 0.025f, h * 0.37f, Color.White.copy(alpha = 0.05f), w * 0.004f)
}

// ---------------------------------------------------------------- East Asia

/** Japan — seigaiha waves under a vermilion sun crossed by two bands of mist. */
private fun DrawScope.seigaiha(th: FitTheme, bg: Brush, w: Float, h: Float) {
    val r = w / 7f
    val sw = max(1.1f, w * 0.0028f)
    var j = 0
    val top = h * 0.36f   // waves fill the lower two-thirds; the sky stays clear for the sun
    while (top + j * r / 2 - r < h + r) {
        val y = top + j * r / 2
        var x = if (j % 2 == 0) 0f else r
        while (x < w + r) {
            val c = Offset(x, y)
            drawCircle(bg, r, c)
            val col = ink(th, x, y, w, h, 0.05f, 0.1f)
            for (q in 0..3) drawCircle(col.copy(alpha = col.alpha * (1f - q * 0.15f)), r * (1f - q * 0.24f), c, style = Stroke(sw))
            x += 2 * r
        }
        j++
    }
    // fade the wave field into the sky
    drawRect(Brush.verticalGradient(listOf(th.bgTop, th.bgTop.copy(alpha = 0f)), top - r, top + r * 2.5f), Offset(0f, 0f), Size(w, top + r * 2.5f))
    val sun = Offset(w * 0.74f, h * 0.17f); val sr = w * 0.19f
    blob(th.accent, sun, w * 0.6f, 0.2f)
    drawCircle(th.accent.copy(alpha = 0.55f), sr, sun)
    for ((yy, hh) in listOf(0.13f to 0.012f, 0.2f to 0.018f)) drawRect(th.bgTop.copy(alpha = 0.75f), Offset(w * 0.4f, h * yy), Size(w * 0.65f, h * hh))
}

/** South Korea — ah-ja (亞) window lattice and a pale celadon moon jar. */
private fun DrawScope.celadon(th: FitTheme, w: Float, h: Float) {
    val s = w / 5f
    val sw = max(1.2f, w * 0.003f)
    var gx = 0f
    while (gx <= w + 1) { drawLine(Color.White.copy(alpha = 0.03f), Offset(gx, 0f), Offset(gx, h), sw); gx += s }
    var gy = 0f
    while (gy <= h) { drawLine(Color.White.copy(alpha = 0.03f), Offset(0f, gy), Offset(w, gy), sw); gy += s }
    var j = 0
    while (j * s < h) {
        var i = 0
        while (i * s < w) {
            val c = Offset(i * s + s / 2, j * s + s / 2)
            val a = s * 0.34f; val n = s * 0.12f
            val col = ink(th, c.x, c.y, w, h, 0.045f, 0.09f)
            val p = Path().apply {
                moveTo(c.x - a + n, c.y - a); lineTo(c.x + a - n, c.y - a); lineTo(c.x + a - n, c.y - a + n); lineTo(c.x + a, c.y - a + n)
                lineTo(c.x + a, c.y + a - n); lineTo(c.x + a - n, c.y + a - n); lineTo(c.x + a - n, c.y + a); lineTo(c.x - a + n, c.y + a)
                lineTo(c.x - a + n, c.y + a - n); lineTo(c.x - a, c.y + a - n); lineTo(c.x - a, c.y - a + n); lineTo(c.x - a + n, c.y - a + n); close()
            }
            drawPath(p, col, style = Stroke(sw))
            drawLine(col.copy(alpha = col.alpha * 0.7f), Offset(c.x, c.y - a), Offset(c.x, c.y - s / 2), sw)
            drawLine(col.copy(alpha = col.alpha * 0.7f), Offset(c.x - a, c.y), Offset(c.x - s / 2, c.y), sw)
            i++
        }
        j++
    }
    // moon jar: wide, soft, slightly imperfect
    val jar = Offset(w * 0.76f, h * 0.76f)
    blob(th.accentBright, jar, w * 0.6f, 0.12f)
    drawOval(Brush.radialGradient(listOf(th.accentBright.copy(alpha = 0.2f), th.accent.copy(alpha = 0.06f)), Offset(jar.x - w * 0.08f, jar.y - w * 0.1f), w * 0.42f),
        Offset(jar.x - w * 0.36f, jar.y - w * 0.33f), Size(w * 0.72f, w * 0.66f))
    drawOval(Color.White.copy(alpha = 0.08f), Offset(jar.x - w * 0.36f, jar.y - w * 0.33f), Size(w * 0.72f, w * 0.66f), style = Stroke(w * 0.004f))
    drawOval(Color.White.copy(alpha = 0.06f), Offset(jar.x - w * 0.11f, jar.y - w * 0.37f), Size(w * 0.22f, w * 0.06f), style = Stroke(w * 0.004f))
}

/** China — key-fret borders, drifting auspicious clouds and a golden moon gate. */
private fun DrawScope.lacquer(th: FitTheme, w: Float, h: Float) {
    val sw = max(1.2f, w * 0.0032f)
    val step = w / 9f
    var x0 = 0f
    while (x0 <= w) { drawLine(Color.White.copy(alpha = 0.025f), Offset(x0, 0f), Offset(x0, h), sw * 0.8f); x0 += step }
    var y0 = 0f
    while (y0 <= h) { drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, y0), Offset(w, y0), sw * 0.8f); y0 += step }
    fun fret(y: Float, u: Float, col: Color) {
        var x = 0f
        drawLine(col, Offset(0f, y + u), Offset(w, y + u), sw); drawLine(col, Offset(0f, y - u * 0.25f), Offset(w, y - u * 0.25f), sw)
        while (x < w + u) {
            drawPath(Path().apply {
                moveTo(x, y + u); lineTo(x, y); lineTo(x + u * 0.78f, y); lineTo(x + u * 0.78f, y + u * 0.62f); lineTo(x + u * 0.36f, y + u * 0.62f); lineTo(x + u * 0.36f, y + u * 0.3f)
            }, col, style = Stroke(sw))
            x += u
        }
    }
    fret(h * 0.035f, w * 0.06f, th.accent.copy(alpha = 0.16f))
    fret(h * 0.93f, w * 0.06f, th.accent.copy(alpha = 0.16f))
    // auspicious clouds
    val rnd = kotlin.random.Random(11)
    repeat(7) {
        val c = Offset(w * rnd.nextFloat(), h * (0.12f + 0.75f * rnd.nextFloat()))
        val r = w * (0.035f + 0.03f * rnd.nextFloat())
        val col = ink(th, c.x, c.y, w, h, 0.07f, 0.12f)
        drawArc(col, 90f, 270f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw * 1.2f))
        drawArc(col, 180f, 270f, false, Offset(c.x - r * 0.45f, c.y - r * 0.45f), Size(r * 0.9f, r * 0.9f), style = Stroke(sw * 1.2f))
        drawArc(col, 180f, 180f, false, Offset(c.x + r * 0.6f, c.y - r * 0.2f), Size(r * 1.6f, r * 1.6f), style = Stroke(sw * 1.2f))
        drawLine(col, Offset(c.x - r * 1.2f, c.y + r), Offset(c.x + r * 2.6f, c.y + r), sw * 1.2f)
    }
    val gate = Offset(w * 0.5f, h * 0.44f)
    drawCircle(th.accent.copy(alpha = 0.12f), w * 0.43f, gate, style = Stroke(w * 0.012f))
    drawCircle(Color.White.copy(alpha = 0.04f), w * 0.46f, gate, style = Stroke(w * 0.004f))
}

// ---------------------------------------------------------------- Europe & Americas

/** United Kingdom — a woven highland tartan in forest, navy, heather and a fine red overcheck. */
private fun DrawScope.tartan(th: FitTheme, w: Float, h: Float) {
    val sett = w / 2.2f
    // (offset in sett, width in sett, colour, alpha)
    val stripes = listOf(
        Triple(0.00f, 0.30f, th.blobs[1]) to 0.35f,
        Triple(0.34f, 0.12f, th.blobs[0]) to 0.16f,
        Triple(0.50f, 0.02f, th.protein) to 0.18f,
        Triple(0.56f, 0.12f, th.blobs[0]) to 0.16f,
        Triple(0.76f, 0.04f, th.accent) to 0.16f,
        Triple(0.86f, 0.01f, Color.White) to 0.10f,
    )
    for (vertical in listOf(true, false)) {
        val span = if (vertical) w else h
        var o = -sett
        while (o < span + sett) {
            for ((st, a) in stripes) {
                val (off, wd, col) = st
                val p = o + off * sett; val sz = max(1f, wd * sett)
                if (vertical) drawRect(col.copy(alpha = a * 0.5f), Offset(p, 0f), Size(sz, h))
                else drawRect(col.copy(alpha = a * 0.5f), Offset(0f, p), Size(w, sz))
            }
            o += sett
        }
    }
    // twill: the fine diagonal weave
    val g = w / 50f
    var k = -h
    while (k < w) { drawLine(Color.Black.copy(alpha = 0.06f), Offset(k, h), Offset(k + h, 0f), max(1f, g * 0.35f)); k += g }
    blob(th.accent, Offset(w * 0.85f, h * 0.1f), w * 0.5f, 0.12f)
}

/** Italy — Amalfi majolica tiles in lemon and azure, above a Roman arcade. */
private fun DrawScope.majolica(th: FitTheme, w: Float, h: Float) {
    val s = w / 4.5f
    val sw = max(1.2f, w * 0.003f)
    var gx = 0f
    while (gx <= w + 1) { drawLine(Color.White.copy(alpha = 0.035f), Offset(gx, 0f), Offset(gx, h), sw); gx += s }
    var gy = 0f
    while (gy <= h) { drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, gy), Offset(w, gy), sw); gy += s }
    var j = 0
    while (j * s < h * 0.8f) {
        var i = 0
        while (i * s < w) {
            val c = Offset(i * s + s / 2, j * s + s / 2)
            val col = ink(th, c.x, c.y, w, h, 0.05f, 0.1f)
            for (q in 0..3) drawCircle(col, s * 0.17f, polar(c, s * 0.16f, PI.toFloat() / 2 * q), style = Stroke(sw))
            drawCircle(th.accent.copy(alpha = 0.05f + 0.12f * lit(c.x, c.y, w, h)), s * 0.07f, c)
            for (q in 0..3) drawPath(petal(polar(c, s * 0.5f * 1.414f, PI.toFloat() / 4 + PI.toFloat() / 2 * q), PI.toFloat() / 4 + PI.toFloat() / 2 * q + PI.toFloat(), 0f, s * 0.2f, 0.4f), col.copy(alpha = col.alpha * 0.8f), style = Stroke(sw))
            i++
        }
        j++
    }
    // Roman arcade along the bottom
    val springY = h * 0.84f; val n = 4; val bay = w / n
    for (q in 0 until n) {
        val cx = bay * q + bay / 2; val r = bay * 0.38f
        drawPath(Path().apply {
            moveTo(cx - r, h); lineTo(cx - r, springY)
            arcTo(androidx.compose.ui.geometry.Rect(cx - r, springY - r, cx + r, springY + r), 180f, 180f, false)
            lineTo(cx + r, h)
        }, th.accent.copy(alpha = 0.14f), style = Stroke(w * 0.006f))
        drawRect(Color.White.copy(alpha = 0.03f), Offset(bay * q - (bay / 2 - r), springY - r * 1.25f), Size(bay - r * 2, h))
    }
    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, springY - bay * 0.48f), Offset(w, springY - bay * 0.48f), w * 0.004f)
}

/** Mexico — Mitla step-fret rows under strings of papel picado. */
private fun DrawScope.picado(th: FitTheme, w: Float, h: Float) {
    val row = w / 5f; val st = w / 30f
    val sw = max(1.2f, w * 0.003f)
    var y = h * 0.3f
    while (y < h + row) {
        val p = Path(); var x = -st * 6
        p.moveTo(x, y)
        while (x < w + st * 6) {
            p.lineTo(x, y - st); p.lineTo(x + st, y - st); p.lineTo(x + st, y - 2 * st); p.lineTo(x + 2 * st, y - 2 * st); p.lineTo(x + 2 * st, y - 3 * st)
            p.lineTo(x + 4 * st, y - 3 * st); p.lineTo(x + 4 * st, y - 2 * st); p.lineTo(x + 5 * st, y - 2 * st); p.lineTo(x + 5 * st, y - st); p.lineTo(x + 6 * st, y - st); p.lineTo(x + 6 * st, y)
            x += 6 * st
        }
        drawPath(p, ink(th, w * 0.7f, y, w, h, 0.045f, 0.08f), style = Stroke(sw))
        y += row
    }
    val colors = listOf(th.accent, th.water, Color(0xFFFF8A1F), Color(0xFFFFD23F), Color(0xFF9B5DE5))
    fun string(y0: Float, sag: Float, fw: Float, alpha: Float, shift: Int) {
        drawPath(Path().apply { moveTo(-w * 0.05f, y0); quadraticTo(w * 0.5f, y0 + sag * 2, w * 1.05f, y0) }, Color.White.copy(alpha = alpha * 0.5f), style = Stroke(sw))
        var i = 0; var x = fw * 0.2f
        while (x < w) {
            val u = x / w; val yy = y0 + sag * 4 * u * (1 - u)
            val col = colors[(i + shift) % colors.size]
            val fh = fw * 1.15f
            val flag = Path().apply {
                moveTo(x, yy); lineTo(x + fw, yy); lineTo(x + fw, yy + fh)
                val teeth = 5; for (q in 1..teeth) { val tx = x + fw - fw * q / teeth; lineTo(tx + fw / teeth / 2, yy + fh * 0.9f); lineTo(tx, yy + fh) }
                close()
            }
            drawPath(flag, col.copy(alpha = alpha))
            val fc = Offset(x + fw / 2, yy + fh * 0.48f)
            drawPath(square(fc, fw * 0.18f, 0f), th.bgTop.copy(alpha = 0.85f))
            for (q in 0..3) drawCircle(th.bgTop.copy(alpha = 0.85f), fw * 0.05f, polar(fc, fw * 0.3f, PI.toFloat() / 2 * q + PI.toFloat() / 4))
            x += fw * 1.25f; i++
        }
    }
    string(h * 0.015f, h * 0.02f, w / 7.5f, 0.24f, 0)
    string(h * 0.13f, h * 0.025f, w / 9.5f, 0.14f, 2)
}

/** Brazil — Copacabana promenade waves with palm fronds and a warm sun. */
private fun DrawScope.rio(th: FitTheme, w: Float, h: Float) {
    val band = w / 5f; val lam = w * 0.55f; val amp = band * 0.42f
    val steps = 48
    var k = -1
    while (k * band < h + band) {
        val y0 = k * band
        val p = Path()
        for (q in 0..steps) { val x = w * q / steps; val yy = y0 + amp * sin(2 * PI.toFloat() * x / lam); if (q == 0) p.moveTo(x, yy) else p.lineTo(x, yy) }
        for (q in steps downTo 0) { val x = w * q / steps; val yy = y0 + band * 0.5f + amp * sin(2 * PI.toFloat() * x / lam); p.lineTo(x, yy) }
        p.close()
        drawPath(p, ink(th, w * 0.7f, y0, w, h, 0.03f, 0.06f))
        k++
    }
    val sun = Offset(w * 0.82f, h * 0.13f)
    blob(th.accent, sun, w * 0.55f, 0.22f)
    drawCircle(th.accent.copy(alpha = 0.3f), w * 0.09f, sun)
    // palm fronds from the bottom-left corner
    val base = Offset(-w * 0.04f, h * 1.01f)
    for (q in 0..6) {
        val a = -PI.toFloat() / 2 + (q - 3) * 0.24f + 0.35f
        val len = w * (0.55f + 0.08f * ((q * 7) % 3))
        val tip = polar(base, len, a)
        val ctrl = polar(base, len * 0.6f, a - 0.25f)
        drawPath(Path().apply { moveTo(base.x, base.y); quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y) }, th.blobs[0].copy(alpha = 0.22f), style = Stroke(w * 0.006f))
        for (f in 1..10) {
            val u = f / 11f
            val px = (1 - u) * (1 - u) * base.x + 2 * (1 - u) * u * ctrl.x + u * u * tip.x
            val py = (1 - u) * (1 - u) * base.y + 2 * (1 - u) * u * ctrl.y + u * u * tip.y
            val ll = w * 0.07f * (1f - u * 0.6f)
            for (sgn in listOf(-1f, 1f)) drawLine(th.blobs[0].copy(alpha = 0.16f), Offset(px, py), polar(Offset(px, py), ll, a + sgn * 0.9f + 0.2f), w * 0.004f)
        }
    }
}
