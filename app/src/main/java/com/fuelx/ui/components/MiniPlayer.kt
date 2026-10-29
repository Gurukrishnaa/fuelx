package com.fuelx.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.model.Track
import com.fuelx.ui.theme.*
import com.fuelx.util.cleanArtistString

/**
 * Modern Liquid-Glass MiniPlayer with smooth transitions, interactive thumb seeker,
 * Previous & Next transport controls, and dynamic artwork ambient glow.
 */
@Composable
fun MiniPlayer(
    currentTrack: Track,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    playbackError: String? = null,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    accentColor: Color = AccentPrimary,
    onPlayPauseClick: () -> Unit,
    onRetryPlayback: () -> Unit = onPlayPauseClick,
    onPreviousClick: () -> Unit = {},
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val effectiveDuration = remember(durationMs, currentTrack.durationMs) {
        if (durationMs > 0L) durationMs else (currentTrack.durationMs ?: 0L)
    }

    // Active timestamp calculation
    val currentMs = if (isScrubbing && effectiveDuration > 0L) {
        (scrubFraction * effectiveDuration).toLong().coerceIn(0L, effectiveDuration)
    } else {
        currentPositionMs.coerceIn(0L, if (effectiveDuration > 0L) effectiveDuration else Long.MAX_VALUE)
    }

    val rawProgress = if (effectiveDuration > 0L) {
        (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = if (isScrubbing) scrubFraction else rawProgress,
        animationSpec = if (isScrubbing) snap() else spring(stiffness = Spring.StiffnessLow),
        label = "miniPlayerProgress"
    )

    // Animated button scale when pressing or buffering
    val playPauseScale by animateFloatAsState(
        targetValue = if (isBuffering) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "playScale"
    )

    // Frosted glass layering: smoked acrylic base + dynamic ambient glow + specular glass sheen
    val frostedGlassBase = remember(accentColor) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF141724).copy(alpha = 0.91f),
                Color(0xFF0F111B).copy(alpha = 0.93f)
            )
        )
    }

    val ambientGlowBrush = remember(accentColor) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.32f),
                Color.Transparent,
                accentColor.copy(alpha = 0.12f)
            )
        )
    }

    val frostedSheenBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.25f)
            )
        )
    }

    val glassBorderBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.12f)
            )
        )
    }

    val pillShape = RoundedCornerShape(20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .shadow(
                elevation = 12.dp,
                shape = pillShape,
                ambientColor = Color.Black,
                spotColor = accentColor.copy(alpha = 0.40f)
            )
            .clip(pillShape)
            .border(
                BorderStroke(1.dp, glassBorderBrush),
                shape = pillShape
            )
            .clickable(onClick = onMiniPlayerClick)
    ) {
        // Frosted glass base (diffuses and conceals underlying text while keeping translucent depth)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(frostedGlassBase)
        )

        // Ambient blurred album artwork background for frosted diffusion
        TrackArtwork(
            artworkUrl = currentTrack.displayArtwork,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .blur(radius = 32.dp)
                .alpha(0.35f),
            shape = pillShape,
            placeholderColor = accentColor.copy(alpha = 0.20f)
        )

        // Dynamic accent color bloom
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(ambientGlowBrush)
        )

        // Frosted glass specular top sheen
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(frostedSheenBrush)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art with smooth rounded corner
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(0.7.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                ) {
                    TrackArtwork(
                        artworkUrl = currentTrack.displayArtwork,
                        contentDescription = "${currentTrack.title} thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        placeholderColor = accentColor.copy(alpha = 0.22f),
                        placeholderText = "♪"
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Track info with title, artist, and real-time timestamp
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedContent(
                        targetState = currentTrack.title,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + slideInVertically(animationSpec = tween(220)) { it / 3 })
                                .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutVertically(animationSpec = tween(180)) { -it / 3 })
                        },
                        label = "miniPlayerTitle"
                    ) { targetTitle ->
                        Text(
                            text = targetTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val cleanArtist = remember(currentTrack.artist) {
                        cleanArtistString(currentTrack.artist).ifBlank { "Unknown Artist" }
                    }

                    // Format current and total timestamp
                    val currSeconds = (currentMs / 1000).coerceAtLeast(0L)
                    val currTimeText = "${currSeconds / 60}:${(currSeconds % 60).toString().padStart(2, '0')}"
                    val durSeconds = (effectiveDuration / 1000).coerceAtLeast(0L)
                    val durTimeText = if (durSeconds > 0) "${durSeconds / 60}:${(durSeconds % 60).toString().padStart(2, '0')}" else ""
                    val timeString = if (durTimeText.isNotEmpty()) "$currTimeText / $durTimeText" else currTimeText

                    if (playbackError != null) {
                        Text(
                            text = "Tap to retry • $playbackError",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = FuelXColors.Error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cleanArtist,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = Color.White.copy(alpha = 0.62f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = " • $timeString",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Medium,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = if (isScrubbing) accentColor else Color.White.copy(alpha = 0.50f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Transport Controls: [Previous] [Play/Pause] [Next]
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous song button
                    IconButton(
                        onClick = onPreviousClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play / Pause / Buffering / Retry button
                    IconButton(
                        onClick = if (playbackError != null) onRetryPlayback else onPlayPauseClick,
                        modifier = Modifier
                            .size(36.dp)
                            .scale(playPauseScale)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        if (playbackError != null) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry Playback",
                                tint = FuelXColors.Error,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = accentColor
                            )
                        } else {
                            AnimatedContent(
                                targetState = isPlaying,
                                transitionSpec = {
                                    (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.8f))
                                        .togetherWith(fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.8f))
                                },
                                label = "playPauseIcon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Next song button
                    IconButton(
                        onClick = onNextClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
                            tint = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Interactive progress bar with glowing seeker dot (thumb)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .height(14.dp)
                    .pointerInput(effectiveDuration) {
                        detectTapGestures { offset ->
                            if (effectiveDuration > 0L && size.width > 0) {
                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeekTo((fraction * effectiveDuration).toLong())
                            }
                        }
                    }
                    .pointerInput(effectiveDuration) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                if (effectiveDuration > 0L && size.width > 0) {
                                    isScrubbing = true
                                    scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                if (isScrubbing && effectiveDuration > 0L) {
                                    onSeekTo((scrubFraction * effectiveDuration).toLong())
                                    isScrubbing = false
                                }
                            },
                            onDragCancel = {
                                isScrubbing = false
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                if (size.width > 0) {
                                    scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                val trackWidth = maxWidth
                val progressFraction = animatedProgress.coerceIn(0f, 1f)

                // Background inactive track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                )

                // Active progress line
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(accentColor.copy(alpha = 0.7f), accentColor)
                                )
                            )
                    )
                }

                // Seeker Dot (Thumb)
                val thumbSize by animateDpAsState(
                    targetValue = if (isScrubbing) 12.dp else 9.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "seekerDotSize"
                )

                val thumbOffset = ((trackWidth - thumbSize) * progressFraction).coerceAtLeast(0.dp)

                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(thumbSize)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, accentColor, CircleShape)
                )
            }
        }
    }
}
