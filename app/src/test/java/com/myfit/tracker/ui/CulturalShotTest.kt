package com.myfit.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.ui.theme.CulturalThemes
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import com.myfit.tracker.ui.theme.drawBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R20: all fifteen cultural backdrops on one contact sheet (phone-shaped tiles with two sample cards). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h1700dp-xxhdpi", application = android.app.Application::class)
class CulturalShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun cultural_sheet() {
        rule.setContent {
            MyFitTheme(Themes.Kinetic, AppSettings()) {
                Column(Modifier.background(Color.Black).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CulturalThemes.all.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { t ->
                                Box(Modifier.width(128.dp).height(300.dp).clip(RoundedCornerShape(16.dp))) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        drawBackdrop(t, null, t.stillT, size.width, size.height)
                                        fun card(y: Float, c: Color) {
                                            drawRoundRect(t.glassFallback.copy(alpha = 0.55f), Offset(size.width * 0.08f, size.height * y), Size(size.width * 0.84f, size.height * 0.13f), CornerRadius(24f))
                                            drawRoundRect(c, Offset(size.width * 0.14f, size.height * (y + 0.025f)), Size(size.width * 0.12f, size.width * 0.12f), CornerRadius(10f))
                                        }
                                        card(0.3f, t.protein); card(0.46f, t.water)
                                        drawRoundRect(t.accent, Offset(size.width * 0.08f, size.height * 0.86f), Size(size.width * 0.84f, size.height * 0.06f), CornerRadius(60f))
                                    }
                                    Column(Modifier.padding(10.dp)) {
                                        Text(t.name, style = FitType.label, color = Color.White)
                                        Text(CulturalThemes.info(t.id)?.country ?: "", style = FitType.caption, color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/cultural_sheet.png")
    }
}
