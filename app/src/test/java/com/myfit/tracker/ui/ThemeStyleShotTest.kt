package com.myfit.tracker.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.notify.NCard
import com.myfit.tracker.notify.NKind
import com.myfit.tracker.notify.NotifKit
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import com.myfit.tracker.ui.theme.UiStyle
import com.myfit.tracker.ui.theme.drawBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Flat vs Glass cards on a few themes, plus the new live notification card — for design review from CI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h1600dp-xxhdpi", application = android.app.Application::class)
class ThemeStyleShotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(themeId: String, flat: Boolean) {
        UiStyle.flat = flat
        rule.setContent {
            MyFitTheme(Themes.byId(themeId), AppSettings(themeId = themeId, uiStyle = if (flat) 1 else 0)) {
                val th = LocalFitTheme.current
                Column(Modifier.width(412.dp).background(th.bgBottom).drawBehind { drawBackdrop(th, null, th.stillT, size.width, size.height) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(Themes.byId(themeId).name + if (flat) " · Flat" else " · Glass", style = FitType.title, color = th.text)
                    GlassCard { CardHeader(Duo.ForkKnife, "Food today", th.protein); Spacer(Modifier.height(6.dp)); Caption("1,240 of 2,000 kcal · 82 g protein") }
                    GlassCard { CardHeader(Duo.WaterDrop, "Water", th.water); Spacer(Modifier.height(6.dp)); Caption("1.4 of 2.5 L") }
                    GlassCard { CardHeader(Duo.Bedtime, "Sleep", th.sleep); Spacer(Modifier.height(6.dp)); Caption("7 h 20 m · good") }
                    GlassCard { CardHeader(Duo.FitnessCenter, "Train", th.accent); Spacer(Modifier.height(6.dp)); Caption("Push day · 5 exercises") }
                    Row { AccentButton("Start workout", {}, icon = Duo.PlayArrow) }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/theme_${themeId}_${if (flat) "flat" else "glass"}.png")
    }

    @Test fun kineticFlat() = shot("kinetic", true)
    @Test fun kineticGlass() = shot("kinetic", false)
    @Test fun porcelainFlat() = shot("porcelain", true)
    @Test fun webCrimsonFlat() = shot("web_crimson", true)
    @Test fun racingRedGlass() = shot("racing_red", false)
    @Test fun trueBlackFlat() = shot("trueblack", true)

    @Test fun liveCards() {
        rule.setContent {
            MyFitTheme(Themes.byId("porcelain"), AppSettings()) {
                Column(Modifier.width(412.dp).background(androidx.compose.ui.graphics.Color(0xFFEDEFF3)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val now = System.currentTimeMillis()
                    listOf(
                        NCard(NKind.LIFT, "Bench press · set 3", "Up next: Incline press", chrono = now - 23 * 60_000, progress = 0.4f, progressLabel = "40% of Push day",
                            stats = listOf("12" to "Sets", "4,200 kg" to "Volume", "2/5" to "Exercises"), eyebrow = "Workout · live", unit = "elapsed", chip = "2/5"),
                        NCard(NKind.REST, "Rest · then Bench press", "8 sets done · Push day", chrono = now + 45_000, countDown = true, progress = 0.62f,
                            chip = "Rest", eyebrow = "Resting", unit = "left"),
                        NCard(NKind.MOON, "Iftar at 5:58 PM", "Ramadan fast · started 4:52 AM", chrono = now + 2 * 3_600_000, countDown = true, progress = 0.84f,
                            eyebrow = "Ramadan · live", unit = "to iftar", chip = "Iftar"),
                    ).forEach { c ->
                        AndroidView({ ctx -> NotifKit.big(ctx, c, listOf(0.2f, 0.4f, 0.6f, 0.8f)).apply(ctx, FrameLayout(ctx)) })
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/live_cards.png")
    }
}

