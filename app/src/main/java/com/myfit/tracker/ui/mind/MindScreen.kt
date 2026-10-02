package com.myfit.tracker.ui.mind

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
fun MindScreen(container: AppContainer) {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) { OverlayTopBar("MindScreen", { nav.pop() }); Caption("Coming soon") }
}

@Composable
fun MindTile(container: AppContainer, onClick: () -> Unit) {
    GlassCard(onClick = onClick) { Caption("MindTile") }
}
