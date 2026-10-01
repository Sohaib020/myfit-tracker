package com.myfit.tracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 3,
    exportSchema = true,
    entities = [
        UserProfile::class, TargetHistory::class,
        Exercise::class, WorkoutTemplate::class, WorkoutTemplateExercise::class, TrainingCycle::class,
        Workout::class, WorkoutExercise::class, WorkoutSet::class, PersonalRecord::class,
        Food::class, Meal::class, MealItem::class, SavedMeal::class, SavedMealItem::class,
        WaterEntry::class, WeightEntry::class, BodyMeasurement::class, SleepEntry::class,
        ActivityEntry::class, Supplement::class, SupplementLog::class, DailyCheckIn::class,
        DailyNote::class, ProgressPhoto::class, FastingSession::class, Goal::class, Reminder::class,
        HcDaily::class, HcSession::class, HcSleep::class, PhoneStepSnapshot::class, ChatMessage::class,
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
    abstract fun healthDao(): HealthDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun healthImportDao(): HealthImportDao
    abstract fun progressPhotoDao(): ProgressPhotoDao

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

        /** v2 → v3: imported health data (Health Connect, phone sensor) and Pip chat. New tables only. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `hc_daily` (`localDate` TEXT NOT NULL, `steps` INTEGER, `distanceM` REAL, `activeKcal` REAL, `totalKcal` REAL, `floors` REAL, `restingHr` INTEGER, `avgHr` INTEGER, `minHr` INTEGER, `maxHr` INTEGER, `hrvMs` REAL, `spo2Pct` REAL, `syncedAt` INTEGER NOT NULL, PRIMARY KEY(`localDate`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `hc_session` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `externalId` TEXT NOT NULL, `exerciseType` INTEGER NOT NULL, `title` TEXT, `startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `localDate` TEXT NOT NULL, `distanceM` REAL, `activeKcal` REAL, `steps` INTEGER, `avgHr` INTEGER, `maxHr` INTEGER, `segments` TEXT NOT NULL, `sourcePackage` TEXT NOT NULL, `syncedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hc_session_externalId` ON `hc_session` (`externalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hc_session_localDate` ON `hc_session` (`localDate`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `hc_sleep` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `externalId` TEXT NOT NULL, `startAt` INTEGER NOT NULL, `endAt` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `localDate` TEXT NOT NULL, `deepMin` INTEGER, `remMin` INTEGER, `lightMin` INTEGER, `awakeMin` INTEGER, `sourcePackage` TEXT NOT NULL, `syncedAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hc_sleep_externalId` ON `hc_sleep` (`externalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hc_sleep_localDate` ON `hc_sleep` (`localDate`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `phone_step_snapshot` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `at` INTEGER NOT NULL, `counter` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `localDate` TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_step_snapshot_localDate` ON `phone_step_snapshot` (`localDate`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `chat_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `source` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                // No destructive migration fallback: losing personal history is never acceptable.
                .build()
    }
}
