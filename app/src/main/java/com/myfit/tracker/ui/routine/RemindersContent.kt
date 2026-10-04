package com.myfit.tracker.ui.routine

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Reminder
import com.myfit.tracker.data.db.ReminderType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.reminders.ReminderNotifier
import com.myfit.tracker.reminders.ReminderScheduler
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme

private const val K_SEEDED = "presets_seeded"

private data class Group(val type: String, val title: String, val icon: ImageVector, val color: Color)

private fun presetReminders(now: Long): List<Reminder> = listOf(
    Reminder(type = ReminderType.WATER, title = "Drink water", message = "Time for a glass of water.", timeMin = 9 * 60, intervalMin = 120, endTimeMin = 21 * 60, enabled = false, createdAt = now, updatedAt = now),
    Reminder(type = ReminderType.MEAL, title = "Breakfast", message = "Time for breakfast. Log it in MyFit when you eat.", timeMin = 8 * 60, enabled = false, createdAt = now, updatedAt = now),
    Reminder(type = ReminderType.MEAL, title = "Lunch", message = "Lunch time. Don't forget to log it.", timeMin = 13 * 60, enabled = false, createdAt = now, updatedAt = now),
    Reminder(type = ReminderType.MEAL, title = "Dinner", message = "Dinner time. Log your meal when you're done.", timeMin = 19 * 60 + 30, enabled = false, createdAt = now, updatedAt = now),
    Reminder(type = ReminderType.WORKOUT, title = "Workout", message = "Your workout is scheduled now. Let's go!", timeMin = 18 * 60, repeatDaysMask = 0x15, enabled = false, createdAt = now, updatedAt = now),
    Reminder(type = ReminderType.WEIGHT, title = "Weigh-in", message = "Quick weigh-in: same time, same conditions for the best trend.", timeMin = 7 * 60 + 30, enabled = false, createdAt = now, updatedAt = now),
    Reminder(type = ReminderType.SLEEP, title = "Wind down", message = "Screens down soon. Time to wind down for bed.", timeMin = 22 * 60, respectQuietHours = false, enabled = false, createdAt = now, updatedAt = now),
)

private fun subtitle(r: Reminder): String {
    val iv = r.intervalMin
    val end = r.endTimeMin
    val time = if (iv != null && iv > 0 && end != null) {
        val ivTxt = if (iv % 60 == 0) "${iv / 60} h" else "$iv min"
        "Every $ivTxt, ${fmtMin(r.timeMin)}–${fmtMin(end)}"
    } else fmtMin(r.timeMin)
    return "$time · ${ReminderScheduler.daysLabel(r.repeatDaysMask)}"
}

private fun openNotificationSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
internal fun RemindersContent(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val dao = container.db.reminderDao()
    val reminders by dao.observeAll().collectAsState(initial = null)
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val allowed = remember(refresh) { ReminderNotifier.canPost(ctx) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        refresh++
        if (ok) ReminderScheduler.reschedule(ctx) else toaster.show("Notifications are blocked — reminders can't appear")
    }
    fun askPermission() {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(ctx)
    }

    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }

    // First open: seed preset suggestions (all off) and ask for notification permission once.
    LaunchedEffect(Unit) {
        val p = ReminderScheduler.prefs(ctx)
        if (!p.getBoolean(K_SEEDED, false)) {
            container.write {
                if (dao.count() == 0) presetReminders(Clock.now()).forEach { dao.insert(it) }
                p.edit().putBoolean(K_SEEDED, true).apply()
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && !ReminderNotifier.canPost(ctx) && !p.getBoolean("perm_asked", false)) {
            p.edit().putBoolean("perm_asked", true).apply()
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun toggle(r: Reminder, on: Boolean) {
        if (on && !ReminderNotifier.canPost(ctx)) askPermission()
        container.write {
            dao.setEnabled(r.id, on, Clock.now())
            ReminderScheduler.rescheduleNow(ctx)
        }
    }

    val groups = listOf(
        Group(ReminderType.WATER, "Water", Duo.WaterDrop, th.water),
        Group(ReminderType.MEAL, "Meals", Duo.ForkKnife, th.protein),
        Group(ReminderType.WORKOUT, "Workout", Duo.FitnessCenter, th.accent),
        Group(ReminderType.WEIGHT, "Weigh-in", Duo.MonitorWeight, th.fat),
        Group(ReminderType.SLEEP, "Sleep wind-down", Duo.Bedtime, th.sleep),
        Group(ReminderType.SUPPLEMENT, "Supplements", Duo.Inventory2, th.success),
        Group(ReminderScheduler.CUSTOM_TYPE, "Custom", Duo.Bell, th.warning),
    )
    val known = groups.map { it.type }.toSet()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar("Reminders", { nav.pop() }, subtitle = "Gentle nudges, on your schedule")
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "nudges") { NudgesCard(ctx) }

                if (!allowed) item {
                    GlassCard {
                        CardHeader(Duo.Bell, "Notifications are off", th.danger)
                        Spacer(Modifier.height(8.dp))
                        Caption("MyFit can't show reminders until notifications are allowed for this app.")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AccentButton("Allow", { askPermission() }, Modifier.weight(1f), height = 46.dp)
                            GlassButton("Open settings", { openNotificationSettings(ctx) }, Modifier.weight(1f), height = 46.dp)
                        }
                    }
                }

                val list = reminders
                if (list == null) item { Caption("Loading…") }
                else {
                    groups.forEach { g ->
                        val rows = list.filter { it.type == g.type }
                        item(key = "g_" + g.type) {
                            GlassCard {
                                CardHeader(g.icon, g.title, g.color)
                                Spacer(Modifier.height(6.dp))
                                rows.forEach { r ->
                                    SwitchRow(r.title, subtitle(r), r.enabled, { toggle(r, it) }, onClick = { editing = r })
                                }
                                when (g.type) {
                                    ReminderType.SUPPLEMENT -> {
                                        if (rows.isEmpty()) Caption("Give a supplement a daily time and its reminder appears here.")
                                        Spacer(Modifier.height(8.dp))
                                        GlassButton("Manage supplements", { nav.push(Overlay.Supplements) }, Modifier.fillMaxWidth(), icon = Duo.Inventory2, height = 44.dp)
                                    }
                                    ReminderScheduler.CUSTOM_TYPE -> {
                                        if (rows.isEmpty()) Caption("Anything else you want a nudge for.")
                                        Spacer(Modifier.height(8.dp))
                                        GlassButton("Add custom reminder", {
                                            val now = Clock.now()
                                            editing = Reminder(type = ReminderScheduler.CUSTOM_TYPE, title = "", message = "", timeMin = 12 * 60, createdAt = now, updatedAt = now)
                                        }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 44.dp)
                                    }
                                    ReminderType.MEAL -> {
                                        Spacer(Modifier.height(8.dp))
                                        GlassButton("Add meal reminder", {
                                            val now = Clock.now()
                                            editing = Reminder(type = ReminderType.MEAL, title = "Snack", message = "Snack time.", timeMin = 16 * 60, createdAt = now, updatedAt = now)
                                        }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 44.dp)
                                    }
                                    else -> if (rows.isEmpty()) Caption("No reminder yet.")
                                }
                            }
                        }
                    }
                    val other = list.filter { it.type !in known }
                    if (other.isNotEmpty()) item(key = "g_other") {
                        GlassCard {
                            CardHeader(Duo.Bell, "Other", th.textDim)
                            other.forEach { r -> SwitchRow(r.title, subtitle(r), r.enabled, { toggle(r, it) }, onClick = { editing = r }) }
                        }
                    }
                }

                item(key = "quiet") { QuietHoursCard(ctx) }

                item(key = "test") {
                    GlassCard {
                        CardHeader(Duo.Send, "Test", th.water)
                        Spacer(Modifier.height(6.dp))
                        Caption("Sends a notification right now so you can check sound and look. Reminders use inexact alarms, so they may arrive a few minutes late to save battery.")
                        Spacer(Modifier.height(12.dp))
                        GlassButton("Send test notification", {
                            if (ReminderNotifier.canPost(ctx)) {
                                ReminderNotifier.post(ctx, ReminderScheduler.RC_TEST, "Test reminder", "Notifications are working. You're all set!")
                                toaster.show("Test sent")
                            } else askPermission()
                        }, Modifier.fillMaxWidth(), icon = Duo.Bell, height = 46.dp)
                    }
                }
            }
        }

        val e = editing
        GlassSheet(visible = e != null, onDismiss = { editing = null }) {
            if (e != null) ReminderEditForm(
                initial = e,
                onSave = { r ->
                    editing = null
                    if (r.enabled && !ReminderNotifier.canPost(ctx)) askPermission()
                    container.write {
                        if (r.id == 0L) dao.insert(r) else dao.update(r)
                        ReminderScheduler.rescheduleNow(ctx)
                    }
                    toaster.show("Reminder saved")
                },
                onDelete = if (e.id != 0L && (e.type == ReminderScheduler.CUSTOM_TYPE || (e.type == ReminderType.MEAL && e.title !in setOf("Breakfast", "Lunch", "Dinner")))) ({
                    editing = null
                    container.write {
                        dao.delete(e.id)
                        ReminderScheduler.rescheduleNow(ctx)
                    }
                    toaster.show("Reminder deleted")
                }) else null,
            )
        }
    }
}

@Composable
private fun NudgesCard(ctx: Context) {
    val th = LocalFitTheme.current
    var on by remember { mutableStateOf(com.myfit.tracker.reminders.Nudges.enabled(ctx)) }
    var types by remember { mutableStateOf(com.myfit.tracker.reminders.Nudges.Type.entries.associateWith { com.myfit.tracker.reminders.Nudges.typeOn(ctx, it) }) }
    var max by remember { mutableIntStateOf(com.myfit.tracker.reminders.Nudges.maxPerDay(ctx)) }
    var q by remember { mutableStateOf(com.myfit.tracker.reminders.Nudges.quiet(ctx)) }
    GlassCard {
        CardHeader(Duo.AutoAwesome, "Smart nudges", th.accentBright)
        Spacer(Modifier.height(6.dp))
        SwitchRow("Gentle nudges", "Only when you're behind, never more than $max a day", on, {
            on = it; com.myfit.tracker.reminders.Nudges.setEnabled(ctx, it)
        })
        if (on) {
            com.myfit.tracker.reminders.Nudges.Type.entries.forEach { t ->
                SwitchRow(t.label, t.sub, types[t] == true, { v -> types = types + (t to v); com.myfit.tracker.reminders.Nudges.setTypeOn(ctx, t, v) })
            }
            Spacer(Modifier.height(8.dp))
            Text("Max per day", style = FitType.label, color = th.textDim)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3, 4).forEach { n -> GlassChip("$n", max == n, { max = n; com.myfit.tracker.reminders.Nudges.setMaxPerDay(ctx, n) }) }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quiet", style = FitType.label, color = th.textDim)
                Spacer(Modifier.width(8.dp))
                MinuteOfDayChip(q.start) { q = q.copy(start = it); com.myfit.tracker.reminders.Nudges.setQuiet(ctx, q.start, q.end) }
                Spacer(Modifier.width(10.dp))
                Text("to", style = FitType.label, color = th.textDim)
                Spacer(Modifier.width(8.dp))
                MinuteOfDayChip(q.end) { q = q.copy(end = it); com.myfit.tracker.reminders.Nudges.setQuiet(ctx, q.start, q.end) }
            }
        }
    }
}

@Composable
private fun QuietHoursCard(ctx: Context) {
    val th = LocalFitTheme.current
    var q by remember { mutableStateOf(ReminderScheduler.quiet(ctx)) }
    fun update(n: ReminderScheduler.Quiet) { q = n; ReminderScheduler.setQuiet(ctx, n) }
    GlassCard {
        CardHeader(Duo.Bedtime, "Quiet hours", th.sleep)
        Spacer(Modifier.height(6.dp))
        SwitchRow("Silence reminders overnight", "Reminders that respect quiet hours are skipped in this window", q.on, { update(q.copy(on = it)) })
        if (q.on) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("From", style = FitType.label, color = th.textDim)
                Spacer(Modifier.width(8.dp))
                MinuteOfDayChip(q.start) { update(q.copy(start = it)) }
                Spacer(Modifier.width(14.dp))
                Text("to", style = FitType.label, color = th.textDim)
                Spacer(Modifier.width(8.dp))
                MinuteOfDayChip(q.end) { update(q.copy(end = it)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditForm(initial: Reminder, onSave: (Reminder) -> Unit, onDelete: (() -> Unit)?) {
    val th = LocalFitTheme.current
    val isInterval = initial.type == ReminderType.WATER
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var message by remember(initial.id) { mutableStateOf(initial.message) }
    var time by remember(initial.id) { mutableIntStateOf(initial.timeMin) }
    var interval by remember(initial.id) { mutableIntStateOf(initial.intervalMin ?: 120) }
    var end by remember(initial.id) { mutableIntStateOf(initial.endTimeMin ?: (21 * 60)) }
    var mask by remember(initial.id) { mutableIntStateOf(initial.repeatDaysMask) }
    var quiet by remember(initial.id) { mutableStateOf(initial.respectQuietHours) }
    var enabled by remember(initial.id) { mutableStateOf(if (initial.id == 0L) true else initial.enabled) }

    Text(if (initial.id == 0L) "New reminder" else "Edit reminder", style = FitType.title, color = th.text)
    FieldLabel("Title")
    RoutineTextField(title, { title = it }, "e.g. Stretch break")
    FieldLabel("Message")
    RoutineTextField(message, { message = it }, "What should the notification say?")

    FieldLabel(if (isInterval) "From" else "Time")
    Row(verticalAlignment = Alignment.CenterVertically) {
        MinuteOfDayChip(time) { time = it }
        if (isInterval) {
            Spacer(Modifier.width(10.dp))
            Text("until", style = FitType.label, color = th.textDim)
            Spacer(Modifier.width(10.dp))
            MinuteOfDayChip(end) { end = it }
        }
    }
    if (isInterval) {
        FieldLabel("Repeat every")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 60, 90, 120, 180, 240).forEach { m ->
                GlassChip(if (m % 60 == 0) "${m / 60} h" else "$m min", interval == m, { interval = m })
            }
        }
    }

    FieldLabel("Days")
    if (initial.type == ReminderType.WEIGHT) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlassChip("Daily", mask == 0x7F, { mask = 0x7F })
            GlassChip("Weekly (Mon)", mask == 0x01, { mask = 0x01 })
        }
        Spacer(Modifier.height(6.dp))
    }
    DayChips(mask) { mask = it }
    if ((mask and 0x7F) == 0) Caption("Pick at least one day.", color = th.warning)

    Spacer(Modifier.height(8.dp))
    SwitchRow("Respect quiet hours", "Skip this reminder during quiet hours", quiet, { quiet = it })
    SwitchRow("Enabled", null, enabled, { enabled = it })

    Spacer(Modifier.height(16.dp))
    AccentButton(
        "Save",
        {
            onSave(
                initial.copy(
                    title = title.trim().ifEmpty { "Reminder" },
                    message = message.trim().ifEmpty { title.trim().ifEmpty { "Reminder" } },
                    timeMin = time,
                    intervalMin = if (isInterval) interval else initial.intervalMin,
                    endTimeMin = if (isInterval) end else initial.endTimeMin,
                    repeatDaysMask = mask,
                    respectQuietHours = quiet,
                    enabled = enabled,
                    updatedAt = Clock.now(),
                )
            )
        },
        Modifier.fillMaxWidth(),
        icon = Duo.Check,
        enabled = (mask and 0x7F) != 0,
    )
    if (onDelete != null) {
        Spacer(Modifier.height(10.dp))
        GlassButton("Delete reminder", onDelete, Modifier.fillMaxWidth(), icon = Duo.DeleteOutline)
    }
}
