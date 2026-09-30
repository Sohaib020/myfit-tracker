package com.myfit.tracker.domain

/**
 * Which number is "today's steps", and where it came from. Priority:
 *  1. Health Connect (Samsung Health + watch, de-duplicated by Health Connect itself)
 *  2. What you entered manually
 *  3. The phone's own step sensor
 * Nothing is ever added across sources — that would double count the same steps.
 */
object StepsSource {
    enum class Source(val label: String) { HEALTH_CONNECT("Samsung Health / watch"), MANUAL("Entered manually"), PHONE("Phone sensor") }
    data class Pick(val steps: Long, val source: Source)

    fun pick(healthConnect: Long?, manual: Long?, phone: Long?): Pick? = when {
        healthConnect != null -> Pick(healthConnect, Source.HEALTH_CONNECT)
        manual != null -> Pick(manual, Source.MANUAL)
        phone != null -> Pick(phone, Source.PHONE)
        else -> null
    }
}

/**
 * Daily steps from cumulative step-counter snapshots. The counter resets to 0 on reboot, so a drop
 * means "reboot": the new reading itself is the steps since boot. Steps between two snapshots are
 * credited to the day of the later snapshot (day boundaries are approximate — labelled as such).
 */
object PhoneStepCalc {
    data class Snap(val at: Long, val counter: Long, val day: String)

    fun daily(snaps: List<Snap>): Map<String, Long> {
        val s = snaps.sortedBy { it.at }
        val out = LinkedHashMap<String, Long>()
        for (i in 1 until s.size) {
            val a = s[i - 1]; val b = s[i]
            val delta = if (b.counter >= a.counter) b.counter - a.counter else b.counter
            if (delta in 0..100_000) out[b.day] = (out[b.day] ?: 0L) + delta
        }
        return out
    }
}
