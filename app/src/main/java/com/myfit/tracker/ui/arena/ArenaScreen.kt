package com.myfit.tracker.ui.arena

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.TopBarSpace
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val GOLD = Color(0xFFFFC83D)

/** One-shot: which Arena tab to open next time (e.g. Friends after accepting an invite). */
object ArenaLaunch { var tab: Int? = null }

/** Arena: levels & stars, scenic challenge tracks with checkpoints, journeys, battles & leaderboards, games, rewards, friends. */
@Composable
fun ArenaScreen(container: AppContainer, bottomPad: Int) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(ArenaLaunch.tab?.also { ArenaLaunch.tab = null } ?: 0) }
    val today = remember { Clock.today() }
    var ver by remember { mutableIntStateOf(0) }
    val days by produceState<List<Day>?>(null) { value = loadDays(container, today.minusDays(60), today) }
    var challenges by remember { mutableStateOf<List<ArenaChallenge>>(emptyList()) }
    var celebrate by remember { mutableStateOf<Pair<List<Award>, Int>?>(null) }   // awards + level before
    var partner by remember { mutableStateOf(ArenaPrefs.partner(ctx)) }
    var openCh by remember { mutableStateOf<ArenaChallenge?>(null) }
    LaunchedEffect(days) {
        val d = days ?: return@LaunchedEffect
        val b = baseline(d, today)
        challenges = ArenaProgress.frozen(ctx, currentChallenges(today, b))
        val before = ArenaProgress.level(ArenaProgress.total(ctx)).n
        val fresh = ArenaProgress.sync(ctx, d, today, challenges, b)
        if (fresh.isNotEmpty()) celebrate = fresh to before
        ver++
        // keep my entry fresh in every race I've joined (friends see live progress)
        val raced = ArenaPrefs.raced(ctx)
        if (raced.isNotEmpty() && container.social.user.value != null) challenges.filter { it.id in raced }.forEach { ch ->
            val st = status(ch, d, today)
            runCatching { container.social.raceUpdate(ch.id, st.frac.toDouble(), st.value, st.doneOn?.toString(), partner.id, ArenaProgress.level(ArenaProgress.total(ctx)).n) }
        }
    }
    val total = remember(ver) { ArenaProgress.total(ctx) }
    val lvl = remember(total) { ArenaProgress.level(total) }
    val award: (String, Int, String) -> Unit = { id, stars, title ->
        val before = ArenaProgress.level(ArenaProgress.total(ctx)).n
        if (ArenaProgress.grant(ctx, id, stars, title)) { celebrate = listOf(Award(id, stars, title, today)) to before; ver++ }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = TopBarSpace)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Arena", style = FitType.display, color = th.text, modifier = Modifier.weight(1f))
                    StarPill(total)
                }
                Spacer(Modifier.height(10.dp))
                LevelHero(lvl, partner) { tab = 4 }
                Spacer(Modifier.height(10.dp))
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf("Challenges", "Journeys", "Battles", "Games", "Rewards", "Friends")
                items(tabs.size) { i -> GlassChip(tabs[i], tab == i, { tab = i }) }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == 5) com.myfit.tracker.ui.social.SocialScreen(container, asTab = true, bottomPad = bottomPad, embedded = true)
                else LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val d = days
                    if (d == null) item { Caption("Loading your activity…") }
                    else when (tab) {
                        0 -> {
                            if (d.isEmpty()) item { Caption("Connect Health Connect (Settings → Health) so your watch or phone activity counts here.", color = th.warning) }
                            val wk = challenges.filter { it.period == Period.WEEK }; val mo = challenges.filter { it.period == Period.MONTH }
                            item { PeriodHeader("THIS WEEK", wk.firstOrNull()?.to, today) }
                            items(wk, key = { it.id }) { ChallengeCard(it, d, today, partner, ver) { openCh = it } }
                            item { PeriodHeader("THIS MONTH", mo.firstOrNull()?.to, today) }
                            items(mo, key = { it.id }) { ChallengeCard(it, d, today, partner, ver) { openCh = it } }
                            item { Caption("Goals are set from your last 4 weeks, about 10% above what you already do. Each checkpoint earns stars; finishing early earns a bonus.", color = th.textFaint) }
                        }
                        1 -> item { JourneyHub(d, partner) { ver++ } }
                        2 -> { item { DuelsPane(container, award) { tab = 5 } }; item { Leaderboard(container, lvl, partner) } }
                        3 -> { item { GardenGame(d, award) }; item { GhostRace(d, partner, award) } }
                        else -> item { RewardsPane(lvl, d, partner, ver) { partner = it; ArenaPrefs.setPartner(ctx, it) } }
                    }
                }
            }
        }
        val oc = openCh; val dd = days
        if (oc != null && dd != null) ChallengeDetail(container, oc, dd, today, partner, lvl.n, award) { openCh = null }
        celebrate?.let { (aw, before) -> Celebration(aw, before, ArenaProgress.level(total), partner) { celebrate = null } }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun StarPill(total: Int) {
    val th = LocalFitTheme.current
    val shown by animateFloatAsState(total.toFloat(), tween(1200), label = "stars")
    Row(Modifier.clip(CircleShape).background(GOLD.copy(alpha = 0.18f)).border(1.dp, GOLD.copy(alpha = 0.6f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) { star(center, size.minDimension / 2, true) }
        Spacer(Modifier.width(6.dp))
        Text(Fmt.int(shown.toDouble()), style = FitType.section, color = th.text)
    }
}

@Composable
private fun LevelHero(l: Level, partner: Mascot, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "hero")
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "bob")
    val frac by animateFloatAsState(l.frac, tween(1400), label = "xp")
    val next = Mascot.entries.firstOrNull { it.unlock > l.n }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(partner.accent.copy(alpha = 0.92f), partner.accent.copy(alpha = 0.55f), partner.color.copy(alpha = 0.55f))))
        .clickableNoRipple(onClick)) {
        // soft rays behind the character
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width * 0.18f, size.height * 0.55f)
            for (k in 0 until 10) {
                val a = Math.toRadians(k * 36.0 + bob * 8)
                drawLine(Color.White.copy(alpha = 0.08f), c, Offset(c.x + (cos(a) * size.width).toFloat(), c.y + (sin(a) * size.width).toFloat()), 26.dp.toPx())
            }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), c, size.height * 0.6f), size.height * 0.6f, c)
        }
        Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CastImage(partner, 108.dp, Modifier.graphicsLayer { translationY = -bob * 6.dp.toPx() })
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(l.tier.label.uppercase() + " TIER", style = FitType.overline, color = Color.White.copy(alpha = 0.85f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Level ", style = FitType.title, color = Color.White)
                    Text("${l.n}", style = FitType.hero.copy(fontSize = 40.sp, lineHeight = 42.sp), color = Color.White)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.22f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0.02f, 1f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFFFFE27A), GOLD))))
                }
                Spacer(Modifier.height(4.dp))
                Text(if (l.n >= MAX_LEVEL) "Max level — you're a Legend!" else "${l.into} / ${l.span} ⭐ to level ${l.n + 1}", style = FitType.label, color = Color.White)
                if (next != null) Text("${next.label.substringBefore(' ')} unlocks at level ${next.unlock}", style = FitType.caption, color = Color.White.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun PeriodHeader(t: String, end: LocalDate?, today: LocalDate) {
    val th = LocalFitTheme.current
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, style = FitType.overline, color = th.textDim, modifier = Modifier.weight(1f))
        if (end != null) {
            val left = (end.toEpochDay() - today.toEpochDay()).toInt()
            Caption(if (left <= 0) "Ends tonight" else "$left day${if (left == 1) "" else "s"} left", color = th.textDim)
        }
    }
}

private fun fmtVal(v: Double, m: ArenaMetric): String = when (m) {
    ArenaMetric.DISTANCE -> Fmt.trim(v, 1) + " km"
    else -> Fmt.int(v) + " " + m.unit
}

// ------------------------------------------------------------------ challenges

@Composable
private fun ChallengeCard(ch: ArenaChallenge, days: List<Day>, today: LocalDate, partner: Mascot, ver: Int, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val v = value(days, ch.metric, ch.from, minOf(ch.to, today))
    val frac = (v / ch.goal).toFloat().coerceIn(0f, 1f)
    val total = (ch.to.toEpochDay() - ch.from.toEpochDay() + 1).toInt()
    val elapsed = (today.toEpochDay() - ch.from.toEpochDay() + 1).toInt().coerceIn(1, total)
    val pace = elapsed.toFloat() / total
    val done = v >= ch.goal
    val got = CHECKPOINTS.filter { frac >= it.first - 1e-6 }.sumOf { it.second } + if (done && ArenaProgress.completedOn(ch, days)?.isBefore(ch.to) == true) EARLY_BONUS else 0
    val maxStars = CHECKPOINTS.sumOf { it.second } + EARLY_BONUS
    GlassCard(padding = 12.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CastImage(ch.mascot, 60.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.title, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Caption(ch.blurb, color = th.textDim)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(14.dp)) { star(center, size.minDimension / 2, got > 0) }
                    Spacer(Modifier.width(3.dp))
                    Text("$got/$maxStars", style = FitType.label, color = th.text)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        key(ver) { ScenicTrack(ch.scene, frac, CHECKPOINTS.map { Checkpoint(it.first.toFloat(), it.second) }, partner, pace = if (done) null else pace, accent = ch.mascot.accent) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(fmtVal(v, ch.metric), style = FitType.title, color = th.text)
            Text("  / ${fmtVal(ch.goal, ch.metric)}", style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 2.dp).weight(1f))
            Text("${(frac * 100).toInt()}%", style = FitType.section, color = if (done) th.success else ch.mascot.accent)
        }
        Spacer(Modifier.height(8.dp))
        // checkpoint chips
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CHECKPOINTS.forEach { (f, s) ->
                val ok = frac >= f - 1e-6
                Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (ok) GOLD.copy(alpha = 0.22f) else th.textFaint.copy(alpha = 0.12f)).padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (f >= 1.0) "Finish" else "${(f * 100).toInt()}%", style = FitType.caption, color = if (ok) th.text else th.textDim)
                    Spacer(Modifier.width(4.dp))
                    repeat(s) { Canvas(Modifier.size(10.dp)) { star(center, size.minDimension / 2, ok) } }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MiniBars(days.filter { !it.date.isBefore(ch.from) && !it.date.isAfter(minOf(ch.to, today)) }, ch.metric, ch.mascot.accent)
        Spacer(Modifier.height(4.dp))
        val name = ch.mascot.label.substringBefore(' ')
        Caption(when {
            done -> "$name: We did it! Stars collected 🎉"
            frac >= pace -> "$name: You're ahead of the pace marker — keep it up!"
            else -> "$name: Just behind pace — ${fmtVal(((pace - frac) * ch.goal).coerceAtLeast(0.0), ch.metric)} catches you up."
        }, color = th.textDim)
    }
}

@Composable
private fun MiniBars(days: List<Day>, m: ArenaMetric, color: Color) {
    val th = LocalFitTheme.current
    if (days.isEmpty()) return
    val vals = days.map { dayValue(it, m) }
    val max = (vals.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)
    val grow by animateFloatAsState(1f, tween(900), label = "bars")
    Canvas(Modifier.fillMaxWidth().height(36.dp)) {
        val n = vals.size.coerceAtLeast(7); val gap = 3.dp.toPx(); val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        vals.forEachIndexed { i, v ->
            val h = ((v / max).toFloat() * size.height * grow).coerceAtLeast(3f)
            drawRoundRect(if (v > 0) Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f))) else Brush.verticalGradient(listOf(th.textFaint.copy(alpha = 0.25f), th.textFaint.copy(alpha = 0.25f))),
                Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(bw / 3))
        }
    }
}

// ------------------------------------------------------------------ journeys

@Composable
private fun JourneyHub(days: List<Day>, partner: Mascot, changed: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val toaster = LocalToaster.current
    var t by remember { mutableIntStateOf(0) }
    val active = remember(t) { ArenaPrefs.journey(ctx) }
    val finished = remember(t) { ArenaPrefs.finished(ctx) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (active != null) {
            val j = Journeys.firstOrNull { it.id == active.first }
            if (j != null) {
                val km = days.filter { !it.date.isBefore(active.second) }.sumOf { it.distanceM } / 1000.0
                LaunchedEffect(km >= j.km) { if (km >= j.km && j.id !in finished) { ArenaPrefs.markFinished(ctx, j.id); toaster.show("Journey complete: ${j.title} 🎉"); t++; changed() } }
                GlassCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CastImage(j.mascot, 56.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(j.title, style = FitType.section, color = th.text)
                            Caption("${j.place} · with ${j.mascot.label.substringBefore(' ')} · started ${active.second.dayOfMonth} ${active.second.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ScenicTrack(j.scene, (km / j.km).toFloat(), j.stops.mapIndexed { i, (n, at) -> Checkpoint((at / j.km).toFloat(), if (i == j.stops.lastIndex) 6 else 2, n.take(14)) }, partner, height = 230.dp, accent = j.mascot.accent)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Fmt.trim(km.coerceAtMost(j.km), 1), style = FitType.title, color = th.text)
                        Text("  of ${Fmt.trim(j.km, 0)} km", style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    val next = j.stops.firstOrNull { it.second > km }
                    Caption(if (next == null) "You made it! 🎉" else "Next stop: ${next.first} in ${Fmt.trim(next.second - km, 1)} km · 2 ⭐ waiting")
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Leave journey", { ArenaPrefs.stopJourney(ctx); t++ }, Modifier.fillMaxWidth(), height = 40.dp)
                }
            }
        }
        Text("PICK A JOURNEY", style = FitType.overline, color = th.textDim)
        Journeys.forEach { j ->
            GlassCard(padding = 0.dp, onClick = { tick(); ArenaPrefs.startJourney(ctx, j.id); t++; toaster.show("${j.mascot.label} joins you on ${j.title}!") }) {
                Box(Modifier.fillMaxWidth().height(86.dp).background(Brush.horizontalGradient(listOf(j.scene.sky.first(), j.scene.hills.last())))) {
                    Canvas(Modifier.matchParentSize()) {
                        val road = j.scene.road
                        drawLine(road, Offset(size.width * 0.32f, size.height * 0.95f), Offset(size.width * 0.98f, size.height * 0.6f), 10.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.7f), Offset(size.width * 0.32f, size.height * 0.95f), Offset(size.width * 0.98f, size.height * 0.6f), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                    }
                    Row(Modifier.fillMaxSize().padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CastImage(j.mascot, 84.dp)
                        Column(Modifier.weight(1f)) {
                            Text(j.title + if (j.id in finished) "  ✓" else "", style = FitType.section, color = Color(0xFF1A1D22), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${j.place} · ${Fmt.trim(j.km, 0)} km · ${j.stops.size - 1} stops · ${(j.stops.size - 2) * 2 + 6} ⭐", style = FitType.caption, color = Color(0xFF2A2F36))
                        }
                        Text(if (active?.first == j.id) "Active" else "Start", style = FitType.label, color = Color.White,
                            modifier = Modifier.clip(CircleShape).background(j.mascot.accent).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
        Caption("Distance comes from your watch or phone (walking, running and cycling all count).", color = th.textFaint)
    }
}

// ------------------------------------------------------------------ battles & leaderboard

@Composable
private fun DuelsPane(container: AppContainer, award: (String, Int, String) -> Unit, openFriends: () -> Unit) {
    val th = LocalFitTheme.current
    val social = container.social
    val user by social.user.collectAsState()
    var list by remember { mutableStateOf<List<Pair<com.myfit.tracker.social.Challenge, List<com.myfit.tracker.social.ChallengeRow>>>?>(null) }
    LaunchedEffect(user) {
        list = if (user == null || !social.available) emptyList() else runCatching {
            runCatching { social.uploadNow() }
            social.myChallenges().map { it to runCatching { social.standings(it) }.getOrDefault(emptyList()) }
        }.getOrDefault(emptyList())
    }
    val today = Clock.today()
    LaunchedEffect(list) {
        list?.forEach { (ch, rows) ->
            val ended = runCatching { LocalDate.parse(ch.end).isBefore(today) }.getOrDefault(false)
            if (ended && rows.size >= 2 && rows.maxByOrNull { it.value }?.me == true && rows[0].value > 0) award("duel:${ch.id}", 5, "Won ${ch.title}")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(96.dp).height(64.dp)) {
                    CastImage(Mascot.SHAHEEN, 64.dp)
                    CastImage(Mascot.ZARA, 64.dp, Modifier.offset(x = 34.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Duels & team battles", style = FitType.section, color = th.text)
                    Caption("Race one friend, or up to 19 in a team battle. Winners get 5 ⭐.")
                }
            }
            Spacer(Modifier.height(10.dp))
            AccentButton("Start a duel or battle", openFriends, Modifier.fillMaxWidth(), height = 46.dp)
        }
        val l = list
        when {
            l == null -> Caption("Loading your battles…")
            l.isEmpty() -> Caption("No active battles yet. Invite a friend in Friends, then start one.")
            else -> l.forEach { (ch, rows) -> RaceTrack(ch, rows) }
        }
    }
}

private fun castFor(uid: String) = Mascot.entries[(uid.hashCode() and 0x7fffffff) % Mascot.entries.size]
private fun castOf(r: com.myfit.tracker.social.BoardRow) = Mascot.entries.firstOrNull { it.id == r.mascot } ?: castFor(r.uid)

@Composable
private fun RaceTrack(ch: com.myfit.tracker.social.Challenge, rows: List<com.myfit.tracker.social.ChallengeRow>) {
    val th = LocalFitTheme.current
    val sorted = rows.sortedByDescending { it.value }
    val max = (sorted.firstOrNull()?.value ?: 1.0).coerceAtLeast(1.0)
    GlassCard(padding = 14.dp) {
        Text(ch.title, style = FitType.section, color = th.text)
        Caption("${ch.metric.label} · ${ch.start} → ${ch.end} · ${if (sorted.size == 2) "Duel" else "Team battle"}")
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3C8D4F), Color(0xFF2E7340)))).padding(vertical = 8.dp)) {
            Column {
                sorted.forEachIndexed { i, r ->
                    val frac by animateFloatAsState((r.value / max).toFloat(), tween(1200), label = "lane")
                    val m = if (r.me) null else castFor(r.uid)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("${i + 1}", style = FitType.label, color = if (i == 0) GOLD else Color.White, modifier = Modifier.width(16.dp))
                        BoxWithConstraints(Modifier.weight(1f).height(44.dp)) {
                            val lane = maxWidth - 44.dp
                            Box(Modifier.fillMaxWidth().height(22.dp).align(Alignment.CenterStart).clip(RoundedCornerShape(6.dp)).background(Color(0xFFB4553A)))
                            Canvas(Modifier.fillMaxWidth().height(22.dp).align(Alignment.CenterStart)) {
                                drawLine(Color.White.copy(alpha = 0.7f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                                for (k in 0 until 4) drawRect(if (k % 2 == 0) Color.White else Color.Black, Offset(size.width - 6.dp.toPx(), k * size.height / 4), Size(6.dp.toPx(), size.height / 4))
                            }
                            Box(Modifier.offset(x = lane * frac).align(Alignment.CenterStart)) {
                                if (m != null) CastImage(m, 44.dp) else Box(Modifier.size(44.dp).clip(CircleShape).background(th.accent), contentAlignment = Alignment.Center) { Text("You", style = FitType.caption, color = th.onAccent) }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.End) {
                            Text(if (r.me) "You" else r.name, style = FitType.label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (ch.metric == com.myfit.tracker.social.Metric.DISTANCE) Fmt.trim(r.value / 1000, 1) + " km" else Fmt.int(r.value), style = FitType.caption, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Leaderboard(container: AppContainer, me: Level, partner: Mascot) {
    val th = LocalFitTheme.current
    val social = container.social
    val user by social.user.collectAsState()
    var global by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<com.myfit.tracker.social.BoardRow>?>(null) }
    LaunchedEffect(user, global) {
        rows = null
        rows = if (user == null || !social.available) emptyList() else runCatching {
            if (global) social.globalBoard(com.myfit.tracker.social.Metric.STEPS) else social.friendsBoard(com.myfit.tracker.social.Metric.STEPS)
        }.getOrDefault(emptyList())
    }
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly leaderboard", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
            GlassChip("Friends", !global, { global = false }); Spacer(Modifier.width(6.dp)); GlassChip("Everyone", global, { global = true })
        }
        Caption("Steps since Monday · device-recorded only")
        Spacer(Modifier.height(10.dp))
        val r = rows
        when {
            user == null || !social.available -> Caption("Sign in (Me → Account) to see the leaderboard.")
            r == null -> Caption("Loading…")
            r.isEmpty() -> Caption(if (global) "Nobody public yet this week." else "Add friends to compete — invite them from Friends.")
            else -> {
                // podium
                val top = r.take(3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    listOf(1, 0, 2).forEach { i ->
                        val row = top.getOrNull(i) ?: return@forEach
                        val hgt = listOf(92.dp, 70.dp, 56.dp)[i]
                        val medal = listOf(GOLD, Color(0xFFC9D1DB), Color(0xFFD99A5B))[i]
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                            CastImage(if (row.me) partner else castOf(row), if (i == 0) 78.dp else 64.dp)
                            Text(if (row.me) "You" else row.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            (if (row.me) me.n else row.level)?.let { Caption("Lv $it") }
                            Caption(Fmt.int(row.value))
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(hgt).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(Brush.verticalGradient(listOf(medal, medal.copy(alpha = 0.55f)))), contentAlignment = Alignment.TopCenter) {
                                Text("${i + 1}", style = FitType.hero.copy(fontSize = 30.sp), color = Color.White, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                r.drop(3).forEachIndexed { k, row ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (row.me) th.accent.copy(alpha = 0.16f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${k + 4}", style = FitType.label, color = th.textDim, modifier = Modifier.width(24.dp))
                        CastImage(if (row.me) partner else castOf(row), 36.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(if (row.me) "You · Lv ${me.n}" else row.name + (row.level?.let { " · Lv $it" } ?: ""), style = FitType.label, color = th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.int(row.value), style = FitType.label, color = th.text)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ games

@Composable
private fun GardenGame(days: List<Day>, award: (String, Int, String) -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val today = Clock.today()
    val steps = days.firstOrNull { it.date == today }?.steps ?: 0L
    val plants = (steps / 1000).toInt().coerceAtMost(10)
    LaunchedEffect(plants) {
        if (plants > ArenaPrefs.gardenBest(ctx)) ArenaPrefs.setGardenBest(ctx, plants)
        if (plants >= 10) award("g:garden:$today", 2, "Grew all 10 flowers in Pip's Garden")
    }
    val grow by animateFloatAsState(((steps % 1000) / 1000f), tween(900), label = "grow")
    val inf = rememberInfiniteTransition(label = "garden")
    val sway by inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "sway")
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CastImage(Mascot.PIP, 58.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Pip's Garden", style = FitType.section, color = th.text)
                Caption("Every 1,000 steps today grows a flower. Fill all 10 beds for 2 ⭐.")
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(18.dp))) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFBDE8FF), Color(0xFFEAF8FF))))
            val n = 10; val w = size.width / n; val ground = size.height * 0.78f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF7A5434), Color(0xFF5A3C24)), ground, size.height), Offset(0f, ground), Size(size.width, size.height - ground))
            drawRect(Color(0xFF5DBB63), Offset(0f, ground - 4.dp.toPx()), Size(size.width, 6.dp.toPx()))
            val petals = listOf(Color(0xFFFF8FAB), Color(0xFFFFD166), Color(0xFF9B8CFF), Color(0xFF6FD3FF), Color(0xFFFF9F68))
            for (i in 0 until n) {
                val x = w * i + w / 2
                val g = when { i < plants -> 1f; i == plants -> grow; else -> 0f }
                if (g <= 0f) { drawCircle(Color(0xFF3E2A19), 3.dp.toPx(), Offset(x, ground + 6.dp.toPx())); continue }
                val top = ground - size.height * 0.55f * g
                val tx = x + sway * 3.dp.toPx() * g
                drawLine(Color(0xFF3DAA5C), Offset(x, ground), Offset(tx, top), 3.dp.toPx(), StrokeCap.Round)
                drawOval(Color(0xFF4CC46E), Offset(x - w * 0.02f, ground - (ground - top) * 0.45f), Size(w * 0.34f, w * 0.18f))
                if (g >= 1f) {
                    val c = petals[i % petals.size]
                    for (k in 0 until 6) {
                        val a = Math.toRadians(k * 60.0 + sway * 10)
                        drawCircle(c, w * 0.15f, Offset(tx + (w * 0.17f * cos(a)).toFloat(), top + (w * 0.17f * sin(a)).toFloat()))
                    }
                    drawCircle(Color(0xFFFFE08A), w * 0.12f, Offset(tx, top))
                } else drawCircle(Color(0xFF4CC46E), w * 0.08f, Offset(tx, top))
            }
        }
        Spacer(Modifier.height(6.dp))
        Caption("${Fmt.int(steps.toDouble())} steps · $plants/10 flowers · best ever ${ArenaPrefs.gardenBest(ctx)}/10")
    }
}

@Composable
private fun GhostRace(days: List<Day>, partner: Mascot, award: (String, Int, String) -> Unit) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    val mine = days.firstOrNull { it.date == today }?.steps ?: 0L
    val past = days.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(7)) }
    val ghost = if (past.isEmpty()) 5000.0 else past.map { it.steps }.average().coerceAtLeast(2000.0)
    val now = java.time.LocalTime.now()
    val dayFrac = ((now.hour * 60 + now.minute) / 1440f).coerceIn(0.05f, 1f)
    val ghostNow = ghost * dayFrac
    LaunchedEffect(mine >= ghost) { if (mine >= ghost) award("g:ghost:$today", 1, "Beat your ghost") }
    val finish = (maxOf(ghost, mine.toDouble()) * 1.05).coerceAtLeast(1000.0)
    val me by animateFloatAsState((mine / finish).toFloat(), tween(1200), label = "me")
    val gh by animateFloatAsState((ghostNow / finish).toFloat(), tween(1200), label = "ghost")
    val inf = rememberInfiniteTransition(label = "ghost")
    val float by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "float")
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CastImage(Mascot.SHAHEEN, 58.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Ghost Race", style = FitType.section, color = th.text)
                Caption("Race your own 7-day average. Beat the ghost's full day for 1 ⭐.")
            }
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2B2F5C), Color(0xFF4B3F7A))))) {
            val usable = maxWidth - 60.dp
            Canvas(Modifier.matchParentSize()) {
                for (l in 0..1) {
                    val y = size.height * (0.32f + 0.4f * l)
                    drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), size.height * 0.26f, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f)))
                }
                for (k in 0 until 8) drawRect(if (k % 2 == 0) Color.White else Color.Black, Offset(size.width - 10.dp.toPx(), k * size.height / 8), Size(10.dp.toPx(), size.height / 8))
                // stars in the night sky
                val rnd = Random(7); repeat(14) { drawCircle(Color.White.copy(alpha = 0.5f), 1.5f, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height * 0.15f)) }
            }
            Box(Modifier.offset(x = usable * me.coerceIn(0f, 1f), y = 0.dp)) { CastAnim(partner, CastClip.RUN, 60.dp) }
            Box(Modifier.offset(x = usable * gh.coerceIn(0f, 1f), y = 58.dp + (float * 4).dp).graphicsLayer { alpha = 0.55f }) { CastImage(partner, 56.dp, dim = true) }
        }
        Spacer(Modifier.height(6.dp))
        val diff = mine - ghostNow
        Text(if (diff >= 0) "You're ${Fmt.int(diff)} steps ahead of your ghost 🏁" else "Ghost leads by ${Fmt.int(-diff)} steps — catch it!", style = FitType.label, color = if (diff >= 0) th.success else th.warning)
        Caption("You: ${Fmt.int(mine.toDouble())} · Ghost now: ${Fmt.int(ghostNow)} · Full-day ghost: ${Fmt.int(ghost)}")
    }
}

// ------------------------------------------------------------------ rewards

@Composable
private fun RewardsPane(l: Level, days: List<Day>, partner: Mascot, ver: Int, pick: (Mascot) -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val toaster = LocalToaster.current
    val badges = remember(ver) { ArenaProgress.badges(ctx, days) }
    val recent = remember(ver) { ArenaProgress.ledger(ctx).take(12) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(padding = 14.dp) {
            Text("Your partner", style = FitType.section, color = th.text)
            Caption("Your partner runs with you on every track. Level up to unlock the whole cast.")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Mascot.entries.toList()) { m ->
                    val open = l.n >= m.unlock
                    val sel = m == partner
                    Column(
                        Modifier.width(92.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (sel) m.accent.copy(alpha = 0.28f) else th.textFaint.copy(alpha = 0.10f))
                            .border(if (sel) 2.dp else 0.dp, if (sel) m.accent else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickableNoRipple { if (open) { tick(); pick(m) } else toaster.show("${m.label} unlocks at level ${m.unlock}") }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CastImage(m, 76.dp, dim = !open)
                        Text(m.label.substringBefore(' '), style = FitType.label, color = th.text)
                        Caption(if (open) (if (sel) "Partner" else "Tap to pick") else "🔒 Lv ${m.unlock}")
                    }
                }
            }
        }
        GlassCard(padding = 14.dp) { com.myfit.tracker.ui.pip.BuddyChooser() }
        // level road
        GlassCard(padding = 14.dp) {
            Text("Level road", style = FitType.section, color = th.text)
            Caption("Stars from checkpoints, daily steps, journeys, games and battles all count.")
            Spacer(Modifier.height(10.dp))
            Tier.entries.forEach { t ->
                val last = Tier.entries.getOrNull(t.ordinal + 1)?.from?.minus(1) ?: MAX_LEVEL
                val reached = l.n >= t.from
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    CastImage(t.mascot, 52.dp, dim = !reached)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${t.label} · Lv ${t.from}–$last", style = FitType.label, color = if (reached) th.text else th.textDim)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (n in t.from..last) {
                                val cur = n == l.n
                                Box(Modifier.size(if (cur) 22.dp else 18.dp).clip(CircleShape)
                                    .background(when { n < l.n -> GOLD; cur -> partner.accent; else -> th.textFaint.copy(alpha = 0.2f) }), contentAlignment = Alignment.Center) {
                                    Text("$n", style = FitType.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold), color = if (n <= l.n) Color.White else th.textDim)
                                }
                            }
                        }
                        Mascot.entries.filter { it.unlock in t.from..last && it.unlock > 1 }.forEach { m ->
                            Caption("Unlock: ${m.label} at level ${m.unlock}", color = if (l.n >= m.unlock) th.success else th.textFaint)
                        }
                    }
                }
            }
        }
        // badges
        GlassCard(padding = 14.dp) {
            Text("Awards · ${badges.count { it.earned }}/${badges.size}", style = FitType.section, color = th.text)
            Spacer(Modifier.height(8.dp))
            badges.chunked(3).forEach { rowB ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowB.forEach { b ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (b.earned) GOLD.copy(alpha = 0.16f) else th.textFaint.copy(alpha = 0.08f)).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(if (b.earned) Brush.radialGradient(listOf(Color(0xFFFFE9A8), GOLD)) else Brush.radialGradient(listOf(th.textFaint.copy(alpha = 0.25f), th.textFaint.copy(alpha = 0.15f)))),
                                contentAlignment = Alignment.Center) { Text(if (b.earned) b.emoji else "🔒", fontSize = 22.sp) }
                            Spacer(Modifier.height(4.dp))
                            Text(b.title, style = FitType.label, color = if (b.earned) th.text else th.textDim, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.desc, style = FitType.caption.copy(fontSize = 9.sp, lineHeight = 11.sp), color = th.textDim, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(3 - rowB.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (recent.isNotEmpty()) GlassCard(padding = 14.dp) {
            Text("Recent stars", style = FitType.section, color = th.text)
            Spacer(Modifier.height(6.dp))
            recent.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                    Canvas(Modifier.size(14.dp)) { star(center, size.minDimension / 2, true) }
                    Spacer(Modifier.width(6.dp))
                    Text("+${a.stars}", style = FitType.label, color = th.text, modifier = Modifier.width(28.dp))
                    Text(a.title, style = FitType.caption, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ celebration

@Composable
private fun Celebration(awards: List<Award>, levelBefore: Int, now: Level, partner: Mascot, onDone: () -> Unit) {
    val stars = awards.sumOf { it.stars }
    val leveled = now.n > levelBefore
    val unlocked = Mascot.entries.filter { it.unlock in (levelBefore + 1)..now.n }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val t by animateFloatAsState(if (shown) 1f else 0f, spring(0.6f, 220f), label = "pop")
    val inf = rememberInfiniteTransition(label = "conf")
    val fall by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600)), label = "fall")
    val parts = remember { List(46) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextInt(5)) } }
    val colors = listOf(GOLD, Color(0xFFFF5C8A), Color(0xFF4FC3FF), Color(0xFF7CFFB2), Color(0xFFB57CFF))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f * t)).clickableNoRipple(onDone), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            parts.forEach { (x, off, c) ->
                val y = ((fall + off) % 1f) * size.height
                val px = x * size.width + sin((fall + off) * 12f) * 18f
                if (c == 0) star(Offset(px, y), 7.dp.toPx(), true) else drawRect(colors[c], Offset(px, y), Size(6.dp.toPx(), 10.dp.toPx()))
            }
        }
        AnimatedVisibility(shown, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
            Column(Modifier.padding(28.dp).clip(RoundedCornerShape(30.dp))
                .background(Brush.verticalGradient(listOf(partner.accent, partner.accent.copy(alpha = 0.75f), Color(0xFF1B1E2A))))
                .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (leveled) "LEVEL UP!" else "STARS COLLECTED", style = FitType.overline.copy(fontSize = 13.sp), color = Color.White)
                CastAnim(partner, CastClip.CHEER, 170.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(34.dp)) { star(center, size.minDimension / 2, true, 1f) }
                    Spacer(Modifier.width(8.dp))
                    Text("+$stars", style = FitType.hero, color = Color.White)
                }
                if (leveled) Text("Level ${now.n} · ${now.tier.label}", style = FitType.title, color = GOLD)
                Spacer(Modifier.height(6.dp))
                awards.take(4).forEach { Text("+${it.stars} ⭐  ${it.title}", style = FitType.caption, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (awards.size > 4) Text("…and ${awards.size - 4} more", style = FitType.caption, color = Color.White.copy(alpha = 0.7f))
                unlocked.forEach { m ->
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CastImage(m, 48.dp); Spacer(Modifier.width(6.dp))
                        Text("Unlocked: ${m.label}!", style = FitType.label, color = Color.White)
                    }
                }
                Spacer(Modifier.height(12.dp))
                AccentButton("Collect", onDone, Modifier.fillMaxWidth(), height = 46.dp)
            }
        }
    }
}

@Suppress("unused") private fun Modifier.noop() = drawBehind { drawCircle(Color.Transparent, style = Stroke(0f)) }
