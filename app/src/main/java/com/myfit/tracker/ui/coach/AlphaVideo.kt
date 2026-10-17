package com.myfit.tracker.ui.coach

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * R26: plays a coach/champion clip made by tools/coach3d/vid.py — H.264 with the colour on the left half and the alpha
 * on the right half. The phone's video decoder does the heavy lifting; a tiny GL shader recombines the halves so the
 * character is drawn with real transparency over the glass card. The character is scaled to fit and stands on the
 * bottom edge (same as the old WebP clips).
 */
class AlphaVideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var file: File? = null
    private var loop = true
    var onEnd: (() -> Unit)? = null
    /** Called once the first frame is on screen (callers keep their still on top until then). */
    var onFirstFrame: (() -> Unit)? = null

    private var player: ExoPlayer? = null
    private var gl: GlThread? = null
    private var videoW = 0
    private var videoH = 0
    private val main = Handler(Looper.getMainLooper())

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun play(f: File, loop: Boolean) {
        if (file?.path == f.path && this.loop == loop && player != null) return
        file = f; this.loop = loop
        player?.let { p -> start(p) }
    }

    private fun start(p: ExoPlayer) {
        val f = file ?: return
        p.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        p.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(f)))
        p.prepare(); p.playWhenReady = true
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        val t = GlThread(st, w, h) { videoSurface ->
            main.post {
                if (gl == null) { videoSurface.release(); return@post }
                val p = ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    setVideoSurface(videoSurface)
                    addListener(object : Player.Listener {
                        override fun onVideoSizeChanged(size: VideoSize) { videoW = size.width; videoH = size.height; gl?.setVideo(size.width, size.height) }
                        override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED && !loop) onEnd?.invoke() }
                        override fun onRenderedFirstFrame() { onFirstFrame?.invoke() }
                    })
                }
                player = p
                start(p)
            }
        }
        gl = t; t.start()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { gl?.setView(w, h) }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        player?.release(); player = null
        gl?.shutdown(); gl = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    /** EGL + one external texture fed by the decoder; draws on every new video frame. */
    private class GlThread(private val out: SurfaceTexture, w: Int, h: Int, private val ready: (Surface) -> Unit) : HandlerThread("alpha-video") {
        private lateinit var handler: Handler
        private var dpy: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var ctx: EGLContext = EGL14.EGL_NO_CONTEXT
        private var surf: EGLSurface = EGL14.EGL_NO_SURFACE
        private var tex = 0
        private var prog = 0
        private var video: SurfaceTexture? = null
        private var videoSurface: Surface? = null
        @Volatile private var vw = w
        @Volatile private var vh = h
        @Volatile private var fw = 0
        @Volatile private var fh = 0
        private val m = FloatArray(16)
        private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
        }

        override fun onLooperPrepared() {
            handler = Handler(looper)
            runCatching { setup() }.onFailure { quitSafely(); return }
            val st = SurfaceTexture(tex).also { video = it }
            st.setOnFrameAvailableListener({ draw() }, handler)
            val s = Surface(st).also { videoSurface = it }
            ready(s)
        }

        fun setView(w: Int, h: Int) { vw = w; vh = h; if (::handler.isInitialized) handler.post { draw(update = false) } }
        fun setVideo(w: Int, h: Int) { fw = w / 2; fh = h }

        fun shutdown() {
            if (!::handler.isInitialized) { quitSafely(); return }
            handler.post {
                runCatching { video?.release(); videoSurface?.release() }
                if (dpy != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, surf)
                    if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(dpy, ctx)
                    EGL14.eglTerminate(dpy)
                }
                quitSafely()
            }
        }

        private fun setup() {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2); EGL14.eglInitialize(dpy, ver, 0, ver, 1)
            val attrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, n, 0)
            ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surf = EGL14.eglCreateWindowSurface(dpy, cfgs[0], out, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            prog = program(VS, FS)
            GLES20.glClearColor(0f, 0f, 0f, 0f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); EGL14.eglSwapBuffers(dpy, surf)
        }

        private fun draw(update: Boolean = true) {
            val st = video ?: return
            if (update) runCatching { st.updateTexImage() }.onFailure { return }
            st.getTransformMatrix(m)
            GLES20.glViewport(0, 0, vw, vh)
            GLES20.glClearColor(0f, 0f, 0f, 0f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (fw > 0 && fh > 0) {
                // fit the character inside the view, standing on the bottom edge (GL's y=0 is the bottom)
                val s = minOf(vw / fw.toFloat(), vh / fh.toFloat())
                val w = (fw * s).toInt(); val h = (fh * s).toInt()
                GLES20.glViewport((vw - w) / 2, 0, w, h)
                GLES20.glUseProgram(prog)
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uTex"), 1, false, m, 0)
                val aPos = GLES20.glGetAttribLocation(prog, "aPos"); val aUv = GLES20.glGetAttribLocation(prog, "aUv")
                quad.position(0); GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(aPos)
                quad.position(2); GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(aUv)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            EGL14.eglSwapBuffers(dpy, surf)
        }

        private fun program(vs: String, fs: String): Int {
            fun sh(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
            return GLES20.glCreateProgram().also { p ->
                GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, vs)); GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(p)
            }
        }

        companion object {
            // colour from the left half, alpha from the right half (both through the decoder's transform)
            const val VS = """
                attribute vec2 aPos; attribute vec2 aUv; uniform mat4 uTex; varying vec2 vC; varying vec2 vA;
                void main() {
                    gl_Position = vec4(aPos, 0.0, 1.0);
                    vC = (uTex * vec4(aUv.x * 0.5, aUv.y, 0.0, 1.0)).xy;
                    vA = (uTex * vec4(0.5 + aUv.x * 0.5, aUv.y, 0.0, 1.0)).xy;
                }"""
            const val FS = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float; uniform samplerExternalOES sTex; varying vec2 vC; varying vec2 vA;
                void main() {
                    float a = clamp((texture2D(sTex, vA).g - 0.03) / 0.94, 0.0, 1.0);
                    vec3 c = texture2D(sTex, vC).rgb;
                    gl_FragColor = vec4(min(c, vec3(a)), a);   // colour is already premultiplied
                }"""
        }
    }
}
