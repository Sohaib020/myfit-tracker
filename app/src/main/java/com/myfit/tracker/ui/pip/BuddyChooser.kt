package com.myfit.tracker.ui.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.arena.ArenaProgress
import com.myfit.tracker.ui.arena.CastImage
import com.myfit.tracker.ui.arena.Mascot
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.launch

/** Pick who lives on your Home screen and chats with you. Characters other than Pip download (~1.7 MB) the first time. */
@Composable
fun BuddyChooser(modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val toaster = LocalToaster.current
    val active by Buddy.active.collectAsState()
    val level = remember { ArenaProgress.level(ArenaProgress.total(ctx)).n }
    var preview by remember { mutableStateOf<Mascot?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dl by Buddy.downloading.collectAsState()
    var packsVer by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    fun use(m: Mascot) {
        if (Buddy.installed(ctx, m)) { tick(); Buddy.choose(ctx, m); toaster.show("${m.label.substringBefore(' ')} is your Home buddy now"); return }
        if (m.id in dl) return
        scope.launch {
            val err = Buddy.download(ctx, m)
            packsVer++
            if (err == null) { Buddy.choose(ctx, m); toaster.show("${m.label.substringBefore(' ')} is your Home buddy now") } else toaster.show(err)
        }
    }
    preview?.let { pm -> com.myfit.tracker.ui.arena.CharacterPreview(pm, level, { preview = null }, onUse = { use(pm) }) }
    Column(modifier) {
        Text("Home buddy", style = FitType.section, color = th.text)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Mascot.entries.toList()) { m ->
                val open = level >= m.unlock
                val sel = m == active
                Column(
                    Modifier.width(88.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) m.accent.copy(alpha = 0.28f) else th.textFaint.copy(alpha = 0.10f))
                        .border(if (sel) 2.dp else 0.dp, if (sel) m.accent else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickableNoRipple {
                            when {
                                !open -> preview = m
                                else -> use(m)
                            }
                        }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (open) CastImage(m, 70.dp) else com.myfit.tracker.ui.arena.CastLocked(m, 70.dp)
                    Text(m.label.substringBefore(' '), style = FitType.label, color = th.text)
                    val installed = remember(m, packsVer) { Buddy.installed(ctx, m) }
                    when {
                        !open -> Caption("Lv ${m.unlock}")
                        m.id in dl -> Caption("${((dl[m.id] ?: 0f) * 100).toInt()}%")
                        sel -> Caption("Active")
                        !installed -> Caption("Download")
                        else -> Caption("Tap to use")
                    }
                }
            }
        }
    }
}
