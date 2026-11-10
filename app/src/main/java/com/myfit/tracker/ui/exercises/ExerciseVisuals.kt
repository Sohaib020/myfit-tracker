package com.myfit.tracker.ui.exercises

import com.myfit.tracker.ui.theme.Duo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.MuscleGroup
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Decoded exercise photos, bounded by memory (bitmaps are ~480 px wide WebP). */
object ExerciseImages {
    private val cache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(k: String, v: Bitmap) = v.allocationByteCount
    }

    suspend fun start(c: android.content.Context, ex: Exercise): ImageBitmap? = get(c, ex, 0)
    suspend fun end(c: android.content.Context, ex: Exercise): ImageBitmap? = get(c, ex, 1)

    private suspend fun get(c: android.content.Context, ex: Exercise, idx: Int): ImageBitmap? {
        val key = "${ex.id}_$idx"
        return cache.get(key)?.asImageBitmap() ?: withContext(Dispatchers.IO) {
            runCatching {
                c.assets.open("exercises/${ex.id}_$idx.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }?.also { cache.put(key, it) }?.asImageBitmap()
    }
}

/** Exercise card media: two-frame loop if animated=true, otherwise just the start frame. */
@Composable
fun ExerciseImage(e: Exercise, modifier: Modifier = Modifier, animate: Boolean, periodMs: Long = 1000L) {
    val th = LocalFitTheme.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val start by produceState<ImageBitmap?>(null, e.id) { value = ExerciseImages.start(ctx, e) }
    val end by produceState<ImageBitmap?>(null, e.id) { value = ExerciseImages.end(ctx, e) }
    var frame by remember(e.id) { mutableIntStateOf(0) }
    LaunchedEffect(animate, e.id) {
        if (animate) while (true) { delay(periodMs); frame = 1 - frame }
    }
    val img = if (frame == 0 || !animate) start else end
    Box(modifier.background(Brush.verticalGradient(listOf(th.text.copy(alpha = 0.08f), th.text.copy(alpha = 0.02f)))), contentAlignment = Alignment.Center) {
        if (img != null) Crossfade(img, label = "exImg", animationSpec = tween(if (animate) 200 else 0)) {
            Image(it, e.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/** Same as ExerciseImage but without the animate param, for overlays that show static frames. */
@Composable
fun ExerciseMedia(e: Exercise, modifier: Modifier = Modifier) = ExerciseImage(e, modifier, animate = true, periodMs = 1200L)

// ------------------------------------------------------------------ Equipment icons

fun equipmentIcon(e: Exercise): ImageVector = when (e.equipment) {
    "barbell", "e-z curl bar", "dumbbell", "kettlebells" -> Duo.FitnessCenter
    "cable"       -> Duo.Cable
    "machine"     -> Duo.PrecisionManufacturing
    "body only"   -> Duo.AccessibilityNew
    "bands"       -> Duo.LinearScale
    "medicine ball", "exercise ball" -> Duo.SportsBaseball
    else          -> Duo.SportsGymnastics
}

fun equipmentLabel(e: String): String = when (e) {
    "body only"    -> "Bodyweight"
    "e-z curl bar" -> "EZ Bar"
    "kettlebells"  -> "Kettlebell"
    "medicine ball" -> "Medicine Ball"
    "exercise ball" -> "Stability Ball"
    "foam roll"    -> "Foam Roller"
    else           -> e.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

val equipmentOptions = listOf(
    "barbell", "dumbbell", "cable", "machine", "body only",
    "kettlebells", "bands", "medicine ball", "exercise ball",
)

// ------------------------------------------------------------------ Measurement type labels

fun measurementLabel(m: String): String = when (m) {
    MeasurementType.WEIGHT_REPS -> "Weight × Reps"
    MeasurementType.BODYWEIGHT_REPS -> "Bodyweight reps"
    MeasurementType.REPS_ONLY -> "Reps only"
    MeasurementType.DURATION -> "Duration"
    MeasurementType.WEIGHT_DURATION -> "Weight · Duration"
    MeasurementType.DISTANCE_DURATION -> "Distance · Duration"
    MeasurementType.ASSISTED_REPS -> "Assisted reps"
    else -> m.replaceFirstChar { it.uppercase() }
}
