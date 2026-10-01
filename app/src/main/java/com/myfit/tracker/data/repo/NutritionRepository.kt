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

    fun search(q: String) = dao.search(q.trim())
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
        val arr = JSONArray(context.assets.open("foods_pk.json").bufferedReader().use { it.readText() })
        if (dao.seededCount() >= arr.length()) return@withContext
        val now = Clock.now()
        val foods = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Food(
                uuid = "pkfood:" + o.getString("id"), name = o.getString("name"),
                servingSize = o.getDouble("serving"), servingUnit = o.getString("unit"), servingGrams = o.optDouble("grams").takeIf { !it.isNaN() },
                calories = o.getDouble("kcal"), proteinG = o.getDouble("p"), carbsG = o.getDouble("c"), fatG = o.getDouble("f"),
                fiberG = o.optDouble("fiber").takeIf { !it.isNaN() },
                source = NutritionSource.DATABASE, sourceRef = "MyFit typical values — varies by recipe and portion",
                createdAt = now, updatedAt = now,
            )
        }
        dao.insertFoods(foods)
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
