package com.fuelx.data

import android.content.Context
import com.fuelx.model.Playlist
import com.fuelx.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository interface defining operations for persistent Favorites, History, Downloads, and Playlists.
 */
interface LibraryRepository {
    val favorites: StateFlow<List<Track>>
    val history: StateFlow<List<Track>>
    val downloads: StateFlow<List<Track>>
    val playlists: StateFlow<List<Playlist>>

    fun isFavorite(trackId: String): Boolean
    suspend fun toggleFavorite(track: Track): Boolean
    suspend fun addFavorite(track: Track)
    suspend fun removeFavorite(trackId: String)
    suspend fun recordHistory(track: Track)
    suspend fun clearHistory()

    fun isDownloaded(trackId: String): Boolean
    fun getDownloadedTrack(trackId: String): Track?
    suspend fun refreshDownloads()
    suspend fun removeDownload(trackId: String)

    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlistId: Long): Boolean
    suspend fun renamePlaylist(playlistId: Long, newName: String): Boolean
    suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Boolean
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Boolean
    suspend fun refreshPlaylists()
}

class DefaultLibraryRepository(
    private val database: LibraryDatabase
) : LibraryRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _favorites = MutableStateFlow<List<Track>>(emptyList())
    override val favorites: StateFlow<List<Track>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<Track>>(emptyList())
    override val history: StateFlow<List<Track>> = _history.asStateFlow()

    private val _downloads = MutableStateFlow<List<Track>>(emptyList())
    override val downloads: StateFlow<List<Track>> = _downloads.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    override val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val favoriteIds = mutableSetOf<String>()
    private val downloadedIds = mutableSetOf<String>()

    init {
        // Load initial state asynchronously
        scope.launch {
            loadInitialData()
        }
    }

    private suspend fun loadInitialData() = withContext(Dispatchers.IO) {
        val favs = database.getAllFavorites()
        val hist = database.getAllHistory()
        val down = database.getAllDownloads()
        val plist = database.getAllPlaylists()

        synchronized(favoriteIds) {
            favoriteIds.clear()
            favoriteIds.addAll(favs.map { it.id })
        }

        synchronized(downloadedIds) {
            downloadedIds.clear()
            downloadedIds.addAll(down.map { it.id })
        }

        _favorites.value = favs
        _history.value = hist
        _downloads.value = down
        _playlists.value = plist
    }

    override fun isFavorite(trackId: String): Boolean {
        synchronized(favoriteIds) {
            return favoriteIds.contains(trackId)
        }
    }

    override suspend fun toggleFavorite(track: Track): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = isFavorite(track.id)
        if (currentlyFav) {
            removeFavorite(track.id)
            false
        } else {
            addFavorite(track)
            true
        }
    }

    override suspend fun addFavorite(track: Track) = withContext(Dispatchers.IO) {
        database.insertFavorite(track)
        synchronized(favoriteIds) {
            favoriteIds.add(track.id)
        }
        _favorites.value = database.getAllFavorites()
    }

    override suspend fun removeFavorite(trackId: String) = withContext(Dispatchers.IO) {
        database.deleteFavorite(trackId)
        synchronized(favoriteIds) {
            favoriteIds.remove(trackId)
        }
        _favorites.value = database.getAllFavorites()
    }

    override suspend fun recordHistory(track: Track) = withContext(Dispatchers.IO) {
        database.insertHistory(track)
        _history.value = database.getAllHistory()
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        database.clearAllHistory()
        _history.value = emptyList()
    }

    override fun isDownloaded(trackId: String): Boolean {
        synchronized(downloadedIds) {
            return downloadedIds.contains(trackId)
        }
    }

    override fun getDownloadedTrack(trackId: String): Track? {
        return database.getDownloadedTrack(trackId)
    }

    override suspend fun refreshDownloads() = withContext(Dispatchers.IO) {
        val down = database.getAllDownloads()
        synchronized(downloadedIds) {
            downloadedIds.clear()
            downloadedIds.addAll(down.map { it.id })
        }
        _downloads.value = down
    }

    override suspend fun removeDownload(trackId: String) = withContext(Dispatchers.IO) {
        database.deleteDownload(trackId)
        synchronized(downloadedIds) {
            downloadedIds.remove(trackId)
        }
        _downloads.value = database.getAllDownloads()
    }

    override suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val id = database.createPlaylist(name)
        _playlists.value = database.getAllPlaylists()
        id
    }

    override suspend fun deletePlaylist(playlistId: Long): Boolean = withContext(Dispatchers.IO) {
        val deleted = database.deletePlaylist(playlistId)
        _playlists.value = database.getAllPlaylists()
        deleted
    }

    override suspend fun renamePlaylist(playlistId: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        val renamed = database.renamePlaylist(playlistId, newName)
        _playlists.value = database.getAllPlaylists()
        renamed
    }

    override suspend fun addTrackToPlaylist(playlistId: Long, track: Track): Boolean = withContext(Dispatchers.IO) {
        val added = database.addTrackToPlaylist(playlistId, track)
        if (added) {
            _playlists.value = database.getAllPlaylists()
        }
        added
    }

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String): Boolean = withContext(Dispatchers.IO) {
        val removed = database.removeTrackFromPlaylist(playlistId, trackId)
        if (removed) {
            _playlists.value = database.getAllPlaylists()
        }
        removed
    }

    override suspend fun refreshPlaylists() = withContext(Dispatchers.IO) {
        _playlists.value = database.getAllPlaylists()
    }

    companion object {
        @Volatile
        private var INSTANCE: DefaultLibraryRepository? = null

        fun getInstance(context: Context): DefaultLibraryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DefaultLibraryRepository(
                    LibraryDatabase(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }
}
