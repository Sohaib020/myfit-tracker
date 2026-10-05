package com.myfit.tracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

/** Vertical snapping wheel (PUMPD height picker). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 52.dp,
    visible: Int = 5,
    /** Draw this wheel's own selection pill. Pass false when several wheels share one band (date pickers). */
    highlight: Boolean = true,
) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(state)
    val center by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - mid) }?.index ?: selected
        }
    }
    val curSel by rememberUpdatedState(selected)
    val curOn by rememberUpdatedState(onSelected)
    LaunchedEffect(state) {
        snapshotFlow { center }.distinctUntilChanged().collect { if (it != curSel) { tick(); curOn(it) } }
    }
    Box(modifier.height(itemHeight * visible), contentAlignment = Alignment.Center) {
        if (highlight) Glass(Modifier.fillMaxWidth(0.86f).height(itemHeight + 6.dp), shape = RoundedCornerShape(16.dp)) {}
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visible / 2)),
            // rows fade out toward the top and bottom edge, like a real wheel
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Transparent, 0.3f to androidx.compose.ui.graphics.Color.Black,
                            0.7f to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { i ->
                val d = abs(i - center)
                Box(Modifier.height(itemHeight).fillMaxWidth().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    // one size for every row (scaled down off-centre) — the selected value never overflows its band
                    FitText(
                        label(i),
                        FitType.title.copy(fontWeight = if (d == 0) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
                        when (d) { 0 -> th.text; 1 -> th.textDim; else -> th.textFaint },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            val s = if (d == 0) 1.08f else 0.92f - d * 0.05f
                            scaleX = s; scaleY = s
                        },
                    )
                }
            }
        }
    }
}

/**
 * Horizontal ruler (PUMPD weight picker). Value in display units, `step` resolution (0.1).
 */
@Composable
fun RulerPicker(
    value: Double,
    onChange: (Double) -> Unit,
    min: Double,
    max: Double,
    step: Double = 0.1,
    modifier: Modifier = Modifier,
) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    val pxPerStep = 14f
    val acc = remember { mutableFloatStateOf(0f) }
    val cur by rememberUpdatedState(value)
    val curOnChange by rememberUpdatedState(onChange)
    Box(modifier.fillMaxWidth().height(96.dp)) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(min, max, step) {
                detectHorizontalDragGestures(onDragEnd = { acc.floatValue = 0f }) { ch, dx ->
                    ch.consume()
                    acc.floatValue -= dx
                    val steps = (acc.floatValue / pxPerStep).toInt()
                    if (steps != 0) {
                        acc.floatValue -= steps * pxPerStep
                        val nv = ((cur / step).roundToInt() + steps) * step
                        val clamped = ((nv.coerceIn(min, max)) * 10).roundToInt() / 10.0
                        if (clamped != cur) { tick(); curOnChange(clamped) }
                    }
                }
            }
        ) {
            val cx = size.width / 2
            val base = (value / step).roundToInt()
            val half = (size.width / 2 / pxPerStep).toInt() + 2
            for (k in -half..half) {
                val idx = base + k
                val v = idx * step
                if (v < min - 1e-9 || v > max + 1e-9) continue
                val x = cx + k * pxPerStep - (acc.floatValue)
                val major = idx % 10 == 0
                val mid = idx % 5 == 0
                val h = when { major -> size.height * 0.55f; mid -> size.height * 0.4f; else -> size.height * 0.28f }
                val fade = 1f - (abs(x - cx) / (size.width / 2)).coerceIn(0f, 1f)
                drawLine(th.text.copy(alpha = 0.25f + 0.6f * fade), Offset(x, size.height * 0.2f), Offset(x, size.height * 0.2f + h), if (major) 3f else 2f)
            }
            // center needle
            drawLine(Brush.verticalGradient(listOf(th.accentBright, th.accent)), Offset(cx, size.height * 0.1f), Offset(cx, size.height * 0.85f), 6f)
            drawCircle(th.accent, 7f, Offset(cx, size.height * 0.9f))
        }
    }
}
