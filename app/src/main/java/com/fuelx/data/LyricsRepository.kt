package com.fuelx.data

import android.content.Context
import com.fuelx.data.lyrics.LrclibClient
import com.fuelx.model.Track
import com.fuelx.util.LrcParser
import com.fuelx.util.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface LyricsRepository {
    suspend fun getLyrics(track: Track): Result<ParsedLyrics>
}

class DefaultLyricsRepository(
    private val context: Context? = null,
    private val lrclibClient: LrclibClient = LrclibClient()
) : LyricsRepository {

    private val lyricsCache = LinkedHashMap<String, ParsedLyrics>(30, 0.75f, true)

    override suspend fun getLyrics(track: Track): Result<ParsedLyrics> = withContext(Dispatchers.IO) {
        // 1. Return from in-memory cache immediately if present
        synchronized(lyricsCache) {
            lyricsCache[track.id]?.let { return@withContext Result.success(it) }
        }

        // 2. Check offline downloaded lyrics file (if song was downloaded)
        try {
            val localLrcFile = if (context != null) {
                File(File(context.filesDir, "downloads"), "${track.id}.lrc")
            } else null

            if (localLrcFile != null && localLrcFile.exists() && localLrcFile.length() > 0L) {
                val content = localLrcFile.readText()
                if (content.trim() == "[instrumental]") {
                    val instrumental = ParsedLyrics(isSynchronized = false, isInstrumental = true, lines = emptyList(), plainText = null)
                    cacheLyrics(track.id, instrumental)
                    return@withContext Result.success(instrumental)
                }
                val parsed = LrcParser.parse(content)
                if (parsed.lines.isNotEmpty() || !parsed.plainText.isNullOrBlank()) {
                    cacheLyrics(track.id, parsed)
                    return@withContext Result.success(parsed)
                }
            }
        } catch (e: Exception) {
            // Ignore error and fall back to online fetch
        }

        try {
            // 3. Query LRCLIB online lyrics database
            val lrclibResult = lrclibClient.fetchLyrics(track)
            if (lrclibResult.isSuccess) {
                val parsed = lrclibResult.getOrThrow()
                cacheLyrics(track.id, parsed)

                // If track is downloaded, cache raw lyrics locally for offline use
                if (context != null && track.isDownloaded) {
                    try {
                        val downloadsDir = File(context.filesDir, "downloads")
                        val targetLrc = File(downloadsDir, "${track.id}.lrc")
                        if (!targetLrc.exists()) {
                            val raw = lrclibClient.fetchRawLyrics(track)
                            if (!raw.isNullOrBlank()) {
                                targetLrc.writeText(raw)
                            }
                        }
                    } catch (e: Exception) {
                        // Best effort
                    }
                }

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
