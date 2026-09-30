package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfile?

    @Upsert
    suspend fun upsert(profile: UserProfile)
}

@Dao
interface TargetDao {
    @Insert
    suspend fun insert(t: TargetHistory): Long

    @Query("SELECT * FROM target_history ORDER BY effectiveFrom ASC, id ASC")
    fun observeAll(): Flow<List<TargetHistory>>

    @Query("SELECT * FROM target_history ORDER BY effectiveFrom ASC, id ASC")
    suspend fun getAll(): List<TargetHistory>

    /** Replace a same-day change instead of stacking versions for one date. */
    @Query("DELETE FROM target_history WHERE type = :type AND effectiveFrom = :date")
    suspend fun deleteForDate(type: String, date: String)
}

@Dao
interface WaterDao {
    @Insert suspend fun insert(e: WaterEntry): Long
    @Update suspend fun update(e: WaterEntry)

    @Query("SELECT * FROM water_entry WHERE id = :id")
    suspend fun get(id: Long): WaterEntry?

    @Query("SELECT * FROM water_entry WHERE deletedAt IS NULL AND localDate = :date ORDER BY loggedAt")
    fun observeDay(date: String): Flow<List<WaterEntry>>

    @Query("SELECT * FROM water_entry WHERE deletedAt IS NULL AND localDate BETWEEN :from AND :to ORDER BY loggedAt")
    fun observeRange(from: String, to: String): Flow<List<WaterEntry>>

    @Query("UPDATE water_entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("SELECT DISTINCT localDate FROM water_entry WHERE deletedAt IS NULL AND localDate BETWEEN :from AND :to")
    fun observeDaysWithData(from: String, to: String): Flow<List<String>>
}

@Dao
interface WeightDao {
    @Insert suspend fun insert(e: WeightEntry): Long
    @Update suspend fun update(e: WeightEntry)

    @Query("SELECT * FROM weight_entry WHERE id = :id")
    suspend fun get(id: Long): WeightEntry?

    @Query("SELECT * FROM weight_entry WHERE deletedAt IS NULL AND localDate = :date ORDER BY loggedAt")
    fun observeDay(date: String): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entry WHERE deletedAt IS NULL AND localDate BETWEEN :from AND :to ORDER BY loggedAt")
    fun observeRange(from: String, to: String): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entry WHERE deletedAt IS NULL ORDER BY loggedAt")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entry WHERE deletedAt IS NULL ORDER BY loggedAt DESC LIMIT 1")
    fun observeLatest(): Flow<WeightEntry?>

    @Query("UPDATE weight_entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface MeasurementDao {
    @Insert suspend fun insert(e: BodyMeasurement): Long
    @Update suspend fun update(e: BodyMeasurement)

    @Query("SELECT * FROM body_measurement WHERE id = :id")
    suspend fun get(id: Long): BodyMeasurement?

    @Query("SELECT * FROM body_measurement WHERE deletedAt IS NULL AND localDate = :date ORDER BY loggedAt")
    fun observeDay(date: String): Flow<List<BodyMeasurement>>

    @Query("SELECT * FROM body_measurement WHERE deletedAt IS NULL ORDER BY loggedAt")
    fun observeAll(): Flow<List<BodyMeasurement>>

    @Query("UPDATE body_measurement SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface SleepDao {
    @Insert suspend fun insert(e: SleepEntry): Long
    @Update suspend fun update(e: SleepEntry)

    @Query("SELECT * FROM sleep_entry WHERE id = :id")
    suspend fun get(id: Long): SleepEntry?

    @Query("SELECT * FROM sleep_entry WHERE deletedAt IS NULL AND localDate = :date ORDER BY startAt")
    fun observeDay(date: String): Flow<List<SleepEntry>>

    @Query("SELECT * FROM sleep_entry WHERE deletedAt IS NULL AND localDate BETWEEN :from AND :to ORDER BY startAt")
    fun observeRange(from: String, to: String): Flow<List<SleepEntry>>

    @Query("UPDATE sleep_entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface ActivityDao {
    @Insert suspend fun insert(e: ActivityEntry): Long
    @Update suspend fun update(e: ActivityEntry)

    @Query("SELECT * FROM activity_entry WHERE id = :id")
    suspend fun get(id: Long): ActivityEntry?

    @Query("SELECT * FROM activity_entry WHERE deletedAt IS NULL AND localDate = :date ORDER BY loggedAt")
    fun observeDay(date: String): Flow<List<ActivityEntry>>

    @Query("SELECT * FROM activity_entry WHERE deletedAt IS NULL AND localDate BETWEEN :from AND :to ORDER BY loggedAt")
    fun observeRange(from: String, to: String): Flow<List<ActivityEntry>>

    @Query("UPDATE activity_entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface CheckInDao {
    @Insert suspend fun insert(e: DailyCheckIn): Long
    @Update suspend fun update(e: DailyCheckIn)

    @Query("SELECT * FROM daily_checkin WHERE id = :id")
    suspend fun get(id: Long): DailyCheckIn?

    @Query("SELECT * FROM daily_checkin WHERE deletedAt IS NULL AND localDate = :date ORDER BY loggedAt")
    fun observeDay(date: String): Flow<List<DailyCheckIn>>

    @Query("UPDATE daily_checkin SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface NoteDao {
    @Insert suspend fun insert(e: DailyNote): Long
    @Update suspend fun update(e: DailyNote)

    @Query("SELECT * FROM daily_note WHERE id = :id")
    suspend fun get(id: Long): DailyNote?

    @Query("SELECT * FROM daily_note WHERE deletedAt IS NULL AND localDate = :date ORDER BY loggedAt")
    fun observeDay(date: String): Flow<List<DailyNote>>

    @Query("UPDATE daily_note SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<Exercise>): List<Long>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Query("SELECT * FROM exercise WHERE archivedAt IS NULL ORDER BY name")
    fun observeActive(): Flow<List<Exercise>>
}
