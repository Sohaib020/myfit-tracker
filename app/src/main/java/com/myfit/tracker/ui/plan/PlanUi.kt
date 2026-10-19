package com.myfit.tracker.ui.plan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.DayPlan
import com.myfit.tracker.domain.DayPlans
import com.myfit.tracker.domain.DayStats
import com.myfit.tracker.domain.DayTargets
import com.myfit.tracker.domain.EnergyUnit
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.PlanItem
import com.myfit.tracker.domain.PlanKind
import com.myfit.tracker.domain.PlanMetric
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ====================================================================== shared state

/** What has happened so far today, for live plan progress. */
data class PlanLive(
    val steps: Long? = null, val waterMl: Double? = null, val sleepMin: Double? = null, val kcal: Double? = null, val protein: Double? = null,
    val workouts: Int = 0, val weighed: Boolean = false, val checkedIn: Boolean = false,
) {
    companion object {
        fun of(s: DayStats?) = if (s == null) PlanLive() else PlanLive(s.steps, s.waterMl, s.sleepMin, s.kcal, s.protein, s.workouts, s.weighed, s.checkedIn)
    }
}

/** 0..1 progress for a plan item, null when the item isn't measurable. */
fun progressOf(i: PlanItem, l: PlanLive): Float? {
    val t = i.target
    fun r(v: Double?) = if (v == null || t == null || t <= 0) 0f else (v / t).toFloat().coerceIn(0f, 1f)
    return when (i.metric) {
        PlanMetric.STEPS -> r(l.steps?.toDouble())
        PlanMetric.WATER -> r(l.waterMl)
        PlanMetric.SLEEP -> r(l.sleepMin)
        PlanMetric.KCAL -> r(l.kcal)
        PlanMetric.PROTEIN -> r(l.protein)
        PlanMetric.WORKOUT -> if (l.workouts > 0) 1f else 0f
        PlanMetric.WEIGH -> if (l.weighed) 1f else 0f
        PlanMetric.MIND -> if (l.checkedIn) 1f else 0f
        PlanMetric.NONE -> null
    }
}

fun doneCount(p: DayPlan, l: PlanLive): Pair<Int, Int> {
    val m = p.items.mapNotNull { progressOf(it, l) }
    return m.count { it >= 0.999f } to m.size
}

/** Today's plan: built on first use (offline), then the optional AI tip is added in the background. */
@Composable
fun rememberTodayPlan(c: AppContainer): DayPlan? {
    val hist by DayPlans.history.collectAsState()
    // follows the calendar day, so a phone left open past midnight gets the new day's plan
    val day by com.myfit.tracker.ui.dashboard.todayFlow.collectAsState(initial = Clock.today())
    val key = Clock.dateKey(day)
    LaunchedEffect(key) {
        withContext(Dispatchers.IO) {
            runCatching { DayPlans.tip(c, DayPlans.ensureToday(c)) }
        }
    }
    return hist[key]
}

/** Today's recorded stats, refreshed every 20 s while visible. */
@Composable
fun rememberTodayLive(c: AppContainer): PlanLive {
    val v by produceState(PlanLive()) {
        while (true) {
            val t = Clock.today()
            value = PlanLive.of(withContext(Dispatchers.IO) { runCatching { DayPlans.stats(c, t, t)[t] }.getOrNull() })
            delay(20_000)
        }
    }
    return v
}

private fun kindColor(k: PlanKind, th: com.myfit.tracker.ui.theme.FitTheme) = when (k) { PlanKind.MOVE -> th.steps; PlanKind.FUEL -> th.protein; PlanKind.RECOVER -> th.sleep }
private fun kindLabel(k: PlanKind) = when (k) { PlanKind.MOVE -> "Move"; PlanKind.FUEL -> "Fuel"; PlanKind.RECOVER -> "Recover" }
private fun metricIcon(m: PlanMetric): ImageVector = when (m) {
    PlanMetric.STEPS -> Duo.Footprints; PlanMetric.WORKOUT -> Duo.FitnessCenter; PlanMetric.KCAL -> Duo.Flame; PlanMetric.PROTEIN -> Duo.Egg
    PlanMetric.WATER -> Duo.WaterDrop; PlanMetric.SLEEP -> Duo.Bedtime; PlanMetric.WEIGH -> Duo.MonitorWeight; PlanMetric.MIND -> Duo.Mood
    PlanMetric.NONE -> Duo.SelfImprovement
}

// ====================================================================== small pieces

/** Ring that grows with a spring the first time it's shown. */
@Composable
fun ScoreRing(frac: Float, size: Dp, stroke: Dp, color: Color, track: Color, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(frac) { a.animateTo(frac.coerceIn(0f, 1f), Motion.gentle()) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx(); val r = this.size.minDimension / 2 - sw / 2
            val tl = Offset(center.x - r, center.y - r); val sz = Size(r * 2, r * 2)
            drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(sw))
            if (a.value > 0.001f) drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color), center), -90f, 360f * a.value, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val th = LocalFitTheme.current
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), Motion.gentle(), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(th.text.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val th = LocalFitTheme.current
    Glass(Modifier.fillMaxWidth().height(44.dp), shape = CircleShape) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val w = maxWidth / labels.size
            val x by androidx.compose.animation.core.animateDpAsState(w * selected, Motion.snappy(), label = "seg")
            Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(Brush.verticalGradient(listOf(th.accentBright, th.accent))))
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { i, l ->
                    Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickableNoRipple { onSelect(i) }, contentAlignment = Alignment.Center) {
                        Text(l, style = FitType.label, color = if (i == selected) th.onAccent else th.textDim, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ====================================================================== Home: inside the large "Today's progress" card

/** Plan my day block at the bottom of the large Today's progress card (replaces the old check-in strip). */
@Composable
fun PlanStrip(c: AppContainer, live: PlanLive) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val plan = rememberTodayPlan(c)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(th.accent.copy(alpha = if (th.isLight) 0.10f else 0.16f), th.sleep.copy(alpha = if (th.isLight) 0.06f else 0.10f))))
            .clickableNoRipple { nav.push(Overlay.DayPlan(0)) }.padding(14.dp),
    ) {
        if (plan == null) {
            Text("PLAN MY DAY", style = FitType.overline, color = th.accentBright)
            Caption("Putting today's plan together…"); return@Column
        }
        val (done, total) = doneCount(plan, live)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(if (total == 0) 0f else done / total.toFloat(), 46.dp, 5.dp, th.accentBright, th.text.copy(alpha = 0.1f)) {
                Text("$done/$total", style = FitType.label, color = th.text)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAN MY DAY", style = FitType.overline, color = th.accentBright)
                Text(plan.headline, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Duo.KeyboardArrowRight, null, tint = th.textDim, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        // one line per pillar: the first measurable item and its progress
        PlanKind.entries.forEach { k ->
            val item = plan.items.firstOrNull { it.kind == k && it.metric != PlanMetric.NONE } ?: plan.items.firstOrNull { it.kind == k } ?: return@forEach
            val p = progressOf(item, live)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(kindColor(k, th).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(if (p != null && p >= 0.999f) Duo.Check else metricIcon(item.metric), null, tint = kindColor(k, th), modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p != null) { Spacer(Modifier.height(4.dp)); Bar(p, kindColor(k, th), height = 4.dp) }
                }
            }
        }
    }
}

/** Small Home tile: the second half of Today's progress when the card is set to small. */
@Composable
fun PlanTile(c: AppContainer, live: PlanLive, height: Dp) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val plan = rememberTodayPlan(c)
    GlassCard(Modifier.height(height), onClick = { nav.push(Overlay.DayPlan(0)) }, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Duo.TrackChanges, th.accentBright, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text("Plan my day", style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        if (plan == null) { Caption("Planning…"); return@GlassCard }
        val (done, total) = doneCount(plan, live)
        Text("$done of $total", style = FitType.title, color = th.text, maxLines = 1)
        Text(plan.headline, style = FitType.caption, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PlanKind.entries.forEach { k ->
                val ps = plan.items.filter { it.kind == k }.mapNotNull { progressOf(it, live) }
                Bar(if (ps.isEmpty()) 0f else ps.average().toFloat(), kindColor(k, th), Modifier.weight(1f), 5.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row { PlanKind.entries.forEach { k -> Text(kindLabel(k), style = FitType.caption, color = th.textFaint, modifier = Modifier.weight(1f), maxLines = 1) } }
    }
}

// ====================================================================== the hub: Today / Report / History

@Composable
fun DailyHubScreen(c: AppContainer, startTab: Int) {
    val nav = LocalNav.current
    var tab by remember { mutableIntStateOf(startTab.coerceIn(0, 2)) }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Plan my day", { nav.pop() }, "Your plan, daily report and history")
        Box(Modifier.padding(horizontal = 16.dp)) { Segmented(listOf("Today", "Report", "History"), tab) { tab = it; Motion.markScreen() } }
        Spacer(Modifier.height(8.dp))
        androidx.compose.animation.AnimatedContent(tab, transitionSpec = {
            val d = if (targetState > initialState) 1 else -1
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180, 40)) + androidx.compose.animation.slideInHorizontally(spring(0.9f, 520f)) { d * it / 14 })
                .togetherWith(androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)))
        }, label = "hub") { t ->
            when (t) { 0 -> TodayTab(c); 1 -> ReportTab(c); else -> HistoryTab(c) }
        }
    }
}

@Composable
private fun TodayTab(c: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val plan = rememberTodayPlan(c)
    val live = rememberTodayLive(c)
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (plan == null) { item { Caption("Putting today's plan together…") }; return@LazyColumn }
        item {
            val (done, total) = doneCount(plan, live)
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(if (total == 0) 0f else done / total.toFloat(), 84.dp, 9.dp, th.accentBright, th.text.copy(alpha = 0.1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$done/$total", style = FitType.section, color = th.text)
                            Text("done", style = FitType.caption, color = th.textDim)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())).uppercase(), style = FitType.overline, color = th.accentBright)
                        Text(plan.headline, style = FitType.title, color = th.text)
                        Caption(plan.why)
                    }
                }
                plan.tip?.let {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(th.accent.copy(alpha = 0.12f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Duo.AutoAwesome, null, tint = th.accentBright, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(it, style = FitType.body, color = th.text)
                    }
                }
            }
        }
        PlanKind.entries.forEach { k ->
            val items = plan.items.filter { it.kind == k }
            if (items.isEmpty()) return@forEach
            item(key = k.name) {
                Text(kindLabel(k).uppercase(), style = FitType.overline, color = kindColor(k, th), modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
            items.forEachIndexed { i, pi -> item(key = "${k.name}$i") { PlanItemRow(pi, progressOf(pi, live), kindColor(k, th)) { go(nav, pi.metric) } } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.myfit.tracker.ui.theme.GlassButton("Rebuild plan", {
                    scope.launch { withContext(Dispatchers.IO) { runCatching { DayPlans.today(c, force = true) } } }
                }, Modifier.weight(1f), icon = Duo.Replay, height = 46.dp)
                com.myfit.tracker.ui.theme.GlassButton("Daily report", { nav.replace(Overlay.DayPlan(1)) }, Modifier.weight(1f), icon = Duo.Insights, height = 46.dp)
            }
        }
        item { Caption("Built on your phone from what you recorded. Targets are estimates — adjust them any time in Report.", Modifier.padding(horizontal = 6.dp)) }
    }
}

private fun go(nav: com.myfit.tracker.ui.nav.Nav, m: PlanMetric) = when (m) {
    PlanMetric.STEPS, PlanMetric.WATER -> nav.push(Overlay.Today)
    PlanMetric.KCAL, PlanMetric.PROTEIN -> nav.push(Overlay.Food(null))
    PlanMetric.WORKOUT -> nav.push(Overlay.TrainProgress)
    PlanMetric.WEIGH -> nav.push(Overlay.Body)
    PlanMetric.MIND, PlanMetric.NONE -> nav.push(Overlay.Mind)
    PlanMetric.SLEEP -> nav.push(Overlay.HealthHub)
}

@Composable
private fun PlanItemRow(i: PlanItem, p: Float?, color: Color, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val done = p != null && p >= 0.999f
    GlassCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = if (done) 0.9f else 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (done) Duo.Check else metricIcon(i.metric), null, tint = if (done) Color.White else color, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(i.title, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Caption(i.detail)
                if (p != null) { Spacer(Modifier.height(8.dp)); Bar(p, color) }
            }
            if (p != null) { Spacer(Modifier.width(10.dp)); Text("${(p * 100).toInt()}%", style = FitType.label, color = if (done) color else th.textDim) }
        }
    }
}

// ---------------------------------------------------------------------- Report & goals

private enum class Trend(val label: String) { STEPS("Steps"), SLEEP("Sleep"), WATER("Water"), KCAL("Calories"), PROTEIN("Protein"), WEIGHT("Weight") }

private data class ReportData(val y: DayStats?, val t: DayTargets, val days: List<DayStats>)

@Composable
private fun ReportTab(c: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    var range by remember { mutableIntStateOf(7) }
    var trend by remember { mutableStateOf(Trend.STEPS) }
    var reload by remember { mutableIntStateOf(0) }
    val data by produceState<ReportData?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val today = Clock.today()
                val t = DayPlans.targets(c, today)
                val st = DayPlans.stats(c, today.minusDays(29), today)
                ReportData(st[today.minusDays(1)], t, st.values.sortedBy { it.date })
            }.getOrNull()
        }
    }
    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d == null) { item { Caption("Putting your report together…") }; return@LazyColumn }
        // yesterday at a glance
        item {
            val score = d.y?.let { DayPlans.score(it, d.t) }
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing((score ?: 0) / 100f, 92.dp, 10.dp, scoreColor(score, th), th.text.copy(alpha = 0.1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(score?.toString() ?: "—", style = FitType.metric, color = th.text)
                            Text("/ 100", style = FitType.caption, color = th.textDim)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("YESTERDAY", style = FitType.overline, color = th.accentBright)
                        Text(when { score == null -> "Nothing recorded"; score >= 85 -> "Great day"; score >= 65 -> "Good day"; score >= 40 -> "Room to grow"; else -> "A slow one" }, style = FitType.title, color = th.text)
                        Caption("How close you got to each goal")
                    }
                }
                Spacer(Modifier.height(14.dp))
                val y = d.y
                StatRow(Duo.Footprints, "Steps", y?.steps?.let { Fmt.int(it) }, Fmt.int(d.t.steps), y?.steps?.let { it / d.t.steps }, th.steps)
                StatRow(Duo.Bedtime, "Sleep", y?.sleepMin?.let { Fmt.duration(it.toLong()) }, Fmt.duration(d.t.sleepMin.toLong()), y?.sleepMin?.let { it / d.t.sleepMin }, th.sleep)
                StatRow(Duo.WaterDrop, "Water", y?.waterMl?.let { "%.1f L".format(it / 1000) }, "%.1f L".format(d.t.waterMl / 1000), y?.waterMl?.let { it / d.t.waterMl }, th.water)
                if (d.t.kcal != null) StatRow(Duo.Flame, "Calories", y?.kcal?.let { Fmt.int(it) }, Fmt.int(d.t.kcal), y?.kcal?.let { it / d.t.kcal }, th.warning)
                if (d.t.protein != null) StatRow(Duo.Egg, "Protein", y?.protein?.let { "${Fmt.int(it)} g" }, "${Fmt.int(d.t.protein)} g", y?.protein?.let { it / d.t.protein }, th.protein)
                StatRow(Duo.FitnessCenter, "Workouts", y?.let { if (it.workouts == 0) "Rest" else "${it.workouts} · ${it.trainMin} min" }, null, null, th.accentBright)
            }
        }
        // trends
        item { Text("TRENDS", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
        item {
            GlassCard {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Trend.entries.size) { i -> val tr = Trend.entries[i]; GlassChip(tr.label, tr == trend, { trend = tr }) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(7, 30).forEach { r -> GlassChip("$r days", r == range, { range = r }) } }
                Spacer(Modifier.height(14.dp))
                val days = d.days.takeLast(range)
                val (vals, target, color, fmt) = trendSeries(trend, days, d.t, th)
                TrendChart(vals, target, color, if (trend == Trend.WEIGHT) ChartKind.LINE else ChartKind.BARS, Modifier.fillMaxWidth().height(160.dp))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(days.firstOrNull()?.date?.format(DateTimeFormatter.ofPattern("d MMM")) ?: "", style = FitType.caption, color = th.textFaint, modifier = Modifier.weight(1f))
                    Text("Today", style = FitType.caption, color = th.textFaint)
                }
                Spacer(Modifier.height(12.dp))
                val have = vals.filterNotNull()
                Row(Modifier.fillMaxWidth()) {
                    Summary("Average", have.takeIf { it.isNotEmpty() }?.average()?.let(fmt) ?: "—", Modifier.weight(1f))
                    Summary("Best", have.maxOrNull()?.let(fmt) ?: "—", Modifier.weight(1f))
                    Summary(if (target != null && trend != Trend.WEIGHT) "At goal" else "Days logged",
                        if (target != null && trend != Trend.WEIGHT) "${have.count { it >= target * (if (trend == Trend.KCAL) 0.9 else 1.0) && (trend != Trend.KCAL || it <= target * 1.1) }}/${vals.size}" else "${have.size}/${vals.size}", Modifier.weight(1f))
                }
            }
        }
        // goals
        item { Text("YOUR DAILY GOALS", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
        item { GoalsEditor(c, d.t) { reload++ } }
        item {
            GlassCard(onClick = { nav.push(Overlay.Body) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Duo.Straighten, th.fat, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Body & measurements", style = FitType.section, color = th.text)
                        Caption("Weight trend, waist, chest and progress photos")
                    }
                    Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
                }
            }
        }
    }
}

private fun scoreColor(s: Int?, th: com.myfit.tracker.ui.theme.FitTheme) = when { s == null -> th.textFaint; s >= 85 -> th.success; s >= 60 -> th.accentBright; s >= 40 -> th.warning; else -> th.danger }

@Composable
private fun StatRow(icon: ImageVector, label: String, value: String?, target: String?, frac: Double?, color: Color) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = FitType.label, color = th.text, modifier = Modifier.weight(1f))
                Text(value ?: "—", style = FitType.label, color = if (value == null) th.textFaint else th.text)
                if (target != null) Text(" / $target", style = FitType.caption, color = th.textDim)
                if (frac != null && frac >= 1.0) { Spacer(Modifier.width(6.dp)); Icon(Duo.CheckCircle, "Goal met", tint = th.success, modifier = Modifier.size(15.dp)) }
            }
            if (frac != null) { Spacer(Modifier.height(5.dp)); Bar(frac.toFloat(), color, height = 5.dp) }
        }
    }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = FitType.section, color = th.text, maxLines = 1)
        Text(label, style = FitType.caption, color = th.textDim)
    }
}

private data class Series(val vals: List<Double?>, val target: Double?, val color: Color, val fmt: (Double) -> String)

private fun trendSeries(t: Trend, days: List<DayStats>, tg: DayTargets, th: com.myfit.tracker.ui.theme.FitTheme): Series = when (t) {
    Trend.STEPS -> Series(days.map { it.steps?.toDouble() }, tg.steps, th.steps) { Fmt.int(it) }
    Trend.SLEEP -> Series(days.map { it.sleepMin }, tg.sleepMin, th.sleep) { Fmt.duration(it.toLong()) }
    Trend.WATER -> Series(days.map { it.waterMl }, tg.waterMl, th.water) { "%.1f L".format(it / 1000) }
    Trend.KCAL -> Series(days.map { it.kcal }, tg.kcal, th.warning) { Fmt.int(it) }
    Trend.PROTEIN -> Series(days.map { it.protein }, tg.protein, th.protein) { "${Fmt.int(it)} g" }
    Trend.WEIGHT -> Series(days.map { it.weightKg }, null, th.fat) { "%.1f kg".format(it) }
}

enum class ChartKind { BARS, LINE }

/** Bars (or a line for weight) that grow in with a spring, with a dashed goal line. Empty days are left blank. */
@Composable
fun TrendChart(vals: List<Double?>, target: Double?, color: Color, kind: ChartKind, modifier: Modifier) {
    val th = LocalFitTheme.current
    val grow = remember(vals, kind) { Animatable(0f) }
    LaunchedEffect(vals, kind) { grow.animateTo(1f, Motion.gentle()) }
    Canvas(modifier) {
        val have = vals.filterNotNull()
        if (have.isEmpty()) {
            drawLine(th.text.copy(alpha = 0.1f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()); return@Canvas
        }
        val g = grow.value
        val lo = if (kind == ChartKind.LINE) have.min() * 0.995 else 0.0
        val hi = maxOf(have.max(), target ?: 0.0) * (if (kind == ChartKind.LINE) 1.005 else 1.08)
        val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
        fun y(v: Double) = (size.height - ((v - lo) / span * size.height)).toFloat()
        val n = vals.size
        val slot = size.width / n
        if (kind == ChartKind.BARS) {
            val bw = (slot * 0.62f).coerceAtMost(26.dp.toPx())
            vals.forEachIndexed { i, v ->
                val x = i * slot + (slot - bw) / 2
                if (v == null) { drawRoundRect(th.text.copy(alpha = 0.05f), Offset(x, size.height - 4.dp.toPx()), Size(bw, 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(bw / 2)); return@forEachIndexed }
                val top = size.height - (size.height - y(v)) * g
                val met = target != null && v >= target
                drawRoundRect(Brush.verticalGradient(listOf(color, color.copy(alpha = if (met) 0.75f else 0.35f)), top, size.height), Offset(x, top), Size(bw, size.height - top),
                    androidx.compose.ui.geometry.CornerRadius(minOf(bw / 2, 8.dp.toPx())))
            }
        } else {
            val pts = vals.mapIndexedNotNull { i, v -> v?.let { Offset(i * slot + slot / 2, y(it)) } }
            val path = androidx.compose.ui.graphics.Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            val clipW = size.width * g
            clipRect(right = clipW) {
                drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                pts.forEach { drawCircle(color, 3.5.dp.toPx(), it); drawCircle(th.bgBottom, 1.5.dp.toPx(), it) }
            }
        }
        target?.let { t ->
            val ty = y(t)
            drawLine(th.text.copy(alpha = 0.35f), Offset(0f, ty), Offset(size.width, ty), 1.2.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        }
    }
}

/** Steppers for the daily goals. Saves only what changed; past days keep the goal they had. */
@Composable
private fun GoalsEditor(c: AppContainer, t: DayTargets, onSaved: () -> Unit) {
    val th = LocalFitTheme.current
    val toaster = com.myfit.tracker.ui.components.LocalToaster.current
    val scope = rememberCoroutineScope()
    var steps by remember(t) { mutableStateOf(t.steps) }
    var water by remember(t) { mutableStateOf(t.waterMl) }
    var sleep by remember(t) { mutableStateOf(t.sleepMin) }
    var kcal by remember(t) { mutableStateOf(t.kcal ?: 2000.0) }
    var protein by remember(t) { mutableStateOf(t.protein ?: 100.0) }
    var weekly by remember(t) { mutableStateOf(t.weeklyWorkouts ?: 3.0) }
    GlassCard {
        Stepper(Duo.Footprints, "Steps", Fmt.int(steps), th.steps, { steps = (steps - 500).coerceAtLeast(1000.0) }, { steps = (steps + 500).coerceAtMost(40000.0) })
        Stepper(Duo.WaterDrop, "Water", "%.2f L".format(water / 1000), th.water, { water = (water - 250).coerceAtLeast(500.0) }, { water = (water + 250).coerceAtMost(6000.0) })
        Stepper(Duo.Bedtime, "Sleep", Fmt.duration(sleep.toLong()), th.sleep, { sleep = (sleep - 15).coerceAtLeast(240.0) }, { sleep = (sleep + 15).coerceAtMost(720.0) })
        Stepper(Duo.Flame, "Calories", "${Fmt.int(kcal)} ${EnergyUnit.label}", th.warning, { kcal = (kcal - 50).coerceAtLeast(1000.0) }, { kcal = (kcal + 50).coerceAtMost(6000.0) })
        Stepper(Duo.Egg, "Protein", "${Fmt.int(protein)} g", th.protein, { protein = (protein - 5).coerceAtLeast(20.0) }, { protein = (protein + 5).coerceAtMost(400.0) })
        Stepper(Duo.FitnessCenter, "Workouts / week", Fmt.int(weekly), th.accentBright, { weekly = (weekly - 1).coerceAtLeast(0.0) }, { weekly = (weekly + 1).coerceAtMost(14.0) })
        val changed = steps != t.steps || water != t.waterMl || sleep != t.sleepMin || kcal != (t.kcal ?: 2000.0) || protein != (t.protein ?: 100.0) || weekly != (t.weeklyWorkouts ?: 3.0)
        Spacer(Modifier.height(10.dp))
        AccentButton("Save goals", {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val r = c.profileRepo
                    if (steps != t.steps) r.setTarget(TargetType.STEPS, steps)
                    if (water != t.waterMl) r.setTarget(TargetType.WATER_ML, water)
                    if (sleep != t.sleepMin) r.setTarget(TargetType.SLEEP_MIN, sleep)
                    if (kcal != (t.kcal ?: 2000.0)) r.setTarget(TargetType.CALORIES, kcal)
                    if (protein != (t.protein ?: 100.0)) r.setTarget(TargetType.PROTEIN_G, protein)
                    if (weekly != (t.weeklyWorkouts ?: 3.0)) r.setTarget(TargetType.WEEKLY_WORKOUTS, weekly)
                }
                toaster.show("Goals saved — they apply from today")
                onSaved()
            }
        }, Modifier.fillMaxWidth(), icon = Duo.Check, enabled = changed, height = 50.dp)
    }
}

@Composable
private fun Stepper(icon: ImageVector, label: String, value: String, color: Color, minus: () -> Unit, plus: () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, color, 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = FitType.label, color = th.text, modifier = Modifier.weight(1f), maxLines = 1)
        StepBtn(Duo.Remove, minus)
        Text(value, style = FitType.label, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.width(92.dp), maxLines = 1)
        StepBtn(Duo.Add, plus)
    }
}

@Composable
private fun StepBtn(icon: ImageVector, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    Box(Modifier.size(34.dp).clip(CircleShape).background(th.text.copy(alpha = 0.08f)).clickableNoRipple { tick(); onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = th.text, modifier = Modifier.size(16.dp))
    }
}

// ---------------------------------------------------------------------- History

@Composable
private fun HistoryTab(c: AppContainer) {
    val th = LocalFitTheme.current
    val hist by DayPlans.history.collectAsState()
    var open by remember { mutableStateOf<LocalDate?>(null) }
    val data by produceState<Pair<Map<LocalDate, DayStats>, DayTargets>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                DayPlans.load(c.app)
                val today = Clock.today()
                DayPlans.stats(c, today.minusDays(29), today.minusDays(1)) to DayPlans.targets(c, today)
            }.getOrNull()
        }
    }
    val d = data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d == null) { item { Caption("Loading your last 30 days…") }; return@LazyColumn }
        val days = d.first.values.sortedByDescending { it.date }
        // 30-day score strip
        item {
            GlassCard {
                Text("LAST 30 DAYS", style = FitType.overline, color = th.textDim)
                Spacer(Modifier.height(10.dp))
                TrendChart(days.reversed().map { s -> DayPlans.score(s, d.second)?.toDouble() }, 85.0, th.accentBright, ChartKind.BARS, Modifier.fillMaxWidth().height(110.dp))
                Spacer(Modifier.height(6.dp))
                Caption("Daily score · dashed line = a great day (85+)")
            }
        }
        items(days.size, key = { days[it].date.toString() }) { idx ->
            val s = days[idx]
            val plan = hist[Clock.dateKey(s.date)]
            val score = DayPlans.score(s, d.second)
            val expanded = open == s.date
            GlassCard(onClick = { open = if (expanded) null else s.date }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing((score ?: 0) / 100f, 44.dp, 5.dp, scoreColor(score, th), th.text.copy(alpha = 0.1f)) {
                        Text(score?.toString() ?: "—", style = FitType.label, color = th.text)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())), style = FitType.section, color = th.text)
                        Text(listOfNotNull(
                            s.steps?.let { "${Fmt.int(it)} steps" }, s.sleepMin?.let { Fmt.duration(it.toLong()) + " sleep" },
                            s.kcal?.let { "${Fmt.int(it)} ${EnergyUnit.label}" }, if (s.workouts > 0) "${s.workouts} workout" + (if (s.workouts > 1) "s" else "") else null,
                        ).joinToString(" · ").ifEmpty { "Nothing recorded" }, style = FitType.caption, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (plan != null) {
                        val (done, total) = doneCount(plan, PlanLive.of(s))
                        Text("$done/$total", style = FitType.label, color = th.accentBright)
                    }
                }
                androidx.compose.animation.AnimatedVisibility(expanded,
                    enter = androidx.compose.animation.expandVertically(spring(0.9f, 420f)) + androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically(spring(1f, 520f)) + androidx.compose.animation.fadeOut()) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        if (plan == null) Caption("No plan saved for this day — plans are kept from the day you started using Plan my day.")
                        else {
                            Text(plan.headline, style = FitType.label, color = th.text)
                            Spacer(Modifier.height(6.dp))
                            plan.items.forEach { i ->
                                val p = progressOf(i, PlanLive.of(s))
                                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (p == null) metricIcon(i.metric) else if (p >= 0.999f) Duo.CheckCircle else Duo.RadioButtonUnchecked, null,
                                        tint = if (p != null && p >= 0.999f) th.success else th.textDim, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(i.title, style = FitType.caption, color = th.text, modifier = Modifier.weight(1f))
                                    if (p != null) Text("${(p * 100).toInt()}%", style = FitType.caption, color = th.textDim)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================== morning story

/** Shows the story once a day after 4 am, when nothing else is on screen. Re-checks when the app comes back. */
@Composable
fun PlanStoryHost(c: AppContainer, allowed: Boolean) {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner, allowed) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && allowed && !show && DayPlans.storyDue(ctx)) show = true
        }
        owner.lifecycle.addObserver(obs)
        if (allowed && DayPlans.storyDue(ctx)) show = true
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    // not on install day: there's no "yesterday" yet — the first story comes tomorrow morning
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        val created = withContext(Dispatchers.IO) { runCatching { c.profileRepo.profile.first()?.createdAt }.getOrNull() }
        if (created != null && java.time.Instant.ofEpochMilli(created).atZone(Clock.zone()).toLocalDate() == Clock.today()) { DayPlans.markStoryShown(ctx); show = false }
    }
    androidx.compose.animation.AnimatedVisibility(show,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) + androidx.compose.animation.scaleIn(spring(0.86f, 340f), initialScale = 1.06f),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(220)) + androidx.compose.animation.scaleOut(spring(1f, 500f), targetScale = 0.96f)) {
        PlanStory(c) { DayPlans.markStoryShown(ctx); show = false }
    }
}

@Composable
fun PlanStory(c: AppContainer, onClose: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val data by produceState<Triple<DayPlan, DayStats?, DayTargets>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { DayPlans.today(c) }.getOrNull() }
    }
    val profile by c.profileRepo.profile.collectAsState(initial = null)
    val pager = androidx.compose.foundation.pager.rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E1630), Color(0xFF1A1240), Color(0xFF08101E))))
            .clickableNoRipple { },
    ) {
        // soft aurora behind everything
        val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora")
        val drift by inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(9000), androidx.compose.animation.core.RepeatMode.Reverse), label = "d")
        Canvas(Modifier.fillMaxSize()) {
            val a = Offset(size.width * (0.2f + 0.3f * drift), size.height * 0.22f)
            val b = Offset(size.width * (0.85f - 0.25f * drift), size.height * 0.62f)
            drawCircle(Brush.radialGradient(listOf(th.accent.copy(alpha = 0.45f), Color.Transparent), a, size.width * 0.7f), size.width * 0.7f, a)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB86CFF).copy(alpha = 0.32f), Color.Transparent), b, size.width * 0.8f), size.width * 0.8f, b)
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // story progress bars
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                (0 until 3).forEach { i ->
                    val f by animateFloatAsState(if (pager.currentPage >= i) 1f else 0f, Motion.smooth(), label = "seg$i")
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Color.White))
                    }
                }
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickableNoRipple(onClose), contentAlignment = Alignment.Center) {
                    Icon(Duo.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            val d = data
            if (d == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Planning your day…", style = FitType.section, color = Color.White) }
            } else {
                androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                    // depth effect between pages
                    val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val a = kotlin.math.abs(off).coerceIn(0f, 1f)
                        alpha = 1f - a * 0.6f; val s = 1f - a * 0.06f; scaleX = s; scaleY = s
                    }.padding(horizontal = 20.dp)) {
                        when (page) {
                            0 -> StoryYesterday(profile?.name, d.second, d.third)
                            1 -> StoryPlan(d.first)
                            else -> StoryGoals(c, d.third, onClose)
                        }
                    }
                }
                if (pager.currentPage < 2) Box(Modifier.padding(20.dp)) {
                    AccentButton(if (pager.currentPage == 0) "See today's plan" else "Set my goals", {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = spring(0.9f, 300f)) }
                    }, Modifier.fillMaxWidth(), icon = Duo.ArrowForward)
                }
            }
        }
    }
}

/** Staggered entrance for story content, replayed each time the page is shown. */
@Composable
private fun Modifier.storyIn(i: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(80L + i * 70L); a.animateTo(1f, spring(0.82f, 260f)) }
    return graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1f - a.value) * 28.dp.toPx() }
}

@Composable
private fun StoryYesterday(name: String?, y: DayStats?, t: DayTargets) {
    val score = y?.let { DayPlans.score(it, t) }
    val hour = java.time.LocalTime.now().hour
    val hello = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
    Column(Modifier.fillMaxSize().verticalScrollCompat(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Text(hello + (name?.takeIf { it.isNotBlank() }?.let { ", ${it.substringBefore(' ')}" } ?: ""), style = FitType.title, color = Color.White, modifier = Modifier.storyIn(0))
        Text("Here's how yesterday went", style = FitType.body, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.storyIn(1))
        Spacer(Modifier.height(24.dp))
        Box(Modifier.storyIn(2)) {
            ScoreRing((score ?: 0) / 100f, 170.dp, 14.dp, Color(0xFF7CF0C8), Color.White.copy(alpha = 0.12f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shown by androidx.compose.animation.core.animateIntAsState(score ?: 0, androidx.compose.animation.core.tween(1100), label = "score")
                    Text(if (score == null) "—" else "$shown", style = FitType.display, color = Color.White)
                    Text("day score", style = FitType.caption, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        val rows = buildList {
            add(Triple("Steps", y?.steps?.let { Fmt.int(it) }, y?.steps?.let { it >= t.steps }))
            add(Triple("Sleep", y?.sleepMin?.let { Fmt.duration(it.toLong()) }, y?.sleepMin?.let { it >= t.sleepMin }))
            add(Triple("Water", y?.waterMl?.let { "%.1f L".format(it / 1000) }, y?.waterMl?.let { it >= t.waterMl }))
            if (t.kcal != null) add(Triple("Calories", y?.kcal?.let { Fmt.int(it) }, y?.kcal?.let { kotlin.math.abs(it - t.kcal) <= t.kcal * 0.1 }))
            if (t.protein != null) add(Triple("Protein", y?.protein?.let { "${Fmt.int(it)} g" }, y?.protein?.let { it >= t.protein }))
            add(Triple("Workout", y?.let { if (it.workouts > 0) "${it.trainMin} min" else "Rest" }, y?.let { it.workouts > 0 }))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f)).padding(16.dp)) {
            rows.forEachIndexed { i, (l, v, met) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp).storyIn(3 + i), verticalAlignment = Alignment.CenterVertically) {
                    Text(l, style = FitType.body, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
                    Text(v ?: "—", style = FitType.section, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Icon(if (met == true) Duo.CheckCircle else Duo.RadioButtonUnchecked, null, tint = if (met == true) Color(0xFF7CF0C8) else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                }
            }
        }
        if (y == null || score == null) {
            Spacer(Modifier.height(12.dp))
            Text("Nothing was recorded yesterday — today's plan uses your goals.", style = FitType.caption, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StoryPlan(p: DayPlan) {
    val th = LocalFitTheme.current
    Column(Modifier.fillMaxSize().verticalScrollCompat()) {
        Spacer(Modifier.height(18.dp))
        Text("TODAY'S PLAN", style = FitType.overline, color = Color(0xFF7CF0C8), modifier = Modifier.storyIn(0))
        Text(p.headline, style = FitType.title, color = Color.White, modifier = Modifier.storyIn(1))
        Text(p.why, style = FitType.caption, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.storyIn(1))
        Spacer(Modifier.height(16.dp))
        var n = 2
        PlanKind.entries.forEach { k ->
            val items = p.items.filter { it.kind == k }
            if (items.isEmpty()) return@forEach
            Text(kindLabel(k).uppercase(), style = FitType.overline, color = kindColor(k, th), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).storyIn(n++))
            items.forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).storyIn(n++).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(kindColor(k, th).copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        Icon(metricIcon(i.metric), null, tint = kindColor(k, th), modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(i.title, style = FitType.section, color = Color.White)
                        Text(i.detail, style = FitType.caption, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
        p.tip?.let {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.storyIn(n).clip(RoundedCornerShape(18.dp)).background(Color(0xFF7CF0C8).copy(alpha = 0.14f)).padding(12.dp)) {
                Icon(Duo.AutoAwesome, null, tint = Color(0xFF7CF0C8), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(it, style = FitType.body, color = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StoryGoals(c: AppContainer, t: DayTargets, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var steps by remember { mutableStateOf(t.steps) }
    var water by remember { mutableStateOf(t.waterMl) }
    var sleep by remember { mutableStateOf(t.sleepMin) }
    var kcal by remember { mutableStateOf(t.kcal) }
    var daily by remember { mutableStateOf(DayPlans.storyEnabled(ctx)) }
    Column(Modifier.fillMaxSize().verticalScrollCompat()) {
        Spacer(Modifier.height(18.dp))
        Text("YOUR GOALS", style = FitType.overline, color = Color(0xFF7CF0C8), modifier = Modifier.storyIn(0))
        Text("Happy with today's targets?", style = FitType.title, color = Color.White, modifier = Modifier.storyIn(1))
        Text("Nudge them if today needs to be easier or harder. Past days keep their old goals.", style = FitType.caption, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.storyIn(1))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().storyIn(2).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f)).padding(14.dp)) {
            StoryStepper("Steps", Fmt.int(steps), { steps = (steps - 500).coerceAtLeast(1000.0) }) { steps = (steps + 500).coerceAtMost(40000.0) }
            StoryStepper("Water", "%.2f L".format(water / 1000), { water = (water - 250).coerceAtLeast(500.0) }) { water = (water + 250).coerceAtMost(6000.0) }
            StoryStepper("Sleep", Fmt.duration(sleep.toLong()), { sleep = (sleep - 15).coerceAtLeast(240.0) }) { sleep = (sleep + 15).coerceAtMost(720.0) }
            kcal?.let { k -> StoryStepper("Calories", Fmt.int(k), { kcal = (k - 50).coerceAtLeast(1000.0) }) { kcal = (k + 50).coerceAtMost(6000.0) } }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().storyIn(3).clip(RoundedCornerShape(18.dp)).clickableNoRipple { daily = !daily; DayPlans.setStoryEnabled(ctx, daily) }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (daily) Duo.CheckCircle else Duo.RadioButtonUnchecked, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Show this every morning", style = FitType.body, color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        AccentButton("Start my day", {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val r = c.profileRepo
                    if (steps != t.steps) r.setTarget(TargetType.STEPS, steps)
                    if (water != t.waterMl) r.setTarget(TargetType.WATER_ML, water)
                    if (sleep != t.sleepMin) r.setTarget(TargetType.SLEEP_MIN, sleep)
                    kcal?.let { if (it != t.kcal) r.setTarget(TargetType.CALORIES, it) }
                    if (steps != t.steps || water != t.waterMl || sleep != t.sleepMin || kcal != t.kcal) runCatching { DayPlans.today(c, force = true) }
                }
                onClose()
            }
        }, Modifier.fillMaxWidth().storyIn(4), icon = Duo.PlayArrow)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StoryStepper(label: String, value: String, minus: () -> Unit, plus: () -> Unit) {
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.body, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickableNoRipple { tick(); minus() }, contentAlignment = Alignment.Center) {
            Icon(Duo.Remove, "Less", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(value, style = FitType.section, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.width(96.dp), maxLines = 1)
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickableNoRipple { tick(); plus() }, contentAlignment = Alignment.Center) {
            Icon(Duo.Add, "More", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(androidx.compose.foundation.rememberScrollState())

// ====================================================================== Me: daily report tile

/** Me → "Your daily report": yesterday's score, a 7-day score sparkline and today's plan progress. */
@Composable
fun DailyReportTile(c: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val plan = rememberTodayPlan(c)
    val live = rememberTodayLive(c)
    val week by produceState<Pair<List<Int?>, DayTargets>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val today = Clock.today()
                val t = DayPlans.targets(c, today)
                val st = DayPlans.stats(c, today.minusDays(7), today.minusDays(1))
                st.values.sortedBy { it.date }.map { DayPlans.score(it, t) } to t
            }.getOrNull()
        }
    }
    val scores = week?.first.orEmpty()
    val y = scores.lastOrNull()
    GlassCard(onClick = { nav.push(Overlay.DayPlan(1)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing((y ?: 0) / 100f, 76.dp, 8.dp, scoreColor(y, th), th.text.copy(alpha = 0.1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(y?.toString() ?: "—", style = FitType.section, color = th.text)
                    Text("yesterday", style = FitType.caption, color = th.textDim)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("DAILY REPORT", style = FitType.overline, color = th.accentBright)
                Text(plan?.headline ?: "Your day at a glance", style = FitType.section, color = th.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                TrendChart(scores.map { it?.toDouble() }, 85.0, th.accentBright, ChartKind.BARS, Modifier.fillMaxWidth().height(34.dp))
                Caption("Last 7 days")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (done, total) = plan?.let { doneCount(it, live) } ?: (0 to 0)
            ReportChip(Duo.TrackChanges, "Today $done/$total", th.accentBright, Modifier.weight(1f)) { nav.push(Overlay.DayPlan(0)) }
            ReportChip(Duo.Insights, "Trends & goals", th.success, Modifier.weight(1f)) { nav.push(Overlay.DayPlan(1)) }
            ReportChip(Duo.History, "History", th.sleep, Modifier.weight(1f)) { nav.push(Overlay.DayPlan(2)) }
        }
    }
}

@Composable
private fun ReportChip(icon: ImageVector, text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)).clickableNoRipple(onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = FitType.caption, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Settings → Notifications: the morning story switch. */
@Composable
fun MorningPlanToggle() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(DayPlans.storyEnabled(ctx)) }
    Row(Modifier.fillMaxWidth().clickableNoRipple { on = !on; DayPlans.setStoryEnabled(ctx, on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(Duo.TrackChanges, th.accentBright, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Morning plan", style = FitType.label, color = th.text)
            Caption("Yesterday's recap and today's plan on your first open of the day")
        }
        androidx.compose.material3.Switch(on, { on = it; DayPlans.setStoryEnabled(ctx, it) })
    }
}
