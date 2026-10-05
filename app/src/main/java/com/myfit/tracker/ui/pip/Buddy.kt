package com.myfit.tracker.ui.pip

import android.content.Context
import android.graphics.ImageDecoder
import androidx.annotation.RequiresApi
import com.myfit.tracker.ui.arena.Mascot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Home buddy: Pip by default, or any unlocked Arena character.
 *  - Pip ships in the APK with his full set (assets/pip).
 *  - Every other character ships only its portrait (assets/buddy/<id>/portrait.webp). Its animation pack
 *    (~1.7 MB: 8 key moves + talk frames) is downloaded the first time you pick it, from the GitHub release
 *    "buddies-v1", into filesDir/buddy/<id>/. Until then (or offline) it shows its portrait.
 * Moves a character doesn't have play their closest kept move. Everything that draws the buddy goes through
 * [open]/[source]/[openAsset] so it doesn't care where the files live.
 */
object Buddy {
    private const val PACKS = "https://github.com/Sohaib020/myfit-tracker/releases/download/buddies-v1"

    /** The buddy currently shown. */
    val active = MutableStateFlow(Mascot.PIP)
    @Volatile var lookN = 13; private set
    /** Only Pip has the look-at-your-finger head grid. */
    val hasLook: Boolean get() = active.value == Mascot.PIP

    /** Download progress per buddy id (0..1), for the chooser UI. */
    val downloading = MutableStateFlow<Map<String, Float>>(emptyMap())

    /** Moves in every lite pack. */
    val LITE = setOf("idle", "wave", "celebrate", "thinking", "love", "sleepy", "letsgo", "train")
    private val FALLBACK = mapOf(
        "excited" to "celebrate", "dance" to "celebrate", "spin" to "celebrate", "cheer" to "celebrate", "clap" to "celebrate",
        "highfive" to "celebrate", "laugh" to "celebrate", "jumpingjacks" to "train", "squat" to "train", "stretch" to "train",
        "jog" to "train", "flex" to "letsgo", "salute" to "letsgo", "thumbsup" to "letsgo", "yes" to "letsgo", "point" to "letsgo",
        "hydrate" to "letsgo", "fuel" to "letsgo", "hearteyes" to "love", "blowkiss" to "love", "shy" to "love", "wink" to "love",
        "curious" to "thinking", "shrug" to "thinking", "surprised" to "thinking", "facepalm" to "thinking", "concerned" to "thinking",
        "sad" to "thinking", "grumpy" to "thinking", "pout" to "thinking", "dizzy" to "thinking", "no" to "thinking",
        "yawn" to "sleepy", "meditate" to "sleepy", "sneeze" to "sleepy", "bow" to "wave", "peekaboo" to "wave",
    )

    private var appCtx: Context? = null
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private fun dir(c: Context, m: Mascot) = File(c.applicationContext.filesDir, "buddy/${m.id}")

    /** Pip is always installed; others once their pack has been downloaded. */
    fun installed(c: Context, m: Mascot) = m == Mascot.PIP || File(dir(c, m), ".complete").exists()
    val name: String get() = active.value.label.substringBefore(' ')

    /** How the AI should introduce itself. */
    fun persona(): String = active.value.let { m ->
        if (m == Mascot.PIP) "You are Pip, the cheerful little buddy inside \"MyFit Tracker\", a private fitness logbook app. You look like a soft mint plush with a navy striped sweatband, a curly antenna and little sneakers."
        else "You are ${m.label.substringBefore(' ')} (${m.label}), the user's chosen buddy inside \"MyFit Tracker\", a private fitness logbook app — a friendly 3D plush character from Pakistan's wildlife. Your motto: \"${m.tagline}\". Always call yourself ${m.label.substringBefore(' ')}, never Pip."
    }

    /** Call once at start-up. */
    fun init(c: Context) {
        appCtx = c.applicationContext
        val id = prefs(c).getString("buddy", "pip")
        val m = Mascot.entries.firstOrNull { it.id == id } ?: Mascot.PIP
        apply(m)
        // the chosen buddy's pack is missing (e.g. after the update that stopped bundling them): fetch it quietly
        if (!installed(c, m)) {
            val app = c.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                if (download(app, m) == null && active.value == m) kotlinx.coroutines.withContext(Dispatchers.Main) { apply(m) }
            }
        }
    }

    private fun apply(m: Mascot) {
        lookN = if (m == Mascot.PIP) 13 else 1
        active.value = m
        onChange.forEach { it() }
    }

    internal val onChange = mutableListOf<() -> Unit>()

    fun choose(c: Context, m: Mascot) {
        prefs(c).edit().putString("buddy", m.id).apply()
        apply(m)
    }

    /**
     * Downloads (once) and installs [m]'s animation pack. Returns null on success, else a short error.
     * Safe to call again — a finished pack is skipped.
     */
    suspend fun download(c: Context, m: Mascot): String? = withContext(Dispatchers.IO) {
        if (installed(c, m)) return@withContext null
        val target = dir(c, m)
        val tmp = File(target.parentFile, "${m.id}.tmp").apply { deleteRecursively(); mkdirs() }
        try {
            var conn = URL("$PACKS/${m.id}.zip").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true; conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect(); conn = URL(loc).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            }
            if (conn.responseCode !in 200..299) return@withContext "Couldn't download ${m.label.substringBefore(' ')} (HTTP ${conn.responseCode})"
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            var read = 0L
            val counting = object : java.io.FilterInputStream(conn.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
                    if (n > 0) { read += n; downloading.value = downloading.value + (m.id to (read.toFloat() / total).coerceIn(0f, 1f)) }
                }
            }
            ZipInputStream(counting.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val out = File(tmp, e.name).canonicalFile
                    if (!out.path.startsWith(tmp.canonicalPath)) continue          // zip-slip guard
                    if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { zip.copyTo(it) } }
                }
            }
            if (!File(tmp, "idle.webp").exists()) return@withContext "That pack looks incomplete — try again"
            File(tmp, ".complete").writeText("1")
            target.deleteRecursively()
            if (!tmp.renameTo(target)) return@withContext "Couldn't save the pack"
            null
        } catch (e: Exception) {
            "No connection — ${m.label.substringBefore(' ')} will download when you're online"
        } finally {
            tmp.takeIf { it.exists() && it.name.endsWith(".tmp") }?.deleteRecursively()
            downloading.value = downloading.value - m.id
        }
    }

    /** Removes a downloaded pack (Settings → storage). */
    fun remove(c: Context, m: Mascot) { if (m != Mascot.PIP) dir(c, m).deleteRecursively() }

    /** Asset-relative path for a Pip-relative file ("idle.webp", "talk/0.webp", "look/…") of [m]. */
    fun path(rel: String, m: Mascot = active.value): String {
        if (m == Mascot.PIP) return "pip/$rel"
        val base = "buddy/${m.id}/"
        return when {
            rel.startsWith("talk/") -> base + rel
            rel.startsWith("look/") -> base + "portrait.webp"
            else -> {
                val clip = rel.removeSuffix(".webp")
                base + (if (clip in LITE) clip else FALLBACK[clip] ?: "idle") + ".webp"
            }
        }
    }

    /** Opens a "buddy/<id>/…" or "pip/…" path: bundled asset, downloaded pack file, or the portrait as a stand-in. */
    fun openAsset(c: Context, assetPath: String): InputStream {
        if (!assetPath.startsWith("buddy/")) return c.assets.open(assetPath)
        val f = File(c.applicationContext.filesDir, assetPath)
        if (f.exists()) return f.inputStream()
        return runCatching { c.assets.open(assetPath) }.getOrElse { c.assets.open(portraitOf(assetPath)) }
    }

    /** "buddy/<id>/anything" → "buddy/<id>/portrait.webp" (always bundled). */
    private fun portraitOf(assetPath: String) = "buddy/" + assetPath.removePrefix("buddy/").substringBefore('/') + "/portrait.webp"

    fun open(c: Context, rel: String): InputStream = openAsset(c, path(rel))

    @RequiresApi(28)
    fun source(c: Context, rel: String): ImageDecoder.Source = sourceFor(c, path(rel))

    @RequiresApi(28)
    fun sourceFor(c: Context, assetPath: String): ImageDecoder.Source {
        if (assetPath.startsWith("buddy/")) {
            val f = File(c.applicationContext.filesDir, assetPath)
            if (f.exists()) return ImageDecoder.createSource(f)
            return runCatching { c.assets.openFd(assetPath).close(); ImageDecoder.createSource(c.assets, assetPath) }
                .getOrElse { ImageDecoder.createSource(c.assets, portraitOf(assetPath)) }
        }
        return ImageDecoder.createSource(c.assets, assetPath)
    }

    /** True when the animated clip for [assetPath] is really available (pack downloaded or bundled). */
    fun hasClip(c: Context, assetPath: String): Boolean =
        !assetPath.startsWith("buddy/") || File(c.applicationContext.filesDir, assetPath).exists() ||
            runCatching { c.assets.open(assetPath).close(); true }.getOrDefault(false)
}
