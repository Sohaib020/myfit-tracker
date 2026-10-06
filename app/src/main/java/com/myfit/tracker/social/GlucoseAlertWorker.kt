package com.myfit.tracker.social

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.myfit.tracker.MainActivity
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.R
import com.myfit.tracker.domain.Glucose
import com.myfit.tracker.notify.LiveUpdates
import java.util.concurrent.TimeUnit

/**
 * For carers: every 15 minutes checks the newest shared reading of each person they care for and alerts once for
 * a low (< 70), very low (< 54) or very high (≥ 250) value from the last 3 hours.
 */
class GlucoseAlertWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MyFitApplication
        val fam = app.container.glucoseFamily
        if (!fam.available()) return Result.success()
        val patients = fam.patients(applicationContext)
        if (patients.isEmpty()) { WorkManager.getInstance(applicationContext).cancelUniqueWork(NAME); return Result.success() }
        val sp = applicationContext.getSharedPreferences("glucose_alerts", Context.MODE_PRIVATE)
        patients.forEachIndexed { i, p ->
            val r = runCatching { fam.readings(p, 1).firstOrNull() }.getOrNull() ?: return@forEachIndexed
            if (System.currentTimeMillis() - r.takenAt > 3 * 3_600_000L) return@forEachIndexed
            if (sp.getString(p.uid, null) == r.id) return@forEachIndexed
            val (title, text) = when {
                r.mgdl < Glucose.VERY_LOW -> "${p.name}: VERY LOW blood sugar" to "${r.mgdl.toInt()} mg/dL. Please check on them now — they need fast sugar."
                r.mgdl < Glucose.LOW -> "${p.name}: low blood sugar" to "${r.mgdl.toInt()} mg/dL. They should take something sweet and recheck in 15 minutes."
                r.mgdl >= Glucose.VERY_HIGH -> "${p.name}: very high blood sugar" to "${r.mgdl.toInt()} mg/dL. Check how they are feeling and follow their care plan."
                else -> null to null
            }
            if (title != null && LiveUpdates.canPost(applicationContext)) {
                val nm = applicationContext.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "Family blood sugar alerts", NotificationManager.IMPORTANCE_HIGH).apply { description = "Lows and very highs of people you care for" })
                val pi = PendingIntent.getActivity(applicationContext, 90 + i, Intent(applicationContext, MainActivity::class.java).putExtra(LiveUpdates.EXTRA_OPEN, "glucose"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val card = com.myfit.tracker.notify.NCard(com.myfit.tracker.notify.NKind.SUGAR, title, text, value = "${r.mgdl.toInt()} mg/dL",
                    chip = if (r.mgdl < Glucose.LOW) "LOW" else "HIGH")
                nm.notify(4400 + i, com.myfit.tracker.notify.NotifKit.apply(applicationContext, NotificationCompat.Builder(applicationContext, CHANNEL), card)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM).setContentIntent(pi).setAutoCancel(true).build())
            }
            sp.edit().putString(p.uid, r.id).apply()
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "glucose_family_alerts"
        private const val CHANNEL = "family_glucose"
        fun schedule(c: Context) {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GlucoseAlertWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
    }
}
