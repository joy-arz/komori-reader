# Downloaded continuous-reader chapter boundaries

Baseline: main 4b5cc60, application version 0.8.9. Report: downloaded chapters can still advance several pages around a continuous chapter boundary.

## Findings and scope

Confirmed: downloaded images still enter the lazy list as 420 dp placeholders. Their headers are read only when their cells are composed. Even with every file present, a chapter's geometry and strip count remain provisional as the user approaches and crosses its boundary. The original 0.8.9 anchor correction handles a visible short placeholder; it does not eliminate these offline layout changes.

A multi-page offline fixture blocks every network request, creates five six-page chapters, and exercises append, progress publication and removal of older chapters. Its original stationary boundary assertions passed on the old implementation. Therefore this is not a claim that the exact reported multi-page skip was reproduced. The stronger geometry acceptance failed on the old APK: 8 list items initially, versus the 13 required by the saved image headers. This directly demonstrates that offline geometry was still changing. The same updated test APK is used for old/fixed comparison.

## Implementation

Prepare complete downloads' image dimensions on Dispatchers.IO before exposing the initial chapter or inserting an adjacent chapter. Read headers only, sequentially, under the chapter file lock; preserve cancellation and do not decode full bitmaps. Publish dimensions and chapter insertion together on the UI thread. Keep lazy strip decoding, cache ownership, streaming anchors, zoom, repair UI and bounded chapter-window retention. Missing/invalid files retain their ordinary loading/repair path. Active downloads must retain their immediate cached-page path rather than wait behind a writer.

## Acceptance checklist

- [x] Demonstrate provisional offline strip counts on the old APK.
- [x] Fixed reader passes the four existing delayed streaming regressions and new five-chapter offline case locally (5/5).
- [ ] Final JVM/lint/debug/release compilation.
- [ ] Full API 26/30/34/36 matrix and selection stress.
- [ ] Preserve reading while a download writer owns the chapter lock.
- [ ] Exact merged-commit build, original signature and full release notes.
- [ ] Sustained downloaded reading on the affected Android 14 phone. No physical-phone or live-source pass is claimed.
