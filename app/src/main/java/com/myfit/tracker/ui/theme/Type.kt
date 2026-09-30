package com.myfit.tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.myfit.tracker.R
import com.myfit.tracker.data.prefs.AppSettings

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_extrabold, FontWeight.ExtraBold),
)
val Anton = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))

object FitType {
    val hero = TextStyle(fontFamily = Anton, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = 0.5.sp)
    val display = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp)
    val metric = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp)
    val title = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
    val section = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val caption = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
    val overline = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.2.sp)
}

val LocalFitTheme = staticCompositionLocalOf { Themes.Crimson }
val LocalSettings = staticCompositionLocalOf { AppSettings() }

@Composable
fun MyFitTheme(theme: FitTheme, settings: AppSettings, content: @Composable () -> Unit) {
    val scheme = if (theme.isLight) lightColorScheme(
        primary = theme.accent, onPrimary = theme.onAccent, surface = Color.White,
        onSurface = theme.text, background = theme.bgTop, onBackground = theme.text,
        surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF1F1F4),
    ) else darkColorScheme(
        primary = theme.accent, onPrimary = theme.onAccent, surface = Color(0xFF17171A),
        onSurface = theme.text, background = theme.bgBottom, onBackground = theme.text,
        surfaceContainerHigh = Color(0xFF232327), surfaceContainerHighest = Color(0xFF2C2C31),
        secondaryContainer = theme.accent.copy(alpha = 0.3f),
    )
    val typo = Typography(
        bodyLarge = FitType.body.copy(fontSize = 16.sp), bodyMedium = FitType.body,
        labelLarge = FitType.label.copy(fontSize = 14.sp), titleLarge = FitType.title,
        headlineSmall = FitType.title, labelMedium = FitType.label, labelSmall = FitType.caption,
    )
    CompositionLocalProvider(LocalFitTheme provides theme, LocalSettings provides settings) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
