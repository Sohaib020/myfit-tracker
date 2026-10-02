package com.myfit.tracker.ai.ondevice

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

/**
 * Daily free allowance for CLOUD AI calls (on-device answers and offline data answers are never counted).
 *
 * NOTE: these caps live on the phone, so a determined user can bypass them (clear data, change the date).
 * That's acceptable for now; the real fix is a small server proxy (e.g. a Cloudflare Worker) that holds the
 * API keys and enforces per-user limits.
 */
class AiQuota(private val prefs: AiPrefs) {

    enum class Kind(val defaultCap: Int, val adGrant: Int, val one: String, val many: String) {
        PHOTO(15, 5, "photo scan", "photo scans"),
        CHAT(40, 10, "online answer", "online answers"),
    }

    data class Allowance(val kind: Kind, val cap: Int, val used: Int, val bonus: Int, val unlimited: Boolean, val adsToday: Int) {
        val remaining: Int get() = if (unlimited) Int.MAX_VALUE else (cap + bonus - used).coerceAtLeast(0)
        val canWatchAd: Boolean get() = adsToday < MAX_ADS_PER_DAY
        fun label(): String = when {
            unlimited -> "Unlimited ${kind.many} (developer)"
            remaining == 0 -> "No free ${kind.many} left today"
            remaining == 1 -> "1 free ${kind.one} left today"
            else -> "$remaining free ${kind.many} left today"
        }
    }

    class CapReached(val kind: Kind, msg: String) : Exception(msg)

    fun allowance(kind: Kind): Flow<Allowance> = combine(prefs.config, prefs.usage) { c, u -> build(kind, c, u) }

    suspend fun current(kind: Kind): Allowance = build(kind, prefs.cfg(), prefs.use())

    private fun build(kind: Kind, c: AiConfig, u: AiUsage): Allowance = when (kind) {
        Kind.PHOTO -> Allowance(kind, if (c.photoCapOverride >= 0) c.photoCapOverride else kind.defaultCap, u.photo, u.bonusPhoto, c.unlimitedDev, u.adsToday)
        Kind.CHAT -> Allowance(kind, if (c.chatCapOverride >= 0) c.chatCapOverride else kind.defaultCap, u.chat, u.bonusChat, c.unlimitedDev, u.adsToday)
    }

    /** Throws [CapReached] (and asks the UI to show the "watch an ad / tomorrow" sheet) when nothing is left. */
    suspend fun require(kind: Kind) {
        val a = current(kind)
        if (a.remaining > 0) return
        AiCapPrompt.show(kind)
        throw CapReached(kind, when (kind) {
            Kind.PHOTO -> "You've used today's free photo scans. Watch a short ad for ${kind.adGrant} more, try again tomorrow, or search the food manually."
            Kind.CHAT -> "You've used today's free online answers. Watch a short ad for ${kind.adGrant} more or try again tomorrow — questions about your own logs still work offline."
        })
    }

    /** Count one successful cloud call. */
    suspend fun consume(kind: Kind) = when (kind) {
        Kind.PHOTO -> prefs.bump(photo = 1)
        Kind.CHAT -> prefs.bump(chat = 1)
    }

    /** Reward from a watched ad. */
    suspend fun grant(kind: Kind) = when (kind) {
        Kind.PHOTO -> prefs.bump(bonusPhoto = kind.adGrant, ads = 1)
        Kind.CHAT -> prefs.bump(bonusChat = kind.adGrant, ads = 1)
    }

    /** Live camera names (small, frequent calls): only while photo scans remain, and at most [LIVE_CAP]/day. */
    suspend fun liveAllowed(): Boolean {
        val u = prefs.use()
        val c = prefs.cfg()
        return c.unlimitedDev || (u.live < LIVE_CAP && current(Kind.PHOTO).remaining > 0)
    }

    suspend fun consumeLive() = prefs.bump(live = 1)

    companion object {
        const val MAX_ADS_PER_DAY = 6
        const val LIVE_CAP = 60
    }
}

/** A request for the UI to show the "free uses finished" sheet (rendered by [AiCapSheetHost]). */
object AiCapPrompt {
    private val _pending = MutableStateFlow<AiQuota.Kind?>(null)
    val pending: StateFlow<AiQuota.Kind?> = _pending
    fun show(kind: AiQuota.Kind) { _pending.value = kind }
    fun dismiss() { _pending.value = null }
}

/** One place that owns the on-device AI pieces (created on first use, never at app start). */
class OnDeviceAi private constructor(val app: Context) {
    val prefs = AiPrefs(app)
    val quota = AiQuota(prefs)
    val models = ModelStore(app, prefs)
    val llm = OnDeviceLlm(app, this)

    companion object {
        @Volatile private var inst: OnDeviceAi? = null
        fun get(ctx: Context): OnDeviceAi = inst ?: synchronized(this) { inst ?: OnDeviceAi(ctx.applicationContext).also { inst = it } }
    }
}
