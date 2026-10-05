package com.myfit.tracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exported only for protected system broadcasts (boot, time / time-zone change, app update), which other apps
 * cannot send. It just re-arms alarms — it never posts or logs anything, so a spoofed intent can do no harm.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    private val allowed = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in allowed) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { ReminderScheduler.rescheduleNow(app) } catch (_: Throwable) {} finally { runCatching { pending.finish() } }
        }
    }
}
