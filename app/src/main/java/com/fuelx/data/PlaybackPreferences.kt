package com.fuelx.data

import android.content.Context
import com.fuelx.model.Track

data class SavedPlaybackState(
    val trackId: String,
    val positionMs: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val streamUri: String?,
    val artworkUrl: String? = null
) {
    fun toTrack(): Track {
        return Track(
            id = trackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            streamUri = streamUri
        )
    }
}

class PlaybackPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("fuelx_playback_prefs", Context.MODE_PRIVATE)

    fun saveLastPlayback(track: Track, positionMs: Long) {
        prefs.edit()
            .putString(KEY_TRACK_ID, track.id)
            .putLong(KEY_POSITION_MS, positionMs)
            .putString(KEY_TITLE, track.title)
            .putString(KEY_ARTIST, track.artist)
            .putString(KEY_ALBUM, track.album)
            .putLong(KEY_DURATION_MS, track.durationMs ?: 0L)
            .putString(KEY_STREAM_URI, track.streamUri)
            .putString(KEY_ARTWORK_URL, track.artworkUrl)
            .apply()
    }

    fun savePositionOnly(positionMs: Long) {
        prefs.edit().putLong(KEY_POSITION_MS, positionMs).apply()
    }

    fun getLastPlayback(): SavedPlaybackState? {
        val trackId = prefs.getString(KEY_TRACK_ID, null) ?: return null
        val positionMs = prefs.getLong(KEY_POSITION_MS, 0L)
        val title = prefs.getString(KEY_TITLE, "") ?: ""
        val artist = prefs.getString(KEY_ARTIST, "") ?: ""
        val album = prefs.getString(KEY_ALBUM, null)
        val durationMs = prefs.getLong(KEY_DURATION_MS, 0L).takeIf { it > 0 }
        val streamUri = prefs.getString(KEY_STREAM_URI, null)
        val artworkUrl = prefs.getString(KEY_ARTWORK_URL, null)

        return SavedPlaybackState(
            trackId = trackId,
            positionMs = positionMs,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            streamUri = streamUri,
            artworkUrl = artworkUrl
        )
    }

    companion object {
        private const val KEY_TRACK_ID = "last_track_id"
        private const val KEY_POSITION_MS = "last_position_ms"
        private const val KEY_TITLE = "last_title"
        private const val KEY_ARTIST = "last_artist"
        private const val KEY_ALBUM = "last_album"
        private const val KEY_DURATION_MS = "last_duration_ms"
        private const val KEY_STREAM_URI = "last_stream_uri"
        private const val KEY_ARTWORK_URL = "last_artwork_url"
    }
}
