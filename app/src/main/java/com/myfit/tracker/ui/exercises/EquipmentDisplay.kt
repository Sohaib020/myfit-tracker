package com.myfit.tracker.ui.exercises

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Equipment image asset loader
// ---------------------------------------------------------------------------

internal object EquipmentImageLoader {
    private val cache = android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024)

    suspend fun load(ctx: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("equipment/$name.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.asImageBitmap()
        }?.also { cache.put(name, it) }
    }
}

// ---------------------------------------------------------------------------
// Muscle image asset loader
// ---------------------------------------------------------------------------

internal object MuscleImageLoader {
    private val cache = android.util.LruCache<String, ImageBitmap>(16 * 1024 * 1024)

    suspend fun load(ctx: Context, name: String): ImageBitmap? {
        cache.get(name)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                ctx.assets.open("muscles/$name.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.asImageBitmap()
        }?.also { cache.put(name, it) }
    }
}

// ---------------------------------------------------------------------------
// Icon fallback helper (takes String, not Exercise)
// ---------------------------------------------------------------------------

/** Returns a suitable icon for a raw equipment string. */
private fun equipmentIconForString(equipment: String): ImageVector = when (equipment) {
    "barbell", "e-z curl bar", "dumbbell", "kettlebells" -> Duo.FitnessCenter
    "cable"       -> Duo.Cable
    "machine"     -> Duo.PrecisionManufacturing
    "body only"   -> Duo.AccessibilityNew
    "bands"       -> Duo.LinearScale
    "medicine ball", "exercise ball" -> Duo.SportsBaseball
    else          -> Duo.SportsGymnastics
}

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

/**
 * Single equipment image with graceful icon fallback.
 *
 * @param equipment  Raw equipment string (e.g. "barbell", "dumbbell").
 * @param modifier   Applied to the outer Column.
 * @param showLabel  Whether to draw the human-readable label below.
 * @param size       Fixed size; if null fills available space.
 */
@Composable
fun EquipmentImage(
    equipment: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
    size: Dp? = null,
) {
    val ctx = LocalContext.current
    val th  = LocalFitTheme.current

    val assetName = when (equipment) {
        "body only", "" -> "bodyweight"
        "kettlebells"   -> "kettlebell"
        "bands"         -> "resistance_band"
        "e-z curl bar"  -> "ez_bar"
        "medicine ball" -> "medicine_ball"
        "exercise ball" -> "stability_ball"
        "foam roll"     -> "foam_roller"
        "smith machine" -> "smith_machine"
        "trap bar"      -> "trap_bar"
        "pull-up bar"   -> "pull_up_bar"
        "suspension"    -> "suspension_trainer"
        "barbell", "dumbbell", "machine", "cable", "plate", "bench" -> equipment
        else            -> equipment.replace(' ', '_').replace('-', '_')
    }

    val img by produceState<ImageBitmap?>(null, assetName) {
        value = EquipmentImageLoader.load(ctx, assetName)
    }

    val outerMod = if (size != null) modifier.size(size) else modifier

    Column(
        outerMod,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val imgMod = Modifier
            .then(if (size != null) Modifier.size(size) else Modifier.fillMaxSize(if (showLabel) 0.82f else 1f))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(th.text.copy(alpha = 0.07f), th.text.copy(alpha = 0.02f))))

        Box(imgMod, contentAlignment = Alignment.Center) {
            if (img != null) {
                Image(
                    bitmap             = img!!,
                    contentDescription = equipmentLabel(equipment),
                    modifier           = Modifier.fillMaxSize().padding(8.dp),
                    contentScale       = ContentScale.Fit,
                    alpha              = 0.95f,
                )
            } else {
                // Fallback icon — uses string-based helper, not Exercise object
                Icon(
                    imageVector        = equipmentIconForString(equipment),
                    contentDescription = equipmentLabel(equipment),
                    tint               = th.accentBright,
                    modifier           = Modifier.size(38.dp),
                )
            }
        }

        if (showLabel) {
            Text(
                text      = equipmentLabel(equipment),
                style     = FitType.caption,
                color     = th.text,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Equipment image card shown in ExerciseDetailScreen below the chips.
 */
@Composable
fun EquipmentRow(
    equipment: String,
    modifier: Modifier = Modifier,
) {
    if (equipment.isBlank() || equipment == "other") return
    val th = LocalFitTheme.current
    Column(modifier) {
        Text("EQUIPMENT", style = FitType.overline, color = th.textDim,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Box(
            Modifier.fillMaxWidth().height(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(th.text.copy(alpha = 0.04f))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            EquipmentImage(equipment = equipment, modifier = Modifier.fillMaxSize(), showLabel = true)
        }
    }
}

/**
 * Scrollable equipment filter strip with 3D image tiles.
 * Replaces the plain text GlassChip row in ExercisesScreen.
 */
@Composable
fun EquipmentFilterRow(
    equipmentList: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val th = LocalFitTheme.current
    LazyRow(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding        = PaddingValues(horizontal = 4.dp),
    ) {
        // "Any" tile
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.width(70.dp).clickableNoRipple { onSelect(null) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected == null) th.accent.copy(alpha = 0.22f) else th.text.copy(alpha = 0.07f))
                        .then(if (selected == null) Modifier.border(1.5.dp, th.accentBright, RoundedCornerShape(14.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("All", style = FitType.section, color = if (selected == null) th.accentBright else th.textDim)
                }
                Text("Any", style = FitType.caption, color = if (selected == null) th.accentBright else th.text, textAlign = TextAlign.Center)
            }
        }

        items(equipmentList.size) { idx ->
            val eq  = equipmentList[idx]
            val sel = selected == eq
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.width(70.dp).clickableNoRipple { onSelect(if (sel) null else eq) },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) th.accent.copy(alpha = 0.22f) else th.text.copy(alpha = 0.07f))
                        .then(if (sel) Modifier.border(1.5.dp, th.accentBright, RoundedCornerShape(14.dp)) else Modifier)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EquipmentImage(equipment = eq, modifier = Modifier.fillMaxSize())
                }
                Text(
                    equipmentLabel(eq),
                    style     = FitType.caption,
                    color     = if (sel) th.accentBright else th.text,
                    textAlign = TextAlign.Center,
                    maxLines  = 2,
                )
            }
        }
    }
}
