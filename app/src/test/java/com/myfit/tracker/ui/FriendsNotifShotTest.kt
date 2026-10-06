package com.myfit.tracker.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.notify.NCard
import com.myfit.tracker.notify.NKind
import com.myfit.tracker.notify.NotifKit
import com.myfit.tracker.social.FriendCard
import com.myfit.tracker.ui.social.ChallengeCard
import com.myfit.tracker.ui.social.Podium
import com.myfit.tracker.ui.social.RaceRow
import com.myfit.tracker.ui.social.UserAvatar
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.MyFitTheme
import com.myfit.tracker.ui.theme.Themes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Friends page pieces (podium, race rows, avatars, challenge card) and the notification cards, for review from CI. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h1600dp-xxhdpi", application = android.app.Application::class)
class FriendsNotifShotTest {
    @get:Rule val rule = createComposeRule()

    private fun f(uid: String, name: String, steps: Long, lvl: Int, avatar: String, me: Boolean = false) =
        FriendCard(uid, name, name.lowercase(), 0xFF4C8DFFL, avatar, null, lvl, lvl * 40, "zara", steps, 120, 3, listOf("k2"), listOf("First steps"), me, System.currentTimeMillis() - 40 * 60_000)

    @OptIn(ExperimentalLayoutApi::class)
    @Test fun friends() {
        val people = listOf(f("a", "Ayesha Khan", 48_210, 7, "ch_zara"), f("b", "You", 41_050, 5, "sp_run", me = true), f("c", "Bilal", 33_900, 3, "cr_fox"),
            f("d", "Hamza", 12_400, 2, ""), f("e", "Sara", 8_300, 1, "fc_cool"))
        rule.setContent {
            MyFitTheme(Themes.Kinetic, AppSettings()) {
                val th = LocalFitTheme.current
                Column(Modifier.width(412.dp).verticalScroll(rememberScrollState()).background(th.bgBottom).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Podium(people.take(3)) {}
                    people.forEachIndexed { i, p -> RaceRow(i + 1, p, p.weekSteps / 48_210f) {} }
                    ChallengeCard(com.myfit.tracker.social.Challenge("x", "Weekend step-off", com.myfit.tracker.social.Metric.STEPS, "2026-10-03", "2026-10-12", "a", listOf("a", "b", "c"))) {}
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("ch_pip_flex", "ch_motu", "sp_cricket", "sp_yoga", "cr_panda", "cr_robot", "fc_love", "fd_samosa", "fd_chai", "ic_moon", "ic_trophy", "").forEach {
                            UserAvatar(it, null, "Zed", 0xFFFF7A1AL, 56.dp)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/friends_parts.png")
    }

    @Test fun notificationCards() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cards = listOf(
            NCard(NKind.LIFT, "Push day", "Now: Bench press", chrono = System.currentTimeMillis() - 23 * 60_000, progress = 0.4f, progressLabel = "Up next: Incline press",
                stats = listOf("12" to "Sets", "3,240 kg" to "Volume", "2/5" to "Exercises")),
            NCard(NKind.FLAME, "Fasting · Fat burning", "Goal 16 h · ends 8:30 PM", chrono = System.currentTimeMillis() - 13 * 3_600_000, progress = 0.81f, progressLabel = "81% of your goal",
                stats = listOf("4:30 AM" to "Started", "Fat burning" to "Phase", "16 h" to "Goal")),
            NCard(NKind.DROP, "Time for water", "A glass now keeps you on track.", value = "1.2 L", progress = 0.48f, progressLabel = "1.2 of 2.5 L today"),
            NCard(NKind.PILL, "Metformin 500 mg", "Take with food.", chip = "8:00 AM"),
            NCard(NKind.SUGAR, "Ammi: low blood sugar", "62 mg/dL. They should take something sweet and recheck in 15 minutes.", value = "62 mg/dL", chip = "LOW"),
        )
        val col = android.widget.LinearLayout(ctx).apply { orientation = android.widget.LinearLayout.VERTICAL; setBackgroundColor(0xFF1E1F24.toInt()); setPadding(24, 24, 24, 24) }
        cards.forEach { c ->
            val big = NotifKit.big(ctx, c).apply(ctx, FrameLayout(ctx))
            val small = NotifKit.small(ctx, c).apply(ctx, FrameLayout(ctx))
            listOf(small, big).forEach { v ->
                val card = FrameLayout(ctx).apply { setBackgroundColor(0xFF2B2D33.toInt()); setPadding(36, 28, 36, 28); addView(v) }
                col.addView(card, android.widget.LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 24 })
            }
        }
        val w = 412 * 3
        col.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
        col.layout(0, 0, w, col.measuredHeight)
        val bmp = android.graphics.Bitmap.createBitmap(w, col.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
        col.draw(android.graphics.Canvas(bmp))
        java.io.File("build/shots").mkdirs()
        java.io.FileOutputStream("build/shots/notif_cards.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
