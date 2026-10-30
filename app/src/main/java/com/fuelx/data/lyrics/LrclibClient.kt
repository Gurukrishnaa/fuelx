package com.fuelx.data.lyrics

import android.net.Uri
import com.fuelx.model.Track
import com.fuelx.util.LrcParser
import com.fuelx.util.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Robust client for the open-source LRCLIB lyrics service (https://lrclib.net/).
 * Resolves both synchronized (time-coded) and plain-text lyrics for songs
 * with intelligent metadata normalization and automated retry resilience.
 */
class LrclibClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    suspend fun fetchLyrics(track: Track): Result<ParsedLyrics> = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = cleanTrackTitle(track.title)
            val primaryArt = extractPrimaryArtist(track.artist)
            val fullArt = track.artist.replace(Regex("""(?i)\s*-\s*Topic"""), "").trim()

            // Strategy 1: Exact lookup with cleaned title + primary artist
            fetchExact(cleanTitle, primaryArt)?.let { return@withContext Result.success(it) }

            // Strategy 2: If primary artist differs from full artist, try full artist
            if (primaryArt != fullArt && fullArt.isNotBlank()) {
                fetchExact(cleanTitle, fullArt)?.let { return@withContext Result.success(it) }
            }

            // Strategy 3: Raw cleaned title if it differed from stripped cleanTitle
            val rawTitle = track.title.replace("\"", "").trim()
            if (rawTitle != cleanTitle && rawTitle.isNotBlank()) {
                fetchExact(rawTitle, primaryArt)?.let { return@withContext Result.success(it) }
            }

            // Strategy 4: Field-based search via /api/search?track_name=...&artist_name=...
            fetchSearchByFields(cleanTitle, primaryArt)?.let { return@withContext Result.success(it) }

            // Strategy 5: Query search via /api/search?q=cleanTitle primaryArtist
            fetchViaSearch("$cleanTitle $primaryArt")?.let { return@withContext Result.success(it) }

            // Strategy 6: Query search with cleanTitle only
            if (cleanTitle.length >= 3) {
                fetchViaSearch(cleanTitle)?.let { return@withContext Result.success(it) }
            }

            Result.failure(NoSuchElementException("No lyrics available for \"${track.title}\""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchExact(trackTitle: String, artistName: String): ParsedLyrics? {
        if (trackTitle.isBlank() || artistName.isBlank()) return null
        val uri = Uri.parse("$BASE_URL/api/get").buildUpon()
            .appendQueryParameter("track_name", trackTitle)
            .appendQueryParameter("artist_name", artistName)
            .build()

        val request = Request.Builder()
            .url(uri.toString())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = executeWithRetry(request) ?: return null
        return response.use { res ->
            if (!res.isSuccessful) return null
            val bodyString = res.body?.string() ?: return null
            parseLrclibResponse(JSONObject(bodyString))
        }
    }

    private fun fetchSearchByFields(trackTitle: String, artistName: String): ParsedLyrics? {
        if (trackTitle.isBlank()) return null
        val uriBuilder = Uri.parse("$BASE_URL/api/search").buildUpon()
            .appendQueryParameter("track_name", trackTitle)
        if (artistName.isNotBlank()) {
            uriBuilder.appendQueryParameter("artist_name", artistName)
        }

        val request = Request.Builder()
            .url(uriBuilder.build().toString())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = executeWithRetry(request) ?: return null
        return response.use { res ->
            if (!res.isSuccessful) return null
            val bodyString = res.body?.string() ?: return null
            parseSearchResultArray(bodyString)
        }
    }

    private fun fetchViaSearch(query: String): ParsedLyrics? {
        if (query.isBlank()) return null
        val uri = Uri.parse("$BASE_URL/api/search").buildUpon()
            .appendQueryParameter("q", query.trim())
            .build()

        val request = Request.Builder()
            .url(uri.toString())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = executeWithRetry(request) ?: return null
        return response.use { res ->
            if (!res.isSuccessful) return null
            val bodyString = res.body?.string() ?: return null
            parseSearchResultArray(bodyString)
        }
    }

    private fun parseSearchResultArray(bodyString: String): ParsedLyrics? {
        val array = JSONArray(bodyString)
        if (array.length() == 0) return null

        var fallbackPlain: ParsedLyrics? = null
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val parsed = parseLrclibResponse(item)
            if (parsed != null) {
                if (parsed.isSynchronized) {
                    return parsed
                } else if (fallbackPlain == null) {
                    fallbackPlain = parsed
                }
            }
        }
        return fallbackPlain
    }

    private fun executeWithRetry(request: Request, maxRetries: Int = 2): Response? {
        var delayMs = 350L
        for (attempt in 0..maxRetries) {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    return response
                }
                val code = response.code
                // 503 Server Overloaded or 429 Too Many Requests -> auto retry with exponential backoff
                if (code == 503 || code == 429) {
                    response.close()
                    if (attempt < maxRetries) {
                        Thread.sleep(delayMs)
                        delayMs *= 2
                        continue
                    }
                } else {
                    return response
                }
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    Thread.sleep(delayMs)
                    delayMs *= 2
                    continue
                }
            }
        }
        return null
    }

    private fun parseLrclibResponse(json: JSONObject): ParsedLyrics? {
        val isInstrumental = json.optBoolean("instrumental", false)
        if (isInstrumental) {
            return ParsedLyrics(
                isSynchronized = false,
                isInstrumental = true,
                lines = emptyList(),
                plainText = null
            )
        }

        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() }
        if (synced != null) {
            val parsed = LrcParser.parse(synced)
            if (parsed.lines.isNotEmpty()) {
                return parsed
            }
        }

        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() }
        if (plain != null) {
            return LrcParser.parse(plain)
        }

        return null
    }

    /**
     * Cleans common metadata suffixes like "(From "Movie")", "(Official Music Video)",
     * "[Remastered]", " | Sony Music", etc. for higher LRCLIB matching hit rates.
     */
    private fun cleanTrackTitle(title: String): String {
        var cleaned = title
        // 1. Strip (From "Movie"), [From "Movie"], (From Movie), etc.
        cleaned = cleaned.replace(Regex("""(?i)\s*[\(\[]from\s+.*?[\)\]]"""), "")
        // 2. Strip standard suffixes like (feat. ...), [Official Video], (Audio), (Lyrics), (Remastered), etc.
        cleaned = cleaned.replace(Regex("""(?i)\s*[\(\[](?:feat\.?|ft\.?|official|music\s+video|video|audio|lyric(?:al)?(?:\s+video)?|remaster(?:ed)?|ost|soundtrack|full\s+song|visualizer).*?[\)\]]"""), "")
        // 3. Strip trailing pipes or hyphens with suffixes like " | Movie Name" or " - Single"
        cleaned = cleaned.replace(Regex("""(?i)\s*\|\s*.*$"""), "")
        cleaned = cleaned.replace(Regex("""(?i)\s*-\s*(?:single|ep|official|audio|video|lyric.*|from\s+.*)$"""), "")
        // 4. Strip stray double quotes which break query syntax
        cleaned = cleaned.replace("\"", "").trim()
        return cleaned.ifBlank { title.replace("\"", "").trim() }
    }

    /**
     * Extracts the primary artist from collaborative strings such as
     * "Anirudh Ravichander, Deepthi Suresh" -> "Anirudh Ravichander"
     */
    private fun extractPrimaryArtist(artist: String): String {
        val withoutTopic = artist.replace(Regex("""(?i)\s*-\s*Topic"""), "")
        val primary = withoutTopic.split(Regex("""(?i)\s*(?:,|&|•|\bfeat\.?\b|\bft\.?\b|/)\s*""")).firstOrNull()?.trim()
        return if (!primary.isNullOrBlank()) primary else withoutTopic.trim()
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "FuelX/1.0 (Android; music-player)"
    }
}
