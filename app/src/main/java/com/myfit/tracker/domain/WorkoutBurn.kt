package com.myfit.tracker.domain

import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetType

/**
 * Calories burned in a logged workout — an estimate, labelled as such wherever it's shown.
 *
 * Strength time uses a MET that rises with how densely you trained (working sets per minute): about 3.5 for a slow,
 * chatty session up to 6.0 for circuit-style work (Compendium of Physical Activities: resistance training 3.5–6.0).
 * Timed cardio sets use their own MET (distance → pace-based running/cycling range, holds such as planks → 3.8).
 * kcal = MET × body kg × hours.
 */
object WorkoutBurn {
    data class Result(val kcal: Double, val met: Double, val activeMin: Int)

    fun estimate(durationSec: Long, sets: List<WorkoutCalc.SetData>, bodyKg: Double): Result {
        val dur = durationSec.coerceIn(0L, 4 * 3600L)
        val work = sets.filter { it.setType != SetType.WARMUP }
        var cardioSec = 0L
        var cardioKcal = 0.0
        work.forEach { s ->
            val sec = s.durationSec ?: 0L
            if (sec <= 0) return@forEach
            when (s.measurementType) {
                MeasurementType.DISTANCE_DURATION -> {
                    val kmh = (s.distanceM ?: 0.0) / 1000.0 / (sec / 3600.0)
                    val met = when { kmh <= 0.0 -> 6.0; kmh < 6.5 -> 4.3; kmh < 9.0 -> 8.3; kmh < 12.0 -> 10.0; kmh < 20.0 -> 8.0 /* cycling */; else -> 10.0 }
                    cardioKcal += met * bodyKg * sec / 3600.0; cardioSec += sec
                }
                MeasurementType.DURATION, MeasurementType.WEIGHT_DURATION -> { cardioKcal += 3.8 * bodyKg * sec / 3600.0; cardioSec += sec }
            }
        }
        val strengthSec = (dur - cardioSec).coerceAtLeast(0L)
        val repSets = work.count { it.measurementType !in setOf(MeasurementType.DISTANCE_DURATION, MeasurementType.DURATION) }
        val perMin = if (strengthSec > 60) repSets / (strengthSec / 60.0) else 0.0
        // ~1 set every 3 min → 5.0; denser work climbs to 6.0, slower sessions settle at 3.5
        val met = if (repSets == 0) 0.0 else (3.5 + 1.5 * (perMin * 3.0)).coerceIn(3.5, 6.0)
        val kcal = met * bodyKg * strengthSec / 3600.0 + cardioKcal
        val avgMet = if (dur > 0) kcal / (bodyKg * dur / 3600.0) else 0.0
        return Result(kcal, avgMet, (dur / 60).toInt())
    }
}

/** Today's estimated workout burn, kept current by MainShell so Home can show calories without Health Connect. */
object TodayBurn {
    val kcal = kotlinx.coroutines.flow.MutableStateFlow(0.0)

    /** Active calories for today's ring: Health Connect's figure, or the logged workouts' estimate when that is higher. */
    fun active(hc: Double?): Double? {
        val w = kcal.value
        return when { hc == null && w <= 0.0 -> null; hc == null -> w; else -> maxOf(hc, w) }
    }
}
