package com.myfit.tracker.ui.arena

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.LocalDate
import kotlin.math.ceil

/** Everything a challenge dashboard shows, computed once from the device-recorded days. */
data class PaceModel(
    val total: Int, val elapsed: Int, val daysLeft: Int,
    val daily: List<Double?>,            // per challenge day; null = still in the future
    val value: Double, val goal: Double, val frac: Float, val pace: Float,
    val perDayNeeded: Double, val avg: Double, val best: Double, val bestDate: LocalDate?,
    val doneOn: LocalDate?, val projectedFinish: LocalDate?, val projectedTotal: Double,
    val aheadBy: Double,                // + ahead of even pace, − behind
)

fun paceModel(ch: ArenaChallenge, days: List<Day>, today: LocalDate): PaceModel {
    val total = (ch.to.toEpochDay() - ch.from.toEpochDay() + 1).toInt()
    val elapsed = (today.toEpochDay() - ch.from.toEpochDay() + 1).toInt().coerceIn(1, total)
    val byDate = days.associateBy { it.date }
    val daily = (0 until total).map { k -> ch.from.plusDays(k.toLong()).let { d -> if (d.isAfter(today)) null else byDate[d]?.let { dayValue(it, ch.metric) } ?: 0.0 } }
    val value = daily.filterNotNull().sum()
    var acc = 0.0; var doneOn: LocalDate? = null
    daily.forEachIndexed { i, v -> if (v != null) { acc += v; if (doneOn == null && acc >= ch.goal - 1e-9) doneOn = ch.from.plusDays(i.toLong()) } }
    val left = (ch.to.toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(0)
    val past = daily.take(elapsed).filterNotNull()
    val avg = if (past.isEmpty()) 0.0 else past.sum() / past.size
    val bestIdx = daily.indices.filter { daily[it] != null }.maxByOrNull { daily[it]!! }
    val projFinish = when {
        doneOn != null -> doneOn
        avg <= 0 -> null
        else -> ch.from.plusDays((ceil(ch.goal / avg) - 1).toLong()).takeIf { !it.isAfter(ch.to) }
    }
    val pace = elapsed.toFloat() / total
    return PaceModel(total, elapsed, left, daily, value, ch.goal, (value / ch.goal).toFloat().coerceIn(0f, 1f), pace,
        ((ch.goal - value).coerceAtLeast(0.0) / (left + 1)), avg, bestIdx?.let { daily[it] } ?: 0.0, bestIdx?.let { ch.from.plusDays(it.toLong()) },
        doneOn, projFinish, avg * total, value - ch.goal * pace)
}

fun fmtMetric(v: Double, m: ArenaMetric, unit: Boolean = true): String =
    (if (m == ArenaMetric.DISTANCE) Fmt.trim(v, 1) else Fmt.int(v)) + if (unit) " " + (if (m == ArenaMetric.DISTANCE) "km" else m.unit) else ""

fun shortDate(d: LocalDate) = "${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"

/** Progress ring with a tick where even pace would be. */
@Composable
fun ProgressRing(frac: Float, pace: Float?, color: Color, size: Dp, stroke: Dp = 10.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val th = LocalFitTheme.current
    val f by animateFloatAsState(frac, tween(1000), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx(); val r = (this.size.minDimension - s) / 2
            val tl = Offset(center.x - r, center.y - r); val sz = Size(r * 2, r * 2)
            drawArc(th.text.copy(alpha = 0.08f), 0f, 360f, false, tl, sz, style = Stroke(s))
            drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.75f), color, color.copy(alpha = 0.75f))), -90f, 360f * f, false, tl, sz, style = Stroke(s, cap = StrokeCap.Round))
            if (pace != null && pace in 0.01f..0.99f) {
                val a = Math.toRadians(-90.0 + 360.0 * pace)
                val inner = r - s * 0.85f; val outer = r + s * 0.85f
                drawLine(th.text.copy(alpha = 0.75f), Offset(center.x + (inner * kotlin.math.cos(a)).toFloat(), center.y + (inner * kotlin.math.sin(a)).toFloat()),
                    Offset(center.x + (outer * kotlin.math.cos(a)).toFloat(), center.y + (outer * kotlin.math.sin(a)).toFloat()), 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        content()
    }
}

/**
 * Cumulative progress vs. even pace: solid line = you (filled area), dashed = target pace, faint dashed = projection
 * at your current daily average. Horizontal guides at the checkpoints. A small avatar marks where you are.
 */
@Composable
fun PaceChart(m: PaceModel, ch: ArenaChallenge, avatar: Mascot?, height: Dp, labels: Boolean = true, color: Color = ch.mascot.accent) {
    val th = LocalFitTheme.current
    val measurer = rememberTextMeasurer()
    val grow by animateFloatAsState(1f, tween(900), label = "pc")
    val top = maxOf(m.goal, m.value, if (m.doneOn == null) m.projectedTotal.coerceAtMost(m.goal * 1.25) else 0.0) * 1.04
    val labelW = if (labels) 44.dp else 0.dp
    val bottomPad = if (labels) 18.dp else 2.dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
        val w = maxWidth - labelW; val h = maxHeight - bottomPad
        fun x(i: Float): Dp = w * (i / (m.total - 1).coerceAtLeast(1).toFloat())
        fun y(v: Double): Dp = h * (1f - (v / top).toFloat())
        Canvas(Modifier.fillMaxSize()) {
            val pw = w.toPx(); val ph = h.toPx()
            fun px(i: Float) = pw * (i / (m.total - 1).coerceAtLeast(1).toFloat())
            fun py(v: Double) = ph * (1f - (v / top).toFloat())
            val lbl = TextStyle(fontSize = 10.sp, color = th.textDim)
            // checkpoint guides
            CHECKPOINTS.forEach { (f, _) ->
                val yy = py(m.goal * f)
                drawLine(th.text.copy(alpha = if (f >= 1.0) 0.22f else 0.08f), Offset(0f, yy), Offset(pw, yy), 1.dp.toPx())
                if (labels) drawText(measurer, if (f >= 1.0) "Goal" else "${(f * 100).toInt()}%", Offset(pw + 6.dp.toPx(), yy - 7.dp.toPx()), lbl)
            }
            // even-pace line
            drawLine(th.text.copy(alpha = 0.45f), Offset(0f, py(0.0)), Offset(pw, py(m.goal)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)))
            // your cumulative line + area
            val pts = ArrayList<Offset>(); var acc = 0.0
            m.daily.forEachIndexed { i, v -> if (v != null) { acc += v; pts += Offset(px(i.toFloat()), py(acc * grow)) } }
            if (pts.isNotEmpty()) {
                val line = Path().apply { moveTo(0f, py(0.0)); pts.forEach { lineTo(it.x, it.y) } }
                val area = Path().apply { addPath(line); lineTo(pts.last().x, ph); lineTo(0f, ph); close() }
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)), 0f, ph))
                drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                // projection at your current average
                if (m.doneOn == null && m.avg > 0 && m.daysLeft > 0) {
                    val last = pts.last(); val endV = (m.value + m.avg * m.daysLeft).coerceAtMost(top)
                    drawLine(color.copy(alpha = 0.55f), last, Offset(pw, py(endV)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)), cap = StrokeCap.Round)
                }
            }
            // today marker
            val tx = px((m.elapsed - 1).toFloat())
            drawLine(th.text.copy(alpha = 0.12f), Offset(tx, 0f), Offset(tx, ph), 1.dp.toPx())
            if (labels) {
                drawText(measurer, shortDate(ch.from), Offset(0f, ph + 4.dp.toPx()), lbl)
                val end = measurer.measure(shortDate(ch.to), lbl)
                drawText(end, topLeft = Offset(pw - end.size.width, ph + 4.dp.toPx()))
            }
        }
        // where you are: a small avatar badge on the line
        if (avatar != null) {
            val cx = x((m.elapsed - 1).toFloat()); val cy = y(m.value * grow)
            val s = 24.dp
            Box(Modifier.offset(x = (cx - s / 2).coerceIn(0.dp, w - s), y = (cy - s / 2).coerceIn(0.dp, h - s)).size(s).clip(CircleShape)
                .background(Color.White).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) { CastImage(avatar, s - 2.dp) }
        }
    }
}

/** Day-by-day bars with the best day highlighted, today outlined and the even-pace target dashed. */
@Composable
fun DailyBars(m: PaceModel, ch: ArenaChallenge, height: Dp, color: Color = ch.mascot.accent) {
    val th = LocalFitTheme.current
    val gold = Color(0xFFFFC83D)
    val target = m.goal / m.total
    val max = ((m.daily.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(target * 1.25)).coerceAtLeast(1e-6)
    val grow by animateFloatAsState(1f, tween(900), label = "db")
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val n = m.daily.size; val gap = (if (n > 14) 2 else 5).dp.toPx(); val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        m.daily.forEachIndexed { i, v ->
            val x = i * (bw + gap)
            if (v == null) { drawRoundRect(th.text.copy(alpha = 0.06f), Offset(x, size.height - 3.dp.toPx()), Size(bw, 3.dp.toPx()), CornerRadius(2f)); return@forEachIndexed }
            val hh = ((v / max).toFloat() * size.height * grow).coerceAtLeast(3f)
            val isBest = v > 0 && v == m.best
            val c = when { isBest -> gold; v >= target -> color; else -> color.copy(alpha = 0.45f) }
            drawRoundRect(c, Offset(x, size.height - hh), Size(bw, hh), CornerRadius(bw.coerceAtMost(12f) / 2))
            if (i == m.elapsed - 1) drawRoundRect(th.text.copy(alpha = 0.8f), Offset(x, size.height - hh), Size(bw, hh), CornerRadius(bw.coerceAtMost(12f) / 2), style = Stroke(1.5.dp.toPx()))
        }
        val yy = size.height * (1 - (target / max).toFloat())
        drawLine(th.text.copy(alpha = 0.5f), Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    }
}

/** "Ahead / behind pace" pill. */
@Composable
fun PaceBadge(m: PaceModel, metric: ArenaMetric) {
    val th = LocalFitTheme.current
    val (txt, c) = when {
        m.doneOn != null -> "Completed ${shortDate(m.doneOn)}" to th.success
        m.aheadBy >= 0 -> "Ahead by ${fmtMetric(m.aheadBy, metric)}" to th.success
        else -> "Behind by ${fmtMetric(-m.aheadBy, metric)}" to th.warning
    }
    Text(txt, style = FitType.label, color = c, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
}
