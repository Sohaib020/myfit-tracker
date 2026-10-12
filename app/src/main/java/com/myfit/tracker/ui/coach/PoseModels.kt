package com.myfit.tracker.ui.coach

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * R20: the camera coach's MediaPipe models (pose heavy ≈ 29 MB + face ≈ 3.6 MB) are downloaded once on first use
 * instead of shipping inside the APK. Same files, same accuracy. Stored in the app's private files.
 */
object PoseModels {
    private const val BASE = "https://storage.googleapis.com/mediapipe-models"
    const val POSE = "pose_landmarker_heavy.task"
    const val FACE = "face_landmarker.task"
    private val urls = mapOf(
        POSE to "$BASE/pose_landmarker/pose_landmarker_heavy/float16/1/pose_landmarker_heavy.task",
        FACE to "$BASE/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
    )
    /** Approximate total download, for the prompt. */
    const val SIZE_MB = 33

    /** Download progress 0..1 while running, null otherwise. */
    val progress = MutableStateFlow<Float?>(null)

    private fun dir(c: Context) = File(c.filesDir, "pose").apply { mkdirs() }
    fun file(c: Context, name: String) = File(dir(c), name)

    /** The pose model is required; the face model is a bonus (head outline / direction). */
    fun installed(c: Context) = file(c, POSE).let { it.exists() && it.length() > 1_000_000 }

    /** Memory-maps a downloaded model for MediaPipe (no copy into the Java heap). */
    fun buffer(c: Context, name: String): ByteBuffer? {
        val f = file(c, name)
        if (!f.exists() || f.length() < 100_000) return null
        return java.io.RandomAccessFile(f, "r").use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.length()) }
    }

    /** Downloads whatever is missing. Returns true when the pose model is ready. */
    suspend fun download(c: Context): Boolean = withContext(Dispatchers.IO) {
        if (progress.value != null) return@withContext installed(c)
        progress.value = 0f
        try {
            val todo = urls.filterKeys { k -> !file(c, k).let { it.exists() && it.length() > 100_000 } }
            val weights = mapOf(POSE to 0.89f, FACE to 0.11f)
            var doneW = urls.keys.filter { it !in todo }.sumOf { (weights[it] ?: 0f).toDouble() }.toFloat()
            for ((name, url) in todo) {
                runCatching {
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
                    val part = File(dir(c), "$name.part")
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024); var read = 0L
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); read += n
                                if (total > 0) progress.value = doneW + (weights[name] ?: 0f) * (read.toFloat() / total)
                            }
                        }
                    }
                    if (total > 0 && part.length() != total) { part.delete(); error("incomplete") }
                    part.renameTo(file(c, name))
                }
                doneW += weights[name] ?: 0f
            }
            installed(c)
        } finally { progress.value = null }
    }

    /** Frees the space (Settings → storage could call this). */
    fun remove(c: Context) { dir(c).deleteRecursively() }
}
