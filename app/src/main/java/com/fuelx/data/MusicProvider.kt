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
     * Get initial recommended/trending tracks for the Home screen, optionally filtered by language preferences.
     */
    suspend fun getTrendingTracks(): List<Track>
    suspend fun getTrendingTracks(languages: Set<String>): List<Track> = getTrendingTracks()

    /**
     * Get genre tracks filtered by genre and language preferences.
     */
    suspend fun getGenreTracks(genreId: String, languages: Set<String>): List<Track> = emptyList()

    /**
     * Get radio recommendations (similar tracks) for a given seed track ID.
     */
    suspend fun getRadio(seedVideoId: String): List<Track> = emptyList()
}
