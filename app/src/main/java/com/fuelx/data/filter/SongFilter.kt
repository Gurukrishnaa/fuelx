package com.fuelx.data.filter

import com.fuelx.model.Track
import com.fuelx.util.cleanArtistString

/**
 * One place that decides "is this a real song?".
 * Use it in the REPOSITORY (so every screen is clean), not only in Home.
 *
 * Needs two optional fields on Track (see the guide):
 *   videoType : "ATV", "OMV", "UGC", "PODCAST_EPISODE", "OFFICIAL_SOURCE_MUSIC" (null if unknown)
 *   typeLabel : "Song", "Video", "Episode", "Podcast" ... (null if not shown)
 * If they are null (parser not patched yet) the text rules below still work.
 */
object SongFilter {

    private const val MIN_MS = 60_000L          // below 1 min: ringtones, shorts, intros
    private const val MAX_MS = 9 * 60_000L      // above 9 min: mixes, podcasts, full albums

    // Official audio tracks, official music videos, label-supplied tracks
    private val ALLOWED_TYPES = setOf("ATV", "OMV", "OFFICIAL_SOURCE_MUSIC")

    // "Episode", "1d ago" etc. show up in the artist/album slot for podcasts
    private val BAD_ARTIST_WORDS = setOf("episode", "podcast", "video", "song", "youtube music")
    private val AGO = Regex("""^\d+\s*(s|m|h|d|w|mo|y)\w*\s+ago$""", RegexOption.IGNORE_CASE)

    // Compilations, podcasts, non-music uploads
    private val NON_SONG = Regex(
        """\b(non[\s-]?stop|mega\s?mix|full\s?album|greatest\s?hits|best\s?of|top\s?\d+|""" +
            """hits?\s+songs?|all\s+songs?|songs?\s+collection|collection|compilation|back\s?to\s?back|""" +
            """playlist|podcast|episode|reaction|review|interview|trailer|teaser|ringtone|""" +
            """whatsapp\s?status|status\s?video|audio\s?launch|making\s?of|full\s?movie)\b""",
        RegexOption.IGNORE_CASE
    )

    // "2026 Tamil Hits Songs", "Songs 2025", "Hits of 2024"
    private val YEAR_COMPILATION = Regex(
        """\b(19|20)\d\d\b.*\b(songs?|hits?)\b|\b(songs?|hits?)\b.*\b(19|20)\d\d\b""",
        RegexOption.IGNORE_CASE
    )

    // Words that are never part of a normal song title
    private val UNAMBIGUOUS_VERSION = Regex(
        """\b(remix(ed)?|slowed|reverb|sped\s?up|speed\s?up|nightcore|8d|bass\s?boosted|mashup|jukebox|dj)\b""",
        RegexOption.IGNORE_CASE
    )

    // Words that are fine in a title, but mean "different version" inside ( ), [ ] or after " - "
    private val AMBIGUOUS_VERSION = Regex(
        """\b(live|cover|instrumental|acoustic|unplugged|mix|lofi|lo-fi|karaoke)\b""",
        RegexOption.IGNORE_CASE
    )

    private val ARTIST_SPLIT = Regex(
        """\s*(,|&|•|\bfeat\.?\b|\bft\.?\b|\bx\b)\s*""",
        RegexOption.IGNORE_CASE
    )

    fun isRealSong(t: Track): Boolean {
        val type = t.videoType?.uppercase()?.removePrefix("MUSIC_VIDEO_TYPE_")
        val label = t.typeLabel?.lowercase()
        val artist = cleanArtistString(t.artist).trim()
        val album = t.album?.trim().orEmpty()
        val title = t.title

        // 1. Hard type checks
        if (type != null && type !in ALLOWED_TYPES) return false          // UGC uploads, podcast episodes
        if (label == "episode" || label == "podcast") return false
        if (label == "video" && type != "OMV") return false
        if (artist.isBlank() || artist.lowercase() in BAD_ARTIST_WORDS) return false
        if (AGO.matches(album)) return false                              // "1d ago" in the album slot

        // 2. Duration (unknown is allowed)
        val d = t.durationMs
        if (d != null && d !in MIN_MS..MAX_MS) return false

        // 3. Title rules
        if (NON_SONG.containsMatchIn(title)) return false
        if (YEAR_COMPILATION.containsMatchIn(title)) return false
        if (UNAMBIGUOUS_VERSION.containsMatchIn(title)) return false
        val suffix = suffixOf(title)
        if (suffix.isNotBlank() && AMBIGUOUS_VERSION.containsMatchIn(suffix)) return false
        if (type != "ATV" && title.contains(" | ")) return false           // "Song | Movie | Singer" uploads

        return true
    }

    // ---- helpers shared with the recommender ----

    fun splitArtists(raw: String): List<String> =
        cleanArtistString(raw).split(ARTIST_SPLIT)
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

    fun artistsOf(t: Track): List<String> = splitArtists(t.artist)

    fun primaryArtist(t: Track): String = artistsOf(t).firstOrNull() ?: t.id

    /** "Song" and "Song (From "Movie")" by the same artist collapse to one key. */
    fun songKey(t: Track): String {
        val base = t.title.substringBefore('(').substringBefore('[').substringBefore(" - ")
            .lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        return base + "|" + primaryArtist(t)
    }

    private fun suffixOf(title: String): String {
        val parts = ArrayList<String>(3)
        title.indexOf('(').takeIf { it >= 0 }?.let { parts += title.substring(it) }
        title.indexOf('[').takeIf { it >= 0 }?.let { parts += title.substring(it) }
        title.indexOf(" - ").takeIf { it >= 0 }?.let { parts += title.substring(it) }
        return parts.joinToString(" ")
    }
}

