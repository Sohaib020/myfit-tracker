package com.myfit.tracker.ui.coach

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

/**
 * R27: the camera coach's body models ship INSIDE the APK (assets/pose, fetched at build time):
 *  - pose_landmarker_full.task (~9 MB) on strong phones
 *  - pose_landmarker_lite.task (~5.5 MB) on everything else, and as the step-down when a phone runs hot or slow
 * The old downloaded heavy + face models (R20, ~33 MB in the app's files) are deleted on first use.
 * Google's figures: Heavy 53 ms/frame, Full 25 ms, Lite 20 ms; Full is within ~1 point of Heavy's accuracy.
 */
object PoseModels {
    const val FULL = "pose/pose_landmarker_full.task"
    const val LITE = "pose/pose_landmarker_lite.task"

    enum class Model(val asset: String, val label: String, val size: android.util.Size) {
        FULL(PoseModels.FULL, "Full", android.util.Size(640, 480)),
        LITE(PoseModels.LITE, "Lite", android.util.Size(480, 360)),
    }

    fun has(c: Context, m: Model) = runCatching { c.assets.openFd(m.asset).use { it.length > 100_000 } }.getOrDefault(false)

    /** Strong phone (8 GB+ RAM, 8 cores, not a low-RAM device) -> Full; else Lite. */
    fun pick(c: Context): Model {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val ram = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
        val strong = ram >= 7_000_000_000L && Runtime.getRuntime().availableProcessors() >= 8 && !am.isLowRamDevice && Build.VERSION.SDK_INT >= 30
        val m = if (strong) Model.FULL else Model.LITE
        return if (has(c, m)) m else Model.entries.firstOrNull { has(c, it) } ?: m
    }

    /** True when at least one bundled model is present. */
    fun installed(c: Context) = Model.entries.any { has(c, it) }

    /** Removes the old downloaded heavy + face models (R20), once. */
    fun cleanupOld(c: Context) { File(c.filesDir, "pose").takeIf { it.exists() }?.deleteRecursively() }
}
