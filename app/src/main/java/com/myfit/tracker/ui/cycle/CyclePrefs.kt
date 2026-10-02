package com.myfit.tracker.ui.cycle

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** What the cycle screen focuses on. Stored as a string so new modes can be added later. */
object CycleMode {
    const val TRACK = "track"
    const val TTC = "ttc"                  // trying to conceive
    const val PREGNANCY = "pregnancy"
    const val PERI = "peri"                // perimenopause
    // Planned for a later version (not shipped): a haiz / istihada / tuhr mode with madhhab-based,
    // user-editable rules, reviewed by a scholar. See [HaizHook].
    val all = listOf(TRACK, TTC, PREGNANCY, PERI)
    fun label(m: String): String = when (m) {
        TTC -> "Trying to conceive"
        PREGNANCY -> "Pregnancy"
        PERI -> "Perimenopause"
        else -> "Cycle tracking"
    }
    fun short(m: String): String = when (m) {
        TTC -> "Conceive"
        PREGNANCY -> "Pregnancy"
        PERI -> "Perimenopause"
        else -> "Track"
    }
}

/**
 * Clean extension point for a future haiz / istihada mode. Nothing reads [ENABLED] yet; when the mode is built it
 * should add a CycleMode value, its own rules object (min/max haiz days per madhhab, editable), and a
 * prayer/fast-status card — without changing the stored CycleDay rows (flow days are already enough input).
 */
object HaizHook {
    const val ENABLED = false
    const val PREF_KEY = "haiz_settings"   // reserved for its JSON settings
}

data class Appointment(val at: Long, val label: String)

/**
 * Cycle settings, kept in SharedPreferences "cycle_prefs" so the notification system can read them without
 * touching the database. Stays on this phone — never uploaded or shown to anyone else.
 */
object CyclePrefs {
    private const val NAME = "cycle_prefs"
    private const val K_PERIOD = "remind_period"
    private const val K_PILL_ON = "pill_on"
    private const val K_PILL_TIME = "pill_time"
    private const val K_NEXT = "next_period_start"
    private const val K_MODE = "mode"
    private const val K_DUE = "preg_due"            // ISO date
    private const val K_APPTS = "preg_appts"        // JSON [{at,label}]
    private const val K_APPT_REMIND = "preg_appt_remind"
    private const val K_LOCK = "lock_on"
    private const val K_PIN_HASH = "pin_hash"
    private const val K_PIN_SALT = "pin_salt"
    private const val K_LOG_NUDGE = "log_nudge"
    const val DEFAULT_PILL_TIME = 21 * 60

    private fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** "Period reminder (2 days before)". */
    fun periodReminder(ctx: Context): Boolean = runCatching { prefs(ctx).getBoolean(K_PERIOD, false) }.getOrDefault(false)
    fun pillOn(ctx: Context): Boolean = runCatching { prefs(ctx).getBoolean(K_PILL_ON, false) }.getOrDefault(false)
    /** Minutes after midnight, local time. */
    fun pillTime(ctx: Context): Int = runCatching { prefs(ctx).getInt(K_PILL_TIME, DEFAULT_PILL_TIME) }.getOrDefault(DEFAULT_PILL_TIME)
    /** Predicted next period start as ISO yyyy-MM-dd, or null when there is no prediction yet. */
    fun nextPeriodStart(ctx: Context): String? = runCatching { prefs(ctx).getString(K_NEXT, null) }.getOrNull()

    fun setPeriodReminder(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(K_PERIOD, on).apply() }
    fun setPillOn(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(K_PILL_ON, on).apply() }
    fun setPillTime(ctx: Context, minutes: Int) { prefs(ctx).edit().putInt(K_PILL_TIME, minutes.coerceIn(0, 24 * 60 - 1)).apply() }
    fun setNextPeriodStart(ctx: Context, isoDate: String?) {
        if (nextPeriodStart(ctx) == isoDate) return
        prefs(ctx).edit().apply { if (isoDate == null) remove(K_NEXT) else putString(K_NEXT, isoDate) }.apply()
    }

    // ---- mode
    fun mode(ctx: Context): String = runCatching { prefs(ctx).getString(K_MODE, CycleMode.TRACK) }.getOrNull()
        ?.takeIf { it in CycleMode.all } ?: CycleMode.TRACK
    fun setMode(ctx: Context, m: String) { prefs(ctx).edit().putString(K_MODE, m).apply() }

    // ---- pregnancy
    fun dueDate(ctx: Context): String? = runCatching { prefs(ctx).getString(K_DUE, null) }.getOrNull()
    fun setDueDate(ctx: Context, iso: String?) {
        prefs(ctx).edit().apply { if (iso == null) remove(K_DUE) else putString(K_DUE, iso) }.apply()
    }
    fun apptRemind(ctx: Context): Boolean = runCatching { prefs(ctx).getBoolean(K_APPT_REMIND, true) }.getOrDefault(true)
    fun setApptRemind(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(K_APPT_REMIND, on).apply() }
    fun appointments(ctx: Context): List<Appointment> = runCatching {
        val a = JSONArray(prefs(ctx).getString(K_APPTS, "[]") ?: "[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Appointment(it.getLong("at"), it.optString("label")) } }.sortedBy { it.at }
    }.getOrDefault(emptyList())
    fun setAppointments(ctx: Context, list: List<Appointment>) {
        val a = JSONArray()
        list.sortedBy { it.at }.take(60).forEach { a.put(JSONObject().put("at", it.at).put("label", it.label.take(80))) }
        prefs(ctx).edit().putString(K_APPTS, a.toString()).apply()
    }

    // ---- "log today" nudge (stored for the reminder screen; off by default)
    fun logNudge(ctx: Context): Boolean = runCatching { prefs(ctx).getBoolean(K_LOG_NUDGE, false) }.getOrDefault(false)

    // ---- app lock (PIN stored only as a salted SHA-256 hash)
    fun lockOn(ctx: Context): Boolean = runCatching {
        prefs(ctx).getBoolean(K_LOCK, false) && prefs(ctx).getString(K_PIN_HASH, null) != null
    }.getOrDefault(false)

    fun setPin(ctx: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs(ctx).edit().putString(K_PIN_SALT, salt).putString(K_PIN_HASH, hash(salt, pin)).putBoolean(K_LOCK, true).apply()
    }

    fun clearPin(ctx: Context) { prefs(ctx).edit().remove(K_PIN_SALT).remove(K_PIN_HASH).putBoolean(K_LOCK, false).apply() }

    fun checkPin(ctx: Context, pin: String): Boolean = runCatching {
        val p = prefs(ctx)
        val salt = p.getString(K_PIN_SALT, null) ?: return false
        val h = p.getString(K_PIN_HASH, null) ?: return false
        MessageDigest.isEqual(h.toByteArray(), hash(salt, pin).toByteArray())
    }.getOrDefault(false)

    private fun hash(salt: String, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var b = (salt + ":" + pin).toByteArray()
        repeat(2_000) { b = md.digest(b) }   // slows down guessing a 4–6 digit PIN from a copied prefs file
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Removes everything the cycle feature stored in prefs (used by "Delete all cycle data"). */
    fun clearAll(ctx: Context) { prefs(ctx).edit().clear().apply() }
}

/** Unlocked state lives only in memory: it resets when the app process ends or after 5 minutes away. */
object CycleLockSession {
    @Volatile private var unlockedAt = 0L
    fun isUnlocked(): Boolean = System.currentTimeMillis() - unlockedAt < 5 * 60_000L
    fun unlock() { unlockedAt = System.currentTimeMillis() }
    fun touch() { if (isUnlocked()) unlockedAt = System.currentTimeMillis() }
    fun lock() { unlockedAt = 0L }
}
