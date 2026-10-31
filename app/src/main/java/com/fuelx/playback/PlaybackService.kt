package com.fuelx.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.fuelx.MainActivity
import com.fuelx.data.PlaybackPreferences
import com.fuelx.model.Track

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fuelx_playback_channel"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Create Notification Channel required for Android 8.0+ through Android 11 Quick Settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "FuelX Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "FuelX background music playback controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }

        // 2. Configure MediaNotificationProvider with the explicit notification channel
        try {
            setMediaNotificationProvider(
                DefaultMediaNotificationProvider.Builder(this)
                    .setChannelId(NOTIFICATION_CHANNEL_ID)
                    .build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Configure smooth network data source with generous timeouts & redirect support
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        // Wrap in DefaultDataSource so both remote HTTP/HTTPS streams and local offline files (file://) play seamlessly
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(defaultDataSourceFactory)

        // 4. High-performance buffer control: prevents buffer starvation, pops, static, and distortion
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(/* backBufferDurationMs = */ 30_000, /* retainBackBufferFromKeyframe = */ true)
            .build()

        // 5. Configure the base ExoPlayer audio engine
        val basePlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // Pauses automatically when headphones are unplugged
            .build()

        // 6. Wrap in ForwardingPlayer so Android 11 Quick Settings notification and lockscreen
        // permanently display the Next/Previous buttons and route skips to the active playlist
        val forwardingPlayer = object : ForwardingPlayer(basePlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        PlayerController.instance?.playlist?.isNotEmpty() == true
                    }
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean {
                return PlayerController.instance?.hasNext() ?: true
            }

            override fun hasPreviousMediaItem(): Boolean {
                return PlayerController.instance?.hasPrevious() ?: true
            }

            override fun seekToNext() {
                seekToNextMediaItem()
            }

            override fun seekToNextMediaItem() {
                val handled = PlayerController.instance?.skipNext()
                if (handled != true) {
                    super.seekToNextMediaItem()
                }
            }

            override fun seekToPrevious() {
                seekToPreviousMediaItem()
            }

            override fun seekToPreviousMediaItem() {
                val handled = PlayerController.instance?.skipPrevious()
                if (handled != true) {
                    super.seekToPreviousMediaItem()
                }
            }
        }

        // 7. PendingIntent to reopen FuelX when notification is tapped
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 8. Build the MediaSession linking ForwardingPlayer to the Android OS
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailablePlayerCommands(availablePlayerCommands)
                        .build()
                }
            })
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val player = session.player
        if (mediaSession == null || player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancelAll()
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    // Called when the user clears/swipes the app from the recent screen
    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val player = mediaSession?.player
            if (player != null) {
                player.pause()
                player.stop()
                player.clearMediaItems()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Dismiss the persistent notification from Android notification shade & lockscreen
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancelAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // User explicitly cleared app from recents: purge saved playback state
        // so on fresh launch the app opens cleanly without a stale miniplayer
        try {
            PlaybackPreferences(this).clearLastPlayback()
            PlayerController.instance?.clearState()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Release MediaSession so Android Quick Settings media player tile vanishes immediately
        try {
            mediaSession?.run {
                player.release()
                release()
                mediaSession = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Stop foreground service completely
        stopSelf()

        super.onTaskRemoved(rootIntent)

        // Terminate process so Android SystemUI immediately removes the media player notification and tile
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.cancelAll()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
