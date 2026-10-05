package com.myfit.tracker.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.myfit.tracker.MainActivity
import com.myfit.tracker.MyFitApplication
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.StepsCalc
import com.myfit.tracker.domain.StepsSource
import com.myfit.tracker.domain.Targets
import kotlinx.coroutines.flow.first

/* Home-screen widgets: water (with +250 / +500 buttons) and steps. Data stays on the phone. */

private val BG = Color(0xFF0C1414)
private val ACCENT = Color(0xFF2CC9A7)
private val TEXT = Color(0xFFEFFAF7)
private val DIM = Color(0xFF8FB3AC)
private fun cp(c: Color) = ColorProvider(c, c)

internal data class WidgetNumbers(val waterMl: Double, val waterTarget: Double?, val steps: Long?, val stepTarget: Double?, val stepsSource: String?, val volumeUnit: com.myfit.tracker.domain.VolumeUnit)

internal suspend fun loadNumbers(c: Context): WidgetNumbers {
    val app = c.applicationContext as MyFitApplication
    val k = app.container
    val today = Clock.today()
    val day = k.logRepo.day(today).first()
    val targets = k.profileRepo.targets.first()
    val h = runCatching { k.healthRepo.day(today).first() }.getOrNull()
    val manual = StepsCalc.dayTotal(day.activity.filter { it.steps != null }.map { StepsCalc.Entry(it.steps!!, it.isDayTotal, it.loggedAt, it.id) })
    val pick = StepsSource.pick(h?.daily?.steps, manual?.toLong(), h?.phoneSteps)
    val units = runCatching { k.settings.settings.first().units.volume }.getOrDefault(com.myfit.tracker.domain.VolumeUnit.entries.first())
    return WidgetNumbers(day.water.sumOf { it.amountMl }, Targets.on(targets, TargetType.WATER_ML, today), pick?.steps,
        Targets.on(targets, TargetType.STEPS, today), pick?.source?.label, units)
}

object Widgets {
    /** Refresh both widgets (after logging water, a sync, etc.). Cheap when none are placed. */
    suspend fun refresh(c: Context) {
        runCatching { WaterWidget().updateAll(c) }
        runCatching { StepsWidget().updateAll(c) }
    }
}

class WaterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val n = loadNumbers(context)
        provideContent {
            val frac = n.waterTarget?.let { (n.waterMl / it).toFloat().coerceIn(0f, 1f) } ?: 0f
            Column(GlanceModifier.fillMaxSize().background(BG).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>())) {
                Text("💧 Water", style = TextStyle(color = cp(DIM), fontSize = 12.sp))
                Spacer(GlanceModifier.height(2.dp))
                Text(Fmt.volume(n.waterMl, n.volumeUnit), style = TextStyle(color = cp(TEXT), fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text(n.waterTarget?.let { "of " + Fmt.volume(it, n.volumeUnit) } ?: "today", style = TextStyle(color = cp(DIM), fontSize = 11.sp))
                Spacer(GlanceModifier.height(8.dp))
                LinearProgressIndicator(frac, GlanceModifier.fillMaxWidth().height(6.dp), color = cp(ACCENT), backgroundColor = cp(Color(0x22FFFFFF)))
                Spacer(GlanceModifier.height(10.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    AddButton("+250", 250.0, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    AddButton("+500", 500.0, GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AddButton(label: String, ml: Double, modifier: GlanceModifier) {
    Box(modifier.height(36.dp).background(ACCENT).cornerRadius(18.dp)
        .clickable(actionRunCallback<AddWaterAction>(actionParametersOf(AddWaterAction.ML to ml))), contentAlignment = Alignment.Center) {
        Text(label, style = TextStyle(color = cp(Color(0xFF04241D)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ml = parameters[ML] ?: return
        val app = context.applicationContext as MyFitApplication
        app.container.logRepo.addWater(ml)
        Widgets.refresh(context)
    }
    companion object { val ML = ActionParameters.Key<Double>("ml") }
}

class StepsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val n = loadNumbers(context)
        provideContent {
            val frac = if (n.steps != null && n.stepTarget != null) (n.steps / n.stepTarget).toFloat().coerceIn(0f, 1f) else 0f
            Column(GlanceModifier.fillMaxSize().background(BG).cornerRadius(24.dp).padding(14.dp).clickable(actionStartActivity<MainActivity>())) {
                Text("👟 Steps", style = TextStyle(color = cp(DIM), fontSize = 12.sp))
                Spacer(GlanceModifier.height(2.dp))
                Text(n.steps?.let { "%,d".format(it) } ?: "—", style = TextStyle(color = cp(TEXT), fontSize = 24.sp, fontWeight = FontWeight.Bold))
                Text(n.stepTarget?.let { "of %,d".format(it.toLong()) } ?: "today", style = TextStyle(color = cp(DIM), fontSize = 11.sp))
                Spacer(GlanceModifier.height(8.dp))
                LinearProgressIndicator(frac, GlanceModifier.fillMaxWidth().height(6.dp), color = cp(ACCENT), backgroundColor = cp(Color(0x22FFFFFF)))
                Spacer(GlanceModifier.height(6.dp))
                Text(n.stepsSource ?: "Connect Health Connect in MyFit", style = TextStyle(color = cp(DIM), fontSize = 10.sp), maxLines = 1)
            }
        }
    }
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WaterWidget() }
class StepsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StepsWidget() }
