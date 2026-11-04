package com.myfit.tracker.domain

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * R32: where you eat — picks the regional meal plans, the "Popular here" foods and search hints.
 * Defaults from the phone's country; changed from the Food tab.
 */
object FoodRegion {
    data class Region(val id: String, val name: String, val short: String, val flag: String, val hint: String)
    val ALL = listOf(
        Region("pk", "Pakistan", "Pakistan", "🇵🇰", "Search nihari, kadhi pakora, zinger, roti…"),
        Region("in", "India", "India", "🇮🇳", "Search dosa, dal makhani, paneer, biryani…"),
        Region("me", "Middle East & Gulf", "Gulf", "🇦🇪", "Search kabsa, hummus, shawarma, machboos…"),
        Region("uk", "United Kingdom", "UK", "🇬🇧", "Search jacket potato, roast dinner, porridge…"),
        Region("us", "USA & Canada", "USA", "🇺🇸", "Search burrito bowl, pancakes, turkey sandwich…"),
        Region("eu", "Mediterranean & Europe", "Europe", "🇪🇺", "Search greek salad, bolognese, souvlaki…"),
        Region("ea", "East Asia", "East Asia", "🇯🇵", "Search ramen, sushi, bibimbap, teriyaki…"),
        Region("sea", "Southeast Asia", "SE Asia", "🇹🇭", "Search pad thai, nasi lemak, pho, satay…"),
    )
    private val _id = MutableStateFlow<String?>(null)
    val flow: StateFlow<String?> = _id
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("food_region", Context.MODE_PRIVATE)

    fun get(c: Context): Region {
        val id = _id.value ?: sp(c).getString("id", null) ?: guess().also { sp(c).edit().putString("id", it).apply() }
        _id.value = id
        return ALL.firstOrNull { it.id == id } ?: ALL[0]
    }
    /** The region last read (for prompts built without a Context); Pakistan until first read. */
    fun current(): Region = ALL.firstOrNull { it.id == _id.value } ?: ALL[0]
    fun set(c: Context, id: String) { sp(c).edit().putString("id", id).apply(); _id.value = id }

    /** Phone country → region (Pakistan when unknown). */
    fun guess(country: String = java.util.Locale.getDefault().country.uppercase()): String = when (country) {
        "PK" -> "pk"; "IN", "BD", "LK", "NP" -> "in"
        "AE", "SA", "QA", "KW", "BH", "OM", "JO", "LB", "EG", "IQ", "SY", "PS", "YE", "MA", "TN", "DZ", "LY" -> "me"
        "GB", "IE" -> "uk"; "US", "CA" -> "us"
        "IT", "ES", "FR", "GR", "PT", "DE", "NL", "BE", "AT", "CH", "TR", "CY", "SE", "NO", "DK", "FI", "PL", "CZ", "HU", "RO", "HR" -> "eu"
        "CN", "JP", "KR", "TW", "HK", "MO", "MN" -> "ea"
        "TH", "VN", "MY", "ID", "PH", "SG", "KH", "LA", "MM", "BN" -> "sea"
        else -> "pk"
    }
}
