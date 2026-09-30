package com.myfit.tracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }
enum class LengthUnit(val label: String) { CM("cm"), IN("in") }
enum class VolumeUnit(val label: String) { ML("ml"), L("L"), FL_OZ("fl oz") }
enum class DistanceUnit(val label: String) { KM("km"), MI("mi") }

data class UnitPrefs(
    val weight: WeightUnit = WeightUnit.KG,
    val length: LengthUnit = LengthUnit.IN,
    val volume: VolumeUnit = VolumeUnit.L,
    val distance: DistanceUnit = DistanceUnit.KM,
)

/**
 * Canonical storage: kg, cm, ml, metres. These functions convert for display/input only.
 * Exact defined constants are used (1 lb = 0.45359237 kg, 1 in = 2.54 cm, 1 mi = 1609.344 m,
 * 1 US fl oz = 29.5735295625 ml), so a round-trip never drifts.
 */
object Units {
    const val KG_PER_LB = 0.45359237
    const val CM_PER_IN = 2.54
    const val M_PER_MI = 1609.344
    const val ML_PER_FL_OZ = 29.5735295625

    fun kgTo(kg: Double, u: WeightUnit) = if (u == WeightUnit.KG) kg else kg / KG_PER_LB
    fun toKg(v: Double, u: WeightUnit) = if (u == WeightUnit.KG) v else v * KG_PER_LB

    fun cmTo(cm: Double, u: LengthUnit) = if (u == LengthUnit.CM) cm else cm / CM_PER_IN
    fun toCm(v: Double, u: LengthUnit) = if (u == LengthUnit.CM) v else v * CM_PER_IN

    fun mlTo(ml: Double, u: VolumeUnit) = when (u) {
        VolumeUnit.ML -> ml; VolumeUnit.L -> ml / 1000.0; VolumeUnit.FL_OZ -> ml / ML_PER_FL_OZ
    }
    fun toMl(v: Double, u: VolumeUnit) = when (u) {
        VolumeUnit.ML -> v; VolumeUnit.L -> v * 1000.0; VolumeUnit.FL_OZ -> v * ML_PER_FL_OZ
    }

    fun mTo(m: Double, u: DistanceUnit) = if (u == DistanceUnit.KM) m / 1000.0 else m / M_PER_MI
    fun toM(v: Double, u: DistanceUnit) = if (u == DistanceUnit.KM) v * 1000.0 else v * M_PER_MI
}

/** Rounding happens here, at display time only. */
object Fmt {
    fun num(v: Double, decimals: Int): String {
        val bd = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP)
        return String.format(Locale.US, "%,.${decimals}f", bd.toDouble())
    }

    /** Drops a trailing ".0" (75.0 -> "75", 75.25 -> "75.3" with 1 decimal). */
    fun trim(v: Double, maxDecimals: Int = 1): String {
        val bd = BigDecimal.valueOf(v).setScale(maxDecimals, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = bd.toPlainString()
        return if (plain.contains('.')) plain else String.format(Locale.US, "%,d", bd.toLong())
    }

    fun signed(v: Double, decimals: Int = 1): String {
        val s = num(kotlin.math.abs(v), decimals)
        val zero = BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).signum() == 0
        return when {
            zero -> "±$s"
            v > 0 -> "+$s"
            else -> "−$s"
        }
    }

    fun weight(kg: Double, u: WeightUnit, decimals: Int = 1) = "${trim(Units.kgTo(kg, u), decimals)} ${u.label}"
    fun length(cm: Double, u: LengthUnit) = "${trim(Units.cmTo(cm, u), 1)} ${u.label}"

    fun volume(ml: Double, u: VolumeUnit): String = when (u) {
        VolumeUnit.ML -> "${trim(ml, 0)} ml"
        VolumeUnit.L -> "${num(ml / 1000.0, 2)} L"
        VolumeUnit.FL_OZ -> "${trim(Units.mlTo(ml, u), 1)} fl oz"
    }

    fun distance(m: Double, u: DistanceUnit) = "${num(Units.mTo(m, u), 2)} ${u.label}"

    fun int(v: Number) = String.format(Locale.US, "%,d", v.toLong())

    fun duration(minutes: Long): String {
        val h = minutes / 60; val m = minutes % 60
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
    }

    fun clock(minOfDay: Int): String {
        val h = ((minOfDay / 60) % 24 + 24) % 24; val m = ((minOfDay % 60) + 60) % 60
        return "%02d:%02d".format(Locale.US, h, m)
    }
}
