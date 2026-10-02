package com.myfit.tracker.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.CycleDay
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sheetDateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)

/** Bottom sheet to log one day. Upserts by date; clearing everything removes the day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CycleLogSheet(container: AppContainer, date: LocalDate, existing: CycleDay?, onClose: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val key = Clock.dateKey(date)

    var flow by remember(key) { mutableStateOf(existing?.flow) }
    var symptoms by remember(key) { mutableStateOf(existing?.symptomList()?.toSet() ?: emptySet()) }
    var mood by remember(key) { mutableStateOf(existing?.mood) }
    var notes by remember(key) { mutableStateOf(existing?.notes ?: "") }
    var ovTest by remember(key) { mutableStateOf(existing?.ovulationTest) }
    var mucus by remember(key) { mutableStateOf(existing?.mucus) }
    var bbt by remember(key) { mutableStateOf(existing?.bbtC?.let { String.format(Locale.US, "%.2f", it) } ?: "") }
    var pill by remember(key) { mutableStateOf(existing?.pillTaken) }
    val hasFertility = existing != null && (existing.ovulationTest != null || existing.mucus != null || existing.bbtC != null || existing.pillTaken != null)
    var fertilityOpen by remember(key) { mutableStateOf(hasFertility) }

    val bbtValue: Double? = bbt.toDoubleOrNull()?.takeIf { it.isFinite() }
    val bbtInvalid = bbt.isNotBlank() && (bbtValue == null || bbtValue < 34.0 || bbtValue > 42.0)
    val isFuture = date.isAfter(Clock.today())

    GlassSheet(visible = true, onDismiss = onClose) {
        Text(if (date == Clock.today()) "Today" else date.format(sheetDateFmt), style = FitType.title, color = th.text)
        if (date == Clock.today()) Caption(date.format(sheetDateFmt))
        if (isFuture) Caption("This day is in the future — you can still plan notes for it.", color = th.warning)
        Spacer(Modifier.height(14.dp))

        Label("Flow")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleOptions.flows.forEach { (v, l) ->
                GlassChip(l, flow == v, { flow = if (flow == v) null else v }, icon = if (v >= 2) Duo.Drop else null)
            }
        }
        Spacer(Modifier.height(14.dp))

        Label("Symptoms")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleOptions.symptoms.forEach { (k, l) ->
                val on = k in symptoms
                GlassChip(l, on, { symptoms = if (on) symptoms - k else symptoms + k })
            }
        }
        Spacer(Modifier.height(14.dp))

        Label("Mood")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleOptions.moods.forEach { (k, l) ->
                GlassChip(l, mood == k, { mood = if (mood == k) null else k })
            }
        }
        Spacer(Modifier.height(14.dp))

        NotesField(notes, { notes = it }, "Notes (optional)")
        Spacer(Modifier.height(14.dp))

        // ---- collapsible fertility section
        Row(
            Modifier.fillMaxWidth().clickableNoRipple { fertilityOpen = !fertilityOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Fertility (optional)", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
            Icon(
                if (fertilityOpen) Duo.KeyboardArrowDown else Duo.KeyboardArrowRight, null,
                tint = th.textDim, modifier = Modifier.size(22.dp),
            )
        }
        if (fertilityOpen) {
            Spacer(Modifier.height(10.dp))
            Label("Ovulation test")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CycleOptions.ovulationTests.forEach { (v, l) -> GlassChip(l, ovTest == v, { ovTest = if (ovTest == v) null else v }) }
            }
            Spacer(Modifier.height(12.dp))
            Label("Cervical mucus")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CycleOptions.mucus.forEach { (v, l) -> GlassChip(l, mucus == v, { mucus = if (mucus == v) null else v }) }
            }
            Spacer(Modifier.height(12.dp))
            Label("Basal body temperature")
            NumberInput(bbt, { bbt = it }, "°C", big = false)
            if (bbtInvalid) Caption("Enter a temperature between 34 and 42 °C", color = th.danger)
            else Caption("Take it on waking, before getting up, at the same time each day.")
            Spacer(Modifier.height(6.dp))
            ToggleRow("Pill taken today", null, pill == true) { pill = if (it) true else null }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (existing != null) {
                GlassButton("Clear day", {
                    val now = Clock.now()
                    container.write { CycleStore.save(container, CycleDay(localDate = key, createdAt = now, updatedAt = now)) }
                    toaster.show("Day cleared")
                    onClose()
                }, Modifier.weight(1f), icon = Duo.DeleteOutline, height = 50.dp)
            }
            AccentButton("Save", {
                val now = Clock.now()
                val day = CycleDay(
                    localDate = key,
                    flow = flow,
                    symptoms = CycleOptions.symptoms.map { it.first }.filter { it in symptoms }.joinToString(","),
                    mood = mood,
                    ovulationTest = ovTest,
                    mucus = mucus,
                    bbtC = if (bbtInvalid) existing?.bbtC else bbtValue,
                    pillTaken = pill,
                    notes = notes.trim(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
                container.write { CycleStore.save(container, day) }
                toaster.show(if (bbtInvalid) "Saved (temperature skipped)" else "Saved")
                onClose()
            }, Modifier.weight(1f), icon = Duo.Check, height = 50.dp)
        }
        Spacer(Modifier.height(8.dp))
        Caption("Kept on this phone only. Never shared with friends or leaderboards.", color = th.textFaint)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = FitType.label, color = LocalFitTheme.current.textDim)
    Spacer(Modifier.height(6.dp))
}
