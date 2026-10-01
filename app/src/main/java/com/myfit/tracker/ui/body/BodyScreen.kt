package com.myfit.tracker.ui.body

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.BodyMeasurement
import com.myfit.tracker.data.db.MeasurementSite
import com.myfit.tracker.data.db.ProgressPhoto
import com.myfit.tracker.data.db.WeightEntry
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Fmt
import com.myfit.tracker.domain.Units
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.ChartPoint
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.GlassSheet
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.ProgressChart
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.entries.Sheet
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val dFmt = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/** One value per day: the last weigh-in of each day (so several weigh-ins don't skew trends). */
private fun dailyWeights(all: List<WeightEntry>): List<Pair<LocalDate, WeightEntry>> =
    all.groupBy { it.localDate }.map { (d, l) -> LocalDate.parse(d) to l.maxBy { it.loggedAt } }.sortedBy { it.first }

/** Least-squares slope in kg per week over (day, kg) points; null with fewer than 2 weeks of data. */
private fun weeklyRate(pts: List<Pair<LocalDate, Double>>): Double? {
    if (pts.size < 4) return null
    val x0 = pts.first().first.toEpochDay()
    val span = pts.last().first.toEpochDay() - x0
    if (span < 14) return null
    val xs = pts.map { (it.first.toEpochDay() - x0).toDouble() }; val ys = pts.map { it.second }
    val mx = xs.average(); val my = ys.average()
    val den = xs.sumOf { (it - mx) * (it - mx) }
    if (den == 0.0) return null
    return xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den * 7.0
}

/** Body analytics: weight & body-fat trends, weekly averages, measurements and private progress photos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyScreen(container: AppContainer, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val u = LocalSettings.current.units
    val weights by container.logRepo.weightsAll().collectAsState(initial = null)
    val measures by container.logRepo.measurementsAll().collectAsState(initial = emptyList())
    val photos by container.db.progressPhotoDao().observeAll().collectAsState(initial = emptyList())
    val profile by container.profileRepo.profile.collectAsState(initial = null)
    var range by remember { mutableIntStateOf(1) }   // 0 1M, 1 3M, 2 6M, 3 1Y, 4 All
    var showFat by remember { mutableStateOf(false) }
    var addPhoto by remember { mutableStateOf(false) }
    var compare by remember { mutableStateOf<Pair<ProgressPhoto, ProgressPhoto>?>(null) }
    var viewPhoto by remember { mutableStateOf<ProgressPhoto?>(null) }

    val today = Clock.today()
    val from = when (range) { 0 -> today.minusMonths(1); 1 -> today.minusMonths(3); 2 -> today.minusMonths(6); 3 -> today.minusYears(1); else -> null }
    val days = remember(weights) { dailyWeights(weights.orEmpty()) }
    val inRange = days.filter { from == null || !it.first.isBefore(from) }

    fun wDisp(kg: Double) = Units.kgTo(kg, u.weight)
    fun wFmt(kg: Double, dec: Int = 1) = Fmt.weight(kg, u.weight, dec)

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Body", { nav.pop() }, "Weight, body fat, measurements & photos")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---------------- weight
            item {
                GlassCard {
                    CardHeader(Duo.MonitorWeight, if (showFat) "Body fat" else "Weight", th.fat) {
                        if (days.isNotEmpty()) DataBadge(DataKind.RECORDED)
                    }
                    Spacer(Modifier.height(10.dp))
                    val fatPts = inRange.mapNotNull { (d, e) -> e.bodyFatPct?.let { d to it } }
                    if (weights == null) Caption("Loading…")
                    else if (days.isEmpty()) {
                        Caption("No weigh-ins yet. Log one, or let your Galaxy Watch / smart scale fill this in through Samsung Health.")
                        Spacer(Modifier.height(10.dp))
                        AccentButton("Log weight", { open(Sheet.Weight()) }, icon = Duo.Add, height = 44.dp)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassChip("Weight", !showFat, { showFat = false })
                            if (days.any { it.second.bodyFatPct != null }) GlassChip("Body fat %", showFat, { showFat = true })
                        }
                        Spacer(Modifier.height(10.dp))
                        val pts = if (showFat) fatPts.map { (d, v) -> ChartPoint(d.toEpochDay(), v, d.format(dFmt)) }
                                  else inRange.map { (d, e) -> ChartPoint(d.toEpochDay(), wDisp(e.weightKg), d.format(dFmt)) }
                        if (pts.size >= 2) ProgressChart(pts, if (showFat) th.warning else th.fat, { v -> if (showFat) Fmt.trim(v, 1) + " %" else Fmt.trim(v, 1) + " " + u.weight.label }, Modifier.fillMaxWidth())
                        else Caption("Need at least two days in this range for a graph.")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1M", "3M", "6M", "1Y", "All").forEachIndexed { i, l -> GlassChip(l, range == i, { range = i }) }
                        }
                        if (!showFat) {
                            Spacer(Modifier.height(12.dp))
                            val latest = days.last().second
                            val avg7 = days.filter { !it.first.isBefore(today.minusDays(6)) }.map { it.second.weightKg }.takeIf { it.isNotEmpty() }?.average()
                            val prev7 = days.filter { it.first.isBefore(today.minusDays(6)) && !it.first.isBefore(today.minusDays(13)) }.map { it.second.weightKg }.takeIf { it.isNotEmpty() }?.average()
                            val rate = weeklyRate(inRange.map { it.first to it.second.weightKg })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Stat("Latest", wFmt(latest.weightKg), latest.localDate)
                                avg7?.let { Stat("7-day avg", wFmt(it), prev7?.let { p -> (if (it - p >= 0) "+" else "−") + wFmt(abs(it - p)) + " vs prior week" } ?: "this week") }
                                if (inRange.size >= 2) Stat("Low / high", "${Fmt.trim(wDisp(inRange.minOf { it.second.weightKg }), 1)} / ${Fmt.trim(wDisp(inRange.maxOf { it.second.weightKg }), 1)}", "in this range")
                                rate?.let { Stat("Trend", (if (it >= 0) "+" else "−") + wFmt(abs(it), 2) + "/wk", "estimate from this range") }
                                profile?.targetWeightKg?.let { t -> Stat("To target", wFmt(abs(latest.weightKg - t)), "target ${wFmt(t)}") }
                            }
                            Spacer(Modifier.height(8.dp))
                            Caption("One value per day (your last weigh-in). Trend is a straight-line fit over the range — an estimate, not a prediction.", color = th.textFaint)
                        }
                        Spacer(Modifier.height(10.dp))
                        GlassButton("Log weight", { open(Sheet.Weight()) }, icon = Duo.Add, height = 42.dp)
                    }
                }
            }
            // ---------------- weekly averages
            if (days.size >= 2) item {
                GlassCard {
                    CardHeader(Duo.CalendarMonth, "Weekly averages", th.water)
                    Spacer(Modifier.height(8.dp))
                    val weeks = days.groupBy { it.first.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }
                        .toSortedMap(compareByDescending { it }).entries.take(8).toList()
                    weeks.forEachIndexed { i, (wk, list) ->
                        val avg = list.map { it.second.weightKg }.average()
                        val older = weeks.getOrNull(i + 1)?.value?.map { it.second.weightKg }?.average()
                        val fat = list.mapNotNull { it.second.bodyFatPct }.takeIf { it.isNotEmpty() }?.average()
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Week of ${wk.format(dFmt)}", style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
                            Text(wFmt(avg), style = FitType.label, color = th.text)
                            Text(older?.let { "  " + (if (avg - it >= 0) "+" else "−") + Fmt.trim(abs(wDisp(avg) - wDisp(it)), 1) } ?: "", style = FitType.caption,
                                color = if (older == null) th.textDim else if (avg <= older) th.success else th.warning, modifier = Modifier.width(56.dp))
                            Text(fat?.let { Fmt.trim(it, 1) + "%" } ?: "", style = FitType.caption, color = th.textDim, modifier = Modifier.width(44.dp))
                        }
                    }
                    Caption("${weeks.sumOf { it.value.size }} weigh-in days · body-fat column shows when your watch or scale records it.", color = th.textFaint)
                }
            }
            // ---------------- measurements
            item { MeasurementsCard(measures, open) }
            // ---------------- photos
            item {
                GlassCard {
                    CardHeader(Duo.Image, "Progress photos", th.protein) { Caption("Private") }
                    Spacer(Modifier.height(6.dp))
                    Caption("Stored only on this phone — never uploaded or sent to any AI. Same pose, same light, same time of day works best.")
                    Spacer(Modifier.height(10.dp))
                    if (photos.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photos, key = { it.id }) { p ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    PhotoThumb(container, p, Modifier.size(width = 92.dp, height = 122.dp).clickableNoRipple { viewPhoto = p })
                                    Text(LocalDate.parse(p.localDate).format(dFmt), style = FitType.caption, color = th.textDim)
                                    Text(p.pose.lowercase().replaceFirstChar { it.uppercase() }, style = FitType.caption, color = th.textFaint)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentButton("Add photo", { addPhoto = true }, icon = Duo.Camera, height = 44.dp)
                        if (photos.size >= 2) GlassButton("Compare", {
                            val same = photos.groupBy { it.pose }.values.firstOrNull { it.size >= 2 } ?: photos
                            compare = same.last() to same.first()     // oldest vs newest
                        }, icon = Duo.Images, height = 44.dp)
                    }
                }
            }
        }
    }

    if (addPhoto) AddPhotoSheet(container, days.lastOrNull()?.second?.weightKg) { addPhoto = false }
    compare?.let { (a, b) -> CompareSheet(container, photos, a, b, { compare = null }) }
    viewPhoto?.let { p -> ViewPhotoSheet(container, p, onCompare = { other -> viewPhoto = null; compare = other to p }, onClose = { viewPhoto = null }, all = photos) }
}

@Composable
private fun Stat(label: String, value: String, sub: String) {
    val th = LocalFitTheme.current
    Column {
        Text(label, style = FitType.caption, color = th.textDim)
        Text(value, style = FitType.section, color = th.text)
        Text(sub, style = FitType.caption, color = th.textFaint)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeasurementsCard(all: List<BodyMeasurement>, open: (Sheet) -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val bySite = all.groupBy { if (it.type == MeasurementSite.CUSTOM) "C:" + (it.customName ?: "") else it.type }
    var site by remember { mutableStateOf<String?>(null) }
    GlassCard {
        CardHeader(Duo.Straighten, "Measurements", th.steps)
        Spacer(Modifier.height(8.dp))
        if (bySite.isEmpty()) {
            Caption("No measurements yet. Waist is the most useful one to track alongside weight.")
        } else {
            bySite.forEach { (k, list) ->
                val first = list.first(); val last = list.last()
                val label = MeasurementSite.label(last.type, last.customName)
                val change = last.valueCm - first.valueCm
                Row(Modifier.fillMaxWidth().clickableNoRipple { site = if (site == k) null else k }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
                    Text(Fmt.length(last.valueCm, u.length), style = FitType.section, color = th.text)
                    Text(if (list.size > 1) "  " + (if (change >= 0) "+" else "−") + Fmt.length(abs(change), u.length) else "", style = FitType.caption,
                        color = th.textDim, modifier = Modifier.width(72.dp))
                }
                if (site == k && list.size >= 2) {
                    ProgressChart(list.map { m -> ChartPoint(LocalDate.parse(m.localDate).toEpochDay(), Units.cmTo(m.valueCm, u.length), LocalDate.parse(m.localDate).format(dFmt)) },
                        th.steps, { v -> Fmt.trim(v, 1) + " " + u.length.label }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
            }
            Caption("Change is since your first measurement. Tap a row for its graph.", color = th.textFaint)
        }
        Spacer(Modifier.height(10.dp))
        GlassButton("Add measurement", { open(Sheet.Measurement()) }, icon = Duo.Add, height = 42.dp)
    }
}

// ------------------------------------------------------------------ photos

private fun photoDir(ctx: android.content.Context) = File(ctx.filesDir, "progress").apply { mkdirs() }

private fun loadPhoto(ctx: android.content.Context, name: String, max: Int): Bitmap? = runCatching {
    val f = File(photoDir(ctx), name)
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1; while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
}.getOrNull()

@Composable
private fun rememberPhoto(container: AppContainer, p: ProgressPhoto, max: Int): ImageBitmap? {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, p.fileName, max) { value = withContext(Dispatchers.IO) { loadPhoto(ctx, p.fileName, max)?.asImageBitmap() } }
    return img
}

@Composable
private fun PhotoThumb(container: AppContainer, p: ProgressPhoto, modifier: Modifier) {
    val img = rememberPhoto(container, p, 300)
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Copies a picked/captured image into private storage, downscaled to ≤ 1600 px and orientation-corrected. */
private fun importPhoto(ctx: android.content.Context, uri: Uri): String? = runCatching {
    val bmp: Bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            val m = maxOf(info.size.width, info.size.height)
            if (m > 1600) { val sc = 1600f / m; d.setTargetSize((info.size.width * sc).toInt(), (info.size.height * sc).toInt()) }
            d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }!!
    }
    val name = "p_" + System.currentTimeMillis() + ".jpg"
    File(photoDir(ctx), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    name
}.getOrNull()

@Composable
private fun AddPhotoSheet(container: AppContainer, weightKg: Double?, close: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var pose by remember { mutableStateOf("FRONT") }
    val camFile = remember { File(ctx.cacheDir, "photos").apply { mkdirs() }.let { File(it, "progress_capture.jpg") } }
    val camUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", camFile) }
    fun save(uri: Uri) {
        scope.launch {
            val name = withContext(Dispatchers.IO) { importPhoto(ctx, uri) }
            if (name == null) { toaster.show("Couldn't save that photo"); return@launch }
            val s = com.myfit.tracker.data.repo.Stamp.now()
            container.db.progressPhotoDao().insert(ProgressPhoto(pose = pose, fileName = name, weightKg = weightKg, takenAt = s.at, zoneId = s.zoneId, localDate = s.localDate, createdAt = s.at, updatedAt = s.at))
            toaster.show("Photo saved privately"); close()
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) save(camUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) save(uri) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) camera.launch(camUri) else toaster.show("Camera not allowed — use the gallery") }
    GlassSheet(visible = true, onDismiss = close) {
        Text("Add progress photo", style = FitType.title, color = th.text)
        Spacer(Modifier.height(10.dp))
        Text("Pose", style = FitType.label, color = th.textDim); Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("FRONT" to "Front", "SIDE" to "Side", "BACK" to "Back").forEach { (k, l) -> GlassChip(l, pose == k, { pose = k }) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccentButton("Camera", {
                if (com.myfit.tracker.ui.onboarding.hasPerm(ctx, android.Manifest.permission.CAMERA)) camera.launch(camUri) else camPerm.launch(android.Manifest.permission.CAMERA)
            }, Modifier.weight(1f), icon = Duo.Camera, height = 48.dp)
            GlassButton("Gallery", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), icon = Duo.Images, height = 48.dp)
        }
        Spacer(Modifier.height(8.dp))
        Caption("Saved inside MyFit only. Your latest weight is stored with it for reference.", color = th.textFaint)
    }
}

@Composable
private fun ViewPhotoSheet(container: AppContainer, p: ProgressPhoto, onCompare: (ProgressPhoto) -> Unit, onClose: () -> Unit, all: List<ProgressPhoto>) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    val img = rememberPhoto(container, p, 1400)
    val toaster = LocalToaster.current
    GlassSheet(visible = true, onDismiss = onClose) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(20.dp))) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(8.dp))
        Text(LocalDate.parse(p.localDate).format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.US)), style = FitType.section, color = th.text)
        Caption(p.pose.lowercase().replaceFirstChar { it.uppercase() } + (p.weightKg?.let { " · " + Fmt.weight(it, u.weight, 1) } ?: ""))
        Spacer(Modifier.height(10.dp))
        val others = all.filter { it.id != p.id }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (others.isNotEmpty()) GlassButton("Compare", { onCompare(others.firstOrNull { it.pose == p.pose && it.takenAt < p.takenAt } ?: others.last()) }, icon = Duo.Images, height = 42.dp)
            GlassButton("Delete", {
                container.write { container.db.progressPhotoDao().softDelete(p.id, Clock.now()); runCatching { File(photoDir(container.app), p.fileName).delete() } }
                toaster.show("Photo deleted"); onClose()
            }, icon = Duo.DeleteOutline, height = 42.dp)
        }
    }
}

/** Before/after with a draggable divider. */
@Composable
private fun CompareSheet(container: AppContainer, all: List<ProgressPhoto>, a0: ProgressPhoto, b0: ProgressPhoto, close: () -> Unit) {
    val th = LocalFitTheme.current
    val u = LocalSettings.current.units
    var a by remember { mutableStateOf(a0) }
    var b by remember { mutableStateOf(b0) }
    var split by remember { mutableFloatStateOf(0.5f) }
    val ia = rememberPhoto(container, a, 1400)
    val ib = rememberPhoto(container, b, 1400)
    GlassSheet(visible = true, onDismiss = close) {
        Text("Compare", style = FitType.title, color = th.text)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(20.dp))) {
            val fullW = maxWidth
            val wPx = with(LocalDensity.current) { fullW.toPx() }
            if (ib != null) Image(ib, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxHeight().fillMaxWidth(split.coerceIn(0.02f, 0.98f)).clip(RoundedCornerShape(0.dp))) {
                if (ia != null) Image(ia, null, Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(fullW).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
            Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                detectDragGestures { ch, d -> ch.consume(); split = (split + d.x / wPx).coerceIn(0f, 1f) }
            }) {
                val x = size.width * split
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
                drawCircle(Color.White, 14.dp.toPx(), Offset(x, size.height / 2))
                drawCircle(th.accent, 10.dp.toPx(), Offset(x, size.height / 2))
            }
            Text("Before", style = FitType.caption, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            Text("After", style = FitType.caption, color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        val dA = LocalDate.parse(a.localDate); val dB = LocalDate.parse(b.localDate)
        val daysBetween = abs(java.time.temporal.ChronoUnit.DAYS.between(dA, dB))
        Text("${dA.format(dFmt)} → ${dB.format(dFmt)} · $daysBetween days", style = FitType.section, color = th.text)
        val wa = a.weightKg; val wb = b.weightKg
        if (wa != null && wb != null) {
            val ch = wb - wa
            Caption("Weight then ${Fmt.weight(wa, u.weight, 1)} → ${Fmt.weight(wb, u.weight, 1)} (" + (if (ch >= 0) "+" else "−") + Fmt.weight(abs(ch), u.weight, 1) + ")")
        }
        Spacer(Modifier.height(10.dp))
        Text("Before", style = FitType.label, color = th.textDim)
        PickRow(container, all, a) { a = it }
        Spacer(Modifier.height(6.dp))
        Text("After", style = FitType.label, color = th.textDim)
        PickRow(container, all, b) { b = it }
    }
}

@Composable
private fun PickRow(container: AppContainer, all: List<ProgressPhoto>, sel: ProgressPhoto, onPick: (ProgressPhoto) -> Unit) {
    val th = LocalFitTheme.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        items(all.reversed(), key = { it.id }) { p ->
            PhotoThumb(container, p, Modifier.size(width = 54.dp, height = 72.dp)
                .border(if (p.id == sel.id) 2.dp else 0.dp, if (p.id == sel.id) th.accent else Color.Transparent, RoundedCornerShape(14.dp))
                .clickableNoRipple { onPick(p) })
        }
    }
}
