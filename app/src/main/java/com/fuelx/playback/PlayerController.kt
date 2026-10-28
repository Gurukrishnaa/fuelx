package com.fuelx.playback

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.fuelx.data.DefaultMusicRepository
import com.fuelx.data.MusicRepository
import com.fuelx.data.PlaybackPreferences
import com.fuelx.data.SavedPlaybackState
import com.fuelx.data.DefaultLibraryRepository
import com.fuelx.data.LibraryRepository
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.ByteArrayOutputStream
import com.fuelx.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerController(
    private val context: Context,
    private val repository: MusicRepository = DefaultMusicRepository(),
    private val libraryRepository: LibraryRepository = DefaultLibraryRepository.getInstance(context)
) {

    private val preferences = PlaybackPreferences(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var playJob: Job? = null

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    var playlist: List<Track> = emptyList()
        private set
    private var autoRetryCount = 0

    companion object {
        private const val MAX_AUTO_RETRIES = 2
        var instance: PlayerController? = null
            private set
    }

    init {
        instance = this
        // Restore last known state from storage into UI immediately
        val saved = preferences.getLastPlayback()
        if (saved != null) {
            _currentTrack.value = saved.toTrack()
            _currentPositionMs.value = saved.positionMs
            _durationMs.value = (saved.durationMs ?: 0L).coerceAtLeast(0L)
        }

        connectToService()
    }

    private fun connectToService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
                try {
                    val mediaController = controllerFuture.get()
                    controller = mediaController
                    setupPlayerListener(mediaController)

                    // If player is idle and has no items, restore the saved track in paused state
                    if (mediaController.mediaItemCount == 0 && playlist.isNotEmpty()) {
                        preferences.getLastPlayback()?.let { saved ->
                            restoreSavedState(saved, playlist)
                        }
                    }
                } catch (e: Exception) {
                    _playbackError.value = "Failed to connect to audio service"
                    e.printStackTrace()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (isPlaying) {
                    autoRetryCount = 0
                    _playbackError.value = null
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                    // Persist exact position on pause
                    preferences.savePositionOnly(player.currentPosition)
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val dur = player.duration
                if (dur > 0L && dur != androidx.media3.common.C.TIME_UNSET) {
                    _durationMs.value = dur
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                val dur = player.duration
                if (dur > 0L && dur != androidx.media3.common.C.TIME_UNSET) {
                    _durationMs.value = dur
                }
                if (playbackState == Player.STATE_ENDED) {
                    skipNext()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                val matched = playlist.find { it.id == mediaId }
                if (matched != null) {
                    _currentTrack.value = matched
                    preferences.saveLastPlayback(matched, 0L)
                    scope.launch {
                        libraryRepository.recordHistory(matched)
                    }
                }
                _currentPositionMs.value = 0L
                _durationMs.value = player.duration.coerceAtLeast(0L)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _isBuffering.value = false
                val current = _currentTrack.value
                val pos = player.currentPosition.coerceAtLeast(0L)

                // If currently playing a downloaded offline track, don't attempt network stream resolution
                if (current != null && current.isDownloaded) {
                    _playbackError.value = "Playback error: ${error.localizedMessage ?: "Local file unreadable"}"
                    return
                }

                // If offline, stop player and display clear network message immediately without looping
                if (!isNetworkAvailable()) {
                    player.stop()
                    player.clearMediaItems()
                    _isPlaying.value = false
                    _playbackError.value = "Playback error: No internet connection. Download tracks to listen offline."
                    return
                }

                // Attempt auto-recovery if within retry budget (handles stream expiration / token timeout / 403)
                if (current != null && autoRetryCount < MAX_AUTO_RETRIES) {
                    autoRetryCount++
                    _isBuffering.value = true
                    _playbackError.value = null
                    scope.launch {
                        kotlinx.coroutines.delay(600) // Brief backoff
                        val result = repository.resolveStream(current.id, forceRefresh = true)
                        if (result.isSuccess) {
                            val streamInfo = result.getOrNull()
                            val streamUri = streamInfo?.uri?.toString()
                            if (streamUri != null) {
                                val metadata = MediaMetadata.Builder()
                                    .setTitle(current.title)
                                    .setArtist(current.artist)
                                    .setAlbumTitle(current.album)
                                    .setArtworkUri(if (!current.artworkUrl.isNullOrBlank()) Uri.parse(current.artworkUrl) else null)
                                    .build()

                                val mediaItem = MediaItem.Builder()
                                    .setMediaId(current.id)
                                    .setUri(streamUri)
                                    .setMimeType(streamInfo.mimeType)
                                    .setMediaMetadata(metadata)
                                    .build()

                                player.setMediaItem(mediaItem, pos)
                                player.prepare()
                                player.play()
                                return@launch
                            }
                        }
                        player.stop()
                        player.clearMediaItems()
                        _isBuffering.value = false
                        _isPlaying.value = false
                        _playbackError.value = "Playback error: ${error.localizedMessage ?: "Failed to recover stream"}"
                    }
                } else {
                    player.stop()
                    player.clearMediaItems()
                    _isPlaying.value = false
                    _playbackError.value = "Playback error: ${error.localizedMessage ?: "Network or stream issue"}"
                }
            }
        })

        _isPlaying.value = player.isPlaying
        if (player.isPlaying) {
            startPositionUpdates()
        }
    }

    fun setPlaylist(tracks: List<Track>) {
        this.playlist = tracks
        val current = _currentTrack.value
        if (current == null) {
            preferences.getLastPlayback()?.let { saved ->
                restoreSavedState(saved, tracks)
            }
        }
    }

    fun restoreSavedState(saved: SavedPlaybackState, tracks: List<Track> = playlist) {
        this.playlist = tracks
        val matchedTrack = tracks.find { it.id == saved.trackId } ?: saved.toTrack()
        _currentTrack.value = matchedTrack
        _currentPositionMs.value = saved.positionMs
        _durationMs.value = (matchedTrack.durationMs ?: 0L).coerceAtLeast(0L)
        _isPlaying.value = false

        val player = controller ?: return

        val mediaItems = tracks.map { t ->
            val metadata = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setAlbumTitle(t.album)
                .setArtworkUri(if (!t.artworkUrl.isNullOrBlank()) Uri.parse(t.artworkUrl) else null)
                .build()

            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(t.streamUri)
                .setMimeType("audio/mp4")
                .setMediaMetadata(metadata)
                .build()
        }

        val startIndex = tracks.indexOfFirst { it.id == matchedTrack.id }.coerceAtLeast(0)
        player.setMediaItems(mediaItems, startIndex, saved.positionMs)
        player.prepare()
        player.pause() // Stays paused at exact saved point
    }

    fun playTrack(track: Track, tracks: List<Track> = playlist, forceRestart: Boolean = false) {
        this.playlist = tracks
        val player = controller

        // If the exact same track is already active and loaded in ExoPlayer without errors,
        // do NOT restart from 0:00! Keep playing or resume seamlessly from the current position.
        if (!forceRestart && player != null && _currentTrack.value?.id == track.id && player.currentMediaItem?.mediaId == track.id && _playbackError.value == null) {
            if (!player.isPlaying) {
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    player.prepare()
                }
                player.play()
            }
            return
        }

        // Cancel any pending playback or stream resolution jobs to prevent race conditions
        playJob?.cancel()
        // Immediately halt position updates without saving previous song's position
        stopPositionUpdates(saveCurrentPlayerPosition = false)
        controller?.pause()
        _currentTrack.value = track
        // Immediately reset position and previous duration so timer and progress bar start at 0:00
        _currentPositionMs.value = 0L
        _durationMs.value = track.durationMs ?: 0L

        _isBuffering.value = true
        _playbackError.value = null

        preferences.saveLastPlayback(track, 0L)
        scope.launch {
            libraryRepository.recordHistory(track)
        }

        playJob = scope.launch {
            // First check if track is available locally on disk for zero-latency offline playback
            val downloadedTrack = if (track.isDownloaded) track else libraryRepository.getDownloadedTrack(track.id)
            var localPath = downloadedTrack?.localFilePath
            var localFile = if (!localPath.isNullOrBlank()) java.io.File(localPath) else null

            // Fallback check in downloads directory if database path wasn't found or moved
            if (track.id.isNotBlank() && (localFile == null || !localFile.exists() || localFile.length() == 0L)) {
                val downloadsDir = java.io.File(context.filesDir, "downloads")
                val fallbackM4a = java.io.File(downloadsDir, "${track.id}.m4a")
                val fallbackWebm = java.io.File(downloadsDir, "${track.id}.webm")
                if (fallbackM4a.exists() && fallbackM4a.length() > 0L) {
                    localFile = fallbackM4a
                    localPath = fallbackM4a.absolutePath
                } else if (fallbackWebm.exists() && fallbackWebm.length() > 0L) {
                    localFile = fallbackWebm
                    localPath = fallbackWebm.absolutePath
                }
            }

            if (localFile != null && localFile.exists() && localFile.length() > 0) {
                val player = controller
                if (player == null) {
                    _isBuffering.value = false
                    _playbackError.value = "Audio player not ready"
                    return@launch
                }

                val effectiveTrack = (downloadedTrack ?: track).copy(localFilePath = localPath)
                if (effectiveTrack.durationMs != null && effectiveTrack.durationMs > 0L) {
                    _durationMs.value = effectiveTrack.durationMs
                }
                _currentTrack.value = effectiveTrack

                val artUri = when {
                    !effectiveTrack.localArtworkPath.isNullOrBlank() && java.io.File(effectiveTrack.localArtworkPath).exists() -> {
                        Uri.fromFile(java.io.File(effectiveTrack.localArtworkPath))
                    }
                    !effectiveTrack.artworkUrl.isNullOrBlank() -> Uri.parse(effectiveTrack.artworkUrl)
                    else -> null
                }

                val metadata = MediaMetadata.Builder()
                    .setTitle(effectiveTrack.title)
                    .setArtist(effectiveTrack.artist)
                    .setAlbumTitle(effectiveTrack.album)
                    .setArtworkUri(artUri)
                    .build()

                // Omit hardcoded setMimeType so ExoPlayer's extractors auto-detect container (m4a/aac or webm/opus) from file headers
                val mediaItem = MediaItem.Builder()
                    .setMediaId(effectiveTrack.id)
                    .setUri(Uri.fromFile(localFile))
                    .setMediaMetadata(metadata)
                    .build()

                player.setMediaItem(mediaItem, /* startPositionMs = */ 0L)
                player.prepare()
                player.play()
                return@launch
            }

            // Track is NOT downloaded: if offline, stop audio engine and clear stale media items immediately
            if (!isNetworkAvailable()) {
                val player = controller
                player?.stop()
                player?.clearMediaItems()
                _isBuffering.value = false
                _isPlaying.value = false
                _playbackError.value = "This song is not downloaded. Connect to the internet or download it first for offline playback."
                return@launch
            }

            // Stream online via repository
            val streamInfo = if (track.streamUri != null) {
                null
            } else {
                val result = repository.resolveStream(track.id)
                if (result.isSuccess) {
                    result.getOrNull()
                } else {
                    val player = controller
                    player?.stop()
                    player?.clearMediaItems()
                    _isBuffering.value = false
                    _isPlaying.value = false
                    _playbackError.value = "Unable to resolve stream: ${result.exceptionOrNull()?.message}"
                    return@launch
                }
            }

            val streamUri = track.streamUri ?: streamInfo?.uri?.toString()
            if (streamInfo?.durationMs != null && streamInfo.durationMs > 0L) {
                _durationMs.value = streamInfo.durationMs
                val updatedTrack = track.copy(durationMs = streamInfo.durationMs)
                _currentTrack.value = updatedTrack
            }

            val player = controller
            if (player == null) {
                _isBuffering.value = false
                _playbackError.value = "Audio player not ready"
                return@launch
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(if (!track.artworkUrl.isNullOrBlank()) Uri.parse(track.artworkUrl) else null)
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(streamUri)
                .setMimeType(streamInfo?.mimeType ?: "audio/mp4")
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem, /* startPositionMs = */ 0L)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        val current = _currentTrack.value

        if (player.isPlaying) {
            player.pause()
            preferences.savePositionOnly(player.currentPosition)
        } else {
            // If there is an active error, or no media item loaded, or the loaded item does not match the active track,
            // re-trigger playTrack for the current track rather than resuming a stale/mismatched media item
            if (_playbackError.value != null || player.currentMediaItem == null || player.currentMediaItem?.mediaId != current?.id) {
                if (current != null) {
                    playTrack(current, playlist)
                }
                return
            }

            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        player.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        preferences.savePositionOnly(positionMs)
    }

    fun hasNext(): Boolean = _queue.value.isNotEmpty() || playlist.isNotEmpty()
    fun hasPrevious(): Boolean = playlist.isNotEmpty()

    fun addToQueue(track: Track) {
        _queue.value = _queue.value + track
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _queue.value = currentQueue
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
    }

    fun playFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            val track = currentQueue.removeAt(index)
            _queue.value = currentQueue
            playTrack(track, playlist, forceRestart = true)
        }
    }

    fun skipNext(): Boolean {
        // FIFO Queue priority: first added song in the queue is played first
        val currentQueue = _queue.value
        if (currentQueue.isNotEmpty()) {
            val nextTrack = currentQueue.first()
            _queue.value = currentQueue.drop(1)
            playTrack(nextTrack, playlist, forceRestart = true)
            return true
        }

        if (playlist.isEmpty()) return false
        val current = _currentTrack.value
        val currentIndex = playlist.indexOfFirst { it.id == current?.id }
        val nextIndex = if (currentIndex != -1 && currentIndex < playlist.size - 1) {
            currentIndex + 1
        } else {
            0
        }
        playTrack(playlist[nextIndex], playlist, forceRestart = true)
        return true
    }

    fun skipPrevious(): Boolean {
        val player = controller
        if (player != null && player.currentPosition > 3000L) {
            seekTo(0L)
            return true
        }
        if (playlist.isEmpty()) return false
        val current = _currentTrack.value
        val currentIndex = playlist.indexOfFirst { it.id == current?.id }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            playlist.size - 1
        }
        playTrack(playlist[prevIndex], playlist, forceRestart = true)
        return true
    }

    fun retryPlayback() {
        val current = _currentTrack.value ?: return
        _playbackError.value = null
        autoRetryCount = 0
        playTrack(current, playlist, forceRestart = true)
    }

    fun clearError() {
        _playbackError.value = null
    }

    private suspend fun loadArtworkBytes(url: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) return@withContext null
        try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .size(512, 512)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    return@withContext stream.toByteArray()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var counter = 0
            while (isActive) {
                controller?.let { player ->
                    if (player.isPlaying) {
                        _currentPositionMs.value = player.currentPosition
                        val dur = player.duration
                        if (dur > 0L && dur != androidx.media3.common.C.TIME_UNSET && _durationMs.value != dur) {
                            _durationMs.value = dur
                        }
                        counter++
                        if (counter % 12 == 0) {
                            preferences.savePositionOnly(player.currentPosition)
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionUpdates(saveCurrentPlayerPosition: Boolean = true) {
        positionJob?.cancel()
        positionJob = null
        if (saveCurrentPlayerPosition) {
            controller?.let {
                val pos = it.currentPosition
                _currentPositionMs.value = pos
                preferences.savePositionOnly(pos)
            }
        }
    }

    fun release() {
        if (instance == this) {
            instance = null
        }
        playJob?.cancel()
        positionJob?.cancel()
        scope.cancel()
        controller?.release()
        controller = null
    }
}
