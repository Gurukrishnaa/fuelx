package com.fuelx.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fuelx.data.DefaultMusicRepository
import com.fuelx.data.MusicRepository
import com.fuelx.model.Track
import com.fuelx.ui.components.TrackArtwork
import com.fuelx.ui.theme.*
import com.fuelx.util.cleanArtistString

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

// Maximum duration ceiling for individual songs (10 minutes). Longer tracks are multi-song mixes.
private const val MAX_SONG_DURATION_MS = 10 * 60 * 1000L

/**
 * Cinematic Glassmorphism & Exaggerated Minimalism Home Screen.
 * Features a spotlight Hero Song Card, frosted glass chips, liquid recent cards,
 * and high-contrast, polished track rows.
 */
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
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = AccentPrimary, strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Discovering sounds for you...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
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
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141418).copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
                    .padding(28.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Error",
                        tint = AccentSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            contentColor = FuelXColors.OnAccent
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
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
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141418).copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(24.dp))
                    .padding(28.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = "No Music",
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No songs loaded",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Check connection and tap retry",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            contentColor = FuelXColors.OnAccent
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
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

    // Filter tracks to ensure no mix/jukebox longer than 10 minutes or compilation videos are shown
    val isHomeCleanSong: (Track) -> Boolean = { track ->
        val dur = track.durationMs
        val durOk = dur == null || (dur in 45_000L..MAX_SONG_DURATION_MS)
        val titleLower = track.title.lowercase()
        val artistLower = track.artist.lowercase()
        val isMix = titleLower.contains("jukebox") ||
                titleLower.contains("dj mix") ||
                titleLower.contains("mix") ||
                titleLower.contains("hit songs") ||
                titleLower.contains("all songs") ||
                titleLower.contains("collection") ||
                titleLower.contains("non stop") ||
                titleLower.contains("nonstop") ||
                titleLower.contains("greatest hits") ||
                titleLower.contains("full album") ||
                artistLower.contains("video") ||
                artistLower.contains("dj") ||
                artistLower.contains("mix")
        durOk && !isMix
    }

    val cleanTracks = remember(tracks) {
        tracks.filter(isHomeCleanSong)
    }
    val cleanGenreTracks = remember(genreTracks) {
        genreTracks.filter(isHomeCleanSong)
    }

    // Hero track is the top trending standalone hit
    val heroTrack = cleanTracks.firstOrNull()
    // Top songs slice (next 6 hits after hero, or first 6 if hero isn't shown)
    val topSongs = remember(cleanTracks) {
        if (cleanTracks.size > 1) cleanTracks.drop(1).take(6) else cleanTracks.take(6)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "FuelX",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.8).sp
                            ),
                            color = AccentPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentPrimary)
                        )
                    }
                    Text(
                        text = "Your music, uninterrupted",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = TextSecondary.copy(alpha = 0.8f)
                    )
                }

                // Frosted Glass Language Selector Button
                val glassBorder = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF16161D).copy(alpha = 0.85f))
                        .border(BorderStroke(1.dp, glassBorder), RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenLanguagePicker)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = AccentPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Hero Song Card Spotlight Banner
        if (heroTrack != null) {
            item {
                val isHeroCurrent = heroTrack.id == currentTrack?.id
                HeroSongCard(
                    track = heroTrack,
                    isCurrent = isHeroCurrent,
                    isPlaying = isHeroCurrent && isPlaying,
                    onPlayClick = { onTrackSelect(heroTrack, cleanTracks) },
                    onToggleFav = { onToggleFavorite(heroTrack) },
                    isFav = isFavorite(heroTrack.id)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Section 1: Recently Listened (Always first if history exists)
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SleekSectionHeader(
                    title = "Recently Listened",
                    badgeText = "HISTORY",
                    icon = Icons.Rounded.History,
                    iconTint = AccentPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
            val topTitle = if (selectedLanguages.isNotEmpty()) {
                "Top ${selectedLanguages.joinToString(" • ")} Hits"
            } else {
                "Trending Hits"
            }
            SleekSectionHeader(
                title = topTitle,
                badgeText = "CHARTS",
                icon = Icons.Rounded.Whatshot,
                iconTint = AccentSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylist(track) },
                onClick = { onTrackSelect(track, topSongs) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Section 3: Genres (Curated shelves & interactive filter based on language)
        item {
            Spacer(modifier = Modifier.height(20.dp))

            SleekSectionHeader(
                title = "Explore Genres",
                badgeText = "CURATED",
                icon = Icons.Rounded.Category,
                iconTint = AccentPrimary,
                subtitle = if (selectedLanguages.isNotEmpty()) {
                    "Curated for ${selectedLanguages.joinToString(", ")}"
                } else {
                    "Curated standalone hits by mood & genre"
                }
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Frosted Glass Genre Chips Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HOME_GENRES.forEach { genre ->
                    val isSelected = selectedGenreId == genre.id
                    FrostedFilterChip(
                        selected = isSelected,
                        label = genre.label,
                        onClick = { selectedGenreId = genre.id }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Genre Items Section
        if (isGenreLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentPrimary,
                        modifier = Modifier.size(32.dp),
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
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onClick = { onTrackSelect(track, cleanGenreTracks) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Cinematic Hero Song Spotlight Card.
 * Full-width visual banner with ambient backdrop glow, gradient darkening vignette,
 * and a floating glassmorphic play trigger.
 */
@Composable
private fun HeroSongCard(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onToggleFav: () -> Unit,
    isFav: Boolean
) {
    val cleanArtist = remember(track.artist) {
        cleanArtistString(track.artist).ifBlank { "Top Artist" }
    }
    val cardShape = RoundedCornerShape(24.dp)
    val cardBorder = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.10f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .shadow(
                elevation = 14.dp,
                shape = cardShape,
                ambientColor = Color.Black,
                spotColor = AccentPrimary.copy(alpha = 0.28f)
            )
            .clip(cardShape)
            .border(BorderStroke(1.dp, cardBorder), cardShape)
            .clickable(onClick = onPlayClick)
    ) {
        // Full bleed background artwork
        TrackArtwork(
            artworkUrl = track.displayArtwork,
            contentDescription = "${track.title} hero artwork",
            modifier = Modifier.fillMaxSize(),
            shape = cardShape,
            placeholderColor = DarkSurfaceVariant
        )

        // Cinematic Vignette: dark gradient fade from bottom & left for text legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.60f),
                            Color(0xFF0A0A0E).copy(alpha = 0.94f)
                        )
                    )
                )
        )

        // Ambient subtle accent bloom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AccentPrimary.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                )
        )

        // Card Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Featured Pill & Favorite Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Frosted Featured Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "SPOTLIGHT #1",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                fontSize = 10.sp
                            ),
                            color = AccentPrimary
                        )
                    }
                }

                // Heart favorite button
                IconButton(
                    onClick = onToggleFav,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.50f))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) AccentPrimary else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom Row: Song details + Floating Play Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanArtist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play / Pause Glass Action Pill
                val isHeroPlaying = isCurrent && isPlaying
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentPrimary)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isHeroPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isHeroPlaying) "Pause" else "Play",
                            tint = FuelXColors.OnAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHeroPlaying) "Playing" else "Play Now",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = FuelXColors.OnAccent
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleek Section Header with minimal uppercase badge and clean typography.
 */
@Composable
private fun SleekSectionHeader(
    title: String,
    badgeText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    subtitle: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextSecondary
                )
            }
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = TextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 38.dp)
            )
        }
    }
}

/**
 * Frosted Glass Filter Chip for category/genre pills.
 */
@Composable
private fun FrostedFilterChip(
    selected: Boolean,
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
    val animatedTextColor by animateColorAsState(
        targetValue = if (selected) AccentPrimary else TextSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipText"
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
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                color = animatedTextColor
            )
        }
    }
}

/**
 * Frosted Glass Card for Recently Listened items with sound wave indicator.
 */
@Composable
private fun RecentTrackCard(
    track: Track,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val cleanArtist = remember(track.artist) {
        cleanArtistString(track.artist).ifBlank { "Unknown Artist" }
    }
    val cardShape = RoundedCornerShape(18.dp)
    val cardBg = if (isSelected) {
        Brush.verticalGradient(
            listOf(
                AccentPrimary.copy(alpha = 0.15f),
                Color(0xFF16161E).copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFF16161C).copy(alpha = 0.80f),
                Color(0xFF0F0F14).copy(alpha = 0.90f)
            )
        )
    }
    val borderStroke = if (isSelected) {
        BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.50f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    }

    Box(
        modifier = Modifier
            .width(138.dp)
            .shadow(
                elevation = if (isSelected) 8.dp else 4.dp,
                shape = cardShape,
                ambientColor = Color.Black,
                spotColor = if (isSelected) AccentPrimary.copy(alpha = 0.25f) else Color.Transparent
            )
            .clip(cardShape)
            .background(cardBg)
            .border(borderStroke, cardShape)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    artworkUrl = track.displayArtwork,
                    contentDescription = "${track.title} artwork",
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    placeholderColor = DarkSurfaceVariant
                )
                if (isSelected && isPlaying) {
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
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                color = if (isSelected) AccentPrimary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = cleanArtist,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = TextSecondary.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Modern Exaggerated Minimalist Track List Item with frosted translucent row styling.
 */
@Composable
private fun TrackListItem(
    track: Track,
    rank: Int? = null,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
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
            // Rank Badge (Minimalist)
            if (rank != null) {
                Text(
                    text = rank.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (rank <= 3) FontWeight.ExtraBold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = if (rank <= 3) AccentPrimary else TextSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.width(26.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Thumbnail with smooth corners & playing equalizer overlay
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(BorderStroke(0.6.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                TrackArtwork(
                    artworkUrl = track.displayArtwork,
                    contentDescription = "${track.title} thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp),
                    placeholderColor = DarkSurfaceVariant
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Track Title & Clean Artist
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

            // Quick Favorite Heart Button
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

            // 3-dots Overflow Menu with Queue and Playlist
            var showMenu by remember { mutableStateOf(false) }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = TextSecondary.copy(alpha = 0.65f),
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
                }
            }
        }
    }
}

