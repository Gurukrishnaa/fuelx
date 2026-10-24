# FuelX — Implementation Plan

> **Goal:** Build a lightweight Android music player with a custom UI, streaming-provider abstraction, background playback, synchronized lyrics, favorites, and history.
>
> **Project name:** FuelX  
> **Platform:** Android  
> **Primary stack:** Kotlin, Jetpack Compose, AndroidX Media3  
> **Approach:** Start with a small, testable app; measure size and memory before optimizing.

---

## 1. Product goals

### Must have (v1)

- Custom dark UI built independently of LastWave's screens.
- Search and browse music from a streaming provider whose access method and terms permit the intended use.
- Playback, pause, seek, next/previous, queue, shuffle, and repeat.
- Background playback with notification, lock-screen, headset, and Bluetooth controls.
- Mini-player and full now-playing screen.
- Lyrics lookup, timestamped lyric parsing, and current-line highlighting.
- Favorites and recently played.
- Clear loading, empty, offline, unavailable-track, and error states.

### Explicitly out of scope for v1

- Social feed or profiles.
- AI recommendations.
- Audio visualizers.
- Custom equalizer/DSP.
- User accounts unless the selected provider requires them.
- Offline copies of streaming tracks unless the provider explicitly permits downloads and offline playback.
- A custom backend unless a concrete requirement emerges.

## 2. Important constraints

1. **Streaming access is a separate dependency from the player.** YouTube's official Data API is not a general-purpose raw-audio streaming API. Do not assume that search access grants audio playback rights.
2. Do not implement ad blocking, stream extraction, or circumvention of a provider's access controls. Before adopting an unofficial connector, review its code, current reliability, applicable terms, and legal implications.
3. Begin development with local audio files or a small catalog of openly licensed test streams. Replace the test provider only after a permitted production source is selected.
4. LastWave Native is GPL-3.0. If you fork it, modify it, or incorporate code from it, comply with applicable GPL terms, including source and notice obligations when distributing a covered derivative. A new UI does not by itself remove those obligations. Independently written code inspired only by general ideas is different from copying or adapting implementation.
5. Check licenses for all dependencies, artwork, fonts, music, and lyrics. Free listening does not automatically grant redistribution or commercial-use rights.

## 3. Architecture

Use a single Android application module initially. Keep boundaries clear without introducing a large framework.

```text
app/src/main/java/com/fuelx/
├── MainActivity.kt
├── model/
│   ├── Track.kt
│   ├── StreamInfo.kt
│   ├── LyricLine.kt
│   └── PlaybackUiState.kt
├── data/
│   ├── MusicRepository.kt
│   ├── MusicProvider.kt
│   ├── DemoMusicProvider.kt
│   ├── LyricsRepository.kt
│   └── FavoritesRepository.kt
├── playback/
│   ├── PlaybackService.kt
│   ├── PlayerController.kt
│   └── PlaybackStateMapper.kt
├── ui/
│   ├── navigation/
│   ├── home/
│   ├── search/
│   ├── player/
│   ├── lyrics/
│   ├── library/
│   ├── settings/
│   ├── components/
│   └── theme/
└── util/
    ├── LrcParser.kt
    └── Formatters.kt
```

Suggested responsibility flow:

```text
Compose screen
    ↓ events / observes state
ViewModel
    ↓
Repository interface
    ├── Music provider adapter
    ├── Lyrics repository
    └── Local persistence
    ↓
Media3 Player + MediaSessionService
```

Do not let Composables make network requests directly. Keep provider-specific code out of the UI and playback screen.

## 4. Data models

Start with small models. Add fields only when a real provider requires them.

```kotlin
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null
)

data class StreamInfo(
    val uri: Uri,
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null
)

data class LyricLine(
    val timeMs: Long,
    val text: String
)
```

A stream URI should come from the selected provider and must be valid for the intended playback use. Avoid logging signed URLs or tokens.

## 5. Provider abstraction

Keep the provider replaceable. Implement the demo/local provider first.

```kotlin
interface MusicProvider {
    suspend fun searchTracks(query: String): List<Track>
    suspend fun getTrack(trackId: String): Track?
    suspend fun getStream(trackId: String): StreamInfo
}
```

Wrap it with a repository if that makes caching and error handling clearer:

```kotlin
interface MusicRepository {
    suspend fun search(query: String): Result<List<Track>>
    suspend fun resolveStream(trackId: String): Result<StreamInfo>
}
```

Rules:
- Use coroutines for asynchronous work.
- Add timeouts and bounded retries only where appropriate.
- Model unavailable tracks and expired streams as explicit errors.
- Do not retry indefinitely.
- Cache metadata only when allowed and useful.
- Do not cache or redistribute stream URLs beyond the provider's permitted behavior.

## 6. Dependencies

Use Android Studio's current stable compatible versions and a Gradle version catalog. Avoid copying stale version numbers from tutorials.

Core dependencies:
- Jetpack Compose UI and Material 3.
- AndroidX Activity Compose.
- Lifecycle ViewModel Compose and lifecycle runtime Compose.
- `androidx.media3:media3-exoplayer`.
- `androidx.media3:media3-session`.
- Kotlin Coroutines Android.
- Kotlin serialization only if needed for JSON.
- One HTTP client if the selected API needs it.
- One artwork loader, such as Coil Compose, if needed.

Add later, only when needed:
- Room for persistent favorites/history.
- DataStore for settings.
- A navigation library if screen navigation grows.
- A dependency-injection library only if manual construction becomes cumbersome.

Avoid duplicate HTTP clients, multiple image-loading libraries, analytics SDKs, crash-reporting SDKs, and Firebase unless there is a clear requirement.

Illustrative dependency aliases (define them in `gradle/libs.versions.toml` first):

```kotlin
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)

    implementation(libs.kotlinx.coroutines.android)
    // Add serialization, networking, and Coil only if used.
}
```

## 7. Playback implementation

Use Media3 ExoPlayer with a `MediaSession` hosted by a `MediaSessionService`.

### Playback service responsibilities

- Own the ExoPlayer instance.
- Create and expose the MediaSession.
- Maintain the queue and playback state.
- Handle audio focus and audio interruptions.
- Expose system media controls.
- Release resources correctly when playback is no longer needed.

### UI responsibilities

- Connect to the service using a MediaController.
- Display the current item, progress, buffering, errors, and play state.
- Send play/pause/seek/next/previous/queue commands through the controller.
- Observe state without creating a new player for every screen recomposition.

### Manifest and service checklist

Follow the current Media3 documentation for the selected dependency version:
- Declare the playback service.
- Add the required media playback foreground-service permission and service type.
- Add notification permission handling where required by Android version.
- Declare only the permissions actually needed.
- Test lock screen, notification, wired headset, Bluetooth, calls, and audio-focus changes on a physical device.

Do not start an always-running service merely to poll for music or lyrics. The playback service should exist for actual playback.

## 8. Lyrics implementation

Use a lyrics provider whose API terms permit the intended app usage. LRCLIB may be evaluated as a candidate; verify its current API, availability, and terms before release.

Flow:
1. When a track becomes current, request lyrics using available title/artist/album/duration metadata.
2. Show cached lyrics immediately if a valid local cache exists.
3. Parse timestamped LRC lines into `LyricLine(timeMs, text)`.
4. Update the active line from the player's current playback position.
5. Throttle UI updates to a reasonable cadence; do not make network calls on every progress tick.
6. Cancel or ignore stale requests when the track changes.
7. Handle instrumental, missing, plain-text, and malformed lyric results.
8. Do not claim lyrics are synchronized when timestamps are absent.

LRC parser test cases:
- One timestamp per line.
- Multiple timestamps on a line.
- Empty lines and metadata tags.
- Invalid timestamps.
- Unicode and Tamil text.
- Lines with identical timestamps.
- Missing final newline.

## 9. UI design system

Use a restrained dark theme and a small reusable component set.

### Main screens

**Home**
- Search field.
- Recently played list.
- Small set of provider-backed recommendations only if the provider supports them.
- Persistent mini-player.

**Search**
- Debounced input.
- Loading, result, empty, offline, and error states.
- Small artwork thumbnails.
- Paging only if the provider requires it.

**Now Playing**
- Artwork, title, artist, progress.
- Play/pause, next/previous.
- Queue, shuffle, repeat.
- Lyrics entry point.
- Optional sleep timer.

**Lyrics**
- Scrollable lyric lines.
- Highlight current line.
- Optional tap-to-seek only when timestamps exist.
- Accessible text sizes and contrast.

**Library**
- Favorites.
- Recently played.
- Local tracks only if local-library support is included.

### UI performance rules

- Use stable keys for lists.
- Avoid storing duplicate copies of the same state in several ViewModels.
- Keep expensive work out of Composables.
- Load artwork asynchronously at display-appropriate sizes.
- Bound artwork and metadata caches.
- Avoid continuously animated backgrounds and visualizers.
- Test scrolling and recomposition with release-like builds.

## 10. Persistence

Start with in-memory state while prototyping. Then choose:
- DataStore for small preferences (theme, repeat/shuffle preferences, last selected tab).
- Room for favorites and recent history if persistence is needed.

Do not add Room merely to store a handful of settings. Store only the metadata needed for the feature. Respect provider caching and retention requirements.

## 11. Build optimization

After the core app works:
- Enable R8 for release.
- Enable resource shrinking where supported by the chosen Android Gradle Plugin.
- Remove unused dependencies and resources.
- Avoid unnecessary native libraries and bundled assets.
- Use app bundles for Play distribution and inspect device-specific APKs.
- Compare release builds before and after each optimization.
- Keep debug builds easy to inspect; optimize the release build, not at the expense of debuggability.

Do not add aggressive ProGuard rules without evidence. Confirm that reflection, serialization, and Media3 continue to work in minified release builds.

## 12. Size and memory targets

These are **targets to validate**, not guaranteed results:

| Metric | Initial target |
|---|---:|
| Universal release APK | Aim for 10–20 MB |
| Installed footprint | Aim for 20–45 MB before accumulated cache |
| Active RAM | Aim for 70–150 MB on a representative device |
| Cold startup | Measure; establish a baseline before optimizing |
| Idle background work | No periodic polling when not needed |
| Artwork cache | Bounded; choose and measure a suitable cap |

A universal APK can be larger than an ABI-specific APK. Actual size and RAM depend on Android version, dependencies, architecture, codecs, artwork, and provider implementation. Do not promise a 5 MB build before measuring.

## 13. Implementation milestones

### Milestone 1 — Project and UI shell
- Create a Kotlin Android project with Compose.
- Set package name and minimum SDK based on the target-device decision.
- Implement theme, navigation, Home, Search, Now Playing, and Lyrics placeholder screens.
- Add preview data and loading/error/empty states.
- Confirm the app builds and launches.

**Done when:** the UI is navigable with mock tracks and no streaming dependency.

### Milestone 2 — Playback proof of concept
- Add Media3 ExoPlayer and MediaSessionService.
- Play a local audio file or openly licensed test stream.
- Add mini-player and playback controls.
- Verify background and notification controls.

**Done when:** playback is reliable through screen lock and navigation.

### Milestone 3 — Provider boundary
- Define `MusicProvider`.
- Implement a demo provider.
- Add search and stream-resolution error states.
- Evaluate a permitted production provider and document its constraints.

**Done when:** the UI and player don't depend on a specific provider's API models.

### Milestone 4 — Lyrics
- Implement the lyrics repository and LRC parser.
- Add current-line highlighting.
- Handle missing and unsynchronized lyrics.
- Test Unicode and track changes.

**Done when:** lyric state follows playback position without excessive polling or stale results.

### Milestone 5 — Library and persistence
- Add favorites and history.
- Introduce DataStore and/or Room only as needed.
- Add bounded metadata/lyrics caching consistent with provider terms.

**Done when:** favorites and history survive process restarts.

### Milestone 6 — Production streaming integration
- Integrate the selected permitted provider.
- Handle expiry, rate limits, network loss, and unavailable tracks.
- Verify terms, attribution, artwork use, lyrics rights, and distribution constraints.

**Done when:** a fresh install can search and play supported catalog items reliably.

### Milestone 7 — Optimization and release
- Capture APK size and memory baselines.
- Enable R8 and resource shrinking.
- Test minified builds.
- Profile startup, scrolling, playback, and battery usage.
- Test on at least one physical device and a slower network.

**Done when:** metrics are measured, crashes are resolved, and all release/licensing checks pass.

## 14. Test checklist

- [ ] App launches offline.
- [ ] Search debouncing and cancellation work.
- [ ] Empty, slow, failed, and rate-limited requests have useful UI states.
- [ ] Play, pause, seek, queue, next, previous, shuffle, and repeat work.
- [ ] Playback survives navigation and screen lock.
- [ ] Calls and audio-focus interruptions are handled.
- [ ] Bluetooth and notification controls work.
- [ ] Expired streams recover gracefully.
- [ ] Lyrics update after seeking and track changes.
- [ ] Favorites/history persist if enabled.
- [ ] Artwork and lyrics caches are bounded.
- [ ] No network requests occur on every UI recomposition or playback tick.
- [ ] Minified release build works.
- [ ] APK size, active RAM, startup time, and battery behavior are recorded.
- [ ] Third-party code, music, artwork, and lyrics comply with their applicable licenses and terms.

## 15. First coding session

Do these tasks in order:

1. Create the Android Studio project using Kotlin and Jetpack Compose.
2. Create the package structure above.
3. Implement `Track`, mock search results, and a dark Material 3 theme.
4. Build Home, Search, Now Playing, and Lyrics placeholder screens.
5. Run the app on an emulator or device and verify navigation.
6. Add Media3 and play one local/openly licensed test track.
7. Only then investigate and integrate the production streaming provider.

**Do not start by copying the entire original project or adding every feature.** First establish a small, working app, then add one subsystem at a time and measure what each adds.
