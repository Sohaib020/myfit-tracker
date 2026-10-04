package com.myfit.tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.Launch
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import com.myfit.tracker.ui.theme.rememberTick
import com.myfit.tracker.domain.Fmt

/** Small round chevron in a card header: "this card opens". */
@Composable
internal fun OpenChevron(size: Dp = 30.dp) {
    val th = LocalFitTheme.current
    Box(
        Modifier.size(size).clip(CircleShape).background(if (th.isLight) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center,
    ) { Icon(Duo.KeyboardArrowRight, "Open", tint = th.textDim, modifier = Modifier.size(size * 0.62f)) }
}

/** Header row used by the Home cards: icon, title, optional subtitle, chevron. */
@Composable
internal fun HomeCardHeader(icon: ImageVector, title: String, color: Color, sub: String? = null) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, color, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.section, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = FitType.caption, color = th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OpenChevron()
    }
}

/** A tonal action chip inside a card (does its job without opening the card). */
@Composable
internal fun CardAction(text: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    Row(
        modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(color.copy(alpha = if (th.isLight) 0.14f else 0.20f))
            .clickableNoRipple { tick(); onClick() }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = FitType.label, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ Vitals

@Composable
internal fun VitalsCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val today = remember { Clock.today() }
    val days by remember { container.db.healthDao().observeDaily(Clock.dateKey(today.minusDays(6)), Clock.dateKey(today)) }.collectAsState(initial = emptyList())
    val restDay = days.lastOrNull { it.restingHr != null }
    val hrDay = days.lastOrNull { it.avgHr != null }
    val spo2 = days.lastOrNull { it.spo2Pct != null }?.spo2Pct
    val bpm = restDay?.restingHr ?: hrDay?.avgHr
    var bpOpen by remember { mutableStateOf(false) }
    GlassCard(onClick = { nav.push(Overlay.Vitals) }, padding = 14.dp) {
        HomeCardHeader(Duo.Favorite, "Vitals", th.danger, listOfNotNull(
            bpm?.let { "$it bpm" + if (restDay != null) " resting" else "" },
            spo2?.let { "SpO₂ ${Math.round(it)}%" },
        ).joinToString(" · ").ifBlank { "Heart rate, blood pressure & more" })
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardAction("Measure heart", Duo.Pulse, th.danger, Modifier.weight(1f)) { nav.push(Overlay.CameraHr) }
            CardAction("Log BP", Duo.Favorite, th.accentBright, Modifier.weight(1f)) { bpOpen = true }
        }
    }
    com.myfit.tracker.ui.vitals.BpEntrySheet(container, bpOpen) { bpOpen = false }
}

// ------------------------------------------------------------------ Mindfulness

@Composable
internal fun MindCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val todayKey = remember { Clock.dateKey(Clock.today()) }
    val moods by remember(todayKey) { container.db.moodDao().observeDay(todayKey) }.collectAsState(initial = emptyList())
    val mood = moods.firstOrNull()?.mood
    var moodOpen by remember { mutableStateOf(false) }
    GlassCard(onClick = { nav.push(Overlay.Mind) }, padding = 14.dp) {
        HomeCardHeader(Duo.SelfImprovement, "Mindfulness", th.sleep,
            if (mood != null) "Feeling ${com.myfit.tracker.ui.mind.moodWord(mood).lowercase()} today" else "Breathe, meditate, check in")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardAction("Breathe", Duo.Drop, th.water, Modifier.weight(1f)) { Launch.mind = "breath"; nav.push(Overlay.Mind) }
            CardAction("Calm", Duo.SelfImprovement, th.sleep, Modifier.weight(1f)) { Launch.calm = true; nav.push(Overlay.Mind) }
            CardAction("Mood", Duo.Mood, th.warning, Modifier.weight(1f)) { moodOpen = true }
        }
    }
    GlassSheet(moodOpen, { moodOpen = false }) {
        com.myfit.tracker.ui.mind.MoodCheckInForm(container) { moodOpen = false }
    }
}

// ------------------------------------------------------------------ Menstrual cycle

@Composable
internal fun CycleCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val enabled = LocalSettings.current.cycleEnabled
    var pinTick by remember { mutableIntStateOf(0) }
    val pinOn = remember(pinTick) { com.myfit.tracker.ui.cycle.CyclePrefs.lockOn(ctx) }
    var pinSetup by remember { mutableStateOf(false) }
    GlassCard(onClick = { nav.push(Overlay.Cycle) }, padding = 14.dp) {
        HomeCardHeader(Duo.CalendarMonth, "Menstrual cycle", com.myfit.tracker.ui.cycle.CycleColors.period, if (enabled) null else "Tap to set up · stays on this phone")
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            com.myfit.tracker.ui.cycle.CycleTileBody(container)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardAction("Today's log", Duo.EditNote, com.myfit.tracker.ui.cycle.CycleColors.period, Modifier.weight(1f)) {
                if (enabled) Launch.cycle = "log"
                nav.push(Overlay.Cycle)
            }
            CardAction(if (pinOn) "PIN on" else "Set a PIN", Duo.Lock, th.accentBright, Modifier.weight(1f)) {
                if (pinOn) nav.push(Overlay.Cycle) else pinSetup = true
            }
        }
    }
    com.myfit.tracker.ui.cycle.PinSetupSheet(pinSetup, { pinSetup = false }) { pin ->
        com.myfit.tracker.ui.cycle.CyclePrefs.setPin(ctx, pin)
        com.myfit.tracker.ui.cycle.CycleLockSession.unlock()
        pinSetup = false; pinTick++
        toaster.show("Cycle PIN set")
    }
}

// ------------------------------------------------------------------ Progress + check-in

private val faces = listOf("😫", "😣", "😟", "😕", "😐", "🙂", "😊", "😄", "😁", "🤩")

/** Check-in strip at the bottom of the large Today's progress card. */
@Composable
internal fun CheckInStrip(s: DashState, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val ci = s.checkIn
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (th.isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.06f))
            .clickableNoRipple { open(Sheet.CheckIn(ci?.id)) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(Duo.Mood, th.warning, 30.dp)
        Spacer(Modifier.width(10.dp))
        if (ci == null) {
            Column(Modifier.weight(1f)) {
                Text("Daily check-in", style = FitType.label, color = th.text)
                Caption("20 seconds · mood, energy, stress")
            }
            Text("Check in", style = FitType.label, color = th.accentBright)
        } else {
            Text("Check-in", style = FitType.label, color = th.text, modifier = Modifier.weight(1f))
            listOf("Mood" to ci.mood, "Energy" to ci.energy).forEach { (l, v) ->
                Text(v?.let { faces[(it - 1).coerceIn(0, 9)] } ?: "·", style = FitType.section)
                Spacer(Modifier.width(4.dp))
                Text("$l ${v ?: "—"}", style = FitType.caption, color = th.textDim)
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ Food + hydration

@Composable
internal fun FoodHydrationCard(s: DashState, c: AppContainer, open: (Sheet) -> Unit, goFood: () -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val units = LocalSettings.current.units
    val today = Clock.today()
    val key = Clock.dateKey(today)
    val items by remember(today) { c.nutritionRepo.itemsOn(today) }.collectAsState(initial = emptyList())
    val targets by c.profileRepo.targets.collectAsState(initial = emptyList())
    val t = com.myfit.tracker.ui.food.totalsOf(items)
    val kcalT = com.myfit.tracker.domain.Targets.on(targets, com.myfit.tracker.data.db.TargetType.CALORIES, today)
    val water = rememberWaterActions(c)
    val fraction = s.waterMl?.let { w -> s.waterTarget?.takeIf { it > 0 }?.let { (w / it).toFloat() } } ?: 0f
    GlassCard(onClick = goFood, padding = 14.dp) {
        HomeCardHeader(Duo.ForkKnife, "Food & hydration", th.protein,
            if (items.isEmpty()) "Nothing eaten logged yet" else "${items.size} ${if (items.size == 1) "item" else "items"} today")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.CenterVertically) {
            // food side
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("FOOD", style = FitType.overline, color = th.protein)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (items.isEmpty()) "—" else Fmt.int(t.kcal), style = FitType.title, color = th.text)
                    Spacer(Modifier.width(4.dp))
                    Text(kcalT?.let { "/ ${Fmt.int(it)} kcal" } ?: "kcal", style = FitType.caption, color = th.textDim, modifier = Modifier.padding(bottom = 3.dp))
                }
                com.myfit.tracker.ui.components.GlassProgressBar(if (items.isEmpty() || kcalT == null || kcalT <= 0) null else (t.kcal / kcalT).toFloat(), th.protein, height = 6.dp)
                Text("P ${Fmt.int(t.protein)} · C ${Fmt.int(t.carbs)} · F ${Fmt.int(t.fat)} g", style = FitType.caption, color = th.textDim, maxLines = 1)
                CardAction("Add food", Duo.Search, th.protein, Modifier.fillMaxWidth()) {
                    nav.push(Overlay.FoodAdd(com.myfit.tracker.ui.food.mealForNow(), key, 0))
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(1.dp).fillMaxHeight().background(th.textFaint.copy(alpha = 0.25f)))
            Spacer(Modifier.width(12.dp))
            // hydration side
            Column(Modifier.weight(1f).fillMaxHeight().clickableNoRipple { open(Sheet.Water()) }, verticalArrangement = Arrangement.SpaceBetween) {
                Text("HYDRATION", style = FitType.overline, color = th.water)
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WaterGlass3D(fraction, th.water, Modifier.width(52.dp).fillMaxHeight().padding(vertical = 4.dp), onTap = water.add)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(s.waterMl?.let { Fmt.volume(it, units.volume) } ?: "—", style = FitType.section, color = th.text, maxLines = 1)
                        Text(s.waterTarget?.let { "of ${Fmt.volume(it, units.volume)}" } ?: "No target", style = FitType.caption, color = th.textDim, maxLines = 1)
                    }
                }
                WaterButtons(water)
            }
        }
    }
}

/** Add 250 ml / remove the last drink, shared by the large card and the small hydration tile. */
internal class WaterActions(val add: () -> Unit, val remove: () -> Unit)

@Composable
internal fun rememberWaterActions(c: AppContainer): WaterActions {
    val toaster = LocalToaster.current
    val units = LocalSettings.current.units
    return remember(c, toaster, units) {
        WaterActions(
            add = {
                c.write {
                    val id = c.logRepo.addWater(250.0)
                    toaster.show("Added ${Fmt.volume(250.0, units.volume)}", "Undo") { c.write { c.logRepo.deleteWater(id) } }
                }
            },
            remove = {
                c.write {
                    val ml = c.logRepo.removeLastWaterToday()
                    toaster.show(if (ml == null) "No water logged today" else "Removed ${Fmt.volume(ml, units.volume)}")
                }
            },
        )
    }
}

@Composable
internal fun WaterButtons(w: WaterActions, compact: Boolean = false) {
    val th = LocalFitTheme.current
    val h = if (compact) 32.dp else 40.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(h).clip(CircleShape).background(th.water.copy(alpha = 0.16f)).clickableNoRipple(w.remove),
            contentAlignment = Alignment.Center,
        ) { Icon(Duo.Remove, "Remove last drink", tint = th.water, modifier = Modifier.size(16.dp)) }
        Row(
            Modifier.weight(1f).height(h).clip(RoundedCornerShape(h / 2))
                .drawBehind { drawRect(Brush.verticalGradient(listOf(th.water.copy(alpha = 0.95f), th.water.copy(alpha = 0.75f)))) }
                .clickableNoRipple(w.add),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Duo.Add, null, tint = Color.White, modifier = Modifier.size(if (compact) 13.dp else 15.dp))
            Spacer(Modifier.width(2.dp))
            Text(if (compact) "250" else "250 ml", style = if (compact) FitType.caption else FitType.label, color = Color.White, maxLines = 1, softWrap = false)
        }
    }
}


// ------------------------------------------------------------------ Shariah & Health

@Composable
internal fun DeenCard() {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val now by produceState(java.time.LocalTime.now()) { while (true) { value = java.time.LocalTime.now(); kotlinx.coroutines.delay(30_000) } }
    val times = remember(now.hour) { com.myfit.tracker.ui.deen.prayerTimes(ctx) }
    val sub = if (times == null) "Prayer times, Qibla, fasting & dhikr" else {
        val n = now.hour * 60 + now.minute
        val next = times.entries.firstOrNull { it.key.isSalah && it.value > n } ?: times.entries.first()
        val until = (next.value - n).let { if (it < 0) it + 1440 else it }
        "${next.key.label} at ${com.myfit.tracker.ui.deen.clock(next.value)} · in ${until / 60}h ${until % 60}m"
    }
    GlassCard(onClick = { nav.push(Overlay.Deen) }, padding = 14.dp) {
        HomeCardHeader(Duo.Bedtime, "Shariah & Health", Color(0xFF2FA37A), sub)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardAction("Qibla", Duo.TrackChanges, Color(0xFFD9A441), Modifier.weight(1f)) { com.myfit.tracker.ui.deen.DeenLaunch.tab = 0; nav.push(Overlay.Deen) }
            CardAction("Tasbeeh", Duo.RadioButtonUnchecked, Color(0xFF2FA37A), Modifier.weight(1f)) { com.myfit.tracker.ui.deen.DeenLaunch.tab = 2; nav.push(Overlay.Deen) }
        }
    }
}

/** One-time question for existing users (new users answer it during setup). */
@Composable
internal fun DeenAskCard(container: AppContainer) {
    val th = LocalFitTheme.current
    GlassCard(padding = 14.dp) {
        HomeCardHeader(Duo.Bedtime, "Shariah & Health", Color(0xFF2FA37A), "Prayer times, Qibla, fasting, dhikr, halal check")
        Spacer(Modifier.height(8.dp))
        Caption("Would you like the Muslim health section? It's shown only if you say yes, and you can change it in Me.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardAction("Yes, show it", Duo.Check, Color(0xFF2FA37A), Modifier.weight(1f)) { container.write { container.settings.setMuslim("yes") } }
            CardAction("No thanks", Duo.Close, th.textDim, Modifier.weight(1f)) { container.write { container.settings.setMuslim("no") } }
        }
    }
}
