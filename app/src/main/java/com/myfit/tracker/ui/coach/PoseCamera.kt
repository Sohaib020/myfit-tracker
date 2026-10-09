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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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

/** MediaPipe face-oval landmark indices (forehead → right → chin → left), drawn as the head outline. */
private val FACE_OVAL = intArrayOf(10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109)

/** One analysed frame: 33 body points (x, y, visibility) in upright image pixels, optional face outline + head pose. */
private class Frame(val pts: FloatArray, val w: Int, val h: Int, val face: FloatArray?, val head: FormGuide.Head?)

/**
 * The body tracker. R17: MediaPipe Pose Landmarker **heavy** (33 3-D body points incl. hands, feet, eyes, ears and mouth)
 * plus the Face Landmarker for the head — its outline and yaw / pitch / roll — every few frames.
 * GPU first, CPU if the GPU path fails; Google ML Kit's accurate model if MediaPipe can't start at all.
 */
private interface Tracker {
    val label: String
    fun analyse(bmp: Bitmap, tsMs: Long, wantFace: Boolean): Frame?
    fun close()
}

private class MediaPipeTracker(ctx: Context) : Tracker {
    override val label = "MediaPipe heavy"
    private val pose: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
    private val face: com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker?
    private var lastHead: FormGuide.Head? = null
    private var lastFace: FloatArray? = null

    init {
        fun base(path: String, gpu: Boolean) = com.google.mediapipe.tasks.core.BaseOptions.builder().setModelAssetPath(path)
            .setDelegate(if (gpu) com.google.mediapipe.tasks.core.Delegate.GPU else com.google.mediapipe.tasks.core.Delegate.CPU).build()
        fun makePose(gpu: Boolean) = com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.createFromOptions(ctx,
            com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base(POSE_MODEL, gpu))
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.VIDEO)
                .setNumPoses(1).setMinPoseDetectionConfidence(0.5f).setMinPosePresenceConfidence(0.5f).setMinTrackingConfidence(0.6f)
                .build())
        pose = runCatching { makePose(true) }.getOrElse { makePose(false) }
        fun makeFace(gpu: Boolean) = com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.createFromOptions(ctx,
            com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(base(FACE_MODEL, gpu))
                .setRunningMode(com.google.mediapipe.tasks.vision.core.RunningMode.VIDEO)
                .setNumFaces(1).setMinFaceDetectionConfidence(0.5f).setMinTrackingConfidence(0.5f)
                .build())
        face = runCatching { makeFace(true) }.recoverCatching { makeFace(false) }.getOrNull()
    }

    override fun analyse(bmp: Bitmap, tsMs: Long, wantFace: Boolean): Frame? {
        val img = com.google.mediapipe.framework.image.BitmapImageBuilder(bmp).build()
        val r = pose.detectForVideo(img, tsMs)
        val lm = r.landmarks().firstOrNull()
        if (lm == null || lm.size < 33) return null
        val w = bmp.width; val h = bmp.height
        val pts = FloatArray(33 * 3)
        for (i in 0 until 33) {
            val l = lm[i]
            pts[i * 3] = l.x() * w; pts[i * 3 + 1] = l.y() * h
            pts[i * 3 + 2] = l.visibility().orElse(0f).let { v -> if (l.x() in -0.05f..1.05f && l.y() in -0.05f..1.05f) v else 0f }
        }
        if (wantFace && face != null) {
            val fr = runCatching { face.detectForVideo(img, tsMs) }.getOrNull()
            val f = fr?.faceLandmarks()?.firstOrNull()
            if (f != null && f.size >= 468) {
                fun px(i: Int) = f[i].x() * w
                fun py(i: Int) = f[i].y() * h
                lastFace = FloatArray(FACE_OVAL.size * 2).also { a -> FACE_OVAL.forEachIndexed { k, i -> a[k * 2] = px(i); a[k * 2 + 1] = py(i) } }
                // head pose from stable face points: eye corners (33, 263), nose tip (1), chin (152), forehead (10)
                val ex = (px(33) + px(263)) / 2; val ey = (py(33) + py(263)) / 2
                val eyeD = kotlin.math.hypot(px(263) - px(33), py(263) - py(33)).coerceAtLeast(1f)
                val yaw = ((px(1) - ex) / eyeD) * 95f
                val faceH = (py(152) - py(10)).coerceAtLeast(1f)
                val pitch = (0.52f - (py(1) - py(10)) / faceH) * 160f
                val roll = Math.toDegrees(atan2((py(263) - py(33)).toDouble(), (px(263) - px(33)).toDouble())).toFloat()
                val prev = lastHead
                lastHead = if (prev == null) FormGuide.Head(yaw, pitch, roll)
                else FormGuide.Head(prev.yaw + 0.5f * (yaw - prev.yaw), prev.pitch + 0.5f * (pitch - prev.pitch), prev.roll + 0.5f * (roll - prev.roll))
            } else if (f == null) { lastFace = null; lastHead = null }
        }
        return Frame(pts, w, h, lastFace, lastHead)
    }

    override fun close() { runCatching { pose.close() }; runCatching { face?.close() } }

    companion object {
        const val POSE_MODEL = "pose/pose_landmarker_heavy.task"
        const val FACE_MODEL = "pose/face_landmarker.task"
        fun available(ctx: Context) = runCatching { ctx.assets.list("pose")?.contains("pose_landmarker_heavy.task") == true }.getOrDefault(false)
    }
}

private class MlKitTracker : Tracker {
    override val label = "ML Kit"
    private val detector = com.google.mlkit.vision.pose.PoseDetection.getClient(
        com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions.STREAM_MODE).build())

    override fun analyse(bmp: Bitmap, tsMs: Long, wantFace: Boolean): Frame? {
        val pose = runCatching { com.google.android.gms.tasks.Tasks.await(detector.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0))) }.getOrNull()
        val lm = pose?.allPoseLandmarks.orEmpty()
        if (lm.size < 33) return null
        val pts = FloatArray(33 * 3)
        lm.forEach { l -> val i = l.landmarkType; if (i in 0..32) { pts[i * 3] = l.position.x; pts[i * 3 + 1] = l.position.y; pts[i * 3 + 2] = l.inFrameLikelihood } }
        return Frame(pts, bmp.width, bmp.height, null, null)
    }

    override fun close() { runCatching { detector.close() } }
}

/** CameraX frame → upright bitmap (front-camera frames stay un-mirrored; the overlay mirrors when drawing). */
private fun ImageProxy.upright(): Bitmap {
    val b = toBitmap()
    val rot = imageInfo.rotationDegrees
    if (rot == 0) return b
    val m = Matrix().apply { postRotate(rot.toFloat()) }
    return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true).also { if (it !== b) b.recycle() }
}

/**
 * Live camera with on-device body + head tracking. Frames are analysed on the phone and never stored or uploaded.
 * Draws the skeleton (body, hands, feet), the head outline and a head-direction readout; [highlight] joints glow in [accent].
 */
@Composable
fun PoseCamera(front: Boolean, accent: Color, highlight: Set<Int>, onPose: (FormGuide.Pose, Long) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val preview = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var engine by remember { mutableStateOf("") }
    val cb by rememberUpdatedState(onPose)

    DisposableEffect(front) {
        val exec = Executors.newSingleThreadExecutor()
        var tracker: Tracker? = null
        var frameNo = 0L
        var lastTs = 0L
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .build()
        analysis.setAnalyzer(exec) { proxy ->
            try {
                val t = tracker ?: (runCatching { if (MediaPipeTracker.available(ctx)) MediaPipeTracker(ctx) else null }.getOrNull() ?: MlKitTracker())
                    .also { tracker = it; val l = it.label; main.execute { engine = l } }
                val bmp = proxy.upright()
                val ts = maxOf(System.currentTimeMillis(), lastTs + 1).also { lastTs = it }
                val f = runCatching { t.analyse(bmp, ts, wantFace = frameNo++ % 3 == 0L) }.getOrNull()
                bmp.recycle()
                main.execute {
                    frame = f
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
            (11..32).forEach { i -> if (ok(i)) drawCircle(if (i in highlight) accent else Color.White, if (i in highlight) 11f else if (i > 16 && i < 23 || i > 28) 5f else 7f, pt(i)) }
            // head: face outline from the face model, else a circle from the body's face points
            val face = f.face
            if (face != null) {
                val path = Path()
                for (k in 0 until face.size / 2) { val o = map(face[k * 2], face[k * 2 + 1]); if (k == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                path.close()
                drawPath(path, accent.copy(alpha = 0.85f), style = Stroke(4f))
                val hd = f.head
                if (hd != null && ok(0)) {
                    // direction the face points, drawn from the nose
                    val n = pt(0)
                    val yawPx = (if (front) -hd.yaw else hd.yaw) / 90f * 70f
                    drawLine(accent, n, Offset(n.x - yawPx, n.y - hd.pitch / 90f * 70f), 5f, cap = StrokeCap.Round)
                }
            } else if (ok(0) && ok(7) && ok(8)) {
                val r = kotlin.math.hypot(pt(7).x - pt(8).x, pt(7).y - pt(8).y) * 0.75f
                drawCircle(Color.White.copy(alpha = 0.8f), r, pt(0), style = Stroke(4f))
            }
        }
        // small status chip: tracking engine + head direction
        val f = frame
        val hd = f?.head
        if (engine.isNotEmpty()) Text(
            if (f == null) "Looking for you… · $engine" else if (hd != null) "Head ${hd.describe()} · ${abs(hd.yaw).toInt()}° · $engine" else engine,
            style = FitType.overline, color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
