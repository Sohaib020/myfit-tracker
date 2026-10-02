package com.myfit.tracker.health

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.myfit.tracker.AppContainer
import com.myfit.tracker.domain.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.reflect.KClass

/**
 * Read-only access to vitals in Health Connect (heart rate, HRV, SpO2, breathing, temperature, VO2 max,
 * blood pressure) plus a per-app breakdown of who wrote data, for the Devices screen.
 * Every read checks the permission first and never throws: a failed or forbidden read returns an empty result.
 */
class VitalsReader(private val c: AppContainer) {

    /** One reading: value, when, and which app wrote it. */
    data class Reading(val value: Double, val at: Instant, val pkg: String)

    /** One local day of readings. */
    data class DayStat(val date: LocalDate, val avg: Double, val min: Double, val max: Double, val n: Int)

    /** Latest reading + daily stats (oldest day first). `permitted = false` → read access not granted. */
    data class Series(
        val latest: Reading?,
        val days: List<DayStat>,
        val permitted: Boolean,
        val raw: List<Reading> = emptyList(),
    ) {
        val hasData: Boolean get() = latest != null || days.isNotEmpty()
        companion object { val NONE = Series(null, emptyList(), false) }
    }

    data class BpReading(val id: String, val systolic: Int, val diastolic: Int, val at: Instant, val pkg: String)

    data class HeartRate(
        val latest: Reading?,
        val today: DayStat?,
        val daily: List<DayStat>,
        val permitted: Boolean,
    )

    data class All(
        val available: Boolean,
        val anyPermitted: Boolean,
        val heartRate: HeartRate,
        val resting: Series,
        val hrv: Series,
        val spo2: Series,
        val spo2Night: Double?,
        val breathing: Series,
        val temperature: Series,
        val vo2: Series,
        val bloodPressure: List<BpReading>?,     // null = not permitted / unavailable
    )

    private val hs get() = c.healthSync

    private fun window(days: Int): Pair<Instant, Instant> {
        val zone = Clock.zone()
        val today = Clock.today()
        val start = today.minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant()
        return start to end
    }

    /** Same pagination pattern as HealthSync.readAll (that one is private). */
    private suspend fun <T : Record> readAll(type: KClass<T>, start: Instant, end: Instant, cap: Int = 20_000): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val resp = hs.hc.readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(start, end), pageToken = token))
            out += resp.records
            token = resp.pageToken
        } while (token != null && out.size < cap)
        return out
    }

    private fun daysOf(readings: List<Reading>): List<DayStat> {
        val zone = Clock.zone()
        return readings.groupBy { it.at.atZone(zone).toLocalDate() }.toSortedMap().map { (d, list) ->
            val v = list.map { it.value }
            DayStat(d, v.average(), v.min(), v.max(), v.size)
        }
    }

    /** Generic instantaneous-record series. */
    private suspend fun <T : Record> series(
        type: KClass<T>, days: Int, granted: Set<String>,
        time: (T) -> Instant, value: (T) -> Double,
    ): Series {
        if (!hs.isAvailable || hs.readPerm(type) !in granted) return Series.NONE
        val (start, end) = window(days)
        return runCatching {
            val readings = readAll(type, start, end).map { Reading(value(it), time(it), it.metadata.dataOrigin.packageName) }
                .filter { !it.value.isNaN() }
                .sortedBy { it.at }
            Series(readings.lastOrNull(), daysOf(readings), true, readings)
        }.getOrDefault(Series(null, emptyList(), true))
    }

    suspend fun resting(days: Int = 30, g: Set<String>? = null): Series {
        val granted = g ?: hs.granted()
        val s = series(RestingHeartRateRecord::class, days, granted, { it.time }, { it.beatsPerMinute.toDouble() })
        if (s.days.isNotEmpty()) return s
        // fall back to the daily table filled by the background sync
        val fromDb = dbDays(days) { it.restingHr?.toDouble() }
        return if (fromDb.isEmpty()) s else s.copy(days = fromDb)
    }

    suspend fun hrv(days: Int = 30, g: Set<String>? = null): Series {
        val granted = g ?: hs.granted()
        val s = series(HeartRateVariabilityRmssdRecord::class, days, granted, { it.time }, { it.heartRateVariabilityMillis })
        if (s.days.isNotEmpty()) return s
        val fromDb = dbDays(days) { it.hrvMs }
        return if (fromDb.isEmpty()) s else s.copy(days = fromDb)
    }

    suspend fun spo2(days: Int = 30, g: Set<String>? = null): Series {
        val granted = g ?: hs.granted()
        val s = series(OxygenSaturationRecord::class, days, granted, { it.time }, { it.percentage.value })
        if (s.days.isNotEmpty()) return s
        val fromDb = dbDays(days) { it.spo2Pct }
        return if (fromDb.isEmpty()) s else s.copy(days = fromDb)
    }

    suspend fun breathing(days: Int = 30, g: Set<String>? = null): Series =
        series(RespiratoryRateRecord::class, days, g ?: hs.granted(), { it.time }, { it.rate })

    suspend fun temperature(days: Int = 30, g: Set<String>? = null): Series =
        series(BodyTemperatureRecord::class, days, g ?: hs.granted(), { it.time }, { it.temperature.inCelsius })

    suspend fun vo2(days: Int = 90, g: Set<String>? = null): Series =
        series(Vo2MaxRecord::class, days, g ?: hs.granted(), { it.time }, { it.vo2MillilitersPerMinuteKilogram })

    /** Blood pressure readings from other apps (newest first). Null when not permitted. */
    suspend fun bloodPressure(days: Int = 30, g: Set<String>? = null): List<BpReading>? {
        val granted = g ?: hs.granted()
        if (!hs.isAvailable || hs.readPerm(BloodPressureRecord::class) !in granted) return null
        val (start, end) = window(days)
        val own = c.app.packageName
        return runCatching {
            readAll(BloodPressureRecord::class, start, end)
                .filter { it.metadata.dataOrigin.packageName != own }
                .map {
                    BpReading(
                        it.metadata.id,
                        it.systolic.inMillimetersOfMercury.toInt(),
                        it.diastolic.inMillimetersOfMercury.toInt(),
                        it.time, it.metadata.dataOrigin.packageName,
                    )
                }
                .sortedByDescending { it.at }
        }.getOrDefault(emptyList())
    }

    /** Latest sample + today's range from raw samples (last 2 days), 30-day daily stats from Health Connect's aggregation. */
    suspend fun heartRate(days: Int = 30, g: Set<String>? = null): HeartRate {
        val granted = g ?: hs.granted()
        if (!hs.isAvailable || hs.readPerm(HeartRateRecord::class) !in granted) {
            val fromDb = dbHrDays(days)
            return HeartRate(null, null, fromDb, false)
        }
        val zone = Clock.zone()
        val today = Clock.today()
        val samples = runCatching {
            val start = today.minusDays(1).atStartOfDay(zone).toInstant()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant()
            readAll(HeartRateRecord::class, start, end, cap = 5_000).flatMap { r ->
                val pkg = r.metadata.dataOrigin.packageName
                r.samples.map { Reading(it.beatsPerMinute.toDouble(), it.time, pkg) }
            }.sortedBy { it.at }
        }.getOrDefault(emptyList())
        val todaySamples = samples.filter { it.at.atZone(zone).toLocalDate() == today }
        val todayStat = if (todaySamples.isEmpty()) null else todaySamples.map { it.value }.let {
            DayStat(today, it.average(), it.min(), it.max(), it.size)
        }
        val daily = runCatching {
            val res = hs.hc.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
                    timeRangeFilter = TimeRangeFilter.between(today.minusDays(days.toLong() - 1).atStartOfDay(), today.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
            res.mapNotNull { grp ->
                val avg = grp.result[HeartRateRecord.BPM_AVG] ?: return@mapNotNull null
                val mn = grp.result[HeartRateRecord.BPM_MIN] ?: avg
                val mx = grp.result[HeartRateRecord.BPM_MAX] ?: avg
                DayStat(grp.startTime.toLocalDate(), avg.toDouble(), mn.toDouble(), mx.toDouble(), 0)
            }.sortedBy { it.date }
        }.getOrDefault(emptyList()).ifEmpty { dbHrDays(days) }
        return HeartRate(samples.lastOrNull(), todayStat, daily, true)
    }

    private suspend fun dbDays(days: Int, pick: (com.myfit.tracker.data.db.HcDaily) -> Double?): List<DayStat> = runCatching {
        val today = Clock.today()
        c.db.healthDao().daily(Clock.dateKey(today.minusDays(days.toLong() - 1)), Clock.dateKey(today)).mapNotNull { d ->
            val v = pick(d) ?: return@mapNotNull null
            DayStat(Clock.parse(d.localDate), v, v, v, 0)
        }
    }.getOrDefault(emptyList())

    private suspend fun dbHrDays(days: Int): List<DayStat> = runCatching {
        val today = Clock.today()
        c.db.healthDao().daily(Clock.dateKey(today.minusDays(days.toLong() - 1)), Clock.dateKey(today)).mapNotNull { d ->
            val avg = d.avgHr ?: return@mapNotNull null
            DayStat(Clock.parse(d.localDate), avg.toDouble(), (d.minHr ?: avg).toDouble(), (d.maxHr ?: avg).toDouble(), 0)
        }
    }.getOrDefault(emptyList())

    /** Everything the Vitals screen needs, read in one go (off the main thread). */
    suspend fun all(): All = withContext(Dispatchers.IO) {
        val available = hs.isAvailable
        val g = if (available) hs.granted() else emptySet()
        val spo2 = spo2(30, g)
        // "last night": readings between 8 pm yesterday and 10 am today
        val zone = Clock.zone()
        val today = Clock.today()
        val nightStart = today.minusDays(1).atStartOfDay(zone).toInstant().plus(Duration.ofHours(20))
        val nightEnd = today.atStartOfDay(zone).toInstant().plus(Duration.ofHours(10))
        val night = spo2.raw.filter { !it.at.isBefore(nightStart) && it.at.isBefore(nightEnd) }.map { it.value }
        val types = listOf(
            HeartRateRecord::class, RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class,
            OxygenSaturationRecord::class, RespiratoryRateRecord::class, BodyTemperatureRecord::class,
            Vo2MaxRecord::class, BloodPressureRecord::class,
        )
        All(
            available = available,
            anyPermitted = types.any { hs.readPerm(it) in g },
            heartRate = heartRate(30, g),
            resting = resting(30, g),
            hrv = hrv(30, g),
            spo2 = spo2,
            spo2Night = if (night.size >= 3) night.average() else null,
            breathing = breathing(30, g),
            temperature = temperature(30, g),
            vo2 = vo2(90, g),
            bloodPressure = bloodPressure(30, g),
        )
    }

    /**
     * Which apps wrote which kinds of data in the last [days] days: type label → (package → record count).
     * Types without read permission are left out of the map.
     */
    suspend fun sources(days: Int = 7): Map<String, Map<String, Int>> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, Map<String, Int>>()
        if (!hs.isAvailable) return@withContext out
        val g = hs.granted()
        val (start, end) = window(days)
        suspend fun <T : Record> count(label: String, type: KClass<T>) {
            if (hs.readPerm(type) !in g) return
            val m = runCatching {
                readAll(type, start, end, cap = 10_000).groupingBy { it.metadata.dataOrigin.packageName }.eachCount()
            }.getOrNull() ?: return
            out[label] = m.entries.sortedByDescending { it.value }.associate { it.key to it.value }
        }
        count(TYPE_STEPS, StepsRecord::class)
        count(TYPE_HEART, HeartRateRecord::class)
        count(TYPE_SLEEP, SleepSessionRecord::class)
        count(TYPE_EXERCISE, ExerciseSessionRecord::class)
        count(TYPE_SPO2, OxygenSaturationRecord::class)
        count(TYPE_WEIGHT, WeightRecord::class)
        count(TYPE_BP, BloodPressureRecord::class)
        count(TYPE_GLUCOSE, BloodGlucoseRecord::class)
        out
    }

    companion object {
        const val TYPE_STEPS = "Steps"
        const val TYPE_HEART = "Heart rate"
        const val TYPE_SLEEP = "Sleep"
        const val TYPE_EXERCISE = "Workouts"
        const val TYPE_SPO2 = "Blood oxygen"
        const val TYPE_WEIGHT = "Weight"
        const val TYPE_BP = "Blood pressure"
        const val TYPE_GLUCOSE = "Blood sugar"
        val ALL_TYPES = listOf(TYPE_STEPS, TYPE_HEART, TYPE_SLEEP, TYPE_EXERCISE, TYPE_SPO2, TYPE_WEIGHT, TYPE_BP, TYPE_GLUCOSE)

        private val KNOWN = mapOf(
            "com.sec.android.app.shealth" to "Samsung Health",
            "com.google.android.apps.fitness" to "Google Fit",
            "com.fitbit.FitbitMobile" to "Fitbit",
            "com.garmin.android.apps.connectmobile" to "Garmin Connect",
            "com.xiaomi.wearable" to "Mi Fitness",
            "com.mi.health" to "Mi Fitness",
            "com.huami.watch.hmwatchmanager" to "Zepp",
            "com.ouraring.oura" to "Oura",
            "fi.polar.polarflow" to "Polar Flow",
            "com.whoop.android" to "WHOOP",
            "com.withings.wiscale2" to "Withings",
            "com.google.android.apps.healthdata" to "Health Connect",
            "com.android.healthconnect.controller" to "Health Connect",
            "nl.appyhapps.healthsync" to "Health Sync",
        )

        /** Friendly app name for a Health Connect data origin package. */
        fun appLabel(pkg: String): String = when {
            pkg.isBlank() -> "Unknown app"
            KNOWN.containsKey(pkg) -> KNOWN.getValue(pkg)
            pkg.startsWith("com.myfit") -> "MyFit"
            else -> pkg
        }
    }
}
