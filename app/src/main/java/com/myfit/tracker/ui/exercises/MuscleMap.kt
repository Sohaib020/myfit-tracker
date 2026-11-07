// ------------------------------------------------------------------ R36 Target muscles

/**
 * Coarse filter group → anatomy view + highlighted muscles for its thumbnail.
 * Updated to include Cardio with anatomical diagrams showing full-body engaged muscles
 */
private val TARGET_ART: List<Triple<String, String, List<String>>> = listOf(
    Triple(MuscleGroup.CHEST, "front", listOf("chest")),
    Triple(MuscleGroup.BACK, "back", listOf("lats", "middle back", "traps")),
    Triple(MuscleGroup.SHOULDERS, "front", listOf("shoulders")),
    Triple(MuscleGroup.BICEPS, "front", listOf("biceps")),
    Triple(MuscleGroup.TRICEPS, "back", listOf("triceps")),
    Triple(MuscleGroup.CORE, "front", listOf("abdominals")),
    Triple(MuscleGroup.GLUTES, "back", listOf("glutes")),
    Triple(MuscleGroup.LEGS, "front", listOf("quadriceps", "calves")),
    // NEW: Cardio muscle groups - shows full body with highlighted cardio-engaged muscles
    Triple(MuscleGroup.CARDIO, "cardio_front", listOf("quadriceps", "calves")),
)