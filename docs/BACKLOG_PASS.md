# Broad backlog implementation pass

Requested October 21, 2026 after the thumbnail badges. Core implementation is pushed to main. Signed 0.8.0 is published; cloud signed-upgrade acceptance subsequently passed on API 26/36. checked items below describe delivered implementation, not universal device or source acceptance. Tracker integration is deferred by the user until core features pass. Physical 2 GB phone benchmarks cannot be claimed from the API 30 emulator.

Design: retain the existing black (#000000), panel (#0B100D), green (#39D353), lime (#D5FA39), pale text (#F2F6EF) palette and bundled Outfit type. Keep left-aligned compact lists and adaptive covers. Put occasional reader controls in a scrollable sheet, filters in compact chips, and explicit review dialogs around migration/destructive actions. No extra dashboard chrome. Review against the request: additional controls must not crowd author/manga browsing or reinstate idle Select bars.

## Reader
- [x] Backward chapter loading with stable anchors/bounded window; numeric gap warning/confirmation.
- [x] Chapter/page jump, seek and previous chapter at first paged page.
- [x] Saved navigation identifiers and process-death recovery.
- [x] Brightness/dim/orientation/volume controls.
- [x] Webtoon zoom and opt-in detection.
- [x] Configurable conservative preloading and disk cache budget.
- [x] Explain selected Smart Chapter release; alternate picker and per-title language.

## Library
- [x] Categories, unread counts/status filters; bulk mark unread.
- [x] Scheduled chapter checks, grouped Updates and opt-in notifications.
- [x] Conservative followed-author checks/notifications; source artist completeness fallback.
- [x] Explicit reviewed migration, aliases/progress transfer and edition choices.

## Downloads and sources
- [x] Durable queue order/Download now/reorder/cancel retaining files.
- [x] Distinct real network/storage wait messages.
- [x] Grouping/ranges and document-provider export.
- [x] Source toggles/capabilities/status and saved searches.
- [x] Verified redirect allowlists/browser fallback/targeted reset.
- [x] Bounded local CBZ/folder import.
- [ ] Trackers: deferred by user until core acceptance.

## Appearance and quality
- [x] System theme/density, cover states and consistent badges.
- [ ] Accessibility checks at large fonts/landscape/RTL.
- [x] Measured query improvements and reproducible synthetic performance fixture.
- [ ] Broader Android CI/migration/upgrade/process/network interruption checks.
- [x] Updater channels/release notes/snooze/conditional HTTP/cooldown.
- [ ] Physical low-memory benchmarks and real protected-source challenge tests remain hardware/network acceptance items.

## Release gate
- [x] 0.8.0 unit/instrumentation/migration checks; lint/build.
- [x] Update TODO with actual results and outstanding external acceptance.
- [x] Push user-authored commits, publish 0.8.0 and inspect build/artifact/signature/local in-place upgrade. No assistant attribution. The 0.8.0 cloud signed upgrade subsequently passed both APIs.

The already-published language-badge release is v0.7.5 / a78e2fd; it has passed signed upgrade validation. Do not roll that feature back while extending the app.

## Queued follow-up: Support (requested during this pass)
- [x] After core backlog implementation, add a new Support tab inside Settings occupying the full content page.
- [x] Use https://www.ui-layouts.com/components/buy-me-coffee as the element reference, adapted to native Compose and the existing app design.
- [x] Link the support action to https://ko-fi.com/joyarz and identify the service accurately as Ko-fi.
- [x] Add concise, voluntary support copy explaining that Komori is an open-source project.
- [x] Add a README Support section with a Ko-fi icon and the same link; validate link/touch behavior and include in the final build.

## Published 0.8.0 validation

- Release tag `v0.8.0` / `fad6beb`, code 123. Release run `39621002834` attempt 2 and exact-tag Android checks `39624937617` succeeded.
- JVM: 143 total, 138 passed, five optional live-source skips. The explicit earlier HentaiZap audit returned 19 verified Kanamirubon works including gallery 1665352; this is point-in-time evidence.
- Device run `39620993524`: API 26 and API 36 each passed all 38 instrumentation cases, with zero skips. Local release lint: zero errors, 45 advisory warnings.
- Downloaded signed APK: 2,502,223 bytes; SHA-256 `597d32f03fde8d965706102fabd57a0b8b8b414c5756a63c3e2fde6da8eaa3df`. Original signing identity verified.
- Installed the published APK over production 0.7.5 on API 30. Schema 5→6 and every prior column/value in two manga/shelf/download rows, four progress rows, one creator and one title preference were retained.
- Historical cloud signed-upgrade run `39626710821` failed: API 26 could not terminate the background process; API 36 had a transient empty UI hierarchy. The bounded diagnostic capture worked. The revised harness passed both APIs in `39651649241`, retaining all assertions and verifying original PID exit before relaunch.
- Warm API 30 query fixture: 1,000 titles / 50,000 progress rows, five samples. Median all-progress materialization 294.681 ms versus selected-title 50 rows 1.642 ms, using `index_progress_mangaId`. Physical 2 GB phone and cold-start/long-read benchmarks remain open.
- Support initial viewport, exact Ko-fi URI and README banner were reviewed. Publication of the Ko-fi announcement remains pending; the draft includes GitHub and latest-release links.

## October 22 follow-up

User confirmed Nhentai connections are working. New implementation adds scheduled metadata backups, download storage/integrity/delete-read controls and manual refresh buttons. New-pass validation is recorded in VALIDATION.md when complete; these features are not part of the already-published 0.8.0 APK. Trackers remain deferred.

## Published 0.8.1 follow-up

- Immutable `v0.8.1` at `bbb288b`, version code 124. Android checks `39663349030`, device checks `39663348851` (45/45 on each API 26/36) and signed release `39664480684` passed. JVM: 146 passed, five optional live-source skips. Local API 30 cover/filter interaction checks passed 3/3.
- Delivered scheduled metadata backups/verified retention, storage and integrity scans, reviewed delete-read cleanup, reachable Settings → Reading cache controls, all five manual refresh controls and bounded HentaiZap gallery-cover fallback. Tracker integration stays deferred.
- APK: 2,518,607 bytes; SHA-256 `3aa5b3195a8892385381460147122ea6825425070146508dd8c29b57d8240107`. Updater identity/size/hash and original certificate verified; debug document-provider fixtures are absent from the release manifest.
- Published-APK signed upgrade `39673007949` passed API 26 and API 36: schema 5→6, saved/liked/progress retention, verified process death, reader restoration and offline page navigation. Broader source/network, accessibility, slow-transfer interruption and physical 2 GB benchmarks remain open.

## Published 0.8.2 — October 26

- `v0.8.2` / `d644594`, code 125. Adds Manhwa18.com/.cc, encrypted source/CDN DNS, dedicated/recovered MangaDex image transport, expanded source-aware search filters, final-page completion/Read again and foreground downloads.
- Android checks `40305624000`, device checks `40305623963` (47/47 on each API 26/36) and signed release `40306953126` passed. Local API 30 targeted reader/download/filter/refresh checks passed 5/5. Live source and actual image checks passed on the tested network.
- APK checksum/size/updater metadata/original signing identity verified. Published-APK signed-upgrade run `40307898798` passed both API 26/36 with data retention, verified process death, restored reading and offline navigation. Force stop/system quotas remain Android constraints; no claim of unrestricted background execution is made.


## Published 0.8.3 — October 26

- Immutable `v0.8.3` at `d9c0b65`, version code 126. Recovers interrupted queue work when the activity/network returns, retains intentional retry delays and paused rows, and holds a bounded CPU wake lock during transfers.
- Reader lookahead waits for the current image and then loads up to ten files sequentially, independent of scrolling. One budget spans continuous chapters, respects gap warnings and cancels on navigation; visible images are protected from cache eviction and speculative loads never mark pages read. Existing selected preload values are retained; the unset default is ten.
- JVM: 164 passed, six optional live-network skips. Local API 30 reader/background checks passed 3/3. Android checks `40357415070`, device checks `40357414941` (48/48 per API 26/36) and release `40358899789` passed.
- Published APK: 2,551,435 bytes; SHA-256 `0677fe765d382ae94d3eb66ac21dfdcaa2da82b14544c539f69c45eff66f9a22`. Updater size/hash, package identity, original signer and foreground/wake-lock manifest verified. Physical screen-off/manufacturer restriction acceptance remains open.


## Published 0.8.4 — October 27

- Immutable `v0.8.4` at `18b194f`, version code 127. Fixes cache-triggered scrolling image blinking: cached file reads no longer change decoded strip keys; bounded access tracking maintains cache eviction order separately. Real image replacements still invalidate decoded strips. Ten-image lookahead and download recovery remain enabled.
- Local JVM/lint/app/test builds passed (164 JVM passes, six optional live-network skips). Android checks `40370932866`, device checks `40370932701` (50/50 each API 26/36, zero skips) and release `40372315984` passed. Both platforms verified repeated cache hits reuse the exact decoded bitmap and replacement files produce the new pixels.
- Published APK: 2,551,435 bytes; SHA-256 `f62b8f21255845d1af5cf46b06e6bd6c394deee5cfaf85f669fadb00b09fbc73`. Updater metadata, package identity, original signing certificate and manifest verified.


## Published 0.8.5 — October 27

- Immutable `v0.8.5` / `ad8988f`, code 128. Completes the seven audit fixes, Authors filters, shared manifests, scoped/checkpointed persistence, component extraction, updater cancellation and full release-note enforcement. Adds conservative continuous-chapter window growth and safe final exit checkpoints.
- Android checks `40391563371`, device checks `40391563386` (66/66 on each API 26/30/36; zero skips; three successful restart probes) and signed release `40393319293` passed. JVM: 168 passed, six optional live-network skips. Final local audit cases: 13 passed; local ARM scrolling remains a separately tracked timing/environment limitation.
- Published APK: 2,567,819 bytes, SHA-256 `4362f62a7d9ec6e62c750487e3a240817b427178f26134258ac46e0ba39fe780`. Package/updater metadata/checksum/original signer/manifest verified. Full 0.8.3/0.8.4 release bodies are backfilled and embedded in the 0.8.5 notes. Physical hardware, full accessibility, protected-source/live network and slow-worker termination stress remain open; tracking is deferred.
