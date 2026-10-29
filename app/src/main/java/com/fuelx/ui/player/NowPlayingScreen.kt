package com.fuelx.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.fuelx.data.DownloadState
import com.fuelx.model.Track
import com.fuelx.ui.components.TrackArtwork
import com.fuelx.ui.theme.*
import com.fuelx.util.ParsedLyrics
import com.fuelx.util.cleanArtistString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    queue: List<Track> = emptyList(),
    parsedLyrics: ParsedLyrics? = null,
    isLyricsLoading: Boolean = false,
    lyricsError: String? = null,
    onToggleFavorite: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onDeleteDownload: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRetryPlayback: () -> Unit = {},
    onRetryLyrics: () -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onPlayFromQueue: (Int) -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isShuffle by remember { mutableStateOf(false) }
    var isRepeat by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var isLyricsExpanded by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val accent = LocalAccent.current
    val artworkScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = ArtworkSpring,
        label = "artworkScale"
    )

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

    NowPlayingBackdrop(artworkUrl = track.displayArtwork) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume taps so they don't bleed through to the screen beneath */ }
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Top bar with Symmetrical Frosted Glass Elements (NOW PLAYING mathematically centered)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Left: Back button (42.dp circular frosted button)
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.CenterStart)
            ) {
                IconButton(onClick = onClose, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center: "NOW PLAYING" Title (perfectly centered on the screen)
            Text(
                text = "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.align(Alignment.Center)
            )

            // Right: Queue button (42.dp circular frosted button, symmetrical to Back)
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.CenterEnd)
            ) {
                IconButton(onClick = { showQueueSheet = true }, modifier = Modifier.fillMaxSize()) {
                    BadgedBox(
                        badge = {
                            if (queue.isNotEmpty()) {
                                Badge(
                                    containerColor = accent,
                                    contentColor = FuelXColors.OnAccent
                                ) {
                                    Text(text = queue.size.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (queue.isNotEmpty()) accent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Album Art Box with Frosted Glass Edge Highlight (320dp Prominent Hero)
        Box(
            modifier = Modifier
                .size(320.dp)
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                }
                .clip(RoundedCornerShape(32.dp))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(32.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Explicit no-op: cover tap must not trigger track switch */ }
        ) {
            TrackArtwork(
                artworkUrl = track.displayArtwork,
                contentDescription = "${track.title} artwork",
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(32.dp),
                placeholderColor = Color(0xFF14151B),
                placeholderText = "♪"
            )
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Title & Artist with Symmetrical Glass Action Controls (Favorite & 3-Dot Menu)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                ContinuousScrollText(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 32.sp
                    ),
                    color = Color.White,
                    velocity = 35.dp
                )
                Spacer(modifier = Modifier.height(4.dp))
                val cleanArtist = cleanArtistString(track.artist).ifBlank { "Unknown Artist" }
                val rawAlbum = track.album?.let { cleanArtistString(it) }.orEmpty()
                val cleanAlbum = rawAlbum.takeIf {
                    it.isNotBlank() && 
                    it != track.title && 
                    it != "YouTube Music" && 
                    it != "," &&
                    it != "•" &&
                    !cleanArtist.contains(it, ignoreCase = true)
                }
                val subtitleText = buildString {
                    append(cleanArtist)
                    if (cleanAlbum != null) {
                        append(" • ")
                        append(cleanAlbum)
                    }
                }
                ContinuousScrollText(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    ),
                    color = Color.White.copy(alpha = 0.65f),
                    velocity = 30.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Favorite button in frosted glass circle (42.dp)
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) accent else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3-Dot More Options button in frosted glass circle (42.dp) with Liquid Glass DropdownMenu
                Box {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = Color(0xFF131520).copy(alpha = 0.78f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        shadowElevation = 12.dp,
                        modifier = Modifier
                            .width(210.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        // Add to Playlist
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Add to Playlist",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onAddToPlaylist()
                            }
                        )

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.08f))
                        )

                        // Download / Remove Download
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (downloadState is DownloadState.Downloaded) "Remove Download" else "Download Track",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            leadingIcon = {
                                when (downloadState) {
                                    is DownloadState.Downloading -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = accent
                                        )
                                    }
                                    is DownloadState.Downloaded -> {
                                        Icon(
                                            imageVector = Icons.Rounded.DownloadDone,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                showMoreMenu = false
                                if (downloadState is DownloadState.Downloaded) {
                                    showRemoveDownloadDialog = true
                                } else {
                                    onDownloadClick()
                                }
                            }
                        )
                    }
                }
            }
        }

        if (playbackError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = AccentSecondary.copy(alpha = 0.15f)
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
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = FuelXColors.Surface3
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Time indicators with tabular numbers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayMs = if (isDragging) (dragProgress * effectiveDuration).toLong() else currentPositionMs
            val currSeconds = (displayMs / 1000).coerceAtLeast(0L)
            val currText = "${currSeconds / 60}:${(currSeconds % 60).toString().padStart(2, '0')}"

            val remainingMs = (effectiveDuration - displayMs).coerceAtLeast(0L)
            val remSecs = (remainingMs / 1000).coerceAtLeast(0L)
            val remText = if (effectiveDuration > 0L) {
                "-${remSecs / 60}:${(remSecs % 60).toString().padStart(2, '0')}"
            } else {
                "--:--"
            }

            Text(
                text = currText,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = FuelXColors.TextTertiary
            )
            Text(
                text = remText,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = FuelXColors.TextTertiary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { isShuffle = !isShuffle }) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accent else FuelXColors.TextTertiary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isShuffle) accent else Color.Transparent)
                )
            }

            IconButton(
                onClick = onPreviousClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = FuelXColors.TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Big 80dp Hero Play/Pause Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .pressableScale(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }

                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        strokeWidth = 3.dp,
                        color = FuelXColors.OnAccent
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = FuelXColors.OnAccent,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(
                onClick = onNextClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = FuelXColors.TextPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { isRepeat = !isRepeat }) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) accent else FuelXColors.TextTertiary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isRepeat) accent else Color.Transparent)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Rolling Synchronized Lyrics Card (Liquid Glass)
        RollingLyricsCard(
            parsedLyrics = parsedLyrics,
            isLyricsLoading = isLyricsLoading,
            lyricsError = lyricsError,
            currentPositionMs = currentPositionMs,
            artworkUrl = track.displayArtwork,
            isExpanded = isLyricsExpanded,
            onToggleExpand = { isLyricsExpanded = !isLyricsExpanded },
            onSeekTo = onSeekTo,
            onRetryLyrics = onRetryLyrics
        )

        Spacer(modifier = Modifier.height(36.dp))
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

    // Up Next Queue Sheet
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = DarkSurface,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Header with Title & Clear All
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (queue.isNotEmpty()) "Up Next (${queue.size})" else "Up Next",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    if (queue.isNotEmpty()) {
                        TextButton(onClick = onClearQueue) {
                            Text("Clear", color = AccentSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (queue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Queue is empty",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the queue icon on any song to play it next.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        itemsIndexed(queue) { index, queuedTrack ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlayFromQueue(index)
                                        showQueueSheet = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AccentPrimary,
                                        modifier = Modifier.width(24.dp)
                                    )

                                    Box(
                                        modifier = Modifier.size(44.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TrackArtwork(
                                            artworkUrl = queuedTrack.displayArtwork,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(8.dp),
                                            placeholderColor = DarkSurface,
                                            placeholderText = "♪"
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = queuedTrack.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = queuedTrack.artist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    queuedTrack.formattedDuration?.let { dur ->
                                        Text(
                                            text = dur,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveFromQueue(index) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove from queue",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RollingLyricsCard(
    parsedLyrics: ParsedLyrics?,
    isLyricsLoading: Boolean,
    lyricsError: String?,
    currentPositionMs: Long,
    artworkUrl: String?,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onSeekTo: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    val listState = rememberLazyListState()
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    var userScrolledAway by remember { mutableStateOf(false) }

    val animatedHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isExpanded) 460.dp else 295.dp,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "lyricsCardHeight"
    )

    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            userScrolledAway = true
        }
    }

    LaunchedEffect(userScrolledAway, isUserDragging) {
        if (userScrolledAway && !isUserDragging) {
            delay(4000)
            userScrolledAway = false
        }
    }

    val lines = parsedLyrics?.lines.orEmpty()
    val activeIndex = remember(currentPositionMs, lines) {
        val idx = lines.indexOfLast { it.timeMs <= currentPositionMs }
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(activeIndex, userScrolledAway) {
        if (!userScrolledAway && lines.isNotEmpty() && activeIndex in lines.indices) {
            val target = maxOf(0, activeIndex - 1)
            listState.animateScrollToItem(target)
        }
    }

    // Clean Translucent Lyrics Card
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedHeight),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF14151C).copy(alpha = 0.70f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onToggleExpand() }
                ) {
                    Text(
                        text = "LYRICS",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.8.sp,
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                        contentDescription = if (isExpanded) "Collapse Lyrics" else "Expand Lyrics",
                        tint = Color.White.copy(alpha = 0.60f),
                        modifier = Modifier.size(12.dp)
                    )
                }

                if (userScrolledAway && lines.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.20f),
                        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.40f)),
                        modifier = Modifier.clickable { userScrolledAway = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Sync",
                                tint = accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (parsedLyrics?.isSynchronized == true) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.10f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = "LIVE SYNC",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )

            // Content Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLyricsLoading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = accent,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Loading synchronized lyrics...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    lyricsError != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = lyricsError,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.70f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onRetryLyrics) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", color = accent)
                            }
                        }
                    }

                    parsedLyrics?.isInstrumental == true -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Instrumental Track",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enjoy the music — no lyrics for this song",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.50f)
                            )
                        }
                    }

                    parsedLyrics?.isSynchronized == false && !parsedLyrics.plainText.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = parsedLyrics.plainText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    color = Color.White.copy(alpha = 0.90f)
                                )
                            )
                        }
                    }

                    lines.isEmpty() -> {
                        Text(
                            text = "No lyrics found for this song",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.50f)
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(lines) { index, line ->
                                val isActiveLine = index == activeIndex
                                val isPastLine = index < activeIndex

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isActiveLine) Color.White.copy(alpha = 0.12f)
                                            else Color.Transparent
                                        )
                                        .then(
                                            if (isActiveLine) Modifier.border(
                                                BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                                                RoundedCornerShape(14.dp)
                                            ) else Modifier
                                        )
                                        .clickable {
                                            userScrolledAway = false
                                            onSeekTo(line.timeMs)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                                    isWordPast && isActiveLine -> accent
                                                    isWordPast -> Color.White.copy(alpha = 0.55f)
                                                    isActiveLine -> Color.White.copy(alpha = 0.40f)
                                                    else -> Color.White.copy(alpha = 0.28f)
                                                }

                                                val animatedColor by animateColorAsState(
                                                    targetValue = targetColor,
                                                    animationSpec = tween(durationMillis = 150),
                                                    label = "wordColor"
                                                )

                                                Text(
                                                    text = word.text,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontSize = if (isActiveLine) 17.sp else 15.sp,
                                                        fontWeight = if (isWordActive) FontWeight.Bold else if (isActiveLine) FontWeight.SemiBold else FontWeight.Medium,
                                                        color = animatedColor
                                                    )
                                                )
                                            }
                                        }
                                    } else {
                                        val targetColor = when {
                                            isActiveLine -> Color.White
                                            isPastLine -> Color.White.copy(alpha = 0.55f)
                                            else -> Color.White.copy(alpha = 0.30f)
                                        }

                                        val animatedColor by animateColorAsState(
                                            targetValue = targetColor,
                                            animationSpec = tween(durationMillis = 200),
                                            label = "lineColor"
                                        )

                                        Text(
                                            text = line.text,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = if (isActiveLine) 17.sp else 15.sp,
                                                fontWeight = if (isActiveLine) FontWeight.Bold else FontWeight.Normal,
                                                lineHeight = if (isActiveLine) 24.sp else 21.sp,
                                                color = animatedColor
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
        }
    }
}

@Composable
private fun ContinuousScrollText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    velocity: androidx.compose.ui.unit.Dp = 30.dp
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = 1200,
            repeatDelayMillis = 1200,
            velocity = velocity
        )
    )
}

