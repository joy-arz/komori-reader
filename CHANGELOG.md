# Komori changelog

## Unreleased

- Keep the current continuous-reader page visible when a streamed image replaces a taller loading placeholder. An obsolete placeholder offset can no longer skip unread pages or spill into the next chapter.
- Wait for the corrected viewport before updating reading progress, chapter-window paging or completion. Preserve offsets that still fit tall images, lazy strip decoding, zoom, retries and the ten-image preload limit.

## 0.8.8

- Add Background downloads controls in Settings and the Downloads menu. Show Android's battery restriction status and let users explicitly allow background transfers through system settings.
- Use regular foreground WorkManager transfers for long chapter downloads instead of the expedited-work quota. Preserve persisted queue order, pause intent, retries, integrity checks and foreground notifications.
- Expand download acceptance to Android 14, switching applications, and screen-off Doze with battery exemption, including the next queued chapter.

- Return directly to the title with one system Back press from the reader, whether controls are hidden or visible. Keep center-tap and accessibility actions for toggling controls.

- Keep the original signing identity and package so the APK updates the installed app in place. Existing Library entries, reading progress, downloads and paused queue items remain preserved.
- Harden Authors selection acceptance by checking Compose toggle state and refreshing native accessibility queries after selection/search transitions. Keep all selection, ordinary navigation and unfollow assertions.

## 0.8.7

- Start the reader with controls hidden. A deliberate tap in the middle third of both screen axes shows or hides them in paged and webtoon modes.
- Prevent scrolling, pinching, double taps and taps outside that center region from opening the reader controls. Preserve directional page turns, retry buttons and accessible control actions; keep the corner page count passive.
- Keep Android 11 fullscreen scrolling responsive after brightness/window updates by retaining transient system-bar behavior.
- Use the latest typed page number when Go is pressed immediately after editing the reader jump field.

### Validation and upgrade notes

- Preserve the existing package and signing identity for in-place updates. Library entries, reading progress and downloads remain intact.
- Reader changes passed 196 JVM tests (six optional live-source skips) and all 84 instrumented tests on each API 26, 30 and 36 before merge.
- Post-merge API 36 testing encountered one Authors catalog assertion: the expected final title was not found after scrolling. All reader tests passed. This remains under investigation; it is not established as a reader regression.
- Physical-phone and manual TalkBack acceptance remain pending.

## 0.8.6

This update focuses on clearer Authors browsing, more reliable downloads, and safer update and source-migration flows.

### Authors and filtering

- Make filtering and sorting easier to distinguish: keep one primary filter button and give sorting its own icon and label. Preserve Latest, Popularity/views, Alphabetical and Author ordering, along with all existing filter options.
- Show when author filters are active and provide a clear Reset filters action. Preserve Authors filter state when switching tabs without applying it to unrelated searches.
- Fix stale accessibility roles and labels when entering selection mode or switching between Manage and Done. Keep author selection, unfollowing, ordinary navigation and search changes consistent.
- Improve filter-dialog usability on narrow screens, in landscape, with larger text and with right-to-left layouts. Resize around the on-screen keyboard so filter actions stay reachable while editing.
- Keep successful author results visible when another title's metadata fails to load. A failed title no longer prevents later catalog pages or other followed creators from loading.
- Show incomplete metadata with a targeted retry action. Unknown metadata is not treated as a language, category or tag match, and persistent failures do not trigger an automatic retry loop.
- Improve HentaiZap supplemental author lookups: retain pending candidates until their creator metadata can be verified, rather than accepting unrelated search matches or failing the entire page.

### Downloads and recovery

- Fix manual retries that could compete with a failed worker still finishing. Preserve queue order, intentional pauses, completed pages and retry delays.
- Protect downloaded chapter files while they are being used by the reader. Deleting or replacing those files requires closing the affected reader first.
- Check saved page integrity before reusing it during a resumed download or repair. Refetch damaged pages while retaining valid pages whose content identity is unchanged.
- Avoid unnecessary redownloads when recognized Nhentai image-delivery hosts change but the gallery and page identity stay the same. Continue distinguishing MangaDex content hashes and original/data-saver quality; preserve signed query parameters and unrecognized URL formats.
- Reclaim abandoned per-page transfer files during download recovery.
- Show Waiting for Android when a download is queued for system scheduling, while keeping specific network, Wi-Fi and storage waits visible.
- Update WorkManager to 2.10.5 to address a reproduced foreground-service shutdown race during worker handoff, alongside upstream network-constraint fixes.

### App updates and source migration

- Prevent update checks and APK downloads from running against conflicting release information. Keep progress, errors and verified APK results associated with the release that created them, and ignore stale callbacks.
- Add a cancel action for update checks and keep Stable/Preview channel changes safe. Preserve automatic unmetered downloads, manual downloads, snoozing, cooldowns and Android installation confirmation.
- Preserve APK size, SHA-256, package, version and signing-certificate verification.
- Cancel obsolete migration searches and destination-detail requests when the query, source, selected destination or migration screen changes.
- Prevent outdated destination chapters or mappings from being applied. Preserve reviewed transactional transfers, chapter ownership, language checks, completed reading progress and optional page copying.
- Clean up abandoned updater and temporary CBZ-export files after process death. Protect active transfers and preserve ready APKs and unrelated files.

### Source messages and release quality

- Add source and request-stage context to errors, including catalog/search, title details, chapter lists, page manifests and image transfers.
- Distinguish verification requirements, rate limits, unavailable content, connection failures and unsupported source content, with clearer next actions. Keep diagnostic summaries free of cookies, credentials, tokens and signed delivery URLs.
- Expand automated coverage for update and migration races, partial author catalogs, accessibility, page integrity, interrupted transfers and real WorkManager lifecycle behavior.
- Add large-Library, progress-database, startup and webtoon-cache measurements. These are emulator measurements, not a claim of improved physical-phone performance.
- Add the Discord community link to the README and automate version-specific VirusTotal release evidence and README updates. Unavailable scan results remain explicitly pending; this does not guarantee Google Play Protect approval.

### Validation and upgrade notes

- Automated code validation passed: 194 JVM tests, with six optional live-network tests skipped; 80 instrumented tests on each of Android 8, Android 11 and Android 16; and five complete repeated Android 16 author-selection checks during the original audit.
- Additional release-tag stress testing passed four of five repeated Android 16 selection checks. One native checked-state assertion timed out; its failure screenshot and native hierarchy show both authors selected and checked. This remains an accessibility/test synchronization investigation.
- Startup cleanup and real WorkManager network-loss, screen-off, process-restart, queue-continuation, page-reuse and pause-preservation probes passed on all three Android API levels.
- Android lint reported zero errors and 46 existing warnings. Debug and release compilation with shrinking passed. Signed APK packaging and signature verification run in the release workflow before publication.
- Keep the existing application identity and signing configuration so the signed release can update the installed app in place. Library entries, reading progress and completed downloads remain preserved.
- Older partial MangaDex downloads may refresh their pages once on resume or repair when their legacy manifests cannot prove image-quality compatibility. Completed downloads are not erased at startup.
- Android scheduling, Doze and manufacturer battery restrictions can still defer background work. Physical-phone battery behavior, manual TalkBack, low-memory hardware benchmarks and new live-source acceptance remain unverified. AniList/MAL tracking remains deferred.

## 0.8.5

### Reader reliability and performance

- Recover abandoned reader transfer files after process death, and include reclaimable partial files in Clear cached pages. Active transfers and downloaded chapters are preserved.
- Protect every composed/visible continuous-reader page and active strip decoder from cache eviction; release the session pin when its ViewModel is cleared. Under cache pressure, discard unpinned lookahead before displayed images.
- Reconcile abandoned local import staging and unpublished local chapter folders after restart. Skip chapter files owned by active download or repair work.
- Keep speculative preload failures out of the visible-page retry budget. Retain source cooldowns, verification requirements and the ten-image lookahead limit.
- Share concurrent requests for the same chapter manifest and image quality. Canceling one reader subscriber does not cancel the request needed by another subscriber.
- Fix continuous-reader append/trim oscillation at short chapter boundaries by loading beyond the tail only when that tail is the current visible chapter. Keep one chapter ahead without repeatedly removing and reloading it.
- Accept the final exit scroll checkpoint without reopening a closed reader or accepting callbacks from an older reader session.
- Reduce repeated scroll-position writes, keep final scroll checkpoints exact, and avoid rewriting the Library shelf on every small scroll movement.
- Limit Library title observation to titles referenced by Library, reading progress or downloads instead of retaining every cached Discover result in the active Library list.

### Download recovery

- Continue long chapter downloads with a successful continuation at the execution checkpoint. Planned resumptions no longer consume network-failure retries.
- Use elapsed time for execution windows so changing the wall clock does not change the checkpoint interval.
- Serialize pause/cancel/delete operations with foreground queue recovery. A returning activity or network callback cannot resurrect a download during those mutations.
- Retain completed page files, queue order, verification states and Retry-After delays.

### Library and author improvements

- Use the shared source/title language policy for unread counts and migration. Korean raw editions now have the expected unread badges and Unread-filter visibility without requiring a manual language override.
- Rotate author checks by attempted time and back off repeated failures independently. Three unavailable creators no longer block all other followed creators.
- Add an Authors filter dialog with source selection, title/creator text, language, category, required tags or parodies, saved/not-saved/liked works, and Library status.
- Retrieve additional metadata only when language/category/tag filters need it, with bounded detail-request concurrency.
- Preserve creator pagination through empty filtered result pages. Load more remains available; filters do not imply that the first page is the complete catalog.
- Keep saved works visually neutral and start filtered catalogs at the top. Keep Latest, Popularity/views, Alphabetical and Author ordering; explain that cross-author Latest/Popularity blend source rankings.
- Keep the Authors header compact. The full filter controls are in a scrolling dialog with Apply, Reset and Cancel.

### Local files and updates

- Align CBZ import and export format support for JPEG, PNG, WebP, GIF and AVIF when the device can decode them.
- Align the compressed archive allowance with app-produced exports while retaining the 512 MiB page-data, per-image, page-count, path and entry limits.
- Cancel obsolete update checks when switching Stable/Preview channels and reject stale responses.
- Bind updater network operations to coroutine cancellation and give each transfer its own temporary file.
- Increase the bounded in-app release-note allowance so the full current release notes and the requested 0.8.3/0.8.4 history can be displayed.

- Clear search focus when closing Library or Discover search so a late keyboard opening cannot cover navigation.

### Maintenance, accessibility and release quality

- Split Library, Authors, Downloads, title Details and Settings screens into separate files. Separate download coordination and reader progress persistence from the main reader ViewModel.
- Give continuous-reader image strips explicit page/part descriptions for accessibility services.
- Add regression coverage for cache cleanup and leases, eviction pressure, effective language, author-check fairness, speculative retries, planned continuations, archive bounds, manifest sharing, progress-write reduction, updater cancellation, and Authors filters with large text and RTL layout.
- Extend device CI to Android 8, Android 11 and Android 16. Add an emulator-only process-death/cache-recovery probe after instrumentation.
- Publish complete written changelogs from this file. Missing, link-only or unverified current entries block release-note generation.
- Backfill the complete 0.8.3 and 0.8.4 release notes without changing their APKs or immutable tags.

Validation: 168 JVM tests passed, with six optional live-network skips. All 66 device checks passed on each of Android 8, 11 and 16 (198 total, zero skips), plus three emulator SIGKILL/restart recovery probes. Exact-commit build, lint and signed-release gates passed. Published version code 128; APK size 2,567,819 bytes; SHA-256 4362f62a7d9ec6e62c750487e3a240817b427178f26134258ac46e0ba39fe780. Physical-phone screen-off/manufacturer restrictions, manual TalkBack and low-memory hardware acceptance remain open. Local ARM emulator scrolling remains timing-sensitive and is tracked separately; clean cloud emulator runs passed. Tracking remains deferred.

## 0.8.4

### Reader image blinking fix

- Fix cache-triggered image blinking while scrolling. Reading or preloading an unchanged cached page no longer changes the file modification timestamp used to identify its decoded image strips.
- Track cache access recency separately in a synchronized, bounded 512-entry index. Eviction can still prefer older cache entries without treating an ordinary read as replacement image content.
- Preserve genuine image replacement detection: a changed image file invalidates the previous decoded bitmap and displays the new pixels.
- Keep the ten-image lookahead, continuous chapter loading, source retry rules and background-download recovery introduced in 0.8.3.
- Preserve Library entries, saved/liked state, reading progress, downloaded files and existing preload/cache preferences. No database migration is required.

### Validation and release

- Add device regressions proving that ten repeated reader cache hits retain the same strip key, modification timestamp and decoded bitmap.
- Verify that replacing an image produces a new key, a different bitmap and the replacement pixels.
- Pass 164 JVM tests, with six optional live-network tests skipped.
- Pass 50 of 50 device checks on Android 8 and 50 of 50 on Android 16, including both cache-stability regressions, with zero device-test skips.
- Pass the build, lint and signed release gates at commit 18b194f. Publish version code 127 with the original release signing certificate.
- Verify the published APK package identity, updater metadata, file size and SHA-256 checksum. APK size: 2,551,435 bytes. SHA-256: f62b8f21255845d1af5cf46b06e6bd6c394deee5cfaf85f669fadb00b09fbc73.

## 0.8.3

### Download sleep and foreground recovery

- Reconcile interrupted Queued/Downloading requests when the activity returns to the foreground or connectivity becomes available. Reschedule an enqueued head request when no request for that chapter is running.
- Preserve healthy running requests, user-paused items, completed chapters and intentional Retrying/Retry-After delays.
- Serialize queue recovery scheduling so overlapping foreground and network callbacks do not enqueue duplicate head requests.
- Hold a partial CPU wake lock during foreground chapter transfers, with a nine-minute timeout, and release it after completion or cancellation.
- Keep per-page download checkpoints, persisted queue order, network/storage constraints and the foreground progress notification.
- Android force-stop, system quotas and manufacturer restrictions can still defer or stop work. Sustained screen-off behavior on physical phones was not certified by this release.

### Reader lookahead

- Replace the paged reader's one-to-two-page nested preload with an independent cancellable lookahead job.
- Start sequential image preloading after the current image is available, without waiting for a scroll to compose the next page.
- Enable the same lookahead behavior in continuous/webtoon reading and move the preload window forward with the current page.
- Allow zero to ten images ahead in Settings → Reading. The unset default becomes ten; explicitly saved previous values are preserved.
- Share one ten-image budget across continuous chapter boundaries, respect disabled continuity and chapter gap warnings, and stop at unavailable/empty boundaries.
- Cancel outdated lookahead when changing position, refreshing or leaving the reader. Speculative loading does not move the reading position or mark chapters complete.
- Prefetch image files into the configured disk cache rather than decoding ten full images into memory. Protect the current displayed file from eviction during the preload pass.

### Validation and release

- Add five JVM regressions covering the moving ten-image limit, shared continuous-chapter budget, boundary stopping, cancellation and failed speculation.
- Pass 164 JVM tests, with six optional live-network tests skipped.
- Pass three focused Android 11 reader/background checks, including actual WorkManager interrupted-request replacement and preservation of a delayed retry.
- Pass 48 of 48 device checks on Android 8 and 48 of 48 on Android 16, with zero device-test skips.
- Pass build/lint and signed release gates at commit d9c0b65. Publish version code 126 with the original release signing certificate.
- Verify the published APK package identity, foreground/wake-lock manifest, updater metadata, file size and SHA-256 checksum. APK size: 2,551,435 bytes. SHA-256: 0677fe765d382ae94d3eb66ac21dfdcaa2da82b14544c539f69c45eff66f9a22.
