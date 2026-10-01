package com.myfit.tracker.ui.dashboard

import com.myfit.tracker.ui.theme.Duo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.prefs.DashCard
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Units
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassProgressBar
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.Metric
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.Sparkline
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DashboardScreen(state: DashState, container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int) {
    val settings = LocalSettings.current
    val cards = settings.dashCards
    val toaster = com.myfit.tracker.ui.components.LocalToaster.current
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // local copy so cards can move live while you drag; saved when you let go
    var order by androidx.compose.runtime.remember(settings.dashOrder) { androidx.compose.runtime.mutableStateOf(settings.dashOrder) }
    var dragKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<DashCard?>(null) }
    var dragDy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var hinted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    fun keyOf(c: DashCard) = "card_" + c.name
    fun onDrag(dy: Float) {
        val dk = dragKey ?: return
        dragDy += dy
        val info = listState.layoutInfo
        val cur = info.visibleItemsInfo.firstOrNull { it.key == keyOf(dk) } ?: return
        // auto-scroll near the top / bottom edge
        val top = cur.offset + dragDy
        val bottom = top + cur.size
        val edge = 120f
        val scroll = when {
            top < info.viewportStartOffset + edge -> -18f
            bottom > info.viewportEndOffset - edge -> 18f
            else -> 0f
        }
        if (scroll != 0f) { val used = listState.dispatchRawDelta(scroll); dragDy += used }
        val center = cur.offset + dragDy + cur.size / 2f
        val target = info.visibleItemsInfo.firstOrNull { t ->
            val k = t.key as? String ?: return@firstOrNull false
            k.startsWith("card_") && k != keyOf(dk) && center > t.offset && center < t.offset + t.size
        } ?: return
        val tc = runCatching { DashCard.valueOf((target.key as String).removePrefix("card_")) }.getOrNull() ?: return
        val list = order.toMutableList()
        val from = list.indexOf(dk); val to = list.indexOf(tc)
        if (from < 0 || to < 0) return
        list.removeAt(from); list.add(to, dk)
        order = list
        val newOffset = if (to > from) target.offset + target.size - cur.size else target.offset
        dragDy -= (newOffset - cur.offset)
        tick()
    }

    @Composable
    fun Modifier.movable(c: DashCard): Modifier {
        val dragging = dragKey == c
        return this
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                if (dragging) { translationY = dragDy; scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 28f; shape = RoundedCornerShape(28.dp); clip = false }
            }
            .pointerInput(c) {
                detectLongPressDrag(
                    onStart = {
                        tick(); dragKey = c; dragDy = 0f
                        if (!hinted) { hinted = true; toaster.show("Drag to move the card · let go to drop") }
                    },
                    onDrag = { onDrag(it) },
                    onEnd = {
                        if (dragKey != null) {
                            dragKey = null; dragDy = 0f
                            val o = order
                            container.write { container.settings.setDashOrder(o) }
                        }
                    },
                )
            }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "greeting") { Box(Modifier.statusBarsPadding()) { Greeting(state) } }
        order.forEach { c ->
            if (c !in cards) return@forEach
            if (c == DashCard.PIP && !settings.pipEnabled) return@forEach
            item(key = keyOf(c)) {
                val place: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset>? =
                    if (dragKey == c) null else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)
                Box(Modifier.animateItem(placementSpec = place).movable(c)) {
                    when (c) {
                        DashCard.PIP -> PipCard(state)
                        DashCard.SNAP -> SnapHeroCard()
                        DashCard.WORKOUT -> WorkoutCard(state.workout, container)
                        DashCard.RINGS -> RingsCard(state, open)
                        DashCard.NUTRITION -> NutritionCard(container)
                        DashCard.BODY -> BodyCard(state) { open(Sheet.Weight()) }
                        DashCard.HYDRATION -> HydrationCard(state, container, open)
                        DashCard.RECOVERY -> RecoveryCard(state, open)
                        DashCard.STEPS -> { val nav = com.myfit.tracker.ui.nav.LocalNav.current; StepsCard(state) { nav.push(com.myfit.tracker.ui.nav.Overlay.Activity) } }
                        DashCard.CHECKIN -> CheckInCard(state) { open(Sheet.CheckIn()) }
                        DashCard.GOALS -> GoalsCard(state)
                    }
                }
            }
        }
        item(key = "arrange") {
            val nav = com.myfit.tracker.ui.nav.LocalNav.current
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Caption("Tip: long-press any card and drag to move it.")
                Spacer(Modifier.height(8.dp))
                com.myfit.tracker.ui.theme.GlassButton("Show / hide cards", { nav.push(com.myfit.tracker.ui.nav.Overlay.ArrangeDash) }, icon = Duo.Tune, height = 44.dp)
            }
        }
    }
}

/**
 * Long-press then drag, detected in the Initial pass so it works on top of cards full of buttons.
 * A normal tap or scroll (movement before the long-press timeout) is left untouched.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectLongPressDrag(
    onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                if (!ch.pressed || ch.isConsumed) return@withTimeoutOrNull true
                if ((ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        }
        if (early != null) return@awaitEachGesture
        onStart()
        try {
            while (true) {
                val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                val dy = ch.position.y - ch.previousPosition.y
                ch.consume()
                if (!ch.pressed) break
                onDrag(dy)
            }
        } finally {
            onEnd()
        }
    }
}

private fun greetingFor(t: LocalTime) = when (t.hour) {
    in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night"
}

@Composable
private fun Greeting(s: DashState) {
    val th = LocalFitTheme.current
    val name = s.profile?.name?.substringBefore(' ') ?: ""
    Row(Modifier.fillMaxWidth().padding(top = com.myfit.tracker.ui.components.TopBarSpace, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${greetingFor(LocalTime.now())}, $name", style = FitType.title, color = th.text)
            Caption(s.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)))
        }
    }
}

// ------------------------------------------------------------------ Pip

private fun pipLine(s: DashState, variant: Int): Pair<PipMood, String> {
    val now = LocalTime.now()
    val p = s.profile
    val nightMin = p?.sleepTimeMin ?: (23 * 60)
    val wakeMin = p?.wakeTimeMin ?: (7 * 60)
    val mNow = now.hour * 60 + now.minute
    val late = if (nightMin > wakeMin) (mNow >= nightMin - 30 || mNow < wakeMin) else (mNow >= nightMin - 30 && mNow < wakeMin)
    val water = s.waterMl; val tw = s.waterTarget
    val nothingToday = s.day.water.isEmpty() && s.day.weight.isEmpty() && s.day.sleep.isEmpty() && s.day.activity.isEmpty() && s.day.checkIns.isEmpty() && s.day.notes.isEmpty()
    val lines = buildList {
        if (late) add(PipMood.SLEEPY to if (s.checkIn == null) "It's getting late. A quick check-in, then rest?" else "Check-in done. Time to recharge — see you tomorrow.")
        if (nothingToday) add(PipMood.WAVE to "Nothing logged yet today. Tap + and I'll keep count.")
        if (water != null && tw != null && water >= tw) add(PipMood.PROUD to "Water target reached — ${Fmt.num(water / 1000, 2)} L logged today!")
        if (tw != null && (water ?: 0.0) < tw * 0.5 && now.hour >= 14) add(PipMood.CONCERNED to "Water so far: ${Fmt.num((water ?: 0.0) / 1000, 2)} of ${Fmt.num(tw / 1000, 2)} L. A glass now?")
        val c7 = s.change7
        if (c7 != null) add(PipMood.THINKING to "Your 7-day weight average moved ${Fmt.signed(c7.delta)} kg vs the week before.")
        if (s.steps != null && s.stepTarget != null && s.steps >= s.stepTarget) add(PipMood.EXCITED to "${Fmt.int(s.steps)} steps — step goal smashed!")
        if (s.sleepMin != null) add(PipMood.HAPPY to "You logged ${Fmt.duration(s.sleepMin)} of sleep last night.")
        add(PipMood.HAPPY to "Tap me any time. I only quote numbers you've logged.")
    }
    return lines[variant % lines.size]
}

@Composable
private fun PipCard(s: DashState) {
    val th = LocalFitTheme.current
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    var variant by remember { mutableIntStateOf(0) }
    val (mood, line) = pipLine(s, variant)
    Glass(Modifier.fillMaxWidth().height(170.dp), onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.PipChat) }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pip(mood, size = 128.dp, onTap = { variant++ })
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("PIP", style = FitType.overline, color = th.accentBright)
                Spacer(Modifier.height(4.dp))
                Text(line, style = FitType.body, color = th.text)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .drawBehind {
                            drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent)))
                            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, size.height * 0.5f))
                        }
                        .clickableNoRipple { nav.push(com.myfit.tracker.ui.nav.Overlay.PipChat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Duo.ChatBubble, null, tint = th.onAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chat with Pip", style = FitType.label, color = th.onAccent)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Rings

@Composable
private fun RingsCard(s: DashState, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    GlassCard {
        Text("TODAY'S PROGRESS", style = FitType.overline, color = th.textDim)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(s.waterMl?.let { w -> s.waterTarget?.let { (w / it).toFloat() } }, th.water, size = 132.dp, stroke = 12.dp)
                ProgressRing(s.steps?.let { st -> s.stepTarget?.let { (st / it).toFloat() } }, th.steps, size = 100.dp, stroke = 12.dp)
                ProgressRing(s.sleepMin?.let { m -> s.sleepTarget?.let { (m / it).toFloat() } }, th.sleep, size = 68.dp, stroke = 12.dp)
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RingLegend(th.water, "Water",
                    s.waterMl?.let { Fmt.volume(it, units.volume) } ?: "—",
                    s.waterTarget?.let { "of ${Fmt.volume(it, units.volume)}" }) { open(Sheet.Water()) }
                RingLegend(th.steps, "Steps", s.steps?.let { Fmt.int(it) } ?: "—", s.stepTarget?.let { "of ${Fmt.int(it)}" }) { open(Sheet.Steps()) }
                RingLegend(th.sleep, "Sleep", s.sleepMin?.let { Fmt.duration(it) } ?: "—", s.sleepTarget?.let { "of ${Fmt.duration(it.toLong())}" }) { open(Sheet.Sleep()) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption("Dashed ring = nothing logged yet (not zero).")
    }
}

@Composable
private fun RingLegend(color: Color, label: String, value: String, of: String?, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickableNoRipple(onClick)) {
        Box(Modifier.size(10.dp).clip(CircleShape)) { Canvas(Modifier.fillMaxSize()) { drawCircle(color) } }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = FitType.caption, color = th.textDim)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = FitType.section, color = th.text)
                if (of != null) { Spacer(Modifier.width(4.dp)); Text(of, style = FitType.caption, color = th.textDim) }
            }
        }
    }
}

// ------------------------------------------------------------------ Body

@Composable
private fun BodyCard(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units.weight
    GlassCard(onClick = onClick) {
        CardHeader(Duo.MonitorWeight, "Body weight", th.accentBright) {
            if (s.latestWeight != null) DataBadge(DataKind.RECORDED)
        }
        Spacer(Modifier.height(14.dp))
        val lw = s.latestWeight
        if (lw == null) {
            Caption("No weigh-ins yet. Tap to log your first.")
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Caption(if (lw.localDate == Clock.dateKey(s.today)) "Latest · today" else "Latest · ${lw.localDate}")
                Metric(Fmt.trim(Units.kgTo(lw.weightKg, u), 1), u.label, FitType.display)
                if (s.day.weight.size > 1 && s.todayWeightMean != null) Caption("${s.day.weight.size} weigh-ins today · mean ${Fmt.weight(s.todayWeightMean, u)}")
            }
            Sparkline(s.weightSpark.map { it?.let { kg -> Units.kgTo(kg, u) } }, th.accentBright, Modifier.width(120.dp).height(54.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("7-day avg", s.avg7.value?.let { Fmt.weight(it, u) } ?: "—", "${s.avg7.daysWithData}/7 days", Modifier.weight(1f))
            MiniStat("vs prev 7d", s.change7?.let { "${Fmt.signed(Units.kgTo(it.delta, u))} ${u.label}" } ?: "Not enough data", "7-day avgs", Modifier.weight(1f))
            MiniStat("vs 30d ago", s.change30?.let { "${Fmt.signed(Units.kgTo(it.delta, u))} ${u.label}" } ?: "Not enough data", "7-day avgs", Modifier.weight(1f))
        }
        s.profile?.targetWeightKg?.let { t ->
            Spacer(Modifier.height(12.dp))
            val ref = s.avg7.value ?: lw.weightKg
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Duo.Flag, null, tint = th.textDim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Caption("Target ${Fmt.weight(t, u)} · ${Fmt.signed(Units.kgTo(t - ref, u))} ${u.label} from your ${if (s.avg7.value != null) "7-day average" else "latest weigh-in"}")
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, sub: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Glass(modifier, shape = RoundedCornerShape(18.dp), tint = if (th.isLight) Color(0x66FFFFFF) else Color(0x14FFFFFF)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = FitType.caption, color = th.textDim)
            Spacer(Modifier.height(4.dp))
            Text(value, style = FitType.label, color = th.text)
            Text(sub, style = FitType.caption.copy(fontSize = FitType.overline.fontSize), color = th.textFaint)
        }
    }
}

// ------------------------------------------------------------------ Hydration

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HydrationCard(s: DashState, c: AppContainer, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    GlassCard(onClick = { open(Sheet.Water()) }) {
        CardHeader(Duo.WaterDrop, "Hydration", th.water) {
            Caption("${s.day.water.size} ${if (s.day.water.size == 1) "entry" else "entries"}")
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaterGlass(s.waterMl?.let { w -> s.waterTarget?.let { (w / it).toFloat() } } ?: 0f, th.water, Modifier.size(width = 70.dp, height = 110.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Metric(s.waterMl?.let { Fmt.volume(it, units.volume).substringBefore(' ') } ?: "—", s.waterMl?.let { units.volume.label })
                s.waterTarget?.let { t ->
                    Caption("of ${Fmt.volume(t, units.volume)} target")
                    val rem = t - (s.waterMl ?: 0.0)
                    Caption(if (rem > 0) "${Fmt.volume(rem, units.volume)} to go" else "Target reached", color = if (rem <= 0) th.success else null)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250.0, 500.0).forEach { ml ->
                        GlassChip("+${Fmt.trim(ml, 0)} ml", false, {
                            c.write {
                                val id = c.logRepo.addWater(ml)
                                toaster.show("Added ${Fmt.trim(ml, 0)} ml of water", "Undo") { c.write { c.logRepo.deleteWater(id) } }
                            }
                        }, icon = Duo.WaterDrop)
                    }
                }
            }
        }
        // today's entries — remove one logged by mistake
        val entries = s.day.water.sortedByDescending { it.loggedAt }
        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY", style = FitType.overline, color = th.textDim, modifier = Modifier.weight(1f))
                Text("Undo last", style = FitType.label, color = th.water, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickableNoRipple {
                    val e = entries.first()
                    c.write { c.logRepo.deleteWater(e.id) }
                    toaster.show("Removed ${Fmt.volume(e.amountMl, units.volume)}", "Undo") { c.write { c.logRepo.addWater(e.amountMl, e.loggedAt) } }
                }.padding(horizontal = 6.dp, vertical = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entries.take(12).forEach { e ->
                    val lt = java.time.Instant.ofEpochMilli(e.loggedAt).atZone(runCatching { java.time.ZoneId.of(e.zoneId) }.getOrDefault(java.time.ZoneId.systemDefault())).toLocalTime()
                    Glass(Modifier.height(32.dp), shape = RoundedCornerShape(16.dp), tint = th.water.copy(alpha = 0.16f)) {
                        Row(Modifier.fillMaxHeight().padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${Fmt.volume(e.amountMl, units.volume)} · ${Fmt.clock(lt.hour * 60 + lt.minute)}", style = FitType.caption, color = th.text)
                            Spacer(Modifier.width(4.dp))
                            Box(
                                Modifier.size(24.dp).clip(CircleShape).clickableNoRipple {
                                    c.write { c.logRepo.deleteWater(e.id) }
                                    toaster.show("Removed ${Fmt.volume(e.amountMl, units.volume)}", "Undo") { c.write { c.logRepo.addWater(e.amountMl, e.loggedAt) } }
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Duo.Close, "Remove", tint = th.textDim, modifier = Modifier.size(15.dp)) }
                        }
                    }
                }
            }
            if (entries.size > 12) Caption("+${entries.size - 12} more in the Log tab")
        }
    }
}

/** Animated glass of water; the level is the real fraction of today's target. */
@Composable
private fun WaterGlass(fraction: Float, color: Color, modifier: Modifier) {
    val th = LocalFitTheme.current
    val level by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1200), label = "water")
    // the wave rides the shared backdrop clock (no extra frames; still in Battery saver)
    val clock = com.myfit.tracker.ui.theme.LocalBackdrop.current.time
    Canvas(modifier) {
        val ph = (clock.floatValue * 2.85f) % (2 * PI).toFloat()
        val w = size.width; val h = size.height
        val glass = Path().apply {
            moveTo(w * 0.08f, 0f); lineTo(w * 0.92f, 0f); lineTo(w * 0.8f, h); lineTo(w * 0.2f, h); close()
        }
        drawPath(glass, if (th.isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.06f))
        clipPath(glass) {
            val top = h * (1f - level)
            if (level > 0f) {
                val wave = Path().apply {
                    moveTo(0f, top)
                    var x = 0f
                    while (x <= w) { lineTo(x, top + sin(x / w * 2 * PI.toFloat() * 1.5f + ph) * h * 0.025f); x += w / 20f }
                    lineTo(w, h); lineTo(0f, h); close()
                }
                drawPath(wave, Brush.verticalGradient(listOf(color.copy(alpha = 0.65f), color), top, h))
                // bubbles
                for (i in 0..4) {
                    val bx = w * (0.3f + 0.1f * i)
                    val by = h - ((ph / (2 * PI.toFloat()) + i * 0.2f) % 1f) * (h - top)
                    drawCircle(Color.White.copy(alpha = 0.35f), 2.5f + i % 2, Offset(bx, by))
                }
            }
            drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.White.copy(alpha = 0.1f))), size = Size(w, h))
        }
        drawPath(glass, Color.White.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
    }
}

// ------------------------------------------------------------------ Recovery

@Composable
private fun RecoveryCard(s: DashState, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    GlassCard(onClick = { open(Sheet.Sleep()) }) {
        CardHeader(Duo.Bedtime, "Sleep & recovery", th.sleep)
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Caption("Last night")
                Metric(s.sleepMin?.let { Fmt.duration(it) } ?: "—")
                s.sleepQuality?.let { Caption("Quality $it/10") }
                s.sleepSource?.let { Caption(it, color = th.textFaint) }
                s.health.sleep?.takeIf { s.sleepSource != "Logged manually" }?.let { sl ->
                    if (sl.deepMin != null) Caption("Deep ${sl.deepMin}m · REM ${sl.remMin ?: 0}m · Light ${sl.lightMin ?: 0}m")
                }
            }
            Column(Modifier.weight(1f)) {
                Caption("7-day average")
                Metric(s.sleepAvg7.value?.let { Fmt.duration(it.toLong()) } ?: "—")
                Caption("${s.sleepAvg7.daysWithData}/7 nights recorded")
            }
        }
        s.sleepTarget?.let { t ->
            Spacer(Modifier.height(12.dp))
            GlassProgressBar(s.sleepMin?.let { (it / t).toFloat() }, th.sleep)
            Spacer(Modifier.height(4.dp))
            Caption("Target ${Fmt.duration(t.toLong())}")
        }
    }
}

// ------------------------------------------------------------------ Steps

@Composable
private fun StepsCard(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val d = s.health.daily
    GlassCard(onClick = onClick) {
        CardHeader(Duo.DirectionsWalk, "Activity & heart", th.steps) {
            s.stepsSource?.let { Caption(it) }
        }
        Spacer(Modifier.height(12.dp))
        Metric(s.steps?.let { Fmt.int(it) } ?: "—", s.stepTarget?.let { "/ ${Fmt.int(it)} steps" })
        Spacer(Modifier.height(10.dp))
        GlassProgressBar(s.steps?.let { st -> s.stepTarget?.let { (st / it).toFloat() } }, th.steps)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Caption("Distance"); Text(s.distanceM?.let { Fmt.distance(it, units.distance) } ?: "—", style = FitType.section, color = th.text) }
            Column(Modifier.weight(1f)) { Caption("Active kcal"); Text(d?.activeKcal?.let { Fmt.int(it.toLong()) } ?: "—", style = FitType.section, color = th.text) }
            Column(Modifier.weight(1f)) { Caption("Floors"); Text(d?.floors?.let { Fmt.trim(it, 0) } ?: "—", style = FitType.section, color = th.text) }
            Column(Modifier.weight(1f)) { Caption("Resting HR"); Text(d?.restingHr?.let { "$it bpm" } ?: "—", style = FitType.section, color = th.text) }
        }
        if (s.health.sessions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Caption("${s.health.sessions.size} activit${if (s.health.sessions.size == 1) "y" else "ies"} detected today: " +
                s.health.sessions.joinToString(", ") { it.title?.takeIf { t -> t.isNotBlank() } ?: com.myfit.tracker.health.HealthSync.exerciseName(it.exerciseType) }, color = th.textDim)
        }
        if (d?.activeKcal != null) { Spacer(Modifier.height(4.dp)); Caption("Calories are estimates from Samsung Health.", color = th.textFaint) }
        if (s.stepsSource == null) { Spacer(Modifier.height(6.dp)); Caption("Tap to connect Samsung Health & your watch for automatic tracking.", color = th.accentBright) }
    }
}

// ------------------------------------------------------------------ Check-in

private val faceFor = listOf("😫", "😣", "😟", "😕", "😐", "🙂", "😊", "😄", "😁", "🤩")

@Composable
private fun CheckInCard(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val ci = s.checkIn
    GlassCard(onClick = onClick) {
        CardHeader(Duo.Mood, "Daily check-in", th.warning)
        Spacer(Modifier.height(12.dp))
        if (ci == null) {
            Caption("Not done yet today. Takes 20 seconds — energy, mood, stress, soreness.")
            return@GlassCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Energy" to ci.energy, "Mood" to ci.mood, "Stress" to ci.stress, "Sore" to ci.soreness, "Drive" to ci.motivation).forEach { (l, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val inverted = l == "Stress" || l == "Sore"
                    Text(v?.let { faceFor[(if (inverted) 11 - it else it) - 1] } ?: "·", style = FitType.title, textAlign = TextAlign.Center)
                    Text(v?.let { "$it" } ?: "—", style = FitType.label, color = th.text)
                    Caption(l)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Goals

@Composable
private fun GoalsCard(s: DashState) {
    val th = LocalFitTheme.current
    val met = s.goals.count { it.met == true }
    GlassCard {
        CardHeader(Duo.CheckCircle, "Today's goals", th.success) {
            Text("$met/${s.goals.size}", style = FitType.section, color = th.text)
        }
        Spacer(Modifier.height(10.dp))
        s.goals.forEach { g ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (g.met == true) Duo.CheckCircle else Duo.RadioButtonUnchecked, null,
                    tint = if (g.met == true) th.success else th.textFaint, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(g.label, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                Caption(g.detail)
            }
        }
    }
}

@Composable
fun DashSection(text: String) = SectionTitle(text)

@Composable
private fun NutritionCard(c: AppContainer) {
    val th = LocalFitTheme.current
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    val today = Clock.today()
    val items by androidx.compose.runtime.remember(today) { c.nutritionRepo.itemsOn(today) }.collectAsState(initial = emptyList())
    val targets by c.profileRepo.targets.collectAsState(initial = emptyList())
    val t = com.myfit.tracker.ui.food.totalsOf(items)
    val key = Clock.dateKey(today)
    GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Food(key)) }) {
        CardHeader(Duo.ForkKnife, "Food", th.protein) {
            Caption(if (items.isEmpty()) "Nothing logged yet" else "${items.size} ${if (items.size == 1) "item" else "items"}")
        }
        Spacer(Modifier.height(12.dp))
        com.myfit.tracker.ui.food.MacroSummary(
            t,
            com.myfit.tracker.domain.Targets.on(targets, com.myfit.tracker.data.db.TargetType.CALORIES, today),
            com.myfit.tracker.domain.Targets.on(targets, com.myfit.tracker.data.db.TargetType.PROTEIN_G, today),
            com.myfit.tracker.domain.Targets.on(targets, com.myfit.tracker.data.db.TargetType.CARBS_G, today),
            com.myfit.tracker.domain.Targets.on(targets, com.myfit.tracker.data.db.TargetType.FAT_G, today),
            ringSize = 104,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val meal = com.myfit.tracker.ui.food.mealForNow()
            com.myfit.tracker.ui.theme.GlassButton("Snap meal", { nav.push(com.myfit.tracker.ui.nav.Overlay.FoodPhoto(meal, key)) }, Modifier.weight(1f), icon = Duo.Camera, height = 44.dp)
            com.myfit.tracker.ui.theme.GlassButton("Log food", { nav.push(com.myfit.tracker.ui.nav.Overlay.FoodAdd(meal, key, 0)) }, Modifier.weight(1f), icon = Duo.ForkKnife, height = 44.dp)
        }
    }
}

/** The headline feature: a big, friendly entry point to the food camera. */
@Composable
private fun SnapHeroCard() {
    val th = LocalFitTheme.current
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pip = androidx.compose.runtime.remember {
        runCatching { ctx.assets.open("pip/still_fuel.webp").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
    }
    val go = { nav.push(com.myfit.tracker.ui.nav.Overlay.FoodPhoto(com.myfit.tracker.ui.food.mealForNow(), Clock.dateKey(Clock.today()))) }
    Glass(Modifier.fillMaxWidth(), onClick = go) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(th.accent.copy(alpha = 0.30f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.5f), radius = size.height * 0.9f,
            ))
        }
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI CALORIE SCANNER", style = FitType.overline, color = th.accentBright)
                Spacer(Modifier.height(4.dp))
                Text("Snap a meal", style = FitType.title, color = th.text)
                Spacer(Modifier.height(4.dp))
                Caption("Point at your plate — Pip names each dish and counts calories & macros.")
                Spacer(Modifier.height(14.dp))
                com.myfit.tracker.ui.theme.AccentButton("Open camera", go, icon = Duo.Camera, height = 44.dp)
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize()) {
                    // viewfinder corners framing Pip
                    val arm = size.width * 0.18f; val sw = 3.dp.toPx(); val col = th.accentBright; val i = sw
                    listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (fx, fy) ->
                        val x = if (fx == 0f) i else size.width - i; val y = if (fy == 0f) i else size.height - i
                        val dx = if (fx == 0f) arm else -arm; val dy = if (fy == 0f) arm else -arm
                        drawLine(col, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx, y), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                        drawLine(col, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                if (pip != null) androidx.compose.foundation.Image(pip, null, Modifier.size(104.dp))
            }
        }
    }
}
