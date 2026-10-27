# Reliability and UI follow-up

Base/current audited main: `501ea97f3cd060395cf4ab8a495adfc7d5d3774a`. Fetched origin on October 27, 2026; origin/main and the clean working tree matched. No repository or ancestor AGENTS.md was found. No intervening commits exist since the supplied audit. Work branch: `codex/reliability-ui-followup`. No version, package, signing-secret or release/tag changes are authorized for this pass.

The supplied 168 JVM passes / six optional live skips, zero lint errors / 46 warnings, API 26/30 passes and API 36 BatchSelection failure are starting evidence, not rerun results.

| Priority | Item | Initial classification | Evidence / next acceptance | Status |
|---|---|---|---|---|
| 1 | Authors duplicate-looking controls | Confirmed UX defect | Filtering and sorting are separate actions using the same FilterList glyph. Inspect shared toolbar, management, selection and saved state; retain both actions with distinct presentation. | Implemented; semantic UI coverage and screenshots captured; final repeats pending |
| 2 | Update operation coordination | Confirmed defect by control-flow demonstration | Cached update remains exposed while checking; download lacks the reverse busy guard and check replaces the entire state. Add operation ownership and delayed/out-of-order regressions. | Implemented; eight owner regressions and delayed-dialog coverage |
| 3 | Migration stale requests | Confirmed defect by control-flow demonstration | edit() does not cancel/invalidate; captured old state can publish after edits; selection rows are shared mutable state. Cover search, selection, close and Apply. | Implemented; two delayed-request integration tests pass; apply/detail expansion pending |
| 4 | Filtered-author enrichment isolation | Confirmed defect by code-path demonstration | awaitAll propagates one detail failure to AuthorCatalog's page catch, so its cursor never advances. Preserve failures for targeted retry while advancing successful pages. | Implemented; four focused JVM regressions pass |
| 5 | Updater/export orphan files | Confirmed ownership gap | Process death skips finally; updater does not reclaim orphan .part files. CBZ export creates comic-*.cbz in cache with finally-only deletion. Add ownership and scoped cleanup tests. | Implemented; ownership tests pass; expanded process-recovery probe pending |
| 6 | API 36 Authors multi-select | Acceptance/investigation; not yet a production defect | Re-run exact BatchSelection sequence repeatedly with screenshot, hierarchy, window/state and log evidence. Do not relax assertions/timeouts. | Reproduced stale exported semantics; immutable state fix awaiting repeated verification |
| 7 | Real WorkManager lifecycle | Improvement requiring implementation/testing | Existing direct worker and queue tests do not prove real JobScheduler/process-death behavior. Add deterministic loopback HTTP fixtures and actual WorkManager work. | Five OS-backed tests passed locally; upstream foreground-service patch and extra retry test pending |
| 8 | Source-specific page identity | Risk requiring demonstration | Current normalization only understands MangaDex /data/ and /data-saver/. Inspect adapters and demonstrate a delivery-only rotation before changing other sources. | Demonstrated parsed Nhentai fixture rotation; four identity tests pass; live rotation unobserved |
| 9 | Source diagnostics | Improvement requiring implementation | Stage/source context is lost in generic Connection reset messages. Preserve typed retry/verification behavior and redact optional detail. | Implemented; three focused JVM tests pass; existing HTTP regression retained |
| 10a | Performance | Measurement/acceptance | Measure cold launch, session construction, 1,000-title / 50,000-progress fixture and bounded reader decode/cache behavior; distinguish emulator data. | Webtoon/cache/session samples captured; full Library/cold launch and physical acceptance remain |
| 10b | Accessibility | Improvement + acceptance | Automate touched-screen semantics, 150% fonts, narrow/landscape/RTL; full TalkBack/manual phone acceptance stays separate. | 320 dp/150%/RTL filter semantics pass; landscape/manual TalkBack pending |
| Prior repairs | Reader pins, import cleanup, planned continuation, speculative accounting, exact-commit gates | Already implemented; do not reopen without evidence | Retain existing regression coverage. | Preserve |
| External | Live sites, challenges, physical 2 GB/battery restrictions | Real-device/external-service acceptance | No phone connected; only API 30 local system image currently installed. Cloud API 26/30/36 available. | Pending external acceptance |

## Per-defect record

Implementation entries will record trigger, root cause, user impact, intended behavior, focused commit, regression coverage and exact verification results here. Screenshots live alongside this report or in linked CI artifacts. Fixture results do not establish live-site or manufacturer behavior.

## Delivery checklist

- [ ] Reproduce/demonstrate current defects and record API 36 baseline diagnostics.
- [ ] Implement priorities 1–10 with focused commits and preserve existing features/security.
- [ ] JVM, lint, debug/test APK and release compilation; compare warnings with baseline.
- [ ] Targeted API 36 repeats, then relevant API 26/30/36 suite.
- [ ] Document before/after Authors screenshots, measurements, residual risks and external acceptance.
- [ ] Push focused branch, open/attach PR, inspect its build/device failures and fix introduced issues.
- [ ] Leave PR unmerged; do not publish or tag a release.

## Current implementation findings

- **P1 confirmed UX defect:** the shared Authors toolbar adds no filter. Authors' metadata/Library filter and its separate sorting menu both used `FilterList`. Keep sorting as a separate named action with an up/down sort icon; use the existing Tune filter icon, selected/state-description semantics, and a visible reset action. Management retains search, unfollow and batch actions. No Discover filter was removed. Semantic regression coverage is being added for one clickable primary filter, sorting, management, navigation persistence, 150% text and RTL at 320 dp.
- **P2 confirmed race:** a cached update could download while a new check ran. A whole-state check assignment then erased transfer state. `UpdateOperations` now owns phase, check/download jobs and generation; both operations exclude the other. Callbacks require both generation and exact artifact metadata. Channel changes invalidate checks; downloads keep their channel until completion. Visibility/snoozing is independent of transfer. `UpdateBackend` permits delayed fake transport tests; real AppUpdates still verifies size, digest, package/version and certificate, and debug UI still cannot install release APKs.
- **P3 confirmed race:** editable migration context left search and detail requests alive, and Apply read shared destination rows. Every edit/search/selection/close invalidates and cancels the old generation. A reviewed destination owns immutable chapter rows, language and generation; Apply checks that context before and inside the existing Room transaction. Closing rolls back canceled transactions and late callbacks cannot reopen state.
- **P4 confirmed poisoning:** one detail exception escaped `awaitAll`, so a successful catalog page never advanced. Catalog enrichment now isolates each title's failure, with concurrency two. Unknown metadata is explicitly incomplete and excluded from metadata matches. Page cursor/ownership/ranks advance independently, successful results remain, failures persist for user-triggered targeted retry, and duplicates do not trigger an automatic retry loop.
- **P5 confirmed process-death gap:** updater `.part` and exported CBZ cleanup depended on `finally`. Shared temporary-file ownership now protects active callbacks, rejects callbacks that start after cancellation, and reclaims only updater/export naming patterns at startup. Ready APKs and unrelated files are preserved. New updater instances serialize transfers, and export files use a dedicated cache directory (legacy owned cache exports are also reclaimed).
- **P6 baseline evidence:** targeted API 36 test passed **5/5** on unchanged app code in [run 40489579309](https://github.com/joy-arz/komori-reader/actions/runs/40489579309), with after-long-press hierarchies and state logs for every run. Baseline full suite [40489579232](https://github.com/joy-arz/komori-reader/actions/runs/40489579232) passed API 26 and API 36; API 30 failed an unrelated NavigationHistory stale-node assertion. Original API 36 log shows the gesture targeted a non-long-clickable text child, with accessibility tree changes during navigation. This is evidence of an unstable test target, not proof of a production selection-state defect. Management rows now expose their actual long-click and selection semantics; the test targets the interactive row and asserts selection activation explicitly, retaining original timeouts and all batch assertions. Exact original failure has not reproduced yet.
- **P7 demonstrated reader ownership gap:** DownloadCoordinator.delete removed downloaded pages while the reader could still hold those paths. Reader viewport ownership now prevents deletion or manifest replacement while a chapter is displayed; users receive a close-reader action message. Actual WorkManager fixture coverage is in progress. Existing chapter/queue locks, planned continuation, retry budgets and repair safeguards are retained.
- **P8 bounded fixture investigation:** Nhentai's parsed manifest supplies stable media ID/page paths while its reader image selects the delivery host for all pages. A delivery-host rotation changes the old full-URL identity although media/page content identity remains identical. Source-specific matching is restricted to recognized Nhentai HTTPS image hosts and verified numeric gallery/page paths; unknown hosts, queries and all other sources retain exact URLs. MangaDex identities now retain data/data-saver quality; compatible old manifests upgrade without deleting reusable pages. No global query stripping is introduced. Live CDN rotation has not been observed in this pass.
- **P9 staged diagnostics:** catalog/search, details, chapter list, page list, image, filter and language boundaries now preserve source/stage and classified actions. Cancellation propagates; wrapped HTTP cooldown/deferred/verification/TLS policies remain typed through unwrapping. Presentation and diagnostic summaries do not include URLs, query text, response bodies, cookies or tokens. No diagnostic export or browsing-history collection is added.

## Validation ledger (in progress)

- Baseline PR app code: Android JVM/lint/debug checks succeeded in [40489579268](https://github.com/joy-arz/komori-reader/actions/runs/40489579268).
- Baseline targeted API 36: 5/5 exact BatchSelection runs passed. Captured state: selected label true, first and second author visible, on every run. XML/log artifacts retained by the run.
- Baseline full device suite: API 26 and API 36 each passed all 66 instrumented tests plus interrupted-cache process recovery; API 30 had one NavigationHistoryTest StaleObjectException. This failure predates functional changes on this branch.
- First local implementation compile caught a shadowed `selected` semantics assignment; corrected to `this.selected`. A subsequent test compile caught two invalid fixture field names; corrected to tagsJson. Two author fixture assertions then exposed missing metadata IDs; corrected the fixtures. These are implementation-development failures, not passing validation.
- Local API 30 emulator is available. No physical phone, manufacturer power behavior, TalkBack interaction or live source acceptance has been exercised by this pass.

### Update transitions

| Current operation | Allowed next operation / result |
|---|---|
| Idle / available / failed | Check, or download the current artifact if available and supported by the build |
| Checking | Success → available/idle, or ready only for the same already verified artifact; error retains cached artifact; cancel restores available/ready/idle; channel change cancels and starts a new generation |
| Downloading | Progress/completion/failure only for the owning generation and artifact; another check/download/channel change is rejected |
| Ready | Installation still uses Android confirmation; a manual check retains the file only if the exact artifact is unchanged |
| Dismissed / snoozed presentation | Orthogonal to operation: hides the dialog/banner without canceling a transfer or letting old callbacks change metadata |
| Cleared owner | Cancels jobs and invalidates all callbacks |

### Authors visual comparison

Captured using the same AuthorsInteraction fixture on the local API 30 emulator. Before uses the baseline GitHub debug APK (its **debug-only** certificate was adapted to the local debug keystore so it could update the disposable test package). After uses this branch's debug APK. This did not touch the production package, release signing identity, signing configuration or secrets. Covers intentionally have no network images in this synthetic fixture.

| Before: identical filter-looking glyphs | After: filter and sort are distinct |
|---|---|
| ![Authors before](screenshots/authors-before.png) | ![Authors after](screenshots/authors-after.png) |

### Early emulator measurement (not phone acceptance)

API 30 local emulator, debug build, synthetic 256×4096 webtoon pages, 3,685,498 encoded bytes per page, 80 publish/decode iterations: median 39.89 ms, p95 82.44 ms, retained cache 66,338,964 bytes under a 67,108,864-byte budget. Process PSS before 161,589 KB; sampled peak 164,920 KB; after 165,804 KB. The decode check samples to a maximum 1024-pixel dimension. This exercises real image decode, cache publication, eviction and visible-file pinning; it does **not** establish full continuous-reader UI recomposition/frame performance or physical 2 GB behavior.

Source-session construction in that warm process measured 417.92 / 0.206 / 0.105 ms. The first sample can include WebView initialization and is not a fresh-phone cold-start benchmark. No production optimization is inferred from these emulator numbers alone.

### Development failures retained for traceability

- Local emulator restored an incompatible graphics snapshot, producing a System UI ANR that obscured the batch test's initial Library control. Screenshot captured; cold boot without snapshots removed that environment issue. Do not count the blocked run as a passing batch test.
- First OS-backed HTTP fixture attempt rejected MockWebServer's `localhost` URL because the debug provider explicitly permits **127.0.0.1 only**. Corrected the fixture URL rather than broadening network policy.
- The Authors row accessibility description appears on a descendant of its actual clickable/long-clickable action in Android's exported hierarchy. An initial overly strict test selector required both on one node and failed. Hierarchy evidence now guides a selector for the long-clickable row containing the description. Selection activation remains a separate required assertion.
- Android's `content call --extra` syntax splits colon-delimited fields, rejecting literal URLs. The process-death fixture needs a port-number configuration argument; this is a harness issue rather than a transfer result.

## October 28 continuation: reproduced causes and follow-up

- **API 36 reproduced:** targeted run [40494412608](https://github.com/joy-arz/komori-reader/actions/runs/40494412608), iteration 4, failed at the second author selection control. Its screenshot shows both checkboxes correctly rendered; its exported XML retains `Manage author Batch creator 2` for the second row, with stale bounds, while the first already says `Select …`. This isolates a stale accessibility semantics update rather than missing data, off-screen rows or failure to activate long-press. Row descriptions and selected flags now capture immutable values read in composition instead of reading the mutable selection holder inside a remembered semantics receiver. No timeout increase or sleeps were added. The test also covers dismissing selection, an ordinary row tap/navigation, and query changes clearing selection. Fixtures have unique visible names and Activity-launch failures now still execute cleanup.
- **Real foreground service race reproduced:** API 30 full-suite run [40494412418](https://github.com/joy-arz/komori-reader/actions/runs/40494412418) logged foreground service shutdown followed by a new foreground worker command, then `RemoteServiceException` during queue handoff. WorkManager 2.10.1 predates the upstream fix for stopping a foreground service with pending commands. Upgrade only to the compatible 2.10.5 patch series, including its testing artifact. [AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/work#2.10.5) identify the pending-command fix; 2.10.2/3 also fix network-constraint scheduling. This is a targeted dependency correction, not a general dependency refresh.
- The five actual WorkManager lifecycle tests all passed locally once the fixture host/path issues were corrected. An additional deterministic fixture holds a failed worker in its final completion window to verify that explicit retry supersedes the old work. No TestWorkerBuilder or test scheduler replaces the OS-backed path in these new tests.
- CI [40494412732](https://github.com/joy-arz/komori-reader/actions/runs/40494412732) passed JVM, lint/debug and **release compilation with shrinking** on ac858ea. No signing secrets were used and no release was published. Device CI exposed an introduced test initialization failure: changing println to Log.i changed an inferred JUnit method return type from Unit to Int. Explicit `runBlocking<Unit>` corrects it. Missing-page diagnostics now retain the safe numeric HTTP status required by the existing worker regression.
- A local instrumented updater-dialog test passed, including a cached update disabled during a delayed check, exact new-release metadata on download completion, and snoozing. Its injected backend never reaches Android installation and production APK verification is unchanged.
