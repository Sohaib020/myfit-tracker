package com.myfit.tracker.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.social.BoardRow
import com.myfit.tracker.social.Metric
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

/** Invite a friend (share link / QR) or scan theirs — the same flow everywhere it's offered. */
object FriendActions {
    fun invite(container: AppContainer, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, toast: (String) -> Unit) {
        scope.launch {
            val p = runCatching { container.social.ensureProfile() }.getOrNull()
            if (p != null) com.myfit.tracker.social.Invite.share(ctx, p.code, p.name) else toast("Sign in first (Me → Account)")
        }
    }

    fun scan(container: AppContainer, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope, toast: (String) -> Unit, done: () -> Unit) {
        if (container.social.user.value == null) { toast("Sign in first (Me → Account)"); return }
        runCatching {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx).startScan()
                .addOnSuccessListener { bc ->
                    val c = com.myfit.tracker.social.Invite.parse(bc.rawValue)
                    if (c == null) toast("That QR isn't a MyFit invite") else scope.launch {
                        runCatching { container.social.addFriendByCode(c) }
                            .onSuccess { toast("Added ${it.name} — you're competing now"); done() }
                            .onFailure { toast(it.message ?: "Couldn't add") }
                    }
                }
        }.onFailure { toast("Scanner unavailable: ${it.message ?: "try adding by code"}") }
    }
}

/**
 * The headline friends card: who you're racing this week, your rank, and big Invite / Scan buttons.
 * Signed out → one tap to sign in.
 */
@Composable
fun FriendsHero(container: AppContainer, modifier: Modifier = Modifier, onOpenFriends: (() -> Unit)? = null) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val social = container.social
    val user by social.user.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val board by produceState<List<BoardRow>?>(null, user, refresh) {
        value = if (user != null && social.available) runCatching { social.friendsBoard(Metric.STEPS) }.getOrNull() else null
    }
    val accent = th.accentBright
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), Color(0xFF2E6FD8).copy(alpha = 0.22f), th.bgBottom.copy(alpha = 0.6f))))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Icon(Duo.EmojiEvents, null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Compete with friends", style = FitType.section, color = th.text)
                    val b = board
                    Caption(when {
                        !social.available -> "Online features are off in this build"
                        user == null -> "Race friends on steps, distance and workouts"
                        b == null -> "Loading this week…"
                        b.size <= 1 -> "Invite a friend — you'll both appear on the board"
                        else -> { val me = b.indexOfFirst { it.me }; "${b.size - 1} friend${if (b.size == 2) "" else "s"} · you're #${me + 1} this week" }
                    })
                }
                if (onOpenFriends != null && user != null) Text("See all", style = FitType.label, color = accent, modifier = Modifier.clip(CircleShape).clickableNoRipple(onOpenFriends).padding(6.dp))
            }
            val b = board
            if (user != null && b != null && b.size > 1) {
                Spacer(Modifier.height(12.dp))
                b.take(3).forEachIndexed { i, r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = FitType.label, color = if (i == 0) Color(0xFFFFC83D) else th.textDim, modifier = Modifier.width(20.dp))
                        Avatar(r.name, r.color, 28)
                        Spacer(Modifier.width(8.dp))
                        Text(if (r.me) "You" else r.name, style = FitType.body, color = th.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fmt.int(r.value) + " steps", style = FitType.label, color = th.text)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (!social.available) Unit
            else if (user == null) AccentButton("Sign in to compete", { nav.push(Overlay.Me) }, Modifier.fillMaxWidth(), icon = Duo.Person, height = 46.dp)
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentButton("Invite friends", { FriendActions.invite(container, ctx, scope) { toaster.show(it) } }, Modifier.weight(1f), icon = Duo.Send, height = 46.dp)
                GlassButton("Scan QR", { FriendActions.scan(container, ctx, scope, { toaster.show(it) }) { refresh++ } }, Modifier.weight(1f), icon = Duo.Scan, height = 46.dp)
            }
        }
    }
}


/** Compact friends strip for the Arena: avatars, your rank, Add friend and See all (opens the Friends page). */
@Composable
fun FriendsGlimpse(container: AppContainer, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val social = container.social
    val user by social.user.collectAsState()
    val board by produceState<List<BoardRow>?>(null, user) {
        value = if (user != null && social.available) runCatching { social.friendsBoard(Metric.STEPS) }.getOrNull() else null
    }
    com.myfit.tracker.ui.components.GlassCard(modifier, padding = 14.dp, onClick = { nav.push(Overlay.Social) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val b = board.orEmpty().filter { !it.me }
            Box(Modifier.width((28 + 20 * (b.take(4).size - 1).coerceAtLeast(0)).dp).height(32.dp)) {
                if (b.isEmpty()) Box(Modifier.size(32.dp).clip(CircleShape).background(th.accentBright.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(Duo.Person, null, tint = th.accentBright, modifier = Modifier.size(18.dp))
                } else b.take(4).forEachIndexed { i, r -> Box(Modifier.offset(x = (i * 20).dp)) { Avatar(r.name, r.color, 30) } }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Friends", style = FitType.section, color = th.text)
                val all = board
                Caption(when {
                    user == null -> "Sign in to compete"
                    all == null -> "Loading…"
                    all.size <= 1 -> "No friends yet"
                    else -> "${all.size - 1} friend${if (all.size == 2) "" else "s"} · you're #${all.indexOfFirst { it.me } + 1} this week"
                })
            }
            com.myfit.tracker.ui.dashboard.CompactPill("Add friend", Duo.Add, {
                if (user == null) nav.push(Overlay.Social) else FriendActions.invite(container, ctx, scope) { toaster.show(it) }
            }, height = 36.dp)
        }
    }
}
