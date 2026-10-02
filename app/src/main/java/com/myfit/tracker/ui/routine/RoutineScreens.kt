package com.myfit.tracker.ui.routine

import androidx.compose.runtime.Composable
import com.myfit.tracker.AppContainer

/** Reminders: water, meals, workout, weigh-in, sleep, supplements, custom + quiet hours. */
@Composable
fun RemindersScreen(container: AppContainer) {
    RemindersContent(container)
}

/** Supplements: daily checklist, streaks, adherence, history, optional daily reminder. */
@Composable
fun SupplementsScreen(container: AppContainer) {
    SupplementsContent(container)
}

/** Fasting timer: presets, Ramadan mode, live ring, stages, history and weekly stats. */
@Composable
fun FastingScreen(container: AppContainer) {
    FastingContent(container)
}
