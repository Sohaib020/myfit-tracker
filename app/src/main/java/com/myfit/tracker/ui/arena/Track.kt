package com.myfit.tracker.ui.arena

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GOLD = Color(0xFFFFC83D)
private val GOLD_DEEP = Color(0xFFF29E0C)

/** Five-point star; collected = gold with a soft glow, waiting = white outline. */
fun DrawScope.star(c: Offset, r: Float, filled: Boolean, glow: Float = 0f) {
    val p = Path()
    for (i in 0 until 10) {
        val a = -PI / 2 + i * PI / 5; val rr = if (i % 2 == 0) r else r * 0.46f
        val x = c.x + (cos(a) * rr).toFloat(); val y = c.y + (sin(a) * rr).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    if (filled) {
        drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.55f + 0.25f * glow), GOLD.copy(alpha = 0f)), c, r * 2.2f), r * 2.2f, c)
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFE27A), GOLD, GOLD_DEEP), c.y - r, c.y + r))
        rotate(0f, c) { drawPath(p, Color.White.copy(alpha = 0.7f), style = Stroke(r * 0.12f)) }
    } else {
        drawPath(p, Color.Black.copy(alpha = 0.15f))
        drawPath(p, Color.White.copy(alpha = 0.9f), style = Stroke(r * 0.16f))
    }
}
