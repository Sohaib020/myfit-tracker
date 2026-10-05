package com.myfit.tracker.ui.theme

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Low-effects mode: Android 12 and older (API ≤ 32). Blur, refraction and see-through glass are switched off and
 * every glass surface is drawn as a solid, theme-coloured card instead — far cheaper on low-end phones.
 * (The dock keeps its animations; only its background becomes solid.)
 */
val lowFx: Boolean get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 || com.myfit.tracker.CrashGuard.safeMode

val realBlurSupported: Boolean get() = !lowFx

/** Solid card colour for low-effects mode: the theme's frosted fill laid over its background. */
fun FitTheme.opaqueSurface(): Color = glassFallback.copy(alpha = 1f).let { f ->
    val a = glassFallback.alpha
    Color(f.red * a + bgTop.red * (1 - a), f.green * a + bgTop.green * (1 - a), f.blue * a + bgTop.blue * (1 - a), 1f)
}

/** Haptic tick that respects the user's setting. */
@Composable
fun rememberTick(): () -> Unit {
    val h = LocalHapticFeedback.current
    val on = LocalSettings.current.haptics
    return remember(h, on) { { if (on) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/**
 * Liquid-glass panel. The shared backdrop layer (recorded once per frame) is replayed behind the
 * panel at its on-screen position, blurred, then bent by the refraction shader near the edges
 * (Android 13+). `seeContent = true` also shows the scrolling content underneath (used by the dock).
 * Android 12: blur only. Older: frosted translucent fill.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    blur: Dp = 26.dp,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    pressScale: Float = 0.97f,
    seeContent: Boolean = false,
    dispersion: Float = 0.05f,
    content: @Composable BoxScope.() -> Unit,
) {
    val b = LocalBackdrop.current
    val th = LocalFitTheme.current
    val st = LocalSettings.current
    val strength = st.glassStrength
    val pos = remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) pressScale else 1f, spring(0.45f, 700f), label = "glassPress")
    val glow by animateFloatAsState(if (pressed && onClick != null) 1f else 0f, label = "glassGlow")
    val tick = rememberTick()
    // Everything samples a backdrop that was blurred ONCE per frame at low resolution:
    // cards use the shared card blur, the dock (`seeContent`) uses its own dock-blur layer.
    // The per-panel refraction shader only runs in "Smooth" motion — it costs one extra GPU pass per panel.
    val refr = st.refraction
    val lens = st.motion == 0 && LiquidGlass.supported && refr > 0.01f && !com.myfit.tracker.CrashGuard.safeMode
    val shader = remember(lens) { if (lens) LiquidGlass.newShader() else null }
    val fill = when {
        lowFx -> th.opaqueSurface().let { base ->          // solid on Android 12 and older; a custom tint is mixed in, never see-through
            if (tint == null) base else Color(tint.red * tint.alpha + base.red * (1 - tint.alpha), tint.green * tint.alpha + base.green * (1 - tint.alpha), tint.blue * tint.alpha + base.blue * (1 - tint.alpha), 1f)
        }
        tint != null -> tint
        else -> th.glassTint.copy(alpha = (th.glassTint.alpha * strength).coerceIn(0f, 1f))
    }

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { pos.value = it.positionInRoot(); if (onClick != null) bounds[0] = it.boundsInRoot() }
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null) { tick(); OpenOrigin.mark(bounds[0]); onClick() }
                else Modifier
            )
    ) {
        if (realBlurSupported) {
            Box(
                Modifier
                    .matchParentSize()
                    .then(if (!lens) Modifier else Modifier.graphicsLayer {
                        val w = size.width; val h = size.height
                        val corner = when (val o = shape.createOutline(size, LayoutDirection.Ltr, this)) {
                            is Outline.Rounded -> o.roundRect.topLeftCornerRadius.x
                            is Outline.Rectangle -> 0f
                            else -> minOf(w, h) / 2f
                        }
                        val bezel = (minOf(w, h) * 0.22f).coerceIn(10.dp.toPx(), 34.dp.toPx())
                        renderEffect = LiquidGlass.effect(
                            shader, w, h, corner, 0f,
                            bezelPx = bezel, strengthPx = bezel * 0.55f * refr,
                            dispersion = (dispersion * refr).coerceIn(0f, 0.6f),
                            highlight = if (th.isLight) 0.10f else 0.16f,
                        )
                        clip = true
                    })
                    .drawBehind {
                        val p = pos.value
                        translate(-p.x, -p.y) {
                            val img = (if (seeContent) b.dockImg else null) ?: b.cardImg
                            val l = (if (seeContent) b.dockLayer else null) ?: b.blurLayer ?: b.layer
                            when {
                                img != null -> drawBaked(img, b.rootSize)
                                l != null -> drawLayer(l)
                                b.bgImg != null -> drawBaked(b.bgImg!!, b.rootSize)
                                else -> drawBackdrop(b.theme, b.image, b.theme.stillT, b.rootSize.width, b.rootSize.height)
                            }
                        }
                    }
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, fill)
                    // soft specular sheen across the top
                    drawOutline(
                        outline,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = if (th.isLight) 0.30f else 0.08f + glow * 0.08f), Color.Transparent),
                            0f, size.height * 0.5f,
                        )
                    )
                    // thin rim: bright top-left, faint elsewhere (no rainbow on cards)
                    drawOutline(
                        outline,
                        Brush.linearGradient(
                            listOf(th.rimHigh, th.rimLow, th.rimLow, th.rimHigh.copy(alpha = th.rimHigh.alpha * 0.5f)),
                            Offset.Zero, Offset(size.width, size.height),
                        ),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
        )
        content()
    }
}

/** Primary call-to-action: glossy accent pill with a coloured glow. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val th = LocalFitTheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.4f, 700f), label = "btn")
    val tick = rememberTick()
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .shadow(if (enabled) 18.dp else 0.dp, shape, ambientColor = th.accent, spotColor = th.accent)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent, th.accent.copy(red = th.accent.red * 0.8f, green = th.accent.green * 0.8f, blue = th.accent.blue * 0.8f))))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), 0f, size.height * 0.5f))
                val o = shape.createOutline(size, layoutDirection, this)
                drawOutline(o, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), style = Stroke(1.dp.toPx()))
            }
            .clickable(interaction, indication = null, enabled = enabled) { tick(); onClick() }
            .defaultMinSize(minHeight = height)
            .padding(horizontal = if (com.myfit.tracker.ui.components.LocalWindowInfo.current.narrow) 14.dp else 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        com.myfit.tracker.ui.components.ButtonLabel(text, icon, 20.dp, th.onAccent, FitType.section)
    }
}

/** Secondary glass pill button. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    val th = LocalFitTheme.current
    Glass(modifier.height(height), shape = RoundedCornerShape(height / 2), onClick = onClick) {
        Box(Modifier.align(Alignment.Center).padding(horizontal = if (com.myfit.tracker.ui.components.LocalWindowInfo.current.narrow) 12.dp else 18.dp), contentAlignment = Alignment.Center) {
            com.myfit.tracker.ui.components.ButtonLabel(text, icon, 18.dp, th.text, FitType.label.copy(fontSize = FitType.body.fontSize))
        }
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color? = null) {
    val th = LocalFitTheme.current
    Glass(
        modifier.size(size), shape = CircleShape, onClick = onClick, pressScale = 0.88f,
        tint = if (th.isLight) Color.White.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.16f),
    ) {
        Icon(icon, null, tint = tint ?: th.text, modifier = Modifier.align(Alignment.Center).size(size * 0.5f))
    }
}

/** Selectable chip — selected state becomes a glossy accent capsule (PUMPD goal chips). */
@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val th = LocalFitTheme.current
    val sel by animateFloatAsState(if (selected) 1f else 0f, spring(0.6f, 500f), label = "chip")
    Glass(modifier.height(40.dp), shape = RoundedCornerShape(20.dp), onClick = onClick, pressScale = 0.92f) {
        Box(
            Modifier.matchParentSize().drawBehind {
                if (sel > 0f) {
                    drawRect(Brush.verticalGradient(listOf(th.accentBright.copy(alpha = sel), th.accent.copy(alpha = sel))))
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f * sel), Color.Transparent), 0f, size.height * 0.5f))
                }
            }
        )
        Row(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = if (selected) th.onAccent else th.textDim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            com.myfit.tracker.ui.components.FitText(text, FitType.label, if (selected) th.onAccent else th.text)
        }
    }
}


/** Where the last tapped card/tile was, so a screen it opens can grow out of it (iOS-style). */
object OpenOrigin {
    private var rect: androidx.compose.ui.geometry.Rect? = null
    private var at = 0L
    fun mark(r: androidx.compose.ui.geometry.Rect?) { rect = r; at = android.os.SystemClock.uptimeMillis() }
    /** The tapped bounds if the tap was in the last moment, else null. Consumed once. */
    fun take(): androidx.compose.ui.geometry.Rect? {
        val r = rect.takeIf { android.os.SystemClock.uptimeMillis() - at < 700 }
        rect = null
        return r
    }
}
