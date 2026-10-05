package com.myfit.tracker.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myfit.tracker.MainActivity
import com.myfit.tracker.R
import com.myfit.tracker.notify.LiveUpdates
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Checks a few times a day; on Sunday from 6 pm it posts "Your week" once. */
class SundayReportWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        if (now.dayOfWeek != DayOfWeek.SUNDAY || now.hour < 18) return Result.success()
        val sp = applicationContext.getSharedPreferences("sunday_report", Context.MODE_PRIVATE)
        val key = now.toLocalDate().toString()
        if (sp.getString("last", null) == key || !sp.getBoolean("on", true)) return Result.success()
        if (!LiveUpdates.canPost(applicationContext)) return Result.success()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly report", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Your week in numbers, every Sunday evening" })
        val pi = PendingIntent.getActivity(applicationContext, 77,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(LiveUpdates.EXTRA_OPEN, "report"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your week is ready").setContentText("Workouts, steps, sleep and more — tap to see and share.")
            .setContentIntent(pi).setAutoCancel(true).build()
        runCatching { nm.notify(4310, n) }
        sp.edit().putString("last", key).apply()
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "weekly_report"
        fun schedule(c: Context) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork("sunday_report", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SundayReportWorker>(3, TimeUnit.HOURS).build())
        }
    }
}
