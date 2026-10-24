package com.fuelx.data

import com.fuelx.model.StreamInfo
import com.fuelx.model.Track

/**
 * Pluggable provider interface for music sources.
 * Can be implemented by local catalog, demo open-license streams,
 * or production providers (e.g. YouTube Music InnerTube).
 */
interface MusicProvider {
    /**
     * Search tracks matching the given search query.
     */
    suspend fun searchTracks(query: String): List<Track>

    /**
     * Get track metadata by unique ID.
     */
    suspend fun getTrack(trackId: String): Track?

    /**
     * Resolve the playable stream URL and metadata for a track.
     */
    suspend fun getStream(trackId: String): StreamInfo

    /**
     * Get initial recommended/trending tracks for the Home screen.
     */
    suspend fun getTrendingTracks(): List<Track>
}
