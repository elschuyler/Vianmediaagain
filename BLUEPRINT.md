# Master Plan & Blueprint
## Phase 1: Core Setup
- [x] Basic layout, Exoplayer, file selection.
## Phase 2: Editor Tools
- [x] Trim & Double Trim
- [x] Crop & Aspect Ratio
- [x] Rotate & Flip
- [x] Speed & Volume
- [x] Captions
## Phase 3: Live Preview Sync
- [x] Sync all edits (Cut/Trim, Captions, Effects) dynamically back to the Main Preview UI.
## Phase 4: Output Pipeline
- [x] FFmpeg command generation
- [x] Progress reporting & background service
- [x] Output video preview size display below player
- [x] Resilient batch image compression lifecycle and interruption handling
- [x] Mini Player "Open With" intent overlay presentation & permission management
- [x] Lock/unlock playback recovery, persistable URI permission retention, and orientation state restoration
- [x] Popup player lowered compact seekbar and unified bottom control row with sticky corner resize handle
- [x] Activity exit & back navigation orientation reset (preventing launcher crash on external playback return)
- [x] GitHub Actions APK pipeline configuration and Gradle lint error suppression (abortOnError=false)
- [x] Gradle wrapper pinning to 8.11.1 and compileSdk/targetSdk stabilization to API 35
- [x] Archived CI/CD GitHub Actions workflow reference to `/reference/github-actions/` and purged redundant `/Vianmedia-main` snapshot and transient root scripts
- [x] Zero-Memory Cache Architecture: Configured Option 0 (memory-only Coil thumbnail cache, 0 MB disk footprint), automated lifecycle temp deletion across VideoEditor/Compression/FFmpeg pipelines, startup orphaned cache purge, and added "Clear Unused Data" manual trigger in Settings (Storage & Output).
- [x] Lock Screen 0:00 Playback Resumption Fix: Eliminated redundant `seekTo(0)` calls on `STATE_IDLE` across `PlayerScreen.kt` and `FloatingVideoPlayerOverlay.kt`, preserved `playWhenReady` intent across lifecycle via `wasPlayingBeforePause` (rememberSaveable), and preserved PlayerView player binding across view resets (`onReset`) to prevent surface renderer detachment.
- [x] Heavy Resource Exclusivity Architecture: Enforced strict single-task execution across hardware decoders, CPU transcoding, and storage I/O. Added automatic background playback pausing on VideoEditorScreen, PhotoEditorScreen, AudioTrimmerScreen, FFmpegService, and CompressionService start. Deferral and exclusion of device-wide MediaStore playlist scans during active FFmpeg/Compression tasks or initial video playback buffering.
- [x] Player Controls Area Tap Retention: Wrapped topbar and bottombar in pointer-consuming container Boxes with gesture interception (`awaitFirstDown(false)` and `detectTapGestures`), preventing control area taps from bubbling down to the background toggle listener (`showControls = !showControls`), and resetting the 4000ms auto-hide countdown on user interaction.
- [x] Continuous Smooth Vertical Volume Gesture: Replaced chunky integer-quantized volume scaling with continuous normalized float ratio tracking. Enables seamless single-unit progression (1, 2, 3, 4...) and fluid vertical fill bar animations while cleanly triggering underlying AudioManager hardware steps and LoudnessEnhancer boost thresholds.
- [x] Application-Wide Crash Resilience & Scoped Storage LogKeeper: Initialized `LogKeeper` at activity startup in `MainActivity.onCreate()`, implemented dual-storage logging targeting public `MediaStore.Downloads` (API 29+) and app-specific external files dir fallback (bypassing legacy scoped storage EACCES).
- [x] External Intent Playback Backstack Stabilization: Fixed fatal `IllegalArgumentException` in Navigation Compose by anchoring `NavHost` root to `startDest` (`main`/`welcome`), adding unpadded Base64 route safety with decoding try/catch fallbacks, and protecting intent parcelable bundle unpacking against `BadParcelableException`.
- [x] Database & MediaStore Query Hardening: Configured `fallbackToDestructiveMigration()` in Room builder to prevent launch lockout on version mismatches, and replaced `getColumnIndexOrThrow` with guarded `getColumnIndex` lookups for optional MediaStore columns.
- [x] Restored Build/Dependency/Toolchain to Working Repo (Viabhron-Core-Dev/Vianmedia): Reinstated AGP 9.1.1, Kotlin 2.2.10, compileSdk 36 (minorApiLevel 1), targetSdk 36, coreKtx 1.18.0, activityCompose 1.10.1, navigationCompose 2.8.9, Room 2.7.0, Compose BOM 2024.09.00, and KSP 2.3.5 across `libs.versions.toml` and `app/build.gradle.kts`. Removed wrapper directory and aligned CI workflow to match working repo.
- [x] Popup Player Enhancements: Added Topbar switch to Mini Player button (`onSwitchToMiniPlayer`), loop toggle button placed directly after playback controls under seekbar cycling repeat modes (`OFF` -> `ALL` -> `ONE`), lowered seekbar and playback buttons to sit compactly at the window bottom, and configured dynamic video aspect ratio window layout and `RESIZE_MODE_FIT` matching across `FloatingVideoPlayerOverlay.kt` and `PlaybackService.kt`.
- [x] Split LogKeeper into Lightweight Catcher & Decoupled UI: Created headless `LogCatcher` engine handling thread-safe background appending, ring-buffer caching, and automated Downloads folder dump upon reaching the 2MB threshold. Decoupled `LoggerScreen` to load log data on-demand with size tracking, search, time filters, and export controls while maintaining zero-heap overhead and full backwards-compatibility.
- [x] Multi-Select Android Share, Dynamic Queue Appending & Temp Playlist Auto-Save with Next-Day Passive Cleanup: Added `*/*` MIME filter to `MiniMediaActivity` for mixed audio/video bundles, combined `clipData` and `EXTRA_STREAM` multi-item parsing with `FLAG_GRANT_READ_URI_PERMISSION`, added dynamic queue append when playback is active, implemented `isTemporary` Room entity schema with auto-saving of played queues, UI "Save/Keep" action, and lightweight on-launch next-day janitor purge without background alarms.
- [x] Fix GitHub Actions CI APK Build & Native Lib Merge: Configured `gradle/actions/setup-gradle@v3` with pinned `gradle-version: "8.11.1"` in `.github/workflows/build.yml` to prevent runner defaulting to incompatible Gradle 9.7.1, stabilized `compileSdk` to 35, and configured `packaging.jniLibs` in `app/build.gradle.kts` with `pickFirsts` for `libc++_shared.so` and ffmpeg libraries to resolve `:app:mergeDebugNativeLibs` conflict.
- [x] Single-Focus Intent Navigation & Clean Exit Architecture: Configured dynamic `startDestination` in `AppNavigation.kt` (`intentDest ?: defaultStartDest`) so external Player or Editor launches do not mount or flash the Library ("main") underneath. Aligned `onNavigateBack` across PlayerScreen, PhotoEditorScreen, VideoEditorScreen, and AudioTrimmerScreen to directly finish the Activity when launched from an external Intent, preventing unintended falls into the Library.
- [x] Player Speed Button Tap Cycle & Long-Press Panel: In `PlayerScreen.kt`, converted the topbar speed button to support single-tap speed cycling (`1.0x` -> `1.2x` -> `2.0x` -> `0.5x` -> `0.25x`) with immediate playback update and persistence, replaced standard icon when non-1x with a distinct rounded accent badge displaying current speed multiplier, and reserved full speed/skip-silence dialog panel for long-press.
- [x] Video Editor Custom Play/Pause Controls & Stock ExoPlayer UI Removal: In `VideoEditorScreen.kt`, disabled ExoPlayer default XML controllers (`useController = false`), integrated video preview tap playback toggling with central floating Compose Play badge when paused, and added a dedicated timeline Play/Pause button styled with app accent `#2196F3` alongside the current timecode.
- [x] Export Quality Defaults & Player Tap/Double-Tap Calibration: Updated Video Editor export and FFmpeg batch dialog defaults to 480p resolution, middle slider quality (0.5f), and disabled ("unticked") Ultra Fast export. In `PlayerScreen.kt`, refactored gesture handling into a unified, high-reliability timestamp-based detector eliminating `detectTapGestures` slop failures; calibrated double-tap exclusively for play/pause toggling, single-tap for controls appearance/disappearance, and single-tap to dismiss the brightness slider immediately when open without disturbing player controls.



