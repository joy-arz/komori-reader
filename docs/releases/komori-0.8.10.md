# Unpublished 0.8.10 candidate

No APK was published for this candidate. Its exact-commit release checks rejected an existing Authors acceptance fixture twice. The immutable tag remains as failure evidence. Version 0.8.11 carries the same reader fix with corrected acceptance tests and complete release notes.

## Komori 0.8.10

- Stabilize downloaded continuous-reader chapter transitions by reading saved image dimensions before displaying the first chapter or adding the next/previous chapter. Valid completed downloads enter with their final page heights and strip counts, instead of changing from loading placeholders during scrolling.
- Keep image-header preparation off the UI thread, sequential and cancelable. Decode visible strips lazily and retain the existing bounded chapter window, ten-image preload limit, zoom and streaming page anchors.
- Keep cached reading responsive when a download or repair owns the chapter file lock. Missing or damaged saved pages retain their existing retry and repair controls.
- Add multi-page offline coverage across five chapters, including viewport retention, strip counts, progress and removal of older chapters, plus a concurrent-writer regression. Preserve the application package, signing identity and existing Library, progress and downloads.
