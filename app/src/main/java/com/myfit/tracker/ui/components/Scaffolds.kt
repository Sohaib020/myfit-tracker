package com.myfit.tracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme

/** Title row used by full-screen overlays: glass back button, title, optional actions. */
@Composable
fun OverlayTopBar(title: String, onBack: () -> Unit, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val th = LocalFitTheme.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Duo.ArrowBack, onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.title, color = th.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Caption(subtitle)
        }
        actions()
    }
}

@Composable
fun OverlayScaffold(title: String, onBack: () -> Unit, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(title, onBack, subtitle, actions)
        content()
    }
}

@Composable
fun GlassSearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val th = LocalFitTheme.current
    Glass(modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(25.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Duo.Search, null, tint = th.textDim, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(hint, style = FitType.body, color = th.textFaint)
                BasicTextField(value, onChange, singleLine = true, textStyle = FitType.body.copy(color = th.text), cursorBrush = SolidColor(th.accent), modifier = Modifier.fillMaxWidth())
            }
            if (value.isNotEmpty()) Icon(Duo.Close, "Clear", tint = th.textDim, modifier = Modifier.size(20.dp).clickableNoRipple { onChange("") })
        }
    }
}
