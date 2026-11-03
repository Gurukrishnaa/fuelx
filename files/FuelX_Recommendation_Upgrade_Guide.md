# FuelX: Clean Songs and a Better Recommendation System

## 1. What your screenshot shows

| What you see | Why it happens |
|---|---|
| Rows with artist **"Episode"** and album **"1d ago"** ("Did Sisqo really destr...") | These are podcast episodes. YouTube Music marks them with a type label ("Episode") in the same text slot where the artist normally is, so your parser reads "Episode" as the artist. |
| **"2026 Tamil Hits Songs..." by "Saravana Djoko"** | A compilation uploaded by a channel, not a real artist. My earlier filter missed it because it looked for the phrase "hit songs", but the title says "Hits Songs". |
| **"Ed Sheeran • YouTube Mu..."**, "Whitney Houston • YouTu..." on every row | Your subtitle shows the *album* field, and it contains "YouTube Music" (probably a source label). The real album, or the duration, should show there. |
| Remixes in lists | The first version of the filter allowed them on purpose. They are now blocked. |

The root cause is that the app **only filters by title text**. YouTube Music already tells you what each item is, so use that information first and text rules second.

Three layers fix it:
1. **Parse the item type** (song vs video vs episode vs user upload).
2. **Filter in one place** (`SongFilter`), at the repository, so Home, genres, search and radio are all clean.
3. **A better recommender** that learns from what you actually finish and skip.

---

## 2. Step 1: Add two fields to `Track`

```kotlin
data class Track(
    // ...your existing fields...
    val videoType: String? = null,   // "ATV", "OMV", "UGC", "PODCAST_EPISODE", "OFFICIAL_SOURCE_MUSIC"
    val typeLabel: String? = null    // "Song", "Video", "Episode", "Podcast"
)
```
Both default to `null`, so nothing else breaks.

What the video types mean:
- **ATV** official audio track (what you want)
- **OMV** official music video (fine)
- **OFFICIAL_SOURCE_MUSIC** label-supplied track (fine)
- **UGC** user upload: compilations, lyric videos, mixes (blocked)
- **PODCAST_EPISODE** (blocked)

---

## 3. Step 2: Patch your InnerTube parser

In the function that turns a `musicResponsiveListItemRenderer` into a `Track`, add:

```kotlin
// (a) video type: the strongest signal
val videoType = (
    item.at("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
        "playNavigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs",
        "watchEndpointMusicConfig", "musicVideoType") as? String
    )?.removePrefix("MUSIC_VIDEO_TYPE_")

// (b) type label: the first run of the second column is "Song" / "Video" / "Episode" ... when shown
val typeWords = setOf("Song", "Video", "Episode", "Podcast", "Playlist", "Album", "Single", "EP", "Artist", "Profile")
val runs = (cols.optJSONObject(1)
    ?.at("musicResponsiveListItemFlexColumnRenderer", "text", "runs") as? JSONArray)
val firstRun = runs?.optJSONObject(0)?.optString("text")
val typeLabel = firstRun?.takeIf { it in typeWords }
```

Then, when you build the artist string from the runs, **skip the type label and the " • " separator after it**. That also fixes the "artist = Episode" bug:

```kotlin
val artistRuns = (0 until (runs?.length() ?: 0))
    .map { runs!!.optJSONObject(it)?.optString("text").orEmpty() }
    .dropWhile { it in typeWords || it.trim() == "•" }
```
Then take the artist from `artistRuns` as you do now, and pass `videoType` and `typeLabel` into `Track(...)`.

Notes:
- The JSON paths come from YouTube's internal responses. **Print one real item and confirm them.** If `videoType` comes back `null` for everything, search the item's JSON for the text `musicVideoType` and use that path.
- If `videoType` stays `null`, nothing breaks. `SongFilter` falls back to its text rules.
- Fix the subtitle bug: where the album is read, ignore the source label:
  ```kotlin
  val album = rawAlbum?.takeUnless { it.equals("YouTube Music", ignoreCase = true) }
  ```
  and in `TrackListItem` show `artist • album` only when the album is real, otherwise `artist • 3:42`.

### Optional: ask YouTube for songs only
In your search request, add the songs filter to the body:
```kotlin
body.put("params", "EgWKAQIIAWoMEA4QChADEAQQCRAF")   // songs filter value used by ytmusicapi
```
That removes most episodes and uploads before they reach your app. **Treat this value as something to verify:** if results come back empty or odd, remove it. The client-side `SongFilter` protects you either way.

---

## 4. Step 3: Add `SongFilter.kt` and apply it in the repository

1. Copy `SongFilter.kt` into `com.fuelx.data.filter`.
2. Apply it where tracks leave the repository, so every screen benefits:

```kotlin
// DefaultMusicRepository
override suspend fun getHomeTracks(languages: Set<String>): Result<List<Track>> =
    runCatching { innertube.fetchHome(languages) }
        .map { list -> list.filter(SongFilter::isRealSong).distinctBy(SongFilter::songKey) }

override suspend fun getGenreTracks(genreId: String, languages: Set<String>): Result<List<Track>> =
    runCatching { innertube.fetchGenre(genreId, languages) }
        .map { list -> list.filter(SongFilter::isRealSong).distinctBy(SongFilter::songKey) }
```
Do the same for search results if you want Search clean too. Add a "Show videos and remixes" switch in Settings only if you want to give users the choice.

What the filter blocks:
- podcast episodes and anything with type label Episode/Podcast
- user uploads (`UGC`) and non-music types
- artist fields like "Episode", "Video", "YouTube Music", and albums like "1d ago"
- under 60 seconds or over 9 minutes
- compilations: jukebox, non-stop, best of, top 10, "2026 Tamil Hits Songs", "Songs 2025", playlists, reactions, reviews, trailers, ringtones, status videos
- remix, slowed, reverb, sped up, nightcore, 8D, bass boosted, mashup, DJ
- live, cover, instrumental, acoustic, unplugged, karaoke, "mix" inside `( )`, `[ ]` or after " - "
- titles with " | " from non-official uploads

If it blocks something you want (for example acoustic versions), remove that word from `AMBIGUOUS_VERSION`.

---

## 5. Step 4: Record what the user actually listens to

The old recommender only knew *what was played*. A better one needs to know *what was finished and what was skipped*.

### 5.1 Room table
```kotlin
@Entity(tableName = "listen_events")
data class ListenEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val artist: String,
    val playedMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val atEpochMs: Long
)

@Dao
interface ListenEventDao {
    @Insert suspend fun insert(e: ListenEventEntity)

    @Query("SELECT * FROM listen_events WHERE atEpochMs > :since ORDER BY atEpochMs DESC LIMIT 500")
    fun since(since: Long): kotlinx.coroutines.flow.Flow<List<ListenEventEntity>>

    @Query("DELETE FROM listen_events WHERE atEpochMs < :before")
    suspend fun prune(before: Long)
}
```
Add the entity to your database, **bump the Room version**, and add a migration (or `fallbackToDestructiveMigration()` while developing). Prune rows older than 90 days at app start.

### 5.2 Record from the player
In `PlayerController`, add this to your `Player.Listener`:

```kotlin
override fun onPositionDiscontinuity(
    oldPosition: Player.PositionInfo,
    newPosition: Player.PositionInfo,
    reason: Int
) {
    val item = oldPosition.mediaItem ?: return
    if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) return   // a seek inside the same song

    val completed = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
    scope.launch(Dispatchers.IO) {
        listenDao.insert(
            ListenEventEntity(
                trackId = item.mediaId,
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                playedMs = oldPosition.positionMs,
                durationMs = lastKnownDurationMs.takeIf { it > 0 },   // save player.duration when STATE_READY
                completed = completed,
                atEpochMs = System.currentTimeMillis()
            )
        )
    }
}
```
Also record when the user stops or closes the app on a track (`onIsPlayingChanged` or in your release/stop path), or you'll miss the last song of each session.

How the scoring reads these rows:

| Event | Weight |
|---|---|
| Finished (or at least 85% played) | +1.0 |
| Played at least half | +0.6 |
| Skipped in under 30 seconds | -1.0 (counts against that artist) |
| Anything in between | +0.1 |
| Age | halves every 14 days |
| Favorite | +1.5 extra |

---

## 6. Step 5: Give the recommender better ingredients

The old engine could only reshuffle the one list you gave it. The new one mixes **three pools**, each cleaned with `SongFilter`:

| Pool | Source | What it adds |
|---|---|---|
| `chart` | your existing home/trending fetch | what's popular now |
| `genre` | your existing `getGenreTracks(...)` for the selected genre and language | matches what the user picked |
| `radio` | InnerTube `next` with `playlistId = "RDAMVM<videoId>"` (see `FuelX_Playlist_Import_Guide.md` section 8 notes, or the earlier suggestion guide) | **YouTube's own "similar to this song"**, the biggest quality jump |

Add `getRadio(videoId)` to your repository if you haven't yet. Call it with the **last played song**.

Optional extras later:
- **Charts by country:** InnerTube `browse` with `browseId = "FEmusic_charts"` and `formData = {"selectedValues": ["IN"]}` for a cleaner "Trending in India".
- **Mood/genre pages:** `browse` on `FEmusic_moods_and_genres` for curated shelves.

Verify the JSON of both before relying on them.

---

## 7. Step 6: Use `HomeRecommender` in `HomeScreen`

1. Delete the old `HomeSuggestions.kt` (it defines a `HomeShelves` that conflicts with the new one).
2. Copy `HomeRecommender.kt` into `com.fuelx.ui.home`.
3. Feed it from your ViewModel or the screen:

```kotlin
// Listening history from Room (collect in the ViewModel and pass in as a list)
val events: List<ListenEvent> = ...   // map ListenEventEntity -> ListenEvent

// Radio seeded by the last played song
val radioSeed = recentlyPlayed.firstOrNull()
var radio by remember(radioSeed?.id) { mutableStateOf<List<Track>>(emptyList()) }
LaunchedEffect(radioSeed?.id) {
    radio = radioSeed
        ?.let { repository.getRadio(it.id).getOrDefault(emptyList()) }
        .orEmpty()
}

val seed = remember { System.currentTimeMillis() / (6 * 3_600_000L) }   // refresh every 6 hours
val favLookup by rememberUpdatedState(isFavorite)

val shelves = remember(tracks, genreTracks, radio, recentlyPlayed, events) {
    HomeRecommender.build(
        RecoInputs(
            chart = tracks,
            radio = radio,
            genre = if (selectedGenreId == "all") emptyList() else genreTracks,
            recent = recentlyPlayed,
            events = events,
            isFavorite = { favLookup(it) },
            currentId = null,                // keep null so the list doesn't change while a song plays
            nowMs = System.currentTimeMillis(),
            seed = seed
        )
    )
}
```

New shelf order on Home:
1. **Hero:** `shelves.hero`, badge "PICKED FOR YOU"
2. **Jump back in:** `recentlyPlayed`
3. **Picked for you:** `shelves.pickedForYou`
4. **`shelves.becauseTitle`:** `shelves.becauseTracks` (only if the title is not null)
5. **Discover:** `shelves.discover`, "New artists for you" (only if not empty)
6. **Trending now:** `shelves.trending`
7. **Explore genres:** your existing chips. Selecting a genre feeds the `genre` pool and also shows that genre's list below, with the same filter.

Hide any shelf with fewer than 3 songs instead of showing a nearly empty row.

---

## 8. Tuning the feel

| Weight in `score()` | Default | Raise it to... |
|---|---|---|
| Artist affinity | 0.40 | feel more "my usual artists" |
| Radio (YouTube similarity) | 0.30 | feel more "songs like what I just played" |
| Genre/language match | 0.15 | respect the chips more |
| Chart rank | 0.15 | feel more "what's popular" |
| New-artist nudge | 0.08 | find more new music |
| Skipped-artist penalty | -0.5 | hide skipped artists faster |

Other knobs: `perArtist = 2` (artist variety), the 14-day half-life (how fast taste changes), the 6-hour seed (how often the list refreshes).

## 9. Test checklist

- [ ] No row with artist "Episode", "Video" or "YouTube Music" anywhere
- [ ] No "Hits Songs" / jukebox / non-stop / best-of items in any category
- [ ] No remix, slowed, reverb, live or cover versions
- [ ] A fresh install (no history) still shows a good chart-based Home
- [ ] Play 5 songs by one artist: that artist appears, but at most 2 per shelf
- [ ] Skip 3 songs by one artist within 30 seconds: they drop down
- [ ] Tapping a heart does not reshuffle Home
- [ ] Genre chips change the list and stay clean
- [ ] If `videoType` is `null` for everything, confirm the text rules alone still clean the lists
- [ ] Subtitle shows the artist and the real album or duration, never "YouTube Music"
- [ ] Release build on the Android 11 phone: the recommender runs on the main thread inside `remember`, so for large pools (500+) move `HomeRecommender.build` into the ViewModel on `Dispatchers.Default`
