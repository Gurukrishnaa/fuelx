package com.fuelx.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.model.Playlist
import com.fuelx.model.Track
import com.fuelx.ui.components.TrackArtwork
import com.fuelx.ui.theme.*
import com.fuelx.util.cleanArtistString

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRenamePlaylist: (String) -> Unit,
    onBackClick: () -> Unit,
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playlist.name) }
    var trackToRemove by remember { mutableStateOf<Track?>(null) }

    // Delete Playlist confirmation dialog
    if (showDeletePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePlaylistDialog = false },
            title = { Text("Delete Playlist", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to delete \"${playlist.name}\"? This action cannot be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaylistDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Rename Playlist Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenamePlaylist(trimmed)
                            showRenameDialog = false
                        }
                    },
                    enabled = renameText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Save", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Remove Track confirmation dialog
    trackToRemove?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToRemove = null },
            title = { Text("Remove from Playlist", color = TextPrimary) },
            text = {
                Text(
                    "Remove \"${track.title}\" from \"${playlist.name}\"?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveTrack(track.id)
                        trackToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { trackToRemove = null }) {
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
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary
                )
            }

            Text(
                text = "PLAYLIST",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextSecondary
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    renameText = playlist.name
                    showRenameDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Rename",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showDeletePlaylistDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete Playlist",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hero / Playlist Header Card with Liquid Frosted Glass
            item {
                val headerShape = RoundedCornerShape(22.dp)
                val headerBorder = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(headerShape)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF16161D).copy(alpha = 0.85f),
                                    Color(0xFF0F0F14).copy(alpha = 0.92f)
                                )
                            )
                        )
                        .border(BorderStroke(1.dp, headerBorder), headerShape)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Artwork or Fallback Cover
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(AccentPrimary.copy(alpha = 0.35f), DarkSurfaceVariant)
                                    )
                                )
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!playlist.coverArtwork.isNullOrBlank()) {
                                TrackArtwork(
                                    artworkUrl = playlist.coverArtwork,
                                    contentDescription = playlist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(18.dp),
                                    placeholderColor = Color.Transparent,
                                    placeholderText = ""
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(54.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary.copy(alpha = 0.75f)
                        )

                        if (playlist.tracks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        onPlayTrack(playlist.tracks.first(), playlist.tracks)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentPrimary,
                                        contentColor = FuelXColors.OnAccent
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play All",
                                        tint = FuelXColors.OnAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play All", fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                OutlinedButton(
                                    onClick = {
                                        val shuffled = playlist.tracks.shuffled()
                                        onPlayTrack(shuffled.first(), shuffled)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                                    shape = RoundedCornerShape(24.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = "Shuffle",
                                        modifier = Modifier.size(18.dp),
                                        tint = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Shuffle", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Empty state if playlist has no tracks
            if (playlist.tracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This playlist is empty",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add songs using the playlist button on the Home or Now Playing screens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // Tracks List
                itemsIndexed(
                    playlist.tracks,
                    key = { _, track -> track.id },
                    contentType = { _, _ -> "playlist_track" }
                ) { index, track ->
                    val isCurrent = track.id == currentTrack?.id
                    PlaylistTrackItem(
                        track = track,
                        index = index + 1,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onClick = { onPlayTrack(track, playlist.tracks) },
                        onRemove = { trackToRemove = track },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistTrackItem(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val cleanArtist = remember(track.artist) {
        cleanArtistString(track.artist).ifBlank { "Unknown Artist" }
    }
    val itemShape = remember { RoundedCornerShape(16.dp) }
    val itemBg = remember(isCurrent) {
        if (isCurrent) {
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
    }
    val itemBorder = remember(isCurrent) {
        if (isCurrent) {
            BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.45f))
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        }
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
            // Index number
            Text(
                text = index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = if (isCurrent) AccentPrimary else TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.width(26.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Artwork
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    artworkUrl = track.displayArtwork,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp),
                    placeholderColor = AccentPrimary.copy(alpha = 0.15f),
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
                val subtitle = if (!track.album.isNullOrBlank()) {
                    "$cleanArtist • ${cleanArtistString(track.album)}"
                } else {
                    cleanArtist
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = TextSecondary.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            track.formattedDuration?.let { dur ->
                Text(
                    text = dur,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Three dots overflow menu
            var showMenu by remember { mutableStateOf(false) }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Song options",
                        tint = TextSecondary.copy(alpha = 0.75f),
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
                                text = "Add to Queue",
                                color = TextPrimary,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = AccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onAddToQueue()
                        }
                    )

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
                                tint = AccentPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Remove from Playlist",
                                color = FuelXColors.Error,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = FuelXColors.Error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

