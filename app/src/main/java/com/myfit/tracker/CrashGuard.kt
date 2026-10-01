package com.myfit.tracker

import android.content.Context
import java.io.File

/**
 * Saves any crash to a file so the next launch can show it (and offer safe mode) instead of the app
 * silently closing again.
 */
object CrashGuard {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")
    private fun safeFile(c: Context) = File(c.filesDir, "safe_mode")

    fun install(app: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val trace = android.util.Log.getStackTraceString(e)
                file(app).writeText("MyFit ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.SDK_INT} · ${android.os.Build.MODEL}\nThread: ${t.name}\n\n$trace")
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun lastCrash(c: Context): String? = file(c).takeIf { it.exists() }?.readText()
    fun clear(c: Context) { file(c).delete() }

    /** Safe mode: no shaders, no blur/refraction, still Pip — for recovering from a rendering crash. */
    var safeMode: Boolean = false
        private set
    fun loadSafe(c: Context) { safeMode = safeFile(c).exists() }
    fun setSafe(c: Context, on: Boolean) { if (on) safeFile(c).writeText("1") else safeFile(c).delete(); safeMode = on }
}
