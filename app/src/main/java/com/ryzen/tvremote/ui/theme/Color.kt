package com.ryzen.tvremote.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand palette: periwinkle on graphite (taken from the launcher icon), with a warm peach tertiary.

val BrandDarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF1A2B6B),
    primaryContainer = Color(0xFF33438A),
    onPrimaryContainer = Color(0xFFDDE1FF),
    inversePrimary = Color(0xFF4B5BA4),
    secondary = Color(0xFFC2C5DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF3D4259),
    onSecondaryContainer = Color(0xFFDEE1F9),
    tertiary = Color(0xFFFFB59B),
    onTertiary = Color(0xFF55200C),
    tertiaryContainer = Color(0xFF723520),
    onTertiaryContainer = Color(0xFFFFDBCF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A1C),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    inverseSurface = Color(0xFFE3E2E9),
    inverseOnSurface = Color(0xFF2F3036),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainer = Color(0xFF1D1F26),
    surfaceContainerHigh = Color(0xFF282A31),
    surfaceContainerHighest = Color(0xFF33353D)
)

val BrandLightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4B5BA4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF32438A),
    inversePrimary = Color(0xFFB6C4FF),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDEE1F9),
    onSecondaryContainer = Color(0xFF424659),
    tertiary = Color(0xFF8F4C34),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF723520),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    inverseSurface = Color(0xFF2F3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    surfaceDim = Color(0xFFDAD9E0),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4),
    surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E2E9)
)

/** Swaps the dark surfaces for true black, keeping the accent colours. */
fun ColorScheme.asPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0C10),
    surfaceContainer = Color(0xFF111217),
    surfaceContainerHigh = Color(0xFF191A20),
    surfaceContainerHighest = Color(0xFF22232A)
)

/** Status colours Material doesn't define. */
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color
)

val DarkStatusColors = StatusColors(
    success = Color(0xFF8EDBA6),
    onSuccess = Color(0xFF0B3A1E),
    warning = Color(0xFFF2C56E)
)

val LightStatusColors = StatusColors(
    success = Color(0xFF2F7D4F),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFF8A5A00)
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatusColors }
