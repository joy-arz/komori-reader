## Install on Android

Download **komori-0.8.9.apk** from Assets below. Android 8.0 or newer. Install over your existing app to preserve your Library, progress and downloads.

## Komori 0.8.9

- Keep the current continuous-reader page visible when a streamed image replaces a taller loading placeholder. An obsolete placeholder offset can no longer skip unread pages or spill into the next chapter.
- Keep a short final image visible when the list reaches the end of a title and images above it finish loading. Preserve its measured screen position until the next user scroll.
- Apply image geometry and anchor requests together on the UI thread. Wait for the new layout before updating reading progress, chapter-window paging or completion.
- Preserve offsets that still fit tall images, lazy strip decoding, zoom, retries and the ten-image preload limit. Keep ordinary scrolling responsive after correcting a page.
- Add delayed DemonicScans transport fixtures for within-chapter jumps, chapter boundaries, tall images and end-of-title clamping. Keep the existing application package and signing identity for in-place upgrades.
