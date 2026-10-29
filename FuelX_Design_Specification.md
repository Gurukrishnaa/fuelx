# FuelX Design Specification
### Exaggerated Minimalism, dark only, built for Jetpack Compose (Android 11+)

---

## 1. Design Philosophy

**One sentence:** *Remove everything except the music, then make what remains impossible to ignore.*

### 1.1 The five principles
1. **Art is the interface.** Album artwork is the only decoration. Everything else is type, space and one accent color.
2. **Exaggerate the few.** Fewer elements, pushed further: huge titles, huge margins, one huge play button.
3. **Color follows the song.** Each track tints the screen. The app never looks the same twice.
4. **Motion has weight.** Springs, not linear tweens. Things settle; they don't snap.
5. **Instant is a feature.** Every touch responds within one frame. Loading shows structure (skeletons), never spinners on blank screens.

### 1.2 What FuelX never does
- No borders on cards (use tonal surfaces).
- No drop shadows on text.
- No more than one accent color on screen at once.
- No gradients that fight the artwork.
- No icon without a purpose; no label under an icon unless it is navigation.

### 1.3 Platform constraints
- **Dark only.** No light theme, no `isSystemInDarkTheme()`.
- **Android 11 (API 30) is the floor.** No Material You dynamic color. `RenderEffect` blur is Android 12+, so every blur has a gradient fallback.
- All colors, type and spacing come from tokens. Never hardcode a value in a screen.

---

## 2. Color System

### 2.1 Base palette (fixed)

| Token | Hex | Use |
|---|---|---|
| `bg` | `#0A0A0B` | App background (near OLED black) |
| `surface1` | `#121214` | Cards, list rows (resting) |
| `surface2` | `#1A1A1D` | Elevated cards, sheets |
| `surface3` | `#232327` | Pressed state, chips, search field |
| `outline` | `#2C2C31` | Hairline dividers only (1dp, used rarely) |
| `textPrimary` | `#F4F4EF` | Titles, key content (warm white) |
| `textSecondary` | `#B4B4B0` | Artist names, subtitles |
| `textTertiary` | `#8A8A8F` | Captions, timestamps (minimum allowed) |
| `scrim` | `#000000` at 60% | Behind sheets and dialogs |
| `error` | `#FF6B6B` | Errors only |

All text colors pass WCAG AA (4.5:1) on `bg` and `surface1`.

### 2.2 Accent

| Token | Value | Notes |
|---|---|---|
| `accentDefault` | `#D4FF4F` "Volt" | Used when no artwork color is available (home, search, settings) |
| `onAccent` | `#0A0A0B` | Text/icons on accent fills |
| `accentTrack` | derived | Extracted from the current artwork, see 2.3 |

### 2.3 Dynamic accent from artwork
1. Load artwork at 544x544 via Coil.
2. Extract with `androidx.palette` on a background dispatcher. Preference order: `vibrantSwatch`, `lightVibrantSwatch`, `dominantSwatch`.
3. **Normalize** the result so it always looks good on dark:
   - Convert to HSL.
   - Clamp saturation to `0.55 to 0.85`.
   - Clamp lightness to `0.55 to 0.72`.
   - If extraction fails or artwork is grayscale, use `accentDefault`.
4. Cache the result by `trackId` in an `LruCache<String, Int>(200)`.
5. Animate between accents with `animateColorAsState(tween(600))`.

### 2.4 Now Playing background
```
Top    0%   accentTrack @ 55% alpha
Mid   60%   bg (#0A0A0B)
Bottom 100% bg (#0A0A0B)
```
Vertical gradient, with an optional 2% noise overlay (a tiled 64x64 PNG) to prevent banding on OLED. Mini player uses `surface2` with a 12% accent tint.

### 2.5 Color tokens in Kotlin
```kotlin
object FuelXColors {
    val Bg = Color(0xFF0A0A0B)
    val Surface1 = Color(0xFF121214)
    val Surface2 = Color(0xFF1A1A1D)
    val Surface3 = Color(0xFF232327)
    val Outline = Color(0xFF2C2C31)
    val TextPrimary = Color(0xFFF4F4EF)
    val TextSecondary = Color(0xFFB4B4B0)
    val TextTertiary = Color(0xFF8A8A8F)
    val Error = Color(0xFFFF6B6B)
    val AccentDefault = Color(0xFFD4FF4F)
    val OnAccent = Color(0xFF0A0A0B)
}

val FuelXColorScheme = darkColorScheme(
    background = FuelXColors.Bg,
    surface = FuelXColors.Surface1,
    surfaceVariant = FuelXColors.Surface2,
    onBackground = FuelXColors.TextPrimary,
    onSurface = FuelXColors.TextPrimary,
    onSurfaceVariant = FuelXColors.TextSecondary,
    primary = FuelXColors.AccentDefault,
    onPrimary = FuelXColors.OnAccent,
    error = FuelXColors.Error,
    outline = FuelXColors.Outline
)

val LocalAccent = compositionLocalOf { FuelXColors.AccentDefault }
```

---

## 3. Typography

### 3.1 Typefaces
| Role | Font | Why |
|---|---|---|
| Display | **Space Grotesk** (Bold 700, Medium 500) | Geometric, confident, distinctive at large sizes |
| Text | **Inter** (Regular 400, Medium 500, SemiBold 600) | Neutral, excellent small-size legibility |

Bundle the font files in `res/font/` (don't rely on downloadable fonts, since they can flash a fallback at first launch). Both are free under the SIL Open Font License.

### 3.2 Type scale

| Style | Font | Size / Line | Weight | Tracking | Use |
|---|---|---|---|---|---|
| `displayXL` | Space Grotesk | 56 / 56sp | 700 | -2% | Home greeting, empty states |
| `displayL` | Space Grotesk | 44 / 46sp | 700 | -1.5% | **Now Playing track title** |
| `headline` | Space Grotesk | 28 / 32sp | 700 | -1% | Section titles |
| `title` | Inter | 20 / 26sp | 600 | 0 | Card titles, sheet titles |
| `bodyL` | Inter | 16 / 24sp | 400 | 0 | Primary body, list titles |
| `body` | Inter | 14 / 20sp | 400 | 0 | Secondary text |
| `label` | Inter | 13 / 16sp | 600 | +4% | Buttons, chips, small caps labels |
| `caption` | Inter | 12 / 16sp | 500 | +2% | Timestamps, metadata |
| `lyricActive` | Space Grotesk | 34 / 42sp | 700 | -1% | Active lyric line |
| `lyricIdle` | Space Grotesk | 34 / 42sp | 700 | -1% | Other lyric lines (alpha 28%) |

### 3.3 Typography rules
- **Contrast of scale is the style.** On Now Playing, title (44sp) vs artist (16sp) is a 2.75x jump. Keep it.
- Titles: max 2 lines, `TextOverflow.Ellipsis`. If a title is longer than 28 characters, step down to `headline` (28sp) so it fits without clipping.
- Never use weights below 400 or above 700.
- Use `fontFeatureSettings = "tnum"` (tabular numbers) on all time labels so digits don't jitter while the timer ticks.
- Sentence case everywhere. ALL CAPS only for `label` section tags, with tracking.

```kotlin
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

val FuelXTypography = Typography(
    displayLarge = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 56.sp, lineHeight = 56.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.015).em),
    headlineMedium = TextStyle(SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(Inter, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(Inter, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
    labelSmall = TextStyle(Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em)
)
```

---

## 4. Spacing, Layout and Shape

### 4.1 Spacing scale (4dp grid)
| Token | dp | Typical use |
|---|---|---|
| `xxs` | 4 | Icon-to-label gap |
| `xs` | 8 | Tight stacks, chip gaps |
| `sm` | 12 | List row inner gaps |
| `md` | 16 | Default gap between related items |
| `lg` | 24 | **Screen horizontal margin**, card padding |
| `xl` | 32 | Between sections |
| `xxl` | 48 | Above a section headline, hero spacing |
| `xxxl` | 64 | Top of hero screens |

### 4.2 Layout rules
- **Horizontal margin: 24dp** on every screen. Artwork on Now Playing is the exception: 24dp margin, full remaining width.
- **Touch targets: minimum 48x48dp**, even if the icon is 24dp.
- **Vertical rhythm:** consecutive sections are separated by `xl` (32dp); a section headline sits `md` (16dp) above its content.
- **Safe areas:** edge-to-edge, content padded by `WindowInsets.systemBars`. Artwork may bleed behind the status bar on Now Playing.
- Content max width on tablets/foldables: 600dp, centered.

### 4.3 Shapes (corner radii)
| Token | dp | Use |
|---|---|---|
| `shapeXS` | 8 | Small thumbnails in lists |
| `shapeS` | 12 | Search field, snackbars |
| `shapeM` | 20 | Cards |
| `shapeL` | 28 | **Now Playing artwork** |
| `shapeXL` | 32 (top only) | Bottom sheets |
| `shapeFull` | 50% | Buttons, chips, play button, mini player |

### 4.4 Elevation
FuelX uses **tonal elevation instead of shadows**: higher surface means a lighter surface color (`surface1 < surface2 < surface3`). The only real shadow is the **artwork glow**:
- Color: `accentTrack` at 35% alpha
- Blur radius: 48dp, offset Y: 16dp
- Android 12+: draw with `Modifier.shadow(ambientColor, spotColor)` or a blurred duplicate
- Android 11: fall back to a radial gradient behind the art (accent 25% to transparent), no blur

---

## 5. Motion and Haptics

### 5.1 Principles
- Fast to start, slow to settle. Use the **emphasized** curve.
- Springs for anything the user drags or presses. Tweens for color and fades.
- Never animate more than 3 things at once on one screen.

### 5.2 Duration tokens
| Token | ms | Use |
|---|---|---|
| `instant` | 100 | Press feedback, icon swaps |
| `short` | 200 | Chip selection, fades |
| `medium` | 350 | Shared-element moves, sheet open |
| `long` | 600 | Accent color change between tracks |

### 5.3 Easing and springs
```kotlin
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

val PressSpring = spring<Float>(dampingRatio = 0.7f, stiffness = 500f)
val ArtworkSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 300f)
val SheetSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 400f)
```

### 5.4 Signature motions
| Motion | Spec |
|---|---|
| **Press feedback** | Any tappable scales to `0.96` with `PressSpring`, back to `1.0` on release. Never use the default ripple on large surfaces. |
| **Artwork play/pause** | Playing: scale `1.0`. Paused: scale `0.92`. `ArtworkSpring`. Glow alpha animates `35% to 15%`. |
| **Play button morph** | Play and pause glyphs morph via `AnimatedVectorDrawable` (200ms). The button itself pulses once to `1.06` on play. |
| **Mini to full player** | Shared element transition (350ms, emphasized): artwork scales and translates, title cross-fades into `displayL`, background gradient fades in from `0` to `55%` alpha. Fully reversible by dragging. |
| **Track change** | Artwork cross-fades 300ms. Title slides up 12dp while fading (200ms, staggered by 40ms after art). Accent color tweens 600ms. |
| **Swipe to skip** | Horizontal drag on artwork: translate with finger, rotate up to 4 degrees, release past 30% width to skip with a spring settle. |
| **Scrubber touch** | At rest: 3dp track. On touch: grows to 8dp (spring), thumb appears (12dp circle), time label scales to 1.1. Release reverses. |
| **List item appear** | First load only: fade + translate Y 16dp to 0, staggered 30ms per item, max 8 items. |
| **Shimmer** | Skeletons with a 1200ms linear shimmer at 6% white. Never show a spinner on an empty screen. |

### 5.5 Haptics
| Event | Feedback |
|---|---|
| Play / Pause | `HapticFeedbackType.LongPress` (light, once) |
| Next / Previous | `HapticFeedbackType.LongPress` |
| Scrubber grab | `HapticFeedbackType.TextHandleMove` |
| Scrubber ticks (every 10s of travel) | `HapticFeedbackType.TextHandleMove` |
| Add to favorites | `HapticFeedbackType.LongPress` plus the heart scales to `1.3` and back |
| Error / unplayable | `HapticFeedbackType.Reject` where available (API 30+) |

Respect system settings: skip haptics if touch feedback is off, and cut animation durations to 0 when the system animator duration scale is `0`.

---

## 6. Components

### 6.1 Primary play button
- Size: **88dp circle** on Now Playing, **44dp** on mini player.
- Fill: `accentTrack`. Glyph: `onAccent`, 40dp (Now Playing).
- Press: scale 0.94. Subtle outer ring: accent at 20% alpha, 6dp wider than the button, visible while playing.

### 6.2 Transport controls (Now Playing)
Row, centered, `xl` (32dp) gaps:
`Shuffle (24dp, tertiary)  Previous (36dp)  PLAY (88dp)  Next (36dp)  Repeat (24dp, tertiary)`
- Icons are **thin-stroke (1.75dp)**, rounded caps, from a single icon family (Phosphor Regular or Material Symbols Rounded, weight 300). Don't mix families.
- Active shuffle/repeat: icon becomes `accentTrack` with a 4dp dot underneath.

### 6.3 Scrubber
- Track: 3dp (8dp when held), `Surface3` background, `accentTrack` progress.
- Thumb: hidden at rest, 12dp circle on touch.
- Time labels: `caption`, tabular numbers, `textTertiary` left (elapsed), right-aligned (remaining as `-m:ss`).
- Hit area: 48dp tall even though the track is 3dp.
- Buffered range: `Surface3` lightened 8%, visible behind the progress.

### 6.4 Track row (lists)
```
[ 56dp art, shapeXS ]  12dp  [ Title  (bodyL, textPrimary, 1 line)   ]   [ ⋮ ]
                             [ Artist · 3:42 (body, textSecondary)    ]
```
- Row height: 72dp. Padding: 24dp horizontal.
- Currently playing row: title in `accentTrack`, a 3-bar animated equalizer replaces the duration.
- No dividers. Spacing alone separates rows.
- Duration only appears when known (never show `0:00`).

### 6.5 Cards (home)
- Large card: 280 x 360dp (art on top 280x280, `shapeM`; title `title`, artist `body` underneath).
- No borders, no shadows. Background `surface1`. Press scales to 0.97.
- Horizontal rows use `LazyRow` with `contentPadding = PaddingValues(horizontal = 24.dp)` and 16dp item gaps, with snap fling.

### 6.6 Mini player
- Height **64dp**, margin 12dp sides and 8dp above the navigation bar. `shapeFull`.
- Surface: `surface2` with accent 12% tint. Android 12+: optional blur 20dp.
- Contents: 44dp art (circular, slowly rotating while playing at 1 revolution per 20s, optional), title (`bodyL`, 1 line, marquee if long), 44dp play button, thin 2dp progress line along the bottom edge.
- Swipe left/right to skip. Swipe up or tap to expand. Swipe down on the full player to collapse.

### 6.7 Search field
- Height 56dp, `shapeFull`, `surface3`, no border. Leading search icon 24dp in `textTertiary`.
- Text: `bodyL`. Placeholder: "Search songs, artists" in `textTertiary`.
- Focus: background lifts to `surface2` and a 2dp `accentDefault` underline fades in at 200ms.

### 6.8 Chips
- Height 36dp, padding 16dp horizontal, `shapeFull`, `surface3`, `label` text.
- Selected: fill `textPrimary`, text `bg`. (A white chip on black is the strongest, simplest "selected" state.)

### 6.9 Bottom navigation
- Three destinations only: **Home, Search, Library**.
- Height 64dp + insets, background `bg` at 92% alpha (no blur on Android 11).
- Icon 24dp. Active: `textPrimary` icon plus a 4dp accent dot beneath. Inactive: `textTertiary`. **No text labels.**
- The mini player floats above it.

### 6.10 Bottom sheets (queue, track options)
- Top corners `shapeXL` (32dp), surface `surface2`, drag handle 36x4dp in `outline`.
- Scrim 60% black. Open with `SheetSpring`. Draggable to dismiss with velocity.

### 6.11 Snackbars and errors
- Floating, `shapeS`, `surface3`, text `body`. Max one line + one action.
- Playback errors: calm and actionable ("Couldn't load this song. Retry"). Never show raw exception text.

---

## 7. Screens

### 7.1 Home
```
 24dp margin
 ┌────────────────────────────────────┐
 │ (status bar)                       │
 │                                    │
 │ Good evening                       │  displayXL (or displayL on small screens)
 │                                    │  48dp
 │ Continue                           │  headline
 │ [ card ] [ card ] [ card ] →       │  LazyRow, snap
 │                                    │  32dp
 │ Made for you                       │  headline
 │ [ card ] [ card ] [ card ] →       │
 │                                    │  32dp
 │ Recently played                    │  headline
 │ ▢ Track row                        │
 │ ▢ Track row                        │
 │                                    │
 │  ( mini player )                   │
 │  ●Home      Search      Library    │
 └────────────────────────────────────┘
```
- Greeting by time of day. It's the one oversized text moment on Home.
- Maximum 3 sections above the fold-to-scroll. Everything else is reachable but never competes.
- Pull-to-refresh uses a thin accent line at the top instead of a spinner.

### 7.2 Search
- Search field pinned at the top. Below it, before typing: **Recent searches** (text rows with a clear action) and a few large genre tiles (2 columns, `shapeM`, each with a solid muted color and `title` text).
- While typing: results appear under the field with 250ms debounce. Skeleton rows while loading.
- Results grouped: Top result (large card with a play button), then Songs, then Artists.
- Empty result: `headline` "Nothing found", `body` suggestion, no illustration.

### 7.3 Now Playing (the hero)
```
 ┌────────────────────────────────────┐
 │ ▾ (collapse)            ⋯ (more)   │  48dp targets, transparent
 │                                    │  accent gradient behind
 │   ┌──────────────────────────┐     │
 │   │                          │     │
 │   │       ARTWORK            │     │  square, shapeL (28dp), glow
 │   │       (full width -48)   │     │
 │   │                          │     │
 │   └──────────────────────────┘     │  32dp
 │                                    │
 │ Track title in                     │  displayL, max 2 lines
 │ huge type                          │
 │ Artist name                        │  bodyL, textSecondary   (8dp above)
 │                                ♡   │  heart 28dp, right-aligned
 │                                    │  24dp
 │ ━━━━━━●─────────────────           │  scrubber
 │ 1:12                      -2:30    │  caption
 │                                    │  24dp
 │  ⤮     ⏮     (  ▶  )     ⏭     ⟳   │  transport
 │                                    │  32dp
 │ ─── swipe up: Lyrics · Queue ───   │  label, tertiary, subtle chevron
 └────────────────────────────────────┘
```
- Title and artist left-aligned (not centered). Left alignment looks more editorial and confident.
- Bottom edge peek: a 32dp "handle" area. Swipe up opens **Lyrics** (primary) with **Queue** as a second tab.
- Only 4 secondary actions exist: favorite, queue, lyrics, more (sleep timer, share, go to artist). Everything else is hidden.

### 7.4 Lyrics
- Full screen, same accent gradient, artwork shrinks to a 56dp thumbnail in the header.
- Active line: `lyricActive`, full white. Other lines: same size, `textPrimary` at 28% alpha. Active line changes with a 300ms alpha + scale (1.0 vs 0.96) transition.
- Auto-scroll keeps the active line at 35% from the top, with `animateScrollToItem` and a spring.
- Tap a line to seek there. Manual scroll pauses auto-scroll for 3 seconds.
- Fallback when no synced lyrics: plain text in `bodyL`, 1.6 line spacing.

### 7.5 Queue
- Bottom sheet or tab under lyrics. "Now playing" row pinned at the top, then "Up next".
- Drag handles on the right (reorder with `longPressDraggable`), swipe left to remove with an undo snackbar.

### 7.6 Library
- Segmented chips: **Favorites, Playlists, Recent, Downloads** (single row, horizontally scrollable).
- Favorites: track rows. Playlists: 2-column grid of `shapeM` covers (collage of the first 4 artworks).
- Empty state per tab: a single `headline` sentence and one accent button ("Find songs").

### 7.7 Splash and first launch
- Background `bg` (set in the XML theme to avoid a white flash).
- Wordmark "fuelx" centered in Space Grotesk Bold 40sp, with the accent dot as the "x" period. Fade in 400ms, then straight to Home. No onboarding carousel.

---

## 8. Iconography
- One family, consistent stroke: **Phosphor Regular** or **Material Symbols Rounded (weight 300)**.
- Sizes: 24dp standard, 28dp for emphasis (heart), 36dp for prev/next, 40dp for play glyph.
- Color: `textPrimary` (active), `textTertiary` (inactive). Accent only on state (favorited, shuffle on).
- Use vector drawables (`.xml`), never PNGs.

---

## 9. Notification and Lockscreen
- Media3 `MediaStyle` notification with: 544x544 artwork, title, artist, previous / play-pause / next.
- Small icon: monochrome vector.
- Notification color: `accentTrack` (via `MediaNotification` color extras, where supported).
- On Android 11 the controls appear in the **Quick Settings media card** and on the lock screen; that is expected behavior.

---

## 10. Smoothness and Performance Budget

### 10.1 Targets
| Metric | Target |
|---|---|
| Frame time | under 11ms on mid-range (90Hz), never over 16ms |
| Cold start to first frame | under 800ms |
| Tap to visual response | 1 frame (about 16ms) |
| Track change to artwork visible | under 150ms (prefetch next artwork) |
| Memory | Under 250MB steady state |

### 10.2 Compose rules
1. **Read fast-changing state as late as possible.** The playback position ticks every 200-500ms. Read it only inside the scrubber and time labels, passing a lambda (`positionProvider: () -> Long`) rather than a value, so the rest of Now Playing never recomposes.
2. Mark UI models `@Immutable` / `@Stable`. Use `ImmutableList` or `@Immutable` wrappers for lists passed to composables.
3. `LazyColumn` / `LazyRow` always get `key = { it.id }` and `contentType`.
4. Use `derivedStateOf` for computed values (for example "is title long").
5. Do animation work in `graphicsLayer { }` (scale, alpha, translation) so it runs in the draw phase and doesn't trigger recomposition.
6. Avoid `Modifier.blur` on lists. Use only on the mini player (Android 12+).
7. Hoist `remember`ed brushes and shapes; don't allocate objects inside draw lambdas.

### 10.3 Images
- Coil with `size(Size.ORIGINAL)` forbidden; always request the displayed size (list thumbs 168px, cards 560px, Now Playing 544px).
- Enable memory cache 25% of heap and a 100MB disk cache.
- Crossfade 200ms. Placeholder = solid `surface2` (not a spinner).
- Prefetch artwork for the next 2 tracks in the queue.
- Palette extraction runs on `Dispatchers.Default` and is cached per track.

### 10.4 Build and tooling
- Ship a **Baseline Profile** (it typically cuts cold start and first-scroll jank by 20-30%).
- R8 full mode, resource shrinking on in release builds.
- Test with **Macrobenchmark** (scroll Home, open/close Now Playing, skip tracks).
- Profile with Layout Inspector's recomposition counts: the Now Playing screen should show near-zero recompositions while a song plays other than the scrubber.
- Always evaluate smoothness on the **real Android 11 phone in a release build**. Debug builds are noticeably slower and the emulator isn't representative.

---

## 11. Accessibility
- Every icon-only button has a `contentDescription` ("Play", "Next track").
- Touch targets at least 48dp.
- Text contrast at least 4.5:1 (all tokens in section 2 comply).
- Support font scaling up to 200%: use `sp`, avoid fixed heights on text containers, and let title wrap to 2 lines.
- Scrubber exposes `Modifier.semantics { progressBarRangeInfo }` and supports `setProgress` for TalkBack.
- Reduce motion: when `ANIMATOR_DURATION_SCALE == 0`, skip rotation, parallax and shimmer; use simple fades.
- Never convey state by color alone (favorite also changes icon fill; shuffle shows a dot).

---

## 12. Brand Details
- **Name:** FuelX. Wordmark in lowercase "fuelx", Space Grotesk Bold.
- **App icon:** near-black rounded square (adaptive icon background `#0A0A0B`), a single Volt-lime (`#D4FF4F`) abstract mark: a bold "x" formed from two crossing audio-wave strokes. Monochrome themed icon variant included for Android 13+.
- **Voice and copy:** short, warm, lowercase-friendly. "Good evening", "Nothing found", "Couldn't load this song". No exclamation marks.

---

## 13. Implementation Order

1. **Foundation:** `FuelXTheme`, color/type/shape/spacing tokens, bundled fonts, dark-only window background, edge-to-edge.
2. **Primitives:** `PressableScale` modifier, `Shimmer`, `FuelXIconButton`, `PlayButton`, `Scrubber`.
3. **Now Playing:** gradient background, artwork + spring, typography, transport, scrubber.
4. **Mini player + shared-element expand.**
5. **Home:** greeting, card rows, track rows, skeleton loading.
6. **Search and Library.**
7. **Lyrics and Queue sheets.**
8. **Polish:** haptics, stagger animations, empty/error states.
9. **Performance pass:** Baseline Profile, recomposition audit, release-build test on the Android 11 phone.

### Reusable primitive: pressable scale
```kotlin
fun Modifier.pressableScale(
    onClick: () -> Unit,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = PressSpring,
        label = "press"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
```

### Reusable primitive: Now Playing background
```kotlin
@Composable
fun NowPlayingBackground(accent: Color, content: @Composable BoxScope.() -> Unit) {
    val animated by animateColorAsState(accent, tween(600), label = "accent")
    Box(
        Modifier
            .fillMaxSize()
            .background(FuelXColors.Bg)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to animated.copy(alpha = 0.55f),
                        0.6f to FuelXColors.Bg,
                        1f to FuelXColors.Bg
                    )
                )
            },
        content = content
    )
}
```

---

## 14. Quality Checklist (before calling it done)

- [ ] No white flash on launch; status/nav bars show light icons
- [ ] Every screen uses tokens only (search the code for `Color(0x` outside the theme file)
- [ ] Now Playing: no recomposition of title/artwork while the position ticks
- [ ] Every tappable element has press feedback and a 48dp target
- [ ] Track change feels like one motion (art, title, color together)
- [ ] No spinner on any empty screen; skeletons only
- [ ] Release build on the Android 11 phone holds smooth scrolling
- [ ] Font scale 200% doesn't break any screen
- [ ] TalkBack reads each control sensibly
- [ ] Unplayable / offline / error states are calm and actionable
