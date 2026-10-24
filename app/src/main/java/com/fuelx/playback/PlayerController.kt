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
import com.fuelx.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerController(
    private val context: Context,
    private val repository: MusicRepository = DefaultMusicRepository()
) {

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
    }

    fun playTrack(track: Track, tracks: List<Track> = playlist) {
        this.playlist = tracks
        _currentTrack.value = track
        _isBuffering.value = true
        _playbackError.value = null

        scope.launch {
            val streamUri = if (track.streamUri != null) {
                track.streamUri
            } else {
                // Dynamically resolve stream from provider repository
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

            // Build list of MediaItems with proper metadata for notification & lock screen
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
            while (isActive) {
                controller?.let {
                    _currentPositionMs.value = it.currentPosition
                }
                delay(300)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
        controller?.let {
            _currentPositionMs.value = it.currentPosition
        }
    }

    fun release() {
        positionJob?.cancel()
        scope.cancel()
        controller?.release()
        controller = null
    }
}
