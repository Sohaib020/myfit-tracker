package com.myfit.tracker.domain

import com.myfit.tracker.data.db.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R35: rules behind Plan my day (DayPlans.build / score). */
class DayPlanTest {
    private val monday = LocalDate.of(2026, 10, 19)          // a Monday
    private val t = DayTargets(steps = 8000.0, sleepMin = 480.0, waterMl = 2500.0, kcal = 2200.0, protein = 120.0, weeklyWorkouts = 3.0)
    private fun profile(mask: Int) = UserProfile(
        name = "Test", age = 30, ageRecordedOn = "2026-01-01", sex = "MALE", heightCm = 178.0, startWeightKg = 80.0, targetWeightKg = 75.0,
        activityLevel = "MODERATE", experience = "BEGINNER", goals = "", workoutDaysMask = mask, workoutTimeMin = null,
        wakeTimeMin = 7 * 60, sleepTimeMin = 23 * 60, createdAt = 0, updatedAt = 0,
    )
    private fun item(p: DayPlan, m: PlanMetric) = p.items.first { it.metric == m }

    @Test fun noYesterdayUsesGoals() {
        val p = DayPlans.build(monday, null, emptyList(), t, profile(0), null, 0)
        assertEquals("Let's build a great day", p.headline)
        assertEquals(8000.0, item(p, PlanMetric.STEPS).target!!, 0.0)
        assertEquals(2500.0, item(p, PlanMetric.WATER).target!!, 0.0)
    }

    @Test fun trainingDayAfterShortNightIsLighterAndAddsWater() {
        val y = DayStats(monday.minusDays(1), steps = 9000, sleepMin = 330.0)
        val p = DayPlans.build(monday, y, listOf(y), t, profile(1), "Push A", 1)   // bit 0 = Monday
        assertTrue(item(p, PlanMetric.WORKOUT).title.startsWith("Lighter"))
        assertEquals(3000.0, item(p, PlanMetric.WATER).target!!, 0.0)
    }

    @Test fun lowStepsBuildBackGradually() {
        val y = DayStats(monday.minusDays(1), steps = 2000)
        val p = DayPlans.build(monday, y, listOf(y), t, profile(0), null, 0)
        // max(2000 × 1.25, 8000 × 0.7) = 5600 → nearest 500
        assertEquals(5500.0, item(p, PlanMetric.STEPS).target!!, 0.0)
    }

    @Test fun hardSessionYesterdayMeansActiveRecovery() {
        val y = DayStats(monday.minusDays(1), workouts = 1, trainMin = 75)
        val p = DayPlans.build(monday, y, listOf(y), t, profile(0), null, 3)
        assertEquals("Active recovery", p.items.first { it.kind == PlanKind.MOVE }.title)
    }

    @Test fun weighInOnlyWhenOverdue() {
        val recent = DayStats(monday.minusDays(1), weighed = true)
        assertTrue(DayPlans.build(monday, recent, listOf(recent), t, profile(0), null, 0).items.none { it.metric == PlanMetric.WEIGH })
        val old = DayStats(monday.minusDays(6), weighed = true)
        assertTrue(DayPlans.build(monday, null, listOf(old), t, profile(0), null, 0).items.any { it.metric == PlanMetric.WEIGH })
    }

    @Test fun scoreAveragesOnlyRecordedGoals() {
        assertNull(DayPlans.score(DayStats(monday), t))
        val s = DayStats(monday, steps = 4000, sleepMin = 480.0)       // 0.5 and 1.0
        assertEquals(75, DayPlans.score(s, t))
        val perfect = DayStats(monday, steps = 9000, sleepMin = 500.0, waterMl = 2600.0, kcal = 2200.0, protein = 130.0, workouts = 1)
        assertEquals(100, DayPlans.score(perfect, t))
    }

    @Test fun caloriesScoreBothWays() {
        val over = DayStats(monday, kcal = 2640.0)    // 20 % over → 0.8
        val under = DayStats(monday, kcal = 1760.0)   // 20 % under → 0.8
        assertEquals(DayPlans.score(over, t), DayPlans.score(under, t))
        assertEquals(80, DayPlans.score(over, t))
    }
}
