package com.myfit.tracker.ui.food

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.FoodVision
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.Duo
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import com.myfit.tracker.ui.theme.rememberTick
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.abs

/** What the on-device check sees in the viewfinder. */
private enum class Guide(val text: String) {
    START("Point the camera at your meal"),
    DARK("Too dark — find more light or tap the flash"),
    SHAKY("Hold steady…"),
    CLOSER("Move a little closer — fill the frame with the plate"),
    FOOD("Food detected — tap the button to snap"),
}

private val FOOD_LABELS = setOf(
    "food", "dish", "cuisine", "fast food", "junk food", "bread", "baked goods", "fruit", "vegetable", "meat", "breakfast",
    "lunch", "dinner", "dessert", "snack", "soup", "rice", "pizza", "sandwich", "salad", "cake", "cookie", "egg", "noodle",
    "pasta", "seafood", "sushi", "juice", "coffee", "tea", "produce", "staple food", "ingredient", "recipe", "cheese",
    "chicken", "hamburger", "hot dog", "french fries", "pastry", "ice cream", "chocolate", "candy", "sweets", "fried food",
    "curry", "bean", "milk", "drink", "beverage", "cookware and bakeware", "meal", "nut", "berry", "citrus",
)
private val TABLE_LABELS = setOf("tableware", "plate", "bowl", "cutlery", "spoon", "fork", "dishware", "platter", "cup", "mug", "table")

/**
 * Full-screen food camera: live preview, a viewfinder that lights up when the phone sees food
 * (on-device, free, ~2 checks a second), plain-language aiming tips, optional live AI dish names,
 * flash, gallery and a big shutter.
 */
@Composable
fun FoodCamera(
    container: AppContainer,
    mealLabel: String,
    onCaptured: (Bitmap) -> Unit,
    onGallery: () -> Unit,
    onClose: () -> Unit,
) {
    val th = LocalFitTheme.current
    val ctx = LocalContext.current
    val settings = LocalSettings.current
    val owner = LocalLifecycleOwner.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val exec = remember { Executors.newSingleThreadExecutor() }
    val labeler = remember { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.45f).build()) }
    val vision = remember { FoodVision(container) }
    val preview = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val selector = remember {
        ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setResolutionSelector(selector).build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var guide by remember { mutableStateOf(Guide.START) }
    var torch by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    var liveOn by remember { mutableStateOf(settings.liveAi) }
    var liveNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var liveBusy by remember { mutableStateOf(false) }
    var liveNote by remember { mutableStateOf<String?>(null) }
    val live by rememberUpdatedState(liveOn)

    DisposableEffect(Unit) {
        var lastRun = 0L
        var lastLive = 0L
        val busy = java.util.concurrent.atomic.AtomicBoolean(false)
        var prevGrid: FloatArray? = null
        var foodStreak = 0
        analysis.setAnalyzer(exec) { proxy ->
            val now = SystemClock.elapsedRealtime()
            if (busy.get() || now - lastRun < 450) { proxy.close(); return@setAnalyzer }
            lastRun = now
            val (luma, grid) = lumaStats(proxy)
            val motion = prevGrid?.let { p -> grid.indices.sumOf { i -> abs(grid[i] - p[i]).toDouble() } / grid.size } ?: 0.0
            prevGrid = grid
            val media = proxy.image
            if (media == null) { proxy.close(); return@setAnalyzer }
            busy.set(true)
            labeler.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnCompleteListener(main) { task ->
                    val labels = if (task.isSuccessful) task.result.orEmpty() else emptyList()
                    val food = labels.filter { it.text.lowercase() in FOOD_LABELS }.maxOfOrNull { it.confidence } ?: 0f
                    val table = labels.any { it.text.lowercase() in TABLE_LABELS }
                    foodStreak = if (food >= 0.6f) foodStreak + 1 else 0
                    val g = when {
                        luma < 40 -> Guide.DARK
                        motion > 14 -> Guide.SHAKY
                        food >= 0.6f -> Guide.FOOD
                        food >= 0.45f || table -> Guide.CLOSER
                        else -> Guide.START
                    }
                    // live AI names: only when food is clearly in view and steady, at most every 3.5 s
                    val wantLive = live && g == Guide.FOOD && foodStreak >= 2 && now - lastLive > 3500 && !liveBusy
                    val frame = if (wantLive) runCatching { rotate(proxy.toBitmap(), proxy.imageInfo.rotationDegrees) }.getOrNull() else null
                    proxy.close()
                    busy.set(false)
                    main.execute {
                        guide = g
                        if (g != Guide.FOOD && g != Guide.SHAKY) liveNames = emptyList()
                    }
                    if (frame != null) {
                        lastLive = now
                        main.execute {
                            liveBusy = true
                            scope.launch {
                                runCatching { vision.quickNames(frame) }
                                    .onSuccess { liveNames = it; liveNote = null }
                                    .onFailure { liveNote = "Live AI paused — ${com.myfit.tracker.ai.AiRouter.shortMsg(it)}" }
                                liveBusy = false
                            }
                        }
                    }
                }
        }
        val future = ProcessCameraProvider.getInstance(ctx)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get().also { provider = it }
                val pv = Preview.Builder().setResolutionSelector(selector).build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                p.unbindAll()
                camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, pv, capture, analysis)
            }
        }, main)
        onDispose {
            disposed = true
            analysis.clearAnalyzer()
            runCatching { provider?.unbindAll() }
            exec.shutdown()
            labeler.close()
        }
    }

    fun shoot() {
        if (shooting) return
        shooting = true; tick()
        capture.takePicture(exec, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = runCatching { rotate(image.toBitmap(), image.imageInfo.rotationDegrees) }.getOrNull()
                image.close()
                main.execute { shooting = false; if (bmp != null) onCaptured(bmp) }
            }
            override fun onError(e: ImageCaptureException) { main.execute { shooting = false } }
        })
    }

    val frameColor by animateColorAsState(
        when (guide) { Guide.FOOD -> th.accent; Guide.DARK -> th.warning; else -> Color.White.copy(alpha = 0.9f) }, tween(300), label = "frame",
    )
    val pulse = rememberInfiniteTransition(label = "scan")
    val scanY by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "scanY")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ preview }, Modifier.fillMaxSize())
        // dimmed surround + rounded viewfinder + corner brackets + soft scan line
        Canvas(Modifier.fillMaxSize()) {
            val side = size.width * 0.82f
            val left = (size.width - side) / 2f
            val top = size.height * 0.42f - side / 2f
            val r = 34.dp.toPx()
            val hole = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(r)))
            }
            drawPath(hole, Color.Black.copy(alpha = 0.45f))
            val arm = side * 0.16f; val sw = 4.dp.toPx()
            listOf(Offset(left, top) to Offset(1f, 1f), Offset(left + side, top) to Offset(-1f, 1f),
                Offset(left, top + side) to Offset(1f, -1f), Offset(left + side, top + side) to Offset(-1f, -1f)).forEach { (o, d) ->
                val p = Path().apply {
                    moveTo(o.x, o.y + d.y * arm)
                    lineTo(o.x, o.y + d.y * r * 0.6f)
                    quadraticTo(o.x, o.y, o.x + d.x * r * 0.6f, o.y)
                    lineTo(o.x + d.x * arm, o.y)
                }
                drawPath(p, frameColor, style = Stroke(sw, cap = StrokeCap.Round))
            }
            if (guide == Guide.FOOD || guide == Guide.CLOSER) {
                val y = top + side * (0.08f + 0.84f * scanY)
                drawLine(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, frameColor.copy(alpha = 0.85f), Color.Transparent), left, left + side),
                    Offset(left + 12.dp.toPx(), y), Offset(left + side - 12.dp.toPx(), y), 2.dp.toPx(),
                )
            }
        }

        // top bar
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIcon(Duo.Close, "Close") { onClose() }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Snap a meal", style = FitType.section, color = Color.White)
                Text(mealLabel, style = FitType.caption, color = Color.White.copy(alpha = 0.75f))
            }
            Pill(if (liveOn) "Live AI on" else "Live AI", Duo.AutoAwesome, liveOn) {
                liveOn = !liveOn; liveNames = emptyList(); liveNote = null
                container.write { container.settings.setLiveAi(liveOn) }
            }
            Spacer(Modifier.width(8.dp))
            RoundIcon(Duo.Flame, if (torch) "Flash on" else "Flash off", active = torch) {
                torch = !torch; camera?.cameraControl?.enableTorch(torch)
            }
        }

        // guidance + live names, placed just under the viewfinder
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = maxWidth * 0.82f
            val below = maxHeight * 0.42f + side / 2 + 16.dp
            Column(
                Modifier.fillMaxWidth().padding(top = below).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    guide.text, style = FitType.label, color = if (guide == Guide.FOOD) th.onAccent else Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (guide == Guide.FOOD) th.accent else Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
                AnimatedVisibility(liveOn && (liveNames.isNotEmpty() || liveBusy || liveNote != null), enter = fadeIn(), exit = fadeOut()) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            liveNames.isNotEmpty() -> liveNames.forEach { n ->
                                Text(n, style = FitType.caption, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.18f)).border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                            liveNote != null -> Text(liveNote ?: "", style = FitType.caption, color = Color.White.copy(alpha = 0.8f))
                            else -> Text("Recognising…", style = FitType.caption, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }

        // bottom controls
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 26.dp, start = 32.dp, end = 32.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RoundIcon(Duo.Images, "Gallery", big = true) { onGallery() }
            Box(
                Modifier.size(82.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp)
                    .clip(CircleShape).background(if (guide == Guide.FOOD) th.accent else Color.White)
                    .clickableNoRipple { shoot() },
                contentAlignment = Alignment.Center,
            ) {
                if (shooting) Text("…", style = FitType.title, color = Color.Black)
                else Icon(Duo.Camera, "Take photo", tint = if (guide == Guide.FOOD) th.onAccent else Color.Black, modifier = Modifier.size(30.dp))
            }
            Box(Modifier.size(52.dp))
        }
        Text(
            "Tip: shoot from above, whole plate in frame",
            style = FitType.caption, color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, active: Boolean = false, big: Boolean = false, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val s = if (big) 52.dp else 42.dp
    Box(
        Modifier.size(s).clip(CircleShape).background(if (active) th.accent else Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape).clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (active) th.onAccent else Color.White, modifier = Modifier.size(s * 0.48f)) }
}

@Composable
private fun Pill(text: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) th.accent else Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).clickableNoRipple(onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) th.onAccent else Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = FitType.caption, color = if (on) th.onAccent else Color.White)
    }
}

/** Average brightness (0–255) and a coarse 8×6 brightness grid for motion detection, from the Y plane. */
private fun lumaStats(p: ImageProxy): Pair<Double, FloatArray> {
    val plane = p.planes[0]
    val buf = plane.buffer
    val rs = plane.rowStride; val ps = plane.pixelStride
    val w = p.width; val h = p.height
    val gw = 8; val gh = 6
    val grid = FloatArray(gw * gh)
    val cnt = IntArray(gw * gh)
    var sum = 0L; var n = 0
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val idx = y * rs + x * ps
            if (idx < buf.limit()) {
                val v = buf.get(idx).toInt() and 0xFF
                sum += v; n++
                val gi = (y * gh / h) * gw + (x * gw / w)
                grid[gi] += v; cnt[gi]++
            }
            x += 16
        }
        y += 16
    }
    for (i in grid.indices) if (cnt[i] > 0) grid[i] /= cnt[i]
    return (if (n > 0) sum.toDouble() / n else 128.0) to grid
}

private fun rotate(b: Bitmap, deg: Int): Bitmap =
    if (deg == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
