package com.myfit.tracker.ui.cycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.data.db.CycleDay
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.CyclePrediction
import com.myfit.tracker.domain.Pregnancy
import com.myfit.tracker.reminders.ReminderScheduler
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/** Mode switcher: Track · Conceive · Pregnancy · Perimenopause. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModePickerCard(mode: String, onMode: (String) -> Unit) {
    val th = LocalFitTheme.current
    GlassCard(padding = 14.dp) {
        Text("FOCUS", style = FitType.overline, color = th.textDim)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CycleMode.all.forEach { m -> GlassChip(CycleMode.short(m), mode == m, { onMode(m) }) }
        }
    }
}

/** Trying to conceive: fertile window estimate, what to log, and honest guidance. */
@Composable
internal fun TtcCard(pred: CyclePrediction, rows: Map<LocalDate, CycleDay>, shifts: List<LocalDate>, skinNights: Int) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    GlassCard {
        CardHeader(Duo.Favorite, "Trying to conceive", th.protein)
        Spacer(Modifier.height(8.dp))
        val fs = pred.fertileStart; val fe = pred.fertileEnd
        if (fs != null && fe != null) {
            val inWindow = !today.isBefore(fs) && !today.isAfter(fe)
            Text(if (inWindow) "You're likely in your fertile window" else "Next estimated fertile window",
                style = FitType.section, color = if (inWindow) th.protein else th.text)
            Caption("${fs.format(dFmt)} – ${fe.format(dFmt)}" + (pred.ovulation?.let { " · estimated ovulation ${it.format(dFmt)}" } ?: ""))
            if (pred.irregular) Caption("Your cycles vary a lot, so this window is less certain — ovulation tests help.", color = th.warning)
        } else Caption("Log at least one full cycle to see an estimated fertile window.")
        Spacer(Modifier.height(10.dp))
        val recent = rows.filterKeys { !it.isBefore(today.minusDays(35)) }.values
        val lh = recent.count { it.ovulationTest != null }; val mucus = recent.count { it.mucus != null }; val bbt = recent.count { it.bbtC != null }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Mini("Ovulation tests", "$lh"); Mini("Mucus logs", "$mucus"); Mini("Temperatures", "$bbt")
        }
        if (shifts.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Caption("Temperature rise detected after ${shifts.last().format(dFmt)} — ovulation likely happened just before it.", color = th.success) }
        if (skinNights > 0) Caption("Your watch shared $skinNights nights of skin temperature — used to cross-check.", color = th.textFaint)
        Spacer(Modifier.height(8.dp))
        Caption("Tips: log ovulation tests (\"Peak\" usually means ovulation in 24–36 h), egg-white mucus, and morning temperature before getting up. Seek advice if you're under 35 and haven't conceived after 12 months of trying (6 months if 35+), or sooner if cycles are very irregular.",
            color = th.textDim)
        Spacer(Modifier.height(4.dp))
        Caption("Estimates only — not contraception and not a fertility diagnosis.", color = th.textFaint)
    }
}

/** Pregnancy: week-by-week progress, appointments, warning signs. */
@Composable
internal fun PregnancyCard() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var tick by remember { mutableIntStateOf(0) }
    val due = remember(tick) { CyclePrefs.dueDate(ctx)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
    val appts = remember(tick) { CyclePrefs.appointments(ctx) }
    var setDue by remember { mutableStateOf(false) }
    var addAppt by remember { mutableStateOf(false) }
    var showSigns by remember { mutableStateOf(false) }
    val today = Clock.today()
    GlassCard {
        CardHeader(Duo.Favorite, "Pregnancy", th.protein) {
            Text("Edit", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple { setDue = true }.padding(4.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (due == null) {
            Caption("Add your due date (or the first day of your last period) to see your week-by-week progress.")
            Spacer(Modifier.height(8.dp))
            AccentButton("Set due date", { setDue = true }, icon = Duo.CalendarMonth, height = 44.dp)
        } else {
            val p = Pregnancy.progress(due, today)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing((p.totalDays / Pregnancy.TERM_DAYS.toFloat()).coerceIn(0f, 1f), th.protein, size = 92.dp, stroke = 10.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${p.week}w ${p.day}d", style = FitType.section, color = th.text)
                        Caption("Trim. ${p.trimester}")
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Due ${due.format(dFmt)}", style = FitType.section, color = th.text)
                    Caption(if (p.daysLeft >= 0) "${p.daysLeft} days to go" else "${-p.daysLeft} days past your due date")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(Pregnancy.stageNote(p.week), style = FitType.body, color = th.textDim)
            Spacer(Modifier.height(10.dp))
            Text("APPOINTMENTS", style = FitType.overline, color = th.textDim)
            val upcoming = appts.filter { it.at >= System.currentTimeMillis() - 3_600_000L }
            if (upcoming.isEmpty()) Caption("No upcoming appointments.")
            upcoming.take(5).forEach { a ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dt = Instant.ofEpochMilli(a.at).atZone(Clock.zone())
                    Column(Modifier.weight(1f)) {
                        Text(a.label.ifBlank { "Antenatal visit" }, style = FitType.body, color = th.text)
                        Caption(dt.format(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a", Locale.US)))
                    }
                    Text("Remove", style = FitType.caption, color = th.textDim, modifier = Modifier.clickableNoRipple {
                        CyclePrefs.setAppointments(ctx, appts.filter { it != a }); ReminderScheduler.reschedule(ctx); tick++
                    }.padding(6.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Add appointment", { addAppt = true }, icon = Duo.Add, height = 40.dp)
                GlassButton("Warning signs", { showSigns = true }, icon = Duo.Info, height = 40.dp)
            }
            ToggleRow("Remind me (evening before + 2 h before)", "Notifications don't show what they're about.", CyclePrefs.apptRemind(ctx)) {
                CyclePrefs.setApptRemind(ctx, it); ReminderScheduler.reschedule(ctx); tick++
            }
        }
        Spacer(Modifier.height(4.dp))
        Caption("General information only — follow your doctor or midwife.", color = th.textFaint)
    }

    GlassSheet(visible = setDue, onDismiss = { setDue = false }) {
        var useLmp by remember { mutableStateOf(false) }
        var d by remember { mutableStateOf(due ?: today.plusDays(200)) }
        Text("Due date", style = FitType.title, color = th.text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("I know my due date", !useLmp, { useLmp = false; d = due ?: today.plusDays(200) })
            GlassChip("First day of last period", useLmp, { useLmp = true; d = today.minusDays(42) })
        }
        Spacer(Modifier.height(10.dp))
        DateOnlyRow(if (useLmp) "Last period started" else "Due date", d, { d = it })
        Spacer(Modifier.height(6.dp))
        val result = if (useLmp) Pregnancy.dueFromLmp(d) else d
        Caption("Due date: ${result.format(dFmt)} (40 weeks from the last period). Your scan date may differ — use the date your doctor gives you.")
        Spacer(Modifier.height(12.dp))
        AccentButton("Save", {
            val days = ChronoUnit.DAYS.between(today, result)
            if (days < -30 || days > 300) { toaster.show("That date doesn't look right"); return@AccentButton }
            CyclePrefs.setDueDate(ctx, result.toString()); ReminderScheduler.reschedule(ctx); tick++; setDue = false
        }, Modifier.fillMaxWidth())
        if (due != null) {
            Spacer(Modifier.height(8.dp))
            Text("Clear due date", style = FitType.label, color = th.textDim, modifier = Modifier.clickableNoRipple {
                CyclePrefs.setDueDate(ctx, null); tick++; setDue = false
            }.padding(6.dp))
        }
    }

    GlassSheet(visible = addAppt, onDismiss = { addAppt = false }) {
        var label by remember { mutableStateOf("") }
        var date by remember { mutableStateOf(today.plusDays(7)) }
        var min by remember { mutableIntStateOf(10 * 60) }
        Text("Add appointment", style = FitType.title, color = th.text)
        Spacer(Modifier.height(8.dp))
        com.myfit.tracker.ui.entries.NotesField(label, { label = it.take(60) }, "e.g. Scan, check-up, glucose test")
        Spacer(Modifier.height(8.dp))
        DateOnlyRow("Date", date, { date = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Time", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
            MinuteOfDayChip(min) { min = it }
        }
        Spacer(Modifier.height(12.dp))
        AccentButton("Save", {
            val at = date.atTime(min / 60, min % 60).atZone(Clock.zone()).toInstant().toEpochMilli()
            CyclePrefs.setAppointments(ctx, appts + Appointment(at, label.trim())); ReminderScheduler.reschedule(ctx); tick++; addAppt = false
        }, Modifier.fillMaxWidth())
    }

    GlassSheet(visible = showSigns, onDismiss = { showSigns = false }) {
        Text("Get help straight away if you have", style = FitType.title, color = th.text)
        Spacer(Modifier.height(8.dp))
        Pregnancy.urgentSigns.forEach { Bullet(it, th.danger) }
        Spacer(Modifier.height(8.dp))
        Caption("Pakistan emergency: 1122 (Rescue) · 115 (Edhi). Or go to your nearest hospital.", color = th.textDim)
    }
}

@Composable
private fun Mini(label: String, value: String) {
    val th = LocalFitTheme.current
    Column {
        Text(value, style = FitType.section, color = th.text)
        Text(label, style = FitType.caption, color = th.textDim)
    }
}

/** Perimenopause: symptom counts from the last 30 days and gentle, honest guidance. */
@Composable
internal fun PeriCard(rows: Map<LocalDate, CycleDay>, pred: CyclePrediction) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    val recent = rows.filterKeys { !it.isBefore(today.minusDays(30)) }.values
    val counts = CycleOptions.periSymptoms.map { (k, l) -> l to recent.count { r -> r.symptomList().contains(k) } }.filter { it.second > 0 }.sortedByDescending { it.second }
    GlassCard {
        CardHeader(Duo.Mood, "Perimenopause", th.fat)
        Spacer(Modifier.height(8.dp))
        if (counts.isEmpty()) Caption("Log hot flushes, night sweats, sleep and mood in \"Log today\" to see patterns here.")
        else {
            Text("LAST 30 DAYS", style = FitType.overline, color = th.textDim)
            counts.take(6).forEach { (l, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(l, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    Text("$n day${if (n == 1) "" else "s"}", style = FitType.label, color = th.textDim)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (pred.recentLengths.size >= 3) {
            Caption("Recent cycle lengths: ${pred.recentLengths.joinToString(", ")} days" + if (pred.irregular) " — cycles becoming irregular is common in perimenopause." else ".")
            Spacer(Modifier.height(4.dp))
        }
        Caption("Strength training, regular sleep and limiting caffeine/spicy food in the evening help many people. See a doctor for bleeding after 12 months without periods, very heavy or prolonged bleeding, or symptoms that affect daily life — treatments are available.",
            color = th.textDim)
    }
}

/** App lock (PIN) settings for the cycle section. */
@Composable
internal fun CycleLockCard() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var tick by remember { mutableIntStateOf(0) }
    val on = remember(tick) { CyclePrefs.lockOn(ctx) }
    var setup by remember { mutableStateOf(false) }
    GlassCard {
        CardHeader(Duo.Lock, "Lock this section", th.success)
        Spacer(Modifier.height(6.dp))
        Caption(if (on) "A PIN is needed to open Cycle. It stays unlocked for 5 minutes." else "Add a 4–6 digit PIN so nobody else using your phone can open your cycle data.")
        Spacer(Modifier.height(8.dp))
        if (on) GlassButton("Remove PIN", { CyclePrefs.clearPin(ctx); CycleLockSession.lock(); tick++; toaster.show("PIN removed") }, height = 42.dp)
        else GlassButton("Set a PIN", { setup = true }, icon = Duo.Lock, height = 42.dp)
    }
    PinSetupSheet(setup, { setup = false }) { pin -> CyclePrefs.setPin(ctx, pin); CycleLockSession.unlock(); setup = false; tick++; toaster.show("PIN set") }
}
