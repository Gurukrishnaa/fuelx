package com.fuelx.data

import com.fuelx.model.Track
import com.fuelx.util.LrcParser
import com.fuelx.util.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

interface LyricsRepository {
    suspend fun getLyrics(track: Track): Result<ParsedLyrics>
}

class DefaultLyricsRepository : LyricsRepository {

    private val lyricsCache = LinkedHashMap<String, ParsedLyrics>(30, 0.75f, true)

    override suspend fun getLyrics(track: Track): Result<ParsedLyrics> = withContext(Dispatchers.IO) {
        // Return from cache immediately if present
        synchronized(lyricsCache) {
            lyricsCache[track.id]?.let { return@withContext Result.success(it) }
        }

        try {
            // Simulated network latency
            delay(150)

            val rawLrc = MockTracks.lrcCatalog[track.id]
            val parsed = LrcParser.parse(rawLrc)

            synchronized(lyricsCache) {
                if (lyricsCache.size >= 50) {
                    val oldestKey = lyricsCache.keys.first()
                    lyricsCache.remove(oldestKey)
                }
                lyricsCache[track.id] = parsed
            }

            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
