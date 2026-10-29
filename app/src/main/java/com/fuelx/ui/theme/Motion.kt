package com.fuelx.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

val PressSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 500f)
val ArtworkSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 300f)
val SheetSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 400f)

/**
 * Signature press feedback from FuelX Design Spec:
 * Scales the element down to [pressedScale] (default 0.96) on touch with spring physics,
 * then smoothly settles back to 1.0 on release.
 * Executes in the graphicsLayer (draw phase) to avoid recomposition.
 */
fun Modifier.pressableScale(
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "pressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
}

