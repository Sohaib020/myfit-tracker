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
    PIP("Pip"), SNAP("Snap a meal"), VITALS("Vitals"), MIND("Mindfulness"), CYCLE("Cycle"), GLUCOSE("Blood sugar"), WORKOUT("Today's workout"), RINGS("Today's rings"), SOCIAL("Compete with friends"), NUTRITION("Food & calories"), BODY("Body weight"), HYDRATION("Hydration"), RECOVERY("Sleep & recovery"),
    STEPS("Steps & activity"), CHECKIN("Daily check-in"), GOALS("Today's goals")
}

/** Cards that start out half width (two per row). Users can resize any card by long-pressing it. */
val DefaultSmallCards: Set<DashCard> = setOf(
    DashCard.HYDRATION, DashCard.STEPS, DashCard.RECOVERY, DashCard.CHECKIN, DashCard.BODY,
    DashCard.GOALS, DashCard.VITALS, DashCard.MIND, DashCard.CYCLE, DashCard.GLUCOSE,
)

/** Values for [AppSettings.diabetesType]. */
val DiabetesTypes = listOf("unset", "none", "type1", "type2", "gestational", "prediabetes", "other")

data class AppSettings(
    val themeId: String = "kinetic",
    val gentleThemes: Boolean = true,
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
    val dashOrder: List<DashCard> = DashCard.entries.toList(),
    val groqKey: String = "",
    val openRouterKey: String = "",
    val mistralKey: String = "",
    val azureKey: String = "",
    val azureRegion: String = "",
    val aiPrimary: String = "auto",      // auto | gemini | groq | openrouter | mistral
    val liveAi: Boolean = false,         // camera: name foods live while aiming (uses AI quota)
    val permsAsked: Boolean = false,     // first-launch permission walk-through done
    val devMode: Boolean = false,        // developer options unlocked (tap version 7×)
    val cycleEnabled: Boolean = false,
    val cycleAsked: Boolean = false,
    val glucoseEnabled: Boolean = false,
    val dashSmall: Set<DashCard> = DefaultSmallCards,   // half-width dashboard cards (defaults ± user overrides)
    val diabetesType: String = "unset",  // unset | none | type1 | type2 | gestational | prediabetes | other
    val diabetesAsked: Boolean = false,  // the Home "do you manage diabetes?" card was answered or dismissed
) {
    /** The user's own key if they added one, otherwise the key built into this build (from CI secrets). */
    val geminiKeyEff: String get() = geminiKey.ifBlank { com.myfit.tracker.BuildConfig.GEMINI_KEY }
    val elevenKeyEff: String get() = elevenKey.ifBlank { com.myfit.tracker.BuildConfig.ELEVEN_KEY }
    val groqKeyEff: String get() = groqKey.ifBlank { com.myfit.tracker.BuildConfig.GROQ_KEY }
    val openRouterKeyEff: String get() = openRouterKey.ifBlank { com.myfit.tracker.BuildConfig.OPENROUTER_KEY }
    val mistralKeyEff: String get() = mistralKey.ifBlank { com.myfit.tracker.BuildConfig.MISTRAL_KEY }
    val azureKeyEff: String get() = azureKey.ifBlank { com.myfit.tracker.BuildConfig.AZURE_SPEECH_KEY }
    val azureRegionEff: String get() = azureRegion.ifBlank { com.myfit.tracker.BuildConfig.AZURE_SPEECH_REGION }
}

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val themeV2 = booleanPreferencesKey("theme_v2")
        val gentle = booleanPreferencesKey("gentle_themes")
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
        val order = stringPreferencesKey("dash_order")
        val groq = stringPreferencesKey("groq_key")
        val orKey = stringPreferencesKey("openrouter_key")
        val mistral = stringPreferencesKey("mistral_key")
        val azure = stringPreferencesKey("azure_key")
        val azureRegion = stringPreferencesKey("azure_region")
        val aiPrimary = stringPreferencesKey("ai_primary")
        val liveAi = booleanPreferencesKey("live_ai")
        val perms = booleanPreferencesKey("perms_asked")
        val dev = booleanPreferencesKey("dev_mode")
        val cycle = booleanPreferencesKey("cycle_on")
        val cycleAsked = booleanPreferencesKey("cycle_asked")
        val glucose = booleanPreferencesKey("glucose_on")
        val dashSize = stringSetPreferencesKey("dash_size")   // "CARD:S" / "CARD:L" overrides of DefaultSmallCards
        val diabetes = stringPreferencesKey("diabetes_type")
        val diabetesAsked = booleanPreferencesKey("diabetes_asked")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeId = p[K.theme]?.takeIf { p[K.themeV2] == true } ?: "kinetic",   // v2: new default for everyone once
            gentleThemes = p[K.gentle] ?: true,
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
            dashOrder = run {
                val saved = p[K.order]?.split(',')?.mapNotNull { n -> runCatching { DashCard.valueOf(n) }.getOrNull() }?.distinct() ?: emptyList()
                saved + DashCard.entries.filter { it !in saved }
            },
            groqKey = p[K.groq] ?: "",
            openRouterKey = p[K.orKey] ?: "",
            mistralKey = p[K.mistral] ?: "",
            azureKey = p[K.azure] ?: "",
            azureRegion = p[K.azureRegion] ?: "",
            aiPrimary = p[K.aiPrimary] ?: "auto",
            liveAi = p[K.liveAi] ?: false,
            permsAsked = p[K.perms] ?: false,
            devMode = p[K.dev] ?: false,
            cycleEnabled = p[K.cycle] ?: false,
            cycleAsked = p[K.cycleAsked] ?: false,
            glucoseEnabled = p[K.glucose] ?: false,
            dashSmall = run {
                val o = p[K.dashSize].orEmpty().mapNotNull { e ->
                    val c = runCatching { DashCard.valueOf(e.substringBefore(':')) }.getOrNull() ?: return@mapNotNull null
                    c to (e.substringAfter(':') == "S")
                }.toMap()
                DashCard.entries.filter { o[it] ?: (it in DefaultSmallCards) }.toSet()
            },
            diabetesType = p[K.diabetes]?.takeIf { it in DiabetesTypes } ?: "unset",
            diabetesAsked = p[K.diabetesAsked] ?: false,
        )
    }

    suspend fun setTheme(id: String) = context.dataStore.edit { it[K.theme] = id; it[K.themeV2] = true }
    suspend fun setGentleThemes(v: Boolean) = context.dataStore.edit { it[K.gentle] = v }
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
    suspend fun setDashOrder(o: List<DashCard>) = context.dataStore.edit { it[K.order] = o.joinToString(",") { c -> c.name } }
    suspend fun setAiKey(provider: String, v: String) = context.dataStore.edit {
        val k = when (provider) { "groq" -> K.groq; "openrouter" -> K.orKey; "mistral" -> K.mistral; "azure" -> K.azure; "azure_region" -> K.azureRegion; else -> return@edit }
        if (v.isBlank()) it.remove(k) else it[k] = v.trim()
    }
    suspend fun setAiPrimary(v: String) = context.dataStore.edit { it[K.aiPrimary] = v }
    suspend fun setLiveAi(v: Boolean) = context.dataStore.edit { it[K.liveAi] = v }
    suspend fun setPermsAsked(v: Boolean) = context.dataStore.edit { it[K.perms] = v }
    suspend fun setDevMode(v: Boolean) = context.dataStore.edit { it[K.dev] = v }
    suspend fun setCycle(enabled: Boolean) = context.dataStore.edit { it[K.cycle] = enabled; it[K.cycleAsked] = true }
    suspend fun setGlucose(enabled: Boolean) = context.dataStore.edit { it[K.glucose] = enabled }
    /** Half width (small = true) or full width for one dashboard card. */
    suspend fun setDashSize(card: DashCard, small: Boolean) = context.dataStore.edit {
        val cur = it[K.dashSize].orEmpty().filterNot { e -> e.substringBefore(':') == card.name }
        it[K.dashSize] = (cur + "${card.name}:${if (small) "S" else "L"}").toSet()
    }
    suspend fun setDiabetesType(v: String) = context.dataStore.edit {
        it[K.diabetes] = if (v in DiabetesTypes) v else "unset"; it[K.diabetesAsked] = true
    }
    suspend fun setDiabetesAsked(v: Boolean) = context.dataStore.edit { it[K.diabetesAsked] = v }
}
