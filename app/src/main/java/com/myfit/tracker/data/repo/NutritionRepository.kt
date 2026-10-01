package com.myfit.tracker.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.db.Food
import com.myfit.tracker.data.db.Meal
import com.myfit.tracker.data.db.MealItem
import com.myfit.tracker.data.db.NutritionSource
import com.myfit.tracker.data.db.SavedMeal
import com.myfit.tracker.data.db.SavedMealItem
import com.myfit.tracker.domain.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId

/** One line to log: nutrition is given per serving, [quantity] servings were eaten. */
data class LogLine(
    val name: String, val quantity: Double, val servingSize: Double, val servingUnit: String,
    val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double?,
    val source: String, val foodId: Long? = null,
)

class NutritionRepository(private val db: AppDatabase, private val context: Context) {
    private val dao = db.nutritionDao()

    /** Extra info for built-in foods: category, search aliases, photo and how solid the numbers are. */
    data class FoodMeta(val category: String, val aliases: List<String>, val photo: String?, val reference: Boolean)
    data class Credit(val artist: String, val license: String, val url: String)

    private val metaMap: Map<String, FoodMeta> by lazy {
        val credits = runCatching { org.json.JSONObject(context.assets.open("foodimg/credits.json").bufferedReader().use { it.readText() }) }.getOrNull()
        val arr = JSONArray(context.assets.open("foods_pk.json").bufferedReader().use { it.readText() })
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val wiki = o.optString("wiki").takeIf { it.isNotBlank() && it != "null" }
            val slug = wiki?.lowercase()?.replace(Regex("[^a-z0-9]+"), "_")?.trim('_')
            val al = o.optJSONArray("aliases")?.let { a -> (0 until a.length()).map { a.getString(it).lowercase() } } ?: emptyList()
            ("pkfood:" + o.getString("id")) to FoodMeta(o.optString("category", "Basics"), al, slug?.takeIf { credits?.has(it) == true }, o.optString("basis") == "reference")
        }
    }
    private val creditMap: org.json.JSONObject? by lazy { runCatching { org.json.JSONObject(context.assets.open("foodimg/credits.json").bufferedReader().use { it.readText() }) }.getOrNull() }

    fun meta(f: Food): FoodMeta? = metaMap[f.uuid]
    fun credit(slug: String): Credit? = creditMap?.optJSONObject(slug)?.let { Credit(it.optString("artist"), it.optString("license"), it.optString("url")) }
    val categories: List<String> get() = listOf("Breakfast", "Breads", "Rice", "Curries", "Daal & Beans", "BBQ & Kebabs", "Vegetables", "Street food", "Fast food", "Restaurant", "Indian", "Sweets", "Drinks", "Fruit", "Dairy & Eggs", "Meat & Fish", "Snacks & Nuts", "Basics")

    /** Name/brand search plus Roman-Urdu aliases ("kardi", "nehari", "anda"). */
    fun search(q: String): kotlinx.coroutines.flow.Flow<List<Food>> {
        val t = q.trim().lowercase()
        val aliasHits = if (t.length < 2) emptyList() else metaMap.filter { (_, m) -> m.aliases.any { it.contains(t) } }.keys.toList().take(40)
        if (aliasHits.isEmpty()) return dao.search(q.trim())
        return kotlinx.coroutines.flow.combine(dao.search(q.trim()), dao.byUuids(aliasHits)) { a, b -> (a + b.filter { x -> a.none { it.id == x.id } }).take(80) }
    }
    fun inCategory(cat: String): kotlinx.coroutines.flow.Flow<List<Food>> = dao.builtIn().map { l -> l.filter { metaMap[it.uuid]?.category == cat } }
    val recent = dao.recent()
    val savedMeals = dao.savedMeals()
    val savedSummaries = dao.savedSummaries()
    fun mealsOn(d: LocalDate) = dao.mealsOn(Clock.dateKey(d))
    fun itemsOn(d: LocalDate) = dao.itemsOn(Clock.dateKey(d))
    fun dailyTotals(from: LocalDate, to: LocalDate) = dao.dailyTotals(Clock.dateKey(from), Clock.dateKey(to))
    suspend fun food(id: Long) = dao.food(id)
    suspend fun foodByBarcode(code: String) = dao.foodByBarcode(code)

    /** Seeds the built-in food list (typical values for common Pakistani dishes and staples). Idempotent. */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val text = context.assets.open("foods_pk.json").bufferedReader().use { it.readText() }
        val prefs = context.getSharedPreferences("food_seed", Context.MODE_PRIVATE)
        val hash = text.hashCode()
        if (prefs.getInt("hash", 0) == hash && dao.seededCount() > 0) return@withContext
        val arr = JSONArray(text)
        val now = Clock.now()
        val foods = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ref = if (o.optString("basis") == "reference") "Based on published food-composition data — recipes and portions vary"
                      else "MyFit typical values — varies by recipe and portion"
            Food(
                uuid = "pkfood:" + o.getString("id"), name = o.getString("name"),
                servingSize = o.getDouble("serving"), servingUnit = o.getString("unit"), servingGrams = o.optDouble("grams").takeIf { !it.isNaN() },
                calories = o.getDouble("kcal"), proteinG = o.getDouble("p"), carbsG = o.getDouble("c"), fatG = o.getDouble("f"),
                fiberG = o.optDouble("fiber").takeIf { !it.isNaN() },
                source = NutritionSource.DATABASE, sourceRef = ref,
                createdAt = now, updatedAt = now,
            )
        }
        db.withTransaction {
            dao.insertFoods(foods)       // new items
            foods.forEach { f ->         // updated values for items that already existed (logged meals keep their own copy)
                dao.refreshSeeded(f.uuid, f.name, f.servingSize, f.servingUnit, f.servingGrams, f.calories, f.proteinG, f.carbsG, f.fatG, f.fiberG, f.sourceRef ?: "", now)
            }
        }
        prefs.edit().putInt("hash", hash).apply()
    }

    suspend fun addFood(f: Food): Long = dao.insertFood(f)

    /** Adds lines to the day's meal of [mealType] (creating the meal if needed). */
    suspend fun log(date: LocalDate, mealType: String, lines: List<LogLine>, at: Long = Clock.now()) {
        if (lines.isEmpty()) return
        db.withTransaction {
            val key = Clock.dateKey(date)
            val meal = dao.mealOf(key, mealType)
            val mealId = meal?.id ?: run {
                val now = Clock.now()
                val eaten = if (date == Clock.today()) at else date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                dao.insertMeal(Meal(mealType = mealType, eatenAt = eaten, zoneId = Clock.zone().id, localDate = key, createdAt = now, updatedAt = now))
            }
            val now = Clock.now()
            dao.insertItems(lines.map {
                MealItem(
                    mealId = mealId, foodId = it.foodId, foodName = it.name, quantity = it.quantity, servingSize = it.servingSize, servingUnit = it.servingUnit,
                    caloriesPerServing = it.kcal, proteinPerServing = it.protein, carbsPerServing = it.carbs, fatPerServing = it.fat,
                    fiberPerServing = it.fiber, source = it.source, createdAt = now, updatedAt = now,
                )
            })
        }
    }

    suspend fun updateQuantity(item: MealItem, q: Double) = dao.updateItem(item.copy(quantity = q, updatedAt = Clock.now()))

    suspend fun deleteItem(item: MealItem) = db.withTransaction {
        dao.deleteItem(item.id)
        if (dao.itemCount(item.mealId) == 0) dao.softDeleteMeal(item.mealId, Clock.now())
    }

    /** Saves the given logged items as a reusable meal (items without a food get a private food entry). */
    suspend fun saveAsMeal(name: String, mealType: String?, items: List<MealItem>) = db.withTransaction {
        val now = Clock.now()
        val id = dao.insertSaved(SavedMeal(name = name, defaultMealType = mealType, createdAt = now, updatedAt = now))
        dao.insertSavedItems(items.map { i ->
            val fid = i.foodId ?: dao.insertFood(
                Food(
                    name = i.foodName, servingSize = i.servingSize, servingUnit = i.servingUnit, calories = i.caloriesPerServing,
                    proteinG = i.proteinPerServing, carbsG = i.carbsPerServing, fatG = i.fatPerServing, fiberG = i.fiberPerServing,
                    source = i.source, sourceRef = "Saved from a meal", archivedAt = now, createdAt = now, updatedAt = now,
                )
            )
            SavedMealItem(savedMealId = id, foodId = fid, quantity = i.quantity)
        })
    }

    suspend fun logSaved(savedId: Long, date: LocalDate, mealType: String) {
        val lines = dao.savedItems(savedId).mapNotNull { si ->
            val f = dao.food(si.foodId) ?: return@mapNotNull null
            LogLine(f.name, si.quantity, f.servingSize, f.servingUnit, f.calories, f.proteinG, f.carbsG, f.fatG, f.fiberG, f.source, f.id)
        }
        log(date, mealType, lines)
    }

    suspend fun deleteSaved(id: Long) = dao.archiveSaved(id, Clock.now())
}
