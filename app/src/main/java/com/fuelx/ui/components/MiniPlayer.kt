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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.model.Track
import com.fuelx.ui.theme.*
import com.fuelx.util.cleanArtistString
import kotlin.math.roundToInt

/**
 * Modern Liquid-Glass MiniPlayer with smooth spring animations,
 * swipe gestures for Next/Previous tracks, sleek progress line (without dot),
 * and dynamic artwork ambient glow.
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
    onNextClick: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveDuration = remember(durationMs, currentTrack.durationMs) {
        if (durationMs > 0L) durationMs else (currentTrack.durationMs ?: 0L)
    }

    val currentMs = currentPositionMs.coerceIn(0L, if (effectiveDuration > 0L) effectiveDuration else Long.MAX_VALUE)

    val rawProgress = if (effectiveDuration > 0L) {
        (currentPositionMs.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
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

    // Horizontal swipe gesture state: Swipe Right -> Next, Swipe Left -> Prev
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "miniPlayerDragOffset"
    )
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 24.dp.toPx() }

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
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount
                        dragOffsetX = totalDragX.coerceIn(-120f, 120f)
                    },
                    onDragEnd = {
                        if (totalDragX > swipeThresholdPx) {
                            // Swiped right -> Next song
                            onNextClick()
                        } else if (totalDragX < -swipeThresholdPx) {
                            // Swiped left -> Previous song
                            onPreviousClick()
                        }
                        dragOffsetX = 0f
                        totalDragX = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        totalDragX = 0f
                    }
                )
            }
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
                .offset { IntOffset(animatedDragOffset.roundToInt(), 0) }
                .padding(top = 7.dp, bottom = 9.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 20.dp),
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
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = Color.White.copy(alpha = 0.50f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Play / Pause / Buffering / Retry button (placed inwards to the left)
                IconButton(
                    onClick = if (playbackError != null) onRetryPlayback else onPlayPauseClick,
                    modifier = Modifier
                        .size(38.dp)
                        .scale(playPauseScale)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
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
            }
        }

        // Progress Bar Line along the very bottom edge (flush to bottom, no dot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(2.5.dp)
                .background(Color.White.copy(alpha = 0.14f))
        ) {
            val progressFraction = animatedProgress.coerceIn(0f, 1f)
            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(accentColor.copy(alpha = 0.75f), accentColor)
                            )
                        )
                )
            }
        }
    }
}

