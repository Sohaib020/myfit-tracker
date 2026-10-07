package com.myfit.tracker.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.HealthProfile
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

/**
 * Pick diets, intolerances, medical conditions, injuries and life stages — or type your own, which is matched
 * offline against 50+ known conditions and, if unknown, classified by the AI (only the condition's name is sent)
 * into food and exercise rules you confirm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HealthProfilePicker(container: AppContainer, compact: Boolean = false) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    remember { HealthProfile.load(ctx); 0 }
    val sel by HealthProfile.selected.collectAsState()
    val customs by HealthProfile.customs.collectAsState()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var proposal by remember { mutableStateOf<HealthProfile.Condition?>(null) }
    val pickable = remember { HealthProfile.catalog.filter { it.id != "no_beef" } }

    HealthProfile.Group.entries.forEach { g ->
        Text(g.label.uppercase(), style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (pickable.filter { it.group == g } + customs.filter { it.group == g }).forEach { c ->
                GlassChip(c.label, c.id in sel, { HealthProfile.toggle(ctx, c.id) })
            }
        }
    }

    Text("SOMETHING ELSE?", style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Glass(Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(20.dp)) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("Type it — e.g. gout, migraine, slip disc", style = FitType.body, color = th.textFaint)
            BasicTextField(text, { text = it.take(60) }, singleLine = true, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
        }
    }
    Spacer(Modifier.height(8.dp))
    GlassButton(if (busy) "Checking…" else "Add", {
        val t = text.trim()
        if (t.length < 2 || busy) return@GlassButton
        val hits = HealthProfile.detect(t)
        if (hits.isNotEmpty()) {
            // known condition(s): select them straight away
            hits.take(3).forEach { if (it.id !in HealthProfile.selected.value) HealthProfile.toggle(ctx, it.id) }
            toaster.show("Added: " + hits.take(3).joinToString { it.label })
            text = ""
            return@GlassButton
        }
        busy = true
        scope.launch {
            val p = runCatching { container.aiRouter.text("You are a careful clinical dietitian and physiotherapist.", HealthProfile.aiPrompt(t), 30_000) }
                .getOrNull()?.let { HealthProfile.fromAi(t, it) }
            busy = false
            proposal = p ?: HealthProfile.Condition("custom_" + t.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(30), t.take(40), HealthProfile.Group.MEDICAL, listOf(t.lowercase()), custom = true)
            if (p == null) toaster.show("Couldn't look it up right now — saved as a note; edit the rules below if you like")
        }
    }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 46.dp)
    Caption("Only the condition's name is sent to the AI if it isn't in our list. Your health profile stays on this phone.", Modifier.padding(top = 6.dp), color = th.textFaint)

    proposal?.let { p ->
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(th.accent.copy(alpha = 0.12f)).padding(14.dp)) {
            Text("\"${p.label}\" — here's what MyFit will do", style = FitType.label, color = th.text)
            Spacer(Modifier.height(6.dp))
            fun line(t: String, s: Set<String>) { if (s.isNotEmpty()) Caption("$t: " + s.joinToString { HealthProfile.tagLabel(it).lowercase() }, color = th.text) }
            line("Avoid foods", p.avoid); line("Limit foods", p.limit); line("Avoid exercises", p.avoidEx); line("Careful with", p.limitEx)
            if (p.tip.isNotBlank()) Caption("Tip: ${p.tip}", color = th.textDim)
            if (p.avoid.isEmpty() && p.limit.isEmpty() && p.avoidEx.isEmpty() && p.limitEx.isEmpty()) Caption("No special food or exercise rules — it's kept as a note for your trainer and nutritionist.", color = th.textDim)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentButton("Add it", { HealthProfile.addCustom(ctx, p); proposal = null; text = ""; toaster.show("Added ${p.label}") }, Modifier.weight(1f), icon = Duo.Check, height = 44.dp)
                GlassButton("Cancel", { proposal = null }, Modifier.weight(1f), height = 44.dp)
            }
        }
    }
    if (customs.isNotEmpty() && !compact) {
        Spacer(Modifier.height(12.dp))
        customs.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Caption("Your own: ${c.label}", Modifier.weight(1f))
                Icon(Duo.Close, "Remove ${c.label}", tint = th.textDim, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).clickableNoRipple { HealthProfile.removeCustom(ctx, c.id) }.padding(5.dp))
            }
        }
    }
    val chosen = HealthProfile.active
    if (chosen.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(th.text.copy(alpha = 0.05f)).padding(12.dp)) {
            Text("How MyFit adapts", style = FitType.label, color = th.text)
            Caption("Meal plans, workouts, the trainer and the nutritionist avoid what doesn't suit you and suggest safer swaps. In food and exercise lists those items are hidden (you can show them, with a warning).", color = th.textDim)
            chosen.mapNotNull { c -> c.tip.takeIf { it.isNotBlank() }?.let { c.label to it } }.take(4).forEach { (l, t) ->
                Spacer(Modifier.height(4.dp)); Caption("• $l: $t", color = th.text)
            }
        }
    }
}

/** Me → Health & diet card. */
@Composable
fun HealthProfileCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    remember { HealthProfile.load(ctx); 0 }
    val sel by HealthProfile.selected.collectAsState()
    var open by remember { mutableStateOf(false) }
    com.myfit.tracker.ui.components.GlassCard(onClick = { open = !open }) {
        com.myfit.tracker.ui.components.CardHeader(Duo.HealthAndSafety, "Health & diet", th.danger)
        Spacer(Modifier.height(6.dp))
        Caption(if (sel.isEmpty()) "Add diets, intolerances, conditions or injuries — meal plans, workouts, the trainer and nutritionist adapt to them."
            else HealthProfile.active.joinToString(" · ") { it.label })
        if (open) {
            HealthProfilePicker(container)
            val show by HealthProfile.showUnsuitable.collectAsState()
            Spacer(Modifier.height(8.dp))
            ToggleRow("Show items that don't suit me", "Food and exercise lists show them too, with a warning.", show) { HealthProfile.setShowUnsuitable(ctx, it) }
        } else Text("Edit", style = FitType.label, color = th.accentBright, modifier = Modifier.padding(top = 6.dp))
    }
}
