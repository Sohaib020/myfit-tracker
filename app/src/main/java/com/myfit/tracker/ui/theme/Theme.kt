package com.myfit.tracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class BackdropArt { AURORA, GRID, WAVES, LANDSCAPE, FROST, BOREALIS, OCEAN, SAKURA, NEON_CITY, DUNES, GALAXY, FOREST, LAVA, RAIN, MINT, GOLD, ARCTIC, CYBER, DESERT_NIGHT, LOTUS, MONSOON }

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
)

object Themes {
    val Crimson = FitTheme(
        id = "crimson", name = "Crimson Glass", isLight = false, art = BackdropArt.AURORA,
        bgTop = Color(0xFF2A0507), bgBottom = Color(0xFF0B0203),
        blobs = listOf(Color(0xFF9C0F16), Color(0xFFD01A22), Color(0xFF5A070B), Color(0xFF7A0A10)),
        accent = Color(0xFFD9232A), accentBright = Color(0xFFFF4A50), onAccent = Color.White,
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1AFFFFFF), glassFallback = Color(0xCC2A0B0D),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Lime = FitTheme(
        id = "lime", name = "Lime Night", isLight = false, art = BackdropArt.GRID,
        bgTop = Color(0xFF2B2B2B), bgBottom = Color(0xFF0F0F0F),
        blobs = listOf(Color(0xFF3C3C3C), Color(0xFF4A5A12), Color(0xFF262626), Color(0xFF333333)),
        accent = Color(0xFFD6F53F), accentBright = Color(0xFFE8FF6E), onAccent = Color(0xFF111111),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x14FFFFFF), glassFallback = Color(0xE0222222),
        rimHigh = Color(0x55FFFFFF), rimLow = Color(0x12FFFFFF),
        success = Color(0xFFD6F53F), steps = Color(0xFFD6F53F),
    )

    val DeepBlue = FitTheme(
        id = "deepblue", name = "Deep Blue", isLight = false, art = BackdropArt.WAVES,
        bgTop = Color(0xFF041552), bgBottom = Color(0xFF01030F),
        blobs = listOf(Color(0xFF0B3BD6), Color(0xFF1B63FF), Color(0xFF061A73), Color(0xFF2A0F6B)),
        accent = Color(0xFF3D7BFF), accentBright = Color(0xFF7AA6FF), onAccent = Color.White,
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1A6FA0FF), glassFallback = Color(0xE00A1845),
        rimHigh = Color(0x7094B8FF), rimLow = Color(0x1A94B8FF),
    )

    val Nature = FitTheme(
        id = "nature", name = "Coastline", isLight = false, art = BackdropArt.LANDSCAPE,
        bgTop = Color(0xFFBFE6E4), bgBottom = Color(0xFF2E6B2A),
        blobs = listOf(Color(0xFF1FB5AE), Color(0xFF7CB83E), Color(0xFF4E8A2A), Color(0xFFE8F4C8)),
        accent = Color(0xFFC6F25A), accentBright = Color(0xFFDEFF8A), onAccent = Color(0xFF14240A),
        text = Color(0xFFFFFFFF), textDim = Color(0xD9FFFFFF), textFaint = Color(0x80FFFFFF),
        glassTint = Color(0x4D0E2A22), glassFallback = Color(0xE0183A30),
        rimHigh = Color(0x80FFFFFF), rimLow = Color(0x1FFFFFFF),
    )

    val Frost = FitTheme(
        id = "frost", name = "Frost Light", isLight = true, art = BackdropArt.FROST,
        bgTop = Color(0xFFF6F7F9), bgBottom = Color(0xFFE6E8EE),
        blobs = listOf(Color(0xFFFFC9CF), Color(0xFFD7D2FF), Color(0xFFC8ECFF), Color(0xFFFFE6C4)),
        accent = Color(0xFFE5383B), accentBright = Color(0xFFFF5A5D), onAccent = Color.White,
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x8CFFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
        success = Color(0xFF17A85A), water = Color(0xFF1C7EE0),
    )


    val Borealis = FitTheme(
        id = "borealis", name = "Aurora Borealis", isLight = false, art = BackdropArt.BOREALIS,
        bgTop = Color(0xFF020716), bgBottom = Color(0xFF06222B),
        blobs = listOf(Color(0xFF3DFFB0), Color(0xFF8CFFD2), Color(0xFF020716), Color(0xFF06222B)),
        accent = Color(0xFF3DFFB0), accentBright = Color(0xFF8CFFD2), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1A9CFFE0), glassFallback = Color(0xE0081C22),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Ocean = FitTheme(
        id = "ocean", name = "Ocean Depths", isLight = false, art = BackdropArt.OCEAN,
        bgTop = Color(0xFF0B5E8A), bgBottom = Color(0xFF001220),
        blobs = listOf(Color(0xFF22D3EE), Color(0xFF7CEBFF), Color(0xFF0B5E8A), Color(0xFF001220)),
        accent = Color(0xFF22D3EE), accentBright = Color(0xFF7CEBFF), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1A7CEBFF), glassFallback = Color(0xE0062A3A),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Sakura = FitTheme(
        id = "sakura", name = "Sakura Rain", isLight = true, art = BackdropArt.SAKURA,
        bgTop = Color(0xFFFFF1F5), bgBottom = Color(0xFFF8C6D6),
        blobs = listOf(Color(0xFFFF4F86), Color(0xFFFF7FA6), Color(0xFFFFF1F5), Color(0xFFF8C6D6)),
        accent = Color(0xFFFF4F86), accentBright = Color(0xFFFF7FA6), onAccent = Color(0xFFFFFFFF),
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x8CFFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
    )

    val Neon = FitTheme(
        id = "neon", name = "Neon City", isLight = false, art = BackdropArt.NEON_CITY,
        bgTop = Color(0xFF12032E), bgBottom = Color(0xFF2A0845),
        blobs = listOf(Color(0xFFFF2E97), Color(0xFFFF6BB5), Color(0xFF12032E), Color(0xFF2A0845)),
        accent = Color(0xFFFF2E97), accentBright = Color(0xFFFF6BB5), onAccent = Color(0xFFFFFFFF),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1AFF6BB5), glassFallback = Color(0xE01A0833),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Dunes = FitTheme(
        id = "dunes", name = "Sunset Dunes", isLight = false, art = BackdropArt.DUNES,
        bgTop = Color(0xFFFF7E5F), bgBottom = Color(0xFFB9603A),
        blobs = listOf(Color(0xFFFFD27A), Color(0xFFFFE3A6), Color(0xFFFF7E5F), Color(0xFFB9603A)),
        accent = Color(0xFFFFD27A), accentBright = Color(0xFFFFE3A6), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x40391A0E), glassFallback = Color(0xE0482414),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Galaxy = FitTheme(
        id = "galaxy", name = "Galaxy", isLight = false, art = BackdropArt.GALAXY,
        bgTop = Color(0xFF05010F), bgBottom = Color(0xFF0B0320),
        blobs = listOf(Color(0xFFB57CFF), Color(0xFFD5B3FF), Color(0xFF05010F), Color(0xFF0B0320)),
        accent = Color(0xFFB57CFF), accentBright = Color(0xFFD5B3FF), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1AB57CFF), glassFallback = Color(0xE0120828),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Forest = FitTheme(
        id = "forest", name = "Misty Forest", isLight = false, art = BackdropArt.FOREST,
        bgTop = Color(0xFF1E4A3A), bgBottom = Color(0xFF071710),
        blobs = listOf(Color(0xFF7CFFB2), Color(0xFFB2FFD3), Color(0xFF1E4A3A), Color(0xFF071710)),
        accent = Color(0xFF7CFFB2), accentBright = Color(0xFFB2FFD3), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1A9CFFC4), glassFallback = Color(0xE00E261C),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Lava = FitTheme(
        id = "lava", name = "Lava Lamp", isLight = false, art = BackdropArt.LAVA,
        bgTop = Color(0xFF3A0B02), bgBottom = Color(0xFF120300),
        blobs = listOf(Color(0xFFFF7A1A), Color(0xFFFFA25E), Color(0xFF3A0B02), Color(0xFF120300)),
        accent = Color(0xFFFF7A1A), accentBright = Color(0xFFFFA25E), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1AFFA25E), glassFallback = Color(0xE0280A02),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Rain = FitTheme(
        id = "rain", name = "Midnight Rain", isLight = false, art = BackdropArt.RAIN,
        bgTop = Color(0xFF0D1428), bgBottom = Color(0xFF04070F),
        blobs = listOf(Color(0xFF7AA6FF), Color(0xFFA9C6FF), Color(0xFF0D1428), Color(0xFF04070F)),
        accent = Color(0xFF7AA6FF), accentBright = Color(0xFFA9C6FF), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1A9CB8FF), glassFallback = Color(0xE00A1122),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Mint = FitTheme(
        id = "mint", name = "Mint Breeze", isLight = true, art = BackdropArt.MINT,
        bgTop = Color(0xFFF1FFF9), bgBottom = Color(0xFFD5F5E8),
        blobs = listOf(Color(0xFF12A97D), Color(0xFF2FD3A2), Color(0xFFF1FFF9), Color(0xFFD5F5E8)),
        accent = Color(0xFF12A97D), accentBright = Color(0xFF2FD3A2), onAccent = Color(0xFFFFFFFF),
        text = Color(0xFF16161A), textDim = Color(0xB316161A), textFaint = Color(0x6616161A),
        glassTint = Color(0x8CFFFFFF), glassFallback = Color(0xF2FFFFFF),
        rimHigh = Color(0xFFFFFFFF), rimLow = Color(0x33FFFFFF),
    )

    val Gold = FitTheme(
        id = "gold", name = "Royal Gold", isLight = false, art = BackdropArt.GOLD,
        bgTop = Color(0xFF1C1407), bgBottom = Color(0xFF070502),
        blobs = listOf(Color(0xFFF5C451), Color(0xFFFFDE8A), Color(0xFF1C1407), Color(0xFF070502)),
        accent = Color(0xFFF5C451), accentBright = Color(0xFFFFDE8A), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x14FFE0A0), glassFallback = Color(0xE0181206),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Arctic = FitTheme(
        id = "arctic", name = "Arctic Ice", isLight = false, art = BackdropArt.ARCTIC,
        bgTop = Color(0xFF9FD8F5), bgBottom = Color(0xFF123B5A),
        blobs = listOf(Color(0xFF7FDBFF), Color(0xFFB8ECFF), Color(0xFF9FD8F5), Color(0xFF123B5A)),
        accent = Color(0xFF7FDBFF), accentBright = Color(0xFFB8ECFF), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x4D0E3350), glassFallback = Color(0xE0123A55),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Cyber = FitTheme(
        id = "cyber", name = "Cyber Grid", isLight = false, art = BackdropArt.CYBER,
        bgTop = Color(0xFF14002E), bgBottom = Color(0xFF3A0A5E),
        blobs = listOf(Color(0xFF00F0FF), Color(0xFF7CF7FF), Color(0xFF14002E), Color(0xFF3A0A5E)),
        accent = Color(0xFF00F0FF), accentBright = Color(0xFF7CF7FF), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1A00F0FF), glassFallback = Color(0xE0180434),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Desertnight = FitTheme(
        id = "desertnight", name = "Desert Night", isLight = false, art = BackdropArt.DESERT_NIGHT,
        bgTop = Color(0xFF070B22), bgBottom = Color(0xFF15122D),
        blobs = listOf(Color(0xFFFFD27A), Color(0xFFFFE6B0), Color(0xFF070B22), Color(0xFF15122D)),
        accent = Color(0xFFFFD27A), accentBright = Color(0xFFFFE6B0), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1AFFE6B0), glassFallback = Color(0xE0110E26),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Lotus = FitTheme(
        id = "lotus", name = "Lotus Pond", isLight = false, art = BackdropArt.LOTUS,
        bgTop = Color(0xFF123E44), bgBottom = Color(0xFF051A1F),
        blobs = listOf(Color(0xFFFF8FB1), Color(0xFFFFB8CD), Color(0xFF123E44), Color(0xFF051A1F)),
        accent = Color(0xFFFF8FB1), accentBright = Color(0xFFFFB8CD), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1AFFB8CD), glassFallback = Color(0xE00A2A2E),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val Monsoon = FitTheme(
        id = "monsoon", name = "Monsoon", isLight = false, art = BackdropArt.MONSOON,
        bgTop = Color(0xFF2B3442), bgBottom = Color(0xFF0E131B),
        blobs = listOf(Color(0xFF9FB7FF), Color(0xFFC6D4FF), Color(0xFF2B3442), Color(0xFF0E131B)),
        accent = Color(0xFF9FB7FF), accentBright = Color(0xFFC6D4FF), onAccent = Color(0xFF10131A),
        text = Color(0xFFFFFFFF), textDim = Color(0xB3FFFFFF), textFaint = Color(0x66FFFFFF),
        glassTint = Color(0x1AC6D4FF), glassFallback = Color(0xE0161C26),
        rimHigh = Color(0x66FFFFFF), rimLow = Color(0x14FFFFFF),
    )

    val all = listOf(Crimson, Lime, DeepBlue, Nature, Frost, Borealis, Ocean, Sakura, Neon, Dunes, Galaxy, Forest, Lava, Rain, Mint, Gold, Arctic, Cyber, Desertnight, Lotus, Monsoon)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: Crimson
}
