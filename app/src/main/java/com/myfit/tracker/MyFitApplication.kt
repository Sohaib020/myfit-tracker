package com.myfit.tracker

import android.app.Application
import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.prefs.SettingsStore
import com.myfit.tracker.data.repo.LogRepository
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
    }
}
