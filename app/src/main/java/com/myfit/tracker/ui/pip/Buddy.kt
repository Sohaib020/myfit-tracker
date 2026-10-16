package com.myfit.tracker.ui.pip

import android.content.Context
import android.graphics.ImageDecoder
import androidx.annotation.RequiresApi
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
 * Home buddy: Pip by default, or a champion look you own (R24 — the champions replaced the plush Arena buddies).
 *  - Pip ships in the APK with his full set (assets/pip).
 *  - A champion look ships only its portrait (assets/champ/<id>/portrait.webp). Its clip pack (animated WebPs named
 *    like Pip's moves) downloads the first time you pick it, from the GitHub release "champs-v1", into
 *    filesDir/champ/<id>/. Capable phones can show the real-time 3D model instead (see ChampStage).
 * Moves a pack doesn't have play their closest kept move. Everything that draws the buddy goes through
 * [open]/[source]/[openAsset], so it doesn't care where the files live.
 */
object Buddy {
    private const val PACKS = "https://github.com/Sohaib020/myfit-tracker/releases/download/champs-v1"

    /** The buddy currently shown. */
    val active = MutableStateFlow(BuddyChar.PIP)

    /** Download progress per look id (0..1), for the chooser UI. */
    val downloading = MutableStateFlow<Map<String, Float>>(emptyMap())

    private val FALLBACK = mapOf(
        "excited" to "celebrate", "dance" to "celebrate", "spin" to "celebrate", "cheer" to "celebrate", "clap" to "celebrate",
        "highfive" to "celebrate", "laugh" to "celebrate", "jumpingjacks" to "train", "squat" to "train", "stretch" to "train",
        "jog" to "train", "flex" to "letsgo", "salute" to "letsgo", "thumbsup" to "letsgo", "yes" to "letsgo", "point" to "letsgo",
        "hydrate" to "letsgo", "fuel" to "letsgo", "hearteyes" to "love", "blowkiss" to "love", "shy" to "love", "wink" to "love",
        "curious" to "thinking", "shrug" to "thinking", "surprised" to "thinking", "facepalm" to "thinking", "concerned" to "thinking",
        "sad" to "thinking", "grumpy" to "thinking", "pout" to "thinking", "dizzy" to "thinking", "no" to "thinking",
        "yawn" to "sleepy", "meditate" to "sleepy", "sneeze" to "sleepy", "bow" to "wave", "peekaboo" to "wave",
    )

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private fun dir(c: Context, b: BuddyChar) = File(c.applicationContext.filesDir, "champ/${b.id}")

    /** Pip is always installed; a champion look once its pack has been downloaded. */
    fun installed(c: Context, b: BuddyChar) = b.isPip || File(dir(c, b), ".complete").exists()
    val name: String get() = active.value.label

    /** How the AI should introduce itself. */
    fun persona(): String = active.value.let { b ->
        val ch = b.champion
        if (ch == null) "You are Pip, the cheerful little buddy inside \"MyFit Tracker\", a private fitness logbook app. You look like a soft mint plush with a navy striped sweatband, a curly antenna and little sneakers."
        else "You are ${ch.tag}, the user's chosen buddy inside \"MyFit Tracker\", a private fitness logbook app — an original game-hero character, a " +
            "${ch.archetype}${if (ch.animal) " (a humanoid animal champion)" else ""}, here in your ${b.tier.label} look. Your motto: \"${ch.motto}\". " +
            "You're a fictional character, not a real person. Always call yourself ${ch.tag}, never Pip."
    }

    /** Call once at start-up. */
    fun init(c: Context) {
        val b = BuddyChar.parse(prefs(c).getString("buddy", "pip"))
        // a look you no longer own (e.g. data cleared) falls back to Pip
        val ok = b.champion?.let { Champions.owns(c, it, b.tier) } ?: true
        val m = if (ok) b else BuddyChar.PIP
        apply(m)
        if (!installed(c, m)) {
            val app = c.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
                if (download(app, m) == null && active.value == m) kotlinx.coroutines.withContext(Dispatchers.Main) { apply(m) }
            }
        }
    }

    private fun apply(m: BuddyChar) {
        active.value = m
        onChange.forEach { it() }
    }

    internal val onChange = mutableListOf<() -> Unit>()

    fun choose(c: Context, m: BuddyChar) {
        prefs(c).edit().putString("buddy", m.id).apply()
        apply(m)
    }

    /**
     * Downloads (once) and installs [m]'s clip pack. Returns null on success, else a short error.
     * Safe to call again — a finished pack is skipped.
     */
    suspend fun download(c: Context, m: BuddyChar): String? = withContext(Dispatchers.IO) {
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
            if (conn.responseCode !in 200..299) return@withContext "Couldn't download ${m.label} (HTTP ${conn.responseCode})"
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
            "No connection — ${m.label} will download when you're online"
        } finally {
            tmp.takeIf { it.exists() && it.name.endsWith(".tmp") }?.deleteRecursively()
            downloading.value = downloading.value - m.id
        }
    }

    /** Removes a downloaded pack (Settings → storage). */
    fun remove(c: Context, m: BuddyChar) { if (!m.isPip) dir(c, m).deleteRecursively() }

    /** Asset-relative path for a Pip-relative file ("idle.webp", "talk/0.webp") of [m]. */
    fun path(rel: String, m: BuddyChar = active.value): String {
        if (m.isPip) return "pip/$rel"
        val base = "champ/${m.id}/"
        if (rel.startsWith("talk/") || rel.startsWith("look/")) return base + "portrait.webp"
        val clip = rel.removeSuffix(".webp")
        val have = Champions.clips(m.tier)
        val pick = when {
            clip in have -> clip
            Champions.alias(clip, m.tier)?.let { it in have } == true -> Champions.alias(clip, m.tier)!!
            FALLBACK[clip] in have -> FALLBACK[clip]!!
            else -> "idle"
        }
        return "$base$pick.webp"
    }

    /** Champions draw no mouth frames (realistic faces): when talking they play their "talk" clip instead. */
    val talksWithClip: Boolean get() = !active.value.isPip

    /** Opens a "champ/<id>/…" or "pip/…" path: downloaded pack file, bundled asset, or the portrait as a stand-in. */
    private fun dl(p: String) = p.startsWith("champ/") || p.startsWith("buddy/")   // downloadable dirs (buddy/ = Arena cast art)

    fun openAsset(c: Context, assetPath: String): InputStream {
        if (!dl(assetPath)) return c.assets.open(assetPath)
        val f = File(c.applicationContext.filesDir, assetPath)
        if (f.exists()) return f.inputStream()
        return runCatching { c.assets.open(assetPath) }.getOrElse { c.assets.open(portraitOf(assetPath)) }
    }

    /** "champ/<id>/anything" → "champ/<id>/portrait.webp" (always bundled; same for buddy/). */
    private fun portraitOf(assetPath: String): String {
        val root = assetPath.substringBefore('/')
        return root + "/" + assetPath.removePrefix("$root/").substringBefore('/') + "/portrait.webp"
    }

    fun open(c: Context, rel: String): InputStream = openAsset(c, path(rel))

    @RequiresApi(28)
    fun source(c: Context, rel: String): ImageDecoder.Source = sourceFor(c, path(rel))

    @RequiresApi(28)
    fun sourceFor(c: Context, assetPath: String): ImageDecoder.Source {
        if (dl(assetPath)) {
            val f = File(c.applicationContext.filesDir, assetPath)
            if (f.exists()) return ImageDecoder.createSource(f)
            return runCatching { c.assets.openFd(assetPath).close(); ImageDecoder.createSource(c.assets, assetPath) }
                .getOrElse { ImageDecoder.createSource(c.assets, portraitOf(assetPath)) }
        }
        return ImageDecoder.createSource(c.assets, assetPath)
    }

    /** True when the animated clip for [assetPath] is really available (pack downloaded or bundled). */
    fun hasClip(c: Context, assetPath: String): Boolean =
        !dl(assetPath) || File(c.applicationContext.filesDir, assetPath).exists() ||
            runCatching { c.assets.open(assetPath).close(); true }.getOrDefault(false)

    /** Bundled portrait of any buddy (chooser, chat header, avatars). */
    fun portraitAsset(b: BuddyChar): String = if (b.isPip) "pip/portrait.webp" else "champ/${b.id}/portrait.webp"
}
