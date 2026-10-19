package com.myfit.tracker.ui.game

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.systemBarsPadding
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.arena.ArenaProgress
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.pip.BuddyChar
import com.myfit.tracker.ui.pip.Champion
import com.myfit.tracker.ui.pip.Champions
import com.myfit.tracker.ui.pip.Tier
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.components.LocalToaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * R26 Arena champion battles. Your champion's four stats come from your real last 7 days, so training is how you win.
 * A battle is three rounds; each round tests one stat (stat + a small roll), best of three wins.
 *  - Ladder: 10 AI rivals, each a little stronger. Beat one to climb. Wins pay ★3, up to 3 paid wins a day.
 *  - Friends: battle the champion a friend publishes (Game Mode on). A win pays ★5 once per friend per day, and
 *    they see the result in their Arena.
 * The roll is seeded by the day and the rival, so re-running a battle on the same day gives the same result.
 */
data class Fighter(val name: String, val look: BuddyChar, val stats: List<Int>, val uid: String? = null, val rank: Int = 0) {
    val rating: Int get() = stats.average().toInt()
}

data class Round(val stat: Stat, val mine: Int, val theirs: Int) { val won: Boolean get() = mine >= theirs }
data class BattleResult(val rounds: List<Round>) {
    val wins: Int get() = rounds.count { it.won }
    val won: Boolean get() = wins >= 2
    val score: String get() = "$wins–${rounds.size - wins}"
}

object Battles {
    const val LADDER = 10
    private const val PAID_PER_DAY = 3
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("battles", Context.MODE_PRIVATE)

    fun rank(c: Context) = p(c).getInt("rank", 1).coerceIn(1, LADDER + 1)
    private fun today() = Clock.today().toString()
    fun paidToday(c: Context) = if (p(c).getString("day", "") == today()) p(c).getInt("paid", 0) else 0
    fun wins(c: Context) = p(c).getInt("wins", 0)
    fun losses(c: Context) = p(c).getInt("losses", 0)

    /** Ladder rival [rank]: a fixed champion per rung, stats rising ~6 per rung (Legendary at the top). */
    fun ai(rank: Int): Fighter {
        val r = rank.coerceIn(1, LADDER)
        val ch = Champion.entries[(r * 7 + 3) % Champion.entries.size]
        val tier = when { r >= 8 -> Tier.LEGENDARY; r >= 4 -> Tier.EPIC; else -> Tier.STREET }
        val rnd = java.util.Random(r * 991L)
        val base = 22 + r * 6
        val stats = List(4) { (base + rnd.nextInt(17) - 8).coerceIn(5, 98) }
        return Fighter("Rival ${ch.tag}", BuddyChar(ch, tier), stats, rank = r)
    }

    fun fight(me: Fighter, them: Fighter): BattleResult {
        val seed = (today() + (them.uid ?: "ai${them.rank}") + me.look.id).hashCode().toLong()
        val rnd = java.util.Random(seed)
        val order = Stat.entries.shuffled(rnd).take(3)
        val rounds = mutableListOf<Round>()
        for (s in order) {
            rounds += Round(s, me.stats[s.ordinal] + rnd.nextInt(21), them.stats[s.ordinal] + rnd.nextInt(21))
            if (rounds.count { it.won } >= 2 || rounds.count { !it.won } >= 2) break
        }
        return BattleResult(rounds)
    }

    /** Books the result; returns the stars earned (0 if this win doesn't pay). */
    fun record(c: Context, them: Fighter, r: BattleResult): Int {
        val e = p(c).edit()
        if (r.won) e.putInt("wins", wins(c) + 1) else e.putInt("losses", losses(c) + 1)
        if (r.won && them.uid == null && them.rank == rank(c)) e.putInt("rank", (rank(c) + 1).coerceAtMost(LADDER + 1))
        e.apply()
        if (!r.won) return 0
        if (them.uid != null) {
            return if (ArenaProgress.grant(c, "battle:${them.uid}:${today()}", 5, "Beat ${them.name}'s champion")) 5 else 0
        }
        if (paidToday(c) >= PAID_PER_DAY) return 0
        val n = paidToday(c) + 1
        p(c).edit().putString("day", today()).putInt("paid", n).apply()
        return if (ArenaProgress.grant(c, "battle:ai${them.rank}:${today()}:$n", 3, "Beat ${them.name}")) 3 else 0
    }
}

// ------------------------------------------------------------------ Arena: Champions tab

/** Arena → Champions: your champion, the battle ladder, friends' champions to battle, results against you. */
@Composable
fun ArenaChampions(container: AppContainer, onStars: () -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { GameMode.load(ctx) }
    val on by GameMode.on.collectAsState()
    val mine by GameMode.champ.collectAsState()
    val stats = rememberStats(container)
    var ver by remember { mutableIntStateOf(0) }
    var fight by remember { mutableStateOf<Fighter?>(null) }
    var share by remember { mutableStateOf(false) }
    val me = Fighter("You", mine, listOf(stats.power, stats.stamina, stats.agility, stats.focus))
    val ch = mine.champion ?: Champion.BLAZE
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!on) {
            Glass(Modifier.fillMaxWidth(), tint = ch.color.copy(alpha = 0.14f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Champions", style = FitType.section, color = th.text)
                    Caption("Turn on Game Mode to get a champion powered by your real workouts — battle rivals and friends, earn stars, unlock looks.")
                    Spacer(Modifier.height(10.dp))
                    AccentButton("Turn on Game Mode", { GameMode.setOn(ctx, true) }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 46.dp)
                }
            }
            RosterRow(ver)
            return@Column
        }
        // your champion
        Glass(Modifier.fillMaxWidth().height(230.dp), tint = ch.color.copy(alpha = 0.16f), onClick = { nav.push(Overlay.Champion) }) {
            Row(Modifier.fillMaxSize()) {
                ChampStage(mine, "idle", Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp))
                Column(Modifier.weight(1.1f).fillMaxHeight().padding(top = 16.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(ch.tag, style = FitType.title, color = th.text)
                        Caption("${mine.tier.label} · Rating ${me.rating}", color = ch.color)
                        Caption("${Battles.wins(ctx)} wins · ${Battles.losses(ctx)} losses")
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Stat.entries.forEach { s -> StatLine(s.label, me.stats[s.ordinal], ch.color) } }
                    GlassButton("Share card", { share = true }, Modifier.fillMaxWidth(), icon = Duo.Send, height = 40.dp)
                }
            }
        }
        // ladder
        val rank = remember(ver) { Battles.rank(ctx) }
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Battle ladder", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
                    Caption(if (rank > Battles.LADDER) "Champion of the ladder!" else "Rung $rank of ${Battles.LADDER}", color = ch.color)
                }
                Caption("3 rounds, one stat each. Wins pay ★3 (${3 - Battles.paidToday(ctx).coerceAtMost(3)} paid wins left today). Train to raise your stats.")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Battles.LADDER) { i ->
                        val r = i + 1; val f = Battles.ai(r)
                        val beaten = r < rank; val next = r == rank
                        Glass(Modifier.width(96.dp).height(150.dp).border(if (next) 2.dp else 0.dp, if (next) f.look.color else Color.Transparent, RoundedCornerShape(18.dp)),
                            shape = RoundedCornerShape(18.dp), tint = if (next) f.look.color.copy(alpha = 0.2f) else null,
                            onClick = if (r <= rank) ({ fight = f }) else null) {
                            Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                ChampStill(f.look, Modifier.fillMaxWidth().weight(1f), locked = r > rank)
                                Text(f.look.label, style = FitType.caption, color = th.text)
                                Caption(if (beaten) "Beaten ✓" else "Rating ${f.rating}")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (rank <= Battles.LADDER) AccentButton("Battle rung $rank", { fight = Battles.ai(rank) }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 46.dp)
            }
        }
        FriendBattles(container, me) { fight = it }
        RosterRow(ver)
    }
    fight?.let { f ->
        BattleDialog(container, me, f, onClose = { fight = null; ver++ }, onStars = onStars)
    }
    if (share) ShareChampionDialog(me, onClose = { share = false })
}

@Composable
private fun StatLine(label: String, v: Int, color: Color) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.caption, color = th.textDim, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(th.text.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((v / 100f).coerceIn(0f, 1f)).clip(CircleShape).background(color))
        }
        Text("$v", style = FitType.caption, color = th.text, modifier = Modifier.width(28.dp).padding(start = 6.dp))
    }
}

@Composable
private fun RosterRow(ver: Int) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    Text("All champions", style = FitType.section, color = th.text)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Champion.entries.size) { i ->
            val c = Champion.entries[i]
            val open = remember(c, ver) { Champions.unlocked(ctx, c) }
            Glass(Modifier.width(84.dp).height(132.dp), shape = RoundedCornerShape(18.dp), onClick = { nav.push(Overlay.Champion) }) {
                Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ChampStill(BuddyChar(c), Modifier.fillMaxWidth().weight(1f), locked = !open)
                    Text(c.tag, style = FitType.caption, color = th.text)
                    Caption(if (open) c.rarity.label else "Lv ${c.unlock}")
                }
            }
        }
    }
}

/** Friends who play Game Mode: battle their published champion; plus battles others fought against yours. */
@Composable
private fun FriendBattles(container: AppContainer, me: Fighter, onFight: (Fighter) -> Unit) {
    val th = LocalFitTheme.current
    val social = container.social
    val user by social.user.collectAsState()
    val snap by container.friendsRepo.snap.collectAsState()
    var rivals by remember { mutableStateOf<List<Fighter>?>(null) }
    var against by remember { mutableStateOf<List<com.myfit.tracker.social.BattleRecord>>(emptyList()) }
    LaunchedEffect(user, snap?.fetchedAt) {
        if (user == null || !social.available) { rivals = emptyList(); return@LaunchedEffect }
        val fs = snap?.friends.orEmpty()
        rivals = withContext(Dispatchers.IO) {
            fs.mapNotNull { f ->
                val ap = runCatching { social.arenaProfile(f.uid) }.getOrNull() ?: return@mapNotNull null
                val look = BuddyChar.parse(ap.champ ?: return@mapNotNull null)
                if (look.champion == null || ap.champStats.size != 4) null else Fighter(f.name, look, ap.champStats, uid = f.uid)
            }
        }
        against = runCatching { withContext(Dispatchers.IO) { social.battlesAgainstMe() } }.getOrDefault(emptyList())
    }
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Battle a friend", style = FitType.section, color = th.text)
            val r = rivals
            when {
                user == null || !social.available -> Caption("Sign in (Me → Friends) to battle your friends' champions.")
                r == null -> Caption("Looking for your friends' champions…")
                r.isEmpty() -> Caption("None of your friends has Game Mode on yet. Invite them from Friends — their champion appears here.")
                else -> r.forEach { f ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(th.text.copy(alpha = 0.05f)).clickableNoRipple { onFight(f) }
                        .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.myfit.tracker.ui.pip.BuddyAvatar(f.look, 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${f.name}'s ${f.look.label}", style = FitType.label, color = th.text)
                            Caption("${f.look.tier.label} · Rating ${f.rating} · win pays ★5 once a day")
                        }
                        Icon(Duo.PlayArrow, null, tint = th.accentBright, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (against.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Battles against your champion", style = FitType.label, color = th.textDim)
                against.take(5).forEach { b ->
                    Caption("${b.attackerName} ${if (b.attackerWon) "beat" else "lost to"} your ${BuddyChar.parse(b.defenderChamp).label} ${b.score}",
                        color = if (b.attackerWon) th.textDim else th.success)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ the battle itself

@Composable
fun BattleDialog(container: AppContainer, me: Fighter, them: Fighter, onClose: () -> Unit, onStars: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val result = remember(me, them) { Battles.fight(me, them) }
    var shown by remember { mutableIntStateOf(0) }           // rounds revealed
    var stars by remember { mutableStateOf<Int?>(null) }
    var share by remember { mutableStateOf(false) }
    val bars = remember { List(3) { Animatable(0f) to Animatable(0f) } }
    LaunchedEffect(result) {
        delay(700)
        result.rounds.forEachIndexed { i, r ->
            shown = i + 1
            launch { bars[i].first.animateTo(r.mine / 120f, tween(900)) }
            bars[i].second.animateTo(r.theirs / 120f, tween(900))
            delay(650)
        }
        val s = Battles.record(ctx, them, result)
        if (s > 0) onStars()
        stars = s
        if (them.uid != null) runCatching {
            withContext(Dispatchers.IO) { container.social.postBattle(them.uid, me.look.id, them.look.id, result.won, result.score) }
        }
    }
    val done = stars != null
    // R32: edge-to-edge dialog; result + buttons are pinned to the bottom and the middle shrinks/scrolls, so nothing is ever off screen
    Dialog(onDismissRequest = { if (done) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07080D), Color(0xFF141826))))) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)) {
                Text("CHAMPION BATTLE", style = FitType.overline, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // fighters get what the three rounds don't need (each round ≈ 62 dp), between 110 and 300 dp
                    val fighterH = (maxHeight - 3 * 62.dp - 12.dp).coerceIn(110.dp, 300.dp)
                    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        Row(Modifier.fillMaxWidth().height(fighterH)) {
                            val winPose = if (me.look.tier == Tier.STREET) "celebrate" else "victory"
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (done) ChampStage(me.look, if (result.won) winPose else "sad", Modifier.fillMaxWidth().weight(1f))
                                else ChampStage(me.look, "idle", Modifier.fillMaxWidth().weight(1f))
                                Text(me.look.label, style = FitType.label, color = me.look.color, maxLines = 1); Caption("You · ${me.rating}", color = Color.White.copy(alpha = 0.7f))
                            }
                            Text("VS", style = FitType.hero.copy(fontSize = 30.sp), color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                com.myfit.tracker.ui.coach.OnPlatform(them.look.color, Modifier.fillMaxWidth().weight(1f)) { ChampStill(them.look, Modifier.fillMaxSize().graphicsLayer { scaleX = -1f }, full = true) }
                                Text(them.look.label, style = FitType.label, color = them.look.color, maxLines = 1); Caption("${them.name} · ${them.rating}", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        result.rounds.take(shown).forEachIndexed { i, r ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Row { Text("Round ${i + 1} · ${r.stat.label}", style = FitType.label, color = Color.White, modifier = Modifier.weight(1f))
                                    Text(if (r.won) "You win" else "${them.look.label} wins", style = FitType.label, color = if (r.won) Color(0xFF7CF7D4) else Color(0xFFFF8A8A)) }
                                Spacer(Modifier.height(4.dp))
                                BattleBar(bars[i].first.value, me.look.color, r.mine)
                                Spacer(Modifier.height(3.dp))
                                BattleBar(bars[i].second.value, them.look.color, r.theirs)
                            }
                        }
                    }
                }
                if (done) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (result.won) "VICTORY ${result.score}" else "DEFEAT ${result.score}", style = FitType.hero.copy(fontSize = 34.sp),
                        color = if (result.won) Color(0xFF7CF7D4) else Color(0xFFFF8A8A), modifier = Modifier.align(Alignment.CenterHorizontally))
                    Caption(when {
                        result.won && (stars ?: 0) > 0 -> "+★$stars earned"
                        result.won -> "No stars this time (daily limit reached) — the win still counts"
                        else -> "Raise ${result.rounds.filter { !it.won }.joinToString(" and ") { it.stat.label }} this week and come back"
                    }, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Share", { share = true }, Modifier.weight(1f), icon = Duo.Send, height = 46.dp)
                        AccentButton("Done", onClose, Modifier.weight(1f), height = 46.dp)
                    }
                }
            }
        }
    }
    if (share) ShareBattleDialog(me, them, result) { share = false }
}

@Composable
private fun BattleBar(f: Float, color: Color, v: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).clip(CircleShape).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
        }
        Text("$v", style = FitType.caption, color = Color.White, modifier = Modifier.width(34.dp).padding(start = 8.dp))
    }
}

// ------------------------------------------------------------------ share cards

/** Your champion as a shareable card (solid drawing, so it renders the same into the image). */
@Composable
fun ShareChampionDialog(me: Fighter, onClose: () -> Unit) {
    ShareDialog("myfit_champion.png", "Share your champion", onClose) {
        ShareCardFrame(me.look.color) {
            ChampStill(me.look, Modifier.fillMaxWidth().height(260.dp), full = true)
            Text(me.look.label.uppercase(), style = FitType.hero.copy(fontSize = 40.sp), color = Color.White)
            Text("${me.look.tier.label} champion · Rating ${me.rating}", style = FitType.label, color = me.look.color)
            Spacer(Modifier.height(10.dp))
            Stat.entries.forEach { s -> ShareStat(s.label, me.stats[s.ordinal], me.look.color) }
            Spacer(Modifier.height(8.dp))
            Text("Powered by my real workouts", style = FitType.caption, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ShareBattleDialog(me: Fighter, them: Fighter, r: BattleResult, onClose: () -> Unit) {
    ShareDialog("myfit_battle.png", "Share this battle", onClose) {
        ShareCardFrame(if (r.won) me.look.color else them.look.color) {
            Row(Modifier.fillMaxWidth().height(200.dp)) {
                ChampStill(me.look, Modifier.weight(1f).fillMaxHeight())
                Text("VS", style = FitType.hero.copy(fontSize = 30.sp), color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
                ChampStill(them.look, Modifier.weight(1f).fillMaxHeight().graphicsLayer { scaleX = -1f })
            }
            Text(if (r.won) "VICTORY ${r.score}" else "DEFEAT ${r.score}", style = FitType.hero.copy(fontSize = 38.sp), color = Color.White)
            Text("${me.look.label} vs ${them.look.label}", style = FitType.label, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(8.dp))
            r.rounds.forEachIndexed { i, x -> Text("Round ${i + 1} · ${x.stat.label}: ${x.mine} – ${x.theirs}", style = FitType.caption, color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}

@Composable
private fun ShareCardFrame(color: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(320.dp).clip(RoundedCornerShape(28.dp))
        .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), Color(0xFF0B0D14))))
        .padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        Spacer(Modifier.height(12.dp))
        Text("MyFit Tracker · Game Mode", style = FitType.caption.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun ShareStat(label: String, v: Int, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = FitType.caption, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth((v / 100f).coerceIn(0f, 1f)).clip(CircleShape).background(color))
        }
        Text("$v", style = FitType.caption, color = Color.White, modifier = Modifier.width(30.dp).padding(start = 6.dp))
    }
}

/** Shows [card], records it into a bitmap and opens the share sheet. */
@Composable
private fun ShareDialog(file: String, title: String, onClose: () -> Unit, card: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Dialog(onDismissRequest = onClose) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) { card() }
            Spacer(Modifier.height(12.dp))
            AccentButton(title, {
                scope.launch {
                    runCatching {
                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                        val f = withContext(Dispatchers.IO) {
                            File(File(ctx.cacheDir, "reports").apply { mkdirs() }, file).also { out -> out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                        }
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { toaster.show("Couldn't create the image") }
                }
            }, Modifier.width(320.dp), icon = Duo.Send, height = 48.dp)
        }
    }
}
