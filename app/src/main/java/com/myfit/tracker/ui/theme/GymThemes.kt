package com.myfit.tracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * R17 theme set — 12 themes, no families. Six dark "gym" themes share the look of Carbon & Lime and Web Crimson
 * (deep gradient, a fine technical pattern, two soft light ribbons, one accent glow) but each has its own pattern
 * and colour story; three light versions of that look; two calm plain/gradient themes; and True Black.
 * Every theme keeps the same background in Glass and Flat — the style only changes the cards.
 */
object GymThemes {
    private val W = Color(0xFFFFFFFF); private val WD = Color(0xBFFFFFFF); private val WF = Color(0x73FFFFFF)
    private val INK = Color(0xFF15171A); private val INKD = Color(0xB315171A); private val INKF = Color(0x6615171A)

    private fun dark(id: String, name: String, art: BackdropArt, top: Long, bottom: Long, blobs: List<Long>,
                     accent: Long, bright: Long, on: Long, tint: Long, fallback: Long, water: Long? = null, protein: Long? = null) = FitTheme(
        id = id, name = name, isLight = false, art = art, bgTop = Color(top), bgBottom = Color(bottom),
        blobs = blobs.map { Color(it) }, accent = Color(accent), accentBright = Color(bright), onAccent = Color(on),
        text = W, textDim = WD, textFaint = WF, glassTint = Color(tint), glassFallback = Color(fallback),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    ).let { t -> t.copy(water = water?.let { Color(it) } ?: t.water, protein = protein?.let { Color(it) } ?: t.protein) }

    private fun light(id: String, name: String, art: BackdropArt, top: Long, bottom: Long, blobs: List<Long>, accent: Long, bright: Long) = FitTheme(
        id = id, name = name, isLight = true, art = art, bgTop = Color(top), bgBottom = Color(bottom),
        blobs = blobs.map { Color(it) }, accent = Color(accent), accentBright = Color(bright), onAccent = Color.White,
        text = INK, textDim = INKD, textFaint = INKF, glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00),
    )

    // ---------------- gym dark (the two favourites + four new patterns)
    val CarbonLime = MoreThemes.CarbonLime
    val WebCrimson = MoreThemes.WebCrimson
    val VoltCyan = dark("volt_cyan", "Volt Cyan", BackdropArt.HEX, 0xFF06131B, 0xFF02070A,
        listOf(0xFF00B8F0, 0xFF0E3A4A, 0xFF06131B, 0xFF5CF2FF), 0xFF22D3EE, 0xFF7DEBFF, 0xFF00222B, 0x149FEFFF, 0xF0081419)
    val EmberForge = dark("ember_forge", "Ember Forge", BackdropArt.SPEED, 0xFF170C06, 0xFF070403,
        listOf(0xFFFF6A00, 0xFF3D2A20, 0xFF170C06, 0xFFFFB347), 0xFFFF7A1A, 0xFFFFAE66, 0xFF261000, 0x1AFFC08A, 0xF0160D08, water = 0xFF4FA3FF)
    val Ultraviolet = dark("ultraviolet", "Ultraviolet", BackdropArt.PLATES, 0xFF0F0719, 0xFF040208,
        listOf(0xFF9B4DFF, 0xFFFF3DA5, 0xFF0F0719, 0xFF5B2DDB), 0xFFB06BFF, 0xFFD3A8FF, 0xFF1A0633, 0x1AD3A8FF, 0xF0110A1C, protein = 0xFFFF6FB5)
    val IronGold = dark("iron_gold", "Iron Gold", BackdropArt.DOTS, 0xFF141311, 0xFF050505,
        listOf(0xFFE8B931, 0xFF3A3630, 0xFF141311, 0xFFFFD86B), 0xFFF2C230, 0xFFFFDE73, 0xFF221800, 0x14FFE9A8, 0xF0121110, water = 0xFF5AA9FF)

    // ---------------- light (same gym look, ink lines on paper)
    val Chalk = light("chalk_lime", "Chalk & Lime", BackdropArt.GRID, 0xFFF7F8F3, 0xFFE6EBDF,
        listOf(0xFFD6F28A, 0xFFE9EEE2, 0xFFFFFFFF, 0xFFC6E86A), 0xFF4F8F00, 0xFF66AA12)
    val ArcticSteel = light("arctic_steel", "Arctic Steel", BackdropArt.HEX, 0xFFF4F7FB, 0xFFDCE5F0,
        listOf(0xFFB9D2FF, 0xFFE6EEF8, 0xFFFFFFFF, 0xFF9CC0F5), 0xFF1F5FD6, 0xFF3B7BEA)
    val CoralCourt = light("coral_court", "Coral Court", BackdropArt.SPEED, 0xFFFCF6F3, 0xFFF1E2DB,
        listOf(0xFFFFC2B8, 0xFFF6E9E4, 0xFFFFFFFF, 0xFFFFA89A), 0xFFE0453F, 0xFFF0625B)

    // ---------------- plain & gradient
    val Slate = dark("slate", "Slate", BackdropArt.PLAIN, 0xFF111418, 0xFF111418,
        listOf(0xFF111418, 0xFF111418, 0xFF111418, 0xFF111418), 0xFF34D399, 0xFF7BE8C0, 0xFF02261A, 0x14FFFFFF, 0xF2161A1F)
    val Dusk = dark("dusk_gradient", "Dusk", BackdropArt.GRADIENT, 0xFF2A1650, 0xFF0A1028,
        listOf(0xFF7A3CC8, 0xFF1C3A8A, 0xFF2A1650, 0xFFFF6FA8), 0xFFF472B6, 0xFFFFA6D2, 0xFF33061E, 0x1AFFC6E4, 0xF0181433)

    val TrueBlack = MoreThemes.TrueBlack

    val all: List<FitTheme> by lazy {
        listOf(CarbonLime, WebCrimson, VoltCyan, EmberForge, Ultraviolet, IronGold, Chalk, ArcticSteel, CoralCourt, Slate, Dusk, TrueBlack)
    }

    /** Every retired theme id → the closest R17 theme (keeps people on a similar colour). */
    val legacy: Map<String, String> = mapOf(
        "kinetic" to "carbon_lime", "forest" to "carbon_lime", "evergreen" to "carbon_lime", "carbon" to "slate", "graphite" to "slate",
        "oceanic" to "volt_cyan", "cobalt" to "volt_cyan", "aurora" to "volt_cyan", "sky_guardian" to "volt_cyan", "rally_blue" to "volt_cyan",
        "blaze" to "ember_forge", "ember" to "ember_forge", "hunza" to "ember_forge", "midnight_supercar" to "ember_forge", "cafe_racer" to "ember_forge", "desert" to "ember_forge",
        "cosmic" to "ultraviolet", "sakura" to "dusk_gradient", "racing_red" to "web_crimson", "badshahi" to "web_crimson", "truckart" to "iron_gold",
        "adamant" to "iron_gold", "midnight_vigilante" to "iron_gold", "reactor_gold" to "iron_gold",
        "porcelain" to "arctic_steel", "cloud" to "arctic_steel", "sandstone" to "coral_court",
    )
}
