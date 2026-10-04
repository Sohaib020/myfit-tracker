package com.myfit.tracker.ui.pip

import android.content.Context
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.myfit.tracker.ui.arena.Mascot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Home buddy: Pip by default, or any unlocked Arena character. Pip's art ships in the APK; other characters'
 * full animation packs (~10 MB each) are downloaded on demand from the "charpacks" release and unzipped to
 * app storage. Everything that draws the buddy goes through [open]/[source] so it doesn't care which one it is.
 */
object Buddy {
    private const val BASE = "https://github.com/Sohaib020/myfit-tracker/releases/download/charpacks/"

    /** The buddy currently shown (falls back to Pip if its pack isn't installed). */
    val active = MutableStateFlow(Mascot.PIP)
    /** Download progress per character id (0..1), or -1 on failure. */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    @Volatile var lookN = 13; private set
    @Volatile private var dir: File? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private fun root(c: Context) = File(c.applicationContext.filesDir, "buddy")
    fun packDir(c: Context, m: Mascot) = File(root(c), m.id)
    fun installed(c: Context, m: Mascot) = m == Mascot.PIP || File(packDir(c, m), "pack.json").exists()
    val name: String get() = active.value.label.substringBefore(' ')

    /** Call once at start-up (and after changes). */
    fun init(c: Context) {
        val id = prefs(c).getString("buddy", "pip")
        val m = Mascot.entries.firstOrNull { it.id == id } ?: Mascot.PIP
        apply(c, if (installed(c, m)) m else Mascot.PIP)
    }

    private fun apply(c: Context, m: Mascot) {
        if (m == Mascot.PIP) { dir = null; lookN = 13 }
        else {
            val d = packDir(c, m); dir = d
            lookN = runCatching { JSONObject(File(d, "pack.json").readText()).optInt("lookN", 7) }.getOrDefault(7)
        }
        active.value = m
        onChange.forEach { it() }
    }

    internal val onChange = mutableListOf<() -> Unit>()

    fun choose(c: Context, m: Mascot) {
        prefs(c).edit().putString("buddy", m.id).apply()
        if (installed(c, m)) apply(c, m)
    }

    /** Opens a buddy file by its Pip-relative path ("idle.webp", "talk/0.webp", "look/look_03_03.webp"). */
    fun open(c: Context, rel: String): InputStream = dir?.let { File(it, rel).inputStream() } ?: c.assets.open("pip/$rel")

    @RequiresApi(28)
    fun source(c: Context, rel: String): ImageDecoder.Source = dir?.let { ImageDecoder.createSource(File(it, rel)) } ?: ImageDecoder.createSource(c.assets, "pip/$rel")

    /** Downloads + unzips a character's pack, then makes it the buddy. Returns true on success. */
    suspend fun download(c: Context, m: Mascot): Boolean = withContext(Dispatchers.IO) {
        if (installed(c, m)) { withContext(Dispatchers.Main) { choose(c, m) }; return@withContext true }
        fun set(v: Float) { progress.value = progress.value + (m.id to v) }
        val tmp = File(root(c), m.id + ".part").apply { parentFile?.mkdirs(); deleteRecursively() }
        val ok = runCatching {
            var conn = URL(BASE + m.id + ".zip").openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000; conn.readTimeout = 30_000; conn.instanceFollowRedirects = true
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) { conn = URL(conn.getHeaderField("Location")).openConnection() as HttpURLConnection }
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.coerceAtLeast(1)
            var read = 0L
            set(0f)
            ZipInputStream(object : java.io.FilterInputStream(conn.inputStream) {
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { read += it; set((read.toFloat() / total).coerceAtMost(0.99f)) } }
            }).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val f = File(tmp, e.name)
                    require(f.canonicalPath.startsWith(tmp.canonicalPath)) { "bad entry" }
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } }
                    e = z.nextEntry
                }
            }
            require(File(tmp, "pack.json").exists())
            val dst = packDir(c, m); dst.deleteRecursively(); tmp.renameTo(dst)
        }.isSuccess
        if (!ok) tmp.deleteRecursively()
        set(if (ok) 1f else -1f)
        if (ok) withContext(Dispatchers.Main) { choose(c, m) }
        ok
    }

    fun remove(c: Context, m: Mascot) {
        if (m == Mascot.PIP) return
        if (active.value == m) choose(c, Mascot.PIP).also { apply(c, Mascot.PIP) }
        packDir(c, m).deleteRecursively()
    }

    fun sizeOnDisk(c: Context, m: Mascot): Long = packDir(c, m).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    @Suppress("unused") private val sdk = Build.VERSION.SDK_INT
}
