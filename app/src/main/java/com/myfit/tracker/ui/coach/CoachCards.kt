package com.myfit.tracker.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType

/** Who the card is for. */
enum class CoachRole { TRAINER, NUTRITIONIST }

private val TRAINER_TIPS = listOf(
    "Two quality sets beat five sloppy ones. Want me to count your reps today?",
    "Warm up for five minutes — your joints will thank you.",
    "Slow the lowering to three seconds. Same weight, more muscle.",
    "Pick one lift today and own it. I'll watch your form.",
    "Rest 90 seconds between hard sets — I'll time it for you.",
    "Can't make the gym? A 20-minute bodyweight session still counts.",
    "Stand tall, brace, breathe. Ready when you are.",
)
private val NUTRI_TIPS = listOf(
    "Protein at every meal keeps you full. Want a quick idea for today?",
    "A glass of water before each meal — easy win.",
    "Half the plate vegetables, a quarter protein, a quarter carbs.",
    "Snack ready? Yogurt, fruit or a handful of nuts beat biscuits.",
    "Log one meal now and I'll suggest the rest of your day.",
    "Dinner light, breakfast strong — your energy follows.",
    "Fibre helps everything: daal, beans, fruit, whole grains.",
)

/**
 * R33 full-bleed character card: the coach stands large in a soft spotlight (cropped at the waist), name and role on
 * the left, a speech bubble with today's tip, and two quick actions.
 */
@Composable
fun CoachCard(role: CoachRole, onOpen: () -> Unit, modifier: Modifier = Modifier, height: Dp = 210.dp,
              primary: Pair<String, () -> Unit>? = null, secondary: Pair<String, () -> Unit>? = null) {
    val ctx = LocalContext.current
    remember { Coach.load(ctx); 0 }
    val cp by Coach.prefs.collectAsState()
    val trainer = role == CoachRole.TRAINER
    val look = if (trainer) cp.look.id else cp.nLook.id
    val name = if (trainer) cp.name else cp.nName
    val accent = if (trainer) Color(0xFFFFB13B) else Color(0xFF34D399)
    val bg = if (trainer) listOf(Color(0xFF2A1A0C), Color(0xFF15121C), Color(0xFF101423)) else listOf(Color(0xFF0B2A22), Color(0xFF101A1C), Color(0xFF101423))
    val tip = remember(role) { val d = java.time.LocalDate.now().dayOfYear; (if (trainer) TRAINER_TIPS else NUTRI_TIPS)[d % 7] }
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(28.dp))
        .background(Brush.linearGradient(bg))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(28.dp))
        .clickableNoRipple(onOpen)) {
        // spotlight + floor glow behind the character
        Box(Modifier.fillMaxSize().drawBehind {
            val c = Offset(size.width * 0.78f, size.height * 0.42f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.42f), accent.copy(alpha = 0.08f), Color.Transparent), c, size.height * 0.85f), size.height * 0.85f, c)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), size.height * 0.6f, size.height))
        })
        // the character, taller than the card so it's cropped at the waist
        Box(Modifier.align(Alignment.TopEnd).width(height * 0.95f).fillMaxSize().clipToBounds()) {
            CoachFigure(look, "idle", Modifier.align(Alignment.TopCenter).offset(y = height * 0.06f).requiredHeight(height * 1.9f).fillMaxWidth(),
                spotlight = false, tapToEmote = false)
        }
        Column(Modifier.align(Alignment.TopStart).padding(16.dp).fillMaxWidth(0.62f)) {
            Text(if (trainer) "YOUR TRAINER" else "YOUR NUTRITIONIST", style = FitType.overline, color = accent)
            Text(name, style = FitType.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            // speech bubble
            Box(Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(Color.White.copy(alpha = 0.13f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(tip, style = FitType.caption.copy(lineHeight = FitType.caption.lineHeight * 1.15f), color = Color.White.copy(alpha = 0.92f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val p = primary ?: ((if (trainer) "Chat" else "Ask") to onOpen)
            val s = secondary ?: ("Today's plan" to onOpen)
            ActionChip(p.first, if (trainer) Duo.ChatBubble else Duo.ChatBubble, accent, filled = true, onClick = p.second)
            ActionChip(s.first, Duo.CalendarMonth, accent, filled = false, onClick = s.second)
        }
    }
}

@Composable
private fun ActionChip(text: String, icon: ImageVector, accent: Color, filled: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(20.dp))
        .background(if (filled) accent else Color.White.copy(alpha = 0.14f))
        .clickableNoRipple(onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (filled) Color(0xFF15121C) else Color.White, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = FitType.label, color = if (filled) Color(0xFF15121C) else Color.White, maxLines = 1)
    }
}
