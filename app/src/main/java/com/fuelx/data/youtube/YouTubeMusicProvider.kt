package com.fuelx.data.youtube

import com.fuelx.data.MusicProvider
import com.fuelx.model.StreamInfo
import com.fuelx.model.Track
import java.util.concurrent.ConcurrentHashMap

/**
 * Production music provider backed exclusively by YouTube Music InnerTube and open streaming instances.
 * Provides access to the universal YouTube Music catalog, ad-free streaming, high-resolution
 * album covers, and synchronized lyrics support.
 */
class YouTubeMusicProvider(
    private val innertubeClient: InnertubeClient = InnertubeClient(),
    private val streamResolver: InvidiousStreamResolver = InvidiousStreamResolver()
) : MusicProvider {

    // Fast in-memory track cache to resolve metadata by ID
    private val trackCache = ConcurrentHashMap<String, Track>()

    override suspend fun searchTracks(query: String): List<Track> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        return try {
            val results = innertubeClient.search(trimmed)
            results.forEach { trackCache[it.id] = it }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getTrack(trackId: String): Track? {
        return trackCache[trackId]
    }

    override suspend fun getStream(trackId: String): StreamInfo {
        return streamResolver.resolveStream(trackId)
    }

    override suspend fun getTrendingTracks(): List<Track> {
        return getTrendingTracks(emptySet())
    }

    override suspend fun getTrendingTracks(languages: Set<String>): List<Track> {
        return try {
            val trending = innertubeClient.getTrending(languages)
            trending.forEach { trackCache[it.id] = it }
            trending
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getGenreTracks(genreId: String, languages: Set<String>): List<Track> {
        return try {
            val tracks = innertubeClient.getGenreTracks(genreId, languages)
            tracks.forEach { trackCache[it.id] = it }
            tracks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getRadio(seedVideoId: String): List<Track> {
        return try {
            val tracks = innertubeClient.getRadio(seedVideoId)
            tracks.forEach { trackCache[it.id] = it }
            tracks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
