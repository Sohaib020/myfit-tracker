package com.myfit.tracker.ui.onboarding

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RoundedRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.domain.LengthUnit
import com.myfit.tracker.domain.PlanProfile
import com.myfit.tracker.domain.Units
import com.myfit.tracker.domain.WeightUnit
import com.myfit.tracker.ui.components.Caption
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.FitType
import com.myfit.tracker.ui.theme.Glass
import com.myfit.tracker.ui.theme.GlassChip
import com.myfit.tracker.ui.theme.LocalFitTheme
import com.myfit.tracker.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ====================================================================== body art

/**
 * R36 onboarding characters: assets/onboarding/body/<g>_bf<1..6>.webp and <g>_side.webp (g = m / f), all on a
 * 360×640 canvas with the figure 92 % tall, feet on a common baseline (tools/bodyart/post.py) — so they line up.
 */
private object BodyArt {
    private val cache = android.util.LruCache<String, ImageBitmap>(24 * 1024 * 1024)
    suspend fun get(c: android.content.Context, id: String): ImageBitmap? = cache.get(id) ?: withContext(Dispatchers.IO) {
        runCatching { c.assets.open("onboarding/body/$id.webp").use { BitmapFactory.decodeStream(it) }.asImageBitmap() }.getOrNull()
    }?.also { cache.put(id, it) }
}

@Composable
private fun rememberBody(id: String): ImageBitmap? {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, id) { value = BodyArt.get(ctx, id) }
    return img
}

private fun g(female: Boolean) = if (female) "f" else "m"

/** Draws a body image (360×640 canvas) so the canvas fills [dst] height, centred, optionally cropped to its top part. */
private fun DrawScope.drawBody(img: ImageBitmap, alpha: Float = 1f, topFrac: Float = 0f, bottomFrac: Float = 1f, scaleX: Float = 1f) {
    val srcTop = (img.height * topFrac).toInt(); val srcH = (img.height * (bottomFrac - topFrac)).toInt().coerceAtLeast(1)
    val s = size.height / srcH
    val w = img.width * s * scaleX
    drawImage(img, IntOffset(0, srcTop), IntSize(img.width, srcH),
        IntOffset(((size.width - w) / 2).toInt(), 0), IntSize(w.toInt(), size.height.toInt()), alpha = alpha)
}

// ====================================================================== vector body (consistent character)

/**
 * A single consistent cartoon character, drawn procedurally so that the face, hair, beard (male) / ponytail
 * (female) and pose stay IDENTICAL at every level — only the belly, shoulders, arms and legs grow with [fat].
 * [fat] is 0 (lean) .. 1 (heaviest). This replaces the old 6-frame cross-fade, whose separately-rendered
 * images were six different-looking people, so the picker no longer "swaps" the character's identity.
 */
private val VB_SKIN_M  = Color(0xFFE0A878)
private val VB_SKIN_F  = Color(0xFFF1C6A5)
private val VB_HAIR    = Color(0xFF2B2B2B)
private val VB_SHOE    = Color(0xFFFAFAFA)
private val VB_SHOELINE = Color(0xFF2BB673)
private val VB_WEAR_M  = Color(0xFF3A3F47)   // shorts
private val VB_WEAR_F  = Color(0xFF2E333B)   // top + leggings

@Composable
fun VectorBodyCharacter(female: Boolean, fat: Float, modifier: Modifier = Modifier) {
    val skin = if (female) VB_SKIN_F else VB_SKIN_M
    val wear = if (female) VB_WEAR_F else VB_WEAR_M
    Canvas(modifier.fillMaxSize()) {
        drawBodyCharacter(female, fat.coerceIn(0f, 1f), skin, wear)
    }
}

private fun DrawScope.drawBodyCharacter(female: Boolean, t: Float, skin: Color, wear: Color) {
    val H = size.height; val W = size.width
    val cx = W / 2f
    val hair = VB_HAIR

    // vertical layout (fractions of height)
    val footY = H * 0.935f; val kneeY = H * 0.775f; val hipY = H * 0.60f
    val waistY = H * 0.50f; val chestY = H * 0.315f; val headCY = H * 0.150f
    val headR = H * 0.115f

    // widths (grow with t)
    val shoulderW = H * (0.185f + 0.075f * t)
    val bellyW    = H * (0.135f + 0.21f * t)
    val bellyH    = H * (0.085f + 0.09f * t)
    val hipW      = H * (0.15f + 0.15f * t)
    val armW      = H * (0.026f + 0.034f * t)
    val legW      = H * (0.052f + 0.02f * t)
    val legCxL = cx - (hipW * 0.5f - legW * 0.5f)
    val legCxR = cx + (hipW * 0.5f - legW * 0.5f)
    val shinColor = if (female) wear else skin

    // ground shadow
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(cx - H * 0.14f, footY + H * 0.02f), Size(H * 0.28f, H * 0.045f))

    // legs + shoes
    fun leg(cxl: Float) {
        drawRoundRect(RoundedRect(cxl - legW * 0.5f, kneeY, cxl + legW * 0.5f, footY, CornerRadius(legW * 0.4f)), color = shinColor)
        drawRoundRect(RoundedRect(cxl - legW * 0.55f, hipY, cxl + legW * 0.55f, kneeY + H * 0.02f, CornerRadius(legW * 0.5f)), color = wear)
        drawRoundRect(RoundedRect(cxl - legW * 0.75f, footY - H * 0.01f, cxl + legW * 0.85f, footY + H * 0.045f, CornerRadius(H * 0.02f)), color = VB_SHOE)
        drawLine(VB_SHOELINE, Offset(cxl - legW * 0.55f, footY + H * 0.028f), Offset(cxl + legW * 0.65f, footY + H * 0.028f), H * 0.006f)
    }
    leg(legCxL); leg(legCxR)

    // torso + belly
    val torsoW = shoulderW * 0.6f + bellyW * 0.4f
    drawRoundRect(RoundedRect(cx - torsoW * 0.5f, chestY, cx + torsoW * 0.5f, waistY + bellyH * 0.2f, CornerRadius(torsoW * 0.35f)), color = if (female) wear else skin)
    val bellyCY = waistY + bellyH * 0.1f
    drawOval(skin, Offset(cx - bellyW * 0.5f, bellyCY - bellyH * 0.5f), Size(bellyW, bellyH))
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - bellyW * 0.02f, bellyCY - bellyW * 0.02f), Size(bellyW * 0.04f, bellyW * 0.04f))

    // arms + hands (skin; a female top is sleeveless)
    val armTop = chestY + H * 0.02f; val armBot = waistY + bellyH * 0.25f
    val armLx = cx - shoulderW * 0.5f - armW * 0.5f; val armRx = cx + shoulderW * 0.5f + armW * 0.5f
    drawRoundRect(RoundedRect(armLx, armTop, armLx + armW, armBot, CornerRadius(armW * 0.5f)), color = skin)
    drawRoundRect(RoundedRect(armRx, armTop, armRx + armW, armBot, CornerRadius(armW * 0.5f)), color = skin)
    drawOval(skin, Offset(armLx + armW * 0.05f, armBot - armW * 0.3f), Size(armW * 0.9f, armW * 1.1f))
    drawOval(skin, Offset(armRx + armW * 0.05f, armBot - armW * 0.3f), Size(armW * 0.9f, armW * 1.1f))

    // neck
    val neckW = H * 0.05f
    drawRoundRect(RoundedRect(cx - neckW * 0.5f, headCY + headR * 0.55f, cx + neckW * 0.5f, chestY + H * 0.01f, CornerRadius(neckW * 0.3f)), color = skin)

    // ponytail (female) sits behind the head
    val faceW = headR * 1.7f; val faceH = headR * 1.9f
    if (female) {
        drawOval(hair, Offset(cx + faceW * 0.35f, headCY - faceH * 0.55f), Size(faceW * 0.5f, faceH * 0.7f))
        drawRoundRect(RoundedRect(cx + faceW * 0.55f, headCY - faceH * 0.3f, cx + faceW * 0.75f, headCY + faceH * 0.5f, CornerRadius(faceW * 0.1f)), color = hair)
    }

    // ears + face + hair cap
    drawOval(skin, Offset(cx - headR * 1.0f, headCY - headR * 0.12f), Size(headR * 0.28f, headR * 0.5f))
    drawOval(skin, Offset(cx + headR * 0.72f, headCY - headR * 0.12f), Size(headR * 0.28f, headR * 0.5f))
    drawOval(skin, Offset(cx - faceW / 2, headCY - faceH / 2), Size(faceW, faceH))
    drawOval(hair, Offset(cx - faceW * 0.55f, headCY - faceH * 0.55f), Size(faceW * 1.1f, faceH * 0.52f))

    // beard (male)
    if (!female) {
        val jawW = faceW * 0.82f; val jawH = faceH * 0.5f
        drawOval(hair, Offset(cx - jawW / 2, headCY + faceH * 0.05f), Size(jawW, jawH))
    }

    // eyes + brows + nose + smile
    val eyeY = headCY - faceH * 0.02f; val eyeDX = faceW * 0.26f
    val eyeW = faceW * 0.22f; val eyeH = faceW * 0.24f
    for (s in listOf(-1f, 1f)) {
        val ex = cx + s * eyeDX
        drawOval(Color.White, Offset(ex - eyeW / 2, eyeY - eyeH / 2), Size(eyeW, eyeH))
        drawOval(Color(0xFF3A2E2A), Offset(ex - eyeW * 0.2f, eyeY - eyeH * 0.15f), Size(eyeW * 0.4f, eyeH * 0.5f))
        drawOval(Color.White, Offset(ex + eyeW * 0.0f, eyeY - eyeH * 0.25f), Size(eyeW * 0.16f, eyeH * 0.18f))
        val bw = eyeW * 0.5f; val browY = eyeY - eyeH * 0.8f
        drawRoundRect(RoundedRect(ex - bw, browY - H * 0.004f, ex + bw, browY + H * 0.004f, CornerRadius(H * 0.004f)), color = hair)
    }
    drawOval(Color.Black.copy(alpha = 0.10f), Offset(cx - faceW * 0.03f, headCY + faceH * 0.12f), Size(faceW * 0.06f, faceH * 0.05f))
    val mouthCY = headCY + faceH * 0.30f
    drawArc(Color(0xFF7A4A3A), 20f, 140f, false, Offset(cx - faceW * 0.18f, mouthCY - faceW * 0.16f), Size(faceW * 0.36f, faceW * 0.32f),
        style = Stroke(H * 0.006f, cap = StrokeCap.Round))
}

// ====================================================================== goal

private data class GoalOpt(val key: String, val title: String, val body: Int)
private val GOALS = listOf(
    GoalOpt(PlanProfile.LOSE, "Lose weight", 4), GoalOpt(PlanProfile.BUILD, "Build muscle", 1),
    GoalOpt(PlanProfile.FIT, "Get fit & healthy", 2), GoalOpt(PlanProfile.GAIN, "Gain weight", 3),
)

@Composable
fun GoalPage(selected: String?, female: Boolean, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GOALS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { o -> ImageChoice(o.title, "${g(female)}_bf${o.body}", selected == o.key, Modifier.weight(1f).height(210.dp)) { onPick(o.key) } }
            }
        }
    }
}

/** A tall glass card: character head→waist rising out of the card, title at the bottom, accent ring when chosen. */
@Composable
private fun ImageChoice(title: String, art: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val th = LocalFitTheme.current
    val img = rememberBody(art)
    val sel by animateFloatAsState(if (selected) 1f else 0f, Motion.snappy(), label = "sel")
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.graphicsLayer { val k = 1f + 0.03f * sel; scaleX = k; scaleY = k }.clip(shape)
        .background(Brush.verticalGradient(listOf(th.text.copy(alpha = 0.04f), (if (selected) th.accent else th.text).copy(alpha = if (selected) 0.28f else 0.09f))))
        .border((1f + 1.5f * sel).dp, if (selected) th.accentBright else th.text.copy(alpha = 0.12f), shape)
        .clickableNoRipple(onClick)) {
        if (img != null) Canvas(Modifier.fillMaxSize().padding(bottom = 34.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(0.7f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn) }) {
            drawBody(img, topFrac = 0.04f, bottomFrac = 0.55f)
        }
        Text(title, style = FitType.section, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 6.dp, end = 6.dp))
    }
}

// ====================================================================== follow-ups

val TRIED = listOf("improve" to "Succeeded, want to improve", "rebound" to "Succeeded but rebounded", "failed" to "Tried but didn't work", "never" to "Never tried")

@Composable
fun TriedPage(selected: String?, onPick: (String) -> Unit) {
    val th = LocalFitTheme.current
    val emoji = mapOf("improve" to "😏", "rebound" to "😅", "failed" to "😣", "never" to "😐")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TRIED.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (k, label) ->
                    val selected0 = selected == k
                    val shape = RoundedCornerShape(24.dp)
                    val draw = remember(k) { Animatable(0f) }
                    LaunchedEffect(k) { delay(120); draw.animateTo(1f, tween(900)) }
                    Box(Modifier.weight(1f).height(170.dp).clip(shape)
                        .background(if (selected0) th.accent.copy(alpha = 0.22f) else th.text.copy(alpha = 0.07f))
                        .border(if (selected0) 2.dp else 1.dp, if (selected0) th.accentBright else th.text.copy(alpha = 0.10f), shape)
                        .clickableNoRipple { onPick(k) }) {
                        Canvas(Modifier.fillMaxWidth().height(90.dp).padding(horizontal = 18.dp, vertical = 16.dp)) {
                            val w = size.width; val h = size.height
                            val p = Path().apply {
                                when (k) {
                                    "improve" -> { moveTo(0f, h * 0.35f); cubicTo(w * 0.25f, h * 0.0f, w * 0.45f, h * 0.2f, w * 0.6f, h * 0.65f); cubicTo(w * 0.72f, h * 0.95f, w * 0.85f, h * 0.85f, w, h * 0.85f) }
                                    "rebound" -> { moveTo(0f, h * 0.3f); cubicTo(w * 0.3f, h * 1.0f, w * 0.65f, h * 1.0f, w, h * 0.15f) }
                                    "failed" -> { moveTo(0f, h * 0.7f); cubicTo(w * 0.4f, h * 0.7f, w * 0.7f, h * 0.55f, w, h * 0.2f) }
                                    else -> { moveTo(0f, h * 0.6f); lineTo(w, h * 0.55f) }
                                }
                            }
                            val pm = PathMeasure(); pm.setPath(p, false)
                            val seg = Path(); pm.getSegment(0f, pm.length * draw.value, seg, true)
                            drawPath(seg, Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF7043))), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text(emoji[k] ?: "", fontSize = 26.sp, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
                        Text(label, style = FitType.label, color = th.text, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
                    }
                }
            }
        }
    }
}

val MUSCLE_GOALS = listOf("outfits" to "Look better in my clothes", "strength" to "Build real strength", "confidence" to "Boost confidence & energy")

@Composable
fun MuscleGoalPage(selected: String?, female: Boolean, onPick: (String) -> Unit) {
    val th = LocalFitTheme.current
    val art = listOf(2, 1, 1)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MUSCLE_GOALS.forEachIndexed { i, (k, label) ->
            val sel = selected == k
            val shape = RoundedCornerShape(26.dp)
            val img = rememberBody("${g(female)}_bf${art[i]}")
            Box(Modifier.fillMaxWidth().height(118.dp).clip(shape)
                .background(Brush.horizontalGradient(listOf((if (sel) th.accent else th.text).copy(alpha = if (sel) 0.30f else 0.08f), th.text.copy(alpha = 0.03f))))
                .border(if (sel) 2.dp else 1.dp, if (sel) th.accentBright else th.text.copy(alpha = 0.10f), shape)
                .clickableNoRipple { onPick(k) }) {
                Text(label, style = FitType.section, color = th.text, modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).fillMaxWidth(0.6f))
                if (img != null) Canvas(Modifier.align(Alignment.CenterEnd).width(130.dp).fillMaxHeight().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent { drawContent(); drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.Black), blendMode = BlendMode.DstIn) }) {
                    drawBody(img, topFrac = 0.03f, bottomFrac = 0.42f)
                }
            }
        }
    }
}

// ====================================================================== focus areas

/** Anchor points on the front body (fractions of the 360×640 canvas) for each callout. */
private val ANCHORS = mapOf(
    // cartoon proportions (big head): shoulders sit at ~29 % of the canvas, hips at ~55 %
    "Back" to Offset(0.62f, 0.30f), "Chest" to Offset(0.56f, 0.345f), "Arms" to Offset(0.33f, 0.40f),
    "Abs" to Offset(0.50f, 0.455f), "Glutes" to Offset(0.43f, 0.555f), "Legs" to Offset(0.45f, 0.72f), "Full body" to Offset(0.55f, 0.90f),
)

@Composable
fun FocusPage(selected: Set<String>, female: Boolean, onChange: (Set<String>) -> Unit) {
    val th = LocalFitTheme.current
    val img = rememberBody("${g(female)}_bf2")
    val labels = PlanProfile.FOCUS + "Full body"
    BoxWithConstraints(Modifier.fillMaxWidth().height(470.dp)) {
        val d = LocalDensity.current
        val W = with(d) { maxWidth.toPx() }; val H = with(d) { maxHeight.toPx() }
        // body box: left 58 % of the width, full height, 360:640 canvas centred in it
        val bodyW = W * 0.58f
        val canW = minOf(bodyW, H * 360f / 640f); val canH = canW * 640f / 360f
        val canX = (bodyW - canW) / 2f; val canY = (H - canH) / 2f
        val chipX = W * 0.60f; val chipH = with(d) { 44.dp.toPx() }
        val gap = (H - chipH * labels.size) / (labels.size - 1).coerceAtLeast(1)
        val accent = th.accentBright; val dim = th.textFaint
        Canvas(Modifier.fillMaxSize()) {
            if (img != null) {
                val s = canH / img.height
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(canX.toInt(), canY.toInt()), IntSize((img.width * s).toInt(), canH.toInt()))
            }
            labels.forEachIndexed { i, l ->
                val on = l in selected || (l != "Full body" && selected.size == PlanProfile.FOCUS.size)
                val a = ANCHORS[l] ?: return@forEachIndexed
                val p0 = Offset(canX + a.x * canW, canY + a.y * canH)
                val p1 = Offset(chipX, i * (chipH + gap) + chipH / 2)
                drawLine(if (on) accent else dim, p0, p1, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                drawCircle(Color.White, 6.dp.toPx(), p0); drawCircle(if (on) accent else dim, 4.dp.toPx(), p0)
            }
        }
        labels.forEachIndexed { i, l ->
            val on = l in selected || (l == "Full body" && selected.size == PlanProfile.FOCUS.size)
            val y = with(d) { (i * (chipH + gap)).toDp() }
            Box(Modifier.offset(x = with(d) { chipX.toDp() }, y = y).width(maxWidth * 0.40f).height(44.dp).clip(RoundedCornerShape(16.dp))
                .background(if (on) Brush.horizontalGradient(listOf(th.accentBright, th.accent)) else Brush.horizontalGradient(listOf(th.text.copy(alpha = 0.10f), th.text.copy(alpha = 0.06f))))
                .clickableNoRipple {
                    onChange(when {
                        l == "Full body" -> if (selected.size == PlanProfile.FOCUS.size) emptySet() else PlanProfile.FOCUS.toSet()
                        l in selected -> selected - l
                        else -> selected + l
                    })
                }, contentAlignment = Alignment.Center) {
                Text(l, style = FitType.label, color = if (on) th.onAccent else th.text)
            }
        }
    }
}

// ====================================================================== height

/**
 * Height chart: the character stands next to a wall ruler; drag anywhere (or on the ruler) and they grow or shrink,
 * the line on their head and the big number follow. Works in cm or ft/in.
 */
@Composable
fun HeightPage(cm: Double, unit: LengthUnit, female: Boolean, onCm: (Double) -> Unit, onUnit: (LengthUnit) -> Unit) {
    val th = LocalFitTheme.current
    val img = rememberBody("${g(female)}_side")
    val minCm = 120.0; val maxCm = 220.0; val topCm = 230.0
    val shown by animateFloatAsState(cm.toFloat(), spring(0.85f, 500f), label = "h")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassChip("ft / in", unit == LengthUnit.IN, { onUnit(LengthUnit.IN) })
            GlassChip("cm", unit == LengthUnit.CM, { onUnit(LengthUnit.CM) })
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp).pointerInput(unit) {
            fun set(y: Float) {
                val v = (size.height - y) / size.height * topCm
                val clamped = v.coerceIn(minCm, maxCm)
                onCm(if (unit == LengthUnit.CM) clamped.roundToInt().toDouble() else ((clamped / Units.CM_PER_IN).roundToInt() * Units.CM_PER_IN))
            }
            detectVerticalDragGestures(onDragStart = { set(it.y) }) { ch, _ -> set(ch.position.y) }
        }.pointerInput(unit) { detectTapGestures { /* taps don't move the value: dragging does */ } }) {
            val d = LocalDensity.current
            val H = with(d) { maxHeight.toPx() }; val W = with(d) { maxWidth.toPx() }
            val headY = H - H * (shown / topCm.toFloat())
            val accent = th.accentBright; val txt = th.textDim; val tick = th.text.copy(alpha = 0.28f)
            Canvas(Modifier.fillMaxSize()) {
                // ruler on the right
                val rx = size.width - 8.dp.toPx()
                if (unit == LengthUnit.CM) {
                    var v = 90
                    while (v <= topCm) {
                        val y = H - H * (v / topCm.toFloat())
                        val major = v % 10 == 0
                        drawLine(tick, Offset(rx - (if (major) 22.dp.toPx() else 11.dp.toPx()), y), Offset(rx, y), (if (major) 1.6f else 1f).dp.toPx())
                        v += 2
                    }
                } else {
                    var inch = 36
                    while (inch * Units.CM_PER_IN <= topCm) {
                        val y = H - H * ((inch * Units.CM_PER_IN) / topCm).toFloat()
                        val major = inch % 12 == 0
                        drawLine(tick, Offset(rx - (if (major) 22.dp.toPx() else 11.dp.toPx()), y), Offset(rx, y), (if (major) 1.6f else 1f).dp.toPx())
                        inch += 1
                    }
                }
                // character: head on the line, feet on the floor
                if (img != null) {
                    val figH = H - headY
                    val imgH = figH / 0.92f
                    val imgW = imgH * img.width / img.height
                    val top = headY - imgH * 0.06f
                    drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(((W * 0.42f) - imgW / 2).toInt(), top.toInt()), IntSize(imgW.toInt(), imgH.toInt()))
                }
                // head line
                drawLine(accent, Offset(size.width * 0.12f, headY), Offset(size.width, headY), 2.dp.toPx())
                drawCircle(accent, 5.dp.toPx(), Offset(size.width * 0.12f, headY))
            }
            // ruler labels
            val labels = if (unit == LengthUnit.CM) (100..220 step 20).map { it.toDouble() to "$it" } else (3..7).map { (it * 12 * Units.CM_PER_IN) to "$it′" }
            labels.forEach { (v, l) ->
                val y = with(d) { (H - H * (v / topCm).toFloat()).toDp() }
                Text(l, style = FitType.caption, color = txt, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-34).dp, y = y - 9.dp))
            }
            // big value riding above the line
            val label = if (unit == LengthUnit.CM) "${shown.roundToInt()} cm" else { val i = (shown / Units.CM_PER_IN).roundToInt(); "${i / 12}′${i % 12}″" }
            Text(label, style = FitType.display.copy(fontSize = 44.sp), color = th.text,
                modifier = Modifier.align(Alignment.TopStart).offset(x = 16.dp, y = with(d) { headY.toDp() } - 62.dp))
        }
        Caption("Drag up or down to set your height")
    }
}

// ====================================================================== BMI

@Composable
fun BmiCard(weightKg: Double, heightCm: Double) {
    val th = LocalFitTheme.current
    val b = PlanProfile.bmi(weightKg, heightCm)
    val shown by animateFloatAsState(b.toFloat(), spring(0.9f, 300f), label = "bmi")
    val frac = ((shown - 15f) / (40f - 15f)).coerceIn(0f, 1f)
    val cls = PlanProfile.bmiClass(b)
    val col = when (cls) { "Underweight" -> Color(0xFF42A5F5); "Healthy" -> Color(0xFF66BB6A); "Overweight" -> Color(0xFFFFA726); else -> Color(0xFFEF5350) }
    Glass(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Current BMI", style = FitType.section, color = th.text)
            Spacer(Modifier.height(30.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFF42A5F5), Color(0xFF26C6DA), Color(0xFF66BB6A), Color(0xFFFFEE58), Color(0xFFFFA726), Color(0xFFEF5350)))))
                val x = maxWidth * frac
                Box(Modifier.offset(x = x - 26.dp, y = (-34).dp).width(52.dp).height(26.dp).clip(RoundedCornerShape(10.dp)).background(col), contentAlignment = Alignment.Center) {
                    Text("%.1f".format(shown), style = FitType.label, color = Color.White)
                }
                Box(Modifier.offset(x = x - 7.dp, y = (-3).dp).size(18.dp).clip(CircleShape).background(Color.White).border(3.dp, col, CircleShape))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Underweight", "Healthy", "Overweight", "Obese").forEach { Text(it, style = FitType.caption, color = if (it == cls) col else th.textFaint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
            Caption(when (cls) {
                "Underweight" -> "A little under the healthy range — your plan will focus on eating enough and building strength."
                "Healthy" -> "You're in the healthy range. Great base to build on."
                "Overweight" -> "A bit above the healthy range — small daily changes to food and movement bring it down."
                else -> "Above the healthy range. We'll start gently and build steady habits."
            } + " BMI is a rough guide: it doesn't see muscle.")
        }
    }
}

// ====================================================================== body shape

/** Body-shape picker: drag the slider and the character gains or loses fat. The character is a single consistent
 * vector figure — only the belly, shoulders, arms and legs change; the face, hair, beard/ponytail and pose are
 * identical at every level, so it no longer "swaps" into a different person. */
@Composable
fun BodyShapePage(level: Int, female: Boolean, onLevel: (Int) -> Unit) {
    val th = LocalFitTheme.current
    val v = remember { Animatable(level.toFloat()) }
    LaunchedEffect(level) { if (kotlin.math.abs(v.value - level) > 0.02f && !v.isRunning) v.animateTo(level.toFloat(), spring(0.8f, 260f)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(400.dp)) {
            // soft glow that warms up as body fat goes up
            Canvas(Modifier.fillMaxSize()) {
                val t = (v.value - 1f) / 5f
                val c = androidx.compose.ui.graphics.lerp(th.accent, Color(0xFFFF8A65), t)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), center, size.minDimension * 0.6f), size.minDimension * 0.6f, center)
                drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), Offset(center.x, size.height * 0.975f), size.width * 0.22f),
                    Offset(center.x - size.width * 0.22f, size.height * 0.95f), Size(size.width * 0.44f, size.height * 0.05f))
            }
            VectorBodyCharacter(female, (v.value - 1f) / 5f, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(PlanProfile.bodyLevelName(level), style = FitType.title, color = th.text)
        Text("Body fat ${PlanProfile.bodyFatLabel(level, female)}", style = FitType.label, color = th.accentBright)
        Spacer(Modifier.height(14.dp))
        // slider: 6 stops, drag continuously, snaps on release
        BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp).pointerInput(Unit) {
            fun at(x: Float) = (1f + (x / size.width).coerceIn(0f, 1f) * 5f)
            detectDragGestures(
                onDragStart = { scope.launch { v.snapTo(at(it.x)) } },
                onDragEnd = { val r = v.value.roundToInt().coerceIn(1, 6); onLevel(r); scope.launch { v.animateTo(r.toFloat(), spring(0.75f, 400f)) } },
            ) { ch, _ -> scope.launch { v.snapTo(at(ch.position.x)) } }
        }.pointerInput(Unit) { detectTapGestures { val r = (1f + (it.x / size.width) * 5f).roundToInt().coerceIn(1, 6); onLevel(r); scope.launch { v.animateTo(r.toFloat(), spring(0.75f, 300f)) } } }) {
            val w = maxWidth
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(10.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(th.accent, Color(0xFFFFD54F), Color(0xFFFF7043)))))
            (0..5).forEach { i -> Box(Modifier.align(Alignment.CenterStart).offset(x = w * (i / 5f) - 4.dp).size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f))) }
            Box(Modifier.align(Alignment.CenterStart).offset(x = w * ((v.value - 1f) / 5f) - 15.dp).size(30.dp).clip(CircleShape).background(Color.White).border(4.dp, th.accentBright, CircleShape))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Leaner", style = FitType.caption, color = th.textDim, modifier = Modifier.weight(1f))
            Text("More body fat", style = FitType.caption, color = th.textDim)
        }
    }
}


// ====================================================================== prediction

@Composable
fun PredictionPage(fromKg: Double, toKg: Double, unit: WeightUnit, pace: Int, onPace: (Int) -> Unit) {
    val th = LocalFitTheme.current
    val date = PlanProfile.predict(fromKg, toKg, pace)
    val losing = toKg < fromKg
    val draw = remember { Animatable(0f) }
    val pop = remember { Animatable(0f) }
    LaunchedEffect(pace) { draw.snapTo(0f); pop.snapTo(0f); draw.animateTo(1f, tween(1300)); pop.animateTo(1f, spring(0.5f, 300f)) }
    val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("We estimate you'll reach", style = FitType.body, color = th.textDim)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${com.myfit.tracker.domain.Fmt.weight(toKg, unit, 0)} on ", style = FitType.display, color = th.text)
            Text(date?.format(fmt) ?: "—", style = FitType.display, color = th.accentBright)
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            val c1 = Color(0xFFFF7043); val c2 = th.accentBright
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val y0 = if (losing) h * 0.18f else h * 0.78f; val y1 = if (losing) h * 0.72f else h * 0.22f
                val x0 = w * 0.12f; val x1 = w * 0.78f
                val p = Path().apply { moveTo(0f, y0 - (y1 - y0) * 0.08f); lineTo(x0, y0); cubicTo(x0 + (x1 - x0) * 0.45f, y0, x0 + (x1 - x0) * 0.55f, y1, x1, y1); lineTo(w, y1) }
                val pm = PathMeasure(); pm.setPath(p, false)
                val seg = Path(); pm.getSegment(0f, pm.length * draw.value, seg, true)
                // fill under the drawn part
                val fill = Path().apply { addPath(seg); val end = pm.getPosition(pm.length * draw.value); lineTo(end.x, h); lineTo(0f, h); close() }
                drawPath(fill, Brush.verticalGradient(listOf(c1.copy(alpha = 0.22f), c2.copy(alpha = 0.02f))))
                drawPath(seg, Brush.horizontalGradient(listOf(c1, c2)), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                drawLine(c1.copy(alpha = 0.5f), Offset(x0, y0), Offset(x0, h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawCircle(Color.White, 8.dp.toPx(), Offset(x0, y0)); drawCircle(c1, 5.dp.toPx(), Offset(x0, y0))
                if (draw.value > 0.85f) {
                    drawLine(c2.copy(alpha = 0.5f), Offset(x1, y1), Offset(x1, h), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    drawCircle(Color.White, 8.dp.toPx() * pop.value, Offset(x1, y1)); drawCircle(c2, 5.dp.toPx() * pop.value, Offset(x1, y1))
                }
            }
            // trophy bubble pops at the goal
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val y1 = if (losing) maxHeight * 0.72f else maxHeight * 0.22f
                Box(Modifier.offset(x = maxWidth * 0.78f - 28.dp, y = y1 - 72.dp).size(56.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFFE082), Color(0xFFFFB300)))), contentAlignment = Alignment.Center) {
                    Text("🏆", fontSize = 26.sp)
                }
            }
            Text("Today", style = FitType.caption, color = th.textDim, modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp))
            Text(date?.format(fmt) ?: "", style = FitType.caption, color = th.accentBright, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Slow", "Steady", "Fast").forEachIndexed { i, l -> GlassChip(l, pace == i, { onPace(i) }) }
        }
        Spacer(Modifier.height(6.dp))
        Text("${"%.2f".format(PlanProfile.kgPerWeek(losing, pace)).trimEnd('0').trimEnd('.')} kg a week", style = FitType.label, color = th.accentBright)
        Spacer(Modifier.height(10.dp))
        Caption("An estimate at a steady weekly pace — real progress has ups and downs. You can change your target any time.", Modifier.padding(horizontal = 8.dp))
    }
}
