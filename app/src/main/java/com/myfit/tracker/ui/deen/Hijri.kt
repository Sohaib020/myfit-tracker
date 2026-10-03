package com.myfit.tracker.ui.deen

import android.content.Context
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Hijri (Umm al-Qura) dates via ICU, with the user's ±2 day moon-sighting adjustment. */
object Hijri {
    data class Day(val year: Int, val month: Int, val day: Int) {
        val label: String get() = "$day ${MONTHS[(month - 1).coerceIn(0, 11)]} $year AH"
    }
    val MONTHS = listOf("Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")

    fun of(date: LocalDate, adjustDays: Int = 0): Day {
        val cal = IslamicCalendar(ULocale("en@calendar=islamic-umalqura"))
        cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        cal.time = Date.from(date.plusDays(adjustDays.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant())
        return Day(cal.get(IslamicCalendar.YEAR), cal.get(IslamicCalendar.MONTH) + 1, cal.get(IslamicCalendar.DAY_OF_MONTH))
    }

    fun today(c: Context): Day = of(LocalDate.now(), DeenPrefs.hijriAdjust(c))

    /** Recommended (Sunnah) fasts in the next [days] days. */
    fun upcomingSunnahFasts(c: Context, days: Int): List<Pair<LocalDate, String>> {
        val adj = DeenPrefs.hijriAdjust(c)
        val out = mutableListOf<Pair<LocalDate, String>>()
        val start = LocalDate.now()
        for (i in 0 until days) {
            val d = start.plusDays(i.toLong())
            val h = of(d, adj)
            val reasons = mutableListOf<String>()
            if (h.month == 9) continue                      // Ramadan is obligatory, listed separately
            if (h.month == 10 && h.day == 1) continue        // Eid al-Fitr: no fasting
            if (h.month == 12 && h.day in 10..13) continue   // Eid al-Adha & Tashreeq: no fasting
            if (h.month == 1 && h.day in 9..10) reasons += "Ashura (${h.day} Muharram)"
            if (h.month == 12 && h.day == 9) reasons += "Day of Arafah"
            if (h.month == 10 && h.day in 2..7) reasons += "Six days of Shawwal"
            if (h.day in 13..15) reasons += "Ayyam al-Beed (${h.day}th)"
            if (d.dayOfWeek == DayOfWeek.MONDAY) reasons += "Monday"
            if (d.dayOfWeek == DayOfWeek.THURSDAY) reasons += "Thursday"
            if (reasons.isNotEmpty()) out += d to reasons.joinToString(" · ")
        }
        return out
    }
}
