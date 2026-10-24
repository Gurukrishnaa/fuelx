package com.fuelx.data

import com.fuelx.model.LyricLine
import com.fuelx.model.Track

object MockTracks {
    val sampleTracks = listOf(
        Track(
            id = "track_1",
            title = "SoundHelix Melody One",
            artist = "Aura Bloom",
            album = "Neon Horizons",
            durationMs = 372000L,
            streamUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
        ),
        Track(
            id = "track_2",
            title = "SoundHelix Melody Two",
            artist = "Jorma Kaukonen",
            album = "Subliminal",
            durationMs = 423000L,
            streamUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
        ),
        Track(
            id = "track_3",
            title = "SoundHelix Melody Three",
            artist = "Jan Morgenstern",
            album = "Future Retro",
            durationMs = 356000L,
            streamUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
        ),
        Track(
            id = "track_4",
            title = "SoundHelix Melody Four",
            artist = "SoundHelix",
            album = "Electro Harmonies",
            durationMs = 502000L,
            streamUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
        ),
        Track(
            id = "track_5",
            title = "SoundHelix Melody Eight",
            artist = "SoundHelix",
            album = "Synth Explorations",
            durationMs = 498000L,
            streamUri = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3"
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

    val lrcCatalog = mapOf(
        "track_1" to """
            [ti:SoundHelix Melody One]
            [ar:Aura Bloom]
            [al:Neon Horizons]
            [00:00.00][Instrumental Intro]
            [00:08.50]<00:08.50>Walking <00:09.50>through <00:10.20>the <00:10.80>quiet <00:12.00>midnight <00:14.00>streets
            [00:18.20]<00:18.20>Neon <00:19.40>shadows <00:21.00>flickering <00:23.00>to <00:24.00>the <00:25.50>beat
            [00:29.00]<00:29.00>Echoes <00:31.00>in <00:32.00>the <00:33.00>air, <00:35.00>a <00:36.00>distant <00:38.00>sound
            [00:41.50]<00:41.50>Gravity <00:44.00>is <00:45.50>fading <00:48.00>from <00:49.50>the <00:51.00>ground
            [00:54.00]<00:54.00>Hear <00:55.50>the <00:57.00>pulse, <00:59.00>we're <01:01.00>alive <01:04.00>tonight
            [01:08.20]<01:08.20>Running <01:10.50>fast <01:12.50>under <01:15.00>electric <01:18.00>sky
            [01:22.00]<01:22.00>Lost <01:24.00>inside <01:26.50>the <01:28.00>rhythm, <01:31.00>let <01:33.00>it <01:35.00>guide
            [01:38.50]<01:38.50>Forever <01:41.00>caught <01:43.50>in <01:45.00>the <01:47.00>midnight <01:50.00>ride
        """.trimIndent(),

        "track_2" to """
            [ti:SoundHelix Melody Two]
            [ar:Jorma Kaukonen]
            [00:00.00]Atmospheric synth notes resonate through the city
            [00:15.00]Basslines hum beneath the glowing pavement
            [00:30.00]Searching for light in synthetic horizons
            [00:48.00]உன்னோடு வாழும் இந்த இசை அலைகள் (Living with you in these music waves)
            [01:05.00]Melody floating softly through the rain
            [01:25.00]Rebuilt from sound, breathing once again
        """.trimIndent(),

        "track_3" to """
            [ti:SoundHelix Melody Three]
            [ar:Jan Morgenstern]
            [00:02.00]Warm chords drifting through the morning air
            [00:14.00]Harmonies intertwining everywhere
            [00:28.00]Gentle beats moving with the flow
            [00:45.00]Feel the golden melody grow
            [01:00.00]Lost in time where colors glow
        """.trimIndent(),

        "track_4" to """
            [ti:SoundHelix Melody Four]
            [ar:SoundHelix]
            [00:00.00][Instrumental track]
        """.trimIndent(),

        "track_5" to """
            These are plain unsynchronized lyrics.
            No timestamps are present in this text.
            The music rolls forward freely while these words are read.
            Pure poetry in motion.
        """.trimIndent()
    )
}
