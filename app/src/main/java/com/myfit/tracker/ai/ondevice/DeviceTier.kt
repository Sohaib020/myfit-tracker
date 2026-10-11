package com.myfit.tracker.ai.ondevice

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build

/**
 * R18: one Gemma file for every phone; this decides HOW it runs on this phone (automatic, no user switch).
 *
 *  FULL — 6 GB+ RAM with a capable GPU: GPU first (with multi-token prediction), 4K-token memory, stays loaded longer.
 *  LITE — 4 GB phones or weak / old GPUs: CPU (XNNPACK) first, 2K-token memory, unloads sooner to give RAM back.
 *
 * Inputs: total RAM, the GPU name (read once through a tiny off-screen EGL context) and a speed test taken from
 * the first real answers. A FULL phone that turns out slow (< [MIN_FULL_TPS] tokens/s) drops to LITE.
 * Everything is re-checked after an Android update (keyed by the build fingerprint).
 */
object DeviceTier {
    enum class Level { FULL, LITE }

    data class Profile(
        val level: Level, val ramBytes: Long, val gpu: String, val gpuStrong: Boolean,
        val maxTokens: Int, val preferGpu: Boolean, val idleMs: Long, val measuredTps: Float?,
    )

    private const val MIN_FULL_TPS = 5f
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("device_tier", Context.MODE_PRIVATE)
    @Volatile private var cached: Profile? = null

    fun profile(c: Context): Profile {
        cached?.let { return it }
        val p = sp(c)
        val fp = Build.FINGERPRINT
        if (p.getString("fp", null) != fp) p.edit().clear().putString("fp", fp).apply()
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val ram = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
        val gpu = p.getString("gpu", null) ?: readGpu().also { p.edit().putString("gpu", it).apply() }
        val strong = gpuStrong(gpu)
        val tps = p.getFloat("tps", -1f).takeIf { it > 0f }
        var level = when {
            ram >= 7_000_000_000L && !weakGpu(gpu) -> Level.FULL
            ram >= 5_300_000_000L && strong -> Level.FULL
            else -> Level.LITE
        }
        if (level == Level.FULL && tps != null && tps < MIN_FULL_TPS) level = Level.LITE
        val prof = if (level == Level.FULL) Profile(level, ram, gpu, strong, 4096, true, if (ram >= 7_500_000_000L) 600_000L else 240_000L, tps)
        else Profile(level, ram, gpu, strong, 2048, strong, 90_000L, tps)
        cached = prof
        return prof
    }

    /** Called after an answer streams: decode speed in tokens/second (≈ 4 characters per token). */
    fun recordSpeed(c: Context, tps: Float) {
        if (tps <= 0f || tps.isNaN()) return
        val p = sp(c)
        val old = p.getFloat("tps", -1f)
        val v = if (old > 0f) old * 0.6f + tps * 0.4f else tps
        p.edit().putFloat("tps", v).apply()
        cached = null
    }

    fun describe(c: Context): String {
        val pr = profile(c)
        return "${if (pr.level == Level.FULL) "Full speed" else "Light mode"} · ${"%.1f".format(pr.ramBytes / 1e9)} GB RAM · ${pr.gpu.ifBlank { "GPU unknown" }}" +
            (pr.measuredTps?.let { " · ~${it.toInt()} tokens/s" } ?: "")
    }

    /** Adreno 640+, Mali-G7x/G6xx-class (G610, G710+), Immortalis, Xclipse, PowerVR DXT: good for on-device LLMs. */
    fun gpuStrong(r: String): Boolean {
        val s = r.lowercase()
        Regex("adreno.*?(\\d{3})").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { return it >= 640 }
        if ("immortalis" in s || "xclipse" in s) return true
        Regex("mali-g(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { return it >= 610 || it in 71..79 }
        if ("powervr" in s && "dxt" in s) return true
        return false
    }

    /** Entry-level GPUs where the CPU path is usually faster (Mali-G52/G57, Adreno 5xx/610, PowerVR GE/Rogue). */
    private fun weakGpu(r: String): Boolean {
        val s = r.lowercase()
        Regex("adreno.*?(\\d{3})").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { return it < 620 }
        Regex("mali-g(\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()?.let { return it in 31..69 }
        return "powervr" in s && "dxt" !in s
    }

    /** GL_RENDERER from a 1×1 off-screen context (a few milliseconds, done once and cached). */
    private fun readGpu(): String = runCatching {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        EGL14.eglInitialize(dpy, ver, 0, ver, 1)
        val cfgs = arrayOfNulls<android.opengl.EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
        val r = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(dpy, surf); EGL14.eglDestroyContext(dpy, ctx); EGL14.eglTerminate(dpy)
        r
    }.getOrDefault("").ifBlank { (if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE) ?: "" }
}
