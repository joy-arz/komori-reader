# Komori

A quiet, lightweight Android manga reader. Built with Kotlin and Compose, with native online and offline reading.

**Status: early alpha.** The first end-to-end reading milestone is implemented. This is not yet the complete product in the roadmap.

<img src="docs/images/library.png" width="280" alt="Komori library in the first Android alpha">

## Available now

- Reorderable source tabs: DemonicScans first, WeebCentral second, Comix third, then MangaDex. Comix is an explicitly pending integration; WeebCentral is experimental and live access is currently blocked on the validation network.
- Native DemonicScans reading and downloads; views/new/title browsing and genre selection. Its title search is separate from genre browsing.
- MangaDex search, popularity/rating/update/title sorting and tag filters, with safe/suggestive catalog filtering.
- Possible-duplicate warnings for normalized matching titles already saved from another source; no automatic merging.
- Dismissible Continue reading banner (restore it in Settings), and direct Read offline from Downloads.
- GitHub release checks on launch/foreground, automatic APK download on unmetered networks, manual checking/downloading, verified same-key upgrades through Android's installer.
- Personal library, likes, reading statuses and history stored locally with Room.
- Local author/artist follows and browsing their works.
- Smart Chapters: language filtering, conservative duplicate grouping, preferred scanlation and strict/fallback selection.
- Direct reading from cover play buttons, page resume, restart-current-chapter preference and optional rereading when caught up.
- Continuous webtoon scrolling across chapters, saved page/scroll position, bounded neighboring-chapter windows and image strips, offline support and a Paged/Webtoon switch.
- Per-title Left-to-right, Right-to-left and Webtoon modes, with app-default fallback.
- Immersive reading: center/image tap hides controls; a floating page counter brings them back.
- Direction-aware edge taps and swipes in paged mode, plus tiled pinch/double-tap zoom and optional keep-screen-awake.
- Persistent chapter downloads, Wi-Fi constraint, retries, pause/resume, original/data-saving images and offline access. Bulk options include all readable, all unread and next 1/5/10/25 unread; active/completed downloads are skipped.
- Per-chapter download progress and pause/retry actions, queue-wide pause/resume, and configurable visible-image retries (off/5/10/30 seconds, up to three attempts).
- Light and dark themes. Android 8.0+; no account, ads or analytics.

MangaDex must be reachable on your network. If a network redirects or blocks it, the app reports a connection error and keeps certificate verification enabled.

MangaDex and scanlation groups are credited in the application. This is an independent client, not an official MangaDex app. Source availability and policy can change.

## Install

Download [komori-0.5.0-alpha.1.apk](https://github.com/joy-arz/komori-reader/releases/download/v0.5.0-alpha.1/komori-0.5.0-alpha.1.apk), or open [GitHub Releases](https://github.com/joy-arz/komori-reader/releases) and expand **Assets**. Choose the `.apk` file, not the source-code archives. Alpha releases are signed with the same private release key, allowing future updates without uninstalling.

## Build

Use JDK 17 (JDK 21 also works locally), Android SDK platform 36 and build-tools 35.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/sdk`.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Release builds use R8 and resource shrinking. An unsigned local release can be built with `./gradlew assembleRelease`; signed releases require the environment variables documented in [release setup](docs/RELEASING.md).

## Automated releases

Every push to `main` and pull request runs unit tests, lint and a debug APK build. A version tag such as `v0.1.0-alpha.2` automatically tests, builds, signs and publishes a release APK plus SHA-256 checksum and updater metadata (`update.json`). Tags containing a suffix are published as prereleases.

```sh
git tag v0.1.0-alpha.2
git push origin v0.1.0-alpha.2
```

Signing secrets are already configured in repository Actions secrets. No signing material is committed. See [RELEASING.md](docs/RELEASING.md) for key backup and release details.

## Current limits

- Webtoon mode saves the vertical position within each source page. Paged mode saves the page, not its zoom/pan viewport.
- Continuous chapters can be disabled in Settings. Webtoon pinch zoom and automatic mode detection remain planned.
- Creator follows are local. Automatic creator/title update feeds and MangaDex account sync are not implemented.
- Smart Chapters uses deterministic selection, not translation-quality scoring. Unnumbered releases and different volumes stay separate; volume metadata inconsistencies may leave duplicates visible.
- Download work is interruptible and Android may delay it. There is no promise of uninterrupted large background transfers; work checkpoints each page and retries.
- Collection management, backup/restore, storage-management UI and per-title language overrides are planned.
- Low-end performance budgets are goals, not yet verified on a physical 2 GB device.

See [the implementation plan](docs/PLAN.md) and [validation notes](docs/VALIDATION.md).

The [Komikku reference review](docs/KOMIKKU_REVIEW.md) documents the feature comparison, implemented reader improvements, and remaining priorities.


Chapter lists now show source-returned counts, unavailable entries and active filters. Choose Language on a title to check another translation or all languages. Start reading uses ascending chapter numbers independently of the list's newest-first display option. Smart Chapters keeps the current scanlation where possible; unread/downloaded filters only affect the visible list.

Local signing is automated through JVM `keytool` validation and Gradle: see [releasing instructions](docs/RELEASING.md).

## App updates

Install version 0.5 manually once if coming from an older build. Subsequent launches check public GitHub releases, including alpha releases, with a 30-second foreground debounce. Failed update checks do not block reading. Automatic downloads default to unmetered connections; change this in Settings or choose a manual download on mobile data. Android requires permission to install from Komori and confirmation in its installer; this is not a silent system installation. The updater verifies the checksum, package, increasing version code, and existing signing certificate before installation. No GitHub token is stored in the app.

## Source limitations

MangaDex external-only chapters cannot supply pages to the native reader. Start reading skips those entries and chooses the first readable chapter. Their separately labeled external links remain available. DemonicScans and WeebCentral pages use the native paged/webtoon reader and the same download storage, not embedded website readers. Completed downloads can be opened directly without a source refresh. MangaDex IDs and existing library/download data are retained.

Source websites may block requests or change their HTML. WeebCentral's parser and request routing have fixture coverage but live reading has not been verified on the current network. Comix currently returns an access challenge and its dynamic reader integration is not implemented. No protection bypass is included. Unsupported sources are not represented as working readers.
