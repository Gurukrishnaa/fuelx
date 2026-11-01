package com.fuelx.data.importer

import com.fuelx.data.youtube.InnertubeClient
import com.fuelx.model.Track
import com.fuelx.util.cleanArtistString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class SpotifyPlaylistImporter(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
    private val innertubeClient: InnertubeClient = InnertubeClient()
) {

    data class RawSpotifyTrack(
        val title: String,
        val artist: String,
        val durationMs: Long?
    )

    companion object {
        private const val PATHFINDER_HASH = "7982b11e21535cd2594badc40030b745671b61a1fa66766e569d45e6364f3422"

        private val SPOTIFY_URL_REGEX = Regex(
            """(?:open\.spotify\.com/(?:[a-zA-Z-]+/)?(playlist|album)/|spotify:(playlist|album):)([A-Za-z0-9]+)"""
        )

        fun isSpotifyLink(input: String): Boolean {
            val trimmed = input.trim()
            return trimmed.contains("open.spotify.com") ||
                    trimmed.contains("spotify.link") ||
                    trimmed.contains("spoti.fi") ||
                    trimmed.startsWith("spotify:")
        }
    }

    /**
     * Resolves redirects (for spotify.link or spoti.fi short links) and extracts entity type and ID.
     */
    private fun extractSpotifyEntity(input: String): Pair<String, String>? {
        var trimmed = input.trim()

        // Handle short URLs by following redirect
        if (trimmed.contains("spotify.link") || trimmed.contains("spoti.fi")) {
            try {
                val req = Request.Builder()
                    .url(trimmed)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .head()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    trimmed = resp.request.url.toString()
                }
            } catch (e: Exception) {
                // Ignore and proceed with original trimmed string
            }
        }

        val match = SPOTIFY_URL_REGEX.find(trimmed) ?: return null
        val type = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val id = match.groupValues[3]
        return if (type.isNotBlank() && id.isNotBlank()) type to id else null
    }

    suspend fun importPlaylist(
        input: String,
        onProgress: (statusText: String) -> Unit = {}
    ): Result<ImportedPlaylistResult> = withContext(Dispatchers.IO) {
        try {
            val entity = extractSpotifyEntity(input)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Invalid Spotify link. Please paste a valid Spotify playlist or album URL.")
                )

            val (type, id) = entity
            onProgress("Connecting to Spotify...")

            val embedUrl = "https://open.spotify.com/embed/$type/$id"
            val request = Request.Builder()
                .url(embedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .build()

            val html = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Spotify returned HTTP ${response.code}. Please ensure the playlist is public.")
                }
                response.body?.string() ?: throw IOException("Empty response from Spotify.")
            }

            val nextDataMatch = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""").find(html)
                ?: throw IOException("Could not read Spotify playlist data. Make sure the playlist is public.")

            val json = JSONObject(nextDataMatch.groupValues[1])
            val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps")
            val state = pageProps?.optJSONObject("state")
            val entityData = state?.optJSONObject("data")?.optJSONObject("entity")
                ?: throw IOException("Playlist details not found on Spotify. Ensure the link is public.")

            val playlistTitle = entityData.optString("name").takeIf { it.isNotBlank() }
                ?: (if (type == "album") "Imported Spotify Album" else "Imported Spotify Playlist")

            val accessToken = state.optJSONObject("settings")
                ?.optJSONObject("session")
                ?.optString("accessToken")

            val rawTracks = mutableListOf<RawSpotifyTrack>()

            // If it's a playlist and we have an access token, fetch ALL tracks with pagination (supporting 400+ songs!)
            if (type == "playlist" && !accessToken.isNullOrBlank()) {
                val allPaginated = fetchAllPlaylistTracks(id, accessToken, onProgress)
                if (allPaginated.isNotEmpty()) {
                    rawTracks.addAll(allPaginated)
                }
            }

            // Fallback: If pagination returned nothing or it's an album, use the embed's trackList
            if (rawTracks.isEmpty()) {
                val trackListJson = entityData.optJSONArray("trackList")
                if (trackListJson != null) {
                    for (i in 0 until trackListJson.length()) {
                        val item = trackListJson.optJSONObject(i) ?: continue
                        val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                        val artist = item.optString("subtitle").takeIf { it.isNotBlank() } ?: ""
                        val durationMs = item.optLong("duration").takeIf { it > 0 }
                        rawTracks.add(RawSpotifyTrack(title = title, artist = artist, durationMs = durationMs))
                    }
                }
            }

            if (rawTracks.isEmpty()) {
                throw IOException("This Spotify $type has no playable tracks.")
            }

            val total = rawTracks.size
            onProgress("Found $total Spotify songs. Migrating...")

            // Migrate songs concurrently to YouTube Music using 6 workers for speed
            val semaphore = Semaphore(6)
            val completed = AtomicInteger(0)

            val matchedTracks: List<Track> = coroutineScope {
                val deferred = rawTracks.mapIndexed { index, rawTrack ->
                    async(Dispatchers.IO) {
                        val track = semaphore.withPermit {
                            matchTrack(rawTrack)
                        }
                        val done = completed.incrementAndGet()
                        onProgress("Migrating songs from Spotify: $done / $total")
                        index to track
                    }
                }
                deferred.awaitAll().sortedBy { it.first }.mapNotNull { it.second }
            }

            if (matchedTracks.isEmpty()) {
                throw IOException("Could not match any songs from this Spotify $type on YouTube Music.")
            }

            Result.success(
                ImportedPlaylistResult(
                    name = playlistTitle,
                    description = "Imported from Spotify ($total songs)",
                    tracks = matchedTracks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all tracks from Spotify's Pathfinder API using pagination.
     * Supports playlists with hundreds or thousands of songs (e.g. 400+).
     */
    private fun fetchAllPlaylistTracks(
        playlistId: String,
        accessToken: String,
        onProgress: (statusText: String) -> Unit
    ): List<RawSpotifyTrack> {
        val tracks = mutableListOf<RawSpotifyTrack>()
        val limit = 100
        var offset = 0
        var totalCount: Int? = null

        while (totalCount == null || offset < totalCount) {
            try {
                val vars = JSONObject().apply {
                    put("uri", "spotify:playlist:$playlistId")
                    put("offset", offset)
                    put("limit", limit)
                    put("enableWatchFeedEntrypoint", false)
                }
                val extensions = JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", PATHFINDER_HASH)
                    })
                }

                val queryUrl = "https://api-partner.spotify.com/pathfinder/v1/query?" +
                        "operationName=fetchPlaylist" +
                        "&variables=" + URLEncoder.encode(vars.toString(), "UTF-8") +
                        "&extensions=" + URLEncoder.encode(extensions.toString(), "UTF-8")

                val req = Request.Builder()
                    .url(queryUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    .header("Origin", "https://open.spotify.com")
                    .header("Referer", "https://open.spotify.com/")
                    .build()

                val respString = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                } ?: break

                val json = JSONObject(respString)
                val pl = json.optJSONObject("data")?.optJSONObject("playlistV2") ?: break
                val content = pl.optJSONObject("content") ?: break
                val total = content.optInt("totalCount", 0)
                if (total > 0) totalCount = total

                val items = content.optJSONArray("items") ?: break
                if (items.length() == 0) break

                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val trackData = it.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                    val name = trackData.optString("name").takeIf { it.isNotBlank() } ?: continue

                    val artistsArr = trackData.optJSONObject("artists")?.optJSONArray("items")
                    val artistNames = mutableListOf<String>()
                    if (artistsArr != null) {
                        for (a in 0 until artistsArr.length()) {
                            val profile = artistsArr.optJSONObject(a)?.optJSONObject("profile")
                            val artistName = profile?.optString("name")
                            if (!artistName.isNullOrBlank()) {
                                artistNames.add(artistName)
                            }
                        }
                    }
                    val artistStr = artistNames.joinToString(", ")
                    val durationMs = trackData.optJSONObject("trackDuration")?.optLong("totalMilliseconds")

                    tracks.add(RawSpotifyTrack(title = name, artist = artistStr, durationMs = durationMs))
                }

                val currentTotal = totalCount ?: tracks.size
                onProgress("Found ${tracks.size} of $currentTotal songs on Spotify...")
                offset += items.length()

                if (items.length() < limit || tracks.size >= currentTotal) {
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }

        return tracks
    }

    private suspend fun matchTrack(raw: RawSpotifyTrack): Track? {
        val cleanSongTitle = cleanTitle(raw.title)
        val cleanArtist = cleanArtistString(raw.artist)

        // Try primary query with artist and cleaned title
        val primaryQuery = if (cleanArtist.isNotBlank()) "$cleanArtist $cleanSongTitle" else cleanSongTitle
        var candidates = runCatching { innertubeClient.search(primaryQuery) }.getOrDefault(emptyList())

        // Fallback to searching title only if no candidates found
        if (candidates.isEmpty() && cleanSongTitle.isNotBlank()) {
            candidates = runCatching { innertubeClient.search(cleanSongTitle) }.getOrDefault(emptyList())
        }

        if (candidates.isEmpty()) return null

        // Score candidates based on title match, artist match, and duration closeness
        val scored = candidates.map { candidate ->
            candidate to scoreCandidate(raw, candidate)
        }.sortedByDescending { it.second }

        val bestCandidate = scored.firstOrNull()?.first ?: candidates.first()
        return bestCandidate
    }

    private fun scoreCandidate(raw: RawSpotifyTrack, cand: Track): Float {
        val srcClean = cleanTitle(raw.title)
        val candClean = cleanTitle(cand.title)
        val titleSim = tokenSimilarity(srcClean, candClean)

        val rawArtistNorm = normalize(raw.artist)
        val candArtistNorm = normalize(cand.artist)
        val artistSim = if (rawArtistNorm.isEmpty()) 0.5f else {
            if (candArtistNorm.contains(rawArtistNorm) || rawArtistNorm.contains(candArtistNorm)) {
                1.0f
            } else {
                val rawTokens = rawArtistNorm.split(" ").filter { it.isNotBlank() }
                if (rawTokens.any { candArtistNorm.contains(it) }) 0.8f else 0.2f
            }
        }

        val durSim = run {
            val a = raw.durationMs
            val b = cand.durationMs
            if (a == null || b == null || b == 0L) 0.5f
            else when (abs(a - b)) {
                in 0..4_000 -> 1.0f
                in 4_001..10_000 -> 0.7f
                in 10_001..20_000 -> 0.4f
                else -> 0.1f
            }
        }

        var score = titleSim * 0.55f + artistSim * 0.25f + durSim * 0.20f

        // Penalize live, cover, karaoke, remix versions if source didn't explicitly ask for it
        val srcLower = raw.title.lowercase()
        val candLower = cand.title.lowercase()
        val unwantedKeywords = listOf("live", "cover", "karaoke", "instrumental", "remix", "reverb", "slowed", "8d")
        for (kw in unwantedKeywords) {
            if (candLower.contains(kw) && !srcLower.contains(kw)) {
                score -= 0.18f
            }
        }

        return score.coerceIn(0f, 1f)
    }

    private fun cleanTitle(title: String): String {
        return normalize(
            title.substringBefore('(')
                .substringBefore('[')
                .substringBefore(" - ")
        )
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenSimilarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val setA = a.split(" ").filter { it.isNotBlank() }.toSet()
        val setB = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0f
        val intersection = setA.intersect(setB).size.toFloat()
        val union = setA.union(setB).size.toFloat()
        return if (union == 0f) 0f else intersection / union
    }
}
