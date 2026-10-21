# Komori implementation plan

## Direction

An Android-first personal bookshelf and reader, optimized for small devices and unreliable connections. Native Kotlin, Compose, Room, DataStore, OkHttp, WorkManager and a dedicated tiled reader. No backend is needed for the first release.

Visual direction: black surfaces, green/lime accents and bundled Outfit typography, as shipped in 0.7.x. Cover artwork is the visual focus; controls use consistent Material touch targets. Left-aligned layouts, adaptive cover grid, compact chapter list. Avoid ornamental animations and blur.

Review of the initial design: a separate empty home/dashboard adds navigation without helping reading. The first alpha opens directly to Library. Downloads gets its own tab until the real Updates feed is implemented. This makes available behavior explicit rather than presenting unfinished tabs.

## Milestone 1: end-to-end alpha (delivered)

- [x] Native Android project and reproducible Gradle wrapper.
- [x] MangaDex discovery, search, creator filtering and chapter pagination.
- [x] Local library, likes, reading statuses, history and creator follows.
- [x] Language settings, preferred scanlation, strict selection and conservative Smart Chapters.
- [x] Direct reading, saved page, tiled reader and automatic next chapter on finish.
- [x] Durable downloads with page checkpoints, retry and offline loading.
- [x] Unit tests for chapter policy, lint and debug/release CI.
- [x] Private GitHub repository and tag-driven signed APK releases.

Current delivery and remaining work are tracked in [TODO.md](TODO.md). Published release validation is recorded in the release review documents.

## Milestone 2: reader quality

- [x] Continuous webtoon scrolling within chapters, bounded 1024-source-pixel strips and normalized vertical position persistence.
- [x] Left-to-right/right-to-left edge taps and gesture paging; immersive controls. Configurable tap-zone layouts remain planned.
- [x] Per-title reader mode with app-default fallback. Per-title language remains planned.
- [x] Normalized vertical position per release in webtoon mode; zoom/pan position in paged mode remains planned.
- Rereading sessions separate from lifetime completion records.
- Explicit start-from-beginning and caught-up states, distinct from publication completion.
- Persisted per-chapter release overrides and visible alternate release picker.
- Test on a real 2 GB RAM phone; tune caches and add Baseline Profiles.

## Milestone 3: library durability and updates

- [x] Versioned metadata backup/restore through the Android document picker, strict previewed merges, transactional writes and Room migration checks. Scheduled backups and device-wide settings export remain planned.
- Collections, sorting, bulk operations and undo for removal.
- Batched, battery-aware updates for saved titles and followed creators.
- Separate caught-up, source publication status and user-completed status.
- Local-only data remains usable without network.

## Milestone 4: download hardening

- Android-version-aware long user-initiated transfer integration.
- Notification controls, progress and cancellation recovery.
- Storage-budget UI, download estimates, queue ordering and configurable automatic cleanup.
- Stronger manifest integrity checks and bounded image response sizes.
- Test process death, device restart, low storage, source deletion and expired delivery URLs.

## Milestone 5: sources and synchronization

- Capability declarations on the source interface, then a second selected source.
- Optional account synchronization only after verifying supported MangaDex auth flows.
- Preserve source-specific IDs; explicit cross-source linking rather than title-name merging.

## Design review and key decisions

1. Logical chapters and releases must be distinct. Logical keys drive ordering/read counts; release IDs drive image files and exact positions.
2. There is no authoritative main scanlation. Prefer an explicit group; preserve the current release; use a stable deterministic fallback. Do not claim translation quality scoring.
3. Ambiguous chapter metadata must remain visible. Decimal chapters, extras and unknown numbers are never casually merged.
4. A read record is not library membership. Reading creates history; Save and Like remain independent.
5. Cache is expendable, downloaded chapters are not. Keep them in separate directories.
6. Tiled image rendering is an early dependency, not late optimization.
7. Signed release automation should be functional from the first alpha. Never publish an unsigned APK as an installable release or rotate signing identity per run.
8. Keep this release honest about limits. A workable alpha is a foundation for the remaining roadmap, not a claim that every planned feature is complete.

## Proposed acceptance budgets

Physical 2 GB device; downloaded chapter opens in under 1 second; usable local library in under 2 seconds; shrunk APK target under 30 MB; typical reader process target under 150 MB with explicit worst-case tall-page tests. These are targets pending measurement, not published benchmark results.

## Source references

- https://api.mangadex.org/docs/static/api.yaml
- https://developer.android.com/topic/performance/graphics/load-bitmap
- https://developer.android.com/develop/background-work/background-tasks/persistent
- https://developer.android.com/develop/background-work/background-tasks/uidt

## Reader refinement, October 2026

The shelf keeps the existing jade/paper/charcoal tokens and system sans typography. A single compact cover-and-title card makes resuming the most recent story prominent; the cover grid remains the main visual content. No decorative dashboard or additional font dependency. Reader controls use the dark palette for readable contrast regardless of the shelf theme.

```
Library                        Reader
[cover | Continue reading]     [Back | chapter | page/count]
[Search your shelf       ]     [ Webtoon | Paged ]
[Saved Liked History ... ]     [ continuous image strips ]
[cover]  [cover]               [ finish & continue       ]
```

Review: avoid filling a small phone with large promotional cards. Keep the resume card compact and omit it during search or other shelf filters. Scrolling uses lazy region-decoded strips rather than full tall image bitmaps. Dimensions stay lightweight in memory; strips reacquire disk files when revisited after cache eviction. Downloads remain the first source of page files. Scrolling position is normalized against source-image height so restoration survives screen-width changes. Mode is currently global; per-title preferences remain future work.
