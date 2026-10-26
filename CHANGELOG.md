# Komori changelog

## 0.8.5

### Reader reliability and performance

- Recover abandoned reader transfer files after process death, and include reclaimable partial files in Clear cached pages. Active transfers and downloaded chapters are preserved.
- Protect every composed/visible continuous-reader page and active strip decoder from cache eviction. Under cache pressure, discard unpinned lookahead before displayed images.
- Reconcile abandoned local import staging and unpublished local chapter folders after restart. Skip chapter files owned by active download or repair work.
- Keep speculative preload failures out of the visible-page retry budget. Retain source cooldowns, verification requirements and the ten-image lookahead limit.
- Share concurrent requests for the same chapter manifest and image quality. Canceling one reader subscriber does not cancel the request needed by another subscriber.
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

### Maintenance, accessibility and release quality

- Split Library, Authors, Downloads, title Details and Settings screens into separate files. Separate download coordination and reader progress persistence from the main reader ViewModel.
- Give continuous-reader image strips explicit page/part descriptions for accessibility services.
- Add regression coverage for cache cleanup and leases, eviction pressure, effective language, author-check fairness, speculative retries, planned continuations, archive bounds, manifest sharing, progress-write reduction, updater cancellation, and Authors filters with large text and RTL layout.
- Extend device CI to Android 8, Android 11 and Android 16. Add an emulator-only process-death/cache-recovery probe after instrumentation.
- Publish complete written changelogs from this file. Missing, link-only or unverified current entries block release-note generation.
- Backfill the complete 0.8.3 and 0.8.4 release notes without changing their APKs or immutable tags.

Validation: 168 JVM tests passed, with six optional live-network skips. All 15 new Android 11 device regressions and the emulator SIGKILL/restart cleanup probe passed. Publication requires successful build/lint and the complete device suite on the exact release commit for Android 8, 11 and 16. Physical-phone screen-off, manufacturer restrictions, manual TalkBack and low-memory hardware acceptance remain deferred; no tracker integration is enabled.

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
