package com.myfit.tracker.reminders

import android.content.Context
import com.myfit.tracker.AppContainer
import com.myfit.tracker.data.db.MedKind
import com.myfit.tracker.data.db.MedicationLog
import com.myfit.tracker.data.db.SupplementLog
import com.myfit.tracker.data.db.TargetType
import com.myfit.tracker.data.repo.Stamp
import com.myfit.tracker.domain.Clock
import com.myfit.tracker.domain.Targets
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Small write helpers shared by the routine screens and the notification "done" buttons,
 * so logging from a notification behaves exactly like logging in the app.
 */
object HabitActions {
    private const val SUPP_PREFS = "supplement_prefs"
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(SUPP_PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ supplement ↔ reminder link
    fun suppReminderKey(suppId: Long) = "supp_rem_$suppId"

    fun reminderIdForSupplement(ctx: Context, suppId: Long): Long =
        ReminderScheduler.prefs(ctx).getLong(suppReminderKey(suppId), 0L)

    fun supplementIdForReminder(ctx: Context, reminderId: Long): Long? =
        ReminderScheduler.prefs(ctx).all.entries.firstOrNull { (k, v) -> k.startsWith("supp_rem_") && (v as? Long) == reminderId }
            ?.key?.removePrefix("supp_rem_")?.toLongOrNull()

    // ------------------------------------------------------------------ stock (servings left)
    /** Servings left, or null when stock isn't tracked for this supplement. */
    fun stock(ctx: Context, suppId: Long): Double? {
        val p = sp(ctx)
        return if (p.contains("stock_$suppId")) p.getFloat("stock_$suppId", 0f).toDouble() else null
    }

    fun setStock(ctx: Context, suppId: Long, servings: Double?) {
        val e = sp(ctx).edit()
        if (servings == null) e.remove("stock_$suppId") else e.putFloat("stock_$suppId", servings.coerceAtLeast(0.0).toFloat())
        e.apply()
    }

    fun lowAt(ctx: Context, suppId: Long): Int = sp(ctx).getInt("low_$suppId", 7)
    fun setLowAt(ctx: Context, suppId: Long, n: Int) { sp(ctx).edit().putInt("low_$suppId", n.coerceIn(0, 999)).apply() }

    /** "loading" or "maintenance" phase for creatine (null = not set). */
    fun creatinePhase(ctx: Context, suppId: Long): String? = sp(ctx).getString("phase_$suppId", null)
    fun setCreatinePhase(ctx: Context, suppId: Long, phase: String?) {
        val e = sp(ctx).edit(); if (phase == null) e.remove("phase_$suppId") else e.putString("phase_$suppId", phase); e.apply()
    }
    fun loadingStart(ctx: Context, suppId: Long): String? = sp(ctx).getString("loadstart_$suppId", null)
    fun setLoadingStart(ctx: Context, suppId: Long, date: String?) {
        val e = sp(ctx).edit(); if (date == null) e.remove("loadstart_$suppId") else e.putString("loadstart_$suppId", date); e.apply()
    }

    private fun adjustStock(ctx: Context, suppId: Long, delta: Double) {
        val s = stock(ctx, suppId) ?: return
        setStock(ctx, suppId, s + delta)
    }

    /** Logs one serving taken now (and uses one from stock). */
    suspend fun logSupplement(container: AppContainer, ctx: Context, suppId: Long, dose: Double? = null): Boolean {
        val s = container.db.supplementDao().get(suppId) ?: return false
        val st = Stamp.now()
        container.db.supplementLogDao().insert(
            SupplementLog(
                supplementId = s.id, dose = dose ?: s.defaultDose, unit = s.unit, taken = true,
                loggedAt = st.at, zoneId = st.zoneId, localDate = st.localDate, createdAt = st.at, updatedAt = st.at,
            )
        )
        adjustStock(ctx, suppId, -1.0)
        return true
    }

    /** Removes a taken log (gives the serving back to stock). */
    suspend fun unlogSupplement(container: AppContainer, ctx: Context, logId: Long, suppId: Long) {
        container.db.supplementLogDao().softDelete(logId, Clock.now())
        adjustStock(ctx, suppId, 1.0)
    }

    suspend fun takenToday(container: AppContainer, suppId: Long): Boolean {
        val today = Clock.dateKey(Clock.today())
        return container.db.supplementLogDao().takenFor(suppId).any { it.localDate == today }
    }

    // ------------------------------------------------------------------ medicines (by name)
    /**
     * Records a medicine as taken, matched by name among active medicines. Insulin is never
     * auto-logged (doses must be entered by the user). Returns false when nothing was logged.
     */
    suspend fun logMedicineByName(container: AppContainer, name: String): Boolean {
        val m = container.db.medicationDao().active().firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) } ?: return false
        if (m.kind == MedKind.INSULIN_RAPID || m.kind == MedKind.INSULIN_LONG) return false
        val st = Stamp.now()
        container.db.medicationLogDao().insert(
            MedicationLog(
                medicationId = m.id, name = m.name, kind = m.kind, dose = m.dose, unit = m.unit,
                takenAt = st.at, zoneId = st.zoneId, localDate = st.localDate, notes = "Logged from reminder",
                createdAt = st.at, updatedAt = st.at,
            )
        )
        return true
    }

    // ------------------------------------------------------------------ water
    /** (today's ml, today's target ml or null). */
    suspend fun waterToday(container: AppContainer): Pair<Double, Double?> {
        val today: LocalDate = Clock.today()
        val ml = container.logRepo.waterRange(today, today).first().sumOf { it.amountMl }
        val target = Targets.on(container.profileRepo.targets.first(), TargetType.WATER_ML, today)
        return ml to target
    }
}
