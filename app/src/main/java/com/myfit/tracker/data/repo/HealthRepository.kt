package com.myfit.tracker.data.repo

import com.myfit.tracker.data.db.AppDatabase
import com.myfit.tracker.data.db.ChatMessage
import com.myfit.tracker.data.db.HcDaily
import com.myfit.tracker.data.db.HcSession
import com.myfit.tracker.data.db.HcSleep
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.PhoneStepCalc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Imported health data for one local day. */
data class HealthDay(
    val date: LocalDate,
    val daily: HcDaily? = null,
    val sessions: List<HcSession> = emptyList(),
    val sleep: HcSleep? = null,        // longest sleep that ended this day
    val phoneSteps: Long? = null,
)

class HealthRepository(private val db: AppDatabase) {
    private val dao = db.healthDao()

    fun day(date: LocalDate): Flow<HealthDay> {
        val k = Clock.dateKey(date)
        val from = date.minusDays(1).atStartOfDay(Clock.zone()).toInstant().toEpochMilli()
        return combine(
            dao.observeDaily(k, k), dao.observeSessions(k, k), dao.observeSleep(k, k), dao.observeSnapshotsSince(from),
        ) { d, s, sl, snaps ->
            HealthDay(
                date, d.firstOrNull(), s, sl.maxByOrNull { it.endAt - it.startAt },
                PhoneStepCalc.daily(snaps.map { PhoneStepCalc.Snap(it.at, it.counter, it.localDate) })[k],
            )
        }
    }

    fun dailyRange(from: LocalDate, to: LocalDate): Flow<List<HcDaily>> = dao.observeDaily(Clock.dateKey(from), Clock.dateKey(to))
    fun sessionsRange(from: LocalDate, to: LocalDate): Flow<List<HcSession>> = dao.observeSessions(Clock.dateKey(from), Clock.dateKey(to))
    fun sleepRange(from: LocalDate, to: LocalDate): Flow<List<HcSleep>> = dao.observeSleep(Clock.dateKey(from), Clock.dateKey(to))
    fun session(id: Long) = dao.observeSession(id)

    fun phoneDaily(from: LocalDate): Flow<Map<String, Long>> =
        dao.observeSnapshotsSince(from.minusDays(1).atStartOfDay(Clock.zone()).toInstant().toEpochMilli())
            .map { s -> PhoneStepCalc.daily(s.map { PhoneStepCalc.Snap(it.at, it.counter, it.localDate) }) }

    // chat
    val chat: Flow<List<ChatMessage>> = dao.observeChat()
    suspend fun addChat(role: String, text: String, source: String) = dao.insertChat(ChatMessage(role = role, text = text, source = source, createdAt = Clock.now()))
    suspend fun lastChat(n: Int) = dao.lastChat(n).reversed()
    suspend fun clearChat() = dao.clearChat()
}
