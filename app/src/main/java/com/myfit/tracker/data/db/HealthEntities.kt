package com.myfit.tracker.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * v3 — data imported from Health Connect (Samsung Health, Galaxy Watch, other apps) and the phone's
 * own step sensor. Imported data is kept in its own tables, never mixed into manual entries, so the
 * source of every number is always known and a re-sync can never duplicate or overwrite your logs.
 */

/** Per-day aggregates computed BY Health Connect (it de-duplicates overlapping sources itself). */
@Entity(tableName = "hc_daily")
data class HcDaily(
    @PrimaryKey val localDate: String,
    val steps: Long?,
    val distanceM: Double?,
    val activeKcal: Double?,
    val totalKcal: Double?,
    val floors: Double?,
    val restingHr: Long?,
    val avgHr: Long?,
    val minHr: Long?,
    val maxHr: Long?,
    val hrvMs: Double?,
    val spo2Pct: Double?,
    val syncedAt: Long,
)

/** A workout/activity session detected or recorded by the watch, Samsung Health or another app. */
@Entity(
    tableName = "hc_session",
    indices = [Index(value = ["externalId"], unique = true), Index("localDate")]
)
data class HcSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,          // Health Connect record id — the de-dup key
    val exerciseType: Int,
    val title: String?,
    val startAt: Long,
    val endAt: Long,
    val zoneId: String,
    val localDate: String,
    val distanceM: Double?,
    val activeKcal: Double?,
    val steps: Long?,
    val avgHr: Long?,
    val maxHr: Long?,
    val segments: String,            // JSON array of {type, reps, start, end} (e.g. watch-counted reps)
    val sourcePackage: String,
    val syncedAt: Long,
)

@Entity(
    tableName = "hc_sleep",
    indices = [Index(value = ["externalId"], unique = true), Index("localDate")]
)
data class HcSleep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,
    val startAt: Long,
    val endAt: Long,
    val zoneId: String,
    val localDate: String,           // wake-up day
    val deepMin: Long?,
    val remMin: Long?,
    val lightMin: Long?,
    val awakeMin: Long?,
    val sourcePackage: String,
    val syncedAt: Long,
)

/** Raw readings of the phone's cumulative step counter (resets on reboot). */
@Entity(tableName = "phone_step_snapshot", indices = [Index("localDate")])
data class PhoneStepSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val counter: Long,
    val zoneId: String,
    val localDate: String,
)

@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,                // "user" | "pip"
    val text: String,
    val source: String,              // "user" | "data" (answered offline from your logs) | "online" (Gemini) | "local"
    val createdAt: Long,
)

@Dao
interface HealthDao {
    @Upsert suspend fun upsertDaily(list: List<HcDaily>)

    @Query("SELECT * FROM hc_daily WHERE localDate BETWEEN :from AND :to ORDER BY localDate")
    fun observeDaily(from: String, to: String): Flow<List<HcDaily>>

    @Query("SELECT * FROM hc_daily WHERE localDate BETWEEN :from AND :to ORDER BY localDate")
    suspend fun daily(from: String, to: String): List<HcDaily>

    @Query("SELECT * FROM hc_daily ORDER BY localDate")
    suspend fun allDaily(): List<HcDaily>

    @Query("SELECT * FROM hc_session WHERE externalId = :ext")
    suspend fun sessionByExternal(ext: String): HcSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSession(s: HcSession): Long

    @Query("DELETE FROM hc_session WHERE startAt >= :from AND startAt < :to AND externalId NOT IN (:keep)")
    suspend fun deleteSessionsNotIn(from: Long, to: Long, keep: List<String>)

    @Query("DELETE FROM hc_session WHERE startAt >= :from AND startAt < :to")
    suspend fun deleteSessionsIn(from: Long, to: Long)

    @Query("SELECT * FROM hc_session WHERE localDate BETWEEN :from AND :to ORDER BY startAt DESC")
    fun observeSessions(from: String, to: String): Flow<List<HcSession>>

    @Query("SELECT * FROM hc_session ORDER BY startAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<HcSession>>

    @Query("SELECT * FROM hc_session WHERE id = :id")
    fun observeSession(id: Long): Flow<HcSession?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSleep(s: HcSleep): Long

    @Query("DELETE FROM hc_sleep WHERE endAt >= :from AND endAt < :to AND externalId NOT IN (:keep)")
    suspend fun deleteSleepNotIn(from: Long, to: Long, keep: List<String>)

    @Query("DELETE FROM hc_sleep WHERE endAt >= :from AND endAt < :to")
    suspend fun deleteSleepIn(from: Long, to: Long)

    @Query("SELECT * FROM hc_sleep WHERE localDate BETWEEN :from AND :to ORDER BY endAt")
    fun observeSleep(from: String, to: String): Flow<List<HcSleep>>

    @Query("SELECT * FROM hc_sleep ORDER BY endAt")
    suspend fun allSleep(): List<HcSleep>

    @Query("SELECT * FROM hc_session ORDER BY startAt")
    suspend fun allSessions(): List<HcSession>

    // phone sensor
    @Insert suspend fun insertSnapshot(s: PhoneStepSnapshot)

    @Query("SELECT * FROM phone_step_snapshot WHERE at >= :from ORDER BY at")
    suspend fun snapshotsSince(from: Long): List<PhoneStepSnapshot>

    @Query("SELECT * FROM phone_step_snapshot WHERE at >= :from ORDER BY at")
    fun observeSnapshotsSince(from: Long): Flow<List<PhoneStepSnapshot>>

    @Query("DELETE FROM phone_step_snapshot WHERE at < :before")
    suspend fun pruneSnapshots(before: Long)

    // chat
    @Insert suspend fun insertChat(m: ChatMessage): Long

    @Query("SELECT * FROM chat_message ORDER BY createdAt, id")
    fun observeChat(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_message ORDER BY createdAt DESC, id DESC LIMIT :n")
    suspend fun lastChat(n: Int): List<ChatMessage>

    @Query("DELETE FROM chat_message")
    suspend fun clearChat()

    @Transaction
    suspend fun replaceSessions(from: Long, to: Long, sessions: List<HcSession>) {
        if (sessions.isEmpty()) deleteSessionsIn(from, to) else deleteSessionsNotIn(from, to, sessions.map { it.externalId })
        sessions.forEach { s ->
            val existing = sessionByExternal(s.externalId)
            insertSession(if (existing != null) s.copy(id = existing.id) else s)
        }
    }

    @Transaction
    suspend fun replaceSleep(from: Long, to: Long, list: List<HcSleep>) {
        if (list.isEmpty()) deleteSleepIn(from, to) else deleteSleepNotIn(from, to, list.map { it.externalId })
        list.forEach { insertSleep(it) }
    }
}
