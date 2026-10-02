package com.myfit.tracker.ui.cycle

import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.CycleDay
import com.myfit.tracker.domain.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.reflect.KClass

/**
 * Option lists for the log sheet. Keys are what we store; labels are what we show.
 * Pain, discharge, perimenopause symptoms and intimacy are stored as extra keys in CycleDay.symptoms
 * (prefixes pain_, dis_, sex_) so no database change is needed.
 */
object CycleOptions {
    val flows = listOf(0 to "None", 1 to "Spotting", 2 to "Light", 3 to "Medium", 4 to "Heavy")
    val symptoms = listOf(
        "cramps" to "Cramps", "headache" to "Headache", "bloating" to "Bloating", "back_pain" to "Back pain",
        "acne" to "Acne", "tender_breasts" to "Tender breasts", "fatigue" to "Fatigue", "cravings" to "Cravings",
        "nausea" to "Nausea", "insomnia" to "Insomnia", "mood_swings" to "Mood swings", "anxiety" to "Anxiety",
        "dizziness" to "Dizziness", "constipation" to "Constipation", "diarrhoea" to "Diarrhoea",
        "hair_growth" to "Extra hair growth", "hair_loss" to "Hair thinning", "weight_gain" to "Weight gain",
    )
    val periSymptoms = listOf(
        "hot_flush" to "Hot flushes", "night_sweats" to "Night sweats", "poor_sleep" to "Poor sleep",
        "vaginal_dryness" to "Dryness", "joint_pain" to "Joint aches", "brain_fog" to "Brain fog", "low_mood" to "Low mood",
    )
    val pain = listOf("pain_1" to "Mild", "pain_2" to "Moderate", "pain_3" to "Severe")
    val discharge = listOf(
        "dis_white" to "White / creamy", "dis_clear" to "Clear / watery", "dis_brown" to "Brown",
        "dis_yellow" to "Yellow / green", "dis_odour" to "Unusual smell", "dis_itch" to "Itching",
    )
    /** Discharge keys that can point to an infection if they persist. */
    val dischargeCheck = setOf("dis_yellow", "dis_odour", "dis_itch")
    val intimacy = listOf("sex_protected" to "Protected", "sex_unprotected" to "Unprotected")
    val moods = listOf(
        "happy" to "Happy", "calm" to "Calm", "sad" to "Sad", "irritable" to "Irritable", "anxious" to "Anxious", "energetic" to "Energetic",
    )
    val ovulationTests = listOf(0 to "Negative", 1 to "High", 2 to "Peak")
    val mucus = listOf(1 to "Dry", 2 to "Sticky", 3 to "Creamy", 4 to "Watery", 5 to "Egg-white")

    private val allSymptomLabels: Map<String, String> = (symptoms + periSymptoms + pain.map { it.first to "Pain: " + it.second } +
        discharge.map { it.first to "Discharge: " + it.second } + intimacy.map { it.first to "Intimacy: " + it.second }).toMap()

    fun flowLabel(f: Int?): String = flows.firstOrNull { it.first == f }?.second ?: "—"
    fun symptomLabel(key: String): String = allSymptomLabels[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    fun moodLabel(key: String?): String? = key?.let { k -> moods.firstOrNull { it.first == k }?.second ?: k }
    /** Keys that are real body symptoms (not pain level, discharge or intimacy) — used for "most common symptoms". */
    fun isSymptom(key: String): Boolean = !key.startsWith("pain_") && !key.startsWith("dis_") && !key.startsWith("sex_")
}

fun CycleDay.symptomList(): List<String> = symptoms.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** True when a row carries no information at all (then we delete it instead of keeping an empty row). */
fun CycleDay.isBlank(): Boolean =
    flow == null && symptoms.isBlank() && mood == null && ovulationTest == null && mucus == null &&
        bbtC == null && pillTaken == null && notes.isBlank()

object CycleStore {

    /**
     * Upsert one day by its localDate (unique). Keeps the original id/uuid/createdAt.
     * A completely empty day is deleted. Also mirrors flow to Health Connect when allowed.
     */
    suspend fun save(c: AppContainer, day: CycleDay) {
        val dao = c.db.cycleDao()
        val now = Clock.now()
        val existing = dao.byDate(day.localDate)
        if (day.isBlank()) {
            if (existing != null) dao.deleteByDate(day.localDate)
        } else if (existing == null) {
            val fresh = day.copy(id = 0, createdAt = now, updatedAt = now)
            val inserted = runCatching { dao.insert(fresh) }
            if (inserted.isFailure) {
                // Lost a race with another write for the same date — fall back to update.
                dao.byDate(day.localDate)?.let { dao.update(fresh.copy(id = it.id, uuid = it.uuid, createdAt = it.createdAt)) }
            }
        } else {
            dao.update(day.copy(id = existing.id, uuid = existing.uuid, createdAt = existing.createdAt, updatedAt = now))
        }
        if (existing?.flow != day.flow) {
            runCatching { CycleHealth.writeFlow(c, LocalDate.parse(day.localDate), if (day.isBlank()) null else day.flow, now) }
        }
        if (existing?.bbtC != day.bbtC || existing?.ovulationTest != day.ovulationTest || existing?.mucus != day.mucus) {
            runCatching { CycleHealth.writeFertility(c, LocalDate.parse(day.localDate), if (day.isBlank()) null else day, existing, now) }
        }
    }

    /** "Delete all cycle data": local rows, cycle settings, and anything MyFit wrote to Health Connect. */
    suspend fun deleteAll(c: AppContainer) {
        val rows = runCatching { c.db.cycleDao().all() }.getOrDefault(emptyList())
        runCatching { CycleHealth.deleteOwn(c, rows.mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }) }
        c.db.cycleDao().deleteAll()
        CyclePrefs.clearAll(c.app)
    }

    /** "Period started today": mark today as medium flow (keeps any other fields already logged). */
    suspend fun periodStartedToday(c: AppContainer) {
        val key = Clock.dateKey(Clock.today())
        val now = Clock.now()
        val base = c.db.cycleDao().byDate(key) ?: CycleDay(localDate = key, createdAt = now, updatedAt = now)
        save(c, base.copy(flow = 3))
    }
}

/**
 * Health Connect bridge for cycle data. Every call is guarded: HC may be missing or permissions
 * may not be granted, in which case nothing happens and the feature keeps working locally.
 */
object CycleHealth {
    private const val ID_PREFIX = "myfit-cycle-"

    private fun flowId(date: LocalDate) = "${ID_PREFIX}flow-$date"
    private fun spotId(date: LocalDate) = "${ID_PREFIX}spot-$date"

    suspend fun granted(c: AppContainer): Set<String> =
        if (!c.healthSync.isAvailable) emptySet() else runCatching { c.healthSync.granted() }.getOrDefault(emptySet())

    fun hasAny(c: AppContainer, granted: Set<String>): Boolean = granted.any { it in c.healthSync.cyclePermissions }

    /**
     * Mirror one day's flow: light/medium/heavy → MenstruationFlowRecord, spotting → IntermenstrualBleedingRecord,
     * none/cleared → delete what we wrote before. Records use a stable client id per date, so re-logging the
     * same day replaces instead of duplicating (version = edit time).
     */
    suspend fun writeFlow(c: AppContainer, date: LocalDate, flow: Int?, version: Long) {
        if (!c.healthSync.isAvailable) return
        val g = granted(c)
        val hs = c.healthSync
        val canFlow = hs.writePerm(MenstruationFlowRecord::class) in g
        val canSpot = hs.writePerm(IntermenstrualBleedingRecord::class) in g
        if (!canFlow && !canSpot) return
        val zone = Clock.zone()
        val time: Instant = date.atTime(12, 0).atZone(zone).toInstant()
        val offset = zone.rules.getOffset(time)
        val hcFlow = when (flow) {
            2 -> MenstruationFlowRecord.FLOW_LIGHT
            3 -> MenstruationFlowRecord.FLOW_MEDIUM
            4 -> MenstruationFlowRecord.FLOW_HEAVY
            else -> null
        }
        if (canFlow) runCatching {
            if (hcFlow != null) {
                hs.hc.insertRecords(
                    listOf(
                        MenstruationFlowRecord(
                            time = time,
                            zoneOffset = offset,
                            metadata = Metadata.manualEntry(clientRecordId = flowId(date), clientRecordVersion = version),
                            flow = hcFlow,
                        )
                    )
                )
            } else {
                hs.hc.deleteRecords(MenstruationFlowRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(flowId(date)))
            }
        }
        if (canSpot) runCatching {
            if (flow == 1) {
                hs.hc.insertRecords(
                    listOf(
                        IntermenstrualBleedingRecord(
                            time = time,
                            zoneOffset = offset,
                            metadata = Metadata.manualEntry(clientRecordId = spotId(date), clientRecordVersion = version),
                        )
                    )
                )
            } else {
                hs.hc.deleteRecords(IntermenstrualBleedingRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(spotId(date)))
            }
        }
    }

    private fun bbtId(date: LocalDate) = "${ID_PREFIX}bbt-$date"
    private fun ovId(date: LocalDate) = "${ID_PREFIX}ov-$date"
    private fun mucusId(date: LocalDate) = "${ID_PREFIX}mucus-$date"

    /** Mirrors BBT, ovulation test and cervical mucus for one day (only what changed; cleared values are deleted). */
    suspend fun writeFertility(c: AppContainer, date: LocalDate, day: CycleDay?, before: CycleDay?, version: Long) {
        if (!c.healthSync.isAvailable) return
        val g = granted(c)
        val hs = c.healthSync
        val zone = Clock.zone()
        val noon: Instant = date.atTime(12, 0).atZone(zone).toInstant()
        val morning: Instant = date.atTime(7, 0).atZone(zone).toInstant()
        if (hs.writePerm(BasalBodyTemperatureRecord::class) in g && day?.bbtC != before?.bbtC) runCatching {
            val v = day?.bbtC
            if (v != null) hs.hc.insertRecords(listOf(
                BasalBodyTemperatureRecord(
                    time = morning, zoneOffset = zone.rules.getOffset(morning),
                    metadata = Metadata.manualEntry(clientRecordId = bbtId(date), clientRecordVersion = version),
                    temperature = Temperature.celsius(v),
                )
            )) else hs.hc.deleteRecords(BasalBodyTemperatureRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(bbtId(date)))
        }
        if (hs.writePerm(OvulationTestRecord::class) in g && day?.ovulationTest != before?.ovulationTest) runCatching {
            val r = when (day?.ovulationTest) {
                0 -> OvulationTestRecord.RESULT_NEGATIVE
                1 -> OvulationTestRecord.RESULT_HIGH
                2 -> OvulationTestRecord.RESULT_POSITIVE
                else -> null
            }
            if (r != null) hs.hc.insertRecords(listOf(
                OvulationTestRecord(
                    time = noon, zoneOffset = zone.rules.getOffset(noon), result = r,
                    metadata = Metadata.manualEntry(clientRecordId = ovId(date), clientRecordVersion = version),
                )
            )) else hs.hc.deleteRecords(OvulationTestRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(ovId(date)))
        }
        if (hs.writePerm(CervicalMucusRecord::class) in g && day?.mucus != before?.mucus) runCatching {
            val m = day?.mucus
            if (m != null && m in 1..5) hs.hc.insertRecords(listOf(
                CervicalMucusRecord(
                    time = noon, zoneOffset = zone.rules.getOffset(noon),
                    metadata = Metadata.manualEntry(clientRecordId = mucusId(date), clientRecordVersion = version),
                    appearance = m,   // our 1..5 codes match Health Connect's APPEARANCE_DRY..EGG_WHITE
                )
            )) else hs.hc.deleteRecords(CervicalMucusRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf(mucusId(date)))
        }
    }

    /** Deletes every record MyFit wrote for these days (used when deleting all cycle data). */
    suspend fun deleteOwn(c: AppContainer, dates: List<LocalDate>) {
        if (!c.healthSync.isAvailable || dates.isEmpty()) return
        val g = granted(c)
        val hs = c.healthSync
        dates.chunked(200).forEach { chunk ->
            if (hs.writePerm(MenstruationFlowRecord::class) in g) runCatching { hs.hc.deleteRecords(MenstruationFlowRecord::class, emptyList(), chunk.map { flowId(it) }) }
            if (hs.writePerm(IntermenstrualBleedingRecord::class) in g) runCatching { hs.hc.deleteRecords(IntermenstrualBleedingRecord::class, emptyList(), chunk.map { spotId(it) }) }
            if (hs.writePerm(BasalBodyTemperatureRecord::class) in g) runCatching { hs.hc.deleteRecords(BasalBodyTemperatureRecord::class, emptyList(), chunk.map { bbtId(it) }) }
            if (hs.writePerm(OvulationTestRecord::class) in g) runCatching { hs.hc.deleteRecords(OvulationTestRecord::class, emptyList(), chunk.map { ovId(it) }) }
            if (hs.writePerm(CervicalMucusRecord::class) in g) runCatching { hs.hc.deleteRecords(CervicalMucusRecord::class, emptyList(), chunk.map { mucusId(it) }) }
        }
    }

    /**
     * Nightly wrist/finger skin-temperature change (°C vs the device's own baseline) from watches that share it
     * (e.g. Pixel Watch, Fitbit, some Galaxy Watch setups). Keyed by the local date the night ended. Empty when
     * not permitted or not available.
     */
    suspend fun skinTempNights(c: AppContainer, days: Int = 60): Map<LocalDate, Double> {
        if (!c.healthSync.isAvailable) return emptyMap()
        val hs = c.healthSync
        if (hs.readPerm(SkinTemperatureRecord::class) !in granted(c)) return emptyMap()
        val zone = Clock.zone()
        val today = Clock.today()
        return runCatching {
            readAll(c, SkinTemperatureRecord::class, today.minusDays(days.toLong()).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant())
                .mapNotNull { r ->
                    if (r.deltas.isEmpty()) return@mapNotNull null
                    r.endTime.atZone(zone).toLocalDate() to r.deltas.map { it.delta.inCelsius }.average()
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.average() }
        }.getOrDefault(emptyMap())
    }

    private suspend fun <T : Record> readAll(c: AppContainer, type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = ArrayList<T>()
        var token: String? = null
        do {
            val resp = c.healthSync.hc.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(start, end), pageToken = token)
            )
            out += resp.records
            token = resp.pageToken
        } while (token != null && out.size < 5_000)
        return out
    }

    /**
     * Pull periods/flow logged in other apps (Flo, Samsung Health, …) for the last 365 days and fill
     * days that have no local flow yet. Never overwrites anything the user logged here. Returns days added/filled.
     */
    suspend fun importFromOthers(c: AppContainer): Int {
        if (!c.healthSync.isAvailable) return 0
        val g = granted(c)
        val hs = c.healthSync
        val canFlow = hs.readPerm(MenstruationFlowRecord::class) in g
        val canPeriod = hs.readPerm(MenstruationPeriodRecord::class) in g
        if (!canFlow && !canPeriod) return 0
        val zone = Clock.zone()
        val today = Clock.today()
        val start = today.minusDays(365).atStartOfDay(zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant()
        val mine = c.app.packageName
        val found = HashMap<LocalDate, Int>()

        if (canPeriod) runCatching {
            readAll(c, MenstruationPeriodRecord::class, start, end)
                .filter { it.metadata.dataOrigin.packageName != mine }
                .forEach { r ->
                    val first = r.startTime.atZone(zone).toLocalDate()
                    // endTime is usually exclusive (start of the next day) — step back a millisecond.
                    val last = r.endTime.minusMillis(1).atZone(zone).toLocalDate().let { if (it.isBefore(first)) first else it }
                    var d = first
                    var guard = 0
                    while (!d.isAfter(last) && guard < 31) { found.putIfAbsent(d, 3); d = d.plusDays(1); guard++ }
                }
        }
        // Flow records are more specific than a period's default "medium", so they win; several per day → heaviest.
        val flowFound = HashMap<LocalDate, Int>()
        if (canFlow) runCatching {
            readAll(c, MenstruationFlowRecord::class, start, end)
                .filter { it.metadata.dataOrigin.packageName != mine }
                .forEach { r ->
                    val d = r.time.atZone(zone).toLocalDate()
                    val f = when (r.flow) {
                        MenstruationFlowRecord.FLOW_LIGHT -> 2
                        MenstruationFlowRecord.FLOW_HEAVY -> 4
                        else -> 3
                    }
                    flowFound[d] = maxOf(f, flowFound[d] ?: 0)
                }
        }
        found.putAll(flowFound)
        if (found.isEmpty()) return 0

        val dao = c.db.cycleDao()
        val now = Clock.now()
        var n = 0
        for ((d, f) in found) {
            val key = Clock.dateKey(d)
            val existing = dao.byDate(key)
            runCatching {
                if (existing == null) {
                    dao.insert(CycleDay(localDate = key, flow = f, createdAt = now, updatedAt = now)); n++
                } else if (existing.flow == null) {
                    dao.update(existing.copy(flow = f, updatedAt = now)); n++
                }
            }
        }
        return n
    }
}
