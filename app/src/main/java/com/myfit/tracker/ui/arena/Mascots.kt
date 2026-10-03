package com.myfit.tracker.ui.arena

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Friendly vector faces for the Arena cast (original designs, drawn in code so they stay crisp and tiny). */
@Composable
fun MascotFace(m: Mascot, size: Dp = 56.dp, modifier: Modifier = Modifier, happy: Boolean = true) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val c = Offset(w / 2, w * 0.55f); val r = w * 0.36f
        // species features behind the head
        when (m) {
            Mascot.TAJ -> { // spiral markhor horns
                for (s in listOf(-1f, 1f)) {
                    val p = Path().apply {
                        moveTo(c.x + s * r * 0.45f, c.y - r * 0.8f)
                        cubicTo(c.x + s * r * 0.2f, c.y - r * 1.6f, c.x + s * r * 1.1f, c.y - r * 1.5f, c.x + s * r * 0.9f, c.y - r * 2.1f)
                    }
                    drawPath(p, m.accent, style = Stroke(w * 0.07f, cap = StrokeCap.Round))
                }
            }
            Mascot.ZARA, Mascot.MOTU -> for (s in listOf(-1f, 1f)) drawCircle(if (m == Mascot.MOTU) m.accent else m.color, r * 0.32f, Offset(c.x + s * r * 0.78f, c.y - r * 0.78f))
            Mascot.KAMI -> drawOval(m.accent.copy(alpha = 0.5f), Offset(c.x - r * 0.2f, c.y - r * 1.25f), Size(r * 0.4f, r * 0.5f))
            Mascot.PIP -> drawLine(m.color, Offset(c.x, c.y - r), Offset(c.x + r * 0.25f, c.y - r * 1.45f), w * 0.05f, StrokeCap.Round)
            else -> Unit
        }
        // head
        drawCircle(Brush.radialGradient(listOf(lighten(m.color, 0.25f), m.color), Offset(c.x - r * 0.3f, c.y - r * 0.4f), r * 1.4f), r, c)
        when (m) {
            Mascot.ZARA -> listOf(-0.5f to -0.3f, 0.55f to -0.2f, -0.1f to -0.65f, 0.3f to 0.55f, -0.6f to 0.4f).forEach { (x, y) -> drawCircle(m.accent.copy(alpha = 0.5f), r * 0.09f, Offset(c.x + x * r, c.y + y * r)) }
            Mascot.MOTU -> for (s in listOf(-1f, 1f)) drawOval(m.accent, Offset(c.x + s * r * 0.38f - r * 0.2f, c.y - r * 0.32f), Size(r * 0.4f, r * 0.5f))
            Mascot.PIP -> drawRect(Color(0xFF1C4157), Offset(c.x - r * 0.95f, c.y - r * 0.62f), Size(r * 1.9f, r * 0.25f))
            Mascot.SHAHEEN -> {
                val beak = Path().apply { moveTo(c.x - r * 0.18f, c.y + r * 0.05f); lineTo(c.x + r * 0.18f, c.y + r * 0.05f); lineTo(c.x, c.y + r * 0.42f); close() }
                drawPath(beak, m.accent)
            }
            else -> Unit
        }
        // eyes + mouth
        val eyeY = c.y - r * 0.12f
        for (s in listOf(-1f, 1f)) {
            val ex = c.x + s * r * 0.38f
            if (happy && m != Mascot.MOTU) drawArc(Color(0xFF15181C), 200f, 140f, false, Offset(ex - r * 0.14f, eyeY - r * 0.1f), Size(r * 0.28f, r * 0.24f), style = Stroke(w * 0.035f, cap = StrokeCap.Round))
            else { drawCircle(if (m == Mascot.MOTU) Color.White else Color(0xFF15181C), r * 0.12f, Offset(ex, eyeY)); if (m == Mascot.MOTU) drawCircle(Color(0xFF15181C), r * 0.07f, Offset(ex, eyeY)) }
        }
        if (m != Mascot.SHAHEEN) drawArc(Color(0xFF15181C), 20f, 140f, false, Offset(c.x - r * 0.22f, c.y + r * 0.12f), Size(r * 0.44f, r * 0.3f), style = Stroke(w * 0.035f, cap = StrokeCap.Round))
        for (s in listOf(-1f, 1f)) drawCircle(Color(0xFFFF9AAE).copy(alpha = 0.55f), r * 0.13f, Offset(c.x + s * r * 0.62f, c.y + r * 0.2f))
    }
}

private fun lighten(c: Color, f: Float) = Color(c.red + (1 - c.red) * f, c.green + (1 - c.green) * f, c.blue + (1 - c.blue) * f, c.alpha)

@Suppress("unused") private fun DrawScope.noop() = Unit
