package com.myfit.tracker.ui.report

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.WeekReport
import com.myfit.tracker.domain.WeeklyReport
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Your week" — the Sunday report, with a card you can share as an image. */
@Composable
fun WeeklyReportScreen(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val r by produceState<WeekReport?>(null) { value = withContext(Dispatchers.IO) { runCatching { WeeklyReport.build(container) }.getOrNull() } }
    val layer = rememberGraphicsLayer()
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Your week", { nav.pop() }, "Last 7 days vs the 7 before")
        val rep = r
        if (rep == null) { Caption("Putting your week together…", Modifier.padding(16.dp)); return@Column }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) { ShareCard(rep) }
            AccentButton("Share my week", {
                scope.launch {
                    runCatching {
                        val bmp = layer.toImageBitmap().asAndroidBitmap()
                        val f = withContext(Dispatchers.IO) {
                            File(File(ctx.cacheDir, "reports").apply { mkdirs() }, "myfit_week.png").also { out -> out.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                        }
                        shareImage(ctx, f)
                    }.onFailure { toaster.show("Couldn't create the image") }
                }
            }, Modifier.fillMaxWidth(), icon = Duo.Send, height = 52.dp)
            Caption("The image only shows what's on the card — nothing else leaves your phone.", Modifier.padding(horizontal = 6.dp))
            Tips(rep)
        }
    }
}

private fun shareImage(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share your week").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

private fun pct(now: Double, before: Double?): String? = if (before == null || before <= 0) null else
    ((now - before) / before * 100).toInt().let { if (it > 0) "▲ $it%" else if (it < 0) "▼ ${-it}%" else "same" }

/** Solid-drawn card (no blur) so it renders identically into the shared image. */
@Composable
private fun ShareCard(r: WeekReport) {
    val u = LocalSettings.current.units
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val ink = Color(0xFF04241D)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF7FE7CF), Color(0xFF2CC9A7), Color(0xFF138F77))))
            .padding(20.dp),
    ) {
        Text("MY WEEK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.7f), letterSpacing = 2.sp)
        Text("${fmt.format(r.from)} – ${fmt.format(r.to)}", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = ink)
        Spacer(Modifier.height(16.dp))
        val cells = buildList {
            add(Triple("Workouts", "${r.workouts}", if (r.prevWorkouts > 0) pct(r.workouts.toDouble(), r.prevWorkouts.toDouble()) else null))
            if (r.volumeKg > 0) add(Triple("Lifted", Fmt.weight(r.volumeKg, u.weight, 0), pct(r.volumeKg, r.prevVolumeKg)))
            else add(Triple("Training", "${r.trainMin} min", null))
            r.steps?.let { add(Triple("Steps", "%,d".format(it), pct(it.toDouble(), r.prevSteps?.toDouble()))) }
            r.sleepAvgMin?.let { add(Triple("Avg sleep", "${(it / 60).toInt()}h ${(it % 60).toInt()}m", null)) }
            r.waterAvgMl?.let { add(Triple("Water / day", Fmt.volume(it, u.volume), null)) }
            r.kcalAvg?.let { add(Triple("Eaten / day", "${it.toInt()} ${com.myfit.tracker.domain.EnergyUnit.label}", "${r.daysFoodLogged} days logged")) }
        }.take(6)
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (l, v, d) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.35f)).padding(12.dp)) {
                        Text(l, fontSize = 12.sp, color = ink.copy(alpha = 0.7f))
                        Text(v, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        Text(d ?: " ", fontSize = 11.sp, color = ink.copy(alpha = 0.75f))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
        Row {
            Text("MyFit Tracker", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
            Spacer(Modifier.width(6.dp))
            Text("· private fitness logbook", fontSize = 13.sp, color = ink.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun Tips(r: WeekReport) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val lines = buildList {
        if (r.workouts == 0) add("No workouts logged this week — even one 20-minute session keeps the habit alive.")
        else if (r.prevWorkouts in 1 until r.workouts) add("You trained more than last week (${r.prevWorkouts} → ${r.workouts}). Keep it steady rather than jumping again.")
        r.bestStepsDay?.let { (d, s) -> add("Best step day: ${d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())} with %,d steps.".format(s)) }
        if (r.stepDaysAtGoal > 0) add("You hit your step goal on ${r.stepDaysAtGoal} of 7 days.")
        r.sleepAvgMin?.let { if (it < 420) add("Sleep averaged under 7 hours — recovery and appetite both improve with more.") }
        r.weightChangeKg?.let { add("Weight average: " + (if (it >= 0) "+" else "−") + Fmt.weight(kotlin.math.abs(it), u.weight, 1) + " vs the week before (averages smooth out water swings).") }
        if (r.daysFoodLogged in 1..3) add("Food logged on ${r.daysFoodLogged} days — logging 5+ days makes the calorie average meaningful.")
    }
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(th.text.copy(alpha = 0.06f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What stood out", style = FitType.section, color = th.text)
        lines.forEach { Text("• $it", style = FitType.body, color = th.text) }
    }
}
