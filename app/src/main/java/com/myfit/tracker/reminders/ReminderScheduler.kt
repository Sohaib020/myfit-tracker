package com.myfit.tracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.data.db.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Local notification engine. Every reminder has at most ONE pending alarm (its next fire time).
 * When it fires, [ReminderReceiver] posts the notification and calls [rescheduleNow] again, which
 * arms the following occurrence. Alarms are inexact (`setAndAllowWhileIdle`) so no exact-alarm
 * permission is needed — Android may deliver them a few minutes late.
 *
 * Day mask convention (Reminder.repeatDaysMask): bit0 = Monday, bit1 = Tuesday … bit6 = Sunday,
 * i.e. bit index = DayOfWeek.value - 1. 0x7F = every day; 0 = never.
 */
object ReminderScheduler {
    const val PREFS = "reminder_prefs"
    const val CUSTOM_TYPE = "CUSTOM"          // not in ReminderType; used for user-made reminders

    // prefs keys
    const val K_QUIET_ON = "quiet_on"
    const val K_QUIET_START = "quiet_start"    // minute of day, default 22:30
    const val K_QUIET_END = "quiet_end"        // minute of day, default 07:00
    private const val K_SCHEDULED = "scheduled_ids"
    private const val K_EXTRA_COUNT = "extra_count"
    private const val K_FAST_AT = "fast_alarm_at"
    const val DEFAULT_QUIET_START = 22 * 60 + 30
    const val DEFAULT_QUIET_END = 7 * 60

    // intent plumbing
    const val ACTION_FIRE = "com.myfit.tracker.reminders.FIRE"
    const val ACTION_WATER = "com.myfit.tracker.reminders.ADD_WATER"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "rid"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_NOTIF = "notif"
    const val KIND_DB = "db"
    const val KIND_PILL = "pill"
    const val KIND_PERIOD = "period"
    const val KIND_FAST = "fast"
    const val KIND_EXTRA = "extra"

    // request codes (also used as notification ids)
    private const val RC_DB_BASE = 100_000
    const val RC_PILL = 900_001
    const val RC_PERIOD = 900_002
    const val RC_FAST = 900_003
    const val RC_TEST = 900_009
    private const val RC_EXTRA_BASE = 910_000
    const val RC_WATER_ACTION_OFFSET = 500_000

    fun rcFor(reminderId: Long): Int = RC_DB_BASE + (reminderId % 400_000).toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Re-arms every alarm on a background thread. Safe to call from the main thread (app start, after edits). */
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { runCatching { rescheduleNow(app) } }
    }

    /**
     * Hook for daily notifications owned by other features (e.g. medicines).
     * Each entry is (title, message, minuteOfDay) and fires every day.
     * TODO: return MedReminders' daily entries once that feature exists (kept dependency-free on purpose).
     */
    @Suppress("UNUSED_PARAMETER")
    fun extraDaily(ctx: Context): List<Triple<String, String, Int>> = emptyList()

    /** One-off "fasting goal reached" alarm. Pass null to cancel. */
    fun setFastAlarm(ctx: Context, atMs: Long?) {
        val app = ctx.applicationContext
        prefs(app).edit().putLong(K_FAST_AT, atMs ?: 0L).apply()
        reschedule(app)
    }

    fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Quiet(val on: Boolean, val start: Int, val end: Int) {
        fun contains(minOfDay: Int): Boolean {
            if (!on || start == end) return false
            return if (start < end) minOfDay in start until end else (minOfDay >= start || minOfDay < end)
        }
    }

    fun quiet(ctx: Context): Quiet {
        val p = prefs(ctx)
        return Quiet(p.getBoolean(K_QUIET_ON, true), p.getInt(K_QUIET_START, DEFAULT_QUIET_START), p.getInt(K_QUIET_END, DEFAULT_QUIET_END))
    }

    fun setQuiet(ctx: Context, q: Quiet) {
        prefs(ctx).edit().putBoolean(K_QUIET_ON, q.on).putInt(K_QUIET_START, q.start).putInt(K_QUIET_END, q.end).apply()
        reschedule(ctx)
    }

    /** Minutes of day at which a reminder fires on a given day (start day; times past midnight roll over). */
    fun firesOfDay(r: Reminder): List<Int> {
        val iv = r.intervalMin
        val end = r.endTimeMin
        if (iv == null || iv <= 0 || end == null) return listOf(r.timeMin.coerceIn(0, 1439))
        val endAdj = if (end < r.timeMin) end + 1440 else end
        val out = ArrayList<Int>()
        var t = r.timeMin
        while (t <= endAdj && out.size < 200) { out.add(t); t += iv.coerceAtLeast(5) }
        return out
    }

    /** Next fire time strictly after [afterMs], or null if the reminder never fires again. */
    fun nextFire(r: Reminder, afterMs: Long, zone: ZoneId, q: Quiet): Long? {
        if ((r.repeatDaysMask and 0x7F) == 0) return null
        val startD = r.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val endD = r.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val today = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate()
        val times = firesOfDay(r)
        // start one day back so an overnight interval reminder (end < start) still catches post-midnight fires
        for (offset in -1..8) {
            val d = today.plusDays(offset.toLong())
            if (startD != null && d.isBefore(startD)) continue
            if (endD != null && d.isAfter(endD)) break
            if ((r.repeatDaysMask and (1 shl (d.dayOfWeek.value - 1))) == 0) continue
            for (t in times) {
                val fireDay = d.plusDays((t / 1440).toLong())
                val m = t % 1440
                if (r.respectQuietHours && q.contains(m)) continue
                val at = fireDay.atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
                if (at > afterMs) return at
            }
        }
        return null
    }

    private fun dailyNext(minOfDay: Int, afterMs: Long, zone: ZoneId): Long {
        val m = minOfDay.coerceIn(0, 1439)
        val today = Instant.ofEpochMilli(afterMs).atZone(zone).toLocalDate()
        val t = today.atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
        return if (t > afterMs) t else today.plusDays(1).atTime(m / 60, m % 60).atZone(zone).toInstant().toEpochMilli()
    }

    private fun fireIntent(ctx: Context): Intent =
        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)

    private fun pending(ctx: Context, rc: Int, intent: Intent, create: Boolean): PendingIntent? {
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(ctx, rc, intent, flags)
    }

    private fun arm(ctx: Context, am: AlarmManager, rc: Int, at: Long, kind: String, id: Long, title: String, text: String) {
        val i = fireIntent(ctx).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_NOTIF, rc)
        val pi = pending(ctx, rc, i, true) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    private fun disarm(ctx: Context, am: AlarmManager, rc: Int) {
        val pi = pending(ctx, rc, fireIntent(ctx), false) ?: return
        runCatching { am.cancel(pi) }
        runCatching { pi.cancel() }
    }

    /** Does the actual work; call from a background coroutine (receivers use goAsync). */
    suspend fun rescheduleNow(ctx: Context) = lock.withLock {
        val app = ctx.applicationContext
        val container = (app as? MyFitApplication)?.container ?: return@withLock
        val am = app.getSystemService(AlarmManager::class.java) ?: return@withLock
        val zone = ZoneId.systemDefault()
        // 60 s guard so an alarm delivered a little early never re-arms the same minute
        val after = System.currentTimeMillis() + 60_000L
        val q = quiet(app)
        val p = prefs(app)

        // ---- database reminders
        val reminders = runCatching { container.db.reminderDao().enabledNow() }.getOrDefault(emptyList())
        val armed = HashSet<String>()
        for (r in reminders) {
            val at = nextFire(r, after, zone, q) ?: continue
            val rc = rcFor(r.id)
            arm(app, am, rc, at, KIND_DB, r.id, r.title, r.message)
            armed.add(rc.toString())
        }
        val previous = p.getStringSet(K_SCHEDULED, emptySet()).orEmpty()
        for (old in previous) if (old !in armed) old.toIntOrNull()?.let { disarm(app, am, it) }
        p.edit().putStringSet(K_SCHEDULED, armed).apply()

        // ---- extra daily hooks (medicines etc.)
        val extras = runCatching { extraDaily(app) }.getOrDefault(emptyList())
        extras.forEachIndexed { i, (title, text, min) ->
            arm(app, am, RC_EXTRA_BASE + i, dailyNext(min, after, zone), KIND_EXTRA, i.toLong(), title, text)
        }
        val oldExtra = p.getInt(K_EXTRA_COUNT, 0)
        for (i in extras.size until oldExtra) disarm(app, am, RC_EXTRA_BASE + i)
        p.edit().putInt(K_EXTRA_COUNT, extras.size).apply()

        // ---- cycle reminders (read raw prefs; no dependency on the cycle feature's classes)
        val cp = app.getSharedPreferences("cycle_prefs", Context.MODE_PRIVATE)
        val pillOn = runCatching { cp.getBoolean("pill_on", false) }.getOrDefault(false)
        val pillTime = runCatching { cp.getInt("pill_time", 21 * 60) }.getOrDefault(21 * 60)
        if (pillOn) arm(app, am, RC_PILL, dailyNext(pillTime, after, zone), KIND_PILL, 0, "Pill reminder", "Time to take your pill.")
        else disarm(app, am, RC_PILL)

        val periodOn = runCatching { cp.getBoolean("remind_period", false) }.getOrDefault(false)
        val nextStart = runCatching { cp.getString("next_period_start", null)?.let { LocalDate.parse(it) } }.getOrNull()
        val periodAt = nextStart?.minusDays(2)?.atTime(9, 0)?.atZone(zone)?.toInstant()?.toEpochMilli()
        if (periodOn && periodAt != null && periodAt > after)
            arm(app, am, RC_PERIOD, periodAt, KIND_PERIOD, 0, "Cycle heads-up", "Your period may start in about 2 days.")
        else disarm(app, am, RC_PERIOD)

        // ---- fasting goal (one-off)
        val fastAt = p.getLong(K_FAST_AT, 0L)
        if (fastAt > System.currentTimeMillis())
            arm(app, am, RC_FAST, fastAt, KIND_FAST, 0, "Fasting goal reached", "You hit your fasting target. Nice work — break your fast gently.")
        else disarm(app, am, RC_FAST)
    }

    internal fun clearFastAlarm(ctx: Context) { prefs(ctx).edit().putLong(K_FAST_AT, 0L).apply() }

    /** Human label for a day mask, e.g. "Every day", "Weekdays", "Mon, Wed, Fri". */
    fun daysLabel(mask: Int): String {
        val m = mask and 0x7F
        return when (m) {
            0x7F -> "Every day"
            0x1F -> "Weekdays"
            0x60 -> "Weekends"
            0 -> "Never"
            else -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").filterIndexed { i, _ -> (m and (1 shl i)) != 0 }.joinToString(", ")
        }
    }
}
