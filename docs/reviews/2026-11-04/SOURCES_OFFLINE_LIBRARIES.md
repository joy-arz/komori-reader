# Sources, offline titles and custom libraries

## Baseline and scope

Fetched `origin/main` before implementation: `050a734` (documentation/scan evidence after the 0.8.14 WeebCentral fix). Existing iOS work, `.gitignore` edits and two October 30 report edits are preserved outside this change. No package, signing configuration, signing secret or version-number changes.

## Checklist and findings

- Implemented: AsuraScans and HiveToons adapters, fresh-install enablement and default-leading order. Existing enabled-source/age-confirmation choices stay intact. Newly introduced sources precede older ordered entries; saving an explicit full order preserves subsequent user arrangements.
- Confirmed defect: `open(..., immediately = true)` waited for chapter network refresh when there was no saved feed/last-read combination, even when chapters were already downloaded. Cached rows were shown but `loading` remained active. Known-offline entry now uses complete downloaded rows and saved metadata without any source request. Available saved chapters start before an online refresh completes; errors retain saved details.
- Confirmed gap: covers depended on Coil's evictable cache. Download preparation now saves title details in Room and retains a validated download-owned cover in app files. Cover failure does not discard usable page downloads; online title refresh retries retention. Last-download deletion and delete-read cleanup remove the download-owned cover, retaining Library metadata and progress. Existing downloads gain covers when refreshed online; an already missing cover cannot be reconstructed offline.
- Improvement: existing categories had names/memberships but alphabetical order and no visible named Library tabs. Reuse these records for custom libraries, preserving membership and adding manual order. Schema 6 → 7 only adds `position`; migration retains old alphabetical order. Existing v2 metadata backups retain ordering through their category-array order, without a new backup format.
- Acceptance pending: physical Android 14/offline-switch behavior, OEM background restrictions, source verification/account/paid chapters, and future live markup changes.

## Source evidence

Public pages fetched without authentication, TLS bypass or challenge bypass:

- Asura `/browse/comics`, Overgeared details and chapter 1. Parsed 347 chapters, metadata, genres and reader images. Comic path restrictions exclude `/novels` and chapter links from catalogs. Astro props provide the full chapter list and locked-state data.
- Hive `/series`, Crocodile details/chapter 1 and Lookism details. Crocodile renders 25 recent chapter rows but its full server-rendered data includes all 28. Lookism server-rendered data contains all 640 chapters. Parsing reads literal chapter fields without executing website scripts or guessing missing chapter routes.
- Hive popularity returns a distinct popularity catalog. `sortBy=title` was ignored by the service, so that option is not offered. Asura's public browse code uses `sort=name&order=asc` for alphabetical ordering.
- Dedicated reader image attributes exclude unrelated images. Delivery URLs retain query parameters. Locked releases remain unavailable and link to the source page. No paywall or site verification bypass was added.

The optional `KOMORI_SOURCE_CAPTURE=/tmp` test validates captured public HTML locally; normal CI uses deterministic fixtures. No copyrighted chapter image collection is committed to the repository. This is public-page/parser validation, not a physical-phone end-to-end session or account-access claim.

## Regression coverage

- Catalog namespaces, origin/referer isolation, comic-only routing, deduplication, query escaping, paging and sort request semantics.
- Full encoded Asura chapter records, reader locks, full Hive server-rendered chapters and literal escaping.
- Reader selectors preserve image order/query strings and reject unrelated images.
- Fresh source defaults, unchanged legacy enablement and one-time adult consent.
- Saved title opening with no source calls; persistent cover selection after Coil cache eviction; delayed catalog failure does not block saved reading.
- Last-download cover ownership/cleanup retains title metadata and progress.
- Create/assign/reorder/delete libraries; named tabs filter their own titles; deletion keeps saved titles.
- Migration retains names, memberships and progress; metadata backup round trip retains explicit library order.

## Validation

Results and CI links are recorded below after execution. Real-phone and site-account acceptance remain separate.

- JVM: 214 cases, 208 passed, six existing optional live-network cases skipped. The optional captured-page parser test was enabled and passed, including all 640 Lookism chapter records, metadata, genres and reader-image selectors.
- API 30 ARM64 emulator: 23 affected cases passed in 126.442 seconds (OfflineLibraryTest, MigrationTest, SourceConsentTest, StreamingReaderPositionTest and DownloadRepairTest). This includes the delayed refresh and actual Library tab interaction. No physical phone was connected.
- Release/changelog Python checks: 10 passed.
- Android lint: zero errors, 50 warnings, matching the existing warning count. Debug app and instrumentation APKs compiled successfully. Release Kotlin compilation passed; final R8 packaging and cloud build/device results are tracked in the PR checks.
- Existing unrelated local files were not staged. CI/publication use the original signing setup; no new release/tag or package/version change is included in this PR.

## First cloud run and focused follow-up

PR #11, initial head `5d4ad0f`: Android build/release packaging passed (run 41843883607); repeated API 36 selection passed (41843883602). Full API 30 and 36 suites passed. API 26 and 34 failed in run 41843883938:

- API 26: 13 UI failures followed a boot-time `com.google.android.apps.messaging:rcs` crash (`NullPointerException` in RCS provisioning). The captured screenshot shows the **Messages has stopped** system modal covering the author filter. No Komori crash caused that overlay. The disposable-emulator script now force-stops/disables only the unrelated Messages package when present. This is gated by `KOMORI_DISPOSABLE_EMULATOR=1` and does not affect phones, app code, or crash reporting for Komori.
- API 34: 100/101 tests passed; the new offline-opening test accepted the previous session's `readerReady=true` while the new `reader` was still null. It now synchronizes exact chapter ID, readiness and a newer reader-session identity before asserting. The five-second deadline and original behavior assertions remain intact. Production behavior did not change for this follow-up.

Local release/R8 packaging completed successfully. Normal cloud JVM execution passed 207 tests with seven optional skips; the extra skip is the captured-public-HTML test that was enabled locally. Live public search also returned Overgeared on Asura and Lookism on Hive. Final follow-up checks are tracked on the PR.

The synchronized offline/library cases passed locally again: 2/2 in 3.310 seconds. The disposable-emulator script passed `bash -n`; no production application code changed in this CI follow-up.

## Consent UI synchronization follow-up

At `c86371b`, build/release packaging and repeated API 36 selection passed again. Full API 30 and 34 suites passed. API 26 and 36 each passed 100/101 cases; their only failure was the existing consent UI test's five-second coroutine wait after touch injection. The Messages overlay no longer caused failures on API 26.

The consent UI test now pumps Compose frames while awaiting the view model's committed setting, then separately checks the DataStore value and actual switch semantics. It retains decline/confirm/one-time confirmation/model-recreation assertions and the original five-second limit. Five consecutive local executions of that consent flow passed. This follow-up changes only instrumentation synchronization, with no production application change.
