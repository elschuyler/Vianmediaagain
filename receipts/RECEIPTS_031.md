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

