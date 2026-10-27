#!/usr/bin/env bash
set -euo pipefail
./gradlew assembleDebug assembleDebugAndroidTest --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p device-diagnostics
status=0
for iteration in 1 2 3 4 5; do
    adb shell am instrument -w -r -e class app.komori.BatchSelectionTest app.komori.reader.debug.test/androidx.test.runner.AndroidJUnitRunner > "device-diagnostics/batch-$iteration.log" 2>&1
    if ! python3 -c 'import pathlib,re,sys; s=pathlib.Path(sys.argv[1]).read_text(); sys.exit(not bool(re.search(r"OK \(1 tests?\)",s)) or "FAILURES!!!" in s)' "device-diagnostics/batch-$iteration.log"; then status=1; fi
    adb pull /data/local/tmp/komori-batch-failure.png "device-diagnostics/batch-$iteration-failure.png" || true
    adb pull /sdcard/Android/data/app.komori.reader.debug/files/batch-failure.xml "device-diagnostics/batch-$iteration-failure.xml" || true
    adb pull /sdcard/Android/data/app.komori.reader.debug/files/batch-after-long-press.xml "device-diagnostics/batch-$iteration-selection.xml" || true
    adb shell dumpsys window > "device-diagnostics/batch-$iteration-window.txt"
    adb logcat -d -s KomoriBatch:I AndroidRuntime:E > "device-diagnostics/batch-$iteration-logcat.txt"
done
exit "$status"
