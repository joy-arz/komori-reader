# Komori development backlog

Updated October 27, 2026 for the 0.8.5 reliability and Authors-filter pass. Published delivery is listed separately below. Checkboxes record implemented features with local checks; device/network acceptance that remains unverified stays open. See [the pass log](BACKLOG_PASS.md) for exact results and release gates. Tracking is deferred by the user until core acceptance.

## October 28 reader controls follow-up

- [x] Start each reader session with controls hidden; reserve a two-dimensional center region for deliberate control taps.
- [x] Remove image-strip-wide control clicks and the interactive corner shortcut; retain a passive page count, Back escape and a screen-reader action.
- [x] Merge the reader follow-up (PR #4); pre-merge API 26/30/36 checks passed. Post-merge API 36 Authors catalog scroll assertion needs separate investigation.

## October 27 code review: implemented reliability fixes

See [the October 27 code review](CODEBASE_REVIEW_2026-10-27.md) for conditions, evidence and acceptance checks. The seven fixes are implemented for 0.8.5; exact release acceptance is recorded in VALIDATION.md.

- [x] Reclaim abandoned reader partials safely; include them in cache maintenance and reconcile interrupted local imports.
- [x] Pin every visible/actively decoding reader file during eviction, with bounded cache-pressure behavior.
- [x] Separate planned download execution slices from actual transfer-failure retry counts.
- [x] Unify effective title language across unread counts, reading and migration, including Manhwa raw editions.
- [x] Ensure failing author checks cannot starve other followed creators.
- [x] Keep speculative preload errors out of the visible-page retry budget while honoring source cooldowns.
- [x] Align CBZ import/export size and image-format contracts and add round-trip coverage.

Additional delivery: shared chapter-manifest requests, reduced/checkpointed progress writes, scoped Library observation, screen/controller extraction, cancelable channel-safe updater checks, Authors source/language/category/tag/Library filters, and written full release notes. The 0.8.3/0.8.4 GitHub bodies have been backfilled without changing their assets or tags.

The clean cloud Android 8/11/16 suites cover all 16 new regressions, including 150% font/RTL filter interaction, cache ownership, import round trips and queue-mutation/recovery serialization. CI now covers API 26/30/36. Physical-phone acceptance was explicitly deferred by the user in favor of emulator validation. Full TalkBack/landscape/hardware checks and real slow-network worker termination remain separate acceptance work below.

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
| 0.8.3 | `d9c0b65` | Interrupted queue recovery on app/network return, bounded transfer wake lock and sequential reader lookahead up to ten images across continuous chapters. |
| 0.8.4 | `18b194f` | Stable decoded reader images during cache hits/preloading; separate cache access tracking from image revision timestamps. |
| 0.8.5 | `ad8988f` | Seven audit fixes, Authors metadata/Library filters, manifest sharing, reduced/checkpointed progress writes, continuity/cache recovery, cancelable updates and full changelogs. |

All twelve releases are published. The latest signed release is 0.8.5 (version code 128). Its build/lint, 198 API 26/30/36 device checks, three restart probes and original signing identity passed. The earlier 0.8.2 published-APK signed-upgrade run `40307898798` passed both APIs, including process-death reader restoration and offline navigation. The earlier 0.8.0 cloud upgrade passed both APIs. GitHub contributors list only `joy-arz`.

## Published 0.8.5: reliability and Authors filters

- [x] Complete exact-commit build/lint, 168 JVM passes and 198 cloud device checks with three restart probes; verify the downloaded signed APK and updater metadata.
- [x] Stabilize short-chapter append/trim behavior, release composed pins when closing the reader, and retain final exit scroll checkpoints without reopening it.
- [x] Publish full written 0.8.5 notes with the complete 0.8.3/0.8.4 changes inline; backfill both older GitHub release bodies.
- [ ] Reproduce and resolve local ARM AVD gesture timing under host load; this environment also showed System UI/service ANRs and the 0.8.4 control stalled at the same forward-chapter boundary. Clean cloud API 26/30/36 suites passed. Physical-phone acceptance remains deferred by the user's choice.

## Published 0.8.4: scrolling image stability

- [x] Separate reader cache access tracking from file modification timestamps so lookahead cannot invalidate visible decoded strips.
- [x] Verify repeated cache hits reuse the decoded bitmap while actual image replacements invalidate it; complete build/device gates and signed release verification. Android checks `40370932866`, device checks `40370932701` (50/50 each API 26/36) and release `40372315984` passed. Published updater metadata, APK checksum/size/package and original signer verified.

## Published 0.8.3: sleep recovery and reader lookahead

- [x] Reconcile interrupted queued/downloading WorkManager requests on app return and network return; preserve retry delays and paused rows.
- [x] Hold a bounded CPU wake lock during foreground chapter transfers and release it on completion or cancellation.
- [x] Preload up to ten images sequentially after the visible image, independently of scrolling, with shared lookahead across continuous chapters and cancellation on navigation.
- [x] Complete new scheduler/preload regression checks, exact-commit device gates and signed release verification. Android checks `40357415070`, device checks `40357414941` (48/48 each API 26/36) and release `40358899789` passed; published APK checksum/size/updater/original signer verified.
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


### October 28 background download follow-up

- [x] One Back exits the reader whether controls are hidden or visible (0.8.8).
- [x] Background-download battery status and explicit system consent in Settings and Downloads; regular foreground work for long chapters.
- [x] API 26/30/34/36 emulator acceptance, app switching, battery-exempt forced Doze with next-chapter continuation, and 5/5 Authors selection stress.
- [ ] Verify the affected physical Android 14 handset after granting background access; OEM policy and real-source behavior remain unverified.
- [ ] Investigate intermittent ManualRefresh native center-tap fixture failure seen on the first merged-head API 30 run; fresh rerun passed without source/assertion changes.


### October 30 streamed reader position

- [x] Reproduce the visible placeholder shrinking past its retained offset, skipping an unread page, on all four emulator APIs.
- [x] Add a logical page anchor and delay progress/paging publication until the corrected viewport is measured.
- [x] Accept delayed short/tall image and chapter-boundary fixtures plus the full API 26/30/34/36 device matrix (PR #6, 90 tests per API). Publish and verify signed 0.8.9 after exact merged-commit acceptance.
- [ ] Verify sustained online DemonicScans reading on the affected Android 14 phone.

### October 30 downloaded chapter geometry

- [x] Confirm provisional 420 dp placeholders/strip counts remain for completed offline chapters in 0.8.9.
- [x] Prepare valid completed-download image headers before chapter insertion, retaining lazy bitmap decoding and streaming anchors.
- [x] Accept five-chapter multi-page offline coverage, current-page/pixel retention during next-chapter preparation, concurrent-writer reading and the full API 26/30/34/36 matrix (93 tests each). Signed 0.8.11 published and verified after exact merged-commit acceptance.
- [ ] Verify sustained downloaded continuous reading near chapter boundaries on the affected Android 14 handset; the reported landing is further ahead in the same chapter, and the exact skip is not yet reproduced on physical hardware.
