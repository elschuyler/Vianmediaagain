# GitHub Actions CI Workflow Reference

This reference documents the Android APK build configuration for GitHub Actions in this repository.

## Active Root Workflow (`.github/workflows/build.yml`)
- **Workflow Name**: Build Android APK
- **Triggers**:
  - `push` to `main`, `master`
  - `pull_request` to `main`, `master`
  - `workflow_dispatch` (Manual run from GitHub Actions UI)
- **Runner**: `ubuntu-latest`
- **JDK**: Java 21 (Eclipse Temurin) via `actions/setup-java@v4`
- **Gradle Action**: `gradle/actions/setup-gradle@v3` (utilizes runner pre-installed Gradle 9.8.0, satisfying AGP 9.1.1's minimum requirement of Gradle ≥ 9.3.1)
- **Build Execution**:
  - Checks for executable `./gradlew`, falls back to `gradle assembleDebug --stacktrace`
- **Artifact Upload**:
  - Uses `actions/upload-artifact@v4`
  - Name: `app-debug.apk`
  - Path: `app/build/outputs/apk/debug/*.apk`

## Historical Vianmedia Workflow (`reference/github-actions/vianmedia_build.yml`)
- Used `gradle/actions/setup-gradle@v3` with implicit runner Gradle and gradle caching under `setup-java`.
- Produced artifact `app-debug.apk`.
