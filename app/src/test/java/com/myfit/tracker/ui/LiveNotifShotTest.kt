package com.myfit.tracker.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.notify.NCard
import com.myfit.tracker.notify.NKind
import com.myfit.tracker.notify.NotifKit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R20: the live cards on a light and a dark notification shade, to check they read well in both. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h1800dp-xxhdpi", application = android.app.Application::class)
class LiveNotifShotTest {
    @get:Rule val rule = createComposeRule()

    private fun cards(): List<NCard> {
        val now = System.currentTimeMillis()
        return listOf(
            NCard(NKind.LIFT, "Bench press · Set 3", "Up next · Incline dumbbell press", chrono = now - 23 * 60_000, progress = 0.4f,
                stats = listOf("12" to "Sets", "4,200 kg" to "Volume", "2/5" to "Exercises"), eyebrow = "Push day", unit = "elapsed", chip = "2/5", segments = 5),
            NCard(NKind.REST, "Next · Bench press", "Breathe and reset — 8 sets done", chrono = now + 45_000, countDown = true, progress = 0.62f,
                chip = "Rest", eyebrow = "Rest · Push day", unit = "rest left"),
            NCard(NKind.RUN, "Running in progress", "≈ 212 kcal so far · lap 2", chrono = now - 14 * 60_000, progress = 0.4f, eyebrow = "Running", unit = "elapsed",
                chip = "Live", stats = listOf("212" to "kcal (est.)", "14 min" to "Time", "2" to "Lap")),
            NCard(NKind.FLAME, "Fasting · Ketosis", "Goal 18 h · ends 2:00 PM", chrono = now - 16 * 3_600_000, progress = 0.84f, eyebrow = "Fasting",
                unit = "fasted", chip = "84%", stats = listOf("8:00 PM" to "Started", "Ketosis" to "Phase", "18 h" to "Goal")),
            NCard(NKind.GOALS, "Today so far", "6,240 steps · 1.4 L water · 1,320 kcal", value = "78%", unit = "of steps goal",
                image = NotifKit.rings(listOf(0.78f to NKind.RUN.color, 0.56f to NKind.DROP.color, 0.66f to NKind.MEAL.color), 160), eyebrow = "Daily goals",
                stats = listOf("6,240" to "of 8,000 steps", "1.4 L" to "of 2.5 L water", "1320" to "of 2000 kcal")),
        )
    }

    private fun shot(night: Boolean) {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        rule.setContent {
            Column(Modifier.width(412.dp).background(if (night) Color(0xFF111214) else Color(0xFFE9EBEF)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cards().forEach { c ->
                    AndroidView({ ctx -> NotifKit.big(ctx, c, if (c.kind == NKind.FLAME) listOf(0.66f, 0.89f) else emptyList()).apply(ctx, FrameLayout(ctx)) },
                        Modifier.clip(RoundedCornerShape(24.dp)).background(if (night) Color(0xFF26282C) else Color.White).padding(16.dp))
                }
                AndroidView({ ctx -> NotifKit.small(ctx, cards()[0]).apply(ctx, FrameLayout(ctx)) },
                    Modifier.clip(RoundedCornerShape(24.dp)).background(if (night) Color(0xFF26282C) else Color.White).padding(16.dp))
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/live_${if (night) "dark" else "light"}.png")
    }

    @Test fun light() = shot(false)
    @Test fun dark() = shot(true)
}
