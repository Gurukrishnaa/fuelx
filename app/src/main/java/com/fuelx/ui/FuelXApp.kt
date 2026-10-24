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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fuelx.data.DefaultMusicRepository
import com.fuelx.data.MockTracks
import com.fuelx.model.Track
import com.fuelx.playback.PlayerController
import com.fuelx.ui.components.MiniPlayer
import com.fuelx.ui.home.HomeScreen
import com.fuelx.ui.lyrics.LyricsScreen
import com.fuelx.ui.player.NowPlayingScreen
import com.fuelx.ui.search.SearchScreen
import com.fuelx.ui.theme.*

enum class ScreenTab {
    HOME, SEARCH, LIBRARY
}

@Composable
fun FuelXApp() {
    val context = LocalContext.current
    val repository = remember { DefaultMusicRepository() }
    val playerController = remember { PlayerController(context, repository) }
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
        }
    }

    var selectedTab by remember { mutableStateOf(ScreenTab.HOME) }
    var trendingTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isTrendingLoading by remember { mutableStateOf(true) }
    var trendingError by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }

    val lyrics = remember { MockTracks.sampleLyrics }

    // Fetch initial trending tracks via repository
    LaunchedEffect(retryCount) {
        isTrendingLoading = true
        trendingError = null
        val result = repository.getTrending()
        isTrendingLoading = false
        if (result.isSuccess) {
            val list = result.getOrDefault(emptyList())
            trendingTracks = list
            playerController.setPlaylist(list)
        } else {
            trendingError = result.exceptionOrNull()?.message ?: "Failed to load music"
        }
    }

    // Real audio player states from AndroidX Media3
    val currentTrack by playerController.currentTrack.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val currentPositionMs by playerController.currentPositionMs.collectAsState()
    val durationMs by playerController.durationMs.collectAsState()
    val isBuffering by playerController.isBuffering.collectAsState()
    val playbackError by playerController.playbackError.collectAsState()

    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var isLyricsOpen by remember { mutableStateOf(false) }

    // Display error snackbar if playback or stream resolution fails
    LaunchedEffect(playbackError) {
        playbackError?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            playerController.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column {
                    // Mini Player (Only visible when not full screen)
                    currentTrack?.let { track ->
                        MiniPlayer(
                            currentTrack = track,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            onPlayPauseClick = { playerController.togglePlayPause() },
                            onNextClick = { playerController.skipNext() },
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
                            tracks = trendingTracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            isLoading = isTrendingLoading,
                            errorMessage = trendingError,
                            onRetry = { retryCount++ },
                            onTrackSelect = { track, list ->
                                playerController.playTrack(track, list)
                            }
                        )
                    }
                    ScreenTab.SEARCH -> {
                        SearchScreen(
                            repository = repository,
                            currentTrack = currentTrack,
                            onTrackSelect = { track, list ->
                                playerController.playTrack(track, list)
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
                durationMs = durationMs,
                isBuffering = isBuffering,
                onPlayPauseClick = { playerController.togglePlayPause() },
                onPreviousClick = { playerController.skipPrevious() },
                onNextClick = { playerController.skipNext() },
                onSeekTo = { newPos -> playerController.seekTo(newPos) },
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
                    playerController.seekTo(targetMs)
                },
                onClose = { isLyricsOpen = false }
            )
        }
    }
}
