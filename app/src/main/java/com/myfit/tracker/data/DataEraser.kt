package com.myfit.tracker.data

import android.content.Context
import com.myfit.tracker.MyFitApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Delete all my data": wipes the local database, settings, preferences and files (photos, backups, caches),
 * and — if signed in — the server copy (profile, weekly totals, friends, feed), then signs out.
 * The downloaded offline AI model and voice pack are kept (they contain no personal data) unless [alsoModels].
 */
object DataEraser {
    suspend fun eraseAll(ctx: Context, alsoModels: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val c = (app as? MyFitApplication)?.container
        // 1. server copy first (needs the signed-in session)
        runCatching { c?.social?.deleteCloudData() }
        runCatching { c?.social?.signOut() }
        // 2. alarms + notifications
        runCatching { androidx.core.app.NotificationManagerCompat.from(app).cancelAll() }
        runCatching { androidx.work.WorkManager.getInstance(app).cancelAllWork() }
        // 3. database
        runCatching { c?.db?.clearAllTables() }
        // 4. files: keep only the offline model / voice pack when asked
        val keep = if (alsoModels) emptySet() else setOf("llm", "voice")
        app.filesDir.listFiles()?.forEach { f -> if (f.name !in keep) f.deleteRecursively() }
        app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        // 5. DataStore + SharedPreferences
        File(app.filesDir.parentFile, "shared_prefs").listFiles()?.forEach { it.delete() }
        File(app.filesDir, "datastore").deleteRecursively()
    }

    /** Restart into a clean app (fresh onboarding). */
    fun restart(ctx: Context) {
        val pm = ctx.packageManager
        val intent = pm.getLaunchIntentForPackage(ctx.packageName)?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) ctx.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
