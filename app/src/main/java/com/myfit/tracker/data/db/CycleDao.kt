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
}
