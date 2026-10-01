package com.myfit.tracker.food

import com.myfit.tracker.data.db.Food
import com.myfit.tracker.data.db.NutritionSource
import com.myfit.tracker.domain.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Barcode lookup in the free Open Food Facts database. Values are per 100 g / 100 ml as published. */
object OpenFoodFacts {
    sealed interface Lookup {
        data class Found(val food: Food) : Lookup
        data object NotFound : Lookup
        data class NoNutrition(val name: String) : Lookup
        data class Error(val message: String) : Lookup
    }

    suspend fun lookup(code: String): Lookup = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,nutriments,serving_size,serving_quantity,quantity")
                .openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("User-Agent", "MyFitTracker/0.2 (Android; personal fitness log)")
            }
            if (c.responseCode == 404) return@runCatching Lookup.NotFound
            if (c.responseCode !in 200..299) return@runCatching Lookup.Error("Open Food Facts error ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (o.optInt("status") != 1) return@runCatching Lookup.NotFound
            val p = o.getJSONObject("product")
            val name = p.optString("product_name").ifBlank { "Product $code" }
            val n = p.optJSONObject("nutriments") ?: return@runCatching Lookup.NoNutrition(name)
            fun v(k: String) = n.optDouble("${k}_100g").takeIf { !it.isNaN() }
            val kcal = v("energy-kcal") ?: v("energy")?.let { it / 4.184 } ?: return@runCatching Lookup.NoNutrition(name)
            val now = Clock.now()
            Lookup.Found(
                Food(
                    name = name, brand = p.optString("brands").takeIf { it.isNotBlank() }?.substringBefore(','),
                    servingSize = 100.0, servingUnit = "g", servingGrams = 100.0,
                    calories = kcal, proteinG = v("proteins") ?: 0.0, carbsG = v("carbohydrates") ?: 0.0, fatG = v("fat") ?: 0.0,
                    fiberG = v("fiber"), source = NutritionSource.BARCODE, sourceRef = "Open Food Facts · $code", barcode = code,
                    createdAt = now, updatedAt = now,
                )
            )
        }.getOrElse { Lookup.Error("Couldn't reach Open Food Facts — check your connection.") }
    }
}
