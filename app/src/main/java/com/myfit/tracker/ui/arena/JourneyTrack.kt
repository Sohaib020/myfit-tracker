package com.myfit.tracker.ui.arena

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.roundToInt

/**
 * Journey route as a clean vertical trail: a gently winding path down the card, one pin per stop (reached = filled
 * with a tick, next = pulsing ring, later = hollow, finish = flag), your photo at your exact position, and each stop's
 * name and distance beside it. Vector only — no map image.
 */
@Composable
fun JourneyTrack(j: Journey, doneKm: Double, photo: ImageBitmap?, initial: Char) {
    val th = LocalFitTheme.current
    val accent = journeyAccent(j.id)
    val stops = j.stops
    val n = stops.size
    val stepDp = 78.dp
    val topDp = 26.dp
    val anim by animateFloatAsState(doneKm.toFloat(), tween(1200), label = "trail")
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p")
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(topDp * 2 + stepDp * (n - 1))) {
        val wPx = with(density) { maxWidth.toPx() }
        val topPx = with(density) { topDp.toPx() }
        val stepPx = with(density) { stepDp.toPx() }
        fun pin(i: Int) = Offset(wPx * (if (i % 2 == 0) 0.14f else 0.27f), topPx + i * stepPx)
        fun bez(a: Offset, b: Offset, t: Float): Offset {
            // vertical tangents: control points straight below a / above b
            val c1 = Offset(a.x, a.y + stepPx * 0.5f); val c2 = Offset(b.x, b.y - stepPx * 0.5f)
            val u = 1 - t
            return Offset(u * u * u * a.x + 3 * u * u * t * c1.x + 3 * u * t * t * c2.x + t * t * t * b.x,
                u * u * u * a.y + 3 * u * u * t * c1.y + 3 * u * t * t * c2.y + t * t * t * b.y)
        }
        // where am I: segment k and fraction f
        val km = anim.toDouble()
        val k = (0 until n - 1).lastOrNull { km >= stops[it].km } ?: 0
        val f = if (k >= n - 1) 1f else ((km - stops[k].km) / (stops[k + 1].km - stops[k].km).coerceAtLeast(0.001)).toFloat().coerceIn(0f, 1f)
        val finished = km >= stops.last().km
        val me = if (finished) pin(n - 1) else bez(pin(k), pin(k + 1), f)

        Canvas(Modifier.fillMaxSize()) {
            val full = Path().apply {
                moveTo(pin(0).x, pin(0).y)
                for (i in 0 until n - 1) { val a = pin(i); val b = pin(i + 1); cubicTo(a.x, a.y + stepPx * 0.5f, b.x, b.y - stepPx * 0.5f, b.x, b.y) }
            }
            // remaining trail: soft dashed
            drawPath(full, th.text.copy(alpha = 0.18f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 9.dp.toPx()))))
            // walked trail: solid, sampled up to my position
            val done = Path().apply {
                moveTo(pin(0).x, pin(0).y)
                for (i in 0 until n - 1) {
                    if (i > k && !finished) break
                    val a = pin(i); val b = pin(i + 1)
                    val end = if (i == k && !finished) f else 1f
                    val steps = 24
                    for (s in 1..steps) { val p = bez(a, b, end * s / steps); lineTo(p.x, p.y) }
                }
            }
            drawPath(done, accent.copy(alpha = 0.35f), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            drawPath(done, accent, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            // pins
            stops.forEachIndexed { i, s ->
                val c = pin(i)
                val reached = km >= s.km
                val next = !reached && (i == 0 || km >= stops[i - 1].km)
                if (next) drawCircle(accent.copy(alpha = 0.35f * (1 - pulse)), 11.dp.toPx() + 14.dp.toPx() * pulse, c)
                drawCircle(if (reached) accent else th.bgTop, 11.dp.toPx(), c)
                drawCircle(if (reached) Color.White.copy(alpha = 0.9f) else accent, 11.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
            }
        }
        // pin glyphs (tick / number / flag)
        stops.forEachIndexed { i, s ->
            val c = pin(i)
            val reached = km >= s.km
            Box(Modifier.offset { IntOffset((c.x - with(density) { 11.dp.toPx() }).roundToInt(), (c.y - with(density) { 11.dp.toPx() }).roundToInt()) }.size(22.dp), contentAlignment = Alignment.Center) {
                when {
                    i == n - 1 -> Icon(Duo.Flag, null, tint = if (reached) Color.White else accent, modifier = Modifier.size(12.dp))
                    reached -> Icon(Duo.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    else -> Text("${i + 1}", style = FitType.caption, color = accent)
                }
            }
            // label to the right
            Column(Modifier.offset { IntOffset((wPx * 0.4f).roundToInt(), (c.y - with(density) { 18.dp.toPx() }).roundToInt()) }.width(with(density) { (wPx * 0.58f).toDp() })) {
                Text(s.name, style = FitType.label, color = if (reached) th.text else th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (i == 0) "Start" else "${Fmt.trim(s.km, 1)} km" + if (reached) " · reached" else " · ${Fmt.trim(s.km - km, 1)} km to go",
                    style = FitType.caption, color = if (reached) accent else th.textFaint, maxLines = 1)
            }
        }
        // you
        Box(Modifier.offset { IntOffset((me.x - with(density) { 19.dp.toPx() }).roundToInt(), (me.y - with(density) { 19.dp.toPx() }).roundToInt()) }
            .size(38.dp).clip(CircleShape).background(accent).border(3.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
            if (photo != null) Image(photo, "You", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            else Text(initial.uppercase(), style = FitType.section, color = Color.White)
        }
    }
}
