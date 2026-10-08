package com.myfit.tracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * New theme families (R16): true-black dark, hero moods, cars & bikes, nature. Every name and colour story is
 * original — no film, studio or brand names, logos or characters.
 */
object MoreThemes {
    private val W = Color(0xFFFFFFFF); private val WD = Color(0xBFFFFFFF); private val WF = Color(0x73FFFFFF)
    private val RH = Color(0x66FFFFFF); private val RL = Color(0x14FFFFFF)

    private fun dark(
        id: String, name: String, art: BackdropArt, top: Long, bottom: Long, blobs: List<Long>,
        accent: Long, bright: Long, on: Long, tint: Long, fallback: Long,
        water: Long? = null, protein: Long? = null, steps: Long? = null,
    ) = FitTheme(
        id = id, name = name, isLight = false, art = art, bgTop = Color(top), bgBottom = Color(bottom),
        blobs = blobs.map { Color(it) }, accent = Color(accent), accentBright = Color(bright), onAccent = Color(on),
        text = W, textDim = WD, textFaint = WF, glassTint = Color(tint), glassFallback = Color(fallback), rimHigh = RH, rimLow = RL,
    ).let { t -> t.copy(water = water?.let { Color(it) } ?: t.water, protein = protein?.let { Color(it) } ?: t.protein, steps = steps?.let { Color(it) } ?: t.steps) }

    // ---------------- dark & AMOLED
    val TrueBlack = dark("trueblack", "True Black", BackdropArt.KINETIC, 0xFF000000, 0xFF000000,
        listOf(0xFF0B3A30, 0xFF0A2A3A, 0xFF000000, 0xFF0F4A3C), 0xFF2EE6B6, 0xFF8CF5DA, 0xFF00261C, 0x14FFFFFF, 0xF2080A0A)

    // ---------------- hero moods (original names only)
    val WebCrimson = dark("web_crimson", "Web Crimson", BackdropArt.GRID, 0xFF140509, 0xFF050A1E,
        listOf(0xFFD7263D, 0xFF1F4BD8, 0xFF140509, 0xFF8E1028), 0xFFE8344E, 0xFFFF7487, 0xFFFFFFFF, 0x1AFF8FA0, 0xE0160A12,
        water = 0xFF4C7DFF)
    val Adamant = dark("adamant", "Adamant", BackdropArt.KINETIC, 0xFF0B1424, 0xFF05080F,
        listOf(0xFFF5C518, 0xFF2D5DA8, 0xFF0B1424, 0xFF9AA4B5), 0xFFF5C518, 0xFFFFDC5C, 0xFF1E1600, 0x1AFFE48A, 0xE00C1424,
        water = 0xFF5A8DE0)
    val SkyGuardian = dark("sky_guardian", "Sky Guardian", BackdropArt.AURORA, 0xFF061538, 0xFF02060F,
        listOf(0xFF1E5BFF, 0xFFE0242F, 0xFF061538, 0xFFF2C230), 0xFF3D7BFF, 0xFF8FB2FF, 0xFFFFFFFF, 0x1A9DB8FF, 0xE00A1636,
        protein = 0xFFFF5A5F, steps = 0xFFF2C230)
    val MidnightVigilante = dark("midnight_vigilante", "Midnight Vigilante", BackdropArt.NEON_CITY, 0xFF0B0C10, 0xFF020203,
        listOf(0xFF2A2D36, 0xFFF2C94C, 0xFF0B0C10, 0xFF3A3E4A), 0xFFF2C94C, 0xFFFFE08A, 0xFF1A1400, 0x14FFFFFF, 0xF00E0F13)
    val ReactorGold = dark("reactor_gold", "Reactor Gold", BackdropArt.CYBER, 0xFF1A0606, 0xFF070202,
        listOf(0xFFB3121B, 0xFFF0B429, 0xFF1A0606, 0xFF5CE1E6), 0xFFF0B429, 0xFFFFD36B, 0xFF231600, 0x1AFFD27A, 0xE01C0A0A,
        water = 0xFF5CE1E6)

    // ---------------- cars & bikes
    val RacingRed = dark("racing_red", "Racing Red", BackdropArt.KINETIC, 0xFF170404, 0xFF070101,
        listOf(0xFFE10600, 0xFFFF5A3C, 0xFF170404, 0xFF7A0A06), 0xFFFF2A1F, 0xFFFF7A6B, 0xFFFFFFFF, 0x1AFF8C80, 0xE0180808)
    val CarbonLime = dark("carbon_lime", "Carbon & Lime", BackdropArt.GRID, 0xFF101211, 0xFF050605,
        listOf(0xFF8FD400, 0xFF3A3F3C, 0xFF101211, 0xFFC6FF1A), 0xFFA6E51E, 0xFFCFFF66, 0xFF142000, 0x14E6FFB0, 0xF0121413)
    val MidnightSupercar = dark("midnight_supercar", "Midnight Supercar", BackdropArt.KINETIC, 0xFF07102A, 0xFF02040C,
        listOf(0xFF1C3FAA, 0xFFFF7A1A, 0xFF07102A, 0xFF0E2366), 0xFFFF8A2B, 0xFFFFB46E, 0xFF261000, 0x1A9DB5FF, 0xE00A1230,
        water = 0xFF4F86FF)
    val CafeRacer = dark("cafe_racer", "Café Racer", BackdropArt.AURORA, 0xFF15110D, 0xFF070605,
        listOf(0xFFB07A4A, 0xFFE8D3B0, 0xFF15110D, 0xFF5C3B22), 0xFFD9A066, 0xFFF0C995, 0xFF1F1206, 0x1AFFE2C0, 0xE0171310)
    val RallyBlue = dark("rally_blue", "Rally Blue", BackdropArt.WAVES, 0xFF041A3A, 0xFF010810,
        listOf(0xFF0B57D0, 0xFFFFD200, 0xFF041A3A, 0xFF0A3A8A), 0xFFFFD200, 0xFFFFE466, 0xFF1C1600, 0x1AA6C8FF, 0xE0061A36,
        water = 0xFF4E95FF)

    /** Ids of themes that were retired in R16, mapped to the closest one we kept. */
    val legacy = mapOf(
        "dusk" to "cosmic", "steel" to "graphite", "golden" to "evergreen", "lavender" to "porcelain", "neon" to "cosmic",
        "sapphire" to "cobalt", "volcanic" to "ember", "matcha" to "forest", "peach" to "sandstone", "mint" to "cloud",
        "track" to "racing_red", "court" to "cobalt", "stadium" to "carbon", "neongym" to "carbon_lime", "ironfloor" to "graphite",
        "clay" to "sandstone", "chalk" to "porcelain", "mughal" to "badshahi", "ajrak" to "badshahi", "thar" to "desert",
        "multani" to "cloud", "swat" to "forest",
    )
}
