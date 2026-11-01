# FuelX: Importing Playlists from Spotify, YouTube Music and Others

## 1. What "fully intact" can and can't mean

| Item | Can you keep it? |
|---|---|
| Playlist name and description | Yes |
| Every track, in the same order, duplicates included | Yes |
| Track title, artist, album, duration | Yes |
| Cover art | Use the first track's artwork (original covers aren't reliably available) |
| Collaborators, followers, "date added" | No, and your app has no use for them |
| Spotify podcasts / local files | Skip and list them in the report |

**The real problem is not reading a playlist, it is matching songs.**
- **YouTube / YT Music playlists** already contain YouTube video IDs, so they import with **100% accuracy** (no matching needed). This is the best path.
- **Spotify playlists** contain Spotify track IDs. Your app plays from YouTube, so every Spotify track has to be searched on YouTube Music and matched by title, artist and duration. Expect about 90 to 97% correct matches. Some tracks will pick a live version or a cover, and a few won't match. The importer must **show a report** and let the user fix the misses.

---

## 2. What each source allows today (checked against Spotify's current docs)

| Source | Method | Works for | Notes |
|---|---|---|---|
| **YouTube / YT Music** | Paste a playlist link, read it via InnerTube `browse` | Public and unlisted playlists | No login needed. Private playlists need auth, skip for now. |
| **Spotify** | Import a **file** (account data export JSON, or a CSV from an export tool) | Everyone | No API limits. Recommended. |
| **Spotify** | Spotify Web API login | Mainly you | Development mode needs a Premium account for the app owner, allows 5 users, and only returns playlist tracks for playlists the user **owns or collaborates on** (not ones they merely follow). Extended access is organisations-only. Not suitable for a public app. |
| **Other apps** (Apple Music, Deezer, Amazon...) | CSV/JSON export, or move them to YouTube Music with a transfer service first | Everyone | Use the same file importer as Spotify. |

**Easy extra path with no code:** the user moves a Spotify playlist into their YouTube Music account using a transfer service (TuneMyMusic, Soundiiz and similar), then pastes the YouTube Music link into FuelX. Those are third-party services, so mention them in the app's help text instead of building on them.

### Build order
1. Data models and the saving step (section 3, 6)
2. YouTube / YT Music link import (section 4)
3. File import for Spotify and others, with the matcher (section 5)
4. The UI and the review screen (section 7)
5. Optional: Spotify login (section 8)

---

## 3. Models and flow

```
source (link / file)  ->  ImportedPlaylist  ->  Matcher (only if no videoId)  ->  Review  ->  Save to Room
```

```kotlin
package com.fuelx.data.importer

data class ImportedTrack(
    val title: String,
    val artists: List<String>,
    val album: String? = null,
    val durationMs: Long? = null,
    val videoId: String? = null,      // set when the source is already YouTube
    val artworkUrl: String? = null
)

data class ImportedPlaylist(
    val name: String,
    val description: String?,
    val tracks: List<ImportedTrack>,   // order and duplicates preserved
    val source: String                 // "YouTube Music", "Spotify", "CSV"
)

sealed interface MatchStatus {
    data class Matched(val track: com.fuelx.model.Track, val score: Float) : MatchStatus
    data class NeedsReview(val candidates: List<com.fuelx.model.Track>) : MatchStatus
    data object NotFound : MatchStatus
}

data class MatchedItem(
    val index: Int,                    // original position in the source playlist
    val source: ImportedTrack,
    val status: MatchStatus
)

// Adapt this to your real Track constructor.
fun ImportedTrack.toTrack(): com.fuelx.model.Track = com.fuelx.model.Track(
    id = requireNotNull(videoId),
    title = title,
    artist = artists.joinToString(", "),
    album = album,
    durationMs = durationMs
    // add artwork fields as your model needs
)
```

---

## 4. YouTube / YT Music link import (100% accurate)

Works with links like:
- `https://music.youtube.com/playlist?list=PLxxxx`
- `https://www.youtube.com/playlist?list=PLxxxx`
- a pasted bare playlist ID

```kotlin
package com.fuelx.data.importer

import com.fuelx.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class YoutubePlaylistImporter(
    private val client: OkHttpClient = OkHttpClient()
) {
    fun extractPlaylistId(input: String): String? {
        Regex("""[?&]list=([A-Za-z0-9_-]+)""").find(input)?.let { return it.groupValues[1] }
        return input.trim().takeIf { Regex("^[A-Za-z0-9_-]{12,}$").matches(it) }
    }

    suspend fun import(
        input: String,
        onProgress: (Int) -> Unit = {}
    ): ImportedPlaylist = withContext(Dispatchers.IO) {
        val id = extractPlaylistId(input) ?: throw IOException("That doesn't look like a playlist link")

        val first = post("browse", JSONObject().put("browseId", "VL$id"))
        val title = headerTitle(first) ?: "Imported playlist"
        val description = headerDescription(first)

        val tracks = ArrayList<ImportedTrack>()
        var (items, token) = firstPage(first)
        var pages = 0

        while (items != null && pages < 100) {            // 100 pages = far above any real playlist
            tracks += parseItems(items)
            onProgress(tracks.size)
            if (token == null) break
            val next = post("browse?ctoken=$token&continuation=$token", JSONObject())
            val page = continuationPage(next)
            items = page.first
            token = page.second
            pages++
        }

        if (tracks.isEmpty()) {
            throw IOException("No songs found. The playlist may be private or empty.")
        }
        ImportedPlaylist(title, description, tracks, "YouTube Music")
    }

    // ---------- networking ----------

    private fun post(path: String, extra: JSONObject): JSONObject {
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", "1.20240401.01.00")
                .put("hl", "en").put("gl", "US")))
        extra.keys().forEach { body.put(it, extra.get(it)) }

        val sep = if (path.contains("?")) "&" else "?"
        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/$path${sep}prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("YouTube returned ${r.code}")
            return JSONObject(r.body!!.string())
        }
    }

    // ---------- JSON walking helpers ----------

    private fun JSONObject.at(vararg path: Any): Any? {
        var cur: Any? = this
        for (p in path) {
            cur = when {
                p is String && cur is JSONObject -> cur.opt(p)
                p is Int && cur is JSONArray -> cur.opt(p)
                else -> return null
            }
            if (cur == null || cur == JSONObject.NULL) return null
        }
        return cur
    }

    // ---------- page extraction (verify these paths against a real response) ----------

    private fun firstPage(root: JSONObject): Pair<JSONArray?, String?> {
        val shelf = root.at(
            "contents", "twoColumnBrowseResultsRenderer", "secondaryContents",
            "sectionListRenderer", "contents", 0, "musicPlaylistShelfRenderer"
        ) as? JSONObject ?: return null to null
        val items = shelf.optJSONArray("contents")
        val token = shelf.at("continuations", 0, "nextContinuationData", "continuation") as? String
            ?: lastItemToken(items)
        return items to token
    }

    private fun continuationPage(root: JSONObject): Pair<JSONArray?, String?> {
        // Older layout
        (root.at("continuationContents", "musicPlaylistShelfContinuation") as? JSONObject)?.let { shelf ->
            val items = shelf.optJSONArray("contents")
            val token = shelf.at("continuations", 0, "nextContinuationData", "continuation") as? String
                ?: lastItemToken(items)
            return items to token
        }
        // Newer layout
        val items = root.at(
            "onResponseReceivedActions", 0, "appendContinuationItemsAction", "continuationItems"
        ) as? JSONArray
        return items to lastItemToken(items)
    }

    private fun lastItemToken(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        val last = items.optJSONObject(items.length() - 1) ?: return null
        return last.at("continuationItemRenderer", "continuationEndpoint", "continuationCommand", "token") as? String
    }

    private fun headerTitle(root: JSONObject): String? =
        (root.at("header", "musicDetailHeaderRenderer", "title", "runs", 0, "text") as? String)
            ?: (root.at("contents", "twoColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
                "sectionListRenderer", "contents", 0, "musicResponsiveHeaderRenderer", "title", "runs", 0, "text") as? String)

    private fun headerDescription(root: JSONObject): String? =
        (root.at("header", "musicDetailHeaderRenderer", "description", "runs", 0, "text") as? String)

    // ---------- track parsing ----------

    private fun parseItems(items: JSONArray): List<ImportedTrack> {
        val out = ArrayList<ImportedTrack>(items.length())
        for (i in 0 until items.length()) {
            val r = items.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
            // Greyed-out / unavailable songs have no videoId. They are skipped here.
            val videoId = r.at("playlistItemData", "videoId") as? String ?: continue
            val cols = r.optJSONArray("flexColumns") ?: continue

            fun col(i: Int): String? {
                val runs = cols.optJSONObject(i)
                    ?.at("musicResponsiveListItemFlexColumnRenderer", "text", "runs") as? JSONArray
                    ?: return null
                return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
                    .takeIf { it.isNotBlank() }
            }

            val title = col(0) ?: continue
            val artistLine = col(1).orEmpty()
            val album = col(2)
            val duration = (r.at("fixedColumns", 0, "musicResponsiveListItemFixedColumnRenderer",
                "text", "runs", 0, "text") as? String)?.let(::parseDuration)

            val thumbs = r.at("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") as? JSONArray
            val art = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
                ?.replace(Regex("=w\\d+-h\\d+.*"), "=w544-h544-l90-rj")

            out += ImportedTrack(
                title = title,
                artists = artistLine.split(Regex("""\s*(,|&|•)\s*""")).filter { it.isNotBlank() },
                album = album,
                durationMs = duration,
                videoId = videoId,
                artworkUrl = art
            )
        }
        return out
    }

    private fun parseDuration(text: String): Long? {
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
    }
}
```

Notes:
- The JSON paths above come from YouTube's internal responses. **Print one real response and confirm them**, because they change from time to time. Your existing search parser already walks `musicResponsiveListItemRenderer`, so you can reuse its helpers.
- Songs that YouTube shows as unavailable have no `videoId`. They are dropped; count them for the report (`rendered rows - parsed rows`).
- You can also use NewPipeExtractor's playlist extractor if you moved to Fix B in the earlier guide. It does the paging for you.

---

## 5. File import (Spotify and everyone else)

### 5.1 How the user gets the file
- **Spotify account data export:** Spotify account page, Privacy, "Download your data" (the account data package). The package includes a playlist file (named like `Playlist1.json`) with playlist names, tracks (name, artist, album, URI) and descriptions. It can take days to arrive. **Open your own export and confirm the key names before relying on the parser below.**
- **CSV from an export tool** (for example Exportify, or the export function of other apps). CSV works for Spotify, Apple Music, Deezer and others.

### 5.2 Parsers

```kotlin
package com.fuelx.data.importer

import org.json.JSONArray
import org.json.JSONObject

object FilePlaylistParser {

    /** Spotify account data export: {"playlists":[{"name":..,"description":..,"items":[{"track":{...}}]}]} */
    fun parseSpotifyJson(text: String): List<ImportedPlaylist> {
        val root = JSONObject(text)
        val playlists = root.optJSONArray("playlists") ?: return emptyList()
        val out = ArrayList<ImportedPlaylist>()
        for (i in 0 until playlists.length()) {
            val p = playlists.optJSONObject(i) ?: continue
            val items = p.optJSONArray("items") ?: JSONArray()
            val tracks = ArrayList<ImportedTrack>()
            for (j in 0 until items.length()) {
                val t = items.optJSONObject(j)?.optJSONObject("track") ?: continue   // skips episodes/local files
                val title = t.optString("trackName").takeIf { it.isNotBlank() } ?: continue
                tracks += ImportedTrack(
                    title = title,
                    artists = splitArtists(t.optString("artistName")),
                    album = t.optString("albumName").takeIf { it.isNotBlank() }
                )
            }
            if (tracks.isNotEmpty()) {
                out += ImportedPlaylist(
                    name = p.optString("name", "Imported playlist"),
                    description = p.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    tracks = tracks,
                    source = "Spotify"
                )
            }
        }
        return out
    }

    /** Generic CSV with a header row. Finds columns by name, so it works across exporters. */
    fun parseCsv(text: String, playlistName: String): ImportedPlaylist {
        val rows = readCsv(text)
        if (rows.size < 2) return ImportedPlaylist(playlistName, null, emptyList(), "CSV")

        val header = rows[0].map { it.trim().lowercase() }
        fun idx(vararg names: String) =
            header.indexOfFirst { h -> names.any { h.contains(it) } }

        val iTitle = idx("track name", "title", "song")
        val iArtist = idx("artist")
        val iAlbum = idx("album")
        val iDur = idx("duration")
        require(iTitle >= 0) { "Couldn't find a track name column" }

        val tracks = rows.drop(1).mapNotNull { r ->
            val title = r.getOrNull(iTitle)?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            ImportedTrack(
                title = title,
                artists = if (iArtist >= 0) splitArtists(r.getOrNull(iArtist).orEmpty()) else emptyList(),
                album = if (iAlbum >= 0) r.getOrNull(iAlbum)?.takeIf { it.isNotBlank() } else null,
                durationMs = if (iDur >= 0) r.getOrNull(iDur)?.trim()?.toLongOrNull() else null  // ms in most exports
            )
        }
        return ImportedPlaylist(playlistName, null, tracks, "CSV")
    }

    private fun splitArtists(raw: String) =
        raw.split(Regex("""\s*(,|;|&|\bfeat\.?\b|\bft\.?\b)\s*""", RegexOption.IGNORE_CASE))
            .filter { it.isNotBlank() }

    // Minimal RFC 4180 CSV reader: quotes, escaped quotes, commas and newlines inside quotes.
    private fun readCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(cell.toString()); cell.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    if (row.any { it.isNotEmpty() }) rows.add(row)
                    row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }
}
```

Reading the file: use the Storage Access Framework (no permission needed).

```kotlin
val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let { viewModel.importFile(it) }
}
// launch with: picker.launch(arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain"))

// ViewModel
val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
```

### 5.3 The matcher (turns Spotify-style tracks into playable YouTube tracks)

```kotlin
package com.fuelx.data.importer

import com.fuelx.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** Plug in your existing InnerTube song search here. */
fun interface SongSearch {
    suspend fun search(query: String): List<Track>
}

class TrackMatcher(
    private val search: SongSearch,
    private val parallelism: Int = 4
) {
    suspend fun matchAll(
        playlist: ImportedPlaylist,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<MatchedItem> = coroutineScope {
        val gate = Semaphore(parallelism)            // be polite to YouTube, avoid rate limits
        val done = AtomicInteger(0)
        val total = playlist.tracks.size

        playlist.tracks.mapIndexed { index, src ->
            async(Dispatchers.IO) {
                val status = gate.withPermit { matchOne(src) }
                onProgress(done.incrementAndGet(), total)
                MatchedItem(index, src, status)
            }
        }.awaitAll()                                  // awaitAll keeps the original order
    }

    private suspend fun matchOne(src: ImportedTrack): MatchStatus {
        // Already a YouTube track: nothing to match.
        if (src.videoId != null) return MatchStatus.Matched(src.toTrack(), 1f)

        val query = "${src.artists.firstOrNull().orEmpty()} ${cleanTitle(src.title)}".trim()
        val results = runCatching { search.search(query) }.getOrDefault(emptyList()).take(8)
        if (results.isEmpty()) return MatchStatus.NotFound

        val scored = results.map { it to score(src, it) }.sortedByDescending { it.second }
        val (best, bestScore) = scored.first()

        return when {
            bestScore >= 0.78f -> MatchStatus.Matched(best, bestScore)
            bestScore >= 0.45f -> MatchStatus.NeedsReview(scored.take(3).map { it.first })
            else -> MatchStatus.NotFound
        }
    }

    // ---- scoring: title 55%, artist 25%, duration 20% ----
    private fun score(src: ImportedTrack, cand: Track): Float {
        val title = tokenSimilarity(cleanTitle(src.title), cleanTitle(cand.title))
        val artist = if (src.artists.isEmpty()) 0.5f else {
            val candArtists = normalize(cand.artist)
            src.artists.count { normalize(it) in candArtists || candArtists.contains(normalize(it)) }
                .toFloat() / src.artists.size
        }
        val dur = run {
            val a = src.durationMs
            val b = cand.durationMs
            if (a == null || b == null) 0.5f
            else when (abs(a - b)) {
                in 0..3_000 -> 1f
                in 3_001..8_000 -> 0.6f
                else -> 0f
            }
        }
        var s = title * 0.55f + artist * 0.25f + dur * 0.20f

        // Penalise live / cover / karaoke versions the source didn't ask for.
        val srcLower = src.title.lowercase()
        val candLower = cand.title.lowercase()
        for (word in listOf("live", "cover", "karaoke", "instrumental", "remix", "reverb", "slowed", "8d")) {
            if (candLower.contains(word) && !srcLower.contains(word)) s -= 0.15f
        }
        return s.coerceIn(0f, 1f)
    }

    private fun cleanTitle(t: String) = normalize(
        t.substringBefore('(').substringBefore('[').substringBefore(" - ")
    )

    private fun normalize(s: String) =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun tokenSimilarity(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val sa = a.split(' ').toSet()
        val sb = b.split(' ').toSet()
        return sa.intersect(sb).size.toFloat() / sa.union(sb).size
    }
}
```

Tuning:
- 78% auto-accept, 45 to 78% goes to review, below 45% is "not found". Adjust after testing with a real playlist of about 100 songs.
- If you can, search with the **songs filter** in your InnerTube search call so results are official audio tracks and not music videos. That alone improves accuracy a lot.
- Tamil, Hindi and other non-English titles: keep the `\p{L}` letter class (already used), so non-Latin text isn't stripped.

---

## 6. Saving into your playlist feature

Wire this to your Room DAO. The only rules that matter for "intact":
- Insert tracks **in the original order** (a `position` column, or insert in order with an auto-increment).
- **Keep duplicates** if your schema allows it.
- Create the playlist and its tracks in **one transaction**, so a crash can't leave half a playlist.

```kotlin
interface PlaylistStore {
    suspend fun createWithTracks(name: String, description: String?, tracks: List<Track>): Long
}

suspend fun savePlaylist(
    store: PlaylistStore,
    playlist: ImportedPlaylist,
    items: List<MatchedItem>,          // includes any manual fixes made in the review screen
    includeUnreviewed: Boolean = false
): Long {
    val tracks = items.sortedBy { it.index }.mapNotNull { item ->
        when (val s = item.status) {
            is MatchStatus.Matched -> s.track
            is MatchStatus.NeedsReview -> if (includeUnreviewed) s.candidates.first() else null
            MatchStatus.NotFound -> null
        }
    }
    return store.createWithTracks(playlist.name, playlist.description, tracks)
}
```

If the name already exists, append " (2)" or ask the user. Don't silently merge.

---

## 7. UI flow

**Entry:** Library, "+ Import playlist", opens a bottom sheet with three rows:
1. **Paste a link** (YouTube / YouTube Music)
2. **Import a file** (Spotify JSON or CSV)
3. **How to move from Spotify** (short help: the two ways above)

**Progress screen** (YouTube link: "Reading playlist... 120 songs"; file: "Matching 37 of 150"):
- Show progress as a count, not a spinner on a blank screen.
- Allow cancel (cancel the coroutine job).

**Report screen** (this is what makes the import feel trustworthy):
```
Imported "Late night drives"
142 added automatically
 6 need a quick check   [Review]
 2 not found            [See list]
[ Save playlist ]
```
- **Review** shows each uncertain song with its top 3 YouTube candidates. Each candidate row shows title, artist and duration, and a Play preview button so the user can listen. Choices: pick one, or skip.
- **Not found** lists the original title and artist, so the user can search for them manually later.
- Saving is always possible. Never block the user on the review step.

Use your flat design tokens and the tonal surfaces for this UI (Surface1 rows, Volt accent for the primary button).

---

## 8. Optional: Spotify login (only for your own use)

The Spotify Web API route is possible, but not recommended for a public app.
- Development mode requires the app owner to have **Premium** and allows only **5 authorized users**. Extended access is only for organisations.
- Reading playlist tracks (`GET /playlists/{id}/items`) works only for playlists the user **owns or collaborates on**. Playlists they only follow return no tracks.
- Refresh tokens now expire after about six months, and endpoint rules have changed several times in 2026.
- Use OAuth with PKCE (no client secret in the app). Scopes: `playlist-read-private` and `playlist-read-collaborative`. Check the redirect URI rules in the Spotify dashboard before choosing your callback.
- Output is `ImportedPlaylist` with `durationMs` filled in, which improves matching. Everything after that (matcher, review, save) is the same.

If you want it for yourself only, it's a nice extra later. For everyone else, the file import covers it.

**YouTube Data API v3** (`playlistItems.list`) with Google sign-in can read the user's *own private* playlists. It needs a Google Cloud project and an OAuth consent screen, and apps in testing mode have user limits. Only build it if private-playlist import matters.

---

## 9. Edge cases checklist

- [ ] Playlist with 1,000+ songs (paging loop, progress, cancel)
- [ ] Unavailable / deleted songs (no `videoId`): counted in the report
- [ ] Playlists with the same song twice: kept twice
- [ ] Non-English titles (Tamil, Hindi): letters not stripped by normalization
- [ ] "Song (From the movie X)" vs "Song": `cleanTitle` removes the bracket part
- [ ] Spotify podcasts / local files: skipped, mentioned in the report
- [ ] Rate limiting: parallelism of 4, retry once on failure, show "paused, retrying"
- [ ] App closed during import: don't save partial playlists (single transaction at the end)
- [ ] Network lost mid-import: clear message, offer retry
- [ ] Duplicate playlist name: ask or append " (2)"
- [ ] Very large exports with many playlists: let the user choose which ones to import
- [ ] Test with one 20-song, one 100-song and one non-English playlist before shipping

## 10. Legal note
Importing a user's own playlist data from files they exported is fine. Reading YouTube through InnerTube and playing it outside YouTube's apps still breaks YouTube's Terms of Service, as discussed earlier, so keep this as a personal project.
