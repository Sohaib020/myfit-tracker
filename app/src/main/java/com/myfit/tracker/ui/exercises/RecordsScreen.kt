package com.myfit.tracker.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Records
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import java.time.LocalDate

/** Every PR you've set, newest first, across all exercises. */
@Composable
fun RecordsScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val u = LocalSettings.current.units
    val history by remember { container.workoutRepo.allHistory() }.collectAsState(initial = null)
    val exercises by container.exerciseRepo.everything.collectAsState(initial = emptyList())
    var range by remember { mutableIntStateOf(1) }   // 0 = 30 days, 1 = 90 days, 2 = all
    val byId = exercises.associateBy { it.id }
    val events = remember(history, exercises) {
        val h = history ?: return@remember emptyList()
        h.groupBy { it.exerciseId }.flatMap { (id, rows) -> byId[id]?.let { Records.events(id, it.measurementType, rows) } ?: emptyList() }
            .sortedByDescending { it.at }
    }
    val from = when (range) { 0 -> Clock.today().minusDays(29); 1 -> Clock.today().minusDays(89); else -> null }
    val shown = events.filter { from == null || !LocalDate.parse(it.date).isBefore(from) }
    val monthCount = events.count { !LocalDate.parse(it.date).isBefore(Clock.today().minusDays(29)) }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Personal records", { nav.pop() }, "Detected automatically from your logged sets")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pip(if (monthCount > 0) PipMood.CELEBRATE else PipMood.FLEX, size = 76.dp, interactive = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$monthCount PR${if (monthCount == 1) "" else "s"} in the last 30 days", style = FitType.section, color = th.text)
                            Caption("${events.size} in total. Your first session of an exercise is the baseline; warm-ups never count.")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("30 days", "90 days", "All time").forEachIndexed { i, l -> GlassChip(l, range == i, { range = i }) }
                }
            }
            if (history != null && shown.isEmpty()) item {
                Caption(if (events.isEmpty()) "No PRs yet. Log an exercise twice and beat your first session — it'll show up here." else "No PRs in this range.")
            }
            items(shown, key = { "${it.workoutId}-${it.exerciseId}-${it.type}-${it.setId}" }) { p ->
                val ex = byId[p.exerciseId] ?: return@items
                Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = { nav.push(Overlay.ExerciseDetail(ex.id)) }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseImage(ex, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Caption(Records.label(p.type) + " · " + p.date + if (p.isEstimate) " · estimate" else "")
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Records.format(p, u), style = FitType.section, color = th.text)
                            Records.delta(p, u)?.let { Text(it, style = FitType.caption, color = th.success) }
                        }
                    }
                }
            }
        }
    }
}

/** Compact "New records" card for the finish-workout screen. */
@Composable
fun NewRecordsCard(prs: List<Pair<String, Records.Pr>>) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    if (prs.isEmpty()) return
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Duo.EmojiEvents, th.warning, 36.dp)
                Spacer(Modifier.width(10.dp))
                Text("${prs.size} new record${if (prs.size == 1) "" else "s"}!", style = FitType.section, color = th.text)
            }
            Spacer(Modifier.height(8.dp))
            prs.forEach { (name, p) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption(Records.label(p.type) + if (p.isEstimate) " · estimate" else "")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Records.format(p, u), style = FitType.label, color = th.text)
                        Records.delta(p, u)?.let { Text(it, style = FitType.caption, color = th.success) }
                    }
                }
            }
        }
    }
}
