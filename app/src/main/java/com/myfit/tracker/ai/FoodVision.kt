package com.myfit.tracker.ai

import android.graphics.Bitmap
import com.myfit.tracker.AppContainer
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
    private val gemini = Gemini(c.app)

    data class Item(
        val name: String, val portion: String, val grams: Double,
        val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val fiber: Double?,
        val confidence: String,
    )
    data class Result(val items: List<Item>, val note: String?)

    class NotFood(msg: String) : Exception(msg)

    suspend fun analyze(photo: Bitmap, hint: String = ""): Result {
        val s = c.settings.settings.first()
        val key = s.geminiKeyEff
        if (key.isBlank()) throw IllegalStateException("Food photos need Pip's online brain (Gemini). Add a key in Me → Pip.")
        val jpeg = withContext(Dispatchers.Default) { compress(photo) }
        val prompt = PROMPT + (if (hint.isNotBlank()) "\nUser note about this meal: $hint" else "")
        val models = buildList { if (s.geminiModel.isNotBlank()) add(s.geminiModel); addAll(gemini.rankedModels(key)) }.distinct().take(4)
        var last: Exception? = null
        for (m in models) {
            for (attempt in 0..1) {
                try {
                    return parse(gemini.generateVision(key, m, prompt, jpeg))
                } catch (e: Gemini.ApiError) {
                    last = e
                    if (e.code in listOf(500, 502, 503, 504) && attempt == 0) { delay(1500); continue }
                    if (e.code == 400 && e.message?.contains("API key", true) == true) throw IllegalStateException("The Gemini key isn't valid.")
                    break
                } catch (e: NotFood) { throw e }
                catch (e: Exception) { last = e; break }
            }
        }
        throw IllegalStateException("Couldn't analyse the photo right now (${last?.message ?: "no model available"}). Try again in a moment.")
    }

    private fun compress(b: Bitmap): ByteArray {
        val max = 1024f
        val sc = minOf(1f, max / maxOf(b.width, b.height))
        val img = if (sc < 1f) Bitmap.createScaledBitmap(b, (b.width * sc).toInt(), (b.height * sc).toInt(), true) else b
        return ByteArrayOutputStream().also { img.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
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
