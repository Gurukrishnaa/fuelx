package com.fuelx.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val streamUri: String? = null
) {
    val formattedDuration: String?
        get() {
            val dur = durationMs ?: return null
            if (dur <= 0L) return null
            val totalSeconds = dur / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }
}
