package com.fuelx.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fuelx.MainActivity
import com.fuelx.data.PlaybackPreferences
import com.fuelx.model.Track

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Configure the ExoPlayer audio engine
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // Pauses automatically when headphones are unplugged
            .build()

        // 2. PendingIntent to reopen FuelX when notification is tapped
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Build the MediaSession linking ExoPlayer to the Android OS
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // Called when the user clears/swipes the app from the recent screen
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            val currentItem = player.currentMediaItem
            if (currentItem != null) {
                val currentPosition = player.currentPosition
                val track = Track(
                    id = currentItem.mediaId,
                    title = currentItem.mediaMetadata.title?.toString() ?: "",
                    artist = currentItem.mediaMetadata.artist?.toString() ?: "",
                    album = currentItem.mediaMetadata.albumTitle?.toString(),
                    durationMs = player.duration.takeIf { it > 0 },
                    streamUri = currentItem.localConfiguration?.uri?.toString()
                )
                // Save the exact track and position for restoration
                PlaybackPreferences(this).saveLastPlayback(track, currentPosition)
            }
            player.pause()
        }
        // Stop foreground service, remove notification, and kill playback state
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
