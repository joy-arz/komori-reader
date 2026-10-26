# Komori development backlog

Updated October 26, 2026 for the new-source, search, rereading and background-download pass. Published delivery is listed separately below. Checkboxes record implemented features with local checks; device/network acceptance that remains unverified stays open. See [the pass log](BACKLOG_PASS.md) for exact results and release gates. Tracking is deferred by the user until core acceptance.

## Published delivery ledger

| Release | Release commit | Delivered scope |
|---|---|---|
| 0.7.0 | `088b9a2` | Includes earlier metadata/author following, Comix removal, paginated author catalogs, batch actions and the cropped bat icon. |
| 0.7.1 | `5994fc8` | Compact Library, Discover and Downloads; long-press selection without idle Select bars. |
| 0.7.2 | `565085c` | Authors Latest default, Popularity/views, Alphabetical and Author ordering; minimal continuous-reader chapter capsules. |
| 0.7.3 | `52f95de` | Typed recovery failures, Retry-After cooldowns, durable deferred download retries and stable reader retry budgets. |
| 0.7.4 | `c8c9d5f` | Manual metadata backup/export, strict validation, restore preview and transactional merge. |
| 0.7.5 | `a78e2fd` | Language badges over thumbnail corners, including supported gallery browse flags. |
| 0.8.0 | `fad6beb` | Reader controls and continuity, Library organization/Updates, durable download queue, local imports, source tools, updater channels and Support. |
| 0.8.1 | `bbb288b` | Scheduled metadata backups with verified retention, storage/integrity/delete-read/cache controls, five refresh buttons and HentaiZap cover recovery. |
| 0.8.2 | `d644594` | Manhwa18.com/.cc, encrypted DNS and MangaDex image recovery, expanded filters, automatic final-page completion/Read again and foreground downloads. |

All nine releases are published. The latest signed release is 0.8.2 (version code 125). Its build, 94 API 26/36 device checks and original signing identity passed. Published-APK signed-upgrade run `40307898798` passed both APIs, including process-death reader restoration and offline navigation. The earlier 0.8.0 cloud upgrade passed both APIs. GitHub contributors list only `joy-arz`.

## In validation: sleep recovery and reader lookahead (0.8.3)

- [x] Reconcile interrupted queued/downloading WorkManager requests on app return and network return; preserve retry delays and paused rows.
- [x] Hold a bounded CPU wake lock during foreground chapter transfers and release it on completion or cancellation.
- [x] Preload up to ten images sequentially after the visible image, independently of scrolling, with shared lookahead across continuous chapters and cancellation on navigation.
- [ ] Complete new scheduler/preload regression checks, exact-commit device gates and signed release verification.
- [ ] Confirm sustained screen-off transfers on physical devices with manufacturer battery restrictions; emulator checks cannot establish this.

## Published October 26: 0.8.2

- [x] Implement Manhwa18.com and Manhwa18.cc catalogs, filters, details, chapters and reader images with separate source/session identities.
- [x] Add encrypted DNS with normal TLS checks; isolate MangaDex image transport, bound reset recovery and support alternate image quality when the preferred list is empty.
- [x] Expand language/category/status/rating filters by source capability, preserve saved-search state and refine unsupported search combinations page by page without dropping pagination.
- [x] Record completion without an extra Finish action and offer Read again directly at the end.
- [x] Run persisted downloads with foreground progress and expedited/normal-job scheduling; retain the queue after normal activity dismissal.
- [x] Complete exact-commit device/build gates and publish/verify signed 0.8.2. Android checks `40305624000`, device checks `40305623963` (47/47 on each API 26/36) and release `40306953126` passed. Downloaded APK identity/checksum/original signer and foreground-service manifest verified. See [source and behavior notes](SOURCES_2026-10-26.md).

## Published 0.8.1 follow-up

- [x] Implement optional daily/weekly metadata backups to a selected document-provider folder, read-back verification, configurable 3/7/14 retention and an immediate backup action.
- [x] Implement on-demand image/checksum integrity scans, downloaded-file size totals and a reviewed delete-read action that rechecks progress/queue state before deletion. Add temporary reader-cache clearing without deleting downloaded files.
- [x] Add compact top refresh controls to Discover, Authors, Library, title details and reading; preserve query/filter context and offline files.
- [x] Add a bounded HentaiZap gallery-cover fallback when a Discover thumbnail fails. Preserve verification/rate-limit failures; no gallery request is made for a successful thumbnail. Device acceptance is included in the release gate below.
- [x] Complete unit, lint, provider, storage and refresh interaction checks; push and publish signed 0.8.1. Android checks `39663349030` and device checks `39663348851` passed at the exact release commit; 45/45 on each API 26/36. Release `39664480684` passed; downloaded APK hash, updater metadata and original signer verified.

## Highest-priority source defect: incomplete author catalogs

- [x] Supplement HentaiZap author catalogs in Author search and Authors with exact-creator-verified search results. The live check returned 19 Kanamirubon works including reported gallery `1665352`; fixtures reject name-only matches and preserve pagination through filtered pages and missing native index pages. HTTP 403 still requires source action. See [the source audit](AUTHOR_CATALOG_AUDIT_2026-10-21.md). Source indexes/searches can still omit works; this is not a universal completeness guarantee.

Pagination support alone does not prove that a source's artist index contains every work. Keep the delivered pagination item checked while tracking this separate completeness defect. Latest/popularity follow native source rank; alphabetical/author sort loaded works, not an unseen complete catalog.

## Delivered feature requests
- [x] Remove Comix from the enabled sources.
- [x] Parse source-provided authors/tags and add parody, language and category browsing for supported gallery sources.
- [x] Keep Authors separate, follow creators, default to All works rather than saved-only works, and compact the toolbar without saved-title highlighting. The All filter does not guarantee completeness of the source index.
- [x] Default Authors to Latest; add Popularity/views, Alphabetical and Author sorting. Latest/popularity blend each source's ranking; alphabetical/author apply to loaded works.
- [x] Compact Library, Discover and Downloads; remove idle Select bars while retaining long-press selection.
- [x] Use minimal chapter capsules between chapters in continuous scrolling.
- [x] Overlay compact language badges on manga thumbnails using explicit metadata; retain supported gallery-card language flags before opening details (0.7.5 change). Unknown languages remain unlabelled.
- [x] Publish signed 0.7.0–0.7.4 and verify in-place upgrades retain Library state.

## Delivered catalogs and batch actions
- [x] Load pages from native author catalogs, including unsaved works; rotate combined feeds and deduplicate titles. Source-index completeness is tracked separately above.
- [x] Add Downloaded Library collection with offline opening and partial-download visibility.
- [x] Add selection/batch actions to title grids, chapters, downloads and followed creators; confirm destructive actions.
- [x] Clip round toolbar touch feedback to prevent square highlights.
- [x] Isolate MangaDex API transport from browser sessions and retain a successful HTTP/1.1 fallback temporarily.
- [x] Close the reported Nhentai connection-reset issue: user confirmed the connection is working on October 22. Broader source/network and toolbar acceptance remains separate.

## Reader, downloads and navigation follow-up
- [x] Default image retry to 3 seconds while preserving explicit preferences.
- [x] Let cached pages bypass network waits; add bounded webtoon strip reuse and two concurrent reader image transfers.
- [x] Show manifest/byte download progress; apply Wi-Fi-only changes to the existing queue.
- [x] Preserve MangaDex client identity, request port 443 image hosts and retry connection resets once.
- [x] Add creator subscription strip, saved-works filters and follow management.
- [x] Restore exact grid/chapter-list scroll position after title/reader navigation.
- [x] Keep filters scoped to their query; restore search/result/tag-origin history on Back.
- [ ] Check MangaDex transport recovery and live reading/downloads across every enabled source. The reported Nhentai connection reset is user-confirmed resolved; synthetic worker/transfer tests do not establish every site's availability.

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
- [x] Implement optional scheduled backups with retention; new-pass export/restore validation is tracked above. Manual metadata backup round trips, merge policies and rollback are already validated in 0.7.4; global device settings and downloaded images remain outside that format.
- [x] Implement downloaded-file integrity scan, size totals, cache controls and delete-read preview; new-pass acceptance is tracked above.

## Reader
- [x] Load previous chapters on backward scrolling with stable anchors and bounded memory.
- [x] Warn about missing chapter-number gaps before transitions.
- [x] Add chapter/page jump and seek controls; previous chapter from first paged page.
- [x] Restore selected title/source/filter/reader after process death using saved identifiers and Room.
- [x] Add brightness/dimming, orientation lock and optional volume-key navigation.
- [x] Add webtoon zoom and optional automatic webtoon detection.
- [x] Expose conservative preload/cache choices; preserve per-title settings.
- [x] Explain why Smart Chapters selected a particular release, add a dedicated alternate-release picker and per-title language overrides. Existing All releases and scanlation preferences remain available.

## Library and updates
- [x] Add categories, unread badges and status filters.
- [x] Add multi-select and bulk Library actions, including mark read.
- [x] Add title-level bulk mark unread.
- [x] Add scheduled chapter checks, grouped Updates feed and optional notifications.
- [x] Add optional followed-author new-work notifications without default aggressive polling.
- [x] Add reviewed source migration with progress transfer, aliases and edition selection; never silently merge by title.

## Downloads and sources
- [x] Persist explicit queue order; add Download now, reorder and cancel-selected without deleting files. Batch pause/resume and confirmed Delete downloads already exist.
- [x] Show Waiting for Wi-Fi/storage separately from queued/downloading.
- [x] Download selected chapters or next 1/5/10/25 unread, all unread and all readable; skip active/completed entries.
- [x] Group downloads by title; add arbitrary chapter-range selection and optional export/folder selection.
- [x] Add source enable/disable, capability/status labels and saved searches.
- [ ] Finish live verification-redirect acceptance per protected source. Native access testing, HTTPS/frame/host allowlists and full-browser fallback are implemented; fixture allowlist checks pass. Real challenges remain network/device acceptance.
- [ ] Finish real-device challenge acceptance. Targeted reset is implemented and API 30-tested for root/path cookies and other-source isolation. WebView cannot enumerate every unknown cookie path; all-session reset remains an explicit fallback. TLS checks and user-operated verification stay enabled.
- [x] Add optional local CBZ/folder import.
- [ ] Consider optional AniList/MAL tracking after core reliability; retain account-free use.

## Appearance, performance and release quality
- [x] Group Settings by purpose and compact browsing toolbars.
- [x] Add system theme and a compact/comfortable layout preference.
- [x] Add cover loading/error placeholders and consistent source/unread badges.
- [ ] Audit TalkBack, large fonts, landscape, contrast, touch targets and RTL layouts.
- [x] Replace repeated library scans with maps and scoped database queries; measure before adding paging.
- [ ] Benchmark cold start (including source sessions), 1,000-title libraries/50,000 progress rows and long reads on a physical 2 GB phone.
- [x] Complete 0.8.0 cloud signed-upgrade acceptance on API 26/36. Run 39651649241 passed both APIs: schema 5→6, saved/liked/progress retention, verified process death and offline reader navigation. The harness retries transient UI capture and uses targeted emulator SIGKILL when Android ignores am kill, without force-stopping the package.
- [x] Add Stable/Preview updater channels, release notes, snooze and conditional requests with longer cooldown.

## Support
- [x] Add a full-page Settings → Support tab inspired by the requested coffee component, correctly labelled Ko-fi and linked to `https://ko-fi.com/joyarz`. Device navigation and exact URI checks passed; revised initial viewport reviewed.
- [x] Add a README Support section with a Ko-fi icon and voluntary open-source support copy.

## Acceptance boundaries
- Webtoon forward/backward continuity and reader controls have API 30 checks; sustained zoom/detection, notifications, document-provider variations and source migration still need broader hands-on acceptance.
- A 1,000-title / 50,000-progress synthetic emulator query measurement is recorded in BACKLOG_PASS.md. It does not close the physical 2 GB cold-start/long-read benchmark.
- Nhentai connection recovery is user-confirmed on October 22; this does not establish availability on every network.
