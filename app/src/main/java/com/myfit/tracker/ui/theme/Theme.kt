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
    /** Default theme: the Kinetic design (dark graphite, energy glow, speed stripes) in Mint Fresh colours. */
    val Kinetic = FitTheme(
        id = "kinetic", name = "Kinetic Mint", isLight = false, art = BackdropArt.KINETIC,
        bgTop = Color(0xFF0C1414), bgBottom = Color(0xFF040808),
        blobs = listOf(Color(0xFF2CC9A7), Color(0xFF5CC8E8), Color(0xFF0C1414), Color(0xFF7FE7CF)),
        accent = Color(0xFF2CC9A7), accentBright = Color(0xFF7FE7CF), onAccent = Color(0xFF032019),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1F7FE7CF), glassFallback = Color(0xE60E1817),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        protein = Color(0xFFFF9A5C), steps = Color(0xFF7FE7CF), water = Color(0xFF5CC8E8),
    )
    /** The original orange Kinetic, kept as its own theme. */
    val Blaze = FitTheme(
        id = "blaze", name = "Blaze", isLight = false, art = BackdropArt.KINETIC,
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

    val Ember = FitTheme(
        id = "ember", name = "Ember", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF120A08), bgBottom = Color(0xFF2A1208),
        blobs = listOf(Color(0xFFFF7A2E), Color(0xFFFFB27F), Color(0xFF120A08), Color(0xFFB8451A)),
        accent = Color(0xFFFF8F4D), accentBright = Color(0xFFFFB27F), onAccent = Color(0xFF1F0D03),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFB98A), glassFallback = Color(0xE01A110D),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        protein = Color(0xFFFFA36B), steps = Color(0xFFFFC46B),
    )
    val Oceanic = FitTheme(
        id = "oceanic", name = "Oceanic Teal", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF06282C), bgBottom = Color(0xFF01090E),
        blobs = listOf(Color(0xFF148F8A), Color(0xFF7EEADB), Color(0xFF06282C), Color(0xFF0E5A6E)),
        accent = Color(0xFF2DD4BF), accentBright = Color(0xFF7EEADB), onAccent = Color(0xFF032420),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1A7EEADB), glassFallback = Color(0xE0062126),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        success = Color(0xFF6EE787),
    )
    val Aurora = FitTheme(
        id = "aurora", name = "Aurora Night", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF050A18), bgBottom = Color(0xFF0A1424),
        blobs = listOf(Color(0xFF2FD38A), Color(0xFFB794FF), Color(0xFF050A18), Color(0xFF5A3FCF)),
        accent = Color(0xFFB794FF), accentBright = Color(0xFFD4BFFF), onAccent = Color(0xFF1A0B33),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AB8F5D8), glassFallback = Color(0xE00A1220),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        fat = Color(0xFFF08AF0),
    )
    val Carbon = FitTheme(
        id = "carbon", name = "Carbon Fibre", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF16181C), bgBottom = Color(0xFF060708),
        blobs = listOf(Color(0xFF3A3E46), Color(0xFFC6F432), Color(0xFF16181C), Color(0xFF24272D)),
        accent = Color(0xFFC6F432), accentBright = Color(0xFFDDFF73), onAccent = Color(0xFF141A02),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x14FFFFFF), glassFallback = Color(0xE014161A),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        steps = Color(0xFFC6F432),
    )
    val Neon = FitTheme(
        id = "neon", name = "Neon Pulse", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0A0420), bgBottom = Color(0xFF08030F),
        blobs = listOf(Color(0xFFFF4FD8), Color(0xFF3FE0FF), Color(0xFF0A0420), Color(0xFF6A1FB8)),
        accent = Color(0xFFFF4FD8), accentBright = Color(0xFFFF8BE6), onAccent = Color(0xFF24041E),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFF8BE6), glassFallback = Color(0xE0140826),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        water = Color(0xFF3FE0FF), fat = Color(0xFF9D8BFF),
    )
    val Sapphire = FitTheme(
        id = "sapphire", name = "Royal Sapphire", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0A122E), bgBottom = Color(0xFF02040C),
        blobs = listOf(Color(0xFF1F3FB8), Color(0xFFE9C46A), Color(0xFF0A122E), Color(0xFF2A2A8A)),
        accent = Color(0xFFE9C46A), accentBright = Color(0xFFF5DC9C), onAccent = Color(0xFF1C1404),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1A9DB4FF), glassFallback = Color(0xE00A1030),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        carbs = Color(0xFFFFA94D),
    )
    val Forest = FitTheme(
        id = "forest", name = "Forest Mist", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF1A2626), bgBottom = Color(0xFF050B0B),
        blobs = listOf(Color(0xFF3E5A55), Color(0xFFA3D977), Color(0xFF1A2626), Color(0xFF24403A)),
        accent = Color(0xFFA3D977), accentBright = Color(0xFFC4EBA1), onAccent = Color(0xFF0F1C06),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AC4EBA1), glassFallback = Color(0xE00E1A1A),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Volcanic = FitTheme(
        id = "volcanic", name = "Volcanic", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF151415), bgBottom = Color(0xFF080707),
        blobs = listOf(Color(0xFFFF6B35), Color(0xFFFF9566), Color(0xFF151415), Color(0xFF5A1E0A)),
        accent = Color(0xFFFF6B35), accentBright = Color(0xFFFF9566), onAccent = Color(0xFF1F0A02),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x14FFB08A), glassFallback = Color(0xE0141212),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        protein = Color(0xFFFF8FA3),
    )
    val Cosmic = FitTheme(
        id = "cosmic", name = "Cosmic", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0C0A20), bgBottom = Color(0xFF030308),
        blobs = listOf(Color(0xFF6A24A8), Color(0xFFF072B0), Color(0xFF0C0A20), Color(0xFF14607A)),
        accent = Color(0xFFC084FC), accentBright = Color(0xFFDDB8FF), onAccent = Color(0xFF1E0838),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AC9A8FF), glassFallback = Color(0xE00C0A1E),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        fat = Color(0xFFF472B6),
    )
    val Matcha = FitTheme(
        id = "matcha", name = "Matcha Night", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF14200F), bgBottom = Color(0xFF060A05),
        blobs = listOf(Color(0xFF4E6E32), Color(0xFFE8DFC0), Color(0xFF14200F), Color(0xFF2C4220)),
        accent = Color(0xFFE8DFC0), accentBright = Color(0xFFF7F1DC), onAccent = Color(0xFF18200F),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AEDE6C8), glassFallback = Color(0xE0111A10),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        steps = Color(0xFFA9D46E),
    )
    val Cloud = FitTheme(
        id = "cloud", name = "Cloud", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFEAF2FD), bgBottom = Color(0xFFF5F8FD),
        blobs = listOf(Color(0xFFB8D2F8), Color(0xFFFFFFFF), Color(0xFFD6E6FB), Color(0xFFF0F5FD)),
        accent = Color(0xFF2563EB), accentBright = Color(0xFF4A82F0), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF0E9FC6),
    )
    val Peach = FitTheme(
        id = "peach", name = "Peach Sorbet", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFFFF5EE), bgBottom = Color(0xFFFCEDE4),
        blobs = listOf(Color(0xFFFFC29A), Color(0xFFFFB4AE), Color(0xFFFFDCAA), Color(0xFFFFF3EA)),
        accent = Color(0xFFC8492B), accentBright = Color(0xFFDD6444), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF1C7EE0), protein = Color(0xFFE0457B),
    )
    val Mint = FitTheme(
        id = "mint", name = "Mint Fresh", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFF2FBF8), bgBottom = Color(0xFFE6F5F2),
        blobs = listOf(Color(0xFFB8EEDB), Color(0xFFC2E2F4), Color(0xFFB4E6E0), Color(0xFFF2FBF8)),
        accent = Color(0xFF0E7490), accentBright = Color(0xFF1A8FAD), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF2563EB),
    )
    val Sakura = FitTheme(
        id = "sakura", name = "Sakura Blush", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFFFF6F7), bgBottom = Color(0xFFFBE8EE),
        blobs = listOf(Color(0xFFFFCCDA), Color(0xFFF8D4E4), Color(0xFFF4EAFF), Color(0xFFFFF3F6)),
        accent = Color(0xFFC2366E), accentBright = Color(0xFFD65A8A), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF1C7EE0),
    )
    val Desert = FitTheme(
        id = "desert", name = "Desert Dawn", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFEEEAF7), bgBottom = Color(0xFFF5E2D2),
        blobs = listOf(Color(0xFFFFE6C8), Color(0xFFEEC2A0), Color(0xFFE8D8F0), Color(0xFFD9926E)),
        accent = Color(0xFFA8432A), accentBright = Color(0xFFC25A3E), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF1C7EE0),
    )

    // ---- sport & gym ----
    val Track = FitTheme(
        id = "track", name = "Track Day", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0F0E10), bgBottom = Color(0xFF2A0F0B),
        blobs = listOf(Color(0xFFB5361F), Color(0xFFFF6A55), Color(0xFF0F0E10), Color(0xFF4A1A12)),
        accent = Color(0xFFFF6A55), accentBright = Color(0xFFFF9A88), onAccent = Color(0xFF2A0805),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFB4A8), glassFallback = Color(0xE0161012),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        protein = Color(0xFFFFA36B),
    )
    val Court = FitTheme(
        id = "court", name = "Court Lines", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0A0B10), bgBottom = Color(0xFF2B1A0E),
        blobs = listOf(Color(0xFF8A4F24), Color(0xFFF28C38), Color(0xFF0A0B10), Color(0xFF5A3418)),
        accent = Color(0xFFF59A4A), accentBright = Color(0xFFFFBE85), onAccent = Color(0xFF2A1404),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFC79A), glassFallback = Color(0xE0141010),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Stadium = FitTheme(
        id = "stadium", name = "Floodlights", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF05080F), bgBottom = Color(0xFF0B2416),
        blobs = listOf(Color(0xFF3A5A9A), Color(0xFF8FB8FF), Color(0xFF05080F), Color(0xFF1A5A30)),
        accent = Color(0xFF7FB2FF), accentBright = Color(0xFFAECFFF), onAccent = Color(0xFF061428),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AAECFFF), glassFallback = Color(0xE00A0F1A),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        water = Color(0xFF5FD4F5),
    )
    val NeonGym = FitTheme(
        id = "neongym", name = "Neon Gym", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0D0C10), bgBottom = Color(0xFF08070A),
        blobs = listOf(Color(0xFFFF5473), Color(0xFF8C66FF), Color(0xFF0D0C10), Color(0xFF5A1A3A)),
        accent = Color(0xFFFF5A7A), accentBright = Color(0xFFFF8FA5), onAccent = Color(0xFF2A0410),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFF9FB5), glassFallback = Color(0xE0130F16),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        fat = Color(0xFF9D8BFF), protein = Color(0xFFFF9466),
    )
    val IronFloor = FitTheme(
        id = "ironfloor", name = "Iron Floor", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF141517), bgBottom = Color(0xFF060607),
        blobs = listOf(Color(0xFF3A3B3F), Color(0xFFFFD23F), Color(0xFF141517), Color(0xFF26272A)),
        accent = Color(0xFFFFD23F), accentBright = Color(0xFFFFE380), onAccent = Color(0xFF1F1800),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x14FFFFFF), glassFallback = Color(0xE0121214),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        carbs = Color(0xFFFFA94D),
    )
    val Clay = FitTheme(
        id = "clay", name = "Clay Court", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFFDF6EE), bgBottom = Color(0xFFEDC9B4),
        blobs = listOf(Color(0xFFF1B89A), Color(0xFFF7D7C2), Color(0xFFFFF6EE), Color(0xFFE39D7A)),
        accent = Color(0xFFB4441F), accentBright = Color(0xFFCC5A33), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF1C7EE0),
    )
    val Chalk = FitTheme(
        id = "chalk", name = "Chalk Dust", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFF2F1EF), bgBottom = Color(0xFFDCDDE0),
        blobs = listOf(Color(0xFFFFFFFF), Color(0xFFF4DCDC), Color(0xFFDCE4F4), Color(0xFFEDEDED)),
        accent = Color(0xFFBE123C), accentBright = Color(0xFFD63A5E), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF1C7EE0),
    )

    // ---- Pakistani-inspired ----
    val TruckArt = FitTheme(
        id = "truckart", name = "Truck Art", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0A0C18), bgBottom = Color(0xFF05060D),
        blobs = listOf(Color(0xFFE8467C), Color(0xFFF7C531), Color(0xFF0A0C18), Color(0xFF2EA36B)),
        accent = Color(0xFFFFB627), accentBright = Color(0xFFFFCF6B), onAccent = Color(0xFF241600),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFD98A), glassFallback = Color(0xE00C0E1C),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        protein = Color(0xFFFF6FA0), water = Color(0xFF4D8DF0),
    )
    val Mughal = FitTheme(
        id = "mughal", name = "Mughal Tiles", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0A1126), bgBottom = Color(0xFF050814),
        blobs = listOf(Color(0xFF1F4FA0), Color(0xFF3FC1C9), Color(0xFF0A1126), Color(0xFF8A5A2A)),
        accent = Color(0xFF47C9CF), accentBright = Color(0xFF8FE3E6), onAccent = Color(0xFF03282A),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1A9FE6EA), glassFallback = Color(0xE00A1228),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        carbs = Color(0xFFDDAA4E),
    )
    val Ajrak = FitTheme(
        id = "ajrak", name = "Ajrak", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF2A0608), bgBottom = Color(0xFF0E0408),
        blobs = listOf(Color(0xFF8A1218), Color(0xFF1A1A3A), Color(0xFF2A0608), Color(0xFFB02A2A)),
        accent = Color(0xFFFF8F70), accentBright = Color(0xFFFFB59E), onAccent = Color(0xFF2E0A04),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFB59E), glassFallback = Color(0xE01C080B),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        water = Color(0xFF6E8BFF), protein = Color(0xFFFFB86B),
    )
    val Hunza = FitTheme(
        id = "hunza", name = "Hunza Dusk", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF0E0E26), bgBottom = Color(0xFF0A0812),
        blobs = listOf(Color(0xFF5C3866), Color(0xFFFFA36C), Color(0xFF0E0E26), Color(0xFF2A2040)),
        accent = Color(0xFFFFA86F), accentBright = Color(0xFFFFC9A0), onAccent = Color(0xFF2B1204),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFCBA8), glassFallback = Color(0xE0141226),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )
    val Badshahi = FitTheme(
        id = "badshahi", name = "Badshahi Red", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF160A0C), bgBottom = Color(0xFF3A1610),
        blobs = listOf(Color(0xFF8A3420), Color(0xFFF1E4CF), Color(0xFF160A0C), Color(0xFF5A2414)),
        accent = Color(0xFFF2E3C8), accentBright = Color(0xFFFFF4E2), onAccent = Color(0xFF3A1610),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFE8D8), glassFallback = Color(0xE01E0F0D),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        protein = Color(0xFFFF8F6B),
    )
    val Thar = FitTheme(
        id = "thar", name = "Thar Dusk", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF120C24), bgBottom = Color(0xFF170808),
        blobs = listOf(Color(0xFF7A3448), Color(0xFFFFA04D), Color(0xFF120C24), Color(0xFF2A6FA0)),
        accent = Color(0xFF4AB0EE), accentBright = Color(0xFF8FD0F7), onAccent = Color(0xFF04223A),
        text = Color(0xFFFFFFFF), textDim = Color(0xBFFFFFFF), textFaint = Color(0x73FFFFFF),
        glassTint = Color(0x1AFFC08A), glassFallback = Color(0xE0180E1C),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
        carbs = Color(0xFFFFB45C),
    )
    val Multani = FitTheme(
        id = "multani", name = "Multani Blue", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFFBFAF6), bgBottom = Color(0xFFF1F1EC),
        blobs = listOf(Color(0xFFB8CCF2), Color(0xFFBFE6EC), Color(0xFFFFFFFF), Color(0xFFD8E2F6)),
        accent = Color(0xFF1E4FBF), accentBright = Color(0xFF3A6AD6), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF1597B0),
    )
    val Swat = FitTheme(
        id = "swat", name = "Swat Valley", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFEEF4F7), bgBottom = Color(0xFFD6E6D8),
        blobs = listOf(Color(0xFFC8DCC9), Color(0xFFDDEBF2), Color(0xFFFFFFFF), Color(0xFFA9C8AE)),
        accent = Color(0xFF1F7A4D), accentBright = Color(0xFF2E9462), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x99FFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), warning = Color(0xFFD98A00), water = Color(0xFF1C7EE0), steps = Color(0xFF2E9462),
    )

    /** Theme families shown in the picker (R16: trimmed to essentials + dark, hero, motor, nature, Pakistan). */
    val families: List<Pair<String, List<FitTheme>>> by lazy {
        listOf(
            "Essentials" to listOf(Kinetic, Blaze, Cobalt, Graphite, Evergreen, Porcelain, Sandstone, Cloud),
            "Dark & AMOLED" to listOf(MoreThemes.TrueBlack, Carbon, Cosmic, Ember),
            "Hero moods" to listOf(MoreThemes.WebCrimson, MoreThemes.Adamant, MoreThemes.SkyGuardian, MoreThemes.MidnightVigilante, MoreThemes.ReactorGold),
            "Cars & bikes" to listOf(MoreThemes.RacingRed, MoreThemes.CarbonLime, MoreThemes.MidnightSupercar, MoreThemes.CafeRacer, MoreThemes.RallyBlue),
            "Nature" to listOf(Forest, Oceanic, Aurora, Hunza, Desert, Sakura),
            "Pakistan" to listOf(TruckArt, Badshahi),
        )
    }
    val all: List<FitTheme> by lazy { families.flatMap { it.second } }
    fun byId(id: String): FitTheme = (MoreThemes.legacy[id] ?: id).let { k -> all.firstOrNull { it.id == k } } ?: Kinetic
}
