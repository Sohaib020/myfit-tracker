package com.myfit.tracker.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Top chrome that scrolls with the content as if it were part of the page.
 * The tab area reports every scroll through [connection]; the floating Me / Friends / Daily log / + buttons and the
 * fixed tab headers (Train) translate by [offset] (0 = fully shown, -[limit] = fully hidden).
 */
@Stable
class ChromeState {
    var offset by mutableFloatStateOf(0f)
    /** How far the chrome can travel (px): the tallest header currently on screen. */
    var limit by mutableFloatStateOf(0f)
    var baseLimit = 0f

    /** How far the current list is scrolled from its top (px), tracked from what the list actually consumed. */
    private var scrolled = 0f

    fun show() { offset = 0f; scrolled = 0f }

    /**
     * The chrome is glued to the page: it moves exactly as far as the list scrolls (1:1, no animation, no snapping)
     * and is fully back only when the top of the page is back on screen.
     */
    val connection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            scrolled = (scrolled - consumed.y).coerceAtLeast(0f)
            if (available.y > 0f) scrolled = 0f          // the list is at its very top
            offset = -scrolled.coerceAtMost(limit.coerceAtLeast(0f))
            return Offset.Zero
        }
    }

    /** 0 = chrome fully shown, 1 = fully hidden (drives the status-bar fade). */
    val hidden: Float get() = if (limit <= 0f) 0f else (-offset / limit).coerceIn(0f, 1f)
}

val LocalChrome = staticCompositionLocalOf { ChromeState() }

/** Extra top padding a tab's list must add because a header is drawn over it (Train's title + segments). */
val LocalTopInset = compositionLocalOf<Dp> { 0.dp }

/**
 * Makes a full-screen layer opaque to touch: taps on its empty areas (or on a text field while the keyboard opens)
 * must never fall through to the screen underneath. It only listens — children still get every event first.
 */
fun androidx.compose.ui.Modifier.blockTouchesBelow(): androidx.compose.ui.Modifier =
    this.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }

/**
 * Soft fade at the left / right edge of a horizontally scrolling row, only on the side that has more content —
 * so cut-off chips read as "scroll for more" instead of a glitch.
 */
fun androidx.compose.ui.Modifier.horizontalFadeEdges(canBack: () -> Boolean, canForward: () -> Boolean, width: Dp = 28.dp): androidx.compose.ui.Modifier =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = width.toPx().coerceAtMost(size.width / 3)
            if (canBack()) drawRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.Transparent), 0f, w),
                size = androidx.compose.ui.geometry.Size(w, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
            )
            if (canForward()) drawRect(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black), size.width - w, size.width),
                topLeft = androidx.compose.ui.geometry.Offset(size.width - w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
            )
        }

fun androidx.compose.ui.Modifier.fadeEdges(state: androidx.compose.foundation.lazy.LazyListState): androidx.compose.ui.Modifier =
    horizontalFadeEdges({ state.canScrollBackward }, { state.canScrollForward })

fun androidx.compose.ui.Modifier.fadeEdges(state: androidx.compose.foundation.ScrollState): androidx.compose.ui.Modifier =
    horizontalFadeEdges({ state.canScrollBackward }, { state.canScrollForward })
