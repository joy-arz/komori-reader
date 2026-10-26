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


## 0.6 source metadata and session recovery — October 15, 2026

- Deterministic JVM suite: 68 tests discovered, 64 passed, four opt-in source smoke tests skipped; no failures or errors. New coverage includes source-scoped metadata, tag/author requests, popularity and later-page retention, source preference removal, cookie isolation and cancellation of an actual pending HTTP call.
- Eight API 30 Android instrumentation tests passed: five Room migration tests (including original schema → 5 and metadata/follow/progress preservation), the real CookieManager/native bridge test, continuous offline reading, and paged/zoom gestures.
- Debug lint, release lint and debug assembly passed. Lint warnings remain for dependency updates, existing accessibility/storage items and Android backup configuration; zero lint errors.
- Emulator UI inspection confirmed the separate Authors destination and its search/empty state, the HentaiZap Title/Tag/Author controls, and entry into Verify source with the real origin, Retry native access and Reset sessions controls. Inspection prompted a baseline padding correction for the verification activity. Manual website challenge completion was not claimed.
- Direct live HTTP probes to HentaiZap and Nhentai returned HTTP 403 on this network. Fixture/mock-server success does not establish live source compatibility. The shared-cookie mechanism was tested against Android CookieManager with synthetic cookie values, including HttpOnly, path scoping, expiry cleanup and exclusion from unrelated CDN/HTTP requests.
- Broader findings, implemented fixes and remaining validation limits: [source/session review](SOURCE_SESSION_REVIEW_2026-10-15.md).

## 0.6.0-alpha.2 automatic loading — October 16, 2026

- Source grids request another page near the end, preserving current query/source/filters. The ViewModel prevents overlapping pagination, retains results on failure and stops when a source returns no new IDs. Four pagination regressions cover advancing results, repeated results, an empty first page and the source end marker.
- Webtoon next-chapter selection observes refreshed feeds and preferences. Strip reload errors now preserve exception types, suppress automated retries for verification/access failures and expose Verify source. Forward continuous reading remains controlled by the existing setting; paged reading advances on user navigation, not a timer.
- Local JVM run: 72 total, 68 passed and four opt-in live-source tests skipped. Debug APK and instrumentation APK assembled. Local lint was initially interrupted while freeing emulator memory; the subsequent complete check passed.
- All audit recommendations are recorded in docs/TODO.md. This patch does not claim the entire backlog is implemented.
- API 30 emulator: all eight Android tests passed (continuous reading, five migrations, gestures and cookie/session isolation). The continuous-reader test now waits for the asynchronous Library resume-marker write before asserting it; chapter 4 and crossed-chapter completion are verified. Live browsing pagination and real browser challenges were not newly tested.
- Published artifact: Android checks 38785905483 and release 38785914318 succeeded. Downloaded v0.6.0-alpha.2 APK (2,090,049 bytes, version code 109); release checksum matches and apksigner verifies the existing signing identity. No new on-device installation of the published APK was performed in this pass.

## 0.6.0-alpha.3 repair and download coordination — October 16, 2026

- Failed local-page retries persist Needs repair plus per-page `.repair` markers before removing the canonical file. Offline opening reconciles file validity and counts; intact pages remain accessible through Read saved pages. Repair resumes skip intact files when the source manifest is unchanged.
- Workers, pause/delete state changes and reader repair promotion share chapter locks. Cancellation is checked before worker publication; generation counters prevent a stale reader fetch from restoring a deleted/paused download. Duplicate enqueue requests no longer reset an active worker's record.
- Tests cover lock ordering, canceled-writer lock release, persistent repair markers/counts, preservation of unaffected pages and missing-file completeness. This does not constitute a physical-device or full WorkManager/network race stress test.
- Local final checks: 70 JVM tests passed, four optional live tests skipped; lint and debug/test APK assembly passed. All ten API 30 Android tests passed, including two new repair-state/integrity tests, five migrations, continuous reading, gestures and source-session isolation.
- GitHub Android checks 38839288907 and release build 38839292566 passed. Published v0.6.0-alpha.3 APK is 2,090,053 bytes, version code 110; downloaded checksum matched and apksigner verified the unchanged signing certificate. The published APK was not newly installed on a device during this pass.

## 0.6.0-alpha.4 compact browsing — October 16, 2026

- Discover replaces its permanent search/filter/verification rows with a compact title toolbar. Search expands on demand, focuses the input and supports the keyboard Search action. Filters remain one tap away; source verification and search reset are in Source options, with the existing verification action also available on errors.
- Horizontal catalog swipes select adjacent sources using the Settings order, with a 72 dp threshold and bounded ends. Vertical gestures remain delegated to the catalog grid. Only the active source catalog is loaded; this is gesture navigation rather than preloading a pager for every source.
- Library search opens from an icon beside the shelf filters; Back closes it and resets the local filter. Shared grids use tighter gutters, the redundant brand label is removed, and bottom navigation labels appear for the active destination while all icons retain accessibility labels.
- Initial API 30 visual inspection confirmed populated DemonicScans covers, leftward switching to WeebCentral, return switching and focused search with the keyboard. Ten Android regression tests passed (continuous reader, repair, migrations, gestures and source session). No physical-device validation was performed.

- Final build: 70 unit tests passed, four optional live tests skipped; debug lint and assembly passed. Final emulator inspection confirmed the compact Library row, opening/closing its search and returning to Discover; a vertical catalog gesture retained the selected source.
- Published v0.6.0-alpha.4: Android checks 38873092724 and release 38873101186 succeeded. Downloaded APK is 2,106,433 bytes, version code 111; SHA-256 `85b5d3204cf38788bb0ad141070acebd08808a4e0cb952ade4bccc6d29e490d1` matched the release checksum. `apksigner` verified the existing signing certificate. The published release APK was not separately installed; emulator checks used the debug build.

## 0.7.0-alpha.1 visual redesign and interaction checks — October 17, 2026

- Replaced shared chrome with a black/green/lime design system, bundled Outfit with explicit variable-font weights, thin outlined panels, gradient primary actions and a floating navigation capsule. Library has collection counts, Discover has source pills/loading feedback, and Settings is split into Reading, Sources and App. Detail/chapter/download/reader/dialog and verification controls use the new styling. Existing light-mode preferences remain supported; dark is the default when no preference is stored.
- Replaced manual pointer consumption in source browsing with Compose's horizontal draggable recognizer. Added injected-touch instrumentation covering every navigation destination, search open/close, cover taps, vertical catalog scrolling and horizontal source switching.
- Automatic update checks and downloads no longer open a modal; a banner offers Review/Install. Manual update checks and user-selected installation retain their dialogs and Android confirmation.
- All 11 API 30 instrumentation tests passed. After final typography and control refinements, the browse interaction and source-session tests were rerun and both passed. Existing JVM suite: 70 passed, four optional live tests skipped. The user-reported first-launch freeze was not reproduced on the emulator; signed in-place upgrade validation is recorded below when complete.
- Visual checks covered Library, populated DemonicScans browsing, and grouped Settings. Screenshot review caught and corrected the variable font's overly thin default weight and excessive empty-state spacing. The radial background is a static brush, with no blur or continuous decorative animation.
- Final local build, lint and unit checks passed after adding catalog loading feedback. GitHub Android checks 38955097720 and release build 38955107555 succeeded. Published APK is 2,164,849 bytes, version code 112; SHA-256 `88ffe9dffafa22a0055c6c511b39808600e62daefe693d93d17d329588a465f2` matched the release checksum, and apksigner verified the unchanged signing certificate.
- Installed that downloaded signed APK over production v0.6.0-alpha.4 (code 111) with `adb install -r`, without clearing data. On the first launch, Eleceed and Yotsuba to! remained in Library; Settings navigation, a saved-title tap into details, Back, Discover, opening search and closing search all passed UI-hierarchy assertions and injected taps. The release UI also rendered the intended dark theme and font weights. This validates the API 30 emulator upgrade path, not every physical device or the unknown cause of the reported freeze.

## October 17: reader loading and subscriptions

Changes under validation for `v0.7.0-alpha.2`:
- Cached reader files bypass network locks; concurrent reader transfers are capped at two. Webtoon strips use a memory cache capped at 8 MB (smaller on low-heap devices), downsample to viewport width and show a loading indicator on a miss.
- Image retry defaults to 3 seconds. Existing explicitly chosen intervals remain unchanged; Off, 3, 5, 10 and 30 seconds are supported.
- Downloads expose manifest loading, current-page bytes and queue requirements. Changing Wi-Fi-only reschedules the existing active queue with the new constraint.
- Following has a creator strip, All/creator library filtering, All works navigation and searchable follow management. It shows saved works, not a claimed live publication feed.
- MangaDex preserves its identifying User-Agent and retries connection reset/EOF once using a separate HTTP/1.1 pool. HTTP errors and rate limits are not retried by this fallback. Image server lookup requests forcePort443, documented in the [official API specification](https://api.mangadex.org/docs/static/api.yaml).
- This host returned `pong` from MangaDex on October 17; the user's specific network reset was not reproduced. No TLS validation or source verification protections were disabled.

Before the navigation follow-up, 74 JVM tests passed (4 opt-in source tests skipped), lint/build passed, and all 15 API 30 instrumentation tests passed. These include a worker completing two synthetic image downloads, transfer byte progress, cached-file access with every network lock held, strip decoding/reuse, creator filtering/unfollowing, continuous reading, migrations and injected navigation touches.

Navigation follow-up under validation: screen state is retained per history entry; Back restores query, filters, results, pagination and the originating title for tag/creator browsing. New search text resets the previous search's filters. History is bounded to 30 entries for memory use. Process-death restoration of the in-memory search history is not claimed.

Final local navigation validation: `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed, with 74 JVM tests passing and 4 opt-in live tests skipped. All **17 API 30 instrumentation tests passed**. The navigation tests assert identical pixel bounds after returning from title details and an offline reader, search-specific filter restoration and system Back from a tag search to its originating title. The Following screen was visually reviewed using clearly synthetic creator/title fixtures. Physical low-end-device performance, the affected user's network reset and live downloads from every source remain unverified.

Published `v0.7.0-alpha.2` from `211c99b` (version code 113). Android checks run `38993252397` and signed release run `38993255634` succeeded. The 2,181,233-byte APK matches both `SHA256SUMS.txt` and `update.json`: SHA-256 `ae37af5bb0f3b93e0803a34a6cafcc660b3579bdeca41da9573fdeb8454c743d`. APK signature verification retained certificate SHA-256 `0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd`.

Installed the published APK over production `v0.7.0-alpha.1` without clearing data. Eleceed and Yotsuba to! remained in Library; injected taps opened Following and Settings. GitHub's contributors endpoint listed only `joy-arz`. Release notes include the navigation behavior and remaining live-source/network validation limits.

## Catalogs, batch actions and reset recovery (October 20, 2026)

- Development build, debug lint and JVM suite passed: 81 passed, 4 opt-in live tests skipped.
- Four new author-catalog tests cover unsaved works, bounded round-robin pagination, duplicate/repeated-page handling, per-creator retry and cancellation propagation.
- Following now requests the source's creator catalog, independently of Discover history. Saved works remain available as a labeled fallback when the source fails. Creator catalog support depends on the source exposing that capability.
- MangaDex API uses its own native client, isolated from browser sessions. Reset recovery tries an alternate protocol pool once and retains a successful HTTP/1.1 preference for 15 minutes; TLS validation stays enabled.
- Toolbar controls clip their outer touch-feedback region to a circle. The reported Android 14 square artifact and intermittent connection reset have not been reproduced on that physical device; user confirmation remains necessary.
- Selection covers title grids, chapter lists, downloads and followed creators. Select all applies to the currently loaded/filtered list. Destructive operations require confirmation.
- API 30 ARM64 device suite passed all 18 tests (54.937 seconds), including author filtering/manage, batch selection and confirmed/cancelled deletion, offline Downloaded Library access, navigation restoration, reader gestures, migrations, repairs and browser sessions. Following and Downloaded layouts were visually inspected using fixture screenshots.
- Published signed release `v0.7.0-alpha.3` (version code 114): release workflow 39360030830 and Android checks 39360026584 succeeded. APK size 2,197,617 bytes; SHA-256 `707a5dc8e23ec0e92f0cdae705ce996a7a9fc15ddcac4a336551c0a49d7a5a76`, matching updater metadata. `apksigner` verified the original release certificate (`0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd`).
- Installed the published APK over alpha.2 without uninstalling. Version 114 launched with Eleceed and Yotsuba to! still present and the Downloaded collection visible. GitHub contributors still listed only joy-arz.


## Parodies, language and category browsing (October 20, 2026)

- Baseline GitHub main: `064f9e1`; both latest Android/release runs for 0.7.0-alpha.3 were successful before this change.
- Added Parody, Language and Category search modes to gallery sources. Title details group typed metadata under Parodies, Languages, Categories and Tags; every entry uses the canonical source link. Older tagsJson entries default to ordinary tags, requiring no Room migration or data reset.
- Added explicit single-language/single-category browse choices. HentaiZap and ImHentai send the source's native category/language flags on every page. Nhentai combines namespace terms with title/tag/creator/parody searches and keeps popularity sorting. All languages/All categories reset the corresponding constraint. Selected language/category survive new text searches and navigation history; Reset clears them.
- HentaiZap taxonomy popularity uses the actual `/popular/` route, with only Latest/Popular offered there. Language/category combinations are disabled and explained during HentaiZap/ImHentai metadata browsing rather than being silently dropped. Exact-name entry follows source slugs; metadata chips retain canonical links when names contain unusual punctuation. This is not a new full parody directory or fuzzy search service.
- Fixed live HentaiZap metadata parsing: restrict it to gallery metadata, exclude ranking/sidebar entities, and remove hz-gallery-tag count/split spans from labels.
- Local JVM suite: **89 passed, four optional live source tests skipped**, no failures/errors. Eight new tests cover typed metadata and old records, native language/category requests on later pages, language-only routing, Nhentai compound queries/creator retention, canonical popularity routes and explicit rejection of unsupported filters. Existing routing tests were updated to the verified taxonomy route.
- All **20 API 30 Android instrumentation tests passed**, including two new real-touch tests for grouped metadata, parody navigation/Back restoration, language/category Apply/Reset and retained filter history. Existing authors, batch actions, reading, repair, migration and session tests also passed.
- Direct live HTTP structure checks of HentaiZap verified English + Manga + Popular on pages 1 and 2: 25 cards per page, every returned card labeled English and Manga; next-page links were present. `/parody/hack/popular/?page=1` returned 24 cards and a next-page link. These checks fetched HTML only, not gallery images. Site forms and canonical sort links were inspected directly.
- Nhentai and ImHentai returned HTTP 403 to direct probes; their new routing is covered by mock-server tests, not claimed as live-verified. API 30 checks do not establish behavior on every physical device.

- Published `v0.7.0-alpha.4` from `ca07ec2` (version code 115). Android checks `39364347569` and signed release `39364371336` succeeded. The 2,197,617-byte APK matched SHA256SUMS and updater metadata: SHA-256 `d1310121adcadf82ba2ab05cb0ce10dee639d7e222bc3fceed5df544334e9131`. `apksigner` verified the same certificate as the installed alpha.3 release. Installed the downloaded APK with `adb install -r`, retaining the two visible Library read actions; no crash was found for the current production process. The emulator's unfiltered buffer retained an earlier debug-test UiAutomation disconnect entry, so it was not an empty-buffer check. GitHub contributors remained `joy-arz` only.

## Nhentai resets and compact Authors (October 20, 2026)

- User reported alpha.4's “The source reset the connection” message, unchanged by Verify Source. That message previously covered every SocketException, so it did not establish a specific reset cause. Generic socket failures now have a distinct message.
- Nhentai's HTML GET path now retries reset/EOF failures once after 500 ms with a separate HTTP/1.1 connection pool. A successful fallback is preferred for 15 minutes. Browser cookies, User-Agent, certificate validation and challenge handling remain inherited from the session client. POSTs, HTTP access failures and TLS failures do not get this fallback. Reader page manifests use this path; image transfers remain under their existing transfer policy.
- Repeated resets explain the retry and suggest another network or retrying later. Verify Source cannot repair an underlying transport failure. The user's physical-device reset has not been reproduced here; host probes returned Cloudflare HTTP 403 challenges, which are a separate failure. No persistent site/network restriction is claimed fixed.
- Authors now shows only the complete paginated source catalog, in catalog order. Removed the saved-work fallback and Library subtitle that could anchor the grid to a saved title while source results loaded. Feed context prevents results from the previous creator selection appearing during a switch. All remains the default filter.
- Replaced the tall Authors heading/avatar strip/count/summary/navigation row with one compact Following/Manage toolbar and creator filter chips. Follow management remains available. The idle Select row is hidden in Authors; long-pressing a cover opens the existing batch actions.
- JVM suite: **97 passed, four optional live source tests skipped**, no failures/errors. Eight recovery tests cover session headers, bounded retries, pool reuse and preference expiry, cancellation, TLS/HTTP exclusions, POST/other-source exclusions and socket-message classification.
- Debug/release lint and debug assembly passed, with zero lint errors and 20 existing advisory warnings. All **20 API 30 instrumentation tests passed**. After hiding Authors' idle Select row, the Authors and batch-selection tests both passed again. The Authors test delays source results, checks the first source title, scrolls to a saved title without a Library badge, switches creators/All, verifies compact controls, enters batch selection by long press and unfollows a creator.
- Visually inspected the compact Authors fixture screenshot. Covers use synthetic empty image URLs; no live gallery images were fetched for UI validation.
- Additional host checks returned `cf-mitigated: challenge` / HTTP 403 on both HTTP/2 and HTTP/1.1. This confirms that protocol fallback is not a solution for every access failure and does not establish the cause of the user's device reset.
- Published signed `v0.7.0-alpha.5` from `a1839d9` (version code 116). Android checks `39407839532` and release `39407845199` succeeded. The 2,214,001-byte APK matched SHA256SUMS and updater metadata: SHA-256 `45d70db1955b2c2e6c907b14b81a987094125e08a6c447a12a4f797269c4b2fc`. `apksigner` verified the original release certificate (`0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd`). Installed the downloaded APK over alpha.4/code 115 using `adb install -r`; both visible Library read actions (Eleceed and Yotsuba to!) were retained. The current production process had no fatal entry in its PID-filtered crash buffer. GitHub contributors remained `joy-arz` only; commits have no assistant co-author trailer.

## Full release and launcher artwork (October 21, 2026)

- Updated launcher icon from the user-supplied bat-and-manga image. A centered 660×660 crop removes the outer pale surround and shadow while preserving the original illustration; resized PNG assets supply the README, launcher fallback and adaptive background.
- Adaptive icon uses a nine-patch background: only the outer green strips stretch, keeping the original illustration intact without a rectangular inset seam. Both regular and round launcher references use the new icon.
- Version 0.7.0 uses a plain tag; CI explicitly marks full releases as Latest while retaining prerelease handling for suffixed tags. README documents the full-release installation, current selection/download features and 3-second default retry.
- Local `lintDebug assembleDebug` passed. Signed-release CI performs unit tests, release lint, assembly and signature verification.

- API 30 launcher inspection caught an inset seam and then clipping of the illustration. Replaced the inset with a nine-patch and reduced its fixed artwork size; final resources pass lint/build. A subsequent emulator renderer failure prevented final on-device visual confirmation before publication.

## Compact browsing and long-press selection (October 21, 2026)

- Started from the full `v0.7.0` release, then incorporated GitHub's README and screenshot updates through `127646a`. Preserved the cropped bat launcher icon and the revised README presentation.
- Removed the idle Select control from the shared selection bar. Title grids, chapters, downloads and creator management enter selection through long press. Select all, bulk actions, Back/Done exit and destructive-action confirmations remain available during selection.
- Library now uses a single compact title/search toolbar and shorter collection tabs with counts. Removed its separate Collections label row and the tall global heading/subtitle.
- Discover uses a shorter toolbar and less source-strip padding. Search, sorting/filter controls, source options and swipe switching remain available.
- Downloads now has a compact title/queue-count header. Queue-wide commands move to Queue options and appear only when actionable. Completed chapters omit redundant progress bars; active downloads retain byte/page progress and per-item actions.
- Removing the idle sticky-header content exposed a Compose LazyList crash when that header contained no placeables. Chapter and download sticky headers are now emitted only during selection. Also fixed pre-existing test timing assumptions: Authors waits for the database update after unfollow, and batch navigation waits for controls after Back.
- JVM checks: **97 passed, four optional live-source tests skipped**. Debug/release lint passed with **zero errors and 24 advisory warnings**, and the debug APK assembled. The corrected full API 30 suite passed 19 of 20 cases; the remaining failure was the batch test querying Authors before Back navigation completed. After adding the control wait, BatchSelectionTest and BrowseInteractionTest both passed on a cold boot. A further focused BatchSelectionTest passed after the final full-width download-card adjustment. All 20 instrumentation cases therefore have passing results across the suite and focused reruns, rather than a claimed clean final 20-case run.
- Visually reviewed native framebuffer captures of populated Library, Discover, download cards, batch selection and delete confirmation using synthetic fixtures. Download cards now fill the available width. The emulator's UiAutomation screenshot helper did not produce images, so review used `adb exec-out screencap -p` instead.
- Published full release `v0.7.1` from `5994fc8` (version code 118). Android checks `39543105825` and signed release `39543122136` succeeded. The 2,321,447-byte APK matched SHA256SUMS and updater metadata: SHA-256 `0ec2fc25771671b01f2ea8c7d0377ccb474647d6745400cd44f4eec2a12619c6`. `apksigner` verified the original certificate (`0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd`).
- Installed the downloaded APK over full `v0.7.0` / code 117 using `adb install -r`. Both visible Library read actions (Eleceed and Yotsuba to!) remained, as did the Liked=1, History=2 and Downloaded=2 collection counts. The current production process had no fatal entry in its PID-filtered crash buffer. GitHub contributors remained `joy-arz` only; no assistant co-author trailer was added.

## Author ordering and chapter markers (October 21, 2026)

- Added Latest (default), Popularity / views, Alphabetical and Author order to the compact Following toolbar. Sorting applies to All and individual creator filters. Changing order resets the grid position and excludes any previous order's feed during a network refresh.
- Latest explicitly requests MangaDex Latest updates and each gallery source's Latest route. Popularity requests MangaDex Popularity, HentaiZap/ImHentai Popular and Nhentai Popular all time. Results blend the creators' native ranking positions instead of appending an entire creator page first. These sources do not expose comparable view totals or universal timestamps in these listings; the menu explains the blended source order rather than claiming a global views/date leaderboard.
- Alphabetical and Author sort the loaded catalog. Author ordering uses the followed creator associated with each result, with deterministic title/ID tie breaks and deduplication of works shared by several creators. Latest/Alphabetical/Author switches retain loaded pages; switching to or from Popularity fetches fresh pages for the different source ranking. Pagination, cancellation, repeated-page guards and retry behavior remain in place.
- Replaced bare inter-chapter text in continuous scrolling with a centered, restrained capsule and fine divider rules. Chapter labels remain readable, wrap to two lines, and expose a heading to accessibility services. Reader page keys and progress callbacks are unchanged.
- JVM checks: **101 passed, four optional live-source tests skipped**, no failures/errors. New tests cover native source-sort mappings, rank blending over multiple pages, alphabetical/author ownership deduplication, and local reordering without losing or refetching pages. Debug/release lint passed with zero errors and 24 advisory warnings; debug assembly passed.
- The broad API 30 run passed 18 of 20 cases, including continuous progress over four chapters. One author test used a scroll helper that stopped too early after the blended feed doubled its row count; it now performs bounded real swipes. A navigation test also encountered a stale accessibility object. The final focused run passed all four Authors, continuous-reader and navigation cases, including all four sorting choices, return-to-top behavior, saved-work styling, selection/unfollow and pixel-offset restoration. These are passing cases across the suite and focused rerun, rather than a claimed clean final 20-case run.
- Reviewed native framebuffer images of the compact Following toolbar and the chapter separator between synthetic offline chapters. No live gallery images were used for these UI checks.
- Published full `v0.7.2` from `565085c` (version code 119). Android checks `39549343421` and signed release `39549357697` succeeded. The 2,321,447-byte APK matched SHA256SUMS and updater metadata: SHA-256 `48f5d424d42cc953b7d98eefe17413b07cacc05fb0426381603b0b2ab7f0a200`. `apksigner` verified the original certificate (`0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd`).
- Installed the downloaded APK over `v0.7.1` / code 118 using `adb install -r`. Both Library read actions (Eleceed and Yotsuba to!) remained, with Liked=1, History=2 and Downloaded=2 counts unchanged. The current production process had no fatal entry in its PID-filtered crash buffer. GitHub contributors remained `joy-arz` only; commits contain no assistant co-author trailer.

## Typed failures and bounded recovery (October 21, 2026)

- Reader and download HTTP failures retain their status. Missing pages (404/410), verification requirements, invalid image content, storage-write failures and TLS errors stop automatic retries and retain manual recovery controls. Offline/transient failures remain retryable. Browser verification is offered only for authentication/challenge failures.
- `Retry-After` supports delta seconds and HTTP dates for 429/503. Origin-scoped cooldowns prevent other pages from immediately repeating a rate-limited request. Reader countdowns respect the deadline; valid cached pages remain available immediately.
- Rate-limited downloads append an initially delayed continuation to the same unique WorkManager chain, then release the queue gate. Continuations carry the previous attempt count. Pause/delete cancellation covers the chain, and each worker still checks the persisted download state before fetching pages. Ordinary transient failures keep exponential backoff.
- Page and chapter-manifest retry budgets live in the reader ViewModel rather than lazy-item composition. Four failed attempts stop automatic network retries across scrolling/recomposition; explicit manual retry or a new reader session resets the relevant budget. Deferred cooldown checks do not consume page attempts. Decode failures require manual action.
- Reconciled TODO/PLAN with the shipped 0.7.x features. Affected Android 14 phone/network validation, full process-death repair tests, slow-transfer WorkManager races, backup/restore and other roadmap work remain open. Synthetic HTTP tests do not establish live source availability.
- Final feature checks: **107 JVM tests passed, four optional live-source tests skipped**; all **10 API 30 instrumentation checks passed** across FailureRecovery, ImageLoading, DownloadRepair, SourceSession and ContinuousReader. Release lint reported zero errors and 24 advisory warnings. Coverage includes 404/403 image types, immediate missing-manifest failure, a delayed rate-limit continuation, cancellation of its unique work, carried retry exhaustion, intact offline image loading, download repair and continuous-reader progress.
- The final overflow guard also passed the focused three-case FailureRecovery instrumentation run. Extreme numeric retry values cannot overflow WorkManager timestamp construction.
- Published full `v0.7.3` from `52f95de` (version code 120). Android checks `39555966976` and signed release `39555971532` succeeded. The 2,321,447-byte APK matched SHA256SUMS and updater metadata: SHA-256 `95d9126c8b714b6bcc852f1b89fa36a76953a9bd7a25ec5cbd90d45ccbfc05de`. `apksigner` verified the original certificate (`0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd`).
- Installed the downloaded APK over `v0.7.2` / code 119 with `adb install -r`. Library read actions for Eleceed and Yotsuba to! remained, as did Saved=2, Liked=1, History=2 and Downloaded=2 counts. The current production process had no fatal entry in its PID-filtered crash buffer. Contributors remained `joy-arz` only, with no assistant author/co-author attribution.


## Reviewed metadata backup and restore (October 21, 2026)

- Added Settings → App → Library backup, using Android CreateDocument/OpenDocument contracts and ContentResolver streams. Exports include Library/likes/history, followed creators, referenced chapter metadata and per-title preferences. Device settings, cookies, verification sessions, cached feeds, image files and download state are excluded.
- Introduced format `komori.metadata.backup` / version 1. Input is bounded to 16 MiB and parsed with strict UTF-8/JSON rules. Validation rejects duplicate keys/record identifiers, unsupported fields/versions, wrong primitive types, invalid ranges, unsafe identifiers/URLs and inconsistent title ownership. Export preflights database size/count limits and uses a bounded streaming JSON writer to stop escaped output growth.
- Restore preview shows incoming and matching counts. The default merge preserves existing values; a labelled checkbox explicitly enables backup values for matching entries. Existing unrelated entries remain. The importer rechecks ownership inside the write transaction, including offline download records, and performs all upserts in one Room transaction. Historical logical chapter keys survive source renumbering.
- The initial eleven-case API 30 run passed: five repository cases (round trip, merge policies, write-failure rollback and chapter/download ownership conflicts), the interactive picker/preview/merge case, and all five existing Room migration cases. The interaction test used synthetic metadata and a private ContentResolver file fixture for actual writes, plus real Android document-picker cancellation. Final exporter acceptance and a successful real-picker round trip are recorded below.
- Final acceptance: **116 JVM tests passed, four optional live-source tests skipped**; all **six final backup instrumentation checks passed** after the streaming writer and renumbering compatibility changes. Release lint passed with zero errors and 24 advisory warnings. No database schema version change was needed.
- Successfully exported a 175-byte empty-Library backup through the real Android Downloads document provider, reopened it through OpenDocument, inspected the preview and confirmed a merge. File-card taps injected by ADB did not activate selection; native keyboard selection completed the picker. Nonempty metadata, merge choices and failures were exercised separately by the automated synthetic fixtures. Native framebuffer captures of Settings and the restore preview were visually reviewed.
- The final labelled checkbox alignment/minimum target and wrapping export/restore actions passed the focused BackupInteraction instrumentation rerun.
- Published full `v0.7.4` from `c8c9d5f` (version code 121). Android checks `39564925070` and signed release `39564929936` succeeded. The 2,354,215-byte APK matched SHA256SUMS and updater metadata: SHA-256 `90a72af6d0a942f78ad9096a1b3473e7811684cc2500ccf620ff75406b9e774d`. `apksigner` verified the original certificate (`0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd`).
- Installed the downloaded signed APK over `v0.7.3` / code 120. Eleceed and Yotsuba to! read actions remained, with Saved=2, Liked=1, History=2 and Downloaded=2 counts unchanged. The signed app successfully exported that populated emulator Library through the real Downloads provider: two title/shelf rows, four chapter/progress rows, one followed creator and one title preference. Reopening the file displayed the matching counts in the restore preview; cancellation preserved the existing state.
- Final Library UI labels/counts after preview cancellation matched the pre-upgrade baseline exactly. The running production process had no fatal entry in its PID-filtered crash buffer. GitHub contributors remained `joy-arz` only; commits contain no assistant author/co-author attribution. Temporary picker test documents were removed from the emulator.


## Backlog reconciliation and author completeness — October 21, 2026

- Fetched GitHub main and verified it matched local `b577b5b`; releases `v0.7.0` through `v0.7.4` are published. Contributors still list only `joy-arz`.
- Reconciled TODO delivery claims with release commits and source code. Distinguished the shipped manual backup from scheduled backup work, selected/next-N chapter downloads from arbitrary ranges, and existing batch pause/resume/delete from a future cancel-without-delete action. Corrected the pending connection-reset reproduction to include the user's Nhentai report.
- Confirmed the user's HentaiZap gallery `1665352` has `/artist/kanamirubon/` metadata while the artist listing omits it. Native artist listing returned one work, keyword search twelve at 08:14 UTC, both HTTP 200. Recorded the reproduction, implementation cause and fix acceptance criteria in [AUTHOR_CATALOG_AUDIT_2026-10-21.md](AUTHOR_CATALOG_AUDIT_2026-10-21.md).
- This reconciliation changes documentation only. The catalog completeness defect remains open; no app fix, new APK or device acceptance is claimed.


## Thumbnail language badges — 0.7.5 change

- Added a shared thumbnail cover with an upper-left language badge for Library/Discover/Authors grids, Continue reading and title details. It uses explicit language metadata only; unknown/missing language and MangaDex original-language hints do not create translation badges. Multiple known languages are deduplicated and shown together.
- Requested display codes are `en`, `jp`, `sp`, `fr`, `kr`, `ch`; site/ISO aliases such as `es`, `ja`, `ko`, `zh`, `gb` and `cn` normalize to those codes. Additional recognized languages retain short codes. Full language names are exposed to accessibility services.
- HentaiZap listing language links/flags, ImHentai flag metadata and Nhentai `lang-*` card classes are retained in existing typed language metadata, without detail-page requests or a Room schema change. HentaiZap markup was inspected live; ImHentai/Nhentai access returned HTTP 403 on this audit network, so their extraction is fixture-validated rather than claimed live-validated.
- Local acceptance: 125 JVM tests total, **121 passed and four optional live tests skipped**; lint passed with zero errors and 24 warnings; debug APK assembled. The focused **API 30 interaction test passed**, checking the displayed codes, retained long-press selection and the title-detail badge. Grid, selected-grid and detail screenshots using synthetic covers were visually reviewed.
- The earlier HentaiZap author-catalog completeness defect remains open. Language badges do not change author lookup or session recovery.

- Published signed `v0.7.5` from `a78e2fd` (code 122). Android checks `39573637613` and release `39573645351` succeeded. The 2,354,215-byte APK matches both SHA256SUMS and updater metadata: SHA-256 `3e019c315abe422d82c344cf3197896e2603a0fd5a636604387b5e85c22b2bf9`. `apksigner` verified the original certificate. Installed over code 121; saved titles, Library labels and Saved=2/Liked=1/History=2/Downloaded=2 counts were unchanged. Contributors remain `joy-arz` only.


## Published 0.8.0 acceptance

- Immutable tag `v0.8.0` at `fad6beb`, version code 123. Release `39621002834` attempt 2 and exact-tag Android checks `39624937617` succeeded. JVM: 138 passed, five optional live-source skips, 143 total. Device run `39620993524` passed 38/38 on each API 26 and API 36, with zero skips. Local release lint: zero errors, 45 advisory warnings.
- Published APK is 2,502,223 bytes, SHA-256 `597d32f03fde8d965706102fabd57a0b8b8b414c5756a63c3e2fde6da8eaa3df`. Original certificate SHA-256 `0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd` verified with apksigner.
- API 30 signed in-place upgrade from production 0.7.5 preserved every existing column/value in manga (2), shelf (2), progress (4), downloads (2), creators (1) and chapter preferences (1). Room advanced from schema 5 to 6.
- Additional signed cloud upgrade run `39626710821` failed: API 26 background-process termination did not occur within its deadline; API 36 returned an empty UI hierarchy during capture. These checks remain open. Their failures are separate from successful instrumentation/build/local-upgrade evidence.
- Supplied README Support banner and centered layout were reviewed. The Ko-fi announcement draft has the supplied featured image and clickable GitHub/latest-release links; no public publication is claimed.
- User confirmed the reported Nhentai connection is working on October 22. Broader live-source acceptance, accessibility and physical low-memory benchmarks remain open.


## Backup/storage/refresh development — October 22

- Main implementation commit `d13265a` adds optional daily/weekly metadata backups with verified retention, download checksums/scans/storage/delete-read review, cache clearing and compact manual refresh controls on five screens. Tracking remains deferred. JVM: 147 total, 142 passed, five optional live-source skips. GitHub Android checks `39651598354` passed.
- Local API 30 first run: 41/42 passed. Integrity/deletion and refresh checks passed; automatic backup hit an orphan shelf entry left by a navigation fixture. Fixture cleanup and permanent validation-failure handling were corrected; focused provider/UI/partial-offline refresh checks and the next full CI run remain to verify those final fixes.
- API 26 device job `119401060319` failed before building/testing because Google's downloaded system-image archive was invalid (`Error on ZipFile unknown archive`). This is an SDK provisioning failure, not a passed or failed app test.
- Published 0.8.0 signed-upgrade run `39651649241` passed on API 26 and API 36 after the harness corrections: schema 5→6, saved/liked/progress retention, verified process death, reader restoration and offline next-page navigation. No published tag was moved.
- Follow-up `5c286cf`: Android checks `39660965326` passed. Device run `39660965668` passed 44/44 on API 36 and 43/44 on API 26. The remaining API 26 filter interaction screenshot showed the keyboard covering dialog actions; initial dialog focus was moved to its heading for the next build. Backup, storage, cache and refresh cases passed on both APIs.
- HentaiZap cover investigation: its browse page supplies `thumb.jpg`, while gallery details supply a separate `cover.jpg`. All 25 sampled live thumbnail HEAD requests returned HTTP 200; the user's specific failing image/network has not been reproduced. The existing image request had no recovery path. Added one alternate-cover attempt after thumbnail failure, coalesced/bounded gallery lookups, and no fallback for verification/rate-limit responses. Four new JVM cases passed; Android decoding/request-count/header verification is pending the next device run.

## Published 0.8.1 — October 22

- Immutable `v0.8.1` / `bbb288b4367a54c369215065458ceef03267367d`, version code 124. Android checks `39663349030` passed; device run `39663348851` passed 45/45 on both API 26 and API 36, with zero skips. This includes actual Coil decoding of a failed HentaiZap thumbnail through its gallery cover, blocked/rate-limited response handling, provider backup/retention/restore, storage review/cancellation, cache reachability, filter focus and manual refresh. Local API 30 focused cover/filter run passed 3/3. JVM: 151 total, 146 passed, five optional live-source skips.
- Signed release `39664480684` passed. Downloaded APK: 2,518,607 bytes; SHA-256 `3aa5b3195a8892385381460147122ea6825425070146508dd8c29b57d8240107`, matching SHA256SUMS and update.json. Package `app.komori.reader`, minimum API 26, target API 36. Original RSA-4096 certificate SHA-256 `0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd` verified. Release manifest contains no debug backup-fixture provider.
- Extra signed-upgrade run `39668881306` failed: API 36 SDK provisioning returned a corrupt emulator archive; API 26 restored the reader to page 2/3 (screenshot and ActivityManager Displayed event) but the framework am start -W command timed out before the harness checked it. Changed only that relaunch to nonblocking am start, retaining original-PID-exit, visible reader-page, offline next-page and database-retention assertions. Run `39673007949` then passed on both API 26 and API 36 against the same immutable published APK: schema 5→6, saved/liked/progress retention, verified process death, reader restoration and offline next-page navigation. The harness change did not reset existing user data or move the release tag.

## October 26 candidate — new sources and reader/download behavior

- Added the source/search/reading/background pass described in SOURCES_2026-10-26.md. No Room schema change (still 6).
- Local JVM/lint/Android-test compilation passed before the final small source-order/genre-identifier refinements. The last full ordinary run had 162 tests: 156 passed, six live skips. Focused search-filter/Android-test compilation passed after native DemonicScans genre-name resolution was added; final whole-commit CI is pending.
- The explicit live new-source test passed: both Manhwa18 catalog/detail/chapter/genre/reader-image paths and a public MangaDex at-home manifest/image, using encrypted DNS and ordinary TLS verification. Other live-source suites were not rerun in this pass.
- New Android tests cover automatic final-page completion and rereading, plus a reset-recovered MangaDex download continuing after the activity closes. Cloud API 26/36 acceptance and signed release verification are pending.

- Initial commit `dc4542f`: API 26/36 device run `40303371047` passed all 47 cases on each API. Local API 30 focused run passed 5/5, including last-page rereading and reset-recovered downloading after activity closure. Android checks `40303371034` passed compilation/JVM tests but failed lint on a direct StateFlow.value read inside the filter composable. The subscription was corrected with collectAsStateWithLifecycle; local lintDebug passed. A final contextual sort normalization and regression case keep page-only search ordering explicit when the query changes. Final exact-commit gates are rerun after this correction.

## Published 0.8.2 — October 26

- Immutable `v0.8.2` / `d64459408ce831bcf69133b25f0d70f05ff3e3a9`, code 125. Android checks `40305624000`, device checks `40305623963` and signed release `40306953126` succeeded. Both API 26 and API 36 passed all 47 instrumentation cases with zero skips. New coverage includes final-page completion/rereading and an actual image transfer/reset retry continuing after activity closure. Local API 30 focused interactions passed 5/5.
- Downloaded signed APK: 2,535,051 bytes; SHA-256 `cff367fd0b0caa7869d5d69e86b176b275d3cc7cff443bcf2c58b1200d788db5`. SHA256SUMS and update.json agree on hash, byte count, package `app.komori.reader`, version 0.8.2 and code 125. Original RSA-4096 certificate SHA-256 `0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd` verified. Manifest declares foreground-service/data-sync permissions and the dataSync service type; no debug backup-fixture provider is present.
- Explicit live checks passed for MangaDex API/at-home/image and both new Manhwa18 catalog/detail/chapter/genre/image paths using encrypted DNS with standard TLS checks. This does not establish availability on every network. Database schema remains 6; AniList/MAL tracking remains deferred.
- Published-APK signed-upgrade run `40307898798` passed on both API 26 and API 36: schema 5→6, saved/liked/progress retention, verified original-process termination, reader restoration and offline page navigation. Physical low-memory, accessibility, broader protected-source verification and slow-transfer/process-death pause/delete stress checks remain open.


## 0.8.3 sleep recovery and reader lookahead — October 26

The reported failure is a download left queued after the display sleeps or the activity backgrounds. The previous implementation used a 15-minute periodic recovery worker and application creation, without reconciling interrupted work on activity/network return. This change checks the Room queue against WorkManager on each started activity/network transition and replaces an enqueued request only for a Queued/Downloading head without running work. Retrying rows retain backoff/Retry-After chains; paused and completed rows remain outside the queue. Scheduling reconciliation is serialized. Transfers hold a partial CPU wake lock with a nine-minute timeout, released in finally; the worker already checkpoints and yields after eight minutes. This does not exempt the app from manufacturer process termination, force-stop or Android network restrictions.

Reader lookahead now has its own cancellable job, waits for the current image, and fetches sequentially with a shared maximum of ten images across continuous chapters. It starts in both paged and webtoon modes, advances with the visible page, avoids gap-warning boundaries, and never writes speculative reading progress. Existing explicit preload settings are preserved; the unset default is ten, adjustable from zero to ten in reader cache settings. Only files are prefetched, within the existing configured disk cache; bitmap decoding remains attached to visible reader content.

Local checks: 170 JVM tests, 164 passed and six optional live-network skips; all five new reader-prefetch regressions passed. Debug lint, app and test APK builds passed. API 30 `ReadingAndBackgroundTest` passed 3/3, including actual WorkManager interrupted-request replacement and retained delayed retries, reread completion and a reset-recovering transfer after activity dismissal. The visible image is protected during speculative cache eviction. API 26/36 device acceptance and signed release remain pending. Physical screen-off acceptance remains open until tested on affected hardware.
