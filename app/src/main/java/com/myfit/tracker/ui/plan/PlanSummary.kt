package com.myfit.tracker.ui.plan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.LengthUnit
import com.myfit.tracker.domain.PlanProfile
import com.myfit.tracker.domain.Units
import com.myfit.tracker.domain.WeightUnit
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlin.math.roundToInt

/**
 * R36 "Your plan" — the personalised-plan summary, shown at the end of onboarding and in Plan my day → Report.
 * Four tidy blocks: About you (+BMI), What we'll train, How hard, Where.
 */
@Composable
fun PlanSummaryCards(s: PlanProfile.Summary, weightUnit: WeightUnit, lengthUnit: LengthUnit) {
    val th = LocalFitTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ---- About you
        GlassCard {
            Text("ABOUT YOU", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val h = if (lengthUnit == LengthUnit.CM) "${s.heightCm.roundToInt()} cm" else { val i = (s.heightCm / Units.CM_PER_IN).roundToInt(); "${i / 12}′${i % 12}″" }
                StatBox("Height", h, Modifier.weight(1f)); StatBox("Weight", Fmt.weight(s.weightKg, weightUnit, 1), Modifier.weight(1f)); StatBox("Age", "${s.age}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BMI", style = FitType.label, color = th.text, modifier = Modifier.weight(1f))
                Text("%.1f".format(s.bmi), style = FitType.section, color = th.text)
                Spacer(Modifier.width(8.dp))
                Text(s.bmiClass, style = FitType.caption, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bmiColor(s.bmiClass)).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(6.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFEE58), Color(0xFFFFA726), Color(0xFFEF5350)))))
                val f = ((s.bmi - 15) / 25).toFloat().coerceIn(0f, 1f)
                Box(Modifier.align(Alignment.CenterStart).offset(x = maxWidth * f - 7.dp).size(14.dp).clip(CircleShape).background(Color.White).border(3.dp, bmiColor(s.bmiClass), CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            InfoRow("Goal", s.goalLabel); InfoRow("Target body shape", s.targetShape); InfoRow("Fitness level", s.ability)
        }
        // ---- Practice suggestions
        GlassCard {
            Text("YOUR PLAN", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Hex(s.goalLabel.substringBefore(' ').replaceFirstChar { it.uppercase() } + "\n" + s.goalLabel.substringAfter(' ', ""), "Goal", Modifier.weight(1f))
                Hex("${s.workouts}\nworkouts", "Over ${s.weeks} weeks", Modifier.weight(1f))
                Hex("${s.perWeek}×\nper week", "Frequency", Modifier.weight(1f))
            }
        }
        // ---- Intensity + training mix
        GlassCard {
            Text("INTENSITY", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(6.dp))
            IntensityGauge(s.intensity)
            Spacer(Modifier.height(14.dp))
            Text("TRAINING MIX", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape)) {
                Box(Modifier.weight(s.anaerobic.toFloat()).fillMaxSize().background(Color(0xFFFF9F43)))
                Box(Modifier.width(3.dp).fillMaxSize())
                Box(Modifier.weight(s.aerobic.toFloat()).fillMaxSize().background(Color(0xFF2ED573)))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                Text("Strength ${s.anaerobic}%", style = FitType.caption, color = Color(0xFFFF9F43), modifier = Modifier.weight(1f))
                Text("Cardio ${s.aerobic}%", style = FitType.caption, color = Color(0xFF2ED573))
            }
        }
        // ---- Target areas
        GlassCard {
            Text("TARGET AREAS", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(10.dp))
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.focus.forEach { Text(it, style = FitType.label, color = th.onAccent, modifier = Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(th.accentBright, th.accent))).padding(horizontal = 14.dp, vertical = 7.dp)) }
            }
        }
        Caption("Built from your answers. Estimates, not medical advice — redo your setup any time in Settings.", Modifier.padding(horizontal = 6.dp))
    }
}

private fun bmiColor(c: String) = when (c) { "Underweight" -> Color(0xFF42A5F5); "Healthy" -> Color(0xFF43A047); "Overweight" -> Color(0xFFFB8C00); else -> Color(0xFFE53935) }

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(th.text.copy(alpha = 0.07f)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = FitType.caption, color = th.textDim)
        Text(value, style = FitType.section, color = th.text, maxLines = 1)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.body, color = th.textDim, modifier = Modifier.weight(1f))
        Text(value, style = FitType.label, color = th.accentBright)
    }
}

/** Hexagon badge (drawn) with two-line value and a caption. */
@Composable
private fun Hex(value: String, caption: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            val c1 = th.accentBright; val c2 = th.accent
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2; val cx = center.x; val cy = center.y
                val p = androidx.compose.ui.graphics.Path().apply {
                    for (i in 0..5) {
                        val a = Math.toRadians((60.0 * i) - 90.0)
                        val x = cx + r * kotlin.math.cos(a).toFloat(); val y = cy + r * kotlin.math.sin(a).toFloat()
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                drawPath(p, Brush.verticalGradient(listOf(c1.copy(alpha = 0.35f), c2.copy(alpha = 0.18f))))
                drawPath(p, c1, style = Stroke(2.dp.toPx()))
            }
            Text(value, style = FitType.label, color = th.text, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        Text(caption, style = FitType.caption, color = th.textDim, textAlign = TextAlign.Center)
    }
}

/** L1–L3 half-dial with a needle that swings in. */
@Composable
private fun IntensityGauge(level: Int) {
    val th = LocalFitTheme.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(level) { a.animateTo(level.toFloat(), tween(900)) }
    Box(Modifier.fillMaxWidth().height(120.dp)) {
        val accent = th.accentBright; val dim = th.text.copy(alpha = 0.12f); val needle = th.text
        Canvas(Modifier.fillMaxSize()) {
            val r = minOf(size.width / 2, size.height) * 0.9f
            val c = Offset(size.width / 2, size.height * 0.95f)
            val tl = Offset(c.x - r, c.y - r); val sz = Size(r * 2, r * 2)
            for (i in 0..2) {
                val on = i < level
                drawArc(if (on) accent.copy(alpha = 0.5f + 0.25f * i) else dim, 180f + i * 60f + 2f, 56f, false, tl, sz, style = Stroke(18.dp.toPx(), cap = StrokeCap.Butt))
            }
            val ang = Math.toRadians(180.0 + (a.value - 0.5) * 60.0).coerceIn(Math.PI, 2 * Math.PI)
            val tip = Offset(c.x + (r - 26.dp.toPx()) * kotlin.math.cos(ang).toFloat(), c.y + (r - 26.dp.toPx()) * kotlin.math.sin(ang).toFloat())
            drawLine(needle, c, tip, 4.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(needle, 7.dp.toPx(), c)
        }
        Text("L$level", style = FitType.title, color = th.text, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).offset(x = 34.dp))
        Text(when (level) { 1 -> "Gentle start"; 3 -> "Push hard"; else -> "Steady challenge" }, style = FitType.caption, color = th.textDim, modifier = Modifier.align(Alignment.TopCenter))
    }
}
