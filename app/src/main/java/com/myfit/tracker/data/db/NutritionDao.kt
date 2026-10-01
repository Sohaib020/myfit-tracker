package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {
    // ---- foods
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoods(f: List<Food>): List<Long>

    @Insert
    suspend fun insertFood(f: Food): Long

    @Update
    suspend fun updateFood(f: Food)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun food(id: Long): Food?

    @Query("SELECT * FROM food WHERE barcode = :code AND archivedAt IS NULL LIMIT 1")
    suspend fun foodByBarcode(code: String): Food?

    @Query("SELECT COUNT(*) FROM food WHERE uuid LIKE 'pkfood:%'")
    suspend fun seededCount(): Int

    @Query("""SELECT * FROM food WHERE archivedAt IS NULL AND (name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%')
              ORDER BY CASE WHEN name LIKE :q || '%' THEN 0 ELSE 1 END, length(name) LIMIT 60""")
    fun search(q: String): Flow<List<Food>>

    /** Foods logged most recently (for the "Recent" list). */
    @Query("""SELECT f.* FROM food f JOIN meal_item i ON i.foodId = f.id JOIN meal m ON m.id = i.mealId
              WHERE m.deletedAt IS NULL AND f.archivedAt IS NULL GROUP BY f.id ORDER BY MAX(m.eatenAt) DESC LIMIT 20""")
    fun recent(): Flow<List<Food>>

    // ---- meals
    @Insert suspend fun insertMeal(m: Meal): Long
    @Insert suspend fun insertItems(items: List<MealItem>)
    @Update suspend fun updateItem(i: MealItem)

    @Query("DELETE FROM meal_item WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE meal SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteMeal(id: Long, now: Long)

    @Query("SELECT * FROM meal WHERE localDate = :date AND deletedAt IS NULL ORDER BY eatenAt")
    fun mealsOn(date: String): Flow<List<Meal>>

    @Query("SELECT i.* FROM meal_item i JOIN meal m ON m.id = i.mealId WHERE m.localDate = :date AND m.deletedAt IS NULL ORDER BY i.id")
    fun itemsOn(date: String): Flow<List<MealItem>>

    @Query("SELECT * FROM meal WHERE localDate = :date AND mealType = :type AND deletedAt IS NULL ORDER BY eatenAt LIMIT 1")
    suspend fun mealOf(date: String, type: String): Meal?

    @Query("SELECT COUNT(*) FROM meal_item WHERE mealId = :mealId")
    suspend fun itemCount(mealId: Long): Int

    @Query("""SELECT m.localDate AS date, SUM(i.quantity * i.caloriesPerServing) AS kcal, SUM(i.quantity * i.proteinPerServing) AS protein
              FROM meal m JOIN meal_item i ON i.mealId = m.id WHERE m.deletedAt IS NULL AND m.localDate BETWEEN :from AND :to GROUP BY m.localDate""")
    fun dailyTotals(from: String, to: String): Flow<List<DayTotal>>

    // ---- saved meals
    @Insert suspend fun insertSaved(s: SavedMeal): Long
    @Insert suspend fun insertSavedItems(items: List<SavedMealItem>)

    @Query("SELECT * FROM saved_meal WHERE archivedAt IS NULL ORDER BY name")
    fun savedMeals(): Flow<List<SavedMeal>>

    @Query("SELECT * FROM saved_meal_item WHERE savedMealId = :id")
    suspend fun savedItems(id: Long): List<SavedMealItem>

    @Query("SELECT s.id AS savedId, COUNT(i.id) AS items, SUM(i.quantity * f.calories) AS kcal FROM saved_meal s JOIN saved_meal_item i ON i.savedMealId = s.id JOIN food f ON f.id = i.foodId WHERE s.archivedAt IS NULL GROUP BY s.id")
    fun savedSummaries(): Flow<List<SavedSummary>>

    @Query("UPDATE saved_meal SET archivedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun archiveSaved(id: Long, now: Long)
}

data class DayTotal(val date: String, val kcal: Double, val protein: Double)
data class SavedSummary(val savedId: Long, val items: Int, val kcal: Double)

/** Inserts records imported from Health Connect; the "hc:<id>" uuid makes re-syncs idempotent. */
@androidx.room.Dao
interface HealthImportDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insertWeight(e: WeightEntry): Long
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insertWater(e: WaterEntry): Long
}
