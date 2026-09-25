# Vianbhr Media 🎬🎵

A modern, high-performance, all-in-one Media Player, Video Suite, and Audio Editor for Android. Built from the ground up with **100% Kotlin**, **Jetpack Compose (Material 3)**, and **AndroidX Media3**.

> 💡 **Built With Zero Compromises on Ultra-Low-End Hardware**  
> This entire application was engineered, tested, and iterated **exclusively on a low-end smartphone running Android 15 (Go Edition) with only 3 GB of RAM**, using **Google AI Studio** for code generation & iteration and **GitHub Actions** for automated CI/CD cloud APK compilation—with no laptop, desktop, or local Android Studio environment.

---

## ✨ Features Overview

### 📽️ 1. Advanced Media Player
* **Modern Playback Engine**: Powered by AndroidX Media3 (ExoPlayer, MediaSession, and SessionService).
* **Smart Time & Scrubbing**: Dynamic time display supporting both `MM:SS` and `HH:MM:SS` for long media, interactive seekbar with live scrubbing, and remaining time calculation.
* **Gesture Controls**:
  * Vertical swipe on the left side for Screen Brightness.
  * Vertical swipe on the right side for Media Volume.
  * Double-tap left/right edges for 10-second skip forward/backward.
  * Pinch-to-zoom and pan gesture support.
* **Playback Controls & Tools**:
  * Variable playback speeds (0.25x to 3.0x).
  * Multiple Audio Track & Subtitle Track selection.
  * A-B Looping / Repeat mode.
  * Configurable Sleep Timer.
  * Aspect Ratio switching (Fit, Fill, Stretch, 16:9, 4:3).
  * One-tap UI Lock Mode to prevent accidental touches.
  * Persistent playback position remembering where you left off.
* **Background & Multitasking**:
  * True Background Audio Playback with persistent notification controls.
  * Native Android Picture-in-Picture (PiP) support.
  * Floating Video Overlay (Mini-Player) to watch while using other apps.

---

### ✂️ 2. Video Studio & Editor
* **Frame-Accurate Video Trimmer**: Cut, trim, and export video clips with millisecond precision.
* **Video Joiner / Concatenator**: Merge multiple video clips into a single continuous video.
* **Speed Adjustments & Scaling**: Fast-forward or slow-motion video rendering.
* **Orientation & Transformation**: 90°/180°/270° video rotation and aspect ratio framing.
* **Audio Extraction & Mute**: Strip audio from video or export audio tracks directly.
* **Video Compression**: Reduce file sizes without sacrificing visual clarity.

---

### 🎧 3. Audio Trimmer & Suite
* Precision waveform-assisted audio trimming.
* Millisecond-level start and end point selection.
* Quick audio preview and lossless export.

---

### 🖼️ 4. Photo Editor
* Built-in image editor supporting cropping, rotation, filters, and image adjustments.
* Clean, standalone UI for fast image modifications.

---

### 📂 5. Media Library & Playlists
* Automated folder scanning and smart media categorization (Videos / Audios / Folders).
* Custom playlist creation, sorting, and management.
* Batch operations: multi-select files for batch deletion, renaming, or playlist addition.
* Comprehensive file properties and metadata viewer (resolution, codecs, bitrate, dimensions, duration).

---

### 🛡️ 6. System & Diagnostics (`LogKeeper`)
* **Live In-App Diagnostics**: Integrated `LogKeeper` logging subsystem that tracks player state changes, media transitions, service lifecycles, and error captures in real time.
* **Permissions Manager**: Dedicated permission dashboard to inspect and request media and overlay permissions seamlessly.

---

## 🛠️ Architecture & Tech Stack

* **Language**: 100% Kotlin
* **UI Toolkit**: Jetpack Compose with Material Design 3 (M3)
* **Media Framework**: AndroidX Media3 (ExoPlayer, MediaSession, SessionService)
* **Video Processing**: FFmpeg & Android MediaCodec
* **Concurrency**: Kotlin Coroutines & Flow
* **Target OS**: Android 8.0 (API 26) through Android 15 (API 35)

---

## 🚀 The Development Story

* **Development Environment**: Crafted entirely via conversational engineering on **Google AI Studio**.
* **Device**: Developed and verified on an **Android 15 (Go Edition)** low-end smartphone with **3 GB RAM**.
* **Build Pipeline**: Continuous Integration and automated APK artifact builds handled 100% through **GitHub Actions** (no Android Studio, laptop, or desktop used).

---

## 📦 Building the APK

The APK is built automatically via GitHub Actions on every push to `main`.

1. Go to the **Actions** tab in GitHub.
2. Select the latest workflow run.
3. Download the compiled APK from the **Artifacts** section.

---

## 📄 License
Licensed under the [MIT License](LICENSE).
