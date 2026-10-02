package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: MoodEntry): Long
    @Update suspend fun update(e: MoodEntry)
    @Query("SELECT * FROM mood_entry WHERE id = :id") suspend fun get(id: Long): MoodEntry?
    @Query("SELECT * FROM mood_entry ORDER BY id DESC") fun observeAllRaw(): Flow<List<MoodEntry>>

    /** Live (not deleted) entries on or after [fromDate] (ISO yyyy-MM-dd), newest first. */
    @Query("SELECT * FROM mood_entry WHERE deletedAt IS NULL AND localDate >= :fromDate ORDER BY loggedAt DESC")
    fun observeSince(fromDate: String): Flow<List<MoodEntry>>

    /** Live entries for one day, newest first. */
    @Query("SELECT * FROM mood_entry WHERE deletedAt IS NULL AND localDate = :date ORDER BY loggedAt DESC")
    fun observeDay(date: String): Flow<List<MoodEntry>>

    /** All live entries, oldest first (used for pattern analysis). */
    @Query("SELECT * FROM mood_entry WHERE deletedAt IS NULL ORDER BY loggedAt")
    suspend fun allLive(): List<MoodEntry>

    @Query("UPDATE mood_entry SET deletedAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)
}
