# Streamed continuous-reader page jumps

Reported: DemonicScans, continuous scrolling, apparently as images finish loading. Baseline main d26bc42 (application code from 0.8.8).

## Confirmed reproduction

The loading cell is 420 dp high. LazyColumn retains its first-visible item offset when that cell becomes one or more real image strips. If the real page is shorter than the retained placeholder offset, normalizing that offset advances through later items. This can skip unread pages and then publish the wrong reading position. Slow streaming exposes the placeholder long enough for the user to scroll inside it.

Reproduction commit 00bd441 and [device run 40921028234](https://github.com/joy-arz/komori-reader/actions/runs/40921028234): a controlled DemonicScans HTML/image client holds page 2 while its placeholder is scrolled 300 dp, then delivers a 400×80 image in a 300 dp-wide viewport. The first visible key moves from page index 1 to index 2 without additional navigation. The same assertion failed on API 26/30/34/36. This uses the production adapter/cache path and no Download row; it is synthetic transport coverage, not live-source acceptance.

The original chapter-boundary variant failed earlier during setup: it requested 300 dp before appending enough content, so the list correctly clamped it to the available end. The corrected fixture waits for the following chapter, then positions inside the last-page placeholder before delivering the image. This setup failure is not counted as proof of a chapter jump.

## Implementation

- Detect when newly resolved dimensions invalidate the offset inside the visible placeholder; retain the same chapter/page and start that unread image at its beginning.
- Keep a logical pending page/strip anchor through layout changes; recalculate its flat index from current chapter and strip counts rather than assuming fixed one-item pages. Retain its measured screen offset through late loads above an end-of-list clamp; discard that stabilization when the viewport moves or a new user scroll starts.
- Serialize image metadata publication, chapter-window mutations and anchor requests on Main.immediate so remeasurement cannot observe half an update. Publish progress, chapter-window paging and completion only after the requested layout is measured. A legitimate end-of-list clamp or subsequent user input must not leave a permanently pending anchor. Keep an intended anchor for an exit checkpoint only while the old layout remains current.
- Keep valid offsets for taller images and preserve bounded strip decoding/cache pins, retries, scrolling/zoom, chapter ownership and speculative preload limits.
- Hoist an optional LazyListState for deterministic layout acceptance; production uses the same default remembered state.

## Acceptance

- [x] Reproduce the within-chapter jump before the fix on API 26/30/34/36.
- [x] Add delayed short-page, chapter-boundary, tall-page offset and end-of-title clamping regression cases.
- [ ] Accept fixed cases and existing forward/backward continuity, navigation, refresh, gestures and progress-restoration checks.
- [ ] Final JVM/build/lint/release compilation and full API 26/30/34/36 device suites; current evidence is in PR #6.
- [ ] Affected physical Android 14 handset reading DemonicScans online. No live-site, real-phone or performance acceptance is claimed.

Local validation initially encountered a missing Gradle/plugin cache. The online dependency restore was stopped once cloud reproduction was available; this was not an app failure.

The first fixed head afc803f passed all three targeted cases locally and full API 30/34/36 checks, but API 26 repeated the within-chapter assertion. The fixture now explicitly waits for initial restoration to finish before requesting its test position. A separate end-of-title case covers the legitimate list clamp; the guard acknowledges the next measured layout rather than requiring an impossible exact top alignment. Final validation must accept this head; earlier partial passes are not presented as final acceptance.

## Controlled APK comparison

On the same local API 30 emulator, the exact same updated test APK was run against the old 00bd441 reader implementation and the fixed implementation. The old APK failed three of four cases: an unread-page jump, last page to next chapter, and a short final image leaving the visible viewport; its tall-image offset control passed. The fixed APK passed all four in 26.180 seconds. The fixture explicitly awaits the measured placeholder key and offset before releasing the response, and includes a real swipe after the short-page correction.

Temporary event tracing exposed metadata/anchor publication being interleaved with measurement under coroutine effect dispatching. Critical geometry mutations now explicitly run on Main.immediate. Temporary traces were removed before this commit; no production logging of reading identifiers was added. The final four-API acceptance remains required, including repeated boundary coverage; this local result is not a physical-phone or live-site pass.

The final implementation also passed five consecutive unlogged local chapter-boundary repetitions. Existing user authorization for updates is to merge only after PR checks pass, then trigger the signed build. This follow-up prepares 0.8.9; exact merged-commit acceptance remains required before publication.
