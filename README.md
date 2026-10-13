# Komori

A quiet, lightweight Android manga reader. Built with Kotlin and Compose, with MangaDex as its first source.

**Status: early alpha.** The first end-to-end reading milestone is implemented. This is not yet the complete product in the roadmap.

## Available now

- MangaDex search and paginated discovery, with safe/suggestive catalog filtering.
- Personal library, likes, reading statuses and history stored locally with Room.
- Local author/artist follows and browsing their works.
- Smart Chapters: language filtering, conservative duplicate grouping, preferred scanlation and strict/fallback selection.
- Direct reading from cover play buttons, page resume, restart-current-chapter preference and optional rereading when caught up.
- Tiled, zoomable page reader designed to avoid decoding entire oversized images into RAM.
- Persistent chapter downloads, Wi-Fi constraint, retries, pause/resume, original/data-saving images and offline access.
- Light and dark themes. Android 8.0+; no account, ads or analytics.

MangaDex and scanlation groups are credited in the application. This is an independent client, not an official MangaDex app. Source availability and policy can change.

## Install

Download the APK from [GitHub Releases](https://github.com/joy-arz/komori-reader/releases). This repository is private, so access requires an authorized GitHub account. Alpha releases are signed with the same private release key, allowing future updates without uninstalling.

## Build

Use JDK 17 (JDK 21 also works locally), Android SDK platform 36 and build-tools 35.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/sdk`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Release builds use R8 and resource shrinking. An unsigned local release can be built with `./gradlew assembleRelease`; signed releases require the environment variables documented in [release setup](docs/RELEASING.md).

## Automated releases

Every push to `main` and pull request runs unit tests, lint and a debug APK build. A version tag such as `v0.1.0-alpha.2` automatically tests, builds, signs and publishes a release APK plus SHA-256 checksum. Tags containing a suffix are published as prereleases.

```sh
git tag v0.1.0-alpha.2
git push origin v0.1.0-alpha.2
```

Signing secrets are already configured in repository Actions secrets. No signing material is committed. See [RELEASING.md](docs/RELEASING.md) for key backup and release details.

## Current limits

- Reading position is saved at page granularity, not the zoom/pan position within a page.
- Continuous webtoon scrolling and right-to-left gesture paging are still planned; this alpha uses explicit Previous/Next controls and tiled zoom/pan.
- Creator follows are local. Automatic creator/title update feeds and MangaDex account sync are not implemented.
- Smart Chapters uses deterministic selection, not translation-quality scoring. Unnumbered releases and different volumes stay separate; volume metadata inconsistencies may leave duplicates visible.
- Download work is interruptible and Android may delay it. There is no promise of uninterrupted large background transfers; work checkpoints each page and retries.
- Collection management, backup/restore, storage-management UI and per-title language/reader overrides are planned.
- Low-end performance budgets are goals, not yet verified on a physical 2 GB device.

See [the implementation plan](docs/PLAN.md) and [validation notes](docs/VALIDATION.md).
