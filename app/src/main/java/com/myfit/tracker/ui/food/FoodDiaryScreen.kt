package com.myfit.tracker.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MealItem
import com.myfit.tracker.data.db.NutritionSource
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Food diary for one day: totals vs targets, meals with their items, quick ways to add food. */
@Composable
fun FoodDiaryScreen(container: AppContainer, startDate: String?, asTab: Boolean = false, bottomPad: Int = 40) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    var date by remember { mutableStateOf(startDate?.let { LocalDate.parse(it) } ?: Clock.today()) }
    val items by remember(date) { container.nutritionRepo.itemsOn(date) }.collectAsState(initial = emptyList())
    val meals by remember(date) { container.nutritionRepo.mealsOn(date) }.collectAsState(initial = emptyList())
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<MealItem?>(null) }
    var saveFor by remember { mutableStateOf<String?>(null) }
    val total = totalsOf(items)
    val byMeal = meals.associateBy { it.id }
    val grouped = items.groupBy { byMeal[it.mealId]?.mealType ?: "OTHER" }
    val dateKey = Clock.dateKey(date)

    Column(Modifier.fillMaxSize()) {
        val dayLabel = if (date == Clock.today()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
        if (asTab) Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = com.myfit.tracker.ui.components.TopBarSpace, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Food", style = FitType.display, color = th.text)
                Caption("$dayLabel · diary, snaps, fasting & supplements")
            }
            GlassIconButton(Duo.KeyboardArrowLeft, { date = date.minusDays(1) })
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Duo.KeyboardArrowRight, { if (date < Clock.today()) date = date.plusDays(1) })
        } else OverlayTopBar("Food", { nav.pop() }, dayLabel) {
            GlassIconButton(Duo.KeyboardArrowLeft, { date = date.minusDays(1) })
            GlassIconButton(Duo.KeyboardArrowRight, { if (date < Clock.today()) date = date.plusDays(1) })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPad.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassCard {
                    MacroSummary(
                        total, Targets.on(targets, TargetType.CALORIES, date), Targets.on(targets, TargetType.PROTEIN_G, date),
                        Targets.on(targets, TargetType.CARBS_G, date), Targets.on(targets, TargetType.FAT_G, date),
                    )
                    if (items.any { it.source == NutritionSource.AI_PHOTO || it.source == NutritionSource.ESTIMATED }) {
                        Spacer(Modifier.height(10.dp))
                        Caption("Includes photo estimates — real portions and recipes vary.", color = th.warning)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile("Snap meal", Duo.Camera, th.protein, Modifier.weight(1f)) { nav.push(Overlay.FoodPhoto(mealForNow(), dateKey)) }
                    QuickTile("Search", Duo.Search, th.water, Modifier.weight(1f)) { nav.push(Overlay.FoodAdd(mealForNow(), dateKey, 0)) }
                    QuickTile("Barcode", Duo.Barcode, th.carbs, Modifier.weight(1f)) { nav.push(Overlay.FoodAdd(mealForNow(), dateKey, 1)) }
                    QuickTile("Saved", Duo.Bookmark, th.fat, Modifier.weight(1f)) { nav.push(Overlay.FoodAdd(mealForNow(), dateKey, 2)) }
                }
            }
            item {
                com.myfit.tracker.ui.theme.Glass(Modifier.fillMaxWidth(), onClick = { nav.push(Overlay.MealPlans) }) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.myfit.tracker.ui.components.IconBubble(Duo.ForkKnife, th.success, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pakistani meal plans", style = FitType.section, color = th.text)
                            Caption("7 days of desi meals in roti, katori and cups — log a meal in one tap")
                        }
                        Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
                    }
                }
            }
            if (asTab) item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HubChip("Fasting", Duo.Timer, th.sleep, Modifier.weight(1f)) { nav.push(Overlay.Fasting) }
                    HubChip("Supplements", Duo.Inventory2, th.success, Modifier.weight(1f)) { nav.push(Overlay.Supplements) }
                }
            }
            MEAL_ORDER.filter { it in grouped.keys || it in listOf("BREAKFAST", "LUNCH", "SNACK", "DINNER") }.forEach { type ->
                val list = grouped[type].orEmpty()
                item(key = type) {
                    GlassCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mealLabel(type), style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                            if (list.isNotEmpty()) Text("${Fmt.int(list.sumOf { it.quantity * it.caloriesPerServing })} ${com.myfit.tracker.domain.EnergyUnit.label}", style = FitType.label, color = th.textDim)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).clickableNoRipple { nav.push(Overlay.FoodAdd(type, dateKey, 0)) },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Duo.Add, "Add to ${mealLabel(type)}", tint = th.accentBright, modifier = Modifier.size(22.dp)) }
                        }
                        if (list.isEmpty()) Caption("Nothing logged.")
                        list.forEach { row ->
                            Spacer(Modifier.height(8.dp))
                            ItemRow(row, onClick = { editing = row }, onDelete = {
                                container.write { container.nutritionRepo.deleteItem(row) }
                                toaster.show("Removed ${row.foodName}")
                            }, icon = container.nutritionRepo.iconForName(row.foodName))
                        }
                        if (list.size >= 2) {
                            Spacer(Modifier.height(10.dp))
                            Text("Save as a meal", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple { saveFor = type }.padding(4.dp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    // ---- edit quantity
    val e = editing
    GlassSheet(visible = e != null, onDismiss = { editing = null }) {
        if (e != null) {
            var qty by remember(e.id) { mutableStateOf(Fmt.trim(if (e.servingUnit == "g" || e.servingUnit == "ml") e.quantity * e.servingSize else e.quantity, 2)) }
            Text(e.foodName, style = FitType.title, color = th.text)
            Caption("${Fmt.int(e.caloriesPerServing)} ${com.myfit.tracker.domain.EnergyUnit.label} per ${Fmt.trim(e.servingSize, 1)} ${e.servingUnit}")
            Spacer(Modifier.height(12.dp))
            val grams = e.servingUnit == "g" || e.servingUnit == "ml"
            NumberInput(qty, { qty = it }, if (grams) e.servingUnit else "servings")
            Spacer(Modifier.height(12.dp))
            AccentButton("Save", {
                val v = qty.toDoubleOrNull() ?: return@AccentButton
                val q = if (grams) v / e.servingSize else v
                if (q > 0) container.write { container.nutritionRepo.updateQuantity(e, q) }
                editing = null
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassButton("Delete", { container.write { container.nutritionRepo.deleteItem(e) }; editing = null }, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline)
        }
    }
    // ---- save meal
    val sf = saveFor
    GlassSheet(visible = sf != null, onDismiss = { saveFor = null }) {
        if (sf != null) {
            var name by remember(sf) { mutableStateOf("My ${mealLabel(sf).lowercase()}") }
            Text("Save as a meal", style = FitType.title, color = th.text)
            Caption("Log the same foods again with one tap.")
            Spacer(Modifier.height(12.dp))
            com.myfit.tracker.ui.entries.NotesField(name, { name = it.take(60) }, "Meal name")
            Spacer(Modifier.height(12.dp))
            AccentButton("Save meal", {
                val list = grouped[sf].orEmpty()
                container.write { container.nutritionRepo.saveAsMeal(name.ifBlank { mealLabel(sf) }, sf, list) }
                toaster.show("Saved \"$name\""); saveFor = null
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HubChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(modifier.height(52.dp), shape = RoundedCornerShape(26.dp), onClick = onClick, pressScale = 0.94f) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = FitType.label, color = th.text, maxLines = 1)
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(modifier.height(86.dp), shape = RoundedCornerShape(22.dp), onClick = onClick, pressScale = 0.92f) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            com.myfit.tracker.ui.components.IconBubble(icon, color, 38.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = FitType.caption, color = th.text, maxLines = 1)
        }
    }
}

@Composable
fun ItemRow(i: MealItem, onClick: () -> Unit, onDelete: () -> Unit, icon: String? = null) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickableNoRipple(onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { FoodThumb(icon, 40.dp, 12.dp); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(i.foodName, style = FitType.body, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (i.source == NutritionSource.AI_PHOTO || i.source == NutritionSource.ESTIMATED) EstimateTag()
            }
            Caption("${servingText(i.quantity, i.servingSize, i.servingUnit)} · P ${Fmt.int(i.quantity * i.proteinPerServing)} · C ${Fmt.int(i.quantity * i.carbsPerServing)} · F ${Fmt.int(i.quantity * i.fatPerServing)} g")
        }
        Text("${Fmt.int(i.quantity * i.caloriesPerServing)}", style = FitType.section, color = th.text)
        Text(" ${com.myfit.tracker.domain.EnergyUnit.label}", style = FitType.caption, color = th.textDim)
        Box(Modifier.size(34.dp).clip(CircleShape).clickableNoRipple(onDelete), contentAlignment = Alignment.Center) {
            Icon(Duo.Close, "Remove", tint = th.textDim, modifier = Modifier.size(16.dp))
        }
    }
}
