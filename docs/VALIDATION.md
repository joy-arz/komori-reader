# Validation

## Automated checks

`./gradlew testDebugUnitTest lintDebug assembleDebug` is the development acceptance command. Release CI additionally runs `lintRelease assembleRelease` with release signing and verifies the generated APK using `apksigner`.

The chapter policy test suite covers padded numeric labels, decimal extras, unknown numbering, volume separation, language filtering, preferred-group fallback, strict groups, stable current-release selection, numeric sorting, page-resume target selection, caught-up behavior and new chapters after completion.

## Integration probes

Live MangaDex requests returned valid manga search results, a chapter feed with expanded scanlation-group metadata and the expected manga relationship, and a readable data-saving page manifest.

## Verified on the development build

- All 12 chapter-policy unit tests passed.
- Debug and release lint passed with no errors (dependency-update and platform-advisory warnings remain).
- Debug and optimized release APKs built successfully.
- The release APK was approximately 1.8 MB and passed `apksigner verify`.
- The signed release installed and launched on an Android 11 / API 30 ARM64 emulator.
- The first live emulator request correctly rejected a certificate from a network blocking endpoint. Emulator-only Android Private DNS restored access without changing the app's TLS policy; live discovery loaded source titles.
- GitHub's Android checks workflow passed after pinning SDK packages.
- Live title search, creator follow, save, like and chapter reading were exercised on the emulator.
- Advancing to page 2, leaving the reader and reopening restored page 2.
- A 48-page chapter downloaded completely. After disabling Wi-Fi and mobile data and force-stopping the app, its library persisted, the reader resumed at page 2 and opened previously unviewed page 3 from the download.
- The tag-triggered release workflow passed and published `v0.1.0-alpha.1` with an APK and SHA-256 checksum.
- The published 1,885,701-byte APK passed checksum and APK-signature verification. It installed over the local signed build without uninstalling; the existing library remained present. Installed version code: 101.
- GitHub's contributors endpoint listed only `joy-arz`.

The screenshot in `docs/images/library.png` was captured from the published APK. The emulator experienced a System UI startup ANR while the host was compiling; the app's subsequent smoke test completed. These results are functional checks, not physical low-end-device benchmarks.

## Device acceptance checklist

- Launch on a clean install; navigate Library, Discover, Downloads and Settings.
- Search a title; inspect its chapter feed and group names.
- Save/like a title and follow its creator; verify after restarting the process.
- Open a chapter, advance a page, close and resume.
- Download a chapter, disable network and reopen it.
- Change language and scanlation preferences without hiding ambiguous extra chapters.
- Confirm download deletion preserves reading state.
- Install a later APK signed by the same key as an in-place update.

## Performance limits

Emulator smoke testing does not establish low-end-device performance. The physical 2 GB phone benchmark, extreme tall-image stress test and sustained battery measurements remain release-hardening tasks. See PLAN.md for proposed budgets.
