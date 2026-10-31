package com.fuelx.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fuelx.ui.theme.FuelXColors
import com.fuelx.ui.theme.SpaceGrotesk

/**
 * The FuelX woven "x".
 *
 * Back stroke  : top-right to bottom-left, TextPrimary (#F4F4EF)
 * Front stroke : top-left to bottom-right, AccentDefault (#D4FF4F)
 * Knockout     : a wider stroke in the SURFACE color under the front stroke, so the
 *                two strokes look woven. Pass the color of whatever the mark sits on.
 *
 * front/back are draw progress (0..1) so the splash can animate the same drawing code.
 */
fun DrawScope.drawFuelXMark(
    knockout: Color,
    front: Float = 1f,
    back: Float = 1f
) {
    val canvas = size.minDimension
    val stroke = canvas * 0.20f                 // 24 / 120
    val gap = stroke * 5f / 3f                  // 40 / 24
    val pad = stroke / 2f
    val max = canvas - pad

    val tl = Offset(pad, pad)
    val br = Offset(max, max)
    val tr = Offset(max, pad)
    val bl = Offset(pad, max)

    if (back > 0f) {
        drawLine(FuelXColors.TextPrimary, tr, lerp(tr, bl, back), stroke, StrokeCap.Round)
    }
    if (front > 0f) {
        val end = lerp(tl, br, front)
        drawLine(knockout, tl, end, gap, StrokeCap.Round)
        drawLine(FuelXColors.AccentDefault, tl, end, stroke, StrokeCap.Round)
    }
}

/** Static mark. Use knockout = the color of the surface behind it. */
@Composable
fun FuelXMark(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    knockout: Color = FuelXColors.Bg
) {
    Canvas(modifier.size(size)) { drawFuelXMark(knockout) }
}

/** Static wordmark: "fuelx" plus the accent dot. Use in the Home header. */
@Composable
fun FuelXWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = "fuelx",
            style = TextStyle(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                letterSpacing = (-0.02).em,
                color = FuelXColors.TextPrimary
            )
        )
        val dot = (fontSize.value * 0.225f).dp
        Box(
            Modifier
                .padding(start = (fontSize.value * 0.075f).dp, bottom = (fontSize.value * 0.2f).dp)
                .size(dot)
                .background(FuelXColors.AccentDefault, CircleShape)
        )
    }
}

/** Mark + wordmark side by side. Good for the Home header and settings/about. */
@Composable
fun FuelXLockup(
    modifier: Modifier = Modifier,
    knockout: Color = FuelXColors.Bg
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FuelXMark(size = 32.dp, knockout = knockout)
        Box(Modifier.size(10.dp))
        FuelXWordmark(fontSize = 26.sp)
    }
}

