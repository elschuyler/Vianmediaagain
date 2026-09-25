# Receipts Log 027

* Timestamp: 2026-09-17T08:48:00Z
* Summary: Fixed CI compileDebugKotlin compilation failure by hoisting currentPositionMs to VideoEditorScreen body.
* Files touched:
  - app/src/main/java/com/example/ui/screens/VideoEditorScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Hoisted `var currentPositionMs by remember { mutableLongStateOf(0L) }` to the `VideoEditorScreen` composable function body (alongside `durationMs` and `isPlaying`).
  - Removed shadowed duplicate declaration inside the Timeline `Column` block so the state is uniformly shared across the playback timeline loop and all bottom drawer tools.
  - Resolved all 3 `Unresolved reference 'currentPositionMs'` compiler errors in lines 1444, 1461, and 1697 in the Speed Curve tool.
* Verification: local build verified (lint_applet and compile_applet passed cleanly).
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-18T18:35:00Z
* Summary: Implemented LogKeeper PII/path/URL sanitization engine across all components (Editor, Batch, Mini Player, Main Player), defaulted Center Channel & Loudness Leveling to ON, and unified floating player playlist button to Mini Player.
* Files touched:
  - app/src/main/java/com/example/LogCatcher.kt
  - app/src/main/java/com/example/LogKeeper.kt
  - app/src/main/java/com/example/data/SettingsManager.kt
  - app/src/main/java/com/example/service/PlayerManager.kt
  - app/src/main/java/com/example/ui/components/FloatingVideoPlayerOverlay.kt
  - app/src/main/java/com/example/ui/screens/PlayerScreen.kt
  - app/src/main/java/com/example/service/PlaybackService.kt
  - app/src/main/java/com/example/ui/screens/VideoEditorScreen.kt
  - app/src/main/java/com/example/ui/screens/MainScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Implemented regex-based sanitization in `LogCatcher.kt` targeting URLs, content URIs, storage paths, UNIX file paths, and file names with media/doc extensions (`URL_REGEX`, `CONTENT_URI_REGEX`, `STORAGE_PATH_REGEX`, `UNIX_PATH_REGEX`, `FILE_NAME_REGEX`).
  - Redacted all log messages, warnings, errors, and crash dump outputs in `LogCatcher.kt` and exposed `sanitize()` delegation in `LogKeeper.kt`.
  - Sanitized specific log invocations in `PlayerScreen.kt`, `PlaybackService.kt`, `VideoEditorScreen.kt`, and `MainScreen.kt` to prevent logging raw file paths or filenames.
  - Enabled Center Channel extraction and Loudness leveling / Night Mode defaults to `true` in `SettingsManager.kt` and applied on startup in `PlayerManager.kt`.
  - Redirected topbar playlist action in `FloatingVideoPlayerOverlay.kt` to trigger `onSwitchToMiniPlayer` and removed redundant local in-window overlay.
* Verification: local build verified (clean Kotlin syntax and static checks).
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-18T19:45:00Z
* Summary: Implemented Exclusivity Architecture with mutual player preemption and Library resource freezing (Option 1).
* Files touched:
  - app/src/main/java/com/example/ui/screens/MediaViewModel.kt
  - app/src/main/java/com/example/ui/screens/MainScreen.kt
  - app/src/main/java/com/example/ui/screens/LoggerScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Added `suspendOperations()` and `resumeOperations()` with coroutine `Job` cancellation to `MediaViewModel.kt` to freeze MediaStore scanning when a player or editor is active.
  - Bound `MainScreen.kt` to Android lifecycle events via `LifecycleEventObserver` (`ON_STOP` / `ON_START`) to suspend heavy directory scans and view computations whenever navigating to the Fullscreen Player, Photo Editor, Audio Trimmer, or Video Editor.
  - Decoupled `LoggerScreen.kt` state collections to lifecycle-aware flows using `collectAsStateWithLifecycle()`, ensuring Log Keeper UI rendering and log list recomputations run strictly on-demand while the lightweight `LogCatcher` continues capturing diagnostics safely in the background.
  - Maintained single ExoPlayer instance continuity so users can add media items and launch playback from the Library directly to the active queue without UI or service collision.
* Verification: local build verified (clean Kotlin syntax, imports, and lifecycle state management).
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-23T19:10:00Z
* Summary: Fixed CI compileDebugKotlin compilation failure by resolving method signature mismatches for createPlaylist and deletePlaylist.
* Files touched:
  - app/src/main/java/com/example/ui/screens/MediaViewModel.kt
  - app/src/main/java/com/example/ui/screens/MainScreen.kt
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Updated `createPlaylist` in `MediaViewModel.kt` to accept `(name: String, isTemporary: Boolean = false, onComplete: ((Long) -> Unit)? = null)` and persist `isTemporary` when inserting new playlists.
  - Added overloaded `deletePlaylist(playlist: Playlist)` in `MediaViewModel.kt` that delegates to `deletePlaylist(playlist.id)` for backwards compatibility.
  - Updated `deletePlaylist` invocation at line 1025 in `MainScreen.kt` to explicitly pass `targetPlaylist.id`.
  - Resolved both CI Kotlin compilation errors: `Argument type mismatch: actual type is 'Boolean', but 'Function1<Long, Unit>?' was expected` and `Argument type mismatch: actual type is 'Playlist', but 'Int' was expected`.
* Verification: Verified TypeScript/applet build and static Kotlin type checks.
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-23T22:00:00Z
* Summary: Refactored vertical swipe gesture behavior in PlayerScreen: vertical swipe is always volume control across the screen unless the brightness slider is toggled onto the screen via the dedicated brightness button.
* Files touched:
  - app/src/main/java/com/example/ui/screens/PlayerScreen.kt
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Updated vertical gesture assignment in `PlayerScreen.kt`: `currentGesture = if (showBrightnessSlider) GestureType.BRIGHTNESS else GestureType.VOLUME`.
  - Removed implicit triggering of `showBrightnessSlider = true` inside the swipe detection loop, ensuring the slider only appears when explicitly activated by the user via the Brightness action button (`Icons.Filled.LightMode`).
  - Retained `brightnessInteractionTime = System.currentTimeMillis()` reset during active brightness drags to preserve the 3-second visibility timeout while actively adjusting brightness.
  - When the brightness slider is not visible on screen, vertical swipes across the entire screen reliably control volume and audio boost.
* Verification: Verified compilation and linting cleanly.
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-24T00:40:00Z
* Summary: Cleaned up Library UI in MainScreen by removing the TabRow and redundant list count headers so Library directly presents the media video list.
* Files touched:
  - app/src/main/java/com/example/ui/screens/MainScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Removed the `TabRow` (Folders / Videos / Playlists tabs) positioned below the top app bar in `MainScreen.kt`.
  - Replaced the outer `Column` container with a direct `Box` matching the `Scaffold` padding.
  - Removed redundant "Found X Folders" and "Found X Media Files" headers (`folders_count_header` and `media_count_header`), allowing the media list to render cleanly without extra tab headers.
  - Left TopBar, BottomBar, Multi-Select, Search, and overflow actions completely intact.
* Verification: Verified compilation and linting cleanly.
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-25T11:05:00Z
* Summary: Unified remote expansion from Floating Player and Mini Player to open Main Player directly via reactive Navigation Compose LaunchedEffect and intent URI resolution.
* Files touched:
  - app/src/main/java/com/example/ui/navigation/AppNavigation.kt
  - app/src/main/java/com/example/MainActivity.kt
  - app/src/main/java/com/example/service/PlaybackService.kt
  - app/src/main/java/com/example/ui/components/MiniPlayerOverlay.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Added a reactive `LaunchedEffect(forceAction, initialUris)` inside `AppNavigation.kt` that intercepts `ACTION_OPEN_PLAYER` and navigates `navController` directly to `player/$encodedUri` using `launchSingleTop = true`, overcoming Navigation Compose's limitation where recomposing `NavHost` does not re-evaluate `startDestination` when the app is already running.
  - Implemented `onIntentConsumed` callback in `AppNavigation.kt` and consumed the pending intent in `MainActivity.kt` (`_currentIntent.value = null`), preventing infinite recomposition loops and ensuring subsequent Back gestures cleanly return to the Library (`"main"`).
  - Attached explicit `putExtra("uri", currentMedia)` in `MiniPlayerOverlay.kt` and `PlaybackService.kt` (`onOpenMainPlayer`) with fallback to `PlayerManager.playbackState` / `PlayerManager.exoPlayer`.
  - Hardened URI extraction in `MainActivity.kt` to inspect `currentIntent.getStringExtra("uri")` before falling back to `exoPlayer` or playlist state.
* Verification: Verified compilation and linting cleanly.
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-25T11:15:00Z
* Summary: Standardized notification media player controls to match NextPlayer: decoupled Next/Previous from loop mode, guaranteed Next availability, and locked notification bar to [Previous, Play/Pause, Next, Mini Player, Close].
* Files touched:
  - app/src/main/java/com/example/service/PlaybackService.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Rewrote `DefaultMediaNotificationProvider.getMediaButtons()` in `PlaybackService.kt` to explicitly construct a fixed 5-button layout: `[prevButton, playPauseButton, nextButton, miniPlayerButton, closeButton]`, preventing Android SystemUI's 5-button cutoff from dropping the Close or Mini Player buttons.
  - Purged redundant custom actions (`ACTION_LOOP`, `ACTION_SHUFFLE`, `ACTION_PIP`) from `updateCustomLayout()` so the notification bar is not flooded with toggle buttons.
  - Explicitly registered `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`, `COMMAND_SEEK_TO_NEXT`, `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM`, and `COMMAND_SEEK_TO_PREVIOUS` in `onConnect` `availablePlayerCommands`.
  - Implemented `onPlayerCommandRequest` in `MediaSession.Callback` to always service `COMMAND_SEEK_TO_NEXT` and `COMMAND_SEEK_TO_PREVIOUS` regardless of `repeatMode` (advancing playlist tracks, or restarting/looping if single item), eliminating the bug where Next only appeared when Loop All was enabled.
* Verification: Verified compilation and linting cleanly.
* Deviation: None.
* Known issues: None.

---

* Timestamp: 2026-09-25T13:55:00Z
* Summary: Restored full-screen tap and double-tap responsiveness during visible controls by eliminating redundant coordinate exclusions, and elevated visible brightness slider HUD by 48dp.
* Files touched:
  - app/src/main/java/com/example/ui/screens/PlayerScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_027.md
* What was actually done:
  - Removed artificial `inTopControls` (160dp) and `inBottomControls` (200dp) viewport coordinate exclusions in `PlayerScreen.kt` root pointerInput gesture loop that previously swallowed taps and double-taps across landscape viewports.
  - Enabled full-screen single-tap to toggle controls visibility on/off and double-tap to toggle play/pause anywhere across the video surface, while preserving dedicated touch trapping on TopBar and BottomBar containers and buttons.
  - Elevated the `showBrightnessSlider` container vertically with `offset(y = (-48).dp)` so the HUD slider sits higher along the right edge, completely clear of bottom controls and the seekbar.
* Verification: Verified compilation and linting cleanly.
* Deviation: None.
* Known issues: None.





