package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GlucoseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: GlucoseReading): Long
    @Update suspend fun update(e: GlucoseReading)
    @Query("SELECT * FROM glucose_reading WHERE id = :id") suspend fun get(id: Long): GlucoseReading?
    @Query("SELECT * FROM glucose_reading ORDER BY id DESC") fun observeAllRaw(): Flow<List<GlucoseReading>>

    /** Health Connect imports use "hc:<id>" uuids, so re-imports are ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(e: GlucoseReading): Long

    @Query("SELECT * FROM glucose_reading WHERE deletedAt IS NULL AND takenAt >= :from ORDER BY takenAt")
    fun observeSince(from: Long): Flow<List<GlucoseReading>>

    @Query("SELECT * FROM glucose_reading WHERE deletedAt IS NULL AND takenAt >= :from AND takenAt < :to ORDER BY takenAt")
    suspend fun between(from: Long, to: Long): List<GlucoseReading>

    @Query("SELECT * FROM glucose_reading WHERE deletedAt IS NULL ORDER BY takenAt DESC LIMIT 1")
    fun observeLatest(): Flow<GlucoseReading?>

    @Query("UPDATE glucose_reading SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE glucose_reading SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    /** Total carbs per local day from the food diary (meal items snapshot carbs per serving). */
    @Query("""SELECT m.localDate AS date, SUM(i.quantity * i.carbsPerServing) AS carbs
              FROM meal m JOIN meal_item i ON i.mealId = m.id
              WHERE m.deletedAt IS NULL AND m.localDate BETWEEN :from AND :to GROUP BY m.localDate""")
    fun dailyCarbs(from: String, to: String): Flow<List<DayCarbs>>

    @Query("""SELECT m.localDate AS date, SUM(i.quantity * i.carbsPerServing) AS carbs
              FROM meal m JOIN meal_item i ON i.mealId = m.id
              WHERE m.deletedAt IS NULL AND m.localDate BETWEEN :from AND :to GROUP BY m.localDate""")
    suspend fun dailyCarbsOnce(from: String, to: String): List<DayCarbs>

    /** Carbs of each meal eaten in a time window (for linking after-meal readings to the meal before them). */
    @Query("""SELECT m.id AS mealId, m.eatenAt AS eatenAt, m.mealType AS mealType, SUM(i.quantity * i.carbsPerServing) AS carbs
              FROM meal m JOIN meal_item i ON i.mealId = m.id
              WHERE m.deletedAt IS NULL AND m.eatenAt BETWEEN :from AND :to GROUP BY m.id ORDER BY m.eatenAt""")
    fun mealCarbs(from: Long, to: Long): Flow<List<MealCarbs>>
}

data class DayCarbs(val date: String, val carbs: Double)
data class MealCarbs(val mealId: Long, val eatenAt: Long, val mealType: String, val carbs: Double)
