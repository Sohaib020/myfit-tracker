package com.myfit.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.ui.arena.JourneyCoverArt
import com.myfit.tracker.ui.arena.JourneyTrack
import com.myfit.tracker.ui.arena.Journeys
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders all journey covers and a trail so the vector art can be reviewed from CI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h2400dp-xxhdpi", application = android.app.Application::class)
class JourneyArtShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun coversAndTrack() {
        rule.setContent {
            MyFitTheme(Themes.Kinetic, AppSettings()) {
                val th = LocalFitTheme.current
                Column(Modifier.width(412.dp).background(th.bgBottom).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Journeys.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { j -> JourneyCoverArt(j.id, Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(18.dp))) }
                        }
                    }
                    val j = Journeys.first { it.id == "lahore" }
                    JourneyTrack(j, 2.6, null, 'S')
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/journeys.png")
    }
}
