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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val realBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Haptic tick that respects the user's setting. */
@Composable
fun rememberTick(): () -> Unit {
    val h = LocalHapticFeedback.current
    val on = LocalSettings.current.haptics
    return remember(h, on) { { if (on) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

/**
 * Liquid-glass panel. On Android 12+ the shared backdrop is re-drawn behind the panel, offset to
 * the panel's on-screen position and blurred with a RenderEffect, then tinted and rimmed with a
 * specular edge. Below Android 12 it falls back to a translucent frosted fill.
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    blur: Dp = 26.dp,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    pressScale: Float = 0.97f,
    content: @Composable BoxScope.() -> Unit,
) {
    val b = LocalBackdrop.current
    val th = LocalFitTheme.current
    val strength = LocalSettings.current.glassStrength
    val pos = remember { mutableStateOf(Offset.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) pressScale else 1f, spring(0.45f, 700f), label = "glassPress")
    val glow by animateFloatAsState(if (pressed && onClick != null) 1f else 0f, label = "glassGlow")
    val tick = rememberTick()
    val blurPx = with(LocalDensity.current) { (blur * strength.coerceIn(0.4f, 1.6f)).toPx() }
    val fill = tint ?: if (realBlurSupported) th.glassTint.copy(alpha = (th.glassTint.alpha * strength).coerceIn(0f, 1f)) else th.glassFallback

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { pos.value = it.positionInRoot() }
            .clip(shape)
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null) { tick(); onClick() }
                else Modifier
            )
    ) {
        if (realBlurSupported) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                        clip = true
                    }
                    .drawBehind {
                        val p = pos.value
                        translate(-p.x, -p.y) {
                            drawBackdrop(b.theme, b.image, b.time.floatValue, b.rootSize.width, b.rootSize.height)
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
                    // specular sheen across the top
                    drawOutline(
                        outline,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = if (th.isLight) 0.35f else 0.10f + glow * 0.08f), Color.Transparent),
                            0f, size.height * 0.55f,
                        )
                    )
                    // rim light: bright top-left, faint in the middle, soft return bottom-right
                    drawOutline(
                        outline,
                        Brush.linearGradient(
                            listOf(th.rimHigh, th.rimLow, th.rimLow, th.rimHigh.copy(alpha = th.rimHigh.alpha * 0.55f)),
                            Offset.Zero, Offset(size.width, size.height),
                        ),
                        style = Stroke(width = 1.2.dp.toPx()),
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
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = th.onAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = FitType.section, color = th.onAccent, textAlign = TextAlign.Center)
        }
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
        Row(Modifier.align(Alignment.Center).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = th.text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = FitType.label.copy(fontSize = FitType.body.fontSize), color = th.text)
        }
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color? = null) {
    val th = LocalFitTheme.current
    Glass(modifier.size(size), shape = CircleShape, onClick = onClick, pressScale = 0.88f) {
        Icon(icon, null, tint = tint ?: th.text, modifier = Modifier.align(Alignment.Center).size(size * 0.46f))
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
            Text(text, style = FitType.label, color = if (selected) th.onAccent else th.text)
        }
    }
}
