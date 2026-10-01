package com.myfit.tracker

import com.myfit.tracker.data.db.ActivityLevel
import com.myfit.tracker.data.db.Experience
import com.myfit.tracker.data.db.Sex
import com.myfit.tracker.data.db.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Debug builds only: lets the CI emulator test skip onboarding and open the dashboard directly. */
object SmokeSetup {
    fun ensureProfile(c: AppContainer) = runBlocking {
        if (c.profileRepo.profile.first() != null) return@runBlocking
        c.profileRepo.createProfile(
            UserProfile(
                name = "Test", age = 28, ageRecordedOn = java.time.LocalDate.now().toString(), sex = Sex.MALE,
                heightCm = 178.0, startWeightKg = 75.0, targetWeightKg = 72.0, activityLevel = ActivityLevel.MODERATE,
                experience = Experience.INTERMEDIATE, goals = "Build Muscle", workoutDaysMask = 0b0011111,
                workoutTimeMin = 18 * 60, wakeTimeMin = 7 * 60, sleepTimeMin = 23 * 60, createdAt = 0, updatedAt = 0,
            ),
            emptyMap(),
        )
    }
}
