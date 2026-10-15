# Current Komori review against Komikku and Mihon

Reviewed October 15, 2026. Komori main: b422d6e4eca0bf7eb7cd3ac7489059a19542356b, release v0.6.0-alpha.1. Primary reference: Komikku master 1656575e8fdaaef010c652f9758130c2a01d8890. Secondary: Mihon main 446f6df82c390ab760802c2fce3b677bce3f31c1. These are point-in-time checks, not promises of continuing parity.

## Current GitHub and validation

- Local checkout matches current GitHub main; no code synchronization was required.
- Latest Android checks (38732482819) and signed release (38732503893) succeeded. Release includes APK (2,090,049 bytes), SHA256SUMS.txt and update.json. Asset presence checked; this review did not repeat signature/install verification.
- PRs #1/#2 remain merged. No open issues returned by GitHub at review time; absence of issues is not evidence of defect-free software.
- Fresh `cleanTestDebugUnitTest testDebugUnitTest lintDebug`: 68 tests total, 64 passed, 4 optional live tests skipped; zero errors/failures. Lint: no errors, 18 warnings.
- Code/test audit only. No new device walkthrough, real challenge completion, physical low-memory test or comprehensive live-source test was performed.

## Earlier findings that are now fixed or materially improved

Browser verification is implemented: visible user-operated WebView, source cookie bridge, shared user agent and source-aware cover loading. Source-action errors stop automatic retries in the main paged reader, webtoon page-manifest and next-chapter paths; downloads enter Verification required. The strip reload exception below remains.

Authors now have a dedicated destination and source-aware browsing. Structured source metadata and tag/author search support were added for selected adapters. Following remains local, without scheduled notifications. Comix was removed, not completed. HTTP source/catalog calls now support cancellation. Rich cached metadata is retained during failed refreshes. Room 4→5 migration and session tests were added. Next-chapter loading now keys on neighbor identity. Image size limits, neutral network errors, reactive bulk counts and local-page repair were already added in alpha.2.

Do not use APP_AUDIT_2026-10-15.md as a current defect list without accounting for these changes.

## Findings: defects and risks

### P1: webtoon strip reload discards verification errors

Evidence: `app/src/main/java/app/komori/ui/WebtoonReader.kt:219`. Once dimensions are known, the strip loader catches every non-cancellation exception into a Boolean `decodeError`. Its retry countdown uses that Boolean without `requiresSourceAction`, and its error control provides Retry rather than Verify source. Unlike the initial page loader, it loses SourceVerificationRequired/401/403 distinctions.

Trigger: revisit a page whose file was evicted while its dimensions remain in the chapter window, with the source session expired. Code-level finding; not live-device reproduced in this review.

Fix: retain the typed error for strip reloads, disable timed retries on source-action failures, and offer Verify source. Share one page-level loader/error state across strips so one image does not spawn independent retries. Regression: a cached-dimensions page returns a challenge; assert one failure, no timer retry and a verification action.

### P1: failed repair leaves inaccurate offline completeness

Evidence: `ReaderViewModel.kt:486` moves a local page to `.repair`; `pageFileFor` only restores it after a successful fetch. The Download record remains Complete and no repair state/count reconciliation occurs. `enqueueDownload` immediately returns for Complete entries.

Trigger: Retry on a local decode error, followed by offline/network failure. The reader no longer has the canonical page file, but Downloads still advertises the chapter as fully available. Repair of truly missing files also needs durable intent rather than relying only on a `.repair` filename.

Fix: persist Needs repair with affected page indices; restore verified pages atomically, reconcile counters, and allow targeted repair from Downloads. Do not discard a good local page on an unrelated network error. Regression: fail repair midway, restart offline, verify accurate state and that other pages still work; restore connectivity and repair only the failed page.

### P1 risk: worker publication and deletion are not coordinated

Evidence: Downloads.kt serializes workers with a global mutex, but ReaderViewModel pause/delete uses WorkManager cancellation acknowledgement and directly changes rows/files outside that mutex. Cancellation-aware network transfer is an improvement, but is not a worker-ownership protocol.

Potential outcome: an in-flight progress write or filesystem action races with pause/delete. Not reproduced here; needs a deterministic slow-transfer/manifest test.

Fix: per-chapter coordination and generation/ownership checks before publishing progress or completion; invalidate ownership before deletion. Include reader repair in file coordination.

### P2: endless scrolling cannot retrieve discarded earlier chapters

WebtoonReader drops older chapters from the bounded window and only appends next neighbors. Scrolling back eventually hits a retained-window boundary. This is a confirmed implementation limitation, not a build failure.

Komikku explicitly supplies previous/current/next chapters and transition objects, including chapter-gap checks: [WebtoonAdapter](https://github.com/komikku-app/komikku/blob/1656575e8fdaaef010c652f9758130c2a01d8890/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonAdapter.kt).

Fix: prepend previous neighbors with stable anchoring; display a missing-chapter warning before silently moving across a numeric gap. Test forward five/back four, rotation and process recreation.

### P2: retry policy is only partly failure-aware

Verification and 401/403 have special handling in most paths. Offline, 429/Retry-After, permanently missing pages and insufficient storage still share generic timed/exponential retries. Per-visible-item counters reset when composition is discarded.

Fix: typed failures and stable page-level budgets, connectivity-triggered recovery, Retry-After compliance and a storage action. Avoid timer retries for permanent failures.

### P2: reader/navigation restoration is incomplete after process death

Progress is persisted, but selected title, current reader, source and filters mainly live in ViewModel state. No SavedStateHandle navigation restoration exists. Rotation surviving does not prove process-death restoration.

Fix: save small navigation identifiers, rebuild from Room, and restore the page/scroll location; test background process kill without force-stop semantics.

### Verification compatibility remains unproven

The screen now exists, so calling verification entirely missing would be wrong. However, real successful challenges on protected sources were not demonstrated by this review. Existing validation notes report access restrictions on their validation network. The activity accepts only its exact source host for top-level navigation: legitimate www/alternate-host redirects may be rejected and require source-specific validation. There is no full-browser fallback in that screen, and session reset clears all sources. Do not solve these by broadly allowing arbitrary navigation or disabling TLS checks.

Verify on a physical device/network, explicitly show whether the retried native request succeeds, offer browser fallback with honest cookie limitations, and add targeted session management. Cookie bridging does not guarantee native access.

## Feature comparison and recommendations

| Area | Current Komori | Next useful addition |
|---|---|---|
| Backup | No export/import; Android backup disabled | Validated metadata backup/restore, then scheduled backups |
| Library | Saved/Liked/history/status and duplicate warning | Categories, unread badges, status filters, multi-select, mark read/unread |
| Updates | App APK updates only | Scheduled manga chapter checks, grouped Updates feed, opt-in notifications |
| Downloads | Next N/all unread/all readable, pause/resume, verification state | Durable queue order, Download now, cancel selected, explicit Wi-Fi/storage waits |
| Storage | Internal downloads, bounded reader cache | Size totals, repair scan, clear cache, delete-read preview, optional folder/export support |
| Reader | Per-title LTR/RTL/webtoon, forward continuous chapters | Backward chapters, jump/seek controls, brightness/rotation, webtoon zoom |
| Smart Chapters | Conservative volume/language/number grouping; preferred scanlation | Explain selected release, show alternatives, gap warnings, per-title language |
| Sources | Ordered tabs, verification, source-dependent sorting/tags | Enable/disable, connection status, saved searches, supported-capability labels |
| Authors | Local follows and source works | Optional new-work/update notifications; no silent background polling by default |
| Migration | Normalized-title warning only | Reviewed source migration preserving progress, aliases and edition choice |
| Tracking | None | Optional AniList/MAL later; keep local use account-free |
| Local files | App-managed downloads only | Optional CBZ/folder import, separate from downloaded-content support |
| Appearance | Light/dark jade theme | System theme, density choice, grouped Settings, accessibility/large-font checks |
| Release quality | JVM/lint/build/signing CI | API 26 and 36 instrumentation, upgrade/process-death/slow-network tests |

Komikku's [DownloadManager](https://github.com/komikku-app/komikku/blob/1656575e8fdaaef010c652f9758130c2a01d8890/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt) provides queue reorder, start-now and selected cancellation operations. Its [ReaderPreferences](https://github.com/komikku-app/komikku/blob/1656575e8fdaaef010c652f9758130c2a01d8890/app/src/main/java/eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt) includes webtoon zoom, orientation, brightness, volume keys, cache/preload controls and automatic webtoon mode. These are reference behaviors, not a proposal to copy every option or enable aggressive loading on low-end phones.

Mihon's [BackupCreator](https://github.com/mihonapp/mihon/blob/446f6df82c390ab760802c2fce3b677bce3f31c1/app/src/main/java/eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt) includes selectable backup data, automatic backup retention and validation of the written backup. Use those reliability principles for a smaller Komori backup format. [Mihon](https://github.com/mihonapp/mihon) is the secondary baseline; [Komikku](https://github.com/komikku-app/komikku) remains the main feature reference.

## Low-end performance priorities

Whole-table manga/progress/download flows and repeated list scans remain. Replace repeated lookups with maps and introduce title-scoped projections; measure before adding paging. Session initialization now creates WebView-related infrastructure through the shared client; measure cold-start impact rather than assuming it is free. Keep one/few downloads and bounded reader preloading. Test 1,000 saved titles/50,000 progress rows on a physical 2 GB device. No such benchmark was performed here.

## Recommended implementation sequence

1. Reliability patch: strip error handling, durable repair status and worker/file ownership; add failing-case regressions first.
2. Data protection: metadata backup/restore and download integrity scan.
3. Reading continuity: backward chapter loading, gap warnings, jump controls and process-death restore.
4. Daily use: categories/unread badges, grouped chapter updates and durable download queue.
5. Optional expansion: saved searches, migration, local-file import, trackers and appearance controls.

No app code or release was changed by this review. Passing builds establish build health, not universal source compatibility or low-end performance.
