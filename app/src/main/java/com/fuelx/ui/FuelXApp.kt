package com.fuelx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fuelx.data.MockTracks
import com.fuelx.model.Track
import com.fuelx.ui.components.MiniPlayer
import com.fuelx.ui.home.HomeScreen
import com.fuelx.ui.lyrics.LyricsScreen
import com.fuelx.ui.player.NowPlayingScreen
import com.fuelx.ui.search.SearchScreen
import com.fuelx.ui.theme.*
import kotlinx.coroutines.delay

enum class ScreenTab {
    HOME, SEARCH, LIBRARY
}

@Composable
fun FuelXApp() {
    var selectedTab by remember { mutableStateOf(ScreenTab.HOME) }
    val tracks = remember { MockTracks.sampleTracks }
    val lyrics = remember { MockTracks.sampleLyrics }

    var currentTrack by remember { mutableStateOf<Track?>(tracks.firstOrNull()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0L) }

    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var isLyricsOpen by remember { mutableStateOf(false) }

    // Position ticker simulation for Milestone 1 preview
    LaunchedEffect(isPlaying, currentTrack) {
        while (isPlaying) {
            delay(500)
            val duration = currentTrack?.durationMs ?: 200000L
            if (currentPositionMs + 500 >= duration) {
                // Next track
                val nextIndex = ((tracks.indexOf(currentTrack) + 1) % tracks.size)
                currentTrack = tracks[nextIndex]
                currentPositionMs = 0L
            } else {
                currentPositionMs += 500
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Scaffold(
            bottomBar = {
                Column {
                    // Mini Player (Only visible when not full screen)
                    currentTrack?.let { track ->
                        MiniPlayer(
                            currentTrack = track,
                            isPlaying = isPlaying,
                            onPlayPauseClick = { isPlaying = !isPlaying },
                            onNextClick = {
                                val nextIndex = ((tracks.indexOf(track) + 1) % tracks.size)
                                currentTrack = tracks[nextIndex]
                                currentPositionMs = 0L
                            },
                            onMiniPlayerClick = { isNowPlayingOpen = true }
                        )
                    }

                    // Navigation Bar
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = TextPrimary,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == ScreenTab.HOME,
                            onClick = { selectedTab = ScreenTab.HOME },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentPrimary,
                                selectedTextColor = AccentPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = DarkSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == ScreenTab.SEARCH,
                            onClick = { selectedTab = ScreenTab.SEARCH },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentPrimary,
                                selectedTextColor = AccentPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = DarkSurfaceVariant
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == ScreenTab.LIBRARY,
                            onClick = { selectedTab = ScreenTab.LIBRARY },
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentPrimary,
                                selectedTextColor = AccentPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    ScreenTab.HOME -> {
                        HomeScreen(
                            tracks = tracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            onTrackSelect = { track ->
                                currentTrack = track
                                currentPositionMs = 0L
                                isPlaying = true
                            }
                        )
                    }
                    ScreenTab.SEARCH -> {
                        SearchScreen(
                            allTracks = tracks,
                            currentTrack = currentTrack,
                            onTrackSelect = { track ->
                                currentTrack = track
                                currentPositionMs = 0L
                                isPlaying = true
                            }
                        )
                    }
                    ScreenTab.LIBRARY -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBackground)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Your Library",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = AccentPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Favorites & History will appear here in Milestone 5",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Now Playing Overlay
        if (isNowPlayingOpen && currentTrack != null) {
            NowPlayingScreen(
                track = currentTrack!!,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                onPlayPauseClick = { isPlaying = !isPlaying },
                onPreviousClick = {
                    val prevIndex = (tracks.indexOf(currentTrack) - 1 + tracks.size) % tracks.size
                    currentTrack = tracks[prevIndex]
                    currentPositionMs = 0L
                },
                onNextClick = {
                    val nextIndex = (tracks.indexOf(currentTrack) + 1) % tracks.size
                    currentTrack = tracks[nextIndex]
                    currentPositionMs = 0L
                },
                onSeekTo = { newPos -> currentPositionMs = newPos },
                onOpenLyrics = { isLyricsOpen = true },
                onClose = { isNowPlayingOpen = false }
            )
        }

        // Lyrics Screen Overlay
        if (isLyricsOpen && currentTrack != null) {
            LyricsScreen(
                track = currentTrack!!,
                lyrics = lyrics,
                currentPositionMs = currentPositionMs,
                onSeekTo = { targetMs ->
                    currentPositionMs = targetMs
                },
                onClose = { isLyricsOpen = false }
            )
        }
    }
}
