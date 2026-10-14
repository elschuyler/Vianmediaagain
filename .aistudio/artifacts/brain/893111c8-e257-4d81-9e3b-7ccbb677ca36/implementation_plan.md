# Implementation Plan: Lightweight, Fast & Modular Main Video Player

Deliver a high-performance, lightweight, and stable Main Video Player inspired by **Next Player** (`anilbeesetti/nextplayer`), specifically optimizing the Android Jetpack Compose player surface and controls while keeping the Editor, Floating Player, and Library strictly untouched.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> This plan focuses **exclusively** on the **Main Player** (`PlayerScreen.kt` and dedicated player sub-components). The Floating Player, Mini Player, Video Editor, and Library modules remain 100% untouched.

* **Decoupling Strategy**: Break the monolithic 2,820-line `PlayerScreen.kt` into distinct, focused leaf composables with isolated recomposition scopes.
* **Recomposition Shielding**: Timeline ticker ticks (500ms) will strictly recompose only the scrub bar and time indicators, not the video surface, controls chrome, or background layers.
* **Lazy Dialog Instantiation**: Heavy dialogs (Audio Tracks, Subtitles, Equalizer, Sleep Timer, Speed, Video Details) will be loaded lazily on demand rather than retained in the active Compose hierarchy.
* **Zero Feature Loss**: All existing player features (AB Repeat, Audio/Sub tracks, Volume/Brightness HUDs, gestures, lock mode, PiP, decoder auto-retry, and LogKeeper auditing) will be strictly preserved.

---

## 1. Overview & Core Concept

### What It Delivers
* **Fast Startup & Fluid Playback**: Instant opening of videos without composition lag, jank, or memory spikes caused by bloated monolithic composables.
* **Ultra-Low Overhead Recomposition**: Position updates (500ms intervals) and gesture drags (60/120fps) are strictly confined to their dedicated HUD and Scrubber layers.
* **Next Player Parity**: Architecture structured similarly to Next Player's clean separation between `PlayerSurface`, `PlayerControls`, `PlayerGestures`, and `PlayerSheets`.

### Scope Boundary
* **Included**: `PlayerScreen.kt` and player UI composables (`PlayerControls`, `PlayerTimeBar`, `PlayerGestures`, `PlayerDialogs`).
* **Excluded**: Video Editor, Floating Player (`FloatingWidgetService`, `FloatingVideoPlayerOverlay`), Mini Player, and Media Library (`MediaViewModel`, `MainScreen`).

---

## 2. Architecture & Component Decomposition

```
┌────────────────────────────────────────────────────────────────────────┐
│                              PlayerScreen                              │
│  (Orchestrator: window flags, back handler, state routing, lifecycle)   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    │                               │                               │
┌───▼─────────────────────┐ ┌───────▼─────────────────────┐ ┌───────▼─────────────────────┐
│    PlayerVideoSurface   │ │    PlayerGestureDetector    │ │    PlayerControlsOverlay    │
│  - AndroidView PlayerView│ │  - Horizontal swipe (seek)  │ │  - Top Header Bar           │
│  - Aspect ratio frame   │ │  - Vertical swipe (vol/bri) │ │  - Center Transport Row     │
│  - Zoom/pan matrix      │ │  - Double-tap (play/skip)   │ │  - Bottom Scrub Bar         │
│  - Zero recomposition   │ │  - Pinch-to-zoom            │ │  - Isolated time ticker     │
└───┬─────────────────────┘ └───────┬─────────────────────┘ └───────┬─────────────────────┘
    │                               │                               │
    └───────────────────────────────┼───────────────────────────────┘
                                    │
                    ┌───────────────▼───────────────┐
                    │      PlayerDialogs (Lazy)     │
                    │  - Audio Track Selector       │
                    │  - Subtitle Track Selector    │
                    │  - Playback Speed Dialog      │
                    │  - Sleep Timer Dialog         │
                    │  - Video Details Dialog       │
                    │  - Equalizer / Audio Boost    │
                    └───────────────────────────────┘
```

---

## 3. Key Improvements & Technical Strategy

### 1. Recomposition Shielding for Playback Ticker
* **Problem in Current Code**: `val playerSnapshot by PlayerManager.playbackState.collectAsState()` is collected at line 314 at the top level of `PlayerScreen`. Every 500ms ticker tick triggers root-level re-evaluation of `PlayerScreen` and 61 `remember` states.
* **Next Player Solution**: Pass progress as a lambda `{ PlayerManager.playbackState.value.currentPosition }` or collect `playbackState` only inside `PlayerTimeBar` and `PlayerProgressRow`. The `PlayerScreen` root and `PlayerVideoSurface` remain completely static during playback.

### 2. Gesture Handling & Drag Feedback Isolation
* **Problem in Current Code**: Dragging on screen updates `activeGesture`, `gestureText`, and `gestureVolumeRatio` within the root scope, causing the entire screen to recompose on every finger move.
* **Next Player Solution**: Isolate gestures into a dedicated `PlayerGestureOverlay`. When dragging, only the lightweight transient HUDs (`VolumeHudOverlay`, `BrightnessHudOverlay`, `SeekHudOverlay`) recompose.

### 3. Lazy Loading of Heavy Dialogs & Sheets
* **Problem in Current Code**: Audio tracks, subtitles, equalizer, speed, and details dialogs have their UI trees parsed and held in composition state even when dismissed.
* **Next Player Solution**: Use conditional composition `if (showDialog) { CompactPlayerDialog(...) }` wrapped in dedicated stateless composables. Zero memory or composition footprint when closed.

### 4. Background Query Elimination
* **Problem in Current Code**: `getDisplayNameFromUri` runs synchronous `ContentResolver` queries (`query(uri, ...)`) within Compose utility functions.
* **Next Player Solution**: Query media metadata once via `MediaMetadata` or background coroutine; avoid blocking Compose layout passes with ContentResolver calls.

### 5. Surface & Codec Lifecycle Stability
* **Next Player Solution**: Ensure `PlayerView.player = null` on disposal and proper surface detachment when switching out of full-screen playback, preventing MediaCodec decoder leak errors on repeated playback sessions.

---

## 4. Phased Implementation Roadmap

### Phase 1: Leaf Component Extraction & Modularization
* Create modular composables for player controls:
  - `PlayerTopControls`: Title, back button, PiP, aspect ratio, audio track, subtitle, speed, and settings toggles.
  - `PlayerCenterTransport`: Previous, 10s Rewind, Play/Pause, 10s Fast-Forward, Next, and Stop buttons.
  - `PlayerBottomBar`: Time display, progress slider, lock toggle, AB repeat markers.
  - `PlayerHudOverlays`: Unified Brightness HUD and Volume HUD.

### Phase 2: Recomposition Scope Decoupling
* Migrate `PlayerManager.playbackState` collection from the root `PlayerScreen` into leaf components.
* Use `derivedStateOf` and lambda reads for fast-changing properties (`currentPosition`, `bufferedPosition`, `currentBrightness`).

### Phase 3: Lazy Dialog Architecture
* Extract dialogs into dedicated modular composable functions:
  - `AudioTracksDialog`
  - `SubtitleTracksDialog`
  - `PlaybackSpeedDialog`
  - `SleepTimerDialog`
  - `MediaDetailsDialog`
* Ensure dialogs are only instantiated when their respective boolean flag is true.

### Phase 4: Verification & Performance Benchmark
* Verify zero regressions across all core features:
  - Playback, seeking, pause, resume, stop.
  - Audio/subtitle track selection.
  - Gestures: swipe volume (right), swipe brightness (left/slider), pinch zoom, double-tap seek.
  - Equalizer and volume boost.
  - AB repeat and sleep timer.
  - Background playback and PiP transitions.
* Gradle compilation and lint validation.

---

## 5. Verification Checklist

1. **Compilation Check**: Run `./gradlew compileDebugKotlin` to ensure 100% type safety and clean build.
2. **Recomposition Profile**: Ensure timeline updates (every 500ms) only redraw the scrubber thumb and time text.
3. **Gesture Smoothness**: 60/120fps gesture response without stutter or frame drops during seek or volume/brightness dragging.
4. **Dialog Dismissal**: Opening and closing tracks/speed/timer dialogs causes zero layout shifts or audio interruptions.
5. **No Regressions Outside Player**: Verify Editor, Floating Player, and Library remain completely unaffected.
