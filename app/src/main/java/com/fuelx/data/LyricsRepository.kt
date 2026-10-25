package com.fuelx.data

import com.fuelx.data.lyrics.LrclibClient
import com.fuelx.model.Track
import com.fuelx.util.LrcParser
import com.fuelx.util.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LyricsRepository {
    suspend fun getLyrics(track: Track): Result<ParsedLyrics>
}

class DefaultLyricsRepository(
    private val lrclibClient: LrclibClient = LrclibClient()
) : LyricsRepository {

    private val lyricsCache = LinkedHashMap<String, ParsedLyrics>(30, 0.75f, true)

    override suspend fun getLyrics(track: Track): Result<ParsedLyrics> = withContext(Dispatchers.IO) {
        // 1. Return from in-memory cache immediately if present
        synchronized(lyricsCache) {
            lyricsCache[track.id]?.let { return@withContext Result.success(it) }
        }

        try {
            // 2. Query LRCLIB online lyrics database
            val lrclibResult = lrclibClient.fetchLyrics(track)
            if (lrclibResult.isSuccess) {
                val parsed = lrclibResult.getOrThrow()
                cacheLyrics(track.id, parsed)
                return@withContext Result.success(parsed)
            }

            Result.failure(NoSuchElementException("No lyrics found for \"${track.title}\""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun cacheLyrics(trackId: String, parsed: ParsedLyrics) {
        synchronized(lyricsCache) {
            if (lyricsCache.size >= 50) {
                val oldestKey = lyricsCache.keys.first()
                lyricsCache.remove(oldestKey)
            }
            lyricsCache[trackId] = parsed
        }
    }
}
