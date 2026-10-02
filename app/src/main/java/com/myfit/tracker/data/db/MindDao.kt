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

    /** Sessions on or after [fromDate] (ISO yyyy-MM-dd), newest first. */
    @Query("SELECT * FROM mind_session WHERE localDate >= :fromDate ORDER BY startedAt DESC")
    fun observeSince(fromDate: String): Flow<List<MindSession>>

    /** Distinct days that have at least one session, newest first (for streaks). */
    @Query("SELECT DISTINCT localDate FROM mind_session ORDER BY localDate DESC LIMIT 400")
    fun observeDays(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM mind_session")
    fun observeCount(): Flow<Int>
}
