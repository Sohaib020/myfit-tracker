package com.myfit.tracker.ui.routine

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Reminder
import com.myfit.tracker.data.db.ReminderType
import com.myfit.tracker.data.db.Supplement
import com.myfit.tracker.data.db.SupplementLog
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.reminders.ReminderNotifier
import com.myfit.tracker.reminders.ReminderScheduler
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val UNITS = listOf("g", "mg", "mcg", "IU", "capsule", "scoop", "ml")
private data class SuppPreset(val name: String, val dose: Double?, val unit: String)
private val PRESETS = listOf(
    SuppPreset("Creatine", 5.0, "g"), SuppPreset("Whey", 1.0, "scoop"), SuppPreset("Vitamin D", 1000.0, "IU"),
    SuppPreset("Omega-3", 1.0, "g"), SuppPreset("Multivitamin", 1.0, "capsule"), SuppPreset("Magnesium", 200.0, "mg"),
    SuppPreset("Iron", null, "mg"), SuppPreset("Caffeine", 200.0, "mg"),
)
private const val CREATINE_NOTE = "Creatine: daily consistency matters more than timing."
private val histFmt get() = com.myfit.tracker.domain.ClockFmt.f("EEE d MMM, ")

private fun suppKey(id: Long) = "supp_rem_$id"
private fun isCreatine(name: String) = name.contains("creatine", ignoreCase = true)

/** Creates/updates (timeMin != null) or disables (null) the SUPPLEMENT reminder linked to a supplement. */
private suspend fun syncSupplementReminder(container: AppContainer, ctx: Context, s: Supplement, timeMin: Int?) {
    val dao = container.db.reminderDao()
    val p = ReminderScheduler.prefs(ctx)
    val rid = p.getLong(suppKey(s.id), 0L)
    val existing = if (rid > 0) dao.get(rid) else null
    val now = Clock.now()
    if (timeMin == null) {
        if (existing != null) dao.update(existing.copy(enabled = false, updatedAt = now))
    } else {
        val dose = if (s.defaultDose > 0) " (${fmtNum(s.defaultDose)} ${s.unit})" else ""
        val msg = "Time for your ${s.name}$dose."
        if (existing != null) {
            dao.update(existing.copy(title = s.name, message = msg, timeMin = timeMin, enabled = true, updatedAt = now))
        } else {
            val id = dao.insert(
                Reminder(type = ReminderType.SUPPLEMENT, title = s.name, message = msg, timeMin = timeMin, enabled = true, createdAt = now, updatedAt = now)
            )
            p.edit().putLong(suppKey(s.id), id).apply()
        }
    }
    ReminderScheduler.rescheduleNow(ctx)
}

private fun streakOf(days: Set<String>, today: LocalDate): Int {
    var d = if (days.contains(Clock.dateKey(today))) today else today.minusDays(1)
    var n = 0
    while (days.contains(Clock.dateKey(d)) && n < 3650) { n++; d = d.minusDays(1) }
    return n
}

/** Percentage of days (last 30, or since the supplement was added if newer) with a taken log. */
private fun adherenceOf(days: Set<String>, createdAt: Long, today: LocalDate): Int {
    val created = Clock.localDateOf(createdAt)
    val window = (ChronoUnit.DAYS.between(created, today) + 1).coerceIn(1, 30).toInt()
    val taken = (0 until window).count { days.contains(Clock.dateKey(today.minusDays(it.toLong()))) }
    return taken * 100 / window
}

private sealed interface SuppSheet {
    data object Add : SuppSheet
    data class Detail(val s: Supplement) : SuppSheet
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupplementsContent(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val today = Clock.today()
    val todayKey = Clock.dateKey(today)
    val since = remember(todayKey) { Clock.dateKey(today.minusDays(400)) }
    val active by remember { container.db.supplementDao().observeActive() }.collectAsState(initial = null)
    val archived by remember { container.db.supplementDao().observeArchived() }.collectAsState(initial = emptyList())
    val logs by remember(since) { container.db.supplementLogDao().observeSince(since) }.collectAsState(initial = emptyList())
    val reminders by remember { container.db.reminderDao().observeAll() }.collectAsState(initial = emptyList())
    var sheet by remember { mutableStateOf<SuppSheet?>(null) }

    val prefs = remember { ReminderScheduler.prefs(ctx) }
    fun reminderFor(s: Supplement): Reminder? {
        val rid = prefs.getLong(suppKey(s.id), 0L)
        return if (rid > 0) reminders.firstOrNull { it.id == rid } else null
    }
    val takenDays: Map<Long, Set<String>> = remember(logs) {
        logs.filter { it.taken }.groupBy { it.supplementId }.mapValues { (_, l) -> l.map { it.localDate }.toSet() }
    }
    val allById = remember(active, archived) { (active.orEmpty() + archived).associateBy { it.id } }

    fun toggleToday(s: Supplement) {
        val todays = logs.filter { it.supplementId == s.id && it.localDate == todayKey && it.taken }
        container.write {
            if (todays.isNotEmpty()) {
                val now = Clock.now()
                todays.forEach { container.db.supplementLogDao().softDelete(it.id, now) }
            } else {
                val st = Stamp.now()
                container.db.supplementLogDao().insert(
                    SupplementLog(supplementId = s.id, dose = s.defaultDose, unit = s.unit, taken = true, loggedAt = st.at, zoneId = st.zoneId, localDate = st.localDate, createdAt = st.at, updatedAt = st.at)
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar("Supplements", { nav.pop() }, subtitle = "Track what you take, build the habit") {
                GlassIconButton(Duo.Add, { sheet = SuppSheet.Add })
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val list = active
                if (list == null) item { Caption("Loading…") }
                else if (list.isEmpty()) item {
                    GlassCard {
                        CardHeader(Duo.Inventory2, "No supplements yet", th.success)
                        Spacer(Modifier.height(8.dp))
                        Caption("Add what you take to get a daily checklist, streaks and optional reminders.")
                        Spacer(Modifier.height(12.dp))
                        AccentButton("Add supplement", { sheet = SuppSheet.Add }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 48.dp)
                    }
                } else {
                    item(key = "today") {
                        val done = list.count { takenDays[it.id]?.contains(todayKey) == true }
                        GlassCard {
                            CardHeader(Duo.CheckCircle, "Today", th.success) {
                                Text("$done / ${list.size}", style = FitType.label, color = th.textDim)
                            }
                            Spacer(Modifier.height(8.dp))
                            list.forEach { s ->
                                val days = takenDays[s.id].orEmpty()
                                val taken = days.contains(todayKey)
                                val streak = streakOf(days, today)
                                val adh = adherenceOf(days, s.createdAt, today)
                                val rem = reminderFor(s)
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (taken) Duo.CheckCircle else Duo.RadioButtonUnchecked, if (taken) "Taken" else "Not taken",
                                        tint = if (taken) th.success else th.textFaint,
                                        modifier = Modifier.size(30.dp).clickableNoRipple { toggleToday(s) },
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f).clickableNoRipple { sheet = SuppSheet.Detail(s) }) {
                                        Text(s.name, style = FitType.body, color = th.text)
                                        val dose = if (s.defaultDose > 0) "${fmtNum(s.defaultDose)} ${s.unit}" else s.unit
                                        val time = if (rem != null && rem.enabled) " · ${fmtMin(rem.timeMin)}" else ""
                                        Caption("$dose$time · ${if (streak > 0) "$streak-day streak" else "no streak"} · $adh% (30 d)")
                                    }
                                    Icon(Duo.KeyboardArrowRight, null, tint = th.textFaint, modifier = Modifier.size(20.dp).clickableNoRipple { sheet = SuppSheet.Detail(s) })
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Caption("Tap the circle to mark taken; tap the name to change the dose, time or archive.")
                        }
                    }
                    if (list.any { isCreatine(it.name) }) item(key = "creatine") {
                        GlassCard {
                            CardHeader(Duo.Info, "Tip", th.water)
                            Spacer(Modifier.height(6.dp))
                            Caption(CREATINE_NOTE)
                        }
                    }
                }

                val recent = logs.filter { it.taken && it.localDate >= Clock.dateKey(today.minusDays(29)) }
                if (recent.isNotEmpty()) item(key = "history") {
                    GlassCard {
                        CardHeader(Duo.History, "History (30 days)", th.accent)
                        Spacer(Modifier.height(6.dp))
                        recent.take(60).forEach { l ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(allById[l.supplementId]?.name ?: "Supplement", style = FitType.body, color = th.text)
                                    val at = Instant.ofEpochMilli(l.loggedAt).atZone(Clock.zone()).format(histFmt)
                                    Caption("${fmtNum(l.dose)} ${l.unit} · $at")
                                }
                                GlassIconButton(Duo.DeleteOutline, {
                                    container.write { container.db.supplementLogDao().softDelete(l.id, Clock.now()) }
                                    toaster.show("Entry deleted")
                                }, size = 36.dp, tint = th.textDim)
                            }
                        }
                        if (recent.size > 60) Caption("Showing the latest 60 entries.")
                    }
                }

                if (archived.isNotEmpty()) item(key = "archived") {
                    GlassCard {
                        CardHeader(Duo.Archive, "Archived", th.textDim)
                        Spacer(Modifier.height(6.dp))
                        archived.forEach { s ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(s.name, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                                GlassIconButton(Duo.Unarchive, {
                                    container.write { container.db.supplementDao().update(s.copy(archivedAt = null, updatedAt = Clock.now())) }
                                    toaster.show("${s.name} restored")
                                }, size = 36.dp, tint = th.textDim)
                            }
                        }
                    }
                }

                item(key = "disclaimer") {
                    Caption("Supplement tracking is for your own records, not medical advice. Follow the label or your clinician's guidance on doses.", Modifier.padding(horizontal = 6.dp))
                }
            }
        }

        val sh = sheet
        GlassSheet(visible = sh != null, onDismiss = { sheet = null }) {
            when (sh) {
                SuppSheet.Add -> AddSupplementForm { name, dose, unit, time ->
                    sheet = null
                    container.write {
                        val now = Clock.now()
                        val s = Supplement(name = name, defaultDose = dose, unit = unit, createdAt = now, updatedAt = now)
                        val id = container.db.supplementDao().insert(s)
                        if (time != null) syncSupplementReminder(container, ctx, s.copy(id = id), time)
                    }
                    if (time != null && !ReminderNotifier.canPost(ctx)) toaster.show("Added — allow notifications in Reminders to get nudges")
                    else toaster.show("$name added")
                }
                is SuppSheet.Detail -> SupplementDetailForm(
                    s = sh.s,
                    reminder = reminderFor(sh.s),
                    onLog = { dose ->
                        sheet = null
                        container.write {
                            val st = Stamp.now()
                            container.db.supplementLogDao().insert(
                                SupplementLog(supplementId = sh.s.id, dose = dose, unit = sh.s.unit, taken = true, loggedAt = st.at, zoneId = st.zoneId, localDate = st.localDate, createdAt = st.at, updatedAt = st.at)
                            )
                        }
                        toaster.show("Logged ${sh.s.name}")
                    },
                    onSave = { dose, unit, time ->
                        sheet = null
                        container.write {
                            val upd = sh.s.copy(defaultDose = dose, unit = unit, updatedAt = Clock.now())
                            container.db.supplementDao().update(upd)
                            syncSupplementReminder(container, ctx, upd, time)
                        }
                        toaster.show("Saved")
                    },
                    onArchive = {
                        sheet = null
                        container.write {
                            container.db.supplementDao().update(sh.s.copy(archivedAt = Clock.now(), updatedAt = Clock.now()))
                            syncSupplementReminder(container, ctx, sh.s, null)
                        }
                        toaster.show("${sh.s.name} archived")
                    },
                )
                null -> {}
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSupplementForm(onAdd: (String, Double, String, Int?) -> Unit) {
    val th = LocalFitTheme.current
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("g") }
    var timed by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(9 * 60) }

    Text("Add supplement", style = FitType.title, color = th.text)
    FieldLabel("Quick picks")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PRESETS.forEach { p ->
            GlassChip(p.name, name == p.name, {
                name = p.name; unit = p.unit; dose = p.dose?.let { fmtNum(it) } ?: ""
            })
        }
    }
    FieldLabel("Name")
    RoutineTextField(name, { name = it }, "e.g. Vitamin C")
    FieldLabel("Default dose")
    NumberInput(dose, { dose = it }, unit, big = false)
    if (name == "Iron") Caption("Iron doses vary a lot — use the amount on your label or from your clinician.", Modifier.padding(top = 4.dp))
    FieldLabel("Unit")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        UNITS.forEach { u -> GlassChip(u, unit == u, { unit = u }) }
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow("Daily reminder", "Get a notification at a set time", timed, { timed = it })
    if (timed) MinuteOfDayChip(time) { time = it }
    if (isCreatine(name)) Caption(CREATINE_NOTE, Modifier.padding(top = 8.dp), color = th.water)
    Spacer(Modifier.height(16.dp))
    AccentButton(
        "Add", { onAdd(name.trim(), dose.toDoubleOrNull() ?: 0.0, unit, if (timed) time else null) },
        Modifier.fillMaxWidth(), icon = Duo.Check, enabled = name.isNotBlank(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SupplementDetailForm(
    s: Supplement,
    reminder: Reminder?,
    onLog: (Double) -> Unit,
    onSave: (Double, String, Int?) -> Unit,
    onArchive: () -> Unit,
) {
    val th = LocalFitTheme.current
    var dose by remember(s.id) { mutableStateOf(if (s.defaultDose > 0) fmtNum(s.defaultDose) else "") }
    var unit by remember(s.id) { mutableStateOf(s.unit) }
    var timed by remember(s.id) { mutableStateOf(reminder?.enabled == true) }
    var time by remember(s.id) { mutableStateOf(reminder?.timeMin ?: (9 * 60)) }

    Text(s.name, style = FitType.title, color = th.text)
    if (isCreatine(s.name)) Caption(CREATINE_NOTE, Modifier.padding(top = 4.dp), color = th.water)
    FieldLabel("Dose")
    NumberInput(dose, { dose = it }, unit, big = false)
    Spacer(Modifier.height(10.dp))
    AccentButton("Mark taken now", { onLog(dose.toDoubleOrNull() ?: s.defaultDose) }, Modifier.fillMaxWidth(), icon = Duo.Check, height = 50.dp)

    FieldLabel("Unit")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        UNITS.forEach { u -> GlassChip(u, unit == u, { unit = u }) }
    }
    Spacer(Modifier.height(8.dp))
    SwitchRow("Daily reminder", "Shows up under Reminders → Supplements", timed, { timed = it })
    if (timed) MinuteOfDayChip(time) { time = it }
    Spacer(Modifier.height(14.dp))
    GlassButton("Save as default", { onSave(dose.toDoubleOrNull() ?: s.defaultDose, unit, if (timed) time else null) }, Modifier.fillMaxWidth(), icon = Duo.Save)
    Spacer(Modifier.height(10.dp))
    GlassButton("Archive supplement", onArchive, Modifier.fillMaxWidth(), icon = Duo.Archive)
    Caption("Archiving keeps your history and turns off its reminder.", Modifier.padding(top = 6.dp))
}
