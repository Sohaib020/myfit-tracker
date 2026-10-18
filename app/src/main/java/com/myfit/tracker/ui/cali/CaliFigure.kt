package com.myfit.tracker.ui.cali

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.myfit.tracker.domain.Cali
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * R31 illustrated pose figure for calisthenics steps — a clean side-view athlete drawn from joint angles
 * (same model as tools/cali/figure.py). Still mode shows the end position with a faint ghost of the start, so a
 * single frame still reads as a movement; animated mode eases between the two keyframes.
 */
object Fig {
    // segment lengths in body units (≈ 1.75 m tall)
    private const val T = 0.50f; private const val NECK = 0.05f; const val HEAD = 0.105f
    private const val A = 0.29f; private const val F = 0.27f; private const val L = 0.44f; private const val S = 0.42f; private const val FT = 0.13f

    const val HIP = 0; const val SH = 1; const val HD = 2; const val EL1 = 3; const val WR1 = 4; const val KN1 = 5; const val AN1 = 6; const val TO1 = 7
    const val EL2 = 8; const val WR2 = 9; const val KN2 = 10; const val AN2 = 11; const val TO2 = 12

    /** Pose angles: t, hd, a1, f1, a2, f2, l1, s1, l2, s2, ft1, ft2 (degrees; 0 = down, 90 = forward, 180 = up). */
    fun joints(p: FloatArray): FloatArray {
        val j = FloatArray(26)
        fun set(i: Int, x: Float, y: Float) { j[i * 2] = x; j[i * 2 + 1] = y }
        fun go(to: Int, from: Int, deg: Float, len: Float) {
            val r = Math.toRadians(deg.toDouble())
            set(to, j[from * 2] + sin(r).toFloat() * len, j[from * 2 + 1] + cos(r).toFloat() * len)
        }
        go(SH, HIP, p[0], T); go(HD, SH, p[1], NECK + HEAD)
        go(EL1, SH, p[2], A); go(WR1, EL1, p[3], F); go(EL2, SH, p[4], A); go(WR2, EL2, p[5], F)
        go(KN1, HIP, p[6], L); go(AN1, KN1, p[7], S); go(TO1, AN1, p[10], FT)
        go(KN2, HIP, p[8], L); go(AN2, KN2, p[9], S); go(TO2, AN2, p[11], FT)
        return j
    }

    fun lerp(a: FloatArray, b: FloatArray, t: Float) = FloatArray(a.size) { i ->
        var d = (b[i] - a[i]) % 360f; if (d > 180f) d -= 360f; if (d < -180f) d += 360f
        a[i] + d * t
    }

    /** A step's figure laid out once: anchor joint, floor and static props, plus bounds covering both keyframes. */
    class Layout(val poses: List<FloatArray>, val props: List<String>) {
        private val wristAnchored = props.any { it in setOf("bar", "rings", "pbars", "plt", "box_h") }
        val hanging = (props.contains("bar") || props.contains("rings")) && "floor" !in props
        val anchor: Int
        var floor: Float? = null; private set
        val boxes = ArrayList<FloatArray>()     // x, top, width (bottom = floor)
        var wall: Float? = null; private set
        var bar: Offset? = null; private set
        val minX: Float; val maxX: Float; val minY: Float; val maxY: Float

        init {
            val j0 = joints(poses[0])
            val low = (0 until 13).maxOf { j0[it * 2 + 1] }.let { max(it, j0[HD * 2 + 1] + HEAD) }
            anchor = when {
                wristAnchored -> WR1
                j0[TO1 * 2 + 1] >= low - 0.07f -> TO1
                j0[WR1 * 2 + 1] >= low - 0.07f -> WR1
                else -> (0 until 13).maxBy { j0[it * 2 + 1] }
            }
            val a0 = anchored(j0)
            val lowA = (0 until 13).maxOf { a0[it * 2 + 1] }.let { max(it, a0[HD * 2 + 1] + HEAD) }
            floor = when {
                hanging -> null
                "pbars" in props -> 0.95f
                "plt" in props -> 0.06f
                props.any { it.startsWith("box_f") } -> {   // boxed feet: floor at the lowest free contact
                    val skip = setOf(TO1, AN1, TO2, AN2, KN2).let { if ("box_f2" in props) setOf(AN2, TO2, KN2) else it }
                    (0 until 13).filter { it !in skip }.maxOf { a0[it * 2 + 1] }.let { max(it, a0[HD * 2 + 1] + HEAD) }
                }
                "box_h" in props -> (0 until 13).filter { it != WR1 && it != WR2 }.maxOf { a0[it * 2 + 1] }
                else -> lowA
            }
            if ("bar" in props || "rings" in props) bar = Offset(a0[WR1 * 2], a0[WR1 * 2 + 1])
            val fl = floor
            if (fl != null) {
                if ("box_h" in props) boxes += floatArrayOf(a0[WR1 * 2], a0[WR1 * 2 + 1], 0.42f)
                if ("box_f" in props) boxes += floatArrayOf(a0[TO1 * 2], a0[TO1 * 2 + 1], 0.42f)
                if ("box_f2" in props) boxes += floatArrayOf(a0[AN2 * 2] - 0.05f, a0[AN2 * 2 + 1] + 0.03f, 0.42f)
                if ("box_hip" in props) boxes += floatArrayOf(a0[AN1 * 2] - 0.38f, fl - 0.45f, 0.42f)
            }
            val all = poses.map { anchored(joints(it)) }
            var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            all.forEach { a ->
                for (i in 0 until 13) { x0 = min(x0, a[i * 2]); x1 = max(x1, a[i * 2]); y0 = min(y0, a[i * 2 + 1]); y1 = max(y1, a[i * 2 + 1]) }
                y0 = min(y0, a[HD * 2 + 1] - HEAD); x0 = min(x0, a[HD * 2] - HEAD); x1 = max(x1, a[HD * 2] + HEAD)
            }
            if ("wall_f" in props) { wall = x1 + 0.03f; x1 += 0.08f }
            if ("wall_b" in props) { wall = x0 - 0.03f; x0 -= 0.08f }
            boxes.forEach { b -> x0 = min(x0, b[0] - b[2] / 2); x1 = max(x1, b[0] + b[2] / 2) }
            if ("pbars" in props) { val c = a0[WR1 * 2]; x0 = min(x0, c - 0.32f); x1 = max(x1, c + 0.32f) }
            bar?.let { y0 = min(y0, it.y - 0.08f) }
            fl?.let { y1 = max(y1, it) }
            minX = x0 - 0.08f; maxX = x1 + 0.08f; minY = y0 - 0.06f; maxY = y1 + 0.03f
        }

        fun anchored(j: FloatArray): FloatArray {
            val ax = j[anchor * 2]; val ay = j[anchor * 2 + 1]
            return FloatArray(j.size) { i -> if (i % 2 == 0) j[i] - ax else j[i] - ay }
        }
    }

    fun draw(ds: DrawScope, lay: Layout, pose: FloatArray, color: Color, ghost: FloatArray?, dark: Boolean) = with(ds) {
        val w = size.width; val h = size.height
        val sc = min(w / (lay.maxX - lay.minX), h / (lay.maxY - lay.minY))
        val ox = (w - (lay.maxX - lay.minX) * sc) / 2 - lay.minX * sc
        val oy = (h - (lay.maxY - lay.minY) * sc) - lay.minY * sc
        fun P(x: Float, y: Float) = Offset(ox + x * sc, oy + y * sc)
        val line = if (dark) Color.White else Color(0xFF1B1E27)
        val propC = line.copy(alpha = 0.32f)
        val propS = 0.035f * sc
        // floor with a soft glow
        lay.floor?.let { f ->
            val y = oy + f * sc
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, line.copy(alpha = 0.28f), line.copy(alpha = 0.28f), Color.Transparent)),
                Offset(0f, y), Offset(w, y), strokeWidth = 0.022f * sc)
            drawOval(Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center = Offset(w / 2, y), radius = w * 0.4f),
                Offset(w * 0.1f, y - 0.05f * sc), Size(w * 0.8f, 0.1f * sc))
        }
        lay.wall?.let { x -> val px = ox + x * sc
            drawLine(propC, Offset(px, oy + lay.minY * sc), Offset(px, lay.floor?.let { oy + it * sc } ?: h), strokeWidth = propS * 1.6f, cap = StrokeCap.Round) }
        lay.boxes.forEach { b ->
            val tl = P(b[0] - b[2] / 2, b[1]); val br = P(b[0] + b[2] / 2, lay.floor ?: (b[1] + 0.4f))
            drawRoundRect(line.copy(alpha = 0.08f), tl, Size(br.x - tl.x, br.y - tl.y), CornerRadius(0.03f * sc))
            drawRoundRect(propC, tl, Size(br.x - tl.x, br.y - tl.y), CornerRadius(0.03f * sc), style = Stroke(propS * 0.7f))
        }
        if ("pbars" in lay.props) {
            val j = lay.anchored(joints(lay.poses[0])); val c = P(j[WR1 * 2], j[WR1 * 2 + 1])
            drawLine(propC, Offset(c.x, c.y), Offset(c.x, oy + (lay.floor ?: 1f) * sc), strokeWidth = propS * 1.2f)
        }
        val cur = lay.anchored(joints(pose))
        ghost?.let { g -> figure(this, lay.anchored(joints(g)), ::P, color.copy(alpha = 0.13f), color.copy(alpha = 0.08f), line.copy(alpha = 0.10f), sc, flat = true) }
        // bar / rings sit behind the near hand
        lay.bar?.let { b ->
            val c = P(b.x, b.y)
            if ("rings" in lay.props) {
                drawLine(propC, Offset(c.x, 0f), Offset(c.x, c.y - 0.07f * sc), strokeWidth = propS * 0.5f)
                drawCircle(line.copy(alpha = 0.55f), 0.07f * sc, c, style = Stroke(propS * 0.8f))
            } else {
                drawLine(propC, Offset(c.x - 0.45f * sc, c.y), Offset(c.x + 0.45f * sc, c.y), strokeWidth = propS * 0.5f)
                drawCircle(line.copy(alpha = 0.7f), 0.034f * sc, c)
            }
        }
        if ("pbars" in lay.props || "plt" in lay.props) {
            val j = lay.anchored(joints(lay.poses[0])); val c = P(j[WR1 * 2], j[WR1 * 2 + 1])
            val half = if ("pbars" in lay.props) 0.32f else 0.12f
            drawLine(line.copy(alpha = 0.55f), Offset(c.x - half * sc, c.y + 0.02f * sc), Offset(c.x + half * sc, c.y + 0.02f * sc), strokeWidth = propS * 1.2f, cap = StrokeCap.Round)
        }
        if ("band" in lay.props) drawLine(Color(0xFFFF6B6B).copy(alpha = 0.8f), P(cur[WR1 * 2], cur[WR1 * 2 + 1]), P(cur[AN1 * 2], cur[AN1 * 2 + 1]), strokeWidth = 0.02f * sc)
        figure(this, cur, ::P, color, color.copy(alpha = 0.55f).compositeOverDark(dark), line, sc, flat = false)
    }

    private fun Color.compositeOverDark(dark: Boolean) = if (dark) Color(red * 0.55f + 0.05f, green * 0.55f + 0.05f, blue * 0.55f + 0.06f, 1f)
        else Color(red * 0.6f + 0.35f, green * 0.6f + 0.35f, blue * 0.6f + 0.35f, 1f)

    private fun figure(ds: DrawScope, j: FloatArray, P: (Float, Float) -> Offset, near: Color, far: Color, line: Color, sc: Float, flat: Boolean) = with(ds) {
        fun p(i: Int) = P(j[i * 2], j[i * 2 + 1])
        fun seg(a: Int, b: Int, wd: Float, c: Color) = drawLine(c, p(a), p(b), strokeWidth = wd * sc, cap = StrokeCap.Round)
        val limb = 0.075f; val shin = 0.068f; val arm = 0.06f; val fore = 0.052f
        // far side first
        seg(HIP, KN2, limb, far); seg(KN2, AN2, shin, far); seg(AN2, TO2, 0.045f, far)
        seg(SH, EL2, arm, far); seg(EL2, WR2, fore, far)
        // torso: tapered capsule with a light rim
        val hipP = p(HIP); val shP = p(SH)
        drawLine(near, hipP, shP, strokeWidth = 0.13f * sc, cap = StrokeCap.Round)
        if (!flat) drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), shP, hipP), shP, hipP, strokeWidth = 0.05f * sc, cap = StrokeCap.Round)
        // head
        val hd = p(HD)
        drawCircle(near, Fig.HEAD * sc, hd)
        if (!flat) drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), center = Offset(hd.x - 0.03f * sc, hd.y - 0.04f * sc), radius = Fig.HEAD * sc),
            Fig.HEAD * sc, hd)
        // near leg
        seg(HIP, KN1, limb, near); seg(KN1, AN1, shin, near); seg(AN1, TO1, 0.048f, near)
        // near arm in a light tone so it reads in front of the body
        val armC = if (flat) near else Color(min(1f, near.red * 0.45f + 0.6f), min(1f, near.green * 0.45f + 0.6f), min(1f, near.blue * 0.45f + 0.6f), near.alpha)
        seg(SH, EL1, arm, armC); seg(EL1, WR1, fore, armC)
        if (!flat) { drawCircle(armC, 0.036f * sc, p(WR1)); drawCircle(near, 0.04f * sc, p(KN1)) }
    }
}

/** One calisthenics step's figure: still (with a ghost of the start) or gently animated between keyframes. */
@Composable
fun CaliFigure(step: Cali.Step, color: Color, modifier: Modifier = Modifier, animate: Boolean = false, dark: Boolean = true) {
    val ctx = LocalContext.current
    val d = remember { Cali.data(ctx) }
    val lay = remember(step.key) {
        val ps = step.poses.mapNotNull { d.poses[it] }.ifEmpty { listOf(FloatArray(12)) }
        Fig.Layout(ps, step.props)
    }
    val two = lay.poses.size > 1 && !lay.poses[0].contentEquals(lay.poses[1])
    if (animate && two) {
        val tr = rememberInfiniteTransition(label = "cali")
        val t by tr.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
            durationMillis = 3200
            0f at 0 using LinearEasing; 0f at 500 using FastOutSlowInEasing; 1f at 1500 using LinearEasing; 1f at 2100 using FastOutSlowInEasing; 0f at 3200
        }, RepeatMode.Restart), label = "t")
        Canvas(modifier) { Fig.draw(this, lay, Fig.lerp(lay.poses[0], lay.poses[1], t), color, null, dark) }
    } else if (animate) {
        // holds: a slow breathing sway so the figure feels alive
        val tr = rememberInfiniteTransition(label = "caliHold")
        val t by tr.animateFloat(0f, 1f, infiniteRepeatable(androidx.compose.animation.core.tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t")
        val p0 = lay.poses[0]
        Canvas(modifier) { Fig.draw(this, lay, FloatArray(p0.size) { i -> p0[i] + if (i == 0) (t - 0.5f) * 1.6f else 0f }, color, null, dark) }
    } else {
        Canvas(modifier) { Fig.draw(this, lay, lay.poses.last(), color, if (two) lay.poses[0] else null, dark) }
    }
}

