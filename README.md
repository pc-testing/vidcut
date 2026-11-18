# VidCut

A simple, CapCut-style video editor for Android. Everything stays on the phone — no sign-in, no server.

**Download the latest build:** https://github.com/pc-testing/vidcut/releases/download/latest/vidcut.apk

## Features (v1)
- Home screen with **New project** and recent projects (rename, duplicate, delete)
- Import videos and photos from the gallery
- Timeline: reorder (long-press and drag), trim (drag the white edges), split, delete
- Text overlays: position (drag), size, colour, start and end time
- Background music from the phone with volume; mute or lower the clips' own sound
- Per-clip speed 0.5x–2x
- Canvas 9:16, 16:9, 1:1
- Export to MP4 (720p / 1080p) into the gallery (Movies/VidCut), with progress
- Auto-save, undo / redo
- Dark, Light or Match-phone theme (Settings on the Home screen)

## Tech
Kotlin, Jetpack Compose (Material 3), AndroidX Media3 (ExoPlayer for preview, Transformer for export).
minSdk 26, targetSdk 35, Gradle 8.14.3.

## Code map
- `app/src/main/java/com/pctesting/vidcut/`
  - `MainActivity.kt` — theme and Home ⇄ Editor switching
  - `data/` — project model (JSON), storage and media import, settings
  - `media/` — preview player, exporter, thumbnails
  - `ui/home/` — Home screen and Settings sheet
  - `ui/editor/` — editor state (undo/redo, auto-save), preview, timeline, tool panels, export screens
  - `ui/theme/` — dark and light colour sets
- `.github/workflows/build.yml` — builds the APK on every push to `main` and publishes the "Latest build" release

## Builds
Every push to `main` builds a debug APK on GitHub Actions and replaces the `latest` release.
The APK is signed with the throwaway debug key in `keystore/` so new builds install over old ones.
This is a debug build, not for the Play Store.
