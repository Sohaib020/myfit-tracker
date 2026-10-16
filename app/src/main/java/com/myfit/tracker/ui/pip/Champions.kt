package com.myfit.tracker.ui.pip

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.myfit.tracker.ui.arena.ArenaProgress

/**
 * R24 Champions — the collectible buddies (owner's Character Bible): 9 human heroes and 6 animal champions, each in
 * three looks (Street → Epic → Legendary). They replace the plush Arena buddies as Home buddy; Pip stays the free default.
 *
 *  - A champion unlocks at an Arena level set by its rarity (Street look).
 *  - Epic and Legendary looks are bought with stars. Stars spent never lower your Arena level: the balance is
 *    "stars earned − stars spent", the level still uses everything earned.
 *  - Each look downloads once: a clip pack (release champs-v1, phones without 3D) or a real-time model
 *    (release champ3d-v1, capable phones). Portraits ship in the APK (assets/champ/<id>/portrait.webp).
 */
enum class Tier(val id: String, val label: String, val cost: Int) {
    STREET("street", "Street", 0), EPIC("epic", "Epic", 150), LEGENDARY("legendary", "Legendary", 400);
    fun next(): Tier? = entries.getOrNull(ordinal + 1)
}

enum class Rarity(val label: String, val color: Color) {
    COMMON("Common", Color(0xFF9AA4B2)), RARE("Rare", Color(0xFF3D7BFF)), EPIC("Epic", Color(0xFF8B5CF6)),
    LEGENDARY("Legendary", Color(0xFFF2B42E)), CHAMPION("Champion", Color(0xFFE0243A)),
}

enum class Champion(
    val id: String, val tag: String, val archetype: String, val color: Color, val female: Boolean, val animal: Boolean,
    val rarity: Rarity, val unlock: Int, val motto: String,
) {
    BLAZE("blaze", "BLAZE", "sprinter and street runner", Color(0xFFFF6B1A), false, false, Rarity.COMMON, 1, "First off the line, every time"),
    NOVA("nova", "NOVA", "sprinter", Color(0xFFFF3D9A), true, false, Rarity.COMMON, 1, "Fast feet, bright mind"),
    TITAN("titan", "TITAN", "powerlifter", Color(0xFF3D7BFF), false, false, Rarity.RARE, 6, "Heavy days build strong years"),
    KAIRO("kairo", "KAIRO", "boxer", Color(0xFFE0243A), false, false, Rarity.RARE, 8, "Hands up, keep moving"),
    VALOR("valor", "VALOR", "cross-trainer", Color(0xFF10B981), true, false, Rarity.RARE, 10, "Strong, steady, unstoppable"),
    RAZE("raze", "RAZE", "calisthenics and parkour athlete", Color(0xFF00D1E0), false, false, Rarity.EPIC, 15, "Your body is the gym"),
    ARIA("aria", "ARIA", "kickboxer", Color(0xFF8B5CF6), true, false, Rarity.EPIC, 18, "Light on my feet, heavy on effort"),
    ONYX("onyx", "ONYX", "bodybuilder", Color(0xFFA3E635), false, false, Rarity.LEGENDARY, 24, "Built one rep at a time"),
    LUNA("luna", "LUNA", "CrossFit and Olympic lifter", Color(0xFFFB7185), true, false, Rarity.LEGENDARY, 28, "Lift heavy, rest well"),
    REX("rex", "REX", "jaguar brawler", Color(0xFF0F766E), false, true, Rarity.CHAMPION, 32, "Quiet steps, big power"),
    TAURUS("taurus", "TAURUS", "bull strongman", Color(0xFFB91C1C), false, true, Rarity.CHAMPION, 36, "Nothing moves me — I move it"),
    HOWL("howl", "HOWL", "wolf runner-fighter", Color(0xFF64748B), false, true, Rarity.CHAMPION, 40, "Run with the pack, finish alone"),
    KODIAK("kodiak", "KODIAK", "grizzly bear powerlifter", Color(0xFF166534), false, true, Rarity.CHAMPION, 44, "Eat, lift, sleep, repeat"),
    FROST("frost", "FROST", "snow leopard acrobat", Color(0xFF7DD3FC), true, true, Rarity.CHAMPION, 48, "Cold focus, warm heart"),
    SABA("saba", "SABA", "lioness sprinter-fighter", Color(0xFF9D174D), true, true, Rarity.CHAMPION, 52, "Lead the pride from the front"),
}

/** Who the buddy is: Pip, or a champion in one of its looks. */
data class BuddyChar(val champion: Champion?, val tier: Tier = Tier.STREET) {
    val isPip: Boolean get() = champion == null
    /** Pack / portrait id: "pip" or "<champion>_<tier>" (e.g. "blaze_epic"). */
    val id: String get() = champion?.let { "${it.id}_${tier.id}" } ?: "pip"
    val label: String get() = champion?.tag ?: "Pip"
    val color: Color get() = champion?.color ?: Color(0xFFA9E9CF)

    companion object {
        val PIP = BuddyChar(null)
        fun parse(s: String?): BuddyChar {
            if (s == null || s == "pip") return PIP
            val ch = Champion.entries.firstOrNull { s.startsWith(it.id + "_") } ?: return PIP   // retired plush ids → Pip
            val t = Tier.entries.firstOrNull { s == "${ch.id}_${it.id}" } ?: Tier.STREET
            return BuddyChar(ch, t)
        }
    }
}

object Champions {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("champions", Context.MODE_PRIVATE)

    fun level(c: Context) = ArenaProgress.level(ArenaProgress.total(c)).n
    fun unlocked(c: Context, ch: Champion) = level(c) >= ch.unlock
    private fun bought(c: Context): Set<String> = p(c).getStringSet("owned", emptySet())!!
    fun owns(c: Context, ch: Champion, t: Tier): Boolean = unlocked(c, ch) && (t == Tier.STREET || "${ch.id}_${t.id}" in bought(c))
    fun spent(c: Context) = p(c).getInt("spent", 0)
    /** Stars you can spend (earned − spent). */
    fun balance(c: Context) = (ArenaProgress.total(c) - spent(c)).coerceAtLeast(0)

    /** Why [t] can't be bought yet, or null when it can. */
    fun blocker(c: Context, ch: Champion, t: Tier): String? = when {
        !unlocked(c, ch) -> "Unlocks at Arena level ${ch.unlock}"
        owns(c, ch, t) -> null
        t == Tier.LEGENDARY && !owns(c, ch, Tier.EPIC) -> "Get the Epic look first"
        balance(c) < t.cost -> "${t.cost - balance(c)} more stars needed"
        else -> null
    }

    /** Spend stars on a look. Returns true when it's now owned. */
    fun buy(c: Context, ch: Champion, t: Tier): Boolean {
        if (owns(c, ch, t)) return true
        if (blocker(c, ch, t) != null) return false
        p(c).edit().putStringSet("owned", bought(c) + "${ch.id}_${t.id}").putInt("spent", spent(c) + t.cost).apply()
        return true
    }

    /** Clip names in a champion's pack (tools/buddy3d/motion.py CLIP_SET, filtered by tier). */
    fun clips(t: Tier): Set<String> = buildSet {
        addAll(listOf("idle", "wave", "celebrate", "thinking", "love", "sleepy", "letsgo", "train", "talk", "dance", "clap", "thumbsup", "yes"))
        if (t != Tier.STREET) addAll(listOf("cocky", "victory"))
        if (t == Tier.LEGENDARY) addAll(listOf("combo", "rally"))
    }

    /** Pip move → the champion clip that plays it (beyond Buddy.FALLBACK): show-off moves use the tier specials. */
    fun alias(clip: String, t: Tier): String? = when (clip) {
        "cheer", "excited" -> "celebrate"
        "highfive" -> "clap"
        "flex", "salute" -> if (t != Tier.STREET) "cocky" else "letsgo"
        "spin", "hearteyes" -> if (t != Tier.STREET) "victory" else null
        "jumpingjacks", "squat", "jog", "stretch" -> if (t == Tier.LEGENDARY) "combo" else "train"
        else -> null
    }
}
