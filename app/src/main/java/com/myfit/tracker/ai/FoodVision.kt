package com.myfit.tracker.ai

import android.graphics.Bitmap
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.ondevice.AiQuota
import com.myfit.tracker.ai.ondevice.OnDeviceAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Food photo → list of items with estimated portion and nutrition (Gemini vision).
 * Every number here is an ESTIMATE; the UI labels it so and the user adjusts before saving.
 */
class FoodVision(private val c: AppContainer) {

    data class Item(
        val name: String, val portion: String, val grams: Double,
        val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double?,
        val confidence: String,
    )
    data class Result(val items: List<Item>, val note: String?)

    class NotFood(msg: String) : Exception(msg)
    /** No internet and no working offline brain — message is ready to show. */
    class Offline(msg: String) : Exception(msg)

    suspend fun analyze(photo: Bitmap, hint: String = ""): Result {
        val jpeg = withContext(Dispatchers.Default) { compress(photo, 1024f, 82) }
        val prompt = PROMPT + (if (hint.isNotBlank()) "\nUser note about this meal: $hint" else "")
        val ai = OnDeviceAi.get(c.app)
        // 1) offline brain on the phone: free and unlimited
        val online = Net.online(c.app)
        var offlineError: String? = null
        if (ai.llm.available()) {
            try {
                val r = parse(ai.llm.vision(prompt, jpeg))
                onDevice = true
                return r
            } catch (e: NotFood) { throw e } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                offlineError = ai.llm.lastError ?: "it took too long"
            } catch (e: Exception) { offlineError = ai.llm.lastError ?: e.message ?: "couldn't read the photo" }
        }
        if (!online) throw Offline(when {
            offlineError != null -> "You're offline and the offline brain couldn't read this photo ($offlineError). Try again, or search foods instead."
            ai.models.installedFile() == null -> "You're offline. Download the offline brain once (Settings → Pip → Offline brain, about 2 GB) to recognise meals without internet — or search foods instead."
            else -> "You're offline and the offline brain is switched off (Settings → Pip). Turn it on, or search foods instead."
        })
        // 2) cloud, within today's free allowance
        onDevice = false
        ai.quota.require(AiQuota.Kind.PHOTO)
        val r = parse(c.aiRouter.vision(prompt, jpeg))
        ai.quota.consume(AiQuota.Kind.PHOTO)
        return r
    }

    /** True when the last [analyze] was answered by the offline brain. */
    var onDevice: Boolean = false
        private set

    /** Quick live guess while aiming the camera: just dish names (small image, fastest provider). */
    suspend fun quickNames(frame: Bitmap): List<String> {
        val ai = OnDeviceAi.get(c.app)
        if (!ai.quota.liveAllowed()) return emptyList()   // live guesses are a cloud extra; never block the snap
        val jpeg = withContext(Dispatchers.Default) { compress(frame, 512f, 70) }
        val raw = c.aiRouter.vision(QUICK, jpeg, fast = true, perProviderMs = 9_000)
        ai.quota.consumeLive()
        val txt = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val o = JSONObject(txt.substring(txt.indexOf('{').coerceAtLeast(0), txt.lastIndexOf('}') + 1))
        val arr = o.optJSONArray("foods") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.take(4)
    }

    val lastProvider: String? get() = if (onDevice) "Offline brain (on this phone)" else c.aiRouter.lastProvider

    private fun compress(b: Bitmap, max: Float, q: Int): ByteArray {
        val sc = minOf(1f, max / maxOf(b.width, b.height))
        val img = if (sc < 1f) Bitmap.createScaledBitmap(b, (b.width * sc).toInt(), (b.height * sc).toInt(), true) else b
        return ByteArrayOutputStream().also { img.compress(Bitmap.CompressFormat.JPEG, q, it) }.toByteArray()
    }

    private fun parse(raw: String): Result {
        val txt = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val o = JSONObject(txt.substring(txt.indexOf('{').coerceAtLeast(0), txt.lastIndexOf('}') + 1))
        if (!o.optBoolean("is_food", true)) throw NotFood(o.optString("note").ifBlank { "I couldn't see any food in that photo." })
        val arr = o.optJSONArray("items") ?: throw NotFood("I couldn't recognise the food — try a closer, brighter photo.")
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }.map { j ->
            Item(
                name = j.optString("name").ifBlank { "Food" }, portion = j.optString("portion"),
                grams = j.optDouble("grams", 0.0).coerceAtLeast(0.0),
                kcal = j.optDouble("calories", 0.0).coerceAtLeast(0.0), protein = j.optDouble("protein_g", 0.0).coerceAtLeast(0.0),
                carbs = j.optDouble("carbs_g", 0.0).coerceAtLeast(0.0), fat = j.optDouble("fat_g", 0.0).coerceAtLeast(0.0),
                fiber = j.optDouble("fiber_g").takeIf { !it.isNaN() }, confidence = j.optString("confidence", "medium"),
            )
        }.filter { it.kcal > 0 || it.grams > 0 }
        if (items.isEmpty()) throw NotFood("I couldn't recognise the food — try a closer, brighter photo.")
        return Result(items, o.optString("note").takeIf { it.isNotBlank() })
    }

    companion object {
        private const val QUICK = "Name the foods or dishes clearly visible in this photo (Pakistani / South Asian dishes by their usual names). Reply ONLY with JSON: {\"foods\": [\"name\", ...]} — at most 4, empty list if there is no food."
        private val PROMPT = """
You are a careful nutrition estimator inside a fitness app used mostly in Pakistan.
Look at the photo and identify every distinct food or drink item that is clearly visible.
Know South Asian / Pakistani dishes well (biryani, pulao, karahi, qorma, nihari, haleem, daal, sabzi, roti, naan, paratha, chai, lassi, kebabs, samosa, halwa puri...).
For each item estimate the portion visible (use plate size, utensils and hands as scale) and the nutrition FOR THAT PORTION, using typical home/restaurant recipes (include cooking oil/ghee).
Be realistic, not optimistic. If unsure, give your best estimate and set confidence "low".
Reply ONLY with JSON in exactly this shape:
{"is_food": true, "items": [{"name": "Chicken biryani", "portion": "1 plate (~350 g)", "grams": 350, "calories": 600, "protein_g": 28, "carbs_g": 70, "fat_g": 22, "fiber_g": 3, "confidence": "high|medium|low"}], "note": "one short tip or caveat, optional"}
If there is no food in the photo, reply {"is_food": false, "items": [], "note": "what you see instead"}.
""".trim()
    }
}
