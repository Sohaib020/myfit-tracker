package com.myfit.tracker.ui.cycle

import android.content.Context
import android.content.SharedPreferences

/**
 * Cycle reminder settings, kept in SharedPreferences "cycle_prefs" so the notification system can
 * read them without touching the database. Stays on this phone.
 */
object CyclePrefs {
    private const val NAME = "cycle_prefs"
    private const val K_PERIOD = "remind_period"
    private const val K_PILL_ON = "pill_on"
    private const val K_PILL_TIME = "pill_time"
    private const val K_NEXT = "next_period_start"
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
}
