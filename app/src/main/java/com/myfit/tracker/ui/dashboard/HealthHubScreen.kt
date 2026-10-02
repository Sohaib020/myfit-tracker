package com.myfit.tracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.Sex
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.IconBubble
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings

private data class HubTile(val title: String, val sub: String, val icon: ImageVector, val color: Color, val go: Overlay)

/** Everything health-related in one place. */
@Composable
fun HealthHubScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val s = LocalSettings.current
    val profile = androidx.compose.runtime.produceState<com.myfit.tracker.data.db.UserProfile?>(null) { container.profileRepo.profile.collect { value = it } }.value
    val female = profile?.sex == Sex.FEMALE
    val tiles = buildList {
        add(HubTile("Vitals", "Heart, oxygen, BP, breathing", Duo.Pulse, th.danger, Overlay.Vitals))
        add(HubTile("Body", "Weight, body fat, photos", Duo.MonitorWeight, th.fat, Overlay.Body))
        add(HubTile("Activity & heart", "Steps, workouts, sleep", Duo.DirectionsRun, th.steps, Overlay.Activity))
        add(HubTile("Mindfulness", "Breathing, meditation, mood", Duo.SelfImprovement, th.sleep, Overlay.Mind))
        if (s.cycleEnabled || female) add(HubTile("Cycle", if (s.cycleEnabled) "Periods & predictions" else "Tap to turn on", Duo.CalendarMonth, th.protein, Overlay.Cycle))
        if (s.glucoseEnabled || s.diabetesType !in setOf("none", "unset")) add(HubTile("Blood sugar", if (s.glucoseEnabled) "Glucose, meds, A1c" else "Tap to turn on", Duo.Drop, th.water, Overlay.Glucose))
        add(HubTile("Reminders", "Water, meds, workouts…", Duo.Bell, th.warning, Overlay.Reminders))
        add(HubTile("Supplements", "Creatine, vitamins…", Duo.Egg, th.carbs, Overlay.Supplements))
        add(HubTile("Fasting", "Intermittent fasting timer", Duo.Timer, th.accent, Overlay.Fasting))
        add(HubTile("Badges", "Streaks & achievements", Duo.EmojiEvents, th.warning, Overlay.Badges))
        add(HubTile("Devices", "Watches & apps connected", Duo.Watch, th.textDim, Overlay.Devices))
    }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Health", { nav.pop() }, "All your health tools")
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(tiles) { t ->
                Glass(Modifier.fillMaxWidth().height(132.dp), shape = RoundedCornerShape(24.dp), onClick = { nav.push(t.go) }) {
                    Column(Modifier.padding(14.dp)) {
                        IconBubble(t.icon, t.color, 40.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(t.title, style = FitType.section, color = th.text)
                        Caption(t.sub)
                    }
                }
            }
            item(span = { GridItemSpan(2) }) { Caption("Data from your watch arrives through Health Connect. Nothing here is medical advice.", Modifier.padding(top = 6.dp), color = th.textFaint) }
        }
    }
}
