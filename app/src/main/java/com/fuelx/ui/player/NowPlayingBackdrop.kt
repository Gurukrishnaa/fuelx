package com.fuelx.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.fuelx.ui.theme.FuelXColors
import com.fuelx.ui.theme.LocalAccent
import com.fuelx.util.ArtworkAccentHelper

/**
 * Ambient Cover-Color Gradient Backdrop for Now Playing Screen:
 * Projects a smooth, organic vertical gradient driven by the track's album cover palette.
 * The cover's dominant color radiates deeply at the top, elegantly easing out and fading
 * into pure OLED dark (#0A0A0B) at the bottom.
 */
@Composable
fun NowPlayingBackdrop(
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    val currentThemeAccent = LocalAccent.current
    var accentColor by remember(artworkUrl) { mutableStateOf(currentThemeAccent) }

    LaunchedEffect(artworkUrl) {
        accentColor = ArtworkAccentHelper.getAccentColor(context, artworkUrl)
    }

    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(
            durationMillis = 700,
            easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        ),
        label = "ambientAccent"
    )

    // Deep OLED background base
    val bgOled = FuelXColors.Bg // #0A0A0B

    // Non-linear color stops: natural atmospheric fade from cover tone down into OLED dark
    val gradientBrush = remember(animatedAccent) {
        Brush.verticalGradient(
            0.00f to lerp(bgOled, animatedAccent, 0.42f),
            0.18f to lerp(bgOled, animatedAccent, 0.30f),
            0.38f to lerp(bgOled, animatedAccent, 0.16f),
            0.58f to lerp(bgOled, animatedAccent, 0.06f),
            0.78f to bgOled,
            1.00f to bgOled
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        content()
    }
}
