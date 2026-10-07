package com.myfit.tracker.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.social.ArenaProfile
import com.myfit.tracker.ui.arena.Journeys
import com.myfit.tracker.ui.arena.Mascot
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import kotlinx.coroutines.launch
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme

/**
 * A friend's profile: picture, level, stars, buddy, this week's numbers, finished journeys and recent rewards.
 * Shows the cached [f] instantly and swaps in fresher Arena data when it arrives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendProfileContent(container: AppContainer, f: com.myfit.tracker.social.FriendCard, onRemove: (() -> Unit)? = null, onBlocked: (() -> Unit)? = null) {
    val th = LocalFitTheme.current
    val fresh by produceState<ArenaProfile?>(null, f.uid) { value = runCatching { container.social.arenaProfile(f.uid) }.getOrNull() }
    val level = fresh?.level ?: f.level
    val stars = fresh?.stars ?: f.stars
    val steps = fresh?.weekSteps?.let { maxOf(it, f.weekSteps) } ?: f.weekSteps
    val active = fresh?.weekActiveMin?.let { maxOf(it, f.weekActiveMin) } ?: f.weekActiveMin
    val workouts = fresh?.weekWorkouts ?: f.weekWorkouts
    val journeys = fresh?.journeys ?: f.journeys
    val rewards = fresh?.rewards ?: f.rewards
    val mascot = fresh?.mascot ?: f.mascot
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        UserAvatar(f.avatar, f.photo, f.name, f.color, 96.dp, ring = th.accentBright, gphoto = f.gphoto, seed = f.uid)
        Spacer(Modifier.height(10.dp))
        Text(f.name, style = FitType.title, color = th.text, maxLines = 1)
        if (f.username.isNotBlank()) Caption("@${f.username}")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            level?.let { LevelChip(it) }
            if (stars != null) { Spacer(Modifier.width(8.dp)); Text("★ ${Fmt.int(stars.toDouble())} stars", style = FitType.label, color = Color(0xFFFFC83D)) }
            val buddy = mascot?.let { m -> Mascot.entries.firstOrNull { it.id == m } }
            if (buddy != null) { Spacer(Modifier.width(8.dp)); com.myfit.tracker.ui.arena.CastImage(buddy, 36.dp) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("This week", style = FitType.label, color = th.textDim)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Steps" to Fmt.int(steps.toDouble()), "Active min" to Fmt.int(active.toDouble()), "Workouts" to "$workouts").forEach { (k, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(th.text.copy(alpha = 0.06f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                com.myfit.tracker.ui.components.FitText(v, FitType.section, th.text)
                Caption(k)
            }
        }
    }
    Caption("Updated ${ago(f.updatedAt)}", Modifier.padding(top = 6.dp), color = th.textFaint)
    if (journeys.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("Journeys completed", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            journeys.forEach { id ->
                val j = Journeys.firstOrNull { it.id == id }
                Text("🏁 " + (j?.title ?: id), style = FitType.caption, color = th.text,
                    modifier = Modifier.clip(CircleShape).background(com.myfit.tracker.ui.arena.journeyAccent(id).copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Recent rewards", style = FitType.label, color = th.textDim)
    Spacer(Modifier.height(6.dp))
    if (rewards.isEmpty()) Caption(if (level == null) "${f.name} hasn't synced their Arena profile yet — it appears after their next app update and sync." else "No rewards yet.")
    rewards.take(12).forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("★", style = FitType.label, color = Color(0xFFFFC83D), modifier = Modifier.size(20.dp))
            Text(r, style = FitType.body, color = th.text)
        }
    }
    if (onRemove != null) {
        var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Text(if (confirm) "Tap again to remove ${f.name}" else "Remove friend", style = FitType.label, color = th.danger,
            modifier = Modifier.padding(top = 10.dp).clickableNoRipple { if (confirm) onRemove() else confirm = true }.padding(vertical = 8.dp))
    }
    SafetyActions(container, f, onBlocked)
    Spacer(Modifier.height(10.dp))
}


/** Report (offensive picture / name, spam, impersonation…) and Block — required for any social feature. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SafetyActions(container: AppContainer, f: com.myfit.tracker.social.FriendCard, onBlocked: (() -> Unit)?) {
    val th = LocalFitTheme.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val toaster = com.myfit.tracker.ui.components.LocalToaster.current
    var reporting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var reason by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var details by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var confirmBlock by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        com.myfit.tracker.ui.theme.GlassButton("Report", { reporting = !reporting }, Modifier.weight(1f), icon = com.myfit.tracker.ui.theme.Duo.Flag, height = 42.dp)
        com.myfit.tracker.ui.theme.GlassButton(if (confirmBlock) "Tap to confirm" else "Block", {
            if (!confirmBlock) { confirmBlock = true; return@GlassButton }
            scope.launch {
                runCatching { container.social.block(f.uid) }
                    .onSuccess { toaster.show("${f.name} is blocked"); onBlocked?.invoke() }
                    .onFailure { toaster.show(it.message ?: "Couldn't block — check your connection") }
            }
        }, Modifier.weight(1f), icon = com.myfit.tracker.ui.theme.Duo.Close, height = 42.dp)
    }
    if (reporting) {
        Spacer(Modifier.height(10.dp))
        Text("What's wrong?", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Offensive picture", "Offensive name", "Spam or fake", "Pretending to be someone", "Bullying", "Other").forEach { r ->
                com.myfit.tracker.ui.theme.GlassChip(r, reason == r, { reason = r })
            }
        }
        Spacer(Modifier.height(8.dp))
        com.myfit.tracker.ui.social.Field(details, { details = it.take(500) }, "Anything else? (optional)")
        Spacer(Modifier.height(8.dp))
        com.myfit.tracker.ui.theme.AccentButton("Send report", {
            val r = reason ?: run { toaster.show("Pick a reason"); return@AccentButton }
            scope.launch {
                runCatching { container.social.report(f.uid, r, details) }
                    .onSuccess { toaster.show("Thanks — we'll review it. You can also block ${f.name}."); reporting = false; reason = null; details = "" }
                    .onFailure { toaster.show(it.message ?: "Couldn't send — check your connection") }
            }
        }, Modifier.fillMaxWidth(), height = 44.dp)
        Caption("Reports go to the MyFit team. ${f.name} isn't told who reported them.", Modifier.padding(top = 6.dp), color = th.textFaint)
    }
}
