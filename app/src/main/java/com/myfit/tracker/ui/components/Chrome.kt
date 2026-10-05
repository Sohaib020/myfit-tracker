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

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy != 0f && limit > 0f) offset = (offset + dy).coerceIn(-limit, 0f)
            return Offset.Zero     // never steal scroll from the list — the chrome just follows it
        }
    }
}

val LocalChrome = staticCompositionLocalOf { ChromeState() }

/** Extra top padding a tab's list must add because a header is drawn over it (Train's title + segments). */
val LocalTopInset = compositionLocalOf<Dp> { 0.dp }
