package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: VitalReading): Long
    @Update suspend fun update(e: VitalReading)
    @Query("SELECT * FROM vital_reading WHERE id = :id") suspend fun get(id: Long): VitalReading?
    @Query("SELECT * FROM vital_reading ORDER BY id DESC") fun observeAllRaw(): Flow<List<VitalReading>>
    /** Active readings of one [VitalType], newest first. */
    @Query("SELECT * FROM vital_reading WHERE type = :type AND deletedAt IS NULL ORDER BY takenAt DESC LIMIT :limit")
    fun observeByType(type: String, limit: Int): Flow<List<VitalReading>>
    @Query("UPDATE vital_reading SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)
}
