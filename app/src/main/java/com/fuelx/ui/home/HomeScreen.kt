package com.fuelx.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fuelx.data.DefaultMusicRepository
import com.fuelx.data.MusicRepository
import com.fuelx.model.Track
import com.fuelx.ui.components.TrackArtwork
import com.fuelx.ui.theme.*
import kotlinx.coroutines.launch

private data class HomeGenre(
    val id: String,
    val label: String
)

private val HOME_GENRES = listOf(
    HomeGenre("all", "All Songs"),
    HomeGenre("melody", "Melody & Romantic"),
    HomeGenre("party", "Party & Dance"),
    HomeGenre("hiphop", "Hip Hop & Rap"),
    HomeGenre("chill", "Acoustic & Chill"),
    HomeGenre("rock", "Rock & Energy"),
    HomeGenre("folk", "Folk & Indie")
)

// Maximum duration ceiling for individual songs (15 minutes). Longer tracks are multi-song mixes.
private const val MAX_SONG_DURATION_MS = 15 * 60 * 1000L

@Composable
fun HomeScreen(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    repository: MusicRepository = remember { DefaultMusicRepository() },
    recentlyPlayed: List<Track> = emptyList(),
    selectedLanguages: Set<String> = emptySet(),
    onOpenLanguagePicker: () -> Unit = {},
    isFavorite: (String) -> Boolean = { false },
    onToggleFavorite: (Track) -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onTrackSelect: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentPrimary, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Loading music...", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
        return
    }

    if (errorMessage != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Error",
                    tint = AccentSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = errorMessage, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(modifier = Modifier.height(16.dp))
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
        return
    }

    if (tracks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = "No Music",
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No songs loaded",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Check connection and tap retry",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
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
        return
    }

    // Dynamic Genre State
    var selectedGenreId by remember { mutableStateOf("all") }
    var genreTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isGenreLoading by remember { mutableStateOf(false) }

    // Fetch genre tracks when selectedGenre or languages change
    LaunchedEffect(selectedGenreId, selectedLanguages, tracks) {
        if (selectedGenreId == "all") {
            genreTracks = tracks
            isGenreLoading = false
        } else {
            isGenreLoading = true
            val result = repository.getGenreTracks(selectedGenreId, selectedLanguages)
            isGenreLoading = false
            if (result.isSuccess) {
                genreTracks = result.getOrDefault(emptyList())
            }
        }
    }

    // Filter tracks to ensure no mix/jukebox longer than 15 minutes is shown
    val cleanTracks = remember(tracks) {
        tracks.filter { it.durationMs == null || it.durationMs <= MAX_SONG_DURATION_MS }
    }
    val cleanGenreTracks = remember(genreTracks) {
        genreTracks.filter { it.durationMs == null || it.durationMs <= MAX_SONG_DURATION_MS }
    }

    // Top Songs slice (first 6 standalone hits)
    val topSongs = cleanTracks.take(6)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
    ) {
        // App Header & Language Switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FuelX",
                        style = MaterialTheme.typography.headlineLarge,
                        color = AccentPrimary
                    )
                    Text(
                        text = "Your music, uninterrupted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                // Language Selector Button
                Surface(
                    onClick = onOpenLanguagePicker,
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurfaceVariant,
                    border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Languages",
                            tint = AccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val langLabel = if (selectedLanguages.isEmpty()) {
                            "All"
                        } else if (selectedLanguages.size <= 2) {
                            selectedLanguages.joinToString(", ")
                        } else {
                            "${selectedLanguages.first()} +${selectedLanguages.size - 1}"
                        }
                        Text(
                            text = langLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = AccentPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Section 1: Recently Listened (Always first if history exists)
        if (recentlyPlayed.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recently Listened",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(recentlyPlayed.take(10)) { track ->
                        RecentTrackCard(
                            track = track,
                            isSelected = track.id == currentTrack?.id,
                            isPlaying = track.id == currentTrack?.id && isPlaying,
                            onClick = { onTrackSelect(track, recentlyPlayed) }
                        )
                    }
                }
            }
        }

        // Section 2: Top Songs (Clean standalone hit singles based on language)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Whatshot,
                    contentDescription = null,
                    tint = AccentSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val topTitle = if (selectedLanguages.isNotEmpty()) {
                    "Top ${selectedLanguages.joinToString(" • ")} Hits"
                } else {
                    "Top Trending Hits"
                }
                Text(
                    text = topTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        }

        itemsIndexed(topSongs) { index, track ->
            val isCurrent = track.id == currentTrack?.id
            TrackListItem(
                track = track,
                rank = index + 1,
                isCurrent = isCurrent,
                isPlaying = isCurrent && isPlaying,
                isFav = isFavorite(track.id),
                onToggleFav = { onToggleFavorite(track) },
                onClick = { onTrackSelect(track, topSongs) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Section 3: Genres (Curated shelves & interactive filter based on language)
        item {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Category,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Explore Genres",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = if (selectedLanguages.isNotEmpty()) {
                            "Curated for ${selectedLanguages.joinToString(", ")}"
                        } else {
                            "Curated standalone hits by mood & genre"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Genre Chips Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HOME_GENRES.forEach { genre ->
                    val isSelected = selectedGenreId == genre.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedGenreId = genre.id },
                        label = {
                            Text(
                                text = genre.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = AccentPrimary,
                            containerColor = DarkSurface,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) AccentPrimary.copy(alpha = 0.6f) else Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Genre Items Section
        if (isGenreLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentPrimary,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        } else {
            items(cleanGenreTracks) { track ->
                val isCurrent = track.id == currentTrack?.id
                TrackListItem(
                    track = track,
                    rank = null,
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying,
                    isFav = isFavorite(track.id),
                    onToggleFav = { onToggleFavorite(track) },
                    onClick = { onTrackSelect(track, cleanGenreTracks) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RecentTrackCard(
    track: Track,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(136.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DarkSurfaceVariant else DarkSurface
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    artworkUrl = track.displayArtwork,
                    contentDescription = "${track.title} artwork",
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp),
                    placeholderColor = AccentSecondary.copy(alpha = 0.2f),
                    placeholderText = "♪"
                )
                if (isSelected && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBackground.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Playing",
                            tint = AccentPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isSelected) AccentPrimary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrackListItem(
    track: Track,
    rank: Int? = null,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) DarkSurfaceVariant else DarkSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional Rank Badge
            if (rank != null) {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (rank <= 3) AccentPrimary else TextSecondary,
                    modifier = Modifier.width(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    artworkUrl = track.displayArtwork,
                    contentDescription = "${track.title} thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp),
                    placeholderColor = AccentPrimary.copy(alpha = 0.15f),
                    placeholderText = "♪"
                )
                if (isCurrent && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground.copy(alpha = 0.65f)),
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) AccentPrimary else TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album ?: "Single"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

            // Heart / Favorite button
            IconButton(
                onClick = onToggleFav,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFav) "Remove favorite" else "Add favorite",
                    tint = if (isFav) AccentPrimary else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
