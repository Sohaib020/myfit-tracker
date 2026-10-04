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

/** Full-screen challenge: host character, big track, numbers, daily chart, checkpoints, rewards and the friends race. */
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
    val total = (ch.to.toEpochDay() - ch.from.toEpochDay() + 1).toInt()
    val elapsed = (today.toEpochDay() - ch.from.toEpochDay() + 1).toInt().coerceIn(1, total)
    val left = (ch.to.toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(0)
    val pace = elapsed.toFloat() / total
    val done = st.doneOn != null
    val inRange = days.filter { !it.date.isBefore(ch.from) && !it.date.isAfter(minOf(ch.to, today)) }
    val perDay = if (left + 1 > 0) ((ch.goal - st.value).coerceAtLeast(0.0) / (left + 1)) else 0.0
    val best = inRange.maxOfOrNull { dayValue(it, ch.metric) } ?: 0.0
    val avg = if (inRange.isEmpty()) 0.0 else inRange.sumOf { dayValue(it, ch.metric) } / inRange.size
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ch.mascot.accent.copy(alpha = 0.35f), th.bgBottom.copy(alpha = 0.96f), th.bgBottom)))) {
        Column(Modifier.fillMaxSize()) {
            OverlayTopBar(ch.title, onClose, subtitle = (if (ch.period == Period.WEEK) "Weekly" else "Monthly") + " · ${dm(ch.from)} – ${dm(ch.to)}")
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CastAnim(ch.mascot, if (done) CastClip.CHEER else CastClip.WAVE, 130.dp)
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)).background(Color.White.copy(alpha = if (th.isLight) 0.9f else 0.12f)).padding(14.dp)) {
                            Column {
                                Text(ch.mascot.label, style = FitType.label, color = ch.mascot.accent)
                                Text(when {
                                    done -> "We did it on ${dm(st.doneOn!!)}! Every star is yours."
                                    st.frac >= pace -> "You're ahead of pace! ${fmt(perDay, ch.metric)} a day keeps us on track."
                                    else -> "Let's catch up: ${fmt(perDay, ch.metric)} a day gets us there by ${dm(ch.to)}."
                                }, style = FitType.body, color = th.text)
                            }
                        }
                    }
                }
                item { ScenicTrack(ch.scene, st.frac, CHECKPOINTS.map { Checkpoint(it.first.toFloat(), it.second) }, partner, height = 250.dp, pace = if (done) null else pace, accent = ch.mascot.accent) }
                item {
                    GlassCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmt(st.value, ch.metric), style = FitType.metric, color = th.text)
                            Text("  / ${fmt(ch.goal, ch.metric)}", style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 4.dp).weight(1f))
                            Text("${(st.frac * 100).toInt()}%", style = FitType.title, color = if (done) th.success else ch.mascot.accent)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Stat(if (done) "Done" else "$left", if (done) "finished" else "days left", Modifier.weight(1f))
                            Stat(if (done) "—" else fmt(perDay, ch.metric).substringBefore(' '), "needed / day", Modifier.weight(1f))
                            Stat(fmt(avg, ch.metric).substringBefore(' '), "daily avg", Modifier.weight(1f))
                            Stat(fmt(best, ch.metric).substringBefore(' '), "best day", Modifier.weight(1f))
                        }
                    }
                }
                item { DailyChart(ch, inRange, today, total) }
                item {
                    GlassCard(padding = 14.dp) {
                        Text("Checkpoints & rewards", style = FitType.section, color = th.text)
                        Spacer(Modifier.height(8.dp))
                        CHECKPOINTS.forEachIndexed { i, (f, s) ->
                            val on = st.reached[i]
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(30.dp).clip(CircleShape).background(if (on != null) GOLD else th.textFaint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                    Text(if (f >= 1.0) "🏁" else "${(f * 100).toInt()}", style = FitType.caption, color = if (on != null) Color(0xFF3A2A00) else th.textDim)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (f >= 1.0) "Finish line" else "${(f * 100).toInt()}% checkpoint", style = FitType.label, color = th.text)
                                    Caption(if (on != null) "Reached ${dm(on)}" else "${fmt((ch.goal * f - st.value).coerceAtLeast(0.0), ch.metric)} to go")
                                }
                                repeat(s) { Canvas(Modifier.size(14.dp)) { star(center, size.minDimension / 2, on != null) } }
                            }
                        }
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(if (done && st.doneOn!!.isBefore(ch.to)) GOLD else th.textFaint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text("⚡", style = FitType.caption) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("Finish early bonus", style = FitType.label, color = th.text); Caption("Complete before the last day") }
                            repeat(EARLY_BONUS) { Canvas(Modifier.size(14.dp)) { star(center, size.minDimension / 2, done && st.doneOn!!.isBefore(ch.to)) } }
                        }
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(th.textFaint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Text("📸", style = FitType.caption) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text("First among friends", style = FitType.label, color = th.text); Caption("Join the race below and finish first · badge progress") }
                            repeat(3) { Canvas(Modifier.size(14.dp)) { star(center, size.minDimension / 2, false) } }
                        }
                    }
                }
                item { FriendsRace(container, ch, st, partner, level, award) }
            }
        }
    }
}

@Composable
private fun Stat(v: String, l: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Caption(l)
    }
}

@Composable
private fun DailyChart(ch: ArenaChallenge, inRange: List<Day>, today: LocalDate, total: Int) {
    val th = LocalFitTheme.current
    val byDate = inRange.associateBy { it.date }
    val vals = (0 until total).map { k -> ch.from.plusDays(k.toLong()).let { d -> if (d.isAfter(today)) null else byDate[d]?.let { dayValue(it, ch.metric) } ?: 0.0 } }
    val target = ch.goal / total
    val max = ((vals.filterNotNull().maxOrNull() ?: 0.0).coerceAtLeast(target * 1.2)).coerceAtLeast(1e-6)
    val grow by animateFloatAsState(1f, tween(900), label = "dc")
    GlassCard(padding = 14.dp) {
        Text("Day by day", style = FitType.section, color = th.text)
        Caption("Dashed line = even pace (${fmt(target, ch.metric)} a day)")
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = vals.size; val gap = (if (n > 14) 2 else 4).dp.toPx(); val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
            vals.forEachIndexed { i, v ->
                val x = i * (bw + gap)
                if (v == null) { drawRoundRect(th.textFaint.copy(alpha = 0.10f), Offset(x, size.height - 4f), Size(bw, 4f), CornerRadius(2f)); return@forEachIndexed }
                val h = ((v / max).toFloat() * size.height * grow).coerceAtLeast(3f)
                drawRoundRect(Brush.verticalGradient(listOf(ch.mascot.accent, ch.mascot.accent.copy(alpha = 0.5f))), Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3))
            }
            val y = size.height * (1 - (target / max).toFloat())
            drawLine(th.text.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Race your friends", style = FitType.section, color = th.text)
                Caption("Everyone's goal is personal, so the race is on % — first to finish wins 3 ⭐.")
            }
        }
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
                    if (r.size <= 1) Caption("No friends in this race yet — invite them!")
                    r.forEachIndexed { i, row ->
                        val m = if (row.me) partner else Mascot.entries.firstOrNull { it.id == row.mascot } ?: Mascot.entries[(row.uid.hashCode() and 0x7fffffff) % Mascot.entries.size]
                        val f by animateFloatAsState(row.pct.toFloat().coerceIn(0f, 1f), tween(1100), label = "race")
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (row.doneAt != null) listOf("🥇", "🥈", "🥉").getOrElse(i) { "🏁" } else "${i + 1}", style = FitType.label, color = th.text, modifier = Modifier.width(26.dp))
                            BoxWithConstraints(Modifier.weight(1f).height(46.dp)) {
                                val lane = maxWidth - 46.dp
                                Box(Modifier.fillMaxWidth().height(12.dp).align(Alignment.CenterStart).clip(CircleShape).background(th.textFaint.copy(alpha = 0.18f)))
                                Box(Modifier.width(lane * f + 23.dp).height(12.dp).align(Alignment.CenterStart).clip(CircleShape).background(m.accent))
                                Box(Modifier.offset(x = lane * f).align(Alignment.CenterStart)) { CastImage(m, 46.dp) }
                            }
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.width(74.dp), horizontalAlignment = Alignment.End) {
                                Text(if (row.me) "You" else row.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Caption(row.doneAt?.let { "done ${dm(LocalDate.parse(it))}" } ?: "${(row.pct * 100).toInt()}%" + (row.level?.let { " · Lv $it" } ?: ""))
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
