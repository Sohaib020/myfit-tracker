package com.myfit.tracker.ai

import android.content.Context
import org.json.JSONObject

/**
 * R20: dishes you corrected after a meal photo. When the recogniser or the AI names something and you change it
 * (renamed it, or tapped a different dish), the fix is remembered here — on this phone only — and used the next
 * time the same dish shows up, with your portion and nutrition. Values are per 100 g when grams are known.
 */
object PersonalDishes {
    private const val P = "personal_dishes"
    data class Dish(val name: String, val kcal100: Double, val p100: Double, val c100: Double, val f100: Double, val grams: Double)

    private fun key(s: String) = s.lowercase().replace(Regex("\\(.*?\\)"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun all(c: Context): JSONObject = runCatching { JSONObject(c.getSharedPreferences(P, Context.MODE_PRIVATE).getString("d", "{}")!!) }.getOrDefault(JSONObject())

    /** Remember that [from] (what was guessed) is really [d]; [d]'s own name is also stored so the AI naming it directly matches. */
    fun remember(c: Context, from: String, d: Dish) {
        val o = all(c)
        val j = JSONObject().put("n", d.name).put("k", d.kcal100).put("p", d.p100).put("c", d.c100).put("f", d.f100).put("g", d.grams)
        if (key(from).isNotEmpty()) o.put(key(from), j)
        o.put(key(d.name), j)
        // keep it small: the newest 300 corrections
        if (o.length() > 300) o.keys().asSequence().take(o.length() - 300).toList().forEach { o.remove(it) }
        c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString("d", o.toString()).apply()
    }

    fun lookup(c: Context, name: String): Dish? {
        val j = all(c).optJSONObject(key(name)) ?: return null
        return Dish(j.optString("n"), j.optDouble("k"), j.optDouble("p"), j.optDouble("c"), j.optDouble("f"), j.optDouble("g", 0.0))
    }

    fun count(c: Context) = all(c).length()
}
