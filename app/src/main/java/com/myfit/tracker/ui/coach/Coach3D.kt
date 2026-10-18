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
    private const val BASE = "https://github.com/Sohaib020/myfit-tracker/releases/download/coach3d-glb-v2"
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
    private const val GLB_VER = 3   // R26 high-res coaches
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
    // R29: centred on a glowing platform, 3D or clip
    OnPlatform(com.myfit.tracker.ui.theme.LocalFitTheme.current.accentBright, modifier) {
        if (on && have && !failed) Coach3DView(look, pose, Modifier.fillMaxSize(), onFail = { failed = true })
        else CoachFigure(look, pose, Modifier.fillMaxSize(), level, spotlight = false)
    }
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
        if (buf == null) CoachFigure(look, pose, Modifier.fillMaxSize(), spotlight = false, tapToEmote = false)
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
        if (fade > 0.01f) CoachFigure(look, pose, Modifier.fillMaxSize().graphicsLayer { alpha = fade }, spotlight = false, tapToEmote = false)
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
    // external mode (Home buddy): Pip's clip logic says what to play; a play-once move holds its last frame and reports the end
    private var external = false
    private var extLoop = true
    private var extEnd: (() -> Unit)? = null
    private var extEndFired = false
    var resolve: (String) -> String = { it }
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
        // R26: real body bounds = every renderable's box through its node transform (the asset box can be inflated
        // by armature/root transforms, which left characters small and floating); normalise to 1 unit tall
        val (lo, hi) = bodyBounds(e, a)
        center = floatArrayOf((lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2)
        unit = 1f / (hi[1] - lo[1]).coerceAtLeast(0.01f)

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
                frame(width.toDouble() / height.coerceAtLeast(1))
            }
        }
        ui.attachTo(tv)
        frame(1.0)
        applyRoot()
        running = true
        Choreographer.getInstance().postFrameCallback(this)
        true
    }.getOrElse { destroy(); false }

    private fun idx(n: String) = names.indexOf(n)

    private fun bodyBounds(e: com.google.android.filament.Engine, a: com.google.android.filament.gltfio.FilamentAsset): Pair<FloatArray, FloatArray> {
        jointBounds(e, a)?.let { return it }
        return boxBounds(e, a)
    }

    /**
     * R28: body size from the skeleton at rest — feet to head joint, extended to the crown — so every model (whatever
     * its mesh/box bookkeeping) is measured the same way. The renderable boxes of skinned meshes weren't reliable: a
     * box from the joints alone made the champion page overflow its card.
     */
    private fun jointBounds(e: com.google.android.filament.Engine, a: com.google.android.filament.gltfio.FilamentAsset): Pair<FloatArray, FloatArray>? = runCatching {
        val inst = a.instance
        if (inst.skinCount < 1) return@runCatching null
        val tm = e.transformManager
        val joints = inst.getJointsAt(0)
        val w = FloatArray(16)
        var minY = Float.MAX_VALUE; var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var headY = Float.NaN; var hipX = Float.NaN; var hipZ = Float.NaN
        for (j in joints) {
            val ti = tm.getInstance(j); if (ti == 0) continue
            tm.getWorldTransform(ti, w)
            val x = w[12]; val y = w[13]; val z = w[14]
            minY = minOf(minY, y); minX = minOf(minX, x); maxX = maxOf(maxX, x); minZ = minOf(minZ, z); maxZ = maxOf(maxZ, z)
            when (a.getName(j)) { "Head" -> headY = y; "Hips" -> { hipX = x; hipZ = z } }
        }
        if (headY.isNaN() || minY == Float.MAX_VALUE || headY <= minY) return@runCatching null
        val floor = minY - (headY - minY) * 0.03f          // toe/ankle joints sit a little above the floor
        val top = floor + (headY - floor) / 0.865f          // head joint (skull base) is ~86% of standing height
        val cx = if (hipX.isNaN()) (minX + maxX) / 2 else hipX
        val cz = if (hipZ.isNaN()) (minZ + maxZ) / 2 else hipZ
        val half = (top - floor) * 0.2f
        floatArrayOf(cx - half, floor, cz - half) to floatArrayOf(cx + half, top, cz + half)
    }.getOrNull()

    private fun boxBounds(e: com.google.android.filament.Engine, a: com.google.android.filament.gltfio.FilamentAsset): Pair<FloatArray, FloatArray> {
        val rm = e.renderableManager; val tm = e.transformManager
        val lo = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE); val hi = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val box = com.google.android.filament.Box(); val w = FloatArray(16); val p = FloatArray(4); val q = FloatArray(4)
        // skinned meshes (all our characters): the vertices are already in bind/world space — glTF ignores the mesh
        // node's transform and Filament compensates for it in the joint matrices — so the node transform must NOT
        // be applied (our exporter's root carries the hip-height offset, which pushed characters half off the card)
        val skinned = runCatching { a.instance.skinCount > 0 }.getOrDefault(true)
        for (ent in a.renderableEntities) {
            val ri = rm.getInstance(ent); if (ri == 0) continue
            rm.getAxisAlignedBoundingBox(ri, box)
            val ti = tm.getInstance(ent)
            if (ti != 0 && !skinned) tm.getWorldTransform(ti, w) else android.opengl.Matrix.setIdentityM(w, 0)
            val c = box.center; val h = box.halfExtent
            for (sx in -1..1 step 2) for (sy in -1..1 step 2) for (sz in -1..1 step 2) {
                p[0] = c[0] + sx * h[0]; p[1] = c[1] + sy * h[1]; p[2] = c[2] + sz * h[2]; p[3] = 1f
                android.opengl.Matrix.multiplyMV(q, 0, w, 0, p, 0)
                for (k in 0..2) { lo[k] = minOf(lo[k], q[k]); hi[k] = maxOf(hi[k], q[k]) }
            }
        }
        if (lo[0] > hi[0]) { val bb = a.boundingBox; val c = bb.center; val h = bb.halfExtent
            return floatArrayOf(c[0] - h[0], c[1] - h[1], c[2] - h[2]) to floatArrayOf(c[0] + h[0], c[1] + h[1], c[2] + h[2]) }
        return lo to hi
    }

    /**
     * R26 framing: the whole body fills the view and stands on its bottom edge (like the 2D clips), whatever the
     * card's shape. The model is normalised to 1 unit tall, centred at (0, 0, -4); we move the camera back just far
     * enough that body height + headroom (arms up, jumps) and a shoulder-wide envelope both fit.
     */
    private fun frame(aspect: Double) {
        val cam = camera ?: return
        val lens = 38.0
        val tanHalf = 12.0 / lens                              // 35 mm film: 24 mm tall
        val needH = maxOf(1.10, 0.80 / aspect.coerceAtLeast(0.2)) // height incl. headroom, or the width envelope on narrow cards
        val d = needH / (2 * tanHalf)
        val yc = -0.5 - 0.02 + needH / 2                         // view bottom just under the feet
        cam.setLensProjection(lens, aspect, 0.05, 50.0)
        cam.lookAt(0.0, yc + d * 0.04, -4.0 + d, 0.0, yc, -4.0, 0.0, 1.0, 0.0)
    }

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

    /** Home buddy: play [name] (looping or once). Unknown names go through [resolve]. */
    fun playExternal(name: String, loop: Boolean, onEnd: (() -> Unit)?) {
        if (dead) return
        external = true
        val n = name.takeIf { idx(it) >= 0 } ?: resolve(name).takeIf { idx(it) >= 0 } ?: "idle"
        extLoop = loop; extEnd = onEnd; extEndFired = false
        if (idx(n) == cur) { curStart = System.nanoTime(); return }        // same move again: restart it
        play(n, force = true)
    }

    private fun timeOf(i: Int, now: Long): Float {
        val an = animator ?: return 0f
        val t = (now - curStart) / 1e9f; val d = an.getAnimationDuration(i).coerceAtLeast(0.01f)
        if (external && i == cur) return if (extLoop) t % d else t.coerceAtMost(d)
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
            if (external) { if (!extLoop && t >= d && !extEndFired) { extEndFired = true; extEnd?.invoke() } }
            else if ((once || names[cur] !in Coach3D.LOOPS) && t >= d) play(pickBase())
            else if (!external && !once && names[cur] in Coach3D.LOOPS && t >= d && (Coach3D.VARIANTS[base]?.size ?: 0) > 1) play(pickBase(), force = true)
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

// ---------------------------------------------------------------- R24 champions (Home buddy) in real-time 3D

/** Champion looks as real-time models (release champ3d-v1/<id>.glb, every move of the tier). */
object ChampModels {
    private const val BASE = "https://github.com/Sohaib020/myfit-tracker/releases/download/champ3d-v2"
    private const val VER = 2   // R26 weight-shift idle
    private val busy = HashSet<String>()
    private fun file(c: Context, id: String) = File(c.filesDir, "champ3d/${id}_v$VER.glb")
    fun installed(c: Context, id: String) = file(c, id).let { it.exists() && it.length() > 100_000 }

    suspend fun ensure(c: Context, id: String): Boolean = withContext(Dispatchers.IO) {
        if (installed(c, id)) return@withContext true
        synchronized(busy) { if (!busy.add(id)) return@withContext false }
        try {
            val f = file(c, id); f.parentFile?.mkdirs()
            f.parentFile?.listFiles()?.filter { it.name.startsWith(id + "_") && it.name != f.name }?.forEach { it.delete() }
            val tmp = File(f.parentFile, "$id.part")
            var conn = URL("$BASE/$id.glb").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect(); conn = URL(loc).openConnection() as HttpURLConnection; conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            }
            if (conn.responseCode != 200) return@withContext false
            conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (tmp.length() < 100_000 || !tmp.renameTo(f)) { tmp.delete(); return@withContext false }
            Coach3D.ready.value++; true
        } catch (_: Exception) { false } finally { synchronized(busy) { busy.remove(id) } }
    }

    fun read(c: Context, id: String): ByteBuffer? = runCatching {
        val b = file(c, id).readBytes()
        ByteBuffer.allocateDirect(b.size).order(ByteOrder.nativeOrder()).put(b).also { it.rewind() }
    }.getOrNull()
}

/** Lets Pip's clip logic drive a 3D champion: play(name, loop, onEnd) is queued until the model is up. */
class Champ3DController {
    internal var scene: Any? = null
    private var pending: Triple<String, Boolean, (() -> Unit)?>? = null
    val shown = androidx.compose.runtime.mutableStateOf(false)
    fun play(name: String, loop: Boolean, onEnd: (() -> Unit)?) {
        val sc = scene as? CoachScene
        if (sc == null) pending = Triple(name, loop, onEnd) else sc.playExternal(name, loop, onEnd)
    }
    internal fun attached(sc: Any) { scene = sc; pending?.let { (n, l, e) -> (sc as CoachScene).playExternal(n, l, e) }; pending = null }
    internal fun detached() { scene = null }
}

/**
 * The Home buddy champion as a live 3D model (drawn behind Pip's effects layer). Drag to spin (springs back), no zoom;
 * taps go to Pip's own tap handling. Calls [onFail] if the model can't be shown (Pip then plays the clip pack).
 */
@Composable
fun Champ3DLayer(id: String, tierRank: Int, ctl: Champ3DController, modifier: Modifier, onFail: () -> Unit) {
    val ctx = LocalContext.current
    var glb by remember(id) { mutableStateOf<ByteBuffer?>(null) }
    LaunchedEffect(id) { glb = withContext(Dispatchers.IO) { ChampModels.read(ctx, id) }; if (glb == null) onFail() }
    val buf = glb ?: return
    val scope = rememberCoroutineScope()
    val yaw = remember(id) { Animatable(0f) }
    var springJob by remember { mutableStateOf<Job?>(null) }
    val holder = remember(id, buf) {
        CoachScene("champ", buf).also { sc ->
            val tier = com.myfit.tracker.ui.pip.Tier.entries[tierRank]
            sc.resolve = { n -> com.myfit.tracker.ui.pip.Buddy.path("$n.webp", com.myfit.tracker.ui.pip.BuddyChar(com.myfit.tracker.ui.pip.Champion.BLAZE, tier))
                .substringAfterLast('/').removeSuffix(".webp") }
        }
    }
    val shown by holder.shown
    LaunchedEffect(shown) { ctl.shown.value = shown }
    Box(modifier) {
        AndroidView(
            factory = { c -> TextureView(c).also { tv -> if (!holder.attach(tv)) onFail() else ctl.attached(holder) } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { ctl.detached(); holder.destroy() },
        )
        DisposableEffect(holder) { onDispose { ctl.detached(); holder.destroy() } }
        Box(Modifier.fillMaxSize().pointerInput(holder) {
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
        })
    }
}
