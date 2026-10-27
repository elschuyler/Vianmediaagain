# Vian Media 🎬🎵

A clean, ultra-lightweight, all-in-one Media Player, Creative Editors Suite, and Batch Processing utility for Android. Engineered with **100% Kotlin**, **Jetpack Compose (Material 3)**, and **AndroidX Media3**, designed from the ground up to run butter-smooth with zero bloat even on resource-constrained hardware.

* **Creator**: Lewis  
* **Series**: Viabhron  

---

## ⚡ Core Highlights

* **Ultra-Lightweight & Zero-Bloat**: Minimal RAM footprint, zero background telemetry, and consistent 60fps performance across low-end and flagship devices alike.
* **All-in-One Local Utility**: Media Player, Video Studio, Audio Trimmer, Photo Editor, Floating Overlays, and Batch Processing unified into a single standalone app.
* **The Origin Story**: Developed and tested exclusively on a low-end smartphone running **Android 15 (Go Edition) with only 3 GB of RAM**. Crafted inside **Google AI Studio** with automated **GitHub Actions** CI/CD compilation, with very little external help (what little help was received came mainly from **Claude**, with minor assistance from **ChatGPT**).

---

## 🌟 Key Features

### 🎬 Advanced Media Player
* **High-Performance Engine**: Powered by AndroidX Media3 (ExoPlayer) with native asynchronous hardware decoding.
* **Dedicated Task Architecture**: Isolated `PlayerActivity` with zero-latency video preparation, completely separated from the library.
* **Precision Gestures**:
  * Double-tap anywhere on screen strictly toggles **Play / Pause**.
  * Vertical swipe controls **Media Volume & Audio Booster** by default, or **Screen Brightness** when the on-screen brightness slider is active.
  * Horizontal swipe across the canvas to **scrub and seek** with real-time delta preview.
  * Multi-touch pinch-to-zoom, translation pan, and **canvas rotation** (`graphicsLayer`).
* **Playback Suite**: Single-tap speed cycling (`1.0x` -> `1.2x` -> `2.0x` -> `0.5x` -> `0.25x`) and full dialog, multi-track audio & subtitle selection, A-B repeat looping, configurable sleep timer, aspect ratio cycling (Fit, Fill, Stretch, 16:9, 4:3), one-tap UI Lock, and a dedicated center Stop button.

### 🗂️ Smart Folder Library
* **Structured Organization**: Categorized media browsing across Videos, Audios, Playlists, and File Folders.
* **Instant Loading**: JSON-backed persistent disk caching (`media_library_cache.json`) with a 24-hour auto-sync cooldown.
* **Butter-Smooth Scrolling**: 60fps lazy lists powered by `@Immutable` data models and non-recomposing `snapshotFlow` state tracking.
* **Privacy & Control**: Recursive `.nomedia` folder hierarchy exclusion, multi-select batch actions, and temporary smart queues with automated next-day cleanup.

### ✂️ Creative Editors Suite
* **Video Studio Pro**:
  * **Trimming**: Frame-accurate single trim and double-trim (cut middle or keep ends).
  * **Dual-Mode Speed**: Standard speed slider (0.25x–16x) and an interactive **Speed Curve (Wave)** canvas with draggable bezier keyframes for cinematic speed ramping.
  * **Aspect Ratio & Crop**: Freeform custom crop and standard ratio presets with letterbox/pillarbox canvas framing.
  * **Rotation & Flip**: 90°/180°/270° video rotation and horizontal/vertical flipping.
  * **Audio Workstation**: Volume adjustments (0–300%), custom audio track insertion, Mix (background) vs Replace (mute original) modes, and live dual-player preview.
  * **Multi-Video Joiner**: Interactive sequence cards, per-clip Fit/Fill/Stretch/Blur edge modes, position shifting, and clip promotion.
  * **Animation & In-Place Editing**: Native animated WebP/GIF frame extraction to MP4 video and instant in-place re-editing of finished exports.
* **Audio Trimmer**: Waveform-assisted audio trimming with millisecond-level start/end point selection and lossless export.
* **Photo Editor**: Standalone image editor supporting cropping, rotation, adjustments, and visual filters.

### 📦 Batch Compression & Conversion
* **Multi-File Background Processing**: Dedicated batch engine integrated with Android's system Share Sheet (`ACTION_SEND_MULTIPLE`).
* **Batch Image Compression**: Adjust quality, resolution, format (JPEG, PNG, WebP), and resize constraints via `CompressionService`.
* **Batch Video Transcoding**: Convert formats (MP4, MKV, AVI, MOV, WebM, MP3, AAC), scale resolutions (480p, 720p, 1080p), and fine-tune CRF quality via `FFmpegService`.
* **2-Tier Stream Optimization**: Zero-cost magic byte container sniffing that automatically unmasks and smoothens erratic `.ts` and `.m4s` streams into continuous 30fps MP4s.

### 🪟 Floating & Mini Player Overlays
* **Floating Video Player**: Resizable overlay window (`TYPE_APPLICATION_OVERLAY`) with 3-zone double-tap, PiP support, and an **Aspect Ratio Break** toggle enabling true freeform 2D window resizing.
* **Mini Player & Folded Bubble**: Compact audio/video player that seamlessly collapses into a draggable, touch-slop responsive floating bubble with in-session coordinate memory.
* **Media Notification**: Background audio service (`PlaybackService`) featuring a standard 5-button transport layout with full lockscreen artwork delivery.

---

## 🛠️ Architecture & Tech Stack

* **Language**: 100% Kotlin
* **UI Toolkit**: Jetpack Compose & Material Design 3 (M3)
* **Media Engine**: AndroidX Media3 (ExoPlayer, MediaSession, SessionService)
* **Transcoding Engine**: FFmpeg-Kit Full & Android MediaCodec
* **Local Storage & Database**: Room DB & Atomic JSON Caching
* **Concurrency**: Kotlin Coroutines & Flow
* **Target OS**: Android 8.0 (API 26) through Android 16 (API 36)

---

## 🙏 Credits & Acknowledgements

* **[Next Player](https://github.com/anilbeesetti/nextplayer)** by Anil Beesetti — Referenced for architectural inspiration regarding dedicated player task isolation (`taskAffinity`) and clean notification media controls.
* **[AndroidX Media3](https://developer.android.com/media/media3)** & **[FFmpeg-Kit](https://github.com/arthenica/ffmpeg-kit)** for powering the playback and video manipulation engines.
* **Google AI Studio**, **Claude**, and **ChatGPT** for conversational engineering assistance.

---

## 📄 License

Licensed under the [MIT License](LICENSE).
