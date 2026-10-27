# Reliability and UI follow-up

Updated October 28, 2026. PR: [#3](https://github.com/joy-arz/komori-reader/pull/3), branch `codex/reliability-ui-followup`.

## Audit boundary and delivery

Fetched main again on October 28: `501ea97f3cd060395cf4ab8a495adfc7d5d3774a`, exactly the supplied audit revision. There were no intervening merged app changes. Initial working tree was clean; no applicable repository/ancestor AGENTS.md was found. The supplied 168 JVM passes, six live skips, lint 0/46 and prior device results were starting evidence only. Implementation through `bfbf34e69f36ec787af709acba3ca2cc4b5fe075` has been checked; final synchronization/fixture verification is in progress.

Application ID, version, production signing configuration and secrets are unchanged. Commits use the repository owner's existing identity without coauthor attribution. This task does not authorize merging, tagging or publishing a release. Release compilation in PR CI is unsigned and includes shrinking.

| Priority | Classification from current implementation | Implementation status | Acceptance boundary |
|---|---|---|---|
| 1 Authors controls | Confirmed UX ambiguity: distinct filter and sort actions used identical glyphs | One primary Tune filter, distinct sorting, active/reset semantics; normal/search/manage/selection coverage | Emulator semantics and before/after screenshots; manual TalkBack pending |
| 2 Updates | Confirmed check/download ownership race | Explicit operation owner, phases, jobs and artifact/generation guards; injectable backend | Delayed/out-of-order owner tests and real dialog test; production installer/verification retained |
| 3 Migration | Confirmed obsolete search/detail publication and stale Apply context | Cancel/invalidate every context change; immutable reviewed destination; transactional Apply guards | Delayed requests and real Room Apply coverage |
| 4 Author catalogs | Confirmed individual details failure could poison successful catalog page, including source-level supplemental lookup | Isolated enrichment, deferred creator ownership, incomplete metadata, explicit targeted retry, cursor advancement | JVM fixtures exercise actual HentaiZap adapter/catalog path; live-site acceptance pending |
| 5 Temporary files | Confirmed updater/export orphans survived process death | Scoped ownership-aware startup recovery and late-callback rejection | Ownership tests and emulator SIGKILL probe; verified-ready APK production flow unchanged |
| 6 API 36 selection | Confirmed stale exported author action semantics; later query-change test synchronization also exposed | Single explicit accessible row action with checked state; Compose synchronization under verification | Baseline 5/5 and one fixed 5/5 run; latest extended repeat has one failure and is being corrected |
| 7 Downloads | Confirmed reader-file ownership and finishing-worker retry gaps; demonstrated upstream foreground shutdown race | Reader leases, serialized explicit retry, checksum reuse/repair, WorkManager 2.10.5 patch, real scheduler fixtures, Android-wait status | Six OS-backed cases and local network/screen-off/SIGKILL probes; latest full matrix failures under investigation |
| 8 Page identity | Risk demonstrated with parsed Nhentai delivery-host rotation fixture | Strict source-specific identity; unknown/signed URLs remain exact; quality/content changes invalidate | Fixture evidence only; no claim of observed live CDN rotation |
| 9 Diagnostics | Improvement required: generic errors lost request stage | Safe stage/source/kind/action messages; typed retry/verification preserved | JVM classification/redaction and existing HTTP regression coverage |
| 10 Performance/accessibility | Measurement and acceptance work | 1,000-title/50,000-progress fixture, decode/cache/startup/session measurements; narrow/large-font/RTL/landscape UI checks | Debug emulator measurements only; physical 2 GB, release frames and manual TalkBack pending |
| Prior fixes | Already present: reader cache pinning, interrupted import recovery, planned continuation, speculative retry accounting, exact-revision release gates | Preserved; no broad rewrite | Existing regressions retained |
| External | Real-device or external-service acceptance | Explicitly pending | Manufacturer battery restrictions, live verification/source behavior and installation on a phone |

## Defects, intended behavior and regression evidence

### 1. Authors filter entry point

**Trigger/root cause:** Normal Authors browsing exposed metadata/Library filtering and sorting with the same FilterList glyph. The shared screen toolbar did not add another Authors filter; these were different controls, not duplicate filter implementations. **Impact:** users could not distinguish filtering from sorting.

**Change:** one primary action is labeled `Filter author works`, uses Tune, has a selected/state-description indicator, a minimum 48 dp target, and visible Reset filters. Sorting remains separately labeled `Sort author works` with an up/down icon and all four orders. Manage retains followed-author search, unfollow and batch actions. No Discover filter was removed. Filter state remains Authors-specific and survives expected tab navigation; management/search changes clear selection. Dialog actions wrap at large font sizes.

**Coverage:** AuthorsFilterActionTest checks exactly one clickable semantic primary filter in normal and searched/filter-applied states, zero in management, preserved sorting/navigation/reset, 320 dp width, 150% font and RTL. AuthorFilterAccessibilityTest covers landscape/150%/RTL action visibility and field usability. AuthorsInteractionTest exercises saved/followed works. Manual TalkBack focus/announcement/contrast acceptance remains pending.

| Before: ambiguous identical glyphs | After: distinct filter and sort |
|---|---|
| ![Authors before](screenshots/authors-before.png) | ![Authors after](screenshots/authors-after.png) |

Both screenshots use the same synthetic AuthorsInteraction fixture. The baseline APK was re-signed only with the local disposable **debug** keystore to compare debug installs; production signing was untouched.

### 2. Update operation ownership

**Trigger/root cause:** With cached release A exposed, check B could start, download A could start concurrently, then check B replaced the whole state. **Impact:** lost download progress, duplicate transfers, or A's APK shown against B's metadata. **Intended behavior:** one compatible operation owns callbacks and results at a time.

**Change:** UpdateOperations owns jobs, generations, artifact identity and phases `IDLE`, `CHECKING`, `AVAILABLE`, `DOWNLOADING`, `READY`, `FAILED`. Dismissal/snoozing controls visibility independently of phase. Both check and download enforce mutual exclusion. Check cancellation restores cached available/ready state. Channel changes cancel/invalidate a check; a transfer retains its channel. Every progress/success/failure callback checks its generation and exact artifact. A newly discovered artifact clears an old ready file. Same-artifact release-note changes do not invalidate a ready APK. Controls mirror those transitions.

Automatic unmetered downloading, manual downloading, cooldowns, snoozing and Android confirmation remain. Production download still checks expected size, SHA-256, package, version and signing certificate. Debug production downloads remain disabled; only an injected fake backend enables the dialog test and it cannot reach installation.

**Coverage:** eight UpdateOperations tests, automatic download matrix, delayed checks/cached downloads/out-of-order completion/channel changes/cancellation/failures; UpdateUiCoordinationTest exercises the cached dialog disabled while checking and the exact new artifact on completion.

### 3. Migration request and Apply context

**Trigger/root cause:** Editing query/source reset visible state without invalidating active work; searches published captured state and selected destination details shared mutable rows. **Impact:** obsolete results/chapters could overwrite new context or be reviewed/applied under another destination.

**Change:** every edit, search, selection, close and teardown cancels old work and advances generation. Noncooperative late success/error is ignored. Destination details must match the selected title and source. The reviewed destination owns immutable chapter rows, language, original/target and generation. Apply checks that snapshot before and inside the existing Room transaction; cancellation rolls back. Obsolete mapping rows clear immediately. Existing chapter ownership, language, completed progress and optional page-copy safeguards remain.

**Coverage:** delayed query/source edits, out-of-order requests, obsolete errors, delayed selection details, close/reopen; MigrationApplyRaceTest uses the actual Room database to transfer completed progress only to the latest valid destination.

### 4. Author metadata and fair paging

**Trigger/root cause:** Catalog enrichment used awaitAll without isolating individual details errors. HentaiZap supplemental creator lookup and source-level language enrichment could fail even before AuthorCatalog received the page. **Impact:** one persistent 404 hid valid titles and prevented cursor advancement, repeatedly blocking later pages.

**Change:** title enrichment runs in bounded chunks of two, preserves coroutine cancellation, and records incomplete metadata per title. A successful catalog page advances independently of title details; catalog-page failures remain separate failures. Unknown language/category/tags never count as a filter match. Explicit retry requests only pending titles; successful items deduplicate and persistent failures do not automatically retry on each page.

Authors opts into deferred HentaiZap supplemental creator details. Native creator-page entries retain known ownership; supplemental candidates with failed/empty ownership remain pending until details prove the expected creator ID. Name-only search hits and known different creators are excluded. Deferred failures are bounded (256 entries, ten-minute window); explicit retry bypasses deferral and refresh clears it. Native source language filtering is used only where supported without hidden bulk enrichment (MangaDex/Nhentai); Authors' isolated metadata owner handles the other sources. Other Discover callers retain their behavior. Fair round-robin creator paging, rank/sort and duplicate ownership are retained.

**Coverage:** AuthorMetadataTest covers valid plus persistent 404, no matching results, catalog failures, later pages, targeted transient recovery, duplicates, other creators and cancellation. SourceAuthorIsolationTest uses real WebSource parsing plus AuthorCatalog against MockWebServer, including supplemental 404, later pages, retry, empty/name-only/unrelated ownership. No live HentaiZap result is claimed.

### 5. Updater and export file recovery

**Trigger/root cause:** finally cleanup cannot run after process death; updater parts and temporary comic exports had no startup owner recovery. **Impact:** repeated interruptions grew cache usage.

**Change:** OwnedTemporaryFiles reference counts active asynchronous writers; cleanup cannot delete their files and callbacks starting after ownership ends cannot recreate them. Updater downloads serialize across instances. Startup cleanup matches only updater-owned part patterns and exporter-owned comic CBZ patterns, including old cache-root exports; new exports use a dedicated directory. Ready APKs and unrelated files remain. Recovery failures cannot block startup.

**Coverage:** four ownership tests cover existing orphans, active protection, repeated cleanup, unrelated files, late callbacks and interrupted writing. Emulator SIGKILL startup probe removes reader/import/updater/export orphans and preserves cached images, persistent markers, ready-named APK and unrelated part. The retained APK is a filename/retention fixture, not a fake verifier pass.

### 6. API 36 author selection

**Reproduction:** unchanged main passed five targeted repeats in [40489579309](https://github.com/joy-arz/komori-reader/actions/runs/40489579309). Later [40494412608](https://github.com/joy-arz/komori-reader/actions/runs/40494412608), iteration 4, captured visually rendered checkboxes but stale native semantics on the second row (`Manage author` instead of `Select`). Both rows were visible; selection state and initial long press had activated. Immutable capture alone and an intermediate custom row did not eliminate the issue. [Failure screenshot](screenshots/authors-stale-semantics-api36.png), [native hierarchy excerpt](screenshots/authors-stale-semantics-api36.json).

**Change:** AuthorManagementRow explicitly owns name, click/long-click, role and checked/selected semantics together using clearAndSetSemantics; its checkbox is decorative and the normal Unfollow action remains a separate sibling. It retains typography, spacing and real pointer actions. The test targets the real long-clickable row, asserts selection activation and native checked controls, then dismisses, taps/navigates normally and verifies query changes clear selection. Unique visible fixture names avoid collisions.

**Evidence:** final explicit-action implementation passed 5/5 in [40555273372](https://github.com/joy-arz/komori-reader/actions/runs/40555273372); [native selected-state excerpt](evidence/authors-selection-api36.json). The next run passed four repeats then failed at the added query-change re-selection assertion; its failure dump contained the expected correct native controls. Explicit Compose idle synchronization and clock-controlled touch long-presses now pass the complete extended BatchSelection sequence locally (22.318 seconds). Native click/long-click/checked-state assertions remain. The Downloads group uses exact semantic text plus its long-click action to distinguish it from the chapter row containing the same title. Assertions/timeouts are retained; no arbitrary sleep or disabled test was added.

### 7. Downloads and concurrent file use

**Confirmed causes:** delete/manifest replacement could remove files owned by an active reader; an explicit retry could race a failed worker still finishing while KEEP retained the old work. API 30 also reproduced a foreground-service shutdown/pending-command crash in WorkManager 2.10.1.

**Change:** chapter reader leases prevent deletion or incompatible manifest replacement while displayed. Explicit retry cancels/replaces old work under queue/chapter ownership. Committed pages are reused only with matching content identity and valid image/SHA-256; corrupted pages are refetched individually. Interrupted per-page parts are reclaimed under the chapter lock. Stopped/cancelled callbacks cannot publish transfer progress. Existing serial writer gate, planned continuation, backoff and retry timing remain. WorkManager/testing upgrade only to 2.10.5, whose [official patch notes](https://developer.android.com/jetpack/androidx/releases/work#2.10.5) include the reproduced pending-command shutdown fix and intervening network constraint fixes. Downloading rows without an active transfer say Waiting for Android, after specific storage/network/Wi-Fi waits.

**Real scheduler coverage:** WorkManagerLifecycleTest creates actual OS-scheduled workers with bounded loopback HTTP fixtures: slow/pause, delete/no resurrection/queue continuation, persistent failure and retry, valid-image SHA corruption and repair, low-storage boundary, connection reset/backoff/recovery, reorder and finishing-worker retry. It checks committed usable pages, first-page reuse and reader deletion protection. The debug fixture provider requires DUMP, permits only fixture IDs and HTTP 127.0.0.1, and is absent from release.

Host probes use root SIGKILL (never force-stop for download recovery), persisted fixture configuration, real foreground work and app relaunch. Local API 30 passed OS network loss/recovery, ongoing foreground transfer with display asleep, process restart, queued continuation and preserved pause intent. An earlier combined network-return-while-asleep API 36 probe remained ENQUEUED with connectivity restored after 45 seconds; Android scheduling latency is not a guaranteed immediate-resume deadline. Network recovery and ongoing screen-off transfer are now separately tested. Manufacturer Doze/battery restrictions and unattended physical-device behavior remain pending.

### 8. Source-specific identity

**Demonstration:** the Nhentai parser fixture returns stable numeric gallery/media/page paths while the image host changes. Full-URL identity needlessly invalidates unchanged content. This is fixture evidence, not observed live rotation.

**Change:** recognized query-free HTTPS Nhentai image hosts use numeric media/page/path identity. MangaDex preserves hash, filename and data/data-saver quality. Other sources, unfamiliar hosts, query parameters and signed URLs remain exact. Changed content, ordering, page count, format or quality invalidates reuse.

**Risk:** old hash-only MangaDex manifests did not preserve quality. Their partial downloads conservatively refresh once on resume/repair because compatibility cannot be proved. Completed downloads are not erased during startup. Four identity tests distinguish unchanged delivery rotation from real content/quality/order/count changes and preserve unknown/signed URL identity.

### 9. Source diagnostics

SourceRequestFailure associates source and stage with catalog/search, details, chapter list, filter/language discovery, page manifest and image transfer. It distinguishes verification, HTTP/rate limiting, transport/offline and parsing/unsupported content, with concise actions. Numeric HTTP status remains visible for worker regressions. Optional diagnostic text excludes URL/query, credentials, cookies, authorization, tokens and server bodies. No history export was introduced. TLS validation, source verification, cancellation, rate cooldowns and bounded retry policy remain; parsing failures are not hidden behind endless transport retries.

## Verification ledger

- Local JVM: **200 discovered, 194 passed, six optional live-network tests skipped, zero failures/errors** after source-level author isolation.
- Python script suite: **10 passed**; host probe scripts compile.
- Local integration: **16/16** in 223.747 seconds (migration Apply race, large-Library measurement, six WorkManager cases, batch selection, seven backlog cases). Later final-row/UI-worker subset: **11/11** in 137.684 seconds, including checksum corruption and narrow/landscape/RTL filters.
- [Android checks 40559146464](https://github.com/joy-arz/komori-reader/actions/runs/40559146464) passed JVM, lint, debug and release shrinking on bfbf34e. Lint: **zero errors, 46 existing warnings**. Local heavy lint/shrinker run overlapping the emulator was stopped; that run is not counted as a pass.
- [Full device run 40555273354](https://github.com/joy-arz/komori-reader/actions/runs/40555273354): all 80 instrumentation tests passed on API 26/30/36; API 30 host probes passed, API 26/36 host harness failures prevented overall green. API 26 content-call port syntax and combined API 36 network/sleep probe were subsequently corrected.
- [Latest full run 40559146530](https://github.com/joy-arz/komori-reader/actions/runs/40559146530): API 26 80/80 instrumentation passed then cache-restart launch timed out; API 30 had a pre-existing NavigationHistory stale-node failure; API 36 had that stale-node failure plus a direct-worker fixture competing with app-started real work. These are being investigated/corrected; this run is not passing acceptance.
- Latest API 36 targeted repeat [40559146436](https://github.com/joy-arz/komori-reader/actions/runs/40559146436): **4/5 passed**, new query-change assertion failed with correct native controls present in the captured dump. Synchronization follow-up pending.

## Measured performance, not phone claims

API 30 local arm64 emulator, debug APK, shared 8 GB Mac host. Gradle was stopped during measurements. [Raw log](evidence/measurements-api30.txt), [cold launches](evidence/cold-start-api30.json), [gfxinfo](evidence/large-library-gfx-api30.txt).

| Workload | Observed result | Interpretation |
|---|---|---|
| 80 synthetic 256×4096 webtoon decode/cache iterations; 3,685,498 encoded bytes/page | Median 39.89 ms, p95 82.44 ms; retained 66,338,964 B under 67,108,864 B; PSS 161,589→165,804 KB | Real sampled decode/cache/eviction/pinning; not full-reader frame/recomposition acceptance |
| Source-session construction, warm process | 417.92 / 0.206 / 0.105 ms | First sample may include WebView/native initialization; no phone claim |
| Real Room 1,000 titles + 50,000 progress seed | 19,915.70 ms; first Activity visible 17,768 ms; PSS 95,259→170,514 KB | Includes debug JIT/first Activity/WebView; not a release cold-start claim |
| Large Library UI | Four accessible cover nodes; unselected VM observes no progress rows; 50 progress writes/observations 2,836.57 ms; composition apply delta 0 | Confirms lazy/scoped observation behavior; no speculative index/rewrite |
| Disk-backed progress query | Full-table median 415.73 ms vs title-scoped 2.31 ms, existing index_progress_mangaId | Verifies earlier scoped-query repair |
| Three process-cold debug launches, disk/OS caches retained | TotalTime 2,133 / 4,124 / 3,609 ms | Force-stop used only for this startup measurement |
| Software-emulator large-Library gfx | 125/125 janky frames, median 300 ms, saturated GPU histogram | Poor emulator frame results; cannot establish smoothness or physical low-memory performance |

No heavy dependency, expensive effect or broad database rewrite was added based on these numbers. Physical 2 GB memory pressure, full long-session release-frame profiling, manual TalkBack/focus/contrast and source-session startup on phones remain acceptance work.

### Verification harness follow-up

NavigationHistoryTest previously retained a UiObject2 across lazy-row recycling. It now captures immutable label/bounds together, discards already-stale nodes, taps that location and still requires the exact restored pixel bounds. The background test previously inserted a queued row then invoked a direct worker while Activity startup could schedule another real worker; it now injects fixture transport into one actual WorkManager worker, retaining reset, headers, Activity-close and file assertions. The final synchronized local subset passed **6/6 in 41.528 seconds**, including BatchSelection, both navigation tests and all three reading/background tests after these corrections. [Selected Authors screenshot](screenshots/authors-selection-api30.png). Restart failures now capture full logs, process state and SIGQUIT/ANR traces; lifecycle probes run independently of instrumentation failures while preserving the failing exit status.

### Latest cloud probe evidence

[Run 40562830400](https://github.com/joy-arz/komori-reader/actions/runs/40562830400) passed startup cleanup and the real WorkManager HTTP/network/screen-off/SIGKILL probes on **all three API levels (26/30/36)**. Each level passed 79/80 instrumentation tests; only the evolving BatchSelection harness failed (ambiguous group-header text on 26/36, a native cover click during UI updates on 30). Exact semantic group matching and synchronized cover/author actions correct those selectors while retaining native accessibility assertions. Final matrix rerun is pending. The prior single API 26 restart-launch timeout did not reproduce with process/ANR capture enabled; its cause remains unconfirmed. The final workflow runs three consecutive startup-recovery probes and fails on the first unsuccessful attempt, rather than retrying a failure until green.

[Android checks 40562830618](https://github.com/joy-arz/komori-reader/actions/runs/40562830618) passed again, including release shrinking. All new local selector changes compile; the previous complete six-test local subset passed, and the latest action-specific subset is being verified.

## Remaining acceptance and known risks

- Final synchronized API 36 five-repeat run and full API 26/30/36 matrix are still required; current failing CI is disclosed above.
- One earlier API 26 restart-launch timeout has an unconfirmed cause; the same app passed subsequent startup and download SIGKILL probes. Capture process/log/ANR evidence on recurrence; do not mask it with a longer timeout.
- No physical phone is connected. Battery restriction/Doze, thermal/low-memory, TalkBack and real Android installation acceptance are pending.
- Six optional live-network tests were skipped. Live sources, rate-limit/challenge redirects and CDN behavior have not been newly validated.
- Partial author metadata deliberately excludes uncertain matches until retry succeeds. Persistent failures require user action; they do not loop automatically.
- Legacy hash-only MangaDex partial downloads may refresh once on resume/repair. Android may defer constrained background work; Waiting for Android explains scheduler waits without forcing or resetting backoff.
- PR remains unmerged; no new version, release, signing identity or secret changes.
