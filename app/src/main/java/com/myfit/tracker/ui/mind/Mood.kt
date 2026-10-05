package com.myfit.tracker.ui.mind

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MoodEntry
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.ChartPoint
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.ProgressChart
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Contents of the mood check-in sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodCheckInForm(container: AppContainer, onDone: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    val tick = rememberTick()
    var mood by remember { mutableIntStateOf(0) }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }

    Text("How are you feeling?", style = FitType.title, color = th.text)
    Spacer(Modifier.height(4.dp))
    Caption("A quick check-in. There are no wrong answers.")
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..5).forEach { m ->
            val on = mood == m
            val sc by androidx.compose.animation.core.animateFloatAsState(if (on) 1.15f else 1f, label = "face")
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickableNoRipple { tick(); mood = m }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MoodFace(m, 52.dp, Modifier.graphicsLayer { scaleX = sc; scaleY = sc }, dim = mood != 0 && !on)
                Spacer(Modifier.height(6.dp))
                Text(moodWord(m), style = FitType.label, color = if (on) th.text else th.textDim)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Caption("What else are you feeling?")
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeelingTags.forEach { t ->
            GlassChip(t.replaceFirstChar { it.uppercase() }, t in tags, { tags = if (t in tags) tags - t else tags + t })
        }
    }
    Spacer(Modifier.height(14.dp))
    NotesField(note, { note = it }, "What's on your mind? (optional)")
    Spacer(Modifier.height(16.dp))
    AccentButton(
        if (mood == 0) "Pick a face" else "Save · ${moodWord(mood)}",
        {
            val m = mood
            val tg = FeelingTags.filter { it in tags }.joinToString(",")
            val n = note.trim()
            container.write {
                val s = Stamp.now()
                container.db.moodDao().insert(
                    MoodEntry(mood = m, tags = tg, note = n, loggedAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = s.at, updatedAt = s.at)
                )
            }
            toaster.show("Mood saved")
            onDone()
        },
        Modifier.fillMaxWidth(), icon = Duo.Check, enabled = mood != 0,
    )
}

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val timeFmt: DateTimeFormatter get() = com.myfit.tracker.domain.ClockFmt.f("EEE d MMM · ")

/** Today's check-in summary + button. */
@Composable
fun MoodTodayCard(today: List<MoodEntry>, onCheckIn: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Mood, "Mood", moodColor(4))
        Spacer(Modifier.height(12.dp))
        val last = today.firstOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (last != null) {
                MoodFace(last.mood, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Feeling ${moodWord(last.mood).lowercase()}", style = FitType.section, color = th.text)
                    val extra = listOfNotNull(
                        last.tags.split(',').filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        if (today.size > 1) "${today.size} check-ins today" else null,
                    )
                    if (extra.isNotEmpty()) Caption(extra.joinToString(" · "))
                    if (last.note.isNotBlank()) Caption("“${last.note.take(80)}”", color = th.textFaint)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { MoodFace(it, 24.dp) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("How are you feeling?", style = FitType.section, color = th.text)
                    Caption("Check in once or twice a day to see patterns.")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (last == null) AccentButton("Check in", onCheckIn, Modifier.fillMaxWidth(), icon = Duo.Mood, height = 48.dp)
        else GlassButton("Check in again", onCheckIn, Modifier.fillMaxWidth(), icon = Duo.Add, height = 46.dp)
    }
}

/** 30-day mood chart, top feelings and data-driven patterns. */
@Composable
fun MoodTrendsCard(month: List<MoodEntry>, patterns: MoodPatterns?) {
    val th = LocalFitTheme.current
    GlassCard {
        CardHeader(Duo.Insights, "Mood trends", th.sleep) { Caption("30 days") }
        Spacer(Modifier.height(12.dp))
        val byDay = month.groupBy { it.localDate }.mapValues { (_, l) -> l.map { it.mood }.average() }.toSortedMap()
        if (byDay.size >= 2) {
            val pts = byDay.map { (d, v) ->
                val ld = runCatching { LocalDate.parse(d) }.getOrNull() ?: LocalDate.now()
                ChartPoint(ld.toEpochDay(), v, ld.format(dayFmt))
            }
            ProgressChart(pts, th.sleep, { v -> "%.1f".format(v) })
            Caption("1 = awful · 3 = okay · 5 = great. Daily average.", color = th.textFaint)
        } else {
            Caption("Check in on a couple of different days to see your trend.")
        }

        // most common feelings
        val counts = month.flatMap { e -> e.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
        if (counts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Most common feelings", style = FitType.label, color = th.textDim)
            Spacer(Modifier.height(8.dp))
            val max = counts.first().value.toFloat()
            counts.forEach { (tag, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.replaceFirstChar { it.uppercase() }, style = FitType.body, color = th.text, modifier = Modifier.width(92.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(th.textFaint.copy(alpha = 0.18f))) {
                        Box(
                            Modifier.fillMaxWidth(n / max).fillMaxHeight().clip(RoundedCornerShape(5.dp))
                                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(th.sleep, lighten(th.sleep, 0.35f))))
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Caption("$n×")
                }
            }
        }

        // patterns
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Patterns", style = FitType.label, color = th.textDim, modifier = Modifier.weight(1f))
            DataBadge(DataKind.CALCULATED)
        }
        Spacer(Modifier.height(6.dp))
        when {
            patterns == null -> Caption("Looking for patterns…")
            patterns.moodDays < 10 -> Caption("Patterns appear after 10 days of check-ins (${patterns.moodDays}/10 so far).")
            patterns.lines.isEmpty() -> Caption("Not enough matching sleep or step data yet. Patterns use sleep and steps shared through Health Connect.")
            else -> {
                patterns.lines.forEach { l ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("•", style = FitType.body, color = th.sleep)
                        Spacer(Modifier.width(8.dp))
                        Text(l, style = FitType.body, color = th.text)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Caption("These are patterns in your own logs, not proof that one causes the other.", color = th.textFaint)
            }
        }

        // recent
        if (month.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Recent check-ins", style = FitType.label, color = th.textDim)
            Spacer(Modifier.height(6.dp))
            month.take(4).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoodFace(e.mood, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        val zone = runCatching { ZoneId.of(e.zoneId) }.getOrDefault(ZoneId.systemDefault())
                        Text(Instant.ofEpochMilli(e.loggedAt).atZone(zone).format(timeFmt), style = FitType.label, color = th.text)
                        val sub = listOf(e.tags.replace(",", ", "), e.note).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotBlank()) Caption(sub.take(90))
                    }
                }
            }
        }
    }
}

data class MoodPatterns(val moodDays: Int, val lines: List<String>)

private fun fmt1(v: Double) = "%.1f".format(v)

/** Compares average daily mood across sleep and step levels. Purely descriptive. */
suspend fun computeMoodPatterns(container: AppContainer): MoodPatterns {
    val moods = container.db.moodDao().allLive()
    val byDay: Map<String, Double> = moods.groupBy { it.localDate }.mapValues { (_, l) -> l.map { it.mood }.average() }
    if (byDay.size < 10) return MoodPatterns(byDay.size, emptyList())
    val out = mutableListOf<String>()

    // Sleep (wake-up day = the day the mood was logged)
    val sleepMin: Map<String, Long> = runCatching { container.db.healthDao().allSleep() }.getOrDefault(emptyList())
        .groupBy { it.localDate }
        .mapValues { (_, l) ->
            l.sumOf { s ->
                val staged = listOfNotNull(s.deepMin, s.remMin, s.lightMin)
                if (staged.isNotEmpty()) staged.sum()
                else ((s.endAt - s.startAt) / 60_000L - (s.awakeMin ?: 0L)).coerceAtLeast(0L)
            }
        }
        .filterValues { it > 60 }
    val longS = byDay.filterKeys { (sleepMin[it] ?: -1L) >= 420L }.values
    val shortS = byDay.filterKeys { k -> sleepMin[k]?.let { it < 420 } == true }.values
    if (longS.size >= 3 && shortS.size >= 3) {
        val a = longS.average(); val b = shortS.average()
        out += if (kotlin.math.abs(a - b) < 0.2)
            "Your mood was about the same after 7 h+ of sleep (${fmt1(a)}) and after shorter nights (${fmt1(b)})."
        else
            "After nights of 7 h+ sleep your mood averaged ${fmt1(a)}, versus ${fmt1(b)} after shorter nights (${longS.size} vs ${shortS.size} days)."
    }

    // Steps
    val steps: Map<String, Long> = runCatching { container.db.healthDao().allDaily() }.getOrDefault(emptyList())
        .mapNotNull { d -> d.steps?.let { d.localDate to it } }.toMap()
    val active = byDay.filterKeys { (steps[it] ?: -1L) >= 8000L }.values
    val quiet = byDay.filterKeys { k -> steps[k]?.let { it < 8000 } == true }.values
    if (active.size >= 3 && quiet.size >= 3) {
        val a = active.average(); val b = quiet.average()
        out += if (kotlin.math.abs(a - b) < 0.2)
            "Your mood was similar on days with 8,000+ steps (${fmt1(a)}) and quieter days (${fmt1(b)})."
        else
            "On days with 8,000+ steps your mood averaged ${fmt1(a)}, versus ${fmt1(b)} on quieter days (${active.size} vs ${quiet.size} days)."
    }
    return MoodPatterns(byDay.size, out)
}
