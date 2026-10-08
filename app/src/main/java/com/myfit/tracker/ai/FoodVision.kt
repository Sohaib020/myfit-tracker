package com.myfit.tracker.ai

import android.graphics.Bitmap
import com.myfit.tracker.AppContainer
import com.myfit.tracker.ai.ondevice.AiQuota
import com.myfit.tracker.ai.ondevice.OnDeviceAi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
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
        /** Matched built-in food (3D icon + typical values), if any. */
        val catalogUuid: String? = null, val photo: String? = null,
    )
    data class Result(val items: List<Item>, val note: String?)

    class NotFood(msg: String) : Exception(msg)
    /** No internet and no working offline brain — message is ready to show. */
    class Offline(msg: String) : Exception(msg)

    /**
     * Fast, staged analysis.
     *  - Online: a quick pass (small photo, fastest model, names + grams only) shows results in about a second or two
     *    through [onEarly]; an accuracy pass (bigger photo, best model, full nutrition) runs at the same time and
     *    replaces it when it lands. Nutrition comes from MyFit's Pakistani food catalog wherever the dish is known, so
     *    numbers are consistent; the AI's own estimate is used only for unknown dishes.
     *  - Offline (or "phone brain" mode): the on-phone model gets a small photo and a short answer format, which is
     *    several times faster than asking it for full nutrition.
     */
    suspend fun analyze(photo: Bitmap, hint: String = "", onEarly: ((Result) -> Unit)? = null): Result = coroutineScope {
        val note = if (hint.isNotBlank()) "\nUser note about this meal: $hint" else ""
        val ai = OnDeviceAi.get(c.app)
        val online = Net.online(c.app)
        val mode = BrainMode.get(c.app)
        val cloud = online && mode != BrainMode.PHONE && c.aiRouter.chain(c.settings.settings.first()).isNotEmpty()
        if (!cloud) return@coroutineScope offline(photo, note, ai, online)

        onDevice = false
        ai.quota.require(AiQuota.Kind.PHOTO)
        val small = async(Dispatchers.Default) { compress(photo, 512f, 72) }
        val big = async(Dispatchers.Default) { compress(photo, 896f, 82) }
        val quick = async { runCatching { enrich(parse(c.aiRouter.vision(COMPACT + note, small.await(), fast = true, perProviderMs = 8_000), compact = true)) } }
        val full = async { runCatching { enrich(parse(c.aiRouter.vision(PROMPT + note, big.await(), fast = false, perProviderMs = 28_000))) } }
        var early: Result? = null
        quick.await().onSuccess { early = it; onEarly?.invoke(it) }
        // once a quick answer is on screen, give the accuracy pass a little longer — but never leave the user waiting long
        val best = withTimeoutOrNull(if (early != null) 14_000L else 32_000L) { full.await() }
        if (best == null) full.cancel()
        val out = best?.getOrNull() ?: early ?: run {
            val e = best?.exceptionOrNull() ?: quick.await().exceptionOrNull() ?: IllegalStateException("Couldn't read the photo")
            throw e
        }
        ai.quota.consume(AiQuota.Kind.PHOTO)
        out
    }

    private suspend fun offline(photo: Bitmap, note: String, ai: OnDeviceAi, online: Boolean): Result {
        var offlineError: String? = null
        if (ai.llm.available()) {
            try {
                val jpeg = withContext(Dispatchers.Default) { compress(photo, 448f, 80) }
                val r = enrich(parse(ai.llm.vision(COMPACT + note, jpeg), compact = true))
                onDevice = true
                return r
            } catch (e: NotFood) { throw e } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                offlineError = ai.llm.lastError ?: "it took too long"
            } catch (e: Exception) { offlineError = ai.llm.lastError ?: e.message ?: "couldn't read the photo" }
        }
        throw Offline(when {
            online && offlineError != null -> "The phone brain couldn't read this photo ($offlineError). Switch Pip's brain to Auto to use online AI, or search foods instead."
            offlineError != null -> "You're offline and the offline brain couldn't read this photo ($offlineError). Try again, or search foods instead."
            ai.models.installedFile() == null -> "You're offline. Download the offline brain once (Me → Pip → Pip settings → Downloads) to recognise meals without internet — or search foods instead."
            else -> "You're offline and the offline brain is switched off (Pip settings → Downloads). Turn it on, or search foods instead."
        })
    }

    /** Start loading the on-phone model while the camera is open, when it's the one that will answer. */
    suspend fun prewarmIfOffline() {
        val ai = OnDeviceAi.get(c.app)
        if (!Net.online(c.app) || BrainMode.get(c.app) == BrainMode.PHONE) ai.llm.prewarm()
    }

    /** Swap in catalog values (per gram) for dishes MyFit knows; keep the AI estimate for the rest. */
    private suspend fun enrich(r: Result): Result = r.copy(items = r.items.map { it0 ->
        val f = runCatching { c.nutritionRepo.matchDish(it0.name) }.getOrNull() ?: return@map it0
        val sg = f.servingGrams?.takeIf { it > 0 }
        val grams = if (it0.grams > 0) it0.grams else sg ?: 0.0
        val k = when {
            sg != null && grams > 0 -> grams / sg
            else -> 1.0
        }
        it0.copy(
            name = f.name, grams = if (grams > 0) grams else it0.grams,
            portion = it0.portion.ifBlank { "${Math.round(grams)} g" },
            kcal = f.calories * k, protein = f.proteinG * k, carbs = f.carbsG * k, fat = f.fatG * k, fiber = f.fiberG?.let { it * k },
            catalogUuid = f.uuid, photo = c.nutritionRepo.meta(f)?.photo,
        )
    })

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

    private fun parse(raw: String, compact: Boolean = false): Result {
        val txt = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val o = JSONObject(txt.substring(txt.indexOf('{').coerceAtLeast(0), txt.lastIndexOf('}') + 1))
        if (!o.optBoolean("is_food", true)) throw NotFood(o.optString("note").ifBlank { "I couldn't see any food in that photo." })
        val arr = o.optJSONArray("items") ?: throw NotFood("I couldn't recognise the food — try a closer, brighter photo.")
        val items = (0 until arr.length()).map { arr.getJSONObject(it) }.map { j ->
            Item(
                name = j.optString("name").ifBlank { "Food" }, portion = j.optString("portion"),
                grams = j.optDouble("grams", 0.0).coerceAtLeast(0.0),
                kcal = j.optDouble("calories", j.optDouble("kcal", 0.0)).coerceAtLeast(0.0),
                // compact answers carry only kcal: split it into a typical South Asian meal macro mix until matched
                protein = j.optDouble("protein_g", if (compact) j.optDouble("kcal", 0.0) * 0.15 / 4 else 0.0).coerceAtLeast(0.0),
                carbs = j.optDouble("carbs_g", if (compact) j.optDouble("kcal", 0.0) * 0.50 / 4 else 0.0).coerceAtLeast(0.0),
                fat = j.optDouble("fat_g", if (compact) j.optDouble("kcal", 0.0) * 0.35 / 9 else 0.0).coerceAtLeast(0.0),
                fiber = j.optDouble("fiber_g").takeIf { !it.isNaN() }, confidence = j.optString("confidence", "medium"),
            )
        }.filter { it.kcal > 0 || it.grams > 0 }
        if (items.isEmpty()) throw NotFood("I couldn't recognise the food — try a closer, brighter photo.")
        return Result(items, o.optString("note").takeIf { it.isNotBlank() })
    }

    companion object {
        /** Short answer format: much faster to generate (on the phone especially) — nutrition comes from the catalog. */
        private val COMPACT = """
Identify each food or drink clearly visible in this photo. Use common Pakistani / South Asian dish names (e.g. chicken karahi, daal mash, aloo paratha, chicken biryani, chapli kebab, nihari, chai, lassi).
Estimate the visible portion in grams (use plate, utensils and hands for scale) and total kcal for that portion.
Reply ONLY with JSON: {"is_food": true, "items": [{"name": "...", "grams": 250, "kcal": 400}]}
If there is no food: {"is_food": false, "items": []}
""".trim()
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
