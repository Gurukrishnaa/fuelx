package com.fuelx.ui.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.model.Track
import com.fuelx.ui.theme.*
import com.fuelx.util.ParsedLyrics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricsScreen(
    track: Track,
    parsedLyrics: ParsedLyrics?,
    isLoading: Boolean,
    errorMessage: String?,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledAway by remember { mutableStateOf(false) }

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            userScrolledAway = true
        }
    }

    val lines = parsedLyrics?.lines.orEmpty()

    // Calculate active line index based on playback position
    val activeIndex = remember(currentPositionMs, lines) {
        val idx = lines.indexOfLast { it.timeMs <= currentPositionMs }
        if (idx >= 0) idx else 0
    }

    // Smooth auto-scroll following the singer (unless user dragged away)
    LaunchedEffect(activeIndex, userScrolledAway) {
        if (!userScrolledAway && lines.isNotEmpty() && activeIndex in lines.indices) {
            val targetScroll = maxOf(0, activeIndex - 2)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with inbuilt Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lyrics",
                        style = MaterialTheme.typography.titleLarge,
                        color = AccentPrimary
                    )
                    Text(
                        text = "${track.title} • ${track.artist}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Body States
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentPrimary, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Loading synchronized lyrics...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = errorMessage, color = TextSecondary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }

                parsedLyrics?.isInstrumental == true -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Instrumental Track",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Enjoy the music — no lyrics for this song",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                }

                parsedLyrics?.isSynchronized == false && !parsedLyrics.plainText.isNullOrBlank() -> {
                    // Plain text lyrics
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 24.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DarkSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = "Unsynchronized Lyrics",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = parsedLyrics.plainText,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 30.sp,
                                color = TextPrimary
                            )
                        )
                    }
                }

                lines.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No lyrics found for this song",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }

                else -> {
                    // Spotify / YouTube Music Style: Word-by-Word Highlighted Lyrics
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        itemsIndexed(lines) { index, line ->
                            val isActiveLine = index == activeIndex
                            val isPastLine = index < activeIndex

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        userScrolledAway = false
                                        onSeekTo(line.timeMs)
                                    }
                                    .background(
                                        if (isActiveLine) DarkSurfaceVariant.copy(alpha = 0.35f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isActiveLine) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = "Active line",
                                        tint = AccentPrimary,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .padding(end = 8.dp)
                                    )
                                }

                                if (line.words.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        line.words.forEach { word ->
                                            val isWordActive = isActiveLine && (currentPositionMs in word.startTimeMs..word.endTimeMs)
                                            val isWordPast = isPastLine || (isActiveLine && currentPositionMs > word.endTimeMs)

                                            val targetColor = when {
                                                isWordActive -> Color.White
                                                isWordPast && isActiveLine -> AccentPrimary
                                                isWordPast -> TextSecondary.copy(alpha = 0.7f)
                                                isActiveLine -> TextMuted.copy(alpha = 0.5f)
                                                else -> TextMuted.copy(alpha = 0.35f)
                                            }

                                            val animatedColor by animateColorAsState(
                                                targetValue = targetColor,
                                                animationSpec = tween(durationMillis = 150),
                                                label = "wordColor"
                                            )

                                            Text(
                                                text = word.text,
                                                color = animatedColor,
                                                fontWeight = when {
                                                    isWordActive -> FontWeight.Black
                                                    isWordPast && isActiveLine -> FontWeight.Bold
                                                    isActiveLine -> FontWeight.SemiBold
                                                    else -> FontWeight.Normal
                                                },
                                                fontSize = when {
                                                    isWordActive -> 25.sp
                                                    isActiveLine -> 22.sp
                                                    else -> 18.sp
                                                },
                                                modifier = Modifier.clickable {
                                                    userScrolledAway = false
                                                    onSeekTo(word.startTimeMs)
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    // Fallback for lines without word segmentation
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = if (isActiveLine) 24.sp else 18.sp,
                                            lineHeight = if (isActiveLine) 32.sp else 26.sp,
                                            fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isActiveLine -> AccentPrimary
                                                isPastLine -> TextSecondary
                                                else -> TextMuted.copy(alpha = 0.5f)
                                            }
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating "Resume Sync" Chip if user manually scrolled away
        AnimatedVisibility(
            visible = userScrolledAway && lines.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            Button(
                onClick = {
                    userScrolledAway = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sync to current line",
                    color = DarkBackground,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
