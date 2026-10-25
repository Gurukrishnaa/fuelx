package com.fuelx.data.lyrics

import android.net.Uri
import com.fuelx.model.Track
import com.fuelx.util.LrcParser
import com.fuelx.util.ParsedLyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Robust client for the open-source LRCLIB lyrics service (https://lrclib.net/).
 * Resolves both synchronized (time-coded) and plain-text lyrics for songs.
 */
class LrclibClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    suspend fun fetchLyrics(track: Track): Result<ParsedLyrics> = withContext(Dispatchers.IO) {
        try {
            // Strategy 1: Exact lookup via /api/get
            val exactResult = fetchExact(track)
            if (exactResult != null) {
                return@withContext Result.success(exactResult)
            }

            // Strategy 2: Search fallback via /api/search
            val searchResult = fetchViaSearch(track)
            if (searchResult != null) {
                return@withContext Result.success(searchResult)
            }

            Result.failure(NoSuchElementException("No lyrics available for \"${track.title}\""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchExact(track: Track): ParsedLyrics? {
        val uriBuilder = Uri.parse("$BASE_URL/api/get").buildUpon()
            .appendQueryParameter("track_name", cleanTrackTitle(track.title))
            .appendQueryParameter("artist_name", track.artist)

        track.album?.takeIf { it.isNotBlank() }?.let {
            uriBuilder.appendQueryParameter("album_name", it)
        }

        track.durationMs?.let {
            val seconds = it / 1000
            if (seconds > 0) {
                uriBuilder.appendQueryParameter("duration", seconds.toString())
            }
        }

        val request = Request.Builder()
            .url(uriBuilder.build().toString())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            parseLrclibResponse(JSONObject(bodyString))
        }
    }

    private fun fetchViaSearch(track: Track): ParsedLyrics? {
        val query = "${cleanTrackTitle(track.title)} ${track.artist}".trim()
        val uri = Uri.parse("$BASE_URL/api/search").buildUpon()
            .appendQueryParameter("q", query)
            .build()

        val request = Request.Builder()
            .url(uri.toString())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val array = JSONArray(bodyString)
            if (array.length() == 0) return null

            // Prioritize items that have synchronized lyrics
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
            fallbackPlain
        }
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
     * Cleans common metadata suffixes like "(Official Music Video)", "[Remastered]", etc.
     * for higher LRCLIB matching hit rates.
     */
    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("\\s*[\\(\\[](?:feat\\.?|ft\\.?|official|remastered|lyrics|video|audio).*?[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "FuelX/1.0 (https://github.com/Gurukrishnaa/fuelx)"
    }
}
