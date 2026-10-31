# FuelX

A minimalist, high-performance Android music streaming application built with modern Android development standards.

FuelX delivers an ad-free, uninterrupted listening experience with synchronized rolling lyrics, seamless background playback, offline caching, and a pure OLED black aesthetic.

---

## Features

### Audio Playback
- Continuous background streaming powered by AndroidX Media3 (ExoPlayer).
- System-level MediaSession integration with lockscreen controls, notification shade transport actions, and Bluetooth headset integration.
- Intelligent queue management with shuffle, repeat, and play next capabilities.
- Audio focus management that automatically pauses on incoming calls and resumes on disconnect.

### Synchronized Lyrics
- Real-time karaoke-style lyrics with word-by-word highlight transitions.
- Automatic music gap detection: Musical interludes, intros, and instrumental breaks are indicated with typographic musical markers.
- Tap-to-seek functionality: Tap any lyric line or word to jump directly to that point in the song.
- Full offline fallback for plain text lyrics and instrumental tracks.

### User Interface and Performance
- Pure OLED dark theme (`#0A0A0B`) designed for battery efficiency and high contrast.
- Custom liquid glass components with subtle borders and frosted backdrops.
- 60/120Hz smooth list rendering:
  - Hardware-accelerated bitmap caching with Coil (25% memory pool and 100MB disk cache).
  - Zero-allocation brushes and shapes using Compose state memory caching.
  - Composable item recycling via explicit keys and content types.

### Music Discovery and Library
- Multi-language discovery: Filter and explore music across English, Hindi, Tamil, Telugu, Malayalam, Punjabi, Kannada, and Bengali.
- Instant search with query history management.
- Offline downloads: Save tracks locally to your device storage for offline playback.
- Custom playlists, favorites, and automatic listening history tracking.

---

## Tech Stack

- **Language:** Kotlin 2.0+
- **UI Toolkit:** Jetpack Compose, Material 3
- **Media Engine:** AndroidX Media3 (ExoPlayer, MediaSession, MediaNotification)
- **Image Loading:** Coil 2.6 (Custom memory and disk caches)
- **Networking:** OkHttp 4 (Connection pooling, custom headers, and fallback stream resolution)
- **Concurrency:** Kotlin Coroutines and StateFlow
- **Architecture:** Clean Architecture with Unidirectional Data Flow (UDF)

---

## Project Structure

```
app/src/main/java/com/fuelx/
├── data/
│   ├── lyrics/          # LRCLIB API client and lyrics fetcher
│   ├── youtube/         # Audio stream extractors and Invidious resolvers
│   ├── MusicRepository  # Music search, trending catalogs, and caching
│   ├── LibraryRepository# Playlists, favorites, and playback history storage
│   └── TrackDownloadManager # Background download queue and local file manager
├── model/               # Immutable domain entities (Track, Playlist, LyricLine)
├── playback/            # MediaSessionService, ExoPlayer controller, notification manager
├── ui/
│   ├── brand/           # Brand marks, wordmarks, and vector assets
│   ├── components/      # MiniPlayer, Artwork, Navigation, GlassSnackbar, Dialogs
│   ├── home/            # Discovery screen, shelves, genre rows, recent items
│   ├── search/          # Search screen with live queries and history
│   ├── library/         # Library tabs (Playlists, Favorites, Downloads, History)
│   ├── lyrics/          # Fullscreen synchronized lyrics reader
│   ├── player/          # Expandable Now Playing screen with queue and lyrics
│   └── theme/           # Color tokens, typography, surfaces, and theme wrapper
└── util/                # Network monitor, LRC parser, and string utilities
```

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer
- JDK 17
- Android SDK 34 (API level 34)
- Minimum supported Android version: Android 8.0 (API level 26)

### Build and Run

1. Clone the repository:
   ```bash
   git clone https://github.com/Gurukrishnaa/fuelx.git
   cd fuelx
   ```

2. Open the project in Android Studio, or compile using the Gradle wrapper:
   ```bash
   # Build the debug APK
   ./gradlew assembleDebug
   ```

3. Locate the output APK:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

4. Install directly to a connected Android device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Permissions

FuelX declares only standard permissions required for core audio streaming and background playback:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.INTERNET` | Stream audio tracks and download album art and synchronized lyrics. |
| `android.permission.ACCESS_NETWORK_STATE` | Detect network changes to pause or resume buffering gracefully. |
| `android.permission.FOREGROUND_SERVICE` | Maintain audio playback when the application is minimized. |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ requirement for foreground media services. |
| `android.permission.POST_NOTIFICATIONS` | Android 13+ requirement for displaying the system media playback notification. |

---

## Disclaimer

This software is developed strictly for educational and personal research purposes. FuelX does not host audio files. All streams are resolved through third-party public instances. All rights and trademarks belong to their respective owners.

---

## License

This project is open source and available under the [MIT License](LICENSE).

