package com.myfit.tracker.ui.mind

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MindSession
import com.myfit.tracker.data.db.MoodEntry
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.DayOfWeek
import java.time.LocalDate

/** Saves a finished session (callers only pass sessions ≥ 30 s). Fire-and-forget. */
fun saveMindSession(container: AppContainer, type: String, title: String, durationSec: Long, startedAt: Long) {
    container.write {
        val s = Stamp.of(startedAt)
        container.db.mindDao().insert(
            MindSession(
                type = type, title = title, durationSec = durationSec, startedAt = startedAt,
                zoneId = s.zoneId, localDate = s.localDate, createdAt = System.currentTimeMillis(),
            )
        )
        // TODO(health-connect): also write a MindfulnessSessionRecord to Health Connect.
        //  Official docs (connect-client ≥ 1.1.0-rc01) show:
        //    MindfulnessSessionRecord(startTime, startZoneOffset, endTime, endZoneOffset, metadata,
        //      mindfulnessSessionType = MINDFULNESS_SESSION_TYPE_BREATHING / _MEDITATION, title, notes)
        //  guarded by hc.features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION)
        //  == FEATURE_STATUS_AVAILABLE and the WRITE_MINDFULNESS permission. It was introduced as an
        //  *experimental* API in 1.1.0-beta02 and it could not be verified offline whether 1.1.0 still
        //  requires an opt-in annotation (or the exact Metadata factory), so the write is skipped for now.
    }
}

private sealed interface MindView {
    data object Hub : MindView
    data class Breath(val pattern: BreathPattern, val minutes: Int, val voice: Boolean) : MindView
    data class Med(val choice: MedChoice) : MindView
}

@Composable
fun MindScreen(container: AppContainer) {
    val nav = LocalNav.current
    var view by remember { mutableStateOf<MindView>(MindView.Hub) }
    var moodSheet by remember { mutableStateOf(false) }

    when (val v = view) {
        is MindView.Breath -> BreathingSession(container, v.pattern, v.minutes, v.voice) { logMood ->
            view = MindView.Hub
            if (logMood) moodSheet = true
        }
        is MindView.Med -> MeditationSession(container, v.choice) { logMood ->
            view = MindView.Hub
            if (logMood) moodSheet = true
        }
        MindView.Hub -> Box(Modifier.fillMaxSize()) {
            MindHub(
                container,
                onBack = { nav.pop() },
                onBreath = { p, m, voice -> view = MindView.Breath(p, m, voice) },
                onMed = { view = MindView.Med(it) },
                onMood = { moodSheet = true },
            )
            GlassSheet(moodSheet, { moodSheet = false }) {
                MoodCheckInForm(container) { moodSheet = false }
            }
        }
    }
}

@Composable
private fun MindHub(
    container: AppContainer,
    onBack: () -> Unit,
    onBreath: (BreathPattern, Int, Boolean) -> Unit,
    onMed: (MedChoice) -> Unit,
    onMood: () -> Unit,
) {
    val today = remember { Clock.today() }
    val todayKey = Clock.dateKey(today)
    val monthFrom = remember { Clock.dateKey(today.minusDays(29)) }
    val sessions by remember { container.db.mindDao().observeSince(monthFrom) }.collectAsState(initial = emptyList())
    val days by remember { container.db.mindDao().observeDays() }.collectAsState(initial = emptyList())
    val moods by remember { container.db.moodDao().observeSince(monthFrom) }.collectAsState(initial = emptyList())
    val todayMoods = moods.filter { it.localDate == todayKey }
    val patterns by produceState<MoodPatterns?>(null, moods.size) { value = runCatching { computeMoodPatterns(container) }.getOrNull() ?: MoodPatterns(0, emptyList()) }
    val stress by produceState<StressEstimate?>(null) { value = runCatching { computeStress(container) }.getOrNull() }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Mindfulness", onBack, subtitle = "Breathe · meditate · check in")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "week") { WeeklyCard(sessions, days, today) }
            item(key = "mood") { MoodTodayCard(todayMoods, onMood) }
            item(key = "t1") { SectionTitle("Practice") }
            item(key = "breath") { BreathingCard(onBreath) }
            item(key = "med") { MeditationCard(onMed) }
            item(key = "t2") { SectionTitle("Insights") }
            item(key = "stress") { StressCard(stress) }
            item(key = "trends") { MoodTrendsCard(moods, patterns) }
            item(key = "foot") {
                Caption(
                    "Mindfulness tools support wellbeing; they're not a treatment. If you're struggling, please talk to someone you trust or a health professional.",
                    Modifier.padding(horizontal = 6.dp),
                    color = LocalFitTheme.current.textFaint,
                )
            }
        }
    }
}

private fun streakDays(days: List<String>, today: LocalDate): Int {
    val set = days.toHashSet()
    var d = if (Clock.dateKey(today) in set) today else today.minusDays(1)
    var n = 0
    while (Clock.dateKey(d) in set) { n++; d = d.minusDays(1) }
    return n
}

@Composable
private fun WeeklyCard(sessions: List<MindSession>, days: List<String>, today: LocalDate) {
    val th = LocalFitTheme.current
    val monday = today.with(DayOfWeek.MONDAY)
    val weekKeys = (0..6).map { Clock.dateKey(monday.plusDays(it.toLong())) }
    val week = sessions.filter { it.localDate >= weekKeys.first() && it.localDate <= weekKeys.last() }
    val minutes = week.sumOf { it.durationSec } / 60
    val perDay = weekKeys.map { k -> week.filter { it.localDate == k }.sumOf { it.durationSec } / 60.0 }
    val streak = streakDays(days, today)
    GlassCard {
        CardHeader(Duo.SelfImprovement, "This week", th.sleep)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("$minutes", "mindful min", Modifier.weight(1f))
            Stat("${week.size}", if (week.size == 1) "session" else "sessions", Modifier.weight(1f))
            Stat("$streak", "day streak", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        val max = (perDay.maxOrNull() ?: 0.0).coerceAtLeast(5.0)
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            perDay.forEachIndexed { i, m ->
                val isToday = weekKeys[i] == Clock.dateKey(today)
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val f = (m / max).toFloat().coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth().weight(1f, fill = true),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight(if (m > 0) f.coerceAtLeast(0.08f) else 0.04f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (m > 0) Brush.verticalGradient(listOf(lighten(th.sleep, 0.35f), th.sleep))
                                    else Brush.verticalGradient(listOf(th.textFaint.copy(alpha = 0.25f), th.textFaint.copy(alpha = 0.25f)))
                                )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("MTWTFSS"[i].toString(), style = FitType.caption, color = if (isToday) th.text else th.textFaint)
                }
            }
        }
        if (sessions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            sessions.take(3).forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s.type == "BREATHING") "Breathing · ${s.title}" else s.title,
                        style = FitType.label, color = th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Caption("${mmss(s.durationSec)} · ${shortDay(s.localDate, today)}")
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Caption("Even one minute of slow breathing counts. Start below.")
        }
    }
}

private fun shortDay(key: String, today: LocalDate): String {
    val d = runCatching { LocalDate.parse(key) }.getOrNull() ?: return key
    return when (d) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) + " " + d.dayOfMonth
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Text(value, style = FitType.metric, color = th.text)
        Caption(label)
    }
}

/** Half-width dashboard tile. */
@Composable
fun MindTile(container: AppContainer, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val today = remember { Clock.today() }
    val todayKey = Clock.dateKey(today)
    val weekFrom = remember { Clock.dateKey(today.with(DayOfWeek.MONDAY)) }
    val moods: List<MoodEntry> by remember(todayKey) { container.db.moodDao().observeDay(todayKey) }.collectAsState(initial = emptyList())
    val week: List<MindSession> by remember(weekFrom) { container.db.mindDao().observeSince(weekFrom) }.collectAsState(initial = emptyList())
    val minutes = week.sumOf { it.durationSec } / 60
    val mood = moods.firstOrNull()?.mood
    GlassCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Duo.SelfImprovement, th.sleep, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text("Mind", style = FitType.label, color = th.textDim, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        if (mood != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoodFace(mood, 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(moodWord(mood), style = FitType.title, color = th.text, maxLines = 1)
            }
        } else {
            Text("How are you feeling?", style = FitType.section, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))
        Caption(if (minutes > 0) "$minutes min mindful this week" else "Tap to breathe or check in", modifier = Modifier, color = th.textDim)
    }
}
