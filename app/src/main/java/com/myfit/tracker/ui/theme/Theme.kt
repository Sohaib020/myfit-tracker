package com.myfit.tracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class BackdropArt { KINETIC, AURORA, GRID, WAVES, LANDSCAPE, FROST, BOREALIS, OCEAN, SAKURA, NEON_CITY, DUNES, GALAXY, FOREST, LAVA, RAIN, MINT, GOLD, ARCTIC, CYBER, DESERT_NIGHT, LOTUS, MONSOON }

@Immutable
data class FitTheme(
    val id: String,
    val name: String,
    val isLight: Boolean,
    val art: BackdropArt,
    val bgTop: Color,
    val bgBottom: Color,
    val blobs: List<Color>,
    val accent: Color,
    val accentBright: Color,
    val onAccent: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val glassTint: Color,          // fill applied over the blurred backdrop
    val glassFallback: Color,      // fill used when real blur is unavailable (< Android 12)
    val rimHigh: Color,            // bright edge of the glass rim
    val rimLow: Color,
    val success: Color = Color(0xFF2FD37A),
    val warning: Color = Color(0xFFFFB43B),
    val danger: Color = Color(0xFFFF4D5A),
    val water: Color = Color(0xFF3FA9FF),
    val protein: Color = Color(0xFFFF7A59),
    val carbs: Color = Color(0xFFFFC53D),
    val fat: Color = Color(0xFFB57CFF),
    val sleep: Color = Color(0xFF7C8CFF),
    val steps: Color = Color(0xFF2FD37A),
    /** Unused: every theme is a still image (kept for source compatibility). */
    val gentle: Boolean = false,
    /** The moment of the theme's animation used for its still image. */
    val stillT: Float = 7f,
)

object Themes {
    val Kinetic = FitTheme(
        id = "kinetic", name = "Kinetic", isLight = false, art = BackdropArt.KINETIC,
        bgTop = Color(0xFF101216), bgBottom = Color(0xFF060709),
        blobs = listOf(Color(0xFFFF7A1A), Color(0xFFFFB547), Color(0xFF101216), Color(0xFFFF9A3D)),
        accent = Color(0xFFFF7A1A), accentBright = Color(0xFFFFA552), onAccent = Color(0xFF1A0C02),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1FFFB27A), glassFallback = Color(0xE6181210),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        protein = Color(0xFFFF9A5C), steps = Color(0xFFFFB547),
    )
    val Cobalt = FitTheme(
        id = "cobalt", name = "Midnight Cobalt", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0A1230), bgBottom = Color(0xFF03050D),
        blobs = listOf(Color(0xFF2A5CFF), Color(0xFF5B7CFF), Color(0xFF0A1230), Color(0xFF4B36D9)),
        accent = Color(0xFF4C8DFF), accentBright = Color(0xFF8AB4FF), onAccent = Color(0xFFFFFFFF),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1A7AA6FF), glassFallback = Color(0xE00A1238),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Graphite = FitTheme(
        id = "graphite", name = "Graphite", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF1A1B1E), bgBottom = Color(0xFF070708),
        blobs = listOf(Color(0xFF5A5D66), Color(0xFF8A8D96), Color(0xFF1A1B1E), Color(0xFF3A3C42)),
        accent = Color(0xFFE9EAEE), accentBright = Color(0xFFFFFFFF), onAccent = Color(0xFF111214),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x14FFFFFF), glassFallback = Color(0xE01A1B1E),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Porcelain = FitTheme(
        id = "porcelain", name = "Porcelain", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFF7F8FC), bgBottom = Color(0xFFE9ECF4),
        blobs = listOf(Color(0xFFC7D0FF), Color(0xFFE2D4FF), Color(0xFFD2ECFF), Color(0xFFF2F4FA)),
        accent = Color(0xFF4F46E5), accentBright = Color(0xFF6D66F0), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), water = Color(0xFF1C7EE0), warning = Color(0xFFD98A00),
    )
    val Sandstone = FitTheme(
        id = "sandstone", name = "Sandstone", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFFBF5EC), bgBottom = Color(0xFFEFE3D2),
        blobs = listOf(Color(0xFFF7C7A6), Color(0xFFEBCFB2), Color(0xFFF3E4D0), Color(0xFFD9A47E)),
        accent = Color(0xFFC2552D), accentBright = Color(0xFFDB6E43), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), water = Color(0xFF1C7EE0), warning = Color(0xFFD98A00),
    )
    val Evergreen = FitTheme(
        id = "evergreen", name = "Evergreen", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0B1F18), bgBottom = Color(0xFF030806),
        blobs = listOf(Color(0xFF1F6B4C), Color(0xFFE8B44C), Color(0xFF0B1F18), Color(0xFF14402F)),
        accent = Color(0xFFE8B44C), accentBright = Color(0xFFF5CE7A), onAccent = Color(0xFF1A1204),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFE2A0), glassFallback = Color(0xE00C1F18),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Dusk = FitTheme(
        id = "dusk", name = "Plum Dusk", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF150A1E), bgBottom = Color(0xFF5A2236),
        blobs = listOf(Color(0xFF8E2F63), Color(0xFFFF8FB8), Color(0xFF150A1E), Color(0xFFD9587F)),
        accent = Color(0xFFFF8FB8), accentBright = Color(0xFFFFB8D2), onAccent = Color(0xFF2A0A18),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFB8D2), glassFallback = Color(0xE01E0E24),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Steel = FitTheme(
        id = "steel", name = "Arctic Steel", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF202A36), bgBottom = Color(0xFF0B0F16),
        blobs = listOf(Color(0xFF4E6A88), Color(0xFF7DD3FC), Color(0xFF202A36), Color(0xFF2E4057)),
        accent = Color(0xFF7DD3FC), accentBright = Color(0xFFB5E6FF), onAccent = Color(0xFF062030),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AB5E6FF), glassFallback = Color(0xE0141B24),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Golden = FitTheme(
        id = "golden", name = "Golden Hour", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0B0C1C), bgBottom = Color(0xFF3A2414),
        blobs = listOf(Color(0xFFFFC857), Color(0xFFFFB04A), Color(0xFF0B0C1C), Color(0xFF5A3A2A)),
        accent = Color(0xFFFFC857), accentBright = Color(0xFFFFDD8E), onAccent = Color(0xFF1A1204),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFDD8E), glassFallback = Color(0xE0141220),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Lavender = FitTheme(
        id = "lavender", name = "Lavender Mist", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFF6F2FE), bgBottom = Color(0xFFE6DFF8),
        blobs = listOf(Color(0xFFDCC8FF), Color(0xFFFFD6EA), Color(0xFFCCE0FF), Color(0xFFF3EEFF)),
        accent = Color(0xFF7C3AED), accentBright = Color(0xFF9461F5), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), water = Color(0xFF1C7EE0), warning = Color(0xFFD98A00),
    )

    val all = listOf(Kinetic, Cobalt, Graphite, Porcelain, Sandstone, Evergreen, Dusk, Steel, Golden, Lavender)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: Kinetic
}
