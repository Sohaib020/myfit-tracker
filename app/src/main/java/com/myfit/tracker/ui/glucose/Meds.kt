package com.myfit.tracker.ui.glucose

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MedKind
import com.myfit.tracker.data.db.Medication
import com.myfit.tracker.data.db.MedicationLog
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.Toaster
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm get() = com.myfit.tracker.domain.ClockFmt.f()

private fun isInsulin(kind: String) = kind == MedKind.INSULIN_RAPID || kind == MedKind.INSULIN_LONG

private fun doseText(dose: Double?, unit: String): String {
    val d = dose?.let { if (it == Math.floor(it)) it.toLong().toString() else String.format(Locale.US, "%.1f", it) }
    return listOfNotNull(d, unit.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { "No dose set" }
}

/** Records a dose taken now (record only — never a suggestion). */
private fun logDose(container: AppContainer, toaster: Toaster, m: Medication) {
    val st = Stamp.now()
    val log = MedicationLog(
        medicationId = m.id, name = m.name, kind = m.kind, dose = m.dose, unit = m.unit,
        takenAt = st.at, zoneId = st.zoneId, localDate = st.localDate, createdAt = st.at, updatedAt = st.at,
    )
    container.write {
        val id = container.db.medicationLogDao().insert(log)
        withContext(Dispatchers.Main) {
            toaster.show("Logged ${m.name}", "Undo") { container.write { container.db.medicationLogDao().softDelete(id, Clock.now()) } }
        }
    }
}

// =================================================================== screen

@Composable
internal fun MedsContent(container: AppContainer) {
    val nav = LocalNav.current
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val todayKey = Clock.dateKey(Clock.today())
    val meds = remember { container.db.medicationDao().observeActive() }.collectAsState(initial = null).value
    val archived = remember { container.db.medicationDao().observeArchived() }.collectAsState(initial = emptyList()).value
    val logs = remember(todayKey) { container.db.medicationLogDao().observeOn(todayKey) }.collectAsState(initial = emptyList()).value
    var editing by remember { mutableStateOf<Medication?>(null) }
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar("Medicines", { nav.pop() }, subtitle = "Record only") {
                GlassIconButton(Duo.Add, { adding = true })
            }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { TodayDosesCard(meds ?: emptyList(), logs) }
                if (meds != null && meds.isEmpty()) item {
                    GlassCard {
                        CardHeader(Duo.Inventory2, "No medicines yet", th.accent)
                        Spacer(Modifier.height(8.dp))
                        Caption("Add tablets, insulin or anything else you take, with optional reminder times.")
                        Spacer(Modifier.height(12.dp))
                        AccentButton("Add medicine", { adding = true }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 48.dp)
                    }
                }
                items(meds ?: emptyList(), key = { it.id }) { m ->
                    MedCard(
                        m = m,
                        takenToday = logs.count { it.medicationId == m.id },
                        onTake = { logDose(container, toaster, m) },
                        onEdit = { editing = m },
                        onUpdate = { u -> container.write { container.db.medicationDao().update(u.copy(updatedAt = Clock.now())) } },
                        onArchive = {
                            container.write { container.db.medicationDao().setArchived(m.id, Clock.now(), Clock.now()) }
                            toaster.show("${m.name} archived", "Undo") { container.write { container.db.medicationDao().setArchived(m.id, null, Clock.now()) } }
                        },
                    )
                }
                if (logs.isNotEmpty()) item {
                    GlassCard {
                        CardHeader(Duo.History, "Taken today", th.success)
                        Spacer(Modifier.height(6.dp))
                        logs.sortedByDescending { it.takenAt }.forEach { l ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Instant.ofEpochMilli(l.takenAt).atZone(Clock.zone()).format(hm), style = FitType.label, color = th.textDim, modifier = Modifier.width(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(l.name, style = FitType.body, color = th.text)
                                    Caption(doseText(l.dose, l.unit))
                                }
                                GlassIconButton(Duo.DeleteOutline, {
                                    container.write { container.db.medicationLogDao().softDelete(l.id, Clock.now()) }
                                    toaster.show("Dose removed")
                                }, size = 34.dp, tint = th.textDim)
                            }
                        }
                    }
                }
                if (archived.isNotEmpty()) {
                    item { SectionTitle("Archived") }
                    items(archived, key = { "a" + it.id }) { m ->
                        GlassCard(padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = FitType.body, color = th.textDim)
                                    Caption(GlucoseReport.kindLabel(m.kind) + " · " + doseText(m.dose, m.unit))
                                }
                                GlassIconButton(Duo.Unarchive, {
                                    container.write { container.db.medicationDao().setArchived(m.id, null, Clock.now()) }
                                }, size = 38.dp)
                            }
                        }
                    }
                }
                item {
                    Caption("MyFit only records what you take — it never suggests insulin or medicine doses. Follow the plan from your doctor, nurse or pharmacist.", Modifier.padding(horizontal = 6.dp))
                }
            }
        }
        MedEditSheet(
            visible = adding || editing != null,
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { m ->
                val now = Clock.now()
                container.write {
                    if (m.id == 0L) container.db.medicationDao().insert(m.copy(createdAt = now, updatedAt = now))
                    else container.db.medicationDao().update(m.copy(updatedAt = now))
                }
                toaster.show("Saved ${m.name}")
                adding = false; editing = null
            },
        )
    }
}

@Composable
private fun TodayDosesCard(meds: List<Medication>, logs: List<MedicationLog>) {
    val th = LocalFitTheme.current
    val scheduled = meds.sumOf { MedReminders.parseTimes(it.times).size }
    val taken = logs.count { l -> meds.any { it.id == l.medicationId } }
    GlassCard {
        CardHeader(Duo.CheckCircle, "Today", th.success)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$taken", style = FitType.metric, color = th.text)
            Text(if (scheduled > 0) " of $scheduled scheduled doses taken" else " doses taken", style = FitType.body, color = th.textDim, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
        }
        val remaining = meds.flatMap { m -> MedReminders.parseTimes(m.times).map { m to it } }
            .filter { (_, t) -> t > Clock.minuteOfDay(Clock.now(), Clock.zone().id) }
            .minByOrNull { it.second }
        if (remaining != null) Caption("Next: ${remaining.first.name} at ${com.myfit.tracker.domain.ClockFmt.time(remaining.second)}")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedCard(
    m: Medication,
    takenToday: Int,
    onTake: () -> Unit,
    onEdit: () -> Unit,
    onUpdate: (Medication) -> Unit,
    onArchive: () -> Unit,
) {
    val th = LocalFitTheme.current
    val times = MedReminders.parseTimes(m.times)
    GlassCard {
        CardHeader(if (isInsulin(m.kind)) Duo.Drop else Duo.Inventory2, m.name, if (isInsulin(m.kind)) th.water else th.accent) {
            GlassIconButton(Duo.Edit, onEdit, size = 36.dp)
        }
        Spacer(Modifier.height(6.dp))
        Caption(GlucoseReport.kindLabel(m.kind).replaceFirstChar { it.uppercase() } + " · " + doseText(m.dose, m.unit) +
            " · taken today: $takenToday" + if (times.isNotEmpty()) " of ${times.size}" else "")
        if (m.notes.isNotBlank()) Caption(m.notes, color = th.textFaint)
        if (isInsulin(m.kind)) Caption("Record only — MyFit never suggests doses.", color = th.warning)
        Spacer(Modifier.height(10.dp))
        Text("Reminder times", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            times.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MinuteOfDayChip(t) { nt -> onUpdate(m.copy(times = MedReminders.formatTimes(times.toMutableList().also { it[i] = nt }))) }
                    GlassIconButton(Duo.Close, { onUpdate(m.copy(times = MedReminders.formatTimes(times.filterIndexed { j, _ -> j != i }))) }, size = 30.dp, tint = th.textDim)
                }
            }
            GlassChip("Add time", false, {
                val next = ((times.maxOrNull() ?: 420) + 240).let { if (it >= 1440) 8 * 60 else it }
                onUpdate(m.copy(times = MedReminders.formatTimes(times + next)))
            }, icon = Duo.Add)
        }
        ToggleRow("Remind me", if (times.isEmpty()) "Add a time first" else null, m.remind) { onUpdate(m.copy(remind = it)) }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton("Take now", onTake, Modifier.weight(1f), icon = Duo.Check, height = 46.dp)
            GlassButton("Archive", onArchive, icon = Duo.Archive, height = 46.dp)
        }
    }
}

// =================================================================== add / edit sheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedEditSheet(visible: Boolean, initial: Medication?, onDismiss: () -> Unit, onSave: (Medication) -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var name by remember(visible, initial) { mutableStateOf(initial?.name ?: "") }
    var kind by remember(visible, initial) { mutableStateOf(initial?.kind ?: MedKind.TABLET) }
    var dose by remember(visible, initial) {
        mutableStateOf(initial?.dose?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: "")
    }
    var unit by remember(visible, initial) { mutableStateOf(initial?.unit ?: "mg") }
    var notes by remember(visible, initial) { mutableStateOf(initial?.notes ?: "") }

    GlassSheet(visible = visible, onDismiss = onDismiss) {
        Text(if (initial == null) "Add medicine" else "Edit medicine", style = FitType.title, color = th.text)
        Spacer(Modifier.height(14.dp))
        TextLine(name, { name = it.take(60) }, "Name (e.g. Metformin)")
        Spacer(Modifier.height(12.dp))
        Text("Type", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(MedKind.TABLET, MedKind.INSULIN_RAPID, MedKind.INSULIN_LONG, MedKind.OTHER).forEach { k ->
                GlassChip(GlucoseReport.kindLabel(k).replaceFirstChar { it.uppercase() }, kind == k, {
                    kind = k
                    if (isInsulin(k) && unit == "mg") unit = "units"
                    if (k == MedKind.TABLET && unit == "units") unit = "mg"
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Usual dose (as prescribed)", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(8.dp))
        NumberInput(dose, { dose = it }, unit, Modifier.fillMaxWidth(), decimal = true, big = false)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mg", "units", "ml", "tablets", "puffs").forEach { u -> GlassChip(u, unit == u, { unit = u }) }
        }
        if (isInsulin(kind)) { Spacer(Modifier.height(8.dp)); Caption("Record only — MyFit never suggests doses. Enter what your care team prescribed.", color = th.warning) }
        Spacer(Modifier.height(12.dp))
        TextLine(notes, { notes = it.take(120) }, "Notes (optional) — e.g. with breakfast")
        Spacer(Modifier.height(6.dp))
        Caption("Set reminder times on the medicine card after saving.")
        Spacer(Modifier.height(16.dp))
        AccentButton("Save", {
            val n = name.trim()
            if (n.isEmpty()) { toaster.show("Enter a name"); return@AccentButton }
            val d = dose.toDoubleOrNull()?.takeIf { it > 0 }
            val base = initial ?: Medication(name = n, kind = kind, dose = d, unit = unit, createdAt = 0L, updatedAt = 0L)
            onSave(base.copy(name = n, kind = kind, dose = d, unit = unit, notes = notes.trim()))
        }, Modifier.fillMaxWidth(), icon = Duo.Check)
    }
}

@Composable
private fun TextLine(value: String, onChange: (String) -> Unit, hint: String) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(22.dp)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(hint, style = FitType.body, color = th.textFaint)
            BasicTextField(value, onChange, singleLine = true, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
        }
    }
}

// =================================================================== summary card (on the blood sugar screen)

@Composable
internal fun MedsSummaryCard(container: AppContainer, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val todayKey = Clock.dateKey(Clock.today())
    val meds = remember { container.db.medicationDao().observeActive() }.collectAsState(initial = emptyList()).value
    val logs = remember(todayKey) { container.db.medicationLogDao().observeOn(todayKey) }.collectAsState(initial = emptyList()).value
    val scheduled = meds.sumOf { MedReminders.parseTimes(it.times).size }
    val taken = logs.count { l -> meds.any { it.id == l.medicationId } }
    GlassCard(onClick = onOpen) {
        CardHeader(Duo.Inventory2, "Medicines", th.accent) { Text(">", style = FitType.section, color = th.textDim) }
        Spacer(Modifier.height(6.dp))
        Caption(
            when {
                meds.isEmpty() -> "Add your tablets or insulin to record doses and get reminders."
                scheduled > 0 -> "$taken of $scheduled scheduled doses taken today · ${meds.size} medicine${if (meds.size == 1) "" else "s"}"
                else -> "$taken doses taken today · ${meds.size} medicine${if (meds.size == 1) "" else "s"}"
            }
        )
    }
}
