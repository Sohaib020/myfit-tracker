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

/**
 * Debug builds only: realistic demo data for the website screenshots (CI emulator) — 3 months of weigh-ins,
 * today's desi meals and water, starter workout days and a few finished workouts. Runs once.
 */
object DemoData {
    fun seed(c: AppContainer) = runBlocking {
        val prefs = c.app.getSharedPreferences("demo", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return@runBlocking
        if (c.profileRepo.profile.first() == null) c.profileRepo.createProfile(
            UserProfile(
                name = "Ali", age = 29, ageRecordedOn = java.time.LocalDate.now().toString(), sex = Sex.MALE,
                heightCm = 175.0, startWeightKg = 84.0, targetWeightKg = 76.0, activityLevel = ActivityLevel.MODERATE,
                experience = Experience.INTERMEDIATE, goals = "Lose Fat,Build Muscle", workoutDaysMask = 0b0010101,
                workoutTimeMin = 18 * 60, wakeTimeMin = 7 * 60, sleepTimeMin = 23 * 60, createdAt = 0, updatedAt = 0,
            ),
            mapOf(
                com.myfit.tracker.data.db.TargetType.CALORIES to 2100.0, com.myfit.tracker.data.db.TargetType.PROTEIN_G to 140.0,
                com.myfit.tracker.data.db.TargetType.WATER_ML to 2500.0, com.myfit.tracker.data.db.TargetType.STEPS to 9000.0,
                com.myfit.tracker.data.db.TargetType.FIBER_G to 30.0, com.myfit.tracker.data.db.TargetType.CARBS_G to 220.0, com.myfit.tracker.data.db.TargetType.FAT_G to 70.0,
            ),
        )
        c.settings.setTourDone(true)
        c.settings.setMuslim("yes")
        c.nutritionRepo.seedIfNeeded()
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        val rnd = java.util.Random(7)
        for (i in 90 downTo 0) {
            if (rnd.nextInt(10) < 2 && i != 0) continue
            val kg = 84.0 - (90 - i) * 0.062 + (rnd.nextGaussian() * 0.35)
            c.logRepo.addWeight(Math.round(kg * 10) / 10.0, null, "", now - i * day - 3 * 3_600_000L)
        }
        repeat(6) { c.logRepo.addWater(250.0, now - it * 1_800_000L) }
        val dao = c.db.nutritionDao()
        suspend fun line(id: String, q: Double): com.myfit.tracker.data.repo.LogLine? {
            val f = dao.byUuids(listOf("pkfood:$id")).first().firstOrNull() ?: return null
            return com.myfit.tracker.data.repo.LogLine(f.name, q, f.servingSize, f.servingUnit, f.calories, f.proteinG, f.carbsG, f.fatG, f.fiberG, f.source, f.id)
        }
        val today = com.myfit.tracker.domain.Clock.today()
        c.nutritionRepo.log(today, "BREAKFAST", listOfNotNull(line("omelette", 1.0), line("paratha_plain", 1.0), line("tea_sugar", 1.0)), now - 6 * 3_600_000L)
        c.nutritionRepo.log(today, "LUNCH", listOfNotNull(line("chicken_karahi", 0.75), line("roti", 2.0), line("salad", 1.0), line("raita", 1.0)), now - 2 * 3_600_000L)
        c.nutritionRepo.log(today, "SNACK", listOfNotNull(line("guava", 1.0), line("lassi_salty", 1.0)), now - 3_600_000L)
        c.workoutRepo.createStarterTemplates()
        val tpl = c.workoutRepo.templates.first()
        tpl.take(3).forEachIndexed { k, t ->
            val ago = listOf(9, 5, 2)[k]
            val id = c.workoutRepo.startFromTemplate(t.template.id)
            val v = c.workoutRepo.workoutView(id).first()
            v?.exercises?.forEach { e -> repeat(3) { s -> c.workoutRepo.completeSet(e.we.id, "WORKING", 20.0 + 5 * s + 2.5 * k, 10 - s, null, null, null, "") } }
            c.workoutRepo.finish(id, t.template.name, "")
            c.workoutRepo.updateWorkoutMeta(id, t.template.name, "", now - ago * day - 2 * 3_600_000L, now - ago * day - 3_600_000L)
        }
        prefs.edit().putBoolean("seeded", true).apply()
    }
}
