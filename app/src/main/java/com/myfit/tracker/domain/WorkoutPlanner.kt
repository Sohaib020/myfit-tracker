package com.myfit.tracker.domain

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * MyFit's workout planner — the "built-in" half of the hybrid AI. It builds a training day (or a whole multi-week
 * plan) instantly and offline from a curated exercise library, using the standard prescriptions for each goal
 * (sets × reps × rest), the time you have, your equipment and your level. The online AI can then adjust a day on
 * request ("no machines", "more biceps"…) — see WorkoutAi.
 *
 * Everything is expressed in exercise-catalog keys (assets/exercise_catalog.json "k"), the same keys the bundled
 * Programs use, so a generated plan plugs straight into the Programs engine, Gym Mode, history and PRs.
 */
object WorkoutPlanner {

    enum class Role { MAIN, SECONDARY, ISOLATION, DURATION }
    enum class Equip(val label: String, val programTag: String) { GYM("Full gym", "gym"), DUMBBELLS("Dumbbells only", "dumbbells"), BODYWEIGHT("No equipment", "bodyweight") }
    enum class Level(val label: String) { BEGINNER("Beginner"), INTERMEDIATE("Intermediate"), ADVANCED("Advanced") }

    /** What people come to a gym app for. */
    enum class Goal(val id: String, val label: String, val blurb: String) {
        MUSCLE("muscle", "Build muscle", "Bigger, fuller muscles — classic hypertrophy"),
        STRENGTH("strength", "Get stronger", "Lift heavier on the big lifts"),
        FAT_LOSS("fatloss", "Lose fat", "Burn more, keep your muscle"),
        GAIN("gain", "Gain weight", "Lean bulk for skinny builds"),
        RECOMP("recomp", "Lose fat & build muscle", "Recomposition: leaner and more defined"),
        TONE("tone", "Tone & shape", "Firm, defined and athletic"),
        ENDURANCE("endurance", "Stamina & endurance", "Go longer without gassing out"),
        GENERAL("health", "Stay fit & healthy", "Balanced strength for everyday life"),
        ATHLETIC("athletic", "Sports performance", "Power, speed and resilience"),
        START("start", "Start from zero", "Gentle first steps into training"),
    }

    /** Muscle groups you can pick for a day. */
    enum class Muscle(val label: String, val weight: Double) {
        CHEST("Chest", 1.0), BACK("Back", 1.0), SHOULDERS("Shoulders", 0.8), BICEPS("Biceps", 0.6), TRICEPS("Triceps", 0.6),
        QUADS("Quads", 1.0), HAMSTRINGS("Hamstrings", 0.8), GLUTES("Glutes", 0.8), CALVES("Calves", 0.45), CORE("Core", 0.5),
        TRAPS("Traps", 0.3), CARDIO("Cardio", 0.0),
    }

    /** What the user taps: some are groups of muscles. */
    enum class Target(val label: String, val muscles: List<Muscle>) {
        CHEST("Chest", listOf(Muscle.CHEST)), BACK("Back", listOf(Muscle.BACK)), SHOULDERS("Shoulders", listOf(Muscle.SHOULDERS)),
        BICEPS("Biceps", listOf(Muscle.BICEPS)), TRICEPS("Triceps", listOf(Muscle.TRICEPS)), ARMS("Arms", listOf(Muscle.BICEPS, Muscle.TRICEPS)),
        LEGS("Legs", listOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.CALVES)), GLUTES("Glutes", listOf(Muscle.GLUTES)),
        CORE("Abs & core", listOf(Muscle.CORE)), FULL("Full body", listOf(Muscle.QUADS, Muscle.CHEST, Muscle.BACK, Muscle.SHOULDERS, Muscle.HAMSTRINGS, Muscle.CORE)),
        CARDIO("Cardio", listOf(Muscle.CARDIO)),
    }

    data class P(val key: String, val role: Role)

    /** One planned exercise: catalog key + prescription. reps: "8-12" | "10" | "45s" | "10m" | "max". */
    data class Item(val key: String, val sets: Int, val reps: String, val rest: Int, val main: Boolean, val muscle: Muscle)

    data class Day(val name: String, val focus: String, val items: List<Item>) {
        /** Rough length in minutes: ~45 s per set + rest. */
        val minutes: Int get() = items.sumOf { i -> i.sets * (setSeconds(i.reps) + i.rest) }.let { (it / 60.0).roundToInt() }
    }

    private fun setSeconds(reps: String): Int = when {
        reps.endsWith("m") -> (reps.dropLast(1).toIntOrNull() ?: 10) * 60
        reps.endsWith("s") -> reps.dropLast(1).toIntOrNull() ?: 40
        else -> 45
    }

    val LIB: Map<Muscle, Map<Equip, List<P>>> = mapOf(
    Muscle.CHEST to mapOf(
        Equip.GYM to listOf(P("Barbell_Bench_Press_-_Medium_Grip", Role.MAIN), P("Incline_Dumbbell_Press", Role.SECONDARY), P("Barbell_Incline_Bench_Press_-_Medium_Grip", Role.SECONDARY), P("Dips_-_Chest_Version", Role.SECONDARY), P("Cable_Crossover", Role.ISOLATION), P("Butterfly", Role.ISOLATION), P("Incline_Dumbbell_Flyes", Role.ISOLATION), P("Decline_Barbell_Bench_Press", Role.SECONDARY)),
        Equip.DUMBBELLS to listOf(P("Dumbbell_Bench_Press", Role.MAIN), P("Incline_Dumbbell_Press", Role.SECONDARY), P("Dumbbell_Flyes", Role.ISOLATION), P("Incline_Dumbbell_Flyes", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Pushups", Role.MAIN), P("Push-Up_Wide", Role.SECONDARY), P("Incline_Push-Up", Role.SECONDARY), P("Decline_Push-Up", Role.SECONDARY)),
    ),
    Muscle.BACK to mapOf(
        Equip.GYM to listOf(P("Pullups", Role.MAIN), P("Bent_Over_Barbell_Row", Role.MAIN), P("Wide-Grip_Lat_Pulldown", Role.SECONDARY), P("Seated_Cable_Rows", Role.SECONDARY), P("T-Bar_Row_with_Handle", Role.SECONDARY), P("One_Arm_Lat_Pulldown", Role.SECONDARY), P("Straight-Arm_Pulldown", Role.ISOLATION), P("Face_Pull", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Bent_Over_Two-Dumbbell_Row", Role.MAIN), P("One-Arm_Dumbbell_Row", Role.SECONDARY), P("Dumbbell_Incline_Row", Role.SECONDARY), P("Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Inverted_Row", Role.MAIN), P("Pullups", Role.SECONDARY), P("Superman", Role.ISOLATION)),
    ),
    Muscle.SHOULDERS to mapOf(
        Equip.GYM to listOf(P("Standing_Military_Press", Role.MAIN), P("Seated_Barbell_Military_Press", Role.SECONDARY), P("Arnold_Dumbbell_Press", Role.SECONDARY), P("Side_Lateral_Raise", Role.ISOLATION), P("Cable_Seated_Lateral_Raise", Role.ISOLATION), P("Reverse_Flyes", Role.ISOLATION), P("Upright_Barbell_Row", Role.ISOLATION), P("Face_Pull", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Seated_Dumbbell_Press", Role.MAIN), P("Arnold_Dumbbell_Press", Role.SECONDARY), P("Side_Lateral_Raise", Role.ISOLATION), P("Bent_Over_Dumbbell_Rear_Delt_Raise_With_Head_On_Bench", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Push-Up_Wide", Role.MAIN), P("Handstand_Push-Ups", Role.SECONDARY)),
    ),
    Muscle.BICEPS to mapOf(
        Equip.GYM to listOf(P("Barbell_Curl", Role.MAIN), P("EZ-Bar_Curl", Role.SECONDARY), P("Preacher_Curl", Role.ISOLATION), P("Incline_Dumbbell_Curl", Role.ISOLATION), P("Hammer_Curls", Role.ISOLATION), P("Cable_Hammer_Curls_-_Rope_Attachment", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Dumbbell_Bicep_Curl", Role.MAIN), P("Alternate_Hammer_Curl", Role.ISOLATION), P("Incline_Dumbbell_Curl", Role.ISOLATION), P("Concentration_Curls", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Chin-Up", Role.MAIN), P("Inverted_Row", Role.SECONDARY)),
    ),
    Muscle.TRICEPS to mapOf(
        Equip.GYM to listOf(P("Close-Grip_Barbell_Bench_Press", Role.MAIN), P("Triceps_Pushdown_-_Rope_Attachment", Role.ISOLATION), P("EZ-Bar_Skullcrusher", Role.ISOLATION), P("Cable_Rope_Overhead_Triceps_Extension", Role.ISOLATION), P("Lying_Triceps_Press", Role.ISOLATION), P("Triceps_Pushdown", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Standing_Dumbbell_Triceps_Extension", Role.MAIN), P("Tricep_Dumbbell_Kickback", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Bench_Dips", Role.MAIN), P("Close-Grip_Push-Up_off_of_a_Dumbbell", Role.SECONDARY)),
    ),
    Muscle.QUADS to mapOf(
        Equip.GYM to listOf(P("Barbell_Squat", Role.MAIN), P("Leg_Press", Role.SECONDARY), P("Hack_Squat", Role.SECONDARY), P("Front_Barbell_Squat", Role.SECONDARY), P("Barbell_Walking_Lunge", Role.SECONDARY), P("Leg_Extensions", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Goblet_Squat", Role.MAIN), P("Split_Squat_with_Dumbbells", Role.SECONDARY), P("Dumbbell_Lunges", Role.SECONDARY), P("Dumbbell_Step_Ups", Role.SECONDARY), P("Dumbbell_Squat", Role.SECONDARY)),
        Equip.BODYWEIGHT to listOf(P("Bodyweight_Squat", Role.MAIN), P("Split_Squats", Role.SECONDARY), P("Bodyweight_Walking_Lunge", Role.SECONDARY)),
    ),
    Muscle.HAMSTRINGS to mapOf(
        Equip.GYM to listOf(P("Romanian_Deadlift", Role.MAIN), P("Lying_Leg_Curls", Role.ISOLATION), P("Seated_Leg_Curl", Role.ISOLATION), P("Good_Morning", Role.SECONDARY), P("Glute_Ham_Raise", Role.SECONDARY)),
        Equip.DUMBBELLS to listOf(P("Stiff-Legged_Dumbbell_Deadlift", Role.MAIN)),
        Equip.BODYWEIGHT to listOf(P("Single_Leg_Glute_Bridge", Role.SECONDARY)),
    ),
    Muscle.GLUTES to mapOf(
        Equip.GYM to listOf(P("Barbell_Hip_Thrust", Role.MAIN), P("Sumo_Deadlift", Role.SECONDARY), P("Glute_Kickback", Role.ISOLATION), P("Single_Leg_Glute_Bridge", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Split_Squat_with_Dumbbells", Role.SECONDARY), P("Plie_Dumbbell_Squat", Role.SECONDARY), P("Single_Leg_Glute_Bridge", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Butt_Lift_Bridge", Role.MAIN), P("Single_Leg_Glute_Bridge", Role.ISOLATION)),
    ),
    Muscle.CALVES to mapOf(
        Equip.GYM to listOf(P("Standing_Calf_Raises", Role.ISOLATION), P("Seated_Calf_Raise", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Standing_Calf_Raises", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Standing_Calf_Raises", Role.ISOLATION)),
    ),
    Muscle.CORE to mapOf(
        Equip.GYM to listOf(P("Hanging_Leg_Raise", Role.ISOLATION), P("Cable_Crunch", Role.ISOLATION), P("Plank", Role.DURATION), P("Russian_Twist", Role.ISOLATION), P("Side_Bridge", Role.DURATION), P("Hyperextensions_Back_Extensions", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Russian_Twist", Role.ISOLATION), P("Plank", Role.DURATION), P("Crunches", Role.ISOLATION), P("Dead_Bug", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(P("Crunches", Role.ISOLATION), P("Reverse_Crunch", Role.ISOLATION), P("Plank", Role.DURATION), P("Dead_Bug", Role.ISOLATION), P("Mountain_Climbers", Role.DURATION), P("Side_Bridge", Role.DURATION)),
    ),
    Muscle.TRAPS to mapOf(
        Equip.GYM to listOf(P("Barbell_Shrug", Role.ISOLATION)),
        Equip.DUMBBELLS to listOf(P("Dumbbell_Shrug", Role.ISOLATION)),
        Equip.BODYWEIGHT to listOf(),
    ),
    Muscle.CARDIO to mapOf(
        Equip.GYM to listOf(P("Rowing_Stationary", Role.DURATION), P("Bicycling_Stationary", Role.DURATION), P("Walking_Treadmill", Role.DURATION), P("Stairmaster", Role.DURATION)),
        Equip.DUMBBELLS to listOf(P("Star_Jump", Role.DURATION), P("Mountain_Climbers", Role.DURATION)),
        Equip.BODYWEIGHT to listOf(P("Star_Jump", Role.DURATION), P("Mountain_Climbers", Role.DURATION), P("Air_Bike", Role.DURATION)),
    ),
    )

    /** Hard / technical lifts beginners get an easier alternative for. */
    private val ADVANCED_ONLY = setOf("Barbell_Deadlift", "Front_Barbell_Squat", "Good_Morning", "Glute_Ham_Raise", "Handstand_Push-Ups", "Sumo_Deadlift", "Standing_Military_Press")

    /** Prescription for a goal and role: sets, reps, rest (s). */
    fun prescribe(goal: Goal, role: Role, level: Level): Triple<Int, String, Int> {
        val (s, r, rest) = when (role) {
            Role.DURATION -> when (goal) {
                Goal.FAT_LOSS, Goal.ENDURANCE, Goal.ATHLETIC -> Triple(3, "40s", 30)
                else -> Triple(3, "40s", 45)
            }
            Role.MAIN -> when (goal) {
                Goal.STRENGTH -> Triple(5, "3-5", 180)
                Goal.MUSCLE, Goal.GAIN -> Triple(4, "6-10", 120)
                Goal.RECOMP -> Triple(4, "6-8", 120)
                Goal.ATHLETIC -> Triple(4, "4-6", 150)
                Goal.FAT_LOSS -> Triple(3, "10-12", 75)
                Goal.TONE -> Triple(3, "10-12", 60)
                Goal.ENDURANCE -> Triple(3, "15-20", 45)
                Goal.GENERAL -> Triple(3, "8-12", 90)
                Goal.START -> Triple(2, "10-12", 75)
            }
            Role.SECONDARY -> when (goal) {
                Goal.STRENGTH -> Triple(3, "6-8", 120)
                Goal.MUSCLE, Goal.GAIN -> Triple(3, "8-12", 90)
                Goal.RECOMP -> Triple(3, "8-12", 75)
                Goal.ATHLETIC -> Triple(3, "6-8", 90)
                Goal.FAT_LOSS -> Triple(3, "12-15", 45)
                Goal.TONE -> Triple(3, "12-15", 45)
                Goal.ENDURANCE -> Triple(3, "15-20", 30)
                Goal.GENERAL -> Triple(3, "10-12", 75)
                Goal.START -> Triple(2, "10-12", 60)
            }
            Role.ISOLATION -> when (goal) {
                Goal.STRENGTH -> Triple(2, "8-12", 60)
                Goal.MUSCLE, Goal.GAIN, Goal.RECOMP -> Triple(3, "12-15", 60)
                Goal.FAT_LOSS, Goal.TONE -> Triple(3, "15", 40)
                Goal.ENDURANCE -> Triple(2, "20", 30)
                Goal.ATHLETIC -> Triple(2, "10-12", 60)
                Goal.GENERAL -> Triple(3, "12", 60)
                Goal.START -> Triple(2, "12", 60)
            }
        }
        val sets = when (level) { Level.BEGINNER -> (s - 1).coerceAtLeast(2); Level.ADVANCED -> if (role == Role.MAIN) s + 1 else s; else -> s }
        return Triple(sets, r, rest)
    }

    private fun candidates(m: Muscle, equip: Equip, level: Level): List<P> {
        val order = when (equip) { Equip.GYM -> listOf(Equip.GYM, Equip.DUMBBELLS, Equip.BODYWEIGHT); Equip.DUMBBELLS -> listOf(Equip.DUMBBELLS, Equip.BODYWEIGHT); Equip.BODYWEIGHT -> listOf(Equip.BODYWEIGHT) }
        val all = order.flatMap { LIB[m]?.get(it).orEmpty() }.distinctBy { it.key }
        val byLevel = if (level == Level.BEGINNER) all.filter { it.key !in ADVANCED_ONLY } else all
        // health profile (knee pain, pregnancy, back pain…): planned days skip exercises that don't suit you
        val safe = byLevel.filter { HealthProfile.judgeExercise(ExerciseTags.of(it.key.replace('_', ' '))).verdict != HealthProfile.Verdict.AVOID }
        return safe.ifEmpty { byLevel }
    }

    /** Other exercises that train the same muscle as [key] with this equipment (for the "Swap" button). */
    fun alternatives(key: String, equip: Equip, level: Level, exclude: Set<String> = emptySet()): List<String> {
        val m = muscleOf(key) ?: return emptyList()
        return candidates(m, equip, level).map { it.key }.filter { it != key && it !in exclude }
    }

    /** Every exercise key the planner may use for these targets (the AI is restricted to this list). */
    fun allowedKeys(targets: List<Target>, equip: Equip, level: Level): List<Pair<String, Muscle>> =
        targets.flatMap { it.muscles }.distinct().flatMap { m -> candidates(m, equip, level).map { it.key to m } }.distinctBy { it.first }

    private val MUSCLE_OF: Map<String, Muscle> by lazy {
        val out = HashMap<String, Muscle>()
        LIB.forEach { (m, byEq) -> byEq.values.forEach { l -> l.forEach { out.putIfAbsent(it.key, m) } } }
        out
    }
    fun muscleOf(key: String): Muscle? = MUSCLE_OF[key]
    fun isMain(key: String): Boolean = LIB.values.any { e -> e.values.any { l -> l.any { it.key == key && it.role == Role.MAIN } } }

    /** "chest and biceps", "legs + abs", "push day" → targets. Empty when nothing matched. */
    fun targetsFromText(text: String): List<Target> {
        val t = text.lowercase()
        val out = LinkedHashSet<Target>()
        fun has(vararg w: String) = w.any { Regex("\\b$it").containsMatchIn(t) }
        if (has("push")) out += PUSH
        if (has("pull")) out += PULL
        if (has("upper")) out += UPPER
        if (has("lower")) out += LOWER
        if (has("full", "whole body", "total body")) out += Target.FULL
        if (has("chest", "pec")) out += Target.CHEST
        if (has("back", "lat", "row")) out += Target.BACK
        if (has("shoulder", "delt")) out += Target.SHOULDERS
        if (has("bicep", "bi\\b")) out += Target.BICEPS
        if (has("tricep", "tri\\b")) out += Target.TRICEPS
        if (has("arm")) out += Target.ARMS
        if (has("leg", "quad", "hamstring", "calf", "calves")) out += Target.LEGS
        if (has("glute", "butt", "hip")) out += Target.GLUTES
        if (has("abs", "core", "six pack", "stomach", "belly")) out += Target.CORE
        if (has("cardio", "hiit", "conditioning", "run")) out += Target.CARDIO
        if (Target.ARMS in out) { out -= Target.BICEPS; out -= Target.TRICEPS }
        return out.toList()
    }

    /**
     * Builds one training day for [targets]. [variant] picks alternative exercises (for "Shuffle" and for the A/B days
     * of a plan) while keeping the same structure.
     */
    fun day(targets: List<Target>, goal: Goal, equip: Equip, level: Level, minutes: Int, variant: Int = 0, name: String? = null): Day {
        val muscles = targets.flatMap { it.muscles }.distinct()
        val strength = muscles.filter { it != Muscle.CARDIO }
        val cardioOnly = strength.isEmpty()
        val finisher = !cardioOnly && goal in setOf(Goal.FAT_LOSS, Goal.ENDURANCE) && minutes >= 40
        val budget = (minutes - (if (finisher) 10 else 0)).coerceAtLeast(15)
        val avgSet = 45 + prescribe(goal, Role.SECONDARY, level).third
        val totalSets = (budget * 60 / avgSet).coerceIn(6, 32)
        val wSum = strength.sumOf { it.weight }.coerceAtLeast(0.01)
        val used = HashSet<String>()
        val items = ArrayList<Item>()
        for (m in strength) {
            val share = (totalSets * m.weight / wSum).roundToInt().coerceAtLeast(if (m.weight >= 0.8) 3 else 2)
            val pool = candidates(m, equip, level)
            if (pool.isEmpty()) continue
            // one main lift first (rotated by variant), then secondaries, then isolations
            fun rot(l: List<P>) = if (l.isEmpty()) l else l.indices.map { l[(it + variant) % l.size] }
            val main = rot(pool.filter { it.role == Role.MAIN })
            val sec = rot(pool.filter { it.role == Role.SECONDARY })
            val iso = rot(pool.filter { it.role == Role.ISOLATION || it.role == Role.DURATION })
            val wantMain = goal !in setOf(Goal.TONE, Goal.ENDURANCE) || m.weight >= 1.0
            val ordered = (if (wantMain) main else emptyList()) + sec + iso + (if (!wantMain) main else emptyList())
            var left = share
            for (p in ordered) {
                if (left <= 0) break
                if (p.key in used) continue
                val (s, r, rest) = prescribe(goal, p.role, level)
                val sets = s.coerceAtMost(maxOf(left, 2))
                items += Item(p.key, sets, r, rest, p.role == Role.MAIN, m)
                used += p.key; left -= sets
                if (items.count { it.muscle == m } >= 4) break
            }
        }
        if (cardioOnly || finisher) {
            val pool = candidates(Muscle.CARDIO, equip, level)
            if (pool.isNotEmpty()) {
                val p = pool[variant % pool.size]
                val mins = if (cardioOnly) minutes.coerceIn(10, 60) else 10
                items += Item(p.key, 1, "${mins}m", 0, false, Muscle.CARDIO)
            }
        }
        // order in the session: main lifts of big muscles → secondaries → isolations → core/calves → cardio
        fun rank(i: Item) = when {
            i.muscle == Muscle.CARDIO -> 9
            i.muscle == Muscle.CORE || i.muscle == Muscle.CALVES -> 7
            i.main -> 0
            i.reps.endsWith("s") -> 6
            i.sets >= 3 && i.rest >= 75 -> 2
            else -> 4
        } * 10 + muscles.indexOf(i.muscle).coerceAtLeast(0)
        val sorted = items.sortedBy { rank(it) }
        val focus = targets.joinToString(" · ") { it.label }
        return Day(name ?: suggestName(targets), focus, sorted)
    }

    fun suggestName(targets: List<Target>): String = when {
        targets.isEmpty() -> "Workout"
        targets.size == 1 -> "${targets[0].label} day"
        targets.size == 2 -> "${targets[0].label} & ${targets[1].label}"
        else -> targets.take(3).joinToString(", ") { it.label }
    }

    // ------------------------------------------------------------------ multi-week plans

    data class Split(val name: String, val targets: List<Target>)

    private val PUSH = listOf(Target.CHEST, Target.SHOULDERS, Target.TRICEPS)
    private val PULL = listOf(Target.BACK, Target.BICEPS)
    private val LEGS = listOf(Target.LEGS, Target.GLUTES)
    private val UPPER = listOf(Target.CHEST, Target.BACK, Target.SHOULDERS, Target.ARMS)
    private val LOWER = listOf(Target.LEGS, Target.GLUTES, Target.CORE)
    private val FULL = listOf(Target.FULL)

    /** The weekly split for a goal, days per week and level. */
    fun split(goal: Goal, days: Int, level: Level): List<Split> {
        val heavy = goal in setOf(Goal.MUSCLE, Goal.GAIN, Goal.STRENGTH, Goal.RECOMP) && level != Level.BEGINNER
        return when (days.coerceIn(2, 6)) {
            2 -> listOf(Split("Full body A", FULL), Split("Full body B", FULL))
            3 -> if (heavy) listOf(Split("Push", PUSH), Split("Pull", PULL), Split("Legs", LEGS))
                 else listOf(Split("Full body A", FULL), Split("Full body B", FULL), Split("Full body C", FULL))
            4 -> listOf(Split("Upper A", UPPER), Split("Lower A", LOWER), Split("Upper B", UPPER), Split("Lower B", LOWER))
            5 -> if (heavy) listOf(Split("Push", PUSH), Split("Pull", PULL), Split("Legs", LEGS), Split("Upper", UPPER), Split("Lower", LOWER))
                 else listOf(Split("Upper", UPPER), Split("Lower", LOWER), Split("Full body + cardio", FULL + Target.CARDIO), Split("Upper B", UPPER), Split("Lower B", LOWER))
            else -> listOf(Split("Push A", PUSH), Split("Pull A", PULL), Split("Legs A", LEGS), Split("Push B", PUSH), Split("Pull B", PULL), Split("Legs B", LEGS))
        }
    }

    /** Weekly progression phases, same scheme as the bundled programs. */
    fun phases(goal: Goal, weeks: Int): List<Triple<IntRange, String, Map<String, Any>>> = when (goal) {
        Goal.STRENGTH -> {
            val a = (weeks / 3).coerceAtLeast(1)
            listOf(Triple(1..a, "Volume", mapOf("note" to "Main lifts for 5 reps. Add 2.5 kg every session you complete all reps.", "reps" to "5")),
                Triple(a + 1..(weeks - 2).coerceAtLeast(a + 1), "Intensity", mapOf("note" to "Main lifts 3–5 reps, heavier. Rest fully.", "reps" to "3-5")),
                Triple(weeks - 1..weeks - 1, "Peak", mapOf("note" to "Doubles and triples near your best. Perfect form only.", "reps" to "2-3")),
                Triple(weeks..weeks, "Deload", mapOf("note" to "Recovery week: one set less, light and fast.", "sets" to -1)))
        }
        Goal.FAT_LOSS, Goal.TONE, Goal.ENDURANCE -> listOf(
            Triple(1..2, "Base", mapOf("note" to "Steady pace. Walk 8–10k steps a day alongside the plan.")),
            Triple(3..(weeks - 1).coerceAtLeast(3), "Density", mapOf("note" to "Rests 20% shorter and one extra set. Keep protein high.", "rest" to 0.8, "sets" to 1)),
            Triple(weeks..weeks, "Test", mapOf("note" to "Repeat week 1 — compare reps, rest and how you feel.")))
        Goal.MUSCLE, Goal.GAIN, Goal.RECOMP, Goal.ATHLETIC -> if (weeks <= 6) listOf(
            Triple(1..(weeks - 2).coerceAtLeast(1), "Base", mapOf("note" to "Own the technique. Stop each set with 2 reps in reserve; add weight when you hit the top of the range.")),
            Triple(weeks - 1..weeks - 1, "Push", mapOf("note" to "One extra set on every exercise. Last set close to failure.", "sets" to 1)),
            Triple(weeks..weeks, "Deload", mapOf("note" to "Recovery week: one set less, about 60% effort.", "sets" to -1)))
        else listOf(
            Triple(1..3, "Base", mapOf("note" to "Own the technique. Stop each set with 2 reps in reserve; add weight when you hit the top of the range.")),
            Triple(4..weeks - 2, "Build", mapOf("note" to "One extra set on every exercise. Push the last set to 1 rep in reserve.", "sets" to 1)),
            Triple(weeks - 1..weeks - 1, "Peak", mapOf("note" to "Heaviest week: main lifts drop to 6–8 reps — chase rep PRs.", "sets" to 1, "reps" to "6-8")),
            Triple(weeks..weeks, "Deload", mapOf("note" to "Recovery week: one set less, about 60% effort. You come back stronger.", "sets" to -1)))
        else -> listOf(Triple(1..weeks, "Progress", mapOf("note" to "Repeat each week, adding a rep or a little weight when every set feels solid.")))
    }

    /** A whole plan as Program JSON (same format as assets/programs.json) so the Programs engine can follow it. */
    fun planJson(goal: Goal, days: Int, equip: Equip, level: Level, minutes: Int, weeks: Int, mascot: String, id: String): org.json.JSONObject {
        val split = split(goal, days, level)
        val counts = HashMap<String, Int>()
        val dayObjs = split.mapIndexed { i, sp ->
            val base = sp.name.substringBefore(' ')
            val v = counts.merge(base, 1, Int::plus)!! - 1
            val d = day(sp.targets, goal, equip, level, minutes, variant = i + v * 2, name = sp.name)
            org.json.JSONObject().put("name", d.name).put("focus", d.focus).put("items", org.json.JSONArray().apply {
                d.items.forEach { it2 -> put(org.json.JSONObject().put("k", it2.key).put("s", it2.sets).put("r", it2.reps).put("rest", it2.rest).put("m", if (it2.main) 1 else 0)) }
            })
        }
        val ph = org.json.JSONArray().apply {
            phases(goal, weeks).forEach { (r, label, extra) ->
                val o = org.json.JSONObject().put("w", org.json.JSONArray().put(r.first).put(r.last)).put("label", label)
                extra.forEach { (k, v) -> o.put(k, v) }
                put(o)
            }
        }
        return org.json.JSONObject()
            .put("id", id).put("name", "My ${goal.label.lowercase()} plan").put("tag", "Made for you by MyFit AI")
            .put("desc", "${goal.blurb}. ${split.size} days a week, about $minutes minutes each, ${equip.label.lowercase()}, ${level.label.lowercase()} level. Built from your choices — edit any day.")
            .put("level", level.name.lowercase()).put("gender", "all").put("equip", equip.programTag)
            .put("goals", org.json.JSONArray().put(goal.id)).put("dpw", split.size).put("weeks", weeks).put("mins", minutes)
            .put("mascot", mascot).put("phases", ph).put("days", org.json.JSONArray(dayObjs))
    }
}
