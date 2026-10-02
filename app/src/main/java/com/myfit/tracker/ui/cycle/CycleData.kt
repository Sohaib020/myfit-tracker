package com.myfit.tracker.ui.cycle

import androidx.health.connect.client.records.IntermenstrualBleedingRecord
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

/** Option lists for the log sheet. Keys are what we store; labels are what we show. */
object CycleOptions {
    val flows = listOf(0 to "None", 1 to "Spotting", 2 to "Light", 3 to "Medium", 4 to "Heavy")
    val symptoms = listOf(
        "cramps" to "Cramps", "headache" to "Headache", "bloating" to "Bloating", "back_pain" to "Back pain",
        "acne" to "Acne", "tender_breasts" to "Tender breasts", "fatigue" to "Fatigue", "cravings" to "Cravings",
        "nausea" to "Nausea", "insomnia" to "Insomnia", "mood_swings" to "Mood swings", "anxiety" to "Anxiety",
    )
    val moods = listOf(
        "happy" to "Happy", "calm" to "Calm", "sad" to "Sad", "irritable" to "Irritable", "anxious" to "Anxious", "energetic" to "Energetic",
    )
    val ovulationTests = listOf(0 to "Negative", 1 to "High", 2 to "Peak")
    val mucus = listOf(1 to "Dry", 2 to "Sticky", 3 to "Creamy", 4 to "Watery", 5 to "Egg-white")

    fun flowLabel(f: Int?): String = flows.firstOrNull { it.first == f }?.second ?: "—"
    fun symptomLabel(key: String): String = symptoms.firstOrNull { it.first == key }?.second ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    fun moodLabel(key: String?): String? = key?.let { k -> moods.firstOrNull { it.first == k }?.second ?: k }
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
