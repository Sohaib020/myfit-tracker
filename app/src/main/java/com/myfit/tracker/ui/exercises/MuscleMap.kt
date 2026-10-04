package com.myfit.tracker.ui.exercises

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.MuscleGroup
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** The 17 muscle groups the anatomy art knows (same names as the exercise catalog, lower-case). */
val MAP_GROUPS = listOf("abdominals", "abductors", "adductors", "biceps", "calves", "chest", "forearms", "glutes", "hamstrings",
    "lats", "lower back", "middle back", "neck", "quadriceps", "shoulders", "traps", "triceps")
private val BACK_FIRST = setOf("lats", "middle back", "lower back", "traps", "glutes", "hamstrings", "calves", "triceps")

/** Detailed primary/secondary muscles: from the bundled catalog (by image key), else from the coarse group. */
object MuscleData {
    private var byKey: Map<String, Pair<List<String>, List<String>>>? = null
    private fun load(c: Context): Map<String, Pair<List<String>, List<String>>> = byKey ?: runCatching {
        val a = JSONArray(c.assets.open("exercise_catalog.json").bufferedReader().use { it.readText() })
        (0 until a.length()).associate { i ->
            val o = a.getJSONObject(i)
            val s = o.optJSONArray("s")
            o.getString("k") to (listOf(o.optString("pm").lowercase()) to (0 until (s?.length() ?: 0)).map { s!!.getString(it).lowercase() })
        }
    }.getOrDefault(emptyMap()).also { byKey = it }

    fun of(c: Context, e: Exercise): Pair<List<String>, List<String>> {
        e.imageKey?.let { k -> load(c)[k]?.let { return it } }
        val prim = when (e.primaryMuscle) {
            MuscleGroup.CHEST -> listOf("chest"); MuscleGroup.BACK -> listOf("lats", "middle back"); MuscleGroup.SHOULDERS -> listOf("shoulders")
            MuscleGroup.BICEPS -> listOf("biceps"); MuscleGroup.TRICEPS -> listOf("triceps"); MuscleGroup.LEGS -> listOf("quadriceps", "hamstrings")
            MuscleGroup.GLUTES -> listOf("glutes"); MuscleGroup.CORE -> listOf("abdominals"); else -> emptyList()
        }
        val sec = e.secondaryMuscles.split(',').map { it.trim().lowercase() }.filter { it in MAP_GROUPS && it !in prim }
        return prim to sec
    }
}

private object MapImages {
    private val cache = android.util.LruCache<String, ImageBitmap>(12 * 1024 * 1024)
    suspend fun get(c: Context, name: String): ImageBitmap? = cache.get(name) ?: withContext(Dispatchers.IO) {
        runCatching { c.assets.open("anatomy/$name.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
    }?.also { cache.put(name, it) }
}

@Composable
private fun MapLayer(name: String, alpha: Float, modifier: Modifier) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, name) { value = MapImages.get(ctx, name) }
    img?.let { Image(it, null, modifier, alpha = alpha, contentScale = ContentScale.Fit) }
}

/** Front + back anatomy with primary muscles in red and secondary in light red. */
@Composable
fun MuscleMap(primary: List<String>, secondary: List<String>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 260.dp) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("front", "back").forEach { view ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val m = Modifier.fillMaxSize()
                    MapLayer(view, 1f, m)
                    secondary.forEach { g -> MapLayer(view + "_" + g.replace(' ', '_'), 0.42f, m) }
                    primary.forEach { g -> MapLayer(view + "_" + g.replace(' ', '_'), 1f, m) }
                    Text(view.replaceFirstChar { it.uppercase() }, style = FitType.overline, color = th.textDim, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            primary.forEach { MuscleChip(it, Color(0xFFE0442A), true) }
            secondary.forEach { MuscleChip(it, Color(0xFFE0442A), false) }
        }
    }
}

@Composable
private fun MuscleChip(name: String, c: Color, primary: Boolean) {
    val th = LocalFitTheme.current
    Row(Modifier.clip(CircleShape).background(c.copy(alpha = if (primary) 0.22f else 0.10f)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c.copy(alpha = if (primary) 1f else 0.45f)))
        Spacer(Modifier.width(6.dp))
        Text(name.replaceFirstChar { it.uppercase() } + if (primary) "" else " · secondary", style = FitType.caption, color = th.text)
    }
}

/** How-to media: the exercise photos crossfading smoothly, on a white card with play/pause. */
@Composable
fun ExerciseMedia(ex: Exercise, modifier: Modifier = Modifier) {
    var playing by remember { mutableStateOf(true) }
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Color.White)) {
        PhotoLoop(ex, playing, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomEnd).padding(14.dp).size(46.dp).clip(CircleShape).background(Color(0xFF6E6E73)).clickableNoRipple { playing = !playing }, contentAlignment = Alignment.Center) {
            if (playing) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(2) { Box(Modifier.width(4.dp).height(16.dp).background(Color.White)) } }
            else Icon(Duo.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PhotoLoop(ex: Exercise, playing: Boolean, modifier: Modifier) {
    val ctx = LocalContext.current
    val key = ex.imageKey
    if (key == null || ex.imageFrames == 0) { ExerciseImage(ex, modifier); return }
    val frames by produceState<List<ImageBitmap>>(emptyList(), key) {
        value = (0 until ex.imageFrames.coerceAtMost(4)).mapNotNull { ExerciseImages.load(ctx, key, it) }
    }
    var i by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, playing, frames.size) { while (playing && frames.size > 1) { delay(1300); i = (i + 1) % frames.size } }
    Crossfade(frames.getOrNull(i), animationSpec = tween(650), label = "loop", modifier = modifier) { img ->
        if (img != null) Image(img, ex.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

@Suppress("unused") private val keep = BACK_FIRST
