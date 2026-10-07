package com.myfit.tracker.domain

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Health conditions, diets and body limits the user has told us about, and what they mean for food and training.
 *
 * Everything that suggests or lists food or exercises asks this object: suggestions and plans avoid what doesn't
 * suit the user (and offer a safer swap); browsing lists hide those items by default with a "show anyway" option,
 * and anything shown carries a warning badge. Nothing here is a diagnosis — it only follows what the user said.
 */
object HealthProfile {
    enum class Group(val label: String) { DIET("Diet & intolerances"), MEDICAL("Medical conditions"), INJURY("Injuries & body limits"), LIFE("Life stage") }

    /** Food tags: what a food contains or is high in (see [FoodTags]). */
    object F {
        const val DAIRY = "dairy"; const val LACTOSE = "lactose"; const val GLUTEN = "gluten"; const val NUTS = "nuts"; const val PEANUT = "peanut"
        const val EGG = "egg"; const val FISH = "fish"; const val SHELLFISH = "shellfish"; const val MEAT = "meat"; const val RED_MEAT = "red_meat"
        const val POULTRY = "poultry"; const val ORGAN = "organ_meat"; const val SOY = "soy"; const val SUGAR = "high_sugar"; const val SALT = "high_salt"
        const val FRIED = "fried"; const val FAT = "high_fat"; const val SPICY = "spicy"; const val CAFFEINE = "caffeine"; const val REFINED = "refined_carb"
        const val CARB = "high_carb"; const val PURINE = "high_purine"; const val POTASSIUM = "high_potassium"; const val SWEETENER = "sweetener"
        const val NONHALAL = "non_halal"; const val RAW = "raw_or_undercooked"; const val ACIDIC = "acidic"; const val GAS = "gassy"; const val SESAME = "sesame"
        const val HONEY = "honey"; const val ANIMAL = "animal_product"; const val PROCESSED = "processed_meat"; const val COLD_DRINK = "fizzy"
    }

    /** Exercise tags (see [ExerciseTags]). */
    object X {
        const val KNEE = "knee_load"; const val SPINE = "spinal_load"; const val OVERHEAD = "overhead"; const val WRIST = "wrist_load"; const val IMPACT = "high_impact"
        const val SUPINE = "lying_on_back"; const val PRONE = "lying_on_belly"; const val INTENSE = "very_intense"; const val NECK = "neck_load"; const val GRIP = "heavy_grip"
        const val HEAVY = "heavy_lift"; const val CORE_PRESSURE = "core_pressure"; const val INVERSION = "inversion"; const val BALANCE = "balance"; const val SHOULDER = "shoulder_load"
        const val HIP = "hip_load"; const val ELBOW = "elbow_load"; const val ANKLE = "ankle_load"; const val TWIST = "twisting"
    }

    /**
     * @param avoid foods that are hidden / swapped for this condition; [limit] foods only get a warning.
     * @param avoidEx exercises hidden / swapped; [limitEx] only warned.
     */
    data class Condition(
        val id: String, val label: String, val group: Group, val keywords: List<String>,
        val avoid: Set<String> = emptySet(), val limit: Set<String> = emptySet(),
        val avoidEx: Set<String> = emptySet(), val limitEx: Set<String> = emptySet(),
        val tip: String = "", val custom: Boolean = false,
    )

    val catalog: List<Condition> = listOf(
        // ---------- diets & intolerances
        Condition("vegetarian", "Vegetarian", Group.DIET, listOf("vegetarian", "veg", "no meat", "sabzi khor"), avoid = setOf(F.MEAT, F.RED_MEAT, F.POULTRY, F.FISH, F.SHELLFISH, F.ORGAN, F.PROCESSED), tip = "Protein from daal, chana, paneer, dahi, eggs (if you eat them) and soya."),
        Condition("eggetarian", "Eggetarian (veg + eggs)", Group.DIET, listOf("eggetarian", "egg vegetarian", "ovo"), avoid = setOf(F.MEAT, F.RED_MEAT, F.POULTRY, F.FISH, F.SHELLFISH, F.ORGAN, F.PROCESSED)),
        Condition("vegan", "Vegan", Group.DIET, listOf("vegan", "plant based", "plant-based"), avoid = setOf(F.MEAT, F.RED_MEAT, F.POULTRY, F.FISH, F.SHELLFISH, F.ORGAN, F.DAIRY, F.EGG, F.HONEY, F.ANIMAL, F.PROCESSED), tip = "Get B12 checked; combine daal + rice/roti for complete protein."),
        Condition("pescatarian", "Pescatarian", Group.DIET, listOf("pescatarian", "fish only"), avoid = setOf(F.RED_MEAT, F.POULTRY, F.ORGAN, F.PROCESSED)),
        Condition("no_beef", "No beef", Group.DIET, listOf("no beef", "beef free")),
        Condition("lactose", "Lactose intolerant", Group.DIET, listOf("lactose", "milk intolerance", "dairy intolerance", "doodh nahi hazam"), avoid = setOf(F.LACTOSE), limit = setOf(F.DAIRY), tip = "Dahi and aged cheese are often tolerated in small amounts; try lactose-free milk."),
        Condition("dairy_allergy", "Milk / dairy allergy", Group.DIET, listOf("dairy allergy", "milk allergy", "casein"), avoid = setOf(F.DAIRY, F.LACTOSE)),
        Condition("gluten", "Gluten intolerant / celiac", Group.DIET, listOf("gluten", "celiac", "coeliac", "wheat allergy", "gandum"), avoid = setOf(F.GLUTEN), tip = "Swap roti for makai, bajra, jowar or rice roti; check sauces and biscuits."),
        Condition("nut_allergy", "Tree-nut allergy", Group.DIET, listOf("nut allergy", "tree nut", "almond allergy", "badam"), avoid = setOf(F.NUTS)),
        Condition("peanut_allergy", "Peanut allergy", Group.DIET, listOf("peanut", "moongphali", "groundnut"), avoid = setOf(F.PEANUT)),
        Condition("egg_allergy", "Egg allergy", Group.DIET, listOf("egg allergy", "anda allergy"), avoid = setOf(F.EGG)),
        Condition("fish_allergy", "Fish allergy", Group.DIET, listOf("fish allergy", "machli allergy"), avoid = setOf(F.FISH)),
        Condition("shellfish_allergy", "Shellfish allergy", Group.DIET, listOf("shellfish", "prawn allergy", "shrimp allergy", "jhinga"), avoid = setOf(F.SHELLFISH)),
        Condition("soy_allergy", "Soy allergy", Group.DIET, listOf("soy", "soya allergy"), avoid = setOf(F.SOY)),
        Condition("sesame_allergy", "Sesame allergy", Group.DIET, listOf("sesame", "til allergy"), avoid = setOf(F.SESAME)),
        Condition("low_sugar_diet", "No added sugar", Group.DIET, listOf("no sugar", "sugar free", "keto", "low carb"), avoid = setOf(F.SUGAR), limit = setOf(F.REFINED, F.CARB)),
        Condition("caffeine_free", "Avoiding caffeine", Group.DIET, listOf("caffeine", "no tea", "no coffee"), avoid = setOf(F.CAFFEINE)),
        // ---------- medical
        Condition("diabetes", "Diabetes / prediabetes", Group.MEDICAL, listOf("diabetes", "diabetic", "sugar ki bimari", "prediabetes", "insulin resistance", "type 2", "type 1"), avoid = setOf(F.SUGAR), limit = setOf(F.REFINED, F.CARB, F.FRIED), limitEx = setOf(X.INTENSE), tip = "Pair carbs with protein and fibre; check sugar before and after hard workouts."),
        Condition("hypertension", "High blood pressure", Group.MEDICAL, listOf("blood pressure", "bp", "hypertension", "high bp"), avoid = setOf(F.SALT), limit = setOf(F.FRIED, F.FAT, F.CAFFEINE, F.PROCESSED), limitEx = setOf(X.HEAVY, X.CORE_PRESSURE, X.INVERSION), tip = "Breathe out while lifting — never hold your breath; keep salt under 1 teaspoon a day."),
        Condition("cholesterol", "High cholesterol", Group.MEDICAL, listOf("cholesterol", "lipids", "ldl"), avoid = setOf(F.FRIED), limit = setOf(F.FAT, F.ORGAN, F.RED_MEAT, F.PROCESSED)),
        Condition("heart", "Heart condition", Group.MEDICAL, listOf("heart", "cardiac", "angina", "heart attack", "stent", "dil"), avoid = setOf(F.SALT, F.FRIED), limit = setOf(F.FAT, F.CAFFEINE, F.PROCESSED), avoidEx = setOf(X.INTENSE, X.HEAVY), limitEx = setOf(X.CORE_PRESSURE, X.IMPACT), tip = "Only train at the intensity your cardiologist cleared; stop if you get chest pain or breathlessness."),
        Condition("pcos", "PCOS", Group.MEDICAL, listOf("pcos", "pcod", "polycystic"), avoid = setOf(F.SUGAR), limit = setOf(F.REFINED, F.FRIED), tip = "Strength training 2–3×/week plus protein at every meal helps most."),
        Condition("hypothyroid", "Underactive thyroid", Group.MEDICAL, listOf("hypothyroid", "thyroid", "hashimoto"), limit = setOf(F.SOY), tip = "Take your thyroid tablet 30–60 min before food and tea."),
        Condition("hyperthyroid", "Overactive thyroid", Group.MEDICAL, listOf("hyperthyroid", "graves"), limit = setOf(F.CAFFEINE), limitEx = setOf(X.INTENSE)),
        Condition("kidney", "Kidney disease", Group.MEDICAL, listOf("kidney", "ckd", "renal", "gurde"), avoid = setOf(F.SALT), limit = setOf(F.POTASSIUM, F.PROCESSED, F.RED_MEAT), tip = "Protein and potassium limits depend on your stage — follow your nephrologist's numbers."),
        Condition("kidney_stones", "Kidney stones", Group.MEDICAL, listOf("kidney stone", "pathri"), limit = setOf(F.SALT, F.ORGAN, F.RED_MEAT), tip = "Drink enough water for pale urine; go easy on salt."),
        Condition("fatty_liver", "Fatty liver", Group.MEDICAL, listOf("fatty liver", "nafld", "liver"), avoid = setOf(F.SUGAR, F.FRIED), limit = setOf(F.REFINED, F.FAT, F.COLD_DRINK)),
        Condition("gerd", "Acid reflux / GERD", Group.MEDICAL, listOf("acid", "reflux", "gerd", "heartburn", "tezabiyat", "gastric"), avoid = setOf(F.SPICY, F.FRIED), limit = setOf(F.CAFFEINE, F.ACIDIC, F.FAT, F.COLD_DRINK), limitEx = setOf(X.INVERSION, X.CORE_PRESSURE), tip = "Eat 3 hours before bed and before hard core work."),
        Condition("ibs", "IBS", Group.MEDICAL, listOf("ibs", "irritable bowel", "bloating"), limit = setOf(F.GAS, F.SPICY, F.FRIED, F.LACTOSE, F.CAFFEINE), tip = "Note trigger foods; smaller meals help."),
        Condition("gout", "Gout / high uric acid", Group.MEDICAL, listOf("gout", "uric acid"), avoid = setOf(F.PURINE, F.ORGAN), limit = setOf(F.RED_MEAT, F.SHELLFISH, F.SUGAR), limitEx = setOf(X.IMPACT)),
        Condition("anemia", "Anaemia / low iron", Group.MEDICAL, listOf("anemia", "anaemia", "low iron", "khoon ki kami"), limit = setOf(F.CAFFEINE), tip = "Avoid tea within an hour of meals; pair iron foods (palak, daal, meat) with lemon."),
        Condition("osteoporosis", "Osteoporosis / weak bones", Group.MEDICAL, listOf("osteoporosis", "osteopenia", "bones"), avoidEx = setOf(X.IMPACT), limitEx = setOf(X.TWIST, X.SPINE, X.HEAVY)),
        Condition("arthritis", "Arthritis", Group.MEDICAL, listOf("arthritis", "joint pain", "gathiya", "jor"), limitEx = setOf(X.IMPACT, X.KNEE, X.GRIP)),
        Condition("migraine", "Migraine", Group.MEDICAL, listOf("migraine", "aadhe sar"), limit = setOf(F.CAFFEINE, F.PROCESSED), limitEx = setOf(X.INVERSION, X.INTENSE)),
        Condition("epilepsy", "Epilepsy", Group.MEDICAL, listOf("epilepsy", "seizure", "mirgi"), limitEx = setOf(X.OVERHEAD, X.BALANCE, X.HEAVY), tip = "Train with someone nearby; avoid heavy weights over your face."),
        Condition("glaucoma", "Glaucoma", Group.MEDICAL, listOf("glaucoma"), avoidEx = setOf(X.INVERSION), limitEx = setOf(X.CORE_PRESSURE)),
        // ---------- injuries & limits
        Condition("knee", "Knee pain / injury", Group.INJURY, listOf("knee", "acl", "meniscus", "ghutna", "patella"), avoidEx = setOf(X.IMPACT), limitEx = setOf(X.KNEE), tip = "Box squats and glute bridges are usually kinder than deep squats and lunges."),
        Condition("lower_back", "Lower-back pain", Group.INJURY, listOf("back", "lower back", "slip disc", "disc", "sciatica", "kamar"), avoidEx = setOf(X.SPINE), limitEx = setOf(X.TWIST, X.HEAVY, X.IMPACT), tip = "Brace your core; machines and supported rows are safer than free bent-over lifts."),
        Condition("shoulder", "Shoulder pain / injury", Group.INJURY, listOf("shoulder", "rotator cuff", "kandha", "impingement"), avoidEx = setOf(X.OVERHEAD), limitEx = setOf(X.SHOULDER)),
        Condition("neck", "Neck pain", Group.INJURY, listOf("neck", "cervical", "gardan"), avoidEx = setOf(X.NECK), limitEx = setOf(X.OVERHEAD, X.INVERSION)),
        Condition("wrist", "Wrist pain", Group.INJURY, listOf("wrist", "carpal", "kalai"), limitEx = setOf(X.WRIST, X.GRIP)),
        Condition("elbow", "Elbow pain (tennis / golfer's)", Group.INJURY, listOf("elbow", "tennis elbow", "golfer"), limitEx = setOf(X.ELBOW, X.GRIP)),
        Condition("hip", "Hip pain", Group.INJURY, listOf("hip", "kulha"), limitEx = setOf(X.HIP, X.IMPACT)),
        Condition("ankle", "Ankle / foot pain", Group.INJURY, listOf("ankle", "foot", "plantar", "takhna", "heel"), avoidEx = setOf(X.IMPACT), limitEx = setOf(X.ANKLE, X.BALANCE)),
        Condition("asthma", "Asthma", Group.INJURY, listOf("asthma", "dama", "breathing"), limitEx = setOf(X.INTENSE, X.IMPACT), tip = "Warm up for 10 minutes and keep your inhaler nearby."),
        Condition("hernia", "Hernia", Group.INJURY, listOf("hernia"), avoidEx = setOf(X.CORE_PRESSURE, X.HEAVY), limitEx = setOf(X.SPINE)),
        Condition("surgery", "Recent surgery", Group.INJURY, listOf("surgery", "operation", "c-section", "caesarean"), avoidEx = setOf(X.HEAVY, X.CORE_PRESSURE, X.IMPACT, X.INTENSE), tip = "Wait for your surgeon's go-ahead before loading the area."),
        // ---------- life stages
        Condition("pregnancy", "Pregnant", Group.LIFE, listOf("pregnant", "pregnancy", "umeed"), avoid = setOf(F.RAW, F.CAFFEINE), limit = setOf(F.FISH, F.SUGAR), avoidEx = setOf(X.SUPINE, X.PRONE, X.IMPACT, X.CORE_PRESSURE, X.INTENSE, X.INVERSION), limitEx = setOf(X.BALANCE, X.HEAVY, X.TWIST), tip = "After the first trimester, avoid lying flat on your back; stop if you feel dizzy or pain."),
        Condition("breastfeeding", "Breastfeeding", Group.LIFE, listOf("breastfeeding", "nursing", "feeding baby"), limit = setOf(F.CAFFEINE), tip = "Add about 400–500 kcal and plenty of water."),
        Condition("older", "Older adult (60+)", Group.LIFE, listOf("old", "senior", "60+", "elderly"), avoidEx = setOf(X.INTENSE), limitEx = setOf(X.IMPACT, X.HEAVY, X.BALANCE, X.INVERSION), tip = "Balance and strength work twice a week lowers fall risk."),
        Condition("teen", "Teen (13–17)", Group.LIFE, listOf("teen", "teenager"), limitEx = setOf(X.HEAVY), tip = "Focus on technique before heavy weights."),
    )

    private val byId = catalog.associateBy { it.id }
    fun get(id: String): Condition? = byId[id] ?: customs.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ store
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val customs = MutableStateFlow<List<Condition>>(emptyList())
    /** Show items that don't suit the user (with warnings) in browsing lists. */
    val showUnsuitable = MutableStateFlow(false)
    @Volatile private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("health_profile", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        selected.value = p.getStringSet("ids", emptySet()).orEmpty()
        showUnsuitable.value = p.getBoolean("show_unsuitable", false)
        customs.value = runCatching {
            val a = JSONArray(p.getString("custom", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                fun set(k: String) = o.optJSONArray(k)?.let { x -> (0 until x.length()).map { x.getString(it) }.toSet() }.orEmpty()
                Condition(o.getString("id"), o.getString("label"), Group.valueOf(o.optString("group", "MEDICAL")), listOf(o.getString("label").lowercase()),
                    set("avoid"), set("limit"), set("avoidEx"), set("limitEx"), o.optString("tip"), custom = true)
            }
        }.getOrDefault(emptyList())
    }

    fun setSelected(c: Context, ids: Set<String>) { selected.value = ids; prefs(c).edit().putStringSet("ids", ids).apply() }
    fun toggle(c: Context, id: String) = setSelected(c, if (id in selected.value) selected.value - id else selected.value + id)
    fun setShowUnsuitable(c: Context, v: Boolean) { showUnsuitable.value = v; prefs(c).edit().putBoolean("show_unsuitable", v).apply() }

    fun addCustom(c: Context, cond: Condition) {
        customs.value = customs.value.filter { it.id != cond.id } + cond.copy(custom = true)
        saveCustoms(c); setSelected(c, selected.value + cond.id)
    }
    fun removeCustom(c: Context, id: String) { customs.value = customs.value.filter { it.id != id }; saveCustoms(c); setSelected(c, selected.value - id) }
    private fun saveCustoms(c: Context) {
        val a = JSONArray()
        customs.value.forEach { x ->
            a.put(JSONObject().put("id", x.id).put("label", x.label).put("group", x.group.name).put("tip", x.tip)
                .put("avoid", JSONArray(x.avoid.toList())).put("limit", JSONArray(x.limit.toList()))
                .put("avoidEx", JSONArray(x.avoidEx.toList())).put("limitEx", JSONArray(x.limitEx.toList())))
        }
        prefs(c).edit().putString("custom", a.toString()).apply()
    }

    val active: List<Condition> get() = selected.value.mapNotNull { get(it) }
    /** One line for AI prompts ("Lactose intolerant; Knee pain / injury"), or "" when none. */
    fun summary(): String = active.joinToString("; ") { it.label }

    // ------------------------------------------------------------------ detection
    /**
     * Matches free text ("I have gout and bad knees") to known conditions, offline. Returns matches (best first).
     */
    fun detect(text: String): List<Condition> {
        val t = " " + text.lowercase().replace(Regex("[^a-z0-9+ ]"), " ") + " "
        return catalog.map { c -> c to c.keywords.count { k -> t.contains(" " + k.lowercase() + " ") || (k.length >= 5 && t.contains(k.lowercase())) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }

    /** Builds a custom condition from AI output: `{"group":"MEDICAL","avoid":[…],"limit":[…],"avoidEx":[…],"limitEx":[…],"tip":"…"}`. */
    fun fromAi(label: String, json: String): Condition? = runCatching {
        val o = JSONObject(json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1))
        val foodOk = foodTagList.toSet()
        val exOk = exTagList.toSet()
        fun set(k: String, ok: Set<String>) = o.optJSONArray(k)?.let { x -> (0 until x.length()).map { x.getString(it) }.filter { it in ok }.toSet() }.orEmpty()
        Condition("custom_" + label.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(30), label.trim().take(40),
            runCatching { Group.valueOf(o.optString("group", "MEDICAL")) }.getOrDefault(Group.MEDICAL), listOf(label.lowercase()),
            set("avoid", foodOk), set("limit", foodOk), set("avoidEx", exOk), set("limitEx", exOk), o.optString("tip").take(200), custom = true)
    }.getOrNull()

    /** The prompt the AI gets to classify an unknown condition (only the condition name is sent). */
    val foodTagList = listOf(F.DAIRY, F.LACTOSE, F.GLUTEN, F.NUTS, F.PEANUT, F.EGG, F.FISH, F.SHELLFISH, F.MEAT, F.RED_MEAT, F.POULTRY, F.ORGAN, F.SOY, F.SUGAR, F.SALT, F.FRIED, F.FAT,
        F.SPICY, F.CAFFEINE, F.REFINED, F.CARB, F.PURINE, F.POTASSIUM, F.ACIDIC, F.GAS, F.SESAME, F.PROCESSED, F.COLD_DRINK, F.RAW)
    val exTagList = listOf(X.KNEE, X.SPINE, X.OVERHEAD, X.WRIST, X.IMPACT, X.SUPINE, X.PRONE, X.INTENSE, X.NECK, X.GRIP, X.HEAVY, X.CORE_PRESSURE, X.INVERSION, X.BALANCE, X.SHOULDER, X.HIP, X.ELBOW, X.ANKLE, X.TWIST)

    fun aiPrompt(label: String): String {
        val foods = foodTagList
        val ex = exTagList
        return "A fitness app user says they have: \"${label.take(60)}\". Reply with ONLY one JSON object: " +
            "{\"group\":\"DIET|MEDICAL|INJURY|LIFE\",\"avoid\":[food tags to avoid],\"limit\":[food tags to limit],\"avoidEx\":[exercise tags to avoid],\"limitEx\":[exercise tags to be careful with],\"tip\":\"one short practical tip\"}. " +
            "Use only these food tags: ${foods.joinToString(",")}. Exercise tags: ${ex.joinToString(",")}. Be conservative; empty lists are fine."
    }

    // ------------------------------------------------------------------ judging food and exercises
    enum class Verdict { OK, LIMIT, AVOID }
    data class Judgement(val verdict: Verdict, val reasons: List<String>) {
        val ok get() = verdict == Verdict.OK
        /** "Contains dairy (Lactose intolerant)" style text for badges. */
        val text: String get() = reasons.joinToString(" · ")
    }

    fun judgeFood(tags: Set<String>, muslim: Boolean = false): Judgement {
        val reasons = mutableListOf<String>(); var v = Verdict.OK
        if (muslim && F.NONHALAL in tags) { v = Verdict.AVOID; reasons += "Not halal" }
        for (c in active) {
            val a = c.avoid intersect tags
            if (a.isNotEmpty()) { v = Verdict.AVOID; reasons += "${tagLabel(a.first())} — ${c.label}" }
            else { val l = c.limit intersect tags; if (l.isNotEmpty()) { if (v == Verdict.OK) v = Verdict.LIMIT; reasons += "${tagLabel(l.first())} — ${c.label}" } }
            if (c.id == "no_beef" && F.RED_MEAT in tags && tags.contains("beef")) { v = Verdict.AVOID; reasons += "Beef" }
        }
        return Judgement(v, reasons.distinct())
    }

    fun judgeExercise(tags: Set<String>): Judgement {
        val reasons = mutableListOf<String>(); var v = Verdict.OK
        for (c in active) {
            val a = c.avoidEx intersect tags
            if (a.isNotEmpty()) { v = Verdict.AVOID; reasons += "${tagLabel(a.first())} — ${c.label}" }
            else { val l = c.limitEx intersect tags; if (l.isNotEmpty()) { if (v == Verdict.OK) v = Verdict.LIMIT; reasons += "${tagLabel(l.first())} — ${c.label}" } }
        }
        return Judgement(v, reasons.distinct())
    }

    fun tagLabel(t: String): String = when (t) {
        F.DAIRY -> "Contains dairy"; F.LACTOSE -> "Contains lactose"; F.GLUTEN -> "Contains gluten"; F.NUTS -> "Contains nuts"; F.PEANUT -> "Contains peanuts"
        F.EGG -> "Contains egg"; F.FISH -> "Contains fish"; F.SHELLFISH -> "Contains shellfish"; F.MEAT, F.RED_MEAT, F.POULTRY -> "Contains meat"; F.ORGAN -> "Organ meat"
        F.SOY -> "Contains soy"; F.SUGAR -> "High in sugar"; F.SALT -> "High in salt"; F.FRIED -> "Deep-fried"; F.FAT -> "High in fat"; F.SPICY -> "Spicy"
        F.CAFFEINE -> "Has caffeine"; F.REFINED -> "Refined carbs"; F.CARB -> "High in carbs"; F.PURINE -> "High in purines"; F.POTASSIUM -> "High in potassium"
        F.ACIDIC -> "Acidic"; F.GAS -> "Can cause gas"; F.SESAME -> "Contains sesame"; F.HONEY -> "Contains honey"; F.PROCESSED -> "Processed meat"
        F.COLD_DRINK -> "Fizzy drink"; F.RAW -> "Raw / undercooked"; F.ANIMAL -> "Animal product"; F.NONHALAL -> "Not halal"
        X.KNEE -> "Loads the knees"; X.SPINE -> "Loads the lower back"; X.OVERHEAD -> "Overhead"; X.WRIST -> "Loads the wrists"; X.IMPACT -> "Jumping / high impact"
        X.SUPINE -> "Lying on your back"; X.PRONE -> "Lying on your belly"; X.INTENSE -> "Very intense"; X.NECK -> "Loads the neck"; X.GRIP -> "Heavy grip"
        X.HEAVY -> "Heavy lifting"; X.CORE_PRESSURE -> "Strains the belly / breath-hold"; X.INVERSION -> "Head below heart"; X.BALANCE -> "Needs balance"
        X.SHOULDER -> "Loads the shoulders"; X.HIP -> "Loads the hips"; X.ELBOW -> "Loads the elbows"; X.ANKLE -> "Loads the ankles"; X.TWIST -> "Twisting"
        else -> t.replace('_', ' ')
    }
}

/** Works out a food's tags from its name, category and nutrients (plus any tags stored with the food). */
object FoodTags {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()
    private val F = HealthProfile.F

    private fun has(n: String, vararg w: String) = w.any { Regex("\\b" + Regex.escape(it)).containsMatchIn(n) }

    fun of(name: String, category: String = "", kcal: Double = 0.0, carbs: Double = 0.0, fat: Double = 0.0, sugarHint: Boolean = false, extra: Collection<String> = emptyList()): Set<String> {
        val key = "$name|$category|${kcal.toInt()}|${carbs.toInt()}|${fat.toInt()}"
        cache[key]?.let { return it + extra }
        val n = (name + " " + category).lowercase()
        val t = mutableSetOf<String>()
        val beef = has(n, "beef", "nihari", "paya", "bong", "haleem", "bihari", "seekh", "chapli", "burger patty", "kofta", "keema", "qeema")
        val mutton = has(n, "mutton", "lamb", "goat", "gosht", "kunna", "sajji", "rosh", "dumpukht", "karahi gosht", "namkeen gosht")
        val chicken = has(n, "chicken", "murgh", "murg", "broast", "wings", "tikka", "chargha", "shawarma", "zinger", "nuggets", "malai boti", "chicken")
        if (beef) t += setOf(F.RED_MEAT, F.MEAT, "beef")
        if (mutton) t += setOf(F.RED_MEAT, F.MEAT)
        if (chicken) t += setOf(F.POULTRY, F.MEAT)
        if (has(n, "kaleji", "liver", "gurda", "kapooray", "maghaz", "brain", "kidney", "paya", "siri")) t += setOf(F.ORGAN, F.PURINE, F.MEAT)
        if (has(n, "fish", "machli", "rahu", "pomfret", "salmon", "tuna", "surmai", "pomfret", "fish")) t += F.FISH
        if (has(n, "prawn", "shrimp", "jhinga", "crab", "lobster")) t += setOf(F.SHELLFISH, F.PURINE)
        if (has(n, "sausage", "salami", "pepperoni", "nugget", "hot dog", "frankfurter")) t += setOf(F.PROCESSED, F.MEAT, F.SALT)
        if (has(n, "pork", "ham", "bacon", "wine", "beer")) t += F.NONHALAL
        if (has(n, "egg", "anda", "anday", "omelette", "omelet", "khagina", "mayonnaise", "mayo", "cake", "bakarkhani")) t += F.EGG
        val dairy = has(n, "milk", "doodh", "dahi", "yogurt", "yoghurt", "raita", "lassi", "cheese", "paneer", "cream", "malai", "butter", "makhan", "ghee", "kheer",
            "rabri", "kulfi", "ice cream", "khoya", "mawa", "barfi", "rasmalai", "gulab jamun", "shake", "latte", "cappuccino", "chai", "tea", "doodh patti", "falooda",
            "custard", "kalakand", "milk cake", "korma", "makhni", "makhani", "dessert", "shahi tukray", "lab-e-shireen", "zarda")
        if (dairy) { t += F.DAIRY; if (!has(n, "ghee", "butter", "makhan", "aged", "cheddar")) t += F.LACTOSE }
        if (has(n, "roti", "naan", "paratha", "chapati", "phulka", "bread", "bun", "puri", "poori", "kulcha", "taftan", "sheermal", "pasta", "macaroni", "noodle",
                "pizza", "burger", "sandwich", "biscuit", "cake", "rusk", "samosa", "pakora", "pakoray", "halwa puri", "cookie", "patties", "pastry", "croissant",
                "maida", "atta", "wheat", "semolina", "suji", "sooji", "seviyan", "vermicelli", "bakarkhani", "khajla", "pheni", "jalebi", "nan khatai", "roll", "shawarma",
                "dalia", "barley", "jau", "oats", "crackers", "wrap", "kachori", "bhatura", "haleem", "nihari"))
            t += F.GLUTEN
        if (has(n, "makai", "corn", "bajra", "jowar", "rice roti", "gluten free")) t -= F.GLUTEN
        if (has(n, "almond", "badam", "cashew", "kaju", "pista", "pistachio", "walnut", "akhrot", "chilgoza", "pine nut", "hazelnut", "dry fruit", "nuts", "sohan halwa",
                "korma", "shahi", "kulfi", "kheer", "barfi", "panjiri", "habshi halwa", "baklava"))
            t += F.NUTS
        if (has(n, "peanut", "moongphali", "chikki", "peanut butter")) t += F.PEANUT
        if (has(n, "soya", "soy", "tofu", "edamame")) t += F.SOY
        if (has(n, "til", "sesame", "tahini", "rewri", "gajak")) t += F.SESAME
        if (has(n, "honey", "shehad")) t += F.HONEY
        if (has(n, "chai", "tea", "coffee", "cola", "pepsi", "coke", "energy drink", "red bull", "sting", "kahwa", "latte", "cappuccino", "espresso", "green tea", "chocolate"))
            t += F.CAFFEINE
        if (has(n, "cola", "pepsi", "coke", "7up", "sprite", "fanta", "mirinda", "soda", "fizzy", "dew", "sting", "energy drink")) t += setOf(F.COLD_DRINK, F.SUGAR)
        if (has(n, "fried", "pakora", "pakoray", "samosa", "fries", "broast", "zinger", "puri", "poori", "jalebi", "kachori", "bhatura", "nuggets", "fish fry",
                "chips", "nimko", "paratha", "spring roll", "dahi baray", "aloo tikki", "shami", "chapli", "fry"))
            t += F.FRIED
        if (has(n, "mithai", "sweet", "halwa", "jalebi", "gulab jamun", "barfi", "ladoo", "laddu", "rasgulla", "rasmalai", "kheer", "zarda", "sheer khurma", "kulfi",
                "ice cream", "cake", "pastry", "chocolate", "candy", "juice", "sharbat", "sherbet", "rooh afza", "falooda", "shake", "gola", "custard", "jam", "sugar",
                "cola", "dessert", "sohan", "pheni", "doughnut", "donut", "muffin", "brownie", "jaggery", "gur", "shakkar", "lassi (sweet)", "sweet lassi", "mango shake"))
            t += F.SUGAR
        if (has(n, "biryani", "pulao", "rice", "chawal", "naan", "paratha", "pasta", "noodle", "bread", "potato", "aloo", "fries", "puri", "halwa")) t += F.CARB
        if (has(n, "naan", "white bread", "maida", "pasta", "noodle", "biscuit", "rusk", "pizza", "burger", "cake", "white rice", "sheermal", "taftan", "bun")) t += F.REFINED
        if (has(n, "achar", "pickle", "papad", "nimko", "chips", "namkeen", "soy sauce", "instant noodle", "sausage", "salami", "cheese", "olives", "salted", "chaat masala",
                "karahi", "nihari", "haleem", "sajji", "pizza", "burger", "pakora", "samosa", "broast"))
            t += F.SALT
        if (has(n, "karahi", "nihari", "haleem", "biryani", "tikka", "chilli", "mirch", "chaat", "jalfrezi", "achar", "spicy", "seekh", "bihari", "golgappa", "pani puri",
                "dahi baray", "chana chaat", "masala", "vindaloo", "hot", "kebab", "peshawari", "sajji", "handi", "qorma", "korma", "chilli"))
            t += F.SPICY
        if (has(n, "lemon", "orange", "malta", "kinnow", "tomato", "imli", "tamarind", "chaat", "pickle", "achar", "vinegar", "citrus", "pineapple", "grapefruit", "kanji"))
            t += F.ACIDIC
        if (has(n, "chana", "cholay", "rajma", "lobia", "beans", "daal", "dal ", "cabbage", "band gobi", "gobi", "cauliflower", "onion", "pyaz", "moong", "mash",
                "masoor", "urad", "broccoli", "garlic", "lehsan", "sabut"))
            t += F.GAS
        if (has(n, "banana", "kela", "potato", "aloo", "palak", "spinach", "tomato", "avocado", "coconut water", "dates", "khajoor", "orange", "apricot", "khubani", "beans", "daal"))
            t += F.POTASSIUM
        if (has(n, "salad (raw", "sushi", "raw egg", "runny", "half fry", "half-fried", "unpasteurized", "rare steak")) t += F.RAW
        // numbers: very fatty (> 35% kcal from fat and > 15 g) or carb-heavy
        if (kcal > 0 && fat * 9 / kcal > 0.45 && fat >= 15) t += F.FAT
        if (carbs >= 50) t += F.CARB
        if (sugarHint) t += F.SUGAR
        if (t.any { it in setOf(F.MEAT, F.FISH, F.SHELLFISH, F.DAIRY, F.EGG, F.HONEY) }) t += F.ANIMAL
        cache[key] = t
        return t + extra
    }
}

/** Works out an exercise's tags from its name, muscles, equipment and type. */
object ExerciseTags {
    private val X = HealthProfile.X
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    fun of(name: String, primary: String = "", equipment: String = "", category: String = "", level: String = "", mechanic: String = ""): Set<String> {
        val key = "$name|$primary|$equipment|$category"
        cache[key]?.let { return it }
        val n = name.lowercase(); val t = mutableSetOf<String>()
        fun h(vararg w: String) = w.any { it in n }
        if (h("squat", "lunge", "leg press", "step-up", "step up", "pistol", "split squat", "leg extension", "hack", "sissy", "wall sit", "box jump", "jump squat", "skater")) t += X.KNEE
        if (h("deadlift", "good morning", "bent over", "bent-over", "barbell row", "t-bar", "back extension", "hyperextension", "clean", "snatch", "kettlebell swing", "superman", "rack pull", "pendlay")) t += X.SPINE
        if ((h("squat") && h("barbell", "back squat", "front squat")) || h("barbell squat")) { t += X.SPINE; t += X.HEAVY }
        if (h("overhead", "military", "shoulder press", "push press", "jerk", "snatch", "handstand", "arnold", "landmine press", "pike")) { t += X.OVERHEAD; t += X.SHOULDER }
        if (h("dip", "upright row", "behind the neck", "lateral raise", "front raise", "bench press", "fly", "flye", "pullover")) t += X.SHOULDER
        if (h("push-up", "pushup", "push up", "plank", "burpee", "mountain climber", "handstand", "crawl", "dip", "front squat", "clean")) t += X.WRIST
        if (h("jump", "burpee", "hop", "skipping", "jumping", "sprint", "run", "box jump", "plyo", "tuck", "jacks", "skater", "bound", "jog")) t += X.IMPACT
        if (h("bench press", "floor press", "crunch", "sit-up", "situp", "leg raise", "lying", "glute bridge", "hip thrust", "dead bug", "pullover", "skull crusher", "flat", "reverse crunch", "flutter")) t += X.SUPINE
        if (h("prone", "superman", "cobra", "lying leg curl", "reverse hyper")) t += X.PRONE
        if (h("burpee", "thruster", "sprint", "hiit", "tabata", "battle rope", "sled", "clean and jerk", "snatch", "man maker", "devil press")) t += X.INTENSE
        if (h("neck", "shrug", "wrestler bridge", "headstand")) t += X.NECK
        if (h("deadlift", "farmer", "pull-up", "pullup", "chin-up", "chinup", "hang", "rack pull", "shrug", "towel")) t += X.GRIP
        if (h("deadlift", "barbell squat", "back squat", "front squat", "leg press", "clean", "snatch", "jerk", "rack pull", "good morning", "hip thrust", "bench press")) t += X.HEAVY
        if (h("deadlift", "squat", "leg press", "crunch", "sit-up", "situp", "leg raise", "v-up", "ab roller", "rollout", "plank", "valsalva", "heavy", "hanging")) t += X.CORE_PRESSURE
        if (h("decline", "handstand", "headstand", "downward dog", "inversion", "head down")) t += X.INVERSION
        if (h("single leg", "single-leg", "one leg", "pistol", "bosu", "balance", "stability ball", "lunge", "step-up", "step up", "bulgarian", "turkish get")) t += X.BALANCE
        if (h("hip thrust", "hip abduction", "hip adduction", "adductor", "abductor", "sumo", "lunge", "fire hydrant", "clamshell")) t += X.HIP
        if (h("skull crusher", "triceps extension", "close grip", "curl", "dip", "pushdown", "french press", "preacher")) t += X.ELBOW
        if (h("calf", "jump", "skipping", "sprint", "run", "hop", "box jump", "jog", "jacks")) t += X.ANKLE
        if (h("russian twist", "woodchop", "wood chop", "rotation", "twist", "bicycle crunch", "windmill", "oblique")) t += X.TWIST
        cache[key] = t
        return t
    }
}
