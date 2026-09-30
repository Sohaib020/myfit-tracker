package com.myfit.tracker.ui.pip

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
    var confirmClear by remember { mutableStateOf(false) }
    var sharedFor by remember { mutableStateOf<String?>(null) }
    var showShared by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val online = settings.geminiKey.isNotBlank() && settings.onlineAi

    LaunchedEffect(Unit) { delay(2200); if (mood == PipMood.WAVE) mood = PipMood.HAPPY }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || thinking) return
        tick(); input = ""; thinking = true; mood = PipMood.THINKING
        scope.launch {
            val r = container.pipBrain.ask(q)
            if (r.shared != null) sharedFor = r.shared
            thinking = false
            mood = PipMood.TALKING
            typingId = container.healthRepo.lastChat(1).firstOrNull()?.id ?: -1L
            delay((r.text.length * 18L).coerceIn(900, 4500))
            mood = r.mood
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        OverlayTopBar("Pip", { nav.pop() }, if (online) "Your data offline · general questions online" else "Answers from your data (offline)") {
            if (messages.isNotEmpty()) GlassIconButton(Icons.Rounded.DeleteSweep, { confirmClear = true })
        }
        // ---- big, live Pip
        Box(Modifier.fillMaxWidth().height(if (messages.isEmpty()) 250.dp else 170.dp).animateContentSize(), contentAlignment = Alignment.Center) {
            Pip(mood, size = if (messages.isEmpty()) 230.dp else 160.dp, talking = mood == PipMood.TALKING, onTap = { if (!thinking) mood = listOf(PipMood.HAPPY, PipMood.LOVE, PipMood.EXCITED, PipMood.CURIOUS).random() })
        }
        if (messages.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hi! I'm Pip 🌱", style = FitType.title, color = th.text)
                Spacer(Modifier.height(6.dp))
                Caption("Ask me about your training, weight, steps, sleep or water — I answer those from your own logs, offline. Stroke me, tap me, or long-press for a hug.")
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.id }) { m ->
                Bubble(m, animate = m.id == typingId, onShowShared = if (m.source == "online" && sharedFor != null && m == messages.lastOrNull()) ({ showShared = true }) else null)
            }
            if (thinking) item { ThinkingBubble() }
        }
        // ---- suggestions + input
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions) { sg -> GlassChip(sg, false, { send(sg) }) }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            Glass(Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(27.dp)) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
                    if (input.isEmpty()) Text("Ask Pip anything…", style = FitType.body, color = th.textFaint)
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
            Box(
                Modifier.size(54.dp).clip(CircleShape)
                    .drawBehind { drawCircle(Brush.verticalGradient(listOf(th.accentBright, th.accent))) }
                    .clickableNoRipple { send(input) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = th.onAccent, modifier = Modifier.size(24.dp))
            }
        }
    }

    if (showShared) AlertDialog(
        onDismissRequest = { showShared = false },
        title = { Text("What Pip shared online") },
        text = { Text(sharedFor ?: "Nothing.") },
        confirmButton = { TextButton({ showShared = false }) { Text("OK") } },
    )
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear chat history?") },
        text = { Text("Only the conversation is removed. None of your logged data is touched.") },
        confirmButton = { TextButton({ container.write { container.healthRepo.clearChat() }; confirmClear = false; mood = PipMood.WAVE }) { Text("Clear") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
    )
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
                    Text(m.text.take(shown), style = FitType.body, color = th.text)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (label, color, icon) = when (m.source) {
                            "data" -> Triple("From your data", th.success, Icons.Rounded.Insights)
                            "online" -> Triple("Online · Gemini", th.water, Icons.Rounded.Cloud)
                            else -> Triple("Pip", th.textFaint, Icons.Rounded.Insights)
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
    LaunchedEffect(Unit) { while (true) { delay(260); phase = (phase + 1) % 4 } }
    Glass(Modifier.size(width = 86.dp, height = 44.dp), shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i -> Box(Modifier.size(9.dp).clip(CircleShape).drawBehind { drawCircle(if (phase > i) th.accentBright else th.textFaint) }) }
        }
    }
}
