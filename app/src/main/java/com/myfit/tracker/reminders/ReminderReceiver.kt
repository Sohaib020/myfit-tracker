package com.myfit.tracker.reminders

import com.myfit.tracker.notify.NKind as K
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
 * Handles: reminder alarms (ACTION_FIRE) and the notification buttons (ACTION_DONE / ACTION_SNOOZE,
 * plus the legacy ACTION_WATER). Not exported: only MyFit's own PendingIntents reach it.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, intent)
            } catch (_: Throwable) {
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun handle(app: Context, intent: Intent) {
        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                fire(app, intent)
                val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND)
                if (kind != ReminderScheduler.KIND_SNOOZED) ReminderScheduler.rearmAfterFire(app, kind, intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L))
            }
            ReminderScheduler.ACTION_WATER -> {
                val container = (app as? MyFitApplication)?.container ?: return
                container.logRepo.addWater(250.0)
                val nid = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIF, 0)
                if (nid != 0) ReminderNotifier.cancel(app, nid)
            }
            ReminderScheduler.ACTION_DONE -> done(app, intent)
            ReminderScheduler.ACTION_SNOOZE -> {
                val nid = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIF, 0)
                if (nid != 0) {
                    ReminderNotifier.cancel(app, nid)
                    ReminderScheduler.snooze(app, nid, intent)
                }
            }
            else -> Unit   // system broadcasts go to SystemEventsReceiver (this receiver isn't exported)
        }
    }

    /** "Done" button: log the thing where that makes sense, then clear the notification. */
    private suspend fun done(app: Context, intent: Intent) {
        val nid = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIF, 0)
        val container = (app as? MyFitApplication)?.container
        if (container != null) {
            val type = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)
            val rid = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
            when (type) {
                ReminderType.WATER -> container.logRepo.addWater(250.0)
                ReminderType.SUPPLEMENT -> {
                    val sid = HabitActions.supplementIdForReminder(app, rid)
                    if (sid != null && !HabitActions.takenToday(container, sid)) HabitActions.logSupplement(container, app, sid)
                }
                ReminderScheduler.MEDICINE -> {
                    val name = intent.getStringExtra(ReminderScheduler.EXTRA_NAME)
                        ?: container.db.reminderDao().get(rid)?.title
                    if (name != null) HabitActions.logMedicineByName(container, name)
                }
            }
            runCatching { com.myfit.tracker.domain.BadgeEngine.refresh(container, force = true) }
        }
        if (nid != 0) ReminderNotifier.cancel(app, nid)
    }

    /** A richer card for reminders from the reminder list: water shows today's progress, the rest pick a matching animation. */
    private suspend fun cardFor(container: com.myfit.tracker.AppContainer, type: String, title: String, text: String): com.myfit.tracker.notify.NCard {
        return when (type) {
            ReminderType.WATER -> {
                val (ml, target) = HabitActions.waterToday(container)
                val t = target?.takeIf { it > 0 } ?: 2500.0
                val l = { v: Double -> "%.1f".format(java.util.Locale.US, v / 1000.0) }
                com.myfit.tracker.notify.NCard(K.DROP, title, text, value = l(ml) + " L", progress = (ml / t).toFloat(), progressLabel = "${l(ml)} of ${l(t)} L today")
            }
            ReminderType.SUPPLEMENT, ReminderScheduler.MEDICINE -> com.myfit.tracker.notify.NCard(K.PILL, title, text, chip = com.myfit.tracker.domain.ClockFmt.f().format(java.time.LocalTime.now()))
            ReminderType.WORKOUT -> com.myfit.tracker.notify.NCard(K.LIFT, title, text)
            ReminderType.STEPS -> com.myfit.tracker.notify.NCard(K.RUN, title, text)
            ReminderType.MEAL -> com.myfit.tracker.notify.NCard(K.MEAL, title, text)
            ReminderType.SLEEP -> com.myfit.tracker.notify.NCard(K.MOON, title, text)
            ReminderType.WEEKLY_REPORT -> com.myfit.tracker.notify.NCard(K.TROPHY, title, text)
            else -> com.myfit.tracker.notify.NCard(ReminderNotifier.guessKind(title, text), title, text)
        }
    }

    private fun doneLabelFor(type: String?, canLogMed: Boolean): String? = when (type) {
        ReminderType.WATER -> "+250 ml"
        ReminderType.SUPPLEMENT -> "Taken"
        ReminderScheduler.MEDICINE -> if (canLogMed) "Taken" else null
        else -> "Done"
    }

    private suspend fun fire(app: Context, intent: Intent) {
        var kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val snoozed = kind == ReminderScheduler.KIND_SNOOZED
        if (snoozed) kind = intent.getStringExtra("orig_kind") ?: ReminderScheduler.KIND_EXTRA
        val nid = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIF, ReminderScheduler.RC_TEST)
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(ReminderScheduler.EXTRA_TEXT).orEmpty()
        // the payload the buttons send back (original kind, so a snoozed copy behaves like the first)
        val payload = Intent().putExtras(intent).putExtra(ReminderScheduler.EXTRA_KIND, kind)
        when (kind) {
            ReminderScheduler.KIND_DB -> {
                val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
                val container = (app as? MyFitApplication)?.container ?: return
                val r = container.db.reminderDao().get(id) ?: return
                if (!r.enabled) return
                // a late delivery that drifted into quiet hours is dropped
                val now = Instant.now().atZone(ZoneId.systemDefault())
                // (a once-a-day reminder that was moved to the end of quiet hours fires right at that edge, which isn't "in" the window)
                if (!snoozed && r.respectQuietHours && ReminderScheduler.quiet(app).contains(now.hour * 60 + now.minute)) return
                // smart water: stay silent once today's goal is reached
                if (r.type == ReminderType.WATER && ReminderScheduler.waterSkipGoal(app)) {
                    val (ml, target) = runCatching { HabitActions.waterToday(container) }.getOrDefault(0.0 to null)
                    if (target != null && target > 0 && ml >= target) return
                }
                // supplement already ticked off today → no nudge
                if (r.type == ReminderType.SUPPLEMENT) {
                    val sid = HabitActions.supplementIdForReminder(app, r.id)
                    if (sid != null && runCatching { HabitActions.takenToday(container, sid) }.getOrDefault(false)) return
                }
                val discreet = ReminderScheduler.isDiscreet(app, r.id)
                val canLogMed = r.type != ReminderScheduler.MEDICINE ||
                    runCatching { container.db.medicationDao().active().any { it.name.equals(r.title, true) && !it.kind.startsWith("INSULIN") } }.getOrDefault(false)
                payload.putExtra(ReminderScheduler.EXTRA_TYPE, r.type)
                ReminderNotifier.post(
                    app, nid,
                    if (discreet) "Reminder" else r.title,
                    if (discreet) "A gentle reminder from MyFit." else r.message,
                    doneLabel = doneLabelFor(r.type, canLogMed), payload = payload, snooze = true, discreet = discreet,
                    card = runCatching { cardFor(container, r.type, r.title, r.message) }.getOrNull(),
                )
            }
            ReminderScheduler.KIND_EXTRA -> {
                val canLog = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0L) == 1L
                ReminderNotifier.post(
                    app, nid, title, text,
                    doneLabel = if (canLog) "Taken" else null, payload = payload, snooze = true,
                    discreet = ReminderScheduler.medDiscreet(app),
                )
            }
            Nudges.KIND -> Nudges.fire(app, intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L).toInt())
            ReminderScheduler.KIND_FAST -> {
                ReminderScheduler.clearFastAlarm(app)
                ReminderNotifier.post(app, nid, title, text, card = com.myfit.tracker.notify.NCard(com.myfit.tracker.notify.NKind.TROPHY, title, text, progress = 1f, progressLabel = "Fast complete"))
            }
            ReminderScheduler.KIND_PILL, ReminderScheduler.KIND_PERIOD ->
                ReminderNotifier.post(app, nid, title, text, doneLabel = "Done", payload = payload, snooze = kind == ReminderScheduler.KIND_PILL, discreet = true)
            else -> ReminderNotifier.post(app, nid, title, text)
        }
    }
}
