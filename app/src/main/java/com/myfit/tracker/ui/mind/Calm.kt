package com.myfit.tracker.ui.mind

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.SectionTitle
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val CALM_TOP = Color(0xFF2A3A6B)
private val CALM_BOTTOM = Color(0xFF1B6B6A)
private val TEAL = Color(0xFF5ED1C4)

/** Pakistan support lines (checked Oct 2026 — services can change). */
private val HELPLINES = listOf(
    Triple("Umang mental health helpline", "0311-7786264", "24/7 · free"),
    Triple("Rozan counselling helpline", "0304-111-1741", "Emotional health & abuse"),
    Triple("Rescue / emergency", "1122", "If you are in danger now"),
    Triple("Madadgar (women & children)", "1098", "Violence & abuse, 24/7"),
)

/** Anxiety & Depression: fast calming (SOS), evidence-based self-help tools, weekly self-checks and support. */
@Composable
fun CalmTab(container: AppContainer, onBreath: (BreathPattern, Int, Boolean) -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    var ver by remember { mutableIntStateOf(0) }
    var tool by remember { mutableStateOf<String?>(null) }
    var sos by remember { mutableStateOf(false) }
    val assessments = remember(ver) { CalmStore.assessments(ctx) }
    val recentRisk = assessments.lastOrNull { it.kind == "phq9" }?.takeIf { it.item9 > 0 && it.date.isAfter(Clock.today().minusDays(14)) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recentRisk != null) item(key = "risk") { SupportCard(urgent = true) }
            item(key = "sos") { SosHero { sos = true } }
            item(key = "t1") { SectionTitle("Calm down fast") }
            item(key = "relief") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("Cyclic sighing", "2 in, long out · 3 min", Duo.Pulse, TEAL, Modifier.weight(1f)) { onBreath(BreathPatterns.first { it.id == "sigh" }, 3, true) }
                    ToolTile("5-4-3-2-1", "Grounding", Duo.TrackChanges, Color(0xFF8C7CFF), Modifier.weight(1f)) { tool = "ground" }
                }
            }
            item(key = "relief2") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolTile("Muscle relaxation", "7 areas · 4 min", Duo.SelfImprovement, Color(0xFFFF9F68), Modifier.weight(1f)) { tool = "pmr" }
                    ToolTile("Box breathing", "4·4·4·4 · 4 min", Duo.RadioButtonUnchecked, Color(0xFF4FA9FF), Modifier.weight(1f)) { onBreath(BreathPatterns.first { it.id == "box" }, 4, true) }
                }
            }
            item(key = "t2") { SectionTitle("Lift your mood") }
            item(key = "ba") { ActionCard("Plan small good things", "When mood is low, doing comes before feeling. Pick 1–3 small activities for today.", Duo.CheckCircle, th.success, "${CalmStore.plan(ctx).count { it.second }}/${CalmStore.plan(ctx).size} done today".takeIf { CalmStore.plan(ctx).isNotEmpty() }) { tool = "ba" } }
            item(key = "cbt") { ActionCard("Reframe a thought", "Catch an anxious or harsh thought, check the evidence and find a fairer one.", Duo.ChatBubble, Color(0xFF8C7CFF), CalmStore.thoughts(ctx).size.takeIf { it > 0 }?.let { "$it saved" }) { tool = "cbt" } }
            item(key = "worry") { ActionCard("Worry time", "Park worries now, deal with them at one set time — so they don't run your whole day.", Duo.Schedule, th.warning, CalmStore.worries(ctx).size.takeIf { it > 0 }?.let { "$it parked" }) { tool = "worry" } }
            item(key = "grat") { ActionCard("3 good things", "Write three things that went well today, however small.", Duo.Favorite, Color(0xFFFF6F91), "${CalmStore.gratitude(ctx).count { it.isNotBlank() }}/3 today") { tool = "grat" } }
            item(key = "t3") { SectionTitle("Weekly check-in") }
            item(key = "check") { CheckInCard(assessments) { tool = "check" } }
            item(key = "prog") { ProgressCard(ver) }
            item(key = "help") { if (recentRisk == null) SupportCard(urgent = false) }
            item(key = "foot") {
                Caption("Based on approaches with research support: slow breathing and cyclic sighing, grounding, progressive muscle relaxation, CBT thought records, worry postponement, behavioural activation and gratitude journaling. These are self-help tools, not a diagnosis or treatment. If low mood or anxiety lasts more than two weeks or affects daily life, please see a doctor or counsellor.",
                    Modifier.padding(horizontal = 6.dp), color = th.textFaint)
            }
        }
        GlassSheet(tool != null, { tool = null; ver++ }) {
            when (tool) {
                "ground" -> GroundingTool { tool = null }
                "pmr" -> PmrTool { tool = null }
                "ba" -> ActivityPlanner()
                "cbt" -> ThoughtTool { tool = null; ver++ }
                "worry" -> WorryTool()
                "grat" -> GratitudeTool { tool = null; ver++ }
                "check" -> CheckInFlow { tool = null; ver++ }
                else -> {}
            }
        }
        if (sos) SosFlow(onDone = { sos = false; ver++ }, onBreath = { sos = false; onBreath(BreathPatterns.first { it.id == "sigh" }, 3, true) }, onPlan = { sos = false; tool = "ba" })
    }
}

// ------------------------------------------------------------------ hub pieces

@Composable
private fun SosHero(onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "sos")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(CALM_TOP, CALM_BOTTOM))).clickableNoRipple(onClick)) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width * 0.84f, size.height * 0.5f)
            for (k in 3 downTo 1) drawCircle(TEAL.copy(alpha = 0.10f * k), size.height * (0.22f + 0.12f * k) * (0.9f + 0.15f * p), c)
            drawCircle(Color.White.copy(alpha = 0.9f), size.height * 0.16f * (0.9f + 0.15f * p), c)
        }
        Column(Modifier.padding(18.dp).fillMaxWidth(0.68f)) {
            Text("FEELING OVERWHELMED?", style = FitType.overline, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            Text("Calm me down now", style = FitType.title, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("A 3-minute guided reset: breathing that slows your heart, grounding, then a quick check of how you feel.", style = FitType.caption, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(12.dp))
            Text("Start", style = FitType.label, color = CALM_TOP, modifier = Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun ToolTile(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard(modifier, onClick = onClick, padding = 14.dp) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        Spacer(Modifier.height(10.dp))
        Text(title, style = FitType.section, color = th.text)
        Caption(sub)
    }
}

@Composable
private fun ActionCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, badge: String?, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    GlassCard(onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = FitType.section, color = th.text)
                Caption(sub)
            }
            if (badge != null) Text(badge, style = FitType.caption, color = color, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun CheckInCard(list: List<Assessment>, onStart: () -> Unit) {
    val gad = list.lastOrNull { it.kind == "gad7" }; val phq = list.lastOrNull { it.kind == "phq9" }
    val due = listOfNotNull(gad?.date, phq?.date).maxOrNull()?.let { it.plusDays(7) <= Clock.today() } ?: true
    GlassCard(padding = 14.dp) {
        CardHeader(Duo.EditNote, "Anxiety & mood check-in", Color(0xFF8C7CFF))
        Spacer(Modifier.height(8.dp))
        Caption("Two short questionnaires used by doctors worldwide (GAD-7 for anxiety, PHQ-9 for mood). About 2 minutes, once a week. Results stay on this phone.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreBox("Anxiety", gad, 21, Modifier.weight(1f))
            ScoreBox("Mood", phq, 27, Modifier.weight(1f))
        }
        val hist = list.takeLast(16)
        if (hist.size >= 2) { Spacer(Modifier.height(10.dp)); ScoreChart(hist) }
        Spacer(Modifier.height(10.dp))
        AccentButton(if (due) "Take this week's check-in" else "Check in again", onStart, Modifier.fillMaxWidth(), height = 44.dp)
    }
}

@Composable
private fun ScoreBox(label: String, a: Assessment?, max: Int, modifier: Modifier) {
    val th = LocalFitTheme.current
    val col = when { a == null -> th.textDim; a.score <= 4 -> th.success; a.score <= 9 -> Color(0xFF8BD450); a.score <= 14 -> th.warning; else -> th.danger }
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(col.copy(alpha = 0.12f)).padding(10.dp)) {
        Caption(label.uppercase())
        Row(verticalAlignment = Alignment.Bottom) {
            Text(a?.score?.toString() ?: "—", style = FitType.metric, color = th.text)
            Text(" / $max", style = FitType.label, color = th.textDim, modifier = Modifier.padding(bottom = 4.dp))
        }
        Text(a?.let { band(it.kind, it.score) } ?: "Not yet", style = FitType.label, color = col)
        if (a != null) Caption("${a.date.dayOfMonth} ${a.date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}")
    }
}

@Composable
private fun ScoreChart(list: List<Assessment>) {
    val th = LocalFitTheme.current
    Canvas(Modifier.fillMaxWidth().height(70.dp)) {
        listOf("gad7" to Color(0xFF4FA9FF), "phq9" to Color(0xFF8C7CFF)).forEach { (k, c) ->
            val pts = list.filter { it.kind == k }; val max = if (k == "phq9") 27f else 21f
            if (pts.size < 2) return@forEach
            val step = size.width / (pts.size - 1)
            for (i in 1 until pts.size) {
                val a = Offset((i - 1) * step, size.height * (1 - pts[i - 1].score / max)); val b = Offset(i * step, size.height * (1 - pts[i].score / max))
                drawLine(c, a, b, 3.dp.toPx(), StrokeCap.Round)
            }
            pts.forEachIndexed { i, p -> drawCircle(c, 3.5.dp.toPx(), Offset(i * step, size.height * (1 - p.score / max))) }
        }
        drawLine(th.textFaint.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
    }
    Row { Caption("● Anxiety", color = Color(0xFF4FA9FF)); Spacer(Modifier.width(10.dp)); Caption("● Mood", color = Color(0xFF8C7CFF)); Spacer(Modifier.weight(1f)); Caption("Lower is better") }
}

@Composable
private fun ProgressCard(ver: Int) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val sos = remember(ver) { CalmStore.sos(ctx) }
    val active = remember(ver) { CalmStore.activeDays(ctx) }
    val thoughts = remember(ver) { CalmStore.thoughts(ctx).size }
    if (sos.isEmpty() && active == 0 && thoughts == 0) return
    GlassCard(padding = 14.dp) {
        CardHeader(Duo.Insights, "Your recovery", th.success)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            val drop = if (sos.isEmpty()) null else sos.map { it.before - it.after }.average()
            MiniStat(drop?.let { "%.1f".format(it) } ?: "—", "avg calmer\nafter SOS (0–10)", Modifier.weight(1f))
            MiniStat("$active", "active days\nlast 30", Modifier.weight(1f))
            MiniStat("$thoughts", "thoughts\nreframed", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(v: String, l: String, modifier: Modifier) {
    val th = LocalFitTheme.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = FitType.title, color = th.text)
        Text(l, style = FitType.caption, color = th.textDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SupportCard(urgent: Boolean) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    GlassCard(padding = 14.dp) {
        CardHeader(Duo.HealthAndSafety, if (urgent) "You don't have to handle this alone" else "Talk to someone", if (urgent) th.danger else th.water)
        Spacer(Modifier.height(6.dp))
        Caption(if (urgent) "You recently told us you've had thoughts of being better off dead or of hurting yourself. Please reach out today — to someone you trust, a doctor, or one of these free lines. If you might act on these thoughts, call 1122 or go to the nearest emergency department now."
                else "Talking helps. Reach out to a friend or family member you trust, or one of these lines.")
        Spacer(Modifier.height(8.dp))
        HELPLINES.forEach { (name, num, note) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickableNoRipple {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num.filter { it.isDigit() })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(name, style = FitType.label, color = th.text); Caption(note) }
                Text(num, style = FitType.section, color = th.accentBright)
            }
        }
        Caption("Numbers checked Oct 2026; services can change.", color = th.textFaint)
    }
}

// ------------------------------------------------------------------ SOS flow

@Composable
private fun SosFlow(onDone: () -> Unit, onBreath: () -> Unit, onPlan: () -> Unit) {
    val ctx = LocalContext.current
    val tick = rememberTick()
    var step by remember { mutableIntStateOf(0) }
    var before by remember { mutableFloatStateOf(6f) }
    var after by remember { mutableFloatStateOf(4f) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CALM_TOP, CALM_BOTTOM))).clickableNoRipple { }) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i <= step) 0.9f else 0.25f))) }
                }
                Spacer(Modifier.width(12.dp))
                Text("Close", style = FitType.label, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.clickableNoRipple(onDone).padding(6.dp))
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(step, transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(250))) }, label = "sos", modifier = Modifier.weight(1f)) { s ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    when (s) {
                        0 -> RateStep("How anxious do you feel right now?", before) { before = it }
                        1 -> SighOrb { tick() }
                        2 -> GroundingSteps(dark = true) { tick(); step = 3 }
                        3 -> BodyStep()
                        else -> if (step == 4) RateStep("And now?", after) { after = it }
                    }
                }
            }
            when (step) {
                0 -> AccentButton("Begin", { step = 1 }, Modifier.fillMaxWidth(), height = 50.dp)
                1 -> AccentButton("Next: grounding", { step = 2 }, Modifier.fillMaxWidth(), height = 50.dp)
                2 -> GlassButton("Skip grounding", { step = 3 }, Modifier.fillMaxWidth(), height = 46.dp)
                3 -> AccentButton("Next", { step = 4 }, Modifier.fillMaxWidth(), height = 50.dp)
                else -> {
                    val diff = (before - after).toInt()
                    Text(when {
                        diff >= 2 -> "From ${before.toInt()} to ${after.toInt()} — your body is settling. Well done."
                        diff >= 0 -> "That's okay. Anxiety often eases in waves — another round of breathing can help."
                        else -> "Thanks for being honest. Let's keep going gently — or reach out to someone."
                    }, style = FitType.body, color = Color.White, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    AccentButton("Done", { CalmStore.addSos(ctx, before.toInt(), after.toInt()); onDone() }, Modifier.fillMaxWidth(), height = 48.dp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Breathe 3 more min", { CalmStore.addSos(ctx, before.toInt(), after.toInt()); onBreath() }, Modifier.weight(1f), height = 44.dp)
                        GlassButton("Plan something small", { CalmStore.addSos(ctx, before.toInt(), after.toInt()); onPlan() }, Modifier.weight(1f), height = 44.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RateStep(q: String, v: Float, set: (Float) -> Unit) {
    Text(q, style = FitType.title, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    Text("${v.toInt()}", style = FitType.hero.copy(fontSize = 72.sp, lineHeight = 74.sp), color = Color.White)
    Text(when (v.toInt()) { 0, 1, 2 -> "Calm"; 3, 4 -> "A little uneasy"; 5, 6 -> "Anxious"; 7, 8 -> "Very anxious"; else -> "Overwhelmed" }, style = FitType.section, color = Color.White.copy(alpha = 0.85f))
    Spacer(Modifier.height(16.dp))
    Slider(v, { set(it) }, valueRange = 0f..10f, steps = 9, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = TEAL, inactiveTrackColor = Color.White.copy(alpha = 0.25f)))
    Row(Modifier.fillMaxWidth()) { Text("0 calm", style = FitType.caption, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f)); Text("10 overwhelmed", style = FitType.caption, color = Color.White.copy(alpha = 0.7f)) }
}

/** Cyclic sighing: two inhales (long + short top-up) through the nose, then a long, slow exhale. */
@Composable
private fun SighOrb(onPhase: () -> Unit) {
    val scale = remember { Animatable(0.45f) }
    var label by remember { mutableStateOf("Get comfortable…") }
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(800)
        while (isActive) {
            label = "Breathe in through your nose"; onPhase(); scale.animateTo(0.85f, tween(2000, easing = FastOutSlowInEasing))
            label = "…and a little more"; onPhase(); scale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            label = "Long, slow breath out through your mouth"; onPhase(); scale.animateTo(0.45f, tween(6000, easing = LinearEasing))
            count++
        }
    }
    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 * scale.value
            for (k in 3 downTo 1) drawCircle(TEAL.copy(alpha = 0.09f * k), r * (1f + 0.12f * k))
            drawCircle(Brush.radialGradient(listOf(Color.White, TEAL.copy(alpha = 0.9f)), center, r), r)
            drawCircle(Color.White.copy(alpha = 0.6f), size.minDimension / 2, style = Stroke(1.5.dp.toPx()))
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(label, style = FitType.title, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(if (count < 6) "Breath ${count + 1} of 6 — tap Next whenever you're ready" else "Great. Keep going, or tap Next.", style = FitType.caption, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center)
}

private val SENSES = listOf(5 to "things you can SEE", 4 to "things you can TOUCH", 3 to "things you can HEAR", 2 to "things you can SMELL", 1 to "thing you can TASTE")

/** 5-4-3-2-1 grounding: tap a dot for each thing you notice. */
@Composable
private fun GroundingSteps(dark: Boolean, onFinish: () -> Unit) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    var sense by remember { mutableIntStateOf(0) }
    var got by remember { mutableIntStateOf(0) }
    val (n, what) = SENSES[sense]
    val fg = if (dark) Color.White else th.text
    Text("Notice $n $what", style = FitType.title, color = fg, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text("Look around slowly. Tap a circle for each one.", style = FitType.caption, color = fg.copy(alpha = 0.75f), textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(n) { i ->
            val on = i < got
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (on) TEAL else fg.copy(alpha = 0.12f)).border(2.dp, TEAL.copy(alpha = 0.8f), CircleShape)
                .clickableNoRipple {
                    if (!on) { tick(); got++ }
                    if (got >= n) { if (sense < SENSES.lastIndex) { sense++; got = 0 } else onFinish() }
                }, contentAlignment = Alignment.Center) { if (on) Text("✓", color = Color.White, fontSize = 20.sp) }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("${sense + 1} of 5 senses", style = FitType.caption, color = fg.copy(alpha = 0.7f))
}

@Composable
private fun BodyStep() {
    val lines = listOf("Drop your shoulders away from your ears.", "Unclench your jaw. Let your tongue rest.", "Place a hand on your chest and feel it rise and fall.", "Feel your feet on the floor. You are here, and you are safe right now.")
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (i < lines.lastIndex) { delay(4500); i++ } }
    Text("Let your body soften", style = FitType.title, color = Color.White)
    Spacer(Modifier.height(18.dp))
    lines.forEachIndexed { k, l ->
        Text(l, style = FitType.section, color = Color.White.copy(alpha = if (k <= i) 1f else 0.25f), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
    }
}

// ------------------------------------------------------------------ tools (sheets)

@Composable
private fun SheetTitle(t: String, sub: String) {
    val th = LocalFitTheme.current
    Text(t, style = FitType.title, color = th.text)
    Caption(sub)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun GroundingTool(onDone: () -> Unit) {
    var done by remember { mutableStateOf(false) }
    SheetTitle("5-4-3-2-1 grounding", "Brings your attention out of anxious thoughts and back to the room around you.")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!done) GroundingSteps(dark = false) { done = true }
        else { Text("Nicely done. Take one slow breath out.", style = FitType.section, color = LocalFitTheme.current.text); Spacer(Modifier.height(12.dp)); AccentButton("Close", onDone, Modifier.fillMaxWidth(), height = 46.dp) }
    }
    Spacer(Modifier.height(12.dp))
}

private val PMR = listOf(
    "Hands" to "Make tight fists", "Arms" to "Bend your elbows and tense your upper arms", "Shoulders" to "Lift your shoulders up to your ears",
    "Face" to "Scrunch your eyes, nose and forehead", "Chest & back" to "Take a breath in and gently squeeze your shoulder blades",
    "Stomach" to "Tighten your stomach muscles", "Legs & feet" to "Press your heels down and point your toes up",
)

/** Progressive muscle relaxation: tense 5 s, release 10 s, for each area. */
@Composable
private fun PmrTool(onDone: () -> Unit) {
    val th = LocalFitTheme.current
    val tick = rememberTick()
    var running by remember { mutableStateOf(false) }
    var idx by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf("Tense") }
    var left by remember { mutableIntStateOf(5) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (idx < PMR.size) {
            phase = "Tense"; tick(); for (s in 5 downTo 1) { left = s; delay(1000) }
            phase = "Release"; tick(); for (s in 10 downTo 1) { left = s; delay(1000) }
            idx++
        }
        running = false
    }
    SheetTitle("Muscle relaxation", "Tense each area for 5 seconds, then let go for 10. Notice the difference — that's what relaxed feels like.")
    if (idx >= PMR.size) {
        Text("All done. Sit for a moment and enjoy the heaviness.", style = FitType.section, color = th.text)
        Spacer(Modifier.height(12.dp)); AccentButton("Close", onDone, Modifier.fillMaxWidth(), height = 46.dp)
    } else {
        val (area, how) = PMR[idx]
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${idx + 1} / ${PMR.size} · $area", style = FitType.overline, color = th.textDim)
            Spacer(Modifier.height(8.dp))
            Text(if (!running) how else if (phase == "Tense") how else "Let go… feel it relax", style = FitType.title, color = th.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            if (running) Text("$phase · $left", style = FitType.metric, color = if (phase == "Tense") th.warning else th.success)
            Spacer(Modifier.height(12.dp))
            if (!running) AccentButton(if (idx == 0) "Start (4 min)" else "Continue", { running = true }, Modifier.fillMaxWidth(), height = 46.dp)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Field(v: String, set: (String) -> Unit, hint: String, lines: Int = 2) {
    val th = LocalFitTheme.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(th.textFaint.copy(alpha = 0.12f)).padding(12.dp)) {
        if (v.isEmpty()) Text(hint, style = FitType.body, color = th.textFaint)
        BasicTextField(v, set, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), minLines = lines, modifier = Modifier.fillMaxWidth())
    }
}

private val TRAPS = listOf("All-or-nothing", "Catastrophising", "Mind reading", "Fortune telling", "\"Should\" statements", "Labelling", "Ignoring the positives", "Blaming myself")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ThoughtTool(onSaved: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var situation by remember { mutableStateOf("") }
    var thought by remember { mutableStateOf("") }
    var before by remember { mutableFloatStateOf(70f) }
    val traps = remember { mutableStateListOf<String>() }
    var against by remember { mutableStateOf("") }
    var balanced by remember { mutableStateOf("") }
    var after by remember { mutableFloatStateOf(40f) }
    SheetTitle("Reframe a thought", "Thoughts aren't facts. This CBT exercise helps you look at one more fairly.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(situation, { situation = it }, "What happened? (where, when, who)")
        Field(thought, { thought = it }, "What went through your mind? e.g. \"I'll mess this up and everyone will think I'm useless\"")
        Text("How much do you believe it? ${before.toInt()}%", style = FitType.label, color = th.text)
        Slider(before, { before = it }, valueRange = 0f..100f)
        Text("Any thinking traps?", style = FitType.label, color = th.text)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TRAPS.forEach { t -> GlassChip(t, t in traps, { if (t in traps) traps.remove(t) else traps.add(t) }) }
        }
        Field(against, { against = it }, "Evidence that doesn't fit the thought. What would you tell a friend thinking this?")
        Field(balanced, { balanced = it }, "A fairer, more balanced thought")
        Text("How much do you believe the original thought now? ${after.toInt()}%", style = FitType.label, color = th.text)
        Slider(after, { after = it }, valueRange = 0f..100f)
        AccentButton("Save", {
            if (thought.isBlank()) { toaster.show("Write the thought first"); return@AccentButton }
            CalmStore.addThought(ctx, ThoughtRecord(Clock.today(), situation.trim(), thought.trim() + if (traps.isEmpty()) "" else " [${traps.joinToString()}]", before.toInt(), (balanced.trim() + if (against.isBlank()) "" else " — " + against.trim()), after.toInt()))
            toaster.show(if (after < before) "Belief down ${(before - after).toInt()}% — nice work" else "Saved"); onSaved()
        }, Modifier.fillMaxWidth(), height = 46.dp)
        val past = remember { CalmStore.thoughts(ctx).takeLast(3).reversed() }
        if (past.isNotEmpty()) {
            Text("RECENT", style = FitType.overline, color = th.textDim)
            past.forEach { r -> Caption("“${r.thought.take(70)}” ${r.before}% → ${r.after}%") }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WorryTool() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val list = remember { mutableStateListOf<String>().apply { addAll(CalmStore.worries(ctx)) } }
    var text by remember { mutableStateOf("") }
    var time by remember { mutableStateOf(CalmStore.worryTime(ctx)) }
    val now = java.time.LocalTime.now()
    val wt = runCatching { java.time.LocalTime.parse(time) }.getOrDefault(java.time.LocalTime.of(18, 0))
    val isTime = now >= wt && now < wt.plusMinutes(30)
    SheetTitle("Worry time", "When a worry pops up, write it here and tell yourself \"not now — at worry time\". Then come back at your set time for 15 minutes.")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("12:00", "17:00", "18:00", "20:00").forEach { t -> GlassChip(t, time == t, { time = t; CalmStore.setWorryTime(ctx, t) }) }
    }
    Spacer(Modifier.height(10.dp))
    Field(text, { text = it }, "What's worrying you?", 1)
    Spacer(Modifier.height(8.dp))
    GlassButton("Park it for later", { if (text.isNotBlank()) { list.add(text.trim()); CalmStore.setWorries(ctx, list.toList()); text = "" } }, Modifier.fillMaxWidth(), height = 44.dp)
    Spacer(Modifier.height(12.dp))
    if (isTime && list.isNotEmpty()) GlassCard(padding = 12.dp) {
        Text("It's worry time ⏰", style = FitType.section, color = th.text)
        Caption("For each one ask: is this a problem I can act on? If yes, write one small next step. If not, practise letting it go — then tick it off.")
    }
    Text(if (list.isEmpty()) "Nothing parked. 🌿" else "PARKED WORRIES · ${list.size}", style = FitType.overline, color = th.textDim)
    list.forEachIndexed { i, w ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("• $w", style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
            Text("Done", style = FitType.label, color = th.success, modifier = Modifier.clickableNoRipple { list.removeAt(i); CalmStore.setWorries(ctx, list.toList()) }.padding(6.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActivityPlanner() {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val muslim = LocalSettings.current.muslim == "yes"
    val ideas = remember(muslim) {
        listOf("10-minute walk outside", "Sit in the sunlight for 10 minutes", "Message or call a friend", "Shower and put on fresh clothes", "Tidy one small area",
            "Cook something simple", "Listen to a favourite song", "Stretch for 5 minutes", "Read 10 pages", "Help someone with something small", "Drink a glass of water") +
            (if (muslim) listOf("Pray on time / make dhikr for 5 minutes") else emptyList())
    }
    val plan = remember { mutableStateListOf<Pair<String, Boolean>>().apply { addAll(CalmStore.plan(ctx)) } }
    var custom by remember { mutableStateOf("") }
    fun save() = CalmStore.setPlan(ctx, plan.toList())
    SheetTitle("Plan small good things", "Low mood says \"wait until you feel like it\". Doing a small thing first is what brings the feeling back (behavioural activation).")
    Text("TODAY", style = FitType.overline, color = th.textDim)
    if (plan.isEmpty()) Caption("Pick 1–3 below. Small is perfect.")
    plan.forEachIndexed { i, (t, done) ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickableNoRipple { tick(); plan[i] = t to !done; save() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (done) th.success else th.textFaint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { if (done) Text("✓", color = Color.White) }
            Spacer(Modifier.width(10.dp))
            Text(t, style = FitType.body, color = if (done) th.textDim else th.text, modifier = Modifier.weight(1f))
            Text("✕", color = th.textFaint, modifier = Modifier.clickableNoRipple { plan.removeAt(i); save() }.padding(6.dp))
        }
    }
    if (plan.isNotEmpty() && plan.all { it.second }) Caption("All done today — that's real progress. 🌱", color = th.success)
    Spacer(Modifier.height(10.dp))
    Text("IDEAS", style = FitType.overline, color = th.textDim)
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ideas.filter { idea -> plan.none { it.first == idea } }.forEach { idea -> GlassChip(idea, false, { plan.add(idea to false); save() }) }
    }
    Spacer(Modifier.height(8.dp))
    Field(custom, { custom = it }, "Your own idea", 1)
    Spacer(Modifier.height(6.dp))
    GlassButton("Add", { if (custom.isNotBlank()) { plan.add(custom.trim() to false); save(); custom = "" } }, Modifier.fillMaxWidth(), height = 42.dp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun GratitudeTool(onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val init = remember { CalmStore.gratitude(ctx) }
    val items = remember { mutableStateListOf(init.getOrElse(0) { "" }, init.getOrElse(1) { "" }, init.getOrElse(2) { "" }) }
    SheetTitle("3 good things", "Writing down what went well — and why — trains your attention to notice good moments.")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0..2).forEach { i -> Field(items[i], { items[i] = it }, "${i + 1}. Something that went well today", 1) }
        AccentButton("Save", { CalmStore.setGratitude(ctx, items.toList()); onSaved() }, Modifier.fillMaxWidth(), height = 46.dp)
    }
    Spacer(Modifier.height(12.dp))
}

/** GAD-7 then PHQ-9, one question at a time; PHQ-9 item 9 > 0 shows support immediately. */
@Composable
private fun CheckInFlow(onDone: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val tick = rememberTick()
    val qs = remember { GAD7.map { "gad7" to it } + PHQ9.map { "phq9" to it } }
    val ans = remember { mutableStateListOf<Int>() }
    var saved by remember { mutableStateOf(false) }
    if (ans.size < qs.size) {
        val (kind, q) = qs[ans.size]
        SheetTitle(if (kind == "gad7") "Anxiety · ${ans.size + 1} of 7" else "Mood · ${ans.size - 6} of 9", "Over the last 2 weeks, how often have you been bothered by…")
        Text(q, style = FitType.title, color = th.text)
        Spacer(Modifier.height(14.dp))
        ANSWERS.forEachIndexed { i, a ->
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(th.textFaint.copy(alpha = 0.12f)).clickableNoRipple { tick(); ans.add(i) }.padding(14.dp)) {
                Text(a, style = FitType.section, color = th.text)
            }
        }
        if (ans.isNotEmpty()) Text("Back", style = FitType.label, color = th.accentBright, modifier = Modifier.clickableNoRipple { ans.removeAt(ans.lastIndex) }.padding(8.dp))
        Spacer(Modifier.height(10.dp))
    } else {
        val gad = ans.take(7).sum(); val phq = ans.drop(7).sum(); val i9 = ans.last()
        LaunchedEffect(Unit) {
            if (!saved) { CalmStore.addAssessment(ctx, Assessment("gad7", Clock.today(), gad)); CalmStore.addAssessment(ctx, Assessment("phq9", Clock.today(), phq, i9)); saved = true }
        }
        SheetTitle("Your check-in", "This is a screening, not a diagnosis.")
        if (i9 > 0) { SupportCard(urgent = true); Spacer(Modifier.height(10.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreBox("Anxiety", Assessment("gad7", Clock.today(), gad), 21, Modifier.weight(1f))
            ScoreBox("Mood", Assessment("phq9", Clock.today(), phq, i9), 27, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        val worst = maxOf(gad, phq)
        Caption(when {
            worst >= 15 -> "Your answers suggest you're having a really hard time. Please talk to a doctor, psychologist or counsellor soon — treatment works, and you don't have to push through alone."
            worst >= 10 -> "Your answers are in the range where talking to a doctor or counsellor is recommended, especially if this has lasted 2 weeks or more. Keep using the calming and mood tools daily."
            worst >= 5 -> "Mild symptoms. Daily calming practice, movement, sleep and small planned activities can help a lot. Check in again next week."
            else -> "Minimal symptoms — great. Keep the habits that help you feel good."
        }, color = th.text)
        Spacer(Modifier.height(12.dp))
        AccentButton("Done", onDone, Modifier.fillMaxWidth(), height = 46.dp)
        Spacer(Modifier.height(12.dp))
    }
}
