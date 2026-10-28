package com.fuelx.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fuelx.model.Playlist
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

        db.execSQL(
            """
            CREATE TABLE $TABLE_DOWNLOADS (
                $COL_TRACK_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_ALBUM TEXT,
                $COL_DURATION_MS INTEGER,
                $COL_ARTWORK_URL TEXT,
                $COL_LOCAL_FILE_PATH TEXT NOT NULL,
                $COL_LOCAL_ARTWORK_PATH TEXT,
                $COL_DOWNLOADED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_history_played_at ON $TABLE_HISTORY($COL_PLAYED_AT DESC)")
        db.execSQL("CREATE INDEX idx_history_track_id ON $TABLE_HISTORY($COL_TRACK_ID)")
        db.execSQL("CREATE INDEX idx_downloads_downloaded_at ON $TABLE_DOWNLOADS($COL_DOWNLOADED_AT DESC)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLIST_TRACKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PLAYLIST_ID INTEGER NOT NULL,
                $COL_TRACK_ID TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_ARTIST TEXT NOT NULL,
                $COL_ALBUM TEXT,
                $COL_DURATION_MS INTEGER,
                $COL_ARTWORK_URL TEXT,
                $COL_STREAM_URI TEXT,
                $COL_LOCAL_FILE_PATH TEXT,
                $COL_LOCAL_ARTWORK_PATH TEXT,
                $COL_ADDED_AT INTEGER NOT NULL,
                $COL_POSITION INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_playlist_tracks_pid ON $TABLE_PLAYLIST_TRACKS($COL_PLAYLIST_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_DOWNLOADS (
                    $COL_TRACK_ID TEXT PRIMARY KEY,
                    $COL_TITLE TEXT NOT NULL,
                    $COL_ARTIST TEXT NOT NULL,
                    $COL_ALBUM TEXT,
                    $COL_DURATION_MS INTEGER,
                    $COL_ARTWORK_URL TEXT,
                    $COL_LOCAL_FILE_PATH TEXT NOT NULL,
                    $COL_LOCAL_ARTWORK_PATH TEXT,
                    $COL_DOWNLOADED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_downloads_downloaded_at ON $TABLE_DOWNLOADS($COL_DOWNLOADED_AT DESC)")
        }
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_PLAYLISTS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_NAME TEXT NOT NULL,
                    $COL_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_PLAYLIST_TRACKS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_PLAYLIST_ID INTEGER NOT NULL,
                    $COL_TRACK_ID TEXT NOT NULL,
                    $COL_TITLE TEXT NOT NULL,
                    $COL_ARTIST TEXT NOT NULL,
                    $COL_ALBUM TEXT,
                    $COL_DURATION_MS INTEGER,
                    $COL_ARTWORK_URL TEXT,
                    $COL_STREAM_URI TEXT,
                    $COL_LOCAL_FILE_PATH TEXT,
                    $COL_LOCAL_ARTWORK_PATH TEXT,
                    $COL_ADDED_AT INTEGER NOT NULL,
                    $COL_POSITION INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_playlist_tracks_pid ON $TABLE_PLAYLIST_TRACKS($COL_PLAYLIST_ID)")
        }
    }

    // --- FAVORITES OPERATIONS ---

    fun insertFavorite(track: Track, addedAtMs: Long = System.currentTimeMillis()): Boolean {
        val db = writableDatabase
        val artwork = track.artworkUrl

        val values = ContentValues().apply {
            put(COL_TRACK_ID, track.id)
            put(COL_TITLE, track.title)
            put(COL_ARTIST, track.artist)
            put(COL_ALBUM, track.album)
            put(COL_DURATION_MS, track.durationMs ?: 0L)
            put(COL_ARTWORK_URL, artwork)
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
                val trackId = it.getString(idIdx)
                val duration = it.getLong(durationIdx).takeIf { d -> d > 0 }
                val artwork = it.getString(artworkIdx)

                list.add(
                    Track(
                        id = trackId,
                        title = it.getString(titleIdx),
                        artist = it.getString(artistIdx),
                        album = it.getString(albumIdx),
                        artworkUrl = artwork,
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
                val trackId = it.getString(idIdx)
                val duration = it.getLong(durationIdx).takeIf { d -> d > 0 }
                val artwork = it.getString(artworkIdx)

                list.add(
                    Track(
                        id = trackId,
                        title = it.getString(titleIdx),
                        artist = it.getString(artistIdx),
                        album = it.getString(albumIdx),
                        artworkUrl = artwork,
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

    // --- DOWNLOADS OPERATIONS ---

    fun insertDownload(
        track: Track,
        localFilePath: String,
        localArtworkPath: String?,
        downloadedAtMs: Long = System.currentTimeMillis()
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TRACK_ID, track.id)
            put(COL_TITLE, track.title)
            put(COL_ARTIST, track.artist)
            put(COL_ALBUM, track.album)
            put(COL_DURATION_MS, track.durationMs ?: 0L)
            put(COL_ARTWORK_URL, track.artworkUrl)
            put(COL_LOCAL_FILE_PATH, localFilePath)
            put(COL_LOCAL_ARTWORK_PATH, localArtworkPath)
            put(COL_DOWNLOADED_AT, downloadedAtMs)
        }
        val result = db.insertWithOnConflict(
            TABLE_DOWNLOADS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        return result != -1L
    }

    fun deleteDownload(trackId: String): Boolean {
        val db = writableDatabase
        val rows = db.delete(TABLE_DOWNLOADS, "$COL_TRACK_ID = ?", arrayOf(trackId))
        return rows > 0
    }

    fun getAllDownloads(): List<Track> {
        val list = mutableListOf<Track>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DOWNLOADS,
            null,
            null,
            null,
            null,
            null,
            "$COL_DOWNLOADED_AT DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_TRACK_ID)
            val titleIdx = it.getColumnIndexOrThrow(COL_TITLE)
            val artistIdx = it.getColumnIndexOrThrow(COL_ARTIST)
            val albumIdx = it.getColumnIndexOrThrow(COL_ALBUM)
            val durationIdx = it.getColumnIndexOrThrow(COL_DURATION_MS)
            val artworkIdx = it.getColumnIndexOrThrow(COL_ARTWORK_URL)
            val filePathIdx = it.getColumnIndexOrThrow(COL_LOCAL_FILE_PATH)
            val artPathIdx = it.getColumnIndexOrThrow(COL_LOCAL_ARTWORK_PATH)

            while (it.moveToNext()) {
                val trackId = it.getString(idIdx)
                val duration = it.getLong(durationIdx).takeIf { d -> d > 0 }
                val artwork = it.getString(artworkIdx)
                val localPath = it.getString(filePathIdx)
                val localArt = it.getString(artPathIdx)

                list.add(
                    Track(
                        id = trackId,
                        title = it.getString(titleIdx),
                        artist = it.getString(artistIdx),
                        album = it.getString(albumIdx),
                        artworkUrl = artwork,
                        durationMs = duration,
                        streamUri = null,
                        localFilePath = localPath,
                        localArtworkPath = localArt
                    )
                )
            }
        }
        return list
    }

    fun getDownloadedTrack(trackId: String): Track? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DOWNLOADS,
            null,
            "$COL_TRACK_ID = ?",
            arrayOf(trackId),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val idIdx = it.getColumnIndexOrThrow(COL_TRACK_ID)
                val titleIdx = it.getColumnIndexOrThrow(COL_TITLE)
                val artistIdx = it.getColumnIndexOrThrow(COL_ARTIST)
                val albumIdx = it.getColumnIndexOrThrow(COL_ALBUM)
                val durationIdx = it.getColumnIndexOrThrow(COL_DURATION_MS)
                val artworkIdx = it.getColumnIndexOrThrow(COL_ARTWORK_URL)
                val filePathIdx = it.getColumnIndexOrThrow(COL_LOCAL_FILE_PATH)
                val artPathIdx = it.getColumnIndexOrThrow(COL_LOCAL_ARTWORK_PATH)

                val duration = it.getLong(durationIdx).takeIf { d -> d > 0 }
                return Track(
                    id = it.getString(idIdx),
                    title = it.getString(titleIdx),
                    artist = it.getString(artistIdx),
                    album = it.getString(albumIdx),
                    artworkUrl = it.getString(artworkIdx),
                    durationMs = duration,
                    streamUri = null,
                    localFilePath = it.getString(filePathIdx),
                    localArtworkPath = it.getString(artPathIdx)
                )
            }
        }
        return null
    }

    fun isDownloaded(trackId: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DOWNLOADS,
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

    // --- PLAYLIST OPERATIONS ---

    fun createPlaylist(name: String, createdAt: Long = System.currentTimeMillis()): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_NAME, name.trim())
            put(COL_CREATED_AT, createdAt)
        }
        return db.insert(TABLE_PLAYLISTS, null, values)
    }

    fun deletePlaylist(playlistId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_PLAYLIST_TRACKS, "$COL_PLAYLIST_ID = ?", arrayOf(playlistId.toString()))
            val deleted = db.delete(TABLE_PLAYLISTS, "$COL_ID = ?", arrayOf(playlistId.toString())) > 0
            db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_NAME, newName.trim())
        }
        return db.update(TABLE_PLAYLISTS, values, "$COL_ID = ?", arrayOf(playlistId.toString())) > 0
    }

    fun getAllPlaylists(): List<Playlist> {
        val db = readableDatabase
        val playlists = mutableListOf<Playlist>()
        val cursor = db.query(
            TABLE_PLAYLISTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_CREATED_AT DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_ID)
            val nameIdx = it.getColumnIndexOrThrow(COL_NAME)
            val createdIdx = it.getColumnIndexOrThrow(COL_CREATED_AT)

            while (it.moveToNext()) {
                val pid = it.getLong(idIdx)
                val name = it.getString(nameIdx)
                val createdAt = it.getLong(createdIdx)
                val tracks = getTracksForPlaylist(db, pid)
                playlists.add(Playlist(id = pid, name = name, createdAt = createdAt, tracks = tracks))
            }
        }
        return playlists
    }

    fun getPlaylist(playlistId: Long): Playlist? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PLAYLISTS,
            null,
            "$COL_ID = ?",
            arrayOf(playlistId.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val idIdx = it.getColumnIndexOrThrow(COL_ID)
                val nameIdx = it.getColumnIndexOrThrow(COL_NAME)
                val createdIdx = it.getColumnIndexOrThrow(COL_CREATED_AT)
                val pid = it.getLong(idIdx)
                val name = it.getString(nameIdx)
                val createdAt = it.getLong(createdIdx)
                val tracks = getTracksForPlaylist(db, pid)
                return Playlist(id = pid, name = name, createdAt = createdAt, tracks = tracks)
            }
        }
        return null
    }

    private fun getTracksForPlaylist(db: SQLiteDatabase, playlistId: Long): List<Track> {
        val tracks = mutableListOf<Track>()
        val cursor = db.query(
            TABLE_PLAYLIST_TRACKS,
            null,
            "$COL_PLAYLIST_ID = ?",
            arrayOf(playlistId.toString()),
            null,
            null,
            "$COL_POSITION ASC, $COL_ADDED_AT ASC"
        )
        cursor.use {
            val trackIdIdx = it.getColumnIndexOrThrow(COL_TRACK_ID)
            val titleIdx = it.getColumnIndexOrThrow(COL_TITLE)
            val artistIdx = it.getColumnIndexOrThrow(COL_ARTIST)
            val albumIdx = it.getColumnIndexOrThrow(COL_ALBUM)
            val durationIdx = it.getColumnIndexOrThrow(COL_DURATION_MS)
            val artworkIdx = it.getColumnIndexOrThrow(COL_ARTWORK_URL)
            val streamIdx = it.getColumnIndexOrThrow(COL_STREAM_URI)
            val localFileIdx = it.getColumnIndexOrThrow(COL_LOCAL_FILE_PATH)
            val localArtIdx = it.getColumnIndexOrThrow(COL_LOCAL_ARTWORK_PATH)

            while (it.moveToNext()) {
                val dur = it.getLong(durationIdx).takeIf { d -> d > 0 }
                tracks.add(
                    Track(
                        id = it.getString(trackIdIdx),
                        title = it.getString(titleIdx),
                        artist = it.getString(artistIdx),
                        album = it.getString(albumIdx),
                        artworkUrl = it.getString(artworkIdx),
                        durationMs = dur,
                        streamUri = it.getString(streamIdx),
                        localFilePath = it.getString(localFileIdx),
                        localArtworkPath = it.getString(localArtIdx)
                    )
                )
            }
        }
        return tracks
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track, addedAtMs: Long = System.currentTimeMillis()): Boolean {
        val db = writableDatabase
        // Prevent duplicate songs in the same playlist
        if (isTrackInPlaylist(playlistId, track.id)) {
            return false
        }

        // Determine next position
        var nextPosition = 0
        val posCursor = db.rawQuery(
            "SELECT MAX($COL_POSITION) FROM $TABLE_PLAYLIST_TRACKS WHERE $COL_PLAYLIST_ID = ?",
            arrayOf(playlistId.toString())
        )
        posCursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                nextPosition = it.getInt(0) + 1
            }
        }

        val values = ContentValues().apply {
            put(COL_PLAYLIST_ID, playlistId)
            put(COL_TRACK_ID, track.id)
            put(COL_TITLE, track.title)
            put(COL_ARTIST, track.artist)
            put(COL_ALBUM, track.album)
            put(COL_DURATION_MS, track.durationMs ?: 0L)
            put(COL_ARTWORK_URL, track.artworkUrl)
            put(COL_STREAM_URI, track.streamUri)
            put(COL_LOCAL_FILE_PATH, track.localFilePath)
            put(COL_LOCAL_ARTWORK_PATH, track.localArtworkPath)
            put(COL_ADDED_AT, addedAtMs)
            put(COL_POSITION, nextPosition)
        }
        return db.insert(TABLE_PLAYLIST_TRACKS, null, values) != -1L
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Boolean {
        val db = writableDatabase
        return db.delete(
            TABLE_PLAYLIST_TRACKS,
            "$COL_PLAYLIST_ID = ? AND $COL_TRACK_ID = ?",
            arrayOf(playlistId.toString(), trackId)
        ) > 0
    }

    fun isTrackInPlaylist(playlistId: Long, trackId: String): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PLAYLIST_TRACKS,
            arrayOf(COL_ID),
            "$COL_PLAYLIST_ID = ? AND $COL_TRACK_ID = ?",
            arrayOf(playlistId.toString(), trackId),
            null,
            null,
            null
        )
        cursor.use {
            return it.count > 0
        }
    }

    companion object {
        const val DATABASE_NAME = "fuelx_library.db"
        const val DATABASE_VERSION = 3

        const val TABLE_FAVORITES = "favorites"
        const val TABLE_HISTORY = "history"
        const val TABLE_DOWNLOADS = "downloads"
        const val TABLE_PLAYLISTS = "playlists"
        const val TABLE_PLAYLIST_TRACKS = "playlist_tracks"

        const val COL_ID = "id"
        const val COL_TRACK_ID = "track_id"
        const val COL_PLAYLIST_ID = "playlist_id"
        const val COL_NAME = "name"
        const val COL_TITLE = "title"
        const val COL_ARTIST = "artist"
        const val COL_ALBUM = "album"
        const val COL_DURATION_MS = "duration_ms"
        const val COL_ARTWORK_URL = "artwork_url"
        const val COL_STREAM_URI = "stream_uri"
        const val COL_LOCAL_FILE_PATH = "local_file_path"
        const val COL_LOCAL_ARTWORK_PATH = "local_artwork_path"
        const val COL_ADDED_AT = "added_at"
        const val COL_PLAYED_AT = "played_at"
        const val COL_DOWNLOADED_AT = "downloaded_at"
        const val COL_CREATED_AT = "created_at"
        const val COL_POSITION = "position"

        const val MAX_HISTORY_ITEMS = 100
    }
}
