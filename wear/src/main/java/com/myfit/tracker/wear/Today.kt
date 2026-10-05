package com.myfit.tracker.wear

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Today's numbers as sent by the phone (path /myfit/today). */
data class Today(val steps: Long, val stepGoal: Long, val waterMl: Double, val waterGoal: Double, val kcal: Double, val kcalGoal: Double, val updated: Long) {
    val stepFrac get() = if (stepGoal > 0) (steps.toFloat() / stepGoal).coerceIn(0f, 1f) else 0f
    companion object {
        const val PATH = "/myfit/today"
        suspend fun load(c: Context): Today? = runCatching {
            val items = Wearable.getDataClient(c).dataItems.await()
            try {
                items.firstOrNull { it.uri.path == PATH }?.let { item ->
                    val m = DataMapItem.fromDataItem(item).dataMap
                    Today(m.getLong("steps"), m.getLong("stepGoal"), m.getDouble("water"), m.getDouble("waterGoal"), m.getDouble("kcal"), m.getDouble("kcalGoal"), m.getLong("at"))
                }
            } finally { items.release() }
        }.getOrNull()
    }
}

private const val ACCENT = 0xFF2CC9A7.toInt()
private const val TEXT = 0xFFEFFAF7.toInt()
private const val DIM = 0xFF8FB3AC.toInt()
private fun fmt(n: Long) = "%,d".format(n)
private fun litres(ml: Double) = if (ml >= 1000) "%.1f L".format(ml / 1000) else "${ml.toInt()} ml"

class TodayTileService : TileService() {
    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = LayoutElementBuilders.Text.Builder().setText(s)
        .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color))
            .apply { if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD) }.build()).build()

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            val t = Today.load(this@TodayTileService)
            val open = ModifiersBuilders.Clickable.Builder().setId("open").setOnClick(
                ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(TodayActivity::class.java.name).build(),
                ).build(),
            ).build()
            val col = LayoutElementBuilders.Column.Builder().setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            if (t == null) {
                col.addContent(text("MyFit", 14f, DIM)).addContent(text("Open MyFit on", 14f, TEXT)).addContent(text("your phone", 14f, TEXT))
            } else {
                col.addContent(text("STEPS", 11f, DIM))
                    .addContent(text(fmt(t.steps), 26f, TEXT, true))
                    .addContent(text(if (t.stepGoal > 0) "of ${fmt(t.stepGoal)}" else "today", 12f, DIM))
                    .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
                    .addContent(text("💧 " + litres(t.waterMl) + (if (t.waterGoal > 0) " / " + litres(t.waterGoal) else ""), 13f, TEXT))
                    .addContent(text("🍽 ${t.kcal.toInt()}" + (if (t.kcalGoal > 0) " / ${t.kcalGoal.toInt()}" else "") + " kcal", 13f, TEXT))
            }
            val root = LayoutElementBuilders.Box.Builder().setWidth(expand()).setHeight(expand())
                .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
                .addContent(
                    LayoutElementBuilders.Arc.Builder()
                        .setAnchorAngle(degrees(0f)).setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                        .addContent(LayoutElementBuilders.ArcLine.Builder().setLength(degrees(360f * (t?.stepFrac ?: 0f))).setThickness(dp(6f)).setColor(argb(ACCENT)).build())
                        .build(),
                )
                .addContent(col.build())
                .build()
            TileBuilders.Tile.Builder().setResourcesVersion("1").setFreshnessIntervalMillis(30 * 60_000L)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root)).build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        SuspendToFutureAdapter.launchFuture { ResourceBuilders.Resources.Builder().setVersion("1").build() }
}

/** New numbers from the phone → refresh the tile. */
class PhoneDataListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(TodayTileService::class.java)
    }
}

/** Tapping the tile: a simple full-screen view of the same numbers. */
class TodayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(Color.BLACK); setPadding(24, 24, 24, 24) }
        fun tv(s: String, size: Float, color: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER }
        val title = tv("MyFit", 14f, DIM); val main = tv("…", 26f, TEXT); val sub = tv("", 13f, TEXT)
        box.addView(title); box.addView(main); box.addView(sub)
        setContentView(box)
        CoroutineScope(Dispatchers.Main).launch {
            val t = Today.load(this@TodayActivity)
            if (t == null) { main.text = "No data yet"; sub.text = "Open MyFit on your phone" }
            else {
                main.text = "${fmt(t.steps)} steps"
                sub.text = "💧 ${litres(t.waterMl)}\n🍽 ${t.kcal.toInt()} kcal"
            }
        }
    }
}
