package com.myfit.tracker.health

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HydrationRecord
import com.myfit.tracker.data.db.WeightEntry
import com.myfit.tracker.data.db.WaterEntry
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.aggregate.AggregateMetric
import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.db.HcDaily
import com.myfit.tracker.data.db.HcSession
import com.myfit.tracker.data.db.HcSleep
import com.myfit.tracker.domain.Clock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import kotlin.reflect.KClass

/**
 * Read-only bridge to Health Connect (where Samsung Health and the Galaxy Watch publish data).
 *
 * Accuracy notes:
 *  - Daily steps/distance/calories use Health Connect's own aggregation, which removes overlaps
 *    between phone and watch. Summing raw records ourselves would double-count.
 *  - Days with no data are stored as null, never 0.
 *  - A sync re-reads a whole window and replaces it, so edits/deletions made in Samsung Health
 *    flow through and nothing is duplicated.
 */
class HealthSync(private val context: Context, private val db: AppDatabase) {

    data class Result(val ok: Boolean, val message: String, val days: Int = 0, val sessions: Int = 0, val sleeps: Int = 0)

    val sdkStatus: Int get() = runCatching { HealthConnectClient.getSdkStatus(context) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)
    val isAvailable: Boolean get() = sdkStatus == HealthConnectClient.SDK_AVAILABLE
    val needsUpdate: Boolean get() = sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private fun p(k: KClass<out Record>) = HealthPermission.getReadPermission(k)

    val dataPermissions: Set<String> = setOf(
        p(StepsRecord::class), p(DistanceRecord::class), p(ActiveCaloriesBurnedRecord::class),
        p(TotalCaloriesBurnedRecord::class), p(FloorsClimbedRecord::class), p(ExerciseSessionRecord::class),
        p(HeartRateRecord::class), p(RestingHeartRateRecord::class), p(HeartRateVariabilityRmssdRecord::class),
        p(OxygenSaturationRecord::class), p(SleepSessionRecord::class),
        p(WeightRecord::class), p(BodyFatRecord::class), p(HydrationRecord::class),
    )
    val backgroundPermission = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    val historyPermission = "android.permission.health.READ_HEALTH_DATA_HISTORY"
    val allPermissions: Set<String> get() = dataPermissions + backgroundPermission + historyPermission

    suspend fun granted(): Set<String> = if (!isAvailable) emptySet() else runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    fun settingsIntent(): Intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS").let {
        if (it.resolveActivity(context.packageManager) != null) it else Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
    }

    /** Re-reads the last `days` local days (including today) and replaces that window. */
    suspend fun sync(days: Int): Result {
        if (!isAvailable) return Result(false, "Health Connect isn't available on this phone")
        val g = granted()
        if (g.none { it in dataPermissions }) return Result(false, "No Health Connect permissions granted yet")
        val zone = Clock.zone()
        val today = Clock.today()
        val first = today.minusDays(days.toLong() - 1)
        val startLdt = first.atStartOfDay()
        val endLdt = today.plusDays(1).atStartOfDay()
        val start = first.atStartOfDay(zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant()
        val now = Clock.now()
        val errors = mutableListOf<String>()

        // ---------------- daily aggregates
        val daily = LinkedHashMap<LocalDate, HcDaily>()
        fun row(d: LocalDate) = daily.getOrPut(d) { HcDaily(Clock.dateKey(d), null, null, null, null, null, null, null, null, null, null, null, now) }
        val metrics = buildSet<AggregateMetric<*>> {
            if (p(StepsRecord::class) in g) add(StepsRecord.COUNT_TOTAL)
            if (p(DistanceRecord::class) in g) add(DistanceRecord.DISTANCE_TOTAL)
            if (p(ActiveCaloriesBurnedRecord::class) in g) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (p(TotalCaloriesBurnedRecord::class) in g) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (p(FloorsClimbedRecord::class) in g) add(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
            if (p(HeartRateRecord::class) in g) { add(HeartRateRecord.BPM_AVG); add(HeartRateRecord.BPM_MIN); add(HeartRateRecord.BPM_MAX) }
            if (p(RestingHeartRateRecord::class) in g) add(RestingHeartRateRecord.BPM_AVG)
        }
        if (metrics.isNotEmpty()) runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(metrics = metrics, timeRangeFilter = TimeRangeFilter.between(startLdt, endLdt), timeRangeSlicer = Period.ofDays(1))
            ).forEach { grp ->
                val r = grp.result
                val d = grp.startTime.toLocalDate()
                row(d).let { base ->
                    daily[d] = base.copy(
                        steps = r[StepsRecord.COUNT_TOTAL],
                        distanceM = r[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                        activeKcal = r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
                        totalKcal = r[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
                        floors = r[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL],
                        avgHr = r[HeartRateRecord.BPM_AVG], minHr = r[HeartRateRecord.BPM_MIN], maxHr = r[HeartRateRecord.BPM_MAX],
                        restingHr = r[RestingHeartRateRecord.BPM_AVG],
                    )
                }
            }
        }.onFailure { errors += "daily totals: ${it.message}" }

        // HRV and SpO2 have no aggregate metrics — average the raw readings per local day
        if (p(HeartRateVariabilityRmssdRecord::class) in g) runCatching {
            readAll(HeartRateVariabilityRmssdRecord::class, start, end).groupBy { it.time.atZone(zone).toLocalDate() }
                .forEach { (d, list) -> daily[d] = row(d).copy(hrvMs = list.map { it.heartRateVariabilityMillis }.average()) }
        }.onFailure { errors += "HRV: ${it.message}" }
        if (p(OxygenSaturationRecord::class) in g) runCatching {
            readAll(OxygenSaturationRecord::class, start, end).groupBy { it.time.atZone(zone).toLocalDate() }
                .forEach { (d, list) -> daily[d] = row(d).copy(spo2Pct = list.map { it.percentage.value }.average()) }
        }.onFailure { errors += "SpO2: ${it.message}" }

        val keepDays = daily.values.filter {
            listOf(it.steps, it.distanceM, it.activeKcal, it.totalKcal, it.floors, it.avgHr, it.restingHr, it.hrvMs, it.spo2Pct).any { v -> v != null }
        }
        db.healthDao().upsertDaily(keepDays)

        // ---------------- workouts / activities (watch-detected included)
        var sessionCount = 0
        if (p(ExerciseSessionRecord::class) in g) runCatching {
            val sessions = readAll(ExerciseSessionRecord::class, start, end).map { s ->
                val off = s.startZoneOffset ?: zone.rules.getOffset(s.startTime)
                val agg = runCatching {
                    val m = buildSet<AggregateMetric<*>> {
                        if (p(DistanceRecord::class) in g) add(DistanceRecord.DISTANCE_TOTAL)
                        if (p(ActiveCaloriesBurnedRecord::class) in g) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                        if (p(StepsRecord::class) in g) add(StepsRecord.COUNT_TOTAL)
                        if (p(HeartRateRecord::class) in g) { add(HeartRateRecord.BPM_AVG); add(HeartRateRecord.BPM_MAX) }
                    }
                    if (m.isEmpty()) null else client.aggregate(AggregateRequest(metrics = m, timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime)))
                }.getOrNull()
                HcSession(
                    externalId = s.metadata.id, exerciseType = s.exerciseType, title = s.title,
                    startAt = s.startTime.toEpochMilli(), endAt = s.endTime.toEpochMilli(),
                    zoneId = off.id, localDate = Clock.dateKey(s.startTime.atOffset(off).toLocalDate()),
                    distanceM = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
                    activeKcal = agg?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
                    steps = agg?.get(StepsRecord.COUNT_TOTAL),
                    avgHr = agg?.get(HeartRateRecord.BPM_AVG), maxHr = agg?.get(HeartRateRecord.BPM_MAX),
                    segments = segmentsJson(s.segments),
                    sourcePackage = s.metadata.dataOrigin.packageName, syncedAt = now,
                )
            }
            db.healthDao().replaceSessions(start.toEpochMilli(), end.toEpochMilli(), sessions)
            sessionCount = sessions.size
        }.onFailure { errors += "workouts: ${it.message}" }

        // ---------------- sleep (with stages)
        var sleepCount = 0
        if (p(SleepSessionRecord::class) in g) runCatching {
            // sleep that ended inside the window may have started the evening before it
            val list = readAll(SleepSessionRecord::class, start.minus(Duration.ofHours(18)), end)
                .filter { !it.endTime.isBefore(start) }
                .map { s ->
                    val off = s.endZoneOffset ?: zone.rules.getOffset(s.endTime)
                    fun mins(vararg types: Int) = s.stages.filter { it.stage in types }
                        .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.takeIf { s.stages.isNotEmpty() }
                    HcSleep(
                        externalId = s.metadata.id, startAt = s.startTime.toEpochMilli(), endAt = s.endTime.toEpochMilli(),
                        zoneId = off.id, localDate = Clock.dateKey(s.endTime.atOffset(off).toLocalDate()),
                        deepMin = mins(SleepSessionRecord.STAGE_TYPE_DEEP),
                        remMin = mins(SleepSessionRecord.STAGE_TYPE_REM),
                        lightMin = mins(SleepSessionRecord.STAGE_TYPE_LIGHT, SleepSessionRecord.STAGE_TYPE_SLEEPING),
                        awakeMin = mins(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED),
                        sourcePackage = s.metadata.dataOrigin.packageName, syncedAt = now,
                    )
                }
            db.healthDao().replaceSleep(start.toEpochMilli(), end.toEpochMilli(), list)
            sleepCount = list.size
        }.onFailure { errors += "sleep: ${it.message}" }

        // ---------------- weight / body fat (Galaxy Watch body composition, smart scales) and water
        var bodyCount = 0
        runCatching {
            if (p(WeightRecord::class) in g) {
                val fats = if (p(BodyFatRecord::class) in g) readAll(BodyFatRecord::class, start, end) else emptyList()
                readAll(WeightRecord::class, start, end).forEach { w ->
                    val off = w.zoneOffset ?: zone.rules.getOffset(w.time)
                    val fat = fats.minByOrNull { kotlin.math.abs(it.time.epochSecond - w.time.epochSecond) }
                        ?.takeIf { kotlin.math.abs(it.time.epochSecond - w.time.epochSecond) < 15 * 60 }?.percentage?.value
                    val id = db.healthImportDao().insertWeight(WeightEntry(
                        uuid = "hc:" + w.metadata.id, weightKg = w.weight.inKilograms, bodyFatPct = fat,
                        loggedAt = w.time.toEpochMilli(), zoneId = off.id, localDate = Clock.dateKey(w.time.atOffset(off).toLocalDate()),
                        note = "From " + sourceLabel(w.metadata.dataOrigin.packageName), createdAt = now, updatedAt = now,
                    ))
                    if (id > 0) bodyCount++
                }
            }
        }.onFailure { errors += "weight: ${it.message}" }
        runCatching {
            if (p(HydrationRecord::class) in g) {
                readAll(HydrationRecord::class, start, end)
                    .filter { it.metadata.dataOrigin.packageName != context.packageName }
                    .forEach { h ->
                        val off = h.startZoneOffset ?: zone.rules.getOffset(h.startTime)
                        db.healthImportDao().insertWater(WaterEntry(
                            uuid = "hc:" + h.metadata.id, amountMl = h.volume.inMilliliters, loggedAt = h.startTime.toEpochMilli(),
                            zoneId = off.id, localDate = Clock.dateKey(h.startTime.atOffset(off).toLocalDate()), createdAt = now, updatedAt = now,
                        ))
                    }
            }
        }.onFailure { errors += "water: ${it.message}" }

        return if (errors.isEmpty()) Result(true, "Synced ${keepDays.size} days · $sessionCount activities · $sleepCount sleeps" + (if (bodyCount > 0) " · $bodyCount weigh-ins" else ""), keepDays.size, sessionCount, sleepCount)
        else Result(keepDays.isNotEmpty() || sessionCount > 0, "Partly synced — " + errors.joinToString("; "), keepDays.size, sessionCount, sleepCount)
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val resp = client.readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(start, end), pageToken = token))
            out += resp.records
            token = resp.pageToken
        } while (token != null && out.size < 20_000)
        return out
    }

    private fun segmentsJson(segs: List<ExerciseSegment>): String {
        val a = JSONArray()
        segs.forEach { sg ->
            a.put(JSONObject().put("type", sg.segmentType).put("reps", sg.repetitions)
                .put("start", sg.startTime.toEpochMilli()).put("end", sg.endTime.toEpochMilli()))
        }
        return a.toString()
    }

    companion object {
        /** Readable names for every Health Connect exercise type, derived from the library's own constants. */
        private val typeNames: Map<Int, String> by lazy { constantNames(ExerciseSessionRecord::class.java, "EXERCISE_TYPE_") }
        private val segmentNames: Map<Int, String> by lazy { constantNames(ExerciseSegment::class.java, "EXERCISE_SEGMENT_TYPE_") }

        fun exerciseName(type: Int): String = typeNames[type] ?: "Workout"
        fun segmentName(type: Int): String = segmentNames[type] ?: "Exercise"

        private fun constantNames(cls: Class<*>, prefix: String): Map<Int, String> = runCatching {
            cls.fields.filter { it.name.startsWith(prefix) && it.type == Int::class.javaPrimitiveType }
                .associate { f ->
                    f.getInt(null) to f.name.removePrefix(prefix).lowercase().split('_')
                        .joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
                        .replace("Hiit", "HIIT").replace("High Intensity Interval Training", "HIIT")
                }
        }.getOrDefault(emptyMap())

        fun sourceLabel(pkg: String) = when {
            pkg.contains("shealth") || pkg.contains("samsung") -> "Samsung Health"
            pkg.contains("google.android.apps.fitness") -> "Google Fit"
            pkg.contains("healthdata") || pkg == "android" -> "Health Connect"
            pkg.contains("myfit") -> "MyFit"
            else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }

    }
}
