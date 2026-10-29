package com.fuelx.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalAccent = compositionLocalOf { AccentPrimary }

private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = FuelXColors.OnAccent,
    secondary = AccentSecondary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = FuelXColors.Error
)

@Composable
fun FuelXTheme(
    accentColor: Color = AccentPrimary,
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DarkBackground.toArgb()
                window.navigationBarColor = DarkBackground.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalAccent provides accentColor) {
        MaterialTheme(
            colorScheme = DarkColorScheme.copy(
                primary = accentColor,
                onPrimary = FuelXColors.OnAccent
            ),
            typography = Typography,
            content = content
        )
    }
}
