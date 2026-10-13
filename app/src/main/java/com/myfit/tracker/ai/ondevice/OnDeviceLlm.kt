package com.myfit.tracker.ai.ondevice

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
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
import kotlinx.coroutines.flow.catch

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
        val tier = DeviceTier.profile(app)
        val all = when (pref) {
            "gpu" -> listOf("gpu" to "gpu", "gpu" to "cpu")
            "cpu" -> listOf("cpu" to "cpu")
            // R18: weak / old GPUs run faster on the CPU path, so light mode tries CPU first
            else -> if (tier.preferGpu) listOf("gpu" to "gpu", "gpu" to "cpu", "cpu" to "cpu") else listOf("cpu" to "cpu", "gpu" to "cpu")
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
        val maxTok = DeviceTier.profile(app).maxTokens
        for ((b, v) in backendsToTry(c.backendPref, c.workingBackend)) {
            try {
                // multi-token prediction: several tokens per step on GPU — a big decode speed-up where the build supports it
                setSpeculative(b == "gpu")
                val vision = file.absolutePath !in noVisionFiles && (ModelCatalog.all.firstOrNull { it.fileName == file.name }?.vision ?: true)
                var e = Engine(EngineConfig(modelPath = file.absolutePath, backend = backend(b), visionBackend = if (vision) backend(v) else null,
                    maxNumTokens = maxTok, maxNumImages = if (vision) 1 else null, cacheDir = app.cacheDir.path))
                e.initialize()
                hasVision = vision
                if (vision) {
                    // some builds ship without the image encoder: then a conversation can't be created — reload text-only
                    val probe = runCatching { e.createConversation().close() }.exceptionOrNull()
                    if (probe != null && (probe.message ?: "").contains("VISION", ignoreCase = true)) {
                        runCatching { e.close() }
                        noVisionFiles += file.absolutePath; hasVision = false
                        e = Engine(EngineConfig(modelPath = file.absolutePath, backend = backend(b), maxNumTokens = maxTok, cacheDir = app.cacheDir.path))
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
            if (lowMem) "Your phone ran out of memory starting the offline brain. Close other apps and try again — it will retry in light mode next time."
            else "The offline brain couldn't start on this phone (${lastError})."
        )
    }

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(DeviceTier.profile(app).idleMs)
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

    private suspend fun <T> run(timeoutMs: Long, block: suspend (Engine) -> T): T = withContext(Dispatchers.IO) {
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

    /**
     * R21: first-start preparation — loads the model and generates a few tokens so LiteRT-LM builds and stores its
     * per-phone caches (cacheDir). Called by [BrainWarmup] after a download or an app update; the engine is then let go
     * on the usual idle timer, and the next real load reuses the caches.
     */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        lock.withLock {
            idleJob?.cancel()
            val e = ensure()
            try {
                withTimeout(120_000) { e.createConversation().use { conv -> conv.sendMessage("Reply with one word: ready") } }
            } finally { scheduleIdleRelease() }
        }
    }

    /** Loads the model in the background (e.g. while the food camera is open) so the first answer comes faster. */
    fun prewarm() {
        scope.launch {
            if (!available()) return@launch
            runCatching { lock.withLock { idleJob?.cancel(); ensure(); scheduleIdleRelease() } }
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

    // ---------------------------------------------------------------- R18: reused conversation + streaming

    private var convo: Conversation? = null
    private var convoKey: Int = 0
    private var convoTurns = 0

    private fun closeConvo() { runCatching { convo?.close() }; convo = null; convoKey = 0; convoTurns = 0 }

    /**
     * Pip chat on the phone. The [system] instructions (persona, rules, units, health notes) are processed ONCE per chat
     * and kept in the model's memory (KV cache); every later turn only feeds the new message — follow-ups start
     * answering much sooner. [onText] receives the growing answer as it streams. The conversation restarts when the
     * instructions change, after many turns (to stay inside the context window) or after an error.
     */
    suspend fun chatStream(system: String, message: String, seedTurns: List<Pair<String, String>> = emptyList(), onText: (String) -> Unit = {}): String = run(120_000) { e ->
        val key = system.hashCode()
        if (convo == null || convoKey != key || convoTurns >= MAX_TURNS) {
            closeConvo()
            convo = e.createConversation(ConversationConfig(systemInstruction = Contents.of(system.trim())))
            convoKey = key
        }
        val conv = convo!!
        val first = convoTurns == 0 && seedTurns.isNotEmpty()
        val msg = if (first) buildString {
            append("Earlier in this chat:\n")
            seedTurns.forEach { (r, t) -> append(if (r == "user") "User: " else com.myfit.tracker.ui.pip.Buddy.name + ": ").append(t.trim()).append("\n") }
            append("\n").append(message.trim())
        } else message.trim()
        val sb = StringBuilder()
        var firstAt = 0L
        try {
            conv.sendMessageAsync(msg).catch { throw it }.collect { chunk ->
                if (firstAt == 0L) firstAt = System.currentTimeMillis()
                sb.append(chunk.toString())
                onText(sb.toString())
            }
        } catch (t: Throwable) {
            closeConvo()           // a failed turn can leave the KV cache half-written: start fresh next time
            throw t
        }
        convoTurns++
        // speed test from real use: tokens/s while decoding (≈ 4 characters per token)
        if (firstAt > 0) {
            val secs = (System.currentTimeMillis() - firstAt) / 1000f
            if (secs > 1.5f && sb.length > 120) DeviceTier.recordSpeed(app, sb.length / 4f / secs)
        }
        sb.toString().trim()
    }

    /** Drop the kept conversation (e.g. when the chat screen closes or the user clears the chat). */
    fun resetChat() { scope.launch { lock.withLock { closeConvo() } } }

    private fun setSpeculative(on: Boolean) {
        runCatching {
            val k = Class.forName("com.google.ai.edge.litertlm.ExperimentalFlags")
            val inst = runCatching { k.getField("INSTANCE").get(null) }.getOrNull()
            val m = k.methods.firstOrNull { it.name == "setEnableSpeculativeDecoding" && it.parameterTypes.size == 1 }
            m?.invoke(inst, on)
        }
    }

    /** Image + prompt → text (food photos). */
    suspend fun vision(prompt: String, jpeg: ByteArray): String = run(120_000) { e ->
        if (!hasVision) throw IllegalStateException("this offline model can't read photos — download the photo model (2.6 GB) in Pip settings → Downloads")
        e.createConversation().use { conv -> conv.sendMessage(Contents.of(Content.ImageBytes(jpeg), Content.Text(prompt))).toString().trim() }
    }

    private fun releaseLocked() {
        closeConvo()
        runCatching { engine?.close() }
        engine = null; enginePath = null
        if (_status.value != Status.FAILED) _status.value = Status.OFF
    }

    /** Free the model's memory now (e.g. before deleting the file). */
    suspend fun release() = lock.withLock { idleJob?.cancel(); releaseLocked() }

    companion object {
        private const val MAX_TURNS = 8
    }
}
