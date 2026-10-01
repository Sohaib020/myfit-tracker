package com.myfit.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** One point of a progress chart. `pr` points get a highlighted marker. */
data class ChartPoint(val x: Long, val y: Double, val label: String, val pr: Boolean = false)

/**
 * Line chart with a light grid, y-axis values and first/last dates. Tap anywhere to read the
 * nearest point. Values are drawn exactly as given — no smoothing that could invent a trend.
 */
@Composable
fun ProgressChart(
    points: List<ChartPoint>,
    color: Color,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val th = LocalFitTheme.current
    val measurer = rememberTextMeasurer()
    var sel by remember(points) { mutableIntStateOf(points.lastIndex) }
    if (points.isEmpty()) return
    val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
    val (lo, hi, step) = niceRange(minY, maxY)
    val minX = points.first().x; val maxX = points.last().x
    val grid = if (th.isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f)
    val axisStyle = FitType.caption.copy(color = th.textFaint)

    Column(modifier) {
        val p = points.getOrNull(sel)
        if (p != null) Row(Modifier.padding(bottom = 6.dp)) {
            Text(format(p.y), style = FitType.section, color = th.text)
            Text("  ·  ${p.label}" + if (p.pr) "  ·  PR" else "", style = FitType.caption, color = if (p.pr) th.warning else th.textDim, modifier = Modifier.padding(top = 3.dp))
        }
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            Canvas(
                Modifier.fillMaxWidth().height(170.dp).pointerInput(points) {
                    detectTapGestures { o ->
                        val left = 44.dp.toPx(); val w = size.width - left - 8.dp.toPx()
                        val idx = points.indices.minByOrNull { i -> abs(xFor(points[i].x, minX, maxX, left, w, points.size, i) - o.x) } ?: return@detectTapGestures
                        sel = idx
                    }
                },
            ) {
                val left = 44.dp.toPx(); val top = 6.dp.toPx(); val bottom = 22.dp.toPx()
                val w = size.width - left - 8.dp.toPx(); val h = size.height - top - bottom
                fun yFor(v: Double) = top + h - ((v - lo) / (hi - lo)).toFloat() * h
                // grid + y labels
                var g = lo
                while (g <= hi + step * 0.01) {
                    val y = yFor(g)
                    drawLine(grid, Offset(left, y), Offset(left + w, y), 1.dp.toPx())
                    label(measurer, format(g), Offset(0f, y - 8.dp.toPx()), axisStyle)
                    g += step
                }
                // x labels: first and last date
                label(measurer, points.first().label, Offset(left, top + h + 5.dp.toPx()), axisStyle)
                if (points.size > 1) {
                    val lastL = measurer.measure(points.last().label, axisStyle)
                    drawText(lastL, topLeft = Offset(left + w - lastL.size.width, top + h + 5.dp.toPx()))
                }
                val pts = points.mapIndexed { i, pt -> Offset(xFor(pt.x, minX, maxX, left, w, points.size, i), yFor(pt.y)) }
                if (pts.size > 1) {
                    val line = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    val area = Path().apply { addPath(line); lineTo(pts.last().x, top + h); lineTo(pts.first().x, top + h); close() }
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent), top, top + h))
                    drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                pts.forEachIndexed { i, o ->
                    val pr = points[i].pr
                    if (pr) drawCircle(th.warning.copy(alpha = 0.35f), 8.dp.toPx(), o)
                    drawCircle(if (pr) th.warning else color, if (i == sel) 5.dp.toPx() else 3.dp.toPx(), o)
                    if (i == sel) {
                        drawLine(color.copy(alpha = 0.5f), Offset(o.x, top), Offset(o.x, top + h), 1.dp.toPx())
                        drawCircle(Color.White, 2.dp.toPx(), o)
                    }
                }
            }
        }
    }
}

/** Points are spaced by date; when every point has the same date they're spaced evenly. */
private fun xFor(x: Long, minX: Long, maxX: Long, left: Float, w: Float, n: Int, i: Int): Float =
    if (maxX > minX) left + ((x - minX).toDouble() / (maxX - minX)).toFloat() * w
    else if (n > 1) left + w * i / (n - 1) else left + w / 2

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(m: TextMeasurer, s: String, at: Offset, style: androidx.compose.ui.text.TextStyle) {
    drawText(m.measure(s, style), topLeft = at)
}

/** Rounded axis range with ~3–4 gridlines. */
private fun niceRange(min: Double, max: Double): Triple<Double, Double, Double> {
    var lo = min; var hi = max
    if (hi - lo < 1e-9) { lo -= maxOf(1.0, abs(lo) * 0.1); hi += maxOf(1.0, abs(hi) * 0.1) }
    val raw = (hi - lo) / 3.0
    val mag = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    lo = floor(lo / step) * step
    hi = ceil(hi / step) * step
    if (lo < 0 && min >= 0) lo = 0.0
    return Triple(lo, hi, step)
}
