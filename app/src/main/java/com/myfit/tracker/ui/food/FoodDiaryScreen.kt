package com.myfit.tracker.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
    val ctxN = androidx.compose.ui.platform.LocalContext.current
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
    var pickRegion by remember { mutableStateOf(false) }
    val regionId by com.myfit.tracker.domain.FoodRegion.flow.collectAsState()
    val region = remember(regionId) { com.myfit.tracker.domain.FoodRegion.get(ctxN) }

    Column(Modifier.fillMaxSize()) {
        val dayLabel = if (date == Clock.today()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
        if (!asTab) OverlayTopBar("Food", { nav.pop() }, dayLabel) {
            GlassIconButton(Duo.KeyboardArrowLeft, { date = date.minusDays(1) })
            GlassIconButton(Duo.KeyboardArrowRight, { if (date < Clock.today()) date = date.plusDays(1) })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPad.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // R33 dashboard: header with the region chip → calorie ring hero → search pill → nutritionist → meal timeline → tools
            if (asTab) item {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = com.myfit.tracker.ui.components.TopBarSpace, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Food", style = FitType.display, color = th.text)
                        Caption(dayLabel)
                    }
                    RegionChip(region) { pickRegion = true }
                }
            } else item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { RegionChip(region) { pickRegion = true } }
            }
            item {
                FoodHero(total, Targets.on(targets, TargetType.CALORIES, date), Targets.on(targets, TargetType.PROTEIN_G, date),
                    Targets.on(targets, TargetType.CARBS_G, date), Targets.on(targets, TargetType.FAT_G, date), dayLabel,
                    onPrev = { date = date.minusDays(1) }, onNext = { if (date < Clock.today()) date = date.plusDays(1) }, canNext = date < Clock.today())
            }
            if (items.any { it.source == NutritionSource.AI_PHOTO || it.source == NutritionSource.ESTIMATED }) item {
                Caption("Includes photo estimates — real portions and recipes vary.", Modifier.padding(horizontal = 6.dp), color = th.warning)
            }
            item {
                // search pill with camera + barcode built in
                Glass(Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp), onClick = { nav.push(Overlay.FoodAdd(mealForNow(), dateKey, 0)) }) {
                    Row(Modifier.fillMaxSize().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Duo.Search, null, tint = th.accentBright, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Search", style = FitType.body, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        PillIcon(Duo.Camera, th.protein) { nav.push(Overlay.FoodPhoto(mealForNow(), dateKey)) }
                        Spacer(Modifier.width(6.dp))
                        PillIcon(Duo.Barcode, th.carbs) { nav.push(Overlay.FoodAdd(mealForNow(), dateKey, 1)) }
                    }
                }
            }
            item {
                com.myfit.tracker.ui.coach.CoachCard(com.myfit.tracker.ui.coach.CoachRole.NUTRITIONIST, { nav.push(Overlay.Nutritionist) }, height = 190.dp,
                    primary = "Ask" to { com.myfit.tracker.ui.nav.Launch.nutriTab = 3; nav.push(Overlay.Nutritionist) }, secondary = "Plans" to { nav.push(Overlay.MealPlans) })
            }
            item { FoodSection("Meals") }
            val types = MEAL_ORDER.filter { it in grouped.keys || it in listOf("BREAKFAST", "LUNCH", "SNACK", "DINNER") }
            types.forEachIndexed { idx, type ->
                val list = grouped[type].orEmpty()
                item(key = type) {
                    MealTimelineRow(type, list, last = idx == types.lastIndex, onAdd = { nav.push(Overlay.FoodAdd(type, dateKey, 0)) }, onSave = if (list.size >= 2) ({ saveFor = type }) else null) {
                        list.forEach { row ->
                            Spacer(Modifier.height(6.dp))
                            ItemRow(row, onClick = { editing = row }, onDelete = {
                                container.write { container.nutritionRepo.deleteItem(row) }
                                toaster.show("Removed ${row.foodName}")
                            }, icon = container.nutritionRepo.iconForName(row.foodName))
                        }
                    }
                }
            }
            item { FoodSection("Plans & tools") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("${region.flag} Meal plans", "7 days · ${region.short}", Duo.ForkKnife, th.success, Modifier.weight(1f)) { nav.push(Overlay.MealPlans) }
                    ToolTile("Saved meals", "One-tap logging", Duo.Bookmark, th.fat, Modifier.weight(1f)) { nav.push(Overlay.FoodAdd(mealForNow(), dateKey, 2)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("Fasting", "Timer & history", Duo.Timer, th.sleep, Modifier.weight(1f)) { nav.push(Overlay.Fasting) }
                    ToolTile("Supplements", "Daily checklist", Duo.Inventory2, th.water, Modifier.weight(1f)) { nav.push(Overlay.Supplements) }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    GlassSheet(visible = pickRegion, onDismiss = { pickRegion = false }) {
        Text("Where do you eat?", style = FitType.title, color = th.text)
        Caption("Sets your meal plans, the \"Popular here\" foods and search hints. Every food stays searchable.")
        Spacer(Modifier.height(10.dp))
        com.myfit.tracker.domain.FoodRegion.ALL.forEach { r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickableNoRipple { com.myfit.tracker.domain.FoodRegion.set(ctxN, r.id); pickRegion = false }
                .background(if (r.id == region.id) th.accent.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(r.flag, style = FitType.title)
                Spacer(Modifier.width(12.dp))
                Text(r.name, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                if (r.id == region.id) Icon(Duo.Check, null, tint = th.accentBright, modifier = Modifier.size(20.dp))
            }
        }
    }

    // ---- edit quantity
    val e = editing
    GlassSheet(visible = e != null, onDismiss = { editing = null }) {
        if (e != null) {
            val weightUnit = e.servingUnit == "g" || e.servingUnit == "ml"
            val gPer by androidx.compose.runtime.produceState<Double?>(if (weightUnit) e.servingSize else null, e.id) {
                if (!weightUnit) value = e.foodId?.let { container.nutritionRepo.food(it) }?.servingGrams?.takeIf { it > 0 }
            }
            var inGrams by remember(e.id) { mutableStateOf(weightUnit) }
            var qty by remember(e.id) { mutableStateOf(Fmt.trim(if (weightUnit) e.quantity * e.servingSize else e.quantity, 2)) }
            Text(e.foodName, style = FitType.title, color = th.text)
            Caption("${Fmt.int(e.caloriesPerServing)} ${com.myfit.tracker.domain.EnergyUnit.label} per ${Fmt.trim(e.servingSize, 1)} ${e.servingUnit}" + (gPer?.takeIf { !weightUnit }?.let { " (~${Fmt.int(it)} g)" } ?: ""))
            Spacer(Modifier.height(12.dp))
            val gp = gPer
            if (gp != null && !weightUnit) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.myfit.tracker.ui.theme.GlassChip("Servings", !inGrams, { if (inGrams) { inGrams = false; qty = Fmt.trim((qty.toDoubleOrNull() ?: gp) / gp, 2) } })
                    com.myfit.tracker.ui.theme.GlassChip("Grams", inGrams, { if (!inGrams) { inGrams = true; qty = Fmt.trim((qty.toDoubleOrNull() ?: 1.0) * gp, 0) } })
                }
                Spacer(Modifier.height(8.dp))
            }
            NumberInput(qty, { qty = it }, if (inGrams) (if (weightUnit) e.servingUnit else "g") else "servings")
            Spacer(Modifier.height(12.dp))
            AccentButton("Save", {
                val v = qty.toDoubleOrNull() ?: return@AccentButton
                val q = if (inGrams && gp != null) v / gp else v
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


@Composable
private fun FoodSection(text: String) {
    val th = LocalFitTheme.current
    Text(text.uppercase(), style = FitType.overline, color = th.textDim, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
}

/** R32: the nutritionist, front and centre — portrait, what they do, and two direct actions. */
@Composable
private fun NutritionistHero(onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    remember { com.myfit.tracker.domain.Coach.load(ctx); 0 }
    val cp by com.myfit.tracker.domain.Coach.prefs.collectAsState()
    Glass(Modifier.fillMaxWidth(), onClick = onOpen, tint = th.success.copy(alpha = 0.12f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(th.success.copy(alpha = 0.35f), th.success.copy(alpha = 0.08f)))),
                contentAlignment = Alignment.BottomCenter) {
                com.myfit.tracker.ui.coach.CoachPortrait(cp.nLook.id, false, 80.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cp.nName, style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Caption("Your nutritionist · reviews your meals, plans your week, builds your grocery list")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.myfit.tracker.ui.theme.AccentButton("Ask", onOpen, Modifier.weight(1f), icon = Duo.ChatBubble, height = 38.dp)
                    GlassButton("My plan", onOpen, Modifier.weight(1f), height = 38.dp)
                }
            }
        }
    }
}


@Composable
private fun RegionChip(region: com.myfit.tracker.domain.FoodRegion.Region, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.height(40.dp), shape = RoundedCornerShape(20.dp), onClick = onClick, pressScale = 0.94f) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(region.flag, style = FitType.section)
            Spacer(Modifier.width(6.dp))
            Text(region.short, style = FitType.label, color = th.text, maxLines = 1)
            Icon(Duo.KeyboardArrowDown, null, tint = th.textDim, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PillIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)).clickableNoRipple(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
    }
}

/** R33 hero: animated calorie ring (eaten / left) with macro bars, day switcher on top. */
@Composable
private fun FoodHero(t: Totals, kcalT: Double?, pT: Double?, cT: Double?, fT: Double?, day: String, onPrev: () -> Unit, onNext: () -> Unit, canNext: Boolean) {
    val th = LocalFitTheme.current
    val frac by androidx.compose.animation.core.animateFloatAsState(kcalT?.let { (t.kcal / it).toFloat() } ?: 0f, androidx.compose.animation.core.tween(900), label = "ring")
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Duo.KeyboardArrowLeft, onPrev, size = 34.dp)
                Text(day, style = FitType.label, color = th.text, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                GlassIconButton(Duo.KeyboardArrowRight, { if (canNext) onNext() }, size = 34.dp, tint = if (canNext) th.text else th.textFaint)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val sw = 14.dp.toPx()
                        val r = size.minDimension / 2 - sw
                        val tl = androidx.compose.ui.geometry.Offset(center.x - r, center.y - r); val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                        drawArc(th.text.copy(alpha = 0.08f), 135f, 270f, false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        val f = frac.coerceIn(0f, 1f)
                        if (f > 0f) drawArc(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(th.protein, th.carbs, th.success, th.protein), center), 135f, 270f * f, false, tl, sz,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        if (frac > 1f) drawArc(th.danger.copy(alpha = 0.8f), 135f, 270f * (frac - 1f).coerceAtMost(1f), false, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(sw * 0.45f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val left = kcalT?.let { it - t.kcal }
                        Text(Fmt.int(left?.let { kotlin.math.abs(it) } ?: t.kcal), style = FitType.metric, color = th.text)
                        Text(when { left == null -> com.myfit.tracker.domain.EnergyUnit.label + " eaten"; left >= 0 -> com.myfit.tracker.domain.EnergyUnit.label + " left"; else -> "over" },
                            style = FitType.caption, color = if (left != null && left < 0) th.danger else th.textDim)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row { Text("Eaten", style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f)); Text("${Fmt.int(t.kcal)}" + (kcalT?.let { " / ${Fmt.int(it)}" } ?: ""), style = FitType.label, color = th.text) }
                    MacroBar("Protein", t.protein, pT, th.protein)
                    MacroBar("Carbs", t.carbs, cT, th.carbs)
                    MacroBar("Fat", t.fat, fT, th.fat)
                }
            }
        }
    }
}

/** A meal on the day's timeline: rail dot, name + kcal, items, add. */
@Composable
private fun MealTimelineRow(type: String, list: List<MealItem>, last: Boolean, onAdd: () -> Unit, onSave: (() -> Unit)?, content: @Composable () -> Unit) {
    val th = LocalFitTheme.current
    val color = when (type) { "BREAKFAST" -> th.carbs; "LUNCH" -> th.protein; "SNACK" -> th.success; "DINNER" -> th.sleep; else -> th.fat }
    val kcal = list.sumOf { it.quantity * it.caloriesPerServing }
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (list.isNotEmpty()) color else color.copy(alpha = 0.3f)))
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(th.text.copy(alpha = 0.08f)))
        }
        Spacer(Modifier.width(8.dp))
        Glass(Modifier.weight(1f).padding(bottom = 10.dp), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(mealLabel(type), style = FitType.section, color = th.text)
                        Caption(if (list.isEmpty()) mealHint(type) else "${list.size} item${if (list.size == 1) "" else "s"} · ${Fmt.int(kcal)} ${com.myfit.tracker.domain.EnergyUnit.label}")
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)).clickableNoRipple(onAdd), contentAlignment = Alignment.Center) {
                        Icon(Duo.Add, "Add to ${mealLabel(type)}", tint = color, modifier = Modifier.size(20.dp))
                    }
                }
                content()
                if (onSave != null) Text("Save as a meal", style = FitType.label, color = th.accentBright, modifier = Modifier.padding(top = 8.dp).clickableNoRipple(onSave).padding(4.dp))
            }
        }
    }
}

private fun mealHint(type: String) = when (type) { "BREAKFAST" -> "Usually 7–10 am"; "LUNCH" -> "Usually 12–3 pm"; "SNACK" -> "Anytime"; "DINNER" -> "Usually 7–10 pm"; else -> "" }

@Composable
private fun ToolTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(modifier.height(92.dp), shape = RoundedCornerShape(22.dp), onClick = onClick, pressScale = 0.95f) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            com.myfit.tracker.ui.components.IconBubble(icon, color, 32.dp)
            Column {
                Text(title, style = FitType.label, color = th.text, maxLines = 1)
                Text(sub, style = FitType.caption, color = th.textDim, maxLines = 1)
            }
        }
    }
}
