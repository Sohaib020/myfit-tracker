package com.myfit.tracker.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.prefs.DashCard
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.tourTarget
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassProgressBar
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tab indices of the main dock (Home · Train · Food · Arena · Settings). */
object Tabs { const val HOME = 0; const val TRAIN = 1; const val FOOD = 2; const val ARENA = 3; const val SETTINGS = 4 }

/**
 * Home. Cards sit in a two-column grid: small cards take one column, large cards the full width.
 * Long-press any card to lift it: drag to move it anywhere, or use the little toolbar that appears
 * on it to make it Small / Large or hide it. Order and sizes are saved.
 */
@Composable
fun DashboardScreen(state: DashState, container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int, goTab: (Int) -> Unit = {}) {
    val settings = LocalSettings.current
    val cards = settings.dashCards
    val toaster = LocalToaster.current
    val tick = rememberTick()
    val density = LocalDensity.current
    val gridState = rememberLazyGridState()
    // local copies so cards move / resize instantly; persisted in the background
    var order by remember { mutableStateOf(settings.dashOrder) }
    var smallSet by remember { mutableStateOf(settings.dashSmall) }
    var dragKey by remember { mutableStateOf<DashCard?>(null) }
    var selected by remember { mutableStateOf<DashCard?>(null) }
    LaunchedEffect(settings.dashOrder) { if (dragKey == null) order = settings.dashOrder }
    LaunchedEffect(settings.dashSmall) { smallSet = settings.dashSmall }
    // Drag model: the dragged card should sit at `dragStart + dragTotal` (grid coordinates, the card's
    // top-left). Its translation is derived every frame from the live layout, so swaps and auto-scroll
    // never need corrections.
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    var hinted by remember { mutableStateOf(false) }
    val edge = remember { floatArrayOf(0f) }                  // auto-scroll direction while dragging (−1, 0, 1)
    val lastSwap = remember { arrayOfNulls<DashCard>(1) }
    val lastSwapAt = remember { longArrayOf(0L) }
    var selBounds by remember { mutableStateOf<Rect?>(null) }   // selected card, in root coordinates
    var rootTopLeft by remember { mutableStateOf(Offset.Zero) }
    val edgePx = with(density) { 72.dp.toPx() }
    val scrollPx = with(density) { 9.dp.toPx() }

    BackHandler(enabled = selected != null) { selected = null }

    fun keyOf(c: DashCard) = "card_" + c.name
    fun infoOf(c: DashCard): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == keyOf(c) }
    fun boundsOf(i: LazyGridItemInfo) = Rect(i.offset.x.toFloat(), i.offset.y.toFloat(), (i.offset.x + i.size.width).toFloat(), (i.offset.y + i.size.height).toFloat())

    fun visible(c: DashCard): Boolean = c in cards && c !in com.myfit.tracker.data.prefs.MergedCards && when (c) {
        DashCard.PIP -> settings.pipEnabled
        DashCard.CYCLE -> showCycle(state.profile, settings) && (settings.cycleEnabled || !settings.cycleAsked)
        DashCard.GLUCOSE -> showDiabetes(settings)
        DashCard.DEEN -> settings.muslim == "yes"
        else -> true
    }

    fun checkSwap() {
        val dk = dragKey ?: return
        val info = gridState.layoutInfo
        val cur = infoOf(dk) ?: return
        val pos = dragStart + dragTotal
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

    fun onDrag(d: Offset) {
        val dk = dragKey ?: return
        dragTotal += d
        val cur = infoOf(dk)
        if (cur != null) {
            val top = (dragStart + dragTotal).y
            val bottom = top + cur.size.height
            val h = gridState.layoutInfo.viewportSize.height
            edge[0] = when {
                top < edgePx -> -1f
                bottom > h - edgePx -> 1f
                else -> 0f
            }
        }
        checkSwap()
    }

    // auto-scroll while a card is held near the top / bottom edge (runs only during a drag)
    LaunchedEffect(dragKey) {
        if (dragKey == null) { edge[0] = 0f; return@LaunchedEffect }
        while (true) {
            withFrameNanos { }
            val e = edge[0]
            if (e != 0f) {
                val used = gridState.dispatchRawDelta(e * scrollPx)
                if (used != 0f) checkSwap()
            }
        }
    }

    fun setSize(c: DashCard, small: Boolean) {
        if ((c in smallSet) == small) return
        tick()
        smallSet = if (small) smallSet + c else smallSet - c
        container.write { container.settings.setDashSize(c, small) }
    }

    fun hide(c: DashCard) {
        val before = settings.dashCards
        selected = null
        container.write { container.settings.setDashCards(before - c) }
        toaster.show("${c.label} hidden", "Undo") { container.write { container.settings.setDashCards(before) } }
    }

    @Composable
    fun Modifier.movable(c: DashCard): Modifier {
        val dragging = dragKey == c
        val lifted = dragging || selected == c
        val k = keyOf(c)
        return this
            .zIndex(if (lifted) 1f else 0f)
            .then(if (selected == c && !dragging) Modifier.onGloballyPositioned { selBounds = it.boundsInRoot() } else Modifier)
            // pointer input sits outside the graphics layer, so its deltas are plain screen deltas
            .pointerInput(c) {
                detectLongPressDrag(
                    onStart = {
                        tick()
                        val i = infoOf(c)
                        dragStart = if (i != null) Offset(i.offset.x.toFloat(), i.offset.y.toFloat()) else Offset.Zero
                        dragTotal = Offset.Zero
                        lastSwap[0] = null
                        selBounds = null
                        selected = c
                        dragKey = c
                        if (!hinted) { hinted = true; toaster.show("Drag to move · or make it Small / Large") }
                    },
                    onDrag = { onDrag(it) },
                    onEnd = {
                        if (dragKey != null) {
                            dragKey = null; dragTotal = Offset.Zero; lastSwap[0] = null
                            val o = order
                            if (o != settings.dashOrder) container.write { container.settings.setDashOrder(o) }
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
                }
                if (lifted) {
                    scaleX = 1.03f; scaleY = 1.03f; shadowElevation = 28f; shape = RoundedCornerShape(28.dp); clip = false
                }
            }
    }

    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    val half: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(1) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { rootTopLeft = it.boundsInRoot().topLeft }) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "greeting", span = full) { Box(Modifier.statusBarsPadding()) { Greeting(state) } }
            if (settings.diabetesType == "unset" && !settings.diabetesAsked && !settings.glucoseEnabled) {
                item(key = "personalise", span = full) { Box(Modifier.animateItem()) { PersonaliseCard(container) } }
            }
            if (settings.muslim == "unset") {
                item(key = "deenask", span = full) { Box(Modifier.animateItem()) { DeenAskCard(container) } }
            }
            order.forEach { c ->
                if (!visible(c)) return@forEach
                val small = c in smallSet
                item(key = keyOf(c), span = if (small) half else full) {
                    val place: androidx.compose.animation.core.FiniteAnimationSpec<IntOffset>? =
                        if (dragKey == c) null else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                    Box(Modifier.animateItem(placementSpec = place).movable(c).tourTarget(keyOf(c))) {
                        if (small) {
                            // every small card is exactly one tile tall so the two cards in a row line up
                            Box(Modifier.fillMaxWidth().height(TileHeight), propagateMinConstraints = true) {
                                DashCardContent(c, true, state, container, open, goTab)
                            }
                        } else DashCardContent(c, false, state, container, open, goTab)
                    }
                }
                // combined cards split into two tiles when small
                if (small && (c == DashCard.RINGS || c == DashCard.NUTRITION)) item(key = keyOf(c) + "_b", span = half) {
                    Box(Modifier.animateItem().movable(c)) {
                        Box(Modifier.fillMaxWidth().height(TileHeight), propagateMinConstraints = true) {
                            if (c == DashCard.RINGS) CheckInTile(state) { open(Sheet.CheckIn()) }
                            else HydrationTile(state, container, open)
                        }
                    }
                }
            }
            item(key = "arrange", span = full) {
                val nav = LocalNav.current
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Caption("Tip: hold any card to move it, resize it or hide it.")
                    Spacer(Modifier.height(8.dp))
                    com.myfit.tracker.ui.theme.GlassButton("Show / hide cards", { nav.push(Overlay.ArrangeDash) }, icon = Duo.Tune, height = 44.dp)
                }
            }
        }

        // tap anywhere else to put a lifted card back down
        if (selected != null && dragKey == null) {
            Box(Modifier.fillMaxSize().pointerInput(Unit) {
                awaitEachGesture { awaitFirstDown(requireUnconsumed = false); selected = null }
            })
        }
        val sel = selected
        AnimatedVisibility(
            visible = sel != null && dragKey == null && selBounds != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.fillMaxSize().layout { m, cons ->
                val p = m.measure(cons.copy(minWidth = 0, minHeight = 0))
                layout(cons.maxWidth, cons.maxHeight) {
                    val b = selBounds
                    if (b != null) {
                        val gap = 10.dp.roundToPx()
                        val margin = 8.dp.roundToPx()
                        val cx = (b.center.x - rootTopLeft.x).toInt()
                        val x = (cx - p.width / 2).coerceIn(margin, (cons.maxWidth - p.width - margin).coerceAtLeast(margin))
                        val above = (b.top - rootTopLeft.y).toInt() - p.height - gap
                        val minTop = 96.dp.roundToPx()       // keep clear of the Me pill / + orb
                        val y = if (above >= minTop) above else ((b.top - rootTopLeft.y).toInt() + gap).coerceAtLeast(minTop)
                        p.place(x, y)
                    }
                }
            },
        ) {
            if (sel != null) CardToolbar(
                small = sel in smallSet,
                onSize = { setSize(sel, it) },
                onHide = { hide(sel) },
                onDone = { selected = null },
            )
        }
    }
}

@Composable
private fun DashCardContent(c: DashCard, small: Boolean, state: DashState, container: AppContainer, open: (Sheet) -> Unit, goTab: (Int) -> Unit) {
    val nav = LocalNav.current
    when (c) {
        DashCard.PIP -> if (small) PipSmall(state) else PipCard(state)
        DashCard.SNAP -> if (small) SnapSmall() else SnapHeroCard()
        DashCard.WORKOUT -> if (small) WorkoutSmall(state.workout, container) { goTab(Tabs.TRAIN) } else WorkoutCard(state.workout, container)
        DashCard.RINGS -> if (small) RingsSmall(state) { nav.push(Overlay.Today) } else RingsCard(state, open)
        DashCard.NUTRITION -> if (small) NutritionSmall(container) { goTab(Tabs.FOOD) } else FoodHydrationCard(state, container, open) { goTab(Tabs.FOOD) }
        DashCard.SOCIAL -> if (small) CompeteSmall(container) { goTab(Tabs.ARENA) } else com.myfit.tracker.ui.social.FriendsHero(container) { goTab(Tabs.ARENA) }
        DashCard.HYDRATION -> HydrationTile(state, container, open, wide = !small)
        DashCard.STEPS -> StepsTile(state) { nav.push(Overlay.Activity) }
        DashCard.RECOVERY -> SleepTile(state) { open(Sheet.Sleep()) }
        DashCard.CHECKIN -> CheckInTile(state) { open(Sheet.CheckIn()) }
        DashCard.BODY -> BodyTile(state) { nav.push(Overlay.Body) }
        DashCard.GOALS -> GoalsTile(state, null)
        DashCard.VITALS -> if (small) com.myfit.tracker.ui.vitals.VitalsTile(container) { nav.push(Overlay.Vitals) } else VitalsCard(container)
        DashCard.MIND -> if (small) com.myfit.tracker.ui.mind.MindTile(container) { nav.push(Overlay.Mind) } else MindCard(container)
        DashCard.CYCLE -> if (small) com.myfit.tracker.ui.cycle.CycleTile(container) { nav.push(Overlay.Cycle) } else CycleCard(container)
        DashCard.GLUCOSE -> com.myfit.tracker.ui.glucose.GlucoseTile(container) { nav.push(Overlay.Glucose) }
        DashCard.STREAKS -> com.myfit.tracker.ui.badges.StreaksTile(container) { nav.push(Overlay.Badges) }
        DashCard.DEEN -> DeenCard()
    }
}

/** Floating toolbar on a lifted card: Small / Large, Hide, Done. */
@Composable
private fun CardToolbar(small: Boolean, onSize: (Boolean) -> Unit, onHide: () -> Unit, onDone: () -> Unit) {
    val th = LocalFitTheme.current
    Glass(
        Modifier.height(48.dp), shape = RoundedCornerShape(24.dp), pressScale = 1f,
        tint = if (th.isLight) Color.White.copy(alpha = 0.86f) else th.glassTint.copy(alpha = (th.glassTint.alpha * 1.6f + 0.1f).coerceAtMost(0.94f)),
    ) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
                    .background(if (th.isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolSeg("Small", small) { onSize(true) }
                ToolSeg("Large", !small) { onSize(false) }
            }
            Spacer(Modifier.width(4.dp))
            ToolAction(Duo.Close, "Hide", th.text, onHide)
            Box(Modifier.size(38.dp).clip(CircleShape).drawBehind { drawCircle(Brush.verticalGradient(listOf(th.accentBright, th.accent))) }.clickableNoRipple(onDone), contentAlignment = Alignment.Center) {
                Icon(Duo.Check, "Done", tint = th.onAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ToolSeg(text: String, on: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp))
            .then(if (on) Modifier.drawBehind { drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent))) } else Modifier)
            .clickableNoRipple(onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = FitType.label, color = if (on) th.onAccent else th.text, maxLines = 1, softWrap = false) }
}

@Composable
private fun ToolAction(icon: ImageVector, text: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).clickableNoRipple(onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = FitType.label, color = color, maxLines = 1, softWrap = false)
    }
}

/**
 * Long-press then drag, detected in the Initial pass so it works on top of cards full of buttons.
 * A normal tap or scroll (movement before the long-press timeout) is left untouched.
 */
private suspend fun PointerInputScope.detectLongPressDrag(
    onStart: () -> Unit, onDrag: (Offset) -> Unit, onEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop
        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
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
                val ev = awaitPointerEvent(PointerEventPass.Initial)
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
            Text(if (name.isBlank()) greetingFor(LocalTime.now()) else "${greetingFor(LocalTime.now())}, $name", style = FitType.title, color = th.text)
            Caption(s.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)))
        }
    }
}

// ------------------------------------------------------------------ personalise (asked once)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaliseCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val toaster = LocalToaster.current
    GlassCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Duo.Drop, th.water, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Personalise MyFit", style = FitType.section, color = th.text)
                Caption("Do you manage diabetes?")
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape).clickableNoRipple { container.write { container.settings.setDiabetesAsked(true) } },
                contentAlignment = Alignment.Center,
            ) { Icon(Duo.Close, "Not now", tint = th.textDim, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Caption("We'll add blood-sugar tools only if you need them.")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("none", "type1", "type2", "gestational", "prediabetes", "other").forEach { t ->
                com.myfit.tracker.ui.theme.GlassChip(diabetesLabel(t), false, {
                    container.write {
                        container.settings.setDiabetesType(t)
                        if (managesDiabetes(t)) container.settings.setGlucose(true)
                    }
                    toaster.show(if (managesDiabetes(t)) "Blood sugar added to Home" else "Got it — no blood-sugar tools")
                })
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption("Change it any time in Me → Health features. MyFit isn't a medical device.", color = th.textFaint)
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
    val nav = LocalNav.current
    var variant by remember { mutableIntStateOf(0) }
    val (mood, line) = pipLine(s, variant)
    Glass(Modifier.fillMaxWidth().height(170.dp), onClick = { nav.push(Overlay.PipChat) }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pip(mood, size = 128.dp, onTap = { variant++ })
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(com.myfit.tracker.ui.pip.Buddy.active.collectAsState().value.label.substringBefore(' ').uppercase(), style = FitType.overline, color = th.accentBright)
                Spacer(Modifier.height(4.dp))
                Text(line, style = FitType.body, color = th.text)
                Spacer(Modifier.height(10.dp))
                CompactPill("Chat with " + com.myfit.tracker.ui.pip.Buddy.active.collectAsState().value.label.substringBefore(' '), Duo.ChatBubble, { nav.push(Overlay.PipChat) }, height = 34.dp)
            }
        }
    }
}

@Composable
private fun PipSmall(s: DashState) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val (mood, line) = pipLine(s, 0)
    GlassCard(Modifier.height(TileHeight), onClick = { nav.push(Overlay.PipChat) }, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(com.myfit.tracker.ui.pip.Buddy.active.collectAsState().value.label.substringBefore(' ').uppercase(), style = FitType.overline, color = th.accentBright, modifier = Modifier.weight(1f))
            Icon(Duo.ChatBubble, "Chat", tint = th.textDim, modifier = Modifier.size(15.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Pip(mood, size = 70.dp, interactive = false) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompactPill("Chat with " + com.myfit.tracker.ui.pip.Buddy.active.collectAsState().value.label.substringBefore(' '), Duo.ChatBubble, { nav.push(Overlay.PipChat) }, height = 34.dp)
        }
    }
}

// ------------------------------------------------------------------ Rings

private fun frac(v: Double?, t: Double?): Float? = if (v == null || t == null || t <= 0) null else (v / t).toFloat()

internal val CaloriesColor = Color(0xFFB66DFF)
internal const val ACTIVE_KCAL_TARGET = 500.0

/** Three concentric rings (water · steps · active calories), sized to [outer]. */
@Composable
private fun TripleRings(s: DashState, outer: Dp) {
    val th = LocalFitTheme.current
    val stroke = (outer * 0.095f).coerceIn(7.dp, 12.dp)
    val step = (stroke + 3.dp) * 2
    Box(Modifier.size(outer), contentAlignment = Alignment.Center) {
        ProgressRing(frac(s.waterMl, s.waterTarget), th.water, size = outer, stroke = stroke)
        ProgressRing(frac(s.steps?.toDouble(), s.stepTarget), th.steps, size = outer - step, stroke = stroke)
        ProgressRing(frac(s.health.daily?.activeKcal, ACTIVE_KCAL_TARGET), CaloriesColor, size = outer - step * 2, stroke = stroke)
    }
}

@Composable
internal fun RingsCard(s: DashState, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val nav = LocalNav.current
    GlassCard(onClick = { nav.push(Overlay.Today) }) {
        Text("TODAY'S PROGRESS", style = FitType.overline, color = th.textDim)
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // fixed split: rings get 38% (capped), stats get the rest with a clear 18dp gutter — nothing overlaps
            val ring = (maxWidth * 0.38f).coerceIn(92.dp, 132.dp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(ring).testTag("rings"), contentAlignment = Alignment.Center) { TripleRings(s, ring) }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RingLegend(th.water, "Water", s.waterMl?.let { Fmt.volume(it, units.volume) } ?: "—",
                        s.waterTarget?.let { "of ${Fmt.volume(it, units.volume)}" }, { open(Sheet.Water()) }, frac(s.waterMl, s.waterTarget))
                    RingLegend(th.steps, "Steps", s.steps?.let { Fmt.int(it) } ?: "—", s.stepTarget?.let { "of ${Fmt.int(it)}" }, { open(Sheet.Steps()) }, frac(s.steps?.toDouble(), s.stepTarget))
                    RingLegend(CaloriesColor, "Calories", s.health.daily?.activeKcal?.let { Fmt.int(it.toLong()) + " kcal" } ?: "—", "of ${Fmt.int(ACTIVE_KCAL_TARGET.toLong())} active", { nav.push(Overlay.Today) }, frac(s.health.daily?.activeKcal, ACTIVE_KCAL_TARGET))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        CheckInStrip(s, open)
        Spacer(Modifier.height(8.dp))
        Caption("Tap for hourly activity · dashed ring = nothing logged yet")
    }
}

/**
 * One stat: dot + LABEL with % on the right, a big value that shrinks to fit, then a slim bar with the target.
 * No clipping on this column (rounded clips used to shave the first letters).
 */
@Composable
internal fun RingLegend(color: Color, label: String, value: String, of: String?, onClick: () -> Unit, pct: Float? = null) {
    val th = LocalFitTheme.current
    Column(Modifier.fillMaxWidth().clickableNoRipple(onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label.uppercase(), style = FitType.overline, color = th.textDim, maxLines = 1, modifier = Modifier.weight(1f))
            if (pct != null) Text("${(pct * 100).toInt().coerceAtMost(999)}%", style = FitType.label, color = color, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.height(2.dp))
        ShrinkText(value, FitType.metric.copy(fontSize = 26.sp, lineHeight = 28.sp), th.text)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(5.dp).background(th.textFaint.copy(alpha = 0.18f), CircleShape)) {
                if (pct != null && pct > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(pct.coerceIn(0.03f, 1f)).background(color, CircleShape))
            }
            if (of != null) {
                Spacer(Modifier.width(8.dp))
                Text(of, style = FitType.caption, color = th.textDim, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Single-line text that steps its font size down until it fits the width. */
@Composable
private fun ShrinkText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    var size by remember(text) { mutableStateOf(style.fontSize) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(text, style = style.copy(fontSize = size, lineHeight = size * 1.1f), color = color, maxLines = 1, softWrap = false,
        modifier = Modifier.fillMaxWidth().drawWithContent { if (ready) drawContent() },
        onTextLayout = { r -> if (r.didOverflowWidth && size.value > 13f) size = (size.value * 0.9f).sp else ready = true })
}

@Composable
private fun RingsSmall(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard(Modifier.height(TileHeight), onClick = onClick, padding = 12.dp) {
        Text("TODAY", style = FitType.overline, color = th.textDim)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            TripleRings(s, minOf(maxWidth, maxHeight).coerceAtMost(112.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf(th.water to "Water", th.steps to "Steps", CaloriesColor to "Kcal").forEach { (c, l) ->
                Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(3.dp))
                Text(l, style = FitType.overline.copy(letterSpacing = FitType.caption.letterSpacing), color = th.textDim, maxLines = 1)
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
fun DashSection(text: String) = SectionTitle(text)

// ------------------------------------------------------------------ Food

@Composable
private fun NutritionCard(c: AppContainer, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val today = Clock.today()
    val items by remember(today) { c.nutritionRepo.itemsOn(today) }.collectAsState(initial = emptyList())
    val targets by c.profileRepo.targets.collectAsState(initial = emptyList())
    val t = com.myfit.tracker.ui.food.totalsOf(items)
    val key = Clock.dateKey(today)
    GlassCard(onClick = onOpen) {
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
            com.myfit.tracker.ui.theme.GlassButton("Snap meal", { nav.push(Overlay.FoodPhoto(meal, key)) }, Modifier.weight(1f), icon = Duo.Camera, height = 44.dp)
            com.myfit.tracker.ui.theme.GlassButton("Add food", { nav.push(Overlay.FoodAdd(meal, key, 0)) }, Modifier.weight(1f), icon = Duo.ForkKnife, height = 44.dp)
        }
    }
}

@Composable
private fun NutritionSmall(c: AppContainer, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val today = Clock.today()
    val items by remember(today) { c.nutritionRepo.itemsOn(today) }.collectAsState(initial = emptyList())
    val targets by c.profileRepo.targets.collectAsState(initial = emptyList())
    val t = com.myfit.tracker.ui.food.totalsOf(items)
    val kcalT = com.myfit.tracker.domain.Targets.on(targets, com.myfit.tracker.data.db.TargetType.CALORIES, today)
    HalfTile(Duo.ForkKnife, "Food", th.protein, onOpen) {
        TileValue(if (items.isEmpty()) "—" else Fmt.int(t.kcal), "kcal")
        TileLine(when {
            items.isEmpty() -> "Nothing logged yet"
            kcalT != null -> "of ${Fmt.int(kcalT)} kcal"
            else -> "${items.size} ${if (items.size == 1) "item" else "items"}"
        })
        Spacer(Modifier.height(8.dp))
        GlassProgressBar(if (items.isEmpty()) null else frac(t.kcal, kcalT), th.protein, height = 6.dp)
        Spacer(Modifier.height(8.dp))
        TileLine(if (items.isEmpty()) "Tap to log a meal" else "P ${Fmt.int(t.protein)} · C ${Fmt.int(t.carbs)} · F ${Fmt.int(t.fat)} g", if (items.isEmpty()) th.accentBright else th.textFaint)
    }
}

/** The headline feature: a friendly, compact entry point to the food camera. */
@Composable
internal fun SnapHeroCard(dateKey: String = Clock.dateKey(Clock.today()), meal: String? = null) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val pip = rememberPipFuel()
    val go = { nav.push(Overlay.FoodPhoto(meal ?: com.myfit.tracker.ui.food.mealForNow(), dateKey)) }
    Glass(Modifier.fillMaxWidth(), onClick = go) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(Brush.radialGradient(
                listOf(th.accent.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.5f), radius = size.height * 0.9f,
            ))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("AI CALORIE SCANNER", style = FitType.overline, color = th.accentBright)
                Spacer(Modifier.height(3.dp))
                Text("Snap a meal", style = FitType.section.copy(fontSize = FitType.title.fontSize, lineHeight = FitType.title.lineHeight), color = th.text)
                Spacer(Modifier.height(2.dp))
                Text("Point at your plate — Pip names each dish and counts calories & macros.", style = FitType.caption, color = th.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                CompactPill("Open camera", Duo.Camera, go)
            }
            Spacer(Modifier.width(8.dp))
            Viewfinder(Modifier.size(92.dp)) { if (pip != null) androidx.compose.foundation.Image(pip, null, Modifier.size(80.dp)) }
        }
    }
}

@Composable
private fun rememberPipFuel(): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching { ctx.assets.open("pip/still_fuel.webp").use { android.graphics.BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
    }
}

/** Viewfinder corners framing [content]. */
@Composable
private fun Viewfinder(modifier: Modifier, content: @Composable () -> Unit) {
    val th = LocalFitTheme.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val arm = size.width * 0.18f; val sw = 2.5.dp.toPx(); val col = th.accentBright; val i = sw
            listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (fx, fy) ->
                val x = if (fx == 0f) i else size.width - i; val y = if (fy == 0f) i else size.height - i
                val dx = if (fx == 0f) arm else -arm; val dy = if (fy == 0f) arm else -arm
                drawLine(col, Offset(x, y), Offset(x + dx, y), sw, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(col, Offset(x, y), Offset(x, y + dy), sw, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        content()
    }
}

@Composable
private fun SnapSmall() {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val pip = rememberPipFuel()
    val go = { nav.push(Overlay.FoodPhoto(com.myfit.tracker.ui.food.mealForNow(), Clock.dateKey(Clock.today()))) }
    GlassCard(Modifier.height(TileHeight), onClick = go, padding = 12.dp) {
        Text("AI CALORIES", style = FitType.overline, color = th.accentBright, maxLines = 1)
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text("Snap a meal", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
            Viewfinder(Modifier.size(58.dp)) { if (pip != null) androidx.compose.foundation.Image(pip, null, Modifier.size(50.dp)) }
        }
        CompactPill("Camera", Duo.Camera, go, height = 34.dp)
    }
}

// ------------------------------------------------------------------ Workout (small)

@Composable
private fun WorkoutSmall(w: WorkoutToday, container: AppContainer, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val a = w.active
    HalfTile(Duo.FitnessCenter, "Workout", th.accentBright, onClick = { if (a != null) nav.push(Overlay.Gym(a.workout.id)) else onOpen() }) {
        when {
            a != null -> {
                Text("In progress", style = FitType.section, color = th.accentBright, maxLines = 1)
                TileLine(a.workout.name)
                Spacer(Modifier.weight(1f))
                CompactPill("Resume", Duo.PlayArrow, { nav.push(Overlay.Gym(a.workout.id)) }, height = 34.dp)
            }
            w.done.isNotEmpty() -> {
                TileValue("${w.done.size}", if (w.done.size == 1) "done" else "done")
                TileLine(w.done.first().workout.name, th.success)
                Spacer(Modifier.height(8.dp))
                w.weeklyTarget?.takeIf { it > 0 }?.let { t ->
                    GlassProgressBar((w.weeklyDone / t).toFloat(), th.accentBright, height = 6.dp)
                    Spacer(Modifier.height(8.dp))
                    TileLine("${w.weeklyDone}/${Fmt.int(t)} this week", th.textFaint)
                } ?: TileLine("${w.weeklyDone} this week", th.textFaint)
            }
            else -> {
                Text(if (w.plannedDay) "Workout day" else "Rest day", style = FitType.section, color = th.text, maxLines = 1)
                TileLine(w.next?.let { "Next: ${it.template.name}" } ?: "No template yet")
                Spacer(Modifier.weight(1f))
                CompactPill("Start", Duo.PlayArrow, {
                    scope.launch {
                        val id = w.next?.let { container.workoutRepo.startFromTemplate(it.template.id) } ?: container.workoutRepo.startEmpty()
                        nav.push(Overlay.Gym(id))
                    }
                }, height = 34.dp)
            }
        }
    }
}

// ------------------------------------------------------------------ Arena

@Composable
private fun rememberBoard(container: AppContainer): Pair<Boolean, List<com.myfit.tracker.social.BoardRow>?> {
    val social = container.social
    val user by social.user.collectAsState()
    val board by produceState<List<com.myfit.tracker.social.BoardRow>?>(null, user) {
        value = if (user != null) runCatching { social.friendsBoard(com.myfit.tracker.social.Metric.STEPS) }.getOrNull() else null
    }
    return (user != null) to board
}

/** Your place on this week's friends board, or an invite to sign in. */
@Composable
private fun CompeteCard(container: AppContainer, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val social = container.social
    val (signedIn, board) = rememberBoard(container)
    GlassCard(onClick = onOpen) {
        CardHeader(Duo.EmojiEvents, "Arena", th.warning) { Caption("This week") }
        Spacer(Modifier.height(10.dp))
        val b = board
        when {
            !social.available -> Caption("Online challenges arrive once Firebase is connected to this build.")
            !signedIn -> {
                Caption("Sign in with Google or email to race friends on steps, distance and watch-recorded workouts.")
                Spacer(Modifier.height(10.dp))
                CompactPill("Sign in", Duo.Person, onOpen)
            }
            b == null -> Caption("Loading…")
            b.size <= 1 -> Caption("Add a friend with your code to start competing.")
            else -> b.take(3).forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = FitType.label, color = th.textDim, modifier = Modifier.width(22.dp))
                    com.myfit.tracker.ui.social.Avatar(r.name, r.color, 28)
                    Spacer(Modifier.width(8.dp))
                    Text(if (r.me) "${r.name} (you)" else r.name, style = FitType.body, color = th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.int(r.value), style = FitType.section, color = th.text)
                }
            }
        }
    }
}

@Composable
private fun CompeteSmall(container: AppContainer, onOpen: () -> Unit) {
    val th = LocalFitTheme.current
    val social = container.social
    val (signedIn, board) = rememberBoard(container)
    HalfTile(Duo.EmojiEvents, "Arena", th.warning, onOpen) {
        val b = board
        when {
            !social.available -> { TileValue("Soon"); TileLine("Online features are off in this build") }
            !signedIn -> {
                Text("Compete", style = FitType.section, color = th.accentBright, maxLines = 1)
                TileLine("Race friends on steps")
                Spacer(Modifier.weight(1f))
                CompactPill("Sign in", Duo.Person, onOpen, height = 34.dp)
            }
            b == null -> TileLine("Loading…")
            else -> {
                val me = b.indexOfFirst { it.me }
                if (me < 0 || b.size <= 1) { TileValue("—"); TileLine("Add a friend to compete") }
                else {
                    TileValue("#${me + 1}", "of ${b.size}")
                    TileLine("Steps · this week")
                    Spacer(Modifier.height(8.dp))
                    TileLine("${Fmt.int(b[me].value)} steps", th.textFaint)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ bits

/** Small glossy accent pill — icon + label on one line, never wraps. */
@Composable
internal fun CompactPill(text: String, icon: ImageVector, onClick: () -> Unit, height: Dp = 38.dp) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    Row(
        Modifier.height(height).clip(RoundedCornerShape(height / 2))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent)))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, size.height * 0.5f))
            }
            .clickableNoRipple { tick(); onClick() }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = th.onAccent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = FitType.label, color = th.onAccent, maxLines = 1, softWrap = false)
    }
}
