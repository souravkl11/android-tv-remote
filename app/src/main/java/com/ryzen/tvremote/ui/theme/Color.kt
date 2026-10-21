package com.ryzen.tvremote.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val DarkOledBackground = Color(0xFF121316)
val RemoteCardSurface = Color(0xFF22242A)
val RemoteTrackpadSurface = Color(0xFF26282F)
val RemoteTrackpadDot = Color(0xFFE2E4E9)
val RemoteButtonSurface = Color(0xFF2B2D35)
val RemoteButtonHighlight = Color(0xFF383B45)
val RemoteAccentGreen = Color(0xFF81C784)
val RemoteInputPillBorder = Color(0xFF4CAF50)
val RemoteTextPrimary = Color(0xFFFFFFFF)
val RemoteTextSecondary = Color(0xFF9AA0A6)
val RemoteDivider = Color(0xFF383B44)
val RemoteRed = Color(0xFFE57373)

data class RemoteAppColors(
    val background: Color,
    val cardSurface: Color,
    val trackpadSurface: Color,
    val trackpadBorder: Color,
    val trackpadDot: Color,
    val buttonSurface: Color,
    val buttonHighlight: Color,
    val accentGreen: Color,
    val inputPillBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val red: Color
)

val StandardDarkColors = RemoteAppColors(
    background = Color(0xFF121316),
    cardSurface = Color(0xFF22242A),
    trackpadSurface = Color(0xFF26282F),
    trackpadBorder = Color.Transparent,
    trackpadDot = Color(0xFFE2E4E9),
    buttonSurface = Color(0xFF2B2D35),
    buttonHighlight = Color(0xFF383B45),
    accentGreen = Color(0xFF81C784),
    inputPillBorder = Color(0xFF4CAF50),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF9AA0A6),
    divider = Color(0xFF383B44),
    red = Color(0xFFE57373)
)

val TrueAmoledColors = RemoteAppColors(
    background = Color(0xFF000000),      // Pure 0-nit pitch black
    cardSurface = Color(0xFF090A0D),     // Deep OLED black card
    trackpadSurface = Color(0xFF000000), // Pure OLED black
    trackpadBorder = Color(0xFF24262E),  // Sharp 1.5dp frame around trackpad
    trackpadDot = Color(0xFFE2E4E9),
    buttonSurface = Color(0xFF121317),   // Deep obsidian button surface
    buttonHighlight = Color(0xFF22242C),
    accentGreen = Color(0xFF81C784),
    inputPillBorder = Color(0xFF4CAF50),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8E929C),
    divider = Color(0xFF1C1D24),
    red = Color(0xFFE57373)
)

val LocalRemoteColors = staticCompositionLocalOf { StandardDarkColors }
