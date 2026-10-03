package com.myfit.tracker.ui.arena

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** Arena: weekly & monthly challenges, virtual journeys with the mascot cast, duels/teams, mini-games and friends. */
@Composable
fun ArenaScreen(container: AppContainer, bottomPad: Int) {
    val th = LocalFitTheme.current
    var tab by remember { mutableIntStateOf(0) }
    val today = remember { Clock.today() }
    val days by produceState<List<Day>?>(null) { value = loadDays(container, today.minusDays(40), today) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = TopBarSpace)) {
            Text("Arena", style = FitType.display, color = th.text)
            Caption("Challenges, journeys and friendly battles · only device-recorded activity counts")
            Spacer(Modifier.height(8.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tabs = listOf("Challenges", "Journeys", "Duels & teams", "Games", "Friends")
            items(tabs.size) { i -> GlassChip(tabs[i], tab == i, { tab = i }) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                4 -> com.myfit.tracker.ui.social.SocialScreen(container, asTab = true, bottomPad = bottomPad, embedded = true)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomPad.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val d = days
                    if (d == null) item { Caption("Loading your activity…") }
                    else when (tab) {
                        0 -> {
                            item { SectionLabel("THIS WEEK") }
                            items(currentChallenges(today).filter { it.period == Period.WEEK }, key = { it.id }) { ChallengeCard(it, d) }
                            item { SectionLabel("THIS MONTH") }
                            items(currentChallenges(today).filter { it.period == Period.MONTH }, key = { it.id }) { ChallengeCard(it, d) }
                            if (d.isEmpty()) item { Caption("Connect Health Connect (Settings → Health) so your watch or phone steps count here.", color = th.warning) }
                        }
                        1 -> { item { JourneyHub(d) } }
                        2 -> { item { DuelsPane(container) { tab = 4 } } }
                        else -> { item { GardenGame(d) }; item { GhostRace(d) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(t: String) = Text(t, style = FitType.overline, color = LocalFitTheme.current.textDim, modifier = Modifier.padding(top = 4.dp))

private fun fmtVal(v: Double, m: ArenaMetric): String = when (m) {
    ArenaMetric.DISTANCE -> Fmt.trim(v, 1) + " km"
    else -> Fmt.int(v) + " " + m.unit
}

// ------------------------------------------------------------------ challenges

@Composable
private fun ChallengeCard(ch: ArenaChallenge, days: List<Day>) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    val v = value(days, ch.metric, ch.from, ch.to)
    val frac = (v / ch.goal).toFloat().coerceIn(0f, 1f)
    val anim by animateFloatAsState(frac, tween(900), label = "ch")
    val left = (ch.to.toEpochDay() - today.toEpochDay()).toInt()
    val done = v >= ch.goal
    // expected pace line: where you'd be if you spread the goal evenly
    val total = (ch.to.toEpochDay() - ch.from.toEpochDay() + 1).toInt()
    val elapsed = (today.toEpochDay() - ch.from.toEpochDay() + 1).toInt().coerceIn(1, total)
    val pace = elapsed.toFloat() / total
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MascotFace(ch.mascot, 52.dp, happy = done || frac >= pace)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ch.title, style = FitType.section, color = th.text)
                Caption(ch.blurb)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.height / 2
                drawRoundRect(th.textFaint.copy(alpha = 0.22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                drawRoundRect(Brush.horizontalGradient(listOf(ch.mascot.color, if (done) th.success else th.accent)), size = Size(size.width * anim, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                if (!done) drawLine(th.text.copy(alpha = 0.7f), Offset(size.width * pace, -3f), Offset(size.width * pace, size.height + 3f), 2.dp.toPx())
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text("${fmtVal(v, ch.metric)} / ${fmtVal(ch.goal, ch.metric)}", style = FitType.label, color = th.text, modifier = Modifier.weight(1f))
            Caption(when { done -> "Completed 🎉"; left <= 0 -> "Last day!"; else -> "$left day${if (left == 1) "" else "s"} left" }, color = if (done) th.success else th.textDim)
        }
        Spacer(Modifier.height(8.dp))
        MiniBars(days.filter { !it.date.isBefore(ch.from) && !it.date.isAfter(minOf(ch.to, today)) }, ch.metric, ch.mascot.color)
        Caption(if (done) "${ch.mascot.label}: You did it!" else if (frac >= pace) "${ch.mascot.label}: You're ahead of pace — keep it up!" else "${ch.mascot.label}: A bit behind pace — a walk today catches you up.", color = th.textFaint)
    }
}

@Composable
private fun MiniBars(days: List<Day>, m: ArenaMetric, color: Color) {
    val th = LocalFitTheme.current
    if (days.isEmpty()) return
    val vals = days.map { dayValue(it, m) }
    val max = (vals.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val n = vals.size; val gap = 3.dp.toPx(); val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        vals.forEachIndexed { i, v ->
            val h = (v / max).toFloat() * size.height
            drawRoundRect(if (v > 0) color else th.textFaint.copy(alpha = 0.25f), Offset(i * (bw + gap), size.height - h.coerceAtLeast(3f)), Size(bw, h.coerceAtLeast(3f)), androidx.compose.ui.geometry.CornerRadius(bw / 3))
        }
    }
}

// ------------------------------------------------------------------ journeys

@Composable
private fun JourneyHub(days: List<Day>) {
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
                LaunchedEffect(km >= j.km) { if (km >= j.km && j.id !in finished) { ArenaPrefs.markFinished(ctx, j.id); toaster.show("Journey complete: ${j.title} 🎉"); t++ } }
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MascotFace(j.mascot, 48.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(j.title, style = FitType.section, color = th.text)
                            Caption("${j.place} · started ${active.second}")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    RouteMap(j, (km / j.km).toFloat().coerceIn(0f, 1f))
                    Spacer(Modifier.height(6.dp))
                    Text("${Fmt.trim(km.coerceAtMost(j.km), 1)} of ${Fmt.trim(j.km, 0)} km", style = FitType.section, color = th.text)
                    val next = j.stops.firstOrNull { it.second > km }
                    Caption(if (next == null) "You made it! 🎉" else "Next stop: ${next.first} in ${Fmt.trim(next.second - km, 1)} km")
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Leave journey", { ArenaPrefs.stopJourney(ctx); t++ }, Modifier.fillMaxWidth(), height = 40.dp)
                }
            }
        }
        Text("PICK A JOURNEY", style = FitType.overline, color = th.textDim)
        Journeys.forEach { j ->
            GlassCard(padding = 14.dp, onClick = { tick(); ArenaPrefs.startJourney(ctx, j.id); t++; toaster.show("${j.mascot.label} joins you on ${j.title}!") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MascotFace(j.mascot, 44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(j.title + if (j.id in finished) "  ✓" else "", style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption("${j.place} · ${Fmt.trim(j.km, 0)} km · ${j.stops.size} stops")
                    }
                    Text(if (active?.first == j.id) "Active" else "Start", style = FitType.label, color = th.accentBright)
                }
            }
        }
        Caption("Distance comes from your watch or phone (walking, running and cycling all count).", color = th.textFaint)
    }
}

@Composable
private fun RouteMap(j: Journey, progress: Float) {
    val th = LocalFitTheme.current
    val anim by animateFloatAsState(progress, tween(1200), label = "route")
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(j.mascot.color.copy(alpha = 0.25f), th.textFaint.copy(alpha = 0.08f))))) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val pts = j.path.map { (x, y) -> Offset(x * size.width, y * size.height) }
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]; val b = pts[i]
                    quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
                }
                lineTo(pts.last().x, pts.last().y)
            }
            drawPath(path, th.textFaint.copy(alpha = 0.5f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))
            val pm = PathMeasure().apply { setPath(path, false) }
            val done = Path(); pm.getSegment(0f, pm.length * anim, done, true)
            drawPath(done, j.mascot.accent, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            // stops
            j.stops.forEach { (_, km) ->
                val p = pm.getPosition(pm.length * (km / j.km).toFloat())
                drawCircle(Color.White, 6.dp.toPx(), p); drawCircle(j.mascot.accent, 4.dp.toPx(), p)
            }
            // the runner
            val me = pm.getPosition(pm.length * anim)
            drawCircle(Color.Black.copy(alpha = 0.25f), 13.dp.toPx(), me + Offset(0f, 3f))
            drawCircle(j.mascot.color, 12.dp.toPx(), me)
            drawCircle(j.mascot.accent, 12.dp.toPx(), me, style = Stroke(3.dp.toPx()))
        }
    }
}

// ------------------------------------------------------------------ duels & teams

@Composable
private fun DuelsPane(container: AppContainer, openFriends: () -> Unit) {
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MascotFace(Mascot.SHAHEEN, 48.dp); Spacer(Modifier.width(4.dp)); MascotFace(Mascot.ZARA, 48.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Duels & team battles", style = FitType.section, color = th.text)
                    Caption("Challenge one friend, or pick up to 19 for a team battle. Steps, distance or workout minutes.")
                }
            }
            Spacer(Modifier.height(10.dp))
            AccentButton("Start a duel or battle", openFriends, Modifier.fillMaxWidth(), height = 46.dp)
        }
        val l = list
        when {
            l == null -> Caption("Loading your battles…")
            l.isEmpty() -> Caption("No active battles yet. Add a friend with their code in Friends, then start one.")
            else -> l.forEach { (ch, rows) -> RaceTrack(ch, rows) }
        }
    }
}

@Composable
private fun RaceTrack(ch: com.myfit.tracker.social.Challenge, rows: List<com.myfit.tracker.social.ChallengeRow>) {
    val th = LocalFitTheme.current
    val sorted = rows.sortedByDescending { it.value }
    val max = (sorted.firstOrNull()?.value ?: 1.0).coerceAtLeast(1.0)
    val cast = Mascot.entries
    GlassCard(padding = 14.dp) {
        Text(ch.title, style = FitType.section, color = th.text)
        Caption("${ch.metric.label} · ${ch.start} → ${ch.end} · ${if (sorted.size == 2) "Duel" else "Team battle"}")
        Spacer(Modifier.height(10.dp))
        sorted.forEachIndexed { i, r ->
            val frac by animateFloatAsState((r.value / max).toFloat(), tween(900), label = "lane")
            val m = cast[(r.uid.hashCode() and 0x7fffffff) % cast.size]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("${i + 1}", style = FitType.label, color = if (i == 0) th.warning else th.textDim, modifier = Modifier.width(18.dp))
                BoxWithConstraints(Modifier.weight(1f).height(36.dp)) {
                    val lane = maxWidth - 34.dp
                    Box(Modifier.fillMaxWidth().height(10.dp).align(Alignment.CenterStart).clip(CircleShape).background(th.textFaint.copy(alpha = 0.2f)))
                    Box(Modifier.width(lane * frac + 17.dp).height(10.dp).align(Alignment.CenterStart).clip(CircleShape).background(if (r.me) th.accent else m.color))
                    Box(Modifier.offset(x = lane * frac).align(Alignment.CenterStart)) { MascotFace(m, 34.dp, happy = i == 0) }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.width(86.dp), horizontalAlignment = Alignment.End) {
                    Text(if (r.me) "You" else r.name, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Caption(if (ch.metric == com.myfit.tracker.social.Metric.DISTANCE) Fmt.trim(r.value / 1000, 1) + " km" else Fmt.int(r.value))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ games

@Composable
private fun GardenGame(days: List<Day>) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val today = Clock.today()
    val steps = days.firstOrNull { it.date == today }?.steps ?: 0L
    val plants = (steps / 1500).toInt().coerceAtMost(10)
    LaunchedEffect(plants) { if (plants > ArenaPrefs.gardenBest(ctx)) ArenaPrefs.setGardenBest(ctx, plants) }
    val grow by animateFloatAsState(((steps % 1500) / 1500f), tween(900), label = "grow")
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MascotFace(Mascot.PIP, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Pip's Garden", style = FitType.section, color = th.text)
                Caption("Every 1,500 steps today grows a flower. Fill all 10 beds!")
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = 10; val w = size.width / n; val ground = size.height * 0.82f
            drawRect(Color(0xFF6B4A2E).copy(alpha = 0.55f), Offset(0f, ground), Size(size.width, size.height - ground))
            val petals = listOf(Color(0xFFFF8FAB), Color(0xFFFFD166), Color(0xFF9B8CFF), Color(0xFF6FD3FF), Color(0xFFFF9F68))
            for (i in 0 until n) {
                val x = w * i + w / 2
                val g = when { i < plants -> 1f; i == plants -> grow; else -> 0f }
                if (g <= 0f) { drawCircle(Color(0xFF3E2A19), 3.dp.toPx(), Offset(x, ground + 6.dp.toPx())); continue }
                val top = ground - size.height * 0.6f * g
                drawLine(Color(0xFF3DAA5C), Offset(x, ground), Offset(x, top), 3.dp.toPx(), StrokeCap.Round)
                drawOval(Color(0xFF4CC46E), Offset(x, ground - (ground - top) * 0.45f), Size(w * 0.32f, w * 0.18f))
                if (g >= 1f) {
                    val c = petals[i % petals.size]
                    for (k in 0 until 6) {
                        val a = Math.toRadians(k * 60.0)
                        drawCircle(c, w * 0.14f, Offset(x + (w * 0.16f * kotlin.math.cos(a)).toFloat(), top + (w * 0.16f * kotlin.math.sin(a)).toFloat()))
                    }
                    drawCircle(Color(0xFFFFE08A), w * 0.11f, Offset(x, top))
                } else drawCircle(Color(0xFF4CC46E), w * 0.08f, Offset(x, top))
            }
        }
        Caption("${Fmt.int(steps.toDouble())} steps · $plants/10 flowers · best ever ${ArenaPrefs.gardenBest(ctx)}/10")
    }
}

@Composable
private fun GhostRace(days: List<Day>) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    val mine = days.firstOrNull { it.date == today }?.steps ?: 0L
    val past = days.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(7)) }
    val ghost = if (past.isEmpty()) 6000.0 else past.map { it.steps }.average()
    val now = java.time.LocalTime.now()
    val dayFrac = ((now.hour * 60 + now.minute) / 1440f).coerceIn(0.05f, 1f)
    val ghostNow = ghost * dayFrac           // where your average self would be by this time of day
    val finish = (maxOf(ghost, mine.toDouble()) * 1.05).coerceAtLeast(1000.0)
    val me by animateFloatAsState((mine / finish).toFloat(), tween(1000), label = "me")
    val gh by animateFloatAsState((ghostNow / finish).toFloat(), tween(1000), label = "ghost")
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MascotFace(Mascot.SHAHEEN, 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Ghost Race", style = FitType.section, color = th.text)
                Caption("Race your own 7-day average. The ghost is where your average self would be right now.")
            }
        }
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val laneH = size.height / 2
            for (l in 0..1) {
                val y = laneH * l + laneH / 2
                drawLine(th.textFaint.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), laneH * 0.55f, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 16f)))
            }
            // finish line
            for (k in 0 until 8) drawRect(if (k % 2 == 0) Color.White else Color.Black, Offset(size.width - 10.dp.toPx(), k * size.height / 8), Size(10.dp.toPx(), size.height / 8))
            val usable = size.width - 34.dp.toPx()
            drawCircle(th.accent, laneH * 0.32f, Offset(16.dp.toPx() + usable * me.coerceIn(0f, 1f), laneH / 2))
            drawCircle(Color.White.copy(alpha = 0.55f), laneH * 0.32f, Offset(16.dp.toPx() + usable * gh.coerceIn(0f, 1f), laneH * 1.5f))
        }
        val diff = mine - ghostNow
        Text(if (diff >= 0) "You're ${Fmt.int(diff)} steps ahead of your ghost 🏁" else "Ghost leads by ${Fmt.int(-diff)} steps — catch it!", style = FitType.label, color = if (diff >= 0) th.success else th.warning)
        Caption("You: ${Fmt.int(mine.toDouble())} · Ghost now: ${Fmt.int(ghostNow)} · Daily average: ${Fmt.int(ghost)}")
    }
}

@Suppress("unused") private val keepDate = LocalDate.MIN
