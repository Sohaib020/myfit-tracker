package com.myfit.tracker.ui.glucose

import android.content.Context
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.GlucoseReading
import com.myfit.tracker.data.db.GlucoseTag
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Glucose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.time.Instant

/** Diabetes type the user picked during setup (only used for wording and info, never for doses). */
object DiabetesType {
    const val TYPE1 = "TYPE1"; const val TYPE2 = "TYPE2"; const val PREDIABETES = "PREDIABETES"
    const val GESTATIONAL = "GESTATIONAL"; const val OTHER = "OTHER"
    val all = listOf(TYPE1, TYPE2, PREDIABETES, GESTATIONAL, OTHER)
    fun label(t: String): String = when (t) {
        TYPE1 -> "Type 1"; TYPE2 -> "Type 2"; PREDIABETES -> "Prediabetes"; GESTATIONAL -> "Gestational"; else -> "Other / not sure"
    }
}

/** Blood-sugar settings, kept in SharedPreferences "glucose_prefs". Targets are stored in mg/dL. */
data class GlucoseConfig(
    val type: String = DiabetesType.OTHER,
    val mmol: Boolean = false,
    val low: Int = Glucose.DEFAULT_LOW,
    val high: Int = Glucose.DEFAULT_HIGH,
    val writeHc: Boolean = false,
    val setupDone: Boolean = false,
    val testRemind: Boolean = false,
    val testTimes: List<Int> = listOf(7 * 60 + 30, 21 * 60),   // minutes of day
    val ramadan: Boolean = false,
    val ramadanRemind: Boolean = true,
    val suhoorMin: Int = 4 * 60 + 15,
    val iftarMin: Int = 18 * 60 + 30,
    val a1cRemind: Boolean = true,
)

object GlucoseConfigStore {
    private const val FILE = "glucose_prefs"
    private val state = MutableStateFlow<GlucoseConfig?>(null)

    /** Loads once per process (tiny prefs file) and then serves the in-memory copy. */
    fun flow(ctx: Context): StateFlow<GlucoseConfig?> {
        if (state.value == null) state.value = load(ctx)
        return state
    }

    fun get(ctx: Context): GlucoseConfig = state.value ?: load(ctx).also { state.value = it }

    private fun load(ctx: Context): GlucoseConfig = runCatching {
        val p = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        GlucoseConfig(
            type = p.getString("type", DiabetesType.OTHER) ?: DiabetesType.OTHER,
            mmol = p.getBoolean("mmol", false),
            low = p.getInt("low", Glucose.DEFAULT_LOW),
            high = p.getInt("high", Glucose.DEFAULT_HIGH),
            writeHc = p.getBoolean("writeHc", false),
            setupDone = p.getBoolean("setupDone", false),
            testRemind = p.getBoolean("testRemind", false),
            testTimes = (p.getString("testTimes", null) ?: "450,1260").split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..1439 }.distinct().sorted(),
            ramadan = p.getBoolean("ramadan", false),
            ramadanRemind = p.getBoolean("ramadanRemind", true),
            suhoorMin = p.getInt("suhoorMin", 4 * 60 + 15),
            iftarMin = p.getInt("iftarMin", 18 * 60 + 30),
            a1cRemind = p.getBoolean("a1cRemind", true),
        )
    }.getOrDefault(GlucoseConfig())

    fun save(ctx: Context, c: GlucoseConfig) {
        state.value = c
        runCatching {
            ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString("type", c.type).putBoolean("mmol", c.mmol).putInt("low", c.low).putInt("high", c.high)
                .putBoolean("writeHc", c.writeHc).putBoolean("setupDone", c.setupDone)
                .putBoolean("testRemind", c.testRemind).putString("testTimes", c.testTimes.joinToString(","))
                .putBoolean("ramadan", c.ramadan).putBoolean("ramadanRemind", c.ramadanRemind)
                .putInt("suhoorMin", c.suhoorMin).putInt("iftarMin", c.iftarMin).putBoolean("a1cRemind", c.a1cRemind).apply()
        }
    }
}

/** Health Connect bridge for blood glucose. Every call is guarded; failures return 0 / false. */
object GlucoseHc {

    suspend fun canRead(container: AppContainer): Boolean {
        val hs = container.healthSync
        if (!hs.isAvailable) return false
        return runCatching { hs.readPerm(BloodGlucoseRecord::class) in hs.granted() }.getOrDefault(false)
    }

    suspend fun canWrite(container: AppContainer): Boolean {
        val hs = container.healthSync
        if (!hs.isAvailable) return false
        return runCatching { hs.writePerm(BloodGlucoseRecord::class) in hs.granted() }.getOrDefault(false)
    }

    /** Imports the last [days] days of readings from other apps (CGM bridges, meters). Returns how many were new. */
    suspend fun importRecent(container: AppContainer, days: Int = 30): Int = withContext(Dispatchers.IO) {
        if (!canRead(container)) return@withContext 0
        val own = container.app.packageName
        val end = Instant.now()
        val start = end.minusSeconds(days * 86_400L)
        var added = 0
        runCatching {
            var token: String? = null
            var seen = 0
            do {
                val resp = container.healthSync.hc.readRecords(
                    ReadRecordsRequest(recordType = BloodGlucoseRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end), pageToken = token)
                )
                seen += resp.records.size
                resp.records.forEach { r ->
                    val pkg = r.metadata.dataOrigin.packageName
                    if (pkg == own) return@forEach   // our own manual writes are already stored locally
                    val mg = r.level.inMilligramsPerDeciliter
                    if (mg <= 0.0 || mg > 1000.0) return@forEach
                    val st = Stamp.of(r.time.toEpochMilli())
                    val now = Clock.now()
                    val id = container.db.glucoseDao().insertIgnore(
                        GlucoseReading(
                            uuid = "hc:" + r.metadata.id, mgdl = mg, tag = GlucoseTag.CGM, source = "HEALTH_CONNECT",
                            sourcePackage = pkg, takenAt = st.at, zoneId = st.zoneId, localDate = st.localDate,
                            createdAt = now, updatedAt = now,
                        )
                    )
                    if (id > 0) added++
                }
                token = resp.pageToken
            } while (token != null && seen < 40_000)
        }
        added
    }

    /** Writes one manual finger-stick reading to Health Connect. */
    suspend fun write(container: AppContainer, r: GlucoseReading): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite(container)) return@withContext false
        runCatching {
            val t = Instant.ofEpochMilli(r.takenAt)
            val offset = runCatching { java.time.ZoneId.of(r.zoneId) }.getOrDefault(Clock.zone()).rules.getOffset(t)
            val relation = when (r.tag) {
                GlucoseTag.FASTING -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                GlucoseTag.BEFORE_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                GlucoseTag.AFTER_MEAL -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                else -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
            }
            container.healthSync.hc.insertRecords(
                listOf(
                    BloodGlucoseRecord(
                        time = t,
                        zoneOffset = offset,
                        metadata = Metadata.manualEntry(),
                        level = BloodGlucose.milligramsPerDeciliter(r.mgdl),
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                        relationToMeal = relation,
                    )
                )
            )
            true
        }.getOrDefault(false)
    }

    fun sourceLabel(pkg: String?): String = when {
        pkg == null -> "Health Connect"
        pkg.contains("xdrip", true) -> "xDrip+"
        pkg.contains("juggluco", true) -> "Juggluco"
        pkg.contains("dexcom", true) -> "Dexcom"
        pkg.contains("samsung", true) || pkg.contains("shealth", true) -> "Samsung Health"
        pkg.contains("libre", true) || pkg.contains("abbott", true) -> "Libre"
        else -> pkg.substringAfterLast('.')
    }
}

/** Reminder times for medicines — the notification system calls this to (re)schedule alarms. */
object MedReminders {
    /** Parses "08:00,20:00" into minutes of the day (invalid parts are skipped). */
    fun parseTimes(times: String): List<Int> = times.split(',').mapNotNull { part ->
        val p = part.trim().split(':')
        if (p.size != 2) return@mapNotNull null
        val h = p[0].trim().toIntOrNull(); val m = p[1].trim().toIntOrNull()
        if (h == null || m == null || h !in 0..23 || m !in 0..59) null else h * 60 + m
    }.distinct().sorted()

    fun formatTimes(mins: List<Int>): String =
        mins.distinct().sorted().joinToString(",") { String.format(java.util.Locale.US, "%02d:%02d", it / 60, it % 60) }

    /** (medicine name, minute of day) for every active medicine with reminders on. */
    suspend fun schedule(container: AppContainer): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            container.db.medicationDao().active().filter { it.remind }.flatMap { m -> parseTimes(m.times).map { m.name to it } }
        }.getOrDefault(emptyList())
    }
}
