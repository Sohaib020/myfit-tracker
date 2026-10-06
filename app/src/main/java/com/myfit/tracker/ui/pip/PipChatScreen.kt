package com.myfit.tracker.ui.pip

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import com.myfit.tracker.ui.theme.Duo

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.ChatMessage
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val suggestions = listOf(
    "How am I doing today?", "Compare my last 30 days", "How much did my bench improve?",
    "How many times did I train legs this month?", "Average sleep last week", "Steps this week",
    "Did I hit my water target this month?", "Any PRs this month?",
    "Plan today's workout", "High-protein Pakistani breakfast ideas", "How can I sleep better?",
)

@Composable
fun PipChatScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val settings = LocalSettings.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val messages by container.healthRepo.chat.collectAsState(initial = emptyList())
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    var mood by remember { mutableStateOf(PipMood.WAVE) }
    var typingId by remember { mutableLongStateOf(-1L) }
    var showSettings by remember { mutableStateOf(PipChatLaunch.openSettings.also { PipChatLaunch.openSettings = false }) }
    var showBrain by remember { mutableStateOf(false) }
    var sharedFor by remember { mutableStateOf<String?>(null) }
    var showShared by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val online = settings.geminiKeyEff.isNotBlank() && settings.onlineAi

    val speaking by container.pipVoice.speaking.collectAsState()
    val voiceLevel by container.pipVoice.level.collectAsState()
    var typing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(2200); if (mood == PipMood.WAVE) mood = PipMood.HAPPY }
    androidx.compose.runtime.DisposableEffect(Unit) {
        container.pipVoice.prepare()
        onDispose { container.pipVoice.release() }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    fun send(text: String, retry: Boolean = false) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        tick(); if (!retry) input = ""; thinking = true; mood = PipMood.THINKING
        scope.launch {
            val r = container.pipBrain.ask(q, addUserMessage = !retry)
            if (r.shared != null) sharedFor = r.shared
            thinking = false
            // Pip reacts to what it's saying while its lips move
            mood = if (r.source == "error") PipMood.CONCERNED else r.mood
            typing = true
            typingId = container.healthRepo.lastChat(1).firstOrNull()?.id ?: -1L
            if (r.source != "error") container.pipVoice.speak(r.text, r.speakUr, r.speakHi)
            delay((r.text.length * 6L).coerceIn(700, 4000))
            typing = false
        }
    }

    val toaster = com.myfit.tracker.ui.components.LocalToaster.current
    val mic = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { res ->
        val heard = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!heard.isNullOrBlank()) send(heard)
    }
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    LaunchedEffect(imeOpen) { if (imeOpen && messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().imePadding()) {
        val ctxB = androidx.compose.ui.platform.LocalContext.current
        val brain by com.myfit.tracker.ai.BrainMode.flow.collectAsState()
        val brainNow = brain ?: com.myfit.tracker.ai.BrainMode.get(ctxB)
        OverlayTopBar(Buddy.active.collectAsState().value.label.substringBefore(' '), { nav.pop() },
            when { speaking -> "Speaking…"; thinking -> "Thinking…"; else -> "Your AI buddy" }) {
            // brain chip: Auto / Online / On this phone
            Glass(Modifier.height(40.dp), shape = RoundedCornerShape(20.dp), onClick = { showBrain = true }) {
                Row(Modifier.padding(horizontal = 12.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (brainNow) { com.myfit.tracker.ai.BrainMode.ONLINE -> Duo.Cloud; com.myfit.tracker.ai.BrainMode.PHONE -> Duo.Lock; else -> Duo.AutoAwesome }, null,
                        tint = th.accentBright, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(com.myfit.tracker.ai.BrainMode.label(brainNow), style = FitType.label, color = th.text, maxLines = 1)
                    Icon(Duo.KeyboardArrowDown, null, tint = th.textDim, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.size(10.dp))
            GlassIconButton(Duo.Gear, { showSettings = true })
        }
        // ---- welcome: big Pip. Once chatting, Pip floats in the corner so the chat gets the whole screen.
        if (messages.isEmpty()) Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
            Pip(mood, size = 230.dp, talking = speaking || typing, idleActions = !thinking, level = if (speaking) voiceLevel else -1f)
        }
        if (messages.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hi! I'm ${com.myfit.tracker.ui.pip.Buddy.name} " + (if (com.myfit.tracker.ui.pip.Buddy.active.value == com.myfit.tracker.ui.arena.Mascot.PIP) "🌱" else "👋"), style = FitType.title, color = th.text)
                Spacer(Modifier.height(6.dp))
                Caption("Ask about your training, food, sleep or steps — or tap the mic and just talk. Tap me, stroke me, or long-press for a hug.")
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (messages.isEmpty()) 8.dp else 96.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.id }) { m ->
                Bubble(m, animate = m.id == typingId, onShowShared = if (m.source == "online" && sharedFor != null && m == messages.lastOrNull()) ({ showShared = true }) else null)
            }
            if (thinking) item { ThinkingBubble() }
        }
        // ---- suggestions + input
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val last = messages.lastOrNull()
            if (last?.source == "error" && !thinking) {
                val failedQ = messages.lastOrNull { it.role == "user" }?.text
                if (failedQ != null) item { GlassChip("↻ Try again", true, { send(failedQ, retry = true) }) }
            }
            items(suggestions) { sg -> GlassChip(sg, false, { send(sg) }) }
        }
        Text("AI answers can be wrong and aren't medical advice.", style = FitType.overline, color = th.textFaint,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            Glass(Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(27.dp)) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                    if (input.isEmpty()) Text("Message ${Buddy.name}…", style = FitType.body, color = th.textFaint)
                    BasicTextField(
                        input, { input = it.take(600) },
                        textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(input) }),
                        maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            val listening = input.isEmpty()
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .drawBehind { drawCircle(Brush.verticalGradient(listOf(th.accentBright, th.accent))) }
                    .clickableNoRipple {
                        if (input.isNotBlank()) send(input)
                        else runCatching {
                            container.pipVoice.stop()
                            mic.launch(android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Ask ${Buddy.name}…"))
                        }.onFailure { toaster.show("Voice typing isn't available on this phone") }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (listening) Duo.Microphone else Duo.Send, if (listening) "Talk" else "Send", tint = th.onAccent, modifier = Modifier.size(24.dp))
            }
        }
    }
    // floating Pip (bobs gently, shrinks while typing)
    if (messages.isNotEmpty()) {
        val bob = androidx.compose.animation.core.rememberInfiniteTransition(label = "float")
        val fy by bob.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2600), androidx.compose.animation.core.RepeatMode.Reverse), label = "fy")
        val pipSize by androidx.compose.animation.core.animateDpAsState(if (imeOpen) 72.dp else 104.dp, label = "pipSize")
        // drag him anywhere on the chat; the spot is remembered
        val ctxF = androidx.compose.ui.platform.LocalContext.current
        val prefsF = remember { ctxF.getSharedPreferences("pip_float", android.content.Context.MODE_PRIVATE) }
        var drag by remember { mutableStateOf(androidx.compose.ui.geometry.Offset(prefsF.getFloat("x", 0f), prefsF.getFloat("y", 0f))) }
        val conf = androidx.compose.ui.platform.LocalConfiguration.current
        val dens = androidx.compose.ui.platform.LocalDensity.current
        val maxX = with(dens) { (conf.screenWidthDp.dp - 110.dp).toPx() }
        val maxY = with(dens) { (conf.screenHeightDp.dp - 260.dp).toPx() }
        Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 58.dp, end = 6.dp)
                .graphicsLayer { translationX = drag.x; translationY = drag.y + (fy - 0.5f) * 10.dp.toPx(); rotationZ = (fy - 0.5f) * 4f }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        var dragging = false
                        var total = androidx.compose.ui.geometry.Offset.Zero
                        while (true) {
                            val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val d = ch.position - ch.previousPosition
                            total += d
                            if (!dragging && total.getDistance() > viewConfiguration.touchSlop) dragging = true
                            if (dragging) {
                                drag = androidx.compose.ui.geometry.Offset((drag.x + d.x).coerceIn(-maxX, 0f), (drag.y + d.y).coerceIn(-40f, maxY))
                                ch.consume()
                            }
                        }
                        if (dragging) prefsF.edit().putFloat("x", drag.x).putFloat("y", drag.y).apply()
                    }
                }
        ) {
            Pip(mood, size = pipSize, talking = speaking || typing, idleActions = !thinking, level = if (speaking) voiceLevel else -1f)
        }
    }
    }

    if (showShared) AlertDialog(
        onDismissRequest = { showShared = false },
        title = { Text("What Pip shared online") },
        text = { Text(sharedFor ?: "Nothing.") },
        confirmButton = { TextButton({ showShared = false }) { Text("OK") } },
    )
    com.myfit.tracker.ui.components.GlassSheet(visible = showBrain, onDismiss = { showBrain = false }) {
        Text("Which brain answers?", style = FitType.title, color = th.text)
        Caption("Questions about your own logs are always answered from your data, on the phone.")
        Spacer(Modifier.height(12.dp))
        BrainPicker(container) { showBrain = false }
        Spacer(Modifier.height(10.dp))
    }
    com.myfit.tracker.ui.components.GlassSheet(visible = showSettings, onDismiss = { showSettings = false }) {
        PipSettingsContent(container) { container.write { container.healthRepo.clearChat() }; mood = PipMood.WAVE; showSettings = false }
    }
}


@Composable
private fun Bubble(m: ChatMessage, animate: Boolean, onShowShared: (() -> Unit)?) {
    val th = LocalFitTheme.current
    val mine = m.role == "user"
    var shown by remember(m.id) { mutableIntStateOf(if (animate) 0 else m.text.length) }
    LaunchedEffect(m.id, animate) {
        if (animate) while (shown < m.text.length) { shown = (shown + 3).coerceAtMost(m.text.length); delay(16) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (mine) {
            Box(
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                    .drawBehind { drawRect(Brush.verticalGradient(listOf(th.accentBright, th.accent))) }
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) { Text(m.text, style = FitType.body, color = th.onAccent) }
        } else {
            Glass(Modifier.widthIn(max = 320.dp), shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    Text(markdown(m.text.take(shown)), style = FitType.body, color = th.text)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (label, color, icon) = when (m.source) {
                            "data" -> Triple("From your data", th.success, Duo.Insights)
                            "online" -> Triple("Online AI", th.water, Duo.Cloud)
                            "on-device" -> Triple("On this phone", th.accentBright, Duo.Lock)
                            "error" -> Triple("Couldn't reach the online AI", th.warning, Duo.Cloud)
                            else -> Triple(Buddy.name, th.textFaint, Duo.Insights)
                        }
                        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(label, style = FitType.overline, color = color)
                        if (onShowShared != null) {
                            Spacer(Modifier.size(10.dp))
                            Text("What was shared?", style = FitType.overline, color = th.textDim, modifier = Modifier.clickableNoRipple { onShowShared() }.padding(2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val th = LocalFitTheme.current
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { androidx.compose.runtime.withFrameMillis { }; delay(260); phase = (phase + 1) % 4 } }
    Glass(Modifier.size(width = 86.dp, height = 44.dp), shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i -> Box(Modifier.size(9.dp).clip(CircleShape).drawBehind { drawCircle(if (phase > i) th.accentBright else th.textFaint) }) }
        }
    }
}

/** Renders the bits of Markdown Gemini uses: **bold**, *italic*, `code`, bullets and headings. */
internal fun markdown(src: String): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    val lines = src.split('\n')
    lines.forEachIndexed { li, raw ->
        var line = raw
        val heading = Regex("""^\s*#{1,6}\s+""").find(line)
        if (heading != null) line = line.substring(heading.range.last + 1)
        val bullet = Regex("""^(\s*)[*\-•]\s+""").find(line)
        if (bullet != null) { append(bullet.groupValues[1]); append("•  "); line = line.substring(bullet.range.last + 1) }
        if (heading != null) pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
        var i = 0
        var bold = false; var italic = false
        while (i < line.length) {
            when {
                line.startsWith("**", i) || line.startsWith("__", i) -> {
                    if (!bold) pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) else pop()
                    bold = !bold; i += 2
                }
                (line[i] == '*' || line[i] == '_') && (italic || (i + 1 < line.length && line[i + 1] != ' ' && (i == 0 || !line[i - 1].isLetterOrDigit()))) -> {
                    if (!italic) pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) else pop()
                    italic = !italic; i += 1
                }
                line[i] == '`' -> i += 1
                else -> { append(line[i]); i += 1 }
            }
        }
        if (italic) pop()
        if (bold) pop()
        if (heading != null) pop()
        if (li < lines.lastIndex) append('\n')
    }
}
