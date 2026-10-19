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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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

// R28: ~15 s check. Finishes as soon as two readings in a row agree (usually 10–15 s), never longer than 22 s.
private const val TARGET_SEC = 13.5
private const val MEASURE_SEC = 20.0
private const val WARMUP_SEC = 1.5

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
                        Step(3, "Stay still while the flash is on — it takes about 15 seconds.")
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
    var beats by remember { androidx.compose.runtime.mutableIntStateOf(0) }

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
            // the ring fills over the usual ~15 s (eases out near the end if the pulse needs longer), so the wait feels short
            val x = ((dur - WARMUP_SEC) / TARGET_SEC).toFloat().coerceAtLeast(0f)
            progress = if (x <= 0.85f) x else (0.85f + 0.13f * (1f - kotlin.math.exp(-(x - 0.85f) * 3f))).coerceAtMost(0.98f)
            if (dur >= WARMUP_SEC + MEASURE_SEC) {
                progress = 1f
                onDone(withContext(Dispatchers.Default) { bestEstimate(buffer) })
                break
            }
            // finish early once the pulse is clearly steady (usually 10–15 s)
            if (dur >= WARMUP_SEC + 8.5 && tick % 10 == 0) {
                val r = withContext(Dispatchers.Default) { bestEstimate(buffer) }
                val b = r.bpm
                if (b != null && r.quality >= 0.75) {
                    if (early != null && kotlin.math.abs(early!! - b) <= 3) { progress = 1f; delay(250); onDone(r); break }
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
                            beats++
                            scope.launch { beat.snapTo(1.22f); beat.animateTo(1f, tween(320)) }
                        }
                    }
                }
                if (tick % 10 == 0 && dur >= 4.5) {
                    val (t2, v2) = buffer.snapshot(8.0)
                    liveBpm = withContext(Dispatchers.Default) { Ppg.liveBpm(t2, v2) }
                }
            } else {
                wave = FloatArray(0); liveBpm = null
            }
        }
    }

    GlassCard {
        // R33: a living orb that pulses on each real beat, with light shockwaves, drifting particles and a tick progress ring
        val shown by androidx.compose.animation.core.animateFloatAsState(progress, androidx.compose.animation.core.tween(240, easing = androidx.compose.animation.core.LinearEasing), label = "ring")
        val bpmShown by androidx.compose.animation.core.animateIntAsState(liveBpm ?: 0, androidx.compose.animation.core.tween(700), label = "bpm")
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            LivingOrb(beats, finger && !cameraError, shown, th.danger, Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Duo.Favorite, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp).scale(beat.value))
                Text(if (liveBpm != null) "$bpmShown" else "—", style = FitType.metric.copy(fontSize = FitType.metric.fontSize * 1.25f), color = Color.White)
                Text(if (liveBpm != null) "bpm · estimate" else if (finger) "reading" else "bpm", style = FitType.caption, color = Color.White.copy(alpha = 0.8f))
            }
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(50.dp).clip(CircleShape).border(2.dp, if (finger) th.success else th.textFaint, CircleShape)) { AndroidView({ preview }, Modifier.fillMaxSize()) }
                Spacer(Modifier.height(3.dp))
                Caption(if (finger) "Finger on" else "No finger", color = if (finger) th.success else th.textDim)
            }
        }
        Spacer(Modifier.height(12.dp))
        val status = when {
            cameraError -> "Couldn't open the camera. Close other camera apps and try again."
            !finger -> "Cover the back camera and flash with your fingertip, stay still"
            settling -> "Finding your pulse…"
            progress < 1f -> "Measuring… stay still · about ${(TARGET_SEC * (1f - progress)).toInt().coerceAtLeast(1)} s"
            else -> "Working it out…"
        }
        Text(status, style = FitType.body, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Waveform(wave, th.danger, Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(12.dp))
        GlassButton("Cancel", onCancel, Modifier.fillMaxWidth(), icon = Duo.Close, height = 44.dp)
    }
}

/** R33 live ECG strip: faint paper grid, smoothed trace that fades in from the left, glow underlay and a bright leading dot. */
@Composable
private fun Waveform(values: FloatArray, color: Color, modifier: Modifier) {
    val th = LocalFitTheme.current
    val grid = if (th.isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.06f)
    Canvas(modifier.background(color.copy(alpha = 0.05f))) {
        val g = 14.dp.toPx()
        var gx = 0f; while (gx < size.width) { drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), 1f); gx += g }
        var gy = 0f; while (gy < size.height) { drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f); gy += g }
        if (values.size < 3) {
            drawLine(color.copy(alpha = 0.35f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx())
            return@Canvas
        }
        var lo = values[0]; var hi = values[0]
        for (x in values) { lo = min(lo, x); hi = max(hi, x) }
        val range = (hi - lo).takeIf { it > 1e-6f } ?: 1f
        val step = size.width / (values.size - 1)
        fun pt(i: Int) = Offset(i * step, size.height * (1f - (values[i] - lo) / range) * 0.8f + size.height * 0.1f)
        val path = Path()
        var prev = pt(0); path.moveTo(prev.x, prev.y)
        for (i in 1 until values.size) {
            val p = pt(i); val mid = Offset((prev.x + p.x) / 2, (prev.y + p.y) / 2)
            path.quadraticBezierTo(prev.x, prev.y, mid.x, mid.y); prev = p
        }
        path.lineTo(prev.x, prev.y)
        val brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.6f), color), 0f, size.width)
        drawPath(path, androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.22f)), 0f, size.width),
            style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, brush, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White, color.copy(alpha = 0.5f), Color.Transparent), prev, 9.dp.toPx()), 9.dp.toPx(), prev)
        drawCircle(Color.White, 2.5.dp.toPx(), prev)
    }
}

/**
 * R33 measuring visual — a "living orb". The blob's edge breathes with layered sine harmonics, it kicks and
 * sends a light shockwave on every real detected beat, particles drift out of it, and 60 ticks around it fill
 * as the measurement progresses. Driven by one frame clock (no per-ripple coroutines), so it never stutters.
 */
@Composable
private fun LivingOrb(beats: Int, active: Boolean, progress: Float, color: Color, modifier: Modifier) {
    var now by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { now = (it - start) / 1e9f }
    }
    val beatAt = remember { FloatArray(4) { -10f } }
    var bi by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(beats) { if (beats > 0) { beatAt[bi % 4] = now; bi++ } }
    val light = androidx.compose.ui.graphics.lerp(color, Color.White, 0.45f)
    val deep = androidx.compose.ui.graphics.lerp(color, Color.Black, 0.55f)
    val warm = androidx.compose.ui.graphics.lerp(color, Color(0xFFFF8A3D), 0.45f)
    Canvas(modifier) {
        val t = now
        val c = center
        val maxR = size.minDimension / 2f
        val baseR = maxR * 0.47f
        // beat envelope: quick rise then exponential fall; gentle synthetic breathing until real beats arrive
        val last = beatAt.max()
        val k = t - last
        val pulse = when {
            k in 0f..0.07f -> k / 0.07f
            k in 0.07f..2f -> kotlin.math.exp(-(k - 0.07f) * 5.5f)
            else -> 0f
        }
        val idle = if (last < 0f) (0.5f + 0.5f * kotlin.math.sin(t * (if (active) 3.4f else 1.6f))) * (if (active) 0.35f else 0.18f) else 0f
        val amp = maxOf(pulse, idle)
        val R = baseR * (1f + 0.085f * amp)
        // outer glow
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(color.copy(alpha = 0.38f + 0.2f * amp), color.copy(alpha = 0.10f), Color.Transparent), c, R * 1.9f), R * 1.9f, c)
        // shockwaves from recent beats
        for (b in beatAt) {
            val d = t - b
            if (d < 0f || d > 1.3f) continue
            val q = d / 1.3f
            val f = (1f - q) * (1f - q)
            val rr = R * (1.02f + 0.95f * androidx.compose.animation.core.FastOutSlowInEasing.transform(q))
            drawCircle(light.copy(alpha = 0.55f * f), rr, c, style = Stroke((5f * (1f - q) + 1f) * density))
            drawCircle(color.copy(alpha = 0.12f * f), rr, c)
        }
        // particles drifting outward, faster right after a beat
        val n = 30
        for (i in 0 until n) {
            val seed = (i * 7919 % 1000) / 1000f
            val ang = (i * 2.399963f) + t * (0.12f + seed * 0.18f) * (if (i % 2 == 0) 1f else -1f)
            val life = ((t * (0.16f + seed * 0.14f) + seed + pulse * 0.02f) % 1f)
            val rr = R * (1.08f + life * 0.95f)
            val a = (kotlin.math.sin(life * Math.PI.toFloat())) * (0.55f + 0.45f * amp)
            val p = Offset(c.x + rr * kotlin.math.cos(ang), c.y + rr * kotlin.math.sin(ang))
            drawCircle((if (i % 3 == 0) light else warm).copy(alpha = a * 0.8f), (1.2f + seed * 2.2f) * density, p)
        }
        // the orb: organic blob, glow layer + body + specular highlight
        fun blob(scale: Float): Path {
            val path = Path(); val m = 120
            for (j in 0..m) {
                val th = (j.toFloat() / m) * 2f * Math.PI.toFloat()
                val wob = 0.032f * kotlin.math.sin(3 * th + t * 1.3f) + 0.024f * kotlin.math.sin(5 * th - t * 1.9f) +
                    0.018f * kotlin.math.sin(2 * th + t * 0.8f) + 0.04f * amp * kotlin.math.sin(4 * th + t * 4f)
                val r = R * scale * (1f + wob)
                val x = c.x + r * kotlin.math.cos(th); val y = c.y + r * kotlin.math.sin(th)
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close(); return path
        }
        drawPath(blob(1.12f), color.copy(alpha = 0.22f))
        val hl = Offset(c.x - R * 0.35f, c.y - R * 0.4f)
        drawPath(blob(1f), androidx.compose.ui.graphics.Brush.radialGradient(listOf(light, color, deep), hl, R * 1.7f))
        drawPath(blob(1f), androidx.compose.ui.graphics.Brush.radialGradient(listOf(warm.copy(alpha = 0.55f), Color.Transparent), Offset(c.x + R * 0.45f, c.y + R * 0.55f), R * 0.9f))
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), hl, R * 0.55f), R * 0.55f, hl)
        // 60-tick progress ring
        val ticks = 60
        val tr0 = maxR * 0.86f; val tr1 = maxR * 0.95f
        val lit = progress * ticks
        for (i in 0 until ticks) {
            val ang = (-90f + i * 6f) * (Math.PI.toFloat() / 180f)
            val on = i < lit
            val head = on && i >= lit - 1.5f
            val col = when { head -> Color.White; on -> androidx.compose.ui.graphics.lerp(color, warm, i / ticks.toFloat()); else -> color.copy(alpha = 0.16f) }
            val ext = if (head) 4f * density else 0f
            drawLine(col, Offset(c.x + tr0 * kotlin.math.cos(ang), c.y + tr0 * kotlin.math.sin(ang)),
                Offset(c.x + (tr1 + ext) * kotlin.math.cos(ang), c.y + (tr1 + ext) * kotlin.math.sin(ang)), (if (on) 3f else 2f) * density, cap = StrokeCap.Round)
        }
        if (progress <= 0f) {
            // waiting: a soft comet orbits the ring
            val ang = (t * 1.6f) % (2f * Math.PI.toFloat())
            val p = Offset(c.x + (tr0 + tr1) / 2 * kotlin.math.cos(ang), c.y + (tr0 + tr1) / 2 * kotlin.math.sin(ang))
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White, color.copy(alpha = 0.4f), Color.Transparent), p, 8f * density), 8f * density, p)
        }
    }
}
