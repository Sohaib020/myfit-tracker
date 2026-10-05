package com.myfit.tracker.ui.vitals

import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.VitalReading
import com.myfit.tracker.data.db.VitalType
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.ui.components.CardHeader
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.DataBadge
import com.myfit.tracker.ui.components.DataKind
import com.myfit.tracker.ui.components.GlassCard
import com.myfit.tracker.ui.components.LocalToaster
import com.myfit.tracker.ui.components.OverlayTopBar
import com.myfit.tracker.ui.components.ProgressRing
import com.myfit.tracker.ui.nav.LocalNav
import com.myfit.tracker.ui.theme.AccentButton
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.GlassButton
import com.myfit.tracker.ui.theme.GlassIconButton
import com.myfit.tracker.ui.theme.LocalFitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

private const val MEASURE_SEC = 30.0
private const val WARMUP_SEC = 2.0

/** Estimate from brightness and from red chroma; keep whichever signal is cleaner. */
private fun bestEstimate(b: PpgBuffer): Ppg.Result {
    val (t, v) = b.snapshot(); val (t2, r) = b.snapshotRed()
    val a = Ppg.estimate(t, v); val c = Ppg.estimate(t2, r)
    return when {
        a.bpm != null && c.bpm != null -> if (c.quality > a.quality) c else a
        a.bpm != null -> a
        c.bpm != null -> c
        else -> if (c.quality > a.quality) c else a
    }
}

/** Per-frame brightness samples written by the camera thread and read by the UI. */
private class PpgBuffer {
    private val lock = Any()
    private val t = ArrayList<Double>(2048)
    private val v = ArrayList<Double>(2048)
    private val cr = ArrayList<Double>(2048)          // red chroma (V plane): often a cleaner pulse than brightness
    @Volatile var finger: Boolean = false
    private var missFrames = 0

    fun onFrame(timeSec: Double, luma: Double, red: Double, fingerOn: Boolean) {
        synchronized(lock) {
            finger = fingerOn
            if (fingerOn) {
                missFrames = 0
                if (t.isEmpty() || timeSec > t.last()) { t.add(timeSec); v.add(luma); cr.add(red) }
            } else {
                missFrames++
                if (missFrames > 8) { t.clear(); v.clear(); cr.clear() }   // finger lifted → start over
            }
            Unit
        }
    }

    /** All samples (or only the last [lastSec] seconds). */
    fun snapshot(lastSec: Double? = null): Pair<DoubleArray, DoubleArray> = synchronized(lock) {
        if (t.isEmpty()) return@synchronized DoubleArray(0) to DoubleArray(0)
        var from = 0
        if (lastSec != null) {
            val cut = t.last() - lastSec
            while (from < t.size - 1 && t[from] < cut) from++
        }
        t.subList(from, t.size).toDoubleArray() to v.subList(from, v.size).toDoubleArray()
    }

    /** Red-chroma samples for the whole recording. */
    fun snapshotRed(): Pair<DoubleArray, DoubleArray> = synchronized(lock) { t.toDoubleArray() to cr.toDoubleArray() }

    fun duration(): Double = synchronized(lock) { if (t.size < 2) 0.0 else t.last() - t.first() }
}

private data class FrameStats(val y: Double, val u: Double, val v: Double, val spread: Double)

/** Mean Y/U/V of the centre half of the frame plus a coarse brightness spread (uniform = finger covering lens). */
private fun frameStats(p: ImageProxy): FrameStats? = runCatching {
    val w = p.width; val h = p.height
    val x0 = w / 4; val x1 = w * 3 / 4; val y0 = h / 4; val y1 = h * 3 / 4
    val yp = p.planes[0]; val yb = yp.buffer; val yrs = yp.rowStride; val yps = yp.pixelStride
    val g = 3
    val grid = DoubleArray(g * g); val cnt = IntArray(g * g)
    var sum = 0L; var n = 0
    var y = y0
    while (y < y1) {
        var x = x0
        while (x < x1) {
            val idx = y * yrs + x * yps
            if (idx < yb.limit()) {
                val vv = yb.get(idx).toInt() and 0xFF
                sum += vv; n++
                val gi = ((y - y0) * g / (y1 - y0)).coerceIn(0, g - 1) * g + ((x - x0) * g / (x1 - x0)).coerceIn(0, g - 1)
                grid[gi] += vv.toDouble(); cnt[gi]++
            }
            x += 4
        }
        y += 4
    }
    if (n == 0) return@runCatching null
    for (i in grid.indices) if (cnt[i] > 0) grid[i] /= cnt[i]
    var su = 0L; var sv = 0L; var m = 0
    if (p.planes.size >= 3) {
        val up = p.planes[1]; val vp = p.planes[2]
        val ub = up.buffer; val vb = vp.buffer
        var cy = y0 / 2
        while (cy < y1 / 2) {
            var cx = x0 / 2
            while (cx < x1 / 2) {
                val ui = cy * up.rowStride + cx * up.pixelStride
                val vi = cy * vp.rowStride + cx * vp.pixelStride
                if (ui < ub.limit() && vi < vb.limit()) {
                    su += ub.get(ui).toInt() and 0xFF
                    sv += vb.get(vi).toInt() and 0xFF
                    m++
                }
                cx += 4
            }
            cy += 4
        }
    }
    val spread = (grid.maxOrNull() ?: 0.0) - (grid.minOrNull() ?: 0.0)
    FrameStats(sum.toDouble() / n, if (m > 0) su.toDouble() / m else 128.0, if (m > 0) sv.toDouble() / m else 128.0, spread)
}.getOrNull()

/** Finger over lens + flash: bright-ish, strongly red (high Cr, low Cb) and evenly lit. */
private fun fingerOn(s: FrameStats): Boolean = s.y in 20.0..250.0 && s.v > 140.0 && s.v - s.u > 20.0 && s.spread < 45.0

private enum class Phase { IDLE, MEASURING, DONE }

@Composable
internal fun CameraHrContent(container: AppContainer) {
    val th = LocalFitTheme.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var result by remember { mutableStateOf<Ppg.Result?>(null) }
    var resultAt by remember { mutableStateOf(0L) }
    var saved by remember { mutableStateOf(false) }
    val history by remember { container.db.vitalDao().observeByType(VitalType.HR_CAMERA, 30) }.collectAsState(initial = emptyList())
    var hasCam by remember { mutableStateOf(com.myfit.tracker.ui.onboarding.hasPerm(ctx, android.Manifest.permission.CAMERA)) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasCam = ok
        if (ok) { result = null; saved = false; phase = Phase.MEASURING } else toaster.show("Camera not allowed — it's needed to see your pulse")
    }
    fun start() {
        if (hasCam) { result = null; saved = false; phase = Phase.MEASURING } else camPerm.launch(android.Manifest.permission.CAMERA)
    }

    // keep the screen awake for the whole measurement screen (a timeout would stop the camera mid-reading)
    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) { hostView.keepScreenOn = true; onDispose { hostView.keepScreenOn = false } }
    Column(Modifier.fillMaxSize()) {
        OverlayTopBar("Camera heart rate", { nav.pop() }, "Wellness estimate · not a medical device")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                when (phase) {
                    Phase.IDLE -> GlassCard {
                        CardHeader(Duo.Favorite, "Measure your pulse", th.danger)
                        Spacer(Modifier.height(10.dp))
                        Step(1, "Rest your hand on a table and sit still for a moment.")
                        Step(2, "Cover the back camera and the flash with your fingertip — gently, don't press hard.")
                        Step(3, "Stay still while the flash is on — it usually takes 15–30 seconds.")
                        Spacer(Modifier.height(14.dp))
                        AccentButton("Start measuring", { start() }, Modifier.fillMaxWidth(), icon = Duo.PlayArrow, height = 50.dp)
                    }
                    Phase.MEASURING -> MeasureCard(
                        onDone = { r -> result = r; resultAt = Clock.now(); phase = Phase.DONE },
                        onCancel = { phase = Phase.IDLE },
                    )
                    Phase.DONE -> {
                        val r = result
                        val bpm: Int? = r?.bpm
                        val quality: Double = r?.quality ?: 0.0
                        GlassCard {
                            CardHeader(Duo.Favorite, "Result", th.danger) { DataBadge(DataKind.ESTIMATED) }
                            Spacer(Modifier.height(10.dp))
                            if (bpm != null) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("${bpm}", style = FitType.display, color = th.text)
                                    Spacer(Modifier.width(6.dp))
                                    Text("bpm", style = FitType.section, color = th.textDim, modifier = Modifier.padding(bottom = 6.dp))
                                }
                                Caption("Wellness estimate · not a medical device")
                                Spacer(Modifier.height(6.dp))
                                Caption("Signal quality: " + when { quality >= 0.8 -> "good"; quality >= 0.6 -> "fair"; else -> "just enough" })
                                if (bpm >= 120) {
                                    Spacer(Modifier.height(6.dp))
                                    Caption("That's high for resting. If you were just active, rest a few minutes and measure again. If you feel unwell, dizzy or have chest pain, seek medical care.", color = th.warning)
                                } else if (bpm < 45) {
                                    Spacer(Modifier.height(6.dp))
                                    Caption("That's low. Fit people can have a low resting pulse, but if you feel faint or unwell, talk to a doctor.", color = th.warning)
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    AccentButton(if (saved) "Saved" else "Save", {
                                        if (saved) return@AccentButton
                                        val st = Stamp.of(resultAt)
                                        val row = VitalReading(
                                            type = VitalType.HR_CAMERA, value = bpm.toDouble(), source = "CAMERA",
                                            takenAt = st.at, zoneId = st.zoneId, localDate = st.localDate, createdAt = Clock.now(),
                                        )
                                        container.write { container.db.vitalDao().insert(row) }
                                        saved = true
                                        toaster.show("Saved ${bpm} bpm")
                                    }, Modifier.weight(1f), icon = Duo.Save, enabled = !saved, height = 48.dp)
                                    GlassButton("Again", { start() }, Modifier.weight(1f), icon = Duo.Replay, height = 48.dp)
                                }
                            } else {
                                Text("No reliable reading", style = FitType.title, color = th.text)
                                Spacer(Modifier.height(6.dp))
                                Caption(r?.reason ?: "Something went wrong. Please try again.")
                                Spacer(Modifier.height(14.dp))
                                AccentButton("Try again", { start() }, Modifier.fillMaxWidth(), icon = Duo.Replay, height = 48.dp)
                            }
                        }
                    }
                }
            }
            item {
                Caption(
                    "How it works: each heartbeat pushes blood into your fingertip, which slightly changes how much flash light gets through. " +
                        "The camera picks up that tiny flicker. It's a rough estimate — movement, cold fingers, pressing too hard or a weak flash can throw it off. " +
                        "It can't detect heart rhythm problems. Not a medical device.",
                    Modifier.padding(horizontal = 6.dp), color = th.textFaint,
                )
            }
            if (history.isNotEmpty()) {
                item { Text("Your camera readings", style = FitType.section, color = th.text, modifier = Modifier.padding(start = 6.dp, top = 6.dp)) }
                items(history, key = { it.id }) { h ->
                    GlassCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Duo.Favorite, null, tint = th.danger, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${h.value.toInt()} bpm", style = FitType.section, color = th.text)
                                Caption(fmtWhenMs(h.takenAt) + " · camera estimate")
                            }
                            GlassIconButton(Duo.DeleteOutline, {
                                container.write { container.db.vitalDao().softDelete(h.id, Clock.now()) }
                                toaster.show("Reading deleted")
                            }, size = 34.dp, tint = th.textDim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val th = LocalFitTheme.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(th.danger.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text("$n", style = FitType.label, color = th.danger)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = FitType.body, color = th.text, modifier = Modifier.weight(1f))
    }
}

/** Camera + torch + analysis while measuring; everything is released when this leaves composition. */
@Composable
private fun MeasureCard(onDone: (Ppg.Result) -> Unit, onCancel: () -> Unit) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val buffer = remember { PpgBuffer() }
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val exec = remember { Executors.newSingleThreadExecutor() }
    val preview = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
            .build()
    }
    var cameraError by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var finger by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(true) }
    var liveBpm by remember { mutableStateOf<Int?>(null) }
    var wave by remember { mutableStateOf(FloatArray(0)) }
    val beat = remember { Animatable(1f) }

    DisposableEffect(Unit) {
        analysis.setAnalyzer(exec) { proxy ->
            try {
                val s = frameStats(proxy)
                if (s != null) buffer.onFrame(proxy.imageInfo.timestamp / 1e9, s.y, s.v, fingerOn(s))
            } finally {
                proxy.close()
            }
        }
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var cam: Camera? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get().also { provider = it }
                val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                p.unbindAll()
                cam = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, analysis).also { c ->
                    if (c.cameraInfo.hasFlashUnit()) c.cameraControl.enableTorch(true)
                }
            }.onFailure { cameraError = true }
        }, main)
        onDispose {
            disposed = true
            runCatching { cam?.cameraControl?.enableTorch(false) }
            analysis.clearAnalyzer()
            runCatching { provider?.unbindAll() }
            exec.shutdown()
        }
    }

    // UI loop: only runs while measuring (stops when this card leaves the screen)
    LaunchedEffect(Unit) {
        var lastBeat = 0.0
        var tick = 0
        var early: Int? = null
        while (true) {
            delay(50)
            tick++
            finger = buffer.finger
            val dur = buffer.duration()
            settling = dur < WARMUP_SEC
            progress = ((dur - WARMUP_SEC) / MEASURE_SEC).toFloat().coerceIn(0f, 1f)
            if (dur >= WARMUP_SEC + MEASURE_SEC) {
                onDone(withContext(Dispatchers.Default) { bestEstimate(buffer) })
                break
            }
            // finish early once the pulse is clearly steady (usually 15–20 s)
            if (dur >= WARMUP_SEC + 15.0 && tick % 20 == 0) {
                val r = withContext(Dispatchers.Default) { bestEstimate(buffer) }
                val b = r.bpm
                if (b != null && r.quality >= 0.8) {
                    if (early != null && kotlin.math.abs(early!! - b) <= 3) { onDone(r); break }
                    early = b
                } else early = null
            }
            if (dur >= 3.0) {
                // waveform + beat detection over the last 5 s
                val (t, v) = buffer.snapshot(6.0)
                val f = withContext(Dispatchers.Default) { Ppg.filter(Ppg.resample(t, v)) }
                if (f.isNotEmpty() && t.isNotEmpty()) {
                    val show = f.copyOfRange(max(0, f.size - (Ppg.FS * 5).toInt()), f.size)
                    wave = FloatArray(show.size) { show[it].toFloat() }
                    val pk = Ppg.peaks(f)
                    val lastPk = pk.lastOrNull()
                    if (lastPk != null) {
                        val pkTime = t.first() + lastPk / Ppg.FS
                        val endTime = t.first() + f.size / Ppg.FS
                        if (pkTime > lastBeat + 0.3 && endTime - pkTime < 1.0) {
                            lastBeat = pkTime
                            scope.launch { beat.snapTo(1.22f); beat.animateTo(1f, tween(320)) }
                        }
                    }
                }
                if (tick % 20 == 0 && dur >= 10.0) {
                    val (t2, v2) = buffer.snapshot(10.0)
                    liveBpm = withContext(Dispatchers.Default) { Ppg.liveBpm(t2, v2) }
                }
            } else {
                wave = FloatArray(0); liveBpm = null
            }
        }
    }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress, th.danger, size = 150.dp, stroke = 10.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Duo.Favorite, null, tint = th.danger, modifier = Modifier.size(44.dp).scale(beat.value))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        liveBpm?.let { "~$it" } ?: "—", style = FitType.title,
                        color = if (liveBpm != null) th.text else th.textFaint,
                    )
                    Text("bpm", style = FitType.caption, color = th.textDim)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).border(2.dp, if (finger) th.success else th.textFaint, CircleShape),
                ) { AndroidView({ preview }, Modifier.fillMaxSize()) }
                Spacer(Modifier.height(6.dp))
                Caption(if (finger) "Finger detected" else "No finger yet", color = if (finger) th.success else th.textDim)
            }
        }
        Spacer(Modifier.height(12.dp))
        val status = when {
            cameraError -> "Couldn't open the camera. Close other camera apps and try again."
            !finger -> "Cover the back camera and flash with your fingertip, stay still"
            settling -> "Finding your pulse…"
            progress < 1f -> "Measuring… stay still · ${(MEASURE_SEC * (1f - progress)).toInt()} s left"
            else -> "Working it out…"
        }
        Text(status, style = FitType.body, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Waveform(wave, th.danger, Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(12.dp))
        GlassButton("Cancel", onCancel, Modifier.fillMaxWidth(), icon = Duo.Close, height = 44.dp)
    }
}

@Composable
private fun Waveform(values: FloatArray, color: Color, modifier: Modifier) {
    val th = LocalFitTheme.current
    val track = if (th.isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f)
    Canvas(modifier) {
        drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        if (values.size < 2) return@Canvas
        var lo = values[0]; var hi = values[0]
        for (x in values) { lo = min(lo, x); hi = max(hi, x) }
        val range = (hi - lo).takeIf { it > 1e-6f } ?: 1f
        val step = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height * (1f - (v - lo) / range) * 0.85f + size.height * 0.075f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
