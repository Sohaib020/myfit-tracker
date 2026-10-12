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
import com.myfit.tracker.domain.Fmt
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
    data class Result(
        val items: List<Item>, val note: String?,
        /** R18: other likely dishes from the offline recogniser ("Not right? Try…"). */
        val alternatives: List<Item> = emptyList(),
        /** True for the instant on-phone recogniser result (before any AI refinement). */
        val instant: Boolean = false,
        /** The recogniser's confidence in its top dish (0..1), for instant results. */
        val score: Float = 0f,
    )

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
        // R18: the offline recogniser answers in ~0.2 s on any phone — shown first, then refined
        val instant = recognized(photo)
        if (instant != null) onEarly?.invoke(instant)
        if (!cloud) return@coroutineScope offline(photo, note, ai, online, instant, onEarly)

        onDevice = false
        ai.quota.require(AiQuota.Kind.PHOTO)
        val small = async(Dispatchers.Default) { compress(photo, 512f, 72) }
        val big = async(Dispatchers.Default) { compress(photo, 896f, 82) }
        val quick = async { runCatching { enrich(parse(c.aiRouter.vision(COMPACT + note, small.await(), fast = true, perProviderMs = 8_000), compact = true)) } }
        val full = async { runCatching { enrich(parse(c.aiRouter.vision(PROMPT + note, big.await(), fast = false, perProviderMs = 28_000))) } }
        var early: Result? = null
        quick.await().onSuccess { early = it.copy(alternatives = instant?.let { r -> r.items + r.alternatives }.orEmpty().filter { a -> it.items.none { i -> i.catalogUuid == a.catalogUuid } }.take(4)); onEarly?.invoke(early!!) }
        // R20: the phone was sure and the quick AI pass agrees → done, no need to wait for the slow pass
        val agree = instant != null && instant.score >= 0.55f && early?.items?.firstOrNull()?.catalogUuid == instant.items.first().catalogUuid
        // once a quick answer is on screen, give the accuracy pass a little longer — but never leave the user waiting long
        val best = if (agree) null else withTimeoutOrNull(if (early != null) 12_000L else 30_000L) { full.await() }
        if (best == null) full.cancel()
        val out = best?.getOrNull()?.let { r -> r.copy(alternatives = early?.alternatives.orEmpty()) } ?: early ?: instant ?: run {
            val e = best?.exceptionOrNull() ?: quick.await().exceptionOrNull() ?: IllegalStateException("Couldn't read the photo")
            throw e
        }
        ai.quota.consume(AiQuota.Kind.PHOTO)
        out
    }

    private suspend fun offline(photo: Bitmap, note: String, ai: OnDeviceAi, online: Boolean, instant: Result? = null, onEarly: ((Result) -> Unit)? = null): Result {
        var offlineError: String? = null
        if (ai.llm.available()) {
            try {
                // R18: full detail for the on-phone model too (the vision encoder resizes itself; we no longer shrink the photo)
                val jpeg = withContext(Dispatchers.Default) { compress(photo, 896f, 85) }
                // the recogniser's best guesses help the model name Pakistani dishes the same way the catalogue does
                val hints = instant?.let { r -> (r.items + r.alternatives).joinToString { it.name } }?.let { "\nLikely dishes (from a food classifier, may be wrong): $it" } ?: ""
                val r = enrich(parse(ai.llm.vision(COMPACT + hints + note, jpeg), compact = true))
                onDevice = true
                return r.copy(alternatives = instant?.let { i -> (i.items + i.alternatives).filter { a -> r.items.none { it.catalogUuid == a.catalogUuid } } }.orEmpty().take(4))
            } catch (e: NotFood) { throw e } catch (e: kotlinx.coroutines.CancellationException) {
                if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                offlineError = ai.llm.lastError ?: "it took too long"
            } catch (e: Exception) { offlineError = ai.llm.lastError ?: e.message ?: "couldn't read the photo" }
        }
        // no (working) offline brain: the recogniser's answer is the result — still accurate catalogue nutrition
        if (instant != null) { onDevice = true; return instant }
        throw Offline(when {
            online && offlineError != null -> "The phone brain couldn't read this photo ($offlineError). Switch Pip's brain to Auto to use online AI, or search foods instead."
            offlineError != null -> "You're offline and the offline brain couldn't read this photo ($offlineError). Try again, or search foods instead."
            ai.models.installedFile() == null -> "You're offline. Download the offline brain once (Me → Pip → Pip settings → Downloads) to recognise meals without internet — or search foods instead."
            else -> "You're offline and the offline brain is switched off (Pip settings → Downloads). Turn it on, or search foods instead."
        })
    }

    /** The offline recogniser's answer as a [Result]: top dish + alternatives, catalogue nutrition per serving. Null if unavailable/unsure. */
    private suspend fun recognized(photo: Bitmap): Result? {
        val g = runCatching { FoodRecognizer.recognize(c.app, photo, 6) }.getOrNull() ?: return null
        if (g.isEmpty() || g[0].score < 0.08f) return null
        val foods = c.db.nutritionDao().byUuids(g.map { "pkfood:${it.id}" }).first().associateBy { it.uuid }
        val items = g.mapNotNull { gu ->
            val f = foods["pkfood:${gu.id}"] ?: return@mapNotNull null
            val grams = f.servingGrams?.takeIf { it > 0 } ?: if (f.servingUnit == "g" || f.servingUnit == "ml") f.servingSize else 0.0
            Item(
                name = f.name, portion = "${Fmt.trim(f.servingSize, 1)} ${f.servingUnit}" + (if (grams > 0 && f.servingUnit != "g") " (~${grams.toInt()} g)" else ""),
                grams = grams, kcal = f.calories, protein = f.proteinG, carbs = f.carbsG, fat = f.fatG, fiber = f.fiberG,
                confidence = when { gu.score >= 0.5f -> "high"; gu.score >= 0.25f -> "medium"; else -> "low" },
                catalogUuid = f.uuid, photo = c.nutritionRepo.meta(f)?.photo,
            )
        }
        if (items.isEmpty()) return null
        // a dish you corrected before wins over the recogniser's own label
        val top = personal(items[0]) ?: items[0]
        val sure = g[0].score >= 0.25f
        return Result(listOf(top), if (sure) "Recognised on your phone — tap another dish below if it's not right."
            else "Not sure — this may be a dish outside my list. Tap the right one below, or rename it.", items.drop(1).take(4), instant = true, score = g[0].score)
    }

    /** Start loading the on-phone model while the camera is open, when it's the one that will answer. */
    suspend fun prewarmIfOffline() {
        val ai = OnDeviceAi.get(c.app)
        if (!Net.online(c.app) || BrainMode.get(c.app) == BrainMode.PHONE) ai.llm.prewarm()
    }

    /** Swap in catalog values (per gram) for dishes MyFit knows; keep the AI estimate for the rest. */
    private suspend fun enrich(r: Result): Result = r.copy(items = r.items.map { it0 ->
        personal(it0)?.let { return@map it }
        // R20: strict match — dishes outside the catalogue keep the AI's own name and estimate instead of a wrong lookalike
        val f = runCatching { c.nutritionRepo.matchDish(it0.name, strict = true) }.getOrNull() ?: return@map it0
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

    /** Your remembered correction for this dish name, scaled to the item's grams (or your usual portion). */
    private fun personal(it0: Item): Item? {
        val d = PersonalDishes.lookup(c.app, it0.name) ?: return null
        val g = if (it0.grams > 0) it0.grams else d.grams
        if (g <= 0) return null
        val k = g / 100.0
        return it0.copy(name = d.name, grams = g, portion = "${Math.round(g)} g", kcal = d.kcal100 * k, protein = d.p100 * k, carbs = d.c100 * k, fat = d.f100 * k,
            confidence = "high", catalogUuid = null)
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
Identify each food or drink clearly visible in this photo — from ANY cuisine worldwide. Name it specifically (e.g. pad thai, margherita pizza, sushi roll, shawarma, caesar salad). For Pakistani / South Asian dishes use the usual local name (chicken karahi, daal mash, aloo paratha, chicken biryani, chapli kebab, nihari, chai, lassi).
Estimate the visible portion in grams (use plate, utensils and hands for scale) and total kcal for that portion.
Reply ONLY with JSON: {"is_food": true, "items": [{"name": "...", "grams": 250, "kcal": 400, "protein_g": 20, "carbs_g": 45, "fat_g": 15}]}
If there is no food: {"is_food": false, "items": []}
""".trim()
        private const val QUICK = "Name the foods or dishes clearly visible in this photo, from any cuisine (Pakistani / South Asian dishes by their usual local names). Reply ONLY with JSON: {\"foods\": [\"name\", ...]} — at most 4, empty list if there is no food."
        private val PROMPT = """
You are a careful nutrition estimator inside a fitness app used mostly in Pakistan.
Look at the photo and identify every distinct food or drink item that is clearly visible.
Know South Asian / Pakistani dishes well (biryani, pulao, karahi, qorma, nihari, haleem, daal, sabzi, roti, naan, paratha, chai, lassi, kebabs, samosa, halwa puri...), but recognise food from every cuisine — Chinese, Thai, Japanese, Arab, Turkish, Italian, Mexican, American fast food, desserts, packaged snacks, fruit — and name it specifically.
For each item estimate the portion visible (use plate size, utensils and hands as scale) and the nutrition FOR THAT PORTION, using typical home/restaurant recipes (include cooking oil/ghee).
Be realistic, not optimistic. If unsure, give your best estimate and set confidence "low".
Reply ONLY with JSON in exactly this shape:
{"is_food": true, "items": [{"name": "Chicken biryani", "portion": "1 plate (~350 g)", "grams": 350, "calories": 600, "protein_g": 28, "carbs_g": 70, "fat_g": 22, "fiber_g": 3, "confidence": "high|medium|low"}], "note": "one short tip or caveat, optional"}
If there is no food in the photo, reply {"is_food": false, "items": [], "note": "what you see instead"}.
""".trim()
    }
}
