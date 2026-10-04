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
 * Handles: reminder alarms (ACTION_FIRE), the notification buttons (ACTION_DONE / ACTION_SNOOZE,
 * plus the legacy ACTION_WATER), and system events that invalidate alarms
 * (boot, clock/time-zone change, app update) → reschedule.
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
                if (intent.getStringExtra(ReminderScheduler.EXTRA_KIND) != ReminderScheduler.KIND_SNOOZED) ReminderScheduler.rescheduleNow(app)
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
            else -> ReminderScheduler.rescheduleNow(app)   // BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED
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
                ReminderNotifier.post(app, nid, title, text)
            }
            ReminderScheduler.KIND_PILL, ReminderScheduler.KIND_PERIOD ->
                ReminderNotifier.post(app, nid, title, text, doneLabel = "Done", payload = payload, snooze = kind == ReminderScheduler.KIND_PILL, discreet = true)
            else -> ReminderNotifier.post(app, nid, title, text)
        }
    }
}
