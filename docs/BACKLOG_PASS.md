# Broad backlog implementation pass

Requested October 21, 2026 after the thumbnail badges. Work is ongoing; this document is an execution checklist, not a completion claim. Tracker integration is deferred by the user until core features pass. Physical 2 GB phone benchmarks cannot be claimed from the API 30 emulator.

Design: retain the existing black (#000000), panel (#0B100D), green (#39D353), lime (#D5FA39), pale text (#F2F6EF) palette and bundled Outfit type. Keep left-aligned compact lists and adaptive covers. Put occasional reader controls in a scrollable sheet, filters in compact chips, and explicit review dialogs around migration/destructive actions. No extra dashboard chrome. Review against the request: additional controls must not crowd author/manga browsing or reinstate idle Select bars.

## Reader
- [ ] Backward chapter loading with stable anchors/bounded window; numeric gap warning/confirmation.
- [ ] Chapter/page jump, seek and previous chapter at first paged page.
- [ ] Saved navigation identifiers and process-death recovery.
- [ ] Brightness/dim/orientation/volume controls.
- [ ] Webtoon zoom and opt-in detection.
- [ ] Configurable conservative preloading and disk cache budget.
- [ ] Explain selected Smart Chapter release; alternate picker and per-title language.

## Library
- [ ] Categories, unread counts/status filters; bulk mark unread.
- [ ] Scheduled chapter checks, grouped Updates and opt-in notifications.
- [ ] Conservative followed-author checks/notifications; source artist completeness fallback.
- [ ] Explicit reviewed migration, aliases/progress transfer and edition choices.

## Downloads and sources
- [ ] Durable queue order/Download now/reorder/cancel retaining files.
- [ ] Distinct real network/storage wait messages.
- [ ] Grouping/ranges and document-provider export.
- [ ] Source toggles/capabilities/status and saved searches.
- [ ] Verified redirect allowlists/browser fallback/targeted reset.
- [ ] Bounded local CBZ/folder import.
- [ ] Trackers: deferred by user until core acceptance.

## Appearance and quality
- [ ] System theme/density, cover states and consistent badges.
- [ ] Accessibility checks at large fonts/landscape/RTL.
- [ ] Measured query improvements and reproducible synthetic performance fixture.
- [ ] Broader Android CI/migration/upgrade/process/network interruption checks.
- [ ] Updater channels/release notes/snooze/conditional HTTP/cooldown.
- [ ] Physical low-memory benchmarks and real protected-source challenge tests remain hardware/network acceptance items.

## Release gate
- [ ] Meaningful unit/instrumentation/migration checks; lint/build.
- [ ] Update TODO with actual results and outstanding external acceptance.
- [ ] Push user-authored commits, trigger signed build, inspect CI/artifact/signature and in-place upgrade. No assistant attribution.

The already-published language-badge release is v0.7.5 / a78e2fd; it has passed signed upgrade validation. Do not roll that feature back while extending the app.

## Queued follow-up: Support (requested during this pass)
- [ ] After core backlog implementation, add a new Support tab inside Settings occupying the full content page.
- [ ] Use https://www.ui-layouts.com/components/buy-me-coffee as the element reference, adapted to native Compose and the existing app design.
- [ ] Link the support action to https://ko-fi.com/joyarz and identify the service accurately as Ko-fi.
- [ ] Add concise, voluntary support copy explaining that Komori is an open-source project.
- [ ] Add a README Support section with a Ko-fi icon and the same link; validate link/touch behavior and include in the final build.

## Validation checkpoints (work in progress)

- Debug APK and instrumentation APK assembly passed after resolving an inline Compose return that D8 rejected.
- JVM run: 141 cases, 137 passed, 4 existing network-gated skips. The opt-in HentaiZap live test passed in this run: 19 exact-creator works, including reported gallery 1665352. No reader images fetched by that audit. This verifies the reported case, not every artist or every source.
- API 30 selected device run: 18/18 passed, covering schema migrations, backup merge/rollback, forward continuous reading, organization/count SQL, durable queue ordering, CBZ page validation/order, source-scoped cookie reset and the exact Ko-fi URI.
- API 30 synthetic warm-query fixture: 1,000 titles, 50,000 progress rows, five samples. Median all-progress materialization 294.681 ms; selected-title 50-row query 1.642 ms. EXPLAIN confirms `index_progress_mangaId`. This is an emulator database measurement, not a physical-device/cold-start or long-read benchmark.
- First Support screenshot reviewed; reduced the illustration for constrained viewports so the support action appears earlier. Final screenshot/full-suite validation pending.

- Full API 30 suite: 36/37 passed, exposing a Downloads group-header selection regression. Fixed group-header long press; isolated batch-selection rerun passed. A combined rerun also exposed differing navigation state across tests; fixtures now start a fresh Android task.
- Added reader-control device case passed with volume navigation, previous chapter, gap confirmation, page jump, brightness restoration, and cancel preserving files. Release lint then rejected the AndroidX dispatch override; replaced with public Android key callbacks and queued the case again.
- Disposable API 30 process script passed schema 5→6, saved/liked/progress retention, verified background process exit, reader/page restoration and offline next-page navigation. Signed APK upgrade and API 26/36 runs remain pending.

- Final JVM pass after creator pagination hardening: 143 total, 138 passed, 5 skipped (including the opt-in live check; its earlier explicit run passed). HTTP 404 artist-index continuation and HTTP 403 propagation have separate passing fixtures.
- Reader display overrides moved above per-chapter session composition so chapter changes cannot recapture reader brightness as the normal app baseline. Device/lint recheck pending.

- API 30 process acceptance passed with a synthetic offline fixture. The background-kill harness now waits/retries Android's kill request and verifies PID exit before relaunch; it never substitutes force-stop for process-death acceptance.
- Fixed a chapter-session cleanup race: an old reader can clear only its own volume handler, not a newer chapter's handler. Reader window overrides now live outside the chapter key. Rechecking before push.
