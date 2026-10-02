package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FastingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: FastingSession): Long
    @Update suspend fun update(e: FastingSession)
    @Query("SELECT * FROM fasting_session WHERE id = :id") suspend fun get(id: Long): FastingSession?
    @Query("SELECT * FROM fasting_session ORDER BY id DESC") fun observeAllRaw(): Flow<List<FastingSession>>

    @Query("SELECT * FROM fasting_session WHERE deletedAt IS NULL ORDER BY startAt DESC") fun observeAll(): Flow<List<FastingSession>>
    @Query("SELECT * FROM fasting_session WHERE deletedAt IS NULL AND endAt IS NULL ORDER BY startAt DESC LIMIT 1") suspend fun activeNow(): FastingSession?
    @Query("UPDATE fasting_session SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: Long, at: Long)
}
