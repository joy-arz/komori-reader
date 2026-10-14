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
