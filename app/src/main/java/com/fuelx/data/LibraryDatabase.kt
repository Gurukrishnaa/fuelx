package com.fuelx.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fuelx.model.Track

/**
 * SQLite database helper for persistent storage of FuelX user library data:
 * - Favorites (tracks marked as favorite)
 * - Playback History (bounded chronological record of listened tracks)
 */
class LibraryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_FAVORITES (
                $COL_TRACK_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_ALBUM TEXT,
                $COL_DURATION_MS INTEGER,
                $COL_ARTWORK_URL TEXT,
                $COL_STREAM_URI TEXT,
                $COL_ADDED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TRACK_ID TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_ALBUM TEXT,
                $COL_DURATION_MS INTEGER,
                $COL_ARTWORK_URL TEXT,
                $COL_STREAM_URI TEXT,
                $COL_PLAYED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_history_played_at ON $TABLE_HISTORY($COL_PLAYED_AT DESC)")
        db.execSQL("CREATE INDEX idx_history_track_id ON $TABLE_HISTORY($COL_TRACK_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Drop and recreate if schema changes in future versions
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FAVORITES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    // --- FAVORITES OPERATIONS ---

    fun insertFavorite(track: Track, addedAtMs: Long = System.currentTimeMillis()): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TRACK_ID, track.id)
            put(COL_TITLE, track.title)
            put(COL_ARTIST, track.artist)
            put(COL_ALBUM, track.album)
            put(COL_DURATION_MS, track.durationMs ?: 0L)
            put(COL_ARTWORK_URL, track.artworkUrl)
            put(COL_STREAM_URI, track.streamUri)
            put(COL_ADDED_AT, addedAtMs)
        }
        val result = db.insertWithOnConflict(
            TABLE_FAVORITES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return result != -1L
    }

    fun deleteFavorite(trackId: String): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_FAVORITES, "$COL_TRACK_ID = ?", arrayOf(trackId))
        return rows > 0
    }

    fun getAllFavorites(): List<Track> {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FAVORITES,
            null,
            null,
            null,
            null,
            null,
            "$COL_ADDED_AT DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_TRACK_ID)
            val titleIdx = it.getColumnIndexOrThrow(COL_TITLE)
            val artistIdx = it.getColumnIndexOrThrow(COL_ARTIST)
            val albumIdx = it.getColumnIndexOrThrow(COL_ALBUM)
            val durationIdx = it.getColumnIndexOrThrow(COL_DURATION_MS)
            val artworkIdx = it.getColumnIndexOrThrow(COL_ARTWORK_URL)
            val streamIdx = it.getColumnIndexOrThrow(COL_STREAM_URI)

            while (it.moveToNext()) {
                val duration = it.getLong(durationIdx).takeIf { d -> d > 0 }
                list.add(
                    Track(
                        id = it.getString(idIdx),
                        title = it.getString(titleIdx),
                        artist = it.getString(artistIdx),
                        album = it.getString(albumIdx),
                        artworkUrl = it.getString(artworkIdx),
                        durationMs = duration,
                        streamUri = it.getString(streamIdx)
                    )
                )
            }
        }
        return list
    }

    fun isTrackFavorite(trackId: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FAVORITES,
            arrayOf(COL_TRACK_ID),
            "$COL_TRACK_ID = ?",
            arrayOf(trackId),
            null,
            null,
            null
        )
        cursor.use {
            return it.count > 0
        }
    }

    // --- HISTORY OPERATIONS ---

    fun insertHistory(track: Track, playedAtMs: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Deduplicate: remove any previous entry for this track so it jumps to top
            db.delete(TABLE_HISTORY, "$COL_TRACK_ID = ?", arrayOf(track.id))

            val values = ContentValues().apply {
                put(COL_TRACK_ID, track.id)
                put(COL_TITLE, track.title)
                put(COL_ARTIST, track.artist)
                put(COL_ALBUM, track.album)
                put(COL_DURATION_MS, track.durationMs ?: 0L)
                put(COL_ARTWORK_URL, track.artworkUrl)
                put(COL_STREAM_URI, track.streamUri)
                put(COL_PLAYED_AT, playedAtMs)
            }
            db.insert(TABLE_HISTORY, null, values)

            // Bound history to MAX_HISTORY_ITEMS (keep newest, prune oldest)
            db.execSQL(
                """
                DELETE FROM $TABLE_HISTORY 
                WHERE $COL_ID NOT IN (
                    SELECT $COL_ID FROM $TABLE_HISTORY 
                    ORDER BY $COL_PLAYED_AT DESC 
                    LIMIT $MAX_HISTORY_ITEMS
                )
                """.trimIndent()
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllHistory(): List<Track> {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HISTORY,
            null,
            null,
            null,
            null,
            null,
            "$COL_PLAYED_AT DESC",
            MAX_HISTORY_ITEMS.toString()
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_TRACK_ID)
            val titleIdx = it.getColumnIndexOrThrow(COL_TITLE)
            val artistIdx = it.getColumnIndexOrThrow(COL_ARTIST)
            val albumIdx = it.getColumnIndexOrThrow(COL_ALBUM)
            val durationIdx = it.getColumnIndexOrThrow(COL_DURATION_MS)
            val artworkIdx = it.getColumnIndexOrThrow(COL_ARTWORK_URL)
            val streamIdx = it.getColumnIndexOrThrow(COL_STREAM_URI)

            while (it.moveToNext()) {
                val duration = it.getLong(durationIdx).takeIf { d -> d > 0 }
                list.add(
                    Track(
                        id = it.getString(idIdx),
                        title = it.getString(titleIdx),
                        artist = it.getString(artistIdx),
                        album = it.getString(albumIdx),
                        artworkUrl = it.getString(artworkIdx),
                        durationMs = duration,
                        streamUri = it.getString(streamIdx)
                    )
                )
            }
        }
        return list
    }

    fun clearAllHistory() {
        val db = writableDatabase
        db.delete(TABLE_HISTORY, null, null)
    }

    companion object {
        const val DATABASE_NAME = "fuelx_library.db"
        const val DATABASE_VERSION = 1

        const val TABLE_FAVORITES = "favorites"
        const val TABLE_HISTORY = "history"

        const val COL_ID = "id"
        const val COL_TRACK_ID = "track_id"
        const val COL_TITLE = "title"
        const val COL_ARTIST = "artist"
        const val COL_ALBUM = "album"
        const val COL_DURATION_MS = "duration_ms"
        const val COL_ARTWORK_URL = "artwork_url"
        const val COL_STREAM_URI = "stream_uri"
        const val COL_ADDED_AT = "added_at"
        const val COL_PLAYED_AT = "played_at"

        const val MAX_HISTORY_ITEMS = 100
    }
}
