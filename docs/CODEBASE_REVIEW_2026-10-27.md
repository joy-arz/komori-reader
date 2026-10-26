# Komori codebase review — October 27, 2026

Reviewed main `1425aed48f5387e3fc0ab5b423b7b8ff31ca62f1`, matching origin/main after fetch. Application code is the released 0.8.4 commit `18b194f`. No application source was changed by this review.

## Findings first

Seven actionable P2 defects follow, in recommended implementation order. P2 means a real correctness/reliability problem under the stated conditions, not a claim that every session fails. No critical security vulnerability was established by this review.

### 1. Interrupted reader files survive both cache eviction and Clear cached pages

Evidence: [ReaderViewModel.kt:1252](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/ui/ReaderViewModel.kt#L1252), [ReaderViewModel.kt:1296](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/ui/ReaderViewModel.kt#L1296), [ReaderViewModel.kt:190](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/ui/ReaderViewModel.kt#L190).

Reader transfers create `page-*.part` files. Normal cancellation deletes them in `finally`; process termination cannot execute that cleanup. Eviction totals every file but removes only `.img` files. The user-facing cache-clear action also removes only `.img` files. There is no reader-part reconciliation at application startup.

Consequently abandoned transfers keep consuming cache space, force valid images out, and survive the app's own cache-clear action. An isolated check with two 20 MiB partials, one 5 MiB cached image and a protected 5 MiB target reproduced 45 MiB remaining under a 32 MiB setting; cache clearing still left 40 MiB.

Fix: reclaim abandoned partials at startup and in cache maintenance, using active-transfer ownership or a safe age/lease policy. Cancel/join reader jobs before deleting their temporary files. Count and report reclaimable temporary bytes.

Acceptance: kill a process during two image transfers, reopen, then clear reader cache. Confirm abandoned bytes disappear, active writes are preserved and downloaded chapters are untouched.

Related recovery improvement: local import stages in `files/local-staging` and the rename-before-Room-commit window can also leave orphaned data after process death ([LocalFiles.kt:140](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LocalFiles.kt#L140), [LocalFiles.kt:160](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LocalFiles.kt#L160)). Add an import journal/startup reconciliation rather than relying solely on `finally`.

### 2. Preloading protects one visible file, although continuous reading can display several

Evidence: [ReaderViewModel.kt:1292](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/ui/ReaderViewModel.kt#L1292); `WebtoonReader.kt:368` loads each visible strip from its file.

Eviction excludes only the current transfer target and the file at `readerPage`. In the continuous reader, that position tracks the first visible page; another visible page or a page whose strip decoder is still opening the file is eligible for eviction.

An isolated eviction check used a 64 MiB limit with visible A=20 MiB, visible B=20 MiB, upcoming C=32 MiB and target D=10 MiB. The implemented predicate deleted visible B. This is separate from the timestamp-key regression fixed in 0.8.4. Under cache pressure it can cause another decode failure or reload even with stable keys.

Fix: pin all visible pages and files held by active decoders. Evict only unpinned files. Define bounded temporary overflow or reduce lookahead when the visible set alone exceeds the selected budget; coordinate eviction with writes.

Acceptance: a small-cache, long-webtoon test with multiple visible images and concurrent preloading. Assert that files remain present until visible/decoder leases are released.

### 3. Planned download yields consume the network failure retry budget

Evidence: [Downloads.kt:137](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/Downloads.kt#L137), [Downloads.kt:175](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/Downloads.kt#L175), [Downloads.kt:238](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/Downloads.kt#L238).

The eight-minute checkpoint returns `Result.retry()`. The failure budget is calculated as `attemptBase + runAttemptCount`. After three planned yields, the next ordinary transient error fails `attempt < 3`, even if those earlier runs had no network errors. Thus a roughly 24-minute-plus download can fail permanently on its first real transient failure.

WorkManager run attempts and explicit retries are described in the [Android API reference](https://developer.android.com/reference/androidx/work/ListenableWorker#getRunAttemptCount()) and [retry/backoff guide](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work). The application conclusion above comes from tracing its checkpoint and failure branches.

Fix: persist actual transfer failure counts separately from execution slices, or schedule a successful continuation for planned yields. Keep Retry-After deadlines and pause/cancel behavior intact.

Acceptance: simulate three successful time-slice resumptions followed by one transient socket failure. The chapter must remain retryable and keep completed pages.

### 4. Raw-language Library unread counts disagree with reading behavior

Evidence: [Database.kt:178](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/Database.kt#L178); `Sources.kt:41`; `ManhwaSource.kt:147`.

The unread-count SQL defaults every colon-prefixed source ID to English. Manhwa raw titles are read and indexed in Korean when the path ends in `-raw`, without requiring a saved language override. These titles therefore receive no unread-count row and disappear from the Unread filter despite having unread chapters.

Executed the exact query extracted from Database.kt against an in-memory database built from the exported Room schema 6. A Korean chapter without an override returned `[]`; adding the explicit `ko` preference returned total=1, unread=1.

Fix: centralize effective source/title language for reading, indexing, migration and unread counts. Avoid reproducing a different default policy inside SQL. MigrationViewModel also hardcodes English for non-MangaDex targets and needs the same policy.

Acceptance: translated and raw titles from both Manhwa sources, with unset, explicit and all-language preferences. Compare opening, unread badge, Unread filter, notifications and migration results.

### 5. Three failing author checks can starve every other followed author

Evidence: [LibraryChecks.kt:104](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LibraryChecks.kt#L104), [LibraryChecks.kt:118](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LibraryChecks.kt#L118), [LibraryChecks.kt:121](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LibraryChecks.kt#L121).

Each cycle selects the three oldest `checkedAt` values. Only successful requests update that timestamp. Three permanently failing creators remain oldest and are selected forever, so healthy creators after them are never checked. A control-flow trace reproduced the same three selections across successive cycles.

Fix: track attempted-at separately from last-success-at, persist a fair cursor, and back off failures independently. Preserve the previous successful snapshot when a request fails.

Acceptance: follow five creators; make the oldest three fail repeatedly. The remaining two must still be polled in subsequent bounded batches.

### 6. Speculative preloading can exhaust retries before a page is viewed

Evidence: [ReaderViewModel.kt:1353](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/ui/ReaderViewModel.kt#L1353), [ReaderViewModel.kt:1314](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/ui/ReaderViewModel.kt#L1314); `FailurePolicy.kt:138` and `FailurePolicy.kt:147`.

Lookahead uses the same `pageFileFor` path and four-attempt budget as visible requests. It suppresses the exception in its own coroutine but leaves the failure recorded. For example, failing page 5 can be retried while pages 1, 2, 3 and 4 become current; by the time the user reaches page 5, it can require manual retry even if connectivity has recovered.

Fix: separate speculative attempts from the visible-page retry budget. Continue honoring real source cooldowns, verification failures and cancellation. A failed speculative fetch must not prevent the first visible request from trying.

Acceptance: inject transient failures during four speculative windows, restore connectivity before the target page becomes visible, and confirm a visible fetch succeeds without a manual retry.

### 7. CBZ exports are not consistently importable by the same app

Evidence: [LocalFiles.kt:24](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LocalFiles.kt#L24), [LocalFiles.kt:51](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LocalFiles.kt#L51), [LocalFiles.kt:187](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LocalFiles.kt#L187), [LocalFiles.kt:195](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/data/LocalFiles.kt#L195).

Exports allow 512 MiB of page data and use ZIP compression level zero, whereas imports reject more than 256 MiB of compressed input. A valid approximately 300 MiB export therefore exceeds the import limit. The format lists also disagree: export permits GIF/AVIF pages, but import accepts only JPEG/PNG/WebP. Mixed archives can skip unsupported small pages, and larger skipped entries hit the separate one-MiB limit.

Fix: define a shared import/export size and format contract. Either ensure every produced archive is accepted, or explicitly reject unsupported export sizes/formats before creating the destination. Never silently drop pages in an app-produced archive.

Acceptance: round trips near both size boundaries, supported GIF/AVIF devices where applicable, mixed formats and long chapters; compare page count, ordering and image digests.

## Improvements after these fixes

1. **Coalesce chapter-manifest requests.** `pageFileFor` uses a concurrent map but a non-atomic get/fetch/store sequence ([ReaderViewModel.kt:1240](https://github.com/joy-arz/komori-reader/blob/18b194fa63655edb05778e60e99621c95e1722df/app/src/main/java/app/komori/ui/ReaderViewModel.kt#L1240)). Different lazy pages can request the same absent manifest concurrently. The image semaphore does not cover this fetch. Use one cancellable in-flight manifest request per chapter/quality and share its result.
2. **Scope UI data and progress writes.** ReaderViewModel eagerly observes all stored manga, while each settled scroll writes both Progress and Shelf. Measure Room invalidations and recomposition before adding indexes. Reduce updates to meaningful page/position changes, flush on exit, and keep recovery checkpoints reliable.
3. **Split the largest classes.** KomoriScreen has 2,467 lines and ReaderViewModel 1,563. Extract download coordination, reader session state, discovery, Library and author features behind narrow interfaces, preserving behavior and existing tests.
4. **Clarify author ranking.** All-author Latest/Popularity blend per-creator rank rather than globally comparing dates/views. The menu already discloses this (`KomoriScreen.kt:575`), so this is a limitation, not a hidden sorting defect. True global ordering needs normalized timestamps/views and honest fallbacks for providers that do not expose them.
5. **Expand interruption coverage.** The existing activity-dismissal download test is useful but does not kill the worker process mid-transfer. Add deterministic process-death, pause/cancel/recovery races, cache-pressure and file-publication tests. Physical screen-off, manufacturer restrictions and 2 GiB long-read benchmarks remain open.
6. **Make updater state changes cancelable.** Changing channels does not cancel an in-flight check, and updater transfers use synchronous calls without the image path's socket-cancellation hook. Use a job/token per channel request and per-download temporary ownership; test slow requests and rapid channel switches.
7. **Complete accessibility acceptance.** Exercise TalkBack, large text, RTL, landscape and touch targets on real screens. This remains in the existing TODO; it is not established by the current functional device checks.
8. **Write release changes inline.** Future release notes should include the complete features, fixes, behavior changes and validation, following the requested 0.8.1/0.8.2 format. A compare link should not substitute for the changelog.

## What was checked and what was not

- Fetched origin/main and confirmed it matches the reviewed local commit.
- Inspected reader/image/cache paths, download queue/worker/cancellation, source sessions/transport, unread indexing, background checks, local import/export, migration, backup/restore, updater and test/workflow boundaries.
- Executed the actual unread SQL with the exported schema; ran isolated sparse-file checks of visible eviction and orphan partial handling. These are targeted logic checks, not an Android end-to-end reproduction.
- Other findings are explicit control-flow/contract findings with stated conditions. They were not claimed as newly reproduced physical-device failures.
- Existing 0.8.4 release evidence remains 164 JVM passes plus six optional live-network skips, and 50/50 device checks on each API 26/36. Those suites were not rerun during this read-only code review and do not establish coverage of the new findings.
- No new comprehensive live-source, accessibility, physical screen-off or low-memory benchmark was performed. Tracking remains deferred until core reliability passes.
- Application source, published releases and signing configuration were not modified. This report and the audit TODO entries are the only workspace changes.


## 0.8.5 implementation follow-up

The findings above describe the immutable 0.8.4 baseline; their links intentionally point to that commit. The follow-up implements all seven fixes:

| Finding | Resolution | Regression evidence |
|---|---|---|
| Abandoned transfer/import files | ReaderCacheFiles active-writer ownership and startup cleanup; LocalFiles import mutex and chapter-ownership reconciliation | Orphan vs active/pinned cleanup; locked import orphan handling |
| Visible/decoder eviction | Composed/visible-page pins and decoder leases | 64 MiB pressure fixture preserves both visible/decoding files |
| Planned download slices | Successful continuation carrying only actual failure count; off-head work yields without retry | Eight-minute elapsed-clock continuation retains failure base and completed page |
| Raw-edition unread counts | Shared sourceReadingLanguage policy for counts and migration | Korean raw default, English override and all-language counts |
| Author-check starvation | Persisted attempted-at rotation with independent exponential failure delay | Three failed creators do not block the other two |
| Speculative retry exhaustion | Speculative requests do not record/check visible-page budgets | Four speculative failures followed by successful visible fetch |
| CBZ contract mismatch | 516 MiB archive / 512 MiB page-data contract; JPEG/PNG/WebP/GIF/AVIF parity when decodable | 300 MiB virtual stream accepted, 517 MiB rejected; GIF/PNG export/import byte equality |

Additional changes coalesce manifests, scope Library observation, reduce progress writes while retaining exact final checkpoints, split five screens plus download/progress components, cancel obsolete updater work with revision-checked cache publication, and add Authors metadata/Library filtering without truncating pagination. Global cross-author dates/views remain unavailable for some providers; the UI retains an explicit source-ranking explanation instead of inventing comparable metrics.

Local acceptance: 168 JVM passes, six optional live-network skips; 15/15 new API 30 instrumentation tests passed. The progress fixture measured 101 samples, three Progress writes and one Shelf write in 1,581 ms; this is an emulator measurement, not a physical-device benchmark. The new host probe separately exercises SIGKILL/restart cleanup; it does not claim to terminate a slow real WorkManager network transfer. Full source-network acceptance, physical low-memory/screen-off behavior and manual TalkBack remain open. User explicitly selected emulator validation while hardware is unavailable. Exact commit CI and published APK evidence belongs in VALIDATION.md.


### Final delivery evidence

Published `v0.8.5` at `ad8988f`, code 128. Exact-commit Android checks `40391563371`, device checks `40391563386` and signed release `40393319293` passed. The final matrix passed 66/66 cases on each API 26/30/36, with zero skips and three successful restart probes. All 13 final local reliability tests passed, including the post-close final scroll checkpoint; the local ARM gesture test remains a separate timing/environment limitation and is not claimed as a pass. Final validation also added session-wide pin release and visible-tail gating to reduce chapter append/trim churn. Published APK size/hash/package/updater metadata/original signer/manifest verified; full evidence and remaining acceptance are in VALIDATION.md and TODO.md.
