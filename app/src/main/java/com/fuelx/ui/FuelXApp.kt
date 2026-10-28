package com.fuelx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fuelx.data.DefaultLibraryRepository
import com.fuelx.data.DefaultLyricsRepository
import com.fuelx.data.DefaultMusicRepository
import com.fuelx.data.DownloadState
import com.fuelx.data.PlaybackPreferences
import com.fuelx.data.TrackDownloadManager
import com.fuelx.model.Playlist
import com.fuelx.model.Track
import com.fuelx.playback.PlayerController
import com.fuelx.ui.components.AddToPlaylistDialog
import com.fuelx.ui.components.LanguageSelectionDialog
import com.fuelx.ui.components.MiniPlayer
import com.fuelx.ui.home.HomeScreen
import com.fuelx.ui.library.LibraryScreen
import com.fuelx.ui.library.PlaylistDetailScreen
import com.fuelx.ui.lyrics.LyricsScreen
import com.fuelx.ui.player.NowPlayingScreen
import com.fuelx.ui.search.SearchScreen
import com.fuelx.ui.theme.*
import com.fuelx.util.NetworkMonitor
import com.fuelx.util.ParsedLyrics
import kotlinx.coroutines.launch

enum class ScreenTab {
    HOME, SEARCH, LIBRARY
}

@Composable
fun FuelXApp() {
    val context = LocalContext.current
    val preferences = remember { PlaybackPreferences(context) }
    val repository = remember { DefaultMusicRepository() }
    val lyricsRepository = remember { DefaultLyricsRepository() }
    val libraryRepository = remember { DefaultLibraryRepository.getInstance(context) }
    val downloadManager = remember { TrackDownloadManager.getInstance(context, repository, libraryRepository) }
    val playerController = remember { PlayerController(context, repository, libraryRepository) }
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Language preference states (prompts on initial fresh open if not set)
    var selectedLanguages by remember { mutableStateOf(preferences.getSelectedLanguages()) }
    var showLanguageDialog by remember { mutableStateOf(!preferences.isLanguagePreferenceSet()) }

    val favorites by libraryRepository.favorites.collectAsState()
    val downloads by libraryRepository.downloads.collectAsState()
    val downloadStates by downloadManager.downloadStates.collectAsState()
    val history by libraryRepository.history.collectAsState()
    val playlists by libraryRepository.playlists.collectAsState()

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    val selectedPlaylist = remember(playlists, selectedPlaylistId) {
        playlists.find { it.id == selectedPlaylistId }
    }
    var trackForPlaylistDialog by remember { mutableStateOf<Track?>(null) }

    val onToggleFavorite: (Track) -> Unit = { track ->
        coroutineScope.launch {
            libraryRepository.toggleFavorite(track)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
            networkMonitor.unregister()
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
    val queue by playerController.queue.collectAsState()

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
    // 3. If on Playlist detail page -> return to Library
    // 4. If on Search or Library tab -> return to Home tab
    BackHandler(enabled = isLyricsOpen) {
        isLyricsOpen = false
    }
    BackHandler(enabled = !isLyricsOpen && isNowPlayingOpen) {
        isNowPlayingOpen = false
    }
    BackHandler(enabled = !isLyricsOpen && !isNowPlayingOpen && selectedPlaylist != null) {
        selectedPlaylistId = null
    }
    BackHandler(enabled = !isLyricsOpen && !isNowPlayingOpen && selectedPlaylist == null && selectedTab != ScreenTab.HOME) {
        selectedTab = ScreenTab.HOME
    }

    // Fetch initial trending tracks via repository based on language preferences
    LaunchedEffect(retryCount, selectedLanguages) {
        isTrendingLoading = true
        trendingError = null
        val result = repository.getTrending(selectedLanguages)
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

    // Auto-reload trending tracks when network reconnects
    LaunchedEffect(isOnline) {
        if (isOnline && (trendingTracks.isEmpty() || trendingError != null)) {
            retryCount++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AnimatedVisibility(
                    visible = !isOnline,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = "Offline",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "You're offline • Reconnecting...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Column {
                    // Mini Player (Only visible when not full screen)
                    currentTrack?.let { track ->
                        MiniPlayer(
                            currentTrack = track,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            playbackError = playbackError,
                            onRetryPlayback = { playerController.retryPlayback() },
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
                            repository = repository,
                            tracks = trendingTracks,
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            recentlyPlayed = history,
                            selectedLanguages = selectedLanguages,
                            onOpenLanguagePicker = { showLanguageDialog = true },
                            isFavorite = { id -> favorites.any { it.id == id } },
                            onToggleFavorite = onToggleFavorite,
                            onAddToQueue = { track ->
                                playerController.addToQueue(track)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Added \"${track.title}\" to queue",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onAddToPlaylist = { track ->
                                trackForPlaylistDialog = track
                            },
                            isLoading = isTrendingLoading,
                            errorMessage = trendingError,
                            onRetry = { retryCount++ },
                            onTrackSelect = { track, list ->
                                playerController.playTrack(track, list)
                                isNowPlayingOpen = true
                            }
                        )
                    }
                    ScreenTab.SEARCH -> {
                        SearchScreen(
                            repository = repository,
                            currentTrack = currentTrack,
                            isFavorite = { id -> favorites.any { it.id == id } },
                            onToggleFavorite = onToggleFavorite,
                            onAddToQueue = { track ->
                                playerController.addToQueue(track)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Added \"${track.title}\" to queue",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            },
                            onAddToPlaylist = { track ->
                                trackForPlaylistDialog = track
                            },
                            onTrackSelect = { track, list ->
                                playerController.playTrack(track, list)
                                isNowPlayingOpen = true
                            },
                            onBackClick = { selectedTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.LIBRARY -> {
                        if (selectedPlaylist != null) {
                            PlaylistDetailScreen(
                                playlist = selectedPlaylist,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                onPlayTrack = { track, list ->
                                    playerController.playTrack(track, list)
                                    isNowPlayingOpen = true
                                },
                                onRemoveTrack = { trackId ->
                                    coroutineScope.launch {
                                        libraryRepository.removeTrackFromPlaylist(selectedPlaylist.id, trackId)
                                        snackbarHostState.showSnackbar("Removed from playlist", duration = SnackbarDuration.Short)
                                    }
                                },
                                onDeletePlaylist = {
                                    val id = selectedPlaylist.id
                                    selectedPlaylistId = null
                                    coroutineScope.launch {
                                        libraryRepository.deletePlaylist(id)
                                        snackbarHostState.showSnackbar("Playlist deleted", duration = SnackbarDuration.Short)
                                    }
                                },
                                onRenamePlaylist = { newName ->
                                    coroutineScope.launch {
                                        libraryRepository.renamePlaylist(selectedPlaylist.id, newName)
                                        snackbarHostState.showSnackbar("Playlist renamed", duration = SnackbarDuration.Short)
                                    }
                                },
                                onBackClick = { selectedPlaylistId = null }
                            )
                        } else {
                            LibraryScreen(
                                playlists = playlists,
                                favorites = favorites,
                                downloads = downloads,
                                history = history,
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                isFavorite = { id -> favorites.any { it.id == id } },
                                onToggleFavorite = onToggleFavorite,
                                onAddToQueue = { track ->
                                    playerController.addToQueue(track)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Added \"${track.title}\" to queue",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                },
                                onAddToPlaylist = { track ->
                                    trackForPlaylistDialog = track
                                },
                                onDeleteDownload = { track ->
                                    downloadManager.deleteDownload(track.id)
                                },
                                onClearHistory = {
                                    coroutineScope.launch {
                                        libraryRepository.clearHistory()
                                    }
                                },
                                onCreatePlaylist = { name ->
                                    coroutineScope.launch {
                                        libraryRepository.createPlaylist(name)
                                        snackbarHostState.showSnackbar("Created playlist \"$name\"", duration = SnackbarDuration.Short)
                                    }
                                },
                                onDeletePlaylist = { pid ->
                                    coroutineScope.launch {
                                        libraryRepository.deletePlaylist(pid)
                                        snackbarHostState.showSnackbar("Playlist deleted", duration = SnackbarDuration.Short)
                                    }
                                },
                                onSelectPlaylist = { playlist ->
                                    selectedPlaylistId = playlist.id
                                },
                                onPlayPlaylist = { playlist ->
                                    if (playlist.tracks.isNotEmpty()) {
                                        playerController.playTrack(playlist.tracks.first(), playlist.tracks)
                                        isNowPlayingOpen = true
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Playlist is empty. Add songs first.", duration = SnackbarDuration.Short)
                                        }
                                    }
                                },
                                onTrackSelect = { track, list ->
                                    playerController.playTrack(track, list)
                                    isNowPlayingOpen = true
                                },
                                onBackClick = { selectedTab = ScreenTab.HOME }
                            )
                        }
                    }
                }
            }
        }

        // Full Screen Now Playing Overlay
        if (isNowPlayingOpen && currentTrack != null) {
            val trackId = currentTrack!!.id
            val currentDownloadState = downloadStates[trackId] ?: if (downloadManager.isDownloaded(trackId)) {
                DownloadState.Downloaded
            } else {
                DownloadState.Idle
            }

            NowPlayingScreen(
                track = currentTrack!!,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isBuffering = isBuffering,
                playbackError = playbackError,
                onRetryPlayback = { playerController.retryPlayback() },
                isFavorite = favorites.any { it.id == currentTrack?.id },
                onToggleFavorite = {
                    currentTrack?.let { onToggleFavorite(it) }
                },
                downloadState = currentDownloadState,
                queue = queue,
                onRemoveFromQueue = { index -> playerController.removeFromQueue(index) },
                onClearQueue = { playerController.clearQueue() },
                onPlayFromQueue = { index -> playerController.playFromQueue(index) },
                onDownloadClick = {
                    currentTrack?.let { downloadManager.downloadTrack(it) }
                },
                onDeleteDownload = {
                    currentTrack?.let { downloadManager.deleteDownload(it.id) }
                },
                onPlayPauseClick = { playerController.togglePlayPause() },
                onPreviousClick = { playerController.skipPrevious() },
                onNextClick = { playerController.skipNext() },
                onSeekTo = { newPos -> playerController.seekTo(newPos) },
                onOpenLyrics = { isLyricsOpen = true },
                onAddToPlaylist = {
                    currentTrack?.let { trackForPlaylistDialog = it }
                },
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

        // Add to Playlist Dialog
        trackForPlaylistDialog?.let { track ->
            AddToPlaylistDialog(
                track = track,
                playlists = playlists,
                onDismissRequest = { trackForPlaylistDialog = null },
                onSelectPlaylist = { playlist ->
                    coroutineScope.launch {
                        val added = libraryRepository.addTrackToPlaylist(playlist.id, track)
                        val msg = if (added) "Added to \"${playlist.name}\"" else "Already in \"${playlist.name}\""
                        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                    }
                },
                onCreatePlaylistAndAdd = { name ->
                    coroutineScope.launch {
                        val newId = libraryRepository.createPlaylist(name)
                        if (newId != -1L) {
                            libraryRepository.addTrackToPlaylist(newId, track)
                            snackbarHostState.showSnackbar("Added to \"$name\"", duration = SnackbarDuration.Short)
                        }
                    }
                }
            )
        }

        // Language Preference Dialog (Fresh open & User triggered)
        if (showLanguageDialog) {
            LanguageSelectionDialog(
                initialSelected = selectedLanguages.ifEmpty { setOf("English", "Tamil") },
                canDismiss = preferences.isLanguagePreferenceSet(),
                onLanguagesSelected = { newLanguages ->
                    selectedLanguages = newLanguages
                    preferences.setSelectedLanguages(newLanguages)
                    showLanguageDialog = false
                },
                onDismissRequest = {
                    if (preferences.isLanguagePreferenceSet()) {
                        showLanguageDialog = false
                    }
                }
            )
        }
    }
}
