package com.myfit.tracker.ui.pip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.BrainMode
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings

/** One-time request from elsewhere (Me → Pip settings) to open the chat with the settings sheet showing. */
object PipChatLaunch { @Volatile var openSettings = false }

/** The three brains, each as one clear row. */
@Composable
fun BrainPicker(container: AppContainer, onPicked: () -> Unit = {}) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val settings = LocalSettings.current
    val mode by BrainMode.flow.collectAsState()
    val cur = mode ?: BrainMode.get(ctx)
    val ai = remember { com.myfit.tracker.ai.ondevice.OnDeviceAi.get(ctx) }
    val installed by androidx.compose.runtime.produceState(true, cur) { value = ai.models.installedFile() != null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(BrainMode.AUTO, Duo.AutoAwesome, "Uses the on-phone brain when it's downloaded, otherwise the online AI. Best of both."),
            Triple(BrainMode.ONLINE, Duo.Cloud, "Fastest and smartest. Sends only a short, question-specific summary — never your full history."),
            Triple(BrainMode.PHONE, Duo.Lock, if (installed) "Private and free. Nothing leaves your phone; answers are a bit slower." else "Private and free — needs a one-time download (Downloads below)."),
        ).forEach { (m, icon, sub) ->
            val sel = cur == m
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(if (sel) th.accent.copy(alpha = 0.16f) else th.text.copy(alpha = 0.05f))
                    .border(if (sel) 1.5.dp else 0.dp, if (sel) th.accentBright else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickableNoRipple {
                        BrainMode.set(ctx, m)
                        // keep the old "online answers" switch in step so every screen agrees
                        container.write { container.settings.setOnlineAi(m != BrainMode.PHONE) }
                        onPicked()
                    }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background((if (sel) th.accentBright else th.textDim).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = if (sel) th.accentBright else th.textDim, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(BrainMode.label(m), style = FitType.section, color = th.text)
                    Caption(sub)
                }
                if (sel) Icon(Duo.CheckCircle, null, tint = th.accentBright, modifier = Modifier.size(22.dp))
            }
        }
        if (!settings.onlineAi && cur != BrainMode.PHONE) Caption("Online answers are off — pick a brain above to switch them back on.", color = th.warning)
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String, color: Color) {
    val th = LocalFitTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = FitType.overline, color = th.textDim)
    }
}

/**
 * Everything about Pip in one place: personality (buddy), brain, voice, downloads and the chat history.
 * Used by the ⚙ sheet in the chat (and opened from Me → Pip).
 */
@Composable
fun PipSettingsContent(container: AppContainer, onClearChat: () -> Unit) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    Text("Pip settings", style = FitType.title, color = th.text)
    Caption("Questions about your own logs are always answered on the phone, from your data.")

    SectionTitle(Duo.Person, "Personality", th.fat)
    BuddyChooser()

    SectionTitle(Duo.AutoAwesome, "Brain", th.accentBright)
    BrainPicker(container)

    SectionTitle(Duo.VolumeUp, "Voice", th.water)
    ToggleRow("Pip speaks", "Reads replies aloud. Tap the mic in the chat to talk to Pip.", settings.pipVoice) { container.write { container.settings.setPipVoice(it) } }
    Spacer(Modifier.height(8.dp))
    com.myfit.tracker.ui.settings.VoiceSettingsCard(container, dev = false)

    SectionTitle(Duo.ArrowDownward, "Downloads", th.success)
    com.myfit.tracker.ui.settings.OfflineBrainCard()

    val ctxP = LocalContext.current
    if (com.myfit.tracker.ai.ondevice.RewardedAds.privacyOptionsRequired(ctxP)) {
        SectionTitle(Duo.Lock, "Ad privacy", th.textDim)
        GlassButton("Ad privacy options", {
            (ctxP as? android.app.Activity ?: (ctxP as? android.content.ContextWrapper)?.baseContext as? android.app.Activity)
                ?.let { com.myfit.tracker.ai.ondevice.RewardedAds.privacyOptions(it) }
        }, Modifier.fillMaxWidth(), height = 46.dp)
    }

    SectionTitle(Duo.ChatBubble, "Chat", th.textDim)
    var confirm by remember { mutableStateOf(false) }
    GlassButton(if (confirm) "Tap again to clear the conversation" else "Clear chat history", {
        if (confirm) { onClearChat(); confirm = false } else confirm = true
    }, Modifier.fillMaxWidth(), icon = Duo.DeleteSweep, height = 46.dp)
    Caption("Only the conversation is removed. None of your logged data is touched.", Modifier.padding(top = 6.dp), color = th.textFaint)
    Spacer(Modifier.height(12.dp))
}
