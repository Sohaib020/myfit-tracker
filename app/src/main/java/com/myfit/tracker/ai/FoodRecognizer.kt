package com.myfit.tracker.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * R18: the offline food recogniser — the same small model on every phone (~0.2 s per photo).
 *
 * A MobileCLIP image encoder (ONNX) turns the photo into an embedding; it's compared with one embedding per catalogue
 * dish (built from its name, aliases and visual description), so every one of the 1,066 dishes can be recognised
 * without per-dish training. Downloaded once in the background on Wi-Fi — nothing is added to the APK.
 */
object FoodRecognizer {
    private const val BASE = "https://github.com/Sohaib020/myfit-tracker/releases/download/models-v2"
    private val FILES = listOf("meta.json", "dish_ids.json", "dish_emb.bin", "foodclip_image.onnx")

    data class Guess(val id: String, val score: Float)

    private fun dir(c: Context) = File(c.filesDir, "foodclip")
    fun ready(c: Context) = FILES.all { File(dir(c), it).let { f -> f.exists() && f.length() > 0 } } && File(dir(c), "ok").exists()

    private val lock = Mutex()
    private var session: ai.onnxruntime.OrtSession? = null
    private var env: ai.onnxruntime.OrtEnvironment? = null
    private var ids: List<String> = emptyList()
    private var emb: FloatArray = FloatArray(0)
    private var dim = 0
    private var size = 256
    private var mean = floatArrayOf(0f, 0f, 0f)
    private var std = floatArrayOf(1f, 1f, 1f)
    private var temp = 100f

    private fun load(c: Context) {
        if (session != null) return
        val d = dir(c)
        val meta = JSONObject(File(d, "meta.json").readText())
        size = meta.optInt("size", 256)
        meta.optJSONArray("mean")?.let { a -> mean = FloatArray(3) { a.getDouble(it).toFloat() } }
        meta.optJSONArray("std")?.let { a -> std = FloatArray(3) { a.getDouble(it).toFloat() } }
        temp = meta.optDouble("temperature", 100.0).toFloat()
        ids = JSONArray(File(d, "dish_ids.json").readText()).let { a -> List(a.length()) { a.getString(it) } }
        val bb = ByteBuffer.wrap(File(d, "dish_emb.bin").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val n = bb.int; dim = bb.int
        emb = FloatArray(n * dim) { halfToFloat(bb.short) }
        val e = ai.onnxruntime.OrtEnvironment.getEnvironment()
        val opts = ai.onnxruntime.OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
            setOptimizationLevel(ai.onnxruntime.OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        env = e
        session = e.createSession(File(d, "foodclip_image.onnx").absolutePath, opts)
    }

    /** Top-[k] catalogue dishes for this photo, best first (scores are softmax probabilities). Null if not downloaded. */
    suspend fun recognize(c: Context, photo: Bitmap, k: Int = 5): List<Guess>? = withContext(Dispatchers.Default) {
        if (!ready(c)) return@withContext null
        lock.withLock {
            runCatching { load(c) }.onFailure { session = null; return@withContext null }
            val s = session ?: return@withContext null
            val px = pixels(photo)
            val input = ai.onnxruntime.OnnxTensor.createTensor(env, FloatBuffer.wrap(px), longArrayOf(1, 3, size.toLong(), size.toLong()))
            val v = input.use { t -> s.run(mapOf(s.inputNames.first() to t)).use { r -> (r[0].value as Array<FloatArray>)[0] } }
            val n = ids.size
            val logits = FloatArray(n) { i -> var dot = 0f; val o = i * dim; for (j in 0 until dim) dot += emb[o + j] * v[j]; dot * temp }
            val mx = logits.max()
            var sum = 0.0
            val ex = DoubleArray(n) { Math.exp((logits[it] - mx).toDouble()).also { e -> sum += e } }
            (0 until n).sortedByDescending { logits[it] }.take(k).map { Guess(ids[it], (ex[it] / sum).toFloat()) }
        }
    }

    /** Resize the short side to [size], centre-crop, normalise → CHW floats. */
    private fun pixels(b: Bitmap): FloatArray {
        val sc = size.toFloat() / minOf(b.width, b.height)
        val w = maxOf(size, Math.round(b.width * sc)); val h = maxOf(size, Math.round(b.height * sc))
        val scaled = Bitmap.createScaledBitmap(b, w, h, true)
        val crop = Bitmap.createBitmap(scaled, (w - size) / 2, (h - size) / 2, size, size)
        val arr = IntArray(size * size); crop.getPixels(arr, 0, size, 0, 0, size, size)
        if (crop !== scaled) crop.recycle()
        if (scaled !== b) scaled.recycle()
        val plane = size * size
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = arr[i]
            out[i] = (((p shr 16) and 255) / 255f - mean[0]) / std[0]
            out[plane + i] = (((p shr 8) and 255) / 255f - mean[1]) / std[1]
            out[2 * plane + i] = ((p and 255) / 255f - mean[2]) / std[2]
        }
        return out
    }

    private fun halfToFloat(h: Short): Float = android.util.Half.toFloat(h)

    /** Free the model's memory (e.g. on low memory). */
    suspend fun release() = lock.withLock { runCatching { session?.close() }; session = null }

    // ---------------------------------------------------------------- background download (Wi-Fi, once)

    fun schedule(c: Context) {
        if (ready(c)) return
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("foodclip", ExistingWorkPolicy.KEEP, req)
    }

    class DownloadWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val d = dir(applicationContext).apply { mkdirs() }
            File(d, "ok").delete()
            try {
                for (name in FILES) {
                    val f = File(d, name)
                    val tmp = File(d, "$name.part")
                    var url = URL("$BASE/$name")
                    var conn: HttpURLConnection
                    var hops = 0
                    while (true) {          // GitHub release assets redirect to a CDN
                        conn = (url.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 20_000; readTimeout = 60_000 }
                        val code = conn.responseCode
                        if (code in 300..399 && hops++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
                        if (code != 200) { conn.disconnect(); return@withContext if (code == 404) Result.failure() else Result.retry() }
                        break
                    }
                    conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it, 64 * 1024) } }
                    if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
                }
                // a quick sanity check before marking it usable
                val meta = JSONObject(File(d, "meta.json").readText())
                if (File(d, "foodclip_image.onnx").length() < 1_000_000 || meta.optInt("count") <= 0) return@withContext Result.retry()
                File(d, "ok").writeText(meta.optString("quant"))
                Result.success()
            } catch (e: Exception) { Result.retry() }
        }
    }
}
