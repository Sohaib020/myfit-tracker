package com.myfit.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepsSourceTest {
    @Test fun healthConnectWinsAndSourcesAreNeverAdded() {
        assertEquals(StepsSource.Pick(8000, StepsSource.Source.HEALTH_CONNECT), StepsSource.pick(8000, 5000, 7000))
        assertEquals(StepsSource.Pick(5000, StepsSource.Source.MANUAL), StepsSource.pick(null, 5000, 7000))
        assertEquals(StepsSource.Pick(7000, StepsSource.Source.PHONE), StepsSource.pick(null, null, 7000))
        assertNull(StepsSource.pick(null, null, null))
    }

    @Test fun phoneDeltasSurviveReboot() {
        val d = PhoneStepCalc.daily(listOf(
            PhoneStepCalc.Snap(1, 10_000, "d1"),
            PhoneStepCalc.Snap(2, 12_500, "d1"),   // +2500
            PhoneStepCalc.Snap(3, 300, "d1"),      // reboot: +300
            PhoneStepCalc.Snap(4, 1_300, "d2"),    // +1000 credited to d2
        ))
        assertEquals(2800L, d["d1"]); assertEquals(1000L, d["d2"])
    }

    @Test fun singleSnapshotGivesNoInventedSteps() {
        assertEquals(emptyMap<String, Long>(), PhoneStepCalc.daily(listOf(PhoneStepCalc.Snap(1, 5000, "d1"))))
    }
}
