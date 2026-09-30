package com.myfit.tracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class BackdropArt { AURORA, GRID, WAVES, LANDSCAPE, FROST }

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

    val all = listOf(Crimson, Lime, DeepBlue, Nature, Frost)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: Crimson
}
