package com.myfit.tracker.ui.coach

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays a coach/champion clip made by tools/coach3d/vid.py — H.264 with the colour on the left half and the alpha on
 * the right half — with real transparency over the card.
 *
 * R30: our own frame pacing. A general video player releases frames on its own clock, and through a TextureView on a
 * 120 Hz screen that showed as stutter / "missing frames". Here the decoder is driven from the display's vsync
 * (Choreographer on the render thread): every vsync we show the frame whose time has come, so a 60 fps clip lands on
 * every 2nd refresh of a 120 Hz panel, evenly. Loops are seamless (the stream is fed again from its first keyframe
 * with shifted timestamps — no decoder flush). The character's feet are found in the first frame and placed on the
 * view's bottom edge, so it stands on the platform.
 */
class AlphaVideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var file: File? = null
    private var loop = true
    var onEnd: (() -> Unit)? = null
    var onFirstFrame: (() -> Unit)? = null

    private var gl: GlThread? = null
    private val main = Handler(Looper.getMainLooper())

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun play(f: File, loop: Boolean) {
        if (file?.path == f.path && this.loop == loop) return
        file = f; this.loop = loop
        gl?.open(f, loop)
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        val t = GlThread(st, w, h, { main.post { onEnd?.invoke() } }, { main.post { onFirstFrame?.invoke() } })
        gl = t; t.start()
        file?.let { t.open(it, loop) }
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { gl?.setView(w, h) }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        gl?.shutdown(); gl = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    companion object {
        /** Gap under the feet (fraction of frame height) per clip folder, found once from the idle clip. */
        private val feetGap = ConcurrentHashMap<String, Float>()

        /** Lowest opaque row of the alpha half in the first frame of [f] → empty space under the feet (0..0.3). */
        fun gapFor(f: File): Float {
            val dir = f.parentFile?.path ?: f.path
            feetGap[dir]?.let { return it }
            val ref = File(f.parentFile, "idle.mp4").takeIf { it.exists() } ?: f
            val g = runCatching {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(ref.path)
                    val bmp = r.getFrameAtTime(0) ?: return@runCatching 0f
                    val w = bmp.width; val h = bmp.height
                    var low = -1
                    val xs = (w / 2 + w / 40 until w step maxOf(1, w / 160)).toList()
                    loop@ for (y in h - 1 downTo h / 2) {
                        for (x in xs) { if ((bmp.getPixel(x, y) shr 8 and 0xFF) > 140) { low = y; break@loop } }
                    }
                    bmp.recycle()
                    if (low < 0) 0f else ((h - 1 - low).toFloat() / h).coerceIn(0f, 0.3f)
                } finally { r.release() }
            }.getOrDefault(0f)
            feetGap[dir] = g
            return g
        }
    }

    /** Render thread: EGL + external texture + MediaCodec, all paced by this thread's Choreographer. */
    private class GlThread(private val out: SurfaceTexture, w: Int, h: Int, private val ended: () -> Unit, private val first: () -> Unit) :
        HandlerThread("alpha-video"), Choreographer.FrameCallback {
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
        private var fw = 0
        private var fh = 0
        private var gap = 0f
        private val m = FloatArray(16)
        private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
        }
        private var ready = false
        private var pending: Pair<File, Boolean>? = null

        // decoder state
        private var ex: MediaExtractor? = null
        private var codec: MediaCodec? = null
        private var loop = true
        private var inputDone = false
        private var ptsOffset = 0L                 // added to input timestamps on each loop (µs)
        private var lastInPts = 0L
        private var frameUs = 16_666L
        private var startNs = 0L                   // vsync time of the first shown frame
        private var basePts = -1L
        private var outIdx = -1
        private var outPts = 0L
        private var shownFirst = false
        private var endedSent = false
        private val info = MediaCodec.BufferInfo()
        private var alive = true

        override fun onLooperPrepared() {
            handler = Handler(looper)
            runCatching { setupGl() }.onFailure { quitSafely(); return }
            val st = SurfaceTexture(tex).also { video = it }
            st.setOnFrameAvailableListener({ draw(update = true) }, handler)
            videoSurface = Surface(st)
            ready = true
            pending?.let { (f, l) -> pending = null; startDecoder(f, l) }
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun open(f: File, loop: Boolean) {
            if (!::handler.isInitialized) { pending = f to loop; return }
            handler.post { if (ready) startDecoder(f, loop) else pending = f to loop }
        }

        fun setView(w: Int, h: Int) { vw = w; vh = h; if (::handler.isInitialized) handler.post { draw(update = false) } }

        private fun startDecoder(f: File, l: Boolean) {
            stopDecoder()
            loop = l; inputDone = false; ptsOffset = 0L; lastInPts = 0L; basePts = -1L; outIdx = -1; shownFirst = false; endedSent = false
            runCatching {
                val e = MediaExtractor().apply { setDataSource(f.path) }
                val track = (0 until e.trackCount).first { e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                e.selectTrack(track)
                val fmt = e.getTrackFormat(track)
                fw = fmt.getInteger(MediaFormat.KEY_WIDTH) / 2; fh = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) frameUs = (1_000_000L / fmt.getInteger(MediaFormat.KEY_FRAME_RATE).coerceAtLeast(1))
                val c = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
                c.configure(fmt, videoSurface, null, 0)
                c.start()
                ex = e; codec = c
            }.onFailure { stopDecoder(); return }
            // feet line: measured off-thread once per folder
            Thread { val g = AlphaVideoView.gapFor(f); handler.post { gap = g; draw(update = false) } }.apply { isDaemon = true }.start()
        }

        private fun stopDecoder() {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; codec = null
            runCatching { ex?.release() }; ex = null
            outIdx = -1
        }

        /** Keep the decoder fed; loops re-feed the stream from the start with shifted timestamps (seamless). */
        private fun feed() {
            val c = codec ?: return; val e = ex ?: return
            repeat(4) {
                if (inputDone) return
                val i = c.dequeueInputBuffer(0); if (i < 0) return
                val buf = c.getInputBuffer(i) ?: return
                var n = e.readSampleData(buf, 0)
                if (n < 0 && loop) {
                    // end of the clip: continue from its first keyframe, timestamps carry on (no flush, no gap)
                    ptsOffset = lastInPts + frameUs
                    e.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    buf.clear()
                    n = e.readSampleData(buf, 0)
                }
                if (n < 0) {
                    c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                } else {
                    val pts = e.sampleTime + ptsOffset
                    lastInPts = pts
                    c.queueInputBuffer(i, 0, n, pts, 0)
                    e.advance()
                }
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!alive) return
            Choreographer.getInstance().postFrameCallback(this)
            val c = codec ?: return
            runCatching {
                feed()
                if (outIdx < 0) {
                    val o = c.dequeueOutputBuffer(info, 0)
                    if (o >= 0) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 && info.size == 0) {
                            c.releaseOutputBuffer(o, false)
                            if (!endedSent) { endedSent = true; ended() }
                            return
                        }
                        outIdx = o; outPts = info.presentationTimeUs
                    }
                }
                if (outIdx >= 0) {
                    if (basePts < 0) { basePts = outPts; startNs = frameTimeNanos }
                    val due = startNs + (outPts - basePts) * 1000L
                    // show it on the vsync whose time has come; drop only if we're more than 2 frames behind
                    if (frameTimeNanos + 4_000_000L >= due) {
                        val late = frameTimeNanos - due > 2 * frameUs * 1000L
                        c.releaseOutputBuffer(outIdx, !late)
                        outIdx = -1
                        if (!shownFirst && !late) { shownFirst = true; first() }
                    }
                }
            }
        }

        fun shutdown() {
            if (!::handler.isInitialized) { quitSafely(); return }
            handler.post {
                alive = false
                Choreographer.getInstance().removeFrameCallback(this)
                stopDecoder()
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

        private fun setupGl() {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2); EGL14.eglInitialize(dpy, ver, 0, ver, 1)
            val attrs = intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_NONE)
            val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            EGL14.eglChooseConfig(dpy, attrs, 0, cfgs, 0, 1, n, 0)
            ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surf = EGL14.eglCreateWindowSurface(dpy, cfgs[0], out, intArrayOf(EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            EGL14.eglSwapInterval(dpy, 0)
            val t = IntArray(1); GLES20.glGenTextures(1, t, 0); tex = t[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            prog = program(VS, FS)
            GLES20.glClearColor(0f, 0f, 0f, 0f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); EGL14.eglSwapBuffers(dpy, surf)
        }

        private fun draw(update: Boolean) {
            val st = video ?: return
            if (update) runCatching { st.updateTexImage() }.onFailure { return }
            st.getTransformMatrix(m)
            GLES20.glViewport(0, 0, vw, vh)
            GLES20.glClearColor(0f, 0f, 0f, 0f); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (fw > 0 && fh > 0) {
                // fit inside the view; the feet (not the frame's bottom margin) sit on the bottom edge
                val usable = fh * (1f - gap)
                val s = minOf(vw / fw.toFloat(), vh / usable)
                val w = (fw * s).toInt(); val h = (fh * s).toInt()
                GLES20.glViewport((vw - w) / 2, -(gap * h).toInt(), w, h)
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
                    gl_FragColor = vec4(min(c, vec3(a)), a);
                }"""
        }
    }
}
