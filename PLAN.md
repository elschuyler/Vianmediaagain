# Implementation Plan: Unified Floating Player, Lightweight Engine & Smooth Library

Inspired by **Next Player** (`anilbeesetti/nextplayer`), this plan establishes a clean, unified architecture where all player surfaces (Main Player, Mini Player, Floating Window, Folded Bubble, Notification, and Background) act as synchronized remote controls connected to a single central playback engine (`PlaybackService` / `PlayerManager`), alongside a fluid, lightweight library.

---

## Mini-Phase 1: Naming & Label Unification [COMPLETED]
- **Goal**: Standardize all terminology across the entire app.
- **Tasks**:
  1. [x] Replace all occurrences of `"Popup Player"`, `"Pop-up"`, and user-facing `"PiP"` with **`"Floating Player"`**.
  2. [x] Update all string resources, settings titles, dialogs, quick menus, and tooltips across `MainScreen`, `PlayerScreen`, and services.

---

## Mini-Phase 2: Floating Window Controls & Folded App Icon [COMPLETED]
- **Goal**: Enhance floating player navigation and refine the minimized state.
- **Tasks**:
  1. [x] **Top Bar Dual Transition Actions**:
     - **Open Full-Screen Player**: Ensure a dedicated expand/fullscreen button in the Floating Player top bar that immediately brings the app to the foreground directly into `PlayerScreen`, re-attaching the video surface seamlessly.
     - **Switch to Mini Player**: Maintain the existing button that docks/minimizes playback into the in-app bottom Mini Player without interrupting audio.
  2. [x] **Folded Bubble Redesign**:
     - Replace generic icons or cropped thumbnails in folded mode with strictly the **compact App Icon**.
     - Style the folded bubble as a clean, small circular icon that docks smoothly against screen edges.

---

## Mini-Phase 3: Player Controls & Brightness Slider HUD [COMPLETED]
- **Goal**: Fix control responsiveness, slider HUD display, and auto-hide timing.
- **Tasks**:
  1. [x] **Brightness Slider HUD**:
     - Connected the brightness button in the player control bar to directly toggle and display the on-screen vertical brightness HUD slider overlay.
     - Enabled vertical swipe gesture on the left half of the screen to adjust brightness with instant HUD feedback.
     - Synchronized slider adjustments with window brightness and real-time percentage feedback (0–100%).
     - Configured a dedicated 3.5s auto-hide timer for the brightness HUD.
  2. [x] **Controls Timeout Reset**:
     - Reset the 4-second auto-hide countdown whenever the user taps or interacts with control buttons, sliders, or the top/bottom shaded gradient scrims.
     - Prevented taps within the shaded control areas from accidentally hiding controls.
     - Maintained empty/unshaded center screen taps as single-tap toggles for control visibility and double-tap for play/pause.

---

## Mini-Phase 4: Lightweight Engine & Surface Detachment [COMPLETED]
- **Goal**: Maximize performance and battery life by treating non-fullscreen modes as audio-only remotes.
- **Tasks**:
  1. [x] **Surface Detachment for Audio-Only Remotes**:
     - Call `PlayerManager.detachVideoSurface()` (`player.clearVideoSurface()`) when entering:
       - In-app bottom Mini Player (`MiniPlayerOverlay`)
       - Folded Floating Bubble (`isMinimizedExternal` in `FloatingVideoPlayerOverlay`)
       - Background playback (`onStop` and back-navigation in `PlayerScreen` when background play is active)
     - Automatically re-attach the video surface when expanding to the Main Player (`PlayerScreen`) or Floating Window (`FloatingVideoPlayerOverlay`).
  2. [x] **Lazy Controller Overlays**:
     - Instantiate and compose heavy dialogs/sheets (equalizer, tracks, sleep timer, bookmarks, speed, and settings) only when explicitly opened on demand.
  3. [x] **Streamlined Bitmaps**:
     - Downsample artwork and frame bitmaps in `MyBitmapLoader` (using `inSampleSize` downsampling and `RGB_565` configuration with max dimensions 256x256) for low-overhead mini player and notification display.

---

## Mini-Phase 5: Library Performance & Smoothness
- **Goal**: Deliver a fluid, instantaneous library experience inspired by Next Player without missing media files.
- **Tasks**:
  1. **Hardware-Accelerated Bitmaps**:
     - Configure thumbnail loading to use hardware-backed bitmaps (`Bitmap.Config.HARDWARE` or `RGB_565`) to halve memory consumption and ensure 60/120fps scrolling.
  2. **Instant Tab Switching**:
     - Preserve tab and list states across Videos, Folders, and Playlists in `MediaViewModel` so switching tabs is instant with zero reload flicker or re-query lag.

---

## Mini-Phase 6: Unified Remote Architecture & Timeline Sync [COMPLETED]
- **Goal**: Guarantee zero timeline drift and seamless resumption everywhere.
- **Tasks**:
  1. [x] **Single Source of Truth**:
     - Ensured all controllers (`MiniPlayerOverlay`, `FloatingVideoPlayerOverlay`, `PlaybackProgressRow`, `PlayerScreen`, and `PlaybackService`) directly observe `PlayerManager.playbackState` StateFlow.
     - Centralized ticker in `PlayerManager.startProgressTicker()` running 500ms intervals when playing, eliminating duplicate or drifting polling loops in UI overlays.
     - Positioned bottom-right action buttons in `FloatingVideoPlayerOverlay` strictly in `[Resize, Minimize, Exit]` order from right to left (Exit, Minimize, Resize from left to right) with integrated resize drag gesture.
  2. [x] **Progress Persistence**:
     - Centralized `PlayerManager.flushProgressToStorage()` committing `currentPosition` to `SettingsManager` on pause (`onIsPlayingChanged = false`), item transition, remote transition (`onSwitchToMiniPlayer`, `onOpenMainPlayer`, `onSwitchToVideo`), player release, and lifecycle `ON_PAUSE` / `onDispose`.
     - Resuming from the library or switching between Mini/Floating/Main player continues from the exact second without reloading or timeline drift.
