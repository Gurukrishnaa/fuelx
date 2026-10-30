package com.fuelx.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.model.Playlist
import com.fuelx.model.Track
import com.fuelx.ui.components.TrackArtwork
import com.fuelx.ui.theme.*
import com.fuelx.util.cleanArtistString

enum class LibraryTab {
    PLAYLISTS,
    FAVORITES,
    DOWNLOADS,
    HISTORY
}

@Composable
fun LibraryScreen(
    playlists: List<Playlist> = emptyList(),
    favorites: List<Track>,
    downloads: List<Track> = emptyList(),
    history: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onDeleteDownload: (Track) -> Unit = {},
    onClearHistory: () -> Unit,
    onCreatePlaylist: (String) -> Unit = {},
    onDeletePlaylist: (Long) -> Unit = {},
    onSelectPlaylist: (Playlist) -> Unit = {},
    onPlayPlaylist: (Playlist) -> Unit = {},
    onTrackSelect: (Track, List<Track>) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.PLAYLISTS) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var trackToDelete by remember { mutableStateOf<Track?>(null) }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            title = {
                Text(
                    text = "New Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name for your playlist:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("My Playlist", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPlaylistName.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreatePlaylist(trimmed)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Create", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Delete Playlist Dialog
    playlistToDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete \"${pl.name}\"?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlaylist(pl.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                colors = IconButtonDefaults.iconButtonColors(contentColor = TextPrimary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to Home"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Your Library",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp
                ),
                color = AccentPrimary
            )
        }

        // Tab Selection Chips (liquid frosted glass pills, horizontally scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FrostedLibraryTabChip(
                selected = selectedTab == LibraryTab.PLAYLISTS,
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Playlists (${playlists.size})",
                onClick = { selectedTab = LibraryTab.PLAYLISTS }
            )

            FrostedLibraryTabChip(
                selected = selectedTab == LibraryTab.FAVORITES,
                icon = Icons.Rounded.Favorite,
                label = "Favorites (${favorites.size})",
                onClick = { selectedTab = LibraryTab.FAVORITES }
            )

            FrostedLibraryTabChip(
                selected = selectedTab == LibraryTab.DOWNLOADS,
                icon = Icons.Rounded.DownloadDone,
                label = "Downloads (${downloads.size})",
                onClick = { selectedTab = LibraryTab.DOWNLOADS }
            )

            FrostedLibraryTabChip(
                selected = selectedTab == LibraryTab.HISTORY,
                icon = Icons.Rounded.History,
                label = "History (${history.size})",
                onClick = { selectedTab = LibraryTab.HISTORY }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            LibraryTab.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    EmptyLibraryView(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = "No playlists yet",
                        subtitle = "Create your own playlists to group and play your favorite songs.",
                        actionButton = {
                            Button(
                                onClick = { showCreatePlaylistDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = DarkBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create Playlist", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Your Playlists",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Button(
                                    onClick = { showCreatePlaylistDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        tint = DarkBackground,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Playlist", color = DarkBackground, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCardItem(
                                playlist = playlist,
                                onClick = { onSelectPlaylist(playlist) },
                                onPlay = { onPlayPlaylist(playlist) },
                                onDelete = { playlistToDelete = playlist }
                            )
                        }
                    }
                }
            }
            LibraryTab.FAVORITES -> {
                if (favorites.isEmpty()) {
                    EmptyLibraryView(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = "No favorites yet",
                        subtitle = "Tap the heart icon on any track or in the player to save your favorite music here."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            // Action row: Play All & Shuffle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onTrackSelect(favorites.first(), favorites) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = DarkBackground,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Play All", color = DarkBackground, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val shuffled = favorites.shuffled()
                                            onTrackSelect(shuffled.first(), shuffled)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0xFF14141C).copy(alpha = 0.65f),
                                            contentColor = TextPrimary
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Shuffle", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        items(favorites, key = { it.id }) { track ->
                            val isCurrent = track.id == currentTrack?.id
                            LibraryTrackItem(
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isFav = true,
                                onToggleFav = { onToggleFavorite(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onClick = { onTrackSelect(track, favorites) }
                            )
                        }
                    }
                }
            }

            LibraryTab.DOWNLOADS -> {
                if (downloads.isEmpty()) {
                    EmptyLibraryView(
                        icon = Icons.Rounded.DownloadDone,
                        title = "No downloaded tracks",
                        subtitle = "Tap the download icon while playing any song to save it for offline listening."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            // Action row: Play All & Shuffle
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onTrackSelect(downloads.first(), downloads) },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = DarkBackground,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Play All", color = DarkBackground, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val shuffled = downloads.shuffled()
                                            onTrackSelect(shuffled.first(), shuffled)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0xFF14141C).copy(alpha = 0.65f),
                                            contentColor = TextPrimary
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Shuffle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Shuffle", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        items(downloads, key = { it.id }) { track ->
                            val isCurrent = track.id == currentTrack?.id
                            LibraryTrackItem(
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isFav = isFavorite(track.id),
                                onToggleFav = { onToggleFavorite(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onDelete = { trackToDelete = track },
                                onClick = { onTrackSelect(track, downloads) }
                            )
                        }
                    }
                }
            }

            LibraryTab.HISTORY -> {
                if (history.isEmpty()) {
                    EmptyLibraryView(
                        icon = Icons.Rounded.History,
                        title = "No listening history",
                        subtitle = "Tracks you listen to will automatically appear here."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            // Action row: Play All & Clear History
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onTrackSelect(history.first(), history) },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = DarkBackground,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play All", color = DarkBackground, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { showClearHistoryDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear History", fontSize = 13.sp)
                                }
                            }
                        }

                        items(history, key = { it.id }) { track ->
                            val isCurrent = track.id == currentTrack?.id
                            LibraryTrackItem(
                                track = track,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && isPlaying,
                                isFav = isFavorite(track.id),
                                onToggleFav = { onToggleFavorite(track) },
                                onAddToQueue = { onAddToQueue(track) },
                                onAddToPlaylist = { onAddToPlaylist(track) },
                                onClick = { onTrackSelect(track, history) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing history
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Clear History?",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "This will remove all your recently played tracks from history. This cannot be undone.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text("Clear", color = AccentSecondary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Confirmation dialog for deleting a downloaded track
    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Delete Download?",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Remove '${track.title}' from your downloaded songs? You won't be able to play it offline until downloaded again.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDownload(track)
                        trackToDelete = null
                    }
                ) {
                    Text("Delete", color = AccentSecondary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun LibraryTrackItem(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val cleanArtist = remember(track.artist) {
        cleanArtistString(track.artist).ifBlank { "Unknown Artist" }
    }
    val itemShape = RoundedCornerShape(16.dp)
    val itemBg = if (isCurrent) {
        Brush.horizontalGradient(
            listOf(
                AccentPrimary.copy(alpha = 0.16f),
                Color(0xFF16161E).copy(alpha = 0.75f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color(0xFF131318).copy(alpha = 0.65f),
                Color(0xFF0F0F13).copy(alpha = 0.75f)
            )
        )
    }
    val itemBorder = if (isCurrent) {
        BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.45f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(itemBg)
            .border(itemBorder, itemShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album artwork
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    artworkUrl = track.displayArtwork,
                    contentDescription = "${track.title} artwork",
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp),
                    placeholderColor = if (isCurrent) AccentPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant,
                    placeholderText = "♪"
                )
                if (isCurrent && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.60f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Playing",
                            tint = AccentPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = if (isCurrent) AccentPrimary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (track.isDownloaded || onDelete != null) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = "Downloaded",
                            tint = AccentPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = cleanArtist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = TextSecondary.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Duration (only rendered when known)
            track.formattedDuration?.let { dur ->
                Text(
                    text = dur,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Quick favorite button
            IconButton(
                onClick = onToggleFav,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFav) "Remove favorite" else "Favorite",
                    tint = if (isFav) AccentPrimary else TextSecondary.copy(alpha = 0.50f),
                    modifier = Modifier.size(19.dp)
                )
            }

            // 3-dots Overflow Menu with Queue, Playlist, Delete options
            var showMenu by remember { mutableStateOf(false) }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Song options",
                        tint = TextSecondary.copy(alpha = 0.70f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(Color(0xFF181820))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isFav) "Remove Favorite" else "Add to Favorites",
                                color = if (isFav) AccentPrimary else TextPrimary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFav) AccentPrimary else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleFav()
                        }
                    )

                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add to Queue",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onAddToQueue()
                            }
                        )
                    }

                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add to Playlist",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )
                    }

                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Delete Download",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCardItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    val cardBg = Brush.horizontalGradient(
        listOf(
            Color(0xFF161620).copy(alpha = 0.80f),
            Color(0xFF101015).copy(alpha = 0.85f)
        )
    )
    val cardBorder = BorderStroke(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.White.copy(alpha = 0.03f)
            )
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(cardBorder, cardShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover / Thumbnail
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentPrimary.copy(alpha = 0.35f), Color(0xFF14141E))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!playlist.coverArtwork.isNullOrBlank()) {
                    TrackArtwork(
                        artworkUrl = playlist.coverArtwork,
                        contentDescription = playlist.name,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        placeholderColor = Color.Transparent,
                        placeholderText = ""
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Playlist Name and Track Count
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextSecondary.copy(alpha = 0.70f)
                )
            }

            // Dedicated Play Button on each playlist!
            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(40.dp)
                    .shadow(8.dp, CircleShape, spotColor = AccentPrimary.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(AccentPrimary)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play ${playlist.name}",
                    tint = DarkBackground,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Delete Playlist Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${playlist.name}",
                    tint = TextSecondary.copy(alpha = 0.50f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionButton: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.7f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .shadow(12.dp, CircleShape, spotColor = AccentPrimary.copy(alpha = 0.20f))
                    .clip(CircleShape)
                    .background(Color(0xFF14141B).copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentPrimary.copy(alpha = 0.80f),
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                ),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = TextSecondary.copy(alpha = 0.70f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (actionButton != null) {
                Spacer(modifier = Modifier.height(20.dp))
                actionButton()
            }
        }
    }
}

/**
 * Frosted Glass Filter Chip for Library tab selector pills.
 */
@Composable
private fun FrostedLibraryTabChip(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (selected) AccentPrimary.copy(alpha = 0.18f) else Color(0xFF14141A).copy(alpha = 0.70f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipBg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (selected) AccentPrimary.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipBorder"
    )
    val animatedContentColor by animateColorAsState(
        targetValue = if (selected) AccentPrimary else TextSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipContent"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animatedBg)
            .border(BorderStroke(1.dp, animatedBorderColor), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = animatedContentColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = animatedContentColor
            )
        }
    }
}
