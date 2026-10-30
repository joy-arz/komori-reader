# Continuous-reader chapter boundary investigation

## Report and baseline

The user reports that downloaded Lookism chapter 104 automatically transitions to the end of chapter 105 on DemonicScans. No Continue button is pressed. The suspected online/cross-source scope is not confirmed by handset evidence.

Reader baseline: bbcbc95 / 0.8.11, unchanged in source-consent commit a95cd49 and merged 0.8.12 commit b589203. The exact live Lookism pages could not be retrieved here: a TLS-validated HTTPS request to DemonicScans failed with an expired-certificate error, and the browser search fetch could not open the two chapter pages. TLS validation was preserved. Synthetic saved pages are used below, not copies of the reported chapters.

## Confirmed continuous-scroll race

The progress observer removed earlier chapters while a gesture/fling still owned the LazyList scroll operation. The downloaded fixture scrolls through three chapters while holding an actual LazyList scroll mutation open. Before the fix, its assertion failed in 2.463 seconds: expected 37 list items during the active gesture, actual 25. This demonstrates reindexing under active scrolling. It does not by itself reproduce the exact full-chapter skip in Lookism.

Trimming now waits for scrolling to settle. Scroll activity is part of the observed state, so an idle transition triggers cleanup even if the logical page has not changed. Idle window edits explicitly retain the visible page/strip key and pixel offset rather than relying only on an old numerical index. Adjacent loading is bounded to a four-chapter transient window; settled cleanup retains the existing three-chapter window. Previous-chapter insertion waits for an idle scroll as well.

The regression verifies no chapter removal during the held gesture, then verifies the exact visible strip and pixel offset after idle cleanup. Existing streaming-placeholder and downloaded-geometry tests remain in place.

## Separate confirmed Continue defect

Store incomplete progress for chapter 2 at zero-based page 5 of 6, scroll fraction 0.95. Open chapter 1 and use `finishChapter()`, the handler for Continue/Next chapter and accepted gap prompts. Before the fix, this test failed in 13.742 seconds: expected page 0, actual page 5. Normal resume semantics incorrectly applied to forward navigation.

Forward continuation now calls `read(next, restart = true)`. Direct chapter opening still resumes its saved page and fraction, as asserted by the same regression. Backward navigation and explicit chapter/page jumps retain their behavior. Existing progress is not globally deleted.

## Validation and limits

Before the continuous-scroll change, nine API 30 reader cases passed in 52.533 seconds with the Continue fix; the extra gesture-based destination-progress case passed in 3.334 seconds. The local JVM suite passed 200 tests with six optional live-source skips. The final 0.8.13 debug build passed all 11 reader instrumentation cases on the API 30 ARM emulator in 41.604 seconds. An earlier combined run had one pre-render settings-flow timeout; the fixture now uses Compose state synchronization with the same five-second limit. The new active-scroll and pixel-retention assertions passed. Before the final version/capacity guard edits, JVM tests, lint and both debug/release compilation passed; final GitHub checks are still required.

Actual Lookism 104 → 105 on the user's downloaded files remains a physical-device acceptance check. No claim of live-source or handset reproduction is made. The source-defaults PR #9 passed its complete matrix and merged. Its 0.8.12 release attempt was blocked by the exact-main acceptance run: API 26/30 hit the same pre-render settings-fixture timeout. The tag is preserved; release gates are unchanged. The 0.8.13 candidate includes the defaults, reader fixes and explicit fixture synchronization. iOS development files are excluded from this Android work.
