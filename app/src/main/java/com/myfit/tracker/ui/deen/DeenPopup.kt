package com.myfit.tracker.ui.deen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.LocalFitTheme

/**
 * Asked once, right after Pip's tour: turn on Shariah & Health? (It used to be an onboarding question.)
 * Either answer can be changed any time in Settings → Shariah & Health.
 */
@Composable
fun DeenPopup(container: AppContainer, visible: Boolean) {
    val th = LocalFitTheme.current
    val green = Color(0xFF2FA37A)
    GlassSheet(visible = visible, onDismiss = { container.write { container.settings.setMuslim("no") } }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(green.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Duo.Bedtime, null, tint = green, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Shariah & Health", style = FitType.title, color = th.text)
                Caption("For Muslim users — optional")
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Would you like these turned on?", style = FitType.section, color = th.text)
        Spacer(Modifier.height(10.dp))
        Feature(Duo.Schedule, "Prayer times & Qibla", "Accurate times for your city with optional reminders, and a Qibla compass.", green)
        Feature(Duo.ForkKnife, "Halal-only foods", "Non-halal foods are hidden from search, meal plans and the nutritionist, with halal tips on packaged foods.", green)
        Feature(Duo.Bedtime, "Ramadan & fasting tools", "Sehri and iftar times, a Ramadan meal plan and fasting-friendly workouts.", green)
        Feature(Duo.Home, "Shariah & Health hub on Home", "Dhikr counter, Sunnah habits, and Hajj & Umrah fitness prep.", green)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("No thanks", { container.write { container.settings.setMuslim("no") } }, Modifier.weight(1f), height = 48.dp)
            AccentButton("Yes, turn on", { container.write { container.settings.setMuslim("yes") } }, Modifier.weight(1f), icon = Duo.Check, height = 48.dp)
        }
        Caption("You can switch this on or off any time in Settings → Shariah & Health.", Modifier.padding(top = 8.dp), color = th.textFaint)
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, sub: String, c: Color) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.label, color = th.text)
            Caption(sub)
        }
    }
}
