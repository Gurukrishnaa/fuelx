package com.fuelx.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FuelX Design System Color Tokens (Exaggerated Minimalism, Dark-Only).
 * Pure solid tonal surfaces — strictly zero gradients.
 */
object FuelXColors {
    val Bg = Color(0xFF0A0A0B)             // App background (near OLED black)
    val Surface1 = Color(0xFF121214)       // Resting cards, list rows
    val Surface2 = Color(0xFF1A1A1D)       // Elevated cards, sheets, mini player
    val Surface3 = Color(0xFF232327)       // Pressed state, chips, search field
    val Outline = Color(0xFF2C2C31)        // Hairline dividers (rare)
    val TextPrimary = Color(0xFFF4F4EF)    // Warm white for titles & primary content
    val TextSecondary = Color(0xFFB4B4B0)  // Subtitles & artist names
    val TextTertiary = Color(0xFF8A8A8F)   // Captions & timestamps
    val Error = Color(0xFFFF6B6B)          // Actionable errors
    val AccentDefault = Color(0xFFD4FF4F)  // "Volt" signature accent
    val OnAccent = Color(0xFF0A0A0B)       // Text/icons on accent fills
}

// Aliases mapped directly to FuelX design tokens to preserve existing callers seamlessly
val DarkBackground = FuelXColors.Bg
val DarkSurface = FuelXColors.Surface1
val DarkSurfaceVariant = FuelXColors.Surface2
val DarkBorder = FuelXColors.Outline

val AccentPrimary = FuelXColors.AccentDefault
val AccentSecondary = Color(0xFF818CF8)
val TextPrimary = FuelXColors.TextPrimary
val TextSecondary = FuelXColors.TextSecondary
val TextMuted = FuelXColors.TextTertiary

val PlaybackGreen = Color(0xFF22C55E)
