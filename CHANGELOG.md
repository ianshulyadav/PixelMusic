# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.08] - 2026-07-31

### Features & Enhancements
- **Redesigned Explore Tab**: Material 3 Expressive hero banner, bento-style category cards, and mood filter chips.
- **Instant Search Playback**: Tap any song in search results to play immediately, seamlessly integrated with the mini player.
- **Search Layout Optimization**: Smooth scrolling above the mini player with clean layout bounds.
- **Explore Header Refresh**: Styled typography matching the main Library tab.

### Performance & Reliability
- **Accelerated Library Loading**: Background database querying for faster tab switching and instant library rendering.
- **Seamless Track Transitions**: Smart Auto-Queue buffering eliminates playback interruptions.
- **Snappy App Startup**: Optimized theme extraction and on-demand initialization for instant launch.
- **Optimized UI Rendering**: Memoized component allocations for smooth 60fps scrolling across all lists.
- **Background Task Safety**: Reliable background operations for playlist and library updates.

## [1.5.06] - 2026-06-12

### Added
- **Smart Mix Playlist Generator (Last.fm Creator)**:
  - Replicated and adapted the creation and discovery features of LastWave into a native Compose implementation.
  - Created a config screen supporting 8 distinct generation modes (Top Tracks, Recent Tracks, Similar Tracks/Artists, Genre, Recommendations, etc.).
  - Added "Recent Mixes (last.fm)" horizontal carousel to the Explore Screen with custom preview cards.
- **Spotify-Style Snapchat Story Sharing**:
  - Integrated the official Snapchat Creative Kit SDK for premium story sharing of customized song and lyrics cards.
- **Share Card Customizations**:
  - Implemented dynamic pastel theming, frosted glassmorphic card containers with deep blurred artwork backgrounds, and wavy seekbar animations.
- **Explore Progressive Loading**:
  - Phased Explore screen rendering (Lazy loading above-the-fold feeds first, then personalized lists in the background) for near-instant page loads.
- **Explore Personalization**:
  - Integrated ArchiveTune's sorting algorithm to prioritize explore new release albums by your favorite and most-played artists.
- **Material You Dynamic Colors**: Added a "Dynamic (System)" option to the App Color Palette setting on Android 12+ devices.
- **Explore Launch Tab**: Support setting the Explore screen as the default tab upon launching the app.
- **Smart Mix Playlist Retention**: Added settings to choose retention periods (24 hours, 7 days, 30 days, or permanent) for generated AI playlists, with automated startup pruning.

### Changed
- Reworked share and options bottom sheets to use dynamic themes and rounded list items.
- Optimized centerpiece floating card width to 84% for wider margins relative to background.
- Maximized size and centered monochrome/base app logo drawables for launcher compatibility.
- Streamlined auto-queue track selection with similarity scoring, discovery balancing, and duplicate prevention.

### Fixed
- **MIUI/HyperOS Lockscreen Art Fix**: Added a custom `SharedArtworkContentProvider` to resolve missing lockscreen album art on Xiaomi/Redmi devices.
- **SmartMix thread-safety**: Fixed concurrent modification crash in the "My Recommendations" mode by synchronizing the candidates list.
- **Playback Reloading Bug**: Resolved redundant playback requests causing YouTube streams to restart after 1 second.
- **Database optimizations**: Resolved Room database parameter warnings and optimized large database queries.
- **Explore screen crash**: Added ProGuard keep rules for Explore cache models to prevent Gson ClassCastException under R8 optimization.
- **Search Result Queueing**: Restored click behavior to play only the selected search result rather than loading the entire list.
- **Library isolation & database updates**: Excluded YouTube Music related/similar songs from showing up in the main library, and corrected dynamic download/favorite status updates in the UI.
- **Playlist management & song removal**: Upgraded playlist deletion and song removal to support all song ID variants, resolving issues with "ghost" playlists, and fixed downloaded songs isolation so downloading new tracks doesn't clear the local downloaded playlist.

### Performance
- Optimized prefetching by making audio bytes loading cooperatively cancelable on skipping.
- Prioritized high-quality Opus audio streaming to minimize initial latency.

## [1.4.06] - 2026-06-06

### Added
- **Last.fm Scrobbler Integration**:
  - Implemented background playback scrobbling engine that calculates progress in real-time.
  - Added live "Now Playing" updates synced automatically during playback.
  - Added dedicated Last.fm settings UI containing threshold configuration sliders (Minimum Track Duration, Delay Percentage, and Max Delay Duration).
  - Added connection management card inside the Accounts screen supporting active username labels and dynamic logouts.
  - Integrated customizable inputs for **API Key** and **API Secret** during linking, and enforced them as **compulsory fields** to prevent rate-limiting bottlenecks and enhance security.
- **YouTube Music Playlist Export & Import**:
  - Integrated support for playlist exports and imports using standard M3U and CSV formats.
  - Implemented URL-decoding for paths, extensionless filename matching, and automated lookup via YouTube Music search to resolve missing local/remote tracks.
  - Optimized lookup speeds using concurrent network requests, bulk database insertions, and duplicate merge confirmation flows.
  - Enhanced import pipelines to retrieve and persist rich metadata alongside high-quality album art.
- **Audio Streaming Improvements**:
  - Prioritized Opus audio format streaming to optimize and accelerate initial playback load latency.

### Changed
- Removed default built-in Last.fm API credential fallbacks in the UI, requiring each user to register their own developer credentials.

### Fixed
- **CI/CD & Nightly Releases**: Integrated Pyrogram MTProto inside nightly Telegram publishers to bypass the 50MB Bot API file size upload limit, added fallbacks for GitHub release redirect links, and added rate-limiting delay retries.
- **R8 / ProGuard Optimization**:
  - Added ProGuard keep rules for Explore caching data models to prevent Gson ClassCastExceptions.
  - Added dontwarn rules for `javax.script` and `org.mozilla.javascript.engine` to resolve release R8 build compilation failures.
- **UI & Stability**: Resolved Quick Picks personalization errors, auto-queue online mix radio transition bugs, and Artist See All list pagination regressions.

## [0.5.0-beta] - 2026-01-14

### Added
- Implemented 10-band Equalizer and effects suite (feat: @theovilardo)
- Added M3U playlist import/export support (feat/fix: @lostf1sh, @theovilardo)
- Integrated Deezer API for artist images (feat: @lostf1sh)
- Added Gemini AI model selection, system prompt settings, and AI playlist entry point (feat: @lostf1sh, @theovilardo)
- Added sync offset support for lyrics and multi-strategy remote search (feat/fix: @lostf1sh, @theovilardo)
- Added Baseline Profiles for improved performance (feat/fix: @theovilardo, @google-labs-julesbot)
- Added support for custom playlist covers

### Changed
- **Material 3 Expressive UI**: Modernized Settings, Stats, Player, Bottom Sheets, and dialogs (refactor: @theovilardo, @lostf1sh)
- **Library Sync**: Rebuilt initial sync flow with phase-based progress reporting and linear indicators (feat: @lostf1sh)
- **Settings Architecture**: Introduced category sub-screens and improved navigation handling (refactor/fix: @theovilardo)
- **Queue & Player**: Decoupled queue updates from scroll animations, added animated queue scrolling (feat/fix: @lostf1sh, @theovilardo)
- Improved widget previews and case-insensitive sorting logic (feat/fix: @lostf1sh, @google-labs-julesbot)

### Fixed
- Fixed casting stability, queue transitions, and reduced latency (fix: @theovilardo)
- Fixed delayed content rendering and unwanted collapses in Player Sheet (fix/refactor: @theovilardo)
- Fixed reordering issues in queue
- General crash fixes and minor UX improvements (fix: @lostf1sh, @theovilardo)

## [0.4.0-beta] - 2025-12-15

### Added
- Major navigation redesign
- New file explorer for choosing source directories
- Landscape mode (thanks to "leave this blank for now")
- New Connectivity and casting functionalities
- Seamless continuity between remote devices
- Gapless transition between songs
- Crossfade
- New Custom Transitions feature (only for playlists)
- Keep playing after closed the app
- UI Optimizations
- Improved stats feature
- Redesigned Queue control with more features
- Improved different filetypes support for playing and metadata editing
- Improved permission controller
- Minor bug fixes

## [0.3.0-beta] - 2025-10-28

### What's new
- Introduced a richer listening stats hub with deeper insights into your sessions.
- Launched a floating quick player to instantly open and preview local files.
- Added a folders tab with a tree-style navigator and playlist-ready view.

### Improvements
- Refined the overall Material 3 UI for a cleaner and more cohesive experience.
- Smoothed out animations and transitions across the app for more fluid navigation.
- Enhanced the artist screen layout with richer details and polish.
- Upgraded DailyMix and YourMix generation with smarter, more diverse selections.
- Strengthened the AI assistant to deliver more relevant playback suggestions.
- Improved search relevance and presentation for faster discovery.
- Expanded support for a broader range of audio file formats.

### Fixes
- Resolved metadata quirks so song details stay accurate everywhere.
- Restored notification shortcuts so they reliably jump back into playback.

## [0.2.0-beta] - 2024-09-15

### Added
- Chromecast support for casting audio from your device (temporarily disabled).
- In-app changelog to keep you updated on the latest features.
- Improved lyrics search
- Support for .LRC files, both embedded and external.
- Offline lyrics support.
- Synchronized lyrics (synced with the song).
- New screen to view the full queue.
- Reorder and remove songs from the queue.
- Mini-player gestures (swipe down to close).
- Added more material animations.
- New settings to customize the look and feel.
- New settings to clear the cache.

### Changed
- Complete redesign of the user interface.
- Complete redesign of the player.
- Performance improvements in the library.
- Improved application startup speed.
- The AI now provides better results.

### Fixed
- Fixed various bugs in the tag editor.
- Fixed a bug where the playback notification was not clearing.
- Fixed several bugs that caused the app to crash.

## [0.1.0-beta] - 2024-08-30

### Added
- Initial beta release of PixelMusic Music Player.
- Local music scanning and playback (MP3, FLAC, AAC).
- Background playback using a foreground service and Media3.
- Modern UI with Jetpack Compose, Material 3, and Dynamic Color support.
- Music library organization by songs, albums, and artists.
- Home screen widget for music control.
- Real-time audio waveform visualization.
- Built-in tag editor for song metadata.
- AI-powered features using Gemini.
- Smooth in-app permission handling.
