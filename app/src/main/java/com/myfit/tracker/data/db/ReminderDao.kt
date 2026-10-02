package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: Reminder): Long
    @Update suspend fun update(e: Reminder)
    @Query("SELECT * FROM reminder WHERE id = :id") suspend fun get(id: Long): Reminder?
    @Query("SELECT * FROM reminder ORDER BY id DESC") fun observeAllRaw(): Flow<List<Reminder>>
}
