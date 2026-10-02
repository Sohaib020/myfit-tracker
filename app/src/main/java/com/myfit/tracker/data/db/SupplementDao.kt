package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(e: Supplement): Long
    @Update suspend fun update(e: Supplement)
    @Query("SELECT * FROM supplement WHERE id = :id") suspend fun get(id: Long): Supplement?
    @Query("SELECT * FROM supplement ORDER BY id DESC") fun observeAllRaw(): Flow<List<Supplement>>

    @Query("SELECT * FROM supplement WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE") fun observeActive(): Flow<List<Supplement>>
    @Query("SELECT * FROM supplement WHERE archivedAt IS NOT NULL ORDER BY name COLLATE NOCASE") fun observeArchived(): Flow<List<Supplement>>
}
