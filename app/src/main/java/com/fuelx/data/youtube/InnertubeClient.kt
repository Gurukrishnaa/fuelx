package com.fuelx.data.youtube

import android.net.Uri
import com.fuelx.model.Track
import com.fuelx.util.cleanArtistString
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

    private val currentYear: Int
        get() = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    suspend fun getTrending(languages: Set<String> = emptySet()): List<Track> = withContext(Dispatchers.IO) {
        val year = currentYear
        if (languages.isEmpty()) {
            val hits = search("Latest Hit Songs $year").filter { isCleanSong(it) }
            return@withContext if (hits.isNotEmpty()) hits else search("Trending Songs $year").filter { isCleanSong(it) }
        }

        val allTracks = mutableListOf<Track>()
        val seenIds = mutableSetOf<String>()

        for (lang in languages) {
            val query = when (lang.lowercase()) {
                "tamil" -> "Latest Tamil Movie Hit Songs $year"
                "hindi" -> "Latest Bollywood Hit Songs $year"
                "malayalam" -> "Latest Malayalam Movie Songs $year"
                "english" -> "Top Billboard Hit Songs $year"
                else -> "Latest $lang Hit Songs $year"
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
            val fallback = search("Latest Hit Songs $year").filter { isCleanSong(it) }
            if (fallback.isNotEmpty()) fallback else search("Trending Songs $year").filter { isCleanSong(it) }
        }
    }

    suspend fun getGenreTracks(genreId: String, languages: Set<String> = emptySet()): List<Track> = withContext(Dispatchers.IO) {
        if (genreId.equals("all", ignoreCase = true)) {
            return@withContext getTrending(languages)
        }

        val year = currentYear
        val allTracks = mutableListOf<Track>()
        val seenIds = mutableSetOf<String>()
        val langs = if (languages.isEmpty()) setOf("Global") else languages

        for (lang in langs) {
            val l = lang.lowercase()
            val query = when (genreId.lowercase()) {
                "melody" -> when (l) {
                    "tamil" -> "Tamil Melody Hit Songs $year"
                    "hindi" -> "Bollywood Romantic Melody Songs $year"
                    "malayalam" -> "Malayalam Melody Songs $year"
                    "english" -> "Pop Acoustic Love Songs $year"
                    else -> "$lang Melody Romantic Songs $year"
                }
                "party" -> when (l) {
                    "tamil" -> "Tamil Kuthu Party Dance Songs $year"
                    "hindi" -> "Bollywood Dance Party Songs $year"
                    "malayalam" -> "Malayalam Dance Party Songs $year"
                    "english" -> "Party Dance Pop Club Songs $year"
                    else -> "$lang Dance Party Songs $year"
                }
                "hiphop" -> when (l) {
                    "tamil" -> "Tamil Hip Hop Rap Songs $year"
                    "hindi" -> "Desi Hip Hop Hindi Rap Songs $year"
                    "malayalam" -> "Malayalam Hip Hop Songs $year"
                    "english" -> "Hip Hop Rap Hits $year"
                    else -> "$lang Hip Hop Rap Songs $year"
                }
                "chill" -> when (l) {
                    "tamil" -> "Tamil Lo-Fi Acoustic Songs $year"
                    "hindi" -> "Hindi Lo-Fi Acoustic Songs $year"
                    "malayalam" -> "Malayalam Lo-Fi Acoustic Songs $year"
                    "english" -> "Lo-Fi Acoustic Chill Pop Songs $year"
                    else -> "$lang Acoustic Chill Songs $year"
                }
                "rock" -> when (l) {
                    "tamil" -> "Tamil Rock Fast Songs $year"
                    "hindi" -> "Hindi Rock Metal Songs $year"
                    "malayalam" -> "Malayalam Rock Songs $year"
                    "english" -> "Rock Hits Modern Songs $year"
                    else -> "$lang Rock Songs $year"
                }
                "folk" -> when (l) {
                    "tamil" -> "Tamil Folk Gaana Songs $year"
                    "hindi" -> "Hindi Indie Folk Songs $year"
                    "malayalam" -> "Malayalam Folk Indie Songs $year"
                    "english" -> "Indie Folk Acoustic Songs $year"
                    else -> "$lang Folk Indie Songs $year"
                }
                else -> "$lang $genreId Songs $year"
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
     * DJ mixes, full albums, 1-hour loops, or short video snippets.
     */
    fun isCleanSong(track: Track): Boolean {
        // 1. Duration check: standalone songs must not exceed 10 minutes (600,000 ms).
        val dur = track.durationMs
        if (dur != null) {
            if (dur > 10 * 60 * 1000L || dur < 45_000L) {
                return false
            }
        }

        val title = track.title.lowercase().trim()
        val artist = track.artist.lowercase().trim()
        val album = (track.album ?: "").lowercase().trim()

        // 2. Reject video category items or channels representing DJ mixes / compilations
        if (artist == "video" || artist.contains("dj mix") || artist.contains("music dj") || artist.contains("remix channel")) {
            return false
        }
        if (album.contains("dj mix") || album.contains("music dj")) {
            return false
        }

        // 3. Reject compilation and mix patterns in title
        val blockedPatterns = listOf(
            "jukebox",
            "audio jukebox",
            "video jukebox",
            "dj mix",
            "music mix",
            "non stop",
            "nonstop",
            "non-stop",
            "hit songs",
            "latest hit songs",
            "latest songs",
            "top songs",
            "all songs",
            "all hits",
            "all-time hits",
            "all time hits",
            "mega hits",
            "greatest hits",
            "superhit songs",
            "super hit songs",
            "best of",
            "best songs",
            "collection",
            "compilation",
            "mashup",
            "full album",
            "full songs",
            "audio songs",
            "video songs",
            "movie songs",
            "1 hour",
            "2 hour",
            "3 hour",
            "hours",
            "hour long",
            "playlist",
            "top 100",
            "top 50",
            "top 40",
            "top 30",
            "top 20",
            "top 10",
            "continuous",
            "uninterrupted"
        )

        for (pattern in blockedPatterns) {
            if (title.contains(pattern)) {
                return false
            }
        }

        // 4. Regex checks for year-based compilation titles (e.g. "Tamil Hits 2026", "Songs 2026", "2026 Hits")
        val compilationRegexes = listOf(
            Regex(".*\\b(?:tamil|hindi|telugu|malayalam|kannada|bollywood|punjabi|english)\\s+.*hits?\\s*\\d{4}.*", RegexOption.IGNORE_CASE),
            Regex(".*\\b(?:hits?|songs?)\\s+(?:2024|2025|2026|2027)\\b.*", RegexOption.IGNORE_CASE),
            Regex(".*\\b(?:2024|2025|2026|2027)\\s+(?:hits?|songs?|jukebox)\\b.*", RegexOption.IGNORE_CASE),
            Regex(".*\\b\\d+\\s+(?:super)?hit\\s+songs?\\b.*", RegexOption.IGNORE_CASE)
        )

        for (regex in compilationRegexes) {
            if (regex.matches(title)) {
                return false
            }
        }

        // 5. Reject compilation patterns in artist or album names
        if (artist.contains("jukebox") || artist.contains("compilation") || artist.contains("mashup")) {
            return false
        }
        if (album.contains("jukebox") || album.contains("compilation")) {
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
                val groups = mutableListOf<StringBuilder>()
                var currentGroup = StringBuilder()

                for (k in 0 until col1Runs.length()) {
                    val runText = col1Runs.optJSONObject(k)?.optString("text").orEmpty()
                    val trimmed = runText.trim()
                    if (trimmed == "•") {
                        if (currentGroup.isNotBlank()) {
                            groups.add(currentGroup)
                            currentGroup = StringBuilder()
                        }
                    } else if (trimmed.isNotEmpty() && trimmed != "Song") {
                        if (trimmed.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))) {
                            durationMs = parseDurationStringToMs(trimmed)
                        } else {
                            if (currentGroup.isNotEmpty() && !runText.startsWith(",") && !currentGroup.endsWith(" ")) {
                                currentGroup.append(" ")
                            }
                            currentGroup.append(runText)
                        }
                    }
                }
                if (currentGroup.isNotBlank()) {
                    groups.add(currentGroup)
                }

                if (groups.isNotEmpty()) {
                    artist = cleanArtistString(groups[0].toString())
                    if (groups.size > 1) {
                        val potentialAlbum = cleanArtistString(groups[1].toString())
                        if (potentialAlbum.isNotEmpty() && potentialAlbum != "•" && potentialAlbum != ",") {
                            album = potentialAlbum
                        }
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

        // Also check thumbnail overlays (where video badges store runtime like "1:01:33")
        if (durationMs == null) {
            val overlays = renderer.optJSONArray("thumbnailOverlays")
                ?: renderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")?.optJSONArray("thumbnailOverlays")
            if (overlays != null) {
                for (o in 0 until overlays.length()) {
                    val timeStatus = overlays.optJSONObject(o)?.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                    val timeRuns = timeStatus?.optJSONObject("text")?.optJSONArray("runs")
                    if (timeRuns != null) {
                        for (r in 0 until timeRuns.length()) {
                            val t = timeRuns.optJSONObject(r)?.optString("text")?.trim().orEmpty()
                            if (t.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))) {
                                durationMs = parseDurationStringToMs(t)
                                break
                            }
                        }
                    }
                    val simpleText = timeStatus?.optJSONObject("text")?.optString("simpleText")?.trim().orEmpty()
                    if (durationMs == null && simpleText.matches(Regex("\\d{1,2}:\\d{2}(?::\\d{2})?"))) {
                        durationMs = parseDurationStringToMs(simpleText)
                    }
                    if (durationMs != null) break
                }
            }
        }

        if (artist.equals("Video", ignoreCase = true) && !album.isNullOrBlank()) {
            artist = album
            album = "YouTube Music"
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
                if (sb.isNotEmpty() && !sb.endsWith(" ") && !clean.startsWith(",")) sb.append(" ")
                sb.append(clean)
            }
        }
        return cleanArtistString(sb.toString())
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

    suspend fun getRadio(seedVideoId: String): List<Track> = withContext(Dispatchers.IO) {
        if (seedVideoId.isBlank()) return@withContext emptyList()
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
                put("videoId", seedVideoId)
                put("playlistId", "RDAMVM$seedVideoId")
                put("isAudioOnly", true)
            }

            val request = Request.Builder()
                .url(NEXT_ENDPOINT)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.youtube.com/")
                .header("Origin", "https://music.youtube.com")
                .header("Content-Type", "application/json")
                .post(requestBodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseRadioResponse(JSONObject(body), seedVideoId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseRadioResponse(json: JSONObject, seedVideoId: String): List<Track> {
        val results = mutableListOf<Track>()
        val seenIds = mutableSetOf<String>()
        seenIds.add(seedVideoId) // skip the seed song itself

        val contents = json.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicQueueRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("playlistPanelRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until contents.length()) {
            val item = contents.optJSONObject(i) ?: continue
            val renderer = item.optJSONObject("playlistPanelVideoRenderer") ?: continue

            val videoId = renderer.optString("videoId")
            if (videoId.isNullOrBlank() || !seenIds.add(videoId)) continue

            val title = renderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            if (title.isNullOrBlank()) continue

            val artistRuns = renderer.optJSONObject("longBylineText")?.optJSONArray("runs")
                ?: renderer.optJSONObject("shortBylineText")?.optJSONArray("runs")
            val artist = artistRuns?.optJSONObject(0)?.optString("text")?.let { cleanArtistString(it) } ?: "YouTube Music"

            val lengthStr = renderer.optJSONObject("lengthText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: renderer.optJSONObject("lengthText")?.optString("simpleText")
            val durationMs = if (!lengthStr.isNullOrBlank()) parseDurationStringToMs(lengthStr.trim()) else null

            val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val rawUrl = if (thumbs != null && thumbs.length() > 0) {
                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
            } else null
            val thumbUrl = rawUrl?.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")

            results.add(
                Track(
                    id = videoId,
                    title = cleanTitle(title),
                    artist = artist,
                    album = "YouTube Music",
                    artworkUrl = thumbUrl,
                    durationMs = durationMs,
                    streamUri = null
                )
            )
        }

        return results
    }

    companion object {
        private const val SEARCH_ENDPOINT = "https://music.youtube.com/youtubei/v1/search"
        private const val NEXT_ENDPOINT = "https://music.youtube.com/youtubei/v1/next?prettyPrint=false"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
