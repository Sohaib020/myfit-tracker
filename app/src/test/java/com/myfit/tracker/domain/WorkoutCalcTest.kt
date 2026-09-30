package com.myfit.tracker.domain

import com.myfit.tracker.data.db.MeasurementType
import com.myfit.tracker.data.db.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutCalcTest {
    private fun s(w: Double?, r: Int?, type: String = SetType.WORKING, m: String = MeasurementType.WEIGHT_REPS, ex: Long = 1, rest: Long? = null) =
        WorkoutCalc.SetData(ex, m, type, w, r, null, null, rest)

    @Test fun volumeIsWeightTimesRepsSummed() {
        val t = WorkoutCalc.totals(listOf(s(70.0, 10), s(70.0, 9), s(65.0, 10)))
        assertEquals(700.0 + 630.0 + 650.0, t.volumeKg!!, 1e-9)
        assertEquals(3, t.sets); assertEquals(29, t.reps)
    }

    @Test fun warmupsExcludedFromVolumeButCountedAsSets() {
        val t = WorkoutCalc.totals(listOf(s(40.0, 10, SetType.WARMUP), s(70.0, 8)))
        assertEquals(560.0, t.volumeKg!!, 1e-9)
        assertEquals(2, t.sets); assertEquals(18, t.reps)
    }

    @Test fun bodyweightAndDurationHaveNoVolume() {
        val t = WorkoutCalc.totals(listOf(s(null, 12, m = MeasurementType.BODYWEIGHT_REPS), s(null, null, m = MeasurementType.DURATION)))
        assertNull(t.volumeKg)
    }

    @Test fun editingASetChangesTheRecalculatedTotal() {
        val before = WorkoutCalc.totals(listOf(s(70.0, 18)))
        val after = WorkoutCalc.totals(listOf(s(70.0, 8)))
        assertEquals(1260.0, before.volumeKg!!, 1e-9)
        assertEquals(560.0, after.volumeKg!!, 1e-9)
    }

    @Test fun averageRestIgnoresMissing() {
        val t = WorkoutCalc.totals(listOf(s(70.0, 8, rest = 90), s(70.0, 8, rest = null), s(70.0, 8, rest = 120)))
        assertEquals(105.0, t.avgRestSec!!, 1e-9)
    }

    @Test fun estimated1RmOnlyForSensibleReps() {
        assertEquals(100.0, WorkoutCalc.estimated1Rm(100.0, 1)!!, 1e-9)
        assertEquals(80.0 * (1 + 6 / 30.0), WorkoutCalc.estimated1Rm(80.0, 6)!!, 1e-9)
        assertNull(WorkoutCalc.estimated1Rm(60.0, 20))
        assertNull(WorkoutCalc.estimated1Rm(null, 5))
    }

    @Test fun prefillPrefersTodayThenLastSessionThenTarget() {
        val v = { w: Double, r: Int -> Prefill.Values(w, r, null, null) }
        val prev = listOf(v(70.0, 10), v(70.0, 9), v(65.0, 10))
        assertEquals(v(70.0, 10), Prefill.next(emptyList(), prev, v(60.0, 8)))
        assertEquals(v(72.5, 8), Prefill.next(listOf(v(72.5, 8)), prev, null))
        assertEquals(v(60.0, 8), Prefill.next(emptyList(), emptyList(), v(60.0, 8)))
        assertEquals(Prefill.Values(null, null, null, null), Prefill.next(emptyList(), emptyList(), null))
    }
}
