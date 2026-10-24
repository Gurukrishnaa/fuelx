package com.fuelx.data

import com.fuelx.model.LyricLine
import com.fuelx.model.Track

object MockTracks {
    val sampleTracks = listOf(
        Track(
            id = "track_1",
            title = "Dethroned (Chillwave)",
            artist = "Aura Bloom",
            album = "Neon Horizons",
            durationMs = 214000L,
            streamUri = "https://storage.googleapis.com/exoplayer-test-media-1/mp3/dethroned.mp3"
        ),
        Track(
            id = "track_2",
            title = "Tears of Steel Soundtrack",
            artist = "Jorma Kaukonen",
            album = "Subliminal",
            durationMs = 186000L,
            streamUri = "https://storage.googleapis.com/exoplayer-test-media-1/mp3/tears-of-steel-soundtrack.mp3"
        ),
        Track(
            id = "track_3",
            title = "Big Buck Bunny Acoustic",
            artist = "Jan Morgenstern",
            album = "Future Retro",
            durationMs = 245000L,
            streamUri = "https://storage.googleapis.com/exoplayer-test-media-1/mp3/bbb_audio.mp3"
        ),
        Track(
            id = "track_4",
            title = "SoundHelix Melody One",
            artist = "SoundHelix",
            album = "Electro Harmonies",
            durationMs = 372000L,
            streamUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        ),
        Track(
            id = "track_5",
            title = "SoundHelix Melody Two",
            artist = "SoundHelix",
            album = "Synth Explorations",
            durationMs = 423000L,
            streamUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
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
