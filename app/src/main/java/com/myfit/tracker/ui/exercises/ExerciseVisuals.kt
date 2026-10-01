package com.myfit.tracker.ui.exercises

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.data.db.Exercise
import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.MuscleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Decoded exercise photos, bounded by memory (bitmaps are ~480 px wide WebP). */
object ExerciseImages {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(context: Context, key: String, frame: Int): ImageBitmap? {
        val id = "$key/$frame"
        cache.get(id)?.let { return it.asImageBitmap() }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("exercises/$key/$frame.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.also { cache.put(id, it) }?.asImageBitmap()
        }
    }
}

/**
 * Exercise photo. With `animate = true` it alternates the start and end frames, so the card shows
 * the movement. Custom exercises (no photo) get a muscle-coloured icon tile instead.
 */
@Composable
fun ExerciseImage(ex: Exercise, modifier: Modifier = Modifier, animate: Boolean = false, periodMs: Long = 1100) {
    val ctx = LocalContext.current
    val key = ex.imageKey
    if (key == null || ex.imageFrames == 0) {
        val c = muscleColor(ex.primaryMuscle)
        Box(
            modifier.background(Brush.linearGradient(listOf(c.copy(alpha = 0.85f), c.copy(alpha = 0.4f)))),
            contentAlignment = Alignment.Center,
        ) { Icon(equipmentIcon(ex), null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        return
    }
    var frame by remember(key) { mutableIntStateOf(0) }
    if (animate && ex.imageFrames > 1) {
        LaunchedEffect(key) { while (true) { delay(periodMs); frame = 1 - frame } }
    }
    val f0 by produceState<ImageBitmap?>(null, key) { value = ExerciseImages.load(ctx, key, 0) }
    val f1 by produceState<ImageBitmap?>(null, key, animate) { if (animate && ex.imageFrames > 1) value = ExerciseImages.load(ctx, key, 1) }
    Box(modifier.background(Color.White)) {
        Crossfade(targetState = if (frame == 1 && f1 != null) f1 else f0, animationSpec = tween(350), label = "exFrame") { img ->
            if (img != null) Image(img, ex.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

fun muscleColor(group: String): Color = when (group) {
    MuscleGroup.CHEST -> Color(0xFFFF5A5F)
    MuscleGroup.BACK -> Color(0xFF3FA9FF)
    MuscleGroup.SHOULDERS -> Color(0xFFFFA93B)
    MuscleGroup.BICEPS -> Color(0xFFB57CFF)
    MuscleGroup.TRICEPS -> Color(0xFFFF6FB5)
    MuscleGroup.LEGS -> Color(0xFF2FD37A)
    MuscleGroup.GLUTES -> Color(0xFFFF7A59)
    MuscleGroup.CORE -> Color(0xFFFFD34D)
    MuscleGroup.CARDIO -> Color(0xFF22D3EE)
    else -> Color(0xFF9AA0AE)
}

fun equipmentIcon(ex: Exercise): ImageVector = when {
    ex.measurementType == MeasurementType.DISTANCE_DURATION || ex.primaryMuscle == MuscleGroup.CARDIO -> Duo.DirectionsRun
    ex.category == "stretching" -> Duo.SelfImprovement
    else -> when (ex.equipment) {
        "barbell", "e-z curl bar", "dumbbell", "kettlebells" -> Duo.FitnessCenter
        "cable" -> Duo.Cable
        "machine" -> Duo.PrecisionManufacturing
        "body only" -> Duo.AccessibilityNew
        "bands" -> Duo.LinearScale
        "medicine ball", "exercise ball" -> Duo.SportsBaseball
        else -> Duo.SportsGymnastics
    }
}

fun equipmentLabel(e: String) = when (e) {
    "body only" -> "Bodyweight"; "e-z curl bar" -> "EZ bar"; "kettlebells" -> "Kettlebell"
    else -> e.replaceFirstChar { it.uppercase() }
}

fun measurementLabel(m: String) = when (m) {
    MeasurementType.WEIGHT_REPS -> "Weight × reps"
    MeasurementType.BODYWEIGHT_REPS -> "Reps (+ optional added weight)"
    MeasurementType.ASSISTED_REPS -> "Reps with assistance weight"
    MeasurementType.REPS_ONLY -> "Reps only"
    MeasurementType.DURATION -> "Duration"
    MeasurementType.DISTANCE_DURATION -> "Distance + duration"
    MeasurementType.WEIGHT_DURATION -> "Weight + duration"
    else -> m
}

val allMeasurementTypes = listOf(
    MeasurementType.WEIGHT_REPS, MeasurementType.BODYWEIGHT_REPS, MeasurementType.ASSISTED_REPS, MeasurementType.REPS_ONLY,
    MeasurementType.DURATION, MeasurementType.DISTANCE_DURATION, MeasurementType.WEIGHT_DURATION,
)

val equipmentOptions = listOf("barbell", "dumbbell", "machine", "cable", "body only", "kettlebells", "bands", "e-z curl bar", "medicine ball", "exercise ball", "other")

/** Words-in-any-order search: "bench db" matches "Dumbbell Bench Press". */
fun Exercise.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val hay = (name + " " + primaryMuscle + " " + secondaryMuscles + " " + equipment).lowercase()
        .replace("dumbbell", "dumbbell db").replace("barbell", "barbell bb")
    return query.lowercase().split(' ', '-', ',').filter { it.isNotBlank() }.all { hay.contains(it) }
}
