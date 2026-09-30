package com.myfit.tracker.domain

import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetType

/**
 * Workout calculations. Everything here is a pure function of raw set records, so any number
 * shown in the app can be recomputed (and re-verified) from stored sets at any time.
 *
 * Volume rules (shown to the user in the UI):
 *  - Volume = weight × reps, only for exercises measured as WEIGHT_REPS.
 *  - Warm-up sets are excluded from volume; they still count as sets and reps.
 *  - Bodyweight, assisted, duration and distance exercises have no weight volume.
 */
object WorkoutCalc {

    data class SetData(
        val exerciseId: Long,
        val measurementType: String,
        val setType: String,
        val weightKg: Double?,
        val reps: Int?,
        val durationSec: Long?,
        val distanceM: Double?,
        val restSec: Long?,
    )

    fun countsForVolume(s: SetData) =
        s.measurementType == MeasurementType.WEIGHT_REPS && s.setType != SetType.WARMUP &&
            s.weightKg != null && s.reps != null && s.weightKg > 0 && s.reps > 0

    /** null when volume doesn't apply to this set (never 0 as a stand-in). */
    fun setVolume(s: SetData): Double? = if (countsForVolume(s)) s.weightKg!! * s.reps!! else null

    data class Totals(
        val sets: Int,
        val reps: Int,
        val volumeKg: Double?,          // null = no weight-based sets at all
        val durationSec: Long,
        val distanceM: Double,
        val avgRestSec: Double?,        // null = no rest recorded
        val exercises: Int,
    )

    fun totals(sets: List<SetData>): Totals {
        val vols = sets.mapNotNull { setVolume(it) }
        val rests = sets.mapNotNull { it.restSec }.filter { it > 0 }
        return Totals(
            sets = sets.size,
            reps = sets.sumOf { it.reps ?: 0 },
            volumeKg = if (vols.isEmpty()) null else vols.sum(),
            durationSec = sets.sumOf { it.durationSec ?: 0L },
            distanceM = sets.sumOf { it.distanceM ?: 0.0 },
            avgRestSec = if (rests.isEmpty()) null else rests.average(),
            exercises = sets.map { it.exerciseId }.distinct().size,
        )
    }

    /**
     * Estimated one-rep max (Epley). Only meaningful for 1–12 reps of a loaded lift; returns null
     * otherwise. Always presented as an estimate, never as a lifted weight.
     */
    fun estimated1Rm(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null || weightKg <= 0 || reps < 1 || reps > 12) return null
        return if (reps == 1) weightKg else weightKg * (1 + reps / 30.0)
    }
}

/**
 * Pre-fill for the next set, in order of preference:
 *  1. the set just completed in this workout (same exercise) — you usually repeat it;
 *  2. the same set number from the previous session of this exercise;
 *  3. the last set of the previous session;
 *  4. the template target;
 *  5. nothing (empty field — never an invented number).
 */
object Prefill {
    data class Values(val weightKg: Double?, val reps: Int?, val durationSec: Long?, val distanceM: Double?)

    fun next(
        todaysSets: List<Values>,
        previousSession: List<Values>,
        target: Values?,
    ): Values {
        todaysSets.lastOrNull()?.let { return it }
        val n = todaysSets.size
        previousSession.getOrNull(n)?.let { return it }
        previousSession.lastOrNull()?.let { return it }
        return target ?: Values(null, null, null, null)
    }
}
