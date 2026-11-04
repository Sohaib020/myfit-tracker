package com.myfit.tracker.ui.game

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myfit.tracker.ui.components.clickableNoRipple
import com.myfit.tracker.ui.theme.FitType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * R26 "Game Mode ON" — liquid chrome. A drop of iridescent liquid metal wobbles in, swells, bursts into a full-screen
 * chrome wash, and the title rises out of it with a light sweep across the letters and an ON pill that pulses.
 * GPU shader on Android 13+, a gradient version elsewhere. ~3 s, tap anywhere to skip. No character in it.
 */
@Composable
fun GameModeIntro(onDone: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val fade = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val pill = remember { Animatable(0f) }
    val sweep = remember { Animatable(-1f) }
    var finished by remember { mutableStateOf(false) }
    val done = { if (!finished) { finished = true; onDone() } }
    val inf = rememberInfiniteTransition(label = "chrome")
    val t by inf.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val ease = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(220))
        grow.animateTo(1f, tween(900, easing = ease))
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { burst.animateTo(1f, tween(700, easing = ease)) }
        delay(260)
        launch { title.animateTo(1f, spring(0.62f, 220f)) }
        delay(240)
        launch { sweep.animateTo(1.6f, tween(900, easing = LinearEasing)) }
        pill.animateTo(1f, spring(0.42f, 420f))
        delay(1300)
        fade.animateTo(0f, tween(360))
        done()
    }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(Color(0xF205060A)).clickableNoRipple { done() },
        contentAlignment = Alignment.Center) {
        // the liquid chrome
        Canvas(Modifier.fillMaxSize()) {
            val drawn = Build.VERSION.SDK_INT >= 33 && runCatching { ChromeShader.draw(this, t, grow.value, burst.value) }.getOrDefault(false)
            if (!drawn) {
                // gradient fallback: a rotating iridescent sweep in a soft-edged drop, then a wash
                val r = size.minDimension * (0.22f * grow.value + 1.1f * burst.value)
                val c = Offset(size.width / 2, size.height / 2)
                if (r > 1f) {
                    drawCircle(Brush.sweepGradient(IRIDESCENT, c), r, c, alpha = 1f - burst.value * 0.8f)
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 0.7f), r, c,
                        alpha = 1f - burst.value)
                    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color(0xCC05060A)), c, r), r, c)
                }
            }
        }
        // title
        Column(Modifier.graphicsLayer {
            alpha = title.value.coerceIn(0f, 1f); translationY = (1f - title.value) * 60f
            scaleX = 0.92f + 0.08f * title.value; scaleY = scaleX
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            val shine = Brush.linearGradient(
                listOf(Color(0xFF9AA3B5), Color(0xFFF4F7FF), Color(0xFFB8C2D9), Color.White, Color(0xFF8C94A8)),
                start = Offset(sweep.value * 900f - 300f, 0f), end = Offset(sweep.value * 900f + 200f, 220f), tileMode = TileMode.Clamp)
            Text("GAME MODE", style = FitType.hero.copy(fontSize = 52.sp, letterSpacing = (10f - 7f * title.value).sp, brush = shine))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.graphicsLayer { scaleX = pill.value; scaleY = pill.value }
                .clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF7CF7D4), Color(0xFF7C9CFF), Color(0xFFE48CFF))))
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
                .padding(horizontal = 26.dp, vertical = 8.dp)) {
                Text("ON", style = FitType.title.copy(fontWeight = FontWeight.Black, letterSpacing = 6.sp), color = Color(0xFF0B0D14))
            }
            Spacer(Modifier.height(22.dp))
            Text("Your workouts now power your champion", style = FitType.body, color = Color.White.copy(alpha = 0.78f * pill.value.coerceIn(0f, 1f)))
        }
    }
}

private val IRIDESCENT = listOf(Color(0xFFDDE3EE), Color(0xFF7CF7D4), Color(0xFF7C9CFF), Color(0xFFE48CFF), Color(0xFFFFD27C), Color(0xFFDDE3EE))

/** AGSL liquid-metal drop: wobbling surface normals -> chrome bands + thin-film colours + a hot specular. */
private object ChromeShader {
    private const val SRC = """
uniform float2 res; uniform float t; uniform float grow; uniform float burst;
half4 main(float2 p) {
    float2 uv = (p - res * 0.5) / min(res.x, res.y);
    float a = t * 0.9;
    float2 q = uv;
    q += 0.06 * float2(sin(3.1 * uv.y + a * 1.3), cos(3.3 * uv.x - a));
    q += 0.03 * float2(sin(7.0 * q.y - a * 2.1), cos(6.2 * q.x + a * 1.7));
    float R = max(0.001, grow * 0.30 + burst * 1.35);
    float ang = atan(q.y, q.x);
    float wob = (0.035 * sin(ang * 5.0 + a * 2.0) + 0.02 * sin(ang * 3.0 - a * 1.4)) * (1.0 - burst);
    float r = length(q);
    float d = r - R * (1.0 + wob);
    float inside = 1.0 - smoothstep(-0.004, 0.004, d);
    float k = clamp(r / (R * (1.0 + wob)), 0.0, 1.0);
    float3 n = normalize(float3(q / R, sqrt(max(0.0, 1.0 - k * k)) + 0.05));
    float bands = sin((n.x * 2.6 + n.y * 4.4 + a * 0.35) * 3.14159);
    float env = n.y * 0.5 + 0.5;
    float3 film = 0.5 + 0.5 * cos(6.28318 * (float3(0.0, 0.33, 0.67) + n.x * 0.55 + n.y * 0.35 + t * 0.12));
    float3 steel = mix(float3(0.05, 0.06, 0.09), float3(0.96, 0.97, 1.0), smoothstep(-0.35, 0.95, bands * 0.55 + env * 0.55));
    float3 col = mix(steel, steel * film * 1.5, 0.5);
    col += pow(max(0.0, dot(n, normalize(float3(-0.45, 0.6, 0.66)))), 48.0) * 1.2;
    float rim = (1.0 - smoothstep(0.0, 0.05, abs(d))) * (1.0 - burst);
    col += film * rim * 0.9;
    float al = inside * (1.0 - burst * 0.82) + rim * 0.55;
    return half4(half3(col * al), half(clamp(al, 0.0, 1.0)));
}
"""
    private var shader: Any? = null
    private var brush: ShaderBrush? = null

    @RequiresApi(33)
    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, t: Float, grow: Float, burst: Float): Boolean {
        if (com.myfit.tracker.CrashGuard.safeMode) return false
        val s = (shader as? android.graphics.RuntimeShader) ?: android.graphics.RuntimeShader(SRC).also { shader = it; brush = ShaderBrush(it) }
        s.setFloatUniform("res", scope.size.width, scope.size.height)
        s.setFloatUniform("t", t)
        s.setFloatUniform("grow", grow)
        s.setFloatUniform("burst", burst)
        scope.drawRect(brush!!)
        return true
    }
}
