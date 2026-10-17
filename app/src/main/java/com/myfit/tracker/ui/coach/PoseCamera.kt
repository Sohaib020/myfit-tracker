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
 * R27 body tracker: MediaPipe Pose Landmarker **Full** (strong phones) or **Lite** (others), bundled in the APK, on the
 * CPU (XNNPACK) so it never competes with the screen for the GPU — the R17 Heavy + Face models on the GPU could
 * overload it badly enough to restart the phone's UI. Head direction now comes from the body's own face points
 * (nose, eyes, ears), so no second model runs. Frames are analysed in place (reused bitmaps, no per-frame copies).
 */
private class MediaPipeTracker(ctx: Context, val model: PoseModels.Model) {
    val label = "MediaPipe ${model.label}"
    private val pose = com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.createFromOptions(ctx,
        com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(com.google.mediapipe.tasks.core.BaseOptions.builder().setModelAssetPath(model.asset)
                .setDelegate(com.google.mediapipe.tasks.core.Delegate.CPU).build())
            .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.VIDEO)
            .setNumPoses(1).setMinPoseDetectionConfidence(0.5f).setMinPosePresenceConfidence(0.5f).setMinTrackingConfidence(0.6f)
            .build())
    private var lastHead: FormGuide.Head? = null

    fun analyse(bmp: Bitmap, w: Int, h: Int, tsMs: Long): Frame? {
        val r = pose.detectForVideo(com.google.mediapipe.framework.image.BitmapImageBuilder(bmp).build(), tsMs)
        val lm = r.landmarks().firstOrNull()
        if (lm == null || lm.size < 33) { lastHead = null; return null }
        val bw = bmp.width; val bh = bmp.height
        val pts = FloatArray(33 * 3)
        for (i in 0 until 33) {
            val l = lm[i]
            val x = l.x() * bw; val y = l.y() * bh
            pts[i * 3] = x; pts[i * 3 + 1] = y
            pts[i * 3 + 2] = l.visibility().orElse(0f).let { v -> if (x in -0.05f * w..1.05f * w && y in -0.05f * h..1.05f * h) v else 0f }
        }
        return Frame(pts, w, h, head(pts))
    }

    /** Head yaw / pitch / roll (degrees) from nose (0), eyes (2, 5) and ears (7, 8), smoothed. */
    private fun head(p: FloatArray): FormGuide.Head? {
        fun ok(i: Int) = p[i * 3 + 2] > 0.5f
        fun x(i: Int) = p[i * 3]; fun y(i: Int) = p[i * 3 + 1]
        if (!ok(0) || !ok(2) || !ok(5)) { lastHead = null; return null }
        val eyeD = kotlin.math.hypot(x(2) - x(5), y(2) - y(5)).coerceAtLeast(1f)
        val yaw = ((x(0) - (x(2) + x(5)) / 2) / eyeD) * 70f
        val earY = when { ok(7) && ok(8) -> (y(7) + y(8)) / 2; ok(7) -> y(7); ok(8) -> y(8); else -> (y(2) + y(5)) / 2 + 0.15f * eyeD }
        val pitch = ((earY - y(0)) / eyeD + 0.3f) * 60f
        val roll = Math.toDegrees(atan2((y(2) - y(5)).toDouble(), (x(2) - x(5)).toDouble())).toFloat()
        val prev = lastHead
        return (if (prev == null) FormGuide.Head(yaw, pitch, roll)
        else FormGuide.Head(prev.yaw + 0.35f * (yaw - prev.yaw), prev.pitch + 0.35f * (pitch - prev.pitch), prev.roll + 0.35f * (roll - prev.roll)))
            .also { lastHead = it }
    }

    fun close() { runCatching { pose.close() } }
}

/** Reused buffers: the camera's RGBA frame and its upright copy (rotated with a matrix, no new bitmaps per frame). */
private class FrameBuffers {
    private var src: Bitmap? = null
    private var up: Bitmap? = null
    private var canvas: android.graphics.Canvas? = null
    private val m = Matrix()
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

    /** Returns the upright bitmap plus the real (unpadded) upright width/height. */
    fun upright(proxy: ImageProxy): Triple<Bitmap, Int, Int> {
        val plane = proxy.planes[0]
        val rowPx = plane.rowStride / plane.pixelStride
        val w = proxy.width; val h = proxy.height
        val s = src?.takeIf { it.width == rowPx && it.height == h } ?: Bitmap.createBitmap(rowPx, h, Bitmap.Config.ARGB_8888).also { src?.recycle(); src = it }
        plane.buffer.rewind()
        if (plane.buffer.remaining() >= plane.rowStride * h) s.copyPixelsFromBuffer(plane.buffer)
        else {   // a few devices trim the last row's padding: fall back to CameraX's own conversion (rare)
            val b = proxy.toBitmap(); android.graphics.Canvas(s).drawBitmap(b, 0f, 0f, null); b.recycle()
        }
        val rot = proxy.imageInfo.rotationDegrees
        if (rot == 0) return Triple(s, w, h)
        val uw = if (rot % 180 == 0) w else h; val uh = if (rot % 180 == 0) h else w
        val u = up?.takeIf { it.width == uw && it.height == uh } ?: Bitmap.createBitmap(uw, uh, Bitmap.Config.ARGB_8888).also { up?.recycle(); up = it; canvas = android.graphics.Canvas(it) }
        m.reset(); m.postRotate(rot.toFloat())
        when (rot) { 90 -> m.postTranslate(h.toFloat(), 0f); 180 -> m.postTranslate(w.toFloat(), h.toFloat()); 270 -> m.postTranslate(0f, w.toFloat()) }
        canvas!!.drawBitmap(s, m, paint)
        return Triple(u, uw, uh)
    }

    fun release() { src?.recycle(); up?.recycle(); src = null; up = null }
}

/**
 * Live camera with on-device body + head tracking. Frames are analysed on the phone and never stored or uploaded.
 * Draws the skeleton (body, hands, feet), the head outline and a head-direction readout; [highlight] joints glow in [accent].
 */
@Composable
fun PoseCamera(front: Boolean, accent: Color, highlight: Set<Int>, onPose: (FormGuide.Pose, Long) -> Unit, modifier: Modifier = Modifier) {
    DisposableEffect(Unit) { com.myfit.tracker.ui.theme.UiPerf.camera.value = true; onDispose { com.myfit.tracker.ui.theme.UiPerf.camera.value = false } }
    PoseCameraLive(front, accent, highlight, onPose, modifier)
}

@Composable
private fun PoseCameraLive(front: Boolean, accent: Color, highlight: Set<Int>, onPose: (FormGuide.Pose, Long) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val preview = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE   // TextureView: keeps the rounded clip; 30 fps preview } }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var engine by remember { mutableStateOf("") }
    val cb by rememberUpdatedState(onPose)

    var fps by remember { mutableStateOf(0) }
    DisposableEffect(front) {
        val exec = Executors.newSingleThreadExecutor()
        var tracker: MediaPipeTracker? = null
        var failed = false
        var lastTs = 0L
        var lastRun = 0L
        val bufs = FrameBuffers()
        PoseModels.cleanupOld(ctx)
        var model = PoseModels.pick(ctx)
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        // adaptive: average inference time; Full -> Lite if the phone can't keep ~24 fps or gets hot
        var avgMs = 0f; var samples = 0
        var shown = 0; var fpsT0 = System.currentTimeMillis()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(model.size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .build()
        analysis.setAnalyzer(exec) { proxy ->
            try {
                val now = System.currentTimeMillis()
                val hot = (pm?.currentThermalStatus ?: 0) >= android.os.PowerManager.THERMAL_STATUS_SEVERE
                val minGap = if (hot) 66L else 40L                       // ~24 fps cap, ~15 fps when the phone is hot
                if (now - lastRun < minGap) return@setAnalyzer
                lastRun = now
                if (tracker != null && model == PoseModels.Model.FULL && (hot || (samples >= 30 && avgMs > 38f)) && PoseModels.has(ctx, PoseModels.Model.LITE)) {
                    tracker?.close(); tracker = null; model = PoseModels.Model.LITE; samples = 0; avgMs = 0f
                }
                val t = tracker ?: runCatching { MediaPipeTracker(ctx, model) }.getOrNull()
                    ?.also { tracker = it; val l = it.label; main.execute { engine = l } }
                if (t == null) { if (!failed) { failed = true; main.execute { engine = "Body tracking couldn't start on this phone" } }; return@setAnalyzer }
                val (bmp, w, h) = bufs.upright(proxy)
                val ts = maxOf(now, lastTs + 1).also { lastTs = it }
                val t0 = android.os.SystemClock.elapsedRealtime()
                val f = runCatching { t.analyse(bmp, w, h, ts) }.getOrNull()
                val dt = (android.os.SystemClock.elapsedRealtime() - t0).toFloat()
                avgMs = if (samples == 0) dt else avgMs * 0.9f + dt * 0.1f; samples++
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
            exec.execute { tracker?.close(); tracker = null; bufs.release() }
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
            (11..32).forEach { i -> if (ok(i)) drawCircle(if (i in highlight) accent else Color.White, if (i in highlight) 11f else if (i > 16 && i < 23 || i > 28) 5f else 7f, pt(i)) }
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
