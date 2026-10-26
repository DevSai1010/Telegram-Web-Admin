package com.msma.tgpanel.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
import com.msma.tgpanel.R

/*
 * Material Design 3 theme. The colour roles are the SAME ones the web panel uses
 * (static/css/variables.css, blue seed #4da3ff), so both clients look like one product.
 * A later pass can swap these two schemes for dynamic / user-seeded ones without touching any screen,
 * because screens only ever use MaterialTheme.colorScheme roles and [ExtColors].
 */

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FCAFF), onPrimary = Color(0xFF003259),
    primaryContainer = Color(0xFF00497E), onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBCC7DC), onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4858), onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD9BDE2), onTertiary = Color(0xFF3C2947),
    tertiaryContainer = Color(0xFF54405F), onTertiaryContainer = Color(0xFFF6D9FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF10131A), onBackground = Color(0xFFE1E2E9),
    surface = Color(0xFF10131A), onSurface = Color(0xFFE1E2E9),
    surfaceVariant = Color(0xFF43474E), onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199), outlineVariant = Color(0xFF43474E),
    inverseSurface = Color(0xFFE1E2E9), inverseOnSurface = Color(0xFF2E3036), inversePrimary = Color(0xFF1A6BB2),
    surfaceDim = Color(0xFF10131A), surfaceBright = Color(0xFF363A42),
    surfaceContainerLowest = Color(0xFF0B0E14), surfaceContainerLow = Color(0xFF181C24),
    surfaceContainer = Color(0xFF1C2028), surfaceContainerHigh = Color(0xFF262B33), surfaceContainerHighest = Color(0xFF31363E),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1A6BB2), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF), onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF53607A), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8), onSecondaryContainer = Color(0xFF101C2F),
    tertiary = Color(0xFF6C5677), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6D9FF), onTertiaryContainer = Color(0xFF261430),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FF), onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFF9F9FF), onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFDFE2EB), onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F), outlineVariant = Color(0xFFC3C6CF),
    inverseSurface = Color(0xFF2E3036), inverseOnSurface = Color(0xFFF1F0F7), inversePrimary = Color(0xFF9FCAFF),
    surfaceDim = Color(0xFFD9D9E0), surfaceBright = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEEF4), surfaceContainerHigh = Color(0xFFE7E8EE), surfaceContainerHighest = Color(0xFFE2E2E8),
)

/** Colours M3 has no role for (the "bot / panel / AI" green bubble, warnings) — defined as a harmonised custom colour. */
data class ExtColors(
    val green: Color, val onGreen: Color, val greenContainer: Color, val onGreenContainer: Color,
    val amber: Color, val amberContainer: Color, val onAmberContainer: Color,
)

private val DarkExt = ExtColors(
    green = Color(0xFF8FD9A8), onGreen = Color(0xFF003919), greenContainer = Color(0xFF0C4A2C), onGreenContainer = Color(0xFFA9F5C0),
    amber = Color(0xFFFFD58A), amberContainer = Color(0xFF5E4100), onAmberContainer = Color(0xFFFFDEA8),
)
private val LightExt = ExtColors(
    green = Color(0xFF1B6C42), onGreen = Color(0xFFFFFFFF), greenContainer = Color(0xFFBDF2CB), onGreenContainer = Color(0xFF002111),
    amber = Color(0xFF7B5800), amberContainer = Color(0xFFFFDEA8), onAmberContainer = Color(0xFF271900),
)

val LocalExtColors = staticCompositionLocalOf { DarkExt }

private val Vazir = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.vazir() = copy(fontFamily = Vazir)

private fun appTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.vazir(), displayMedium = d.displayMedium.vazir(), displaySmall = d.displaySmall.vazir(),
        headlineLarge = d.headlineLarge.vazir(), headlineMedium = d.headlineMedium.vazir(), headlineSmall = d.headlineSmall.vazir(),
        titleLarge = d.titleLarge.vazir().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = d.titleMedium.vazir().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = d.titleSmall.vazir().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = d.bodyLarge.vazir(), bodyMedium = d.bodyMedium.vazir(), bodySmall = d.bodySmall.vazir(),
        labelLarge = d.labelLarge.vazir().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = d.labelMedium.vazir().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = d.labelSmall.vazir().copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun TgPanelTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    CompositionLocalProvider(LocalExtColors provides (if (dark) DarkExt else LightExt)) {
        MaterialTheme(colorScheme = scheme, typography = appTypography(), content = content)
    }
}
