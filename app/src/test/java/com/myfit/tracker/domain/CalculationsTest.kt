package com.myfit.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalculationsTest {
    private val d = LocalDate.of(2026, 9, 30)

    @Test fun multipleWeighInsBecomeOneDailyMean() {
        val m = Stats.dailyMeans(listOf(d to 75.2, d to 75.8, d.minusDays(1) to 76.0))
        assertEquals(75.5, m[d]!!, 1e-9)
        assertEquals(76.0, m[d.minusDays(1)]!!, 1e-9)
    }

    @Test fun missingDaysAreExcludedNotZero() {
        val daily = mapOf(d to 75.0, d.minusDays(3) to 77.0)
        val w = Stats.windowAverage(daily, d, 7)
        assertEquals(76.0, w.value!!, 1e-9)
        assertEquals(2, w.daysWithData)
        assertEquals(7, w.daysInWindow)
        assertTrue(!w.isComplete)
    }

    @Test fun emptyWindowIsNullNotZero() {
        assertNull(Stats.windowAverage(emptyMap(), d, 7).value)
    }

    @Test fun changeRequiresDataInBothWindows() {
        val daily = (0..6).associate { d.minusDays(it.toLong()) to 75.0 }
        assertNull(Stats.windowChange(daily, d, 7, 7, 3))
        val both = daily + (7..13).associate { d.minusDays(it.toLong()) to 76.0 }
        assertEquals(-1.0, Stats.windowChange(both, d, 7, 7, 3)!!.delta, 1e-9)
    }

    @Test fun unitRoundTripDoesNotDrift() {
        val kg = 75.2
        assertEquals(kg, Units.toKg(Units.kgTo(kg, WeightUnit.LB), WeightUnit.LB), 1e-12)
        assertEquals(180.0, Units.toCm(Units.cmTo(180.0, LengthUnit.IN), LengthUnit.IN), 1e-12)
        assertEquals(1500.0, Units.toMl(Units.mlTo(1500.0, VolumeUnit.FL_OZ), VolumeUnit.FL_OZ), 1e-9)
    }

    @Test fun targetsUseTheValueEffectiveOnThatDay() {
        val rows = listOf(
            Targets.Row("WATER_ML", 3000.0, d.minusDays(10), 1),
            Targets.Row("WATER_ML", 3500.0, d.minusDays(2), 2),
        )
        assertEquals(3000.0, Targets.on(rows, "WATER_ML", d.minusDays(5))!!, 0.0)
        assertEquals(3500.0, Targets.on(rows, "WATER_ML", d)!!, 0.0)
        assertNull(Targets.on(rows, "WATER_ML", d.minusDays(20)))
    }

    @Test fun stepsDayTotalReplacesAndIncrementsAdd() {
        val e = listOf(
            StepsCalc.Entry(3000, true, 1, 1),
            StepsCalc.Entry(500, false, 2, 2),
            StepsCalc.Entry(6000, true, 3, 3),
            StepsCalc.Entry(1000, false, 4, 4),
        )
        assertEquals(7000, StepsCalc.dayTotal(e))
        assertNull(StepsCalc.dayTotal(emptyList()))
    }

    @Test fun sleepDurationAcrossMidnight() {
        val start = 1_000_000_000_000L
        assertEquals(432L, SleepCalc.minutes(start, start + 432 * 60_000L))
        assertNull(SleepCalc.minutes(start, start - 1))
    }

    @Test fun displayRoundingOnlyAtTheEnd() {
        assertEquals("75.3", Fmt.trim(75.25, 1))
        assertEquals("75", Fmt.trim(75.0, 1))
        assertEquals("2.25 L", Fmt.volume(2250.0, VolumeUnit.L))
        assertEquals("−0.4", Fmt.signed(-0.4))
    }
}
