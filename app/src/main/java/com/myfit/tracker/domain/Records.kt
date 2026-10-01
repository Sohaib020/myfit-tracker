package com.myfit.tracker.domain

import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.PrType
import com.myfit.tracker.data.db.SetRow
import com.myfit.tracker.data.db.SetType

/**
 * Personal records, derived purely from logged sets (so they can always be re-checked).
 *
 * Rules:
 *  - Warm-up sets never count.
 *  - Your first session of an exercise sets the baseline; PRs start from the second session.
 *  - At most one PR of each type per session (the best one).
 *  - Estimated 1RM (Epley, 1–12 reps) is always flagged as an estimate.
 */
object Records {

    data class Pr(
        val exerciseId: Long,
        val type: String,
        val value: Double,
        val previous: Double?,          // the record it beat (null = baseline)
        val atWeightKg: Double? = null, // REPS_AT_WEIGHT
        val isEstimate: Boolean = false,
        val workoutId: Long,
        val setId: Long?,               // null for session totals
        val date: String,
        val at: Long,
    )

    fun typesFor(m: String): List<String> = when (m) {
        MeasurementType.WEIGHT_REPS -> listOf(PrType.MAX_WEIGHT, PrType.EST_1RM, PrType.MAX_SET_VOLUME, PrType.MAX_SESSION_VOLUME, PrType.REPS_AT_WEIGHT)
        MeasurementType.WEIGHT_DURATION -> listOf(PrType.MAX_WEIGHT, PrType.MAX_DURATION)
        MeasurementType.BODYWEIGHT_REPS, MeasurementType.REPS_ONLY, MeasurementType.ASSISTED_REPS -> listOf(PrType.MAX_REPS)
        MeasurementType.DURATION -> listOf(PrType.MAX_DURATION)
        MeasurementType.DISTANCE_DURATION -> listOf(PrType.MAX_DISTANCE)
        else -> emptyList()
    }

    fun label(t: String) = when (t) {
        PrType.MAX_WEIGHT -> "Heaviest weight"; PrType.EST_1RM -> "Estimated 1RM"; PrType.MAX_SET_VOLUME -> "Best set volume"
        PrType.MAX_SESSION_VOLUME -> "Best session volume"; PrType.REPS_AT_WEIGHT -> "Most reps at a weight"; PrType.MAX_REPS -> "Most reps"
        PrType.MAX_DURATION -> "Longest set"; PrType.MAX_DISTANCE -> "Longest distance"; else -> t
    }

    private fun working(rows: List<SetRow>) = rows.filter { it.setType != SetType.WARMUP }

    /** Candidate values of one type within one session: (value, set, atWeight). */
    private fun candidates(m: String, type: String, session: List<SetRow>): List<Triple<Double, SetRow?, Double?>> {
        val w = working(session)
        return when (type) {
            PrType.MAX_WEIGHT -> w.filter { (it.weightKg ?: 0.0) > 0 && (m != MeasurementType.WEIGHT_REPS || (it.reps ?: 0) >= 1) }.map { Triple(it.weightKg!!, it, null) }
            PrType.EST_1RM -> w.mapNotNull { s -> WorkoutCalc.estimated1Rm(s.weightKg, s.reps)?.let { Triple(it, s, null) } }
            PrType.MAX_SET_VOLUME -> w.filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.map { Triple(it.weightKg!! * it.reps!!, it, null) }
            PrType.MAX_SESSION_VOLUME -> {
                val v = w.filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.sumOf { it.weightKg!! * it.reps!! }
                if (v > 0) listOf(Triple(v, null, null)) else emptyList()
            }
            PrType.MAX_REPS -> w.filter { (it.reps ?: 0) > 0 }.map { Triple(it.reps!!.toDouble(), it, null) }
            PrType.MAX_DURATION -> w.filter { (it.durationSec ?: 0) > 0 }.map { Triple(it.durationSec!!.toDouble(), it, null) }
            PrType.MAX_DISTANCE -> w.filter { (it.distanceM ?: 0.0) > 0 }.map { Triple(it.distanceM!!, it, null) }
            else -> emptyList()
        }
    }

    /** All PR events for one exercise, oldest first. `rows` = that exercise's sets, any order. */
    fun events(exerciseId: Long, m: String, rows: List<SetRow>): List<Pr> {
        val sessions = rows.groupBy { it.workoutId }.values.sortedBy { it.first().workoutStartedAt }
        if (sessions.size < 2) return emptyList()
        val out = ArrayList<Pr>()
        val best = HashMap<String, Double>()
        val repsAt = HashMap<Double, Int>()     // weight (rounded to 0.01 kg) → best reps
        sessions.forEachIndexed { idx, sess ->
            val first = sess.first()
            for (type in typesFor(m)) {
                if (type == PrType.REPS_AT_WEIGHT) continue
                val c = candidates(m, type, sess).maxByOrNull { it.first } ?: continue
                val prev = best[type]
                if (prev == null || c.first > prev + 1e-9) {
                    if (idx > 0 && prev != null) out += Pr(exerciseId, type, c.first, prev, isEstimate = type == PrType.EST_1RM,
                        workoutId = first.workoutId, setId = c.second?.setId, date = first.workoutLocalDate, at = c.second?.completedAt ?: first.workoutStartedAt)
                    best[type] = c.first
                }
            }
            if (m == MeasurementType.WEIGHT_REPS) {
                // more reps than ever before at a weight you've lifted before (best per session)
                var top: Pr? = null
                working(sess).filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.forEach { s ->
                    val k = Math.round(s.weightKg!! * 100) / 100.0
                    val prev = repsAt[k]
                    if (prev != null && s.reps!! > prev && idx > 0) {
                        val cand = Pr(exerciseId, PrType.REPS_AT_WEIGHT, s.reps.toDouble(), prev.toDouble(), atWeightKg = k,
                            workoutId = first.workoutId, setId = s.setId, date = first.workoutLocalDate, at = s.completedAt)
                        if (top == null || k > (top!!.atWeightKg ?: 0.0)) top = cand
                    }
                }
                working(sess).filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }.forEach { s ->
                    val k = Math.round(s.weightKg!! * 100) / 100.0
                    repsAt[k] = maxOf(repsAt[k] ?: 0, s.reps!!)
                }
                top?.let { out += it }
            }
        }
        return out
    }

    /** Current best of each type (including the first-session baseline). */
    fun current(exerciseId: Long, m: String, rows: List<SetRow>): List<Pr> {
        val sessions = rows.groupBy { it.workoutId }.values.sortedBy { it.first().workoutStartedAt }
        return typesFor(m).filter { it != PrType.REPS_AT_WEIGHT }.mapNotNull { type ->
            var bestPr: Pr? = null
            sessions.forEach { sess ->
                val c = candidates(m, type, sess).maxByOrNull { it.first } ?: return@forEach
                if (bestPr == null || c.first > bestPr!!.value + 1e-9) {
                    val f = sess.first()
                    bestPr = Pr(exerciseId, type, c.first, bestPr?.value, isEstimate = type == PrType.EST_1RM, workoutId = f.workoutId,
                        setId = c.second?.setId, date = f.workoutLocalDate, at = c.second?.completedAt ?: f.workoutStartedAt)
                }
            }
            bestPr
        }
    }

    /** Value formatted for display. */
    fun format(p: Pr, u: UnitPrefs): String = when (p.type) {
        PrType.MAX_WEIGHT, PrType.EST_1RM -> Fmt.weight(p.value, u.weight, 1)
        PrType.MAX_SET_VOLUME, PrType.MAX_SESSION_VOLUME -> Fmt.weight(p.value, u.weight, 0)
        PrType.REPS_AT_WEIGHT -> "${Fmt.int(p.value)} reps @ ${Fmt.weight(p.atWeightKg ?: 0.0, u.weight, 1)}"
        PrType.MAX_REPS -> "${Fmt.int(p.value)} reps"
        PrType.MAX_DURATION -> { val s = p.value.toLong(); if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
        PrType.MAX_DISTANCE -> Fmt.distance(p.value, u.distance)
        else -> Fmt.trim(p.value, 1)
    }

    /** "+2.5 kg", "+2 reps", … vs the record it beat. */
    fun delta(p: Pr, u: UnitPrefs): String? {
        val prev = p.previous ?: return null
        val d = p.value - prev
        return when (p.type) {
            PrType.MAX_WEIGHT, PrType.EST_1RM, PrType.MAX_SET_VOLUME, PrType.MAX_SESSION_VOLUME -> "+" + Fmt.weight(d, u.weight, 1)
            PrType.REPS_AT_WEIGHT, PrType.MAX_REPS -> "+${Fmt.int(d)} reps"
            PrType.MAX_DURATION -> "+${d.toLong()} s"
            PrType.MAX_DISTANCE -> "+" + Fmt.distance(d, u.distance)
            else -> null
        }
    }
}
