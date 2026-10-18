package com.myfit.tracker.ui.arena

import android.content.Context
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
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var bigBuddy by remember { mutableStateOf(TilePrefs.large(ctx, "buddy")) }
    var bigChamp by remember { mutableStateOf(TilePrefs.large(ctx, "champ")) }
    val toggleB = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); bigBuddy = !bigBuddy; TilePrefs.set(ctx, "buddy", bigBuddy) }
    val toggleC = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); bigChamp = !bigChamp; TilePrefs.set(ctx, "champ", bigChamp) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !bigBuddy && !bigChamp -> Row(Modifier.fillMaxWidth().height(SMALL_H.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { buddy(true, l, toggleB, onBuddy) }
                Box(Modifier.weight(1f).fillMaxHeight()) { ChampionTile(false, toggleC, onChamp) }
            }
            else -> {
                if (bigBuddy) buddy(false, l, toggleB, onBuddy)
                else Row(Modifier.fillMaxWidth().height(SMALL_H.dp)) { Box(Modifier.weight(1f).fillMaxHeight()) { buddy(true, l, toggleB, onBuddy) }; Spacer(Modifier.weight(1f)) }
                if (bigChamp) Box(Modifier.fillMaxWidth().height(220.dp)) { ChampionTile(true, toggleC, onChamp) }
                else Row(Modifier.fillMaxWidth().height(SMALL_H.dp)) { Box(Modifier.weight(1f).fillMaxHeight()) { ChampionTile(false, toggleC, onChamp) }; Spacer(Modifier.weight(1f)) }
            }
        }
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

/** AGSL holographic foil: thin-film rainbow bands + diffraction lines that slide as you tilt, with a moving glare. */
private object HoloShader {
    private const val SRC = """
uniform float2 res; uniform float t; uniform float2 tilt;
half4 main(float2 p) {
    float2 uv = p / res;
    float d = uv.x * 0.8 + uv.y * 1.1 + tilt.x * 0.7 - tilt.y * 0.5;
    float3 c = 0.55 + 0.45 * cos(6.28318 * (d * 1.4 + t * 0.04 + float3(0.0, 0.33, 0.67)));
    float lines = 0.5 + 0.5 * sin(uv.x * 210.0 + uv.y * 90.0 + tilt.x * 24.0 + t * 0.6);
    c *= 0.85 + 0.3 * lines;
    float g = 1.0 - abs((uv.x - 0.5 - tilt.x * 0.55) + (uv.y - 0.5 + tilt.y * 0.45) * 0.55);
    float glare = smoothstep(0.82, 1.0, g);
    float a = 0.30 + 0.18 * lines + glare * 0.35;
    return half4(half3(c * a + glare * 0.45), half(a));
}
"""
    private var shader: Any? = null
    private var brush: androidx.compose.ui.graphics.ShaderBrush? = null
    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, t: Float, tilt: Offset): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 33 || com.myfit.tracker.CrashGuard.safeMode) return false
        return runCatching {
            val s = (shader as? android.graphics.RuntimeShader) ?: android.graphics.RuntimeShader(SRC).also { shader = it; brush = androidx.compose.ui.graphics.ShaderBrush(it) }
            s.setFloatUniform("res", scope.size.width, scope.size.height)
            s.setFloatUniform("t", t)
            s.setFloatUniform("tilt", tilt.x, tilt.y)
            scope.drawRect(brush!!)
            true
        }.getOrDefault(false)
    }
}

private val GLITTER = List(70) { Triple(kotlin.random.Random(it * 31 + 7).nextFloat(), kotlin.random.Random(it * 57 + 3).nextFloat(), kotlin.random.Random(it * 11 + 5).nextFloat()) }

/**
 * R29 champion tile: a holographic trading card. Iridescent foil and diffraction lines that shift with the phone's
 * tilt, a glare that follows your hand, twinkling glitter, a rotating neon edge and a slight 3D lean — your champion
 * stands on a glowing platform in the middle. Same design small and large.
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
    val inf = rememberInfiniteTransition(label = "holo")
    val t by inf.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "t")
    val edge by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(6_000, easing = LinearEasing)), label = "edge")
    val twinkle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2_400, easing = LinearEasing)), label = "tw")
    val c1 = ch.color
    val rarity = ch.rarity
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxSize().graphicsLayer {
        rotationY = tilt.x * 9f; rotationX = -tilt.y * 7f; cameraDistance = 14f * density
    }.clip(shape).background(Brush.verticalGradient(listOf(mixC(c1, Color(0xFF0A0B14), 0.72f), Color(0xFF07080F))))
        .combinedClickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
            onClick = { if (on) onClick() else GameMode.setOn(ctx, true) }, onLongClick = onLong)) {
        // foil + glitter + glare
        Canvas(Modifier.matchParentSize()) {
            if (!HoloShader.draw(this, t, tilt)) {
                val sx = (0.5f + tilt.x * 0.5f) * size.width
                drawRect(Brush.linearGradient(listOf(Color(0x5562F7E4), Color(0x55B67CFF), Color(0x55FF8AD8), Color(0x55FFE07C), Color(0x5562F7E4)),
                    Offset(sx - size.width, 0f), Offset(sx + size.width, size.height)))
            }
            GLITTER.forEach { (x, y, k) ->
                val a = (0.5f + 0.5f * sin((twinkle + k) * 6.283f * 2f + tilt.x * 3f)).let { it * it }
                drawCircle(Color.White.copy(alpha = 0.75f * a), (0.8f + 1.6f * k).dp.toPx(), Offset(x * size.width, y * size.height))
            }
        }
        // the champion on a platform
        Box(Modifier.fillMaxSize().padding(top = if (large) 12.dp else 30.dp, bottom = if (large) 12.dp else 34.dp)
            .then(if (large) Modifier.padding(end = 0.dp) else Modifier)) {
            Box(Modifier.fillMaxHeight().then(if (large) Modifier.fillMaxWidth(0.55f) else Modifier.fillMaxWidth())) {
                com.myfit.tracker.ui.coach.OnPlatform(c1, Modifier.fillMaxSize()) {
                    if (on) ChampStage(b, "idle", Modifier.fillMaxSize()) else ChampStill(b, Modifier.fillMaxSize())
                }
            }
        }
        // rarity chip + name
        Column(Modifier.align(if (large) Alignment.TopEnd else Alignment.TopStart).padding(12.dp), horizontalAlignment = if (large) Alignment.End else Alignment.Start) {
            Box(Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(rarity.color, c1))).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(rarity.label.uppercase(), style = FitType.overline.copy(fontSize = 9.sp), color = Color.White)
            }
            Spacer(Modifier.height(4.dp))
            Text(ch.tag, style = (if (large) FitType.hero.copy(fontSize = 34.sp, lineHeight = 36.sp) else FitType.metric.copy(fontSize = 20.sp, lineHeight = 22.sp)), color = Color.White, maxLines = 1)
            if (large) Text(if (on) "${b.tier.label} · Rating ${stats.rating}" else "Game Mode is off", style = FitType.label, color = Color.White.copy(alpha = 0.85f))
        }
        // glass footer
        Box(Modifier.align(if (large) Alignment.BottomEnd else Alignment.BottomCenter).padding(10.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f)).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(if (on) (if (large) "Battle · Looks · Roster" else "★ ${stats.rating} · Battle") else "Tap to turn on", style = FitType.caption.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
        // rotating neon edge
        Canvas(Modifier.matchParentSize()) {
            val cols = intArrayOf(c1.toArgbInt(), 0xFF7CF7D4.toInt(), 0xFFB67CFF.toInt(), 0xFFFF8AD8.toInt(), c1.toArgbInt())
            val sg = android.graphics.SweepGradient(size.width / 2, size.height / 2, cols, null)
            sg.setLocalMatrix(android.graphics.Matrix().apply { setRotate(edge, size.width / 2, size.height / 2) })
            drawRoundRect(androidx.compose.ui.graphics.ShaderBrush(sg), cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.2.dp.toPx()))
        }
    }
}

private fun Color.toArgbInt(): Int = this.toArgb()

private fun mixC(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
