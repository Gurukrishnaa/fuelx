package com.fuelx.data

import com.fuelx.model.StreamInfo
import com.fuelx.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.fuelx.data.youtube.YouTubeMusicProvider

interface MusicRepository {
    suspend fun getTrending(): Result<List<Track>>
    suspend fun getTrending(languages: Set<String>): Result<List<Track>> = getTrending()
    suspend fun search(query: String): Result<List<Track>>
    suspend fun resolveStream(trackId: String, forceRefresh: Boolean = false): Result<StreamInfo>
}

class DefaultMusicRepository(
    private val provider: MusicProvider = YouTubeMusicProvider()
) : MusicRepository {

    // Simple bounded in-memory cache for search results
    private val searchCache = LinkedHashMap<String, List<Track>>(20, 0.75f, true)
    private val streamCache = LinkedHashMap<String, StreamInfo>(20, 0.75f, true)

    override suspend fun getTrending(): Result<List<Track>> = getTrending(emptySet())

    override suspend fun getTrending(languages: Set<String>): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val tracks = provider.getTrendingTracks(languages)
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun search(query: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase()

        // Check cache first
        synchronized(searchCache) {
            searchCache[trimmed]?.let { return@withContext Result.success(it) }
        }

        try {
            val results = provider.searchTracks(query)
            synchronized(searchCache) {
                if (searchCache.size >= 25) {
                    val oldestKey = searchCache.keys.first()
                    searchCache.remove(oldestKey)
                }
                searchCache[trimmed] = results
            }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resolveStream(trackId: String, forceRefresh: Boolean): Result<StreamInfo> = withContext(Dispatchers.IO) {
        // If forceRefresh, evict cached stream to recover from expiration or HTTP 403
        synchronized(streamCache) {
            if (forceRefresh) {
                streamCache.remove(trackId)
            } else {
                val cached = streamCache[trackId]
                if (cached != null) {
                    val now = System.currentTimeMillis()
                    val expiresAt = cached.expiresAtEpochMs
                    if (expiresAt == null || expiresAt > now + 60_000L) { // At least 1 min valid
                        return@withContext Result.success(cached)
                    }
                    streamCache.remove(trackId)
                }
            }
        }

        try {
            val stream = provider.getStream(trackId)
            synchronized(streamCache) {
                streamCache[trackId] = stream
            }
            Result.success(stream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
