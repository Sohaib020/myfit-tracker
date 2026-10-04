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

/** Pick who lives on your Home screen and chats with you. Unlocked characters download their full pack (~10 MB) once. */
@Composable
fun BuddyChooser(modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val active by Buddy.active.collectAsState()
    val prog by Buddy.progress.collectAsState()
    val level = remember { ArenaProgress.level(ArenaProgress.total(ctx)).n }
    Column(modifier) {
        Text("Home buddy", style = FitType.section, color = th.text)
        Caption("Who lives on your Home screen and answers in chat. They do every move Pip does.")
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Mascot.entries.toList()) { m ->
                val open = level >= m.unlock
                val have = Buddy.installed(ctx, m)
                val p = prog[m.id]
                val busy = p != null && p in 0f..0.999f
                val sel = m == active
                Column(
                    Modifier.width(88.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (sel) m.accent.copy(alpha = 0.28f) else th.textFaint.copy(alpha = 0.10f))
                        .border(if (sel) 2.dp else 0.dp, if (sel) m.accent else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickableNoRipple {
                            when {
                                !open -> toaster.show("${m.label} unlocks at Arena level ${m.unlock}")
                                busy -> Unit
                                have -> { tick(); Buddy.choose(ctx, m); toaster.show("${m.label.substringBefore(' ')} is your Home buddy now") }
                                else -> { tick(); toaster.show("Downloading ${m.label.substringBefore(' ')}…")
                                    scope.launch { if (!Buddy.download(ctx, m)) toaster.show("Couldn't download ${m.label.substringBefore(' ')} — check your connection") } }
                            }
                        }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CastImage(m, 70.dp, dim = !open)
                    Text(m.label.substringBefore(' '), style = FitType.label, color = th.text)
                    when {
                        !open -> Caption("🔒 Lv ${m.unlock}")
                        busy -> Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(CircleShape).background(th.textFaint.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(p!!.coerceAtLeast(0.04f)).background(m.accent))
                        }
                        sel -> Caption("Active")
                        have -> Caption("Tap to use")
                        else -> Caption("⬇ ~10 MB")
                    }
                }
            }
        }
    }
}
