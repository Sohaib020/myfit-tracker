package com.myfit.tracker.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat

/**
 * R34 motion system — one place for every spring in the app so things feel like one product.
 *
 * - Springs, not tweens: they keep their velocity when interrupted and look smooth at 60 Hz.
 * - Everything animates through graphicsLayer lambdas (draw phase only), so no recomposition per frame.
 * - Entrances cascade only in the first ~0.6 s after a screen opens; cards scrolled into view later just appear.
 * - Low-end phones (low-RAM / < 4 GB) get shorter, alpha-only entrances; "Remove animations" turns them off.
 */
object Motion {
    @Volatile var lowEnd = false; private set
    @Volatile var reduced = false; private set
    /** Settings → Motion → Battery saver: everything still. */
    @Volatile var appStill = false

    fun init(ctx: Context) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        lowEnd = am?.isLowRamDevice == true || mem.totalMem in 1 until 4_500_000_000L
        reduced = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }

    // ---- the springs
    /** Default for position/size: settles quickly with no visible overshoot. */
    fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 0.88f, stiffness = 380f)
    /** Taps, toggles, chips. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 700f)
    /** A touch of life for celebratory things (scores, rings landing). */
    fun <T> lively(): SpringSpec<T> = spring(dampingRatio = 0.68f, stiffness = 300f)
    /** Slow reveals (numbers counting, charts growing). */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow)

    // ---- screen-entrance cascade
    @Volatile private var screenAt = 0L
    private var counter = 0
    private const val WINDOW_MS = 600L

    /** Call when a screen/tab/sheet opens: the next cards to compose cascade in. */
    fun markScreen() { screenAt = SystemClock.uptimeMillis(); counter = 0; runCatching { Awake.poke() } }

    /** Order of this element in the current cascade, or -1 if the screen opened too long ago. */
    internal fun slot(): Int {
        if (reduced || appStill) return -1
        return if (SystemClock.uptimeMillis() - screenAt < WINDOW_MS) counter++ else -1
    }
}

/**
 * Fade + rise (+ a hair of scale) on screen open, staggered by composition order. Used by every Glass surface, so
 * every card in the app enters the same way. No-op for anything composed outside the opening window.
 */
@Composable
fun Modifier.entrance(index: Int? = null): Modifier {
    val slot = remember { index ?: Motion.slot() }
    if (slot < 0 || slot > 14) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(slot.coerceAtMost(9) * (if (Motion.lowEnd) 18L else 32L))
        a.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = if (Motion.lowEnd) 520f else 300f))
    }
    val rise = if (Motion.lowEnd) 10.dp else 22.dp
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * rise.toPx()
        if (!Motion.lowEnd) { val s = 0.965f + 0.035f * v; scaleX = s; scaleY = s }
    }
}

/**
 * R35 "awake" window: decorative loops (heart sweep, arena aurora, neon tiles…) run for 8 s after the last touch or
 * screen change, then settle — so an idle screen stops redrawing every frame. Any touch wakes them again.
 */
object Awake {
    private val _on = kotlinx.coroutines.flow.MutableStateFlow(true)
    val on: kotlinx.coroutines.flow.StateFlow<Boolean> = _on
    private var last = 0L
    private var job: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob())
    private const val WINDOW_MS = 8_000L

    /** Main thread. Cheap to call on every pointer event: re-arms at most twice a second. */
    fun poke() {
        val now = SystemClock.uptimeMillis()
        if (_on.value && now - last < 500) return
        last = now
        _on.value = true
        job?.cancel()
        job = scope.launch { delay(WINDOW_MS); _on.value = false }
    }
}

@Composable
fun rememberAwake(): Boolean {
    val on by Awake.on.collectAsState()
    return on && !Motion.appStill && !Motion.reduced
}

/** Like animateFloat, but only ticks while the screen is awake; otherwise it holds [rest] and requests no frames. */
@Composable
fun androidx.compose.animation.core.InfiniteTransition.awakeFloat(
    initialValue: Float, targetValue: Float, animationSpec: androidx.compose.animation.core.InfiniteRepeatableSpec<Float>,
    label: String = "FloatAnimation", rest: Float = initialValue,
): androidx.compose.runtime.State<Float> {
    val awake = rememberAwake()
    val still = remember { androidx.compose.runtime.mutableFloatStateOf(rest) }
    return if (awake) animateFloat(initialValue, targetValue, animationSpec, label) else still
}
