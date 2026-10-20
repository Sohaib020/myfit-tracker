package com.myfit.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.domain.LengthUnit
import com.myfit.tracker.domain.PlanProfile
import com.myfit.tracker.domain.WeightUnit
import com.myfit.tracker.ui.onboarding.BmiCard
import com.myfit.tracker.ui.onboarding.BodyShapePage
import com.myfit.tracker.ui.onboarding.FocusPage
import com.myfit.tracker.ui.onboarding.GoalPage
import com.myfit.tracker.ui.onboarding.HeightPage
import com.myfit.tracker.ui.onboarding.MuscleGoalPage
import com.myfit.tracker.ui.onboarding.PredictionPage
import com.myfit.tracker.ui.onboarding.TriedPage
import com.myfit.tracker.ui.plan.PlanSummaryCards
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** R36: the new onboarding pages, rendered for review (ui-shots branch). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1000dp-xxhdpi", application = android.app.Application::class)
class OnboardingShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun pages() {
        var page by mutableStateOf(0)
        rule.setContent {
            MyFitTheme(Themes.Kinetic, AppSettings()) {
                val th = LocalFitTheme.current
                Box(Modifier.width(400.dp).background(th.bgBottom).padding(20.dp)) {
                    when (page) {
                        0 -> GoalPage(PlanProfile.BUILD, false) {}
                        1 -> TriedPage("rebound") {}
                        2 -> MuscleGoalPage("strength", false) {}
                        3 -> FocusPage(setOf("Chest", "Arms", "Abs"), false) {}
                        4 -> HeightPage(178.0, LengthUnit.IN, false, {}) {}
                        5 -> Column { BmiCard(86.0, 178.0) }
                        6 -> BodyShapePage(4, false) {}
                        7 -> BodyShapePage(2, true) {}
                        8 -> PredictionPage(86.0, 75.0, WeightUnit.KG, 1) {}
                        9 -> GoalPage(PlanProfile.LOSE, true) {}
                        else -> PlanSummaryCards(PlanProfile.Summary(178.0, 86.0, 26, 27.1, "Overweight", "Build muscle", "Muscular", "Intermediate",
                            32, 8, 4, 2, 70, 30, listOf("Chest", "Arms", "Abs"), false), WeightUnit.KG, LengthUnit.IN)
                    }
                }
            }
        }
        for (p in 0..10) {
            page = p
            rule.waitForIdle(); Thread.sleep(1200); rule.mainClock.advanceTimeBy(2500); rule.waitForIdle()
            rule.onRoot().captureRoboImage("build/shots/onboarding_$p.png")
        }
    }
}
