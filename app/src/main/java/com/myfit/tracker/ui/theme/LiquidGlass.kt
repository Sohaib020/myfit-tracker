package com.myfit.tracker.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Liquid-glass lens (Android 13+ AGSL). Samples the (already blurred) backdrop with a displacement
 * that grows toward the rounded edges — like light bending through a thick glass slab — plus an
 * optional tiny R/B split (dispersion) and a directional rim highlight.
 */
object LiquidGlass {
    private const val SRC = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bezel;
uniform float strength;
uniform float dispersion;
uniform float highlight;

float sdRR(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 coord) {
    float2 c = size * 0.5;
    float2 p = coord - c;
    float r = min(radius, min(c.x, c.y));
    float d = sdRR(p, c, r);
    float t = clamp(1.0 + d / bezel, 0.0, 1.0);
    float2 g = float2(sdRR(p + float2(1.0, 0.0), c, r) - sdRR(p - float2(1.0, 0.0), c, r),
                      sdRR(p + float2(0.0, 1.0), c, r) - sdRR(p - float2(0.0, 1.0), c, r));
    float gl = length(g);
    float2 n = float2(0.0, 0.0);
    if (gl > 0.0001) { n = g / gl; }
    float bend = t * t * t * strength;
    float2 off = -n * bend;
    half4 col = content.eval(coord + off);
    if (dispersion > 0.0) {
        col.r = content.eval(coord + off * (1.0 + dispersion)).r;
        col.b = content.eval(coord + off * (1.0 - dispersion)).b;
    }
    float light = max(0.0, dot(n, normalize(float2(-0.55, -0.85))));
    float rim = smoothstep(0.72, 1.0, t) * (0.3 + 0.7 * light);
    col.rgb += half3(rim * highlight);
    return col;
}
"""

    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** A fresh shader instance (each glass surface owns one, since uniforms differ). Null if unsupported. */
    fun newShader(): Any? = if (!supported) null else runCatching { make() }.getOrNull()

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun make(): RuntimeShader = RuntimeShader(SRC)

    /**
     * Blur first, then refraction. Returns null when neither applies (caller falls back).
     * @param shaderObj value from [newShader]
     */
    fun effect(
        shaderObj: Any?, w: Float, h: Float, cornerPx: Float, blurPx: Float,
        bezelPx: Float, strengthPx: Float, dispersion: Float, highlight: Float,
    ): RenderEffect? {
        if (w < 2f || h < 2f) return null
        if (supported && shaderObj != null) {
            return runCatching { build(shaderObj as RuntimeShader, w, h, cornerPx, blurPx, bezelPx, strengthPx, dispersion, highlight) }.getOrNull()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0.5f) {
            return android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun build(
        s: RuntimeShader, w: Float, h: Float, cornerPx: Float, blurPx: Float,
        bezelPx: Float, strengthPx: Float, dispersion: Float, highlight: Float,
    ): RenderEffect {
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("radius", cornerPx)
        s.setFloatUniform("bezel", bezelPx.coerceAtLeast(1f))
        s.setFloatUniform("strength", strengthPx)
        s.setFloatUniform("dispersion", dispersion)
        s.setFloatUniform("highlight", highlight)
        val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content")
        val fx = if (blurPx > 0.5f) {
            android.graphics.RenderEffect.createChainEffect(
                lens, android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
            )
        } else lens
        return fx.asComposeRenderEffect()
    }
}
