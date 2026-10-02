package com.myfit.tracker.ui.vitals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.BloodPressure
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.health.VitalsReader
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.ChartPoint
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.ProgressChart
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** AHA / ACC 2017 categories. */
internal enum class BpCategory(val label: String) {
    NORMAL("Normal"), ELEVATED("Elevated"), STAGE1("High · stage 1"), STAGE2("High · stage 2"), CRISIS("Very high");
}

internal fun bpCategory(sys: Int, dia: Int): BpCategory = when {
    sys > 180 || dia > 120 -> BpCategory.CRISIS
    sys >= 140 || dia >= 90 -> BpCategory.STAGE2
    sys >= 130 || dia >= 80 -> BpCategory.STAGE1
    sys >= 120 -> BpCategory.ELEVATED
    else -> BpCategory.NORMAL
}

internal fun bpColor(c: BpCategory, th: FitTheme): Color = when (c) {
    BpCategory.NORMAL -> th.success
    BpCategory.ELEVATED -> th.carbs
    BpCategory.STAGE1 -> th.warning
    BpCategory.STAGE2, BpCategory.CRISIS -> th.danger
}

internal const val BP_CRISIS_TEXT =
    "Very high reading. Sit quietly for 5 minutes and measure again. If it's still above 180/120, or you have chest pain, " +
        "shortness of breath, back pain, numbness, weakness, vision changes or trouble speaking, seek emergency care now."

/** A blood-pressure reading from either the user's own log or another app via Health Connect. */
internal data class BpItem(
    val key: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val at: Long,
    val source: String,
    val manualId: Long?,
    val note: String,
)

internal fun mergeBp(manual: List<BloodPressure>, hc: List<VitalsReader.BpReading>?): List<BpItem> {
    val a = manual.map { BpItem("m" + it.id, it.systolic, it.diastolic, it.pulse, it.takenAt, if (it.source == "MANUAL") "Logged by you" else appName(it.sourcePackage), it.id, it.notes) }
    val b = hc.orEmpty().map { BpItem("hc" + it.id, it.systolic, it.diastolic, null, it.at.toEpochMilli(), appName(it.pkg), null, "") }
    return (a + b).sortedByDescending { it.at }
}

@Composable
private fun CategoryPill(c: BpCategory) {
    val th = LocalFitTheme.current
    val col = bpColor(c, th)
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(c.label, style = FitType.caption, color = col)
    }
}

/** Blood pressure card: latest, category, 30-day chart and averages, merged list, add button. */
@Composable
internal fun BloodPressureCard(container: AppContainer, items: List<BpItem>, hcPermitted: Boolean, onAdd: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var showDia by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    GlassCard {
        CardHeader(Duo.Pulse, "Blood pressure", th.danger) {
            GlassIconButton(Duo.Add, onAdd, size = 36.dp)
        }
        Spacer(Modifier.height(10.dp))
        val latest = items.firstOrNull()
        if (latest == null) {
            Caption("No readings yet. Tap + to log one from your home monitor" + if (hcPermitted) "." else ", or allow Health Connect to show readings from other apps.")
            Spacer(Modifier.height(10.dp))
            AccentButton("Log blood pressure", onAdd, icon = Duo.Add, height = 46.dp)
        } else {
            val cat = bpCategory(latest.systolic, latest.diastolic)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${latest.systolic}/${latest.diastolic}", style = FitType.metric, color = th.text)
                Spacer(Modifier.width(6.dp))
                Text("mmHg", style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                CategoryPill(cat)
            }
            Caption(fmtWhenMs(latest.at) + " · " + latest.source + (latest.pulse?.let { " · pulse $it" } ?: ""))
            if (cat == BpCategory.CRISIS) {
                Spacer(Modifier.height(8.dp))
                Caption(BP_CRISIS_TEXT, color = th.danger)
            }

            // 30-day averages + chart (daily means)
            val zone = ZoneId.systemDefault()
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            val byDay = items.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }.toSortedMap()
            val avgS = items.map { it.systolic }.average().roundToInt()
            val avgD = items.map { it.diastolic }.average().roundToInt()
            Spacer(Modifier.height(10.dp))
            Text(
                "30-day average $avgS/$avgD mmHg · ${items.size} reading" + (if (items.size == 1) "" else "s"),
                style = FitType.label, color = th.text,
            )
            Caption("That average is in the “${bpCategory(avgS, avgD).label.lowercase()}” range. One reading alone doesn't tell the whole story — averages over weeks matter most.")
            if (byDay.size >= 2) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Systolic (top)", !showDia, { showDia = false })
                    GlassChip("Diastolic (bottom)", showDia, { showDia = true })
                }
                Spacer(Modifier.height(8.dp))
                val pts = byDay.map { (d, l) ->
                    ChartPoint(
                        d.atStartOfDay(zone).toInstant().toEpochMilli(),
                        if (showDia) l.map { it.diastolic }.average() else l.map { it.systolic }.average(),
                        d.format(fmt),
                    )
                }
                ProgressChart(pts, if (showDia) th.water else th.danger, { "${it.roundToInt()} mmHg" }, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(12.dp))
            val shown = if (showAll) items else items.take(5)
            shown.forEach { b ->
                val c = bpCategory(b.systolic, b.diastolic)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${b.systolic}/${b.diastolic}", style = FitType.section, color = th.text)
                            if (b.pulse != null) Text("  · ${b.pulse} bpm", style = FitType.caption, color = th.textDim)
                            Spacer(Modifier.width(8.dp))
                            CategoryPill(c)
                        }
                        Caption(fmtWhenMs(b.at) + " · " + b.source + if (b.note.isNotBlank()) " · " + b.note else "")
                    }
                    val id = b.manualId
                    if (id != null) GlassIconButton(Duo.DeleteOutline, {
                        container.write { container.db.bloodPressureDao().softDelete(id, Clock.now()) }
                        toaster.show("Reading deleted")
                    }, size = 34.dp, tint = th.textDim)
                }
            }
            if (items.size > 5) {
                Spacer(Modifier.height(6.dp))
                GlassButton(if (showAll) "Show less" else "Show all ${items.size}", { showAll = !showAll }, height = 40.dp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption("Categories follow the American Heart Association (2017): normal below 120/80. This is a log, not a diagnosis — share it with your doctor.", color = th.textFaint)
    }
}

/** Manual entry sheet; saves to the local blood_pressure table (source MANUAL). */
@Composable
internal fun BpEntrySheet(container: AppContainer, visible: Boolean, onClose: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    var sys by remember(visible) { mutableStateOf("") }
    var dia by remember(visible) { mutableStateOf("") }
    var pulse by remember(visible) { mutableStateOf("") }
    var note by remember(visible) { mutableStateOf("") }
    var at by remember(visible) { mutableLongStateOf(Clock.now()) }
    GlassSheet(visible = visible, onDismiss = onClose) {
        Text("Log blood pressure", style = FitType.title, color = th.text)
        Caption("Sit quietly for 5 minutes, arm supported at heart height, then measure.")
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Caption("Systolic (top)")
                Spacer(Modifier.height(4.dp))
                NumberInput(sys, { sys = it.take(3) }, "", decimal = false, big = false)
            }
            Column(Modifier.weight(1f)) {
                Caption("Diastolic (bottom)")
                Spacer(Modifier.height(4.dp))
                NumberInput(dia, { dia = it.take(3) }, "", decimal = false, big = false)
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption("Pulse (optional)")
        Spacer(Modifier.height(4.dp))
        NumberInput(pulse, { pulse = it.take(3) }, "bpm", decimal = false, big = false)
        Spacer(Modifier.height(12.dp))
        com.myfit.tracker.ui.components.DateTimeRow("Time", at, { at = it.coerceAtMost(Clock.now()) })
        Spacer(Modifier.height(12.dp))
        NotesField(note, { note = it.take(200) }, "Note (optional) — e.g. left arm, after coffee")

        val s = sys.toIntOrNull()
        val d = dia.toIntOrNull()
        val p = pulse.toIntOrNull()
        val error = when {
            s == null || d == null -> null
            s !in 60..260 -> "Systolic should be between 60 and 260"
            d !in 30..160 -> "Diastolic should be between 30 and 160"
            s <= d -> "The top number should be higher than the bottom number"
            p != null && p !in 30..220 -> "Pulse should be between 30 and 220"
            else -> null
        }
        if (s != null && d != null && error == null) {
            val c = bpCategory(s, d)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Caption("Category: "); CategoryPill(c) }
            if (c == BpCategory.CRISIS) { Spacer(Modifier.height(6.dp)); Caption(BP_CRISIS_TEXT, color = th.danger) }
        }
        if (error != null) { Spacer(Modifier.height(10.dp)); Caption(error, color = th.danger) }
        Spacer(Modifier.height(16.dp))
        AccentButton("Save", {
            val ss = s ?: return@AccentButton
            val dd = d ?: return@AccentButton
            val st = Stamp.of(at)
            val now = Clock.now()
            val row = BloodPressure(
                systolic = ss, diastolic = dd, pulse = p, source = "MANUAL",
                takenAt = st.at, zoneId = st.zoneId, localDate = st.localDate,
                notes = note.trim(), createdAt = now, updatedAt = now,
            )
            container.write { container.db.bloodPressureDao().insert(row) }
            toaster.show("Saved $ss/$dd")
            onClose()
        }, Modifier.fillMaxWidth(), icon = Duo.Check, enabled = s != null && d != null && error == null)
    }
}
