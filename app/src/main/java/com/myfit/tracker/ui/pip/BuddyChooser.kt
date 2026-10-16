package com.myfit.tracker.ui.pip

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val portraitCache = android.util.LruCache<String, ImageBitmap>(24)

/** Round portrait of any buddy look (bundled in the APK). [dim] greys it out for locked looks. */
@Composable
fun BuddyAvatar(b: BuddyChar, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false) {
    val ctx = LocalContext.current
    val path = Buddy.portraitAsset(b)
    val bmp by produceState(portraitCache.get(path), path) {
        value = portraitCache.get(path) ?: withContext(Dispatchers.IO) {
            runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
        }?.also { portraitCache.put(path, it) }
    }
    Box(modifier.size(size).clip(CircleShape).background(Brush.radialGradient(listOf(b.color.copy(alpha = 0.55f), b.color.copy(alpha = 0.12f)))),
        contentAlignment = Alignment.Center) {
        bmp?.let {
            Image(it, b.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                colorFilter = if (dim) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null, alpha = if (dim) 0.55f else 1f)
        }
    }
}

/** Pick who lives on your Home screen and chats with you: Pip, or a champion look you've unlocked. */
@Composable
fun BuddyChooser(modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val toaster = LocalToaster.current
    val active by Buddy.active.collectAsState()
    var ver by remember { mutableIntStateOf(0) }            // bump after a purchase/download so the cards refresh
    val level = remember(ver) { Champions.level(ctx) }
    val balance = remember(ver) { Champions.balance(ctx) }
    var open by remember { mutableStateOf<Champion?>(null) }
    val scope = rememberCoroutineScope()
    val dl by Buddy.downloading.collectAsState()

    fun use(b: BuddyChar) {
        if (Buddy.installed(ctx, b)) { tick(); Buddy.choose(ctx, b); toaster.show("${b.label} is your Home buddy now"); return }
        if (b.id in dl) return
        scope.launch {
            val err = Buddy.download(ctx, b)
            ver++
            if (err == null) { Buddy.choose(ctx, b); toaster.show("${b.label} is your Home buddy now") } else toaster.show(err)
        }
    }

    open?.let { ch -> ChampionSheet(ch, active, dl, onDismiss = { open = null }, onUse = { use(it) }, onBuy = { t ->
        if (Champions.buy(ctx, ch, t)) { tick(); ver++; toaster.show("${ch.tag} ${t.label} unlocked") } else Champions.blocker(ctx, ch, t)?.let { toaster.show(it) }
    }, version = ver) }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Home buddy", style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
            Caption("★ $balance to spend · Lv $level")
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                BuddyCard(BuddyChar.PIP, sel = active.isPip, locked = false, caption = if (active.isPip) "Active" else "Tap to use") { use(BuddyChar.PIP) }
            }
            items(Champion.entries.toList()) { ch ->
                val unlocked = remember(ch, ver) { Champions.unlocked(ctx, ch) }
                // show the best look you own (or the one in use)
                val shown = remember(ch, ver, active) {
                    if (active.champion == ch) active else BuddyChar(ch, Tier.entries.lastOrNull { Champions.owns(ctx, ch, it) } ?: Tier.STREET)
                }
                val cap = when {
                    !unlocked -> "Lv ${ch.unlock}"
                    shown.id in dl -> "${((dl[shown.id] ?: 0f) * 100).toInt()}%"
                    active.champion == ch -> "Active"
                    else -> ch.rarity.label
                }
                BuddyCard(shown, sel = active.champion == ch, locked = !unlocked, caption = cap) { open = ch }
            }
        }
    }
}

@Composable
private fun BuddyCard(b: BuddyChar, sel: Boolean, locked: Boolean, caption: String, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Column(
        Modifier.width(88.dp).clip(RoundedCornerShape(18.dp))
            .background(if (sel) b.color.copy(alpha = 0.28f) else th.textFaint.copy(alpha = 0.10f))
            .border(if (sel) 2.dp else 0.dp, if (sel) b.color else Color.Transparent, RoundedCornerShape(18.dp))
            .clickableNoRipple(onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            BuddyAvatar(b, 70.dp, dim = locked)
            if (locked) Box(Modifier.align(Alignment.BottomEnd).size(21.dp).clip(CircleShape).background(Color(0xE6101218)), contentAlignment = Alignment.Center) {
                Icon(Duo.Lock, "Locked", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        Text(b.label, style = FitType.label, color = th.text)
        Caption(caption)
    }
}

/** A champion's card: its three looks, what they cost, and use / unlock. */
@Composable
private fun ChampionSheet(
    ch: Champion, active: BuddyChar, dl: Map<String, Float>, onDismiss: () -> Unit, onUse: (BuddyChar) -> Unit, onBuy: (Tier) -> Unit, version: Int,
) {
    val ctx = LocalContext.current
    var tier by remember(ch) { mutableStateOf(if (active.champion == ch) active.tier else Tier.entries.lastOrNull { Champions.owns(ctx, ch, it) } ?: Tier.STREET) }
    val owns = remember(ch, tier, version) { Champions.owns(ctx, ch, tier) }
    val blocker = remember(ch, tier, version) { Champions.blocker(ctx, ch, tier) }
    val look = BuddyChar(ch, tier)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(30.dp)).background(Brush.verticalGradient(listOf(ch.color.copy(alpha = 0.85f), Color(0xFF15171F)))).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BuddyAvatar(look, 150.dp, dim = !Champions.unlocked(ctx, ch))
            Spacer(Modifier.height(10.dp))
            Text(ch.tag, style = FitType.title, color = Color.White)
            Text("${ch.rarity.label} · ${ch.archetype}", style = FitType.label, color = Color.White.copy(alpha = 0.8f))
            Text("“${ch.motto}”", style = FitType.body, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tier.entries.forEach { t ->
                    val have = remember(ch, t, version) { Champions.owns(ctx, ch, t) }
                    com.myfit.tracker.ui.theme.GlassChip(if (have || t == Tier.STREET) t.label else "${t.label} ★${t.cost}", tier == t, { tier = t })
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                owns && active == look -> Caption("In use", color = Color.White)
                owns -> com.myfit.tracker.ui.theme.AccentButton(if (look.id in dl) "Downloading ${((dl[look.id] ?: 0f) * 100).toInt()}%" else "Use this look",
                    { onUse(look); onDismiss() }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 46.dp)
                blocker == null -> com.myfit.tracker.ui.theme.AccentButton("Unlock for ★${tier.cost}", { onBuy(tier) }, Modifier.fillMaxWidth(), icon = Duo.Lock, height = 46.dp)
                else -> Caption(blocker, color = Color.White)
            }
            Spacer(Modifier.height(6.dp))
            Caption("Stars come from workouts, steps and Arena challenges. Spending them never lowers your level.", color = Color.White.copy(alpha = 0.7f))
        }
    }
}
