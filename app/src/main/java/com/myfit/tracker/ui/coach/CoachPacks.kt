package com.myfit.tracker.ui.coach

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * R21: animated 3D coach moves, one small pack per character (≈4–8 MB of animated WebP), downloaded the first time
 * that coach appears — the APK only carries each coach's portrait and a standing still.
 */
object CoachPacks {
    private const val BASE = "https://github.com/Sohaib020/myfit-tracker/releases/download/coach3d-v1"

    /** Coach look id (Coach.Look / NLook) -> pack name. */
    fun pack(lookId: String) = when (lookId) { "m" -> "trainer_m"; "f" -> "trainer_f"; "nm" -> "nutri_m"; "nf" -> "nutri_f"; else -> "trainer_m" }

    /** Bumps whenever a pack finishes installing, so screens switch from the still to the animation. */
    val installed = MutableStateFlow(0)
    private val running = java.util.Collections.synchronizedSet(HashSet<String>())

    private fun dir(c: Context, p: String) = File(c.filesDir, "coach3d/$p")
    /** Bump when the release packs are re-rendered so phones replace their old copy (v3 = R22 Mixamo mocap). */
    private const val VER = "3"
    fun isInstalled(c: Context, lookId: String) = File(dir(c, pack(lookId)), ".complete").let { it.exists() && it.readText().trim() == VER }

    /** Old pose names from the 2D coaches -> 3D clip names (anything else is already a clip name). */
    fun clipFor(pose: String): String = when (pose) {
        "stand" -> "idle"; "clipboard", "point", "demo" -> "talk"; "rest" -> "idle"; "thumbs" -> "thumbsup"; "cart" -> "plate"
        else -> pose
    }

    /** The animated clip file, or null if the pack (or that move) isn't there. */
    fun clip(c: Context, lookId: String, pose: String): File? {
        if (!isInstalled(c, lookId)) return null
        val d = dir(c, pack(lookId))
        return File(d, clipFor(pose) + ".webp").takeIf { it.exists() } ?: File(d, "idle.webp").takeIf { it.exists() }
    }

    /** Downloads and installs a coach's pack once (quietly; retried on the next screen visit if it fails). */
    suspend fun ensure(c: Context, lookId: String): Boolean = withContext(Dispatchers.IO) {
        val p = pack(lookId)
        if (isInstalled(c, lookId)) return@withContext true
        if (!running.add(p)) return@withContext false
        try {
            val d = dir(c, p); d.deleteRecursively(); d.mkdirs()
            var conn = URL("$BASE/$p.zip").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true; conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            // GitHub release downloads redirect across hosts
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect(); conn = URL(loc).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            }
            if (conn.responseCode != 200) return@withContext false
            ZipInputStream(conn.inputStream.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = File(e.name).name
                    if (e.isDirectory || !name.endsWith(".webp")) continue
                    val out = File(d, name)
                    if (!out.canonicalPath.startsWith(d.canonicalPath)) continue          // zip-slip guard
                    out.outputStream().use { zip.copyTo(it) }
                }
            }
            if (File(d, "idle.webp").exists()) { File(d, ".complete").writeText(VER); installed.value++; true } else false
        } catch (t: Throwable) { false } finally { running.remove(p) }
    }

    /** Settings → storage: remove a downloaded pack. */
    fun remove(c: Context, lookId: String) { dir(c, pack(lookId)).deleteRecursively(); installed.value++ }
}
