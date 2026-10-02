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
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIgnore(e: BloodPressure): Long
    /** Active (not deleted) readings taken at or after [from], newest first. */
    @Query("SELECT * FROM blood_pressure WHERE deletedAt IS NULL AND takenAt >= :from ORDER BY takenAt DESC")
    fun observeSince(from: Long): Flow<List<BloodPressure>>
    @Query("SELECT * FROM blood_pressure WHERE deletedAt IS NULL ORDER BY takenAt DESC LIMIT 1")
    suspend fun latest(): BloodPressure?
    @Query("UPDATE blood_pressure SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)
}
