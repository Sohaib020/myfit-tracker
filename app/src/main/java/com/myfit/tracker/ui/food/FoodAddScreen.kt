package com.myfit.tracker.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Food
import com.myfit.tracker.data.db.NutritionSource
import com.myfit.tracker.data.repo.LogLine
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.food.OpenFoodFacts
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.GlassSearchField
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Add food: search the food list, scan a barcode, or log a saved meal. tab 0 = search, 1 = barcode, 2 = saved. */
@Composable
fun FoodAddScreen(container: AppContainer, mealType0: String, dateKey: String, tab0: Int) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val date = LocalDate.parse(dateKey)
    var mealType by remember { mutableStateOf(mealType0) }
    var tab by remember { mutableIntStateOf(if (tab0 == 2) 2 else 0) }
    var query by remember { mutableStateOf("") }
    val results by remember(query) { container.nutritionRepo.search(query) }.collectAsState(initial = emptyList())
    val recent by container.nutritionRepo.recent.collectAsState(initial = emptyList())
    val saved by container.nutritionRepo.savedMeals.collectAsState(initial = emptyList())
    val savedSum by container.nutritionRepo.savedSummaries.collectAsState(initial = emptyList())
    var picked by remember { mutableStateOf<Food?>(null) }
    var custom by remember { mutableStateOf<String?>(null) }   // non-null = open custom form (value = barcode or "")
    var busy by remember { mutableStateOf<String?>(null) }

    fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128)
            .enableAutoZoom().build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b ->
                val code = b.rawValue ?: return@addOnSuccessListener
                busy = "Looking up $code…"
                scope.launch {
                    val local = container.nutritionRepo.foodByBarcode(code)
                    if (local != null) { busy = null; picked = local; return@launch }
                    when (val r = OpenFoodFacts.lookup(code)) {
                        is OpenFoodFacts.Lookup.Found -> {
                            val id = container.nutritionRepo.addFood(r.food)
                            busy = null; picked = r.food.copy(id = id)
                        }
                        is OpenFoodFacts.Lookup.NoNutrition -> { busy = null; toaster.show("${r.name}: no nutrition data — add it once"); custom = code }
                        OpenFoodFacts.Lookup.NotFound -> { busy = null; toaster.show("Not in Open Food Facts yet — add it once"); custom = code }
                        is OpenFoodFacts.Lookup.Error -> { busy = null; toaster.show(r.message) }
                    }
                }
            }
            .addOnFailureListener { toaster.show("Scanner unavailable: ${it.message ?: "Google Play services needed"}") }
    }
    LaunchedEffect(Unit) { if (tab0 == 1) scan() }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Add food", { nav.pop() }, "${mealLabel(mealType)} · ${if (date == Clock.today()) "today" else dateKey}")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MEAL_ORDER.dropLast(1)) { t -> GlassChip(mealLabel(t), t == mealType, { mealType = t }) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionTile("Snap photo", Duo.Camera, th.protein, Modifier.weight(1f)) { nav.replace(Overlay.FoodPhoto(mealType, dateKey)) }
            ActionTile("Scan barcode", Duo.Barcode, th.carbs, Modifier.weight(1f)) { scan() }
            ActionTile("Add your own", Duo.Add, th.fat, Modifier.weight(1f)) { custom = "" }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("Foods", tab == 0, { tab = 0 }, icon = Duo.ForkKnife)
            GlassChip("Saved meals", tab == 2, { tab = 2 }, icon = Duo.Bookmark)
        }
        busy?.let { Caption(it, Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        if (tab == 0) {
            Spacer(Modifier.height(10.dp))
            GlassSearchField(query, { query = it }, "Search biryani, roti, egg, whey…", Modifier.padding(horizontal = 16.dp))
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab == 0) {
                val list = if (query.isBlank()) recent.ifEmpty { results } else results
                item { Text(if (query.isBlank() && recent.isNotEmpty()) "RECENT" else if (query.isBlank()) "FOODS" else "RESULTS", style = FitType.overline, color = th.textDim) }
                items(list, key = { "f" + it.id }) { f -> FoodRow(f) { picked = f } }
                if (query.isNotBlank() && results.isEmpty()) item {
                    Caption("No match. Try another spelling, snap a photo, or add it as a custom food.")
                }
            } else {
                if (saved.isEmpty()) item { Caption("No saved meals yet. In the food diary, tap \"Save as a meal\" under any meal with 2+ items.") }
                items(saved, key = { "s" + it.id }) { m ->
                    val sum = savedSum.firstOrNull { it.savedId == m.id }
                    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = {
                        container.write { container.nutritionRepo.logSaved(m.id, date, mealType) }
                        toaster.show("Logged ${m.name}"); nav.pop()
                    }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.myfit.tracker.ui.components.IconBubble(Duo.Bookmark, th.fat, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = FitType.section, color = th.text)
                                Caption("${sum?.items ?: 0} items · ${Fmt.int(sum?.kcal ?: 0.0)} kcal")
                            }
                            Text("Delete", style = FitType.caption, color = th.textDim, modifier = Modifier.clickableNoRipple { container.write { container.nutritionRepo.deleteSaved(m.id) } }.padding(8.dp))
                        }
                    }
                }
            }
        }
    }

    // ---- amount picker for a food
    val f = picked
    GlassSheet(visible = f != null, onDismiss = { picked = null }) {
        if (f != null) {
            val byWeight = f.servingUnit == "g" || f.servingUnit == "ml"
            var amount by remember(f.id) { mutableStateOf(if (byWeight) Fmt.trim(f.servingSize, 0) else "1") }
            val q = (amount.toDoubleOrNull() ?: 0.0).let { if (byWeight) it / f.servingSize else it }
            Text(f.name, style = FitType.title, color = th.text)
            Caption(listOfNotNull(f.brand, "${Fmt.int(f.calories)} kcal per ${Fmt.trim(f.servingSize, 1)} ${f.servingUnit}" + (f.servingGrams?.takeIf { !byWeight }?.let { " (~${Fmt.int(it)} g)" } ?: "")).joinToString(" · "))
            if (f.source == NutritionSource.DATABASE) Caption("Typical values — recipes and portions vary.", color = th.textFaint)
            Spacer(Modifier.height(12.dp))
            NumberInput(amount, { amount = it }, if (byWeight) f.servingUnit else "servings")
            Spacer(Modifier.height(10.dp))
            Text("${Fmt.int(q * f.calories)} kcal · P ${Fmt.int(q * f.proteinG)} · C ${Fmt.int(q * f.carbsG)} · F ${Fmt.int(q * f.fatG)} g", style = FitType.section, color = th.text)
            Spacer(Modifier.height(12.dp))
            AccentButton("Add to ${mealLabel(mealType)}", {
                if (q <= 0) return@AccentButton
                container.write {
                    container.nutritionRepo.log(date, mealType, listOf(LogLine(f.name, q, f.servingSize, f.servingUnit, f.calories, f.proteinG, f.carbsG, f.fatG, f.fiberG, f.source, f.id)))
                }
                toaster.show("Added ${f.name}"); picked = null
            }, Modifier.fillMaxWidth())
        }
    }

    // ---- custom food
    val cb = custom
    GlassSheet(visible = cb != null, onDismiss = { custom = null }) {
        if (cb != null) {
            var name by remember(cb) { mutableStateOf("") }
            var size by remember(cb) { mutableStateOf("100") }
            var unit by remember(cb) { mutableStateOf("g") }
            var kcal by remember(cb) { mutableStateOf("") }
            var p by remember(cb) { mutableStateOf("") }
            var c by remember(cb) { mutableStateOf("") }
            var fat by remember(cb) { mutableStateOf("") }
            Text("Custom food", style = FitType.title, color = th.text)
            Caption(if (cb.isNotBlank()) "Barcode $cb — copy the numbers from the label once; next time it's instant." else "From a label or a recipe. Values are per serving below.")
            Spacer(Modifier.height(10.dp))
            com.myfit.tracker.ui.entries.NotesField(name, { name = it.take(80) }, "Name (e.g. Shan biryani masala)")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(size, { size = it }, "", Modifier.weight(1f), big = false)
                listOf("g", "ml", "piece", "cup").forEach { u -> GlassChip(u, unit == u, { unit = u }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(kcal, { kcal = it }, "kcal", Modifier.weight(1f), big = false)
                NumberInput(p, { p = it }, "P g", Modifier.weight(1f), big = false)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberInput(c, { c = it }, "C g", Modifier.weight(1f), big = false)
                NumberInput(fat, { fat = it }, "F g", Modifier.weight(1f), big = false)
            }
            Spacer(Modifier.height(12.dp))
            AccentButton("Save food", {
                val k = kcal.toDoubleOrNull(); val s = size.toDoubleOrNull()
                if (name.isBlank() || k == null || s == null || s <= 0) { toaster.show("Name, serving and calories are needed"); return@AccentButton }
                val now = Clock.now()
                val food = Food(
                    name = name.trim(), servingSize = s, servingUnit = unit, servingGrams = if (unit == "g") s else null,
                    calories = k, proteinG = p.toDoubleOrNull() ?: 0.0, carbsG = c.toDoubleOrNull() ?: 0.0, fatG = fat.toDoubleOrNull() ?: 0.0,
                    fiberG = null, source = if (cb.isNotBlank()) NutritionSource.BARCODE else NutritionSource.USER,
                    sourceRef = if (cb.isNotBlank()) "Label · $cb" else "Entered by you", barcode = cb.ifBlank { null }, createdAt = now, updatedAt = now,
                )
                scope.launch { val id = container.nutritionRepo.addFood(food); custom = null; picked = food.copy(id = id) }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FoodRow(f: Food, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.name, style = FitType.body, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Caption(listOfNotNull(f.brand, "${Fmt.trim(f.servingSize, 1)} ${f.servingUnit}", "P ${Fmt.int(f.proteinG)} · C ${Fmt.int(f.carbsG)} · F ${Fmt.int(f.fatG)}").joinToString(" · "))
            }
            Text(Fmt.int(f.calories), style = FitType.section, color = th.text)
            Text(" kcal", style = FitType.caption, color = th.textDim)
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(modifier.height(84.dp), shape = RoundedCornerShape(22.dp), onClick = onClick, pressScale = 0.94f) {
        Column(Modifier.align(Alignment.Center).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            com.myfit.tracker.ui.components.IconBubble(icon, color, 34.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = FitType.caption, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
