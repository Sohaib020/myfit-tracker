package com.myfit.tracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------- Diabetes

/** One blood-glucose reading, always stored in mg/dL. Imported Health Connect readings use uuid "hc:<id>". */
@Entity(tableName = "glucose_reading", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class GlucoseReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val mgdl: Double,
    val tag: String,                     // GlucoseTag.*
    val source: String,                  // MANUAL / HEALTH_CONNECT
    val sourcePackage: String? = null,
    val takenAt: Long,
    val zoneId: String,
    val localDate: String,
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object GlucoseTag {
    const val FASTING = "FASTING"; const val BEFORE_MEAL = "BEFORE_MEAL"; const val AFTER_MEAL = "AFTER_MEAL"
    const val BEDTIME = "BEDTIME"; const val RANDOM = "RANDOM"; const val CGM = "CGM"
}

/** A medicine you take (tablet, insulin, other) with optional reminder times ("08:00,20:00"). */
@Entity(tableName = "medication", indices = [Index(value = ["uuid"], unique = true)])
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val kind: String,                    // MedKind.*
    val dose: Double?,
    val unit: String,
    val times: String = "",
    val remind: Boolean = true,
    val notes: String = "",
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object MedKind { const val TABLET = "TABLET"; const val INSULIN_RAPID = "INSULIN_RAPID"; const val INSULIN_LONG = "INSULIN_LONG"; const val OTHER = "OTHER" }

/** A dose actually taken (record only — the app never suggests doses). */
@Entity(tableName = "medication_log", indices = [Index("localDate"), Index("medicationId"), Index(value = ["uuid"], unique = true)])
data class MedicationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val medicationId: Long?,
    val name: String,
    val kind: String,
    val dose: Double?,
    val unit: String,
    val takenAt: Long,
    val zoneId: String,
    val localDate: String,
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

// ---------------------------------------------------------------- Vitals

@Entity(tableName = "blood_pressure", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class BloodPressure(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val source: String,                  // MANUAL / HEALTH_CONNECT
    val sourcePackage: String? = null,
    val takenAt: Long,
    val zoneId: String,
    val localDate: String,
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Other single readings the app records itself (e.g. camera heart rate). */
@Entity(tableName = "vital_reading", indices = [Index("localDate"), Index("type"), Index(value = ["uuid"], unique = true)])
data class VitalReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val type: String,                    // VitalType.*
    val value: Double,
    val source: String,
    val takenAt: Long,
    val zoneId: String,
    val localDate: String,
    val deletedAt: Long? = null,
    val createdAt: Long,
)

object VitalType { const val HR_CAMERA = "HR_CAMERA"; const val STRESS = "STRESS" }

// ---------------------------------------------------------------- Cycle

/** One calendar day of cycle tracking (at most one row per day). Never synced online. */
@Entity(tableName = "cycle_day", indices = [Index(value = ["localDate"], unique = true), Index(value = ["uuid"], unique = true)])
data class CycleDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val localDate: String,
    val flow: Int? = null,               // 0 none, 1 spotting, 2 light, 3 medium, 4 heavy
    val symptoms: String = "",           // comma-separated keys
    val mood: String? = null,
    val ovulationTest: Int? = null,      // 0 negative, 1 high, 2 positive (peak)
    val mucus: Int? = null,              // 1 dry, 2 sticky, 3 creamy, 4 watery, 5 egg-white
    val bbtC: Double? = null,
    val pillTaken: Boolean? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

// ---------------------------------------------------------------- Mindfulness

@Entity(tableName = "mind_session", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class MindSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val type: String,                    // BREATHING / MEDITATION
    val title: String,
    val durationSec: Long,
    val startedAt: Long,
    val zoneId: String,
    val localDate: String,
    val createdAt: Long,
)

@Entity(tableName = "mood_entry", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class MoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val mood: Int,                       // 1 awful … 5 great
    val tags: String = "",               // comma-separated feelings
    val note: String = "",
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
