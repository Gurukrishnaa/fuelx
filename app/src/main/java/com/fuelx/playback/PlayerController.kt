package com.fuelx.playback

import android.content.ComponentName
import android.content.Context
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

    private var playlist: List<Track> = emptyList()

    init {
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
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                    // Persist exact position on pause
                    preferences.savePositionOnly(player.currentPosition)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0L)
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
                .build()

            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(t.streamUri)
                .setMediaMetadata(metadata)
                .build()
        }

        val startIndex = tracks.indexOfFirst { it.id == matchedTrack.id }.coerceAtLeast(0)
        player.setMediaItems(mediaItems, startIndex, saved.positionMs)
        player.prepare()
        player.pause() // Stays paused at exact saved point
    }

    fun playTrack(track: Track, tracks: List<Track> = playlist) {
        this.playlist = tracks
        _currentTrack.value = track
        _isBuffering.value = true
        _playbackError.value = null

        preferences.saveLastPlayback(track, 0L)
        scope.launch {
            libraryRepository.recordHistory(track)
        }

        scope.launch {
            val streamUri = if (track.streamUri != null) {
                track.streamUri
            } else {
                val result = repository.resolveStream(track.id)
                if (result.isSuccess) {
                    result.getOrNull()?.uri?.toString()
                } else {
                    _isBuffering.value = false
                    _playbackError.value = "Unable to resolve stream: ${result.exceptionOrNull()?.message}"
                    return@launch
                }
            }

            val player = controller
            if (player == null) {
                _isBuffering.value = false
                _playbackError.value = "Audio player not ready"
                return@launch
            }

            val mediaItems = tracks.map { t ->
                val uri = if (t.id == track.id) streamUri else t.streamUri

                val metadata = MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .build()

                MediaItem.Builder()
                    .setMediaId(t.id)
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build()
            }

            val startIndex = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

            player.setMediaItems(mediaItems, startIndex, /* startPositionMs = */ 0L)
            player.prepare()
            player.play()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) {
            player.pause()
            preferences.savePositionOnly(player.currentPosition)
        } else {
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

    fun skipNext() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(0, 0L)
        }
    }

    fun skipPrevious() {
        val player = controller ?: return
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun clearError() {
        _playbackError.value = null
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            var counter = 0
            while (isActive) {
                controller?.let {
                    val pos = it.currentPosition
                    _currentPositionMs.value = pos
                    counter++
                    // Persist position every 3 seconds while playing
                    if (counter % 10 == 0) {
                        preferences.savePositionOnly(pos)
                    }
                }
                delay(300)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
        controller?.let {
            val pos = it.currentPosition
            _currentPositionMs.value = pos
            preferences.savePositionOnly(pos)
        }
    }

    fun release() {
        positionJob?.cancel()
        scope.cancel()
        controller?.release()
        controller = null
    }
}
