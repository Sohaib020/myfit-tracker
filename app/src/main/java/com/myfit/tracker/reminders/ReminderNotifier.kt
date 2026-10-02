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

    /**
     * @param doneLabel adds a "done" button (e.g. "+250 ml", "Taken") that logs without opening the app.
     * @param payload the fire intent's extras; done/snooze buttons send them back to [ReminderReceiver].
     * @param snooze adds a "Snooze" button.
     * @param discreet keeps the lock-screen version neutral.
     */
    fun post(
        ctx: Context, id: Int, title: String, text: String,
        doneLabel: String? = null, payload: Intent? = null, snooze: Boolean = false, discreet: Boolean = false,
    ) {
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
        if (discreet) {
            b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            b.setPublicVersion(
                NotificationCompat.Builder(app, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("MyFit")
                    .setContentText("Reminder")
                    .build()
            )
        }
        if (doneLabel != null) {
            val i = Intent(app, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_DONE)
            if (payload != null) i.putExtras(payload)
            i.putExtra(ReminderScheduler.EXTRA_NOTIF, id)
            val pi = PendingIntent.getBroadcast(app, id + ReminderScheduler.RC_DONE_OFFSET, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(R.drawable.ic_notification, doneLabel, pi)
        }
        if (snooze) {
            val i = Intent(app, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_SNOOZE)
            if (payload != null) i.putExtras(payload)
            i.putExtra(ReminderScheduler.EXTRA_NOTIF, id)
            val pi = PendingIntent.getBroadcast(app, id + ReminderScheduler.RC_SNOOZE_ACTION_OFFSET, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(R.drawable.ic_notification, "Snooze ${ReminderScheduler.snoozeMin(app)} min", pi)
        }
        runCatching { NotificationManagerCompat.from(app).notify(id, b.build()) }
    }

    fun cancel(ctx: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(ctx.applicationContext).cancel(id) }
    }
}
