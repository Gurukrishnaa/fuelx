package com.fuelx.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.data.DownloadState
import com.fuelx.model.Track
import com.fuelx.ui.components.TrackArtwork
import com.fuelx.ui.theme.*

@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long? = null,
    isBuffering: Boolean = false,
    isFavorite: Boolean = false,
    playbackError: String? = null,
    downloadState: DownloadState = DownloadState.Idle,
    onToggleFavorite: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onDeleteDownload: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRetryPlayback: () -> Unit = {},
    onOpenLyrics: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Reset seekbar drag state when the track switches
    LaunchedEffect(track.id) {
        isDragging = false
        dragProgress = 0f
    }

    val effectiveDuration = when {
        durationMs != null && durationMs > 0L -> durationMs
        track.durationMs != null && track.durationMs > 0L -> track.durationMs
        else -> 0L
    }
    val progress = if (effectiveDuration > 0L) {
        (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val sliderValue = if (isDragging) dragProgress else progress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { /* Consume taps so they don't bleed through to the screen beneath */ }
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextSecondary
            )

            IconButton(onClick = onOpenLyrics) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = "Lyrics",
                    tint = AccentPrimary
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Large Album Art Box (intercepts touches so tapping cover never changes song)
        TrackArtwork(
            artworkUrl = track.displayArtwork,
            contentDescription = "${track.title} artwork",
            modifier = Modifier
                .size(280.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Explicit no-op: cover tap must not trigger track switch */ },
            shape = RoundedCornerShape(24.dp),
            placeholderColor = AccentSecondary.copy(alpha = 0.2f),
            placeholderText = "♪"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Title & Artist with Favorite Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Download button
                IconButton(
                    onClick = {
                        if (downloadState is DownloadState.Downloaded) {
                            showRemoveDownloadDialog = true
                        } else {
                            onDownloadClick()
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            val p = (downloadState as DownloadState.Downloading).progress
                            if (p in 0f..1f) {
                                CircularProgressIndicator(
                                    progress = { p },
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AccentPrimary
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = AccentPrimary
                                )
                            }
                        }
                        is DownloadState.Downloaded -> {
                            Icon(
                                imageVector = Icons.Rounded.DownloadDone,
                                contentDescription = "Downloaded offline",
                                tint = AccentPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Download for offline playback",
                                tint = TextSecondary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Favorite button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) AccentPrimary else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        if (playbackError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = AccentSecondary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentSecondary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Error",
                            tint = AccentSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = playbackError,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onRetryPlayback) {
                        Text("Retry", color = AccentPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Seek Slider
        Slider(
            value = sliderValue,
            onValueChange = { newProgress ->
                isDragging = true
                dragProgress = newProgress
            },
            onValueChangeFinished = {
                onSeekTo((dragProgress * effectiveDuration).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = AccentPrimary,
                activeTrackColor = AccentPrimary,
                inactiveTrackColor = DarkSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Time indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayMs = if (isDragging) (dragProgress * effectiveDuration).toLong() else currentPositionMs
            val currSeconds = (displayMs / 1000).coerceAtLeast(0L)
            val currText = "${currSeconds / 60}:${(currSeconds % 60).toString().padStart(2, '0')}"

            val totalText = if (effectiveDuration > 0L) {
                val totalSeconds = (effectiveDuration / 1000).coerceAtLeast(0L)
                "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
            } else {
                "--:--"
            }

            Text(text = currText, style = MaterialTheme.typography.labelSmall)
            Text(text = totalText, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { isShuffle = !isShuffle }) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) AccentPrimary else TextMuted
                )
            }

            IconButton(onClick = onPreviousClick) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Big Play/Pause Button
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onPlayPauseClick, modifier = Modifier.fillMaxSize()) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                            color = DarkBackground
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = DarkBackground,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            IconButton(onClick = onNextClick) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = { isRepeat = !isRepeat }) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeat) AccentPrimary else TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Quick Lyrics pill button
        Button(
            onClick = onOpenLyrics,
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Subtitles,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "View Lyrics", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showRemoveDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Remove Download?",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "This will remove '${track.title}' from your offline downloads and free up storage space.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDownload()
                        showRemoveDownloadDialog = false
                    }
                ) {
                    Text("Remove", color = AccentSecondary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDownloadDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

