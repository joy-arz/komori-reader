# Downloaded continuous-reader chapter boundaries

Baseline: main 4b5cc60, application 0.8.9. Source: DemonicScans, continuous reading. The user clarified that the skip lands further ahead **inside the same downloaded chapter**, near an upcoming chapter transition.

## Findings and evidence

Confirmed: downloaded images still entered the lazy list as 420 dp placeholders. Their headers were inspected only when their cells were composed. Even with every file present, page heights and strip counts changed during reading. The 0.8.9 correction handles a visible short placeholder; it does not eliminate provisional offline geometry throughout the current chapter.

A five-chapter fixture creates six saved pages per chapter and blocks every network request. Basic stationary append/trim assertions passed on the old APK, so the exact reported handset skip is not claimed as reproduced. The stronger geometry invariant failed on that APK: eight initial list items versus the 13 required by the saved image headers. The updated test APK passed against the fixed implementation. A supplemental case starts on the penultimate page, scrolls 100 dp into it, prepares the next chapter and requires the same page key and pixel offset. Both cases also exercise progress and removal of older chapters.

## Implementation

Read complete downloads' image headers sequentially on Dispatchers.IO before exposing the first chapter or inserting either adjacent chapter. Publish final dimensions and chapter insertion together on Main.immediate. Keep bitmap/strip decoding lazy and chapter-window metadata bounded; preserve zoom, streaming anchors, cache ownership, ten-image lookahead and cancellation.

Check download completion before attempting the chapter file lock, use tryLocked and recheck completion while holding it. A concurrent writer must not block cached reading. Missing, invalid or busy files keep their existing loading/retry/repair path. There is no database, download-content, package or signing change.

## Acceptance fixture repairs

The 0.8.10 candidate remains unpublished. PR #7's first API 34 run missed the unchanged Authors catalog-scroll assertion (line 117); its one fresh-emulator rerun passed 92/92 without source/assertion/timeout changes. The merged head 0edeef3 then failed Authors Manage (line 144), and its fresh rerun failed the initial catalog query (line 99). Release run 40991118495 correctly rejected both attempts at its exact-commit gate. That tag was retained rather than moved or bypassed.

New local failure captures demonstrated a native longClick opening title details instead of selection. [Screenshot](evidence/native-author-longpress-failure.png) and [UI hierarchy](evidence/native-author-longpress-failure.xml) contain synthetic fixture data. Authors now waits for Compose rendering, refreshes Android 14+ native accessibility queries and injects the actual Compose long press already used by BatchSelectionTest. All selected-state, sorting, navigation and unfollow assertions and timeout limits remain. Its corrected fixture passed once in 20.113 seconds and three further local repetitions.

A 0.8.11 pre-final API 26 run repeated the previously recorded NavigationHistory pre-reader coordinate-tap miss (reader=null, error=null). Navigation acceptance now advances Compose frames during native queries and waits for scrolling to settle before capturing/tapping the row and after returning. The actual coordinate tap and exact pixel-bounds comparison remain. An intermediate local edit installed the Compose rule without pumping the initial native waits; that local fixture issue was corrected before final acceptance. No runtime Authors/navigation fix is claimed.

Restart probes had erased app failure captures before artifact collection. Copy those files immediately after instrumentation, with the existing emulator root access on failed runs, then run the cleanup probes. This preserves diagnostics while retaining all probe results and failure statuses.

## Final software acceptance

- Local final debug/app test compilation passed; combined navigation, Authors and reader suite: **10/10 in 50.175 seconds** on API 30 ARM. Seven reader cases include delayed streaming, current-chapter retention, saved geometry and a held chapter writer. Earlier offline geometry case passed five extra local repetitions before the nonblocking guard.
- Ten Python release/evidence cases and bash syntax passed.
- Final PR #8 head 26818795968568b1ee123b6f4bede1a13478d214: [Android checks 40995810934](https://github.com/joy-arz/komori-reader/actions/runs/40995810934), [device checks 40995810923](https://github.com/joy-arz/komori-reader/actions/runs/40995810923), [selection stress 40995811294](https://github.com/joy-arz/komori-reader/actions/runs/40995811294) all passed on their first attempt. Each API 26/30/34/36 accepted **93/93**, zero failures/errors/skips; selection stress accepted five runs.
- JVM: 202 total, 196 passed, six optional live-network skips. Lint: zero errors, 50 warnings with the same warning messages as the downloaded 0.8.9 report. Debug/release compilation passed.
- PR #8 merged at 3ae8dbc9be386d40cbcfea92cf57e110fe24152d. Version 0.8.11 includes the unchanged reader runtime fix from PR #7 plus the corrected acceptance tests and full written notes.

## Delivery checklist

- [x] Demonstrate provisional offline geometry on the old APK.
- [x] Accept current/last-page retention, multi-page transitions, progress/trim, concurrent writer and streaming controls.
- [x] Accept final PR JVM/lint/debug/release compilation, API 26/30/34/36 matrix and selection stress.
- [x] Exact merged-commit build/device acceptance and signed release verification.
- [ ] Sustained downloaded reading on the affected physical Android 14 phone. No physical handset, new live-source, TalkBack, OEM policy or low-memory performance pass is claimed. Large-chapter header-scan latency on slow physical storage remains unmeasured.

## Published release verification

Merged-head [Android 40996932560](https://github.com/joy-arz/komori-reader/actions/runs/40996932560) and [device 40996932569](https://github.com/joy-arz/komori-reader/actions/runs/40996932569) passed first attempt. Each API accepted 93/93, zero failures/errors/skips. [Release 40996996725](https://github.com/joy-arz/komori-reader/actions/runs/40996996725) passed first attempt, including the unchanged exact-commit gate and automatic VirusTotal/README update.

Downloaded APK: app.komori.reader, version 0.8.11/code 134, minimum API 26, target 36, 2,584,243 bytes. SHA-256 3734c29e002f5c82fd54772648756c655ed6949b050034511c00494077879f8f matches updater metadata and SHA256SUMS. Signature verifies against original certificate 0f654f439d6059cf8caea9fa1d5403342cc47b1b5a8228bf90e2858ec423d5fd. Published body matches the complete saved notes. Release mapping includes savedScrollImages; synthetic fixture/trace markers are absent from DEX. API-backed scan: zero malicious, zero suspicious, 68 undetected. Both working branches were removed after merge. Receipt: docs/releases/komori-0.8.11-verification.json.
