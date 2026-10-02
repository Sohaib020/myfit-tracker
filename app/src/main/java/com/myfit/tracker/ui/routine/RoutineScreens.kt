package com.myfit.tracker.ui.routine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav

@Composable
fun RemindersScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("RemindersScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun SupplementsScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("SupplementsScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun FastingScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("FastingScreen", { nav.pop() }); Caption("Coming soon") }
}

