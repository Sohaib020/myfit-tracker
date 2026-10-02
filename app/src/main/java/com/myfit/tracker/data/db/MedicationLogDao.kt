package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: MedicationLog): Long
    @Update suspend fun update(e: MedicationLog)
    @Query("SELECT * FROM medication_log WHERE id = :id") suspend fun get(id: Long): MedicationLog?
    @Query("SELECT * FROM medication_log ORDER BY id DESC") fun observeAllRaw(): Flow<List<MedicationLog>>
}
