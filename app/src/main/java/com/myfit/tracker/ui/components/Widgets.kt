package com.myfit.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick

/** Standard card = glass panel + inner padding + vertical content. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Glass(modifier, onClick = onClick) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Duotone glass tile: a frosted rounded square washed with the colour, with a glowing duotone glyph. */
@Composable
fun IconBubble(icon: ImageVector, color: Color, size: Dp = 36.dp) {
    val th = com.myfit.tracker.ui.theme.LocalFitTheme.current
    val corner = size * 0.32f
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
    val glyph = if (th.isLight) Color(color.red * 0.8f, color.green * 0.8f, color.blue * 0.8f) else
        Color(minOf(1f, color.red * 0.55f + 0.45f), minOf(1f, color.green * 0.55f + 0.45f), minOf(1f, color.blue * 0.55f + 0.45f))
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .drawBehind {
                val r = androidx.compose.ui.geometry.CornerRadius(corner.toPx())
                // colour wash, brighter at the top-left like light through tinted glass
                drawRoundRect(Brush.linearGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.22f)), androidx.compose.ui.geometry.Offset.Zero,
                    androidx.compose.ui.geometry.Offset(this.size.width, this.size.height)), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = if (th.isLight) 0.35f else 0.06f), cornerRadius = r)
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.Transparent), 0f, this.size.height * 0.5f), cornerRadius = r)
                // soft glow behind the glyph
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent)), radius = this.size.minDimension * 0.42f)
                drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f)),
                    androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(this.size.width, this.size.height)),
                    cornerRadius = r, style = Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = glyph, modifier = Modifier.size(size * 0.6f)) }
}

@Composable
fun CardHeader(icon: ImageVector, title: String, color: Color, trailing: @Composable RowScope.() -> Unit = {}) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, color)
        Spacer(Modifier.width(10.dp))
        Text(title, style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Metric(value: String, unit: String? = null, style: TextStyle = FitType.metric, color: Color? = null) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = style, color = color ?: th.text)
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(unit, style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(text, style = FitType.caption, color = color ?: LocalFitTheme.current.textDim, modifier = modifier)
}

enum class DataKind(val label: String) { RECORDED("Recorded"), CALCULATED("Calculated"), ESTIMATED("Estimate"), MISSING("Missing") }

/** Provenance tag shown next to any figure whose origin matters. */
@Composable
fun DataBadge(kind: DataKind) {
    val th = LocalFitTheme.current
    val c = when (kind) {
        DataKind.RECORDED -> th.success; DataKind.CALCULATED -> th.water
        DataKind.ESTIMATED -> th.warning; DataKind.MISSING -> th.textFaint
    }
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 3.dp)
    ) { Text(kind.label.uppercase(), style = FitType.overline.copy(fontSize = FitType.overline.fontSize), color = c) }
}

/** Animated gradient ring. `progress == null` means no data — drawn as a dashed empty track. */
@Composable
fun ProgressRing(
    progress: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    stroke: Dp = 9.dp,
    content: @Composable () -> Unit = {},
) {
    val th = LocalFitTheme.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) { anim.animateTo((progress ?: 0f).coerceIn(0f, 1.25f), tween(900, easing = FastOutSlowInEasing)) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val track = if (th.isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.1f)
            if (progress == null) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))))
            } else {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
                val sweep = 360f * anim.value.coerceAtMost(1f)
                if (sweep > 0f) {
                    // glow
                    drawArc(color.copy(alpha = 0.25f), -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(sw * 2.1f, cap = StrokeCap.Round))
                    drawArc(
                        Brush.sweepGradient(listOf(color.copy(alpha = 0.65f), color, color)),
                        -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun GlassProgressBar(progress: Float?, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val th = LocalFitTheme.current
    val p by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), tween(800), label = "bar")
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height))
            .background(if (th.isLight) Color.Black.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.1f))
    ) {
        if (progress != null && p > 0f) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(p).clip(RoundedCornerShape(height))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)))
            )
        }
    }
}

/** Minimal line chart for real values; gaps (null) break the line instead of being invented. */
@Composable
fun Sparkline(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pts = values.withIndex().filter { it.value != null }
        if (pts.size < 2) return@Canvas
        val min = pts.minOf { it.value!! }; val max = pts.maxOf { it.value!! }
        val range = (max - min).takeIf { it > 1e-9 } ?: 1.0
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        fun y(v: Double) = (size.height * (1 - (v - min) / range)).toFloat() * 0.85f + size.height * 0.075f
        val path = Path()
        var started = false
        values.forEachIndexed { i, v ->
            if (v == null) { started = false; return@forEachIndexed }
            val x = i * stepX
            if (!started) { path.moveTo(x, y(v)); started = true } else path.lineTo(x, y(v))
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.lastOrNull()?.let { drawCircle(color, 4.dp.toPx(), Offset(it.index * stepX, y(it.value!!))) }
    }
}

/** Glass segmented control with a sliding liquid indicator. */
@Composable
fun <T> GlassSegmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val idx = options.indexOf(selected).coerceAtLeast(0)
    val pos by animateFloatAsState(idx.toFloat(), spring(0.7f, 420f), label = "seg")
    val tick = rememberTick()
    Glass(modifier.height(44.dp), shape = RoundedCornerShape(22.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val w = maxWidth / options.size
            Box(
                Modifier.offset(x = w * pos).width(w).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(th.accentBright, th.accent)))
            )
            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { i, o ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                            .then(Modifier.clickableNoRipple { tick(); onSelect(o) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label(o), style = FitType.label, color = if (i == idx) th.onAccent else th.textDim)
                    }
                }
            }
        }
    }
}

/** Large numeric input used across logging sheets. */
@Composable
fun NumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = true,
    big: Boolean = true,
) {
    val th = LocalFitTheme.current
    Glass(modifier.height(if (big) 76.dp else 56.dp), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = { s -> onValueChange(sanitizeNumber(s, decimal)) },
                singleLine = true,
                textStyle = (if (big) FitType.display else FitType.title).copy(color = th.text, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(th.accent),
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text("0", style = (if (big) FitType.display else FitType.title), color = th.textFaint)
                        inner()
                    }
                },
            )
            Text(unit, style = FitType.section, color = th.textDim)
        }
    }
}

fun sanitizeNumber(s: String, decimal: Boolean): String {
    val cleaned = s.replace(',', '.').filter { it.isDigit() || (decimal && it == '.') }
    if (!decimal) return cleaned.take(7)
    val firstDot = cleaned.indexOf('.')
    return (if (firstDot < 0) cleaned else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")).take(9)
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier, label: (Int) -> String = { it.toString() }) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        com.myfit.tracker.ui.theme.GlassIconButton(Duo.Remove, { if (value > range.first) { tick(); onChange(value - 1) } }, size = 40.dp)
        Text(label(value), style = FitType.title, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        com.myfit.tracker.ui.theme.GlassIconButton(Duo.Add, { if (value < range.last) { tick(); onChange(value + 1) } }, size = 40.dp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = FitType.overline, color = LocalFitTheme.current.textDim, modifier = modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp))
}
