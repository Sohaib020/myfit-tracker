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

private val SPARKS = List(46) { Triple(kotlin.random.Random(it * 31).nextFloat(), kotlin.random.Random(it * 77).nextFloat(), kotlin.random.Random(it * 13).nextFloat()) }

/**
 * The champion tile: an arena at night — rotating stadium light beams, a glowing floor ring that pulses, sparks rising
 * through the beams, and your champion standing in the spotlight. Different from the buddy tile's soft aurora.
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
    val inf = rememberInfiniteTransition(label = "arena")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "pulse")
    val rise by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "rise")
    val c1 = ch.color; val c2 = Color(0xFF6A5CFF)
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Color(0xFF07080F))
        .border(1.dp, Brush.verticalGradient(listOf(c1.copy(alpha = 0.7f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(28.dp))
        .combinedClickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
            onClick = { if (on) onClick() else GameMode.setOn(ctx, true) }, onLongClick = onLong)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val floor = Offset(w * (if (large) 0.28f else 0.5f), h * 0.86f)
            // stadium light beams sweeping from above the champion
            rotate(spin, Offset(floor.x, -h * 0.25f)) {
                for (i in 0 until 6) {
                    val a = Math.toRadians(i * 60.0).toFloat()
                    val tip = Offset(floor.x + cos(a) * h * 1.6f, -h * 0.25f + sin(a) * h * 1.6f)
                    drawLine(Brush.linearGradient(listOf((if (i % 2 == 0) c1 else c2).copy(alpha = 0.22f), Color.Transparent), Offset(floor.x, -h * 0.25f), tip),
                        Offset(floor.x, -h * 0.25f), tip, strokeWidth = w * 0.16f)
                }
            }
            // spotlight cone + glow on the champion
            drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.45f + 0.15f * pulse), Color.Transparent), Offset(floor.x, h * 0.55f), h * 0.6f), h * 0.6f, Offset(floor.x, h * 0.55f))
            // pulsing floor ring
            val rw = (if (large) w * 0.36f else w * 0.78f) * (0.92f + 0.08f * pulse)
            drawOval(Brush.radialGradient(listOf(c1.copy(alpha = 0.55f), Color.Transparent), floor, rw / 2), Offset(floor.x - rw / 2, floor.y - rw * 0.11f), Size(rw, rw * 0.22f))
            drawOval(c1.copy(alpha = 0.85f), Offset(floor.x - rw * 0.42f, floor.y - rw * 0.08f), Size(rw * 0.84f, rw * 0.16f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            // sparks rising through the beams
            SPARKS.forEach { (x, y, k) ->
                val yy = ((y - rise * (0.5f + k)) % 1f + 1f) % 1f
                drawCircle((if (k > 0.5f) c1 else Color.White).copy(alpha = 0.25f + 0.5f * k * (1f - yy)), (1.2f + 2f * k).dp.toPx(), Offset(x * w, yy * h))
            }
        }
        val stage = Modifier.fillMaxHeight().padding(top = if (large) 14.dp else 36.dp, bottom = 10.dp)
        if (large) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(0.56f).then(stage)) {
                    if (on) ChampStage(b, "idle", Modifier.fillMaxSize()) else ChampStill(b, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(0.6f).fillMaxHeight().padding(top = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("CHAMPION", style = FitType.overline, color = c1)
                        Text(ch.tag, style = FitType.hero.copy(fontSize = 34.sp, lineHeight = 36.sp), color = Color.White)
                        Text(if (on) "${b.tier.label} · Rating ${stats.rating}" else "Game Mode is off", style = FitType.label, color = Color.White.copy(alpha = 0.8f))
                    }
                    Box(Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(c1, c2))).padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text(if (on) "Battle · Looks · Roster" else "Turn on Game Mode", style = FitType.label.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().then(stage)) {
                if (on) ChampStage(b, "idle", Modifier.fillMaxSize()) else ChampStill(b, Modifier.fillMaxSize())
            }
            Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Text("CHAMPION", style = FitType.overline, color = c1)
                Text(ch.tag, style = FitType.metric.copy(fontSize = 22.sp, lineHeight = 24.sp), color = Color.White, maxLines = 1)
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(if (on) "Rating ${stats.rating} · Battle" else "Tap to turn on", style = FitType.caption.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
        }
    }
}
