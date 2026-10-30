# Continuous-reader chapter boundary investigation

## Report and baseline

The user reports that downloaded Lookism chapter 104 automatically transitions to the end of chapter 105 on DemonicScans. No Continue button is pressed. The suspected online/cross-source scope is not confirmed by handset evidence.

Reader baseline: bbcbc95 / 0.8.11, unchanged in source-consent commit a95cd49 and merged 0.8.12 commit b589203. The exact live Lookism pages could not be retrieved here: a TLS-validated HTTPS request to DemonicScans failed with an expired-certificate error, and the browser search fetch could not open the two chapter pages. TLS validation was preserved. Synthetic saved pages are used below, not copies of the reported chapters.

## Confirmed continuous-scroll race

The progress observer removed earlier chapters while a gesture/fling still owned the LazyList scroll operation. The downloaded fixture scrolls through three chapters while holding an actual LazyList scroll mutation open. Before the fix, its assertion failed in 2.463 seconds: expected 37 list items during the active gesture, actual 25. This demonstrates reindexing under active scrolling. It does not by itself reproduce the exact full-chapter skip in Lookism.

Trimming now waits for scrolling to settle. Scroll activity is part of the observed state, so an idle transition triggers cleanup even if the logical page has not changed. Idle window edits explicitly retain the visible page/strip key and pixel offset rather than relying only on an old numerical index. Adjacent loading is bounded to a four-chapter transient window; settled cleanup retains the existing three-chapter window. Previous-chapter insertion waits for an idle scroll as well.

The regression verifies no chapter removal during the held gesture, then verifies the exact visible strip and pixel offset after idle cleanup. Existing streaming-placeholder and downloaded-geometry tests remain in place.

## Layout acknowledgement and CI follow-up

On candidate ac38d6b, API 30 and 36 passed all 98 instrumented cases. API 26 caught the final short-page case sampling a layout where the preceding tall image had expanded and the expected end image was no longer in the visible item list. The previous pending-anchor acknowledgement accepted any new layout object, including an intermediate layout before the intended key/index/geometry had been measured.

Restoration now requires the actual target key, current index, strip height (one-pixel rounding tolerance), and requested offset or a legitimate start/end clamp. A subsequent layout is not sufficient on its own. The final-short-page regression waits for the real short-image size, the preceding image's final strip, and full viewport containment before asserting completion.

API 34 also failed the manual-refresh test while revealing reader controls through a native tap. That test now uses the existing accessible Show reader controls action and explicit Compose rendering synchronization. ReaderControlsTest and ReaderGestureTest retain physical center-tap and drag coverage. Refresh still uses the real visible action and checks session renewal, page/file preservation and active-cache ownership. Timeout limits are unchanged. The final combined API 30 run passed 12/12 reader and manual-refresh cases in 63.105 seconds; all ten release-script Python tests also passed.

## Separate confirmed Continue defect

Store incomplete progress for chapter 2 at zero-based page 5 of 6, scroll fraction 0.95. Open chapter 1 and use `finishChapter()`, the handler for Continue/Next chapter and accepted gap prompts. Before the fix, this test failed in 13.742 seconds: expected page 0, actual page 5. Normal resume semantics incorrectly applied to forward navigation.

Forward continuation now calls `read(next, restart = true)`. Direct chapter opening still resumes its saved page and fraction, as asserted by the same regression. Backward navigation and explicit chapter/page jumps retain their behavior. Existing progress is not globally deleted.

## Validation and limits

Before the continuous-scroll change, nine API 30 reader cases passed in 52.533 seconds with the Continue fix; the extra gesture-based destination-progress case passed in 3.334 seconds. The local JVM suite passed 200 tests with six optional live-source skips. The final 0.8.13 debug build passed all 11 reader instrumentation cases on the API 30 ARM emulator in 41.604 seconds. Repeated runs exposed a pre-render setup race: the test model still had preload 10 while the fixture expected 0. Compose state synchronization alone did not resolve it. The fixture now persists its settings before constructing the model, retains Compose synchronization and the same five-second limit, and logs persisted/model configuration on failure. All three new boundary regressions then passed three consecutive runs (9/9). The new active-scroll and pixel-retention assertions passed. Before the final version/capacity guard edits, JVM tests, lint and both debug/release compilation passed; final GitHub checks are still required.

Actual Lookism 104 → 105 on the user's downloaded files remains a physical-device acceptance check. No claim of live-source or handset reproduction is made. The source-defaults PR #9 passed its complete matrix and merged. Its 0.8.12 release attempt was blocked by the exact-main acceptance run: API 26/30 hit the same pre-render settings-fixture timeout. The tag is preserved; release gates are unchanged. The 0.8.13 candidate includes the defaults, reader fixes and explicit fixture synchronization. iOS development files are excluded from this Android work.
