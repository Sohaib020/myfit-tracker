package com.myfit.tracker.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    WORKOUT("Today's workout"), BODY("Body weight"), HYDRATION("Hydration"), RECOVERY("Sleep & recovery"),
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
    val restAutoStart: Boolean = true,
    val restDefaultSec: Int = 90,
    val restSound: Boolean = true,
    val restVibrate: Boolean = true,
    val weightStepKg: Double = 2.5,
    val keepScreenOn: Boolean = true,
    val lastHealthSync: Long? = null,
    val lastHealthSyncMsg: String = "",
    val geminiKey: String = "",
    val geminiModel: String = "",
    val onlineAi: Boolean = true,
    val blurAmount: Float = 1f,          // 0 (crystal clear) … 2 (heavy frost)
    val dockBlur: Float = 1.6f,          // dock has its own blur; heavy by default
    val motion: Int = 1,                 // 0 smooth (60 fps), 1 balanced (30 fps), 2 battery saver (still)
    val elevenKey: String = "",
    val voiceEngine: Int = 0,            // 0 auto (ElevenLabs → on-device → phone), 1 on-device only, 2 phone voice
    val refraction: Float = 1f,          // 0 (flat) … 2 (strong lens)
    val pipVoice: Boolean = true,
    val pipVoiceOnline: Boolean = true,  // realistic Gemini voice when online; offline voice otherwise
) {
    /** The user's own key if they added one, otherwise the key built into this build (from CI secrets). */
    val geminiKeyEff: String get() = geminiKey.ifBlank { com.myfit.tracker.BuildConfig.GEMINI_KEY }
    val elevenKeyEff: String get() = elevenKey.ifBlank { com.myfit.tracker.BuildConfig.ELEVEN_KEY }
}

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
        val hidden = stringSetPreferencesKey("dash_hidden")   // stores HIDDEN cards so new cards default to visible
        val restAuto = booleanPreferencesKey("rest_auto")
        val restSec = intPreferencesKey("rest_sec")
        val restSound = booleanPreferencesKey("rest_sound")
        val restVib = booleanPreferencesKey("rest_vibrate")
        val wStep = doublePreferencesKey("weight_step_kg")
        val screenOn = booleanPreferencesKey("keep_screen_on")
        val hcSync = longPreferencesKey("hc_last_sync")
        val hcMsg = stringPreferencesKey("hc_last_msg")
        val gKey = stringPreferencesKey("gemini_key")       // stored only in this app's private storage
        val gModel = stringPreferencesKey("gemini_model")
        val online = booleanPreferencesKey("online_ai")
        val blurAmt = floatPreferencesKey("blur_amount")
        val dockBlur = floatPreferencesKey("dock_blur")
        val motion = androidx.datastore.preferences.core.intPreferencesKey("motion")
        val elevenKey = stringPreferencesKey("eleven_key")
        val voiceEngine = androidx.datastore.preferences.core.intPreferencesKey("voice_engine")
        val refr = floatPreferencesKey("refraction")
        val voice = booleanPreferencesKey("pip_voice")
        val voiceOnline = booleanPreferencesKey("pip_voice_online")
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
            dashCards = DashCard.entries.toSet() - (p[K.hidden]?.mapNotNull { runCatching { DashCard.valueOf(it) }.getOrNull() }?.toSet() ?: emptySet()),
            haptics = p[K.haptics] ?: true,
            pipEnabled = p[K.pip] ?: true,
            restAutoStart = p[K.restAuto] ?: true,
            restDefaultSec = p[K.restSec] ?: 90,
            restSound = p[K.restSound] ?: true,
            restVibrate = p[K.restVib] ?: true,
            weightStepKg = p[K.wStep] ?: 2.5,
            keepScreenOn = p[K.screenOn] ?: true,
            lastHealthSync = p[K.hcSync],
            lastHealthSyncMsg = p[K.hcMsg] ?: "",
            geminiKey = p[K.gKey] ?: "",
            geminiModel = p[K.gModel] ?: "",
            onlineAi = p[K.online] ?: true,
            blurAmount = p[K.blurAmt] ?: 1f,
            dockBlur = p[K.dockBlur] ?: 1.6f,
            motion = p[K.motion] ?: 1,
            elevenKey = p[K.elevenKey] ?: "",
            voiceEngine = p[K.voiceEngine] ?: 0,
            refraction = p[K.refr] ?: 1f,
            pipVoice = p[K.voice] ?: true,
            pipVoiceOnline = p[K.voiceOnline] ?: true,
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
    suspend fun setDashCards(c: Set<DashCard>) = context.dataStore.edit { it[K.hidden] = (DashCard.entries.toSet() - c).map { x -> x.name }.toSet() }
    suspend fun setRestAuto(v: Boolean) = context.dataStore.edit { it[K.restAuto] = v }
    suspend fun setRestSec(v: Int) = context.dataStore.edit { it[K.restSec] = v }
    suspend fun setRestSound(v: Boolean) = context.dataStore.edit { it[K.restSound] = v }
    suspend fun setRestVibrate(v: Boolean) = context.dataStore.edit { it[K.restVib] = v }
    suspend fun setWeightStep(v: Double) = context.dataStore.edit { it[K.wStep] = v }
    suspend fun setKeepScreenOn(v: Boolean) = context.dataStore.edit { it[K.screenOn] = v }
    suspend fun setLastHealthSync(at: Long, msg: String) = context.dataStore.edit { it[K.hcSync] = at; it[K.hcMsg] = msg }
    suspend fun setGeminiKey(v: String) = context.dataStore.edit { if (v.isBlank()) it.remove(K.gKey) else it[K.gKey] = v.trim() }
    suspend fun setGeminiModel(v: String) = context.dataStore.edit { it[K.gModel] = v }
    suspend fun setOnlineAi(v: Boolean) = context.dataStore.edit { it[K.online] = v }
    suspend fun setBlurAmount(v: Float) = context.dataStore.edit { it[K.blurAmt] = v }
    suspend fun setDockBlur(v: Float) = context.dataStore.edit { it[K.dockBlur] = v }
    suspend fun setMotion(v: Int) = context.dataStore.edit { it[K.motion] = v; it[K.animated] = v != 2 }
    suspend fun setElevenKey(v: String) = context.dataStore.edit { it[K.elevenKey] = v.trim() }
    suspend fun setVoiceEngine(v: Int) = context.dataStore.edit { it[K.voiceEngine] = v }
    suspend fun setRefraction(v: Float) = context.dataStore.edit { it[K.refr] = v }
    suspend fun setPipVoice(v: Boolean) = context.dataStore.edit { it[K.voice] = v }
    suspend fun setPipVoiceOnline(v: Boolean) = context.dataStore.edit { it[K.voiceOnline] = v }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setPip(v: Boolean) = context.dataStore.edit { it[K.pip] = v }
}
