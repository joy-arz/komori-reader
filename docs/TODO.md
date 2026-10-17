# Komori development backlog

Updated October 17, 2026. Based on CURRENT_COMPARISON_REVIEW.md; Komikku is the main behavior reference and Mihon the secondary reference. Checkboxes mean delivered and validated, not merely started.

## Reader, downloads and navigation follow-up
- [x] Default image retry to 3 seconds while preserving explicit preferences.
- [x] Let cached pages bypass network waits; add bounded webtoon strip reuse and two concurrent reader image transfers.
- [x] Show manifest/byte download progress; apply Wi-Fi-only changes to the existing queue.
- [x] Preserve MangaDex client identity, request port 443 image hosts and retry connection resets once.
- [x] Add creator subscription strip, saved-works filters and follow management.
- [x] Restore exact grid/chapter-list scroll position after title/reader navigation.
- [x] Keep filters scoped to their query; restore search/result/tag-origin history on Back.
- [ ] Reproduce the reported MangaDex reset on the affected device/network; verify live downloads across each enabled source. Synthetic worker/transfer tests do not establish every site's availability.

## Reference-led visual redesign
- [x] Replace shared chrome, typography, colors and controls with the black/green/lime design; retain all existing destinations and settings.
- [x] Group Settings, add outlined collection counts and keep catalog loading visible.
- [x] Use native Compose drag arbitration and test real navigation/search/cover/swipe touches.
- [x] Keep automatic update checks/downloads nonmodal.

## Compact browsing UI
- [x] Expand Discover and Library search from icons; keep source filters and verification accessible.
- [x] Swipe left/right through sources in Settings order without loading all catalogs.
- [x] Tighten grid spacing and simplify shared navigation; inspect the UI on an Android emulator.

## Current delivery: automatic loading and reader error handling
- [x] Automatically fetch the next source results page near the end; retain source/query/sort/tag/author filters. Prevent concurrent duplicate requests, stop on errors and repeated/empty pages, offer manual retry.
- [x] Keep forward chapter loading automatic in webtoon mode, including newly refreshed chapter feeds. Keep offline reads local and respect the continuous-chapters setting.
- [x] Preserve verification errors in webtoon strip reloads; show Verify source and suppress automatic challenge retries.
- [x] Run regression checks, push changes and publish a signed APK (v0.6.0-alpha.2).

## Reliability and data protection
Current patch: durable per-page repair markers, Needs repair counts, partial offline reading, chapter file locks and stale reader-repair rejection are implemented. Local unit, lint and API 30 device checks passed; full process-death and slow-network WorkManager race coverage remains a follow-up.
- [x] Persist Needs repair/page indices, reconcile completed download counts and repair failed pages while retaining intact files.
- [x] Coordinate worker writes, reader repair, pause and deletion per chapter; reject stale reader repair results and test lock/cancellation behavior.
- [ ] Stress-test repair across process death and real WorkManager pause/delete during slow network transfers.
- [ ] Add typed offline/rate-limit/missing-page/storage handling, Retry-After support and stable page retry budgets.
- [ ] Add document-picker metadata backup/export, strict validation, restore preview and transactional restore; exclude cookies/images by default.
- [ ] Add optional scheduled backups with retention and restore round-trip tests.
- [ ] Add downloaded-file integrity scan, size totals, cache controls and delete-read preview.

## Reader
- [ ] Load previous chapters on backward scrolling with stable anchors and bounded memory.
- [ ] Warn about missing chapter-number gaps before transitions.
- [ ] Add chapter/page jump and seek controls; previous chapter from first paged page.
- [ ] Restore selected title/source/filter/reader after process death using saved identifiers and Room.
- [ ] Add brightness/dimming, orientation lock and optional volume-key navigation.
- [ ] Add webtoon zoom and optional automatic webtoon detection.
- [ ] Expose conservative preload/cache choices; preserve per-title settings.
- [ ] Explain Smart Chapter selection, expose alternate releases and per-title language overrides.

## Library and updates
- [ ] Add categories, unread badges and status filters.
- [ ] Add multi-select, mark read/unread and bulk library actions.
- [ ] Add scheduled chapter checks, grouped Updates feed and optional notifications.
- [ ] Add optional followed-author new-work notifications without default aggressive polling.
- [ ] Add reviewed source migration with progress transfer, aliases and edition selection; never silently merge by title.

## Downloads and sources
- [ ] Persist queue order; add Download now, reorder and cancel-selected controls.
- [ ] Show Waiting for Wi-Fi/storage separately from queued/downloading.
- [ ] Group downloads by title; offer selected/range downloads and optional export/folder selection.
- [ ] Add source enable/disable, capability/status labels and saved searches.
- [ ] Test legitimate verification redirects per source; add honest native-access results and full-browser fallback.
- [ ] Add targeted session reset and real-device challenge tests; preserve TLS checks and user-operated verification.
- [ ] Add optional local CBZ/folder import.
- [ ] Consider optional AniList/MAL tracking after core reliability; retain account-free use.

## Appearance, performance and release quality
- [ ] Add system theme and compact/comfortable layouts; group Settings by purpose.
- [ ] Add cover loading/error placeholders and consistent source/unread badges.
- [ ] Audit TalkBack, large fonts, landscape, contrast, touch targets and RTL layouts.
- [ ] Replace repeated library scans with maps and scoped database queries; measure before adding paging.
- [ ] Benchmark cold start (including source sessions), 1,000-title libraries/50,000 progress rows and long reads on a physical 2 GB phone.
- [ ] Add API 26/API 36 instrumentation and signed upgrade/process-death/interrupted-network tests to CI.
- [ ] Add Stable/Preview updater channels, release notes, snooze and conditional requests with longer cooldown.
