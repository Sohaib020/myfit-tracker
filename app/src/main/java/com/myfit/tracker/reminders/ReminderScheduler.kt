package com.myfit.tracker.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.data.db.MedKind
import com.myfit.tracker.data.db.Reminder
import com.myfit.tracker.domain.Clock
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
 * arms the following occurrence.
 *
 * Alarms are exact only when the system allows it without extra permissions (Android 11 and older,
 * or when the user granted "Alarms & reminders"); otherwise they're inexact (`setAndAllowWhileIdle`)
 * and Android may deliver them a few minutes late to save battery.
 *
 * Day mask convention (Reminder.repeatDaysMask): bit0 = Monday, bit1 = Tuesday … bit6 = Sunday,
 * i.e. bit index = DayOfWeek.value - 1. 0x7F = every day; 0 = never.
 *
 * Public API for other features (e.g. cycle / pill reminders from the health area):
 *   ReminderScheduler.schedule(context, reminder, discreet = true)  → saves + arms (returns at once)
 *   ReminderScheduler.cancel(context, reminderId)
 */
object ReminderScheduler {
    const val PREFS = "reminder_prefs"

    // Reminder types not in ReminderType (kept here so the entity file stays untouched)
    const val CUSTOM_TYPE = "CUSTOM"
    const val MEDICINE = "MEDICINE"           // title = medicine name; "Done" logs it (never for insulin)
    const val GLUCOSE = "GLUCOSE"
    const val FAST_START = "FAST_START"       // "time to start your fast / eating window closes"
    const val FAST_END = "FAST_END"           // "you can break your fast / iftar"
    const val CYCLE = "CYCLE"                 // discreet by default

    // prefs keys
    const val K_QUIET_ON = "quiet_on"
    const val K_QUIET_START = "quiet_start"    // minute of day, default 22:30
    const val K_QUIET_END = "quiet_end"        // minute of day, default 07:00
    const val K_WATER_SKIP_GOAL = "water_skip_goal"
    const val K_SNOOZE_MIN = "snooze_min"
    const val K_MED_AUTO = "med_auto"          // remind for medicine times set in Medicines
    const val K_MED_DISCREET = "med_discreet"
    private const val K_DISCREET = "discreet_ids"
    private const val K_SCHEDULED = "scheduled_ids"
    private const val K_EXTRA_COUNT = "extra_count"
    private const val K_FAST_AT = "fast_alarm_at"
    const val DEFAULT_QUIET_START = 22 * 60 + 30
    const val DEFAULT_QUIET_END = 7 * 60

    // intent plumbing
    const val ACTION_FIRE = "com.myfit.tracker.reminders.FIRE"
    const val ACTION_WATER = "com.myfit.tracker.reminders.ADD_WATER"   // legacy, still handled
    const val ACTION_DONE = "com.myfit.tracker.reminders.DONE"
    const val ACTION_SNOOZE = "com.myfit.tracker.reminders.SNOOZE"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "rid"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_NOTIF = "notif"
    const val EXTRA_TYPE = "rtype"
    const val EXTRA_NAME = "name"
    const val KIND_DB = "db"
    const val KIND_PILL = "pill"
    const val KIND_PERIOD = "period"
    const val KIND_FAST = "fast"
    const val KIND_EXTRA = "extra"             // medicine times from the Medicines screen
    const val KIND_SNOOZED = "snoozed"         // a one-off re-post of any of the above

    // request codes (also used as notification ids)
    private const val RC_DB_BASE = 100_000
    const val RC_PILL = 900_001
    const val RC_PERIOD = 900_002
    const val RC_FAST = 900_003
    const val RC_TEST = 900_009
    private const val RC_EXTRA_BASE = 910_000
    const val RC_WATER_ACTION_OFFSET = 500_000
    const val RC_DONE_OFFSET = 2_000_000
    const val RC_SNOOZE_ACTION_OFFSET = 3_000_000
    const val RC_SNOOZE_ALARM_OFFSET = 4_000_000

    fun rcFor(reminderId: Long): Int = RC_DB_BASE + (reminderId % 400_000).toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Re-arms every alarm on a background thread. Safe to call from the main thread (app start, after edits). */
    fun reschedule(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { runCatching { rescheduleNow(app) } }
    }

    /**
     * Saves [reminder] (insert when id == 0, else update) and re-arms alarms. Returns immediately;
     * the work runs in the background. [discreet] replaces the notification text with a neutral
     * "Reminder" so nothing sensitive shows on the lock screen or in the shade.
     */
    fun schedule(context: Context, reminder: Reminder, discreet: Boolean = false) {
        val app = context.applicationContext
        scope.launch { runCatching { scheduleNow(app, reminder, discreet) } }
    }

    /** Suspending variant of [schedule]; returns the reminder id. */
    suspend fun scheduleNow(context: Context, reminder: Reminder, discreet: Boolean = false): Long {
        val app = context.applicationContext
        val container = (app as? MyFitApplication)?.container ?: return -1L
        val dao = container.db.reminderDao()
        val r = reminder.copy(updatedAt = Clock.now())
        val id = if (r.id == 0L) dao.insert(r) else { dao.update(r); r.id }
        setDiscreet(app, id, discreet)
        rescheduleNow(app)
        return id
    }

    /** Deletes a reminder and its alarm. */
    fun cancel(context: Context, reminderId: Long) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val container = (app as? MyFitApplication)?.container ?: return@runCatching
                container.db.reminderDao().delete(reminderId)
                setDiscreet(app, reminderId, false)
                ReminderNotifier.cancel(app, rcFor(reminderId))
                rescheduleNow(app)
            }
        }
    }

    fun isDiscreet(ctx: Context, id: Long): Boolean =
        prefs(ctx).getStringSet(K_DISCREET, emptySet()).orEmpty().contains(id.toString())

    fun setDiscreet(ctx: Context, id: Long, on: Boolean) {
        val p = prefs(ctx)
        val s = p.getStringSet(K_DISCREET, emptySet()).orEmpty().toMutableSet()
        if (on) s.add(id.toString()) else s.remove(id.toString())
        p.edit().putStringSet(K_DISCREET, s).apply()
    }

    fun snoozeMin(ctx: Context): Int = prefs(ctx).getInt(K_SNOOZE_MIN, 10).coerceIn(1, 120)
    fun waterSkipGoal(ctx: Context): Boolean = prefs(ctx).getBoolean(K_WATER_SKIP_GOAL, true)
    fun medAuto(ctx: Context): Boolean = prefs(ctx).getBoolean(K_MED_AUTO, true)
    fun medDiscreet(ctx: Context): Boolean = prefs(ctx).getBoolean(K_MED_DISCREET, false)

    /** True when exact alarms can be used without asking for anything. */
    fun exactAllowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
    }

    data class Extra(val title: String, val text: String, val minOfDay: Int, val medName: String?, val canLog: Boolean)

    /** Parses "08:00,20:00" (the Medicines screen's format) into minutes of the day. */
    private fun parseTimes(times: String): List<Int> = times.split(',').mapNotNull { part ->
        val p = part.trim().split(':')
        if (p.size != 2) return@mapNotNull null
        val h = p[0].trim().toIntOrNull(); val m = p[1].trim().toIntOrNull()
        if (h == null || m == null || h !in 0..23 || m !in 0..59) null else h * 60 + m
    }.distinct().sorted()

    /**
     * Daily notifications owned by other features: medicine times set on the Medicines screen
     * (skipped when a MEDICINE reminder with the same name + time already exists).
     */
    suspend fun extraDaily(ctx: Context, existing: List<Reminder>): List<Extra> {
        if (!medAuto(ctx)) return emptyList()
        val container = (ctx.applicationContext as? MyFitApplication)?.container ?: return emptyList()
        val meds = runCatching { container.db.medicationDao().active() }.getOrDefault(emptyList())
        val discreet = medDiscreet(ctx)
        val covered = existing.filter { it.type == MEDICINE }.map { it.title.trim().lowercase() to it.timeMin }.toSet()
        return meds.filter { it.remind }.flatMap { m ->
            parseTimes(m.times).filter { (m.name.trim().lowercase() to it) !in covered }.map { t ->
                val insulin = m.kind == MedKind.INSULIN_RAPID || m.kind == MedKind.INSULIN_LONG
                val title = if (discreet) "Reminder" else m.name
                val text = when {
                    discreet -> "A gentle reminder from MyFit."
                    insulin -> "Time for ${m.name}. Open MyFit to record the dose you take."
                    else -> "Time for ${m.name}" + (m.dose?.let { d -> " (${trimNum(d)} ${m.unit})" } ?: "") + "."
                }
                Extra(title, text, t, m.name, canLog = !insulin)
            }
        }.take(60)
    }

    private fun trimNum(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(java.util.Locale.US, v)

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

    private fun setAlarm(ctx: Context, am: AlarmManager, at: Long, pi: PendingIntent) {
        val exact = exactAllowed(ctx)
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }.onFailure { runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } }
    }

    private fun arm(
        ctx: Context, am: AlarmManager, rc: Int, at: Long, kind: String, id: Long, title: String, text: String,
        type: String? = null, name: String? = null,
    ) {
        val i = fireIntent(ctx).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_NOTIF, rc)
        if (type != null) i.putExtra(EXTRA_TYPE, type)
        if (name != null) i.putExtra(EXTRA_NAME, name)
        val pi = pending(ctx, rc, i, true) ?: return
        setAlarm(ctx, am, at, pi)
    }

    private fun disarm(ctx: Context, am: AlarmManager, rc: Int) {
        val pi = pending(ctx, rc, fireIntent(ctx), false) ?: return
        runCatching { am.cancel(pi) }
        runCatching { pi.cancel() }
    }

    /**
     * Re-posts a notification after [snoozeMin] minutes. The snoozed copy keeps its actions:
     * [extras] carries everything the original fire intent had.
     */
    fun snooze(ctx: Context, nid: Int, extras: Intent) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val i = fireIntent(app).putExtras(extras).putExtra(EXTRA_KIND, KIND_SNOOZED).putExtra(EXTRA_NOTIF, nid)
            .putExtra("orig_kind", extras.getStringExtra(EXTRA_KIND))
        val pi = pending(app, nid + RC_SNOOZE_ALARM_OFFSET, i, true) ?: return
        setAlarm(app, am, System.currentTimeMillis() + snoozeMin(app) * 60_000L, pi)
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
            arm(app, am, rc, at, KIND_DB, r.id, r.title, r.message, type = r.type)
            armed.add(rc.toString())
        }
        val previous = p.getStringSet(K_SCHEDULED, emptySet()).orEmpty()
        for (old in previous) if (old !in armed) old.toIntOrNull()?.let { disarm(app, am, it) }
        p.edit().putStringSet(K_SCHEDULED, armed).apply()

        // ---- extra daily hooks (medicine times from the Medicines screen)
        val extras = runCatching { extraDaily(app, reminders) }.getOrDefault(emptyList())
        extras.forEachIndexed { i, e ->
            val at = dailyNext(e.minOfDay, after, zone)
            arm(app, am, RC_EXTRA_BASE + i, at, KIND_EXTRA, if (e.canLog) 1L else 0L, e.title, e.text, type = MEDICINE, name = e.medName)
        }
        val oldExtra = p.getInt(K_EXTRA_COUNT, 0)
        for (i in extras.size until oldExtra) disarm(app, am, RC_EXTRA_BASE + i)
        p.edit().putInt(K_EXTRA_COUNT, extras.size).apply()

        // ---- cycle reminders (read raw prefs; no dependency on the cycle feature's classes).
        // Discreet unless the cycle settings explicitly turn it off ("discreet" = false).
        val cp = app.getSharedPreferences("cycle_prefs", Context.MODE_PRIVATE)
        val cycleDiscreet = runCatching { cp.getBoolean("discreet", true) }.getOrDefault(true)
        val pillOn = runCatching { cp.getBoolean("pill_on", false) }.getOrDefault(false)
        val pillTime = runCatching { cp.getInt("pill_time", 21 * 60) }.getOrDefault(21 * 60)
        if (pillOn) arm(
            app, am, RC_PILL, dailyNext(pillTime, after, zone), KIND_PILL, 0,
            if (cycleDiscreet) "Daily reminder" else "Pill reminder",
            if (cycleDiscreet) "A gentle reminder from MyFit." else "Time to take your pill.",
        ) else disarm(app, am, RC_PILL)

        val periodOn = runCatching { cp.getBoolean("remind_period", false) }.getOrDefault(false)
        val nextStart = runCatching { cp.getString("next_period_start", null)?.let { LocalDate.parse(it) } }.getOrNull()
        val periodAt = nextStart?.minusDays(2)?.atTime(9, 0)?.atZone(zone)?.toInstant()?.toEpochMilli()
        if (periodOn && periodAt != null && periodAt > after) arm(
            app, am, RC_PERIOD, periodAt, KIND_PERIOD, 0,
            if (cycleDiscreet) "Heads-up" else "Cycle heads-up",
            if (cycleDiscreet) "You have an upcoming reminder in MyFit." else "Your period may start in about 2 days (estimate).",
        ) else disarm(app, am, RC_PERIOD)

        // ---- fasting goal (one-off)
        val fastAt = p.getLong(K_FAST_AT, 0L)
        if (fastAt > System.currentTimeMillis())
            arm(app, am, RC_FAST, fastAt, KIND_FAST, 0, "Fasting goal reached", "You hit your fasting target. Nice work — break your fast gently.")
        else disarm(app, am, RC_FAST)

        // ---- Shariah & Health: adhan and suhoor alerts (next 2 days)
        val deen = runCatching { com.myfit.tracker.ui.deen.DeenAlarms.upcoming(app, after) }.getOrDefault(emptyList())
        val deenRcs = deen.map { it.rc }.toSet()
        deen.forEach { a -> arm(app, am, a.rc, a.at, "deen", 0, a.title, a.text) }
        for (rc in (920_000..920_016) + (920_100..920_101)) if (rc !in deenRcs) disarm(app, am, rc)

        // ---- health features: medicines, blood-sugar checks, Ramadan, HbA1c, pregnancy appointments
        runCatching { com.myfit.tracker.health.HealthReminders.arm(app) }
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
