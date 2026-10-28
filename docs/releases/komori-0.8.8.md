## Install on Android

Download **komori-0.8.8.apk** from Assets below. Android 8.0 or newer. Install over your existing app to preserve your Library, progress and downloads.

## Komori 0.8.8

- Add Background downloads controls in Settings and the Downloads menu. Show Android's battery restriction status and let users explicitly allow background transfers through system settings.
- Use regular foreground WorkManager transfers for long chapter downloads instead of the expedited-work quota. Preserve persisted queue order, pause intent, retries, integrity checks and foreground notifications.
- Expand download acceptance to Android 14, switching applications, and screen-off Doze with battery exemption, including the next queued chapter.

- Return directly to the title with one system Back press from the reader, whether controls are hidden or visible. Keep center-tap and accessibility actions for toggling controls.

- Keep the original signing identity and package so the APK updates the installed app in place. Existing Library entries, reading progress, downloads and paused queue items remain preserved.
- Harden Authors selection acceptance by checking Compose toggle state and refreshing native accessibility queries after selection/search transitions. Keep all selection, ordinary navigation and unfollow assertions.
