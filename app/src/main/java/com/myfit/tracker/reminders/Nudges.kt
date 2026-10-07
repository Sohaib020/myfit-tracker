package com.myfit.tracker.reminders

import com.myfit.tracker.notify.NKind as K
import android.Manifest
import android.app.AlarmManager
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
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.R
import com.myfit.tracker.data.db.ReminderType
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Targets
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Smart, gentle nudges — on by default, separate from the reminders people set themselves.
 *
 *  - Water (late morning, afternoon, early evening): only when you're behind your pace for the day.
 *  - Move (lunchtime, late afternoon): only when your steps are behind pace.
 *  - Workout days: 30 min before your usual workout time, only on planned days and only if you haven't trained yet.
 *  - Wind-down: 45 min before your bedtime (kept out of quiet hours).
 *  - Weigh-in: Monday morning, only if you haven't weighed in yet.
 *
 * Rules: at most [maxPerDay] a day (default 3), never in quiet hours (default 22:00–08:00), at least 90 min apart,
 * and the day's budget is reserved for workout / wind-down nudges still to come. Every condition is checked at
 * fire time, so a nudge that's no longer needed simply never appears.
 */
object Nudges {
    const val KIND = "nudge"
    const val CHANNEL = "nudges"
    private const val PREFS = "nudges"
    private const val RC_BASE = 940_000
    private const val GAP_MS = 90 * 60_000L

    enum class Type(val key: String, val label: String, val sub: String) {
        WATER("water", "Water", "When you're behind on water"),
        MOVE("move", "Move", "When your steps are behind pace"),
        WORKOUT("workout", "Workout days", "Before your usual workout time"),
        WIND_DOWN("winddown", "Wind-down", "45 min before bed"),
        WEIGH_IN("weighin", "Monday weigh-in", "If you haven't weighed in yet"),
    }

    private data class Slot(val id: Int, val type: Type, val minOfDay: Int, val days: Int = 0x7F)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(c: Context) = prefs(c).getBoolean("on", true)
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean("on", on).apply(); ReminderScheduler.reschedule(c) }
    fun typeOn(c: Context, t: Type) = prefs(c).getBoolean("t_" + t.key, true)
    fun setTypeOn(c: Context, t: Type, on: Boolean) { prefs(c).edit().putBoolean("t_" + t.key, on).apply(); ReminderScheduler.reschedule(c) }
    fun maxPerDay(c: Context) = prefs(c).getInt("max", 3).coerceIn(1, 6)
    fun setMaxPerDay(c: Context, n: Int) { prefs(c).edit().putInt("max", n.coerceIn(1, 6)).apply() }
    /** One quiet-hours setting for the whole app (Reminders → Quiet hours); nudges always respect it. */
    fun quiet(c: Context) = ReminderScheduler.quiet(c).copy(on = true)
    fun setQuiet(c: Context, start: Int, end: Int) = ReminderScheduler.setQuiet(c, ReminderScheduler.quiet(c).copy(start = start, end = end))

    private data class Rhythm(val wake: Int, val sleep: Int, val workout: Int, val workoutDays: Int)

    private suspend fun rhythm(c: Context): Rhythm {
        val p = (c.applicationContext as? MyFitApplication)?.container?.profileRepo?.profile?.first()
        return Rhythm(p?.wakeTimeMin ?: 7 * 60, p?.sleepTimeMin ?: 23 * 60, p?.workoutTimeMin ?: 18 * 60, p?.workoutDaysMask ?: 0)
    }

    private fun slots(c: Context, r: Rhythm): List<Slot> {
        val q = quiet(c)
        fun clear(m: Int): Int {            // move a time out of quiet hours to just before they start
            val mm = ((m % 1440) + 1440) % 1440
            return if (q.contains(mm)) (q.start - 30 + 1440) % 1440 else mm
        }
        return listOf(
            Slot(0, Type.WATER, 10 * 60 + 30), Slot(1, Type.WATER, 14 * 60), Slot(2, Type.WATER, 17 * 60 + 30),
            Slot(3, Type.MOVE, 12 * 60 + 30), Slot(4, Type.MOVE, 16 * 60 + 15),
            Slot(5, Type.WORKOUT, clear(r.workout - 30), r.workoutDays and 0x7F),
            Slot(6, Type.WIND_DOWN, clear(r.sleep - 45)),
            Slot(7, Type.WEIGH_IN, maxOf(8 * 60 + 30, q.end + 30), 1 shl (DayOfWeek.MONDAY.value - 1)),
        )
    }

    private fun intent(c: Context) = Intent(c, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_FIRE)

    /** Arms the next occurrence of every slot. Called from [ReminderScheduler.rescheduleNow]. */
    suspend fun arm(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val after = System.currentTimeMillis() + 60_000L
        val on = enabled(app)
        val r = rhythm(app)
        val q = quiet(app)
        for (s in slots(app, r)) {
            val rc = RC_BASE + s.id
            val at = if (on && typeOn(app, s.type) && (s.days and 0x7F) != 0 && !q.contains(s.minOfDay)) next(s, after, zone) else null
            if (at == null) {
                PendingIntent.getBroadcast(app, rc, intent(app), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)?.let { runCatching { am.cancel(it) }; it.cancel() }
                continue
            }
            val pi = PendingIntent.getBroadcast(app, rc,
                intent(app).putExtra(ReminderScheduler.EXTRA_KIND, KIND).putExtra(ReminderScheduler.EXTRA_ID, s.id.toLong()).putExtra(ReminderScheduler.EXTRA_NOTIF, rc),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    private fun next(s: Slot, after: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        for (o in 0..7) {
            val d = today.plusDays(o.toLong())
            if (s.days and (1 shl (d.dayOfWeek.value - 1)) == 0) continue
            val at = d.atTime(s.minOfDay / 60, s.minOfDay % 60).atZone(zone).toInstant().toEpochMilli()
            if (at > after) return at
        }
        return null
    }

    /** Fraction of the waking day that has passed (0 at wake-up, 1 at bedtime). */
    private fun dayFrac(r: Rhythm, now: Int): Double {
        val sleep = if (r.sleep <= r.wake) r.sleep + 1440 else r.sleep
        val n = if (now < r.wake) now + 1440 else now
        return ((n - r.wake).toDouble() / (sleep - r.wake)).coerceIn(0.0, 1.0)
    }

    private suspend fun stepsToday(app: Context): Long? {
        val c = (app as? MyFitApplication)?.container ?: return null
        val today = Clock.today()
        val hc = runCatching { c.healthSync.autoDays(today, today).firstOrNull()?.steps?.takeIf { it > 0 } }.getOrNull()
        if (hc != null) return hc
        val manual = runCatching { c.db.activityDao().observeDay(Clock.dateKey(today)).first() }.getOrNull().orEmpty()
        var total: Long? = null
        manual.sortedBy { it.loggedAt }.forEach { e -> val st = e.steps ?: return@forEach; total = if (e.isDayTotal) st.toLong() else (total ?: 0L) + st }
        return total
    }

    /** Called by [ReminderReceiver] when a slot's alarm fires. Decides whether the nudge is still useful. */
    suspend fun fire(ctx: Context, slotId: Int) {
        val app = ctx.applicationContext
        if (!enabled(app)) return
        val c = (app as? MyFitApplication)?.container ?: return
        val r = rhythm(app)
        val s = slots(app, r).firstOrNull { it.id == slotId } ?: return
        if (!typeOn(app, s.type)) return
        val zone = ZoneId.systemDefault()
        val nowZ = Instant.now().atZone(zone)
        val nowMin = nowZ.hour * 60 + nowZ.minute
        if (quiet(app).contains(nowMin)) return                      // a late delivery that drifted into quiet hours
        // daily budget
        val p = prefs(app)
        val todayKey = Clock.dateKey(Clock.today())
        val count = if (p.getString("day", "") == todayKey) p.getInt("count", 0) else 0
        val lastAt = p.getLong("last_at", 0L)
        val max = maxPerDay(app)
        if (count >= max) return
        val lowPriority = s.type == Type.WATER || s.type == Type.MOVE
        if (lowPriority) {
            if (System.currentTimeMillis() - lastAt < GAP_MS) return
            val dow = 1 shl (nowZ.dayOfWeek.value - 1)
            val reserved = slots(app, r).count { o ->
                (o.type == Type.WORKOUT || o.type == Type.WIND_DOWN) && typeOn(app, o.type) && o.minOfDay > nowMin && (o.days and dow) != 0
            }
            if (count + reserved >= max) return
        }
        val today = LocalDate.now(zone)
        val frac = dayFrac(r, nowMin)
        val msg: Pair<String, String> = when (s.type) {
            Type.WATER -> {
                val (ml, target) = runCatching { HabitActions.waterToday(c) }.getOrDefault(0.0 to null)
                val goal = target?.takeIf { it > 0 } ?: 2500.0
                if (ml >= goal * frac * 0.75 || ml >= goal) return
                pick(listOf(
                    "A sip break?" to "You're at ${(ml / 1000.0).let { "%.1f".format(java.util.Locale.US, it) }} L today. A glass now keeps you on track.",
                    "Water check" to "A glass of water now would be nice — you're a little behind today.",
                    "Stay hydrated" to "Small sips add up. Grab a glass when you can.",
                ))
            }
            Type.MOVE -> {
                val steps = stepsToday(app)
                val goal = Targets.on(c.profileRepo.targets.first(), TargetType.STEPS, today)?.takeIf { it > 0 } ?: 8000.0
                if (steps != null && (steps >= goal * frac * 0.6 || steps >= goal)) return
                if (steps == null && s.id != 4) return                      // no step data: one gentle afternoon nudge only
                if (steps == null) "Stretch your legs" to "A 5-minute walk is a great reset for body and mind."
                else pick(listOf(
                    "Time for a short walk?" to "You're at ${java.text.NumberFormat.getIntegerInstance().format(steps)} steps. A 10-minute walk adds about 1,000.",
                    "Little movement break" to "Stand up, stretch, take a lap. Your back will thank you.",
                ))
            }
            Type.WORKOUT -> {
                val trained = runCatching { c.db.workoutDao().observeDay(todayKey).first().isNotEmpty() }.getOrDefault(false)
                if (trained) return
                pick(listOf(
                    "Workout day" to "Your session is coming up. Even a short one counts.",
                    "Ready when you are" to "It's a planned training day. Open MyFit to start your workout.",
                ))
            }
            Type.WIND_DOWN -> pick(listOf(
                "Time to wind down" to "Dim the lights and put screens away soon for better sleep.",
                "Almost bedtime" to "A calm 10 minutes now helps you fall asleep faster.",
            ))
            Type.WEIGH_IN -> {
                val weighed = runCatching { c.db.weightDao().observeDay(todayKey).first().isNotEmpty() }.getOrDefault(false)
                if (weighed) return
                "Monday weigh-in" to "Same time, same conditions: a quick weigh-in keeps your trend honest."
            }
        }
        p.edit().putString("day", todayKey).putInt("count", count + 1).putLong("last_at", System.currentTimeMillis()).apply()
        val card = when (s.type) {
            Type.WATER -> runCatching {
                val (ml, target) = HabitActions.waterToday(c); val t = target?.takeIf { it > 0 } ?: 2500.0
                val l = { v: Double -> "%.1f".format(java.util.Locale.US, v / 1000.0) }
                com.myfit.tracker.notify.NCard(K.DROP, msg.first, msg.second, value = l(ml) + " L", progress = (ml / t).toFloat(), progressLabel = "${l(ml)} of ${l(t)} L today")
            }.getOrNull()
            Type.MOVE -> com.myfit.tracker.notify.NCard(K.RUN, msg.first, msg.second)
            Type.WORKOUT -> com.myfit.tracker.notify.NCard(K.LIFT, msg.first, msg.second)
            Type.WIND_DOWN -> com.myfit.tracker.notify.NCard(K.MOON, msg.first, msg.second)
            Type.WEIGH_IN -> com.myfit.tracker.notify.NCard(K.BELL, msg.first, msg.second)
        } ?: com.myfit.tracker.notify.NCard(K.BELL, msg.first, msg.second)
        post(app, RC_BASE + s.id, card, water = s.type == Type.WATER)
    }

    private fun <T> pick(l: List<T>): T = l[(System.currentTimeMillis() / 3_600_000L % l.size).toInt()]

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Gentle nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Soft water, movement, workout, wind-down and weigh-in nudges (max a few a day)"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 60)
        })
    }

    private fun post(c: Context, id: Int, card: com.myfit.tracker.notify.NCard, water: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(c)
        val open = PendingIntent.getActivity(c, id, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = com.myfit.tracker.notify.NotifKit.apply(c, NotificationCompat.Builder(c, CHANNEL), card)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true).setContentIntent(open)
            .setTimeoutAfter(3 * 3_600_000L)                         // stale nudges tidy themselves away
        if (water) {
            val i = Intent(c, ReminderReceiver::class.java).setAction(ReminderScheduler.ACTION_DONE)
                .putExtra(ReminderScheduler.EXTRA_TYPE, ReminderType.WATER).putExtra(ReminderScheduler.EXTRA_NOTIF, id)
            b.addAction(R.drawable.ic_notification, "+250 ml", PendingIntent.getBroadcast(c, id + ReminderScheduler.RC_DONE_OFFSET, i,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        runCatching { NotificationManagerCompat.from(c).notify(id, b.build()) }
    }
}
