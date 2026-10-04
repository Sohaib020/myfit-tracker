package com.myfit.tracker.ui.arena

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TRACK_DONE = Color(0xFFFFC83D)
private val PIN = Color(0xFF4CC38A)

/** Decoded map/cover art, kept small: at most a few bitmaps stay in memory. */
internal object MapArt {
    private val cache = android.util.LruCache<String, ImageBitmap>(6)
    suspend fun get(c: android.content.Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val b = withContext(Dispatchers.IO) { runCatching { c.assets.open(path).use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull() }
        if (b != null) cache.put(path, b)
        return b
    }
}

/** Serpentine route over the map in 0..1 coordinates: four lanes joined by rounded turns (start top-left). */
private fun route(w: Float, h: Float): Path {
    val xs = 0.12f * w; val xe = 0.88f * w
    val ys = listOf(0.13f, 0.37f, 0.61f, 0.85f).map { it * h }
    val r = (ys[1] - ys[0]) / 2
    return Path().apply {
        moveTo(xs, ys[0])
        ys.forEachIndexed { i, y ->
            val goingRight = i % 2 == 0
            val endX = if (goingRight) xe - r else xs + r
            if (i == ys.lastIndex) { lineTo(if (goingRight) xe else xs, y); return@forEachIndexed }
            lineTo(endX, y)
            // half-circle turn down to the next lane
            val cx = endX
            if (goingRight) cubicTo(cx + r * 1.33f, y, cx + r * 1.33f, y + 2 * r, cx, y + 2 * r)
            else cubicTo(cx - r * 1.33f, y, cx - r * 1.33f, y + 2 * r, cx, y + 2 * r)
        }
    }
}

/** A pin on a [RouteMap]: where it sits on the route (0..1), whether you've reached it, and its label. */
data class RoutePin(val frac: Float, val label: String, val reached: Boolean, val final: Boolean = false)

/**
 * Samsung-Health-style challenge map: illustrated Pakistani scene, a winding route, star checkpoints with goal
 * values, your photo (or buddy) marking progress, and the time left.
 */
@Composable
fun ChallengeMap(ch: ArenaChallenge, frac: Float, reached: Set<Int>, daysLeft: Int, photo: ImageBitmap?, partner: Mascot, height: Dp = 420.dp) {
    val ctx = LocalContext.current
    val art by produceState<ImageBitmap?>(null, ch.scene) { value = MapArt.get(ctx, "challenge/${ch.scene.name.lowercase()}.webp") }
    val pins = CHECKPOINTS.mapIndexed { i, (cf, _) -> RoutePin(cf.toFloat(), fmtMetric(ch.goal * cf, ch.metric, unit = false), i in reached, cf >= 1.0) }
    RouteMap(art, ch.scene.sky + ch.scene.hills, frac, pins,
        if (frac >= 1f) "Completed" else if (daysLeft <= 0) "Last day" else "$daysLeft day${if (daysLeft == 1) "" else "s"} left",
        photo, partner, height, scrim = false)
}

/** Generic illustrated route: winding track over [art], pins, and you. [scrim] darkens busy artwork so the route reads. */
@Composable
fun RouteMap(art: ImageBitmap?, fallback: List<Color>, frac: Float, pins: List<RoutePin>, footer: String?, photo: ImageBitmap?, partner: Mascot,
             height: Dp = 420.dp, scrim: Boolean = true, header: (@Composable BoxScope.() -> Unit)? = null) {
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(1400), label = "map")
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(28.dp))
        .background(Brush.verticalGradient(fallback))) {
        art?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        if (scrim) Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f)))))
        BoxWithConstraints(Modifier.matchParentSize()) {
            val wPx = with(density) { maxWidth.toPx() }; val hPx = with(density) { maxHeight.toPx() }
            val path = remember(wPx, hPx) { route(wPx, hPx) }
            val pm = remember(path) { PathMeasure().apply { setPath(path, false) } }
            val len = pm.length
            fun at(p: Float): Offset = pm.getPosition((p.coerceIn(0f, 1f)) * len)
            Canvas(Modifier.matchParentSize()) {
                val sw = 14.dp.toPx()
                drawPath(path, Color.Black.copy(alpha = 0.18f), style = Stroke(sw + 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, Color.White.copy(alpha = if (scrim) 0.85f else 1f), style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (f > 0.001f) {
                    val done = Path(); pm.getSegment(0f, f * len, done, true)
                    drawPath(done, TRACK_DONE, style = Stroke(sw * 0.62f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // checkered start and finish
                fun flag(o: Offset) {
                    val s = 5.dp.toPx()
                    for (r in 0..1) for (c in 0..2) drawRect(if ((r + c) % 2 == 0) Color.Black else Color.White, Offset(o.x - s * 1.5f + c * s, o.y - s + r * s), androidx.compose.ui.geometry.Size(s, s))
                }
                flag(at(0f)); flag(at(1f))
            }
            // pins + labels
            pins.forEach { pn ->
                val o = at(pn.frac)
                val pin = 30.dp
                Box(Modifier.offset(x = with(density) { o.x.toDp() } - pin / 2, y = with(density) { o.y.toDp() } - pin / 2).size(pin)
                    .shadow(4.dp, CircleShape).clip(CircleShape).background(if (pn.reached) TRACK_DONE else PIN).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (pn.final) Duo.Flag else Duo.Star, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                if (pn.label.isNotEmpty()) Text(pn.label, style = FitType.label, color = Color.White, maxLines = 1,
                    modifier = Modifier.offset(x = with(density) { o.x.toDp() } - 34.dp, y = with(density) { o.y.toDp() } + 17.dp)
                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            // you
            val me = at(f); val av = 44.dp
            Column(Modifier.offset(x = with(density) { me.x.toDp() } - av / 2, y = with(density) { me.y.toDp() } - av - 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(av).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).border(3.dp, Color(0xFF1B1E2A), CircleShape), contentAlignment = Alignment.Center) {
                    if (photo != null) Image(photo, "You", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop) else CastImage(partner, av - 4.dp)
                }
                Canvas(Modifier.size(12.dp, 8.dp)) {
                    drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, Color(0xFF1B1E2A))
                }
            }
        }
        header?.invoke(this)
        if (footer != null) Text(footer, style = FitType.label, color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}
