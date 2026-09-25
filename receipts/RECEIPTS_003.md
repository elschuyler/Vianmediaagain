2026-08-05T09:24:00Z
- Requested: implement (implement PiP fixes)
- Touched: app/src/main/java/com/example/ui/screens/PipHelper.kt, app/src/main/java/com/example/MainActivity.kt
- Action: Added `Context.findActivity()` fallback extension inside `PipHelper.kt` to allow correct extraction of the Activity context from `ContextWrapper`. Overridden `onUserLeaveHint()` in `MainActivity.kt` to properly invoke `enterPictureInPictureMode()` if the ExoPlayer instance is currently playing when the user leaves the app (e.g. by pressing the home button).
- Verification: local build only (successful)
