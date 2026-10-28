package com.fuelx.data.youtube

import android.net.Uri
import com.fuelx.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * YouTube Music InnerTube client.
 * Connects directly to the YouTube Music WEB_REMIX API to provide a universal catalog
 * of songs, search results, high-resolution album artwork, and artist metadata.
 */
class InnertubeClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val requestBodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240401.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", trimmed)
            }

            val request = Request.Builder()
                .url(SEARCH_ENDPOINT)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .header("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseSearchResponse(JSONObject(body)).filter { isCleanSong(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getTrending(languages: Set<String> = emptySet()): List<Track> = withContext(Dispatchers.IO) {
        if (languages.isEmpty()) {
            val hits = search("Latest Hit Songs 2024").filter { isCleanSong(it) }
            return@withContext if (hits.isNotEmpty()) hits else search("Trending Songs").filter { isCleanSong(it) }
        }

        val allTracks = mutableListOf<Track>()
        val seenIds = mutableSetOf<String>()

        for (lang in languages) {
            val query = when (lang.lowercase()) {
                "tamil" -> "Latest Tamil Movie Hit Songs 2024"
                "hindi" -> "Latest Bollywood Hit Songs 2024"
                "malayalam" -> "Latest Malayalam Movie Songs 2024"
                "english" -> "Top Billboard Hit Songs 2024"
                else -> "Latest $lang Hit Songs 2024"
            }
            val results = search(query).filter { isCleanSong(it) }
            results.take(12).forEach { track ->
                if (seenIds.add(track.id)) {
                    allTracks.add(track)
                }
            }
        }

        if (allTracks.isNotEmpty()) {
            allTracks
        } else {
            val fallback = search("Latest Hit Songs 2024").filter { isCleanSong(it) }
            if (fallback.isNotEmpty()) fallback else search("Trending Songs").filter { isCleanSong(it) }
        }
    }

    suspend fun getGenreTracks(genreId: String, languages: Set<String> = emptySet()): List<Track> = withContext(Dispatchers.IO) {
        if (genreId.equals("all", ignoreCase = true)) {
            return@withContext getTrending(languages)
        }

        val allTracks = mutableListOf<Track>()
        val seenIds = mutableSetOf<String>()
        val langs = if (languages.isEmpty()) setOf("Global") else languages

        for (lang in langs) {
            val l = lang.lowercase()
            val query = when (genreId.lowercase()) {
                "melody" -> when (l) {
                    "tamil" -> "Tamil Melody Hit Songs 2024"
                    "hindi" -> "Bollywood Romantic Melody Songs 2024"
                    "malayalam" -> "Malayalam Melody Songs 2024"
                    "english" -> "Pop Acoustic Love Songs 2024"
                    else -> "$lang Melody Romantic Songs"
                }
                "party" -> when (l) {
                    "tamil" -> "Tamil Kuthu Party Dance Songs"
                    "hindi" -> "Bollywood Dance Party Songs"
                    "malayalam" -> "Malayalam Dance Party Songs"
                    "english" -> "Party Dance Pop Club Songs"
                    else -> "$lang Dance Party Songs"
                }
                "hiphop" -> when (l) {
                    "tamil" -> "Tamil Hip Hop Rap Songs"
                    "hindi" -> "Desi Hip Hop Hindi Rap Songs"
                    "malayalam" -> "Malayalam Hip Hop Songs"
                    "english" -> "Hip Hop Rap Hits"
                    else -> "$lang Hip Hop Rap Songs"
                }
                "chill" -> when (l) {
                    "tamil" -> "Tamil Lo-Fi Acoustic Songs"
                    "hindi" -> "Hindi Lo-Fi Acoustic Songs"
                    "malayalam" -> "Malayalam Lo-Fi Acoustic Songs"
                    "english" -> "Lo-Fi Acoustic Chill Pop Songs"
                    else -> "$lang Acoustic Chill Songs"
                }
                "rock" -> when (l) {
                    "tamil" -> "Tamil Rock Fast Songs"
                    "hindi" -> "Hindi Rock Metal Songs"
                    "malayalam" -> "Malayalam Rock Songs"
                    "english" -> "Rock Hits Modern Songs"
                    else -> "$lang Rock Songs"
                }
                "folk" -> when (l) {
                    "tamil" -> "Tamil Folk Gaana Songs"
                    "hindi" -> "Hindi Indie Folk Songs"
                    "malayalam" -> "Malayalam Folk Indie Songs"
                    "english" -> "Indie Folk Acoustic Songs"
                    else -> "$lang Folk Indie Songs"
                }
                else -> "$lang $genreId Songs"
            }

            val results = search(query).filter { isCleanSong(it) }
            results.take(12).forEach { track ->
                if (seenIds.add(track.id)) {
                    allTracks.add(track)
                }
            }
        }

        if (allTracks.isNotEmpty()) allTracks else getTrending(languages)
    }

    /**
     * Filters out non-song videos such as long compilation playlists, jukeboxes,
     * full albums, 1-hour loops, or short video snippets.
     */
    fun isCleanSong(track: Track): Boolean {
        // 1. Filter out videos that are excessively long or too short
        // Single songs must not exceed 15 minutes (900,000 ms). Anything longer is a mix/jukebox.
        val dur = track.durationMs
        if (dur != null) {
            if (dur > 15 * 60 * 1000L || dur < 45_000L) {
                return false
            }
        }

        // 2. Reject compilation and jukebox patterns in title
        val title = track.title.lowercase()
        val blockedPatterns = listOf(
            "jukebox",
            "audio jukebox",
            "video jukebox",
            "top 100",
            "top 50",
            "top 40",
            "top 30",
            "top 20",
            "top 10",
            "non stop",
            "nonstop",
            "non-stop",
            "full album",
            "full songs",
            "all songs",
            "all hits",
            "mega hits",
            "greatest hits",
            "best of",
            "collection",
            "compilation",
            "mashup",
            "1 hour",
            "2 hour",
            "3 hour",
            "hours",
            "playlist",
            "audio songs"
        )

        for (pattern in blockedPatterns) {
            if (title.contains(pattern)) {
                return false
            }
        }

        // 3. Reject compilation patterns in artist or album names
        val artist = track.artist.lowercase()
        if (artist.contains("jukebox") || artist.contains("compilation")) {
            return false
        }

        return true
    }

    private fun parseSearchResponse(json: JSONObject): List<Track> {
        val results = mutableListOf<Track>()
        val seenIds = mutableSetOf<String>()

        val contents = json.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i) ?: continue

            // 1. Check musicCardShelfRenderer (Top result)
            section.optJSONObject("musicCardShelfRenderer")?.let { card ->
                parseCardShelf(card)?.let { track ->
                    if (seenIds.add(track.id)) {
                        results.add(track)
                    }
                }
            }

            // 2. Check musicShelfRenderer (Section list)
            section.optJSONObject("musicShelfRenderer")?.optJSONArray("contents")?.let { items ->
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    item.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                        parseListItem(renderer)?.let { track ->
                            if (seenIds.add(track.id)) {
                                results.add(track)
                            }
                        }
                    }
                }
            }

            // 3. Check itemSectionRenderer
            section.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.let { items ->
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    item.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                        parseListItem(renderer)?.let { track ->
                            if (seenIds.add(track.id)) {
                                results.add(track)
                            }
                        }
                    }
                }
            }

            // 4. Direct musicResponsiveListItemRenderer
            section.optJSONObject("musicResponsiveListItemRenderer")?.let { renderer ->
                parseListItem(renderer)?.let { track ->
                    if (seenIds.add(track.id)) {
                        results.add(track)
                    }
                }
            }
        }

        return results
    }

    private fun parseCardShelf(card: JSONObject): Track? {
        val title = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: return null

        var videoId = card.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")

        if (videoId.isNullOrBlank()) {
            videoId = card.optJSONArray("buttons")?.optJSONObject(0)
                ?.optJSONObject("buttonRenderer")?.optJSONObject("command")
                ?.optJSONObject("watchEndpoint")?.optString("videoId")
        }
        if (videoId.isNullOrBlank()) return null

        val subtitleRuns = card.optJSONObject("subtitle")?.optJSONArray("runs")
        var cardDurationMs: Long? = null
        if (subtitleRuns != null) {
            for (i in 0 until subtitleRuns.length()) {
                val t = subtitleRuns.optJSONObject(i)?.optString("text")?.trim().orEmpty()
                if (t.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))) {
                    cardDurationMs = parseDurationStringToMs(t)
                    break
                }
            }
        }
        val artist = extractSubtitleText(subtitleRuns)

        val thumbUrl = extractThumbnail(card.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer"))

        return Track(
            id = videoId,
            title = cleanTitle(title),
            artist = artist.ifBlank { "YouTube Music" },
            album = "YouTube Music",
            artworkUrl = thumbUrl,
            durationMs = cardDurationMs,
            streamUri = null
        )
    }

    private fun parseListItem(renderer: JSONObject): Track? {
        var videoId = renderer.optJSONObject("playlistItemData")?.optString("videoId")

        if (videoId.isNullOrBlank()) {
            videoId = renderer.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")?.optString("videoId")
        }

        if (videoId.isNullOrBlank()) {
            videoId = renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")?.optString("videoId")
        }

        if (videoId.isNullOrBlank()) return null

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
        if (flexColumns.length() == 0) return null

        // Column 0: Title
        val col0Runs = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs")
        val title = col0Runs?.optJSONObject(0)?.optString("text") ?: return null

        // Column 1: Artist, Album, Duration
        var artist = ""
        var album: String? = null
        var durationMs: Long? = null

        if (flexColumns.length() > 1) {
            val col1Runs = flexColumns.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")

            if (col1Runs != null) {
                val parts = mutableListOf<String>()
                for (k in 0 until col1Runs.length()) {
                    val runText = col1Runs.optJSONObject(k)?.optString("text") ?: continue
                    val cleanRun = runText.trim()
                    if (cleanRun.isNotEmpty() && cleanRun != "•" && cleanRun != "Song") {
                        // Check if it's duration (e.g. "3:45" or "4:12")
                        if (cleanRun.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))) {
                            durationMs = parseDurationStringToMs(cleanRun)
                        } else {
                            parts.add(cleanRun)
                        }
                    }
                }
                if (parts.isNotEmpty()) {
                    artist = parts[0]
                    if (parts.size > 1) {
                        album = parts[1]
                    }
                }
            }
        }

        // Also check all subsequent flexColumns and fixedColumns for duration
        if (durationMs == null) {
            for (c in 1 until flexColumns.length()) {
                val runs = flexColumns.optJSONObject(c)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                for (k in 0 until runs.length()) {
                    val t = runs.optJSONObject(k)?.optString("text")?.trim().orEmpty()
                    if (t.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))) {
                        durationMs = parseDurationStringToMs(t)
                        break
                    }
                }
                if (durationMs != null) break
            }
        }

        if (durationMs == null) {
            val fixedCols = renderer.optJSONArray("fixedColumns")
            if (fixedCols != null) {
                for (c in 0 until fixedCols.length()) {
                    val runs = fixedCols.optJSONObject(c)
                        ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                        ?.optJSONObject("text")?.optJSONArray("runs") ?: continue
                    for (k in 0 until runs.length()) {
                        val t = runs.optJSONObject(k)?.optString("text")?.trim().orEmpty()
                        if (t.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))) {
                            durationMs = parseDurationStringToMs(t)
                            break
                        }
                    }
                    if (durationMs != null) break
                }
            }
        }

        val thumbUrl = extractThumbnail(renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer"))

        return Track(
            id = videoId,
            title = cleanTitle(title),
            artist = artist.ifBlank { "YouTube Music" },
            album = album ?: "YouTube Music",
            artworkUrl = thumbUrl,
            durationMs = durationMs,
            streamUri = null
        )
    }

    private fun extractThumbnail(musicThumbRenderer: JSONObject?): String? {
        val thumbs = musicThumbRenderer?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: return null
        if (thumbs.length() == 0) return null
        val rawUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: return null

        // Upgrade thumbnail resolution to 544x544 for stunning crisp album artwork in player & notifications
        return rawUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")
    }

    private fun extractSubtitleText(runs: JSONArray?): String {
        if (runs == null) return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            val t = runs.optJSONObject(i)?.optString("text") ?: continue
            val clean = t.trim()
            if (clean != "•" && clean != "Song") {
                if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                sb.append(clean)
            }
        }
        return sb.toString().trim()
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\s*\\[Official (?:Music )?Video\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Official (?:Music )?Video\\)", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun parseDurationStringToMs(durationStr: String): Long? {
        return try {
            val parts = durationStr.split(":")
            if (parts.size == 2) {
                val mins = parts[0].toLong()
                val secs = parts[1].toLong()
                (mins * 60 + secs) * 1000L
            } else if (parts.size == 3) {
                val hours = parts[0].toLong()
                val mins = parts[1].toLong()
                val secs = parts[2].toLong()
                (hours * 3600 + mins * 60 + secs) * 1000L
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val SEARCH_ENDPOINT = "https://music.youtube.com/youtubei/v1/search"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
