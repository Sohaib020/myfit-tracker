package com.myfit.tracker.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Responsive layout for every screen shape: phones (compact), tall phones, wide phones, foldables (Fold cover and
 * inner screen) and tablets.
 *
 *  - [WindowInfo] says how much room there is (width class, tall/short, foldable-sized).
 *  - [adaptiveWidth] keeps reading content to a comfortable column on wide screens instead of stretching
 *    edge-to-edge (the backdrop still fills the screen).
 *  - [FitText] keeps a label on ONE line by shrinking it a little when space is tight — buttons never wrap
 *    letter-by-letter.
 */
data class WindowInfo(val widthDp: Float, val heightDp: Float) {
    enum class Width { COMPACT, MEDIUM, EXPANDED }
    val width: Width = when { widthDp < 600f -> Width.COMPACT; widthDp < 840f -> Width.MEDIUM; else -> Width.EXPANDED }
    /** Narrow phones (< 360 dp) and the Galaxy Fold cover screen. */
    val narrow: Boolean get() = widthDp < 360f
    /** 20:9 and taller. */
    val tall: Boolean get() = heightDp / widthDp.coerceAtLeast(1f) > 2.1f
    /** Landscape phones / short windows (split-screen). */
    val short: Boolean get() = heightDp < 560f
    /** Max width of a single reading column on this window. */
    val contentMax: Dp get() = when (width) { Width.COMPACT -> Dp.Unspecified; Width.MEDIUM -> 640.dp; Width.EXPANDED -> 720.dp }
    /** Columns for card grids (e.g. Home tiles): 1 on phones, 2 on unfolded foldables / tablets. */
    val gridColumns: Int get() = if (width == Width.COMPACT) 1 else 2
}

val LocalWindowInfo = staticCompositionLocalOf { WindowInfo(400f, 860f) }

/** Measures the window once at the root and provides [LocalWindowInfo] to everything below. */
@Composable
fun ProvideWindowInfo(content: @Composable () -> Unit) {
    BoxWithConstraints {
        val info = remember(maxWidth, maxHeight) { WindowInfo(maxWidth.value, maxHeight.value) }
        CompositionLocalProvider(LocalWindowInfo provides info) { content() }
    }
}

/** Width cap for a centred reading column (put BEFORE fillMaxSize, inside a parent that centres it). */
fun WindowInfo.column(): Modifier = if (width == WindowInfo.Width.COMPACT) Modifier else Modifier.widthIn(max = contentMax)

/** Centre content in a readable column on medium/expanded widths; no-op on phones. */
fun Modifier.adaptiveWidth(info: WindowInfo): Modifier =
    if (info.width == WindowInfo.Width.COMPACT) this.fillMaxWidth()
    else this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = info.contentMax)

/**
 * One-line text that shrinks (down to [minScale] of its size) instead of wrapping or clipping, then ellipsises
 * as a last resort. Use for buttons, pills, chips and tight headers.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    minScale: Float = 0.72f,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableFloatStateOf(0f) }
    val size: TextUnit = style.fontSize
    Text(
        text,
        style = if (size == TextUnit.Unspecified) style else style.copy(fontSize = size * scale, letterSpacing = style.letterSpacing),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier.drawWithContent { if (ready > 0f || scale <= minScale) drawContent() },
        onTextLayout = { r ->
            if (r.hasVisualOverflow && scale > minScale) scale = (scale - 0.07f).coerceAtLeast(minScale) else ready = 1f
        },
    )
}

/**
 * Button content that always stays readable: icon + one-line label when it fits; on tight widths (Fold cover,
 * two buttons side by side) the icon is dropped first, then the label may shrink a little, and only then wrap to
 * two centred lines — never "Build …".
 */
@Composable
fun ButtonLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, iconSize: Dp, color: Color, style: TextStyle) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val avail = constraints.maxWidth
        val full = remember(text, style) { measurer.measure(text, style, maxLines = 1, softWrap = false).size.width }
        val iconPx = with(density) { (iconSize + 8.dp).roundToPx() }
        val unbounded = avail == androidx.compose.ui.unit.Constraints.Infinity
        val showIcon = icon != null && (unbounded || full + iconPx <= avail)
        val oneLine = unbounded || full * 0.82f <= avail - (if (showIcon) iconPx else 0)
        if (oneLine) androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon) {
                androidx.compose.material3.Icon(icon!!, null, tint = color, modifier = Modifier.size(iconSize))
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            }
            FitText(text, style, color, textAlign = TextAlign.Center, minScale = 0.82f)
        } else {
            val fs = if (style.fontSize == TextUnit.Unspecified) style.fontSize else style.fontSize * 0.88f
            Text(text, style = style.copy(fontSize = fs, lineHeight = if (fs == TextUnit.Unspecified) fs else fs * 1.08f), color = color,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}
