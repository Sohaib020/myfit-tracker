package com.myfit.tracker.ui.vitals

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
fun VitalsScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("VitalsScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun CameraHeartRateScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("CameraHeartRateScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun DevicesScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("DevicesScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun VitalsTile(container: AppContainer, onClick: () -> Unit) {
    GlassCard(onClick = onClick) { Caption("VitalsTile") }
}
