# Komori

A quiet, lightweight Android manga reader. Built with Kotlin and Compose, with MangaDex as its first source.

**Status: early alpha.** The first end-to-end reading milestone is implemented. This is not yet the complete product in the roadmap.

<img src="docs/images/library.png" width="280" alt="Komori library in the first Android alpha">

## Available now

- MangaDex search and paginated discovery, with safe/suggestive catalog filtering.
- Personal library, likes, reading statuses and history stored locally with Room.
- Local author/artist follows and browsing their works.
- Smart Chapters: language filtering, conservative duplicate grouping, preferred scanlation and strict/fallback selection.
- Direct reading from cover play buttons, page resume, restart-current-chapter preference and optional rereading when caught up.
- Continuous webtoon scrolling with saved position within a page, bounded image strips, offline support and a Paged/Webtoon switch.
- Tiled, zoomable paged reader for traditional manga.
- Persistent chapter downloads, Wi-Fi constraint, retries, pause/resume, original/data-saving images and offline access.
- Light and dark themes. Android 8.0+; no account, ads or analytics.

MangaDex must be reachable on your network. If a network redirects or blocks it, the app reports a connection error and keeps certificate verification enabled.

MangaDex and scanlation groups are credited in the application. This is an independent client, not an official MangaDex app. Source availability and policy can change.

## Install

Download [komori-0.2.0-alpha.2.apk](https://github.com/joy-arz/komori-reader/releases/download/v0.2.0-alpha.2/komori-0.2.0-alpha.2.apk), or open [GitHub Releases](https://github.com/joy-arz/komori-reader/releases) and expand **Assets**. Choose the `.apk` file, not the source-code archives. This repository is private, so access requires an authorized GitHub account. Alpha releases are signed with the same private release key, allowing future updates without uninstalling.

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

- Webtoon mode saves the vertical position within each source page. Paged mode saves the page, not its zoom/pan viewport.
- Webtoon scrolling is continuous within a chapter; use Finish & continue at the end. Cross-chapter endless scrolling, immersive controls and right-to-left gesture paging remain planned.
- Creator follows are local. Automatic creator/title update feeds and MangaDex account sync are not implemented.
- Smart Chapters uses deterministic selection, not translation-quality scoring. Unnumbered releases and different volumes stay separate; volume metadata inconsistencies may leave duplicates visible.
- Download work is interruptible and Android may delay it. There is no promise of uninterrupted large background transfers; work checkpoints each page and retries.
- Collection management, backup/restore, storage-management UI and per-title language/reader overrides are planned.
- Low-end performance budgets are goals, not yet verified on a physical 2 GB device.

See [the implementation plan](docs/PLAN.md) and [validation notes](docs/VALIDATION.md).
