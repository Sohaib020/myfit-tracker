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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.border
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

// ------------------------------------------------------------------ R36 Target muscles

/** Coarse filter group → anatomy view + highlighted muscles for its thumbnail. */
private val TARGET_ART: List<Triple<String, String, List<String>>> = listOf(
    Triple(MuscleGroup.CHEST, "front", listOf("chest")),
    Triple(MuscleGroup.BACK, "back", listOf("lats", "middle back", "traps")),
    Triple(MuscleGroup.SHOULDERS, "front", listOf("shoulders")),
    Triple(MuscleGroup.BICEPS, "front", listOf("biceps")),
    Triple(MuscleGroup.TRICEPS, "back", listOf("triceps")),
    Triple(MuscleGroup.CORE, "front", listOf("abdominals")),
    Triple(MuscleGroup.GLUTES, "back", listOf("glutes")),
    Triple(MuscleGroup.LEGS, "front", listOf("quadriceps", "calves")),
)

/** Bounding box of the highlighted pixels (cached), used to zoom each thumbnail onto its muscle. */
private object MuscleBox {
    private val cache = HashMap<String, android.graphics.RectF>()
    fun of(bmps: List<ImageBitmap>, key: String): android.graphics.RectF = cache.getOrPut(key) {
        var l = Int.MAX_VALUE; var t = Int.MAX_VALUE; var r = -1; var b = -1
        bmps.forEach { ib ->
            val bm = ib.asAndroidBitmap(); val w = bm.width; val h = bm.height
            val px = IntArray(w); var y = 0
            while (y < h) {
                bm.getPixels(px, 0, w, 0, y, w, 1)
                var x = 0
                while (x < w) { if ((px[x] ushr 24) > 60) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }; x += 3 }
                y += 3
            }
        }
        if (r < 0) android.graphics.RectF(0f, 0f, 480f, 800f) else {
            // square around the muscle with breathing room
            val cx = (l + r) / 2f; val cy = (t + b) / 2f
            val side = maxOf(r - l, b - t) * 1.55f + 40f
            android.graphics.RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        }
    }
}

@Composable
private fun TargetThumb(group: String, view: String, muscles: List<String>, selected: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val imgs by produceState<Pair<ImageBitmap?, List<ImageBitmap>>?>(null, view, muscles) {
        value = MapImages.get(ctx, view) to muscles.mapNotNull { MapImages.get(ctx, view + "_" + it.replace(' ', '_')) }
    }
    val ring by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, com.myfit.tracker.ui.theme.Motion.snappy(), label = "ring")
    val accent = th.accentBright
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 8.dp).clickableNoRipple(onClick)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.34f))
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(th.text.copy(alpha = 0.16f), th.text.copy(alpha = 0.05f))))
            .then(if (ring > 0.01f) Modifier.border(androidx.compose.foundation.BorderStroke((2.5f * ring).dp, accent), RoundedCornerShape(size * 0.34f)) else Modifier)) {
            val p = imgs
            if (p?.first != null) {
                val box = MuscleBox.of(p.second, view + muscles.joinToString())
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val so = androidx.compose.ui.unit.IntOffset(box.left.toInt(), box.top.toInt())
                    val ss = androidx.compose.ui.unit.IntSize(box.width().toInt(), box.height().toInt())
                    val ds = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt())
                    // srcOffset may run past the bitmap edge: clamp by drawing into a matching sub-rect
                    fun draw(ib: ImageBitmap, a: Float) {
                        val l = so.x.coerceAtLeast(0); val t = so.y.coerceAtLeast(0)
                        val r = (so.x + ss.width).coerceAtMost(ib.width); val b = (so.y + ss.height).coerceAtMost(ib.height)
                        if (r <= l || b <= t) return
                        val sx = ds.width / ss.width.toFloat(); val sy = ds.height / ss.height.toFloat()
                        drawImage(ib, androidx.compose.ui.unit.IntOffset(l, t), androidx.compose.ui.unit.IntSize(r - l, b - t),
                            androidx.compose.ui.unit.IntOffset(((l - so.x) * sx).toInt(), ((t - so.y) * sy).toInt()),
                            androidx.compose.ui.unit.IntSize(((r - l) * sx).toInt(), ((b - t) * sy).toInt()), alpha = a)
                    }
                    draw(p.first!!, 1f)
                    p.second.forEach { draw(it, 1f) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(group, style = FitType.label, color = if (selected) accent else th.text, maxLines = 1)
    }
}

/** "Target muscles" strip for the exercise library: tap a muscle to filter, tap again for all. */
@Composable
fun TargetMuscleRow(selected: String?, onSelect: (String?) -> Unit) {
    val th = LocalFitTheme.current
    Column {
        Text("TARGET MUSCLES", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(TARGET_ART.size) { i ->
                val (g, v, m) = TARGET_ART[i]
                TargetThumb(g, v, m, selected == g, 78.dp) { onSelect(if (selected == g) null else g) }
            }
            items(listOf(MuscleGroup.CARDIO to Duo.DirectionsRun, MuscleGroup.OTHER to Duo.SportsGymnastics).size) { i ->
                val (g, icon) = listOf(MuscleGroup.CARDIO to Duo.DirectionsRun, MuscleGroup.OTHER to Duo.SportsGymnastics)[i]
                val sel = selected == g
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(86.dp).clickableNoRipple { onSelect(if (sel) null else g) }) {
                    Box(Modifier.size(78.dp).clip(RoundedCornerShape(26.dp)).background(if (sel) th.accent.copy(alpha = 0.25f) else th.text.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = if (sel) th.accentBright else th.textDim, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(g, style = FitType.label, color = if (sel) th.accentBright else th.text, maxLines = 1)
                }
            }
        }
    }
}
