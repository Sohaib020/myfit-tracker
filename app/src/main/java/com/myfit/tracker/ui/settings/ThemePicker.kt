package com.myfit.tracker.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.prefs.AppSettings
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitTheme
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalBackdrop
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.Themes
import com.myfit.tracker.ui.theme.drawBackdrop
import com.myfit.tracker.ui.theme.flatSurface

/**
 * Pick a design style (Glass or Flat) and a theme, grouped by family. Used in Settings and onboarding.
 * Every theme works in both styles.
 */
@Composable
fun ThemePicker(container: AppContainer, settings: AppSettings, tileWidth: Int = 92) {
    val th = LocalFitTheme.current
    val flat = settings.uiStyle == 1
    Text("DESIGN STYLE", style = FitType.overline, color = th.textDim)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StyleCard("Glass", "Frosted, see-through cards over theme art", !flat, Modifier.weight(1f)) { container.write { container.settings.setUiStyle(0) } }
        StyleCard("Flat", "Solid cards, each tinted by what it shows", flat, Modifier.weight(1f)) { container.write { container.settings.setUiStyle(1) } }
    }
    Themes.families.forEach { (family, list) ->
        Text(family.uppercase(), style = FitType.overline, color = th.textDim, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { t -> ThemeTile(t, t.id == settings.themeId, flat, tileWidth) { container.write { container.settings.setTheme(t.id) } } }
        }
    }
}

@Composable
private fun StyleCard(title: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val glass = title == "Glass"
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) th.accent else th.text.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clickableNoRipple(onClick).padding(12.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(54.dp)) {
            val r = CornerRadius(14f)
            if (glass) {
                drawRoundRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(th.accent.copy(alpha = 0.55f), th.water.copy(alpha = 0.45f))), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(size.width * 0.12f, size.height * 0.2f), size = Size(size.width * 0.76f, size.height * 0.6f), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = 0.6f), topLeft = Offset(size.width * 0.12f, size.height * 0.2f), size = Size(size.width * 0.76f, size.height * 0.6f), cornerRadius = r, style = Stroke(2f))
            } else {
                val w3 = (size.width - 16f) / 3f
                listOf(th.protein, th.water, th.sleep).forEachIndexed { i, c ->
                    drawRoundRect(th.flatSurface(c), topLeft = Offset(i * (w3 + 8f), 0f), size = Size(w3, size.height), cornerRadius = r)
                    drawRoundRect(c, topLeft = Offset(i * (w3 + 8f) + 8f, 8f), size = Size(14f, 14f), cornerRadius = CornerRadius(7f))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = FitType.label, color = th.text)
        Caption(sub)
    }
}

@Composable
private fun ThemeTile(t: FitTheme, selected: Boolean, flat: Boolean, width: Int, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val backdrop = LocalBackdrop.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width.dp)) {
        Box(
            Modifier.size(width = width.dp, height = (width * 1.55f).dp).clip(RoundedCornerShape(20.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) t.accent else Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .clickableNoRipple(onClick)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val full = backdrop.rootSize
                if (full.width > 0) {
                    val s = size.width / full.width
                    scale(s, s, pivot = Offset.Zero) { drawBackdrop(t, null, t.stillT, full.width, full.height) }
                } else drawRect(t.bgBottom)
                val r = CornerRadius(18f)
                fun card(y: Float, h: Float, role: Color?) {
                    val c = if (flat) t.flatSurface(role) else t.glassFallback.copy(alpha = 0.55f)
                    drawRoundRect(c, topLeft = Offset(size.width * 0.1f, size.height * y), size = Size(size.width * 0.8f, size.height * h), cornerRadius = r)
                    if (role != null) drawRoundRect(role, topLeft = Offset(size.width * 0.16f, size.height * (y + 0.03f)), size = Size(size.width * 0.12f, size.width * 0.12f), cornerRadius = CornerRadius(8f))
                }
                card(0.14f, 0.2f, t.protein)
                card(0.38f, 0.2f, t.water)
                drawRoundRect(t.accent, topLeft = Offset(size.width * 0.1f, size.height * 0.8f), size = Size(size.width * 0.8f, size.height * 0.1f), cornerRadius = CornerRadius(40f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(t.name, style = FitType.caption, color = if (selected) th.text else th.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
