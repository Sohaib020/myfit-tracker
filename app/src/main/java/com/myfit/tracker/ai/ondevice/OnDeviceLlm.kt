package com.myfit.tracker.ai.ondevice

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Pip's offline brain: Gemma running on the phone through LiteRT-LM. Loaded lazily on first use and
 * released after a couple of idle minutes so it doesn't hold ~1–2 GB of RAM (or warm the phone) for nothing.
 */
class OnDeviceLlm(private val app: Context, private val hub: OnDeviceAi) {

    enum class Status { OFF, LOADING, READY, BUSY, FAILED }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status
    var lastError: String? = null
        private set
    var backendInUse: String = ""
        private set

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var enginePath: String? = null
    private var idleJob: Job? = null
    /** False when the loaded model has no image encoder (text-only build) — set from the catalog or learned at runtime. */
    @Volatile var hasVision: Boolean = true
        private set
    private val noVisionFiles = mutableSetOf<String>()

    /** True when the model is downloaded, the user wants it, and developers haven't forced cloud. */
    suspend fun available(): Boolean {
        val c = hub.prefs.cfg()
        if (!c.useOnDevice || c.forceCloud) return false
        if (DeviceCheck.check(app).tier == DeviceCheck.Tier.UNSUPPORTED) return false
        return hub.models.installedFile() != null
    }

    /** (text backend, vision backend) pairs to try, best first. The vision encoder can fail on GPU where text works. */
    private fun backendsToTry(pref: String, working: String): List<Pair<String, String>> {
        val all = when (pref) {
            "gpu" -> listOf("gpu" to "gpu", "gpu" to "cpu")
            "cpu" -> listOf("cpu" to "cpu")
            else -> listOf("gpu" to "gpu", "gpu" to "cpu", "cpu" to "cpu")
        }
        val w = all.firstOrNull { "${it.first}/${it.second}" == working }
        return if (w != null) listOf(w) + (all - w) else all
    }

    private fun backend(id: String): Backend = if (id == "gpu") Backend.GPU() else Backend.CPU()

    /** Must be called with [lock] held. */
    private suspend fun ensure(): Engine {
        val file = hub.models.installedFile() ?: throw IllegalStateException("The offline brain isn't downloaded.")
        engine?.let { if (enginePath == file.absolutePath) return it }
        releaseLocked()
        _status.value = Status.LOADING
        val c = hub.prefs.cfg()
        var last: Throwable? = null
        val errors = mutableListOf<String>()
        for ((b, v) in backendsToTry(c.backendPref, c.workingBackend)) {
            try {
                val vision = file.absolutePath !in noVisionFiles && (ModelCatalog.all.firstOrNull { it.fileName == file.name }?.vision ?: true)
                var e = Engine(EngineConfig(modelPath = file.absolutePath, backend = backend(b), visionBackend = if (vision) backend(v) else null,
                    maxNumTokens = 4096, maxNumImages = if (vision) 1 else null, cacheDir = app.cacheDir.path))
                e.initialize()
                hasVision = vision
                if (vision) {
                    // some builds ship without the image encoder: then a conversation can't be created — reload text-only
                    val probe = runCatching { e.createConversation().close() }.exceptionOrNull()
                    if (probe != null && (probe.message ?: "").contains("VISION", ignoreCase = true)) {
                        runCatching { e.close() }
                        noVisionFiles += file.absolutePath; hasVision = false
                        e = Engine(EngineConfig(modelPath = file.absolutePath, backend = backend(b), maxNumTokens = 4096, cacheDir = app.cacheDir.path))
                        e.initialize()
                    }
                }
                engine = e; enginePath = file.absolutePath; backendInUse = "$b/$v"
                if (c.workingBackend != "$b/$v") hub.prefs.setWorkingBackend("$b/$v")
                _status.value = Status.READY
                lastError = null
                return e
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                last = t; errors += "$b/$v: ${t.message?.take(120) ?: t.javaClass.simpleName}"
            }
        }
        _status.value = Status.FAILED
        lastError = errors.joinToString(" · ").ifBlank { last?.message ?: "couldn't start" }
        val lowMem = errors.any { it.contains("memory", true) || it.contains("OOM", false) || it.contains("alloc", true) } || last is OutOfMemoryError
        throw IllegalStateException(
            if (lowMem) "Your phone ran out of memory starting the offline brain. Close other apps and try again — phones with 8 GB of RAM or more run it best."
            else "The offline brain couldn't start on this phone (${lastError})."
        )
    }

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            lock.withLock { releaseLocked() }
        }
    }

    /** Quick check from Settings: loads the model and answers one short prompt. Returns a human summary. */
    suspend fun selfTest(): String {
        val t0 = System.currentTimeMillis()
        return try {
            val out = chat("You are a helpful assistant. Answer in one short sentence.", listOf("user" to "Say hello and name one healthy breakfast."))
            "Works offline ✓ (${backendInUse}, ${(System.currentTimeMillis() - t0) / 1000}s): \"${out.take(80)}\""
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException && t !is kotlinx.coroutines.TimeoutCancellationException) throw t
            lastError = lastError ?: t.message
            "Failed: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    private suspend fun <T> run(timeoutMs: Long, block: (Engine) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            idleJob?.cancel()
            val e = ensure()
            _status.value = Status.BUSY
            try {
                withTimeout(timeoutMs) { block(e) }
            } catch (t: Throwable) {
                lastError = if (t is kotlinx.coroutines.TimeoutCancellationException) "took longer than ${timeoutMs / 1000}s" else (t.message ?: t.javaClass.simpleName)
                throw t
            } finally {
                _status.value = Status.READY
                scheduleIdleRelease()
            }
        }
    }

    /** One-shot text answer. [system] is prepended because each call is a fresh conversation. */
    suspend fun chat(system: String, turns: List<Pair<String, String>>): String = run(90_000) { e ->
        val prompt = buildString {
            append(system.trim()).append("\n\n")
            turns.forEach { (role, text) -> append(if (role == "user") "User: " else com.myfit.tracker.ui.pip.Buddy.name + ": ").append(text.trim()).append("\n\n") }
            append(com.myfit.tracker.ui.pip.Buddy.name + ":")
        }
        e.createConversation().use { conv -> conv.sendMessage(prompt).toString().trim() }
    }

    /** Image + prompt → text (food photos). */
    suspend fun vision(prompt: String, jpeg: ByteArray): String = run(120_000) { e ->
        if (!hasVision) throw IllegalStateException("this offline model can't read photos — download the photo model (2.6 GB) in Settings → Pip → Offline brain")
        e.createConversation().use { conv -> conv.sendMessage(Contents.of(Content.ImageBytes(jpeg), Content.Text(prompt))).toString().trim() }
    }

    private fun releaseLocked() {
        runCatching { engine?.close() }
        engine = null; enginePath = null
        if (_status.value != Status.FAILED) _status.value = Status.OFF
    }

    /** Free the model's memory now (e.g. before deleting the file). */
    suspend fun release() = lock.withLock { idleJob?.cancel(); releaseLocked() }

    companion object {
        private const val IDLE_MS = 120_000L
    }
}
