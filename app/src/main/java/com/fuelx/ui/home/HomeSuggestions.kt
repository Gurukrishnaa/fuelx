package com.fuelx.ui.home

import com.fuelx.model.Track
import com.fuelx.util.cleanArtistString
import java.util.Random

/**
 * Everything the Home screen shows as "suggestions", computed in one pure function.
 * No Compose, no network. Easy to test.
 */
data class HomeShelves(
    val hero: Track?,               // single spotlight pick
    val pickedForYou: List<Track>,  // personalised, diversified by artist
    val becauseArtist: String?,     // e.g. "G. V. Prakash Kumar"
    val becauseTracks: List<Track>, // more from that artist
    val trending: List<Track>       // leftover chart-order songs
)

object HomeSuggestions {

    private const val MIN_MS = 45_000L
    private const val MAX_MS = 10 * 60_000L

    // Whole-word match only. "Remix" and "Mixed Feelings" are NOT rejected any more.
    private val MIX_TITLE = Regex(
        """\b(jukebox|non[\s-]?stop|mashup|mega\s?mix|dj\s?mix|full\s?album|""" +
            """greatest\s?hits|hit\s?songs|all\s?songs|collection|compilation|back\s?to\s?back)\b""",
        RegexOption.IGNORE_CASE
    )

    private val ARTIST_SPLIT = Regex(
        """\s*(,|&|•|\bfeat\.?\b|\bft\.?\b|\bx\b)\s*""",
        RegexOption.IGNORE_CASE
    )

    /** Rejects mixes, jukeboxes and over-long uploads. Unknown duration is allowed. */
    fun isCleanSong(t: Track): Boolean {
        val d = t.durationMs
        if (d != null && d !in MIN_MS..MAX_MS) return false
        return !MIX_TITLE.containsMatchIn(t.title)
    }

    private fun artistsOf(t: Track): List<String> =
        cleanArtistString(t.artist)
            .split(ARTIST_SPLIT)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

    /** Same song uploaded twice ("Song" and "Song (From "Movie")") collapses to one key. */
    private fun songKey(t: Track): String =
        t.title.substringBefore('(').substringBefore('[').trim().lowercase() +
            "|" + (artistsOf(t).firstOrNull() ?: "")

    /**
     * @param pool       tracks from the repository, in chart/trending order
     * @param recent     recently played, newest first
     * @param isFavorite lookup by track id
     * @param currentId  currently playing track id (excluded from suggestions)
     * @param seed       changes the small random jitter; keep it stable for a few hours
     *                   so the list doesn't reshuffle on every recomposition
     */
    fun build(
        pool: List<Track>,
        recent: List<Track>,
        isFavorite: (String) -> Boolean,
        currentId: String?,
        seed: Long
    ): HomeShelves {
        val clean = pool.filter(::isCleanSong).distinctBy(::songKey)
        if (clean.isEmpty()) return HomeShelves(null, emptyList(), null, emptyList(), emptyList())

        val recentIds = recent.map { it.id }.toSet()

        // 1. Taste profile: artist -> weight. Recent plays decay; favorites count extra.
        val weights = HashMap<String, Float>()
        recent.take(20).forEachIndexed { i, t ->
            val w = 1f / (1f + i * 0.15f)
            artistsOf(t).forEach { a -> weights[a] = (weights[a] ?: 0f) + w }
        }
        clean.filter { isFavorite(it.id) }.forEach { t ->
            artistsOf(t).forEach { a -> weights[a] = (weights[a] ?: 0f) + 1.5f }
        }

        // 2. Score every candidate we haven't just played.
        val rnd = Random(seed)
        val candidates = clean.filter { it.id !in recentIds && it.id != currentId }
        val total = candidates.size.coerceAtLeast(1)
        val ranked = candidates
            .mapIndexed { idx, t ->
                val affinity = artistsOf(t).maxOfOrNull { weights[it] ?: 0f } ?: 0f
                val trend = 1f - idx.toFloat() / total          // earlier in pool = more trending
                t to (affinity * 2f + trend + rnd.nextFloat() * 0.35f)
            }
            .sortedByDescending { it.second }
            .map { it.first }

        // 3. Never show more than 2 songs by the same artist in the personalised list.
        val picked = diversify(ranked, limit = 9, perArtist = 2)
        val hero = picked.firstOrNull() ?: clean.first()
        val pickedForYou = picked.drop(1)
        val used = (picked + hero).map { it.id }.toMutableSet()

        // 4. "Because you played X": more from the artist you listened to most recently.
        val becauseArtist = recent.firstOrNull()?.let { artistsOf(it).firstOrNull() }
        val becauseTracks = becauseArtist?.let { a ->
            clean.filter { t ->
                a in artistsOf(t) && t.id !in recentIds && t.id !in used && t.id != currentId
            }.take(8)
        }.orEmpty()
        used += becauseTracks.map { it.id }

        // 5. Whatever is left, still in chart order, becomes the Trending list.
        val trending = clean.filter { it.id !in used && it.id !in recentIds }.take(20)

        return HomeShelves(
            hero = hero,
            pickedForYou = pickedForYou,
            becauseArtist = becauseArtist?.takeIf { becauseTracks.size >= 3 }
                ?.let { name -> recent.first().artistDisplayName(name) },
            becauseTracks = if (becauseTracks.size >= 3) becauseTracks else emptyList(),
            trending = trending
        )
    }

    private fun diversify(sorted: List<Track>, limit: Int, perArtist: Int): List<Track> {
        val count = HashMap<String, Int>()
        val out = ArrayList<Track>(limit)
        for (t in sorted) {
            val key = artistsOf(t).firstOrNull() ?: t.id
            if ((count[key] ?: 0) >= perArtist) continue
            count[key] = (count[key] ?: 0) + 1
            out += t
            if (out.size == limit) break
        }
        return out
    }

    // Shows the artist with its original capitalisation for the section title.
    private fun Track.artistDisplayName(lowerName: String): String =
        cleanArtistString(artist)
            .split(ARTIST_SPLIT)
            .map { it.trim() }
            .firstOrNull { it.lowercase() == lowerName }
            ?: lowerName
}

