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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Top chrome that scrolls away with the content and comes back as soon as you scroll up (like Chrome / Samsung apps).
 * The tab area reports every scroll through [connection]; the floating Me / Friends / Daily log / + buttons and the
 * fixed tab headers (Train) translate by [offset] (0 = fully shown, -[limit] = fully hidden).
 */
@Stable
class ChromeState {
    var offset by mutableFloatStateOf(0f)
    /** How far the chrome can travel (px): the tallest header currently on screen. */
    var limit by mutableFloatStateOf(0f)
    var baseLimit = 0f

    fun show() { offset = 0f }

    /**
     * Scrolling down hides the chrome; it only comes back once the list is back at the very top (the leftover upward
     * scroll the list can't use), not on every small upward flick.
     */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy < 0f && limit > 0f) offset = (offset + dy).coerceIn(-limit, 0f)
            return Offset.Zero     // never steal scroll from the list — the chrome just follows it
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y > 0f && limit > 0f) offset = (offset + available.y).coerceIn(-limit, 0f)
            return Offset.Zero
        }
        override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
            if (available.y > 0f) offset = 0f      // flung all the way back to the top
            return androidx.compose.ui.unit.Velocity.Zero
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
