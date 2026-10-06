package com.myfit.tracker.ui.glucose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.GlucoseReading
import com.myfit.tracker.domain.Glucose
import com.myfit.tracker.social.Patient
import com.myfit.tracker.social.SharedReading
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.NotesField
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.launch

/*
 * Blood sugar made simple: every reading is shown as a traffic light with plain words — Low (red), Good (green),
 * High (amber), Very high (red) — big numbers, big buttons, and family sharing so a son or daughter can keep an eye.
 */

private val TL_RED = Color(0xFFE5484D)
private val TL_GREEN = Color(0xFF2FBF71)
private val TL_AMBER = Color(0xFFF5A524)

/** Plain-words meaning of a reading. */
data class Meaning(val word: String, val advice: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

fun meaningOf(mgdl: Double, cfg: GlucoseConfig): Meaning = when {
    mgdl < Glucose.VERY_LOW -> Meaning("Very low", "Eat or drink something sweet right now, then check again in 15 minutes. Don't stay alone.", TL_RED, Duo.Info)
    mgdl < Glucose.LOW -> Meaning("Low", "Have something sweet (½ glass juice or 3 spoons sugar in water) and check again in 15 minutes.", TL_RED, Duo.Info)
    mgdl <= cfg.high -> Meaning("Good", "Your sugar is in your target. Well done!", TL_GREEN, Duo.CheckCircle)
    mgdl < Glucose.VERY_HIGH -> Meaning("High", "A bit high. Drink water, take a short walk if you can, and check again later.", TL_AMBER, Duo.ArrowUpward)
    else -> Meaning("Very high", "Very high. Drink water and follow your care plan. If you feel unwell, call your doctor.", TL_RED, Duo.ArrowUpward)
}

/** Big status card: traffic-light ring, giant number, one-line meaning, huge "Add reading" button. */
@Composable
fun StatusHero(latest: GlucoseReading?, cfg: GlucoseConfig, onLog: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard {
        if (latest == null) {
            Text("No reading yet", style = FitType.title, color = th.text)
            Spacer(Modifier.height(4.dp))
            Text("Tap the button below after you check your sugar.", style = FitType.body, color = th.textDim)
        } else {
            val m = meaningOf(latest.mgdl, cfg)
            val frac by animateFloatAsState(((latest.mgdl - 40) / (300 - 40)).toFloat().coerceIn(0.03f, 1f), tween(900), label = "g")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val s = 12.dp.toPx()
                        drawArc(th.text.copy(alpha = 0.08f), 135f, 270f, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
                        drawArc(m.color, 135f, 270f * frac, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Glucose.format(latest.mgdl, cfg.mmol), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = th.text)
                        Text(Glucose.unitLabel(cfg.mmol), style = FitType.caption, color = th.textDim)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.clip(RoundedCornerShape(14.dp)).background(m.color).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(m.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(m.word, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${agoText(latest.takenAt)} · ${Glucose.tagLabel(latest.tag)}", style = FitType.label, color = th.textDim)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(m.advice, fontSize = 17.sp, lineHeight = 24.sp, color = th.text)
        }
        Spacer(Modifier.height(16.dp))
        AccentButton("Add my sugar reading", onLog, Modifier.fillMaxWidth(), icon = Duo.Add, height = 62.dp)
    }
}

/** What the colours mean, in the user's own target numbers. */
@Composable
fun MeaningStrip(cfg: GlucoseConfig) {
    val th = LocalFitTheme.current
    val u = Glucose.unitLabel(cfg.mmol)
    fun f(v: Double) = Glucose.format(v, cfg.mmol)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("Low", "under ${f(Glucose.LOW)}", TL_RED),
            Triple("Good", "${f(cfg.low.toDouble())}–${f(cfg.high.toDouble())}", TL_GREEN),
            Triple("High", "over ${f(cfg.high.toDouble())}", TL_AMBER),
        ).forEach { (w, r, c) ->
            com.myfit.tracker.ui.theme.Glass(Modifier.weight(1f), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().background(c.copy(alpha = 0.12f)).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.height(4.dp))
                    Text(w, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = th.text, maxLines = 1)
                    Text(r, style = FitType.caption, color = th.textDim, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
    Caption("Numbers in $u. Your doctor may give you a different target — change it in Targets & units.", Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
}

/** Word pill for a reading (used in lists). */
@Composable
fun MeaningPill(mgdl: Double, cfg: GlucoseConfig) {
    val m = meaningOf(mgdl, cfg)
    Text(m.word, style = FitType.label, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(m.color).padding(horizontal = 10.dp, vertical = 4.dp))
}

/** One-sentence summary of recent readings ("Mostly good: 8 of 10 in target"). */
fun summarySentence(rs: List<GlucoseReading>, cfg: GlucoseConfig): String {
    if (rs.isEmpty()) return "No readings in this period yet."
    val good = rs.count { it.mgdl >= Glucose.LOW && it.mgdl <= cfg.high }
    val low = rs.count { it.mgdl < Glucose.LOW }
    val pct = good * 100 / rs.size
    val head = when { pct >= 70 -> "Mostly good"; pct >= 40 -> "Mixed"; else -> "Often out of target" }
    return "$head: $good of ${rs.size} readings were good" + if (low > 0) ", $low low." else "."
}

/** Large keypad for typing a reading — easy for older hands. */
@Composable
fun BigKeypad(value: String, decimal: Boolean, onChange: (String) -> Unit) {
    val th = LocalFitTheme.current
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (decimal) "." else "", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Box(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(18.dp))
                        .background(if (k.isEmpty()) Color.Transparent else th.text.copy(alpha = 0.08f))
                        .clickableNoRipple {
                            when (k) {
                                "" -> {}
                                "⌫" -> onChange(value.dropLast(1))
                                "." -> if ('.' !in value && value.isNotEmpty()) onChange("$value.")
                                else -> if (value.length < 5) onChange(value + k)
                            }
                        }, contentAlignment = Alignment.Center) {
                        Text(k, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = th.text)
                    }
                }
            }
        }
    }
}

// =================================================================== family sharing

@Composable
fun FamilyCard(container: AppContainer) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val fam = container.glucoseFamily
    val user by container.social.user.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var on by remember { mutableStateOf(fam.sharingOn(ctx)) }
    var code by remember { mutableStateOf(fam.myCode(ctx)) }
    var joinCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val carers by produceState(emptyList<com.myfit.tracker.social.Carer>(), on, refresh) { value = if (on) runCatching { fam.carers() }.getOrDefault(emptyList()) else emptyList() }
    val patients = remember(refresh) { fam.patients(ctx) }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.myfit.tracker.ui.components.IconBubble(Duo.Person, th.accent, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Family", style = FitType.section, color = th.text)
                Caption("Let a son or daughter see your readings and get an alert if you go low")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!container.social.available || user == null) {
            Text("Sign in first (You → Your MyFit account) to use family sharing.", style = FitType.body, color = th.textDim)
            return@GlassCard
        }
        if (!on) {
            AccentButton(if (busy) "Turning on…" else "Share my readings with family", {
                if (busy) return@AccentButton
                busy = true
                scope.launch {
                    runCatching { fam.enable(ctx) }.onSuccess { code = it; on = true }.onFailure { toaster.show(it.message ?: "Couldn't turn on sharing") }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), icon = Duo.Link, height = 54.dp)
        } else {
            Text("Your family code", style = FitType.label, color = th.textDim)
            Text(code ?: "······", fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = th.text)
            Caption("Give this code to your family. They type it in their MyFit app under Blood sugar → Family.")
            Spacer(Modifier.height(10.dp))
            GlassButton("Send code on WhatsApp / SMS", {
                val text = "Add me in MyFit to see my blood sugar: open Blood sugar → Family and enter code $code. Get MyFit: ${com.myfit.tracker.update.Store.appLink}"
                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), "Send family code"))
            }, Modifier.fillMaxWidth(), icon = Duo.Send, height = 50.dp)
            if (carers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Can see your readings", style = FitType.label, color = th.textDim)
                carers.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                        Text("Remove", style = FitType.label, color = th.danger, modifier = Modifier.clickableNoRipple { scope.launch { runCatching { fam.removeCarer(c.uid) }; refresh++ } }.padding(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Stop sharing", style = FitType.label, color = th.danger, modifier = Modifier.clickableNoRipple {
                scope.launch { runCatching { fam.disable(ctx) }; on = false; code = null; toaster.show("Sharing stopped — shared readings deleted") }
            }.padding(vertical = 8.dp))
        }

        // ---- carer side
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(th.text.copy(alpha = 0.08f)))
        Spacer(Modifier.height(12.dp))
        Text("People I care for", style = FitType.section, color = th.text)
        patients.forEach { p -> PatientRow(container, p, onLeave = { scope.launch { fam.leave(ctx, p); refresh++ } }) }
        Spacer(Modifier.height(8.dp))
        NotesField(joinCode, { joinCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(6) }, "Enter a family code to follow someone")
        Spacer(Modifier.height(8.dp))
        GlassButton("Follow", {
            scope.launch {
                runCatching { fam.join(ctx, joinCode) }.onSuccess { toaster.show("You'll now see ${it.name}'s readings"); joinCode = ""; refresh++ }
                    .onFailure { toaster.show(it.message ?: "Couldn't follow") }
            }
        }, Modifier.fillMaxWidth(), icon = Duo.Add, height = 48.dp)
    }
}

@Composable
private fun PatientRow(container: AppContainer, p: Patient, onLeave: () -> Unit) {
    val th = LocalFitTheme.current
    val rs by produceState<List<SharedReading>?>(null, p.uid) { value = runCatching { container.glucoseFamily.readings(p, 10) }.getOrNull() }
    val cfg = GlucoseConfig()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(th.text.copy(alpha = 0.05f)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = FitType.section, color = th.text, modifier = Modifier.weight(1f))
            Text("Stop", style = FitType.label, color = th.textDim, modifier = Modifier.clickableNoRipple(onLeave).padding(6.dp))
        }
        val list = rs
        when {
            list == null -> Caption("Loading…")
            list.isEmpty() -> Caption("No readings shared yet.")
            else -> {
                val last = list.first()
                val m = meaningOf(last.mgdl, cfg)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${last.mgdl.toInt()}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = th.text)
                    Spacer(Modifier.width(8.dp))
                    MeaningPill(last.mgdl, cfg)
                    Spacer(Modifier.width(8.dp))
                    Caption(agoText(last.takenAt))
                }
                Caption(m.advice)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.take(8).reversed().forEach { r -> Box(Modifier.size(14.dp).clip(CircleShape).background(meaningOf(r.mgdl, cfg).color)) }
                }
            }
        }
    }
}

