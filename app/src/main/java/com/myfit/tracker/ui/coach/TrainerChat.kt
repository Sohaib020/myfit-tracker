package com.myfit.tracker.ui.coach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.PipVoice
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Coach
import com.myfit.tracker.domain.HealthProfile
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** R36: the trainer's own chat (the card's "Chat" button), same fast chat as the nutritionist's. */
@Composable
fun TrainerChatScreen(container: AppContainer) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    remember { Coach.load(ctx); HealthProfile.load(ctx); 0 }
    val cp by Coach.prefs.collectAsState()
    val context by produceState("") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val p = container.profileRepo.profile.first()
                val today = Clock.today()
                val monday = today.with(java.time.DayOfWeek.MONDAY)
                val week = container.workoutRepo.completedRange(Clock.dateKey(monday), Clock.dateKey(today)).first().size
                val days = p?.workoutDaysMask?.let { m -> (0..6).count { m and (1 shl it) != 0 } }
                buildString {
                    if (p != null) append("The user is ${p.age}, ${p.sex.lowercase()}, ${p.heightCm.toInt()} cm, experience ${p.experience.lowercase()}, goals: ${p.goals.lowercase().ifBlank { "general fitness" }}. ")
                    if (days != null && days > 0) append("They plan to train $days days a week. ")
                    append("Workouts done this week: $week. ")
                }
            }.getOrDefault("")
        }
    }
    val h = HealthProfile.summary()
    val system = "You are ${cp.name}, a friendly, motivating personal trainer (a fictional character in the MyFit app). " +
        "Give safe, specific training advice: exercises, sets, reps, rest, form cues, warm-ups and simple progressions. " +
        "Be brief (under 120 words unless asked for a full plan), use simple English or Roman Urdu if the user writes that way. " +
        "Never diagnose injuries or prescribe medication; for pain, chest symptoms or medical conditions tell them to stop and see a doctor. " +
        (if (h.isNotBlank()) "The user's health profile: $h — adapt exercises to it. " else "") + context
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(cp.name, { nav.pop() }, "Your trainer · chat")
        ChatTab(
            container, cp.name, system, PipVoice.Persona(cp.look.male, trainer = true), cp.look.id, "t",
            intro = "Ask me about training — a quick home workout, how to fix your squat, how many sets to build muscle, what to do on a rest day…",
            starters = listOf("Give me a 20-minute home workout", "How do I start building muscle?", "My knees hurt when I squat — what should I change?"),
            reportWhere = "trainer",
        )
    }
}
