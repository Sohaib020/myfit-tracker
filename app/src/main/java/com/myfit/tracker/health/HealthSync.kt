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
    /** Shared Health Connect client for feature screens (vitals, glucose, cycle, mindfulness). Check [isAvailable] first. */
    val hc: HealthConnectClient get() = client

    /** Read/write permission strings for any record type. */
    fun readPerm(k: KClass<out Record>): String = HealthPermission.getReadPermission(k)
    fun writePerm(k: KClass<out Record>): String = HealthPermission.getWritePermission(k)

    /** Vitals read with the main "Allow everything" request. */
    val vitalsPermissions: Set<String> get() = setOf(
        readPerm(androidx.health.connect.client.records.RespiratoryRateRecord::class),
        readPerm(androidx.health.connect.client.records.BodyTemperatureRecord::class),
        readPerm(androidx.health.connect.client.records.Vo2MaxRecord::class),
        readPerm(androidx.health.connect.client.records.SkinTemperatureRecord::class),
        readPerm(androidx.health.connect.client.records.BloodPressureRecord::class),
        writePerm(androidx.health.connect.client.records.BloodPressureRecord::class),
    )
    /** Only requested when the user turns on blood-sugar tracking. */
    val glucosePermissions: Set<String> get() = setOf(
        readPerm(androidx.health.connect.client.records.BloodGlucoseRecord::class),
        writePerm(androidx.health.connect.client.records.BloodGlucoseRecord::class),
    )
    /** Only requested when the user turns on cycle tracking. */
    val cyclePermissions: Set<String> get() = setOf(
        readPerm(androidx.health.connect.client.records.MenstruationFlowRecord::class), writePerm(androidx.health.connect.client.records.MenstruationFlowRecord::class),
        readPerm(androidx.health.connect.client.records.MenstruationPeriodRecord::class), writePerm(androidx.health.connect.client.records.MenstruationPeriodRecord::class),
        readPerm(androidx.health.connect.client.records.OvulationTestRecord::class), writePerm(androidx.health.connect.client.records.OvulationTestRecord::class),
        readPerm(androidx.health.connect.client.records.CervicalMucusRecord::class), writePerm(androidx.health.connect.client.records.CervicalMucusRecord::class),
        readPerm(androidx.health.connect.client.records.BasalBodyTemperatureRecord::class), writePerm(androidx.health.connect.client.records.BasalBodyTemperatureRecord::class),
        readPerm(androidx.health.connect.client.records.IntermenstrualBleedingRecord::class), writePerm(androidx.health.connect.client.records.IntermenstrualBleedingRecord::class),
        readPerm(androidx.health.connect.client.records.SkinTemperatureRecord::class),
    )
    /** Saving mindful minutes to Health Connect (asked from the Mindfulness screen). */
    val mindfulnessPermissions: Set<String> get() = setOf(
        readPerm(androidx.health.connect.client.records.HeartRateRecord::class),
        readPerm(androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord::class),
    )

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
    val allPermissions: Set<String> get() = dataPermissions + vitalsPermissions + backgroundPermission + historyPermission

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

    /** One day of activity that counts for competitions: only data a device recorded by itself. */
    data class AutoDay(val date: LocalDate, val steps: Long, val distanceM: Double, val activeMin: Long, val workouts: Int)

    /**
     * Competition-grade numbers: steps and distance de-duplicated by Health Connect (watch + phone are not
     * double-counted), MINUS anything typed in by hand; workout minutes only from sessions a watch or
     * phone actually recorded. Manual entries never count, so nobody can type their way up a leaderboard.
     */
    /** One day split by hour, for the Today screen: steps, active minutes (steps at walking pace + workouts) and active kcal. */
    data class Hourly(val steps: LongArray, val activeMin: IntArray, val kcal: DoubleArray, val exerciseMin: Int, val exerciseKcal: Double, val floors: Double?)

    suspend fun hourly(day: LocalDate): Hourly? {
        if (!isAvailable) return null
        val g = granted()
        if (g.none { it in dataPermissions }) return null
        val zone = Clock.zone()
        val steps = LongArray(24); val act = IntArray(24); val kcal = DoubleArray(24)
        val metrics = buildSet<AggregateMetric<*>> {
            if (p(StepsRecord::class) in g) add(StepsRecord.COUNT_TOTAL)
            if (p(ActiveCaloriesBurnedRecord::class) in g) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
        }
        if (metrics.isNotEmpty()) runCatching {
            client.aggregateGroupByDuration(androidx.health.connect.client.request.AggregateGroupByDurationRequest(
                metrics = metrics, timeRangeFilter = TimeRangeFilter.between(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()),
                timeRangeSlicer = java.time.Duration.ofHours(1),
            )).forEach { grp ->
                val h = grp.startTime.atZone(zone).hour
                grp.result[StepsRecord.COUNT_TOTAL]?.let { steps[h] += it }
                grp.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { kcal[h] += it.inKilocalories }
            }
        }
        val start = day.atStartOfDay(zone).toInstant(); val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        // active minutes: step records at ≥ 60 steps/min count as active time, spread over the hours they cover
        if (p(StepsRecord::class) in g) runCatching {
            readAll(StepsRecord::class, start, end).forEach { r ->
                val mins = java.time.Duration.between(r.startTime, r.endTime).toMinutes().coerceAtLeast(1)
                if (r.count / mins.toDouble() >= 60.0) {
                    var t = r.startTime
                    while (t.isBefore(r.endTime)) { val h = t.atZone(zone).hour; act[h] = (act[h] + 1).coerceAtMost(60); t = t.plusSeconds(60) }
                }
            }
        }
        var exMin = 0; var exKcal = 0.0
        if (p(ExerciseSessionRecord::class) in g) runCatching {
            readAll(ExerciseSessionRecord::class, start, end).forEach { sx ->
                exMin += java.time.Duration.between(sx.startTime, sx.endTime).toMinutes().toInt()
                var t = sx.startTime
                while (t.isBefore(sx.endTime)) { val h = t.atZone(zone).hour; act[h] = (act[h] + 1).coerceAtMost(60); t = t.plusSeconds(60) }
                if (p(ActiveCaloriesBurnedRecord::class) in g) runCatching {
                    client.aggregate(androidx.health.connect.client.request.AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(sx.startTime, sx.endTime)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { exKcal += it.inKilocalories }
                }
            }
        }
        var floors: Double? = null
        if (p(FloorsClimbedRecord::class) in g) runCatching {
            floors = client.aggregate(androidx.health.connect.client.request.AggregateRequest(setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL), TimeRangeFilter.between(start, end)))[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL]
        }
        return Hourly(steps, act, kcal, exMin, exKcal, floors)
    }

    suspend fun autoDays(from: LocalDate, to: LocalDate): List<AutoDay> {
        if (!isAvailable) return emptyList()
        val g = granted()
        val zone = Clock.zone()
        val start = from.atStartOfDay(zone).toInstant()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant()
        if (p(StepsRecord::class) !in g) return emptyList()     // nothing trustworthy to report
        val steps = HashMap<LocalDate, Long>(); val dist = HashMap<LocalDate, Double>()
        runCatching {
            client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
                metrics = buildSet { if (p(StepsRecord::class) in g) add(StepsRecord.COUNT_TOTAL); if (p(DistanceRecord::class) in g) add(DistanceRecord.DISTANCE_TOTAL) },
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()), timeRangeSlicer = Period.ofDays(1),
            )).forEach { grp ->
                val d = grp.startTime.toLocalDate()
                grp.result[StepsRecord.COUNT_TOTAL]?.let { steps[d] = it }
                grp.result[DistanceRecord.DISTANCE_TOTAL]?.let { dist[d] = it.inMeters }
            }
        }.onFailure { return emptyList() }     // never upload zeros because a read failed
        // subtract hand-typed entries
        if (p(StepsRecord::class) in g) runCatching {
            readAll(StepsRecord::class, start, end).filter { it.metadata.recordingMethod == androidx.health.connect.client.records.metadata.Metadata.RECORDING_METHOD_MANUAL_ENTRY }
                .forEach { r -> val d = r.startTime.atZone(zone).toLocalDate(); steps[d] = ((steps[d] ?: 0L) - r.count).coerceAtLeast(0L) }
        }
        if (p(DistanceRecord::class) in g) runCatching {
            readAll(DistanceRecord::class, start, end).filter { it.metadata.recordingMethod == androidx.health.connect.client.records.metadata.Metadata.RECORDING_METHOD_MANUAL_ENTRY }
                .forEach { r -> val d = r.startTime.atZone(zone).toLocalDate(); dist[d] = ((dist[d] ?: 0.0) - r.distance.inMeters).coerceAtLeast(0.0) }
        }
        val mins = HashMap<LocalDate, Long>(); val count = HashMap<LocalDate, Int>()
        if (p(ExerciseSessionRecord::class) in g) runCatching {
            readAll(ExerciseSessionRecord::class, start, end)
                .filter { it.metadata.recordingMethod != androidx.health.connect.client.records.metadata.Metadata.RECORDING_METHOD_MANUAL_ENTRY && it.metadata.dataOrigin.packageName != context.packageName }
                .forEach { s ->
                    val d = s.startTime.atZone(zone).toLocalDate()
                    val m = Duration.between(s.startTime, s.endTime).toMinutes().coerceIn(0, 600)
                    mins[d] = (mins[d] ?: 0L) + m; count[d] = (count[d] ?: 0) + 1
                }
        }
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            AutoDay(d, steps[d] ?: 0L, dist[d] ?: 0.0, (mins[d] ?: 0L).coerceAtMost(1440), count[d] ?: 0)
        }.toList()
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
