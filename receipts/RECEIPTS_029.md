# Receipts Log — Series 029

## Phase 95: README Architecture Reset, Creator & Series Branding & Attribution Sync
* Timestamp: 2026-10-19T01:40:00Z
* One-line summary: Reset and streamlined README.md with comprehensive feature documentation, Creator/Series attribution, low-resource hardware story, and Next Player reference credit.
* Files touched:
  - README.md
  - BLUEPRINT.md
* What was actually done:
  - Reset and restructured `README.md` to reflect the comprehensive state of Vian Media across all application features without excessive low-level implementation details.
  - Formally specified Creator: Lewis and Series: Viabhron.
  - Documented the low-resource origin story (tested on 3GB RAM Android 15 Go Edition, built in Google AI Studio with minor assistance from Claude and ChatGPT).
  - Explicitly specified double-tap strictly toggles Play/Pause (confirming no double tap seek).
  - Added clean attribution credit to Anil Beesetti's Next Player for player task isolation architecture and media notification patterns under open source reference.
* How it was verified: File syntax check and Markdown verification.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: None.

## Phase 96: CI Pipeline Inset Resolution, Speed Curve AAC Audio Encoding & Standalone MIT License
* Timestamp: 2026-10-19T01:50:00Z
* One-line summary: Resolved GitHub Actions CI compilation failure with stable window insets, fixed speed curve video export failure by adding AAC audio encoder and internal filtergraph trimming, and created standalone LICENSE file.
* Files touched:
  - app/src/main/java/com/example/ui/screens/player/PlayerBottomBar.kt
  - app/src/main/java/com/example/ui/screens/VideoEditorScreen.kt
  - LICENSE
  - BLUEPRINT.md
  - receipts/RECEIPTS_029.md
* What was actually done:
  - Resolved GitHub Actions CI compilation failure in `job-logs_cd0a13c8.txt`:
    - Replaced experimental `WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)` with stable `WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)` in `PlayerBottomBar.kt`, fixing `Unresolved reference 'systemBarsIgnoringVisibility'` and `Unresolved reference 'displayCutout'`.
    - Verified `mediaController: Player?` parameter signature in `PlayerBottomBar` and `PlayerScreen`.
    - Verified `import android.net.Uri` in `PlayerActivity.kt`.
  - Resolved speed curve video export failure ("Video file after edit not getting saved after using speed curve"):
    - Added explicit `-acodec aac -b:a 192k` in `VideoEditorScreen.kt` when exporting MP4 videos with speed curve and audio.
    - Added `aresample=48000,aformat=channel_layouts=stereo` to `[a_ramped]` in the filter complex to prevent audio channel and sample rate mismatches between concatenated speed curve segments.
    - Removed conflicting `-ss / -to` CLI arguments from the input line when speed curve is active; trimming is now natively performed within each segment's `sStart = baseStartSec + fStart * activeDurationSec` and `sEnd = baseStartSec + fEnd * activeDurationSec`, preventing output truncation on slowed down playback and avoiding demuxer PTS seek conflicts.
    - Hardened `videoHasAudio` detection from `(audioStr != "no")` to `(audioStr == "yes")` against null metadata to prevent fatal `[0:a]` filter specifier crashes when videos have no audio track.
  - Created root `LICENSE` file under the MIT License with copyright to Lewis (Series: Viabhron) and third-party reference acknowledgment for Next Player.
* How it was verified: AST / bracket balance verification, `lint_applet` clean check, and `compile_applet` passing with 0 errors.
* Any deviation from what was requested: None.
* Any known issue or follow-up needed: None.
