package com.myfit.tracker.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Glass bottom sheet with a springy entrance and drag-to-dismiss. */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val th = LocalFitTheme.current
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    if (visible) BackHandler(onBack = onDismiss)
    // while any sheet is open the floating dock hides, so sheets are never drawn under it
    androidx.compose.runtime.DisposableEffect(visible) {
        if (visible) SheetsOpen.count.intValue++
        onDispose { if (visible) SheetsOpen.count.intValue = (SheetsOpen.count.intValue - 1).coerceAtLeast(0) }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = if (th.isLight) 0.25f else 0.5f))
                    .clickableNoRipple(onDismiss)
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.8f, 380f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            val maxH = LocalConfiguration.current.screenHeightDp.dp * 0.92f
            Glass(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .heightIn(max = maxH)
                    .imePadding(),
                shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                blur = 34.dp,
                tint = if (th.isLight) Color(0xCCFFFFFF) else th.bgBottom.copy(alpha = 0.55f),
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                    // grabber — drag it down to dismiss
                    Box(
                        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            if (drag.value > 140f) onDismiss() else drag.animateTo(0f, spring(0.7f, 500f))
                                        }
                                    },
                                ) { ch, dy ->
                                    ch.consume()
                                    scope.launch { drag.snapTo((drag.value + dy).coerceAtLeast(0f)) }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(th.textFaint))
                    }
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
                        content = content,
                    )
                }
            }
        }
    }
}


/** Number of GlassSheets currently open (the dock hides while > 0). */
object SheetsOpen { val count = androidx.compose.runtime.mutableIntStateOf(0) }
