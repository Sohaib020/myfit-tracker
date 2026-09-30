package com.myfit.tracker

import android.app.Application
import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.prefs.SettingsStore
import com.myfit.tracker.data.repo.ExerciseRepository
import com.myfit.tracker.data.repo.LogRepository
import com.myfit.tracker.data.repo.WorkoutRepository
import com.myfit.tracker.gym.RestTimer
import com.myfit.tracker.data.repo.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container — no DI framework, nothing hidden. */
class AppContainer(app: Application) {
    val db: AppDatabase = AppDatabase.build(app)
    val settings = SettingsStore(app)
    val profileRepo = ProfileRepository(db)
    val logRepo = LogRepository(db)
    val exerciseRepo = ExerciseRepository(db, app)
    val workoutRepo = WorkoutRepository(db)
    val restTimer = RestTimer(app)
    val app: Application = app
    val filesDir = app.filesDir

    /** Writes run here so closing a sheet or leaving a screen can never cancel a save mid-way. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun write(block: suspend () -> Unit) { appScope.launch { block() } }
}

class MyFitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Bundled exercise catalogue — idempotent, runs off the main thread.
        container.write { container.exerciseRepo.seedIfNeeded() }
    }
}
