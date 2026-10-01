package com.myfit.tracker.ui.theme

import android.content.Context
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Animated theme backdrops as GPU fragment shaders (Android 13+). One full-screen shader pass is far
 * cheaper than drawing hundreds of paths on the CPU every frame, and looks much richer.
 * Sources live in assets/themes/<theme id>.agsl and share assets/themes/common.agsl.
 */
object ThemeShaders {
    val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private var app: Context? = null
    private var common: String? = null
    private val cache = HashMap<String, Pair<Any, ShaderBrush>>()   // id -> (RuntimeShader, brush)
    private val failed = mutableSetOf<String>()

    fun init(context: Context) { app = context.applicationContext }

    /** Draws the theme shader; returns false if unavailable so the caller can use the static art. */
    fun draw(scope: DrawScope, themeId: String, t: Float, w: Float, h: Float): Boolean {
        if (!supported || themeId in failed || com.myfit.tracker.CrashGuard.safeMode) return false
        return runCatching { drawImpl(scope, themeId, t, w, h) }.getOrElse { failed += themeId; false }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun drawImpl(scope: DrawScope, themeId: String, t: Float, w: Float, h: Float): Boolean {
        val ctx = app ?: return false
        val entry = cache[themeId] ?: run {
            val base = common ?: ctx.assets.open("themes/common.agsl").bufferedReader().use { it.readText() }.also { common = it }
            val body = runCatching { ctx.assets.open("themes/$themeId.agsl").bufferedReader().use { it.readText() } }.getOrNull()
                ?: run { failed += themeId; return false }
            val rs = RuntimeShader(base + "\n" + body)
            Pair<Any, ShaderBrush>(rs, ShaderBrush(rs)).also { cache[themeId] = it }
        }
        val s = entry.first as RuntimeShader
        s.setFloatUniform("res", w, h)
        s.setFloatUniform("t", t % 3600f)
        scope.drawRect(entry.second)
        return true
    }
}
