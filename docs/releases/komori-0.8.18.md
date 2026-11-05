## Install on Android

Download **komori-0.8.18.apk** from Assets below. Android 8.0 or newer. Install over your existing app to preserve your Library, progress and downloads.

## Komori 0.8.18

- Separate Library into compact Collection and Libraries tabs; shorten All libraries to All. Remember the last viewed collection or custom library across app restarts, or choose a fixed starting library in Manage libraries. Fall back to All when that library is deleted.
- Add drag handles for custom-library and source ordering, with tactile feedback, persistent ordering and accessible Move earlier / Move later actions. Keep source switches and the existing adult confirmation independent.
- Add All, Queue and Offline download views, visible grouping and pause/resume controls, clearer selected cards, and a compact selection bar with an announced count and explicit dismissal. Add a Clear filters action to empty filtered libraries.
- Animate GIF pages online and offline, plus animated WebP on Android 9+ (first frame on Android 8); retain tiled decoding for long static pages and add bounded decoder fallback for BMP and device-supported HEIF/AVIF. Import, export and integrity checks recognize the same formats. Animations above 4 megapixels use the first frame to protect memory.
- Use DemonicScans’ advertised alternate image CDN once when its primary returns missing or invalid content. Preserve verification, rate-limit timing, TLS and storage actions from either endpoint. Report empty source images separately from unsupported or damaged files; keep a missing page retryable rather than silently skipping it. The reported Manager Kim chapter 202 page 17 returned an empty primary JPEG response during validation.

- Keep animation decoder failures on manual retry to prevent automatic redownload loops. Preserve the existing cached native decoder for ordinary paged images, and validate Library restoration, lazy-list reordering, selection dismissal and image compatibility across Android API 26, 30, 34 and 36.
