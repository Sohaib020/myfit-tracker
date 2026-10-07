package com.myfit.tracker.domain

import kotlin.math.roundToInt

/**
 * The pro nutritionist's on-phone reasoning: daily coaching tips, a simple review of each logged meal,
 * and a grocery list in local units for a week of the Pakistani meal plan. Typical values — guidance, not
 * medical advice; no medication or insulin dosing, ever.
 */
object Nutritionist {
    data class Totals(val kcal: Double, val p: Double, val c: Double, val f: Double, val fiber: Double)
    data class Goals(val kcal: Double?, val p: Double?, val c: Double?, val f: Double?, val fiber: Double?)
    data class Line(val name: String, val servings: Double, val kcal: Double, val p: Double, val c: Double, val f: Double, val fiber: Double)

    /** A short coaching note for today. */
    data class Tip(val title: String, val body: String, val good: Boolean = false)

    fun tips(t: Totals, g: Goals, hour: Int, ideas: (String) -> List<String>): List<Tip> {
        val out = mutableListOf<Tip>()
        val kcalGoal = g.kcal
        if (t.kcal < 1) {
            out += Tip("Start with a solid breakfast", "Eggs or an omelette with one roti, or daliya with milk, keeps you full till lunch. Log it and I'll review it.")
            return out
        }
        g.p?.let { pg ->
            val left = pg - t.p
            when {
                left <= 0 -> out += Tip("Protein goal reached", "You've had ${t.p.roundToInt()} g protein today — great for recovery and keeping hunger down.", good = true)
                hour >= 14 && left > pg * 0.4 -> out += Tip("You're ${left.roundToInt()} g short on protein", "Easy desi fixes: " + ideas("protein").take(3).joinToString(", ") + ".")
            }
        }
        if (kcalGoal != null) {
            val left = kcalGoal - t.kcal
            when {
                left < -150 -> out += Tip("About ${(-left).roundToInt()} kcal over today", "No stress — one day doesn't decide anything. Keep dinner light: daal or sabzi with one phulka and salad, and take a 20-minute walk.")
                left in 150.0..900.0 && hour >= 16 -> out += Tip("${left.roundToInt()} kcal left for the day", "Dinner ideas that fit: " + ideas("dinner:${left.roundToInt()}").take(3).joinToString(", ") + ".")
                left > 900 && hour >= 19 -> out += Tip("You've eaten quite little today", "Under-eating can backfire later. Have a proper dinner with protein — chicken or daal, roti and dahi.")
            }
        }
        g.fiber?.let { fg -> if (hour >= 15 && t.fiber < fg * 0.45) out += Tip("Low on fibre so far (${t.fiber.roundToInt()} g)", "Add a katori of sabzi or daal, a guava, or kachumber salad — fibre keeps blood sugar and digestion steady.") }
        if (t.kcal > 0 && t.f * 9 / t.kcal > 0.42) out += Tip("Fat is high today", "Lots of ghee/oil or fried food. Ask for 'kam tel' at home, choose tikka or boti over karahi, and skip the extra paratha.")
        if (out.isEmpty()) out += Tip("Nicely balanced so far", "Keep going: water with each meal, protein in every meal, and a fruit as your snack.", good = true)
        return out.take(4)
    }

    data class Review(val grade: String, val comment: String, val fix: String?)

    /** Grades one meal on protein density, fibre, fat share and sweets/fried items. */
    fun review(lines: List<Line>): Review {
        val kcal = lines.sumOf { it.kcal * it.servings }
        if (kcal < 1) return Review("–", "Nothing logged yet.", null)
        val p = lines.sumOf { it.p * it.servings }; val f = lines.sumOf { it.f * it.servings }; val fib = lines.sumOf { it.fiber * it.servings }
        val pDensity = p / kcal * 100          // g protein per 100 kcal
        val fatShare = f * 9 / kcal
        val names = lines.joinToString(" ") { it.name.lowercase() }
        val sweet = listOf("halwa", "jalebi", "gulab", "barfi", "ladoo", "kheer", "cake", "mithai", "soft drink", "cola", "sweet", "rasmalai", "zarda").any { it in names }
        val fried = listOf("samosa", "pakora", "paratha", "puri", "fries", "fried", "broast", "roll", "nimko").any { it in names }
        var score = 0
        if (pDensity >= 6) score += 2 else if (pDensity >= 4) score += 1
        if (fib >= 6) score += 1
        if (fatShare < 0.35) score += 1 else if (fatShare > 0.5) score -= 1
        if (sweet) score -= 1
        if (fried) score -= 1
        if (kcal > 1100) score -= 1
        val grade = when { score >= 4 -> "A"; score >= 2 -> "B"; score >= 0 -> "C"; else -> "D" }
        val good = mutableListOf<String>(); val fix = mutableListOf<String>()
        if (pDensity >= 6) good += "plenty of protein" else fix += "add protein (an egg, dahi, chicken or a katori of daal)"
        if (fib >= 6) good += "good fibre" else fix += "add sabzi or salad for fibre"
        if (fatShare > 0.45) fix += "go lighter on oil/ghee"
        if (sweet) fix += "keep the sweet to a small portion"
        if (fried) fix += "swap fried items for roti or tikka"
        val comment = "${kcal.roundToInt()} kcal · ${p.roundToInt()} g protein" + if (good.isNotEmpty()) " — ${good.joinToString(" and ")}." else "."
        return Review(grade, comment, fix.firstOrNull()?.replaceFirstChar { it.uppercase() })
    }

    // ------------------------------------------------------------------ grocery list
    enum class Aisle(val label: String) { GRAIN("Atta & chawal"), MEAT("Meat, fish & anday"), DAIRY("Doodh & dahi"), DAAL("Daal & chanay"), SABZI("Sabzi"), FRUIT("Phal (fruit)"), PANTRY("Pantry & extras") }
    enum class Measure { G, ML, COUNT }
    data class Item(val name: String, val aisle: Aisle, val unit: Measure = Measure.G)

    private val ATTA = Item("Atta (whole-wheat flour)", Aisle.GRAIN); private val CHAWAL = Item("Chawal (rice)", Aisle.GRAIN); private val DALIYA = Item("Daliya", Aisle.GRAIN)
    private val CHICKEN = Item("Chicken", Aisle.MEAT); private val QEEMA = Item("Qeema (mince)", Aisle.MEAT); private val FISH = Item("Fish (machli)", Aisle.MEAT)
    private val EGGS = Item("Anday (eggs)", Aisle.MEAT, Measure.COUNT)
    private val MILK = Item("Doodh (milk)", Aisle.DAIRY, Measure.ML); private val DAHI = Item("Dahi (yogurt)", Aisle.DAIRY)
    private val MASOOR = Item("Masoor daal", Aisle.DAAL); private val MOONG = Item("Moong daal", Aisle.DAAL); private val CHANA_D = Item("Chana daal", Aisle.DAAL); private val MASH = Item("Mash daal", Aisle.DAAL)
    private val KABULI = Item("Safaid chanay (dry)", Aisle.DAAL); private val KALA = Item("Kalay chanay (dry)", Aisle.DAAL); private val LOBIA = Item("Lobia (dry)", Aisle.DAAL); private val BHUNA = Item("Bhunay chanay", Aisle.DAAL)
    private val ONION = Item("Piyaz (onion)", Aisle.SABZI); private val TOMATO = Item("Tamatar (tomato)", Aisle.SABZI); private val CUCUMBER = Item("Kheera (cucumber)", Aisle.SABZI)
    private val BHINDI = Item("Bhindi", Aisle.SABZI); private val LAUKI = Item("Lauki (kaddu)", Aisle.SABZI); private val SAAG = Item("Saag (sarson / palak)", Aisle.SABZI); private val BAINGAN = Item("Baingan", Aisle.SABZI)
    private val ALOO = Item("Aloo (potato)", Aisle.SABZI); private val MATAR = Item("Matar (peas)", Aisle.SABZI); private val ADRAK = Item("Adrak lehsan (ginger garlic)", Aisle.SABZI)
    private val BANANA = Item("Kela (banana)", Aisle.FRUIT, Measure.COUNT); private val GUAVA = Item("Amrood (guava)", Aisle.FRUIT, Measure.COUNT); private val KINNOW = Item("Kinnow / malta", Aisle.FRUIT, Measure.COUNT)
    private val APPLE = Item("Saib (apple)", Aisle.FRUIT, Measure.COUNT); private val MANGO = Item("Aam (mango)", Aisle.FRUIT); private val DATES = Item("Khajoor (dates)", Aisle.FRUIT)
    private val OIL = Item("Cooking oil / ghee", Aisle.PANTRY, Measure.ML); private val ALMONDS = Item("Badam (almonds)", Aisle.PANTRY); private val PEANUTS = Item("Mungphali (peanuts)", Aisle.PANTRY)
    private val SATTU = Item("Sattu", Aisle.PANTRY); private val CHAI = Item("Chai patti", Aisle.PANTRY); private val SUGAR = Item("Cheeni (sugar)", Aisle.PANTRY); private val GUR = Item("Gur", Aisle.PANTRY)

    /** Main ingredients per one catalog serving of each meal-plan food. */
    private val RECIPE: Map<String, List<Pair<Item, Double>>> = mapOf(
        "roti" to listOf(ATTA to 30.0), "phulka" to listOf(ATTA to 25.0), "paratha_plain" to listOf(ATTA to 50.0, OIL to 10.0),
        "anda_paratha" to listOf(ATTA to 50.0, EGGS to 1.0, OIL to 10.0), "rice_white" to listOf(CHAWAL to 55.0),
        "chana_pulao" to listOf(CHAWAL to 80.0, KABULI to 40.0, OIL to 10.0, ONION to 30.0),
        "chicken_biryani" to listOf(CHAWAL to 90.0, CHICKEN to 120.0, DAHI to 30.0, ONION to 40.0, TOMATO to 40.0, OIL to 15.0),
        "chicken_karahi" to listOf(CHICKEN to 200.0, TOMATO to 120.0, ADRAK to 10.0, OIL to 20.0),
        "chicken_handi" to listOf(CHICKEN to 180.0, DAHI to 60.0, ONION to 40.0, OIL to 20.0),
        "chicken_tikka" to listOf(CHICKEN to 180.0, DAHI to 20.0), "chicken_boti" to listOf(CHICKEN to 150.0, DAHI to 20.0),
        "fish_curry" to listOf(FISH to 180.0, TOMATO to 60.0, ONION to 40.0, OIL to 15.0),
        "keema_matar" to listOf(QEEMA to 120.0, MATAR to 60.0, ONION to 40.0, TOMATO to 50.0, OIL to 15.0),
        "daal_masoor" to listOf(MASOOR to 50.0, ONION to 20.0, OIL to 10.0), "daal_moong" to listOf(MOONG to 50.0, OIL to 8.0),
        "daal_chana" to listOf(CHANA_D to 55.0, OIL to 10.0), "daal_mash" to listOf(MASH to 50.0, OIL to 12.0),
        "chana_masala" to listOf(KABULI to 60.0, ONION to 30.0, TOMATO to 50.0, OIL to 12.0), "lobia" to listOf(LOBIA to 55.0, TOMATO to 50.0, OIL to 10.0),
        "anda_channay" to listOf(EGGS to 2.0, KABULI to 40.0, TOMATO to 40.0, OIL to 10.0), "ublay_chanay" to listOf(KALA to 60.0), "gur_chanay" to listOf(BHUNA to 40.0, GUR to 15.0),
        "omelette" to listOf(EGGS to 2.0, ONION to 20.0, TOMATO to 20.0, OIL to 8.0), "egg_boiled" to listOf(EGGS to 1.0),
        "daliya" to listOf(DALIYA to 45.0, MILK to 150.0), "yogurt" to listOf(DAHI to 245.0), "raita" to listOf(DAHI to 150.0, CUCUMBER to 50.0),
        "kachi_lassi" to listOf(MILK to 125.0), "lassi_salty" to listOf(DAHI to 200.0), "milk_low" to listOf(MILK to 240.0), "milk_whole" to listOf(MILK to 240.0),
        "tea_sugar" to listOf(MILK to 80.0, CHAI to 3.0, SUGAR to 5.0), "salad" to listOf(CUCUMBER to 80.0, TOMATO to 80.0, ONION to 40.0),
        "bhindi" to listOf(BHINDI to 200.0, ONION to 40.0, OIL to 15.0), "lauki" to listOf(LAUKI to 250.0, TOMATO to 30.0, OIL to 10.0),
        "saag" to listOf(SAAG to 300.0, OIL to 15.0), "baingan_bharta" to listOf(BAINGAN to 250.0, ONION to 30.0, TOMATO to 40.0, OIL to 10.0),
        "chaat" to listOf(KABULI to 50.0, ALOO to 80.0, DAHI to 50.0, ONION to 30.0),
        "banana" to listOf(BANANA to 1.0), "apple" to listOf(APPLE to 1.0), "guava" to listOf(GUAVA to 1.0), "orange" to listOf(KINNOW to 1.0),
        "mango" to listOf(MANGO to 165.0), "dates" to listOf(DATES to 24.0), "almonds" to listOf(ALMONDS to 28.0), "peanuts" to listOf(PEANUTS to 30.0),
        "sattu" to listOf(SATTU to 40.0, SUGAR to 10.0),
    )

    data class Grocery(val aisle: Aisle, val name: String, val amount: String)

    /** Sums a week of (food id or name, servings) into a shopping list; unknown dishes are listed as they are. */
    fun groceries(items: List<Triple<String, String, Double>>): List<Grocery> {
        val sum = LinkedHashMap<Item, Double>()
        val other = LinkedHashMap<String, Double>()
        items.forEach { (id, name, q) ->
            val r = RECIPE[id]
            if (r == null) other[name] = (other[name] ?: 0.0) + q
            else r.forEach { (it, g) -> sum[it] = (sum[it] ?: 0.0) + g * q }
        }
        val out = sum.entries.map { (it, v) -> Grocery(it.aisle, it.name, amount(it.unit, v, it == EGGS)) }.sortedBy { it.aisle.ordinal }.toMutableList()
        other.forEach { (n, q) -> out += Grocery(Aisle.PANTRY, n, "for ${fmtQ(q)} servings") }
        return out
    }

    private fun fmtQ(q: Double) = if (q % 1.0 == 0.0) q.toInt().toString() else "%.1f".format(q)
    private fun amount(u: Measure, v: Double, eggs: Boolean): String = when (u) {
        Measure.COUNT -> { val n = kotlin.math.ceil(v).toInt(); if (eggs && n >= 12) "$n (${"%.1f".format(n / 12.0)} dozen)" else "$n" }
        Measure.ML -> if (v >= 1000) "${"%.1f".format(v / 1000)} litre" else "${(v / 50).roundToInt().coerceAtLeast(1) * 50} ml"
        Measure.G -> when {
            v >= 1000 -> "${"%.1f".format(v / 1000)} kg"
            v >= 250 -> "${(v / 250).roundToInt() * 250} g"
            else -> "${(v / 50).roundToInt().coerceAtLeast(1) * 50} g"
        }
    }
}
