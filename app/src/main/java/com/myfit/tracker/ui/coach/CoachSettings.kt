package com.myfit.tracker.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.PipVoice
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.settings.ToggleRow
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme

/** Me → Pro trainer & nutritionist: pick looks, rename them, language, push level, camera and voice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProTeamCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val nav = LocalNav.current
    remember { Coach.load(ctx); 0 }
    val cp by Coach.prefs.collectAsState()
    var open by remember { mutableStateOf(false) }
    GlassCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachPortrait(cp.look.id, false, 52.dp)
            Spacer(Modifier.width(8.dp))
            CoachPortrait(cp.nLook.id, false, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Pro trainer & nutritionist", style = FitType.section, color = th.text)
                Caption("${cp.name} trains you · ${cp.nName} plans your food")
            }
        }
        if (!open) { Text("Customise", style = FitType.label, color = th.accentBright, modifier = Modifier.padding(top = 8.dp)); return@GlassCard }

        Text("TRAINER", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Coach.Look.entries.forEach { l -> GlassChip(l.label, cp.look == l, { Coach.update(ctx) { it.copy(look = l) } }, icon = if (l.male) Duo.Male else Duo.Female) }
        }
        NameField("Trainer's name", cp.name) { n -> Coach.update(ctx) { it.copy(name = n) } }
        Text("NUTRITIONIST", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Coach.NLook.entries.forEach { l -> GlassChip(l.label, cp.nLook == l, { Coach.update(ctx) { it.copy(nLook = l) } }, icon = if (l.male) Duo.Male else Duo.Female) }
        }
        NameField("Nutritionist's name", cp.nName) { n -> Coach.update(ctx) { it.copy(nName = n) } }
        Caption("They're fictional characters — call them whatever you like.", Modifier.padding(top = 4.dp))

        Text("COACHING", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("English", !cp.urdu, { Coach.update(ctx) { it.copy(urdu = false) } })
            GlassChip("اردو Urdu", cp.urdu, { Coach.update(ctx) { it.copy(urdu = true) } })
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Coach.Push.entries.forEach { p -> GlassChip(p.label, cp.push == p, { Coach.update(ctx) { it.copy(push = p) } }) }
        }
        Spacer(Modifier.height(8.dp))
        ToggleRow("Voice coaching", "Your trainer talks you through each set: setup, tempo, counting and pushing.", cp.voice) { v -> Coach.update(ctx) { it.copy(voice = v) } }
        ToggleRow("Camera form-check by default", "Counts reps and checks angles with the camera. Everything is processed on your phone; nothing is recorded.", cp.camera) { v -> Coach.update(ctx) { it.copy(camera = v) } }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Hear ${cp.name.substringAfter("Coach ")}", {
                val en = "Hi, I'm ${cp.name}. Let's train with good form and push for one more rep."
                val ur = "السلام علیکم، میں ${cp.name} ہوں۔ آئیں اچھی فارم کے ساتھ ورزش کریں۔"
                container.pipVoice.speak(en, ur = if (cp.urdu) ur else null, force = true, persona = PipVoice.Persona(cp.look.male))
            }, Modifier.weight(1f), icon = Duo.VolumeUp, height = 44.dp)
            GlassButton("Open ${cp.nName}", { nav.push(Overlay.Nutritionist) }, Modifier.weight(1f), icon = Duo.ForkKnife, height = 44.dp)
        }
        Caption("Start the trainer from any exercise in a live workout. Most natural voices need the online voice (Azure); otherwise your phone's voice is used.", Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun NameField(label: String, value: String, onSave: (String) -> Unit) {
    val th = LocalFitTheme.current
    var t by remember(value) { mutableStateOf(value) }
    Spacer(Modifier.height(8.dp))
    Glass(Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (t.isEmpty()) Text(label, style = FitType.body, color = th.textFaint)
                BasicTextField(t, { t = it.take(24) }, singleLine = true, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
            }
            if (t.trim() != value) Text("Save", style = FitType.label, color = th.accentBright, modifier = Modifier.padding(start = 8.dp).clickableNoRipple { onSave(t) }.padding(4.dp))
        }
    }
}
