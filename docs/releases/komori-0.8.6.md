## Install on Android

Download **komori-0.8.6.apk** from Assets below. Android 8.0 or newer. Install over your existing app to preserve your Library, progress and downloads.

## Komori 0.8.6

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

- Automated code validation passed: 194 JVM tests, with six optional live-network tests skipped; 80 instrumented tests on each of Android 8, Android 11 and Android 16; and five complete repeated Android 16 author-selection checks.
- Startup cleanup and real WorkManager network-loss, screen-off, process-restart, queue-continuation, page-reuse and pause-preservation probes passed on all three Android API levels.
- Android lint reported zero errors and 46 existing warnings. Debug and release compilation with shrinking passed. Signed APK packaging and signature verification run in the release workflow before publication.
- Keep the existing application identity and signing configuration so the signed release can update the installed app in place. Library entries, reading progress and completed downloads remain preserved.
- Older partial MangaDex downloads may refresh their pages once on resume or repair when their legacy manifests cannot prove image-quality compatibility. Completed downloads are not erased at startup.
- Android scheduling, Doze and manufacturer battery restrictions can still defer background work. Physical-phone battery behavior, manual TalkBack, low-memory hardware benchmarks and new live-source acceptance remain unverified. AniList/MAL tracking remains deferred.
