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
import androidx.compose.ui.graphics.graphicsLayer
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

    /** Bump with each re-export of the release GLBs so phones fetch the new one (v2 = R22 Mixamo mocap). */
    private const val GLB_VER = 2
    private fun file(c: Context, lookId: String) = File(c.filesDir, "coach3d_glb/${CoachPacks.pack(lookId)}_v$GLB_VER.glb")
    fun installed(c: Context, lookId: String) = file(c, lookId).let { it.exists() && it.length() > 100_000 }

    /** Downloads the coach's GLB once (a few MB). Safe to call repeatedly. */
    suspend fun ensure(c: Context, lookId: String): Boolean = withContext(Dispatchers.IO) {
        if (installed(c, lookId)) return@withContext true
        val pack = CoachPacks.pack(lookId)
        synchronized(busy) { if (!busy.add(pack)) return@withContext false }
        try {
            val f = file(c, lookId); f.parentFile?.mkdirs()
            f.parentFile?.listFiles()?.filter { it.name.startsWith("${pack}") && it.name != f.name }?.forEach { it.delete() }   // old versions
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

    private val cache = java.util.concurrent.ConcurrentHashMap<String, ByteBuffer>()
    fun readGlb(c: Context, lookId: String): ByteBuffer? = cache[CoachPacks.pack(lookId)]?.duplicate()?.also { it.rewind() } ?: runCatching {
        val b = file(c, lookId).readBytes()
        ByteBuffer.allocateDirect(b.size).order(ByteOrder.nativeOrder()).put(b).also { it.rewind(); cache[CoachPacks.pack(lookId)] = it }.duplicate()
    }.getOrNull()

    /**
     * R23: called once after launch. Creates the shared engine on the main thread while it's idle and reads the trainer's
     * and nutritionist's GLBs into memory, so opening their screens shows the 3D coach almost at once instead of a
     * frozen clip for several seconds. Also removes files of the retired male nutritionist.
     */
    fun prewarm(c: Context, looks: List<String>) {
        val app = c.applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            runCatching { File(app.filesDir, "coach3d/nutri_m").deleteRecursively(); File(app.filesDir, "coach3d_glb").listFiles()?.filter { it.name.startsWith("nutri_m") }?.forEach { it.delete() } }
            if (!enabled(app)) return@launch
            looks.distinct().forEach { l -> if (installed(app, l)) readGlb(app, l) else ensure(app, l) }
        }
        if (enabled(app)) android.os.Looper.getMainLooper().queue.addIdleHandler { runCatching { shared() }; false }
    }

    /** Animations baked into every GLB (see tools/coach3d/export_glb.py). */
    val LOOPS = setOf("idle", "idle2", "talk", "talk2", "talk3", "think", "dance", "dance2", "dance3")
    fun emotes(look: String) = if (look.startsWith("n")) listOf("wave", "thumbsup", "cheer", "clap", "nod", "happy", "acknowledge", "dance", "dance2")
        else listOf("wave", "thumbsup", "fistpump", "cheer", "victory", "clap", "rally", "jump", "dance", "dance2", "dance3")
    /** Talking/idle variety: a new variant each time the loop comes round. */
    val VARIANTS = mapOf("talk" to listOf("talk", "talk2", "talk3"), "idle" to listOf("idle", "idle", "idle2"))

    /**
     * One Filament engine + material provider + glTF loader for the whole app, created on first use and never torn
     * down (creating/destroying an engine per screen visit stalls the UI thread and was the S23 crash). Main thread only.
     */
    private var shared: Shared? = null
    class Shared(val engine: Engine, val materials: com.google.android.filament.gltfio.UbershaderProvider, val loader: com.google.android.filament.gltfio.AssetLoader)
    fun shared(): Shared = shared ?: run {
        com.google.android.filament.gltfio.Gltfio.init()
        val e = Engine.create()
        val m = com.google.android.filament.gltfio.UbershaderProvider(e)
        Shared(e, m, com.google.android.filament.gltfio.AssetLoader(e, m, EntityManager.get())).also { shared = it }
    }
}

/**
 * The coach on the hub / nutritionist stage: live 3D when enabled and downloaded, otherwise the animated clip.
 * [pose] is the screen's mood (idle, talk, wave, thumbsup, think, plate, tablet…).
 */
@Composable
fun CoachStage(look: String, poseIn: String, modifier: Modifier = Modifier, level: MutableStateFlow<Float>? = null) {
    val ctx = LocalContext.current
    val pose = rememberSequence(poseIn)
    val tick by Coach3D.ready.collectAsState()
    val on = remember(tick) { Coach3D.enabled(ctx) }
    val have = remember(tick, look) { Coach3D.installed(ctx, look) }
    LaunchedEffect(on, look) { if (on && !Coach3D.installed(ctx, look)) Coach3D.ensure(ctx, look) }
    var failed by remember(look) { mutableStateOf(false) }
    if (on && have && !failed) Coach3DView(look, pose, modifier, onFail = { failed = true })
    else CoachFigure(look, pose, modifier, level)
}

/** "a>b>c" plays a, then b (one-shots, timed to their clips), then stays on c. Plain names pass through. */
private val SEQ_MS = mapOf("nod" to 2600L, "happy" to 2950L, "wave" to 4700L, "thumbsup" to 4200L, "acknowledge" to 1950L)

@Composable
private fun rememberSequence(pose: String): String {
    if ('>' !in pose) return pose
    val parts = remember(pose) { pose.split('>') }
    var i by remember(pose) { mutableStateOf(0) }
    LaunchedEffect(pose) { for (k in 0 until parts.size - 1) { i = k; delay(SEQ_MS[parts[k]] ?: 2500L) }; i = parts.size - 1 }
    return parts[i]
}

@Composable
private fun Coach3DView(look: String, pose: String, modifier: Modifier, onFail: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val yaw = remember(look) { Animatable(0f) }
    var springJob by remember { mutableStateOf<Job?>(null) }
    // read the GLB off the UI thread first (a few MB)
    var glb by remember(look) { mutableStateOf<ByteBuffer?>(null) }
    LaunchedEffect(look) { glb = withContext(Dispatchers.IO) { Coach3D.readGlb(ctx, look) }; if (glb == null) onFail() }
    val buf = glb
    Box(modifier) {
        if (buf == null) CoachFigure(look, pose, Modifier.fillMaxSize(), tapToEmote = false)
        else Scene3D(look, pose, buf, onFail, scope, yaw, { springJob }, { springJob = it })
    }
}

@Composable
private fun Scene3D(look: String, pose: String, buf: ByteBuffer, onFail: () -> Unit, scope: kotlinx.coroutines.CoroutineScope,
                    yaw: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>, getJob: () -> Job?, setJob: (Job?) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        val holder = remember(look, buf) { CoachScene(look, buf) }
        LaunchedEffect(holder, pose) { holder.setPose(pose) }
        // the animated clip keeps playing underneath until the 3D coach has actually drawn, then fades out (no frozen start)
        val shown by holder.shown
        val fade by androidx.compose.animation.core.animateFloatAsState(if (shown) 0f else 1f, androidx.compose.animation.core.tween(350), label = "c3d")
        if (fade > 0.01f) CoachFigure(look, pose, Modifier.fillMaxSize().graphicsLayer { alpha = fade }, tapToEmote = false)
        val poseNow by rememberUpdatedState(pose)
        AndroidView(
            factory = { c -> TextureView(c).also { tv -> if (!holder.attach(tv)) onFail() else holder.setPose(poseNow) } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { holder.destroy() },
        )
        DisposableEffect(holder) { onDispose { holder.destroy() } }
        Box(Modifier.fillMaxSize()
            .pointerInput(holder) {
                detectHorizontalDragGestures(
                    onDragStart = { getJob()?.cancel() },
                    onDragEnd = {
                        setJob(scope.launch {
                            delay(3000)
                            val home = Math.round(yaw.value / 360f) * 360f
                            yaw.animateTo(home, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessVeryLow)) { holder.yawDeg = value }
                            yaw.snapTo(0f); holder.yawDeg = 0f
                        })
                    },
                ) { ch, dx -> ch.consume(); scope.launch { yaw.snapTo(yaw.value + dx * 0.45f); holder.yawDeg = yaw.value } }
            }
            .pointerInput(holder) { detectTapGestures { holder.tapEmote() } })
    }
}

/**
 * One coach on screen: its own renderer / view / scene / camera / swap chain on the shared engine, the GLB asset,
 * studio lights on a transparent background, our own yaw and clip blending. [destroy] is idempotent and tears down in
 * dependency order (swap chain first, engine never), so leaving the screen can't touch freed native objects.
 */
private class CoachScene(private val look: String, private val glb: ByteBuffer) : Choreographer.FrameCallback {
    /** True once the coach (with textures) has been on screen for a few frames — the clip underneath can fade out. */
    val shown = androidx.compose.runtime.mutableStateOf(false)
    private var framesSinceAdd = 0
    @Volatile var yawDeg = 0f
    private var sh: Coach3D.Shared? = null
    private var renderer: Renderer? = null
    private var view: View? = null
    private var scene: com.google.android.filament.Scene? = null
    private var camera: com.google.android.filament.Camera? = null
    private var camEntity = 0
    private var swap: com.google.android.filament.SwapChain? = null
    private var uiHelper: UiHelper? = null
    private var resLoader: com.google.android.filament.gltfio.ResourceLoader? = null
    private var asset: com.google.android.filament.gltfio.FilamentAsset? = null
    private var animator: com.google.android.filament.gltfio.Animator? = null
    private var added = false
    private val lights = ArrayList<Int>()
    private var ibl: IndirectLight? = null
    private var names: List<String> = emptyList()
    private var base = "idle"
    private var cur = -1; private var curStart = 0L
    private var prev = -1; private var prevTime = 0f; private var fadeStart = 0L
    private var lastEmote: String? = null
    private var once = false
    private var center = floatArrayOf(0f, 0f, 0f); private var unit = 1f
    private var running = false
    private var dead = false

    fun attach(tv: TextureView): Boolean = runCatching {
        check(!dead)
        val s = Coach3D.shared(); sh = s; val e = s.engine
        renderer = e.createRenderer().apply { clearOptions = Renderer.ClearOptions().apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) } }
        val sc = e.createScene(); scene = sc
        camEntity = EntityManager.get().create()
        val cam = e.createCamera(camEntity); camera = cam
        cam.setExposure(16f, 1f / 125f, 100f)
        view = e.createView().apply {
            scene = sc; camera = cam
            blendMode = View.BlendMode.TRANSLUCENT
            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 4 }
        }
        // soft studio: even ambient + a warm key from the front-left + a cool rim from behind
        ibl = IndirectLight.Builder().irradiance(1, floatArrayOf(0.95f, 0.95f, 1.0f)).intensity(32_000f).build(e).also { sc.indirectLight = it }
        fun dir(r: Float, g: Float, b: Float, lux: Float, x: Float, y: Float, z: Float) {
            val l = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL).color(r, g, b).intensity(lux).direction(x, y, z).castShadows(false).build(e, l)
            sc.addEntity(l); lights += l
        }
        dir(1f, 0.96f, 0.9f, 60_000f, 0.45f, -0.55f, -0.7f)
        dir(0.8f, 0.88f, 1f, 35_000f, -0.3f, -0.2f, 0.9f)

        glb.rewind()
        val a = s.loader.createAsset(glb) ?: error("bad glb"); asset = a
        resLoader = com.google.android.filament.gltfio.ResourceLoader(e).also { it.asyncBeginLoad(a) }
        a.releaseSourceData()
        animator = a.instance.animator
        val an = animator
        names = if (an != null) (0 until an.animationCount).map { an.getAnimationName(it) } else emptyList()
        val bb = a.boundingBox
        center = bb.center.copyOf(); val h = bb.halfExtent
        unit = 1f / maxOf(h[0], h[1], h[2]).coerceAtLeast(0.01f)

        val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }; uiHelper = ui
        ui.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: android.view.Surface) {
                if (dead) return
                swap?.let { e.destroySwapChain(it) }
                swap = e.createSwapChain(surface, ui.swapChainFlags)
            }
            override fun onDetachedFromSurface() {
                swap?.let { e.destroySwapChain(it); e.flushAndWait() }
                swap = null
            }
            override fun onResized(width: Int, height: Int) {
                if (dead) return
                view?.viewport = com.google.android.filament.Viewport(0, 0, width, height)
                val aspect = width.toDouble() / height.coerceAtLeast(1)
                camera?.setLensProjection(38.0, aspect, 0.05, 50.0)
            }
        }
        ui.attachTo(tv)
        cam.lookAt(0.0, 0.25, 0.0, 0.0, 0.0, -4.0, 0.0, 1.0, 0.0)
        applyRoot()
        running = true
        Choreographer.getInstance().postFrameCallback(this)
        true
    }.getOrElse { destroy(); false }

    private fun idx(n: String) = names.indexOf(n)

    fun setPose(pose: String) {
        if (dead) return
        val p = CoachPacks.clipFor(pose)
        base = when (p) { "plate", "tablet", "bottle" -> "talk"; in Coach3D.LOOPS -> p; else -> "idle" }
        if (p !in Coach3D.LOOPS && idx(p) >= 0) play(p) else if (cur < 0 || names.getOrNull(cur) in Coach3D.LOOPS) play(base)
    }

    fun tapEmote() {
        if (dead) return
        val pick = Coach3D.emotes(look).filter { it != lastEmote && idx(it) >= 0 }.randomOrNull() ?: return
        lastEmote = pick; play(pick, once = true)
    }

    private fun pickBase(): String = Coach3D.VARIANTS[base]?.filter { idx(it) >= 0 }?.randomOrNull() ?: base

    private fun play(n: String, once: Boolean = false, force: Boolean = false) {
        val i = idx(n); if (i < 0 || (i == cur && !once && !force)) return
        val now = System.nanoTime()
        if (cur >= 0) { prev = cur; prevTime = timeOf(cur, now); fadeStart = now }
        this.once = once
        cur = i; curStart = now
    }

    private fun timeOf(i: Int, now: Long): Float {
        val an = animator ?: return 0f
        val t = (now - curStart) / 1e9f; val d = an.getAnimationDuration(i).coerceAtLeast(0.01f)
        return if (names[i] in Coach3D.LOOPS && !(once && i == cur)) t % d else t.coerceAtMost(d)
    }

    private fun applyRoot() {
        val a = asset ?: return; val e = sh?.engine ?: return
        val tm = e.transformManager
        val m = FloatArray(16); android.opengl.Matrix.setIdentityM(m, 0)
        android.opengl.Matrix.translateM(m, 0, 0f, 0f, -4f)
        android.opengl.Matrix.rotateM(m, 0, yawDeg, 0f, 1f, 0f)
        android.opengl.Matrix.scaleM(m, 0, unit, unit, unit)
        android.opengl.Matrix.translateM(m, 0, -center[0], -center[1], -center[2])
        tm.setTransform(tm.getInstance(a.root), m)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || dead) return
        Choreographer.getInstance().postFrameCallback(this)
        val a = asset ?: return; val r = renderer ?: return; val v = view ?: return
        // textures finish loading in the background; show the coach only once they're ready (no grey flash)
        resLoader?.let { rl ->
            rl.asyncUpdateLoad()
            if (!added && rl.asyncGetLoadProgress() >= 1f) { scene?.addEntities(a.entities); added = true }
        }
        val an = animator
        if (an != null && cur >= 0) {
            val now = System.nanoTime()
            val d = an.getAnimationDuration(cur)
            val t = (now - curStart) / 1e9f
            if ((once || names[cur] !in Coach3D.LOOPS) && t >= d) play(pickBase())
            else if (!once && names[cur] in Coach3D.LOOPS && t >= d && (Coach3D.VARIANTS[base]?.size ?: 0) > 1) play(pickBase(), force = true)
            an.applyAnimation(cur, timeOf(cur, now))
            val fade = (now - fadeStart) / 1e9f / 0.3f
            if (prev >= 0 && fade < 1f) an.applyCrossFade(prev, prevTime, fade.coerceIn(0f, 1f))
            an.updateBoneMatrices()
        }
        applyRoot()
        val sc = swap ?: return
        if (uiHelper?.isReadyToRender == true && r.beginFrame(sc, frameTimeNanos)) {
            r.render(v); r.endFrame()
            if (added && !shown.value && ++framesSinceAdd >= 3) shown.value = true
        }
    }

    fun destroy() {
        if (dead) return
        dead = true; running = false
        Choreographer.getInstance().removeFrameCallback(this)
        val s = sh ?: return; val e = s.engine
        // 1) swap chain (via the UiHelper callback) while everything it renders still exists
        runCatching { uiHelper?.detach() }
        swap?.let { runCatching { e.destroySwapChain(it); e.flushAndWait() } }; swap = null
        // 2) content, 3) per-view objects. The shared engine, materials and loader stay alive.
        asset?.let { a -> runCatching { scene?.removeEntities(a.entities); s.loader.destroyAsset(a) } }; asset = null; animator = null
        runCatching { resLoader?.destroy() }; resLoader = null
        lights.forEach { l -> runCatching { scene?.removeEntity(l); e.destroyEntity(l); EntityManager.get().destroy(l) } }; lights.clear()
        ibl?.let { runCatching { e.destroyIndirectLight(it) } }; ibl = null
        renderer?.let { runCatching { e.destroyRenderer(it) } }; renderer = null
        view?.let { runCatching { e.destroyView(it) } }; view = null
        scene?.let { runCatching { e.destroyScene(it) } }; scene = null
        if (camEntity != 0) runCatching { e.destroyCameraComponent(camEntity); EntityManager.get().destroy(camEntity) }
        camera = null; camEntity = 0; uiHelper = null
    }
}
