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
- [x] Push user-authored commits, publish 0.8.0 and inspect build/artifact/signature/local in-place upgrade. No assistant attribution. Additional cloud upgrade checks remain open.

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
