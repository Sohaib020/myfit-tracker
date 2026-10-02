package com.myfit.tracker.ui.glucose

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
fun GlucoseScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("GlucoseScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun MedsScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("MedsScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun GlucoseTile(container: AppContainer, onClick: () -> Unit) {
    GlassCard(onClick = onClick) { Caption("GlucoseTile") }
}
