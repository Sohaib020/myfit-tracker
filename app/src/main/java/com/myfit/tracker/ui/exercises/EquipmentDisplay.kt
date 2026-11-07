package com.myfit.tracker.ui.exercises

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Equipment Images Cache
 * Loads 3D rendered equipment images from assets/equipment/
 * Images are WebP format for optimal mobile performance
 */
private object EquipmentImages {
    private val cache = android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024)

    suspend fun load(context: Context, equipmentName: String): ImageBitmap? {
        val id = "equipment/$equipmentName"
        cache.get(id)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("equipment/$equipmentName.webp").use { BitmapFactory.decodeStream(it) }
            }.getOrNull()?.also { bitmap ->
                cache.put(id, bitmap.asImageBitmap())
            }?.asImageBitmap()
        }
    }
}

/**
 * Display a single equipment image with fallback to icon if image not available.
 * Used in exercise detail screens to show what equipment is needed.
 *
 * @param equipment Equipment name (e.g., "barbell", "dumbbell", "kettlebell")
 * @param modifier Compose modifier for styling
 * @param showLabel Whether to show the equipment label below the image
 */
@Composable
fun EquipmentImage(
    equipment: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false,
) {
    val ctx = LocalContext.current
    val th = LocalFitTheme.current
    val equipmentId = equipmentArt(equipment).removePrefix("equipment/").removeSuffix(".webp")

    val img by produceState<ImageBitmap?>(null, equipmentId) {
        value = EquipmentImages.load(ctx, equipmentId)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(if (showLabel) 0.85f else 1f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            th.text.copy(alpha = 0.06f),
                            th.text.copy(alpha = 0.02f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (img != null) {
                Image(
                    bitmap = img!!,
                    contentDescription = equipment,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit,
                    alpha = 0.95f
                )
            } else {
                // Fallback: show icon with color background
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = equipmentIcon(equipment),
                        contentDescription = equipment,
                        tint = th.accentBright,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        if (showLabel) {
            Text(
                text = equipmentLabel(equipment),
                style = FitType.caption,
                color = th.text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Equipment section for exercise detail screen.
 * Displays all equipment options with images in a grid or horizontal scroll.
 *
 * @param equipmentList List of equipment items with their images
 * @param modifier Compose modifier
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EquipmentSection(
    equipmentList: List<String>,
    modifier: Modifier = Modifier,
) {
    val th = LocalFitTheme.current

    if (equipmentList.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = "EQUIPMENT NEEDED",
            style = FitType.overline,
            color = th.textDim,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            equipmentList.forEach { equipment ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(th.text.copy(alpha = 0.04f))
                        .padding(8.dp)
                ) {
                    EquipmentImage(
                        equipment = equipment,
                        modifier = Modifier.fillMaxSize(),
                        showLabel = true
                    )
                }
            }
        }
    }
}

/**
 * Horizontal equipment carousel for browsing multiple equipment types.
 * Used in training day builder to show available equipment for an exercise.
 *
 * @param equipmentList List of equipment to display
 * @param selectedEquipment Currently selected equipment
 * @param onSelect Callback when equipment is selected
 * @param modifier Compose modifier
 */
@Composable
fun EquipmentCarousel(
    equipmentList: List<String>,
    selectedEquipment: String? = null,
    onSelect: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val th = LocalFitTheme.current

    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
    ) {
        items(equipmentList.size) { idx ->
            val equipment = equipmentList[idx]
            val isSelected = selectedEquipment == equipment

            Column(
                modifier = Modifier
                    .width(90.dp)
                    .clickableNoRipple { onSelect(equipment) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) th.accent.copy(alpha = 0.2f)
                            else th.text.copy(alpha = 0.06f)
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EquipmentImage(
                        equipment = equipment,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = equipmentLabel(equipment),
                    style = FitType.caption,
                    color = if (isSelected) th.accentBright else th.text,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
