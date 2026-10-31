package com.fuelx.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.ui.theme.LocalAccent

/**
 * Ultra-sleek, frosted liquid-glass notification pill matching FuelX aesthetic.
 * Automatically adapts iconography and accent glows to the user's action.
 */
@Composable
fun GlassSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    val message = snackbarData.visuals.message
    val actionLabel = snackbarData.visuals.actionLabel
    val accent = LocalAccent.current

    val (icon, iconTint) = resolveSnackbarIcon(message, accent)

    Surface(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = Color.Black.copy(alpha = 0.65f),
                ambientColor = Color.Black.copy(alpha = 0.40f)
            )
            .border(
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.White.copy(alpha = 0.06f)
                        )
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE1E222F),
                            Color(0xEE12141D)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Frosted circular badge housing action icon
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(iconTint.copy(alpha = 0.16f), CircleShape)
                        .border(BorderStroke(0.8.dp, iconTint.copy(alpha = 0.35f)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp,
                        fontSize = 13.5.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!actionLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = accent
                        ),
                        modifier = Modifier
                            .clickable { snackbarData.performAction() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun resolveSnackbarIcon(message: String, defaultAccent: Color): Pair<ImageVector, Color> {
    val lower = message.lowercase()
    val isRemoval = lower.contains("remove") || lower.contains("delete") || lower.contains("clear")

    return when {
        lower.contains("favourite") || lower.contains("favorite") -> {
            if (isRemoval) {
                Icons.Rounded.FavoriteBorder to Color.White.copy(alpha = 0.70f)
            } else {
                Icons.Rounded.Favorite to Color(0xFFFF3366) // Vibrant cherry red/pink
            }
        }
        lower.contains("download") -> {
            if (isRemoval) {
                Icons.Rounded.DeleteOutline to Color.White.copy(alpha = 0.70f)
            } else {
                Icons.Rounded.DownloadDone to Color(0xFF00E676) // Vivid emerald green
            }
        }
        lower.contains("playlist") -> {
            if (isRemoval) {
                Icons.Rounded.DeleteOutline to Color.White.copy(alpha = 0.70f)
            } else {
                Icons.AutoMirrored.Rounded.PlaylistAddCheck to defaultAccent
            }
        }
        lower.contains("queue") -> {
            if (isRemoval) {
                Icons.Rounded.DeleteOutline to Color.White.copy(alpha = 0.70f)
            } else {
                Icons.AutoMirrored.Rounded.QueueMusic to defaultAccent
            }
        }
        lower.contains("repeat") || lower.contains("loop") -> {
            if (lower.contains("off")) {
                Icons.Rounded.Repeat to Color.White.copy(alpha = 0.70f)
            } else {
                Icons.Rounded.Repeat to defaultAccent
            }
        }
        lower.contains("error") || lower.contains("fail") || lower.contains("unable") || lower.contains("couldn't") -> {
            Icons.Rounded.WarningAmber to Color(0xFFFF5252)
        }
        else -> {
            Icons.Rounded.CheckCircle to defaultAccent
        }
    }
}

