package com.fuelx.data

import android.net.Uri
import com.fuelx.model.StreamInfo
import com.fuelx.model.Track
import kotlinx.coroutines.delay

class DemoMusicProvider : MusicProvider {

    private val catalog = MockTracks.sampleTracks

    override suspend fun searchTracks(query: String): List<Track> {
        // Simulate real-world network latency
        delay(250)

        if (query.isBlank()) return catalog

        return catalog.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            (it.album?.contains(query, ignoreCase = true) == true)
        }
    }

    override suspend fun getTrack(trackId: String): Track? {
        delay(100)
        return catalog.find { it.id == trackId }
    }

    override suspend fun getStream(trackId: String): StreamInfo {
        delay(150)
        val track = catalog.find { it.id == trackId }
            ?: throw IllegalArgumentException("Track not found: $trackId")

        val streamUrl = track.streamUri
            ?: throw IllegalStateException("No stream URL available for track: $trackId")

        return StreamInfo(
            uri = Uri.parse(streamUrl),
            mimeType = "audio/mpeg",
            expiresAtEpochMs = System.currentTimeMillis() + (6 * 3600 * 1000L) // 6-hour validity
        )
    }

    override suspend fun getTrendingTracks(): List<Track> {
        delay(200)
        return catalog
    }
}
