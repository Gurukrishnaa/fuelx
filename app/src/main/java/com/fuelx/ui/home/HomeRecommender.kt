package com.fuelx.ui.home

import com.fuelx.data.filter.SongFilter
import com.fuelx.model.Track
import java.util.Random
import kotlin.math.pow

/** One row per finished / skipped play. Stored in Room / SQLite (see the guide). */
data class ListenEvent(
    val trackId: String,
    val artist: String,
    val playedMs: Long,
    val durationMs: Long?,
    val completed: Boolean,
    val atEpochMs: Long
)

data class RecoInputs(
    val chart: List<Track>,                 // trending, in chart order
    val radio: List<Track>,                 // "radio" from the last played song (InnerTube next)
    val genre: List<Track>,                 // results for the selected genre / language
    val recent: List<Track>,                // recently played, newest first
    val events: List<ListenEvent>,          // listening history, newest first
    val isFavorite: (String) -> Boolean,
    val currentId: String?,
    val nowMs: Long,
    val seed: Long                          // keep stable for a few hours
)

data class HomeShelves(
    val hero: Track?,
    val pickedForYou: List<Track>,
    val becauseTitle: String?,              // "Because you played Mutta Kalakki"
    val becauseTracks: List<Track>,
    val discover: List<Track>,              // new artists, outside your usual taste
    val trending: List<Track>
) {
    companion object {
        val Empty = HomeShelves(null, emptyList(), null, emptyList(), emptyList(), emptyList())
    }
}

object HomeRecommender {

    private const val DAY_MS = 86_400_000L

    fun build(i: RecoInputs): HomeShelves {
        // 1. Clean every pool with the same strict filter.
        val chart = i.chart.filter(SongFilter::isRealSong)
        val radio = i.radio.filter(SongFilter::isRealSong)
        val genre = i.genre.filter(SongFilter::isRealSong)

        val chartR = rankMap(chart)
        val radioR = rankMap(radio)
        val genreR = rankMap(genre)

        // 2. Never suggest what you just played.
        val blockedIds = HashSet<String>().apply {
            i.recent.take(30).forEach { add(it.id) }
            i.currentId?.let { add(it) }
            i.events.filter { i.nowMs - it.atEpochMs < DAY_MS && it.playedMs >= 30_000 }
                .forEach { add(it.trackId) }
        }
        val recentKeys = i.recent.take(30).map(SongFilter::songKey).toSet()

        val candidates = (radio + genre + chart)
            .distinctBy(SongFilter::songKey)
            .filter { it.id !in blockedIds && SongFilter.songKey(it) !in recentKeys }
        if (candidates.isEmpty()) return HomeShelves.Empty

        // 3. Taste profile from real listening behaviour.
        val taste = buildTaste(i, candidates)
        val maxPos = (taste.pos.values.maxOrNull() ?: 0f).coerceAtLeast(1f)

        fun affinity(t: Track): Float =
            (SongFilter.artistsOf(t).maxOfOrNull { taste.pos[it] ?: 0f } ?: 0f) / maxPos

        val rnd = Random(i.seed)

        fun score(t: Track): Float {
            val k = SongFilter.songKey(t)
            val aff = affinity(t)
            val skip = SongFilter.artistsOf(t).maxOfOrNull { taste.neg[it] ?: 0f } ?: 0f
            var s = 0.40f * aff +
                0.30f * (radioR[k] ?: 0f) +      // YouTube's own "similar to what you play"
                0.15f * (genreR[k] ?: 0f) +      // matches the genre / language you picked
                0.15f * (chartR[k] ?: 0f)        // popular right now
            if (aff == 0f) s += 0.08f            // small nudge toward new artists
            if (t.videoType?.uppercase()?.endsWith("ATV") == true) s += 0.05f   // official audio
            if (skip >= 1.5f) s -= 0.5f          // you keep skipping this artist
            s += rnd.nextFloat() * 0.05f         // tiny jitter so the list isn't frozen
            return s
        }

        val ranked = candidates.map { it to score(it) }
            .sortedByDescending { it.second }
            .map { it.first }

        // 4. Build the shelves.
        val picked = pick(ranked, limit = 10, perArtist = 2)
        val hero = picked.firstOrNull()
        val pickedForYou = picked.drop(1)
        val used = HashSet<String>().apply { picked.forEach { add(it.id) } }

        // "Because you played X": radio of the last song, else more from that artist
        val seedTrack = i.recent.firstOrNull()
        var because = radio
            .filter { it.id !in blockedIds && it.id !in used }
            .distinctBy(SongFilter::songKey)
            .take(8)
        if (because.size < 3 && seedTrack != null) {
            val a = SongFilter.primaryArtist(seedTrack)
            because = candidates.filter { it.id !in used && a in SongFilter.artistsOf(it) }.take(8)
        }
        val becauseTitle = if (because.size >= 3 && seedTrack != null)
            "Because you played ${seedTrack.title.substringBefore('(').trim()}" else null
        if (becauseTitle != null) because.forEach { used += it.id }

        // Discover: artists you haven't played yet, best pool rank first
        val discover = pick(
            candidates
                .filter { it.id !in used && affinity(it) == 0f }
                .sortedByDescending {
                    val k = SongFilter.songKey(it)
                    (radioR[k] ?: 0f) + (genreR[k] ?: 0f) + (chartR[k] ?: 0f)
                },
            limit = 8, perArtist = 1
        )
        discover.forEach { used += it.id }

        // Trending: pure chart order, whatever is left
        val trending = chart
            .filter { it.id !in used && it.id !in blockedIds }
            .distinctBy(SongFilter::songKey)
            .take(10)

        return HomeShelves(
            hero = hero,
            pickedForYou = pickedForYou,
            becauseTitle = becauseTitle,
            becauseTracks = if (becauseTitle != null) because else emptyList(),
            discover = if (discover.size >= 3) discover else emptyList(),
            trending = trending
        )
    }

    // ---------- taste ----------

    private class Taste(val pos: Map<String, Float>, val neg: Map<String, Float>)

    private fun buildTaste(i: RecoInputs, candidates: List<Track>): Taste {
        val pos = HashMap<String, Float>()
        val neg = HashMap<String, Float>()

        fun add(map: HashMap<String, Float>, artist: String, w: Float) {
            SongFilter.splitArtists(artist).forEach {
                map[it] = (map[it] ?: 0f) + w
            }
        }

        // Real listening: finished songs count most, early skips count against.
        for (e in i.events) {
            val ageDays = ((i.nowMs - e.atEpochMs) / DAY_MS.toFloat()).coerceAtLeast(0f)
            val decay = 0.5f.pow(ageDays / 14f)                      // half-life: 14 days
            val ratio = e.durationMs?.takeIf { it > 0 }?.let { e.playedMs.toFloat() / it }
                ?: if (e.completed) 1f else 0.3f
            val w = when {
                e.completed || ratio >= 0.85f -> 1.0f
                ratio >= 0.5f -> 0.6f
                e.playedMs < 30_000 -> -1.0f
                else -> 0.1f
            } * decay
            if (w >= 0f) add(pos, e.artist, w) else add(neg, e.artist, -w)
        }

        // Recently played (also works before any events exist)
        i.recent.take(20).forEachIndexed { idx, t ->
            add(pos, t.artist, 0.5f / (1f + idx * 0.15f))
        }
        // Favorites count extra
        candidates.filter { i.isFavorite(it.id) }.forEach { add(pos, it.artist, 1.5f) }

        return Taste(pos, neg)
    }

    // ---------- selection ----------

    private fun rankMap(list: List<Track>): Map<String, Float> {
        val m = HashMap<String, Float>()
        val n = list.size.coerceAtLeast(1)
        list.forEachIndexed { idx, t -> m.putIfAbsent(SongFilter.songKey(t), 1f - idx.toFloat() / n) }
        return m
    }

    /** Greedy pick: max [perArtist] per artist, and avoid two songs by the same artist in a row. */
    private fun pick(sorted: List<Track>, limit: Int, perArtist: Int): List<Track> {
        val count = HashMap<String, Int>()
        val rest = ArrayList(sorted)
        val out = ArrayList<Track>(limit)
        while (out.size < limit && rest.isNotEmpty()) {
            val last = out.lastOrNull()?.let(SongFilter::primaryArtist)
            var idx = rest.indexOfFirst {
                val a = SongFilter.primaryArtist(it)
                (count[a] ?: 0) < perArtist && a != last
            }
            if (idx < 0) idx = rest.indexOfFirst { (count[SongFilter.primaryArtist(it)] ?: 0) < perArtist }
            if (idx < 0) break
            val t = rest.removeAt(idx)
            val a = SongFilter.primaryArtist(t)
            count[a] = (count[a] ?: 0) + 1
            out += t
        }
        return out
    }
}

