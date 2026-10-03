package com.myfit.tracker.ui.deen

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

/** Calculation methods (Fajr / Isha twilight angles). Umm al-Qura uses a fixed 90 min Isha. */
enum class CalcMethod(val label: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0) {
    KARACHI("Univ. of Islamic Sciences, Karachi", 18.0, 18.0),
    MWL("Muslim World League", 18.0, 17.0),
    ISNA("ISNA (North America)", 15.0, 15.0),
    EGYPT("Egyptian General Authority", 19.5, 17.5),
    MAKKAH("Umm al-Qura, Makkah", 18.5, 0.0, 90),
}

enum class Prayer(val label: String, val urdu: String) {
    FAJR("Fajr", "فجر"), SUNRISE("Sunrise", "طلوع"), DHUHR("Dhuhr", "ظہر"), ASR("Asr", "عصر"), MAGHRIB("Maghrib", "مغرب"), ISHA("Isha", "عشاء");
    val isSalah get() = this != SUNRISE
}

data class Place(val name: String, val lat: Double, val lng: Double)

/** Common cities for people who don't share location. */
val Cities = listOf(
    Place("Lahore", 31.5204, 74.3587), Place("Karachi", 24.8607, 67.0011), Place("Islamabad", 33.6844, 73.0479),
    Place("Rawalpindi", 33.5651, 73.0169), Place("Faisalabad", 31.4504, 73.1350), Place("Multan", 30.1575, 71.5249),
    Place("Peshawar", 34.0151, 71.5249), Place("Quetta", 30.1798, 66.9750), Place("Sialkot", 32.4945, 74.5229),
    Place("Gujranwala", 32.1877, 74.1945), Place("Hyderabad", 25.3960, 68.3578), Place("Makkah", 21.4225, 39.8262),
    Place("Madinah", 24.4672, 39.6112), Place("Dubai", 25.2048, 55.2708), Place("Riyadh", 24.7136, 46.6753),
    Place("London", 51.5072, -0.1276), Place("Manchester", 53.4808, -2.2426), Place("Toronto", 43.6532, -79.3832),
    Place("New York", 40.7128, -74.0060),
)

/**
 * Prayer times from sun position (the standard method used by most prayer apps):
 * Fajr/Isha by twilight angle, Asr by shadow ratio (1 Shafi'i, 2 Hanafi), Maghrib at sunset.
 * Accurate to about a minute; mosques may add their own safety minutes.
 */
object PrayerCalc {
    private fun rad(d: Double) = d * PI / 180
    private fun deg(r: Double) = r * 180 / PI
    private fun fix(a: Double, b: Double) = (a - b * floor(a / b))

    /** Returns minutes after local midnight for each prayer, in [Prayer] order. */
    fun times(date: LocalDate, lat: Double, lng: Double, zone: ZoneId, method: CalcMethod, hanafi: Boolean): Map<Prayer, Int> {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(date) - lng / (15 * 24)
        fun sun(jdt: Double): Pair<Double, Double> {   // (declination, equation of time hours)
            val d = jdt - 2451545.0
            val g = fix(357.529 + 0.98560028 * d, 360.0)
            val q = fix(280.459 + 0.98564736 * d, 360.0)
            val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
            val e = 23.439 - 0.00000036 * d
            val ra = fix(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15, 24.0)
            val eqt = q / 15 - ra
            val decl = deg(asin(sin(rad(e)) * sin(rad(l))))
            return decl to eqt
        }
        fun midDay(t: Double): Double { val eqt = sun(jd + t).second; return fix(12 - eqt, 24.0) }
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t).first
            val noon = midDay(t)
            val cosv = (-sin(rad(angle)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            val v = deg(acos(cosv.coerceIn(-1.0, 1.0))) / 15
            return noon + if (ccw) -v else v
        }
        fun asrTime(factor: Double, t: Double): Double {
            val decl = sun(jd + t).first
            val angle = -deg(atan(1 / (factor + tan(rad(abs(lat - decl))))))
            return angleTime(angle, t, false)
        }
        // two passes for accuracy (start from approximate times)
        var f = 5.0 / 24; var sr = 6.0 / 24; var dh = 12.0 / 24; var ar = 13.0 / 24; var ss = 18.0 / 24; var ish = 18.0 / 24
        repeat(2) {
            val fj = angleTime(method.fajr, f, true)
            val r = angleTime(0.833, sr, true)
            val d = midDay(dh)
            val a = asrTime(if (hanafi) 2.0 else 1.0, ar)
            val s = angleTime(0.833, ss, false)
            val i = if (method.ishaMinutes > 0) s + method.ishaMinutes / 60.0 else angleTime(method.isha, ish, false)
            f = fj / 24; sr = r / 24; dh = d / 24; ar = a / 24; ss = s / 24; ish = i / 24
        }
        fun toMin(hUtcLocal: Double, offsetMin: Int = 0): Int {
            val h = hUtcLocal + tz - lng / 15
            return (fix(h, 24.0) * 60).roundToInt().plus(offsetMin).mod(1440)
        }
        return linkedMapOf(
            Prayer.FAJR to toMin(f * 24), Prayer.SUNRISE to toMin(sr * 24), Prayer.DHUHR to toMin(dh * 24, 1),
            Prayer.ASR to toMin(ar * 24), Prayer.MAGHRIB to toMin(ss * 24, 1), Prayer.ISHA to toMin(ish * 24),
        )
    }

    private fun julian(d: LocalDate): Double {
        var y = d.year; var m = d.monthValue; val day = d.dayOfMonth
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0); val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Qibla bearing (degrees from true north) from a location to the Kaaba. */
    fun qibla(lat: Double, lng: Double): Double {
        val kLat = rad(21.4225); val kLng = rad(39.8262)
        val p = rad(lat); val dl = kLng - rad(lng)
        val b = deg(atan2(sin(dl), cos(p) * tan(kLat) - sin(p) * cos(dl)))
        return fix(b, 360.0)
    }

    /** Great-circle distance in km. */
    fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = rad(lat2 - lat1); val dLng = rad(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(rad(lat1)) * cos(rad(lat2)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}

/** Shariah & Health settings (SharedPreferences "deen_prefs"). */
object DeenPrefs {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("deen_prefs", Context.MODE_PRIVATE)
    fun place(c: Context): Place? = p(c).let { s -> if (!s.contains("lat")) null else Place(s.getString("pname", "My location") ?: "My location", s.getFloat("lat", 0f).toDouble(), s.getFloat("lng", 0f).toDouble()) }
    fun setPlace(c: Context, pl: Place) = p(c).edit().putString("pname", pl.name).putFloat("lat", pl.lat.toFloat()).putFloat("lng", pl.lng.toFloat()).apply()
    fun method(c: Context): CalcMethod = runCatching { CalcMethod.valueOf(p(c).getString("method", "KARACHI")!!) }.getOrDefault(CalcMethod.KARACHI)
    fun setMethod(c: Context, m: CalcMethod) = p(c).edit().putString("method", m.name).apply()
    fun hanafi(c: Context) = p(c).getBoolean("hanafi", true)
    fun setHanafi(c: Context, v: Boolean) = p(c).edit().putBoolean("hanafi", v).apply()
    fun adhan(c: Context, pr: Prayer) = p(c).getBoolean("adhan_" + pr.name, pr.isSalah)
    fun setAdhan(c: Context, pr: Prayer, v: Boolean) = p(c).edit().putBoolean("adhan_" + pr.name, v).apply()
    fun adhanOn(c: Context) = p(c).getBoolean("adhan_on", false)
    fun setAdhanOn(c: Context, v: Boolean) = p(c).edit().putBoolean("adhan_on", v).apply()
    fun suhoorAlert(c: Context) = p(c).getBoolean("suhoor_alert", false)
    fun setSuhoorAlert(c: Context, v: Boolean) = p(c).edit().putBoolean("suhoor_alert", v).apply()
    fun hijriAdjust(c: Context) = p(c).getInt("hijri_adj", 0)
    fun setHijriAdjust(c: Context, v: Int) = p(c).edit().putInt("hijri_adj", v.coerceIn(-2, 2)).apply()

    // prayer log: set of "yyyy-MM-dd:PRAYER"
    fun prayed(c: Context, date: LocalDate, pr: Prayer) = p(c).getStringSet("prayed", emptySet())!!.contains("$date:${pr.name}")
    fun setPrayed(c: Context, date: LocalDate, pr: Prayer, v: Boolean) {
        val cur = p(c).getStringSet("prayed", emptySet())!!.toMutableSet()
        if (v) cur += "$date:${pr.name}" else cur -= "$date:${pr.name}"
        val keep = date.minusDays(120).toString()
        p(c).edit().putStringSet("prayed", cur.filter { it.substringBefore(':') >= keep }.toSet()).apply()
    }

    // fasting: qada owed + fasts kept
    fun qadaOwed(c: Context) = p(c).getInt("qada_owed", 0)
    fun setQadaOwed(c: Context, v: Int) = p(c).edit().putInt("qada_owed", v.coerceAtLeast(0)).apply()
    fun fasted(c: Context): Set<String> = p(c).getStringSet("fasted", emptySet())!!
    fun setFasted(c: Context, date: LocalDate, v: Boolean) {
        val cur = fasted(c).toMutableSet(); if (v) cur += date.toString() else cur -= date.toString()
        p(c).edit().putStringSet("fasted", cur).apply()
    }

    // dhikr totals per day: key "dhikr:yyyy-MM-dd:phrase"
    fun dhikr(c: Context, date: LocalDate, phrase: String) = p(c).getInt("dhikr:$date:$phrase", 0)
    fun addDhikr(c: Context, date: LocalDate, phrase: String, n: Int) = p(c).edit().putInt("dhikr:$date:$phrase", dhikr(c, date, phrase) + n).apply()

    // simple daily checklists (adhkar, sunnah habits): key "chk:yyyy-MM-dd:id"
    fun checked(c: Context, date: LocalDate, id: String) = p(c).getBoolean("chk:$date:$id", false)
    fun setChecked(c: Context, date: LocalDate, id: String, v: Boolean) = p(c).edit().putBoolean("chk:$date:$id", v).apply()
}

/** Today's (or any day's) prayer times for the saved place; null until a place is set. */
fun prayerTimes(c: Context, date: LocalDate = LocalDate.now()): Map<Prayer, Int>? {
    val pl = DeenPrefs.place(c) ?: return null
    return PrayerCalc.times(date, pl.lat, pl.lng, ZoneId.systemDefault(), DeenPrefs.method(c), DeenPrefs.hanafi(c))
}

/** Upcoming adhan / suhoor alarms for the reminder scheduler: (requestCode, epochMs, title, text). */
object DeenAlarms {
    fun upcoming(c: Context, after: Long): List<DeenAlarm> {
        val out = mutableListOf<DeenAlarm>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val on = DeenPrefs.adhanOn(c); val suhoor = DeenPrefs.suhoorAlert(c)
        if (!on && !suhoor) return out
        for (d in 0..1) {
            val date = today.plusDays(d.toLong())
            val t = prayerTimes(c, date) ?: return out
            if (on) for ((pr, min) in t) {
                if (!pr.isSalah || !DeenPrefs.adhan(c, pr)) continue
                val at = date.atStartOfDay(zone).plusMinutes(min.toLong()).toInstant().toEpochMilli()
                if (at > after) out += DeenAlarm(920_000 + d * 10 + pr.ordinal, at, "${pr.label} time", "It's time for ${pr.label} (${pr.urdu}).")
            }
            if (suhoor) {
                val fajr = t[Prayer.FAJR] ?: continue
                val at = date.atStartOfDay(zone).plusMinutes((fajr - 45).toLong()).toInstant().toEpochMilli()
                if (at > after) out += DeenAlarm(920_100 + d, at, "Suhoor ends in 45 minutes", "Fajr is at ${clock(fajr)}. Drink water and eat something light.")
            }
        }
        return out
    }
}

data class DeenAlarm(val rc: Int, val at: Long, val title: String, val text: String)

fun clock(min: Int): String { val h = min / 60; val m = min % 60; val h12 = if (h % 12 == 0) 12 else h % 12; return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM") }
