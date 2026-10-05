package com.myfit.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.ui.components.ProvideWindowInfo
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import com.myfit.tracker.ui.train.QUICK_DAYS
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Buttons and chips from the new Train / Backup / Builder screens at every screen width we support — Fold cover
 * (280 dp) to tablets (840 dp). Fails if any label wraps onto a second line or spills outside its parent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h1400dp-xxhdpi", application = android.app.Application::class)
class AdaptiveShotTest {
    @get:Rule val rule = createComposeRule()

    private fun texts(n: SemanticsNode): List<SemanticsNode> =
        (if (n.config.getOrNull(SemanticsProperties.Text) != null) listOf(n) else emptyList()) + n.children.flatMap { texts(it) }

    private val pairs = listOf(
        "Build my day" to "Empty workout", "Start now" to "Save to My workout days", "Back up now" to "Restore from Drive",
        "Save to a file" to "Restore from file", "Accept" to "Decline", "Build a day" to "Start from scratch",
    )

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun buttonsAndChipsAtWidths() {
        val problems = mutableListOf<String>()
        var width by mutableIntStateOf(360)
        rule.setContent {
            MyFitTheme(Themes.Kinetic, AppSettings()) {
                val th = LocalFitTheme.current
                ProvideWindowInfo {
                    Column(Modifier.width(width.dp).background(th.bgBottom).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pairs.forEach { (a, b) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccentButton(a, {}, Modifier.weight(1f), icon = Duo.PlayArrow, height = 48.dp)
                                GlassButton(b, {}, Modifier.weight(1f), icon = Duo.Add, height = 48.dp)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QUICK_DAYS.forEach { (l, _) -> GlassChip(l, l == "Chest & Biceps", {}) }
                        }
                    }
                }
            }
        }
        val density = 3f   // xxhdpi
        for (w in listOf(280, 320, 360, 412, 600, 673, 840)) {
            width = w
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("build/shots/adaptive_$w.png")
            val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val bounds = root.boundsInRoot
            texts(root).forEach { t ->
                val b = t.boundsInRoot
                val label = t.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: "?"
                if (b.left < bounds.left - 1 || b.right > bounds.right + 1) problems += "$w: \"$label\" spills outside"
                if (b.height > 44 * density) problems += "$w: \"$label\" more than two lines (${b.height / density} dp tall)"
                if (w >= 360 && b.height > 30 * density) problems += "$w: \"$label\" wraps on a normal phone"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
