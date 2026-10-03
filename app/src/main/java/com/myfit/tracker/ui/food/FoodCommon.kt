package com.myfit.tracker.ui.food

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myfit.tracker.data.db.MealItem
import com.myfit.tracker.data.db.MealType
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.LocalTime

val MEAL_ORDER = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER, MealType.PRE_WORKOUT, MealType.POST_WORKOUT, MealType.OTHER)

fun mealLabel(t: String) = when (t) {
    MealType.BREAKFAST -> "Breakfast"; MealType.LUNCH -> "Lunch"; MealType.DINNER -> "Dinner"; MealType.SNACK -> "Snacks"
    MealType.PRE_WORKOUT -> "Pre-workout"; MealType.POST_WORKOUT -> "Post-workout"; else -> "Other"
}

/** Sensible default meal for the current time of day. */
fun mealForNow(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 4..10 -> MealType.BREAKFAST
    in 11..15 -> MealType.LUNCH
    in 16..18 -> MealType.SNACK
    in 19..23 -> MealType.DINNER
    else -> MealType.SNACK
}

data class Totals(val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double?)

fun totalsOf(items: List<MealItem>): Totals = Totals(
    items.sumOf { it.quantity * it.caloriesPerServing }, items.sumOf { it.quantity * it.proteinPerServing },
    items.sumOf { it.quantity * it.carbsPerServing }, items.sumOf { it.quantity * it.fatPerServing },
    if (items.isNotEmpty() && items.all { it.fiberPerServing != null }) items.sumOf { it.quantity * (it.fiberPerServing ?: 0.0) } else null,
)

fun servingText(qty: Double, size: Double, unit: String): String {
    val q = Fmt.trim(qty, 2)
    return when (unit) {
        "g", "ml" -> "${Fmt.trim(qty * size, 0)} $unit"
        else -> if (size == 1.0) "$q $unit" else "$q × ${Fmt.trim(size, 1)} $unit"
    }
}

/** Calorie ring + three macro bars. */
@Composable
fun MacroSummary(t: Totals, kcalTarget: Double?, proteinTarget: Double?, carbsTarget: Double?, fatTarget: Double?, ringSize: Int = 116) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(kcalTarget?.let { (t.kcal / it).toFloat() } ?: (if (t.kcal > 0) 1f else null), th.protein, size = ringSize.dp, stroke = 11.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.int(t.kcal), style = FitType.title, color = th.text, maxLines = 1)
                Text(kcalTarget?.let { "/ ${Fmt.int(it)}" } ?: "kcal", style = FitType.caption, color = th.textDim, maxLines = 1)
                if (kcalTarget != null) Text("kcal", style = FitType.caption, color = th.textFaint, maxLines = 1)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroBar("Protein", t.protein, proteinTarget, th.protein)
            MacroBar("Carbs", t.carbs, carbsTarget, th.carbs)
            MacroBar("Fat", t.fat, fatTarget, th.fat)
        }
    }
}

@Composable
private fun MacroBar(label: String, v: Double, target: Double?, color: Color) {
    val th = LocalFitTheme.current
    Column {
        Row {
            Text(label, style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
            Text("${Fmt.int(v)}${target?.let { " / ${Fmt.int(it)}" } ?: ""} g", style = FitType.caption, color = th.text)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp)) {
            Canvas(Modifier.matchParentSize()) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(if (th.isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f), cornerRadius = r)
                val f = if (target != null && target > 0) (v / target).toFloat().coerceIn(0f, 1f) else if (v > 0) 1f else 0f
                if (f > 0f) drawRoundRect(color, size = Size(size.width * f, size.height), cornerRadius = r)
            }
        }
    }
}

@Composable
fun EstimateTag() {
    val th = LocalFitTheme.current
    Text("ESTIMATE", style = FitType.overline, color = th.warning, modifier = Modifier.padding(start = 6.dp))
}

/** Small in-memory cache of decoded 3D food icons (assets/foodicon, 192 px, transparent). */
object FoodPhotos {
    private val cache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(80) {}
    fun load(ctx: android.content.Context, id: String): androidx.compose.ui.graphics.ImageBitmap? =
        cache.get(id) ?: runCatching {
            ctx.assets.open("foodicon/$id.webp").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap()
        }.getOrNull()?.also { cache.put(id, it) }
}

/** The food's own 3D icon on a soft tile, or a generic icon when the food has none (custom / barcode foods). */
@Composable
fun FoodThumb(photo: String?, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp = 14.dp, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo) {
        value = photo?.let { p -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FoodPhotos.load(ctx, p) } }
    }
    val m = if (modifier == Modifier) Modifier.size(size) else modifier
    val i = img
    if (i == null) {
        Box(m, contentAlignment = Alignment.Center) { com.myfit.tracker.ui.components.IconBubble(com.myfit.tracker.ui.theme.Duo.ForkKnife, th.protein, size) }
        return
    }
    val tile = if (th.isLight) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)
    Box(m.clip(androidx.compose.foundation.shape.RoundedCornerShape(corner)).background(tile), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(i, null, Modifier.fillMaxSize().padding(size * 0.06f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    }
}
