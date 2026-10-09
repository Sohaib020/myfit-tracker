package com.myfit.tracker.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.BadgeEngine
import com.myfit.tracker.domain.BadgeGroup
import com.myfit.tracker.domain.BadgeProgress
import com.myfit.tracker.domain.Tier
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

/** All badges: earned, in progress and locked, grouped; tap one for its rule. */
@Composable
fun BadgesScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val state by BadgeEngine.state.collectAsState()
    var open by remember { mutableStateOf<BadgeProgress?>(null) }
    LaunchedEffect(Unit) { BadgeEngine.refresh(container, force = true) }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Badges & streaks", { nav.pop() }, "Earned only from what you actually log")
        val s = state
        if (s == null) { Caption("Counting your achievements…", Modifier.padding(20.dp)); return@Column }
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Glass(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${s.earnedCount} of ${s.items.size} badges", style = FitType.section, color = th.text)
                            Caption("Gold ${s.countOf(Tier.GOLD)} · Silver ${s.countOf(Tier.SILVER)} · Bronze ${s.countOf(Tier.BRONZE)}")
                        }
                    }
                }
            }
            if (s.streaks.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("CURRENT STREAKS", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 6.dp)) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Glass(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            s.streaks.forEach { st ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(st.label, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                                    Text("${st.current} ${st.unit}", style = FitType.section, color = if (st.current > 0) th.text else th.textDim)
                                    Text("  best ${st.best}", style = FitType.caption, color = th.textFaint)
                                }
                            }
                        }
                    }
                }
            }
            BadgeGroup.entries.forEach { g ->
                val list = s.items.filter { it.def.group == g }
                if (list.isEmpty()) return@forEach
                item(span = { GridItemSpan(maxLineSpan) }) { Text(g.label.uppercase(), style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 6.dp)) }
                items(list, key = { it.def.id }) { b -> BadgeCell(b) { open = b } }
            }
        }
    }
    val o = open
    GlassSheet(visible = o != null, onDismiss = { open = null }) {
        if (o != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BadgeMedallion(o.def.shape, o.def.icon, o.def.hue, o.tier, size = 110.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text(o.def.title + (o.tier?.let { " · ${it.label}" } ?: ""), style = FitType.title, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Caption(o.def.rule, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            o.def.tiers.forEach { (t, v) ->
                val at = o.earnedAt[t]
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(t.label, style = FitType.label, color = th.text, modifier = Modifier.width(70.dp))
                    Text(o.def.goal(v), style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
                    Text(at?.let { Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(dFmt) } ?: "—", style = FitType.caption, color = if (at != null) th.success else th.textFaint)
                }
            }
            o.next?.let { (t, v) ->
                Spacer(Modifier.height(8.dp))
                Caption("Next: ${t.label} — ${o.def.goal(v)} (now ${o.value} ${o.def.unit})")
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { o.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = th.accent, trackColor = th.textFaint.copy(alpha = 0.25f))
            }
        }
    }
}

@Composable
private fun BadgeCell(b: BadgeProgress, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeMedallion(b.def.shape, b.def.icon, b.def.hue, b.tier, size = 64.dp)
            Spacer(Modifier.height(6.dp))
            Text(b.def.title, style = FitType.caption, color = if (b.earned) th.text else th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (b.next != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { b.progress }, modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).clip(RoundedCornerShape(2.dp)), color = th.accent, trackColor = th.textFaint.copy(alpha = 0.25f))
            }
        }
    }
}

/** Half-width dashboard tile: best current streak + badge count. */
@Composable
fun StreaksTile(container: AppContainer, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val state by BadgeEngine.state.collectAsState()
    LaunchedEffect(Unit) { BadgeEngine.refresh(container) }
    Glass(Modifier.fillMaxWidth().height(com.myfit.tracker.ui.dashboard.TileHeight), onClick = onClick) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text("STREAKS", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(6.dp))
            val s = state
            val top = s?.streaks?.maxByOrNull { it.current }
            if (top == null || top.current == 0) {
                Text("Start one today", style = FitType.section, color = th.text)
                Caption("Log water, food or a workout")
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${top.current}", style = FitType.title, color = th.text)
                    Spacer(Modifier.width(4.dp))
                    Caption(top.unit)
                }
                Caption(top.label, Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            Caption(s?.let { "${it.earnedCount} badges earned" } ?: "…", color = th.accentBright)
        }
    }
}

/** Shown once per newly earned badge (e.g. from MainShell). */
@Composable
fun BadgeCelebration(container: AppContainer) {
    if (com.myfit.tracker.domain.BadgeEngine.quiet) return
    val th = LocalFitTheme.current
    val state by BadgeEngine.state.collectAsState()
    val list = state?.celebrate.orEmpty()
    val first = list.firstOrNull()
    GlassSheet(visible = first != null, onDismiss = { BadgeEngine.markCelebrated(container, list) }) {
        if (first != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Pip(PipMood.CELEBRATE, size = 96.dp, interactive = false)
                Spacer(Modifier.width(8.dp))
                BadgeMedallion(first.def.shape, first.def.icon, first.def.hue, first.tier, size = 96.dp)
            }
            Spacer(Modifier.height(10.dp))
            Text("New badge: ${first.def.title}" + (first.tier?.let { " · ${it.label}" } ?: ""), style = FitType.title, color = th.text)
            Caption(first.tier?.let { t -> first.def.tiers.firstOrNull { it.first == t }?.let { first.def.goal(it.second) } } ?: first.def.rule)
            if (list.size > 1) Caption("+${list.size - 1} more — see them in Badges.", color = th.accentBright)
            Spacer(Modifier.height(14.dp))
            AccentButton("Nice!", { BadgeEngine.markCelebrated(container, list) }, Modifier.fillMaxWidth())
        }
    }
}
