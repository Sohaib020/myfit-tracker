package com.myfit.tracker.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.prefs.DashCard
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Units
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassProgressBar
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.Metric
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.Sparkline
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.pip.Pip
import com.myfit.tracker.ui.pip.PipMood
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DashboardScreen(state: DashState, container: AppContainer, open: (Sheet) -> Unit, bottomPad: Int) {
    val settings = LocalSettings.current
    val cards = settings.dashCards
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Box(Modifier.statusBarsPadding()) { Greeting(state) } }
        if (settings.pipEnabled) item { PipCard(state) }
        item { RingsCard(state, open) }
        if (DashCard.BODY in cards) item { BodyCard(state) { open(Sheet.Weight()) } }
        if (DashCard.HYDRATION in cards) item { HydrationCard(state, container, open) }
        if (DashCard.RECOVERY in cards) item { RecoveryCard(state, open) }
        if (DashCard.STEPS in cards) item { StepsCard(state) { open(Sheet.Steps()) } }
        if (DashCard.CHECKIN in cards) item { CheckInCard(state) { open(Sheet.CheckIn()) } }
        if (DashCard.GOALS in cards) item { GoalsCard(state) }
    }
}

private fun greetingFor(t: LocalTime) = when (t.hour) {
    in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night"
}

@Composable
private fun Greeting(s: DashState) {
    val th = LocalFitTheme.current
    val name = s.profile?.name?.substringBefore(' ') ?: ""
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Glass(Modifier.size(48.dp), shape = CircleShape) {
            Text(name.take(1).uppercase(), style = FitType.title, color = th.text, modifier = Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${greetingFor(LocalTime.now())}, $name", style = FitType.title, color = th.text)
            Caption(s.today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)))
        }
    }
}

// ------------------------------------------------------------------ Pip

private fun pipLine(s: DashState, variant: Int): Pair<PipMood, String> {
    val now = LocalTime.now()
    val p = s.profile
    val nightMin = p?.sleepTimeMin ?: (23 * 60)
    val wakeMin = p?.wakeTimeMin ?: (7 * 60)
    val mNow = now.hour * 60 + now.minute
    val late = if (nightMin > wakeMin) (mNow >= nightMin - 30 || mNow < wakeMin) else (mNow >= nightMin - 30 && mNow < wakeMin)
    val water = s.waterMl; val tw = s.waterTarget
    val nothingToday = s.day.water.isEmpty() && s.day.weight.isEmpty() && s.day.sleep.isEmpty() && s.day.activity.isEmpty() && s.day.checkIns.isEmpty() && s.day.notes.isEmpty()
    val lines = buildList {
        if (late) add(PipMood.SLEEPY to if (s.checkIn == null) "It's getting late. A quick check-in, then rest?" else "Check-in done. Time to recharge — see you tomorrow.")
        if (nothingToday) add(PipMood.WAVE to "Nothing logged yet today. Tap + and I'll keep count.")
        if (water != null && tw != null && water >= tw) add(PipMood.PROUD to "Water target reached — ${Fmt.num(water / 1000, 2)} L logged today!")
        if (tw != null && (water ?: 0.0) < tw * 0.5 && now.hour >= 14) add(PipMood.CONCERNED to "Water so far: ${Fmt.num((water ?: 0.0) / 1000, 2)} of ${Fmt.num(tw / 1000, 2)} L. A glass now?")
        val c7 = s.change7
        if (c7 != null) add(PipMood.THINKING to "Your 7-day weight average moved ${Fmt.signed(c7.delta)} kg vs the week before.")
        if (s.steps != null && s.stepTarget != null && s.steps >= s.stepTarget) add(PipMood.EXCITED to "${Fmt.int(s.steps)} steps — step goal smashed!")
        if (s.sleepMin != null) add(PipMood.HAPPY to "You logged ${Fmt.duration(s.sleepMin)} of sleep last night.")
        add(PipMood.HAPPY to "Tap me any time. I only quote numbers you've logged.")
    }
    return lines[variant % lines.size]
}

@Composable
private fun PipCard(s: DashState) {
    val th = LocalFitTheme.current
    var variant by remember { mutableIntStateOf(0) }
    val (mood, line) = pipLine(s, variant)
    Glass(Modifier.fillMaxWidth().height(120.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Pip(mood, size = 96.dp, onTap = { variant++ })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("PIP", style = FitType.overline, color = th.accentBright)
                Spacer(Modifier.height(4.dp))
                Text(line, style = FitType.body, color = th.text)
            }
        }
    }
}

// ------------------------------------------------------------------ Rings

@Composable
private fun RingsCard(s: DashState, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    GlassCard {
        Text("TODAY'S PROGRESS", style = FitType.overline, color = th.textDim)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(s.waterMl?.let { w -> s.waterTarget?.let { (w / it).toFloat() } }, th.water, size = 132.dp, stroke = 12.dp)
                ProgressRing(s.steps?.let { st -> s.stepTarget?.let { (st / it).toFloat() } }, th.steps, size = 100.dp, stroke = 12.dp)
                ProgressRing(s.sleepMin?.let { m -> s.sleepTarget?.let { (m / it).toFloat() } }, th.sleep, size = 68.dp, stroke = 12.dp)
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RingLegend(th.water, "Water",
                    s.waterMl?.let { Fmt.volume(it, units.volume) } ?: "—",
                    s.waterTarget?.let { "of ${Fmt.volume(it, units.volume)}" }) { open(Sheet.Water()) }
                RingLegend(th.steps, "Steps", s.steps?.let { Fmt.int(it) } ?: "—", s.stepTarget?.let { "of ${Fmt.int(it)}" }) { open(Sheet.Steps()) }
                RingLegend(th.sleep, "Sleep", s.sleepMin?.let { Fmt.duration(it) } ?: "—", s.sleepTarget?.let { "of ${Fmt.duration(it.toLong())}" }) { open(Sheet.Sleep()) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption("Dashed ring = nothing logged yet (not zero).")
    }
}

@Composable
private fun RingLegend(color: Color, label: String, value: String, of: String?, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickableNoRipple(onClick)) {
        Box(Modifier.size(10.dp).clip(CircleShape)) { Canvas(Modifier.fillMaxSize()) { drawCircle(color) } }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = FitType.caption, color = th.textDim)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = FitType.section, color = th.text)
                if (of != null) { Spacer(Modifier.width(4.dp)); Text(of, style = FitType.caption, color = th.textDim) }
            }
        }
    }
}

// ------------------------------------------------------------------ Body

@Composable
private fun BodyCard(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units.weight
    GlassCard(onClick = onClick) {
        CardHeader(Icons.Rounded.MonitorWeight, "Body weight", th.accentBright) {
            if (s.latestWeight != null) DataBadge(DataKind.RECORDED)
        }
        Spacer(Modifier.height(14.dp))
        val lw = s.latestWeight
        if (lw == null) {
            Caption("No weigh-ins yet. Tap to log your first.")
            return@GlassCard
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Caption(if (lw.localDate == Clock.dateKey(s.today)) "Latest · today" else "Latest · ${lw.localDate}")
                Metric(Fmt.trim(Units.kgTo(lw.weightKg, u), 1), u.label, FitType.display)
                if (s.day.weight.size > 1 && s.todayWeightMean != null) Caption("${s.day.weight.size} weigh-ins today · mean ${Fmt.weight(s.todayWeightMean, u)}")
            }
            Sparkline(s.weightSpark.map { it?.let { kg -> Units.kgTo(kg, u) } }, th.accentBright, Modifier.width(120.dp).height(54.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("7-day avg", s.avg7.value?.let { Fmt.weight(it, u) } ?: "—", "${s.avg7.daysWithData}/7 days", Modifier.weight(1f))
            MiniStat("vs prev 7d", s.change7?.let { "${Fmt.signed(Units.kgTo(it.delta, u))} ${u.label}" } ?: "Not enough data", "7-day avgs", Modifier.weight(1f))
            MiniStat("vs 30d ago", s.change30?.let { "${Fmt.signed(Units.kgTo(it.delta, u))} ${u.label}" } ?: "Not enough data", "7-day avgs", Modifier.weight(1f))
        }
        s.profile?.targetWeightKg?.let { t ->
            Spacer(Modifier.height(12.dp))
            val ref = s.avg7.value ?: lw.weightKg
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Flag, null, tint = th.textDim, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Caption("Target ${Fmt.weight(t, u)} · ${Fmt.signed(Units.kgTo(t - ref, u))} ${u.label} from your ${if (s.avg7.value != null) "7-day average" else "latest weigh-in"}")
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, sub: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Glass(modifier, shape = RoundedCornerShape(18.dp), tint = if (th.isLight) Color(0x66FFFFFF) else Color(0x14FFFFFF)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = FitType.caption, color = th.textDim)
            Spacer(Modifier.height(4.dp))
            Text(value, style = FitType.label, color = th.text)
            Text(sub, style = FitType.caption.copy(fontSize = FitType.overline.fontSize), color = th.textFaint)
        }
    }
}

// ------------------------------------------------------------------ Hydration

@Composable
private fun HydrationCard(s: DashState, c: AppContainer, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    GlassCard(onClick = { open(Sheet.Water()) }) {
        CardHeader(Icons.Rounded.WaterDrop, "Hydration", th.water) {
            Caption("${s.day.water.size} ${if (s.day.water.size == 1) "entry" else "entries"}")
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaterGlass(s.waterMl?.let { w -> s.waterTarget?.let { (w / it).toFloat() } } ?: 0f, th.water, Modifier.size(width = 70.dp, height = 110.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Metric(s.waterMl?.let { Fmt.volume(it, units.volume).substringBefore(' ') } ?: "—", s.waterMl?.let { units.volume.label })
                s.waterTarget?.let { t ->
                    Caption("of ${Fmt.volume(t, units.volume)} target")
                    val rem = t - (s.waterMl ?: 0.0)
                    Caption(if (rem > 0) "${Fmt.volume(rem, units.volume)} to go" else "Target reached", color = if (rem <= 0) th.success else null)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(250.0, 500.0).forEach { ml ->
                        GlassChip("+${Fmt.trim(ml, 0)} ml", false, {
                            c.write {
                                val id = c.logRepo.addWater(ml)
                                toaster.show("Added ${Fmt.trim(ml, 0)} ml of water", "Undo") { c.write { c.logRepo.deleteWater(id) } }
                            }
                        }, icon = Icons.Rounded.WaterDrop)
                    }
                }
            }
        }
    }
}

/** Animated glass of water; the level is the real fraction of today's target. */
@Composable
private fun WaterGlass(fraction: Float, color: Color, modifier: Modifier) {
    val th = LocalFitTheme.current
    val level by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1200), label = "water")
    val inf = rememberInfiniteTransition(label = "wave")
    val ph by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ph")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val glass = Path().apply {
            moveTo(w * 0.08f, 0f); lineTo(w * 0.92f, 0f); lineTo(w * 0.8f, h); lineTo(w * 0.2f, h); close()
        }
        drawPath(glass, if (th.isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.06f))
        clipPath(glass) {
            val top = h * (1f - level)
            if (level > 0f) {
                val wave = Path().apply {
                    moveTo(0f, top)
                    var x = 0f
                    while (x <= w) { lineTo(x, top + sin(x / w * 2 * PI.toFloat() * 1.5f + ph) * h * 0.025f); x += w / 20f }
                    lineTo(w, h); lineTo(0f, h); close()
                }
                drawPath(wave, Brush.verticalGradient(listOf(color.copy(alpha = 0.65f), color), top, h))
                // bubbles
                for (i in 0..4) {
                    val bx = w * (0.3f + 0.1f * i)
                    val by = h - ((ph / (2 * PI.toFloat()) + i * 0.2f) % 1f) * (h - top)
                    drawCircle(Color.White.copy(alpha = 0.35f), 2.5f + i % 2, Offset(bx, by))
                }
            }
            drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent, Color.White.copy(alpha = 0.1f))), size = Size(w, h))
        }
        drawPath(glass, Color.White.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx()))
    }
}

// ------------------------------------------------------------------ Recovery

@Composable
private fun RecoveryCard(s: DashState, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    GlassCard(onClick = { open(Sheet.Sleep()) }) {
        CardHeader(Icons.Rounded.Bedtime, "Sleep & recovery", th.sleep)
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Caption("Last night")
                Metric(s.sleepMin?.let { Fmt.duration(it) } ?: "—")
                s.sleepQuality?.let { Caption("Quality $it/10") }
            }
            Column(Modifier.weight(1f)) {
                Caption("7-day average")
                Metric(s.sleepAvg7.value?.let { Fmt.duration(it.toLong()) } ?: "—")
                Caption("${s.sleepAvg7.daysWithData}/7 nights logged")
            }
        }
        s.sleepTarget?.let { t ->
            Spacer(Modifier.height(12.dp))
            GlassProgressBar(s.sleepMin?.let { (it / t).toFloat() }, th.sleep)
            Spacer(Modifier.height(4.dp))
            Caption("Target ${Fmt.duration(t.toLong())}")
        }
    }
}

// ------------------------------------------------------------------ Steps

@Composable
private fun StepsCard(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    GlassCard(onClick = onClick) {
        CardHeader(Icons.Rounded.DirectionsWalk, "Steps & activity", th.steps)
        Spacer(Modifier.height(12.dp))
        Metric(s.steps?.let { Fmt.int(it) } ?: "—", s.stepTarget?.let { "/ ${Fmt.int(it)}" })
        Spacer(Modifier.height(10.dp))
        GlassProgressBar(s.steps?.let { st -> s.stepTarget?.let { (st / it).toFloat() } }, th.steps)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Caption("Distance: ${s.distanceM?.let { Fmt.distance(it, units.distance) } ?: "not logged"}")
            Caption("Active: ${s.activeMin?.let { "$it min" } ?: "not logged"}")
        }
    }
}

// ------------------------------------------------------------------ Check-in

private val faceFor = listOf("😫", "😣", "😟", "😕", "😐", "🙂", "😊", "😄", "😁", "🤩")

@Composable
private fun CheckInCard(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val ci = s.checkIn
    GlassCard(onClick = onClick) {
        CardHeader(Icons.Rounded.Mood, "Daily check-in", th.warning)
        Spacer(Modifier.height(12.dp))
        if (ci == null) {
            Caption("Not done yet today. Takes 20 seconds — energy, mood, stress, soreness.")
            return@GlassCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Energy" to ci.energy, "Mood" to ci.mood, "Stress" to ci.stress, "Sore" to ci.soreness, "Drive" to ci.motivation).forEach { (l, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val inverted = l == "Stress" || l == "Sore"
                    Text(v?.let { faceFor[(if (inverted) 11 - it else it) - 1] } ?: "·", style = FitType.title, textAlign = TextAlign.Center)
                    Text(v?.let { "$it" } ?: "—", style = FitType.label, color = th.text)
                    Caption(l)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Goals

@Composable
private fun GoalsCard(s: DashState) {
    val th = LocalFitTheme.current
    val met = s.goals.count { it.met == true }
    GlassCard {
        CardHeader(Icons.Rounded.CheckCircle, "Today's goals", th.success) {
            Text("$met/${s.goals.size}", style = FitType.section, color = th.text)
        }
        Spacer(Modifier.height(10.dp))
        s.goals.forEach { g ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (g.met == true) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (g.met == true) th.success else th.textFaint, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(g.label, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                Caption(g.detail)
            }
        }
    }
}

@Composable
fun DashSection(text: String) = SectionTitle(text)
