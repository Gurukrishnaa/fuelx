package com.fuelx.data

import android.content.Context
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
 * Repository interface defining operations for persistent Favorites and History.
 */
interface LibraryRepository {
    val favorites: StateFlow<List<Track>>
    val history: StateFlow<List<Track>>
    val downloads: StateFlow<List<Track>>

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
