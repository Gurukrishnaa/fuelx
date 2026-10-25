package com.fuelx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.fuelx.ui.theme.AccentPrimary
import com.fuelx.ui.theme.DarkSurfaceVariant

/**
 * Reusable, cached album artwork component powered by Coil Compose.
 * Displays cross-fading remote artwork with a styled dark fallback placeholder.
 */
@Composable
fun TrackArtwork(
    artworkUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    placeholderColor: Color = DarkSurfaceVariant,
    placeholderIcon: ImageVector? = null,
    placeholderText: String = "♪"
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderColor),
        contentAlignment = Alignment.Center
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(placeholderColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (placeholderIcon != null) {
                            Icon(
                                imageVector = placeholderIcon,
                                contentDescription = null,
                                tint = AccentPrimary.copy(alpha = 0.5f)
                            )
                        } else {
                            Text(
                                text = placeholderText,
                                color = AccentPrimary.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(placeholderColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (placeholderIcon != null) {
                            Icon(
                                imageVector = placeholderIcon,
                                contentDescription = null,
                                tint = AccentPrimary
                            )
                        } else {
                            Text(
                                text = placeholderText,
                                color = AccentPrimary,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            )
        } else {
            if (placeholderIcon != null) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    tint = AccentPrimary
                )
            } else {
                Text(
                    text = placeholderText,
                    color = AccentPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
