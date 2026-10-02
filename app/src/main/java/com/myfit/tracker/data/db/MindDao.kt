package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MindDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: MindSession): Long
    @Update suspend fun update(e: MindSession)
    @Query("SELECT * FROM mind_session WHERE id = :id") suspend fun get(id: Long): MindSession?
    @Query("SELECT * FROM mind_session ORDER BY id DESC") fun observeAllRaw(): Flow<List<MindSession>>
}
