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
if [[ "${KOMORI_DISPOSABLE_EMULATOR:-0}" == 1 ]]; then
    probe_status=0
    for attempt in 1 2 3; do
        python3 scripts/check_interrupted_cache.py || probe_status=$?
        if [[ -f device-diagnostics/process-recovery.json ]]; then
            cp device-diagnostics/process-recovery.json "device-diagnostics/process-recovery-$attempt.json"
        fi
        if [[ "$probe_status" != 0 ]]; then break; fi
    done
    if [[ "$probe_status" == 0 ]]; then
        python3 scripts/check_download_process_recovery.py || probe_status=$?
    fi
    if [[ "$test_status" == 0 ]]; then test_status=$probe_status; fi
fi
adb logcat -d > device-diagnostics/logcat.txt || true
adb shell dumpsys activity processes > device-diagnostics/processes-after.txt || true
if [[ "$test_status" != 0 ]]; then
    adb root >/dev/null 2>&1 || true
    adb wait-for-device
    adb pull /data/anr device-diagnostics/anr >/dev/null 2>&1 || true
fi
adb logcat -d -s KomoriBenchmark:I > device-diagnostics/progress-benchmark.txt || true
for capture in download-maintenance scheduled-backup-settings reader-manual-refresh navigation-failure facet-failure continuous-failure author-filter-failure; do
    adb pull "/data/local/tmp/komori-$capture.png" "device-diagnostics/$capture.png" || true
done
adb pull /data/local/tmp/komori-author-filter-failure.json device-diagnostics/author-filter-failure.json || true
adb pull /sdcard/Android/data/app.komori.reader.debug/files/ device-diagnostics/app-captures/ || true
adb pull /data/local/tmp/komori-batch-failure.png device-diagnostics/batch-failure.png || true
adb shell dumpsys power > device-diagnostics/power-after.txt || true
adb shell dumpsys window > device-diagnostics/windows-after.txt || true
adb exec-out screencap -p > device-diagnostics/screen-after.png || true
exit "$test_status"
