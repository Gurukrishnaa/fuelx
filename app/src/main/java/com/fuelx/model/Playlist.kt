package com.fuelx.model

data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<Track> = emptyList()
) {
    val trackCount: Int
        get() = tracks.size

    val coverArtwork: String?
        get() = tracks.firstOrNull()?.displayArtwork
}

