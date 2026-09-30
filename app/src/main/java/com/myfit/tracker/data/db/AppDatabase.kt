package com.myfit.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 2,
    exportSchema = true,
    entities = [
        UserProfile::class, TargetHistory::class,
        Exercise::class, WorkoutTemplate::class, WorkoutTemplateExercise::class, TrainingCycle::class,
        Workout::class, WorkoutExercise::class, WorkoutSet::class, PersonalRecord::class,
        Food::class, Meal::class, MealItem::class, SavedMeal::class, SavedMealItem::class,
        WaterEntry::class, WeightEntry::class, BodyMeasurement::class, SleepEntry::class,
        ActivityEntry::class, Supplement::class, SupplementLog::class, DailyCheckIn::class,
        DailyNote::class, ProgressPhoto::class, FastingSession::class, Goal::class, Reminder::class,
    ]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun targetDao(): TargetDao
    abstract fun waterDao(): WaterDao
    abstract fun weightDao(): WeightDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun sleepDao(): SleepDao
    abstract fun activityDao(): ActivityDao
    abstract fun checkInDao(): CheckInDao
    abstract fun noteDao(): NoteDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "myfit.db"

        /** v1 → v2: exercise catalogue metadata. Additive only — no existing row is touched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercise ADD COLUMN level TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercise ADD COLUMN mechanic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercise ADD COLUMN forceType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE exercise ADD COLUMN imageFrames INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                // No destructive migration fallback: losing personal history is never acceptable.
                .build()
    }
}
