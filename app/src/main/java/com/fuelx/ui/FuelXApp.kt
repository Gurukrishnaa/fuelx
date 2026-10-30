package com.fuelx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.zIndex
import com.fuelx.ui.components.AddToPlaylistDialog
import com.fuelx.ui.components.GlassSnackbar
import com.fuelx.ui.components.LanguageSelectionDialog
import com.fuelx.ui.components.MiniPlayer
import com.fuelx.ui.home.HomeScreen
import com.fuelx.ui.library.LibraryScreen
import com.fuelx.ui.library.PlaylistDetailScreen
import com.fuelx.ui.player.NowPlayingScreen
import com.fuelx.ui.search.SearchScreen
import com.fuelx.ui.theme.*
import com.fuelx.util.ArtworkAccentHelper
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
            val wasFav = favorites.any { it.id == track.id }
            libraryRepository.toggleFavorite(track)
            val msg = if (!wasFav) "Added to favourites" else "Removed from favourites"
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
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

    // Dynamic Artwork Accent
    var currentAccent by remember { mutableStateOf(FuelXColors.AccentDefault) }

    LaunchedEffect(currentTrack?.displayArtwork) {
        currentAccent = ArtworkAccentHelper.getAccentColor(context, currentTrack?.displayArtwork)
    }

    // Lyrics State
    var parsedLyrics by remember { mutableStateOf<ParsedLyrics?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var lyricsError by remember { mutableStateOf<String?>(null) }
    var lyricsRetryCount by remember { mutableIntStateOf(0) }

    // Intercept phone's System Back button:
    // 1. If on NowPlaying page -> collapse player to return to browsing
    // 2. If on Playlist detail page -> return to Library
    // 3. If on Search or Library tab -> return to Home tab
    BackHandler(enabled = isNowPlayingOpen) {
        isNowPlayingOpen = false
    }
    BackHandler(enabled = !isNowPlayingOpen && selectedPlaylist != null) {
        selectedPlaylistId = null
    }
    BackHandler(enabled = !isNowPlayingOpen && selectedPlaylist == null && selectedTab != ScreenTab.HOME) {
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

    CompositionLocalProvider(LocalAccent provides currentAccent) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
        Scaffold(
            snackbarHost = {},
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
                // Minimalist 3-Destination Navigation with 4dp Accent Dot - Solid background limited only to nav buttons
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = FuelXColors.Bg,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(56.dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FuelXNavItem(
                            imageVector = Icons.Rounded.Home,
                            contentDescription = "Home",
                            isSelected = selectedTab == ScreenTab.HOME,
                            onClick = { selectedTab = ScreenTab.HOME }
                        )
                        FuelXNavItem(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            isSelected = selectedTab == ScreenTab.SEARCH,
                            onClick = { selectedTab = ScreenTab.SEARCH }
                        )
                        FuelXNavItem(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = "Library",
                            isSelected = selectedTab == ScreenTab.LIBRARY,
                            onClick = { selectedTab = ScreenTab.LIBRARY }
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
                                onBackClick = { selectedPlaylistId = null },
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
                                }
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
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            message = "Removed from downloads",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
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

                // Mini Player floating pill above navigation bar
                AnimatedVisibility(
                    visible = currentTrack != null && !isNowPlayingOpen,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy)
                    ) + fadeIn(animationSpec = tween(240)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(200)
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    currentTrack?.let { track ->
                        MiniPlayer(
                            currentTrack = track,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            playbackError = playbackError,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            accentColor = currentAccent,
                            onRetryPlayback = { playerController.retryPlayback() },
                            onPlayPauseClick = { playerController.togglePlayPause() },
                            onPreviousClick = { playerController.skipPrevious() },
                            onNextClick = { playerController.skipNext() },
                            onSeekTo = { posMs -> playerController.seekTo(posMs) },
                            onMiniPlayerClick = { isNowPlayingOpen = true }
                        )
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
                    currentTrack?.let { track ->
                        downloadManager.downloadTrack(track)
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = "Added to downloads",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onDeleteDownload = {
                    currentTrack?.let { track ->
                        downloadManager.deleteDownload(track.id)
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                message = "Removed from downloads",
                                duration = SnackbarDuration.Short
                            )
                        }
                    }
                },
                onPlayPauseClick = { playerController.togglePlayPause() },
                onPreviousClick = { playerController.skipPrevious() },
                onNextClick = { playerController.skipNext() },
                onSeekTo = { newPos -> playerController.seekTo(newPos) },
                parsedLyrics = parsedLyrics,
                isLyricsLoading = isLyricsLoading,
                lyricsError = lyricsError,
                onRetryLyrics = { lyricsRetryCount++ },
                onAddToPlaylist = {
                    currentTrack?.let { trackForPlaylistDialog = it }
                },
                onClose = { isNowPlayingOpen = false }
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
                        val msg = if (added) "Added to playlist \"${playlist.name}\"" else "Already in playlist \"${playlist.name}\""
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
                    }
                },
                onCreatePlaylistAndAdd = { name ->
                    coroutineScope.launch {
                        val newId = libraryRepository.createPlaylist(name)
                        if (newId != -1L) {
                            libraryRepository.addTrackToPlaylist(newId, track)
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar("Added to playlist \"$name\"", duration = SnackbarDuration.Short)
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

        // Global Liquid Glass Notification Host - Floats above all screens, sheets & dialogs
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 20.dp, end = 20.dp)
                .zIndex(999f)
        ) { data ->
            GlassSnackbar(snackbarData = data)
        }
    }
}
}

@Composable
private fun FuelXNavItem(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current

    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
        label = "navItemBg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.50f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navItemBorder"
    )
    val animatedIconColor by animateColorAsState(
        targetValue = if (isSelected) accent else FuelXColors.TextTertiary.copy(alpha = 0.60f),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "navItemIcon"
    )

    val navPillShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .pressableScale(onClick = onClick)
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = navPillShape,
                ambientColor = Color.Transparent,
                spotColor = if (isSelected) accent.copy(alpha = 0.35f) else Color.Transparent
            )
            .clip(navPillShape)
            .background(animatedBg)
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) {
                        Brush.verticalGradient(
                            listOf(
                                animatedBorderColor,
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                ),
                navPillShape
            )
            .padding(horizontal = 20.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(23.dp),
            tint = animatedIconColor
        )
    }
}
