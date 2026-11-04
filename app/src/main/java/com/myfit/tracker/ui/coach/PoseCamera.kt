package com.myfit.tracker.ui.coach

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myfit.tracker.domain.FormGuide
import com.myfit.tracker.ui.theme.FitType
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2

private val BONES = listOf(
    11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28,
    27 to 31, 28 to 32, 27 to 29, 28 to 30, 29 to 31, 30 to 32, 15 to 17, 15 to 19, 17 to 19, 16 to 18, 16 to 20, 18 to 20,
    0 to 2, 0 to 5, 2 to 7, 5 to 8, 9 to 10,
)

/** One analysed frame: 33 body points (x, y, visibility) in upright image pixels + head direction from body points. */
private class Frame(val pts: FloatArray, val w: Int, val h: Int, val head: FormGuide.Head?)

/**
 * R29 body tracker: Google ML Kit pose detection (base model, stream mode) — the smooth tracker the camera coach used
 * before R17, back by the owner's choice. ML Kit reads the camera frame directly (no bitmap copies), returns the same
 * 33 body points (in the upright image) and runs well on old phones. Head direction is worked out from the face points.
 */
private class MlKitTracker {
    val label = "ML Kit"
    private val detector = com.google.mlkit.vision.pose.PoseDetection.getClient(
        com.google.mlkit.vision.pose.defaults.PoseDetectorOptions.Builder()
            .setDetectorMode(com.google.mlkit.vision.pose.defaults.PoseDetectorOptions.STREAM_MODE).build())
    private var lastHead: FormGuide.Head? = null

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    fun analyse(proxy: ImageProxy): Frame? {
        val media = proxy.image ?: return null
        val rot = proxy.imageInfo.rotationDegrees
        val w = if (rot % 180 == 0) proxy.width else proxy.height
        val h = if (rot % 180 == 0) proxy.height else proxy.width
        val pose = com.google.android.gms.tasks.Tasks.await(detector.process(com.google.mlkit.vision.common.InputImage.fromMediaImage(media, rot)))
        val lms = pose.allPoseLandmarks
        if (lms.size < 33) {
            lastHead = null
            return null
        }
        val pts = FloatArray(33 * 3)
        for (l in lms) {
            val i = l.landmarkType
            if (i !in 0..32) continue
            pts[i * 3] = l.position.x
            pts[i * 3 + 1] = l.position.y
            pts[i * 3 + 2] = l.inFrameLikelihood
        }
        return Frame(pts, w, h, head(pts))
    }

    /** Head yaw / pitch / roll (degrees) from nose (0), eyes (2, 5) and ears (7, 8), smoothed. */
    private fun head(p: FloatArray): FormGuide.Head? {
        val nose = 0; val le = 2; val re = 5; val lear = 7; val rear = 8
        val vis = { i: Int -> p[i * 3 + 2] > 0.5f }
        val px = { i: Int -> p[i * 3] }
        val py = { i: Int -> p[i * 3 + 1] }
        if (!vis(nose) || !vis(le) || !vis(re)) {
            lastHead = null
            return null
        }
        val eyeD = kotlin.math.hypot(px(le) - px(re), py(le) - py(re)).coerceAtLeast(1f)
        val yaw = ((px(nose) - (px(le) + px(re)) / 2f) / eyeD) * 70f
        val earY = when {
            vis(lear) && vis(rear) -> (py(lear) + py(rear)) / 2f
            vis(lear) -> py(lear)
            vis(rear) -> py(rear)
            else -> (py(le) + py(re)) / 2f + 0.15f * eyeD
        }
        val pitch = ((earY - py(nose)) / eyeD + 0.3f) * 60f
        val roll = Math.toDegrees(atan2((py(le) - py(re)).toDouble(), (px(le) - px(re)).toDouble())).toFloat()
        val prev = lastHead
        val next = if (prev == null) FormGuide.Head(yaw, pitch, roll)
        else FormGuide.Head(prev.yaw + 0.35f * (yaw - prev.yaw), prev.pitch + 0.35f * (pitch - prev.pitch), prev.roll + 0.35f * (roll - prev.roll))
        lastHead = next
        return next
    }

    fun close() { runCatching { detector.close() } }
}

/**
 * Live camera with on-device body + head tracking. Frames are analysed on the phone and never stored or uploaded.
 * Draws the skeleton (body, hands, feet), the head outline and a head-direction readout; [highlight] joints glow in [accent].
 */
@Composable
fun PoseCamera(front: Boolean, accent: Color, highlight: Set<Int>, onPose: (FormGuide.Pose, Long) -> Unit, modifier: Modifier = Modifier) {
    val perf = com.myfit.tracker.ui.theme.UiPerf.camera
    DisposableEffect(Unit) { perf.value = true; onDispose { perf.value = false } }
    PoseCameraLive(front, accent, highlight, onPose, modifier)
}

@Composable
private fun PoseCameraLive(front: Boolean, accent: Color, highlight: Set<Int>, onPose: (FormGuide.Pose, Long) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val preview = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }   // TextureView keeps the rounded clip
    var frame by remember { mutableStateOf<Frame?>(null) }
    var engine by remember { mutableStateOf("") }
    val cb by rememberUpdatedState(onPose)

    var fps by remember { mutableStateOf(0) }
    DisposableEffect(front) {
        val exec = Executors.newSingleThreadExecutor()
        var tracker: MlKitTracker? = null
        var lastTs = 0L
        var lastRun = 0L
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        var shown = 0
        var fpsT0 = System.currentTimeMillis()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .build()
        analysis.setAnalyzer(exec) { proxy ->
            try {
                val now = System.currentTimeMillis()
                val hot = (pm?.currentThermalStatus ?: 0) >= android.os.PowerManager.THERMAL_STATUS_SEVERE
                // ~30 fps, ~15 fps when the phone is hot
                if (now - lastRun < (if (hot) 66L else 30L)) return@setAnalyzer
                lastRun = now
                val t = tracker ?: MlKitTracker().also { tracker = it; val l = it.label; main.execute { engine = l } }
                val ts = maxOf(now, lastTs + 1).also { lastTs = it }
                val f = runCatching { t.analyse(proxy) }.getOrNull()
                shown++
                val fpsNow = if (now - fpsT0 >= 1000) { val v = shown; shown = 0; fpsT0 = now; v } else -1
                main.execute {
                    frame = f
                    if (fpsNow >= 0) fps = fpsNow
                    if (f != null) cb(FormGuide.Pose(f.pts, f.w, f.h, f.head), ts)
                }
            } finally { proxy.close() }
        }
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get().also { provider = it }
                val pv = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                p.unbindAll()
                val sel = if (front && p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                p.bindToLifecycle(owner, sel, pv, analysis)
            }
        }, main)
        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            runCatching { provider?.unbindAll() }
            exec.execute { tracker?.close(); tracker = null }
            exec.shutdown()
        }
    }

    Box(modifier) {
        AndroidView({ preview }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val f = frame ?: return@Canvas
            val pts = f.pts
            // FILL_CENTER mapping (preview is mirrored for the front camera)
            val scale = maxOf(size.width / f.w, size.height / f.h)
            val dx = (size.width - f.w * scale) / 2f; val dy = (size.height - f.h * scale) / 2f
            fun map(x: Float, y: Float): Offset { val sx = x * scale + dx; return Offset(if (front) size.width - sx else sx, y * scale + dy) }
            fun pt(i: Int) = map(pts[i * 3], pts[i * 3 + 1])
            fun ok(i: Int) = pts[i * 3 + 2] > 0.5f
            BONES.forEach { (a, b) ->
                if (ok(a) && ok(b)) {
                    val hot = a in highlight || b in highlight
                    val thin = a < 11 || a in 15..22 || a in 27..32 && b in 27..32
                    drawLine(if (hot) accent else Color.White.copy(alpha = 0.75f), pt(a), pt(b), strokeWidth = if (hot) 9f else if (thin) 4f else 6f, cap = StrokeCap.Round)
                }
            }
            // R29: every point the tracker sees right now, as a glowing dot (brighter = more certain)
            (0..32).forEach { i ->
                val v = pts[i * 3 + 2]
                if (v > 0.5f) {
                    val hot = i in highlight
                    val r = if (hot) 11f else if (i <= 10 || i in 17..22 || i > 28) 6f else 8f
                    drawCircle((if (hot) accent else Color(0xFF7CF7D4)).copy(alpha = 0.35f * v), r * 2.1f, pt(i))
                    drawCircle(if (hot) accent else Color.White, r, pt(i))
                }
            }
            // head: a circle from the body's face points, plus the direction the face points
            if (ok(0) && ok(7) && ok(8)) {
                val r = kotlin.math.hypot(pt(7).x - pt(8).x, pt(7).y - pt(8).y) * 0.75f
                drawCircle(accent.copy(alpha = 0.85f), r, pt(0), style = Stroke(4f))
            }
            val hd = f.head
            if (hd != null && ok(0)) {
                val n = pt(0)
                val yawPx = (if (front) -hd.yaw else hd.yaw) / 90f * 70f
                drawLine(accent, n, Offset(n.x - yawPx, n.y - hd.pitch / 90f * 70f), 5f, cap = StrokeCap.Round)
            }
        }
        // small status chip: tracking engine + head direction
        val f = frame
        val hd = f?.head
        if (engine.isNotEmpty()) Text(
            (if (f == null) "Looking for you… · " else if (hd != null) "Head ${hd.describe()} · " else "") + "$engine · $fps fps",
            style = FitType.overline, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
