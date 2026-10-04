package com.myfit.tracker.domain

import kotlin.math.roundToInt

/**
 * Calories and body effects for timed activities, from the Compendium of Physical Activities (MET values).
 * kcal = MET × body weight (kg) × hours. Always an ESTIMATE — heart rate, fitness and terrain change the real number.
 */
object Burn {
    enum class Kind { CARDIO, STRENGTH, SPORT, MIND }

    data class Activity(val id: String, val name: String, val met: Double, val kind: Kind)

    val activities = listOf(
        Activity("walk", "Walking", 3.5, Kind.CARDIO),
        Activity("brisk", "Brisk walk", 4.3, Kind.CARDIO),
        Activity("run", "Running", 9.8, Kind.CARDIO),
        Activity("jog", "Jogging", 7.0, Kind.CARDIO),
        Activity("cycle", "Cycling", 7.5, Kind.CARDIO),
        Activity("swim", "Swimming", 6.0, Kind.CARDIO),
        Activity("hike", "Hiking", 6.0, Kind.CARDIO),
        Activity("stairs", "Stair climbing", 8.0, Kind.CARDIO),
        Activity("rope", "Jump rope", 11.0, Kind.CARDIO),
        Activity("hiit", "HIIT / circuit", 8.0, Kind.STRENGTH),
        Activity("weights", "Weight training", 5.0, Kind.STRENGTH),
        Activity("body", "Bodyweight", 3.8, Kind.STRENGTH),
        Activity("cricket", "Cricket", 4.8, Kind.SPORT),
        Activity("football", "Football", 7.0, Kind.SPORT),
        Activity("badminton", "Badminton", 5.5, Kind.SPORT),
        Activity("dance", "Dancing", 5.0, Kind.SPORT),
        Activity("yoga", "Yoga", 2.5, Kind.MIND),
        Activity("stretch", "Stretching", 2.3, Kind.MIND),
    )

    fun byId(id: String?) = activities.firstOrNull { it.id == id }

    /** Gym Mode: general resistance training (Compendium 02054 ≈ 5.0 MET). */
    const val GYM_MET = 5.0

    fun kcal(met: Double, kg: Double, seconds: Long): Double = met * kg * seconds / 3600.0

    fun intensity(met: Double) = when {
        met < 3.0 -> "Light"
        met < 6.0 -> "Moderate"
        else -> "Vigorous"
    }

    /**
     * WHO guideline minutes: moderate counts 1:1, vigorous counts double (150 moderate or 75 vigorous min/week).
     * Light activity doesn't count toward the target.
     */
    fun whoMinutes(met: Double, seconds: Long): Int {
        val m = (seconds / 60).toInt()
        return when { met >= 6.0 -> m * 2; met >= 3.0 -> m; else -> 0 }
    }

    /** Short, evidence-based notes on what this session did for the body. */
    fun effects(a: Activity?, met: Double, seconds: Long, kcal: Double): List<String> {
        val min = (seconds / 60).toInt()
        val out = ArrayList<String>()
        val who = whoMinutes(met, seconds)
        if (who > 0) out += "+$who min toward the WHO weekly target of 150 active minutes"
        when {
            met >= 6.0 -> out += "Vigorous effort trains your heart and raises VO₂max — the strongest fitness marker for long life"
            met >= 3.0 -> out += "Moderate effort builds your aerobic base and improves insulin sensitivity for up to 24–48 h"
            else -> out += "Light movement eases stiffness, lowers stress hormones and aids recovery"
        }
        when (a?.kind) {
            Kind.STRENGTH -> out += "Muscle repair stays elevated for 24–48 h — 20–40 g protein in the next few hours helps it along"
            Kind.MIND -> out += "Slow breathing and stretching shift you toward the rest-and-digest (parasympathetic) state"
            Kind.SPORT -> out += "Stop-start play builds agility, coordination and bone strength"
            else -> if (min >= 20) out += "After ~20 min your body draws noticeably more on fat for fuel"
        }
        if (met >= 6.0 && min >= 15) out += "Afterburn (EPOC) may add roughly 6–15% more calories over the next hours"
        if (kcal >= 50) out += "≈ ${(kcal / 7.7).roundToInt()} g of body fat worth of energy (7,700 ${EnergyUnit.label} ≈ 1 kg)"
        if (min >= 10) out += "Expect a mood lift — exercise releases endorphins and endocannabinoids"
        out += "Replace fluids: roughly ${(min * 8).coerceIn(150, 1200)} ml of water"
        return out
    }
}
