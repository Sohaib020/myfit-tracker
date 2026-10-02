package com.myfit.tracker.health

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.reminders.ReminderReceiver
import com.myfit.tracker.reminders.ReminderScheduler
import com.myfit.tracker.ui.cycle.CycleMode
import com.myfit.tracker.ui.cycle.CyclePrefs
import com.myfit.tracker.ui.glucose.GlucoseConfigStore
import com.myfit.tracker.ui.glucose.HbA1cStore
import com.myfit.tracker.ui.glucose.MedReminders
import com.myfit.tracker.ui.glucose.RamadanTimes
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Notifications for the health features: medicines, blood-sugar checks, Ramadan checks,
 * the 3-monthly HbA1c reminder and pregnancy appointments.
 *
 * Called from [ReminderScheduler.rescheduleNow] (which already runs after boot, after every alarm,
 * and whenever a screen calls [ReminderScheduler.reschedule]). Each reminder has at most one pending
 * inexact alarm — its next occurrence — and the receiver's reschedule arms the following one.
 *
 * Cycle-related reminders are discreet: a neutral title and no content text.
 */
object HealthReminders {
    private const val PREFS = "health_reminders"
    private const val K_COUNT = "armed_count"
    private const val RC_BASE = 930_000
    private const val MAX = 120
    const val KIND = "health"

    private data class Alarm(val at: Long, val title: String, val text: String)

    private fun dailyNext(minOfDay: Int, afterMs: Long, zone: ZoneId): Long {
        val m = minOfDay.coerceIn(0, 1439)
        val today = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate()
        val t = today.atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
        return if (t > afterMs) t else today.plusDays(1).atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
    }

    private fun intent(ctx: Context): Intent = Intent(ctx, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_FIRE)

    /** Re-arms every health alarm. Runs on a background thread (inside the scheduler's lock). */
    suspend fun arm(ctx: Context) {
        val app = ctx.applicationContext
        val container = (app as? MyFitApplication)?.container ?: return
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val after = System.currentTimeMillis() + 60_000L
        val out = ArrayList<Alarm>()

        // ---- medicines (record-only app: the reminder never states a dose)
        runCatching { MedReminders.schedule(container) }.getOrDefault(emptyList()).forEach { (name, min) ->
            out += Alarm(dailyNext(min, after, zone), "Medicine reminder", "Time for $name, as prescribed by your doctor.")
        }

        // ---- blood sugar
        val g = runCatching { GlucoseConfigStore.get(app) }.getOrNull()
        val diabetes = runCatching { container.settings.settings.first().diabetesType }.getOrDefault("none") != "none"
        if (g != null && diabetes) {
            if (g.testRemind) g.testTimes.forEach { m ->
                out += Alarm(dailyNext(m, after, zone), "Blood sugar check", "Time to check your blood sugar.")
            }
            if (g.ramadan && g.ramadanRemind) RamadanTimes.checks(g.suhoorMin, g.iftarMin).forEach { (label, m) ->
                out += Alarm(dailyNext(m, after, zone), "Ramadan sugar check", "$label check. Checking your sugar does not break the fast.")
            }
            if (g.a1cRemind) {
                val last = runCatching { HbA1cStore.latestDate(container) }.getOrNull()
                val due = (last ?: LocalDate.now(zone)).plusDays(90).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
                if (last != null && due > after) out += Alarm(due, "HbA1c test due", "It's about 3 months since your last HbA1c. Ask your doctor about your next test.")
            }
        }

        // ---- pregnancy appointments (discreet: no content text)
        if (runCatching { CyclePrefs.mode(app) }.getOrNull() == CycleMode.PREGNANCY && CyclePrefs.apptRemind(app)) {
            CyclePrefs.appointments(app).forEach { a ->
                val eve = Instant.ofEpochMilli(a.at).atZone(zone).toLocalDate().minusDays(1).atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
                if (eve > after) out += Alarm(eve, "MyFit reminder", "")
                val sameDay = a.at - 2 * 3_600_000L
                if (sameDay > after) out += Alarm(sameDay, "MyFit reminder", "")
            }
        }

        val list = out.sortedBy { it.at }.take(MAX)
        list.forEachIndexed { i, a ->
            val rc = RC_BASE + i
            val pi = PendingIntent.getBroadcast(
                app, rc,
                intent(app).putExtra(ReminderScheduler.EXTRA_KIND, KIND).putExtra(ReminderScheduler.EXTRA_ID, i.toLong())
                    .putExtra(ReminderScheduler.EXTRA_TITLE, a.title).putExtra(ReminderScheduler.EXTRA_TEXT, a.text)
                    .putExtra(ReminderScheduler.EXTRA_NOTIF, rc),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.at, pi) }
        }
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = p.getInt(K_COUNT, 0)
        for (i in list.size until old) {
            val pi = PendingIntent.getBroadcast(app, RC_BASE + i, intent(app), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            if (pi != null) { runCatching { am.cancel(pi) }; runCatching { pi.cancel() } }
        }
        p.edit().putInt(K_COUNT, list.size).apply()
    }
}
