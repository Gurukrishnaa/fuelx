package com.fuelx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fuelx.data.DefaultLibraryRepository
import com.fuelx.data.DefaultLyricsRepository
import com.fuelx.data.DefaultMusicRepository
import com.fuelx.model.Track
import com.fuelx.playback.PlayerController
import com.fuelx.ui.components.MiniPlayer
import com.fuelx.ui.home.HomeScreen
import com.fuelx.ui.library.LibraryScreen
import com.fuelx.ui.lyrics.LyricsScreen
import com.fuelx.ui.player.NowPlayingScreen
import com.fuelx.ui.search.SearchScreen
import com.fuelx.ui.theme.*
import com.fuelx.util.ParsedLyrics
import kotlinx.coroutines.launch

enum class ScreenTab {
    HOME, SEARCH, LIBRARY
}

@Composable
fun FuelXApp() {
    val context = LocalContext.current
    val repository = remember { DefaultMusicRepository() }
    val lyricsRepository = remember { DefaultLyricsRepository() }
    val libraryRepository = remember { DefaultLibraryRepository.getInstance(context) }
    val playerController = remember { PlayerController(context, repository, libraryRepository) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val favorites by libraryRepository.favorites.collectAsState()
    val history by libraryRepository.history.collectAsState()

    val onToggleFavorite: (Track) -> Unit = { track ->
        coroutineScope.launch {
            libraryRepository.toggleFavorite(track)
        }
    }

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

    // Real audio player states from AndroidX Media3
    val currentTrack by playerController.currentTrack.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val currentPositionMs by playerController.currentPositionMs.collectAsState()
    val durationMs by playerController.durationMs.collectAsState()
    val isBuffering by playerController.isBuffering.collectAsState()
    val playbackError by playerController.playbackError.collectAsState()

    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var isLyricsOpen by remember { mutableStateOf(false) }

    // Lyrics State
    var parsedLyrics by remember { mutableStateOf<ParsedLyrics?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var lyricsRetryCount by remember { mutableIntStateOf(0) }

    // Intercept phone's System Back button:
    // 1. If on Lyrics page -> close Lyrics to return to player
    // 2. If on NowPlaying page -> collapse player to return to browsing
    // 3. If on Search or Library tab -> return to Home tab
    BackHandler(enabled = isLyricsOpen) {
        isLyricsOpen = false
    }
    BackHandler(enabled = !isLyricsOpen && isNowPlayingOpen) {
        isNowPlayingOpen = false
    }
    BackHandler(enabled = !isLyricsOpen && !isNowPlayingOpen && selectedTab != ScreenTab.HOME) {
        selectedTab = ScreenTab.HOME
    }

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

    // Load synchronized lyrics whenever current track changes
    LaunchedEffect(currentTrack?.id, lyricsRetryCount) {
        val track = currentTrack
        if (track == null) {
            parsedLyrics = null
            isLyricsLoading = false
            lyricsError = null
        } else {
            isLyricsLoading = true
            lyricsError = null
            val result = lyricsRepository.getLyrics(track)
            isLyricsLoading = false
            if (result.isSuccess) {
                parsedLyrics = result.getOrNull()
            } else {
                lyricsError = result.exceptionOrNull()?.message ?: "Unable to fetch lyrics"
            }
        }
    }

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
                            recentlyPlayed = history,
                            isFavorite = { id -> favorites.any { it.id == id } },
                            onToggleFavorite = onToggleFavorite,
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
                            isFavorite = { id -> favorites.any { it.id == id } },
                            onToggleFavorite = onToggleFavorite,
                            onTrackSelect = { track, list ->
                                playerController.playTrack(track, list)
                            },
                            onBackClick = { selectedTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.LIBRARY -> {
                        LibraryScreen(
                            favorites = favorites,
                            history = history,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            isFavorite = { id -> favorites.any { it.id == id } },
                            onToggleFavorite = onToggleFavorite,
                            onClearHistory = {
                                coroutineScope.launch {
                                    libraryRepository.clearHistory()
                                }
                            },
                            onTrackSelect = { track, list ->
                                playerController.playTrack(track, list)
                            },
                            onBackClick = { selectedTab = ScreenTab.HOME }
                        )
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
                isFavorite = favorites.any { it.id == currentTrack?.id },
                onToggleFavorite = {
                    currentTrack?.let { onToggleFavorite(it) }
                },
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
                parsedLyrics = parsedLyrics,
                isLoading = isLyricsLoading,
                errorMessage = lyricsError,
                currentPositionMs = currentPositionMs,
                onSeekTo = { targetMs ->
                    playerController.seekTo(targetMs)
                },
                onRetry = { lyricsRetryCount++ },
                onClose = { isLyricsOpen = false }
            )
        }
    }
}
