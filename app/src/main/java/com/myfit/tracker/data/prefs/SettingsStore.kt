package com.myfit.tracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myfit.tracker.domain.DistanceUnit
import com.myfit.tracker.domain.LengthUnit
import com.myfit.tracker.domain.UnitPrefs
import com.myfit.tracker.domain.VolumeUnit
import com.myfit.tracker.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class DashCard(val label: String) {
    BODY("Body weight"), HYDRATION("Hydration"), RECOVERY("Sleep & recovery"),
    STEPS("Steps & activity"), CHECKIN("Daily check-in"), GOALS("Today's goals")
}

data class AppSettings(
    val themeId: String = "crimson",
    val customBackground: String? = null,     // file name in app storage, null = theme art
    val animatedBackground: Boolean = true,
    val glassStrength: Float = 1f,            // 0.4 … 1.4
    val units: UnitPrefs = UnitPrefs(),
    val dashCards: Set<DashCard> = DashCard.entries.toSet(),
    val haptics: Boolean = true,
    val pipEnabled: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val bg = stringPreferencesKey("custom_bg")
        val animated = booleanPreferencesKey("animated_bg")
        val glass = floatPreferencesKey("glass_strength")
        val wu = stringPreferencesKey("unit_weight")
        val lu = stringPreferencesKey("unit_length")
        val vu = stringPreferencesKey("unit_volume")
        val du = stringPreferencesKey("unit_distance")
        val cards = stringSetPreferencesKey("dash_cards")
        val haptics = booleanPreferencesKey("haptics")
        val pip = booleanPreferencesKey("pip")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeId = p[K.theme] ?: "crimson",
            customBackground = p[K.bg],
            animatedBackground = p[K.animated] ?: true,
            glassStrength = p[K.glass] ?: 1f,
            units = UnitPrefs(
                weight = p[K.wu]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG,
                length = p[K.lu]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() } ?: LengthUnit.IN,
                volume = p[K.vu]?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() } ?: VolumeUnit.L,
                distance = p[K.du]?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() } ?: DistanceUnit.KM,
            ),
            dashCards = p[K.cards]?.mapNotNull { runCatching { DashCard.valueOf(it) }.getOrNull() }?.toSet()
                ?: DashCard.entries.toSet(),
            haptics = p[K.haptics] ?: true,
            pipEnabled = p[K.pip] ?: true,
        )
    }

    suspend fun setTheme(id: String) = context.dataStore.edit { it[K.theme] = id }
    suspend fun setCustomBackground(file: String?) = context.dataStore.edit {
        if (file == null) it.remove(K.bg) else it[K.bg] = file
    }
    suspend fun setAnimated(v: Boolean) = context.dataStore.edit { it[K.animated] = v }
    suspend fun setGlassStrength(v: Float) = context.dataStore.edit { it[K.glass] = v }
    suspend fun setUnits(u: UnitPrefs) = context.dataStore.edit {
        it[K.wu] = u.weight.name; it[K.lu] = u.length.name; it[K.vu] = u.volume.name; it[K.du] = u.distance.name
    }
    suspend fun setDashCards(c: Set<DashCard>) = context.dataStore.edit { it[K.cards] = c.map { x -> x.name }.toSet() }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setPip(v: Boolean) = context.dataStore.edit { it[K.pip] = v }
}
