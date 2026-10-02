package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: Medication): Long
    @Update suspend fun update(e: Medication)
    @Query("SELECT * FROM medication WHERE id = :id") suspend fun get(id: Long): Medication?
    @Query("SELECT * FROM medication ORDER BY id DESC") fun observeAllRaw(): Flow<List<Medication>>
}
