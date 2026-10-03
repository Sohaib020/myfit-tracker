package com.myfit.tracker.ui.pip

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Moods the live 3D Pip can show. Prop animations (dumbbell, bottle, bowl, dance…) keep the rendered clips. */
internal val Pip3DMoods = setOf(
    PipMood.HAPPY, PipMood.NEUTRAL, PipMood.TALKING, PipMood.CURIOUS, PipMood.WAVE, PipMood.CELEBRATE, PipMood.EXCITED,
    PipMood.PROUD, PipMood.LETS_GO, PipMood.WINK, PipMood.LAUGH, PipMood.SPIN, PipMood.SURPRISED, PipMood.CONCERNED,
)

/** Global switch: turned off for the session if the 3D renderer ever fails on this phone. */
object Pip3DSupport {
    @Volatile var failed = false
    private var inited = false
    fun init(): Boolean {
        if (failed) return false
        if (inited) return true
        return runCatching { com.google.android.filament.utils.Utils.init(); inited = true; true }.getOrElse { failed = true; false }
    }
    @Volatile private var glb: ByteArray? = null
    fun glb(ctx: Context): ByteArray? = glb ?: runCatching { ctx.assets.open("pip/pip3d.glb").use { it.readBytes() } }.getOrNull()?.also { glb = it }
}

/**
 * Live, real-time Pip: his head turns smoothly toward your finger (no frame cross-fades), he breathes, blinks,
 * wiggles his antenna, and taps make him wave, hop, spin, wink or laugh.
 */
@Composable
fun Pip3D(mood: PipMood, modifier: Modifier = Modifier, size: Dp = 96.dp, onTap: (() -> Unit)? = null, talking: Boolean = false, interactive: Boolean = true) {
    val ctrl = remember { PipRig() }
    val tap by rememberUpdatedState(onTap)
    SideEffect { ctrl.setMood(mood); ctrl.talking = talking }
    Box(
        modifier.size(size).then(
            if (!interactive && onTap == null) Modifier else Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = this.size.width.toFloat().coerceAtLeast(1f); val h = this.size.height.toFloat().coerceAtLeast(1f)
                    var moved = false
                    val start = down.position
                    ctrl.pointer(start.x / w * 2 - 1, start.y / h * 2 - 1)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        if ((c.position - start).getDistance() > 12f) moved = true
                        ctrl.pointer(c.position.x / w * 2 - 1, c.position.y / h * 2 - 1)
                    }
                    ctrl.release()
                    if (!moved) { ctrl.react(); tap?.invoke() }
                }
            },
        ),
    ) {
        AndroidView(
            factory = { ctx -> TextureView(ctx).also { it.isOpaque = false; ctrl.attach(it) } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { ctrl.detach() },
        )
    }
}

/** Pose state + Filament scene for one Pip. Everything runs on the main thread via Choreographer. */
internal class PipRig : Choreographer.FrameCallback {
    // ---- pose inputs
    @Volatile var talking = false
    private var mood = PipMood.HAPPY
    private var px = 0f; private var py = 0f; private var touching = false
    private var reaction: String? = null; private var reactionT = 0f
    private var nextReaction = 0
    private var moodT = 0f

    fun setMood(m: PipMood) {
        if (m == mood) return
        mood = m; moodT = 0f
        when (m) {
            PipMood.WAVE -> startReaction("wave")
            PipMood.CELEBRATE, PipMood.EXCITED -> startReaction("hop")
            PipMood.SPIN -> startReaction("spin")
            PipMood.WINK -> startReaction("wink")
            PipMood.LAUGH -> startReaction("laugh")
            else -> Unit
        }
    }
    fun pointer(x: Float, y: Float) { px = x.coerceIn(-1.3f, 1.3f); py = y.coerceIn(-1.3f, 1.3f); touching = true }
    fun release() { touching = false }
    fun react() { startReaction(listOf("wave", "hop", "wink", "spin", "laugh")[nextReaction++ % 5]) }
    private fun startReaction(r: String) { reaction = r; reactionT = 0f }

    // ---- smoothed state
    private var yaw = 0f; private var yawV = 0f; private var nod = 0f; private var nodV = 0f
    private var t = 0f; private var blinkT = 2.5f; private var blink = 0f; private var antV = 0f; private var ant = 0f
    private var lastNs = 0L; private var frame = 0

    // ---- filament
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var camEntity = 0
    private var swapChain: SwapChain? = null
    private var uiHelper: UiHelper? = null
    private var loader: AssetLoader? = null
    private var resources: ResourceLoader? = null
    private var asset: FilamentAsset? = null
    private val lights = mutableListOf<Int>()
    private var ibl: IndirectLight? = null
    private var running = false
    private val base = HashMap<String, FloatArray>()
    private val ents = HashMap<String, Int>()

    fun attach(tv: TextureView) {
        if (!Pip3DSupport.init()) return
        val bytes = Pip3DSupport.glb(tv.context) ?: return
        try {
            val e = Engine.create(); engine = e
            renderer = e.createRenderer().also { r ->
                r.clearOptions = r.clearOptions.apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) }
            }
            val sc = e.createScene(); scene = sc
            camEntity = EntityManager.get().create()
            val cam = e.createCamera(camEntity); camera = cam
            cam.setExposure(1f)   // unit exposure: light values are relative, and glint emissive 1.0 reads as white
            val v = e.createView(); view = v
            v.scene = sc; v.camera = cam
            v.blendMode = View.BlendMode.TRANSLUCENT
            v.isPostProcessingEnabled = true
            v.colorGrading = ColorGrading.Builder().toneMapping(ColorGrading.ToneMapping.FILMIC).build(e)
            v.multiSampleAntiAliasingOptions = v.multiSampleAntiAliasingOptions.apply { enabled = true; sampleCount = 4 }
            v.antiAliasing = View.AntiAliasing.NONE

            // soft studio light: warm key, cool fill, rim, plus even ambient
            fun dir(r: Float, g: Float, b: Float, lux: Float, x: Float, y: Float, z: Float) {
                val l = EntityManager.get().create()
                LightManager.Builder(LightManager.Type.DIRECTIONAL).color(r, g, b).intensity(lux).direction(x, y, z).castShadows(false).build(e, l)
                sc.addEntity(l); lights += l
            }
            dir(1.0f, 0.97f, 0.93f, 3.0f, 0.45f, -0.55f, -0.70f)
            dir(0.92f, 0.96f, 1.0f, 1.1f, -0.6f, -0.25f, -0.75f)
            dir(1f, 1f, 1f, 1.4f, -0.05f, -0.45f, 0.9f)
            ibl = IndirectLight.Builder().irradiance(1, floatArrayOf(1f, 1f, 1.02f)).intensity(1.2f).build(e).also { sc.indirectLight = it }

            val ld = AssetLoader(e, UbershaderProvider(e), EntityManager.get()); loader = ld
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.flip() }
            val a = ld.createAsset(buf) ?: throw IllegalStateException("bad glb")
            asset = a
            val rl = ResourceLoader(e); resources = rl
            rl.loadResources(a)
            a.releaseSourceData()
            sc.addEntities(a.entities)
            val tm = e.transformManager
            for (n in NODES) {
                val ent = a.getFirstEntityByName(n)
                if (ent == 0) continue
                ents[n] = ent
                val m = FloatArray(16); tm.getTransform(tm.getInstance(ent), m); base[n] = m
            }

            val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK); uiHelper = ui
            ui.isOpaque = false
            ui.renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    swapChain?.let { e.destroySwapChain(it) }
                    swapChain = e.createSwapChain(surface, ui.swapChainFlags)
                }
                override fun onDetachedFromSurface() {
                    swapChain?.let { e.destroySwapChain(it); e.flushAndWait() }
                    swapChain = null
                }
                override fun onResized(width: Int, height: Int) {
                    v.viewport = Viewport(0, 0, width, height)
                    val aspect = width.toDouble() / height.coerceAtLeast(1)
                    val fovV = 2.0 * Math.toDegrees(atan(18.0 / 85.0)) / (if (aspect < 1) aspect else 1.0)
                    cam.setProjection(fovV, aspect, 0.5, 30.0, Camera.Fov.VERTICAL)
                }
            }
            ui.attachTo(tv)
            // camera matches the rendered clips: 85 mm lens, 8.6 units in front, looking slightly down
            cam.lookAt(0.0, 1.95, 8.6, 0.0, 1.53, 0.0, 0.0, 1.0, 0.0)
            running = true
            lastNs = 0L
            Choreographer.getInstance().postFrameCallback(this)
        } catch (t: Throwable) {
            Pip3DSupport.failed = true
            detach()
        }
    }

    fun detach() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        val e = engine ?: return
        runCatching {
            uiHelper?.detach()
            asset?.let { a -> scene?.removeEntities(a.entities); loader?.destroyAsset(a) }
            loader?.destroy(); resources?.destroy()
            lights.forEach { e.destroyEntity(it); EntityManager.get().destroy(it) }
            ibl?.let { e.destroyIndirectLight(it) }
            swapChain?.let { e.destroySwapChain(it) }
            renderer?.let { e.destroyRenderer(it) }
            view?.let { e.destroyView(it) }
            scene?.let { e.destroyScene(it) }
            e.destroyCameraComponent(camEntity); EntityManager.get().destroy(camEntity)
            e.destroy()
        }
        engine = null; renderer = null; scene = null; view = null; camera = null; swapChain = null; uiHelper = null
        loader = null; resources = null; asset = null; lights.clear(); ibl = null; ents.clear(); base.clear()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        val dt = if (lastNs == 0L) 1f / 60f else ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = frameTimeNanos
        frame++
        val busy = touching || reaction != null || abs(yawV) > 2f || abs(nodV) > 2f || talking
        if (!busy && frame % 2 == 1) return          // idle: 30 fps is plenty (cooler, saves battery)
        step(if (!busy) dt * 2 else dt)
        val r = renderer ?: return; val sc = swapChain ?: return; val v = view ?: return
        if (uiHelper?.isReadyToRender == true && r.beginFrame(sc, frameTimeNanos)) { r.render(v); r.endFrame() }
    }

    // ------------------------------------------------------------------ animation
    private fun spring(x: Float, v: Float, target: Float, dt: Float, w: Float = 14f): Pair<Float, Float> {
        // critically damped spring (smooth, no overshoot), exact for a step of dt
        val d = x - target
        val e = exp(-w * dt)
        val nx = target + (d + (v + w * d) * dt) * e
        val nv = (v - w * (v + w * d) * dt) * e
        return nx to nv
    }

    private fun step(dt: Float) {
        t += dt; moodT += dt
        // where to look: the finger, or a gentle idle wander
        val ty = if (touching) px * 30f else 7f * sin(t * 0.55f) + 3f * sin(t * 1.3f)
        val tn = if (touching) py * 16f else 3f * sin(t * 0.41f + 1f)
        spring(yaw, yawV, ty, dt, if (touching) 16f else 6f).let { (x, v) -> yaw = x; yawV = v }
        spring(nod, nodV, tn, dt, if (touching) 16f else 6f).let { (x, v) -> nod = x; nodV = v }
        // antenna lags behind head motion
        val antT = -yawV * 0.22f
        spring(ant, antV, antT, dt, 9f).let { (x, v) -> ant = x; antV = v }
        // blink every 2.5–5 s
        blinkT -= dt
        if (blinkT < 0f) { blink = 1f; blinkT = 2.5f + Random.nextFloat() * 2.5f }
        blink = (blink - dt * 7f).coerceAtLeast(0f)
        val blinkAmt = if (blink > 0f) sin(blink * PI.toFloat()) else 0f

        var hop = 0f; var spin = 0f; var armR = 0f; var armL = 0f; var happy = false; var mouthOpen = 0f; var squash = 1f
        val r = reaction
        if (r != null) {
            reactionT += dt
            val dur = when (r) { "wave" -> 1.6f; "hop" -> 1.3f; "spin" -> 1.0f; "wink" -> 0.8f; else -> 1.2f }
            val u = (reactionT / dur).coerceIn(0f, 1f)
            val env = smooth(u / 0.18f) * smooth((1f - u) / 0.2f)
            when (r) {
                "wave" -> { armR = env * (115f + 22f * sin(u * 6f * PI.toFloat())); mouthOpen = 0.6f * env }
                "hop" -> {
                    val j = abs(sin(u * 2f * PI.toFloat()))
                    hop = 0.26f * j * env; armL = 125f * env; armR = 125f * env; happy = true; mouthOpen = 0.9f * env
                    squash = 1f + 0.04f * j - 0.06f * (1 - j) * env
                }
                "spin" -> { spin = 360f * smooth(u); hop = 0.08f * sin(u * PI.toFloat()) }
                "wink" -> { happy = true }
                else -> { happy = true; mouthOpen = 0.8f * env * (0.6f + 0.4f * sin(u * 14f)); hop = 0.04f * abs(sin(u * 4f * PI.toFloat())) * env }
            }
            if (u >= 1f) reaction = null
        }
        if (mood == PipMood.CELEBRATE && r == null && moodT > 3f) { startReaction("hop"); moodT = 0f }
        if (mood == PipMood.EXCITED || mood == PipMood.PROUD || mood == PipMood.LETS_GO) happy = happy || (moodT < 1.5f)
        val talk = talking && r == null
        if (talk) mouthOpen = 0.35f + 0.45f * abs(sin(t * 11f)) * abs(sin(t * 3.3f + 0.5f))
        val surprised = mood == PipMood.SURPRISED
        val concerned = mood == PipMood.CONCERNED
        val breath = sin(t * 1.7f)
        val wink = r == "wink"

        apply(yaw, nod, if (concerned) -4f else 2f * sin(t * 0.9f), hop, spin, squash, breath, armL, armR, ant, blinkAmt, happy, wink, mouthOpen, surprised)
    }

    private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }

    // ------------------------------------------------------------------ transforms (glTF space: +Y up, +Z toward camera)
    private val tmp = FloatArray(16); private val out = FloatArray(16)

    private fun set(name: String, m: FloatArray) {
        val e = engine ?: return; val ent = ents[name] ?: return
        val tm = e.transformManager
        tm.setTransform(tm.getInstance(ent), m)
    }

    /** base * extra (extra applied in the node's own local frame). */
    private fun local(name: String, extra: FloatArray) {
        val b = base[name] ?: return
        Matrix.multiplyMM(out, 0, b, 0, extra, 0); set(name, out)
    }

    private fun scaleM(sx: Float, sy: Float, sz: Float): FloatArray { Matrix.setIdentityM(tmp, 0); Matrix.scaleM(tmp, 0, sx, sy, sz); return tmp }

    private fun apply(yaw: Float, nod: Float, tilt: Float, hop: Float, spin: Float, squash: Float, breath: Float, armL: Float, armR: Float,
                      ant: Float, blink: Float, happy: Boolean, wink: Boolean, mouthOpen: Float, surprised: Boolean) {
        // whole body
        val root = FloatArray(16); Matrix.setIdentityM(root, 0)
        Matrix.translateM(root, 0, 0f, hop, 0f)
        Matrix.rotateM(root, 0, spin, 0f, 1f, 0f)
        Matrix.scaleM(root, 0, 1f / sqrt(squash), squash, 1f / sqrt(squash))
        base["Root"]?.let { b -> Matrix.multiplyMM(out, 0, b, 0, root, 0); set("Root", out) }
        // head turns around the neck
        val h = FloatArray(16); Matrix.setIdentityM(h, 0)
        Matrix.translateM(h, 0, 0f, 1.15f, 0f)
        Matrix.rotateM(h, 0, yaw, 0f, 1f, 0f)
        Matrix.rotateM(h, 0, nod, 1f, 0f, 0f)
        Matrix.rotateM(h, 0, tilt, 0f, 0f, 1f)
        Matrix.translateM(h, 0, 0f, 0.63f, 0f)
        set("HeadRig", h)
        // breathing
        local("Body", scaleM(1f + 0.008f * breath, 1f + 0.016f * breath, 1f + 0.008f * breath))
        // arms swing up around the shoulders (sideways)
        val a = FloatArray(16)
        Matrix.setIdentityM(a, 0); Matrix.rotateM(a, 0, armL, 0f, 0f, 1f); local("ArmL", a)
        Matrix.setIdentityM(a, 0); Matrix.rotateM(a, 0, -armR, 0f, 0f, 1f); local("ArmR", a)
        Matrix.setIdentityM(a, 0); Matrix.rotateM(a, 0, ant, 0f, 0f, 1f); local("AntBase", a)
        // eyes: blink by squashing vertically; happy (^ ^) swaps to the closed arcs
        val big = if (surprised) 1.15f else 1f
        for (s in listOf("L", "R")) {
            val closed = happy && !(wink && s == "L")          // wink closes Pip's right eye (screen left)
            val eyeS = if (closed) 0f else (1f - 0.92f * blink)
            local("Eye$s", scaleM(big, eyeS * big, big))
            val g = if (closed || blink > 0.6f) 0f else 1f
            local("Glint$s", scaleM(g, g, g)); local("Glint2$s", scaleM(g, g, g))
            val lid = if (closed) 1f else 0f
            local("LidHappy$s", scaleM(lid, lid, lid))
        }
        // mouth: smile line, or the open "D" mouth scaled by how open it is
        val open = mouthOpen.coerceIn(0f, 1f)
        val showOpen = open > 0.08f || surprised
        local("MouthLine", if (showOpen) scaleM(0f, 0f, 0f) else scaleM(1f, 1f, 1f))
        val ow = if (surprised && open < 0.08f) 0.55f else (0.75f + 0.25f * open)
        val oh = if (surprised && open < 0.08f) 0.6f else (0.35f + 0.65f * open)
        local("MouthBig", if (showOpen) scaleM(ow, oh, 1f) else scaleM(0f, 0f, 0f))
        local("TongueBig", if (showOpen && open > 0.4f) scaleM(ow, oh, 1f) else scaleM(0f, 0f, 0f))
        local("MouthOpen", scaleM(0f, 0f, 0f)); local("Tongue", scaleM(0f, 0f, 0f))
    }

    companion object {
        private val NODES = listOf(
            "Root", "HeadRig", "Body", "ArmL", "ArmR", "AntBase", "EyeL", "EyeR", "GlintL", "GlintR", "Glint2L", "Glint2R",
            "LidHappyL", "LidHappyR", "MouthLine", "MouthBig", "TongueBig", "MouthOpen", "Tongue",
        )
    }
}
