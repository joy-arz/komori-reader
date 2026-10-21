# Komori development backlog

Updated October 21, 2026 after fetching GitHub main at `b577b5b` and checking the published releases through `v0.7.4`. Reconciled against source code and [release validation](VALIDATION.md), rather than treating the October 15 comparison as current status. Komikku remains the main behavior reference and Mihon the secondary reference. Checkboxes mean delivered and validated, not merely started. Open items below are remaining work, not release promises. The reconciled checklist contains **37 completed groups and 34 open groups**; groups can contain several related tasks.

## Published delivery ledger

| Release | Release commit | Delivered scope |
|---|---|---|
| 0.7.0 | `088b9a2` | Includes earlier metadata/author following, Comix removal, paginated author catalogs, batch actions and the cropped bat icon. |
| 0.7.1 | `5994fc8` | Compact Library, Discover and Downloads; long-press selection without idle Select bars. |
| 0.7.2 | `565085c` | Authors Latest default, Popularity/views, Alphabetical and Author ordering; minimal continuous-reader chapter capsules. |
| 0.7.3 | `52f95de` | Typed recovery failures, Retry-After cooldowns, durable deferred download retries and stable reader retry budgets. |
| 0.7.4 | `c8c9d5f` | Manual metadata backup/export, strict validation, restore preview and transactional merge. |

All five releases are published. The latest signed release is 0.7.4; its CI, signature, in-place upgrade and real document-picker checks are recorded in VALIDATION.md. The later `b577b5b` commit records that validation; it does not add app behavior. GitHub contributors list only `joy-arz`.

## Highest-priority source defect: incomplete author catalogs

- [ ] Fix HentaiZap author catalog completeness in both Author search and the Authors tab. On October 21 the native `/artist/kanamirubon/` listing returned only gallery `1580076`, while name search returned 12 works including user-reported `1665352`; that gallery explicitly links to the same artist. The app currently follows the incomplete native artist listing. This remains unfixed in 0.7.4. See [the reproduction and acceptance criteria](AUTHOR_CATALOG_AUDIT_2026-10-21.md). Do not treat unverified title/name matches as confirmed creator works.

Pagination support alone does not prove that a source's artist index contains every work. Keep the delivered pagination item checked while tracking this separate completeness defect. Latest/popularity follow native source rank; alphabetical/author sort loaded works, not an unseen complete catalog.

## Delivered feature requests through 0.7.4
- [x] Remove Comix from the enabled sources.
- [x] Parse source-provided authors/tags and add parody, language and category browsing for supported gallery sources.
- [x] Keep Authors separate, follow creators, default to All works rather than saved-only works, and compact the toolbar without saved-title highlighting. The All filter does not guarantee completeness of the source index.
- [x] Default Authors to Latest; add Popularity/views, Alphabetical and Author sorting. Latest/popularity blend each source's ranking; alphabetical/author apply to loaded works.
- [x] Compact Library, Discover and Downloads; remove idle Select bars while retaining long-press selection.
- [x] Use minimal chapter capsules between chapters in continuous scrolling.
- [x] Publish signed 0.7.0–0.7.4 and verify in-place upgrades retain Library state.

## Delivered catalogs and batch actions
- [x] Load pages from native author catalogs, including unsaved works; rotate combined feeds and deduplicate titles. Source-index completeness is tracked separately above.
- [x] Add Downloaded Library collection with offline opening and partial-download visibility.
- [x] Add selection/batch actions to title grids, chapters, downloads and followed creators; confirm destructive actions.
- [x] Clip round toolbar touch feedback to prevent square highlights.
- [x] Isolate MangaDex API transport from browser sessions and retain a successful HTTP/1.1 fallback temporarily.
- [ ] Confirm intermittent source connection-reset recovery and toolbar rendering on the affected Android 14 phone, including the reported Nhentai failure that Verify source did not resolve.

## Reader, downloads and navigation follow-up
- [x] Default image retry to 3 seconds while preserving explicit preferences.
- [x] Let cached pages bypass network waits; add bounded webtoon strip reuse and two concurrent reader image transfers.
- [x] Show manifest/byte download progress; apply Wi-Fi-only changes to the existing queue.
- [x] Preserve MangaDex client identity, request port 443 image hosts and retry connection resets once.
- [x] Add creator subscription strip, saved-works filters and follow management.
- [x] Restore exact grid/chapter-list scroll position after title/reader navigation.
- [x] Keep filters scoped to their query; restore search/result/tag-origin history on Back.
- [ ] Reproduce the reported Nhentai connection reset on the affected device/network and separately check MangaDex transport recovery; verify live reading/downloads across every enabled source. Synthetic worker/transfer tests do not establish every site's availability.

## Reference-led visual redesign
- [x] Replace shared chrome, typography, colors and controls with the black/green/lime design; retain all existing destinations and settings.
- [x] Group Settings, add outlined collection counts and keep catalog loading visible.
- [x] Use native Compose drag arbitration and test real navigation/search/cover/swipe touches.
- [x] Keep automatic update checks/downloads nonmodal.

## Compact browsing UI
- [x] Expand Discover and Library search from icons; keep source filters and verification accessible.
- [x] Swipe left/right through sources in Settings order without loading all catalogs.
- [x] Tighten grid spacing and simplify shared navigation; inspect the UI on an Android emulator.

## Delivered automatic loading and reader error handling
- [x] Automatically fetch the next source results page near the end; retain source/query/sort/tag/author filters. Prevent concurrent duplicate requests, stop on errors and repeated/empty pages, offer manual retry.
- [x] Keep forward chapter loading automatic in webtoon mode, including newly refreshed chapter feeds. Keep offline reads local and respect the continuous-chapters setting.
- [x] Preserve verification errors in webtoon strip reloads; show Verify source and suppress automatic challenge retries.
- [x] Run regression checks, push changes and publish a signed APK (v0.6.0-alpha.2).

## Reliability and data protection
Released repair work: durable per-page repair markers, Needs repair counts, partial offline reading, chapter file locks and stale reader-repair rejection are implemented. Local unit, lint and API 30 device checks passed; full process-death and slow-network WorkManager race coverage remains a follow-up.
- [x] Persist Needs repair/page indices, reconcile completed download counts and repair failed pages while retaining intact files.
- [x] Coordinate worker writes, reader repair, pause and deletion per chapter; reject stale reader repair results and test lock/cancellation behavior.
- [ ] Stress-test repair across process death and real WorkManager pause/delete during slow network transfers.
- [x] Add typed offline/rate-limit/missing-page/storage handling, Retry-After support and stable page retry budgets (0.7.3; JVM and API 30 checks).
- [x] Add document-picker metadata backup/export, strict validation, restore preview and transactional restore; exclude cookies/images by default (0.7.4). Device-wide settings remain excluded.
- [ ] Add optional scheduled backups with retention and tests of scheduled exports/restores. Manual metadata backup round trips, merge policies and rollback are already validated in 0.7.4; global device settings and downloaded images remain outside that format.
- [ ] Add downloaded-file integrity scan, size totals, cache controls and delete-read preview.

## Reader
- [ ] Load previous chapters on backward scrolling with stable anchors and bounded memory.
- [ ] Warn about missing chapter-number gaps before transitions.
- [ ] Add chapter/page jump and seek controls; previous chapter from first paged page.
- [ ] Restore selected title/source/filter/reader after process death using saved identifiers and Room.
- [ ] Add brightness/dimming, orientation lock and optional volume-key navigation.
- [ ] Add webtoon zoom and optional automatic webtoon detection.
- [ ] Expose conservative preload/cache choices; preserve per-title settings.
- [ ] Explain why Smart Chapters selected a particular release, add a dedicated alternate-release picker and per-title language overrides. Existing All releases and scanlation preferences remain available.

## Library and updates
- [ ] Add categories, unread badges and status filters.
- [x] Add multi-select and bulk Library actions, including mark read.
- [ ] Add title-level bulk mark unread.
- [ ] Add scheduled chapter checks, grouped Updates feed and optional notifications.
- [ ] Add optional followed-author new-work notifications without default aggressive polling.
- [ ] Add reviewed source migration with progress transfer, aliases and edition selection; never silently merge by title.

## Downloads and sources
- [ ] Persist explicit queue order; add Download now, reorder and cancel-selected without deleting files. Batch pause/resume and confirmed Delete downloads already exist.
- [ ] Show Waiting for Wi-Fi/storage separately from queued/downloading.
- [x] Download selected chapters or next 1/5/10/25 unread, all unread and all readable; skip active/completed entries.
- [ ] Group downloads by title; add arbitrary chapter-range selection and optional export/folder selection.
- [ ] Add source enable/disable, capability/status labels and saved searches.
- [ ] Test legitimate verification redirects per source; add honest native-access results and full-browser fallback.
- [ ] Add targeted session reset and real-device challenge tests; preserve TLS checks and user-operated verification.
- [ ] Add optional local CBZ/folder import.
- [ ] Consider optional AniList/MAL tracking after core reliability; retain account-free use.

## Appearance, performance and release quality
- [x] Group Settings by purpose and compact browsing toolbars.
- [ ] Add system theme and a compact/comfortable layout preference.
- [ ] Add cover loading/error placeholders and consistent source/unread badges.
- [ ] Audit TalkBack, large fonts, landscape, contrast, touch targets and RTL layouts.
- [ ] Replace repeated library scans with maps and scoped database queries; measure before adding paging.
- [ ] Benchmark cold start (including source sessions), 1,000-title libraries/50,000 progress rows and long reads on a physical 2 GB phone.
- [ ] Add API 26/API 36 instrumentation and signed upgrade/process-death/interrupted-network tests to CI.
- [ ] Add Stable/Preview updater channels, release notes, snooze and conditional requests with longer cooldown.
