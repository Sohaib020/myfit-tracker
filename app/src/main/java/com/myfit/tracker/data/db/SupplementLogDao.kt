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

    @Query("SELECT * FROM supplement_log WHERE deletedAt IS NULL AND localDate >= :fromDate ORDER BY loggedAt DESC") fun observeSince(fromDate: String): Flow<List<SupplementLog>>
    @Query("SELECT * FROM supplement_log WHERE deletedAt IS NULL AND taken = 1 AND supplementId = :supplementId ORDER BY localDate DESC") suspend fun takenFor(supplementId: Long): List<SupplementLog>
    @Query("UPDATE supplement_log SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: Long, at: Long)
}
