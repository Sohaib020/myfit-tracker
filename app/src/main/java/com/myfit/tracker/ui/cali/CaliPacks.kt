package com.myfit.tracker.ui.cali

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.myfit.tracker.domain.Cali
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * R32: your trainer performing every calisthenics step — a looping alpha MP4 per rep step and a still per step
 * (release cali3d-v1, one pack per trainer, ~6 MB, downloaded once). Until it's in, the drawn figure stands in.
 */
object CaliPacks {
    private const val BASE = "https://github.com/Sohaib020/myfit-tracker/releases/download/cali3d-v1"
    private const val VER = "1"
    val installed = MutableStateFlow(0)
    val progress = MutableStateFlow<Float?>(null)
    private val running = java.util.Collections.synchronizedSet(HashSet<String>())

    /** Trainer look → pack (the female trainer has her own pack; everyone else gets the male trainer). */
    fun pack(c: Context): String { com.myfit.tracker.domain.Coach.load(c); return if (com.myfit.tracker.domain.Coach.prefs.value.look.id == "f") "trainer_f" else "trainer_m" }
    private fun dir(c: Context, p: String) = File(c.filesDir, "cali3d/$p")
    fun isInstalled(c: Context) = File(dir(c, pack(c)), ".complete").let { it.exists() && it.readText().trim() == VER }
    fun clip(c: Context, key: String): File? = if (!isInstalled(c)) null else File(dir(c, pack(c)), "$key.mp4").takeIf { it.exists() }
    fun still(c: Context, key: String): File? = if (!isInstalled(c)) null else File(dir(c, pack(c)), "$key.webp").takeIf { it.exists() }

    suspend fun ensure(c: Context): Boolean = withContext(Dispatchers.IO) {
        val p = pack(c)
        if (isInstalled(c)) return@withContext true
        if (!running.add(p)) return@withContext false
        try {
            progress.value = 0f
            val d = dir(c, p); d.deleteRecursively(); d.mkdirs()
            var conn = URL("$BASE/$p.zip").openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true; conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect(); conn = URL(loc).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            }
            if (conn.responseCode != 200) return@withContext false
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 6_000_000L
            val counting = object : java.io.FilterInputStream(conn.inputStream.buffered()) {
                var n = 0L
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { n += it; progress.value = (n.toFloat() / total).coerceIn(0f, 1f) } }
            }
            var count = 0
            ZipInputStream(counting).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = File(e.name).name
                    if (e.isDirectory || !(name.endsWith(".webp") || name.endsWith(".mp4"))) continue
                    val out = File(d, name)
                    if (!out.canonicalPath.startsWith(d.canonicalPath)) continue          // zip-slip guard
                    out.outputStream().use { zip.copyTo(it) }; count++
                }
            }
            if (count > 20) { File(d, ".complete").writeText(VER); installed.value++; true } else false
        } catch (t: Throwable) { false } finally { running.remove(p); progress.value = null }
    }
}

private val stillCache = android.util.LruCache<String, ImageBitmap>(40)

/**
 * A calisthenics step as your coach: the looping 3D clip when [animate] (and downloaded), the 3D still otherwise,
 * or the drawn figure until the pack is in.
 */
@Composable
fun CaliArt(step: Cali.Step, color: Color, modifier: Modifier = Modifier, animate: Boolean = false) {
    val ctx = LocalContext.current
    val ver by CaliPacks.installed.collectAsState()
    LaunchedEffect(Unit) { CaliPacks.ensure(ctx) }
    val clip = remember(step.key, ver, animate) { if (animate) CaliPacks.clip(ctx, step.key) else null }
    if (clip != null) {
        com.myfit.tracker.ui.coach.CoachClip(clip, modifier, loop = true)
        return
    }
    val still = remember(step.key, ver) { CaliPacks.still(ctx, step.key) }
    if (still != null) {
        val bmp by produceState(stillCache.get(still.path), still.path) {
            if (value == null) value = withContext(Dispatchers.IO) {
                runCatching { android.graphics.BitmapFactory.decodeFile(still.path).asImageBitmap() }.getOrNull()
            }?.also { stillCache.put(still.path, it) }
        }
        Box(modifier, contentAlignment = Alignment.Center) {
            bmp?.let { Image(it, step.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        return
    }
    CaliFigure(step, color, modifier, animate)
}
