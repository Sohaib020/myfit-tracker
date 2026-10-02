package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplementLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: SupplementLog): Long
    @Update suspend fun update(e: SupplementLog)
    @Query("SELECT * FROM supplement_log WHERE id = :id") suspend fun get(id: Long): SupplementLog?
    @Query("SELECT * FROM supplement_log ORDER BY id DESC") fun observeAllRaw(): Flow<List<SupplementLog>>
}
