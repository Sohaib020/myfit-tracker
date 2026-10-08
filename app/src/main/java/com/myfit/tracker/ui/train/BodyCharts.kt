package com.myfit.tracker.ui.train

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.WeightEntry
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Units
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val dFmt = DateTimeFormatter.ofPattern("d MMM")

/** Last weigh-in of each day, oldest first. */
private fun daily(all: List<WeightEntry>): List<Pair<LocalDate, Double>> =
    all.groupBy { it.localDate }.map { (d, l) -> LocalDate.parse(d) to l.maxBy { it.loggedAt }.weightKg }.sortedBy { it.first }

/** kg per week from a least-squares fit; null with under 2 weeks of data. */
private fun trendPerWeek(p: List<Pair<LocalDate, Double>>): Double? {
    if (p.size < 4) return null
    val x0 = p.first().first.toEpochDay()
    if (p.last().first.toEpochDay() - x0 < 14) return null
    val xs = p.map { (it.first.toEpochDay() - x0).toDouble() }; val ys = p.map { it.second }
    val mx = xs.average(); val my = ys.average()
    val den = xs.sumOf { (it - mx) * (it - mx) }
    return if (den == 0.0) null else xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den * 7
}

/**
 * Weight progress: smooth area chart with a 7-day average, goal line and touch-to-read values, range chips,
 * and the numbers that matter (latest, change, weekly trend, distance to goal).
 */
@Composable
fun WeightProgressCard(container: AppContainer, onLog: () -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val weights by remember { container.logRepo.weightsAll() }.collectAsState(initial = null)
    val profile by container.profileRepo.profile.collectAsState(initial = null)
    var range by rememberSaveable { mutableIntStateOf(1) }
    val today = Clock.today()
    val all = remember(weights) { daily(weights.orEmpty()) }
    val from = when (range) { 0 -> today.minusMonths(1); 1 -> today.minusMonths(3); 2 -> today.minusMonths(6); 3 -> today.minusYears(1); else -> null }
    val pts = all.filter { from == null || !it.first.isBefore(from) }
    val goal = profile?.targetWeightKg
    fun disp(kg: Double) = Units.kgTo(kg, u.weight)
    fun f(kg: Double, d: Int = 1) = Fmt.weight(kg, u.weight, d)

    GlassCard {
        CardHeader(Duo.MonitorWeight, "Weight", th.fat)
        Spacer(Modifier.height(10.dp))
        if (weights == null) { Caption("Loading…"); return@GlassCard }
        if (all.isEmpty()) {
            Text("Start your weight story", style = FitType.section, color = th.text)
            Caption("Log your first weigh-in — or let your watch or smart scale fill it in. Weigh at the same time each day for the clearest trend.")
            Spacer(Modifier.height(12.dp))
            AccentButton("Log weight", onLog, icon = Duo.Add, height = 46.dp)
            return@GlassCard
        }
        val latest = all.last().second
        val start = pts.firstOrNull()?.second ?: latest
        val change = latest - start
        val towardGoal = goal?.let { abs(latest - it) < abs(start - it) } ?: (change <= 0)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.trim(disp(latest), 1), style = FitType.hero.copy(fontSize = 44.sp), color = th.text)
            Spacer(Modifier.width(6.dp))
            Text(u.weight.label, style = FitType.section, color = th.textDim, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            if (pts.size >= 2) {
                val c = if (abs(change) < 0.05) th.textDim else if (towardGoal) th.success else th.warning
                Text((if (change > 0) "▲ " else if (change < 0) "▼ " else "") + f(abs(change)),
                    style = FitType.label, color = c,
                    modifier = Modifier.padding(bottom = 10.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Caption("Last weigh-in ${LocalDate.parse(weights!!.maxBy { it.loggedAt }.localDate).format(dFmt)}")
        Spacer(Modifier.height(12.dp))
        if (pts.size >= 2) WeightChart(pts.map { it.first to disp(it.second) }, goal?.let { disp(it) }, th.fat, u.weight.label)
        else Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Caption("Two or more days in this range draw the graph.") }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1M", "3M", "6M", "1Y", "All").forEachIndexed { i, l -> GlassChip(l, range == i, { range = i }, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(14.dp))
        val trend = trendPerWeek(pts)
        Row(Modifier.fillMaxWidth()) {
            MiniStat("Start", f(start), Modifier.weight(1f))
            MiniStat("Trend", trend?.let { (if (it >= 0) "+" else "−") + f(abs(it), 2) + "/wk" } ?: "—", Modifier.weight(1f))
            MiniStat("To goal", goal?.let { f(abs(latest - it)) } ?: "Set one", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        GlassButton("Log weight", onLog, Modifier.fillMaxWidth(), icon = Duo.Add, height = 44.dp)
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Text(value, style = FitType.label, color = th.text)
        Text(label, style = FitType.caption, color = th.textDim)
    }
}

/** Smooth area line with 7-day average, goal line, min/max markers and a draggable read-out. */
@Composable
private fun WeightChart(pts: List<Pair<LocalDate, Double>>, goal: Double?, color: Color, unit: String) {
    val th = LocalFitTheme.current
    val tm = rememberTextMeasurer()
    val reveal = remember(pts) { Animatable(0f) }
    LaunchedEffect(pts) { reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    var touch by remember { mutableStateOf<Float?>(null) }
    val vals = pts.map { it.second }
    val lo0 = (vals + listOfNotNull(goal)).min(); val hi0 = (vals + listOfNotNull(goal)).max()
    val pad = ((hi0 - lo0) * 0.18).coerceAtLeast(0.6)
    val lo = lo0 - pad; val hi = hi0 + pad
    val x0 = pts.first().first.toEpochDay(); val x1 = pts.last().first.toEpochDay().coerceAtLeast(x0 + 1)
    val avg = pts.mapIndexed { i, (d, _) -> d to pts.subList(maxOf(0, i - 6), i + 1).map { it.second }.average() }
    Canvas(
        Modifier.fillMaxWidth().height(190.dp)
            .pointerInput(pts) { detectDragGestures(onDragEnd = { touch = null }, onDragCancel = { touch = null }) { c, _ -> touch = c.position.x } }
            .pointerInput(pts) { detectTapGestures(onPress = { o -> touch = o.x; tryAwaitRelease(); touch = null }) },
    ) {
        val left = 0f; val right = size.width; val top = 14.dp.toPx(); val bottom = size.height - 18.dp.toPx()
        fun x(d: LocalDate) = left + (right - left) * ((d.toEpochDay() - x0).toFloat() / (x1 - x0))
        fun y(v: Double) = (bottom - (bottom - top) * ((v - lo) / (hi - lo))).toFloat()
        // grid
        repeat(4) { i ->
            val gy = top + (bottom - top) * i / 3f
            drawLine(th.text.copy(alpha = 0.06f), Offset(left, gy), Offset(right, gy), 1.dp.toPx())
        }
        val p = pts.map { Offset(x(it.first), y(it.second)) }
        val line = smooth(p)
        val clipW = left + (right - left) * reveal.value
        clipRect(right = clipW) {
            val area = Path().apply { addPath(line); lineTo(p.last().x, bottom); lineTo(p.first().x, bottom); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0.02f)), top, bottom))
            drawPath(smooth(avg.map { Offset(x(it.first), y(it.second)) }), th.text.copy(alpha = 0.35f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
            drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
        goal?.let { g ->
            val gy = y(g)
            drawLine(th.success.copy(alpha = 0.8f), Offset(left, gy), Offset(right, gy), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            label(tm, "Goal ${Fmt.trim(g, 1)}", Offset(right - 4.dp.toPx(), gy - 4.dp.toPx()), th.success, alignEnd = true)
        }
        // low / high markers + last point
        val iMin = vals.indexOf(vals.min()); val iMax = vals.indexOf(vals.max())
        listOf(iMin, iMax).distinct().forEach { i -> drawCircle(color.copy(alpha = 0.35f), 6.dp.toPx(), p[i]); drawCircle(color, 3.dp.toPx(), p[i]) }
        drawCircle(Color.White, 6.dp.toPx(), p.last()); drawCircle(color, 4.dp.toPx(), p.last())
        // axis dates
        label(tm, pts.first().first.format(dFmt), Offset(left, size.height - 2.dp.toPx()), th.textDim, bottomAnchor = true)
        label(tm, pts.last().first.format(dFmt), Offset(right, size.height - 2.dp.toPx()), th.textDim, alignEnd = true, bottomAnchor = true)
        // read-out
        touch?.let { tx ->
            val i = p.indices.minBy { abs(p[it].x - tx) }
            drawLine(th.text.copy(alpha = 0.4f), Offset(p[i].x, top), Offset(p[i].x, bottom), 1.dp.toPx())
            drawCircle(Color.White, 7.dp.toPx(), p[i]); drawCircle(color, 5.dp.toPx(), p[i])
            val txt = "${Fmt.trim(pts[i].second, 1)} $unit · ${pts[i].first.format(dFmt)}"
            val r = tm.measure(txt, FitType.caption.copy(color = th.text))
            val bx = (p[i].x - r.size.width / 2f).coerceIn(0f, size.width - r.size.width - 16.dp.toPx())
            drawRoundRect(th.bgBottom.copy(alpha = 0.92f), Offset(bx, 0f), Size(r.size.width + 16.dp.toPx(), r.size.height + 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
            drawText(r, topLeft = Offset(bx + 8.dp.toPx(), 4.dp.toPx()))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.label(tm: TextMeasurer, t: String, at: Offset, c: Color, alignEnd: Boolean = false, bottomAnchor: Boolean = false) {
    val r = tm.measure(t, FitType.caption.copy(color = c, fontSize = 11.sp))
    val x = if (alignEnd) at.x - r.size.width else at.x
    val y = if (bottomAnchor) at.y - r.size.height else at.y - r.size.height
    drawText(r, topLeft = Offset(x, y))
}

/** Monotone-ish cubic smoothing through the points. */
private fun smooth(p: List<Offset>): Path = Path().apply {
    if (p.isEmpty()) return@apply
    moveTo(p[0].x, p[0].y)
    for (i in 1 until p.size) {
        val a = p[i - 1]; val b = p[i]
        val cx = (a.x + b.x) / 2f
        cubicTo(cx, a.y, cx, b.y, b.x, b.y)
    }
}

/**
 * BMI with a gauge: zones, an animated needle, the healthy weight range for your height, and the lower
 * South Asian cut-offs (WHO Asia-Pacific: 23 overweight, 27.5 obese) on by default.
 */
@Composable
fun BmiCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val latest by remember { container.logRepo.latestWeight() }.collectAsState(initial = null)
    val profile by container.profileRepo.profile.collectAsState(initial = null)
    var asian by rememberSaveable { mutableStateOf(true) }
    val p = profile ?: return
    val kg = latest?.weightKg ?: p.startWeightKg
    val m = p.heightCm / 100.0
    if (m <= 0.5) return
    val bmi = kg / (m * m)
    val over = if (asian) 23.0 else 25.0; val obese = if (asian) 27.5 else 30.0
    val (cat, catColor) = when {
        bmi < 18.5 -> "Underweight" to th.water
        bmi < over -> "Healthy" to th.success
        bmi < obese -> "Overweight" to th.warning
        else -> "Obese" to th.danger
    }
    val needle = remember { Animatable(15f) }
    LaunchedEffect(bmi) { needle.animateTo(bmi.toFloat().coerceIn(15f, 40f), tween(1100, easing = FastOutSlowInEasing)) }
    GlassCard {
        CardHeader(Duo.AccessibilityNew, "Body Mass Index", catColor)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(2f), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(2f)) {
                val st = 18.dp.toPx()
                val r = size.width / 2f - st
                val c = Offset(size.width / 2f, size.height - 4.dp.toPx())
                val tl = Offset(c.x - r, c.y - r); val sz = Size(r * 2, r * 2)
                fun ang(v: Double) = 180f + 180f * ((v - 15) / 25).toFloat().coerceIn(0f, 1f)
                listOf(15.0 to 18.5 to th.water, 18.5 to over to th.success, over to obese to th.warning, obese to 40.0 to th.danger).forEach { (rg, col) ->
                    val a0 = ang(rg.first); val a1 = ang(rg.second)
                    drawArc(col.copy(alpha = 0.85f), a0 + 1f, (a1 - a0 - 2f).coerceAtLeast(1f), false, tl, sz, style = Stroke(st, cap = StrokeCap.Butt))
                }
                val a = Math.toRadians(ang(needle.value.toDouble()).toDouble())
                val tip = Offset(c.x + (r - st) * cos(a).toFloat(), c.y + (r - st) * sin(a).toFloat())
                drawLine(th.text, c, tip, 4.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(th.text, 9.dp.toPx(), c); drawCircle(catColor, 5.dp.toPx(), c)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 26.dp)) {
                Text(Fmt.trim(bmi, 1), style = FitType.hero.copy(fontSize = 38.sp), color = th.text)
                Text(cat, style = FitType.label, color = catColor)
            }
        }
        Spacer(Modifier.height(10.dp))
        val loKg = 18.5 * m * m; val hiKg = (over - 0.1) * m * m
        Text("Healthy weight for ${Fmt.trim(p.heightCm, 0)} cm: ${Fmt.weight(loKg, u.weight, 0)} – ${Fmt.weight(hiKg, u.weight, 0)}", style = FitType.body, color = th.text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("South Asian", asian, { asian = true })
            GlassChip("Standard WHO", !asian, { asian = false })
        }
        Spacer(Modifier.height(6.dp))
        Caption(if (asian) "South Asian cut-offs: health risks start at a lower BMI — 23+ overweight, 27.5+ obese." else "Standard WHO cut-offs: 25+ overweight, 30+ obese.", color = th.textDim)
        Caption("BMI doesn't see muscle vs fat — use it alongside waist size and how you feel. Not a diagnosis.", color = th.textFaint)
    }
}
