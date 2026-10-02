package com.myfit.tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Units
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassProgressBar
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.Sparkline
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings

/** Fixed height for half-width dashboard tiles so the two tiles in a row line up. */
internal val TileHeight: Dp = 160.dp

/** Half-width tile shell: small header (30dp bubble + label) and compact content. */
@Composable
internal fun HalfTile(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val th = LocalFitTheme.current
    GlassCard(Modifier.height(TileHeight), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, color, 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing()
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** One-line caption that never wraps (ellipsis instead). */
@Composable
internal fun TileLine(text: String, color: Color? = null) {
    Text(text, style = FitType.caption, color = color ?: LocalFitTheme.current.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Big number with a smaller unit, on one line. */
@Composable
internal fun TileValue(value: String, unit: String? = null, color: Color? = null) {
    val th = LocalFitTheme.current
    Text(
        buildAnnotatedString {
            append(value)
            if (unit != null) withStyle(SpanStyle(fontSize = FitType.label.fontSize, color = th.textDim)) { append(" $unit") }
        },
        style = FitType.title, color = color ?: th.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

// ------------------------------------------------------------------ Hydration (with the 3D glass)

@Composable
internal fun HydrationTile(s: DashState, c: AppContainer, open: (Sheet) -> Unit, wide: Boolean = false) {
    val th = LocalFitTheme.current
    val units = LocalSettings.current.units
    val toaster = LocalToaster.current
    val add250: () -> Unit = {
        c.write {
            val id = c.logRepo.addWater(250.0)
            toaster.show("Added 250 ml of water", "Undo") { c.write { c.logRepo.deleteWater(id) } }
        }
    }
    val fraction = s.waterMl?.let { w -> s.waterTarget?.takeIf { it > 0 }?.let { (w / it).toFloat() } } ?: 0f
    GlassCard(Modifier.height(TileHeight), onClick = { open(Sheet.Water()) }, padding = 12.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            WaterGlass3D(fraction, th.water, Modifier.width(if (wide) 84.dp else 50.dp).fillMaxHeight(), onTap = add250)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("HYDRATION", style = FitType.overline, color = th.water, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    val w = s.waterMl
                    if (w == null) TileValue("—") else {
                        val txt = Fmt.volume(w, units.volume)
                        TileValue(txt.substringBefore(' '), txt.substringAfter(' ', ""))
                    }
                    val t = s.waterTarget
                    when {
                        t == null -> TileLine("No target set")
                        (s.waterMl ?: 0.0) >= t -> TileLine("Target reached", th.success)
                        else -> TileLine("of ${Fmt.volume(t, units.volume)}")
                    }
                }
                Row(
                    Modifier.height(30.dp).clip(RoundedCornerShape(15.dp))
                        .background(th.water.copy(alpha = 0.18f))
                        .clickableNoRipple(add250)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Duo.Add, "Add 250 ml", tint = th.water, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("250 ml", style = FitType.label, color = th.text, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Steps

@Composable
internal fun StepsTile(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    HalfTile(Duo.DirectionsWalk, "Steps", th.steps, onClick) {
        TileValue(s.steps?.let { Fmt.int(it) } ?: "—")
        TileLine(s.stepTarget?.let { "of ${Fmt.int(it)} target" } ?: "No target set")
        Spacer(Modifier.height(8.dp))
        GlassProgressBar(s.steps?.let { st -> s.stepTarget?.takeIf { it > 0 }?.let { (st / it).toFloat() } }, th.steps, height = 6.dp)
        Spacer(Modifier.height(8.dp))
        if (s.stepsSource != null) TileLine(s.stepsSource, th.textFaint)
        else TileLine("Tap to connect a tracker", th.accentBright)
    }
}

// ------------------------------------------------------------------ Sleep

@Composable
internal fun SleepTile(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    HalfTile(Duo.Bedtime, "Sleep", th.sleep, onClick) {
        TileValue(s.sleepMin?.let { Fmt.duration(it) } ?: "—")
        TileLine(if (s.sleepMin == null) "Nothing logged last night" else "Last night")
        Spacer(Modifier.height(8.dp))
        val sl = s.health.sleep?.takeIf { s.sleepSource != "Logged manually" }
        val stages = sl?.let {
            listOf(
                (it.deepMin ?: 0L) to th.sleep,
                (it.remMin ?: 0L) to th.accentBright,
                (it.lightMin ?: 0L) to th.sleep.copy(alpha = 0.45f),
                (it.awakeMin ?: 0L) to th.warning.copy(alpha = 0.7f),
            ).filter { p -> p.first > 0L }
        }.orEmpty()
        if (stages.isNotEmpty()) {
            // mini sleep-stage bar: deep · REM · light · awake
            Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                stages.forEach { (m, col) -> Box(Modifier.weight(m.toFloat()).fillMaxHeight().background(col)) }
            }
            Spacer(Modifier.height(8.dp))
            val deep = sl?.deepMin
            TileLine(if (deep != null) "Deep ${Fmt.duration(deep)} · REM ${Fmt.duration(sl?.remMin ?: 0L)}" else (s.sleepSource ?: ""), th.textFaint)
        } else {
            GlassProgressBar(s.sleepMin?.let { m -> s.sleepTarget?.takeIf { it > 0 }?.let { (m / it).toFloat() } }, th.sleep, height = 6.dp)
            Spacer(Modifier.height(8.dp))
            val q = s.sleepQuality
            TileLine(
                when {
                    q != null -> "Quality $q/10"
                    s.sleepTarget != null -> "Target ${Fmt.duration(s.sleepTarget.toLong())}"
                    else -> s.sleepSource ?: "Tap to log sleep"
                },
                th.textFaint,
            )
        }
    }
}

// ------------------------------------------------------------------ Check-in

private val tileFaces = listOf("😫", "😣", "😟", "😕", "😐", "🙂", "😊", "😄", "😁", "🤩")

@Composable
internal fun CheckInTile(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val ci = s.checkIn
    HalfTile(Duo.Mood, "Check-in", th.warning, onClick) {
        if (ci == null) {
            Text("Check in", style = FitType.title, color = th.accentBright, maxLines = 1)
            TileLine("Not done yet today")
            Spacer(Modifier.height(8.dp))
            TileLine("20 seconds · mood, energy", th.textFaint)
            return@HalfTile
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Mood" to ci.mood, "Energy" to ci.energy).forEach { (label, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v?.let { tileFaces[(it - 1).coerceIn(0, 9)] } ?: "·", style = FitType.title)
                    Text(v?.let { "$it/10" } ?: "—", style = FitType.label, color = th.text, maxLines = 1)
                    TileLine(label)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        TileLine("Logged today", th.success)
    }
}

// ------------------------------------------------------------------ Body

@Composable
internal fun BodyTile(s: DashState, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units.weight
    HalfTile(Duo.MonitorWeight, "Weight", th.accentBright, onClick) {
        val lw = s.latestWeight
        if (lw == null) {
            TileValue("—")
            TileLine("No weigh-ins yet")
            Spacer(Modifier.height(8.dp))
            TileLine("Tap to add one", th.accentBright)
            return@HalfTile
        }
        TileValue(Fmt.trim(Units.kgTo(lw.weightKg, u), 1), u.label)
        val c7 = s.change7
        if (c7 != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val up = c7.delta > 0
                if (c7.delta != 0.0) {
                    Icon(if (up) Duo.ArrowUpward else Duo.ArrowDownward, if (up) "Up" else "Down", tint = th.textDim, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(2.dp))
                }
                TileLine("${Fmt.signed(Units.kgTo(c7.delta, u))} ${u.label} vs last wk")
            }
        } else {
            TileLine(if (lw.localDate == Clock.dateKey(s.today)) "Weighed today" else "Latest · ${lw.localDate}")
        }
        Spacer(Modifier.height(6.dp))
        Sparkline(s.weightSpark.map { it?.let { kg -> Units.kgTo(kg, u) } }, th.accentBright, Modifier.fillMaxWidth().height(30.dp))
    }
}

// ------------------------------------------------------------------ Goals

@Composable
internal fun GoalsTile(s: DashState, onClick: (() -> Unit)?) {
    val th = LocalFitTheme.current
    val met = s.goals.count { it.met == true }
    HalfTile(Duo.CheckCircle, "Goals", th.success, onClick) {
        TileValue("$met", "of ${s.goals.size}")
        TileLine(if (s.goals.isNotEmpty() && met == s.goals.size) "All done today!" else "done today", if (s.goals.isNotEmpty() && met == s.goals.size) th.success else null)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            s.goals.take(6).forEach { g ->
                Icon(
                    if (g.met == true) Duo.CheckCircle else Duo.RadioButtonUnchecked, g.label,
                    tint = if (g.met == true) th.success else th.textFaint, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
