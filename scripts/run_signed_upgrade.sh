#!/usr/bin/env bash
set -euo pipefail

mkdir -p device-diagnostics
status=0
python3 scripts/device_acceptance.py --serial emulator-5554 --package app.komori.reader \
  --baseline baseline/*.apk --candidate candidate/*.apk --seed-disposable-fixture \
  2>&1 | tee device-diagnostics/acceptance.log || status=$?

# Capture while android-emulator-runner still owns a live device; never wait forever.
timeout 15s adb logcat -d > device-diagnostics/device-log.txt || true
timeout 15s adb exec-out screencap -p > device-diagnostics/screen-after.png || true
timeout 15s adb shell uiautomator dump /data/local/tmp/komori-upgrade-final.xml || true
timeout 15s adb exec-out cat /data/local/tmp/komori-upgrade-final.xml > device-diagnostics/window-after.xml || true
timeout 15s adb shell dumpsys activity activities > device-diagnostics/activities-after.txt || true
exit "$status"
