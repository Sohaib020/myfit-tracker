package com.myfit.tracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.data.db.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Handles: reminder alarms (ACTION_FIRE), the "+250 ml" notification action (ACTION_WATER),
 * and system events that invalidate alarms (boot, clock/time-zone change, app update) → reschedule.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, intent)
            } catch (e: Throwable) {
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun handle(app: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                fire(app, intent)
                ReminderScheduler.rescheduleNow(app)
            }
            ReminderScheduler.ACTION_WATER -> {
                val container = (app as? MyFitApplication)?.container ?: return
                container.logRepo.addWater(250.0)
                val nid = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIF, 0)
                if (nid != 0) ReminderNotifier.cancel(app, nid)
            }
            else -> ReminderScheduler.rescheduleNow(app)   // BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED
        }
    }

    private suspend fun fire(app: Context, intent: Intent) {
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val nid = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIF, ReminderScheduler.RC_TEST)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT).orEmpty()
        when (kind) {
            ReminderScheduler.KIND_DB -> {
                val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
                val container = (app as? MyFitApplication)?.container ?: return
                val r = container.db.reminderDao().get(id) ?: return
                if (!r.enabled) return
                // a late delivery that drifted into quiet hours is dropped
                val now = Instant.now().atZone(ZoneId.systemDefault())
                if (r.respectQuietHours && ReminderScheduler.quiet(app).contains(now.hour * 60 + now.minute)) return
                ReminderNotifier.post(app, nid, r.title, r.message, waterAction = r.type == ReminderType.WATER)
            }
            ReminderScheduler.KIND_FAST -> {
                ReminderScheduler.clearFastAlarm(app)
                ReminderNotifier.post(app, nid, title, text)
            }
            else -> ReminderNotifier.post(app, nid, title, text)
        }
    }
}
