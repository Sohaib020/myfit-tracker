package com.myfit.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.ui.arena.*
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Renders the challenge dashboard pieces (ring, pace chart, daily bars) so the design can be reviewed from CI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-xxhdpi", application = android.app.Application::class)
class ChallengeShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dashboard() {
        val from = LocalDate.of(2026, 10, 1); val today = from.plusDays(17)
        val ch = ArenaChallenge("t", "October step marathon", "Walk 240,000 steps this month", Period.MONTH, ArenaMetric.STEPS, 240_000.0,
            Mascot.TAJ, from, from.plusDays(30), Scene.entries.first())
        val vals = listOf(6200, 8100, 9400, 4300, 7700, 12100, 10500, 3900, 8800, 9100, 7200, 6600, 11800, 9900, 5400, 8300, 7600, 6100)
        val days = vals.mapIndexed { i, v -> Day(from.plusDays(i.toLong()), v.toLong(), 30, v * 0.75, 0) }
        val m = paceModel(ch, days, today)
        rule.setContent {
            MyFitTheme(Themes.Kinetic, AppSettings()) {
                val th = LocalFitTheme.current
                Column(Modifier.width(412.dp).background(th.bgBottom).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row {
                        ProgressRing(m.frac, m.pace, ch.mascot.accent, 128.dp, 12.dp) { Text("${(m.frac * 100).toInt()}%", style = FitType.metric, color = th.text) }
                        Spacer(Modifier.width(16.dp))
                        Column { Text(fmtMetric(m.value, ch.metric), style = FitType.title, color = th.text); PaceBadge(m, ch.metric)
                            Text("needed ${fmtMetric(m.perDayNeeded, ch.metric)}/day · finish ${m.projectedFinish?.let { shortDate(it) } ?: "—"}", style = FitType.caption, color = th.textDim) }
                    }
                    PaceChart(m, ch, Mascot.TAJ, 190.dp)
                    DailyBars(m, ch, 110.dp)
                    PaceChart(m, ch, Mascot.TAJ, 84.dp, labels = false)
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/challenge_dashboard.png")
    }
}
