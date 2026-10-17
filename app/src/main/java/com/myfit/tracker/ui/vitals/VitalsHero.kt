package com.myfit.tracker.ui.vitals

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.VitalType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme

private val HEART_RED = Color(0xFFFF3B5C)
private val HEART_PINK = Color(0xFFFF7A9A)

/** Latest pulse to show: your last camera check, else resting / average HR from your watch. */
@Composable
private fun rememberLastPulse(container: AppContainer): Pair<Int?, String> {
    val today = remember { Clock.today() }
    val cam by remember { container.db.vitalDao().observeByType(VitalType.HR_CAMERA, 1) }.collectAsState(initial = emptyList())
    val days by remember { container.db.healthDao().observeDaily(Clock.dateKey(today.minusDays(6)), Clock.dateKey(today)) }.collectAsState(initial = emptyList())
    val c = cam.firstOrNull()
    if (c != null) {
        val mins = ((System.currentTimeMillis() - c.takenAt) / 60_000L).coerceAtLeast(0)
        val ago = when { mins < 1 -> "just now"; mins < 60 -> "$mins min ago"; mins < 24 * 60 -> "${mins / 60} h ago"; else -> "${mins / (24 * 60)} d ago" }
        return c.value.toInt() to "Last check $ago"
    }
    val rest = days.lastOrNull { it.restingHr != null }?.restingHr
    val avg = days.lastOrNull { it.avgHr != null }?.avgHr
    return (rest ?: avg) to (if (rest != null) "Resting · from your watch" else if (avg != null) "Average · from your watch" else "No reading yet")
}

/** Heart shape centred in a w×h box. */
private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.36f)
    cubicTo(cx - s * 0.62f, cy - s * 0.02f, cx - s * 0.46f, cy - s * 0.58f, cx, cy - s * 0.26f)
    cubicTo(cx + s * 0.46f, cy - s * 0.58f, cx + s * 0.62f, cy - s * 0.02f, cx, cy + s * 0.36f)
    close()
}

/** Lub-dub: two quick swells per beat, then rest. [p] = phase 0..1 within one beat. */
private fun beatScale(p: Float): Float {
    fun bump(c: Float, w: Float, a: Float): Float { val d = (p - c) / w; return if (d < -1f || d > 1f) 0f else a * (1f - d * d) }
    return 1f + bump(0.08f, 0.08f, 0.16f) + bump(0.26f, 0.07f, 0.09f)
}

/** One ECG cycle (P, QRS, T) sampled at x in 0..1 → y offset (-1..1, up = negative). */
private fun ecg(x: Float): Float {
    fun g(c: Float, w: Float, a: Float): Float { val d = (x - c) / w; return a * kotlin.math.exp(-d * d) }
    return -(g(0.18f, 0.035f, 0.12f) - g(0.30f, 0.010f, 0.18f) + g(0.33f, 0.012f, 1.0f) - g(0.36f, 0.012f, 0.32f) + g(0.58f, 0.06f, 0.26f))
}

/** The glowing, beating heart (drawn, so it's crisp at any size). */
@Composable
fun BeatingHeart(bpm: Int, size: Dp, modifier: Modifier = Modifier) {
    val period = (60_000 / bpm.coerceIn(45, 140))
    val inf = rememberInfiniteTransition(label = "heart")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing)), label = "beat")
    val k = beatScale(p)
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val s = this.size.minDimension * 0.78f * k
        // halo that breathes with each beat
        drawCircle(Brush.radialGradient(listOf(HEART_RED.copy(alpha = 0.55f * (k - 0.92f) * 4f), Color.Transparent), c, this.size.minDimension * 0.62f), this.size.minDimension * 0.62f, c)
        val path = heartPath(c.x, c.y + s * 0.06f, s)
        drawPath(path, Brush.linearGradient(listOf(HEART_PINK, HEART_RED, Color(0xFFC2103A)), Offset(c.x - s / 2, c.y - s / 2), Offset(c.x + s / 2, c.y + s / 2)))
        // glossy highlight
        drawPath(heartPath(c.x - s * 0.17f, c.y - s * 0.12f, s * 0.32f), Color.White.copy(alpha = 0.28f))
    }
}

/** ECG trace sweeping left→right with a bright head and a fading trail. */
private fun DrawScope.ecgSweep(t: Float, color: Color, beats: Int = 3) {
    val w = size.width; val h = size.height; val mid = h * 0.55f; val amp = h * 0.36f
    val head = t * w
    val n = 140
    var prev: Offset? = null
    for (i in 0..n) {
        val x = w * i / n
        val y = mid + ecg((x / w * beats) % 1f) * amp
        val o = Offset(x, y)
        val behind = ((head - x) / w).let { if (it < 0) it + 1f else it }      // 0 at head … 1 a full lap behind
        val a = (1f - behind).coerceIn(0f, 1f)
        prev?.let { if (a > 0.02f) drawLine(color.copy(alpha = a * 0.9f), it, o, strokeWidth = 2.6f + 1.4f * a, cap = StrokeCap.Round) }
        prev = o
    }
    val hy = mid + ecg((head / w * beats) % 1f) * amp
    drawCircle(Color.White, 4.5f, Offset(head, hy))
    drawCircle(color.copy(alpha = 0.45f), 12f, Offset(head, hy))
}

/** R28 Home Vitals card: a beating heart, a live ECG sweep and one big "Check my heart" call to action. */
@Composable
fun VitalsHeroCard(container: AppContainer, onLogBp: () -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val (bpm, sub) = rememberLastPulse(container)
    val inf = rememberInfiniteTransition(label = "vitals")
    val sweep by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val glow by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")
    Glass(Modifier.fillMaxWidth(), tint = HEART_RED.copy(alpha = 0.14f), onClick = { nav.push(Overlay.Vitals) }) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(HEART_RED.copy(alpha = 0.30f), Color.Transparent), Offset(size.width * 0.18f, size.height * 0.35f), size.height * 0.9f),
                size.height * 0.9f, Offset(size.width * 0.18f, size.height * 0.35f))
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BeatingHeart(bpm ?: 72, 92.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("HEART CHECK", style = FitType.overline, color = HEART_PINK)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(bpm?.toString() ?: "--", style = FitType.metric.copy(fontSize = 42.sp, lineHeight = 44.sp), color = th.text)
                        Spacer(Modifier.width(4.dp))
                        Text("bpm", style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 7.dp))
                    }
                    Text(sub, style = FitType.caption, color = th.textDim)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(44.dp).padding(vertical = 2.dp)) { ecgSweep(sweep, HEART_RED) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // the main call to action, glowing
                Box(Modifier.weight(1f).height(54.dp)) {
                    Box(Modifier.matchParentSize().graphicsLayer { scaleX = 1f + 0.03f * glow; scaleY = 1f + 0.08f * glow; alpha = 0.55f * glow }
                        .clip(RoundedCornerShape(27.dp)).background(HEART_RED.copy(alpha = 0.6f)))
                    Row(Modifier.matchParentSize().clip(RoundedCornerShape(27.dp))
                        .background(Brush.horizontalGradient(listOf(HEART_RED, HEART_PINK)))
                        .clickableNoRipple { nav.push(Overlay.CameraHr) }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Duo.Pulse, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check my heart · 15 s", style = FitType.label.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
                Box(Modifier.height(54.dp).clip(RoundedCornerShape(27.dp)).background(th.text.copy(alpha = 0.08f))
                    .border(1.dp, th.text.copy(alpha = 0.12f), RoundedCornerShape(27.dp)).clickableNoRipple(onLogBp).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center) { Text("Log BP", style = FitType.label, color = th.text) }
            }
            Text("Just your fingertip on the camera — no watch needed", style = FitType.caption, color = th.textFaint, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Small Home tile version: beating heart, last bpm, tap to check. */
@Composable
fun VitalsHeroTile(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val (bpm, _) = rememberLastPulse(container)
    val inf = rememberInfiniteTransition(label = "vtile")
    val sweep by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    Glass(Modifier.fillMaxSize(), tint = HEART_RED.copy(alpha = 0.16f), onClick = { nav.push(Overlay.CameraHr) }) {
        Canvas(Modifier.fillMaxWidth().height(36.dp).align(Alignment.BottomCenter).padding(bottom = 6.dp)) { ecgSweep(sweep, HEART_RED, beats = 2) }
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BeatingHeart(bpm ?: 72, 44.dp)
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(bpm?.let { "$it" } ?: "--", style = FitType.title, color = th.text)
                    Text("bpm", style = FitType.caption, color = th.textDim)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Tap · check in 15 s", style = FitType.caption.copy(fontWeight = FontWeight.Bold), color = HEART_PINK)
        }
    }
}
