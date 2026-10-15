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

## Continuous reader — 0.2.0-alpha.1

- All 12 chapter-policy unit tests pass. Debug and signed release lint: zero errors (15 advisory warnings, predominantly dependency versions).
- Room 1 → 2 instrumentation migration test passes on Android API 30. Existing page number and completion survive and the new normalized scroll field defaults to zero.
- Signed APK installed over alpha.1 (same release key): existing saved title, history and downloaded 48-page chapter remained available.
- On the API 30 / 2 GB emulator, switched Paged → Webtoon and scrolled across source page boundaries, including region-strip boundaries, without blank gaps after loading.
- Disabled both emulator Wi-Fi and mobile data, force-stopped and relaunched the app, and opened Continue reading: the same vertical position was restored offline. Scrolled forward to further downloaded pages successfully.
- Switched back to Paged, advanced a page, and returned to Webtoon at that page. No app crash entries were present in the emulator crash buffer.
- Screenshot review prompted explicit jade foreground colors for primary containers and dark-theme buttons, replacing Material's fallback purple.

Continuous scrolling currently spans one chapter. The end button marks it finished and opens the next available chapter. That build used a global reader mode; webtoon zoom, cross-chapter endless scrolling and physical low-end benchmarks remain future work. Scroll saves are debounced by 200 ms, so an abrupt process kill during an active gesture may lose the most recent movement.

Run the migration test with `./gradlew connectedDebugAndroidTest` on a connected emulator/device. Debug builds use the `.debug` application-ID suffix to keep test installations separate from the user's release library.

Additional scroll smoke check: traversed the downloaded chapter to page 48/48 with repeated flings, then used Finish & continue to open chapter 2 from MangaDex. A single `dumpsys meminfo` snapshot at the end of chapter 1 reported approximately 53 MiB total PSS; this is an emulator observation, not a peak-memory measurement or physical-device benchmark.

### Alpha.2 rotation correction

The alpha.1 release workflow was cancelled before publication after review found that Compose's saved flattened list index could outlive the in-memory page dimensions. Alpha.2 instead restores from source-page coordinates. Verified portrait → landscape → portrait in the debug app on chapter 2, page 2: the same panel position remained at the viewport top, with no crash. Both reader modes use the corrected jade text contrast.

## Reader controls — 0.3.0-alpha.1

- All 16 JVM tests pass, including mode defaults, per-title overrides and RTL/LTR direction mapping. Debug lint has no errors.
- Four API 30 instrumentation tests pass: Room 1 → 2, 2 → 3 and 1 → 3 upgrades, plus a real tiled-view gesture test that turns at fit scale and does not turn during a zoomed pan.
- Emulator UI checks exercised RTL swipes, LTR edge navigation, center-tap immersive mode, Back to restore controls, and a force-stop/relaunch that preserved the per-title LTR choice and page 5.
- A double-tap zoom followed by a horizontal pan retained page 4/36. Screenshot inspection identified artwork drawing outside the viewport; the paged AndroidView now clips to its bounds.
- Reader toolbar text has explicit dark-theme contrast. The mode sheet opens fully, scrolls on smaller displays, and exposes all four choices and Done.
- Per-title mode updates preserve scanlation settings; the database migration preserves saved scroll positions and preferred groups.

Physical low-end-phone benchmarks remain outstanding. This milestone does not add cross-chapter endless scrolling or webtoon pinch zoom.

### Published artifact verification

GitHub release run `38537752359` and main checks run `38537748871` completed successfully. Downloaded the actual `v0.3.0-alpha.1` release asset: 1,902,085 bytes, SHA-256 checksum matched, APK v2 signature verified. It installed in place over the previous signed release as version code 104. The saved library and completed 48-page download remained available; the installed release opened the new mode sheet and RTL left-edge navigation advanced from page 1 to 2. Final debug screenshot review also confirmed that zoomed artwork stays clipped and the immersive floating control restores the toolbar. Contributors remained `joy-arz` only.

## Chapter feeds and signing — 0.4.0-alpha.1

- Reproduced One Punch-Man's empty English feed in the app and directly against MangaDex. The source returned 0 English, 40 Indonesian and 1,924 total unique releases across four pages on October 14. Its title description identifies a takedown; metadata language hints still include English.
- Added regression coverage for chapter-first numeric ordering with missing volumes, scanlation continuity, unavailable alternatives, all-language selection, ambiguous extras, more than 500 releases, incomplete pagination, and preservation of completed downloads after a source refresh.
- Five API 30 instrumentation tests passed on the final build, including Room 1 → 4 / 3 → 4 migration and preserved download counts and RTL preferences.
- JVM keytool validated the existing release identity. A temporary signing directory passed automated key generation, credential file permission checks, and refusal to overwrite an existing identity. Production signing keys were not replaced.


### Published 0.4 artifact verification

Main run `38608459984` and release run `38608662909` succeeded. All 26 JVM tests passed; release CI validated the existing keystore with JVM keytool, built and signed the APK, and verified its signature. The downloaded GitHub asset was 1,918,473 bytes; its SHA-256 checksum matched and APK v2 signature verified. It installed over 0.3 as version code 105, retaining the library and completed 48-page download.

On the final debug app, changing One Punch-Man from English to Indonesian loaded all 40 source releases, with 33 Smart Chapter entries. Newest-first displayed chapter 301 first, while Start reading correctly opened the earliest available chapter 92. On the published APK, selecting the 48-page Momotato release through the Downloaded filter successfully displayed its image with Wi-Fi and mobile data disabled. Networking was restored afterward. The general reading queue can select a different scanlation than the downloaded one; the Downloaded filter explicitly exposes local releases for offline use. No low-end physical-device performance guarantee is implied by emulator checks.

## 0.5 source and updater work — October 15, 2026

- Debug build, debug lint, release lint and 40 JVM tests passed locally, including the explicitly enabled live source test (`KOMORI_SOURCE_SMOKE=1`). Ordinary CI skips that one live-network test and runs 39 deterministic tests.
- DemonicScans live test verified genre identifiers, views sorting, distinct first/second browse pages, Eleceed title search and description, over 400 chapter links, chapter 1 and its page manifest. A page image request returned HTTP 200 with a valid 54,914-byte WebP.
- Mock-server/fixture regressions cover source IDs, legacy MangaDex routing, duplicate-title matching, page extraction excluding ads, sort/tag POST requests, unknown page counts resolved before reading, WeebCentral full chapter/image endpoints, and access challenges preserving saved feeds.
- Updater tests cover numeric prerelease sorting, metadata scope/package/version/size validation, and corrupted/truncated APK rejection. Android signer and installer behavior is checked separately on a signed build.
- WeebCentral live access returns an access error. Comix returns a challenge; its reader is not implemented and its tab states integration is pending. No working native/offline support is claimed for Comix.

### Android and QoL verification

- Synced merged PRs #1/#2 through `07d2e8a` before the general-reader changes.
- 51 deterministic JVM tests pass; four opt-in live-source tests are skipped in ordinary builds. Debug lint and APK/test APK assembly pass.
- All six Android instrumentation tests pass on API 30 (2 GB emulator). The new synthetic four-chapter offline test scrolls across chapters without buttons, verifies the reader session stays intact, verifies crossed chapters become completed, and confirms old scroll callbacks cannot undo manual chapter changes.
- The test found and fixed two regressions before release: the global language filter could hide the next offline chapter, and fast flings could skip completion bookkeeping. The offline queue now uses the opened chapter's language, and forward crossings record every intervening chapter. Backward scrolling preserves completed state.
- Signed local baseline: existing Yotsuba library/download survived install. Continue reading dismissal survived force-stop/relaunch. DemonicScans Eleceed returned 426 chapters, Start reading opened chapter 1 (7 pages) in native webtoon mode, all seven downloaded successfully, and Read offline rendered after a cold launch with Wi-Fi and mobile data disabled. Screenshots were inspected on the emulator.
- General UI/source-adapter changes from merged PRs are preserved. No new live verification of those merged source adapters is claimed by this QoL pass.

### Published 0.5 artifact and updater verification

- Main checks run `38691820635` and release run `38691825515` both succeeded for `3b8cbf1` / `v0.5.0-alpha.1`. The published APK is 2,055,921 bytes; downloaded SHA-256 matches the release checksum and update manifest, and Android APK signature verification passes with the existing release identity. Version code is 106.
- On the API 30 emulator, a signed local code-105 baseline containing the updater detected the public release on launch, automatically downloaded it, and displayed Verified update ready. Enabled Android's install-from-this-source permission, tapped Install update, and confirmed the system installer. The app upgraded successfully to code 106. This tests the new updater; the previously published 0.4 APK predates that feature and requires a manual first upgrade.
- After upgrade, Yotsuba to! and Eleceed remained in Library, Continue reading remained dismissed, and Downloads retained both completed chapters (48/48 and 7/7 pages). Settings reported up to date (0.5.0-alpha.1).
- Manual bulk UI check showed all six choices and a 426-chapter preview for All readable chapters. Selected Next unread instead and queued one chapter; its row changed to Downloading, showed page progress and a Pause control. The 426-chapter batch was not downloaded.
- Timed retries are bounded to three attempts per visible failed reader item. A real network-interruption recovery test and physical low-end-device performance measurements remain outstanding.

## 0.5.0-alpha.2 reliability patch

- Added a shared asynchronous OkHttp image transfer with cancellation wired to Call.cancel(), 32 MiB per-image limits, streaming free-space checks and image-header validation before promotion. HTML source documents have a 4 MiB limit. Downloads use unique temporary paths so canceled callbacks cannot remove a later attempt's temporary file.
- Reader retries quarantine the failing local page and replace it after a successful fetch; damaged image headers no longer short-circuit network recovery. Download workers also validate existing page headers before skipping them. Header validation cannot detect every possible corruption; decode errors still expose Retry.
- Bulk download selection observes chapter/feed and preference changes, reselects at confirmation, and has a scrollable dialog with 48 dp radio rows. Network errors are source-neutral.
- Added transfer regressions for exact size limits, streamed overflow, interruption checks and cancellation of a throttled real HTTP response. No new physical-device or Android instrumentation run was performed for this patch. Browser verification UI, backup/restore, durable queue ordering and backward chapter loading remain subsequent audit work.
