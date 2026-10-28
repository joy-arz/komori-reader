## Install on Android

Download **komori-0.8.7.apk** from Assets below. Android 8.0 or newer. Install over your existing app to preserve your Library, progress and downloads.

## Komori 0.8.7

- Start the reader with controls hidden. A deliberate tap in the middle third of both screen axes shows or hides them in paged and webtoon modes.
- Prevent scrolling, pinching, double taps and taps outside that center region from opening the reader controls. Preserve directional page turns, retry buttons and accessible control actions; keep the corner page count passive.
- Keep Android 11 fullscreen scrolling responsive after brightness/window updates by retaining transient system-bar behavior.
- Use the latest typed page number when Go is pressed immediately after editing the reader jump field.

### Validation and upgrade notes

- Preserve the existing package and signing identity for in-place updates. Library entries, reading progress and downloads remain intact.
- Reader changes passed 196 JVM tests (six optional live-source skips) and all 84 instrumented tests on each API 26, 30 and 36 before merge.
- Post-merge API 36 testing encountered one Authors catalog assertion: the expected final title was not found after scrolling. All reader tests passed. This remains under investigation; it is not established as a reader regression.
- Physical-phone and manual TalkBack acceptance remain pending.
