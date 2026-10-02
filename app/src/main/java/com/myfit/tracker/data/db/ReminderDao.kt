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

    @Query("SELECT * FROM reminder ORDER BY type, timeMin, id") fun observeAll(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminder WHERE enabled = 1") suspend fun enabledNow(): List<Reminder>
    @Query("SELECT * FROM reminder") suspend fun allNow(): List<Reminder>
    @Query("SELECT COUNT(*) FROM reminder") suspend fun count(): Int
    @Query("DELETE FROM reminder WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE reminder SET enabled = :enabled, updatedAt = :at WHERE id = :id") suspend fun setEnabled(id: Long, enabled: Boolean, at: Long)
}
