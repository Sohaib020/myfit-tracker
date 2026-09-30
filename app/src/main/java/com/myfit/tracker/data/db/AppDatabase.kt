package com.myfit.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    version = 1,
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

    companion object {
        const val NAME = "myfit.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // No destructive migration fallback: losing personal history is never acceptable.
                .build()
    }
}
