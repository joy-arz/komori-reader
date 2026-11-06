# Catalog and Library reliability follow-up

Audited baseline: `a2c5d052e69c6ff1a8cad0d3504547222d62b4c3` (main). Five code-verified findings; no new significant Security or Design issue was identified. This pass preserves signing, package identity, source verification, TLS and download/reader behavior.

## Checklist and implementation

- [x] Library aggregation: remove the eagerly subscribed global join. Observe the active chapter-key index and completed-key projection independently; deduplicate completion snapshots before aggregation and stop the count subscription immediately when unused. Page-only changes still invalidate Room's small completed-key projection while Library is visible, but do not rebuild the index or calculate totals. No schema migration or completion-table synchronization was added.
- [x] MangaDex response bounds: reject responses above 8 MiB after decompression before JSONObject materialization. Chunked and gzip fixtures exercise this path; ordinary responses retain parsing and connection recovery.
- [x] MangaDex metadata: parse real API tag names/IDs and origin categories, refresh legacy title details, and reuse populated native metadata during Authors enrichment. Unknown categories are not invented.
- [x] Discover refinement: isolate per-title failures at concurrency two; retain unknown metadata for explicit retry while preserving catalog errors, cancellation and native cursor/order. Expose a partial-results message and manual retry; successful items are deduplicated and restored to source order. New searches invalidate obsolete retries; navigation snapshots retain unresolved titles.
- [x] Custom libraries: default to All titles, keep unsaved membership visible, preserve optional collection intersections, announce visible restrictions and reset to All titles. Collection shortcuts keep their original behavior.
- [ ] Complete JVM, lint, debug/release and affected emulator checks; record exact results.
- [ ] PR checks, merge, exact-main APK build and public-asset/signature verification; remove the task branch after delivery.

## Triggers and root causes

The former count query joined the entire active index to all completed progress and was eagerly observed even in the reader. The exact old query measured a 75.2 ms desktop in-memory SQLite median with 1,000 titles and 50,000 progress/index rows; this was not a phone benchmark.

MangaDex browse previously discarded attributes.tags; details returned the unchanged Manga. A local API fixture containing Fantasy stored tagsJson=[] and failed the Fantasy Authors filter.

Demonic title-plus-genre and HentaiZap/ImHentai facet-plus-language/category refinements awaited all details without individual failure isolation. One 404 discarded successful matches and blocked that page's completion. The retry never treats an unresolved title as matching.

Custom-library membership survived unsaving, but the UI silently intersected every custom library with Saved and lacked an unrestricted option.

## Acceptance limits

Fixture and emulator results do not prove current live-site behavior or physical 2 GB / OEM performance. Physical TalkBack, battery behavior and real-service acceptance remain separate. No protection-bypass functionality is introduced. Unrelated local iOS, ignore-file and older review edits remain untouched in the original working tree.
