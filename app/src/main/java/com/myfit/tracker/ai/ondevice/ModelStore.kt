package com.myfit.tracker.ai.ondevice

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A downloadable on-device model. Nothing is bundled in the APK. */
data class ModelSpec(
    val id: String,
    val label: String,
    val fileName: String,
    val url: String,
    /** Expected size in bytes (0 = unknown, e.g. a custom URL). */
    val sizeBytes: Long,
    /** Expected SHA-256 (null = take it from the server's X-Linked-Etag header, or size-check only). */
    val sha256: String?,
    val minRamBytes: Long,
    val license: String,
    val source: String,
    val vision: Boolean = true,
    /** Fallback copy on MyFit's GitHub release, split into [ModelCatalog.SPLIT]-byte parts (GitHub caps files at 2 GB). */
    val mirrorParts: List<String> = emptyList(),
)

object ModelCatalog {
    private const val HF = "https://huggingface.co/litert-community"
    private const val MIRROR = "https://github.com/Sohaib020/myfit-tracker/releases/download/models-v1"
    /** Size of each mirror part (the last part is the remainder). Must match .github/workflows/model-mirror.yml. */
    const val SPLIT = 1_500_000_000L

    /** Gemma 4 E2B instruction-tuned, full LiteRT-LM build (2.6 GB): text + image input (reads meal photos), runs on GPU or CPU. Apache 2.0. */
    val DEFAULT = ModelSpec(
        id = "gemma4-e2b", label = "Gemma 4 E2B (2.6 GB · chat + photos)",
        fileName = "gemma-4-E2B-it.litertlm",
        url = "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        minRamBytes = 5_300_000_000L, license = "Apache 2.0", source = "Hugging Face · litert-community",
        mirrorParts = listOf("$MIRROR/gemma-4-E2B-it.litertlm.part00", "$MIRROR/gemma-4-E2B-it.litertlm.part01"),
    )

    /** The GPU-only build (2.0 GB) has no image encoder: chat only. Kept so phones that downloaded it still chat offline. */
    val TEXT = ModelSpec(
        id = "gemma4-e2b-gpu", label = "Gemma 4 E2B text-only (2.0 GB)",
        fileName = "gemma-4-E2B-it-gpu.litertlm",
        url = "$HF/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm",
        sizeBytes = 0L, sha256 = null,
        minRamBytes = 5_300_000_000L, license = "Apache 2.0", source = "Hugging Face · litert-community", vision = false,
    )
    @Deprecated("same as DEFAULT now") val CPU get() = DEFAULT

    /** Bigger, smarter, slower sibling — for 12 GB phones. Developer option only. */
    val E4B = ModelSpec(
        id = "gemma4-e4b", label = "Gemma 4 E4B (12 GB phones, slower)",
        fileName = "gemma-4-E4B-it.litertlm",
        url = "$HF/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240L, sha256 = null,
        minRamBytes = 10_000_000_000L, license = "Apache 2.0", source = "Hugging Face · litert-community",
    )

    val all = listOf(DEFAULT, TEXT, E4B)
    /** Approximate download size for display before the server reports the exact one. */
    fun approxBytes(s: ModelSpec): Long = if (s.sizeBytes > 0) s.sizeBytes else if (s.id == TEXT.id) 2_010_000_000L else 0L

    fun resolve(c: AiConfig): ModelSpec {
        // the old default (text-only GPU build) is migrated to the photo-capable model
        val base = all.firstOrNull { it.id == c.modelPreset && it.id != TEXT.id } ?: DEFAULT
        val o = c.modelUrlOverride.trim()
        if (o.isBlank() || !o.startsWith("https://")) return base
        val name = o.substringBefore('?').substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "custom.litertlm" }
        return if (o == base.url) base else base.copy(id = "custom", label = "Custom model", fileName = "custom-$name", url = o, sizeBytes = 0, sha256 = null, source = o.substringAfter("https://").substringBefore('/'))
    }
}

/** Can this phone run the offline brain? RAM, CPU type and free space. */
object DeviceCheck {
    enum class Tier { RECOMMENDED, OK, UNSUPPORTED }
    data class Capability(val totalRam: Long, val arm64: Boolean, val freeBytes: Long, val tier: Tier, val reason: String?)

    fun check(ctx: Context, spec: ModelSpec = ModelCatalog.DEFAULT): Capability {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val ram = mi.totalMem
        val arm64 = Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }
        val free = runCatching { StatFs(ctx.filesDir.absolutePath).availableBytes }.getOrDefault(0L)
        val (tier, reason) = when {
            !arm64 -> Tier.UNSUPPORTED to "This phone's processor isn't supported (needs 64-bit ARM)."
            ram < spec.minRamBytes -> Tier.UNSUPPORTED to "Your phone isn't compatible with the offline brain: it needs at least 6 GB of RAM and this phone has ${gb(ram)}. Online AI still works."
            ram < spec.minRamBytes + 1_800_000_000L -> Tier.OK to "Works, but answers may be slower on ${gb(ram)} of RAM."
            else -> Tier.RECOMMENDED to null
        }
        return Capability(ram, arm64, free, tier, reason)
    }

    fun gb(b: Long) = "%.1f GB".format(b / 1_000_000_000.0)
}

/** Download / verify / delete the on-device model file. The download itself runs in [ModelDownloadWorker]. */
class ModelStore(private val ctx: Context, private val prefs: AiPrefs) {

    sealed interface DlState {
        data class NotInstalled(val partialBytes: Long, val total: Long) : DlState
        data class Waiting(val wifiOnly: Boolean) : DlState
        data class Downloading(val done: Long, val total: Long, val bytesPerSec: Long) : DlState
        data class Verifying(val progress: Float) : DlState
        data object Installed : DlState
        data class Failed(val message: String) : DlState
    }

    val dir = File(ctx.filesDir, "llm")
    private val _state = MutableStateFlow<DlState>(DlState.NotInstalled(0, 0))
    val state: StateFlow<DlState> = _state

    fun file(s: ModelSpec) = File(dir, s.fileName)
    private fun part(s: ModelSpec) = File(dir, s.fileName + ".part")
    private fun ok(s: ModelSpec) = File(dir, s.fileName + ".ok")

    fun isInstalled(s: ModelSpec) = ok(s).exists() && file(s).exists() && (s.sizeBytes <= 0 || file(s).length() == s.sizeBytes)

    suspend fun spec() = ModelCatalog.resolve(prefs.cfg())

    /** Installed model file, or null. Cheap: only file checks. */
    suspend fun installedFile(): File? = spec().let { s -> if (isInstalled(s)) file(s) else legacy() }

    /** A previously downloaded model that isn't the current choice (e.g. the 2 GB text-only one) — still good for chat. */
    fun legacy(): File? = ModelCatalog.all.firstOrNull { isInstalled(it) }?.let { file(it) }

    /** Does the model file that will be used read images? */
    suspend fun installedHasVision(): Boolean = spec().let { s -> isInstalled(s) && s.vision }

    /** Recompute state from disk + WorkManager (call when the settings card opens / polls). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val s = spec()
        val cur = _state.value
        if (cur is DlState.Downloading || cur is DlState.Verifying) {
            if (workRunning()) return@withContext
        }
        _state.value = when {
            isInstalled(s) -> DlState.Installed
            workState() == WorkInfo.State.ENQUEUED -> DlState.Waiting(!prefs.cfg().allowMobileData)
            workState() == WorkInfo.State.RUNNING -> if (cur is DlState.Downloading || cur is DlState.Verifying) cur else DlState.Downloading(part(s).length(), s.sizeBytes, 0)
            cur is DlState.Failed -> cur
            else -> DlState.NotInstalled(part(s).takeIf { it.exists() }?.length() ?: 0, s.sizeBytes)
        }
    }

    private fun workState(): WorkInfo.State? = runCatching {
        WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(WORK).get(2, TimeUnit.SECONDS).firstOrNull { !it.state.isFinished }?.state
    }.getOrNull()

    private fun workRunning() = workState() == WorkInfo.State.RUNNING

    /** Queue (or re-queue) the download. Wi-Fi only unless the user allowed mobile data. */
    suspend fun start() {
        val mobile = prefs.cfg().allowMobileData
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (mobile) NetworkType.CONNECTED else NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
        _state.value = DlState.Waiting(!mobile)
    }

    /** Stop downloading; the partial file is kept so the next start resumes where it left off. */
    suspend fun pause() {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        val s = spec()
        _state.value = DlState.NotInstalled(part(s).takeIf { it.exists() }?.length() ?: 0, s.sizeBytes)
    }

    /** Remove every model file (installed or partial) and free the memory. */
    suspend fun deleteAll() {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        OnDeviceAi.get(ctx).llm.release()
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.deleteRecursively() } }
        val s = spec()
        _state.value = DlState.NotInstalled(0, s.sizeBytes)
    }

    fun storageUsed(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /**
     * The actual download (resumable via HTTP Range) + SHA-256 check. Runs inside the worker.
     * Returns null on success, else a user-facing error message. Throws only on cancellation.
     */
    suspend fun downloadNow(onProgress: suspend (DlState) -> Unit): String? = withContext(Dispatchers.IO) {
        val s = spec()
        if (isInstalled(s)) { _state.value = DlState.Installed; return@withContext null }
        dir.mkdirs()
        val part = part(s)
        val free = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        val need = (if (s.sizeBytes > 0) s.sizeBytes else 3_000_000_000L) - part.length() + 300_000_000L
        if (free < need) return@withContext fail("Not enough free space — need about ${DeviceCheck.gb(need)} free.")

        // what the server says the file is (Hugging Face sends the LFS sha256 + size before redirecting to its CDN)
        var expectSha = s.sha256
        var expectSize = s.sizeBytes
        runCatching {
            val h = (URL(s.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"; instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 15_000
                setRequestProperty("User-Agent", UA)
            }
            h.responseCode
            h.getHeaderField("X-Linked-Etag")?.trim('"', ' ', 'W', '/')?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }?.let { expectSha = it }
            h.getHeaderField("X-Linked-Size")?.toLongOrNull()?.takeIf { it > 0 }?.let { expectSize = it }
            h.disconnect()
        }

        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val have = part.length()
            if (expectSize > 0 && have == expectSize) break
            if (expectSize in 1 until have) part.delete()
            val c = (URL(s.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Encoding", "identity")
                if (part.length() > 0) setRequestProperty("Range", "bytes=${part.length()}-")
            }
            try {
                val code = c.responseCode
                when {
                    code == 416 -> { if (expectSize <= 0) break else { part.delete(); continue } }
                    (code == 401 || code == 403 || code == 404 || code >= 500) && s.mirrorParts.isNotEmpty() && expectSize > 0 -> {
                        c.disconnect()
                        mirror(s, part, expectSize, onProgress)?.let { return@withContext it }
                        break
                    }
                    code == 401 || code == 403 -> return@withContext fail("The model server refused the download (HTTP $code). If you set a custom URL in developer options, it may need a login.")
                    code == 404 -> return@withContext fail("Model file not found (HTTP 404). Check the model URL in developer options.")
                    code !in 200..299 -> throw java.io.IOException("HTTP $code")
                }
                val resume = code == 206
                if (!resume && part.length() > 0) part.delete() // server ignored Range → start over
                val len = c.contentLengthLong
                if (expectSize <= 0 && len > 0) expectSize = len + (if (resume) part.length() else 0)
                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(part.length())
                    c.inputStream.use { inp ->
                        val buf = ByteArray(256 * 1024)
                        var done = part.length()
                        var lastT = System.currentTimeMillis(); var lastB = done; var bps = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = inp.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastT >= 700) {
                                bps = ((done - lastB) * 1000 / (now - lastT)).let { if (bps == 0L) it else (bps * 2 + it) / 3 }
                                lastT = now; lastB = done
                                val st = DlState.Downloading(done, expectSize, bps)
                                _state.value = st; onProgress(st)
                            }
                        }
                    }
                }
                attempt = 0
                if (expectSize <= 0) break
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (++attempt >= 4) {
                    // the main server keeps failing → try MyFit's mirror before giving up
                    if (s.mirrorParts.isNotEmpty() && expectSize > 0) { mirror(s, part, expectSize, onProgress)?.let { return@withContext it }; break }
                    return@withContext fail("Download interrupted (${e.message ?: "network error"}). It will resume from where it stopped.", keepPartial = true)
                }
                kotlinx.coroutines.delay(2_000L * attempt)
            } finally {
                c.disconnect()
            }
        }

        if (expectSize > 0 && part.length() != expectSize) return@withContext fail("Download incomplete — tap Resume.", keepPartial = true)
        // verify
        val sha = expectSha
        if (sha != null) {
            val got = sha256(part) { p -> val st = DlState.Verifying(p); _state.value = st; onProgress(st) }
            if (!got.equals(sha, ignoreCase = true)) {
                part.delete()
                return@withContext fail("The downloaded file was damaged (checksum mismatch) and was deleted. Please download again.")
            }
        }
        val dest = file(s)
        dest.delete()
        if (!part.renameTo(dest)) return@withContext fail("Couldn't save the model file.")
        ok(s).writeText(sha ?: "size:${dest.length()}")
        // the new model replaces any older one (frees ~2 GB)
        ModelCatalog.all.filter { it.fileName != s.fileName }.forEach { o -> file(o).delete(); ok(o).delete(); part(o).delete() }
        _state.value = DlState.Installed
        onProgress(DlState.Installed)
        null
    }

    /** Downloads the rest of [part] from the split mirror (resumable). Returns null on success, else an error. */
    private suspend fun mirror(s: ModelSpec, part: File, size: Long, onProgress: suspend (DlState) -> Unit): String? {
        s.mirrorParts.forEachIndexed { i, url ->
            val start = i * ModelCatalog.SPLIT
            val end = minOf((i + 1) * ModelCatalog.SPLIT, size)
            var tries = 0
            while (part.length() < end) {
                currentCoroutineContext().ensureActive()
                if (part.length() < start) return fail("Download got out of step — tap Download again.", keepPartial = false).also { part.delete() }
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA); setRequestProperty("Accept-Encoding", "identity")
                    val off = part.length() - start
                    if (off > 0) setRequestProperty("Range", "bytes=$off-")
                }
                try {
                    val code = c.responseCode
                    if (code !in 200..299) throw java.io.IOException("mirror HTTP $code")
                    if (code == 200 && part.length() > start) RandomAccessFile(part, "rw").use { it.setLength(start) }  // no Range support → redo this part
                    RandomAccessFile(part, "rw").use { raf ->
                        raf.seek(part.length())
                        c.inputStream.use { inp ->
                            val buf = ByteArray(256 * 1024)
                            var done = part.length(); var lastT = System.currentTimeMillis(); var lastB = done; var bps = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = inp.read(buf); if (n < 0) break
                                raf.write(buf, 0, n); done += n
                                val now = System.currentTimeMillis()
                                if (now - lastT >= 700) {
                                    bps = ((done - lastB) * 1000 / (now - lastT)).let { if (bps == 0L) it else (bps * 2 + it) / 3 }
                                    lastT = now; lastB = done
                                    val st = DlState.Downloading(done, size, bps); _state.value = st; onProgress(st)
                                }
                            }
                        }
                    }
                    tries = 0
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (++tries >= 4) return fail("Download interrupted (${e.message ?: "network error"}). It will resume from where it stopped.", keepPartial = true)
                    kotlinx.coroutines.delay(2_000L * tries)
                } finally { c.disconnect() }
            }
        }
        return null
    }

    private fun fail(msg: String, keepPartial: Boolean = true): String {
        _state.value = DlState.Failed(msg)
        return msg
    }

    private suspend fun sha256(f: File, progress: suspend (Float) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-256")
        val total = f.length().coerceAtLeast(1)
        var read = 0L
        var lastT = 0L
        FileInputStream(f).use { inp ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = inp.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                read += n
                val now = System.currentTimeMillis()
                if (now - lastT > 500) { lastT = now; progress(read.toFloat() / total) }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun setState(s: DlState) { _state.value = s }

    companion object {
        const val WORK = "llm-download"
        private const val UA = "MyFitTracker-Android"
    }
}
