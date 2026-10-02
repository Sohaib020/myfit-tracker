package com.myfit.tracker.ui.dashboard

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.LocalSettings
import kotlinx.coroutines.channels.Channel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * A 3D tumbler of water. The level is today's water / target. The surface stays level with the
 * real world as you tilt the phone (gravity sensor) and sloshes on a damped spring when you move it.
 *
 * Battery: frames are only requested while the simulation still has energy (or an add-water
 * animation is playing). Once settled, it is a single static drawing. Sensors are registered only
 * while this composable is on screen and the app is resumed, and never in Battery-saver motion.
 */
@Composable
fun WaterGlass3D(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    val th = LocalFitTheme.current
    val settings = LocalSettings.current
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1.05f)
    val sim = remember { SloshSim(target) }
    val frame = remember { mutableLongStateOf(0L) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }

    // Level changes from any source (this tile, the water sheet, Pip…). Rising = pour animation.
    LaunchedEffect(target) {
        if (target > sim.levelTarget + 0.002f) sim.pour(full = target >= 1f)
        sim.levelTarget = target
        wake.trySend(Unit)
    }

    // Frame loop: runs only while there is something moving, then parks on the channel.
    LaunchedEffect(Unit) {
        for (signal in wake) {
            sim.running = true
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
                last = now
                val alive = sim.step(dt)
                frame.longValue = now
                if (!alive) {
                    sim.settle()
                    frame.longValue = now + 1
                    break
                }
            }
            sim.running = false
        }
    }

    // Gravity / motion sensors — only while visible (composed) and the app is resumed.
    val motionAllowed = settings.motion != 2
    DisposableEffect(owner, motionAllowed) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        if (!motionAllowed || sm == null) return@DisposableEffect onDispose { }
        val grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val acc = if (grav == null) sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val lin = if (grav != null) sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) else null
        if (grav == null && acc == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.size < 2) return
                val significant = when (e.sensor.type) {
                    Sensor.TYPE_GRAVITY -> sim.onGravity(e.values[0], e.values[1])
                    Sensor.TYPE_ACCELEROMETER -> sim.onAccelerometer(e.values[0], e.values[1])
                    Sensor.TYPE_LINEAR_ACCELERATION -> sim.onLinear(e.values[0])
                    else -> false
                }
                if (significant && !sim.running) wake.trySend(Unit)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        var registered = false
        fun register() {
            if (registered) return
            runCatching {
                grav?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                acc?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
                lin?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            }
            registered = true
        }
        fun unregister() {
            if (!registered) return
            runCatching { sm.unregisterListener(listener) }
            registered = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)   // replays ON_RESUME if already resumed
        onDispose {
            owner.lifecycle.removeObserver(observer)
            unregister()
            sim.resetSensors()
            if (!sim.running) wake.trySend(Unit)   // let the surface ease back to level
        }
    }

    val isLight = th.isLight
    Canvas(modifier.then(if (onTap != null) Modifier.clickableNoRipple(onTap) else Modifier)) {
        frame.longValue   // redraw on every simulation frame
        drawTumbler(sim, color, isLight)
    }
}

// ---------------------------------------------------------------------------------------- physics

private class Bubble(var u: Float, var y: Float, val r: Float, val speed: Float, var phase: Float)
private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float)

/** Plain mutable state, touched only on the main thread (sensor callbacks + frame loop). */
private class SloshSim(start: Float) {
    var running = false

    // fill level (fraction of target), spring-eased
    var levelTarget = start
    var level = start
    var levelV = 0f

    // surface tilt relative to the glass, radians, canvas-clockwise positive
    var tiltTarget = 0f
    var tilt = 0f
    var tiltV = 0f

    // two sloshing modes (amplitude as a fraction of the glass's inner height)
    var a1 = 0f; var a1V = 0f
    var a2 = 0f; var a2V = 0f
    var force = 0f          // lateral acceleration (m/s²), decays when the sensor goes quiet

    // add-water animation
    var pourT = 1f          // 0..1 falling stream
    var splashT = 1f        // 0..1 ripple + shimmer
    var fullShimmer = false
    var time = 0f
    val bubbles = ArrayList<Bubble>()
    val drops = ArrayList<Drop>()

    // sensor filters
    private var lpX = 0f; private var lpY = 9.81f
    private var sRoll = 0f

    fun onGravity(gx: Float, gy: Float): Boolean {
        val mag = hypot(gx, gy)
        // phone lying flat: roll is meaningless, fade the tilt out
        val fade = ((mag - 2.5f) / 4f).coerceIn(0f, 1f)
        val roll = atan2(gx, gy).coerceIn(-MAX_TILT, MAX_TILT)
        sRoll += (roll - sRoll) * 0.35f
        tiltTarget = sRoll * fade
        return abs(tiltTarget - tilt) > 0.006f
    }

    fun onAccelerometer(ax: Float, ay: Float): Boolean {
        lpX += (ax - lpX) * 0.12f
        lpY += (ay - lpY) * 0.12f
        val g = onGravity(lpX, lpY)
        val l = onLinear(ax - lpX)
        return g || l
    }

    fun onLinear(x: Float): Boolean {
        val v = if (abs(x) > 0.35f) x.coerceIn(-15f, 15f) else 0f
        if (abs(v) > abs(force) || v == 0f) force = v
        return abs(v) > 0.8f
    }

    fun resetSensors() { tiltTarget = 0f; force = 0f; sRoll = 0f; lpX = 0f; lpY = 9.81f }

    fun pour(full: Boolean) {
        pourT = 0f; splashT = 0f; fullShimmer = full
        val r = Random
        repeat(9) {
            bubbles += Bubble(0.18f + r.nextFloat() * 0.64f, r.nextFloat() * 0.35f, 0.018f + r.nextFloat() * 0.022f, 0.45f + r.nextFloat() * 0.6f, r.nextFloat() * 6f)
        }
        repeat(7) {
            drops += Drop((r.nextFloat() - 0.5f) * 0.18f, 0f, (r.nextFloat() - 0.5f) * 0.7f, 0.55f + r.nextFloat() * 0.55f, 0.018f + r.nextFloat() * 0.016f)
        }
        a1V += if (r.nextBoolean()) 0.35f else -0.35f
        a2V -= 0.25f
    }

    /** One physics step. Returns true while anything is still moving. */
    fun step(dt: Float): Boolean {
        time += dt
        // level spring (slightly underdamped: a soft overshoot as the water rises)
        val la = 70f * (levelTarget - level) - 2f * 0.62f * sqrt(70f) * levelV
        levelV += la * dt; level += levelV * dt

        // tilt spring (underdamped → the surface swings past level and back)
        val ta = 38f * (tiltTarget - tilt) - 2f * 0.30f * sqrt(38f) * tiltV
        tiltV += ta * dt; tilt += tiltV * dt

        // slosh modes: mass-spring-damper, driven by lateral acceleration and tilt acceleration
        val drive = force * 0.22f + ta * 0.012f
        a1V += (-W1 * W1 * a1 - 2f * Z1 * W1 * a1V + drive) * dt
        a1 = (a1 + a1V * dt).coerceIn(-0.07f, 0.07f)
        a2V += (-W2 * W2 * a2 - 2f * Z2 * W2 * a2V - drive * 0.5f) * dt
        a2 = (a2 + a2V * dt).coerceIn(-0.035f, 0.035f)
        force *= exp(-dt * 8f)
        if (abs(force) < 0.05f) force = 0f

        if (pourT < 1f) pourT = (pourT + dt / 0.45f).coerceAtMost(1f)
        if (splashT < 1f) splashT = (splashT + dt / 0.95f).coerceAtMost(1f)

        val bi = bubbles.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.y += b.speed * dt * (0.6f + b.y)      // speeds up as it rises
            b.phase += dt * 7f
            if (b.y >= 1f) bi.remove()
        }
        val di = drops.iterator()
        while (di.hasNext()) {
            val d = di.next()
            d.vy -= 2.8f * dt
            d.x += d.vx * dt; d.y += d.vy * dt
            if (d.y < 0f && d.vy < 0f) di.remove()
        }

        return abs(levelTarget - level) > 0.0008f || abs(levelV) > 0.002f ||
            abs(tiltTarget - tilt) > 0.002f || abs(tiltV) > 0.004f ||
            abs(a1) + abs(a2) > 0.0006f || abs(a1V) + abs(a2V) > 0.004f ||
            force != 0f || pourT < 1f || splashT < 1f || bubbles.isNotEmpty() || drops.isNotEmpty()
    }

    /** Snap the last tiny residue so the static frame is exactly at rest. */
    fun settle() {
        level = levelTarget; levelV = 0f
        tilt = tiltTarget; tiltV = 0f
        a1 = 0f; a1V = 0f; a2 = 0f; a2V = 0f
    }

    companion object {
        const val MAX_TILT = 0.45f
        val W1 = (2 * PI * 1.5).toFloat(); const val Z1 = 0.07f
        val W2 = (2 * PI * 2.6).toFloat(); const val Z2 = 0.09f
    }
}

// ---------------------------------------------------------------------------------------- drawing

private const val ELL = 0.17f   // ellipse squash: we look at the glass from slightly above

private fun DrawScope.drawTumbler(s: SloshSim, water: Color, isLight: Boolean) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val cx = w / 2f
    val topY = h * 0.09f
    val botY = h * 0.90f
    val rxTop = w * 0.43f; val rxBot = w * 0.33f
    val t = w * 0.045f                  // wall thickness
    val baseThick = h * 0.075f
    val yB = botY - baseThick           // inner floor
    fun rxAt(y: Float): Float = rxTop + (rxBot - rxTop) * ((y - topY) / (botY - topY))
    val rxBin = rxAt(yB) - t
    val ryTop = rxTop * ELL; val ryBot = rxBot * ELL; val ryBin = rxBin * ELL

    val edge = if (isLight) Color(0xFF3C4A5C) else Color.White
    val glassFill = if (isLight) 0.10f else 0.07f

    // ---- shadow on the "table"
    val shadowShift = -tan(s.tilt) * rxBot * 0.6f
    scale(1f, 0.26f, pivot = Offset(cx + shadowShift, botY + ryBot * 0.9f)) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color.Black.copy(alpha = if (isLight) 0.22f else 0.42f), Color.Transparent),
                center = Offset(cx + shadowShift, botY + ryBot * 0.9f), radius = rxBot * 1.7f,
            ),
            radius = rxBot * 1.7f, center = Offset(cx + shadowShift, botY + ryBot * 0.9f),
        )
    }

    // ---- glass body (back wall, translucent)
    val outer = Path().apply {
        moveTo(cx - rxTop, topY)
        lineTo(cx - rxBot, botY)
        arcTo(Rect(cx - rxBot, botY - ryBot, cx + rxBot, botY + ryBot), 180f, -180f, false)
        lineTo(cx + rxTop, topY)
        arcTo(Rect(cx - rxTop, topY - ryTop, cx + rxTop, topY + ryTop), 0f, -180f, false)
        close()
    }
    drawPath(outer, Brush.horizontalGradient(
        listOf(edge.copy(alpha = glassFill * 2.2f), edge.copy(alpha = glassFill * 0.6f), edge.copy(alpha = glassFill * 0.5f), edge.copy(alpha = glassFill * 1.8f)),
        startX = cx - rxTop, endX = cx + rxTop,
    ))
    // back half of the rim, seen through the glass
    drawArc(edge.copy(alpha = 0.28f), 180f, 180f, false, Offset(cx - rxTop + t, topY - ryTop + t * ELL), Size((rxTop - t) * 2f, (ryTop - t * ELL) * 2f), style = Stroke(0.8.dp.toPx()))

    val inner = Path().apply {
        moveTo(cx - (rxTop - t), topY)
        lineTo(cx - rxBin, yB)
        arcTo(Rect(cx - rxBin, yB - ryBin, cx + rxBin, yB + ryBin), 180f, -180f, false)
        lineTo(cx + (rxTop - t), topY)
        close()
    }
    // floor of the glass (seen from above, through the water)
    drawOval(edge.copy(alpha = 0.10f), Offset(cx - rxBin, yB - ryBin), Size(rxBin * 2f, ryBin * 2f))

    // ---- water
    val fillH = yB - (topY + ryTop)
    val lvl = s.level.coerceIn(0f, 1.08f)
    if (lvl > 0.004f) {
        val surfaceY = yB - lvl * fillH * 0.9f
        val rxS = (rxAt(surfaceY) - t).coerceAtLeast(1f)
        val ryS = rxS * ELL
        val tanT = tan(s.tilt.coerceIn(-0.6f, 0.6f))
        val amp = (lvl * 6f).coerceAtMost(1f)          // tiny puddles barely slosh
        val ripAmp = (1f - s.splashT).let { it * it } * 0.028f
        fun centerY(x: Float): Float {
            val u = ((x - (cx - rxS)) / (2f * rxS)).coerceIn(0f, 1f)
            val wave = s.a1 * cos(PI.toFloat() * u) + s.a2 * cos(2f * PI.toFloat() * u) +
                ripAmp * sin(5f * PI.toFloat() * abs(u - 0.5f) * 2f - s.splashT * 20f)
            return (surfaceY + (x - cx) * tanT - wave * amp * fillH).coerceIn(topY + ryTop * 0.4f, yB + ryBin)
        }
        val n = 14
        val front = ArrayList<Offset>(n + 1); val back = ArrayList<Offset>(n + 1)
        for (i in 0..n) {
            val x = cx - rxS + 2f * rxS * i / n
            val k = (x - cx) / rxS
            val e = ryS * sqrt((1f - k * k).coerceAtLeast(0f))
            val yc = centerY(x)
            front += Offset(x, yc + e); back += Offset(x, yc - e)
        }
        val deep = Color(water.red * 0.30f, water.green * 0.42f, water.blue * 0.62f)
        val light = lerp(water, Color.White, 0.38f)

        clipPath(inner) {
            // body: from the front edge of the surface down to the floor
            val body = Path().apply {
                moveTo(cx - rxS - 3f * t, front.first().y)
                lineTo(front.first().x, front.first().y)
                smoothThrough(front)
                lineTo(cx + rxS + 3f * t, front.last().y)
                lineTo(cx + rxBin + 3f * t, yB)
                lineTo(cx + rxBin, yB)
                arcTo(Rect(cx - rxBin, yB - ryBin, cx + rxBin, yB + ryBin), 0f, 180f, false)
                lineTo(cx - rxBin - 3f * t, yB)
                close()
            }
            drawPath(body, Brush.verticalGradient(
                listOf(light.copy(alpha = 0.80f), water.copy(alpha = 0.88f), deep.copy(alpha = 0.96f)),
                startY = surfaceY - ryS, endY = yB + ryBin,
            ))
            // cylindrical shading: darker towards the walls
            drawPath(body, Brush.horizontalGradient(
                listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.32f)),
                startX = cx - rxS, endX = cx + rxS,
            ))
            // refracted light band + caustic glow on the floor
            drawPath(body, Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                startX = cx - rxS * 0.55f, endX = cx - rxS * 0.05f,
            ))
            drawOval(
                Brush.radialGradient(listOf(light.copy(alpha = 0.45f), Color.Transparent), center = Offset(cx - rxBin * 0.15f, yB), radius = rxBin),
                Offset(cx - rxBin * 0.9f, yB - ryBin * 1.2f), Size(rxBin * 1.8f, ryBin * 2.4f),
            )

            // bubbles
            val waterTop = surfaceY
            for (b in s.bubbles) {
                val by = yB - (yB - waterTop) * b.y
                val bx = cx - rxS * 0.9f + rxS * 1.8f * b.u + sin(b.phase) * w * 0.02f
                val r = b.r * w
                drawCircle(Color.White.copy(alpha = 0.55f), r, Offset(bx, by), style = Stroke(1f.coerceAtLeast(r * 0.28f)))
                drawCircle(Color.White.copy(alpha = 0.75f), r * 0.3f, Offset(bx - r * 0.35f, by - r * 0.35f))
            }

            // the surface itself (a tilted, wavy ellipse)
            val top = Path().apply {
                moveTo(back.first().x, back.first().y)
                smoothThrough(back)
                lineTo(front.last().x, front.last().y)
                smoothThrough(front.asReversed())
                close()
            }
            drawPath(top, Brush.verticalGradient(
                listOf(lerp(light, Color.White, 0.25f).copy(alpha = 0.95f), light.copy(alpha = 0.9f), water.copy(alpha = 0.92f)),
                startY = surfaceY - ryS * 1.4f, endY = surfaceY + ryS * 1.4f,
            ))
            // meniscus highlight along the back edge + front lip
            drawPath(Path().apply { moveTo(back.first().x, back.first().y); smoothThrough(back) }, Color.White.copy(alpha = 0.55f), style = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))
            drawPath(Path().apply { moveTo(front.first().x, front.first().y); smoothThrough(front) }, Color.White.copy(alpha = 0.22f), style = Stroke(0.8.dp.toPx(), cap = StrokeCap.Round))

            // splash ripple ring
            if (s.splashT < 1f) {
                val rr = rxS * (0.15f + 0.85f * s.splashT)
                val cy = centerY(cx)
                drawOval(
                    Color.White.copy(alpha = 0.6f * (1f - s.splashT)),
                    Offset(cx - rr, cy - rr * ELL), Size(rr * 2f, rr * 2f * ELL), style = Stroke(1.2.dp.toPx()),
                )
                // overflow shimmer: a band of light sweeping across a full glass
                if (s.fullShimmer) {
                    val p = cx - rxTop + 2.6f * rxTop * s.splashT
                    drawRect(Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.35f * (1f - s.splashT * 0.5f)), Color.Transparent),
                        start = Offset(p - w * 0.35f, topY), end = Offset(p + w * 0.05f, botY),
                    ))
                }
            }
        }

        // pour stream from above
        if (s.pourT < 1f) {
            val a = sin(PI.toFloat() * s.pourT)
            val sx = cx + w * 0.04f
            val endY = centerY(sx)
            val startY = (endY - (endY - 0f) * (1f - s.pourT * 0.35f)).coerceAtLeast(0f)
            drawLine(
                Brush.verticalGradient(listOf(light.copy(alpha = 0.0f), light.copy(alpha = 0.85f * a), water.copy(alpha = 0.9f * a)), startY = startY, endY = endY),
                Offset(sx, startY), Offset(sx, endY), strokeWidth = w * 0.055f * (0.5f + 0.5f * a), cap = StrokeCap.Round,
            )
        }
        // droplets thrown up by the splash
        for (d in s.drops) {
            val dx = cx + d.x * w
            val dy = centerY(cx) - d.y * h
            drawCircle(light.copy(alpha = 0.9f), d.r * w, Offset(dx, dy))
            drawCircle(Color.White.copy(alpha = 0.8f), d.r * w * 0.35f, Offset(dx - d.r * w * 0.3f, dy - d.r * w * 0.3f))
        }
    }

    // ---- thick glass base (front slab)
    val slab = Path().apply {
        moveTo(cx - rxBot, botY)
        arcTo(Rect(cx - rxBot, botY - ryBot, cx + rxBot, botY + ryBot), 180f, -180f, false)
        lineTo(cx + rxBin, yB)
        arcTo(Rect(cx - rxBin, yB - ryBin, cx + rxBin, yB + ryBin), 0f, 180f, false)
        close()
    }
    drawPath(slab, Brush.verticalGradient(listOf(edge.copy(alpha = 0.10f), edge.copy(alpha = 0.30f)), startY = yB, endY = botY + ryBot))
    drawArc(edge.copy(alpha = 0.55f), 20f, 140f, false, Offset(cx - rxBot, botY - ryBot), Size(rxBot * 2f, ryBot * 2f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))

    // ---- side walls (thickness strips)
    val leftWall = Path().apply {
        moveTo(cx - rxTop, topY); lineTo(cx - rxBot, botY); lineTo(cx - rxBin, yB); lineTo(cx - rxTop + t, topY); close()
    }
    val rightWall = Path().apply {
        moveTo(cx + rxTop, topY); lineTo(cx + rxBot, botY); lineTo(cx + rxBin, yB); lineTo(cx + rxTop - t, topY); close()
    }
    drawPath(leftWall, edge.copy(alpha = 0.22f))
    drawPath(rightWall, edge.copy(alpha = 0.14f))
    // silhouette edges
    drawLine(edge.copy(alpha = 0.55f), Offset(cx - rxTop, topY), Offset(cx - rxBot, botY), 1.dp.toPx())
    drawLine(edge.copy(alpha = 0.40f), Offset(cx + rxTop, topY), Offset(cx + rxBot, botY), 1.dp.toPx())

    // ---- refraction streaks (vertical highlights on the curved front)
    fun streak(fx: Float, wTop: Float, wBot: Float, alpha: Float, y0: Float, y1: Float) {
        val xa = cx + rxAt(y0) * fx; val xb = cx + rxAt(y1) * fx
        val p = Path().apply {
            moveTo(xa - wTop / 2, y0); lineTo(xa + wTop / 2, y0); lineTo(xb + wBot / 2, y1); lineTo(xb - wBot / 2, y1); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha), Color.White.copy(alpha = alpha * 0.15f)), startY = y0, endY = y1))
    }
    streak(-0.66f, w * 0.10f, w * 0.06f, if (isLight) 0.55f else 0.38f, topY + ryTop * 1.3f, yB - ryBin)
    streak(-0.42f, w * 0.025f, w * 0.018f, 0.30f, topY + ryTop * 2f, topY + (botY - topY) * 0.55f)
    streak(0.62f, w * 0.035f, w * 0.022f, 0.22f, topY + ryTop * 1.6f, yB - ryBin * 2f)

    // ---- rim: front half bright, inner lip, specular glint
    val rimRect = Size(rxTop * 2f, ryTop * 2f)
    drawArc(edge.copy(alpha = 0.40f), 180f, 180f, false, Offset(cx - rxTop, topY - ryTop), rimRect, style = Stroke(1.2.dp.toPx()))
    drawArc(
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.95f), edge.copy(alpha = 0.55f), Color.White.copy(alpha = 0.75f)), startX = cx - rxTop, endX = cx + rxTop),
        0f, 180f, false, Offset(cx - rxTop, topY - ryTop), rimRect, style = Stroke(1.5.dp.toPx()),
    )
    drawArc(edge.copy(alpha = 0.30f), 0f, 180f, false, Offset(cx - rxTop + t, topY - ryTop + t * ELL), Size((rxTop - t) * 2f, (ryTop - t * ELL) * 2f), style = Stroke(0.8.dp.toPx()))
    drawArc(Color.White, 118f, 34f, false, Offset(cx - rxTop, topY - ryTop), rimRect, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    drawCircle(Color.White.copy(alpha = 0.9f), 1.6.dp.toPx(), Offset(cx + rxTop * 0.55f, topY + ryTop * 0.8f))
}

/** Smooth Catmull-Rom curve through the points (path must already be at pts[0]). */
private fun Path.smoothThrough(pts: List<Offset>) {
    if (pts.size < 2) return
    for (i in 0 until pts.size - 1) {
        val p0 = pts[if (i == 0) 0 else i - 1]
        val p1 = pts[i]; val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 < pts.size) i + 2 else pts.size - 1]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}
