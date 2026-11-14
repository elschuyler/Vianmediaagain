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


