package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BloodPressureDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: BloodPressure): Long
    @Update suspend fun update(e: BloodPressure)
    @Query("SELECT * FROM blood_pressure WHERE id = :id") suspend fun get(id: Long): BloodPressure?
    @Query("SELECT * FROM blood_pressure ORDER BY id DESC") fun observeAllRaw(): Flow<List<BloodPressure>>
}
