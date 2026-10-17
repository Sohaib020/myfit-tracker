package com.myfit.tracker.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.BuddyAvatar
import com.myfit.tracker.ui.pip.BuddyChar
import com.myfit.tracker.ui.pip.Champion
import com.myfit.tracker.ui.pip.Champions
import com.myfit.tracker.ui.pip.Tier
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.delay

/** Latest stats, refreshed when Home or the champion screen opens. */
object StatsCache { val value = kotlinx.coroutines.flow.MutableStateFlow(ChampStats.EMPTY) }

@Composable
private fun rememberStats(container: AppContainer): ChampStats {
    val s by StatsCache.value.collectAsState()
    LaunchedEffect(Unit) { StatsCache.value.value = ChampStats.compute(container) }
    return s
}

// ------------------------------------------------------------------ Home hero card

/**
 * Home hero card (Game Mode): your champion on a stage, today's energy bar (steps, active minutes, water, a
 * workout) and the four stats. The champion reacts to your day: celebrates when the bar is full, trains while you're
 * on the way, waits sleepily when nothing's happened yet.
 */
@Composable
fun HeroCard(container: AppContainer, steps: Int?, stepTarget: Double?, activeMin: Int?, waterMl: Double?, waterTarget: Double?, workedOut: Boolean) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { GameMode.load(ctx) }
    val b by GameMode.champ.collectAsState()
    val ch = b.champion ?: Champion.BLAZE
    val stats = rememberStats(container)
    val parts = listOfNotNull(
        stepTarget?.takeIf { it > 0 }?.let { ((steps ?: 0) / it).coerceIn(0.0, 1.0) },
        ((activeMin ?: 0) / 30.0).coerceIn(0.0, 1.0),
        waterTarget?.takeIf { it > 0 }?.let { ((waterMl ?: 0.0) / it).coerceIn(0.0, 1.0) },
        if (workedOut) 1.0 else 0.0,
    )
    val energy = if (parts.isEmpty()) 0.0 else parts.average()
    var reaction by remember { mutableStateOf<String?>(null) }
    val base = when { energy >= 0.999 -> "celebrate"; energy >= 0.35 -> "idle"; java.time.LocalTime.now().hour >= 21 -> "sleepy"; else -> "idle" }
    val pose = reaction ?: base
    Glass(Modifier.fillMaxWidth().height(250.dp), onClick = { nav.push(Overlay.Champion) }, tint = ch.color.copy(alpha = 0.16f)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ChampStage(b, pose, Modifier.fillMaxSize().padding(top = 8.dp), onEnd = { reaction = null })
                // tap the hero for a move it knows (tier specials included)
                Box(Modifier.fillMaxSize().clickableNoRipple {
                    reaction = listOf("letsgo", "thumbsup", "clap", "dance").let { if (b.tier != Tier.STREET) it + "cocky" else it }.let { if (b.tier == Tier.LEGENDARY) it + "combo" else it }.random()
                })
            }
            Column(Modifier.weight(1.05f).fillMaxHeight().padding(top = 16.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(ch.tag, style = FitType.title, color = th.text)
                    Caption("${b.tier.label} · Rating ${stats.rating}", color = ch.color)
                }
                Column {
                    Text("Today's energy", style = FitType.label, color = th.textDim)
                    Spacer(Modifier.height(4.dp))
                    Bar(energy.toFloat(), ch.color, 10.dp)
                    Caption("${(energy * 100).toInt()}% — " + when { energy >= 0.999 -> "fully charged!"; energy >= 0.5 -> "keep going"; else -> "move to charge ${ch.tag}" })
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Stat.entries.forEach { s -> MiniStat(s.label, stats.of(s), ch.color) }
                }
            }
        }
    }
}

@Composable
private fun Bar(f: Float, color: Color, h: androidx.compose.ui.unit.Dp) {
    val th = LocalFitTheme.current
    Box(Modifier.fillMaxWidth().height(h).clip(CircleShape).background(th.text.copy(alpha = 0.10f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
    }
}

@Composable
private fun MiniStat(label: String, v: Int, color: Color) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.caption, color = th.textDim, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f)) { Bar(v / 100f, color, 5.dp) }
        Text("$v", style = FitType.caption, color = th.text, modifier = Modifier.width(28.dp).padding(start = 6.dp))
    }
}

// ------------------------------------------------------------------ champion screen

/** Your champion: big stage (drag to spin, tap for a move), stats with where they come from, roster, looks. */
@Composable
fun ChampionScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val toaster = LocalToaster.current
    LaunchedEffect(Unit) { GameMode.load(ctx) }
    val mine by GameMode.champ.collectAsState()
    var view by remember { mutableStateOf(mine) }                 // the look being inspected
    var ver by remember { mutableIntStateOf(0) }
    val stats = rememberStats(container)
    val balance = remember(ver) { Champions.balance(ctx) }
    val level = remember(ver) { Champions.level(ctx) }
    var reaction by remember { mutableStateOf<String?>(null) }
    val ch = view.champion ?: Champion.BLAZE
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Champion", { nav.pop() }, "★ $balance to spend · Arena Lv $level")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Glass(Modifier.fillMaxWidth().height(380.dp), shape = RoundedCornerShape(30.dp), tint = ch.color.copy(alpha = 0.18f)) {
                    val owned = remember(view, ver) { Champions.owns(ctx, ch, view.tier) }
                    if (owned) {
                        ChampStage(view, reaction ?: "idle", Modifier.fillMaxSize().padding(top = 10.dp), onEnd = { reaction = null })
                        Box(Modifier.align(Alignment.BottomStart).padding(14.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f))
                            .clickableNoRipple { reaction = listOf("wave", "letsgo", "celebrate", "dance", "victory", "combo").filter { it in Champions.clips(view.tier) || it == "victory" && view.tier != Tier.STREET }.random() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)) { Text("Tap: show a move", style = FitType.caption, color = Color.White) }
                    } else {
                        // R26: a look you don't own yet is a still glimpse — the animations download once it's yours
                        ChampStill(view, Modifier.fillMaxSize().padding(top = 70.dp, bottom = 6.dp), locked = true)
                        Row(Modifier.align(Alignment.BottomStart).padding(14.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.40f))
                            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Duo.Lock, null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                            Text(if (Champions.unlocked(ctx, ch)) "Unlock to bring ${ch.tag} to life" else "Unlocks at Arena Lv ${ch.unlock}", style = FitType.caption, color = Color.White)
                        }
                    }
                    Column(Modifier.align(Alignment.TopStart).padding(16.dp)) {
                        Text(ch.tag, style = FitType.display.copy(fontSize = 34.sp, fontWeight = FontWeight.Black), color = Color.White)
                        Text("${ch.rarity.label} · ${ch.archetype}", style = FitType.label, color = Color.White.copy(alpha = 0.85f))
                        if (owned && mine == view) Caption("Your champion", color = Color.White)
                    }
                }
            }
            // looks + action
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Looks", style = FitType.section, color = th.text)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Tier.entries.forEach { t ->
                                val have = remember(ch, t, ver) { Champions.owns(ctx, ch, t) }
                                val sel = view.tier == t
                                Glass(Modifier.weight(1f).height(150.dp).border(if (sel) 2.dp else 0.dp, if (sel) ch.color else Color.Transparent, RoundedCornerShape(20.dp)),
                                    shape = RoundedCornerShape(20.dp), tint = if (sel) ch.color.copy(alpha = 0.22f) else null, onClick = { view = BuddyChar(ch, t) }) {
                                    ChampStill(BuddyChar(ch, t), Modifier.fillMaxSize().padding(top = 6.dp, bottom = 30.dp), locked = !have)
                                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (!have) { Icon(Duo.Lock, null, tint = th.textDim, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                                        Text(if (have || t == Tier.STREET) t.label else "${t.label} ★${t.cost}", style = FitType.caption, color = if (sel) th.text else th.textDim)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val t = view.tier
                        val owns = remember(view, ver) { Champions.owns(ctx, ch, t) }
                        val gate = Milestones.met(t, stats)
                        val blocker = remember(view, ver, stats) { Champions.blocker(ctx, ch, t) ?: if (!gate) "Needs ${Milestones.need(t)}" else null }
                        when {
                            owns && mine == view -> Caption("In use — ${GameMode.sport(ch).second} earns ${ch.tag} bonus stars.")
                            owns -> com.myfit.tracker.ui.theme.AccentButton("Make ${ch.tag} ${t.label} my champion", { tick(); GameMode.choose(ctx, view); toaster.show("${ch.tag} is your champion") },
                                Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 46.dp)
                            blocker == null -> com.myfit.tracker.ui.theme.AccentButton("Unlock for ★${t.cost}", {
                                if (Champions.buy(ctx, ch, t)) { tick(); ver++; GameMode.choose(ctx, view); toaster.show("${ch.tag} ${t.label} unlocked") }
                            }, Modifier.fillMaxWidth(), icon = Duo.Lock, height = 46.dp)
                            else -> Caption(blocker)
                        }
                        Milestones.need(t)?.let { Caption("${t.label} looks need ★${t.cost} and $it.", Modifier.padding(top = 6.dp)) }
                    }
                }
            }
            // stats
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Stats", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                            Text("Rating ${stats.rating}", style = FitType.label, color = ch.color)
                        }
                        Stat.entries.forEach { s ->
                            Column {
                                Row { Text(s.label, style = FitType.label, color = th.text, modifier = Modifier.weight(1f)); Text("${stats.of(s)}", style = FitType.label, color = th.text) }
                                Spacer(Modifier.height(4.dp)); Bar(stats.of(s) / 100f, ch.color, 8.dp)
                                Caption("From ${s.from} (last 7 days)" + if (GameMode.sport(ch).first == s) " · ${ch.tag}'s speciality" else "")
                            }
                        }
                        Caption("Streak ${stats.streak} days · ${stats.totalWorkouts} workouts logged")
                    }
                }
            }
            // roster
            item {
                Text("Champions", style = FitType.section, color = th.text)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Champion.entries.toList()) { c ->
                        val open = remember(c, ver) { Champions.unlocked(ctx, c) }
                        val shown = remember(c, ver) { BuddyChar(c, Tier.entries.lastOrNull { Champions.owns(ctx, c, it) } ?: Tier.STREET) }
                        Glass(Modifier.width(92.dp).height(148.dp).border(if (mine.champion == c) 2.dp else 0.dp, if (mine.champion == c) c.color else Color.Transparent, RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp), tint = if (view.champion == c) c.color.copy(alpha = 0.24f) else null, onClick = { view = shown }) {
                            Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                ChampStill(shown, Modifier.fillMaxWidth().weight(1f), locked = !open)
                                Text(c.tag, style = FitType.caption, color = th.text)
                                Caption(if (open) c.rarity.label else "Lv ${c.unlock}")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Game Mode intro: see GameIntro.kt (R26 liquid chrome)

/** Arena card: your champion (Game Mode on) or the invitation to turn it on. */
@Composable
fun ArenaChampionCard() {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { GameMode.load(ctx) }
    val on by GameMode.on.collectAsState()
    val b by GameMode.champ.collectAsState()
    val ch = b.champion ?: Champion.BLAZE
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickableNoRipple {
        if (on) nav.push(Overlay.Champion) else GameMode.setOn(ctx, true)
    }) {
        BuddyAvatar(b, 64.dp, dim = !on)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (on) "${ch.tag} · your champion" else "Game Mode", style = FitType.section, color = th.text)
            Caption(if (on) "Stats, looks and the champion roster" else "Turn your workouts into a champion: stats, looks and battles. Tap to turn on.")
        }
        Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
    }
}
