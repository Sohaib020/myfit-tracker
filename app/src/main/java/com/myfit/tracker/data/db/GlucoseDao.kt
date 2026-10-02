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
}
