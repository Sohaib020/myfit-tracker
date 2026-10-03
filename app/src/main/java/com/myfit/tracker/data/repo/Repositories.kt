package com.myfit.tracker.data.repo

import androidx.room.withTransaction
import com.myfit.tracker.data.db.ActivityEntry
import com.myfit.tracker.data.db.ActivitySource
import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.db.BodyMeasurement
import com.myfit.tracker.data.db.DailyCheckIn
import com.myfit.tracker.data.db.DailyNote
import com.myfit.tracker.data.db.SleepEntry
import com.myfit.tracker.data.db.TargetHistory
import com.myfit.tracker.data.db.UserProfile
import com.myfit.tracker.data.db.WaterEntry
import com.myfit.tracker.data.db.WeightEntry
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Targets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Timestamp triple every entry receives automatically. */
data class Stamp(val at: Long, val zoneId: String, val localDate: String) {
    companion object {
        fun now(): Stamp = of(Clock.now())
        fun of(epochMs: Long): Stamp {
            val z = Clock.zone()
            return Stamp(epochMs, z.id, Clock.dateKey(Clock.localDateOf(epochMs, z)))
        }
    }
}

class ProfileRepository(private val db: AppDatabase) {
    val profile: Flow<UserProfile?> = db.profileDao().observe()

    val targets: Flow<List<Targets.Row>> = db.targetDao().observeAll().map { rows ->
        rows.map { Targets.Row(it.type, it.value, LocalDate.parse(it.effectiveFrom), it.id) }
    }

    /** First-launch setup: profile + first target versions + starting weight, atomically. */
    suspend fun createProfile(profile: UserProfile, targets: Map<String, Double>) {
        val s = Stamp.now()
        db.withTransaction {
            db.profileDao().upsert(profile.copy(createdAt = s.at, updatedAt = s.at))
            targets.forEach { (type, v) ->
                db.targetDao().insert(TargetHistory(type = type, value = v, effectiveFrom = s.localDate, createdAt = s.at))
            }
            db.weightDao().insert(
                WeightEntry(
                    weightKg = profile.startWeightKg, loggedAt = s.at, zoneId = s.zoneId,
                    localDate = s.localDate, note = "Starting weight (setup)", createdAt = s.at, updatedAt = s.at,
                )
            )
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        db.profileDao().upsert(profile.copy(updatedAt = Clock.now()))
    }

    /**
     * Changing a target never rewrites the past: a new version starts today. A second change
     * on the same day replaces today's version only.
     */
    suspend fun setTarget(type: String, value: Double) {
        val s = Stamp.now()
        db.withTransaction {
            val current = db.targetDao().getAll()
                .filter { it.type == type }
                .maxWithOrNull(compareBy<TargetHistory> { it.effectiveFrom }.thenBy { it.id })
            if (current != null && current.value == value) return@withTransaction
            db.targetDao().deleteForDate(type, s.localDate)
            db.targetDao().insert(TargetHistory(type = type, value = value, effectiveFrom = s.localDate, createdAt = s.at))
        }
    }
}

/** Everything recorded on one calendar day (raw rows only — no derived numbers stored). */
data class DayLog(
    val date: LocalDate,
    val water: List<WaterEntry> = emptyList(),
    val weight: List<WeightEntry> = emptyList(),
    val measurements: List<BodyMeasurement> = emptyList(),
    val sleep: List<SleepEntry> = emptyList(),
    val activity: List<ActivityEntry> = emptyList(),
    val checkIns: List<DailyCheckIn> = emptyList(),
    val notes: List<DailyNote> = emptyList(),
)

class LogRepository(private val db: AppDatabase) {

    // ------------------------------------------------ day view
    fun day(date: LocalDate): Flow<DayLog> {
        val k = Clock.dateKey(date)
        val a = combine(
            db.waterDao().observeDay(k), db.weightDao().observeDay(k),
            db.measurementDao().observeDay(k), db.sleepDao().observeDay(k),
        ) { w, wt, m, s -> DayLog(date, w, wt, m, s) }
        return combine(
            a, db.activityDao().observeDay(k), db.checkInDao().observeDay(k), db.noteDao().observeDay(k),
        ) { base, act, ci, n -> base.copy(activity = act, checkIns = ci, notes = n) }
    }

    fun weightsAll(): Flow<List<WeightEntry>> = db.weightDao().observeAll()
    fun latestWeight(): Flow<WeightEntry?> = db.weightDao().observeLatest()
    fun measurementsAll(): Flow<List<BodyMeasurement>> = db.measurementDao().observeAll()

    fun waterRange(from: LocalDate, to: LocalDate) =
        db.waterDao().observeRange(Clock.dateKey(from), Clock.dateKey(to))
    fun sleepRange(from: LocalDate, to: LocalDate) =
        db.sleepDao().observeRange(Clock.dateKey(from), Clock.dateKey(to))
    fun activityRange(from: LocalDate, to: LocalDate) =
        db.activityDao().observeRange(Clock.dateKey(from), Clock.dateKey(to))

    // ------------------------------------------------ water
    suspend fun addWater(ml: Double, at: Long = Clock.now()): Long {
        val s = Stamp.of(at)
        return db.waterDao().insert(WaterEntry(amountMl = ml, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = Clock.now(), updatedAt = Clock.now()))
    }
    suspend fun updateWater(id: Long, ml: Double, at: Long) {
        val old = db.waterDao().get(id) ?: return
        val s = Stamp.of(at)
        db.waterDao().update(old.copy(amountMl = ml, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }
    suspend fun deleteWater(id: Long) = db.waterDao().softDelete(id, Clock.now())
    /** Removes today's most recent drink (the tile's "−" button). Returns its amount, or null if none. */
    suspend fun removeLastWaterToday(): Double? {
        val last = db.waterDao().lastOn(Clock.dateKey(Clock.today())) ?: return null
        db.waterDao().softDelete(last.id, Clock.now())
        return last.amountMl
    }
    suspend fun getWater(id: Long) = db.waterDao().get(id)

    // ------------------------------------------------ weight
    suspend fun addWeight(kg: Double, bodyFat: Double?, note: String, at: Long = Clock.now()): Long {
        val s = Stamp.of(at)
        return db.weightDao().insert(WeightEntry(weightKg = kg, bodyFatPct = bodyFat, note = note, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = Clock.now(), updatedAt = Clock.now()))
    }
    suspend fun updateWeight(id: Long, kg: Double, bodyFat: Double?, note: String, at: Long) {
        val old = db.weightDao().get(id) ?: return
        val s = Stamp.of(at)
        db.weightDao().update(old.copy(weightKg = kg, bodyFatPct = bodyFat, note = note, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }
    suspend fun deleteWeight(id: Long) = db.weightDao().softDelete(id, Clock.now())
    suspend fun getWeight(id: Long) = db.weightDao().get(id)

    // ------------------------------------------------ measurements
    suspend fun addMeasurement(type: String, custom: String?, cm: Double, note: String, at: Long = Clock.now()): Long {
        val s = Stamp.of(at)
        return db.measurementDao().insert(BodyMeasurement(type = type, customName = custom, valueCm = cm, note = note, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = Clock.now(), updatedAt = Clock.now()))
    }
    suspend fun updateMeasurement(id: Long, type: String, custom: String?, cm: Double, note: String, at: Long) {
        val old = db.measurementDao().get(id) ?: return
        val s = Stamp.of(at)
        db.measurementDao().update(old.copy(type = type, customName = custom, valueCm = cm, note = note, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }
    suspend fun deleteMeasurement(id: Long) = db.measurementDao().softDelete(id, Clock.now())
    suspend fun getMeasurement(id: Long) = db.measurementDao().get(id)

    // ------------------------------------------------ sleep (attributed to the wake-up day)
    suspend fun addSleep(start: Long, end: Long, quality: Int?, notes: String): Long {
        val s = Stamp.of(end)
        return db.sleepDao().insert(SleepEntry(startAt = start, endAt = end, quality = quality, notes = notes, zoneId = s.zoneId, localDate = s.localDate, createdAt = Clock.now(), updatedAt = Clock.now()))
    }
    suspend fun updateSleep(id: Long, start: Long, end: Long, quality: Int?, notes: String) {
        val old = db.sleepDao().get(id) ?: return
        val s = Stamp.of(end)
        db.sleepDao().update(old.copy(startAt = start, endAt = end, quality = quality, notes = notes, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }
    suspend fun deleteSleep(id: Long) = db.sleepDao().softDelete(id, Clock.now())
    suspend fun getSleep(id: Long) = db.sleepDao().get(id)

    // ------------------------------------------------ steps / activity
    suspend fun addActivity(steps: Int?, isDayTotal: Boolean, distanceM: Double?, activeMin: Int?, kcal: Double?, at: Long = Clock.now()): Long {
        val s = Stamp.of(at)
        return db.activityDao().insert(ActivityEntry(steps = steps, isDayTotal = isDayTotal, distanceM = distanceM, activeMinutes = activeMin, exerciseCalories = kcal, source = ActivitySource.MANUAL, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = Clock.now(), updatedAt = Clock.now()))
    }
    suspend fun updateActivity(id: Long, steps: Int?, isDayTotal: Boolean, distanceM: Double?, activeMin: Int?, kcal: Double?, at: Long) {
        val old = db.activityDao().get(id) ?: return
        val s = Stamp.of(at)
        db.activityDao().update(old.copy(steps = steps, isDayTotal = isDayTotal, distanceM = distanceM, activeMinutes = activeMin, exerciseCalories = kcal, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }
    suspend fun deleteActivity(id: Long) = db.activityDao().softDelete(id, Clock.now())
    suspend fun getActivity(id: Long) = db.activityDao().get(id)

    // ------------------------------------------------ check-in
    suspend fun addCheckIn(c: DailyCheckIn, at: Long = Clock.now()): Long {
        val s = Stamp.of(at)
        return db.checkInDao().insert(c.copy(id = 0, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = Clock.now(), updatedAt = Clock.now()))
    }
    suspend fun updateCheckIn(c: DailyCheckIn, at: Long) {
        val s = Stamp.of(at)
        db.checkInDao().update(c.copy(loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }
    suspend fun deleteCheckIn(id: Long) = db.checkInDao().softDelete(id, Clock.now())
    suspend fun getCheckIn(id: Long) = db.checkInDao().get(id)

    // ------------------------------------------------ notes
    suspend fun addNote(text: String, at: Long = Clock.now()): Long {
        val s = Stamp.of(at)
        return db.noteDao().insert(DailyNote(text = text, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = Clock.now(), updatedAt = Clock.now()))
    }
    suspend fun updateNote(id: Long, text: String, at: Long) {
        val old = db.noteDao().get(id) ?: return
        val s = Stamp.of(at)
        db.noteDao().update(old.copy(text = text, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, updatedAt = Clock.now()))
    }
    suspend fun deleteNote(id: Long) = db.noteDao().softDelete(id, Clock.now())
    suspend fun getNote(id: Long) = db.noteDao().get(id)
}
