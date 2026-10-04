package com.myfit.tracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.ui.dashboard.DashState
import com.myfit.tracker.ui.dashboard.RingsCard
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders Today's progress at several phone widths with long values, saves screenshots and FAILS if any text
 * spills outside the card or overlaps the rings — so this card can't silently break again.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h900dp-xxhdpi")
class ProgressCardShotTest {
    @get:Rule val rule = createComposeRule()

    private val cases = listOf(
        "typical" to DashState(waterMl = 2000.0, waterTarget = 2900.0, steps = 229, stepTarget = 8000.0, sleepMin = null, sleepTarget = 480.0),
        "long" to DashState(waterMl = 10_500.0, waterTarget = 12_000.0, steps = 123_456, stepTarget = 15_000.0, sleepMin = 645, sleepTarget = 540.0),
        "empty" to DashState(),
    )

    private fun texts(n: SemanticsNode): List<SemanticsNode> =
        (if (n.config.getOrNull(SemanticsProperties.Text) != null) listOf(n) else emptyList()) + n.children.flatMap { texts(it) }

    private fun find(n: SemanticsNode, tag: String): SemanticsNode? =
        if (n.config.getOrNull(SemanticsProperties.TestTag) == tag) n else n.children.firstNotNullOfOrNull { find(it, tag) }

    @Test fun progressCardAtWidths() {
        val problems = mutableListOf<String>()
        for (w in listOf(320, 360, 412, 480)) for ((name, st) in cases) {
            rule.setContent {
                MyFitTheme(Themes.Kinetic, AppSettings()) {
                    Box(Modifier.width(w.dp).padding(16.dp)) { RingsCard(st) {} }
                }
            }
            rule.waitForIdle()
            rule.onRoot().captureRoboImage("build/shots/progress_${w}_$name.png")
            val root = rule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val bounds = root.boundsInRoot
            val rings = find(root, "rings")?.boundsInRoot
            texts(root).forEach { t ->
                val b = t.boundsInRoot
                val label = t.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: "?"
                if (b.left < bounds.left - 1 || b.right > bounds.right + 1) problems += "$w/$name: \"$label\" spills outside ($b vs $bounds)"
                if (rings != null && b.overlaps(rings) && b.width > 0) problems += "$w/$name: \"$label\" overlaps rings ($b vs $rings)"
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
