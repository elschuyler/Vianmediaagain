# Receipts Log 031

## Phase 137: Application-Scoped Coil ImageLoader, Data-Integrity/Crash Blockers & Security/Privacy Hardening
* Timestamp: 2026-11-13T10:20:00Z
* One-line summary: Moved Coil ImageLoader initialization to `ShuraApplication.newImageLoader()`, resolved end-of-playback/overlay/concurrency/IO/transaction/binder crash blockers, and hardened MediaSession, Widget IPC, FFmpeg invocation, backup rules, and log sanitization.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/AndroidManifest.xml
  - app/src/main/res/xml/backup_rules.xml
  - app/src/main/res/xml/data_extraction_rules.xml
  - app/src/main/java/shura/media/ShuraApplication.kt (created)
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/BatchActionActivity.kt
  - app/src/main/java/shura/media/LogCatcher.kt
  - app/src/main/java/shura/media/data/CacheManager.kt
  - app/src/main/java/shura/media/data/BackupManager.kt
  - app/src/main/java/shura/media/data/MediaUriResolver.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/service/CompressionService.kt
  - app/src/main/java/shura/media/service/FFmpegService.kt
  - app/src/main/java/shura/media/widget/MediaWidgetService.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/components/FFmpegBatchDialog.kt
  - app/src/main/java/shura/media/ui/navigation/AppNavigation.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/ui/screens/VideoEditorScreen.kt
  - app/src/main/java/shura/media/ui/screens/AudioTrimmerScreen.kt
  - receipts/RECEIPTS_031.md (created)
* What was actually done:
  - **Coil ImageLoader & Application Init**: Created `ShuraApplication : Application(), ImageLoaderFactory` initializing `LogKeeper.init(this)` in `onCreate()` and providing a lazy Coil `ImageLoader` (`VideoThumbnailFetcher.Factory()`, 25% memory cache, 100 MB `thumbnail_cache` disk cache, `crossfade(false)`). Registered `.ShuraApplication` in `AndroidManifest.xml` and removed the redundant `Coil.setImageLoader(...)` call from `MainActivity.kt`.
  - **Data-Integrity & Crash Blockers**:
    - Excluded `thumbnail_cache` raw deletes from `CacheManager.purgeOrphanedTempFiles()` and cleared Coil `memoryCache`/`diskCache` via Coil API first in `CacheManager.clearUnusedCache()` (plus cleaning `Temp_Input_*` files).
    - Unified `pauseAtEndOfMediaItems = false` across `PlayerManager`, `PlaybackService`, `PlayerActivity`, `PlayerScreen`, and `MiniPlayerOverlay`; removed the `stop(); clearMediaItems()` auto-clear in `PlaybackService.onPlayWhenReadyChanged`; guarded `STATE_ENDED`/`STATE_IDLE` service shutdown behind `!PlayerManager.isFloatingVideoActive && composeView == null`.
    - Fixed overlay audio/video switching via Compose `isOverlayVideoMode` state, wrapped overlay layout updates in `safeUpdateViewLayout()` (`isAttachedToWindow` + `try/catch`), tore down overlay before `ON_DESTROY`, posted fallback FGS notification `1002` when entering overlay with 0 items or denied overlay permission, and removed background `context.startService(...)` calls from `PlaybackService.hideOverlay(context)` and `MainActivity` double overlay broadcasts.
    - Guarded `AppNavigation` auto-launch against duplicate `PlayerActivity` creation and forwarded `ClipData` + `FLAG_GRANT_READ_URI_PERMISSION` across player/service handoffs.
    - Moved `PlayerManager.release()` player teardown onto the Main Looper; replaced global `Job()` in `CompressionService` and `FFmpegService` with `SupervisorJob()` + serialized job handling + `try/finally` foreground cleanup + Main-Looper status updates + `MediaUriResolver.openInputStreamSafely(...)` input reads + `requestCancel()` in-process cancellation (no background `startService` `"STOP"`).
    - Serialized `LogCatcher` log file I/O on a single-threaded `Dispatchers.IO.limitedParallelism(1)` executor + lock.
    - Replaced `runBlocking(Dispatchers.Main)` and `folderId!!` in `MediaWidgetService` with `PlayerManager.playbackState.value` snapshots and `toIntOrNull()`.
    - Made `BackupManager.restoreAll()` pre-validate JSON and execute playlist/item restoration atomically inside `db.withTransaction { ... }`.
  - **Security & Privacy Hardening**:
    - Disabled OS cloud/device backup (`android:allowBackup="false"`) and excluded `vianbr_settings.xml` and `logs` in `backup_rules.xml` and `data_extraction_rules.xml`.
    - Hardened `PlaybackService` `MediaSession.Callback` to reject untrusted callers, restrict internal session commands to `isSelfController(controller)`, and enforce URI scheme/authority checks (`isAllowedMediaUri` / `isAllowedSubtitleUri`).
    - Bound widget broadcasts (`MediaWidgetProvider` / `PlaybackService`) with a per-install `SecureRandom` token (`widget_prefs`) while separating system `APPWIDGET_*` callbacks, and migrated runtime broadcast receivers in `MainActivity`, `PlaybackService`, and `PlayerScreen` to `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`.
    - Eliminated all `FFmpegKit.execute(...)` shell-parsed invocations in favor of `FFmpegKit.executeWithArguments(...)` across `FFmpegService`, `FFmpegBatchDialog`, `BatchActionActivity`, `VideoEditorScreen`, and `AudioTrimmerScreen`, hardened `drawtext` escaping, sanitized & capped FFmpeg failure log tails (25 lines), redacted logcat output lines written by `LogCatcher.writeToDownloads`, and blocked ambiguous multi-match `DISPLAY_NAME` guessing in `MediaUriResolver`.
* How it was verified: Local static `grep` sweep confirmed zero `com.example` references, zero `FFmpegKit.execute(` calls, zero unguarded `updateViewLayout(` calls outside `safeUpdateViewLayout`, zero `pauseAtEndOfMediaItems` mismatches, zero unflagged `registerReceiver(` calls, and zero background `startService` `"STOP"` calls; `lint_applet` and `compile_applet` verified the workspace toolchain. Android Kotlin/Gradle compilation (`./gradlew assembleDebug`) is not available locally (`./gradlew: not found`) and runs on GitHub Actions CI after export.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: Ensure GitHub Actions CI compiles `:app:compileDebugKotlin` and `assembleDebug` cleanly after export, and verify on-device behavior for the flows listed in the On-Device Verification Guide.

## Phase 138: Option A R8 `minifiedDebug` Pipeline, ProGuard Hardening & Phase 137 Gap Remediations
* Timestamp: 2026-11-13T16:59:00Z
* One-line summary: Added Option A `minifiedDebug` R8 test build & CI artifacts, hardened ProGuard rules for SoLoader/FFmpegKit/Media3/LineNumberTable, and resolved all identified Phase 137 concurrency, overlay, backup, widget, join-audio, and log privacy gaps.
* Files touched:
  - BLUEPRINT.md
  - app/build.gradle.kts
  - app/proguard-rules.pro
  - .github/workflows/build.yml
  - app/src/main/res/xml/backup_rules.xml
  - app/src/main/res/xml/data_extraction_rules.xml
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/LogCatcher.kt
  - app/src/main/java/shura/media/data/BackupManager.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/service/FFmpegService.kt
  - app/src/main/java/shura/media/service/CompressionService.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - app/src/main/java/shura/media/ui/navigation/AppNavigation.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/ui/screens/SettingsScreen.kt
  - app/src/main/java/shura/media/ui/screens/VideoEditorScreen.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - **Option A R8 & ProGuard Hardening**: Added `minifiedDebug` buildType in `app/build.gradle.kts` inheriting `release` (`isMinifyEnabled = true`, `isShrinkResources = true`) with debug signing; enabled `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile` and added keep/dontwarn rules for Fresco/SoLoader reflection (`com.facebook.soloader.**`, `animated.webp`, `imagepipeline.animated`), `com.arthenica.**`, and `org.jellyfin.media3.**` in `app/proguard-rules.pro`; updated `.github/workflows/build.yml` to build `assembleDebug assembleMinifiedDebug` and upload `app-minified-debug.apk` + `mapping.txt`.
  - **Batch Intents & Concurrency**: Updated `AppNavigation.kt` to forward `quality`, `format`, `action = "START_BATCH"`, `data`, `ClipData`, and `FLAG_GRANT_READ_URI_PERMISSION` to `CompressionService`, plus added extension/animated-media fallback detection matching `BatchActionActivity`. Added monotonic `batchRunId` (`AtomicLong`) guards in `FFmpegService.kt` and `CompressionService.kt` so cancelled/superseded jobs never reset `isRunning` or stop the active foreground notification, and migrated `FFmpegService`'s Cancel notification action from `PendingIntent.getService(..., "STOP")` to a non-exported in-process broadcast receiver calling `requestCancel()`.
  - **Overlay, Playback & Lifecycle**: Updated `PlaybackService.kt` to invoke `ensureFallbackForegroundNotification()` on overlay starts whenever playback is not actively playing (including paused queues), switched `updateWindowForAspectRatio` to read live `isOverlayVideoMode && !sessionIsMinimized` so `onSwitchToVideo` resizes immediately, and validated/cleaned `ACTION_PLAY_FILE` URIs. Moved `PlayerManager.flushProgressToStorage(player)` onto the Main Looper inside `release()`'s `releaseBlock` prior to `player.stop()`/`release()`. Removed unsolicited `MainActivity.onUserLeaveHint()` PiP trigger, guarded TopBar back `clearMediaItems()` in `PlayerScreen.kt` with `playerError == null`, and deduplicated `STATE_ENDED`/`END_OF_MEDIA_ITEM` `onNavigateBack()`.
  - **Backup, Widget, Video Join & Log Privacy**: Moved `BackupManager.createBackup`/`restoreBackup` and `SettingsScreen` SAF stream reads/writes to `Dispatchers.IO` with URI scheme allowlist checks; added `widget_prefs.xml`, `log_catcher_prefs.xml`, and `media_database` exclusions to `backup_rules.xml` & `data_extraction_rules.xml`. Fixed `MediaWidgetProvider.kt` `ACTION_FFWD` `C.TIME_UNSET` seek clamp, added `"ACTION_MINIPLAYER"` command handling, and moved `MediaController.releaseFuture` into `finally`. Added MP3 silent-video precheck and per-clip audio probing (`anullsrc` duration-matched pad for mixed silent/audio joins, or `v=1:a=0` video-only concat when all segments lack audio) in `VideoEditorScreen.kt`. Added `SPACED_STORAGE_PATH_REGEX` and `HH:mm:ss.SSS` calendar timestamp parsing in `LogCatcher.kt`.
  - *(Phase 137 Receipt Clarification)*: Above line 42 referenced `restoreAll()`, whose actual symbol in `BackupManager.kt` is `restoreBackup()`; line 47 referenced "logcat output lines written by `LogCatcher.writeToDownloads`", whereas `LogCatcher` writes its own sanitized `LogCatcher` ring/disk buffer (not Android system `logcat`).
* How it was verified: Static verification only — XML parsing passed across `AndroidManifest.xml`, `backup_rules.xml`, and `data_extraction_rules.xml`; brace counts verified balanced across all 13 modified `.kt`/`.kts` files; `grep` confirmed zero `PendingIntent.getService(`, zero `onUserLeaveHint`, zero `com.example` references, and expected `batchRunId` / `minifiedDebug` / `SourceFile,LineNumberTable` entries. Android Gradle build (`assembleDebug` / `assembleMinifiedDebug`) was NOT run locally (`gradle`/`./gradlew` absent) and awaits GitHub Actions CI after export.
* Any deviation from what was requested: None (Option A + all 10 audit items implemented as finalized).
* Any known issue or follow-up needed: Push to GitHub to run `assembleDebug` and `assembleMinifiedDebug` in CI; inspect R8 build output (`mapping.txt` / any new third-party `-dontwarn` needs from `ffmpeg-kit` or `media3-ffmpeg-decoder`) and run on-device verification on `app-minified-debug.apk`.

## Phase 139: UI & Architecture Hardening (Single Engine, Zero-Video Audio Mode, Loop OFF Fix, PiP Cleanup, 120Hz Library & Settings Navigation)
* Timestamp: 2026-11-13T12:12:00Z
* One-line summary: Implemented all 7 Phase 139 architecture, playback, UX, and 120Hz library smoothness items while leaving All Videos tab and Home Screen Widget untouched.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/ui/screens/PipHelper.kt (deleted)
  - app/src/main/java/shura/media/ui/screens/PipActionReceiver.kt (deleted)
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/components/FloatingVideoPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/ui/EditorActivity.kt
  - app/src/main/java/shura/media/ui/screens/AudioTrimmerScreen.kt
  - app/src/main/java/shura/media/ui/screens/PhotoEditorScreen.kt
  - app/src/main/java/shura/media/ui/screens/VideoEditorScreen.kt
  - app/src/main/java/shura/media/ui/navigation/AppNavigation.kt
  - app/src/main/java/shura/media/data/SettingsManager.kt
  - app/src/main/java/shura/media/data/MediaRepository.kt
  - app/src/main/java/shura/media/ui/screens/MediaViewModel.kt
  - app/src/main/java/shura/media/ui/screens/MainScreen.kt
  - app/src/main/java/shura/media/ui/screens/SettingsScreen.kt
  - app/compose_compiler_config.conf (created)
  - app/src/main/baseline-prof.txt (created)
  - app/build.gradle.kts
  - receipts/RECEIPTS_031.md
* What was actually done:
  - **1. Native Android PiP Plumbing Removal**: Deleted `PipHelper.kt` and `PipActionReceiver.kt`; removed `android:supportsPictureInPicture="true"` from `MainActivity` and `.ui.PlayerActivity` in `AndroidManifest.xml`; removed `MainActivity.pipReceiver`, `ACTION_ENTER_PIP`, and `ACTION_START_PIP`; stripped `PictureInPictureParams`, `isInPipMode`, `isPip`, and `PipActionReceiver` hooks from `PlayerScreen.kt` while keeping custom `WindowManager` overlays (`.PipMediaActivity`, `ACTION_VIDEO_OVERLAY`, `ACTION_MINIPLAYER`).
  - **2. Loop OFF (`REPEAT_MODE_OFF`) & Notification Next/Prev Replay Fix**: Replaced hardcoded `pauseAtEndOfMediaItems = false` across `PlayerManager.kt`, `PlaybackService.kt`, `PlayerActivity.kt`, `PlayerScreen.kt`, `MiniPlayerOverlay.kt`, and `FloatingVideoPlayerOverlay.kt` with `(repeatMode == Player.REPEAT_MODE_OFF)`. Removed forced `seekToNextMediaItem()` and `onNavigateBack()` on `STATE_ENDED` / `PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` in `PlayerScreen.kt` (pausing cleanly at end with controls available for Replay/Next/Prev). Guarded `PlaybackService` `STATE_ENDED` stop with `!PlayerManager.isPlayerScreenVisible` and updated Notification Prev/Next, Widget Prev/Next, and Floating Overlay Prev/Next to wrap and call `prepare()` + `play()` from `STATE_ENDED`/`STATE_IDLE`.
  - **3. True Single Engine Across Players & Editors**: Added `PlaybackService.stopPlaybackAndRelease(context)` and invoked it before navigating to or initializing `EditorActivity`, `AppNavigation` editor routes, `VideoEditorScreen`, `AudioTrimmerScreen`, and `PhotoEditorScreen`. Isolated `VideoEditorScreen` export preview dialog (`isExportPreviewOpen`) so main `exoPlayer` and `bgAudioPlayer` save position and release before `previewPlayer` builds, restoring playback position upon dismissal.
  - **4. Zero Video Resources When No Video Is Active**: Added `PlayerManager.isPlayerScreenVisible`, `isAudioOnlyHint()`, and `hasSupportedVideoTrack()`. Updated `PlaybackService.onTracksChanged`, `showOverlay`, `handleMinimizeToggle`, and `onSwitchToVideo` to detach video surface and disable video tracks when audio-only or when neither `PlayerScreen` nor floating video overlay is visible. Updated `PlayerScreen.kt` and `FloatingVideoPlayerOverlay.kt` to skip composing `AndroidView(PlayerView)` and render audio UI when `isAudioOnly == true`, guarded `ON_RESUME` video track enabling (`!isAudioOnly`), and guarded screenshot capture against audio-only mode.
  - **5. Batch-Only External Panels**: Removed duplicate multi-URI batch dialogs (`batchCompressionUris`, `batchFFmpegUris`) and global `CompressionStatus` / `FFmpegStatus` dialogs from `AppNavigation.kt`, keeping `BatchActionActivity` (upgraded `MainScreen.kt` launch Intent with `data`, `ClipData`, and `FLAG_GRANT_READ_URI_PERMISSION`) + short-lived foreground services.
  - **6. 120Hz Library Smoothness**: Added `SettingsManager.PlaybackStateLookup` for O(1) snapshot reads inside `MediaRepository` cursor loops; removed redundant disk JSON reads in `getMediaFolder()` via optional `cachedFolder`; debounced `MediaViewModel` `scheduleCacheSave()` (1500ms); added fast in-memory `syncPlaybackTags()` replacing forced `ON_START` ContentResolver rescans in `MainScreen.kt`; migrated `MainScreen.kt` to `collectAsStateWithLifecycle()`, single hoisted `SettingsManager`, gated `snapshotFlow` scroll trackers, `HashSet<Long>` `selectedMediaIds`, and `Dispatchers.IO` info dialog query; created `app/compose_compiler_config.conf` (`android.net.Uri`), `app/src/main/baseline-prof.txt`, and `composeCompiler { stabilityConfigurationFiles }` in `app/build.gradle.kts`.
  - **7. Android UX Hardening (NavHost Settings)**: Promoted `SettingsScreen` out of `Dialog()` into `AppNavigation.kt` `composable("settings")` and shared Activity-scoped `MediaViewModel` across `MainScreen.kt` and `SettingsScreen.kt` (`StorageSettingsPage`) so folder exclusions immediately synchronize.
* How it was verified: Local static checks passed (`AndroidManifest.xml` parsed cleanly via Python `ElementTree`, balanced brackets/delimiters verified across all 18 modified `.kt`/`.kts` files, codebase grep checks confirmed removal of `PipHelper`/`PipActionReceiver`/`ACTION_ENTER_PIP`/`ACTION_START_PIP`/`isInPictureInPictureMode`/`pauseAtEndOfMediaItems = false`/duplicate batch dialogs, and workspace security scan was clean); `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent, so Android Gradle build (`assembleDebug` / `assembleMinifiedDebug`) and runtime verification occur on GitHub Actions CI and on-device after export.
* Any deviation from what was requested: None (All Videos tab and Home Screen Widget preserved untouched; custom floating overlay `.PipMediaActivity` preserved).
* Any known issue or follow-up needed: Push to GitHub to run CI Gradle compilation (`assembleDebug` and `assembleMinifiedDebug`) and verify all flows in the On-Device Verification Guide.

## Phase 140: Fast Playback Startup, Explicit Queue Propagation & Active-Player Isolation (LogKeeper Preserved)
* Timestamp: 2026-11-14T18:24:00Z
* One-line summary: Eliminated startup UI/Binder/thumbnail stalls and unnecessary background DSP/tickers/scans during Main, Mini, Floating, and Background playback while preserving explicit multi-select/playlist queues and keeping LogKeeper/LogCatcher active and untouched.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/data/SettingsManager.kt
  - app/src/main/java/shura/media/service/CenterChannelAudioProcessor.kt
  - app/src/main/java/shura/media/MyBitmapLoader.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/components/FloatingVideoPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/screens/MainScreen.kt
  - app/src/main/java/shura/media/ui/screens/PlaylistDetailScreen.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - **DSP & Settings Isolation**: Changed `centerChannelEnabled` and `nightModeEnabled` defaults to `false` in `SettingsManager.kt`; added `@Volatile enabled` and `releaseScratchBuffer()` in `CenterChannelAudioProcessor.kt` so disabled center-channel mode returns `AudioFormat.NOT_SET` and skips stereo DSP buffer allocation (`isActive() == false`).
  - **Thumbnail & Notification Metadata Startup**: Reordered `MyBitmapLoader.loadBitmap()` to query Coil `context.imageLoader` (`thumbnail_cache`) before `loadThumbnail` and `MediaMetadataRetriever`. Removed `PlaybackService.ensureArtworkLoaded()` and `PlayerActivity.extractThumbnailBytes()` / `ACTION_UPDATE_NOTIFICATION` and `replaceMediaItem()` thumbnail churn, using lazy `artworkUri` injection in `PlaybackService.ForwardingPlayer.getMediaMetadata()`.
  - **Startup Main-Thread & Queue Fixes**: Added `PlayerActivity.resolveFastUriOnce()` skipping Binder IPC for `MediaStore.AUTHORITY` and reused `preResolvedUri`; removed duplicate `MediaUriResolver` and `getType`/`grantUriPermission` Main-thread calls in `PlayerScreen.kt` (moving to `Dispatchers.IO`). Added `pendingQueueIsImplicit`, `consumePendingQueueWithImplicitFlag()`, `isQueuePopulationInProgress`, and `applyFolderQueueAroundCurrent()` in `PlayerManager.kt`. Updated `MainScreen.kt` to offload WebP animation checks and folder queue building (`Dispatchers.Default`) while navigating to player immediately on tap, and updated `MainScreen.kt` multi-select Play + `PlaylistDetailScreen.kt` FAB/item clicks to build explicit queues off the Main thread with `isImplicit = false`. Deferred `PlayerScreen.kt` sibling scan until `Player.STATE_READY` (skipping network streams, explicit queues, and active `isQueuePopulationInProgress`), and updated `resolveSiblingPlaylistItems()` to handle audio files via `isAudioOnlyHint` + `COMMON_AUDIO_EXTENSIONS` + `MediaColumns`.
  - **Background & Player Ticker Isolation**: Removed eager `PlayerManager.initialize()` from `MainActivity.kt` and replaced synchronous `getDisplayNameFromUri()` in `handleFloatingOrMiniIntent()` with fast decoded segments. Consolidated `PlayerManager.updateSnapshot()` onto `Player.Listener.onEvents()` reusing unchanged `playlist` references and deduplicating `"Temp Current"` deletion; gated `PlayerManager` progress ticker to expanded `MiniPlayerOverlay` (`setOverlayProgressTickerActive`); widened `PlayerScreen.kt` periodic save interval from `500ms/3s` to `10s` when A-B repeat and sleep timer are inactive. Deferred `MiniPlayerOverlay.kt` `getMediaFolders()` and `getAllPlaylists()` collection until `explorerMode` switches to `"folders"`/`"playlists"`. Removed unused playlist mirror list/loop from `FloatingVideoPlayerOverlay.kt`. Cached `hasInstalledWidgets` in `MediaWidgetProvider.kt` (`onEnabled`/`onDisabled`/`onDeleted`) and guarded `PlaybackService.updateWidgetUI()`.
  - **LogKeeper Preserved**: Left `LogKeeper` / `LogCatcher` enabled and untouched per user instruction.
* How it was verified: Local static verification only — security scan reported clean; brace and parenthesis balance tokenizer reported `OK` across all 13 modified `.kt` files; targeted `grep` confirmed expected symbol updates and preserved `isLogKeeperEnabled`; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is not present here; Kotlin/Android compilation runs on GitHub Actions CI after export, and runtime behavior is not tested on device.
* Any deviation from what was requested: None (`LogKeeper` / `LogCatcher` retained active as explicitly requested).
* Any known issue or follow-up needed: Export/push to GitHub Actions to run `assembleDebug` and `assembleMinifiedDebug` and execute the On-Device Verification Guide.

## Phase 141: CI Kotlin Compilation Fix — ExoPlayer `pauseAtEndOfMediaItems` Resolution (Option 1)
* Timestamp: 2026-11-14T13:53:30Z
* One-line summary: Resolved the two `Unresolved reference 'pauseAtEndOfMediaItems'` Kotlin CI errors by safely casting/resolving `ExoPlayer` instead of invoking it on `Player`-typed references.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/components/FloatingVideoPlayerOverlay.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `app/src/main/java/shura/media/service/PlaybackService.kt`: Updated `ACTION_LOOP` (`L595`) from `player.pauseAtEndOfMediaItems = ...` (where `session.player` is typed as `androidx.media3.common.Player` via `ForwardingPlayer`) to `((player as? androidx.media3.exoplayer.ExoPlayer) ?: PlayerManager.exoPlayer)?.pauseAtEndOfMediaItems = (player.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF)`.
  - `app/src/main/java/shura/media/ui/components/FloatingVideoPlayerOverlay.kt`: Updated loop button click (`L544`) from `controller.pauseAtEndOfMediaItems = ...` (where `player` parameter is typed as `Player?`) to `((controller as? androidx.media3.exoplayer.ExoPlayer) ?: shura.media.service.PlayerManager.exoPlayer)?.pauseAtEndOfMediaItems = (nextMode == Player.REPEAT_MODE_OFF)`.
* How it was verified: Static verification only — verified all 15 `pauseAtEndOfMediaItems` sites across `app/src/main/java` resolve through `ExoPlayer` / `ExoPlayer?` receivers, verified cross-file Phase 140 symbol signatures, verified curly/paren balance (`0`/`0`) on both edited `.kt` files, confirmed clean workspace security scan, and executed `compile_applet` and `lint_applet` for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; CI `assembleDebug` / `assembleMinifiedDebug` awaits GitHub push and on-device testing has not run.
* Any deviation from what was requested: None (Option 1 implemented cleanly).
* Any known issue or follow-up needed: Push to GitHub to run CI `assembleDebug` & `assembleMinifiedDebug` and verify `:app:minifyMinifiedDebugWithR8` plus runtime behavior on-device.

## Phase 142: CI Kotlin Compilation Fix — `PlayerManager` JVM Setter Declaration Clash (Option 1)
* Timestamp: 2026-11-15T17:01:00Z
* One-line summary: Resolved the `Platform declaration clash (setOverlayProgressTickerActive(Z)V)` Kotlin CI error in `PlayerManager.kt` by renaming the internal flag to `@Volatile private var overlayTickerActive: Boolean = false` while keeping `fun setOverlayProgressTickerActive(active: Boolean)` public.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `app/src/main/java/shura/media/service/PlayerManager.kt`: Replaced `@Volatile var isOverlayProgressTickerActive: Boolean = false` (`L78-79`) with `@Volatile private var overlayTickerActive: Boolean = false`, and updated the 6 internal read/write sites (`L227`, `L235`, `L507`, `L521`, `L524`, `L626`). Kept `fun setOverlayProgressTickerActive(active: Boolean)` (`L506`) public so external callers in `MiniPlayerOverlay.kt` and `PlaybackService.kt` remain untouched.
* How it was verified: Static verification only — confirmed zero remaining `isOverlayProgressTickerActive` occurrences across `app/src/main/java/shura/media`, confirmed repo-wide Kotlin/JVM `var` vs `fun` setter/getter clash scan returned no clashes (excluding non-clashing `SettingsManager.instance` vs `getInstance(Context)`), verified balanced braces/parentheses (`0`/`0`) on `PlayerManager.kt`, confirmed clean workspace security scan, and executed `compile_applet` + `lint_applet` for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; CI `assembleDebug` / `assembleMinifiedDebug` (including R8 `minifyMinifiedDebugWithR8`) awaits GitHub push and on-device testing has not run.
* Any deviation from what was requested: None (Option 1 implemented cleanly).
* Any known issue or follow-up needed: Push to GitHub to run CI `assembleDebug` & `assembleMinifiedDebug` and verify `:app:minifyMinifiedDebugWithR8` plus runtime behavior on-device.

## Phase 143: Library Folder Menu Anchor, Bookmark Ordering, Option A New-Count Tag & Loop OFF End-of-Item Playback Fix
* Timestamp: 2026-11-15T17:42:00Z
* One-line summary: Anchored `FolderCard` 3-dots `DropdownMenu`, added persisted bookmark-first folder ordering (no separator/header) and `Option A` folder new-count badges in Main Library, and fixed Loop OFF (`REPEAT_MODE_OFF`) non-last item auto-advance / `Play`-at-end / `markAsFinished` / `ON_RESUME` / `MiniPlayerOverlay` Prev-Next bugs.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/data/SettingsManager.kt
  - app/src/main/java/shura/media/data/BackupManager.kt
  - app/src/main/java/shura/media/data/MediaRepository.kt
  - app/src/main/java/shura/media/ui/screens/MediaViewModel.kt
  - app/src/main/java/shura/media/ui/screens/MainScreen.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/MainActivity.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `SettingsManager.kt` & `BackupManager.kt`: Added `bookmarkedFolders: StateFlow<Set<String>>` persisted under `"bookmarked_folders"` with `addBookmarkedFolder`, `removeBookmarkedFolder`, and `toggleBookmarkedFolder`, plus backup serialization and restore support.
  - `MediaRepository.kt` & `MediaViewModel.kt`: Moved `hasNew` out of `MediaFolder` primary constructor into the class body alongside `val newCount: Int = mediaItems.count { it.tag == PlaybackTag.NEW }`, and simplified `MediaViewModel.syncPlaybackTags()` `folder.copy(mediaItems = updatedItems)` so both `markAsStarted()` and `syncPlaybackTags()` automatically keep `newCount` and `hasNew` accurate.
  - `MainScreen.kt`: Removed the unanchored `folderForOptionsMenu` `DropdownMenu` outside `LazyColumn` and placed `DropdownMenu` inside a `Box` wrapping `FolderCard`'s `MoreVert` `IconButton`; added `"Bookmark Folder"` / `"Remove Bookmark"` and `"Exclude Folder"` items; sorted `mediaFolders` with bookmarked folders first regardless of `SortOrder.NAME` / `SortOrder.DATE` without separator lines or section text; added subtle `Icons.Filled.Bookmark` next to bookmarked folder titles and `Option A` small count badge (`TopEnd`) alongside `"NEW"` (`TopStart`) on the `80.dp` folder icon box (Main Library only).
  - `PlayerManager.kt`, `PlayerScreen.kt`, `MiniPlayerOverlay.kt`, `MainActivity.kt`: Tracked `lastObserved*` and `pendingAutoEnded*` in `PlayerManager.kt` so when `pauseAtEndOfMediaItems = true` transitions `REASON_AUTO` -> `PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` at the end of a non-last item under `REPEAT_MODE_OFF`, `PlayerManager` marks the *true* finished item via `SettingsManager.markAsFinished(...)` and rewinds paused (`playWhenReady = false`, `seekTo(endedIdx, 0L)`) back onto the finished item (and marks `STATE_ENDED` items on the last item). Updated `PlayerScreen.kt` to skip `markAsOpened` on passive `REASON_AUTO` peeks under `REPEAT_MODE_OFF` and stopped rewinds, reset `wasPlayingBeforePause = false` cleanly on `END_OF_MEDIA_ITEM`, and guard `ON_RESUME` against `STATE_ENDED`. Updated `MiniPlayerOverlay.kt` Prev/Next buttons to handle `STATE_ENDED`/`STATE_IDLE` (`prepare()`/`play()`) and wrapping consistently, and ensured `MainActivity.kt` sets `pauseAtEndOfMediaItems = (repeatMode == REPEAT_MODE_OFF)` on direct queue setup.
* How it was verified: Static verification only — brace and parenthesis tokenizer returned `0`/`0` across all 9 edited `.kt` files; repo-wide Kotlin/JVM `var` vs `fun` setter/getter scan found zero clashes (excluding non-clashing `SettingsManager.instance` vs `getInstance(Context)`); clean workspace security scan; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; CI `assembleDebug` / `assembleMinifiedDebug` awaits GitHub push and on-device testing has not run.
* Any deviation from what was requested: Added a subtle `16.dp` bookmark icon next to bookmarked folder titles (without adding any separator line or section header text) for visual clarity; kept explicit Next button wrapping on last item consistent with Notification Prev/Next while stopping auto-advance and `Play`-at-end next-file playback under Loop OFF.
* Any known issue or follow-up needed: Push to GitHub to run CI `assembleDebug` & `assembleMinifiedDebug` and execute the On-Device Verification Guide.

## Phase 144: CI Lint Vital Fix — `widget_media.xml` Duplicate `widget_btn_refresh` ID (Option 1A)
* Timestamp: 2026-11-15T20:38:31Z
* One-line summary: Removed the duplicate `@+id/widget_btn_refresh` `ImageView` from the Top Bar of `widget_media.xml` (Option 1A) to resolve the `:app:lintVitalMinifiedDebug` `DuplicateIds` CI failure.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/res/layout/widget_media.xml
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `app/src/main/res/layout/widget_media.xml`: Removed the duplicate `<ImageView android:id="@+id/widget_btn_refresh">` block from the Top Bar (`L30–37`), retaining the Controls Row 2 `widget_btn_refresh` button alongside `widget_btn_shuffle` and `widget_btn_loop` so `MediaWidgetProvider.kt` binds `R.id.widget_btn_refresh` unambiguously.
* How it was verified: Static verification only — Python `ElementTree` parse and duplicate-ID scan across all XML files under `app/src/main/res/` confirmed zero duplicate IDs and valid XML; `grep` confirmed `@+id/widget_btn_refresh` appears once in `widget_media.xml`; security scan was clean; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; CI `:app:lintVitalMinifiedDebug` & `assembleMinifiedDebug` awaits GitHub push and on-device testing has not run.
* Any deviation from what was requested: None (Option 1A implemented as approved).
* Any known issue or follow-up needed: Export/push to GitHub to run CI `assembleDebug` and `assembleMinifiedDebug` (including `:app:lintVitalMinifiedDebug`) and verify widget refresh tap behavior on-device.

## Phase 145: Option 1 End-of-Playback Auto-Close Across All 5 Targets (`REPEAT_MODE_OFF`)
* Timestamp: 2026-11-15T22:23:01Z
* One-line summary: Implemented Option 1 end-of-playback auto-close across Main Player, Floating Video Overlay, Mini Player Overlay, Background Audio Service/Notification, and Home Screen Widget reset when `repeatMode == Player.REPEAT_MODE_OFF`, marking finished media `DONE` cleanly without sticking or rewinding paused at `0:00`.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `PlayerManager.kt`: Added `isHandlingNaturalEnd` guard, `playbackEndedCloseEvent: SharedFlow<Long>`, and `handleNaturalItemCompletion(...)` triggered on both `Player.STATE_ENDED` and `PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM` when `repeatMode == Player.REPEAT_MODE_OFF` (including non-last items with `pauseAtEndOfMediaItems = true`), marking the true finished item via `SettingsManager.markAsFinished(...)`, guarding `flushProgressToStorage()` while `isHandlingNaturalEnd` is active, and emitting `playbackEndedCloseEvent` instead of rewinding paused to `0:00`.
  - `PlayerActivity.kt` & `PlayerScreen.kt`: Added `playbackEndedCloseEvent` collectors in both `PlayerActivity` (`finish()`) and `PlayerScreen` (`playerViewRef.value?.player = null`, orientation reset, `PlaybackService.stopPlaybackAndRelease(context)`, `onNavigateBack()`), guarded `PlayerScreen` periodic/lifecycle/back `savePlaybackState` calls with `!settings.isFinished(...)`, and guarded `ON_RESUME` against finishing/destroyed activities.
  - `PlaybackService.kt`: Collected `playbackEndedCloseEvent` and handled `STATE_ENDED` under `REPEAT_MODE_OFF` via `handleNaturalPlaybackEndClose()` (`hideOverlay()`, `resetSessionFloatingState()`, stop/clear `exoPlayer`, `stopForeground` + `cancelAll()`, `mediaSession.release()`, `PlayerManager.release()`, `updateWidgetUI()`, `stopSelf()`), reset `isClosingOnEnd = false` on `STATE_BUFFERING`/`isPlaying == true`, and updated notification `ACTION_CLOSE` to reset floating state and update widget UI.
  - `MiniPlayerOverlay.kt`: Added `isScrubbing` / `scrubProgress` / `wasPlayingBeforeScrub` drag protection pausing during slider seek and clamping preview seeks to `(duration - 100L).coerceAtLeast(0L)` so right-edge drags do not prematurely close the overlay prior to finger release.
  - `MediaWidgetProvider.kt` & `PlaybackService.kt`: Added `MediaWidgetProvider.resetAllWidgetsToIdle(context)` and updated `updateAppWidget()` / `updateWidgetUI()` to clear `widget_title` to `"No Media"`, reset `widget_progress` to `0`, restore `ic_widget_play`, and reset loop/shuffle icon tints when idle/empty. Preserved `REPEAT_MODE_ALL`, `REPEAT_MODE_ONE`, manual Next/Prev, editor preview players, and `LogKeeper`/`LogCatcher`.
* How it was verified: Static verification only — Python delimiter tokenizer returned balanced `curly=0, paren=0, bracket=0` across all 6 edited `.kt` files; targeted `grep` sweep confirmed removal of Phase 143 pause-on-end messages, verified all `PlayerScreen.kt` `savePlaybackState` call sites are guarded with `!settings.isFinished(...)`, and confirmed `playbackEndedCloseEvent` / `resetAllWidgetsToIdle` / `LogKeeper` integrity; workspace security scan was clean; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; Android CI compilation (`assembleDebug` / `assembleMinifiedDebug`) and on-device runtime behavior are not tested locally and await GitHub Actions / on-device verification.
* Any deviation from what was requested: None (Option 1 implemented across all 5 targets).
* Any known issue or follow-up needed: Push/export to GitHub Actions to run `assembleDebug` and `assembleMinifiedDebug` and run the On-Device Verification Guide across Main Player, Floating Video Overlay, Mini Player Overlay, Background Audio Service/Notification, and Home Screen Widget.

## Phase 146: Single-Owner `PlaybackService`, Unified Notification (`1001`), and Option 1 Codec/OOM/Surface Stability
* Timestamp: 2026-11-16T09:33:30Z
* One-line summary: Refactored ExoPlayer ownership exclusively into `PlaybackService : MediaSessionService`, removed Notification `1002` (`"Floating player ready"`) so a single system Media Notification (`1001`) persists across Main/Floating/Mini/Bubble/Background modes, and fixed the `c2.unisoc.vp9.decoder` released-state crash, Launcher OOM buffer spike, and mid-init `replaceMediaItem`/surface races.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/MyBitmapLoader.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/ui/components/FloatingVideoPlayerOverlay.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `MyBitmapLoader.kt`: Removed `MediaMetadataRetriever` video frame decoding (`getScaledFrameAtTime` / `frameAtTime`) while retaining Coil cache (`imageLoader`), `contentResolver.loadThumbnail`, and audio `embeddedPicture` extraction so `MyBitmapLoader` never spawns a second HW video decoder alongside ExoPlayer.
  - `PlayerManager.kt`: Restricted `ExoPlayer` creation/teardown to `internal fun initializeOwnedPlayer` / `releaseOwnedPlayer`; added `ownedPlayerFlow`, `PendingPlaybackRequest` + `stagePlaybackRequest(...)`, and `playbackErrorEvent`; capped `DefaultLoadControl` (`15_000ms`/`30_000ms`, `24MB` targetBufferBytes, `prioritizeTimeOverSizeThresholds = false`).
  - `PlaybackService.kt`: Removed legacy `1002` fallback notification and `"playback_fgs_channel"`; added `applyStagedPlaybackRequest()`, `promoteSessionNotificationNow()`, `ensureForegroundNotificationSlotForOverlayStart()` (`1001` on `DEFAULT_CHANNEL_ID` only), `applyStagedPlaybackOrStart()`, and `openOverlay()`; moved debounced codec recovery (`blacklistDecoder` -> `stop()` -> `150ms` delay -> `prepare()/seekTo()/play()`) solely into `PlaybackService.onPlayerError`; kept `mediaSession` alive until `onDestroy()`.
  - `PlayerActivity.kt`, `PlayerScreen.kt`, `MainActivity.kt`, `MediaWidgetProvider.kt`, `FloatingVideoPlayerOverlay.kt`: Migrated all `PlayerManager.initialize()` / `release()` and raw overlay broadcast callers to `stagePlaybackRequest()` + `PlaybackService.applyStagedPlaybackOrStart()` / `openOverlay()` / `stopPlaybackAndRelease()`; bound `MediaController` via `SessionToken` in `PlayerScreen`; removed `PlayerScreen`'s duplicate `onPlayerError` retry loop and `nm?.cancelAll()`; deferred `replaceMediaItem` subtitle/name enrichment until `STATE_READY`; detached `PlayerView.player = null` in `FloatingVideoPlayerOverlay.onRelease` and Main Player transitions before switching overlays.
* How it was verified: Static verification only — Python delimiter tokenizer verified `curly=0, paren=0, bracket=0` across all 8 edited `.kt` files; repo-wide `grep` confirmed zero remaining `PlayerManager.initialize(`, `PlayerManager.release(`, `playback_fgs_channel`, `"Floating player ready"`, or `nm?.cancelAll` calls; clean workspace security scan; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; Android CI build (`assembleDebug` / `assembleMinifiedDebug`) awaits GitHub push and on-device runtime testing has not run.
* Any deviation from what was requested: Withheld `io.github.anilbeesetti:nextlib-media3ext` (`NextRenderersFactory`) binary dependency as agreed under Condition 2 (*"Unless it would interfere with features of app. That is working and stable"*) because `nextlib-media3ext` bundles `libavcodec.so` (Media3 `1.7.x+`), which collides with `ffmpeg-kit-android-16kb-lts-full-gpl:v1.0.7`'s `pickFirsts` `libavcodec.so` in `app/build.gradle.kts`; adopted NextPlayer's single-owner `PlaybackService : MediaSessionService` + matured `DefaultRenderersFactory` architecture instead.
* Any known issue or follow-up needed: Push/export to GitHub Actions to run `assembleDebug` & `assembleMinifiedDebug` and run the On-Device Verification Guide (especially VP9 decoder fallback, launcher stability on HD videos, notification persistence across overlay/Main switches, and swipe-away restore).

## Phase 147: Single-Owner Playback Audit Gap Fixes (Reentry, Stop/Restart Race, Codec Retry Reset, Overlay & Widget Hygiene)
* Timestamp: 2026-11-16T18:22:00Z
* One-line summary: Resolved all 7 High/Medium logic bugs and 2 minor hygiene items surfaced by the post-Phase-146 code audit across `PlaybackService`, `PlayerActivity`, `PlayerScreen`, `MiniPlayerOverlay`, `MediaWidgetProvider`, and `MyBitmapLoader`.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - app/src/main/java/shura/media/MyBitmapLoader.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `PlaybackService.kt`: Added `isServiceStopping`, `isServiceDestroying`, and `requestStopSelf()`; updated `applyStagedPlaybackOrStart()` and `openOverlay()` to re-issue `startService`/`startForegroundService` when `isServiceStopping == true` so pending `stopSelf()` calls are cancelled and delayed `MediaController` unbinds cannot destroy newly started playback; reset `isRecoveringCodecError = false` immediately before `activePlayer.prepare()` inside `codecRecoveryJob` so Attempt 2 executes if Attempt 1 fails before `STATE_READY`.
  - `PlayerActivity.kt` & `PlayerScreen.kt`: Resolved empty-URI (`ACTION_OPEN_PLAYER` notification/widget) launches by recovering the active `exoPlayer?.currentMediaItem` URI or redirecting to `MainActivity` when idle, and guarded `PlayerScreen` against staging an empty `MediaItem`; seeded `tracksVideoSupport` and active title/URI from `controller.currentTracks` / `currentMediaItem` on mount; ensured `pendingQueue` is consumed even when matching the currently active `mediaId`; set `PlayerManager.isPlayerScreenVisible = true` before `preparePlaybackImmediately` and cleared `sessionIsMinimized = false` on Main Player open/reentry; added synchronous `file://` sibling subtitle check and `activeItem.mediaMetadata` copying in `PlayerActivity` while removing the redundant duplicate `content://` `DISPLAY_NAME`/subtitle `replaceMediaItem` block in `PlayerScreen`.
  - `MiniPlayerOverlay.kt`, `MediaWidgetProvider.kt`, `MyBitmapLoader.kt`: Removed stale `onMinimize()` from `MiniPlayerOverlay` expand-to-main action, replaced `player?.stop(); clearMediaItems()` in Stop with `flushProgressToStorage(); onClose()`, and updated folder (`isImplicitFolderQueue = true`) / playlist (`false`) item clicks + `updateSnapshot`; moved `MediaWidgetProvider` `ACTION_CLOSE` before `MediaController.Builder` binding; cleaned unused `videoFrameMillis` import and restricted `MyBitmapLoader` `MediaMetadataRetriever` fallback to local/content audio URIs.
* How it was verified: Static verification only — Python delimiter tokenizer verified balanced `curly=0, paren=0, bracket=0` across all 6 edited `.kt` files; targeted `grep` confirmed expected symbols and clean workspace security scan; `compile_applet` and `lint_applet` verified the workspace toolchain. Local `./gradlew` / Android SDK is absent; Android Gradle compilation (`assembleDebug` / `assembleMinifiedDebug`) runs on GitHub Actions CI after export and on-device testing has not run.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: Push/export to GitHub Actions to verify `:app:compileDebugKotlin`, `assembleDebug`, and `assembleMinifiedDebug`, then run the On-Device Verification Guide across notification/widget reentry, rapid stop-to-replay, overlay switches, and codec fallback.

## Phase 148: CI `:app:compileDebugKotlin` Compilation Fix (`PlaybackService`, `PlayerManager`, `PlayerScreen`)
* Timestamp: 2026-11-16T19:08:00Z
* One-line summary: Resolved all 6 `:app:compileDebugKotlin` unresolved reference errors reported by the GitHub Actions CI build (`4343742d6cb1c212fc6148521af8d236513f9258`).
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `app/src/main/java/shura/media/service/PlaybackService.kt`: Replaced the `if (forceForeground)` / `triggerNotificationUpdate()` calls (`L123-131`) in `promoteSessionNotificationNow` with `onUpdateNotification(session, forceForeground)` (compatible with `androidx.media3:media3-session:1.5.1`).
  - `app/src/main/java/shura/media/service/PlayerManager.kt`: Added `import kotlinx.coroutines.flow.asSharedFlow` (`L5`) to resolve `playbackEndedCloseEvent` and `playbackErrorEvent` `.asSharedFlow()` calls.
  - `app/src/main/java/shura/media/ui/screens/PlayerScreen.kt`: Moved the `LaunchedEffect(Unit)` `playbackEndedCloseEvent` collector (`L368-381`) immediately after `val playerViewRef` (`L438`, shifted to `L437-452`) so `wasPlayingBeforePause` and `playerViewRef` are in lexical scope.
* How it was verified: Static verification only — Python delimiter tokenizer verified `curly=0, paren=0, bracket=0` across `PlaybackService.kt`, `PlayerManager.kt`, and `PlayerScreen.kt`; `grep` confirmed zero remaining `triggerNotificationUpdate` references and clean workspace security scan; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; CI `:app:compileDebugKotlin`, `assembleDebug`, and `assembleMinifiedDebug` run on GitHub Actions after export, and on-device runtime testing has not run.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: Push/export to GitHub Actions to run `assembleDebug` and `assembleMinifiedDebug` (including R8 + `lintVitalMinifiedDebug`), then run the On-Device Verification Guide on the resulting APK.

## Phase 149: PlaybackService Loop OOM Fix, LogCatcher Bounded Rotation (Option B), 5-Min Pause Grace, Background Play Persistence & External Filename Cleanup
* Timestamp: 2026-11-17T09:55:00Z
* One-line summary: Eliminated the `PlaybackService.onStartCommand` `cmd == null` feedback OOM crash loop, hardened `LogCatcher` with bounded batched disk I/O + coalescing + Option B capped error-preserving Downloads rotation, added 5-minute paused notification foreground grace, persisted session `backgroundPlayEnabled` across notification/overlay transitions, and cleaned external URIs to display filenames without extensions.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/LogCatcher.kt
  - app/src/main/java/shura/media/data/MediaUriResolver.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `PlaybackService.kt`: Removed the `else if (... playWhenReady == true) promoteSessionNotificationNow(true)` fallback branch for `cmd == null` inside `onStartCommand()`, gated `onStartCommand` logging to non-null commands, tagged `applyStagedPlaybackOrStart()` intents with `"ACTION_APPLY_STAGED"`, added 5-minute `pauseGraceJob` (`PAUSE_GRACE_DURATION_MS = 300_000L`) with `onUpdateNotification(session, startInForegroundRequired)` override keeping paused notifications foregrounded for 5 minutes before clean detachment/stop, reset session background play on close/natural end/timer expiry, and used `PlayerManager.getResolvedTitleForItem` / `MediaUriResolver.extractCleanTitleFromUri` for notification, widget, and `ACTION_PLAY_FILE`.
  - `LogCatcher.kt` (Option B): Replaced per-line `diskScope.launch` + `toList()` allocations with bounded `Channel<LogEntry>(capacity = 512, DROP_OLDEST)`, single `BufferedWriter` batch loop (`DISK_BATCH_MAX = 64`), 2000ms consecutive duplicate coalescing + 250ms UI flow throttling, 2 MB compaction preserving `ERROR`/`CRASH` blocks ±15 lines + 250-block tail, 10s auto-dump cooldown with in-place compaction, and Downloads auto-dump capping (`MAX_AUTO_DUMP_FILES = 3`, hard cap `6` when all contain errors; never deleting `Vianbrplay_crash_*` or manual `Vianbrplay_logs_*`).
  - `MediaUriResolver.kt`, `PlayerManager.kt`, `PlayerActivity.kt`, `PlayerScreen.kt`, `MainActivity.kt`, `MediaWidgetProvider.kt`: Added `cleanDisplayName`, `extractCleanTitleFromUri`, and `queryCleanDisplayName` (decoding URIs, stripping SAF volume prefixes & directories, and hiding known media extensions); hoisted `backgroundPlayEnabled: StateFlow<Boolean>` and `updateActiveTitleOverride` into `PlayerManager`; updated audio detection to check `isAudioOnlyHint` / `tracksVideoSupport == false` (`file://` audio included); prioritized active `mediaId` on notification/widget reentry so Background Play persists across Main <-> Floating <-> Mini <-> Notification transitions; and dispatched immediate UI title updates on IO resolution without waiting 2s for `STATE_READY`.
* How it was verified: Static verification only — Python delimiter tokenizer verified balanced `curly=0, paren=0, bracket=0` across all 8 modified `.kt` files; targeted `grep` audit verified removal of the `cmd == null` `promoteSessionNotificationNow` feedback branch, presence of 5-minute grace timer, Option B LogCatcher methods, hoisted `backgroundPlayEnabled` Flow, and clean workspace security scan; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; Android Gradle compilation (`assembleDebug` / `assembleMinifiedDebug`) runs on GitHub Actions CI after export and on-device testing has not run.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: Push/export to GitHub Actions to verify `assembleDebug` & `assembleMinifiedDebug` and run the On-Device Verification Guide across `LogCatcher` memory/Downloads retention, 5-minute paused notification window, Background Play reentry persistence, and external SAF/content URI filenames.

## Phase 150: Full-Repo Stability, Security & Lightweight/On-Demand Hardening (Phases 1–5)
* Timestamp: 2026-11-19T20:52:00Z
* One-line summary: Executed the approved 5-phase security, stability, and lightweight optimization plan across the codebase while keeping Fresco, replacing single-use `media3-transformer` with `FFmpegKit`, migrating Playback History from `vianbr_settings.xml` into Room (`v5`), and replacing `material-icons-extended`/`material-icons-core` with explicit `AppIcons` vector assets.
* Files touched:
  - BLUEPRINT.md
  - gradle/libs.versions.toml
  - app/build.gradle.kts
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/shura/media/BatchActionActivity.kt
  - app/src/main/java/shura/media/LogCatcher.kt
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/VideoThumbnailFetcher.kt
  - app/src/main/java/shura/media/data/AppDatabase.kt
  - app/src/main/java/shura/media/data/CacheManager.kt
  - app/src/main/java/shura/media/data/MediaRepository.kt
  - app/src/main/java/shura/media/data/MediaUriResolver.kt
  - app/src/main/java/shura/media/data/PlaybackHistoryDao.kt
  - app/src/main/java/shura/media/data/PlaybackHistoryEntity.kt
  - app/src/main/java/shura/media/data/SettingsManager.kt
  - app/src/main/java/shura/media/service/CenterChannelAudioProcessor.kt
  - app/src/main/java/shura/media/service/CompressionService.kt
  - app/src/main/java/shura/media/service/FFmpegService.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/ui/EditorActivity.kt
  - app/src/main/java/shura/media/ui/PlayerActivity.kt
  - app/src/main/java/shura/media/ui/components/FloatingVideoPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/icons/AppIcons.kt
  - app/src/main/java/shura/media/ui/navigation/AppNavigation.kt
  - app/src/main/java/shura/media/ui/screens/AudioSettingsScreen.kt
  - app/src/main/java/shura/media/ui/screens/AudioTrimmerScreen.kt
  - app/src/main/java/shura/media/ui/screens/LoggerScreen.kt
  - app/src/main/java/shura/media/ui/screens/MainScreen.kt
  - app/src/main/java/shura/media/ui/screens/MediaViewModel.kt
  - app/src/main/java/shura/media/ui/screens/PermissionsManagerScreen.kt
  - app/src/main/java/shura/media/ui/screens/PhotoEditorScreen.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/ui/screens/PlayerSettingsScreen.kt
  - app/src/main/java/shura/media/ui/screens/PlaylistDetailScreen.kt
  - app/src/main/java/shura/media/ui/screens/PlaylistsScreen.kt
  - app/src/main/java/shura/media/ui/screens/SettingsScreen.kt
  - app/src/main/java/shura/media/ui/screens/VideoEditorScreen.kt
  - app/src/main/java/shura/media/ui/screens/dialogs/AudioTracksDialog.kt
  - app/src/main/java/shura/media/ui/screens/dialogs/SpeedControlsSheet.kt
  - app/src/main/java/shura/media/ui/screens/dialogs/SubtitleTracksDialog.kt
  - app/src/main/java/shura/media/ui/screens/hud/PlayerHudCapsules.kt
  - app/src/main/java/shura/media/ui/screens/player/PlayerBottomBar.kt
  - app/src/main/java/shura/media/ui/screens/player/PlayerGestureModifier.kt
  - app/src/main/java/shura/media/ui/screens/player/PlayerLeftTools.kt
  - app/src/main/java/shura/media/ui/screens/player/PlayerRightTools.kt
  - app/src/main/java/shura/media/ui/screens/player/PlayerTopBar.kt
  - app/src/main/java/shura/media/ui/screens/player/PlayerTransportControls.kt
  - app/src/main/java/shura/media/widget/MediaWidgetProvider.kt
  - app/src/main/java/shura/media/widget/WidgetSearchActivity.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - Phase 1: Fixed `CompressionService.kt` `||` -> `&&` arbitrary `edited_*` source deletion; added `isAppPrivatePath`/`isSafeExternalUri`/`safeStartActivity` in `MediaUriResolver.kt` and enforced them across entry points (`EditorActivity`, `BatchActionActivity`, `PlayerActivity`, `MainActivity`, `PlaybackService`, `CompressionService`, `FFmpegService`, `PhotoEditorScreen`, `AudioTrimmerScreen`, `MediaWidgetProvider`, `PlayerScreen`, `MainScreen`, `PermissionsManagerScreen`, `AppNavigation`); narrowed `MiniMediaActivity` `SEND_MULTIPLE` manifest filter to `image/*`; cleaned orphaned `IS_PENDING = 1` MediaStore rows in `FFmpegService`; added `onTimeout(startId, fgsType)` on `FFmpegService` & `CompressionService`.
  - Phase 2: Fixed horizontal seek runaway (`PlayerGestureModifier.kt`, `PlayerScreen.kt`), `CenterChannelAudioProcessor` underflow (`remaining() >= 4`), `SharingStarted.Eagerly` -> `WhileSubscribed(5000L)` (`MediaViewModel.kt`), Main-thread startup & picker I/O (`AppNavigation.kt`, `VideoEditorScreen.kt`, `AudioTrimmerScreen.kt` + `try/finally { release() }`), `VideoThumbnailFetcher` `"video"` substring match, `PhotoEditorScreen` normalized strokes/bitmap recycle + missing `withContext(Dispatchers.IO)` closing brace caught during final audit, duplicate `file://` `cacheDir` copies & WebP OOM resilience (`FFmpegService.kt`, `VideoEditorScreen.kt`), `PlayerManager.backgroundScope` supervision (`MainActivity.kt`, `CacheManager.kt`, `PlayerManager.kt`), widget `REWIND`/`FFWD` routing (`MediaWidgetProvider.kt`), `PlaybackService.performFullPlaybackTeardown()` + `store.clear()`, and `onMediaItemTransition` same-`mediaId` guard (`PlayerScreen.kt`).
  - Phase 3: Removed `androidx-media3-transformer`; replaced `AudioTrimmerScreen` transformer/untracked thread with cancellable `FFmpegKit` (`-c:a copy` / `aac 192k` fallback); hardened & lightened `LogCatcher.kt` (`ReentrantLock` + crash `tryLock(250ms)`, fast-path `sanitize()`, O(1) `parseHmsToOffsetMs`, `ThreadLocal<SimpleDateFormat>`, streaming `useLines`/`bufferedReader`, no duplicate external mirror write on API 29+ MediaStore success) and `LoggerScreen.kt`.
  - Phase 4: Added `PlaybackHistoryEntity` & `PlaybackHistoryDao`, bumped `AppDatabase` v4 -> v5 with exact-DDL `MIGRATION_4_5` and removed `.fallbackToDestructiveMigration()`, replaced `vianbr_settings.xml` history keys in `SettingsManager` with warm `ConcurrentHashMap` cache backed by serialized `Dispatchers.IO.limitedParallelism(1)` + `Mutex` Room persistence, one-time legacy XML migration + cleanup, 250-row cap, safe `isSafeFileNameKey` stem guard, and `activeMediaUriString` alignment in `PlayerScreen`.
  - Phase 5: Created lazy explicit vector asset registry `AppIcons.kt` covering all 90 used icons, migrated all 25 UI files from `Icons.*` to `AppIcons.*`, removed `androidx-compose-material-icons-core` & `androidx-compose-material-icons-extended` from `libs.versions.toml` and `app/build.gradle.kts`, and deleted `/TEMP_REMEDIATION_PLAN.md`.
* How it was verified: Static verification only — Python tokenizer verified balanced `{}`/`()`/`[]` across all 72 Kotlin files in `app/src` (after catching and fixing 1 missing `}` in `PhotoEditorScreen.kt`); cross-checked every `AppIcons.*` reference across all 25 migrated files against `AppIcons.kt` definitions with 0 mismatches; repo-wide `grep` confirmed zero remaining `material.icons`, `\bIcons.`, `media3-transformer`, `fallbackToDestructiveMigration`, or legacy `getFileNameVariants`; workspace security scan found 0 exposed keystores/credentials/secrets; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; Android Gradle compilation (`assembleDebug` / `assembleMinifiedDebug` / `lintVitalMinifiedDebug`) runs on GitHub Actions CI after export and on-device testing has not run.
* Any deviation from what was requested: None (removed both `material-icons-extended` and `material-icons-core` after verifying zero remaining Material icon references across `app/src`).
* Any known issue or follow-up needed: Push/export to GitHub Actions CI to verify `:app:compileDebugKotlin`, `assembleDebug`, and `assembleMinifiedDebug`, then execute the On-Device Verification Guide (especially Room v4->v5 upgrade, vector icon rendering on dark/light themes & RTL, `AudioTrimmerScreen` export cancellation, and `PhotoEditorScreen` brush/crop save).

## Phase 151: Single-Edit Original Folder Output Routing & Option A Batch Image Compression
* Timestamp: 2026-11-19T14:01:00Z
* One-line summary: Added default-ON single-edit original-folder output saving across Video/Photo/Audio editors (preserving Output Folder for batch jobs, Video Join, and Scoped Storage fallbacks), fixed MainScreen batch image routing (`force_ffmpeg`), and implemented EXIF normalization + Option A equal pixel-area compression in `CompressionService`.
* Files touched:
  - BLUEPRINT.md
  - app/src/main/java/shura/media/data/MediaOutputRouter.kt (created)
  - app/src/main/java/shura/media/data/SettingsManager.kt
  - app/src/main/java/shura/media/data/BackupManager.kt
  - app/src/main/java/shura/media/ui/screens/SettingsScreen.kt
  - app/src/main/java/shura/media/ui/screens/PhotoEditorScreen.kt
  - app/src/main/java/shura/media/ui/screens/VideoEditorScreen.kt
  - app/src/main/java/shura/media/ui/screens/AudioTrimmerScreen.kt
  - app/src/main/java/shura/media/ui/screens/MainScreen.kt
  - app/src/main/java/shura/media/service/FFmpegService.kt
  - app/src/main/java/shura/media/service/CompressionService.kt
  - receipts/RECEIPTS_031.md
* What was actually done:
  - Created `MediaOutputRouter.kt` to resolve source parent folders (`file://`, `MediaStore`, `media.documents`, `externalstorage`), insert into the matching MediaStore collection (`Downloads`/`Video.Media`/`Images.Media`/`Audio.Media`) on API 29+ with `IS_PENDING = 1 -> 0` or writable POSIX files with collision avoidance and `MediaScannerConnection.scanFile`, avoid `_edited_edited` / `_compressed_compressed` chaining via `buildSuffixedFileName()`, and fall back cleanly to the configured SAF Output Folder / app default when restricted by Android.
  - Added `saveSingleEditsToOriginalFolder` (default `true`) to `SettingsManager.kt` and `BackupManager.kt`, plus the `"Save Single Edits in Original Folder"` toggle and `"Output Folder (Batch, Join & Fallback)"` label in `SettingsScreen.kt` (`StorageSettingsPage`).
  - Wired `source_uris`, `is_single_edit`, and `output_suffix = "edited"` in `PhotoEditorScreen.kt`; wired `source_uris` and `is_single_edit = urisToJoin.isEmpty()` in `VideoEditorScreen.kt` (keeping joined exports in Output Folder); wired `AudioTrimmerScreen.kt` single audio trims and `FFmpegService.kt` / `CompressionService.kt` through `MediaOutputRouter` with Toast feedback when Scoped Storage fallback occurs.
  - Fixed `MainScreen.kt` `"Batch Compress"` button so all-image multi-selections no longer force `force_ffmpeg = true` into `FFmpegBatchDialog`, keeping `BatchActionActivity` batch jobs directed to Output Folder (`is_single_edit = false`).
  - Updated `CompressionService.kt` to read `android.media.ExifInterface` orientation before decoding/scaling (rotating/flipping via `Matrix`) and compute Option A equal pixel-area budget scaling (`sqrt(targetPixelBudget / sourcePixels)` with `2x` extreme-edge clamp and `<= 1.0` no-upscale guard) so horizontal and vertical images in the same batch compress consistently.
* How it was verified: Static verification only — Python delimiter tokenizer verified `curly=0, paren=0, bracket=0` across all 10 created/modified Kotlin files, symbol/reference checks verified imports and API-24/29+ guards, and workspace security scan found 0 exposed credentials/keystores; `compile_applet` and `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; Android Gradle build (`assembleDebug` / `assembleMinifiedDebug`) awaits GitHub Actions CI after export and on-device testing has not run.
* Any deviation from what was requested: None (locked decisions 1–4 + Option A implemented cleanly).
* Any known issue or follow-up needed: Push/export to GitHub Actions CI to verify `:app:compileDebugKotlin`, `assembleDebug`, and `assembleMinifiedDebug`, then run the On-Device Verification Guide on Android hardware.

## Phase 152: CI `:app:compileDebugKotlin` & `:app:compileMinifiedDebugKotlin` Fix
* Timestamp: 2026-11-19T14:22:00Z
* One-line summary: Fixed the two Kotlin compilation errors reported by GitHub Actions CI (`AppNavigation.kt` missing `fillMaxSize` import and `VideoEditorScreen.kt` out-of-scope `mimeType` reference).
* Files touched:
  - app/src/main/java/shura/media/ui/navigation/AppNavigation.kt
  - app/src/main/java/shura/media/ui/screens/VideoEditorScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_031.md
* What was actually done:
  - Added `import androidx.compose.foundation.layout.fillMaxSize` in `AppNavigation.kt` so `androidx.compose.ui.Modifier.fillMaxSize()` resolves at line 115.
  - Updated `VideoEditorScreen.kt:957` inside `if (isConverting)` to check `resolvedMimeType == "image/webp" || resolvedMimeType == "image/gif" || extRawInitial == "webp" || extRawInitial == "gif"` instead of referencing the `mimeType` local variable scoped inside `LaunchedEffect(uriString)`.
* How it was verified: Static verification only — Python delimiter tokenizer verified `curly=0, paren=0, bracket=0` across all 69 `.kt` files in `app/src/main/java`, repo-wide Compose extension & coroutine import scan found 0 missing imports, `grep` confirmed zero out-of-scope `mimeType` references in `VideoEditorScreen.kt`, workspace security scan found 0 exposed credentials/keystores, and `compile_applet` + `lint_applet` executed for workspace toolchain verification. Local `./gradlew` / Android SDK is absent; actual `:app:compileDebugKotlin`, `assembleDebug`, and `assembleMinifiedDebug` verification runs on GitHub Actions CI after export and on-device testing has not run.
* Any deviation from what was requested: None (only added `extRawInitial` fallback alongside `resolvedMimeType` so `file://` animated WebP/GIF pre-conversion text renders accurately from frame 1).
* Any known issue or follow-up needed: Export/push to GitHub Actions CI to confirm green `assembleDebug` + `assembleMinifiedDebug` builds, then run on-device QA.

## Phase 153: Auto-Open Active Background Video (Option A), Background-Play Leak Fix & Floating->Main Flash Fix
* Timestamp: 2026-11-20T04:03:00Z
* One-line summary: Fixed the Floating->Main Mini Player flash in `PlaybackService`, stopped overlay entries from forcing `backgroundPlayEnabled = true`, and implemented Option A auto-opening `PlayerActivity` when opening the app during active non-overlay background video playback.
* Files touched:
  - app/src/main/java/shura/media/service/PlayerManager.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - app/src/main/java/shura/media/ui/components/MiniPlayerOverlay.kt
  - app/src/main/java/shura/media/ui/screens/PlayerScreen.kt
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/ui/screens/MainScreen.kt
  - app/src/main/java/shura/media/ui/screens/SettingsScreen.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `PlaybackService.kt` (`hideOverlay`) & `MiniPlayerOverlay.kt`: Reordered `hideOverlay()` so `ComposeView` is set `View.GONE`, lifecycle-stopped, disposed (`disposeComposition()`), removed from `WindowManager`, and nulled *before* setting `isOverlayVideoMode = false` (eliminating the transient Mini Player frame flash on Floating->Main), and added immediate `PlaybackService.hideOverlay(context)` on Mini Player expand click.
  - `PlayerManager.kt`, `PlaybackService.kt`, `PlayerScreen.kt`, `MainActivity.kt`: Added `PlayerManager.isOverlayActive` kept in sync across overlay start/show/hide/teardown, removed forced `setBackgroundPlayEnabled(true)` from `PlayerScreen` (`onMiniPlayerClick`, `onFloatingPlayerClick`) and updated `MainActivity` `MiniMediaActivity`/`PipMediaActivity` launches to initialize via `initSessionBackgroundPlayForNewMedia(...)`, and updated `PlayerScreen` `BackHandler`/TopBar Back/`ON_PAUSE`/`ON_STOP` to preserve overlay playback using `isOverlayActive` without mutating `backgroundPlayEnabled`.
  - `PlayerManager.kt`, `MainActivity.kt`, `PlayerScreen.kt`, `MainScreen.kt`, `SettingsScreen.kt`: Added `isActivelyPlayingBackgroundVideo(context)` (active `isPlaying || playWhenReady`, video tracks / non-audio URI, and `!isOverlayActive && !isFloatingVideoActive && !isPlayerScreenVisible`), wired `MainActivity` (`onCreate`, `onNewIntent`, `onStart`, `onResume`, `onStop`, `onDestroy`) to open `PlayerActivity` on standard launcher/Recents app open, set `suppressLibraryAutoOpenToPlayer = true` on non-overlay Player Back exits until `MainActivity` leaves to background (`onStop`), and guarded MediaStore delete/rename sheets and SAF folder/backup pickers with `suppressNextAppReturnAutoOpen`.
* How it was verified: Static verification only — Python tokenizer check across all `.kt` files (`curly=0, paren=0, bracket=0`), symbol/reference audit of all newly added/updated methods and flags across modified Kotlin files, workspace security scan (0 exposed credentials/keystores), and workspace `compile_applet` + `lint_applet`. Local `./gradlew` / Android SDK is absent; Android Gradle compilation runs on GitHub Actions CI after export and on-device testing has not run.
* Any deviation from what was requested: None (Option A, active playback only, and overlay/background-play decoupling implemented as agreed).
* Any known issue or follow-up needed: Export/push to GitHub Actions CI for `assembleDebug`/`assembleMinifiedDebug`, then execute the On-Device Verification Guide across Floating->Main, Mini->Main, Back-to-Library, and Home/Recents/Launcher re-entry flows.

## Phase 154: Audit Hardening for Auto-Open Suppression, Screen Lock, Widget Search & Overlay Recovery
* Timestamp: 2026-11-20T17:30:00Z
* One-line summary: Applied the 4 audit hardening fixes covering Batch/Share/Permissions suppression guards, PowerManager screen-lock check in `MainActivity.onStop()`, `ACTION_SEARCH` intent consumption in `AppNavigation`, and `isOverlayActive = false` error recovery in `PlaybackService`.
* Files touched:
  - app/src/main/java/shura/media/ui/screens/MainScreen.kt
  - app/src/main/java/shura/media/ui/screens/PermissionsManagerScreen.kt
  - app/src/main/java/shura/media/MainActivity.kt
  - app/src/main/java/shura/media/ui/navigation/AppNavigation.kt
  - app/src/main/java/shura/media/service/PlaybackService.kt
  - BLUEPRINT.md
  - receipts/RECEIPTS_031.md
* What was actually done:
  - `MainScreen.kt` (`L427–430`, `L468–471`) & `PermissionsManagerScreen.kt` (`L80–90`, `L112–118`, `L139–142`, `L268–271`): Wrapped Batch Compress (`BatchActionActivity`), Share (`Intent.createChooser`), `storageLauncher`, `notificationLauncher`, Draw Over Other Apps (`ACTION_MANAGE_OVERLAY_PERMISSION`), and `openAppSettings` with `PlayerManager.suppressNextAppReturnAutoOpen = true` and `false` rollback on failed launch or exception.
  - `MainActivity.kt` (`L113–125`): Checked `PowerManager.isInteractive != false` inside `onStop()` before resetting `PlayerManager.suppressLibraryAutoOpenToPlayer = false` so screen lock/unlock does not clear Back-to-Library suppression.
  - `AppNavigation.kt` (`L163–165`): Added `else if (forceAction == "ACTION_SEARCH") { onIntentConsumed() }` inside `LaunchedEffect(forceAction, initialUris)` so widget-search launches clear `forceAction` and set `allowAutoOpenOnRestart = true`.
  - `PlaybackService.kt` (`L1766–1768`, `L1786–1788`): Added `PlayerManager.isOverlayActive = false` in `openOverlay` `startForegroundService` `catch` and in `hideOverlay(context)` `instance == null` branch.
* How it was verified: Static verification only — Python delimiter tokenizer scanned all `.kt` files (`curly=0, paren=0, bracket=0`), `grep` confirmed all symbols (`suppressNextAppReturnAutoOpen`, `suppressLibraryAutoOpenToPlayer`, `isOverlayActive`, `ACTION_SEARCH`, `POWER_SERVICE`) and single remaining `setBackgroundPlayEnabled` user-toggle call, silent security scan found 0 exposed credentials/keystores, and `compile_applet` + `lint_applet` executed for workspace checks. Local `./gradlew` / Android SDK is absent; Android APK build runs on GitHub Actions CI after export and on-device testing has not run.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: Export/push to GitHub Actions CI to verify `:app:compileDebugKotlin` / `assembleDebug` / `assembleMinifiedDebug`, then run the On-Device Verification Guide.
