package com.myfit.tracker.ui.arena

import android.content.Context
import com.myfit.tracker.ui.theme.awakeFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.ui.game.ChampStage
import com.myfit.tracker.ui.game.ChampStill
import com.myfit.tracker.ui.game.GameMode
import com.myfit.tracker.ui.game.StatsCache
import com.myfit.tracker.ui.pip.Champion
import com.myfit.tracker.ui.theme.FitType
import kotlin.math.cos
import kotlin.math.sin

/** Which Arena hero tiles are large (long-press toggles, remembered). */
private object TilePrefs {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("arena_tiles", Context.MODE_PRIVATE)
    fun large(c: Context, k: String) = p(c).getBoolean(k, false)
    fun set(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()
}

private const val SMALL_H = 196

/**
 * R28 Arena header: your buddy level tile and your champion tile, side by side when both are small. Long-press either
 * to make it full width (long-press again to shrink) — like Home cards.
 */
@Composable
fun ArenaHeroTiles(l: Level, partner: Mascot, onBuddy: () -> Unit, onChamp: () -> Unit,
                   buddy: @Composable (small: Boolean, l: Level, onLong: () -> Unit, onClick: () -> Unit) -> Unit) {
    // R32: both tiles always small, side by side (no resizing)
    Row(Modifier.fillMaxWidth().height(SMALL_H.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()) { buddy(true, l, {}, onBuddy) }
        Box(Modifier.weight(1f).fillMaxHeight()) { ChampionTile(false, {}, onChamp) }
    }
}

/** Phone tilt (-1..1 on each axis), smoothed — drives the holographic foil and the card's 3D lean. */
@Composable
private fun rememberTilt(): State<Offset> {
    val ctx = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val acc = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var x = 0f; var y = 0f
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                // gravity on x/y, relative to holding the phone upright at ~35°
                val tx = (-e.values[0] / 5f).coerceIn(-1f, 1f)
                val ty = ((e.values[1] - 7.5f) / 4f).coerceIn(-1f, 1f)
                x += (tx - x) * 0.12f; y += (ty - y) * 0.12f
                tilt.value = Offset(x, y)
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) = Unit
        }
        if (acc != null) sm.registerListener(l, acc, android.hardware.SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(l) }
    }
    return tilt
}

private val GRAIN2 = List(220) { Triple(kotlin.random.Random(it * 19 + 1).nextFloat(), kotlin.random.Random(it * 43 + 9).nextFloat(), kotlin.random.Random(it * 7 + 3).nextFloat()) }

/**
 * R30 champion tile — premium and quiet: a deep card tinted by the champion's colour, two soft light fields drifting
 * slowly behind them, a horizon glow, fine film grain, an occasional soft light sweep, and a thin edge light that
 * travels around the border. The phone's tilt adds a gentle parallax (background and champion move apart a little).
 * Same design small and large.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChampionTile(large: Boolean, onLong: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { GameMode.load(ctx) }
    val on by GameMode.on.collectAsState()
    val b by GameMode.champ.collectAsState()
    val stats by StatsCache.value.collectAsState()
    LaunchedEffect(on) {
        if (on) runCatching { StatsCache.value.value = com.myfit.tracker.ui.game.ChampStats.compute((ctx.applicationContext as com.myfit.tracker.MyFitApplication).container) }
    }
    val ch = b.champion ?: Champion.BLAZE
    val tilt by rememberTilt()
    val inf = rememberInfiniteTransition(label = "tile")
    val drift by inf.awakeFloat(0f, 1f, infiniteRepeatable(tween(22_000, easing = LinearEasing)), label = "drift")
    val edge by inf.awakeFloat(0f, 360f, infiniteRepeatable(tween(9_000, easing = LinearEasing)), label = "edge")
    val sweep by inf.awakeFloat(-0.6f, 2.4f, infiniteRepeatable(tween(7_500, easing = androidx.compose.animation.core.FastOutSlowInEasing)), label = "sweep")
    val breathe by inf.awakeFloat(0f, 1f, infiniteRepeatable(tween(3_200), RepeatMode.Reverse), label = "breathe", rest = 0.5f)
    val c1 = ch.color
    val c2 = ch.rarity.color
    val shape = RoundedCornerShape(26.dp)
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxSize().clip(shape)
        .background(Brush.verticalGradient(listOf(mixC(c1, Color(0xFF07080D), 0.80f), Color(0xFF07080D))))
        .combinedClickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
            onClick = { if (on) onClick() else GameMode.setOn(ctx, true) }, onLongClick = onLong)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height; val tau = 6.2832f
            val px = tilt.x * 10.dp.toPx(); val py = tilt.y * 6.dp.toPx()
            fun field(cx: Float, cy: Float, r: Float, c: Color, a: Float) =
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), c.copy(alpha = a * 0.3f), Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
            field(w * (0.30f + 0.10f * sin(drift * tau)) + px, h * (0.28f + 0.06f * cos(drift * tau)) + py, h * 0.85f, c1, 0.34f)
            field(w * (0.78f + 0.08f * cos(drift * tau + 1f)) + px, h * (0.62f + 0.07f * sin(drift * tau + 2f)) + py, h * 0.70f, c2, 0.18f)
            // horizon glow behind the platform
            val hy = h * (if (large) 0.84f else 0.80f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, c1.copy(alpha = 0.16f + 0.06f * breathe), Color.Transparent), hy - h * 0.16f, hy + h * 0.10f))
            // film grain
            GRAIN2.forEach { (x, y, k) -> drawCircle(Color.White.copy(alpha = 0.025f + 0.03f * k), 0.55.dp.toPx(), Offset(x * w, y * h)) }
            // soft light sweep (one pass, then rests off-card)
            val sx = sweep * w
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent), Offset(sx - w * 0.35f, 0f), Offset(sx, h)))
            // vignette for depth and legibility
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.45f))))
        }
        // champion on the platform, with a touch of parallax against the background
        Box(Modifier.fillMaxSize().padding(top = if (large) 14.dp else 46.dp, bottom = if (large) 14.dp else 38.dp)
            .graphicsLayer { translationX = -tilt.x * with(density) { 5.dp.toPx() } }) {
            Box(Modifier.fillMaxHeight().then(if (large) Modifier.fillMaxWidth(0.55f) else Modifier.fillMaxWidth())) {
                com.myfit.tracker.ui.coach.OnPlatform(c1, Modifier.fillMaxSize()) {
                    if (on) ChampStage(b, "idle", Modifier.fillMaxSize()) else ChampStill(b, Modifier.fillMaxSize())
                }
            }
        }
        // type
        Column(Modifier.align(if (large) Alignment.TopEnd else Alignment.TopStart).padding(14.dp), horizontalAlignment = if (large) Alignment.End else Alignment.Start) {
            Text(ch.rarity.label.uppercase() + " · " + b.tier.label.uppercase(), style = FitType.overline.copy(fontSize = 9.sp, letterSpacing = 1.6.sp), color = c1.copy(alpha = 0.95f), maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(ch.tag, style = FitType.label.copy(fontSize = if (large) 30.sp else 20.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp), color = Color.White, maxLines = 1)
            if (large) Text(if (on) ch.archetype else "Game Mode is off", style = FitType.caption, color = Color.White.copy(alpha = 0.7f))
        }
        Row(Modifier.align(if (large) Alignment.BottomEnd else Alignment.BottomCenter).padding(12.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (on) {
                Text("${stats.rating}", style = FitType.caption.copy(fontWeight = FontWeight.Black), color = Color.White)
                Text("  RATING", style = FitType.overline.copy(fontSize = 8.sp), color = Color.White.copy(alpha = 0.6f))
                if (large) Text("   ·   Battle", style = FitType.caption.copy(fontWeight = FontWeight.Bold), color = c1)
            } else Text("Tap to turn on", style = FitType.caption.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        // hairline border + an edge light travelling around it
        Canvas(Modifier.matchParentSize()) {
            val r = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx())
            drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            val cols = intArrayOf(0x00FFFFFF, 0x00FFFFFF, c1.copy(alpha = 0.9f).toArgbInt(), 0xCCFFFFFF.toInt(), 0x00FFFFFF, 0x00FFFFFF)
            val pos = floatArrayOf(0f, 0.62f, 0.72f, 0.75f, 0.80f, 1f)
            val sg = android.graphics.SweepGradient(size.width / 2, size.height / 2, cols, pos)
            sg.setLocalMatrix(android.graphics.Matrix().apply { setRotate(edge, size.width / 2, size.height / 2) })
            drawRoundRect(androidx.compose.ui.graphics.ShaderBrush(sg), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
        }
    }
}

private fun Color.toArgbInt(): Int = this.toArgb()

private fun mixC(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
