package com.myfit.tracker.ai.ondevice

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiStore: DataStore<Preferences> by preferencesDataStore(name = "ai_prefs")

/** AI-only settings and daily usage counters. Kept apart from the main settings file on purpose. */
data class AiConfig(
    /** User toggle: use the downloaded offline brain when it's installed. */
    val useOnDevice: Boolean = true,
    /** Download over mobile data too (default: Wi-Fi only). */
    val allowMobileData: Boolean = false,
    // ---- developer options
    val modelPreset: String = ModelCatalog.DEFAULT.id,
    val modelUrlOverride: String = "",
    val backendPref: String = "auto",          // auto | gpu | cpu
    val photoCapOverride: Int = -1,            // -1 = default
    val chatCapOverride: Int = -1,
    val unlimitedDev: Boolean = false,
    val forceCloud: Boolean = false,           // skip on-device even when installed (testing)
    val workingBackend: String = "",           // remembered backend that loaded fine last time
)

data class AiUsage(val day: String, val photo: Int, val chat: Int, val live: Int, val bonusPhoto: Int, val bonusChat: Int, val adsToday: Int)

class AiPrefs(private val ctx: Context) {
    private object K {
        val useOnDevice = booleanPreferencesKey("use_on_device")
        val mobile = booleanPreferencesKey("allow_mobile")
        val preset = stringPreferencesKey("model_preset")
        val url = stringPreferencesKey("model_url")
        val backend = stringPreferencesKey("backend_pref")
        val capPhoto = intPreferencesKey("cap_photo")
        val capChat = intPreferencesKey("cap_chat")
        val unlimited = booleanPreferencesKey("unlimited_dev")
        val forceCloud = booleanPreferencesKey("force_cloud")
        val working = stringPreferencesKey("working_backend")
        val day = stringPreferencesKey("q_day")
        val uPhoto = intPreferencesKey("q_photo")
        val uChat = intPreferencesKey("q_chat")
        val uLive = intPreferencesKey("q_live")
        val bPhoto = intPreferencesKey("q_bonus_photo")
        val bChat = intPreferencesKey("q_bonus_chat")
        val ads = intPreferencesKey("q_ads")
        val autoDl = booleanPreferencesKey("auto_dl_done")
    }

    val config: Flow<AiConfig> = ctx.aiStore.data.map { p ->
        AiConfig(
            useOnDevice = p[K.useOnDevice] ?: true,
            allowMobileData = p[K.mobile] ?: false,
            modelPreset = p[K.preset] ?: ModelCatalog.DEFAULT.id,
            modelUrlOverride = p[K.url] ?: "",
            backendPref = p[K.backend] ?: "auto",
            photoCapOverride = p[K.capPhoto] ?: -1,
            chatCapOverride = p[K.capChat] ?: -1,
            unlimitedDev = p[K.unlimited] ?: false,
            forceCloud = p[K.forceCloud] ?: false,
            workingBackend = p[K.working] ?: "",
        )
    }

    val usage: Flow<AiUsage> = ctx.aiStore.data.map { p ->
        val today = today()
        if (p[K.day] != today) AiUsage(today, 0, 0, 0, 0, 0, 0)
        else AiUsage(today, p[K.uPhoto] ?: 0, p[K.uChat] ?: 0, p[K.uLive] ?: 0, p[K.bPhoto] ?: 0, p[K.bChat] ?: 0, p[K.ads] ?: 0)
    }

    suspend fun cfg() = config.first()
    suspend fun use() = usage.first()

    suspend fun setUseOnDevice(v: Boolean) = ctx.aiStore.edit { it[K.useOnDevice] = v }
    suspend fun setAllowMobile(v: Boolean) = ctx.aiStore.edit { it[K.mobile] = v }
    suspend fun setPreset(v: String) = ctx.aiStore.edit { it[K.preset] = v }
    suspend fun setUrlOverride(v: String) = ctx.aiStore.edit { it[K.url] = v.trim() }
    suspend fun setBackendPref(v: String) = ctx.aiStore.edit { it[K.backend] = v; it[K.working] = "" }
    suspend fun setCaps(photo: Int, chat: Int) = ctx.aiStore.edit { it[K.capPhoto] = photo; it[K.capChat] = chat }
    suspend fun setUnlimited(v: Boolean) = ctx.aiStore.edit { it[K.unlimited] = v }
    suspend fun setForceCloud(v: Boolean) = ctx.aiStore.edit { it[K.forceCloud] = v }
    suspend fun setWorkingBackend(v: String) = ctx.aiStore.edit { it[K.working] = v }

    /** Adds to today's counters, starting a fresh day when the date changed. */
    suspend fun bump(photo: Int = 0, chat: Int = 0, live: Int = 0, bonusPhoto: Int = 0, bonusChat: Int = 0, ads: Int = 0) {
        ctx.aiStore.edit { p ->
            val today = today()
            if (p[K.day] != today) {
                p[K.day] = today
                listOf(K.uPhoto, K.uChat, K.uLive, K.bPhoto, K.bChat, K.ads).forEach { p[it] = 0 }
            }
            p[K.uPhoto] = (p[K.uPhoto] ?: 0) + photo
            p[K.uChat] = (p[K.uChat] ?: 0) + chat
            p[K.uLive] = (p[K.uLive] ?: 0) + live
            p[K.bPhoto] = (p[K.bPhoto] ?: 0) + bonusPhoto
            p[K.bChat] = (p[K.bChat] ?: 0) + bonusChat
            p[K.ads] = (p[K.ads] ?: 0) + ads
        }
    }

    /** True the first time it's called, so the offline brain auto-download is queued only once per install. */
    suspend fun takeAutoDownload(): Boolean {
        var first = false
        ctx.aiStore.edit { p -> if (p[K.autoDl] != true) { first = true; p[K.autoDl] = true } }
        return first
    }

    suspend fun resetToday() = ctx.aiStore.edit { p -> listOf(K.uPhoto, K.uChat, K.uLive, K.bPhoto, K.bChat, K.ads).forEach { p[it] = 0 } }

    companion object {
        fun today(): String = java.time.LocalDate.now().toString()
    }
}
