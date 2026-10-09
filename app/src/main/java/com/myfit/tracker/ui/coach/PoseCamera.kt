package com.myfit.tracker.ui.coach

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.myfit.tracker.domain.FormGuide
import java.util.concurrent.Executors

private val BONES = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16, 11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28, 27 to 31, 28 to 32)

/**
 * Live camera with on-device body-landmark detection (ML Kit accurate pose model, stream mode). Frames are analysed on the phone
 * and never stored or uploaded. Draws a skeleton over the preview; [highlight] joints glow in [accent].
 */
@Composable
fun PoseCamera(front: Boolean, accent: Color, highlight: Set<Int>, onPose: (FormGuide.Pose, Long) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val preview = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var frame by remember { mutableStateOf<Triple<FloatArray, Int, Int>?>(null) }
    val cb by rememberUpdatedState(onPose)

    DisposableEffect(front) {
        val exec = Executors.newSingleThreadExecutor()
        val detector = PoseDetection.getClient(AccuratePoseDetectorOptions.Builder().setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE).build())
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .build()
        analysis.setAnalyzer(exec) { proxy ->
            @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class) val media = proxy.image
            if (media == null) { proxy.close(); return@setAnalyzer }
            val rot = proxy.imageInfo.rotationDegrees
            val w = if (rot % 180 == 0) proxy.width else proxy.height
            val h = if (rot % 180 == 0) proxy.height else proxy.width
            detector.process(InputImage.fromMediaImage(media, rot))
                .addOnCompleteListener(main) { t ->
                    proxy.close()
                    val pose = if (t.isSuccessful) t.result else null
                    val lm = pose?.allPoseLandmarks.orEmpty()
                    if (lm.size >= 33) {
                        val pts = FloatArray(33 * 3)
                        lm.forEach { l -> val i = l.landmarkType; if (i in 0..32) { pts[i * 3] = l.position.x; pts[i * 3 + 1] = l.position.y; pts[i * 3 + 2] = l.inFrameLikelihood } }
                        frame = Triple(pts, w, h)
                        cb(FormGuide.Pose(pts, w, h), System.currentTimeMillis())
                    } else frame = null
                }
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
            exec.shutdown()
            detector.close()
        }
    }

    Box(modifier) {
        AndroidView({ preview }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val f = frame ?: return@Canvas
            val (pts, iw, ih) = f
            // FILL_CENTER mapping (preview is mirrored for the front camera)
            val scale = maxOf(size.width / iw, size.height / ih)
            val dx = (size.width - iw * scale) / 2f; val dy = (size.height - ih * scale) / 2f
            fun pt(i: Int): Offset {
                val x = pts[i * 3] * scale + dx
                return Offset(if (front) size.width - x else x, pts[i * 3 + 1] * scale + dy)
            }
            fun ok(i: Int) = pts[i * 3 + 2] > 0.5f
            BONES.forEach { (a, b) ->
                if (ok(a) && ok(b)) {
                    val hot = a in highlight || b in highlight
                    drawLine(if (hot) accent else Color.White.copy(alpha = 0.75f), pt(a), pt(b), strokeWidth = if (hot) 9f else 6f, cap = StrokeCap.Round)
                }
            }
            (11..28).forEach { i -> if (ok(i)) drawCircle(if (i in highlight) accent else Color.White, if (i in highlight) 11f else 7f, pt(i)) }
        }
    }
}
