package com.myfit.tracker.ai.ondevice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.myfit.tracker.R

/**
 * Downloads Pip's offline brain in the background (WorkManager: survives leaving the app, waits for Wi-Fi,
 * resumes after interruptions). Runs as a foreground "data sync" job with a progress notification.
 */
class ModelDownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private var lastNotified = 0L

    override suspend fun doWork(): Result {
        val hub = OnDeviceAi.get(applicationContext)
        runCatching { setForeground(info(0f, "Starting…")) }
        val err = hub.models.downloadNow { st ->
            val now = System.currentTimeMillis()
            if (now - lastNotified < 1500) return@downloadNow
            lastNotified = now
            val (p, text) = when (st) {
                is ModelStore.DlState.Downloading -> (if (st.total > 0) st.done.toFloat() / st.total else 0f) to
                    "${DeviceCheck.gb(st.done)} of ${if (st.total > 0) DeviceCheck.gb(st.total) else "?"}"
                is ModelStore.DlState.Verifying -> st.progress to "Checking the file…"
                else -> 1f to "Done"
            }
            runCatching { setForeground(info(p, text)) }
        }
        return when {
            err == null -> Result.success()
            err.contains("resume", true) && runAttemptCount < 8 -> Result.retry()
            else -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(0f, "Starting…")

    private fun info(progress: Float, text: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm?.getNotificationChannel(CHANNEL) == null) {
            nm?.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW).apply { description = "Pip's offline brain download" })
        }
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading Pip's offline brain")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(1000, (progress * 1000).toInt().coerceIn(0, 1000), progress <= 0f)
            .apply {
                // Android 16 Live Update: download progress in the Now Bar / status chip
                setRequestPromotedOngoing(true)
                setShortCriticalText("${(progress * 100).toInt().coerceIn(0, 100)}%")
            }
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, n)
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIF_ID = 4207
    }
}
