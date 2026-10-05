package com.myfit.tracker.ui.badges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.domain.BadgeHue
import com.myfit.tracker.domain.BadgeIcon
import com.myfit.tracker.domain.BadgeShape
import com.myfit.tracker.domain.Tier
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal fun badgeIcon(i: BadgeIcon): ImageVector = when (i) {
    BadgeIcon.WORKOUT -> Duo.FitnessCenter
    BadgeIcon.STEPS -> Duo.DirectionsWalk
    BadgeIcon.FOOTPRINTS -> Duo.Footprints
    BadgeIcon.WATER -> Duo.WaterDrop
    BadgeIcon.FOOD -> Duo.ForkKnife
    BadgeIcon.SUPPLEMENT -> Duo.Inventory2
    BadgeIcon.MIND -> Duo.SelfImprovement
    BadgeIcon.TROPHY -> Duo.EmojiEvents
    BadgeIcon.STAR -> Duo.Star
    BadgeIcon.FLAG -> Duo.Flag
    BadgeIcon.WEIGHT -> Duo.MonitorWeight
    BadgeIcon.CALENDAR -> Duo.CalendarMonth
    BadgeIcon.FLAME -> Duo.Flame
}

internal fun badgeHue(h: BadgeHue, th: FitTheme): Color = when (h) {
    BadgeHue.ACCENT -> th.accent
    BadgeHue.STEPS -> th.steps
    BadgeHue.WATER -> th.water
    BadgeHue.PROTEIN -> th.protein
    BadgeHue.CARBS -> th.carbs
    BadgeHue.FAT -> th.fat
    BadgeHue.SLEEP -> th.sleep
    BadgeHue.SUCCESS -> th.success
    BadgeHue.WARNING -> th.warning
}

/** Metallic rim colours (light → dark) per tier. */
internal fun tierColors(t: Tier?): Pair<Color, Color> = when (t) {
    Tier.BRONZE -> Color(0xFFF0B27A) to Color(0xFF8A4B1F)
    Tier.SILVER -> Color(0xFFF4F6FA) to Color(0xFF8790A0)
    Tier.GOLD -> Color(0xFFFFE89A) to Color(0xFFC08414)
    null -> Color(0xFF9AA0A8) to Color(0xFF5C626B)
}

/** Polygon with softly rounded corners (quadratic curves at each vertex). */
private fun roundedPolygon(pts: List<Offset>, radius: Float): Path {
    val p = Path()
    val n = pts.size
    for (i in 0 until n) {
        val prev = pts[(i - 1 + n) % n]
        val cur = pts[i]
        val next = pts[(i + 1) % n]
        val d1 = hypot(cur.x - prev.x, cur.y - prev.y)
        val d2 = hypot(next.x - cur.x, next.y - cur.y)
        val r1 = min(radius, d1 / 2f); val r2 = min(radius, d2 / 2f)
        val a = Offset(cur.x + (prev.x - cur.x) * r1 / d1, cur.y + (prev.y - cur.y) * r1 / d1)
        val b = Offset(cur.x + (next.x - cur.x) * r2 / d2, cur.y + (next.y - cur.y) * r2 / d2)
        if (i == 0) p.moveTo(a.x, a.y) else p.lineTo(a.x, a.y)
        p.quadraticBezierTo(cur.x, cur.y, b.x, b.y)
    }
    p.close()
    return p
}

private fun shapePoints(shape: BadgeShape, size: Size, scale: Float): List<Offset> {
    val cx = size.width / 2f; val cy = size.height / 2f
    return when (shape) {
        BadgeShape.HEX -> {
            val r = min(size.width, size.height) / 2f * 0.98f * scale
            (0 until 6).map { i ->
                val a = Math.toRadians(-90.0 + i * 60.0)
                Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
            }
        }
        BadgeShape.SHIELD -> {
            val w = size.width; val h = size.height
            listOf(
                Offset(0.12f, 0.06f), Offset(0.5f, 0.0f), Offset(0.88f, 0.06f),
                Offset(0.92f, 0.52f), Offset(0.5f, 0.98f), Offset(0.08f, 0.52f),
            ).map { o -> Offset(cx + (o.x * w - cx) * scale, cy + (o.y * h - cy) * scale) }
        }
    }
}

/**
 * A vector medallion: metallic tier rim (hexagon for streaks, shield for milestones), a coloured
 * enamel face with a gloss highlight, the badge's icon, and 1–3 tier pips. Locked = muted greys.
 * Static drawing only — no animation, cheap to draw in grids.
 */
@Composable
fun BadgeMedallion(
    shape: BadgeShape,
    icon: BadgeIcon,
    hue: BadgeHue,
    tier: Tier?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    locked: Boolean = tier == null,
) {
    val th = LocalFitTheme.current
    val base = if (locked) Color(0xFF6B7078) else badgeHue(hue, th)
    val (rimHi, rimLo) = tierColors(if (locked) null else tier)
    val faceHi = remember(base) { lerpWhite(base, 0.28f) }
    val faceLo = remember(base) { Color(base.red * 0.62f, base.green * 0.62f, base.blue * 0.62f, 1f) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val corner = this.size.minDimension * 0.09f
            val outer = roundedPolygon(shapePoints(shape, this.size, 1f), corner)
            val inner = roundedPolygon(shapePoints(shape, this.size, 0.80f), corner * 0.8f)
            // soft drop shadow
            drawPath(roundedPolygon(shapePoints(shape, this.size, 1f).map { it + Offset(0f, this.size.height * 0.03f) }, corner),
                Color.Black.copy(alpha = if (locked) 0.10f else 0.22f))
            // metal rim
            drawPath(outer, Brush.linearGradient(listOf(rimHi, rimLo, rimHi.copy(alpha = 1f)), Offset.Zero, Offset(this.size.width, this.size.height)))
            drawPath(outer, Color.White.copy(alpha = 0.35f), style = Stroke(this.size.minDimension * 0.012f))
            // enamel face
            drawPath(inner, Brush.radialGradient(listOf(faceHi, base, faceLo), Offset(this.size.width * 0.42f, this.size.height * 0.35f), this.size.minDimension * 0.55f))
            drawPath(inner, rimLo.copy(alpha = 0.6f), style = Stroke(this.size.minDimension * 0.018f))
            // gloss on the upper half of the face
            clipPath(inner) {
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = if (locked) 0.10f else 0.30f), Color.Transparent), 0f, this.size.height * 0.5f),
                    Offset(-this.size.width * 0.1f, -this.size.height * 0.35f), Size(this.size.width * 1.2f, this.size.height * 0.8f),
                )
            }
            // tier pips along the bottom of the face
            if (!locked && tier != null) {
                val n = tier.ordinal + 1
                val r = this.size.minDimension * 0.035f
                val gap = r * 3f
                val y = this.size.height * (if (shape == BadgeShape.SHIELD) 0.74f else 0.78f)
                val startX = this.size.width / 2f - gap * (n - 1) / 2f
                for (i in 0 until n) {
                    drawCircle(rimLo, r * 1.35f, Offset(startX + i * gap, y))
                    drawCircle(rimHi, r, Offset(startX + i * gap, y))
                }
            }
        }
        Icon(
            if (locked) Duo.Lock else badgeIcon(icon), null,
            tint = Color.White.copy(alpha = if (locked) 0.75f else 1f),
            modifier = Modifier.size(size * (if (locked) 0.30f else 0.38f)).align(Alignment.Center),
        )
    }
}

private fun lerpWhite(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, 1f)
