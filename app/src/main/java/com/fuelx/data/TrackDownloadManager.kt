package com.fuelx.data

import android.content.Context
import android.util.Log
import com.fuelx.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Downloaded : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

/**
 * Manages background downloading and offline storage of audio files and artwork.
 * Audio is saved to internal storage (context.filesDir/downloads/) ensuring privacy,
 * fast local retrieval, and zero external permission requirements.
 */
class TrackDownloadManager(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val libraryRepository: LibraryRepository,
    private val libraryDatabase: LibraryDatabase
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadsDir = File(context.filesDir, "downloads").apply {
        if (!exists()) mkdirs()
    }

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    init {
        scope.launch {
            syncInitialDownloads()
        }
    }

    private suspend fun syncInitialDownloads() = withContext(Dispatchers.IO) {
        val allDownloads = libraryDatabase.getAllDownloads()
        val initialMap = mutableMapOf<String, DownloadState>()

        for (track in allDownloads) {
            val localPath = track.localFilePath
            val directFile = if (!localPath.isNullOrBlank()) File(localPath) else null
            val m4aFile = File(downloadsDir, "${track.id}.m4a")
            val webmFile = File(downloadsDir, "${track.id}.webm")

            val validFile = when {
                directFile != null && directFile.exists() && directFile.length() > 0 -> directFile
                m4aFile.exists() && m4aFile.length() > 0 -> m4aFile
                webmFile.exists() && webmFile.length() > 0 -> webmFile
                else -> null
            }

            if (validFile != null) {
                initialMap[track.id] = DownloadState.Downloaded
                if (track.localFilePath != validFile.absolutePath) {
                    libraryDatabase.insertDownload(
                        track.copy(localFilePath = validFile.absolutePath),
                        localFilePath = validFile.absolutePath,
                        localArtworkPath = track.localArtworkPath
                    )
                }
            } else {
                // Disk file was removed outside the app; cleanup database record
                libraryDatabase.deleteDownload(track.id)
            }
        }
        _downloadStates.value = initialMap
        libraryRepository.refreshDownloads()
    }

    fun getDownloadState(trackId: String): DownloadState {
        return _downloadStates.value[trackId] ?: if (isDownloaded(trackId)) {
            DownloadState.Downloaded
        } else {
            DownloadState.Idle
        }
    }

    fun isDownloaded(trackId: String): Boolean {
        val state = _downloadStates.value[trackId]
        if (state is DownloadState.Downloaded) return true
        val downloadedTrack = libraryDatabase.getDownloadedTrack(trackId)
        val path = downloadedTrack?.localFilePath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists() && file.length() > 0) return true
        }
        val m4a = File(downloadsDir, "$trackId.m4a")
        if (m4a.exists() && m4a.length() > 0) return true
        val webm = File(downloadsDir, "$trackId.webm")
        return webm.exists() && webm.length() > 0
    }

    fun downloadTrack(track: Track) {
        if (activeJobs.containsKey(track.id)) return
        if (isDownloaded(track.id)) {
            _downloadStates.value = _downloadStates.value + (track.id to DownloadState.Downloaded)
            return
        }

        _downloadStates.value = _downloadStates.value + (track.id to DownloadState.Downloading(0f))

        val job = scope.launch {
            val tempAudio = File(downloadsDir, "${track.id}.tmp")
            val targetArt = File(downloadsDir, "${track.id}.jpg")

            try {
                // 1. Resolve Audio Stream URL
                val streamResult = musicRepository.resolveStream(track.id)
                if (streamResult.isFailure) {
                    val err = streamResult.exceptionOrNull()?.message ?: "Failed to resolve audio stream"
                    _downloadStates.value = _downloadStates.value + (track.id to DownloadState.Failed(err))
                    return@launch
                }

                val streamInfo = streamResult.getOrThrow()
                val streamUrl = streamInfo.uri.toString()

                val isWebm = streamInfo.mimeType?.contains("webm", ignoreCase = true) == true
                val audioExt = if (isWebm) "webm" else "m4a"
                val targetAudio = File(downloadsDir, "${track.id}.$audioExt")

                // 2. Download Audio Stream Chunks
                val audioRequest = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                httpClient.newCall(audioRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP error ${response.code} downloading track audio")
                    }

                    val body = response.body ?: throw IOException("Empty audio response body")
                    val totalBytes = body.contentLength()
                    var bytesCopied = 0L
                    val buffer = ByteArray(16 * 1024)

                    tempAudio.outputStream().use { output ->
                        body.byteStream().use { input ->
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                if (totalBytes > 0) {
                                    val progress = (bytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    _downloadStates.value = _downloadStates.value + (track.id to DownloadState.Downloading(progress))
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (!tempAudio.exists() || tempAudio.length() == 0L) {
                    throw IOException("Downloaded audio file is empty")
                }

                if (targetAudio.exists()) {
                    targetAudio.delete()
                }
                if (!tempAudio.renameTo(targetAudio)) {
                    tempAudio.copyTo(targetAudio, overwrite = true)
                    tempAudio.delete()
                }

                // 3. Best-effort Artwork Caching
                var localArtworkPath: String? = null
                if (!track.artworkUrl.isNullOrBlank()) {
                    try {
                        val artRequest = Request.Builder().url(track.artworkUrl).build()
                        httpClient.newCall(artRequest).execute().use { artResponse ->
                            if (artResponse.isSuccessful) {
                                artResponse.body?.byteStream()?.use { artIn ->
                                    targetArt.outputStream().use { artOut ->
                                        artIn.copyTo(artOut)
                                    }
                                }
                                if (targetArt.exists() && targetArt.length() > 0) {
                                    localArtworkPath = targetArt.absolutePath
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("TrackDownloadManager", "Artwork download ignored for ${track.id}: ${e.message}")
                    }
                }

                // 4. Save to Database
                val downloadedTrack = track.copy(
                    localFilePath = targetAudio.absolutePath,
                    localArtworkPath = localArtworkPath,
                    durationMs = streamInfo.durationMs ?: track.durationMs
                )
                libraryDatabase.insertDownload(
                    track = downloadedTrack,
                    localFilePath = targetAudio.absolutePath,
                    localArtworkPath = localArtworkPath
                )
                libraryRepository.refreshDownloads()

                _downloadStates.value = _downloadStates.value + (track.id to DownloadState.Downloaded)
                Log.d("TrackDownloadManager", "Successfully downloaded: ${track.title} (${targetAudio.length()} bytes)")
            } catch (ce: CancellationException) {
                if (tempAudio.exists()) tempAudio.delete()
                _downloadStates.value = _downloadStates.value - track.id
                throw ce
            } catch (e: Exception) {
                if (tempAudio.exists()) tempAudio.delete()
                Log.e("TrackDownloadManager", "Download failed for ${track.id}: ${e.message}", e)
                _downloadStates.value = _downloadStates.value + (track.id to DownloadState.Failed(e.message ?: "Download failed"))
            } finally {
                activeJobs.remove(track.id)
            }
        }

        activeJobs[track.id] = job
    }

    fun cancelDownload(trackId: String) {
        val job = activeJobs.remove(trackId)
        job?.cancel()
        val tempAudio = File(downloadsDir, "$trackId.tmp")
        if (tempAudio.exists()) tempAudio.delete()
        _downloadStates.value = _downloadStates.value - trackId
    }

    fun deleteDownload(trackId: String) {
        cancelDownload(trackId)

        scope.launch {
            val m4aFile = File(downloadsDir, "$trackId.m4a")
            val webmFile = File(downloadsDir, "$trackId.webm")
            val artFile = File(downloadsDir, "$trackId.jpg")
            val tempAudio = File(downloadsDir, "$trackId.tmp")

            if (m4aFile.exists()) m4aFile.delete()
            if (webmFile.exists()) webmFile.delete()
            if (artFile.exists()) artFile.delete()
            if (tempAudio.exists()) tempAudio.delete()

            libraryDatabase.deleteDownload(trackId)
            libraryRepository.removeDownload(trackId)

            _downloadStates.value = _downloadStates.value - trackId
            Log.d("TrackDownloadManager", "Deleted download for $trackId")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TrackDownloadManager? = null

        fun getInstance(
            context: Context,
            musicRepository: MusicRepository,
            libraryRepository: LibraryRepository
        ): TrackDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrackDownloadManager(
                    context = context.applicationContext,
                    musicRepository = musicRepository,
                    libraryRepository = libraryRepository,
                    libraryDatabase = LibraryDatabase(context.applicationContext)
                ).also { INSTANCE = it }
            }
        }
    }
}
