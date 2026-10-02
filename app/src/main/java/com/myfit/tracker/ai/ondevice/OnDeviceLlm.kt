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

    /** True when the model is downloaded, the user wants it, and developers haven't forced cloud. */
    suspend fun available(): Boolean {
        val c = hub.prefs.cfg()
        if (!c.useOnDevice || c.forceCloud) return false
        if (DeviceCheck.check(app).tier == DeviceCheck.Tier.UNSUPPORTED) return false
        return hub.models.installedFile() != null
    }

    private fun backendsToTry(pref: String, working: String): List<String> = when (pref) {
        "gpu" -> listOf("gpu")
        "cpu" -> listOf("cpu")
        else -> if (working.isNotBlank()) listOf(working) + listOf("gpu", "cpu").filter { it != working } else listOf("gpu", "cpu")
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
        for (b in backendsToTry(c.backendPref, c.workingBackend)) {
            try {
                val e = Engine(EngineConfig(modelPath = file.absolutePath, backend = backend(b), visionBackend = backend(b), cacheDir = app.cacheDir.path))
                e.initialize()
                engine = e; enginePath = file.absolutePath; backendInUse = b
                if (c.workingBackend != b) hub.prefs.setWorkingBackend(b)
                _status.value = Status.READY
                lastError = null
                return e
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                last = t
            }
        }
        _status.value = Status.FAILED
        lastError = last?.message ?: "couldn't start"
        throw IllegalStateException("The offline brain couldn't start on this phone (${lastError}).")
    }

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MS)
            lock.withLock { releaseLocked() }
        }
    }

    private suspend fun <T> run(timeoutMs: Long, block: (Engine) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            idleJob?.cancel()
            val e = ensure()
            _status.value = Status.BUSY
            try {
                withTimeout(timeoutMs) { block(e) }
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
            turns.forEach { (role, text) -> append(if (role == "user") "User: " else "Pip: ").append(text.trim()).append("\n\n") }
            append("Pip:")
        }
        e.createConversation().use { conv -> conv.sendMessage(prompt).toString().trim() }
    }

    /** Image + prompt → text (food photos). */
    suspend fun vision(prompt: String, jpeg: ByteArray): String = run(120_000) { e ->
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
