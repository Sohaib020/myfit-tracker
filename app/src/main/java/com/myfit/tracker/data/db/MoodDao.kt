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
}
