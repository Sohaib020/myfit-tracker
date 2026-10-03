package com.myfit.tracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

/** Softly fades content out at the top edge (lists scrolling under a fixed header). */
fun Modifier.fadeTopEdge(height: androidx.compose.ui.unit.Dp = 18.dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = height.toPx()
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black), 0f, h),
            size = androidx.compose.ui.geometry.Size(size.width, h),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
