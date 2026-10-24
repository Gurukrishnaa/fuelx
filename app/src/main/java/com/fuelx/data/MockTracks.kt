package com.fuelx.data

import com.fuelx.model.LyricLine
import com.fuelx.model.Track

object MockTracks {
    val sampleTracks = listOf(
        Track(
            id = "track_1",
            title = "Midnight City Lights",
            artist = "Aura Bloom",
            album = "Neon Horizons",
            durationMs = 214000L
        ),
        Track(
            id = "track_2",
            title = "Velvet Dreams",
            artist = "Echo Theory",
            album = "Subliminal",
            durationMs = 186000L
        ),
        Track(
            id = "track_3",
            title = "Cybernetic Pulse",
            artist = "Kavinsky Waves",
            album = "Future Retro",
            durationMs = 245000L
        ),
        Track(
            id = "track_4",
            title = "Silent Drift",
            artist = "Solaris",
            album = "Deep Space",
            durationMs = 198000L
        ),
        Track(
            id = "track_5",
            title = "Afterglow Horizon",
            artist = "Krono & Nova",
            album = "Euphoria",
            durationMs = 230000L
        )
    )

    val sampleLyrics = listOf(
        LyricLine(0L, "[Instrumental Intro]"),
        LyricLine(12000L, "Walking through the quiet midnight streets"),
        LyricLine(24000L, "Neon shadows flickering to the beat"),
        LyricLine(36000L, "Echoes in the air, a distant sound"),
        LyricLine(48000L, "Gravity is fading from the ground"),
        LyricLine(62000L, "Hear the pulse, we're alive tonight"),
        LyricLine(76000L, "Running fast under electric sky"),
        LyricLine(92000L, "Lost inside the rhythm, let it guide"),
        LyricLine(110000L, "Forever caught in the midnight ride")
    )
}
