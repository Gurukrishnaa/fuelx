package com.fuelx.data.youtube

import android.net.Uri
import com.fuelx.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Resilient stream resolver for YouTube audio.
 * Leverages open Invidious streaming instances with multi-instance failover
 * to provide ad-free, high-bitrate audio streams directly to ExoPlayer.
 */
class InvidiousStreamResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    // Primary resilient instances with automatic round-robin / failover
    private val instances = listOf(
        "https://yt.chocolatemoo53.com",
        "https://invidious.privacydev.net",
        "https://invidious.jing.rocks",
        "https://iv.melmac.space",
        "https://invidious.drgns.space"
    )

    suspend fun resolveStream(videoId: String): StreamInfo = withContext(Dispatchers.IO) {
        var lastError: Exception? = null

        for (instance in instances) {
            try {
                // Strategy 1: Direct proxied latest_version itag=140 (128kbps AAC audio/mp4)
                // Resolve redirects ahead of time so ExoPlayer connects directly to media CDN without hop latency
                val directUrl = "$instance/latest_version?id=$videoId&itag=140&local=true"
                val resolvedDirectUrl = resolveDirectStreamUrl(directUrl)
                if (resolvedDirectUrl != null) {
                    return@withContext StreamInfo(
                        uri = Uri.parse(resolvedDirectUrl),
                        mimeType = "audio/mp4",
                        expiresAtEpochMs = System.currentTimeMillis() + 4 * 3600 * 1000L
                    )
                }

                // Strategy 2: Invidious video metadata API with adaptive formats extraction
                val apiUrl = "$instance/api/v1/videos/$videoId"
                val apiRequest = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()

                client.newCall(apiRequest).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyString = response.body?.string() ?: return@use
                    val json = JSONObject(bodyString)
                    val lengthSeconds = json.optLong("lengthSeconds", 0L)
                    val resolvedDurationMs = if (lengthSeconds > 0L) lengthSeconds * 1000L else null
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats") ?: return@use

                    var bestAudioUrl: String? = null
                    var bestMimeType = "audio/mp4"

                    for (i in 0 until adaptiveFormats.length()) {
                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                        val type = fmt.optString("type")
                        val url = fmt.optString("url")

                        if (type.contains("audio/mp4") && url.isNotBlank()) {
                            bestAudioUrl = url
                            bestMimeType = "audio/mp4"
                            break
                        } else if (type.contains("audio/") && bestAudioUrl == null && url.isNotBlank()) {
                            bestAudioUrl = url
                            bestMimeType = if (type.contains("webm")) "audio/webm" else "audio/mp4"
                        }
                    }

                    if (bestAudioUrl != null) {
                        val rawUrl = if (bestAudioUrl.startsWith("/")) {
                            "$instance$bestAudioUrl"
                        } else {
                            if (!bestAudioUrl.contains("local=true") && !bestAudioUrl.contains("googlevideo.com")) {
                                "$instance/latest_version?id=$videoId&itag=140&local=true"
                            } else {
                                bestAudioUrl
                            }
                        }

                        val finalResolvedUrl = resolveDirectStreamUrl(rawUrl) ?: rawUrl

                        return@withContext StreamInfo(
                            uri = Uri.parse(finalResolvedUrl),
                            mimeType = bestMimeType,
                            durationMs = resolvedDurationMs,
                            expiresAtEpochMs = System.currentTimeMillis() + 4 * 3600 * 1000L
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        // If all remote instances are unreachable (e.g. offline airplane mode),
        // raise IOException so caller can trigger offline cache / fallback
        throw lastError ?: IOException("Failed to resolve audio stream for track: $videoId")
    }

    private fun resolveDirectStreamUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Range", "bytes=0-1024")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    // Following redirects extracts the final CDN / companion video playback URL
                    response.request.url.toString()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
    }
}
