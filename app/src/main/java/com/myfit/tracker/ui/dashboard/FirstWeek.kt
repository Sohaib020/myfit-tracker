package com.myfit.tracker.ui.dashboard

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.nav.Overlay
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** First-week checklist on Home: three steps that make the app useful, shown for the first 10 days until done or dismissed. */
object FirstWeek {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("first_week", Context.MODE_PRIVATE)
    fun dismissed(c: Context) = sp(c).getBoolean("dismissed", false)
    fun dismiss(c: Context) = sp(c).edit().putBoolean("dismissed", true).apply()
    fun inWindow(c: Context): Boolean = runCatching {
        val t = c.packageManager.getPackageInfo(c.packageName, 0).firstInstallTime
        System.currentTimeMillis() - t < 10L * 86_400_000L
    }.getOrDefault(false)
}

private data class Steps(val meal: Boolean, val workout: Boolean, val health: Boolean) { val done get() = listOf(meal, workout, health).count { it } }

@Composable
fun FirstWeekCard(container: AppContainer, goTab: (Int) -> Unit) {
    val ctx = LocalContext.current
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    var hidden by remember { mutableStateOf(FirstWeek.dismissed(ctx) || !FirstWeek.inWindow(ctx)) }
    if (hidden) return
    // re-checks every few seconds while Home is open, so ticks appear as soon as you come back
    val s by produceState<Steps?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                Steps(
                    meal = runCatching { container.db.nutritionDao().mealItemCount() > 0 }.getOrDefault(false),
                    workout = runCatching { container.db.workoutDao().completedCount() > 0 }.getOrDefault(false),
                    health = runCatching { container.healthSync.granted().isNotEmpty() }.getOrDefault(false),
                )
            }
            delay(4000)
        }
    }
    val st = s ?: return
    Glass(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (st.done == 3) "You're all set! 🎉" else "Your first week", style = FitType.section, color = th.text)
                    Caption(if (st.done == 3) "MyFit now has what it needs to coach you." else "${st.done} of 3 done — each takes under a minute.")
                }
                Icon(Duo.Close, "Hide", tint = th.textDim, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp))
                    .clickableNoRipple { FirstWeek.dismiss(ctx); hidden = true }.padding(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (i < st.done) th.accentBright else th.text.copy(alpha = 0.1f)))
                }
            }
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StepRow(st.health, "Connect Health Connect", "Steps, sleep and heart rate from your phone or watch") { nav.push(Overlay.Devices) }
                StepRow(st.meal, "Log your first meal", "Search, scan a barcode or snap a photo") { nav.push(Overlay.Food()) }
                StepRow(st.workout, "Finish your first workout", "Tap a muscle in Train — it builds itself") { goTab(1) }
            }
        }
    }
}

@Composable
private fun StepRow(done: Boolean, title: String, hint: String, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickableNoRipple { if (!done) onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(if (done) Duo.CheckCircle else Duo.RadioButtonUnchecked, null, tint = if (done) th.success else th.textDim, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = FitType.label, color = if (done) th.textDim else th.text, textDecoration = if (done) TextDecoration.LineThrough else null)
            if (!done) Caption(hint)
        }
        if (!done) Icon(Duo.KeyboardArrowRight, null, tint = th.textDim)
    }
}
