package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: CycleDay): Long
    @Update suspend fun update(e: CycleDay)
    @Query("SELECT * FROM cycle_day WHERE id = :id") suspend fun get(id: Long): CycleDay?
    @Query("SELECT * FROM cycle_day ORDER BY id DESC") fun observeAllRaw(): Flow<List<CycleDay>>

    /** All days, oldest first (localDate is ISO yyyy-MM-dd so text order = date order). */
    @Query("SELECT * FROM cycle_day ORDER BY localDate ASC") fun observeAll(): Flow<List<CycleDay>>
    @Query("SELECT * FROM cycle_day ORDER BY localDate ASC") suspend fun all(): List<CycleDay>
    @Query("SELECT * FROM cycle_day WHERE localDate = :localDate LIMIT 1") suspend fun byDate(localDate: String): CycleDay?
    @Query("SELECT * FROM cycle_day WHERE localDate BETWEEN :from AND :to ORDER BY localDate ASC") suspend fun range(from: String, to: String): List<CycleDay>
    @Query("DELETE FROM cycle_day WHERE localDate = :localDate") suspend fun deleteByDate(localDate: String)
}
