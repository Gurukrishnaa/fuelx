package com.fuelx.ui.splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fuelx.ui.brand.drawFuelXMark
import com.fuelx.ui.theme.FuelXColors
import com.fuelx.ui.theme.Inter
import com.fuelx.ui.theme.SpaceGrotesk
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val WORD = "fuelx"

/**
 * Timeline (ms):
 *   0-380    accent stroke draws
 *   120-500  white stroke draws
 *   380-900  letters rise in, 45ms apart
 *   ~825     accent dot pops (spring)
 *   ~925     tagline fades in
 *   +250     hold
 *   +250     whole splash fades out, revealing the app that loaded underneath
 */
@Composable
fun FuelXSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val front = remember { Animatable(0f) }
    val back = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val letters = remember { List(WORD.length) { Animatable(0f) } }
    val exit = remember { Animatable(1f) }

    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            front.snapTo(1f); back.snapTo(1f); dot.snapTo(1f); tagline.snapTo(1f)
            letters.forEach { it.snapTo(1f) }
            delay(500)
        } else {
            coroutineScope {
                launch { front.animateTo(1f, tween(380, easing = Emphasized)) }
                launch { delay(120); back.animateTo(1f, tween(380, easing = Emphasized)) }
                letters.forEachIndexed { i, a ->
                    launch { delay(380L + i * 45L); a.animateTo(1f, tween(320, easing = Emphasized)) }
                }
                launch {
                    delay(380L + WORD.length * 45L + 120L)
                    dot.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
                }
                launch {
                    delay(925L)
                    tagline.animateTo(1f, tween(300, easing = Emphasized))
                }
            }
            delay(250)
        }
        exit.animateTo(0f, tween(250))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .background(FuelXColors.Bg)                 // solid #0A0A0B, no gradient
            .pointerInput(Unit) { },                    // swallow touches while visible
        contentAlignment = Alignment.Center
    ) {
        // Mark at the exact screen center, same spot as the system splash icon.
        Canvas(Modifier.size(120.dp)) {
            drawFuelXMark(
                knockout = FuelXColors.Bg,
                front = front.value,
                back = back.value
            )
        }

        // Wordmark below the mark
        Row(
            modifier = Modifier.offset(y = 92.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            WORD.forEachIndexed { i, ch ->
                Text(
                    text = ch.toString(),
                    style = TextStyle(
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 40.sp,
                        letterSpacing = (-0.02).em,
                        color = FuelXColors.TextPrimary
                    ),
                    modifier = Modifier.graphicsLayer {
                        alpha = letters[i].value
                        translationY = (1f - letters[i].value) * 12.dp.toPx()
                    }
                )
            }
            Box(
                Modifier
                    .padding(start = 3.dp, bottom = 8.dp)
                    .size(9.dp)
                    .graphicsLayer { scaleX = dot.value; scaleY = dot.value }
                    .background(FuelXColors.AccentDefault, CircleShape)
            )
        }

        // Tagline in the tertiary text color (#8A8A8F)
        Text(
            text = "Your music, uninterrupted",
            style = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 0.02.em,
                color = FuelXColors.TextTertiary
            ),
            modifier = Modifier
                .offset(y = 138.dp)
                .graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 6.dp.toPx()
                }
        )
    }
}

