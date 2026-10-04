package com.myfit.tracker.ui.arena

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

private val GOLD = Color(0xFFFFC83D)

private fun fmt(v: Double, m: ArenaMetric) = if (m == ArenaMetric.DISTANCE) Fmt.trim(v, 1) + " km" else Fmt.int(v) + " " + m.unit
private fun dm(d: LocalDate) = "${d.dayOfMonth} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"

/** Progress of a challenge as fractions + the day each checkpoint was reached. */
data class ChallengeStatus(val value: Double, val frac: Float, val doneOn: LocalDate?, val reached: Map<Int, LocalDate>)

fun status(ch: ArenaChallenge, days: List<Day>, today: LocalDate): ChallengeStatus {
    val inRange = days.filter { !it.date.isBefore(ch.from) && !it.date.isAfter(minOf(ch.to, today)) }.sortedBy { it.date }
    var acc = 0.0; val reached = HashMap<Int, LocalDate>()
    inRange.forEach { d ->
        acc += dayValue(d, ch.metric)
        CHECKPOINTS.forEachIndexed { i, (f, _) -> if (i !in reached && acc >= ch.goal * f - 1e-9) reached[i] = d.date }
    }
    return ChallengeStatus(acc, (acc / ch.goal).toFloat().coerceIn(0f, 1f), reached[CHECKPOINTS.lastIndex], reached)
}

/** Full-screen challenge dashboard: progress ring, pace chart, key numbers, daily breakdown, friends and rewards. */
@Composable
fun ChallengeDetail(
    container: AppContainer, ch: ArenaChallenge, days: List<Day>, today: LocalDate, partner: Mascot, level: Int,
    award: (String, Int, String) -> Unit, onClose: () -> Unit,
) {
    val th = LocalFitTheme.current
    BackHandler(onBack = onClose)
    // hide the floating dock while this full-screen view is open
    DisposableEffect(Unit) { com.myfit.tracker.ui.components.SheetsOpen.count.intValue++; onDispose { com.myfit.tracker.ui.components.SheetsOpen.count.intValue = (com.myfit.tracker.ui.components.SheetsOpen.count.intValue - 1).coerceAtLeast(0) } }
    val st = remember(ch, days) { status(ch, days, today) }
    val m = remember(ch, days, today) { paceModel(ch, days, today) }
    val done = m.doneOn != null
    val accent = ch.mascot.accent
    // opaque screen: nothing behind shows through, and taps don't fall through to the list underneath
    Box(Modifier.fillMaxSize().background(th.bgBottom.copy(alpha = 1f)).pointerInput(Unit) { detectTapGestures { } }) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))))
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar(ch.title, onClose, subtitle = (if (ch.period == Period.WEEK) "Weekly" else "Monthly") + " · ${dm(ch.from)} – ${dm(ch.to)}")
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---- the route map
                item { ChallengeMap(ch, m.frac, st.reached.keys, m.daysLeft, com.myfit.tracker.ui.social.rememberAccountPhoto(container), partner) }
                // ---- headline: ring + status
                item {
                    GlassCard(padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(m.frac, if (done) null else m.pace, if (done) th.success else accent, 128.dp, 12.dp) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(m.frac * 100).toInt()}%", style = FitType.metric, color = th.text)
                                    Caption(if (done) "complete" else "of goal")
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(fmt(m.value, ch.metric), style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Caption("of ${fmt(m.goal, ch.metric)}")
                                Spacer(Modifier.height(8.dp))
                                PaceBadge(m, ch.metric)
                                Spacer(Modifier.height(8.dp))
                                Caption(when {
                                    done -> "Finished with ${m.daysLeft} day${if (m.daysLeft == 1) "" else "s"} to spare."
                                    m.daysLeft == 0 -> "Last day — ${fmt((m.goal - m.value).coerceAtLeast(0.0), ch.metric)} to go."
                                    else -> "${m.daysLeft} day${if (m.daysLeft == 1) "" else "s"} left · tick on the ring = even pace"
                                })
                            }
                        }
                    }
                }
                // ---- key numbers
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Kpi("Daily pace needed", if (done) "Done" else fmt(m.perDayNeeded, ch.metric), if (done) "goal reached" else "every day until ${dm(ch.to)}", Modifier.weight(1f))
                        Kpi("Projected finish", when { done -> dm(m.doneOn!!); m.projectedFinish != null -> dm(m.projectedFinish); else -> "Not on pace" },
                            when { done -> "completed"; m.projectedFinish != null -> "at your ${fmt(m.avg, ch.metric)}/day average"; else -> "${fmt(m.perDayNeeded, ch.metric)}/day catches up" },
                            Modifier.weight(1f), warn = !done && m.projectedFinish == null)
                    }
                }
                // ---- pace chart
                item {
                    GlassCard(padding = 14.dp) {
                        Text("Progress vs. pace", style = FitType.section, color = th.text)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Legend(accent, "You", false); Legend(th.text.copy(alpha = 0.5f), "Even pace", true)
                            if (!done && m.avg > 0) Legend(accent.copy(alpha = 0.6f), "Projection", true)
                        }
                        Spacer(Modifier.height(10.dp))
                        PaceChart(m, ch, partner, 190.dp)
                    }
                }
                // ---- daily breakdown
                item {
                    GlassCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Daily breakdown", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                            Caption("target ${fmt(m.goal / m.total, ch.metric)}/day")
                        }
                        Spacer(Modifier.height(10.dp))
                        DailyBars(m, ch, 110.dp)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat(fmt(m.avg, ch.metric), "daily average", Modifier.weight(1f))
                            MiniStat(fmt(m.best, ch.metric), "best day" + (m.bestDate?.let { " · ${dm(it)}" } ?: ""), Modifier.weight(1f), GOLD)
                            MiniStat("${m.daily.count { it != null && it >= m.goal / m.total }}/${m.elapsed}", "days on target", Modifier.weight(1f))
                        }
                    }
                }
                item { FriendsRace(container, ch, st, partner, level, award) }
                // ---- rewards
                item {
                    GlassCard(padding = 14.dp) {
                        Text("Checkpoints & rewards", style = FitType.section, color = th.text)
                        Spacer(Modifier.height(6.dp))
                        CHECKPOINTS.forEachIndexed { i, (f, s) ->
                            val on = st.reached[i]
                            RewardRow(if (f >= 1.0) "Goal reached" else "${(f * 100).toInt()}% checkpoint",
                                if (on != null) "Reached ${dm(on)}" else "${fmt((ch.goal * f - st.value).coerceAtLeast(0.0), ch.metric)} to go", s, on != null, if (f >= 1.0) "100" else "${(f * 100).toInt()}")
                        }
                        val early = done && st.doneOn!!.isBefore(ch.to)
                        RewardRow("Finish early", "Complete before the last day", EARLY_BONUS, early, "⏱")
                        RewardRow("First among friends", "Win the race below · badge progress", 3, false, "1st")
                    }
                }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, sub: String, modifier: Modifier, warn: Boolean = false) {
    val th = LocalFitTheme.current
    GlassCard(modifier, padding = 14.dp) {
        Text(label.uppercase(), style = FitType.overline, color = th.textDim, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value, style = FitType.title, color = if (warn) th.warning else th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Caption(sub, color = th.textDim)
    }
}

@Composable
private fun Legend(c: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 8.dp)) {
            drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f, 4f)) else null)
        }
        Spacer(Modifier.width(5.dp)); Caption(label)
    }
}

@Composable
private fun MiniStat(v: String, l: String, modifier: Modifier, dot: Color? = null) {
    val th = LocalFitTheme.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(5.dp)) }
            Text(v, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Caption(l)
    }
}

@Composable
private fun RewardRow(title: String, sub: String, stars: Int, on: Boolean, badge: String) {
    val th = LocalFitTheme.current
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) GOLD.copy(alpha = 0.9f) else th.text.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Text(badge, style = FitType.caption, color = if (on) Color(0xFF3A2A00) else th.textDim)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, style = FitType.label, color = th.text); Caption(sub) }
        Text("+$stars", style = FitType.label, color = if (on) GOLD else th.textDim)
        Spacer(Modifier.width(3.dp))
        Canvas(Modifier.size(13.dp)) { star(center, size.minDimension / 2, on) }
    }
}

/** Race your friends in this challenge: join, see who finishes first, earn stars + badges. */
@Composable
private fun FriendsRace(container: AppContainer, ch: ArenaChallenge, st: ChallengeStatus, partner: Mascot, level: Int, award: (String, Int, String) -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val social = container.social
    val user by social.user.collectAsState()
    var joined by remember { mutableStateOf(ch.id in ArenaPrefs.raced(ctx)) }
    var rows by remember { mutableStateOf<List<com.myfit.tracker.social.RaceRow>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(user, joined, refresh) {
        if (user == null || !social.available) { rows = emptyList(); return@LaunchedEffect }
        if (joined) runCatching { social.raceUpdate(ch.id, st.frac.toDouble(), st.value, st.doneOn?.toString(), partner.id, level) }
        rows = runCatching { social.raceStandings(ch.id) }.getOrDefault(emptyList())
        val r = rows.orEmpty()
        if (joined && r.size >= 2 && r.first().me && r.first().doneAt != null) award("race:${ch.id}", 3, "First among friends · ${ch.title}")
    }
    GlassCard(padding = 14.dp) {
        Text("Friends race", style = FitType.section, color = th.text)
        Caption("Ranked by % of each person's own goal — first to 100% wins 3 ⭐.")
        Spacer(Modifier.height(10.dp))
        when {
            user == null || !social.available -> Caption("Sign in (Me → Account) to race friends.")
            !joined -> AccentButton("Join the race", {
                joined = true; ArenaPrefs.setRaced(ctx, ch.id, true); toaster.show("You're in! Friends who join show up here.")
            }, Modifier.fillMaxWidth(), height = 46.dp)
            else -> {
                val r = rows
                if (r == null) Caption("Loading…")
                else {
                    val myIdx = r.indexOfFirst { it.me }
                    if (r.size <= 1) Caption("No friends in this race yet — invite them below.")
                    else if (myIdx >= 0) {
                        val me = r[myIdx]
                        val lead = r.first()
                        val behind = r.getOrNull(myIdx + 1)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(th.text.copy(alpha = 0.05f)).padding(12.dp)) {
                            MiniStat("${myIdx + 1} of ${r.size}", "your rank", Modifier.weight(1f))
                            MiniStat(if (myIdx == 0) "Leading" else "${((lead.pct - me.pct) * 100).toInt().coerceAtLeast(0)}%", if (myIdx == 0) "you're in front" else "behind ${lead.name}", Modifier.weight(1f))
                            MiniStat(behind?.let { "${((me.pct - it.pct) * 100).toInt().coerceAtLeast(0)}%" } ?: "—", behind?.let { "ahead of ${it.name}" } ?: "no one behind", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    r.forEachIndexed { i, row ->
                        val mm = if (row.me) partner else Mascot.entries.firstOrNull { it.id == row.mascot } ?: Mascot.entries[(row.uid.hashCode() and 0x7fffffff) % Mascot.entries.size]
                        val f by animateFloatAsState(row.pct.toFloat().coerceIn(0f, 1f), tween(1100), label = "race")
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = FitType.label, color = if (i == 0) GOLD else th.textDim, modifier = Modifier.width(20.dp))
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { CastImage(mm, 28.dp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (row.me) "You" else row.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(row.doneAt?.let { "Done ${dm(LocalDate.parse(it))}" } ?: "${(row.pct * 100).toInt()}%", style = FitType.caption, color = if (row.doneAt != null) th.success else th.textDim)
                                }
                                Spacer(Modifier.height(5.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(th.text.copy(alpha = 0.08f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.02f)).clip(CircleShape).background(if (row.me) ch.mascot.accent else th.text.copy(alpha = 0.45f)))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton("Invite friends", {
                        scope.launch {
                            val p = runCatching { social.ensureProfile() }.getOrNull()
                            if (p != null) com.myfit.tracker.social.Invite.share(ctx, p.code, p.name) else toaster.show("Sign in first")
                        }
                    }, Modifier.weight(1f), height = 42.dp)
                    GlassButton("Leave", {
                        scope.launch { runCatching { social.raceLeave(ch.id) } }
                        joined = false; ArenaPrefs.setRaced(ctx, ch.id, false); refresh++
                    }, height = 42.dp)
                }
            }
        }
    }
}
