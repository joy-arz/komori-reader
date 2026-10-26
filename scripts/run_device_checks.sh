#!/usr/bin/env bash
set -euo pipefail

# Fresh emulators have no passcode. Keep the test display awake through compilation.
adb shell svc power stayon true
adb shell settings put system screen_off_timeout 2147483647
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell input keyevent 82
mkdir -p device-diagnostics
adb shell dumpsys power > device-diagnostics/power-before.txt
adb shell dumpsys window > device-diagnostics/windows-before.txt

test_status=0
./gradlew connectedDebugAndroidTest --no-daemon || test_status=$?
if [[ "$test_status" == 0 && "${KOMORI_DISPOSABLE_EMULATOR:-0}" == 1 ]]; then
    python3 scripts/check_interrupted_cache.py || test_status=$?
fi
adb logcat -d -s KomoriBenchmark:I > device-diagnostics/progress-benchmark.txt || true
for capture in download-maintenance scheduled-backup-settings reader-manual-refresh navigation-failure facet-failure continuous-failure; do
    adb pull "/data/local/tmp/komori-$capture.png" "device-diagnostics/$capture.png" || true
done
adb shell dumpsys power > device-diagnostics/power-after.txt || true
adb shell dumpsys window > device-diagnostics/windows-after.txt || true
adb exec-out screencap -p > device-diagnostics/screen-after.png || true
exit "$test_status"
