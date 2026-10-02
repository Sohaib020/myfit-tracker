package com.myfit.tracker.ui.glucose

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.reminders.ReminderScheduler
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.MinuteOfDayChip
import com.myfit.tracker.ui.components.NumberInput
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lab HbA1c results (date + %), kept in a tiny private JSON file on the phone. */
object HbA1cStore {
    data class Entry(val date: LocalDate, val pct: Double)
    private const val FILE = "hba1c"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Entry> = runCatching {
        val a = JSONArray(prefs(ctx).getString("list", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o ->
            runCatching { Entry(LocalDate.parse(o.getString("d")), o.getDouble("v")) }.getOrNull()
        }.sortedBy { it.date }
    }.getOrDefault(emptyList())

    fun add(ctx: Context, e: Entry) {
        val l = (all(ctx).filter { it.date != e.date } + e).sortedBy { it.date }
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("d", it.date.toString()).put("v", it.pct)) }
        prefs(ctx).edit().putString("list", a.toString()).apply()
    }

    fun remove(ctx: Context, e: Entry) {
        val l = all(ctx).filter { it != e }
        val a = JSONArray(); l.forEach { a.put(JSONObject().put("d", it.date.toString()).put("v", it.pct)) }
        prefs(ctx).edit().putString("list", a.toString()).apply()
    }

    fun latestDate(container: AppContainer): LocalDate? = all(container.app).lastOrNull()?.date

    /** mmol/mol (IFCC) from % (NGSP): (% − 2.15) × 10.929 */
    fun mmolMol(pct: Double) = (pct - 2.15) * 10.929
}

/**
 * Suggested check times while fasting, following the IDF-DAR practical guidelines (2021):
 * before suhoor, mid-morning, midday, mid-afternoon, before iftar, and 2 h after iftar.
 * Checking blood sugar does not break the fast.
 */
object RamadanTimes {
    fun checks(suhoorMin: Int, iftarMin: Int): List<Pair<String, Int>> {
        val s = suhoorMin.coerceIn(0, 1439); val i = iftarMin.coerceIn(0, 1439)
        val fast = if (i > s) i - s else i + 1440 - s
        fun at(m: Int) = ((m % 1440) + 1440) % 1440
        return listOf(
            "Pre-suhoor" to at(s - 30),
            "Midday" to at(s + fast / 2),
            "Pre-iftar" to at(i - 30),
            "2 hours after iftar" to at(i + 120),
        )
    }
}

private val dfmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
private fun hm(m: Int) = "%d:%02d".format(m / 60, m % 60)

/** Ramadan fasting card: banner with the break-the-fast rule, check times, reminders. */
@Composable
fun RamadanCard(container: AppContainer, cfg: GlucoseConfig) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    var info by remember { mutableStateOf(false) }
    fun save(c: GlucoseConfig) { GlucoseConfigStore.save(ctx, c); ReminderScheduler.reschedule(ctx) }
    GlassCard {
        CardHeader(Duo.Bedtime, "Ramadan mode", th.carbs) {
            Text("Guide", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple { info = true }.padding(4.dp))
        }
        Spacer(Modifier.height(6.dp))
        ToggleRow("Fasting this Ramadan", "Adds suggested check times and safety warnings.", cfg.ramadan) { save(cfg.copy(ramadan = it)) }
        if (cfg.ramadan) {
            GlassCard(padding = 12.dp) {
                Text("Break your fast if sugar is below 70 mg/dL (3.9 mmol/L) or above 300 mg/dL (16.6 mmol/L), or if you feel unwell.",
                    style = FitType.label, color = th.danger)
                Spacer(Modifier.height(4.dp))
                Caption("Checking your blood sugar does not break the fast. Talk to your doctor before Ramadan about medicine timing — MyFit never changes doses.")
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Suhoor ends", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                MinuteOfDayChip(cfg.suhoorMin) { save(cfg.copy(suhoorMin = it)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Iftar", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                MinuteOfDayChip(cfg.iftarMin) { save(cfg.copy(iftarMin = it)) }
            }
            Spacer(Modifier.height(8.dp))
            Text("SUGGESTED CHECKS", style = FitType.overline, color = th.textDim)
            RamadanTimes.checks(cfg.suhoorMin, cfg.iftarMin).forEach { (label, m) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(label, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    Text(hm(m), style = FitType.label, color = th.textDim)
                }
            }
            ToggleRow("Remind me at these times", null, cfg.ramadanRemind) { save(cfg.copy(ramadanRemind = it)) }
            Caption("Set the times from your local timetable each week — they shift during the month.", color = th.textFaint)
        }
    }
    GlassSheet(visible = info, onDismiss = { info = false }) {
        Text("Fasting safely with diabetes", style = FitType.title, color = th.text)
        Spacer(Modifier.height(8.dp))
        listOf(
            "See your doctor 6–8 weeks before Ramadan to plan medicines and decide if fasting is safe for you.",
            "Check more often: before suhoor, during the day, before iftar and 2 hours after iftar.",
            "Break the fast straight away if below 70 mg/dL, above 300 mg/dL, or if you feel shaky, confused, faint or unwell.",
            "Eat suhoor as late as possible; choose slow carbs (whole-wheat roti, daal, yogurt) and drink plenty of water between iftar and suhoor.",
            "Avoid heavy exercise close to iftar; light taraweeh walking is usually fine.",
        ).forEach { Text("• $it", style = FitType.body, color = th.textDim, modifier = Modifier.padding(vertical = 3.dp)) }
        Spacer(Modifier.height(8.dp))
        Caption("Source: IDF-DAR Diabetes and Ramadan Practical Guidelines (2021). General information, not medical advice.", color = th.textFaint)
    }
}

/** Lab HbA1c log, kept separate from the sensor-based estimate (GMI). */
@Composable
fun HbA1cCard(container: AppContainer, cfg: GlucoseConfig) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var tick by remember { mutableIntStateOf(0) }
    val list = remember(tick) { HbA1cStore.all(ctx) }
    var adding by remember { mutableStateOf(false) }
    GlassCard {
        CardHeader(Duo.TrackChanges, "HbA1c (lab results)", th.fat)
        Spacer(Modifier.height(8.dp))
        val last = list.lastOrNull()
        if (last == null) Caption("Add results from your lab reports to see your long-term trend. Most people test every 3 months.")
        else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.US, "%.1f%%", last.pct), style = FitType.title, color = th.text)
                Spacer(Modifier.width(8.dp))
                Caption(String.format(Locale.US, "%.0f mmol/mol · %s", HbA1cStore.mmolMol(last.pct), last.date.format(dfmt)))
            }
            list.dropLast(1).takeLast(4).reversed().forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(e.date.format(dfmt), style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.1f%%", e.pct), style = FitType.caption, color = th.text)
                }
            }
            Spacer(Modifier.height(6.dp))
            Caption("Your doctor sets your personal target (many adults aim below 7%).", color = th.textFaint)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton("Add result", { adding = true }, icon = Duo.Add, height = 40.dp)
        }
        ToggleRow("Remind me 3 months after my last test", null, cfg.a1cRemind) {
            GlucoseConfigStore.save(ctx, cfg.copy(a1cRemind = it)); ReminderScheduler.reschedule(ctx)
        }
    }
    GlassSheet(visible = adding, onDismiss = { adding = false }) {
        var v by remember { mutableStateOf("") }
        var daysAgo by remember { mutableIntStateOf(0) }
        Text("Add HbA1c result", style = FitType.title, color = th.text)
        Spacer(Modifier.height(10.dp))
        NumberInput(v, { v = it }, "%", Modifier.fillMaxWidth(), decimal = true, big = false)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Test date", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
            com.myfit.tracker.ui.components.Stepper(daysAgo, { daysAgo = it }, 0..730) { d -> if (d == 0) "Today" else Clock.today().minusDays(d.toLong()).format(DateTimeFormatter.ofPattern("d MMM", Locale.US)) }
        }
        Spacer(Modifier.height(12.dp))
        AccentButton("Save", {
            val pct = v.toDoubleOrNull()
            if (pct == null || pct < 3.5 || pct > 20) { toaster.show("Enter the % from your report (e.g. 6.8)"); return@AccentButton }
            HbA1cStore.add(ctx, HbA1cStore.Entry(Clock.today().minusDays(daysAgo.toLong()), pct))
            ReminderScheduler.reschedule(ctx)
            tick++; adding = false; toaster.show("HbA1c saved")
        }, Modifier.fillMaxWidth())
    }
}

/** Daily check reminders. */
@Composable
fun CheckRemindersCard(cfg: GlucoseConfig) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    fun save(c: GlucoseConfig) { GlucoseConfigStore.save(ctx, c); ReminderScheduler.reschedule(ctx) }
    GlassCard {
        CardHeader(Duo.Bell, "Check reminders", th.warning)
        ToggleRow("Remind me to check my sugar", null, cfg.testRemind) { save(cfg.copy(testRemind = it)) }
        if (cfg.testRemind) {
            cfg.testTimes.forEachIndexed { idx, m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check ${idx + 1}", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    MinuteOfDayChip(m) { nm -> save(cfg.copy(testTimes = cfg.testTimes.toMutableList().also { l -> l[idx] = nm }.distinct().sorted())) }
                    if (cfg.testTimes.size > 1) Text("Remove", style = FitType.caption, color = th.textDim,
                        modifier = Modifier.clickableNoRipple { save(cfg.copy(testTimes = cfg.testTimes.filterIndexed { i, _ -> i != idx })) }.padding(8.dp))
                }
            }
            if (cfg.testTimes.size < 6) Text("+ Add a time", style = FitType.label, color = th.accentBright,
                modifier = Modifier.clickableNoRipple { save(cfg.copy(testTimes = (cfg.testTimes + 12 * 60).distinct().sorted())) }.padding(6.dp))
        }
    }
}
