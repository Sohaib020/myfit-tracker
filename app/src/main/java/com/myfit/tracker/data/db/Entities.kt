package com.myfit.tracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * MyFit Tracker schema — version 1.
 *
 * Conventions (mandatory, see accuracy rules):
 *  - All instants are epoch milliseconds (UTC) + the IANA zone id at the moment of logging.
 *  - `localDate` (yyyy-MM-dd) is the user's calendar day in that zone, fixed at log time,
 *    so a DST shift or travel never moves an old entry to a different day.
 *  - Canonical units: kg, cm, ml, metres, seconds. Conversion happens only for display.
 *  - Raw records are the source of truth. Totals/averages/volumes are always recomputed.
 *  - History is never hard-deleted where other rows depend on it: archivedAt / deletedAt.
 */

fun newUuid(): String = UUID.randomUUID().toString()

// ---------------------------------------------------------------- Profile & targets

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Long = 1,
    val name: String,
    val age: Int,
    val ageRecordedOn: String,           // yyyy-MM-dd — current age derived from this
    val sex: String,                     // Sex.*
    val heightCm: Double,
    val startWeightKg: Double,           // weight entered at setup (also logged as a WeightEntry)
    val targetWeightKg: Double?,
    val activityLevel: String,           // ActivityLevel.*
    val experience: String,              // Experience.*
    val goals: String,                   // comma separated FitnessGoal.*
    val workoutDaysMask: Int,            // bit 0 = Monday … bit 6 = Sunday
    val workoutTimeMin: Int?,            // minutes after midnight
    val wakeTimeMin: Int,
    val sleepTimeMin: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Every daily target is versioned. A target change inserts a new row effective from that day,
 * so days before the change are still judged against the target that applied then.
 */
@Entity(
    tableName = "target_history",
    indices = [Index(value = ["type", "effectiveFrom"])]
)
data class TargetHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                    // TargetType.*
    val value: Double,                   // canonical unit
    val effectiveFrom: String,           // yyyy-MM-dd inclusive
    val createdAt: Long,
)

// ---------------------------------------------------------------- Exercises & templates

@Entity(
    tableName = "exercise",
    indices = [Index("name"), Index("primaryMuscle"), Index(value = ["uuid"], unique = true)]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val primaryMuscle: String,           // MuscleGroup.*
    val secondaryMuscles: String = "",   // comma separated
    val equipment: String = "",
    val measurementType: String,         // MeasurementType.*
    val instructions: String = "",
    val personalNotes: String = "",
    val imageKey: String? = null,        // bundled asset folder, null for custom
    val isCustom: Boolean = false,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "workout_template", indices = [Index(value = ["uuid"], unique = true)])
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val notes: String = "",
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "workout_template_exercise",
    foreignKeys = [
        ForeignKey(WorkoutTemplate::class, ["id"], ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class WorkoutTemplateExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeightKg: Double?,
    val targetDurationSec: Long? = null,
    val restSeconds: Int,
    val supersetGroup: Int? = null,
    val notes: String = "",
)

@Entity(tableName = "training_cycle", indices = [Index(value = ["uuid"], unique = true)])
data class TrainingCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val primaryGoal: String,             // Strength / Hypertrophy / Endurance / custom
    val weeks: Int,
    val daysPerWeek: Int,
    val startDate: String,
    val endDate: String,
    val notes: String = "",
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

// ---------------------------------------------------------------- Workouts (raw sets = truth)

@Entity(
    tableName = "workout",
    foreignKeys = [
        ForeignKey(WorkoutTemplate::class, ["id"], ["templateId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(TrainingCycle::class, ["id"], ["cycleId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("templateId"), Index("cycleId"), Index("localDate"), Index(value = ["uuid"], unique = true)]
)
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val templateId: Long?,
    val cycleId: Long? = null,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val zoneId: String,
    val localDate: String,
    val status: String,                  // WorkoutStatus.*
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "workout_exercise",
    foreignKeys = [
        ForeignKey(Workout::class, ["id"], ["workoutId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    val position: Int,
    val supersetGroup: Int? = null,
    val notes: String = "",
)

@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(WorkoutExercise::class, ["id"], ["workoutExerciseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("workoutExerciseId"), Index(value = ["uuid"], unique = true)]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val workoutExerciseId: Long,
    val setNumber: Int,
    val setType: String,                 // SetType.*
    val weightKg: Double?,               // added weight for bodyweight, assistance for assisted
    val reps: Int?,
    val durationSec: Long?,
    val distanceM: Double?,
    val rpe: Double?,
    val restSec: Long?,
    val completedAt: Long,
    val zoneId: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** Cache of detected PRs — fully rebuildable from workout_set; rebuilt after any set edit. */
@Entity(
    tableName = "personal_record",
    foreignKeys = [
        ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(WorkoutSet::class, ["id"], ["sourceSetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exerciseId"), Index("sourceSetId")]
)
data class PersonalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val prType: String,                  // PrType.*
    val value: Double,
    val atWeightKg: Double?,             // for REPS_AT_WEIGHT
    val isEstimate: Boolean,
    val achievedAt: Long,
    val localDate: String,
    val sourceSetId: Long,
)

// ---------------------------------------------------------------- Nutrition

@Entity(
    tableName = "food",
    indices = [Index("name"), Index("barcode"), Index(value = ["uuid"], unique = true)]
)
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val brand: String? = null,
    val servingSize: Double,
    val servingUnit: String,             // g, ml, piece, cup …
    val servingGrams: Double? = null,
    val calories: Double,                // per serving
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double?,                 // null = unknown (never assumed 0)
    val source: String,                  // NutritionSource.*
    val sourceRef: String? = null,       // citation / database name / URL
    val barcode: String? = null,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "meal", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val mealType: String,                // MealType.*
    val eatenAt: Long,
    val zoneId: String,
    val localDate: String,
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Nutrition is snapshotted per serving, so editing a Food never rewrites past meals. */
@Entity(
    tableName = "meal_item",
    foreignKeys = [
        ForeignKey(Meal::class, ["id"], ["mealId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Food::class, ["id"], ["foodId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("mealId"), Index("foodId")]
)
data class MealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodId: Long?,
    val foodName: String,
    val quantity: Double,                // number of servings
    val servingSize: Double,
    val servingUnit: String,
    val caloriesPerServing: Double,
    val proteinPerServing: Double,
    val carbsPerServing: Double,
    val fatPerServing: Double,
    val fiberPerServing: Double?,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "saved_meal", indices = [Index(value = ["uuid"], unique = true)])
data class SavedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val defaultMealType: String? = null,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "saved_meal_item",
    foreignKeys = [
        ForeignKey(SavedMeal::class, ["id"], ["savedMealId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Food::class, ["id"], ["foodId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("savedMealId"), Index("foodId")]
)
data class SavedMealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedMealId: Long,
    val foodId: Long,
    val quantity: Double,
)

// ---------------------------------------------------------------- Body & daily logs

@Entity(tableName = "water_entry", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class WaterEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val amountMl: Double,
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "weight_entry", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val weightKg: Double,
    val bodyFatPct: Double? = null,
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val note: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "body_measurement",
    indices = [Index("localDate"), Index("type"), Index(value = ["uuid"], unique = true)]
)
data class BodyMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val type: String,                    // MeasurementSite.*
    val customName: String? = null,
    val valueCm: Double,
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val note: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "sleep_entry", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class SleepEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val startAt: Long,
    val endAt: Long,
    val zoneId: String,
    val localDate: String,               // calendar day of waking up
    val quality: Int?,                   // 1–10, optional
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "activity_entry", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class ActivityEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val steps: Int?,
    val distanceM: Double?,
    val activeMinutes: Int?,
    val exerciseCalories: Double?,
    /**
     * true  = "my step counter shows X for today" (a running total that replaces earlier totals)
     * false = "add X steps" (an increment on top of the latest total)
     */
    val isDayTotal: Boolean = true,
    val source: String,                  // ActivitySource.*
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "supplement", indices = [Index(value = ["uuid"], unique = true)])
data class Supplement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val name: String,
    val defaultDose: Double,
    val unit: String,
    val notes: String = "",
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "supplement_log",
    foreignKeys = [ForeignKey(Supplement::class, ["id"], ["supplementId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("supplementId"), Index("localDate"), Index(value = ["uuid"], unique = true)]
)
data class SupplementLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val supplementId: Long,
    val dose: Double,
    val unit: String,
    val taken: Boolean,
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "daily_checkin", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class DailyCheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val energy: Int?,
    val mood: Int?,
    val stress: Int?,
    val sleepQuality: Int?,
    val soreness: Int?,
    val motivation: Int?,
    val notes: String = "",
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "daily_note", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class DailyNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val text: String,
    val loggedAt: Long,
    val zoneId: String,
    val localDate: String,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "progress_photo", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val pose: String,                    // FRONT / SIDE / BACK
    val fileName: String,                // inside app private storage
    val weightKg: Double?,
    val notes: String = "",
    val takenAt: Long,
    val zoneId: String,
    val localDate: String,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "fasting_session", indices = [Index("localDate"), Index(value = ["uuid"], unique = true)])
data class FastingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val startAt: Long,
    val endAt: Long?,
    val targetHours: Double,
    val zoneId: String,
    val localDate: String,               // day the fast started
    val notes: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

// ---------------------------------------------------------------- Goals & reminders

/**
 * Goals are immutable once they have history: editing a goal ends the old row (endDate)
 * and starts a new one, so past performance is always judged against the goal that applied.
 */
@Entity(
    tableName = "goal",
    foreignKeys = [ForeignKey(Exercise::class, ["id"], ["exerciseId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("exerciseId"), Index(value = ["uuid"], unique = true)]
)
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = newUuid(),
    val type: String,                    // GoalType.*
    val title: String,
    val target: Double,
    val unit: String,
    val period: String,                  // GoalPeriod.*
    val exerciseId: Long? = null,
    val startDate: String,
    val endDate: String?,
    val supersedesGoalId: Long? = null,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "reminder")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,                    // ReminderType.*
    val title: String,
    val message: String,
    val timeMin: Int,                    // first fire, minutes after midnight
    val intervalMin: Int? = null,        // repeat every N minutes (water); null = once per day
    val endTimeMin: Int? = null,         // last fire for interval reminders
    val repeatDaysMask: Int = 0x7F,
    val startDate: String? = null,
    val endDate: String? = null,
    val respectQuietHours: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

// ---------------------------------------------------------------- Constants

object Sex { const val MALE = "MALE"; const val FEMALE = "FEMALE" }

object ActivityLevel {
    const val SEDENTARY = "SEDENTARY"; const val LIGHT = "LIGHT"; const val MODERATE = "MODERATE"
    const val ACTIVE = "ACTIVE"; const val VERY_ACTIVE = "VERY_ACTIVE"
}

object Experience {
    const val BEGINNER = "BEGINNER"; const val INTERMEDIATE = "INTERMEDIATE"; const val ADVANCED = "ADVANCED"
}

object TargetType {
    const val WATER_ML = "WATER_ML"; const val STEPS = "STEPS"; const val CALORIES = "CALORIES"
    const val PROTEIN_G = "PROTEIN_G"; const val CARBS_G = "CARBS_G"; const val FAT_G = "FAT_G"
    const val FIBER_G = "FIBER_G"; const val SLEEP_MIN = "SLEEP_MIN"; const val WEEKLY_WORKOUTS = "WEEKLY_WORKOUTS"
}

object MuscleGroup {
    const val CHEST = "Chest"; const val BACK = "Back"; const val SHOULDERS = "Shoulders"
    const val BICEPS = "Biceps"; const val TRICEPS = "Triceps"; const val LEGS = "Legs"
    const val GLUTES = "Glutes"; const val CORE = "Core"; const val CARDIO = "Cardio"; const val OTHER = "Other"
    val all = listOf(CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, LEGS, GLUTES, CORE, CARDIO, OTHER)
}

object MeasurementType {
    const val WEIGHT_REPS = "WEIGHT_REPS"
    const val BODYWEIGHT_REPS = "BODYWEIGHT_REPS"   // reps + optional added weight
    const val ASSISTED_REPS = "ASSISTED_REPS"       // reps + assistance weight
    const val REPS_ONLY = "REPS_ONLY"
    const val DURATION = "DURATION"
    const val DISTANCE_DURATION = "DISTANCE_DURATION"
    const val WEIGHT_DURATION = "WEIGHT_DURATION"   // loaded carries, weighted planks
}

object SetType {
    const val WARMUP = "WARMUP"; const val WORKING = "WORKING"; const val DROP = "DROP"
    const val FAILURE = "FAILURE"; const val AMRAP = "AMRAP"; const val ASSISTED = "ASSISTED"
}

object WorkoutStatus { const val IN_PROGRESS = "IN_PROGRESS"; const val COMPLETED = "COMPLETED" }

object PrType {
    const val MAX_WEIGHT = "MAX_WEIGHT"; const val REPS_AT_WEIGHT = "REPS_AT_WEIGHT"
    const val MAX_SET_VOLUME = "MAX_SET_VOLUME"; const val MAX_SESSION_VOLUME = "MAX_SESSION_VOLUME"
    const val EST_1RM = "EST_1RM"; const val MAX_REPS = "MAX_REPS"; const val MAX_DURATION = "MAX_DURATION"
    const val MAX_DISTANCE = "MAX_DISTANCE"
}

object NutritionSource {
    const val USER = "USER"; const val DATABASE = "DATABASE"; const val ESTIMATED = "ESTIMATED"
    const val BARCODE = "BARCODE"; const val AI_PHOTO = "AI_PHOTO"
}

object MealType {
    const val BREAKFAST = "BREAKFAST"; const val PRE_WORKOUT = "PRE_WORKOUT"; const val POST_WORKOUT = "POST_WORKOUT"
    const val LUNCH = "LUNCH"; const val DINNER = "DINNER"; const val SNACK = "SNACK"; const val OTHER = "OTHER"
}

object MeasurementSite {
    const val WAIST = "WAIST"; const val CHEST = "CHEST"; const val LEFT_ARM = "LEFT_ARM"; const val RIGHT_ARM = "RIGHT_ARM"
    const val LEFT_THIGH = "LEFT_THIGH"; const val RIGHT_THIGH = "RIGHT_THIGH"; const val NECK = "NECK"
    const val HIPS = "HIPS"; const val CUSTOM = "CUSTOM"
    val all = listOf(WAIST, CHEST, LEFT_ARM, RIGHT_ARM, LEFT_THIGH, RIGHT_THIGH, NECK, HIPS)
    fun label(type: String, custom: String? = null) = when (type) {
        WAIST -> "Waist"; CHEST -> "Chest"; LEFT_ARM -> "Left arm"; RIGHT_ARM -> "Right arm"
        LEFT_THIGH -> "Left thigh"; RIGHT_THIGH -> "Right thigh"; NECK -> "Neck"; HIPS -> "Hips"
        else -> custom ?: "Custom"
    }
}

object ActivitySource { const val MANUAL = "MANUAL"; const val HEALTH_CONNECT = "HEALTH_CONNECT" }

object GoalType {
    const val TARGET_WEIGHT = "TARGET_WEIGHT"; const val WEEKLY_WORKOUTS = "WEEKLY_WORKOUTS"
    const val DAILY_WATER = "DAILY_WATER"; const val DAILY_PROTEIN = "DAILY_PROTEIN"
    const val DAILY_CALORIES = "DAILY_CALORIES"; const val DAILY_STEPS = "DAILY_STEPS"
    const val SLEEP = "SLEEP"; const val EXERCISE_PR = "EXERCISE_PR"
}

object GoalPeriod { const val DAILY = "DAILY"; const val WEEKLY = "WEEKLY"; const val BY_DATE = "BY_DATE" }

object ReminderType {
    const val WATER = "WATER"; const val MEAL = "MEAL"; const val WORKOUT = "WORKOUT"; const val WEIGHT = "WEIGHT"
    const val SUPPLEMENT = "SUPPLEMENT"; const val SLEEP = "SLEEP"; const val STEPS = "STEPS"
    const val MEASUREMENTS = "MEASUREMENTS"; const val WEEKLY_REPORT = "WEEKLY_REPORT"
}

object FitnessGoal {
    val all = listOf(
        "Build Muscle", "Gain Strength", "Improve Endurance", "Lose Fat",
        "Increase Flexibility & Mobility", "Maintain Shape", "General Health", "Rehab / Recovery"
    )
}
