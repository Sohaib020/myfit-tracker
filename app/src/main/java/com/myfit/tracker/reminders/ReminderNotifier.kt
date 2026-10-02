package com.myfit.tracker.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.myfit.tracker.MainActivity
import com.myfit.tracker.R

/** Posts reminder notifications on the "reminders" channel. */
object ReminderNotifier {
    const val CHANNEL = "reminders"

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Water, meals, workouts, supplements, fasting and other reminders you set"
            }
        )
    }

    /** @param waterAction adds a "+250 ml" button that logs water without opening the app. */
    fun post(ctx: Context, id: Int, title: String, text: String, waterAction: Boolean = false) {
        val app = ctx.applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(app)
        val open = PendingIntent.getActivity(
            app, id,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (waterAction) {
            val add = PendingIntent.getBroadcast(
                app, id + ReminderScheduler.RC_WATER_ACTION_OFFSET,
                Intent(app, ReminderReceiver::class.java)
                    .setAction(ReminderScheduler.ACTION_WATER)
                    .putExtra(ReminderScheduler.EXTRA_NOTIF, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(R.drawable.ic_notification, "+250 ml", add)
        }
        runCatching { NotificationManagerCompat.from(app).notify(id, b.build()) }
    }

    fun cancel(ctx: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(ctx.applicationContext).cancel(id) }
    }
}
