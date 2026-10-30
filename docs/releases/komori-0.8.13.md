## Install on Android

Download **komori-0.8.13.apk** from Assets below. Android 8.0 or newer. Install over your existing app to preserve your Library, progress and downloads.

## Komori 0.8.13

- Start fresh installations with DemonicScans and WeebCentral enabled. MangaDex and the five dedicated adult sources start disabled.
- Ask whether the user is 18 or older before enabling the first adult source. Declining or dismissing leaves it disabled; accepting enables that source and saves one confirmation for all adult sources. MangaDex can be enabled without the adult warning.
- Preserve existing source choices during upgrades, including sources previously enabled under the old defaults. Existing adult-source users keep access without a new launch prompt, and the confirmation remains saved after disabling and re-enabling sources.
- Wait for the matching image key, list index, strip height and scroll position before accepting restored reader geometry.
- Keep the continuous reader's chapter window stable during a drag or fling. Remove older chapters after scrolling settles, and retain the visible image strip and pixel offset when updating the window.
- Bound adjacent chapter loading while scrolling and wait for an idle position before inserting a previous chapter. Preserve lazy strip decoding, saved-image geometry, zoom and page preloading.
- Start Continue/Next chapter at the destination's first page, even if an earlier visit left unfinished progress near its end. Opening a chapter directly still resumes its saved position.
- Add downloaded-gesture coverage for chapter-window cleanup and next-chapter entry with old progress. Preserve the existing streaming-placeholder, offline geometry, backward continuity and signing checks. The exact reported Lookism 104 → 105 transition remains a handset acceptance check.
