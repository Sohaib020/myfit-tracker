package com.myfit.tracker.ui.coach

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.PipVoice
import com.myfit.tracker.data.db.Food
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.domain.HealthProfile
import com.myfit.tracker.domain.Nutritionist
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.food.FoodThumb
import com.myfit.tracker.ui.food.MealPlanData
import com.myfit.tracker.ui.food.mealLabel
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PROTEIN_IDEAS = listOf("egg_boiled", "omelette", "chicken_tikka", "chicken_boti", "yogurt", "daal_masoor", "ublay_chanay", "lassi_salty", "fish_curry", "anda_channay")
private val DINNERS = listOf(
    listOf("chicken_tikka" to 1.0, "roti" to 1.0, "raita" to 1.0, "salad" to 1.0),
    listOf("daal_masoor" to 1.0, "phulka" to 2.0, "salad" to 1.0),
    listOf("chicken_karahi" to 0.5, "roti" to 1.0, "salad" to 1.0),
    listOf("fish_curry" to 1.0, "phulka" to 1.0, "salad" to 1.0),
    listOf("omelette" to 1.0, "roti" to 1.0, "yogurt" to 0.5),
    listOf("keema_matar" to 0.5, "roti" to 1.0, "salad" to 1.0),
    listOf("chicken_boti" to 1.0, "salad" to 1.0, "raita" to 1.0),
    listOf("lauki" to 1.0, "daal_moong" to 0.5, "phulka" to 2.0),
    listOf("chana_masala" to 1.0, "rice_white" to 0.5, "salad" to 1.0),
)

private data class Msg(val mine: Boolean, val text: String)
private val chatLog = mutableStateListOf<Msg>()   // kept for this app session only

/** Pro nutritionist: today's coaching, meal-by-meal review, a week of Pakistani meals with groceries, and chat. */
@Composable
fun NutritionistScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    remember { Coach.load(ctx); HealthProfile.load(ctx); 0 }
    val cp by Coach.prefs.collectAsState()
    val health by HealthProfile.selected.collectAsState()
    val muslim = s.muslim == "yes"
    val voice = container.pipVoice
    val speaking by voice.speaking.collectAsState()
    val persona = PipVoice.Persona(cp.nLook.male)
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val today = remember { Clock.today() }
    val meals by remember { container.nutritionRepo.mealsOn(today) }.collectAsState(initial = emptyList())
    val items by remember { container.nutritionRepo.itemsOn(today) }.collectAsState(initial = emptyList())
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())
    val goals = remember(targets) {
        Nutritionist.Goals(Targets.on(targets, TargetType.CALORIES, today), Targets.on(targets, TargetType.PROTEIN_G, today),
            Targets.on(targets, TargetType.CARBS_G, today), Targets.on(targets, TargetType.FAT_G, today), Targets.on(targets, TargetType.FIBER_G, today))
    }
    val ideaIds = remember { (PROTEIN_IDEAS + DINNERS.flatten().map { it.first }).distinct().map { "pkfood:$it" } }
    val ideaFoods by produceState(emptyMap<String, Food>()) {
        value = container.db.nutritionDao().byUuids(ideaIds).first().associateBy { it.uuid.removePrefix("pkfood:") }
    }
    fun ok(f: Food?) = f != null && container.nutritionRepo.judge(f, muslim).ok

    val totals = Nutritionist.Totals(items.sumOf { it.caloriesPerServing * it.quantity }, items.sumOf { it.proteinPerServing * it.quantity },
        items.sumOf { it.carbsPerServing * it.quantity }, items.sumOf { it.fatPerServing * it.quantity }, items.sumOf { (it.fiberPerServing ?: 0.0) * it.quantity })
    val hour = java.time.LocalTime.now().hour
    val tips = remember(totals, goals, ideaFoods, health) {
        Nutritionist.tips(totals, goals, hour) { kind ->
            if (kind == "protein") PROTEIN_IDEAS.mapNotNull { ideaFoods[it] }.filter { ok(it) }.map { it.name.substringBefore(" (") }
            else {
                val left = kind.substringAfter(":").toIntOrNull() ?: 600
                DINNERS.mapNotNull { combo ->
                    val fs = combo.map { (id, q) -> ideaFoods[id] to q }
                    if (fs.any { !ok(it.first) }) null
                    else { val k = fs.sumOf { (f, q) -> f!!.calories * q }; if (k <= left + 50) fs.joinToString(" + ") { (f, q) -> (if (q != 1.0) "${if (q == 0.5) "½" else q.toInt()} " else "") + f!!.name.substringBefore(" (").substringBefore(" /") } + " (~${k.toInt()} kcal)" else null }
                }.shuffled(java.util.Random(today.toEpochDay())).take(3).ifEmpty { listOf("a katori of daal with salad") }
            }
        }
    }
    fun speak(text: String) { if (speaking) voice.stop() else voice.speak(text, force = true, persona = persona) }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(cp.nName, { voice.stop(); nav.pop() }, "Your nutritionist")
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp).clip(CircleShape)) { CoachPortrait(cp.nLook.id, speaking, 84.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Hi, I'm ${cp.nName}.", style = FitType.section, color = th.text)
                Caption("I plan desi meals around your goals" + (if (health.isNotEmpty()) " and your health profile" else "") + ". Typical values — not medical advice.")
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(listOf("Today", "Meal review", "Week & groceries", "Chat")) { i, t -> GlassChip(t, tab == i, { tab = i }) }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Today so far", style = FitType.label, color = th.textDim)
                            Text("${totals.kcal.toInt()}" + (goals.kcal?.let { " / ${it.toInt()}" } ?: "") + " kcal", style = FitType.title, color = th.text)
                            Caption("Protein ${totals.p.toInt()}" + (goals.p?.let { "/${it.toInt()}" } ?: "") + " g · Carbs ${totals.c.toInt()} g · Fat ${totals.f.toInt()} g · Fibre ${totals.fiber.toInt()} g")
                        }
                    }
                }
                items(tips) { t ->
                    Glass(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp)) {
                            Icon(if (t.good) Duo.CheckCircle else Duo.AutoAwesome, null, tint = if (t.good) th.success else th.accentBright, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = FitType.label, color = th.text)
                                Caption(t.body, color = th.textDim)
                            }
                            Icon(Duo.VolumeUp, "Read aloud", tint = th.textDim, modifier = Modifier.size(30.dp).clip(CircleShape).clickableNoRipple { speak("${t.title}. ${t.body}") }.padding(5.dp))
                        }
                    }
                }
                item { GlassButton("Ask ${cp.nName} something", { tab = 3 }, Modifier.fillMaxWidth(), icon = Duo.ChatBubble, height = 48.dp) }
            }
            1 -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (meals.isEmpty()) item { Caption("No meals logged today yet. Log one and I'll review it here.", Modifier.padding(8.dp)) }
                items(meals.sortedBy { it.eatenAt }, key = { it.id }) { meal ->
                    val its = items.filter { it.mealId == meal.id }
                    val r = Nutritionist.review(its.map { Nutritionist.Line(it.foodName, it.quantity, it.caloriesPerServing, it.proteinPerServing, it.carbsPerServing, it.fatPerServing, it.fiberPerServing ?: 0.0) })
                    var aiNote by remember(meal.id) { mutableStateOf<String?>(null) }
                    var busy by remember(meal.id) { mutableStateOf(false) }
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(mealLabel(meal.mealType), style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                                val gc = when (r.grade) { "A" -> th.success; "B" -> th.accentBright; "C" -> th.warning; else -> th.danger }
                                Box(Modifier.size(36.dp).clip(CircleShape).background(gc.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text(r.grade, style = FitType.title, color = gc) }
                            }
                            Caption(its.joinToString(" · ") { it.foodName.substringBefore(" (") }, color = th.textDim)
                            Spacer(Modifier.height(4.dp))
                            Text(r.comment, style = FitType.body, color = th.text)
                            r.fix?.let { Caption("Try: $it", color = th.accentBright) }
                            aiNote?.let { Spacer(Modifier.height(6.dp)); Text(it, style = FitType.body, color = th.text) }
                            Spacer(Modifier.height(8.dp))
                            GlassButton(if (busy) "Thinking…" else "Detailed review from ${cp.nName}", {
                                if (busy) return@GlassButton
                                busy = true
                                scope.launch {
                                    aiNote = ask(container, systemPrompt(cp.nName, totals, goals, muslim),
                                        "Review my ${mealLabel(meal.mealType).lowercase()}: " + its.joinToString { "${it.foodName} x${"%.1f".format(it.quantity)} (${(it.caloriesPerServing * it.quantity).toInt()} kcal, ${(it.proteinPerServing * it.quantity).toInt()} g protein)" } +
                                            ". In under 90 words: what's good, one improvement and one Pakistani swap.")
                                    busy = false
                                }
                            }, Modifier.fillMaxWidth(), icon = Duo.AutoAwesome, height = 42.dp)
                        }
                    }
                }
            }
            2 -> WeekTab(container, goals.kcal, muslim)
            else -> ChatTab(container, cp.nName, systemPrompt(cp.nName, totals, goals, muslim), persona)
        }
    }
}

@Composable
private fun WeekTab(container: AppContainer, kcal: Double?, muslim: Boolean) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val data by produceState<com.myfit.tracker.ui.food.MealPlans?>(null) { value = withContext(Dispatchers.IO) { runCatching { MealPlanData.load(ctx) }.getOrNull() } }
    val d = data ?: run { Caption("Loading…", Modifier.padding(16.dp)); return }
    val band = d.bands.keys.minByOrNull { kotlin.math.abs(it - (kcal ?: 2000.0)) } ?: 2000
    val days = d.bands[band].orEmpty()
    val ids = remember(days) { days.flatMap { dd -> dd.meals.flatMap { m -> m.items.map { "pkfood:" + it.id } } }.distinct() }
    // health swaps applied to the whole week
    val plan by produceState<Pair<Map<String, Food>, Map<String, Food>>?>(null, ids, muslim) {
        val foods = container.db.nutritionDao().byUuids(ids).first().associateBy { it.uuid.removePrefix("pkfood:") }
        val swaps = foods.filterValues { !container.nutritionRepo.judge(it, muslim).ok }
            .mapNotNull { (id, f) -> container.nutritionRepo.swaps(f, muslim, 1).firstOrNull()?.let { id to it } }.toMap()
        value = foods to swaps
    }
    val (foods, swaps) = plan ?: (emptyMap<String, Food>() to emptyMap())
    val groceries = remember(days, swaps) {
        Nutritionist.groceries(days.flatMap { dd -> dd.meals.flatMap { m -> m.items.map { it2 ->
            val sw = swaps[it2.id]
            if (sw != null) Triple(sw.uuid.removePrefix("pkfood:"), sw.name, (it2.kcal / sw.calories.coerceAtLeast(1.0) * 2).let { q -> Math.round(q) / 2.0 }.coerceAtLeast(0.5))
            else Triple(it2.id, foods[it2.id]?.name ?: it2.label, it2.q)
        } } })
    }
    val checked = remember { mutableStateListOf<String>() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Caption("A week at about $band kcal a day, strictly Pakistani home food" + (if (swaps.isNotEmpty()) ", with ${swaps.size} item${if (swaps.size == 1) "" else "s"} swapped for your health profile" else "") + ".")
        }
        items(days) { dd ->
            var open by remember { mutableStateOf(false) }
            Glass(Modifier.fillMaxWidth(), onClick = { open = !open }) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dd.name, style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                        Caption("${dd.kcal} kcal · ${dd.p.toInt()} g P")
                    }
                    if (!open) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dd.meals.flatMap { it.items }.filter { it.id !in listOf("salad", "raita", "tea_sugar") }.take(6).forEach { it2 ->
                            val f = swaps[it2.id] ?: foods[it2.id]
                            FoodThumb(f?.let { container.nutritionRepo.meta(it)?.photo }, 40.dp, 10.dp)
                        }
                    } else dd.meals.forEach { m ->
                        Spacer(Modifier.height(6.dp))
                        Text(mealLabel(m.type), style = FitType.label, color = th.textDim)
                        m.items.forEach { it2 ->
                            val sw = swaps[it2.id]
                            val f = sw ?: foods[it2.id]
                            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                FoodThumb(f?.let { container.nutritionRepo.meta(it)?.photo }, 32.dp, 8.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(if (sw != null) "${sw.name} (swap)" else it2.label.replaceFirstChar { c -> c.uppercase() }, style = FitType.body, color = if (sw != null) th.accentBright else th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item { GlassButton("Open the full meal plan", { nav.push(Overlay.MealPlans) }, Modifier.fillMaxWidth(), icon = Duo.ForkKnife, height = 46.dp) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Grocery list for the week", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                GlassIconButton(Duo.Send, {
                    val txt = "MyFit grocery list (${band} kcal week)\n" + groceries.groupBy { it.aisle }.entries.joinToString("\n") { (a, l) -> "\n${a.label}:\n" + l.joinToString("\n") { "• ${it.name} — ${it.amount}" } }
                    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Share grocery list")) }
                }, size = 40.dp)
            }
            Caption("For one person. Spices, salt and water not included. Amounts are rounded up to what shops sell.")
        }
        groceries.groupBy { it.aisle }.forEach { (aisle, list) ->
            item(key = "aisle_${aisle.name}") {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(aisle.label.uppercase(), style = FitType.overline, color = th.textDim)
                        list.forEach { g ->
                            val done = g.name in checked
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickableNoRipple { if (done) checked.remove(g.name) else checked.add(g.name) }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (done) Duo.CheckCircle else Duo.RadioButtonUnchecked, null, tint = if (done) th.success else th.textDim, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(g.name, style = FitType.body, color = if (done) th.textFaint else th.text, modifier = Modifier.weight(1f))
                                Text(g.amount, style = FitType.label, color = th.textDim)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatTab(container: AppContainer, name: String, system: String, persona: PipVoice.Persona) {
    val th = LocalFitTheme.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(chatLog.size) { if (chatLog.isNotEmpty()) list.animateScrollToItem(chatLog.size - 1) }
    fun send(q: String) {
        val t = q.trim(); if (t.isEmpty() || busy) return
        chatLog += Msg(true, t); text = ""; busy = true
        scope.launch {
            val hist = chatLog.takeLast(8).joinToString("\n") { (if (it.mine) "User: " else "$name: ") + it.text }
            val a = ask(container, system, "Conversation so far:\n$hist\n\nReply to the user's last message as $name.")
            chatLog += Msg(false, a); busy = false
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (chatLog.isEmpty()) item {
                Caption("Ask me anything about food — what to eat before the gym, a diabetic-friendly breakfast, how much roti fits your goal, Ramadan sehri ideas…")
                Spacer(Modifier.height(8.dp))
                listOf("What should I eat after a workout?", "Make me a high-protein desi breakfast", "Is biryani OK on a diet?").forEach { q ->
                    GlassButton(q, { send(q) }, Modifier.fillMaxWidth().padding(vertical = 3.dp), height = 42.dp)
                }
            }
            items(chatLog) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.fillMaxWidth(0.86f).clip(RoundedCornerShape(18.dp)).background(if (m.mine) th.accent.copy(alpha = 0.22f) else th.text.copy(alpha = 0.06f)).padding(12.dp)) {
                        Text(m.text, style = FitType.body, color = th.text)
                        if (!m.mine) Icon(Duo.VolumeUp, "Read aloud", tint = th.textDim, modifier = Modifier.align(Alignment.End).size(28.dp).clickableNoRipple { container.pipVoice.speak(m.text, force = true, persona = persona) }.padding(4.dp))
                    }
                }
            }
            if (busy) item { Caption("$name is typing…") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glass(Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(26.dp)) {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text("Message $name", style = FitType.body, color = th.textFaint)
                    BasicTextField(text, { text = it.take(500) }, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth(), maxLines = 3)
                }
            }
            Spacer(Modifier.width(8.dp))
            AccentButton("", { send(text) }, icon = Duo.Send, height = 52.dp, enabled = !busy)
        }
    }
}

private fun systemPrompt(name: String, t: Nutritionist.Totals, g: Nutritionist.Goals, halal: Boolean): String {
    val h = HealthProfile.summary()
    return "You are $name, a warm, practical sports nutritionist for people in Pakistan (a fictional character in the MyFit app). " +
        "Recommend Pakistani home food and local options in household units (roti, katori, cup, glass, plate) and local names. " +
        "Be specific and brief (under 120 words unless asked for a plan), use simple English or Roman Urdu if the user writes that way. " +
        "Values are typical estimates. Never prescribe medicines, supplements doses or insulin; for medical conditions suggest checking with their doctor. " +
        (if (h.isNotBlank()) "The user's health profile: $h — never suggest foods that conflict with it and offer safe swaps. " else "") +
        (if (halal) "The user eats halal only. " else "") +
        "Today so far: ${t.kcal.toInt()} kcal, ${t.p.toInt()} g protein, ${t.c.toInt()} g carbs, ${t.f.toInt()} g fat, ${t.fiber.toInt()} g fibre. " +
        "Daily targets: " + listOfNotNull(g.kcal?.let { "${it.toInt()} kcal" }, g.p?.let { "${it.toInt()} g protein" }, g.fiber?.let { "${it.toInt()} g fibre" }).ifEmpty { listOf("not set") }.joinToString() + "."
}

/** One cloud answer within the daily free allowance; friendly text on failure. */
private suspend fun ask(container: AppContainer, system: String, user: String): String {
    val ai = com.myfit.tracker.ai.ondevice.OnDeviceAi.get(container.app)
    return try {
        ai.quota.require(com.myfit.tracker.ai.ondevice.AiQuota.Kind.CHAT)
        val r = container.aiRouter.text(system, user, 40_000)
        ai.quota.consume(com.myfit.tracker.ai.ondevice.AiQuota.Kind.CHAT)
        r.trim()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
        e.message ?: "I couldn't answer just now — try again in a moment."
    }
}
