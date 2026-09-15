#!/usr/bin/env bash
set -uo pipefail
mkdir -p device-evidence
adb shell dumpsys webviewupdate > device-evidence/webview.txt
cat device-evidence/webview.txt
test_status=0
gradle --no-daemon :app:connectedDebugAndroidTest || test_status=$?
python3 ci/test_report.py app/build/outputs/androidTest-results/connected
adb pull /sdcard/Android/data/app.notenote.todo.test/files/ui-evidence device-evidence/ui-flow || true
mkdir -p device-evidence
adb shell am start -n app.notenote.todo.test/app.notenote.todo.MainActivity || true
sleep 3
adb exec-out screencap -p > device-evidence/android-home.png
adb logcat -d -s chromium:E AndroidRuntime:E > device-evidence/runtime-errors.log
exit "$test_status"
