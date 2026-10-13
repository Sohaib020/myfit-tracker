package com.myfit.tracker.ui.coach

import android.content.Context
import android.view.Choreographer
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import com.myfit.tracker.ai.ondevice.DeviceTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * R21 real-time 3D coaches (hub + nutritionist only). Capable phones (strong GPU, enough RAM) get a live Filament
 * model you can spin with a finger; it springs back to face you ~3 s after you let go, and a tap plays a random
 * emote. Everyone else keeps the animated clips (with tap-to-emote). Settings → "3D coaches" overrides the default.
 */
object Coach3D {
    private const val BASE = "https://github.com/Sohaib020/myfit-tracker/releases/download/coach3d-glb-v1"
    private const val P = "coach3d"
    val ready = MutableStateFlow(0)
    private val busy = HashSet<String>()

    /** On by default only where real-time 3D runs smoothly. */
    fun capable(c: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 28) return false
        val p = runCatching { DeviceTier.profile(c) }.getOrNull() ?: return false
        return p.ramBytes >= 5_500_000_000L && p.gpuStrong
    }
    fun enabled(c: Context): Boolean = c.getSharedPreferences(P, Context.MODE_PRIVATE).getBoolean("on", capable(c))
    fun setEnabled(c: Context, on: Boolean) { c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putBoolean("on", on).apply(); ready.value++ }

    private fun file(c: Context, lookId: String) = File(c.filesDir, "coach3d_glb/${CoachPacks.pack(lookId)}.glb")
    fun installed(c: Context, lookId: String) = file(c, lookId).let { it.exists() && it.length() > 100_000 }

    /** Downloads the coach's GLB once (a few MB). Safe to call repeatedly. */
    suspend fun ensure(c: Context, lookId: String): Boolean = withContext(Dispatchers.IO) {
        if (installed(c, lookId)) return@withContext true
        val pack = CoachPacks.pack(lookId)
        synchronized(busy) { if (!busy.add(pack)) return@withContext false }
        try {
            val f = file(c, lookId); f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, "$pack.part")
            var conn = URL("$BASE/$pack.glb").openConnection() as HttpURLConnection
            repeat(5) {
                conn.instanceFollowRedirects = false; conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                if (conn.responseCode !in 300..399) return@repeat
                val loc = conn.getHeaderField("Location") ?: return@repeat
                conn.disconnect(); conn = URL(loc).openConnection() as HttpURLConnection
            }
            if (conn.responseCode != 200) return@withContext false
            conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (tmp.length() < 100_000 || !tmp.renameTo(f)) { tmp.delete(); return@withContext false }
            ready.value++; true
        } catch (_: Exception) { false } finally { synchronized(busy) { busy.remove(pack) } }
    }

    fun readGlb(c: Context, lookId: String): ByteBuffer? = runCatching {
        val b = file(c, lookId).readBytes()
        ByteBuffer.allocateDirect(b.size).order(ByteOrder.nativeOrder()).put(b).also { it.rewind() }
    }.getOrNull()

    /** Animations baked into every GLB (see tools/coach3d/export_glb.py). */
    val LOOPS = setOf("idle", "talk", "think", "dance")
    fun emotes(look: String) = if (look.startsWith("n")) listOf("wave", "thumbsup", "cheer", "fistpump")
        else listOf("wave", "thumbsup", "fistpump", "cheer", "victory")

    @Volatile private var inited = false
    fun init() { if (!inited) { Utils.init(); inited = true } }
}

/**
 * The coach on the hub / nutritionist stage: live 3D when enabled and downloaded, otherwise the animated clip.
 * [pose] is the screen's mood (idle, talk, wave, thumbsup, think, plate, tablet…).
 */
@Composable
fun CoachStage(look: String, pose: String, modifier: Modifier = Modifier, level: MutableStateFlow<Float>? = null) {
    val ctx = LocalContext.current
    val tick by Coach3D.ready.collectAsState()
    val on = remember(tick) { Coach3D.enabled(ctx) }
    val have = remember(tick, look) { Coach3D.installed(ctx, look) }
    LaunchedEffect(on, look) { if (on && !Coach3D.installed(ctx, look)) Coach3D.ensure(ctx, look) }
    var failed by remember(look) { mutableStateOf(false) }
    if (on && have && !failed) Coach3DView(look, pose, modifier, onFail = { failed = true })
    else CoachFigure(look, pose, modifier, level)
}

@Composable
private fun Coach3DView(look: String, pose: String, modifier: Modifier, onFail: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val yaw = remember(look) { Animatable(0f) }
    var springJob by remember { mutableStateOf<Job?>(null) }
    val holder = remember(look) { CoachScene(ctx, look) }
    val poseNow by rememberUpdatedState(pose)
    LaunchedEffect(pose) { holder.setPose(pose) }
    DisposableEffect(holder) { onDispose { holder.destroy() } }
    Box(modifier) {
        AndroidView(factory = { c ->
            TextureView(c).also { tv -> if (!holder.attach(tv)) onFail(); holder.setPose(poseNow) }
        }, modifier = Modifier.fillMaxSize(), update = { holder.yawDeg = yaw.value })
        Box(Modifier.fillMaxSize()
            .pointerInput(look) {
                detectHorizontalDragGestures(
                    onDragStart = { springJob?.cancel() },
                    onDragEnd = {
                        springJob = scope.launch {
                            delay(3000)
                            val home = Math.round(yaw.value / 360f) * 360f
                            yaw.animateTo(home, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)) { holder.yawDeg = value }
                            yaw.snapTo(0f); holder.yawDeg = 0f
                        }
                    },
                ) { ch, dx -> ch.consume(); scope.launch { yaw.snapTo(yaw.value + dx * 0.45f); holder.yawDeg = yaw.value } }
            }
            .pointerInput(look) { detectTapGestures { holder.tapEmote() } })
    }
}

/** One Filament scene: the coach GLB, studio lights, transparent background, our own yaw + clip blending. */
private class CoachScene(private val ctx: Context, private val look: String) : Choreographer.FrameCallback {
    @Volatile var yawDeg = 0f
    private var engine: Engine? = null
    private var viewer: ModelViewer? = null
    private var uiHelper: UiHelper? = null
    private val lights = ArrayList<Int>()
    private var ibl: IndirectLight? = null
    private var names: List<String> = emptyList()
    private var base = "idle"
    private var cur = -1; private var curStart = 0L
    private var prev = -1; private var prevTime = 0f; private var fadeStart = 0L
    private var lastEmote: String? = null
    private var center = floatArrayOf(0f, 0f, 0f); private var unit = 1f
    private var running = false

    fun attach(tv: TextureView): Boolean = runCatching {
        Coach3D.init()
        val e = Engine.create(); engine = e
        val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }; uiHelper = ui
        val manip = Manipulator.Builder().targetPosition(0f, 0f, -4f).orbitHomePosition(0f, 0.25f, 0f).viewport(1, 1).build(Manipulator.Mode.ORBIT)
        val v = ModelViewer(tv, e, ui, manip); viewer = v
        v.cameraFocalLength = 38f
        v.view.blendMode = View.BlendMode.TRANSLUCENT
        v.renderer.clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) }
        v.view.multiSampleAntiAliasingOptions = v.view.multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 4 }
        v.scene.skybox = null
        // soft studio: even ambient + a warm key from the front-left + a cool rim from behind
        ibl = IndirectLight.Builder().irradiance(1, floatArrayOf(0.95f, 0.95f, 1.0f)).intensity(32_000f).build(e).also { v.scene.indirectLight = it }
        fun dir(r: Float, g: Float, b: Float, lux: Float, x: Float, y: Float, z: Float) {
            val l = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL).color(r, g, b).intensity(lux).direction(x, y, z).castShadows(false).build(e, l)
            v.scene.addEntity(l); lights += l
        }
        dir(1f, 0.96f, 0.9f, 60_000f, 0.45f, -0.55f, -0.7f)
        dir(0.8f, 0.88f, 1f, 35_000f, -0.3f, -0.2f, 0.9f)
        val buf = Coach3D.readGlb(ctx, look) ?: error("no glb")
        v.loadModelGlb(buf)
        val asset = v.asset ?: error("bad glb")
        val bb = asset.boundingBox
        center = bb.center.copyOf(); val h = bb.halfExtent
        unit = 1f / maxOf(h[0], h[1], h[2]).coerceAtLeast(0.01f)
        val an = v.animator
        names = if (an != null) (0 until an.animationCount).map { an.getAnimationName(it) } else emptyList()
        applyRoot()
        running = true
        Choreographer.getInstance().postFrameCallback(this)
        true
    }.getOrElse { destroy(); false }

    private fun idx(n: String) = names.indexOf(n)

    fun setPose(pose: String) {
        val p = CoachPacks.clipFor(pose)
        base = when (p) { "plate", "tablet", "bottle" -> "talk"; in Coach3D.LOOPS -> p; else -> "idle" }
        if (p !in Coach3D.LOOPS && idx(p) >= 0) play(p) else if (cur < 0 || names.getOrNull(cur) in Coach3D.LOOPS) play(base)
    }

    fun tapEmote() {
        val pick = Coach3D.emotes(look).filter { it != lastEmote && idx(it) >= 0 }.randomOrNull() ?: return
        lastEmote = pick; play(pick)
    }

    private fun play(n: String) {
        val i = idx(n); if (i < 0 || i == cur) return
        val now = System.nanoTime()
        if (cur >= 0) { prev = cur; prevTime = timeOf(cur, now); fadeStart = now }
        cur = i; curStart = now
    }

    private fun timeOf(i: Int, now: Long): Float {
        val an = viewer?.animator ?: return 0f
        val t = (now - curStart) / 1e9f; val d = an.getAnimationDuration(i).coerceAtLeast(0.01f)
        return if (names[i] in Coach3D.LOOPS) t % d else t.coerceAtMost(d)
    }

    private fun applyRoot() {
        val v = viewer ?: return; val asset = v.asset ?: return
        val tm = v.engine.transformManager
        val m = FloatArray(16); android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.translateM(m, 0, 0f, 0f, -4f)
        android.opengl.Matrix.rotateM(m, 0, yawDeg, 0f, 1f, 0f)
        android.opengl.Matrix.scaleM(m, 0, unit, unit, unit)
        android.opengl.Matrix.translateM(m, 0, -center[0], -center[1], -center[2])
        tm.setTransform(tm.getInstance(asset.root), m)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val v = viewer ?: return
        runCatching {
            val an = v.animator
            if (an != null && cur >= 0) {
                val now = System.nanoTime()
                val d = an.getAnimationDuration(cur)
                if (names[cur] !in Coach3D.LOOPS && (now - curStart) / 1e9f >= d) play(base)
                an.applyAnimation(cur, timeOf(cur, now))
                val fade = (now - fadeStart) / 1e9f / 0.3f
                if (prev >= 0 && fade < 1f) an.applyCrossFade(prev, prevTime, fade.coerceIn(0f, 1f))
                an.updateBoneMatrices()
            }
            applyRoot()
            v.render(frameTimeNanos)
        }
    }

    fun destroy() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        val e = engine ?: return
        runCatching {
            viewer?.destroyModel()
            uiHelper?.detach()
            ibl?.let { e.destroyIndirectLight(it) }
            lights.forEach { e.destroyEntity(it); EntityManager.get().destroy(it) }
            e.destroy()
        }
        engine = null; viewer = null; uiHelper = null; lights.clear(); ibl = null
    }
}
