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
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme

/** A friend's profile: level, stars, buddy, this week's numbers, finished journeys and recent rewards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendProfileContent(container: AppContainer, uid: String, name: String, color: Long) {
    val th = LocalFitTheme.current
    val p by produceState<ArenaProfile?>(null, uid) { value = runCatching { container.social.arenaProfile(uid) }.getOrNull() }
    val loaded by produceState(false, uid) { kotlinx.coroutines.delay(2500); value = true }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, color, 64)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = FitType.title, color = th.text, maxLines = 1)
            val pr = p
            if (pr != null) Row(verticalAlignment = Alignment.CenterVertically) {
                LevelChip(pr.level)
                Spacer(Modifier.width(8.dp))
                Text("★ ${Fmt.int(pr.stars.toDouble())} stars", style = FitType.label, color = Color(0xFFFFC83D))
            }
        }
        val buddy = p?.mascot?.let { m -> Mascot.entries.firstOrNull { it.id == m } }
        if (buddy != null) com.myfit.tracker.ui.arena.CastImage(buddy, 56.dp)
    }
    Spacer(Modifier.height(16.dp))
    val pr = p
    if (pr == null) {
        Caption(if (loaded) "$name hasn't synced their Arena profile yet — it appears after their next app update and sync." else "Loading…")
        return
    }
    Text("This week", style = FitType.label, color = th.textDim)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Steps" to Fmt.int(pr.weekSteps.toDouble()), "Active min" to Fmt.int(pr.weekActiveMin.toDouble()), "Workouts" to "${pr.weekWorkouts}").forEach { (k, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(th.text.copy(alpha = 0.06f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                com.myfit.tracker.ui.components.FitText(v, FitType.section, th.text)
                Caption(k)
            }
        }
    }
    if (pr.journeys.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Text("Journeys completed", style = FitType.label, color = th.textDim)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            pr.journeys.forEach { id ->
                val j = Journeys.firstOrNull { it.id == id }
                Text("🏁 " + (j?.title ?: id), style = FitType.caption, color = th.text,
                    modifier = Modifier.clip(CircleShape).background(com.myfit.tracker.ui.arena.journeyAccent(id).copy(alpha = 0.25f)).padding(horizontal = 10.dp, vertical = 5.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Recent rewards", style = FitType.label, color = th.textDim)
    Spacer(Modifier.height(6.dp))
    if (pr.rewards.isEmpty()) Caption("No rewards yet.")
    pr.rewards.take(12).forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("★", style = FitType.label, color = Color(0xFFFFC83D), modifier = Modifier.size(20.dp))
            Text(r, style = FitType.body, color = th.text)
        }
    }
    Spacer(Modifier.height(10.dp))
}
