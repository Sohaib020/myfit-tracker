package com.myfit.tracker.ui.food

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.data.repo.LogLine
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/* Pakistani 7-day meal plans in household units (roti, katori, cup) — assets/meal_plans.json from tools/mealplans/gen.py. */

data class PlanItem(val id: String, val q: Double, val label: String, val kcal: Int, val p: Double)
data class PlanMeal(val type: String, val items: List<PlanItem>, val kcal: Int, val p: Double)
data class PlanDay(val name: String, val meals: List<PlanMeal>, val kcal: Int, val p: Double)
data class MealPlans(val note: String, val bands: Map<Int, List<PlanDay>>)

internal object MealPlanData {
    @Volatile private var cache: MealPlans? = null
    fun load(c: Context): MealPlans = cache ?: run {
        val o = JSONObject(c.assets.open("meal_plans.json").bufferedReader().use { it.readText() })
        val plans = o.getJSONArray("plans")
        val bands = (0 until plans.length()).associate { i ->
            val p = plans.getJSONObject(i)
            val days = p.getJSONArray("days")
            p.getInt("kcal") to (0 until days.length()).map { d ->
                val dd = days.getJSONObject(d); val ms = dd.getJSONArray("meals")
                PlanDay(dd.getString("name"), (0 until ms.length()).map { m ->
                    val mm = ms.getJSONObject(m); val its = mm.getJSONArray("items")
                    PlanMeal(mm.getString("type"), (0 until its.length()).map { k ->
                        val x = its.getJSONObject(k); PlanItem(x.getString("id"), x.getDouble("q"), x.getString("label"), x.getInt("kcal"), x.getDouble("p"))
                    }, mm.getInt("kcal"), mm.getDouble("p"))
                }, dd.getInt("kcal"), dd.getDouble("p"))
            }
        }
        MealPlans(o.optString("note"), bands).also { cache = it }
    }
}

@Composable
fun MealPlansScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val data by produceState<MealPlans?>(null) { value = withContext(Dispatchers.IO) { runCatching { MealPlanData.load(ctx) }.getOrNull() } }
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())
    val myKcal = remember(targets) { Targets.on(targets, TargetType.CALORIES, Clock.today()) }
    var band by rememberSaveable { mutableIntStateOf(-1) }
    var day by rememberSaveable { mutableIntStateOf((Clock.today().dayOfWeek.value - 1)) }
    val d = data
    val muslim = com.myfit.tracker.ui.theme.LocalSettings.current.muslim == "yes"
    remember { com.myfit.tracker.domain.HealthProfile.load(ctx); 0 }
    val health by com.myfit.tracker.domain.HealthProfile.selected.collectAsState()
    val allIds = remember(d) { d?.bands?.values?.flatten()?.flatMap { dd -> dd.meals.flatMap { m -> m.items.map { "pkfood:" + it.id } } }?.distinct().orEmpty() }
    val foodsById by produceState(emptyMap<String, com.myfit.tracker.data.db.Food>(), allIds) {
        if (allIds.isNotEmpty()) value = container.db.nutritionDao().byUuids(allIds).first().associateBy { it.uuid.removePrefix("pkfood:") }
    }
    val swapped = remember { mutableStateMapOf<String, com.myfit.tracker.data.db.Food>() }
    var openKey by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Pakistani meal plans", { nav.pop() }, "Desi ghar ka khana, everyday portions")
        if (d == null) { Caption("Loading…", Modifier.padding(16.dp)); return@Column }
        val bands = d.bands.keys.sorted()
        if (band < 0) band = myKcal?.let { k -> bands.minByOrNull { kotlin.math.abs(it - k) } } ?: 2000
        val days = d.bands[band].orEmpty()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Calories per day", style = FitType.label, color = th.textDim)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bands) { b -> GlassChip("$b" + if (myKcal != null && bands.minByOrNull { kotlin.math.abs(it - myKcal) } == b) " ★" else "", band == b, { band = b }) }
                }
                if (myKcal != null) Caption("★ closest to your target of ${myKcal.toInt()}. Plans are within about 60 kcal of the band.", Modifier.padding(top = 6.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(days) { i, dd -> GlassChip(dd.name.replace("Day ", "Day "), day == i, { day = i }) }
                }
            }
            val dd = days.getOrNull(day.coerceIn(0, (days.size - 1).coerceAtLeast(0))) ?: return@LazyColumn
            val flagged = health.size.let { _ -> dd.meals.sumOf { m -> m.items.count { i -> foodsById[i.id]?.let { f -> !container.nutritionRepo.judge(f, muslim).ok } == true } } }
            if (flagged > 0) item {
                Caption("$flagged item${if (flagged == 1) "" else "s"} today may not suit your health profile — tap one to see safer Pakistani swaps.", Modifier.padding(horizontal = 6.dp), color = th.warning)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("${dd.name} · ${dd.kcal} kcal · ${dd.p.toInt()} g protein", Modifier.weight(1f))
                }
            }
            items(dd.meals, key = { "${band}_${day}_${it.type}" }) { m ->
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mealLabel(m.type), style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                            Text("${m.kcal} kcal · ${m.p.toInt()} g P", style = FitType.label, color = th.textDim)
                        }
                        Spacer(Modifier.height(8.dp))
                        m.items.forEach { it ->
                            val key = "${band}_${day}_${m.type}_${it.id}"
                            val base = foodsById[it.id]
                            val sw = swapped[key]
                            val shown = sw ?: base
                            val j = shown?.let { f -> container.nutritionRepo.judge(f, muslim) }
                            val q = sw?.let { f -> swapQty(it.kcal, f.calories) } ?: it.q
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickableNoRipple { if (j != null && !j.ok || sw != null) openKey = if (openKey == key) null else key },
                                verticalAlignment = Alignment.CenterVertically) {
                                FoodThumb(shown?.let { f -> container.nutritionRepo.meta(f)?.photo }, 44.dp, 12.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (sw != null) "${Fmt.num(q * sw.servingSize, 1)} ${sw.servingUnit} ${sw.name}" else it.label.replaceFirstChar { c -> c.uppercase() },
                                        style = FitType.body, color = th.text)
                                    if (j != null && !j.ok) HealthBadge(j)
                                    else if (sw != null) Caption("Swapped for your health · tap to undo", color = th.accentBright)
                                }
                                Text("${sw?.let { f -> (f.calories * q).toInt() } ?: it.kcal}", style = FitType.caption, color = th.textDim)
                            }
                            if (openKey == key) {
                                if (sw != null) GlassButton("Undo swap", { swapped.remove(key); openKey = null }, Modifier.fillMaxWidth().padding(bottom = 6.dp), height = 38.dp)
                                else if (base != null && j != null) {
                                    val alts by produceState(emptyList<com.myfit.tracker.data.db.Food>(), base.id, muslim) { value = container.nutritionRepo.swaps(base, muslim) }
                                    HealthWarningCard(j, alts.map { a -> a.name }) { name -> alts.firstOrNull { a -> a.name == name }?.let { a -> swapped[key] = a; openKey = null } }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        GlassButton("Log this ${mealLabel(m.type).lowercase()}", {
                            container.write {
                                val foods = container.db.nutritionDao().byUuids(m.items.map { "pkfood:" + it.id }).first().associateBy { it.uuid.removePrefix("pkfood:") }
                                val lines = m.items.mapNotNull { it2 ->
                                    val sw = swapped["${band}_${day}_${m.type}_${it2.id}"]
                                    val f = sw ?: foods[it2.id] ?: return@mapNotNull null
                                    val q = if (sw != null) swapQty(it2.kcal, sw.calories) else it2.q
                                    LogLine(f.name, q, f.servingSize, f.servingUnit, f.calories, f.proteinG, f.carbsG, f.fatG, f.fiberG, f.source, f.id)
                                }
                                container.nutritionRepo.log(Clock.today(), m.type, lines)
                                withContext(Dispatchers.Main) { toaster.show("Logged ${lines.size} items to ${mealLabel(m.type)}") }
                            }
                        }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 42.dp)
                    }
                }
            }
            item { Caption(d.note + " Swap any curry for one you like with similar calories — protein is what to keep steady.", Modifier.padding(horizontal = 6.dp)) }
        }
    }
}

/** Servings of a swap that roughly match the original item's calories, in half steps. */
private fun swapQty(kcal: Int, perServing: Double): Double =
    if (perServing <= 0) 1.0 else (Math.round(kcal / perServing * 2) / 2.0).coerceIn(0.5, 4.0)
