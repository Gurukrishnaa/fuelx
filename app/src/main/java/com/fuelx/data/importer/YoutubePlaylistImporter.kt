package com.fuelx.data.importer

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
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ImportedPlaylistResult(
    val name: String,
    val description: String?,
    val tracks: List<Track>
)

class YoutubePlaylistImporter(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    private val spotifyImporter by lazy { SpotifyPlaylistImporter(client) }

    /**
     * Extracts YouTube / YouTube Music playlist ID from URL or bare ID string.
     * Examples:
     * - https://music.youtube.com/playlist?list=PLDIoUOhQQPlXr63I_vwF9GD8sAKh77dWU
     * - https://www.youtube.com/playlist?list=PLDIoUOhQQPlXr63I_vwF9GD8sAKh77dWU
     * - PLDIoUOhQQPlXr63I_vwF9GD8sAKh77dWU
     */
    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val regex = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
        regex.find(trimmed)?.let {
            return it.groupValues[1]
        }
        // Direct bare playlist ID
        if (trimmed.matches(Regex("^[A-Za-z0-9_-]{10,}$"))) {
            return trimmed
        }
        return null
    }

    suspend fun importPlaylist(
        input: String,
        onProgress: (statusText: String) -> Unit = {}
    ): Result<ImportedPlaylistResult> = withContext(Dispatchers.IO) {
        if (SpotifyPlaylistImporter.isSpotifyLink(input)) {
            return@withContext spotifyImporter.importPlaylist(input, onProgress)
        }

        try {
            val playlistId = extractPlaylistId(input)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Please enter a valid YouTube, YouTube Music, or Spotify playlist link.")
                )

            onProgress("Connecting to YouTube Music...")
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val initialResponse = postBrowse(browseId)

            val playlistTitle = extractTitle(initialResponse) ?: "Imported Playlist"
            val description = extractDescription(initialResponse)

            val tracks = mutableListOf<Track>()
            val seenTrackIds = mutableSetOf<String>()

            var (itemsArray, continuationToken) = extractFirstPage(initialResponse)
            var pages = 0

            while (itemsArray != null && pages < 100) {
                val parsedPageTracks = parseItems(itemsArray)
                for (track in parsedPageTracks) {
                    if (seenTrackIds.add(track.id)) {
                        tracks.add(track)
                    }
                }
                onProgress("Found ${tracks.size} songs...")

                if (continuationToken.isNullOrBlank()) break

                val continuationResponse = postContinuation(continuationToken)
                val (nextItems, nextToken) = extractContinuationPage(continuationResponse)
                itemsArray = nextItems
                continuationToken = nextToken
                pages++
            }

            if (tracks.isEmpty()) {
                return@withContext Result.failure(IOException("No songs found. The playlist might be private, empty, or unavailable."))
            }

            Result.success(
                ImportedPlaylistResult(
                    name = playlistTitle,
                    description = description,
                    tracks = tracks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun postBrowse(browseId: String): JSONObject {
        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240401.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
            put("browseId", browseId)
        }

        val request = Request.Builder()
            .url(BROWSE_ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("YouTube Music returned HTTP ${response.code}")
            }
            val str = response.body?.string().orEmpty()
            return JSONObject(str)
        }
    }

    private fun postContinuation(token: String): JSONObject {
        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240401.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }

        val url = "$BROWSE_ENDPOINT?ctoken=$token&continuation=$token"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.youtube.com/")
            .header("Origin", "https://music.youtube.com")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("YouTube Music continuation returned HTTP ${response.code}")
            }
            val str = response.body?.string().orEmpty()
            return JSONObject(str)
        }
    }

    private fun extractFirstPage(root: JSONObject): Pair<JSONArray?, String?> {
        val shelf = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONObject("secondaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.optJSONObject(0)
            ?.optJSONObject("musicPlaylistShelfRenderer")
            ?: return null to null

        val items = shelf.optJSONArray("contents")
        val token = shelf.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?: extractLastItemContinuationToken(items)

        return items to token
    }

    private fun extractContinuationPage(root: JSONObject): Pair<JSONArray?, String?> {
        // Newer layout with appendContinuationItemsAction
        val actions = root.optJSONArray("onResponseReceivedActions")
        if (actions != null && actions.length() > 0) {
            val appendAction = actions.optJSONObject(0)?.optJSONObject("appendContinuationItemsAction")
            val items = appendAction?.optJSONArray("continuationItems")
            val token = extractLastItemContinuationToken(items)
            return items to token
        }

        // Older layout with continuationContents
        val shelf = root.optJSONObject("continuationContents")?.optJSONObject("musicPlaylistShelfContinuation")
        if (shelf != null) {
            val items = shelf.optJSONArray("contents")
            val token = shelf.optJSONArray("continuations")
                ?.optJSONObject(0)
                ?.optJSONObject("nextContinuationData")
                ?.optString("continuation")
                ?: extractLastItemContinuationToken(items)
            return items to token
        }

        return null to null
    }

    private fun extractLastItemContinuationToken(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        val last = items.optJSONObject(items.length() - 1) ?: return null
        return last.optJSONObject("continuationItemRenderer")
            ?.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
    }

    private fun extractTitle(root: JSONObject): String? {
        // Header title style 1 (musicDetailHeaderRenderer)
        val headerTitle = root.optJSONObject("header")
            ?.optJSONObject("musicDetailHeaderRenderer")
            ?.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
        if (!headerTitle.isNullOrBlank()) return headerTitle

        // Header title style 2 (musicResponsiveHeaderRenderer inside tabs)
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
        val tabTitle = tabs?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveHeaderRenderer")
            ?.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")

        return tabTitle?.takeIf { it.isNotBlank() }
    }

    private fun extractDescription(root: JSONObject): String? {
        val desc = root.optJSONObject("header")
            ?.optJSONObject("musicDetailHeaderRenderer")
            ?.optJSONObject("description")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
        return desc?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun parseItems(items: JSONArray): List<Track> {
        val tracks = mutableListOf<Track>()
        for (i in 0 until items.length()) {
            val itemObj = items.optJSONObject(i) ?: continue
            val r = itemObj.optJSONObject("musicResponsiveListItemRenderer") ?: continue

            val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")
            if (videoId.isNullOrBlank()) continue

            val flexCols = r.optJSONArray("flexColumns") ?: continue

            fun getColumnText(colIndex: Int): String? {
                val runs = flexCols.optJSONObject(colIndex)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: return null
                val sb = StringBuilder()
                for (k in 0 until runs.length()) {
                    val t = runs.optJSONObject(k)?.optString("text").orEmpty()
                    sb.append(t)
                }
                return sb.toString().trim().takeIf { it.isNotBlank() }
            }

            val title = getColumnText(0) ?: continue
            val artistLine = getColumnText(1) ?: "YouTube Music"
            val album = getColumnText(2)

            // Duration parsing
            var durationMs: Long? = null
            val fixedCols = r.optJSONArray("fixedColumns")
            val durText = fixedCols?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
            if (!durText.isNullOrBlank()) {
                durationMs = parseDurationStringToMs(durText.trim())
            }

            // Thumbnail parsing with high resolution upgrade
            val thumbs = r.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            val rawArtUrl = if (thumbs != null && thumbs.length() > 0) {
                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url")
            } else null
            val artworkUrl = rawArtUrl?.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")

            tracks.add(
                Track(
                    id = videoId,
                    title = cleanTitle(title),
                    artist = cleanArtistString(artistLine),
                    album = album ?: "YouTube Music",
                    artworkUrl = artworkUrl,
                    durationMs = durationMs,
                    streamUri = null
                )
            )
        }
        return tracks
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
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val BROWSE_ENDPOINT = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

