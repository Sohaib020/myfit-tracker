package com.myfit.tracker.ui.cycle

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.domain.CyclePrediction
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cycle palette shared by the screen, calendar and tile. */
internal object CycleColors {
    val period = Color(0xFFFF5C8A)
    val fertile = Color(0xFF2EC4B6)
    val ovulation = Color(0xFF9B7BFF)

    /** Fill strength for each flow code (1 spotting … 4 heavy). */
    fun flowAlpha(flow: Int): Float = when (flow) { 1 -> 0.0f; 2 -> 0.45f; 3 -> 0.72f; else -> 1f }
}

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/**
 * Month grid (Monday first). Swipe sideways or use the arrows to change month. Tap a day to log it.
 * Logged bleeding = filled circle (stronger = heavier), spotting = small ring, predicted period = dashed ring,
 * fertile window estimate = soft teal tint, estimated ovulation = soft violet tint, today = bold outline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CycleCalendar(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    flows: Map<LocalDate, Int>,
    otherLogs: Set<LocalDate>,
    pred: CyclePrediction,
    bbtShiftDays: Set<LocalDate>,
    today: LocalDate,
    onDay: (LocalDate) -> Unit,
) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    val curMonth by rememberUpdatedState(month)
    val curOnMonth by rememberUpdatedState(onMonth)
    val acc = remember { mutableFloatStateOf(0f) }

    Column(
        Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { acc.floatValue = 0f },
                onDragEnd = {
                    val a = acc.floatValue
                    if (a > 90f) { tick(); curOnMonth(curMonth.minusMonths(1)) }
                    else if (a < -90f) { tick(); curOnMonth(curMonth.plusMonths(1)) }
                    acc.floatValue = 0f
                },
                onDragCancel = { acc.floatValue = 0f },
            ) { ch, dx -> ch.consume(); acc.floatValue += dx }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Duo.KeyboardArrowLeft, { onMonth(month.minusMonths(1)) }, size = 38.dp)
            Text(
                month.format(monthFmt), style = FitType.section, color = th.text,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            GlassIconButton(Duo.KeyboardArrowRight, { onMonth(month.plusMonths(1)) }, size = 38.dp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, style = FitType.overline, color = th.textFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value   // 0..6
        val total = month.lengthOfMonth()
        val rows = (lead + total + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (cIdx in 0 until 7) {
                    val dayNum = r * 7 + cIdx - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..total) {
                            val d = month.atDay(dayNum)
                            DayCell(
                                day = d,
                                flow = flows[d],
                                predicted = d in pred.predictedPeriodDays,
                                fertile = d in pred.fertileDays,
                                ovulation = d in pred.ovulationDays,
                                hasOther = d in otherLogs,
                                bbtShift = d in bbtShiftDays,
                                isToday = d == today,
                                isFuture = d.isAfter(today),
                                onClick = { onDay(d) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LegendDot(CycleColors.period, filled = true, label = "Period")
            LegendDot(CycleColors.period, filled = false, label = "Predicted")
            LegendSwatch(CycleColors.fertile, "Fertile (est.)")
            LegendSwatch(CycleColors.ovulation, "Ovulation (est.)")
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    flow: Int?,
    predicted: Boolean,
    fertile: Boolean,
    ovulation: Boolean,
    hasOther: Boolean,
    bbtShift: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
) {
    val th = LocalFitTheme.current
    val bleeding = flow != null && flow >= 2
    val spotting = flow == 1
    val fillAlpha = if (bleeding && flow != null) CycleColors.flowAlpha(flow) else 0f
    val textColor = when {
        bleeding && fillAlpha >= 0.7f -> Color.White
        isFuture && !predicted && !fertile -> th.textDim
        else -> th.text
    }
    Box(
        Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick)
            .drawBehind {
                val r = size.minDimension / 2f
                val c = Offset(size.width / 2f, size.height / 2f)
                // window tints (behind everything)
                if (ovulation) drawRoundRect(CycleColors.ovulation.copy(alpha = 0.26f), cornerRadius = CornerRadius(12.dp.toPx()))
                else if (fertile) drawRoundRect(CycleColors.fertile.copy(alpha = 0.16f), cornerRadius = CornerRadius(12.dp.toPx()))
                when {
                    bleeding -> drawCircle(CycleColors.period.copy(alpha = fillAlpha), r * 0.86f, c)
                    spotting -> drawCircle(CycleColors.period, r * 0.80f, c, style = Stroke(1.5.dp.toPx()))
                    predicted -> drawCircle(
                        CycleColors.period.copy(alpha = 0.85f), r * 0.80f, c,
                        style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                    )
                }
                if (isToday) drawCircle(th.text, r * 0.97f, c, style = Stroke(2.dp.toPx()))
                if (hasOther) drawCircle(if (bleeding && fillAlpha >= 0.7f) Color.White else th.textDim, 1.8.dp.toPx(), Offset(c.x, size.height - 5.dp.toPx()))
                if (bbtShift) drawCircle(CycleColors.ovulation, 2.6.dp.toPx(), Offset(size.width - 6.dp.toPx(), 6.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = FitType.label.copy(fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold),
            color = textColor,
        )
    }
}

@Composable
private fun LegendDot(color: Color, filled: Boolean, label: String) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(10.dp).drawBehind {
                if (filled) drawCircle(color)
                else drawCircle(color, style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
            }
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = FitType.caption, color = th.textDim, maxLines = 1)
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = 0.45f)))
        Spacer(Modifier.width(4.dp))
        Text(label, style = FitType.caption, color = th.textDim, maxLines = 1)
    }
}
