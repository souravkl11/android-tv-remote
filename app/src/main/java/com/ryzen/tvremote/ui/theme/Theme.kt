package com.ryzen.tvremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun TVRemoteTheme(
    isAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (isAmoled) TrueAmoledColors else StandardDarkColors

    val colorScheme = darkColorScheme(
        primary = colors.accentGreen,
        secondary = colors.trackpadDot,
        tertiary = colors.buttonSurface,
        background = colors.background,
        surface = colors.cardSurface,
        onPrimary = colors.background,
        onSecondary = colors.background,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary
    )

    CompositionLocalProvider(LocalRemoteColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

object RemoteTheme {
    val colors: RemoteAppColors
        @Composable
        get() = LocalRemoteColors.current
}
