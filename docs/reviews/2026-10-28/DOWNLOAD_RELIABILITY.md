# Android 14 background downloads and reader Back follow-up

## Scope and findings

- Confirmed reader defect: 0.8.7 hides controls by default but retains a Back handler that reveals them before exiting. One Back now closes the reader. The new instrumented regression exercises hidden and visible controls through the real Back dispatcher and requires the underlying destination.
- Confirmed missing capability: Komori has no user-controlled battery-exemption flow. Added explicit system consent in Settings > App and Downloads > Queue options, current restriction status and app-settings fallback. Nothing opens or grants itself automatically. Android Doze can suspend network/jobs despite a visible notification.
- Scheduling improvement: chapter jobs used expedited quota despite potentially long transfers. They now use regular work and retain foreground promotion, timed wake lock, constraints, persisted queue, backoff, safe pause/delete and integrity checks. No phone trace is available to establish expedited quota as the reported handset's cause.
- Native test synchronization: prior API 36 stress run 40690991153 passed 1/5; four failures at the native checkbox assertion, with multiple post-failure hierarchies showing both rows checked. Refreshing that query passed the original assertion in all five cases; run 40697219712 then passed 4/5 with one stale query after search/selection reset. It likewise showed both expected controls in the failure hierarchy. Apply fresh native-tree reads across these transitions and keep Compose checked-state plus all native assertions, timeouts, long presses, ordinary navigation and batch actions. API 34+ uses public UiAutomation.clearCache; older devices retain standard querying. This changes the automation client, not production authors or security settings.

## Komikku comparison

Inspected Komikku commit 1656575e8fdaaef010c652f9758130c2a01d8890: [DownloadJob](https://github.com/komikku-app/komikku/blob/1656575e8fdaaef010c652f9758130c2a01d8890/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadJob.kt) also uses regular foreground WorkManager work; [Advanced settings](https://github.com/komikku-app/komikku/blob/1656575e8fdaaef010c652f9758130c2a01d8890/app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt) exposes explicit battery-exemption consent. Komori uses its existing Room/Compose queue rather than copying their downloader. [Android Doze guidance](https://developer.android.com/training/monitoring-device-state/doze-standby) explains network, wake-lock and scheduler limits; [UiAutomation.clearCache](https://developer.android.com/reference/android/app/UiAutomation#clearCache()) documents the native-cache test seam (API 34).

## Acceptance checklist

- [x] Reader Back regression and background-status/intent UI regression implemented.
- [x] Local app/test APK compilation passed; Python script suite 10/10, extended probe syntax passed.
- [x] Add API 34 to the API 26/30/36 full device matrix.
- [x] Extend actual WorkManager + local HTTP probe to complete a transfer under another foreground app and to complete both the current and next chapter during forced screen-off Doze with explicit test-only battery exemption. Restore emulator power/allowlist/network settings in finally. Production never grants the exemption itself.
- [x] PR exact-head build 40699754496, all four device jobs 40699754147 (86 tests each, 344 total) and five-run native selection stress 40699754083 passed. Failed-job attempt 2 recovered corrupt SDK downloads (API 34/targeted 36) and an API 26 adb-root reconnection failure; no assertion/timeout changes.
- [ ] User's physical Android 14 handset: grant background access and verify a multi-chapter queue while switched to another app and locked for several minutes. OEM restrictions and real-site behavior remain unverified.
- [x] Merged PR #5 only after all checks passed; immutable tag v0.8.8 at a00f473e5eca5849504d634fdea7282be086b47b. Merge build 40704716575 and all four device jobs 40704716592 passed. Release 40704843276 published after exact-commit acceptance. Downloaded APK, checksum, updater metadata, full notes and original signer verified; version code 131. README and VirusTotal receipt refreshed automatically (zero malicious/suspicious, 68 undetected, seven unsupported).

## Post-merge failure retained

The first merged-head API 30 run passed the download probe but failed ManualRefreshTest: after a native center tap it did not find Refresh reading. The other three APIs passed, as had all four PR jobs. A fresh API 30 emulator accepted all 86 tests and probes without source, timeout or assertion changes. This is retained as a reader-fixture reliability investigation, not claimed as a demonstrated production fix. Release attempt 1 was blocked; attempt 2 published only after the complete merged-head matrix passed. Detailed downloaded-artifact receipt is in docs/releases/komori-0.8.8-verification.json.
