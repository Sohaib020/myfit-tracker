package com.myfit.tracker.ui.activity

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Targets
import com.myfit.tracker.health.HealthSync
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme

private val STEPS_C = Color(0xFF7ED957)
private val ACTIVE_C = Color(0xFF3CC8F0)
private val KCAL_C = Color(0xFFB66DFF)
private const val ACTIVE_TARGET = 90
private const val KCAL_TARGET = 500.0

/** Today in detail: rings, hourly steps / active time / calories, motion, time, calories and monthly badge progress. */
@Composable
fun TodayScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val today = remember { Clock.today() }
    val day by remember { container.healthRepo.day(today) }.collectAsState(initial = null)
    val month by remember { container.healthRepo.dailyRange(today.withDayOfMonth(1), today) }.collectAsState(initial = emptyList())
    val targets by container.profileRepo.targets.collectAsState(initial = emptyList())
    val stepTarget = Targets.on(targets, TargetType.STEPS, today) ?: 6000.0
    var hourly by remember { mutableStateOf<HealthSync.Hourly?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hourly = runCatching { container.healthSync.hourly(today) }.getOrNull(); loaded = true }

    val d = day?.daily
    val steps = d?.steps ?: day?.phoneSteps ?: hourly?.steps?.sum() ?: 0L
    val activeMin = hourly?.activeMin?.sum() ?: 0
    val kcal = d?.activeKcal ?: hourly?.kcal?.sum() ?: 0.0

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Today", { nav.pop() }, "Daily activity")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ---- rings + three targets
            GlassCard(padding = 16.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Rings(listOf((steps / stepTarget).toFloat() to STEPS_C, (activeMin / ACTIVE_TARGET.toFloat()) to ACTIVE_C, (kcal / KCAL_TARGET).toFloat() to KCAL_C), 170.dp)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Target("Steps", Fmt.int(steps), "/${Fmt.int(stepTarget.toLong())}", STEPS_C, Modifier.weight(1f))
                    Target("Active time", "$activeMin", "/$ACTIVE_TARGET mins", ACTIVE_C, Modifier.weight(1f))
                    Target("Activity ${com.myfit.tracker.domain.EnergyUnit.label}", Fmt.int(kcal.toLong()), "/${KCAL_TARGET.toInt()}", KCAL_C, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                Line("Total burned calories", d?.totalKcal?.let { "${Fmt.int(it.toLong())} ${com.myfit.tracker.domain.EnergyUnit.label}" } ?: "—")
                Line("Distance", d?.distanceM?.let { "${Fmt.trim(it / 1000.0, 2)} km" } ?: "—")
            }
            // ---- hourly charts
            GlassCard(padding = 16.dp) {
                val h = hourly
                if (h == null) {
                    Text("Hourly activity", style = FitType.section, color = th.text)
                    Caption(if (!loaded) "Loading…" else "Connect Health Connect to see your day hour by hour.")
                    if (loaded) { Spacer(Modifier.height(10.dp)); GlassButton("Connect", { nav.push(Overlay.Activity) }, height = 40.dp) }
                } else {
                    HourChart("Steps", h.steps.map { it.toDouble() }, STEPS_C)
                    Spacer(Modifier.height(18.dp))
                    HourChart("Active time", h.activeMin.map { it.toDouble() }, ACTIVE_C)
                    Spacer(Modifier.height(18.dp))
                    HourChart("Activity calories", h.kcal.toList(), KCAL_C)
                }
            }
            // ---- motion / time / calories
            GlassCard(padding = 16.dp) {
                Text("Motion", style = FitType.section, color = th.text)
                Spacer(Modifier.height(8.dp))
                Row { Pair2("Floors", (hourly?.floors ?: d?.floors)?.let { Fmt.int(it.toLong()) } ?: "—", Modifier.weight(1f)); Pair2("Active hours", hourly?.let { h -> "${h.steps.count { it >= 250 }}" } ?: "—", Modifier.weight(1f)) }
                Spacer(Modifier.height(14.dp))
                Text("Time", style = FitType.section, color = th.text)
                Spacer(Modifier.height(8.dp))
                Row { Pair2("Exercise time", hourly?.let { "${it.exerciseMin} mins" } ?: "—", Modifier.weight(1f)); Pair2("Active time", "$activeMin mins", Modifier.weight(1f)) }
                Spacer(Modifier.height(14.dp))
                Text("Calories", style = FitType.section, color = th.text)
                Spacer(Modifier.height(8.dp))
                Row { Pair2("Exercise calories", hourly?.let { "${Fmt.int(it.exerciseKcal.toLong())} ${com.myfit.tracker.domain.EnergyUnit.label}" } ?: "—", Modifier.weight(1f)); Pair2("Activity calories", "${Fmt.int(kcal.toLong())} ${com.myfit.tracker.domain.EnergyUnit.label}", Modifier.weight(1f)) }
            }
            // ---- monthly badge progress
            val hit = month.count { (it.steps ?: 0L) >= stepTarget }
            val goal = 25
            GlassCard(padding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Badge progress", style = FitType.section, color = th.text)
                        Caption("Reach your daily step target on $goal days this month to earn the monthly badge.")
                    }
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFFFC83D).copy(alpha = if (hit >= goal) 0.9f else 0.15f)).border(1.dp, Color(0xFFFFC83D).copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("$goal", style = FitType.section, color = if (hit >= goal) Color(0xFF3A2A00) else Color(0xFFFFC83D))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$hit", style = FitType.metric, color = th.text); Text(" /$goal days", style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(th.text.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((hit / goal.toFloat()).coerceIn(0.02f, 1f)).clip(CircleShape).background(Color(0xFFFFC83D)))
                }
            }
            GlassButton("Activity & heart details", { nav.push(Overlay.Activity) }, Modifier.fillMaxWidth(), height = 44.dp)
        }
    }
}

@Composable
private fun Rings(rings: List<Pair<Float, Color>>, size: Dp) {
    val th = LocalFitTheme.current
    val anim = rings.map { (f, _) -> animateFloatAsState(f.coerceIn(0f, 1f), tween(1100), label = "r").value }
    Canvas(Modifier.size(size)) {
        val stroke = size.toPx() * 0.085f
        rings.forEachIndexed { i, (_, c) ->
            val inset = stroke / 2 + i * (stroke + 6.dp.toPx())
            val tl = Offset(inset, inset); val sz = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(th.text.copy(alpha = 0.07f), 0f, 360f, false, tl, sz, style = Stroke(stroke))
            drawArc(c, -90f, 360f * anim[i], false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Target(label: String, value: String, of: String, c: Color, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, th.text.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = FitType.caption, color = c, maxLines = 1)
        Text(value, style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Caption(of)
    }
}

@Composable
private fun Line(l: String, v: String) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(l, style = FitType.body, color = th.textDim, modifier = Modifier.weight(1f)); Text(v, style = FitType.label, color = th.text)
    }
}

@Composable
private fun Pair2(l: String, v: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier) { Caption(l); Text(v, style = FitType.section, color = th.text) }
}

/** 24 hourly bars with axis ticks and the most active hour called out. */
@Composable
private fun HourChart(title: String, vals: List<Double>, c: Color) {
    val th = LocalFitTheme.current
    val max = (vals.maxOrNull() ?: 0.0).coerceAtLeast(1e-6)
    val peak = vals.indices.maxByOrNull { vals[it] }?.takeIf { vals[it] > 0 }
    val grow by animateFloatAsState(1f, tween(900), label = "hc")
    Text(title, style = FitType.section, color = th.text)
    Caption(peak?.let { "Most active period: ${hr(it)} – ${hr(it + 1)}" } ?: "No activity recorded yet")
    Spacer(Modifier.height(8.dp))
    Canvas(Modifier.fillMaxWidth().height(80.dp)) {
        val gap = 3.dp.toPx(); val bw = (size.width - gap * 23) / 24
        vals.forEachIndexed { i, v ->
            val h = ((v / max).toFloat() * size.height * grow)
            if (v > 0) drawRoundRect(c, Offset(i * (bw + gap), size.height - h.coerceAtLeast(2f)), Size(bw, h.coerceAtLeast(2f)), CornerRadius(bw / 2))
            else drawRoundRect(th.text.copy(alpha = 0.08f), Offset(i * (bw + gap), size.height - 2.dp.toPx()), Size(bw, 2.dp.toPx()), CornerRadius(1f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("12 AM", "6 AM", "12 PM", "6 PM").forEach { Text(it, style = FitType.caption, color = th.textFaint, modifier = Modifier.weight(1f)) }
        Text("(h)", style = FitType.caption, color = th.textFaint)
    }
}

private fun hr(h: Int): String = when (val x = h % 24) { 0 -> "12 AM"; 12 -> "12 PM"; in 1..11 -> "$x AM"; else -> "${x - 12} PM" }
