package com.myfit.tracker.ui.dashboard

import com.myfit.tracker.ui.theme.Duo

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.prefs.DashCard
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cards with little data sit side by side (one grid column); the rest span the full width. */
internal val halfCards = setOf(
    DashCard.HYDRATION, DashCard.STEPS, DashCard.RECOVERY, DashCard.CHECKIN, DashCard.BODY,
    DashCard.GOALS, DashCard.VITALS, DashCard.MIND, DashCard.CYCLE, DashCard.GLUCOSE,
)

@Composable
fun DashboardScreen(state: DashState, container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int) {
    val settings = LocalSettings.current
    val cards = settings.dashCards
    val toaster = com.myfit.tracker.ui.components.LocalToaster.current
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    val gridState = rememberLazyGridState()
    // local copy so cards can move live while you drag; saved when you let go
    var order by remember { mutableStateOf(settings.dashOrder) }
    var dragKey by remember { mutableStateOf<DashCard?>(null) }
    LaunchedEffect(settings.dashOrder) { if (dragKey == null) order = settings.dashOrder }
    // Drag model: the dragged card should sit at `dragStart + dragTotal` (viewport coordinates, the
    // top-left of the card under your finger). Its translation is computed every frame from the
    // current layout (target − real layout offset), so swaps and auto-scroll never need corrections.
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    var hinted by remember { mutableStateOf(false) }
    // swap guards (plain holders: they never need to recompose anything)
    val lastSwap = remember { arrayOfNulls<DashCard>(1) }
    val lastSwapAt = remember { longArrayOf(0L) }

    fun keyOf(c: DashCard) = "card_" + c.name
    fun infoOf(c: DashCard): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == keyOf(c) }
    fun boundsOf(i: LazyGridItemInfo) = Rect(i.offset.x.toFloat(), i.offset.y.toFloat(), (i.offset.x + i.size.width).toFloat(), (i.offset.y + i.size.height).toFloat())

    fun onDrag(d: Offset) {
        val dk = dragKey ?: return
        dragTotal += d
        val info = gridState.layoutInfo
        val cur = infoOf(dk) ?: return
        val pos = dragStart + dragTotal
        // auto-scroll near the top / bottom edge (the card stays under your finger: its target is in viewport space)
        val top = pos.y
        val bottom = top + cur.size.height
        val edge = 120f
        val scroll = when {
            top < info.viewportStartOffset + edge -> -18f
            bottom > info.viewportEndOffset - edge -> 18f
            else -> 0f
        }
        if (scroll != 0f) gridState.dispatchRawDelta(scroll)
        val center = Offset(pos.x + cur.size.width / 2f, pos.y + cur.size.height / 2f)
        // forget the last swap partner once the finger has left it (prevents ping-pong between unequal sizes)
        val prev = lastSwap[0]
        if (prev != null) {
            val pi = infoOf(prev)
            if (pi == null || !boundsOf(pi).contains(center)) lastSwap[0] = null
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSwapAt[0] < 90) return            // let the previous swap lay out first
        val target = info.visibleItemsInfo.firstOrNull { t ->
            val k = t.key as? String ?: return@firstOrNull false
            k.startsWith("card_") && k != keyOf(dk) && boundsOf(t).contains(center)
        } ?: return
        val tc = runCatching { DashCard.valueOf((target.key as String).removePrefix("card_")) }.getOrNull() ?: return
        if (tc == lastSwap[0]) return
        val list = order.toMutableList()
        val from = list.indexOf(dk); val to = list.indexOf(tc)
        if (from < 0 || to < 0) return
        list.removeAt(from); list.add(to, dk)
        order = list
        lastSwap[0] = tc; lastSwapAt[0] = now
        tick()
    }

    @Composable
    fun Modifier.movable(c: DashCard): Modifier {
        val dragging = dragKey == c
        val k = keyOf(c)
        return this
            .zIndex(if (dragging) 1f else 0f)
            // pointer input sits outside the graphics layer, so its deltas are plain screen deltas
            .pointerInput(c) {
                detectLongPressDrag(
                    onStart = {
                        tick()
                        val i = infoOf(c)
                        dragStart = if (i != null) Offset(i.offset.x.toFloat(), i.offset.y.toFloat()) else Offset.Zero
                        dragTotal = Offset.Zero
                        lastSwap[0] = null
                        dragKey = c
                        if (!hinted) { hinted = true; toaster.show("Drag to move the card · let go to drop") }
                    },
                    onDrag = { onDrag(it) },
                    onEnd = {
                        if (dragKey != null) {
                            dragKey = null; dragTotal = Offset.Zero; lastSwap[0] = null
                            val o = order
                            container.write { container.settings.setDashOrder(o) }
                        }
                    },
                )
            }
            .graphicsLayer {
                if (dragKey == c) {
                    val i = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }
                    if (i != null) {
                        val p = dragStart + dragTotal
                        translationX = p.x - i.offset.x
                        translationY = p.y - i.offset.y
                    }
                    scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 28f; shape = RoundedCornerShape(28.dp); clip = false
                }
            }
    }

    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    val half: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(1) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "greeting", span = full) { Box(Modifier.statusBarsPadding()) { Greeting(state) } }
        order.forEach { c ->
            if (c !in cards) return@forEach
            if (c == DashCard.PIP && !settings.pipEnabled) return@forEach
            if (c == DashCard.CYCLE && !settings.cycleEnabled) return@forEach
            if (c == DashCard.GLUCOSE && !settings.glucoseEnabled) return@forEach
            item(key = keyOf(c), span = if (c in halfCards) half else full) {
                val place: androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset>? =
                    if (dragKey == c) null else androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)
                Box(Modifier.animateItem(placementSpec = place).movable(c)) {
                    val nav = com.myfit.tracker.ui.nav.LocalNav.current
                    when (c) {
                        DashCard.PIP -> PipCard(state)
                        DashCard.SNAP -> SnapHeroCard()
                        DashCard.WORKOUT -> WorkoutCard(state.workout, container)
                        DashCard.RINGS -> RingsCard(state, open)
                        DashCard.NUTRITION -> NutritionCard(container)
                        DashCard.SOCIAL -> CompeteCard(container)
                        DashCard.HYDRATION -> HydrationTile(state, container, open)
                        DashCard.STEPS -> StepsTile(state) { nav.push(com.myfit.tracker.ui.nav.Overlay.Activity) }
                        DashCard.RECOVERY -> SleepTile(state) { open(Sheet.Sleep()) }
                        DashCard.CHECKIN -> CheckInTile(state) { open(Sheet.CheckIn()) }
                        DashCard.BODY -> BodyTile(state) { nav.push(com.myfit.tracker.ui.nav.Overlay.Body) }
                        DashCard.GOALS -> GoalsTile(state, null)
                        DashCard.VITALS -> com.myfit.tracker.ui.vitals.VitalsTile(container) { nav.push(com.myfit.tracker.ui.nav.Overlay.Vitals) }
                        DashCard.MIND -> com.myfit.tracker.ui.mind.MindTile(container) { nav.push(com.myfit.tracker.ui.nav.Overlay.Mind) }
                        DashCard.CYCLE -> com.myfit.tracker.ui.cycle.CycleTile(container) { nav.push(com.myfit.tracker.ui.nav.Overlay.Cycle) }
                        DashCard.GLUCOSE -> com.myfit.tracker.ui.glucose.GlucoseTile(container) { nav.push(com.myfit.tracker.ui.nav.Overlay.Glucose) }
                    }
                }
            }
        }
        item(key = "arrange", span = full) {
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
    onStart: () -> Unit, onDrag: (Offset) -> Unit, onEnd: () -> Unit,
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
                val d = ch.position - ch.previousPosition
                ch.consume()
                if (!ch.pressed) break
                onDrag(d)
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
                ProgressRing(s.waterMl?.let { w -> s.waterTarget?.takeIf { it > 0 }?.let { (w / it).toFloat() } }, th.water, size = 116.dp, stroke = 11.dp)
                ProgressRing(s.steps?.let { st -> s.stepTarget?.takeIf { it > 0 }?.let { (st / it).toFloat() } }, th.steps, size = 88.dp, stroke = 11.dp)
                ProgressRing(s.sleepMin?.let { m -> s.sleepTarget?.takeIf { it > 0 }?.let { (m / it).toFloat() } }, th.sleep, size = 60.dp, stroke = 11.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RingLegend(th.water, "Water",
                    s.waterMl?.let { Fmt.volume(it, units.volume) } ?: "—",
                    s.waterTarget?.let { "/ ${Fmt.volume(it, units.volume)}" }) { open(Sheet.Water()) }
                RingLegend(th.steps, "Steps", s.steps?.let { Fmt.int(it) } ?: "—", s.stepTarget?.let { "/ ${Fmt.int(it)}" }) { open(Sheet.Steps()) }
                RingLegend(th.sleep, "Sleep", s.sleepMin?.let { Fmt.duration(it) } ?: "—", s.sleepTarget?.let { "/ ${Fmt.duration(it.toLong())}" }) { open(Sheet.Sleep()) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption("Dashed ring = nothing logged yet (not zero).")
    }
}

@Composable
private fun RingLegend(color: Color, label: String, value: String, of: String?, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableNoRipple(onClick)) {
        Box(Modifier.size(10.dp).clip(CircleShape)) { Canvas(Modifier.fillMaxSize()) { drawCircle(color) } }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = FitType.caption, color = th.textDim, maxLines = 1)
            Text(
                buildAnnotatedString {
                    append(value)
                    if (of != null) withStyle(SpanStyle(fontSize = FitType.caption.fontSize, fontWeight = FitType.caption.fontWeight, color = th.textDim)) { append(" $of") }
                },
                style = FitType.section, color = th.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            )
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
                CompactPill("Open camera", Duo.Camera, go)
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

/** Your place on this week's friends board, or an invite to sign in. */
@Composable
private fun CompeteCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = com.myfit.tracker.ui.nav.LocalNav.current
    val social = container.social
    val user by social.user.collectAsState()
    val board by androidx.compose.runtime.produceState<List<com.myfit.tracker.social.BoardRow>?>(null, user) {
        value = if (user != null) runCatching { social.friendsBoard(com.myfit.tracker.social.Metric.STEPS) }.getOrNull() else null
    }
    GlassCard(onClick = { nav.push(com.myfit.tracker.ui.nav.Overlay.Social) }) {
        CardHeader(Duo.EmojiEvents, "Compete", th.warning) { Caption("This week") }
        Spacer(Modifier.height(10.dp))
        val b = board
        when {
            !social.available -> Caption("Online challenges arrive once Firebase is connected to this build.")
            user == null -> {
                Caption("Sign in with Google or email to race friends on steps, distance and watch-recorded workouts.")
                Spacer(Modifier.height(10.dp))
                com.myfit.tracker.ui.theme.AccentButton("Sign in", { nav.push(com.myfit.tracker.ui.nav.Overlay.Social) }, icon = Duo.Person, height = 44.dp)
            }
            b == null -> Caption("Loading…")
            b.size <= 1 -> Caption("Add a friend with your code to start competing.")
            else -> b.take(3).forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = FitType.label, color = th.textDim, modifier = Modifier.width(22.dp))
                    com.myfit.tracker.ui.social.Avatar(r.name, r.color, 28)
                    Spacer(Modifier.width(8.dp))
                    Text(if (r.me) "${r.name} (you)" else r.name, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    Text(Fmt.int(r.value), style = FitType.section, color = th.text)
                }
            }
        }
    }
}

/** Small glossy accent pill (38dp) — icon + label on one line, never wraps. */
@Composable
private fun CompactPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val tick = com.myfit.tracker.ui.theme.rememberTick()
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent)))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, size.height * 0.5f))
            }
            .clickableNoRipple { tick(); onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = th.onAccent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = FitType.label, color = th.onAccent, maxLines = 1, softWrap = false)
    }
}
