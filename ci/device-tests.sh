#!/usr/bin/env bash
set -uo pipefail
mkdir -p device-evidence
adb shell dumpsys webviewupdate > device-evidence/webview.txt
cat device-evidence/webview.txt
test_status=0
gradle --no-daemon :app:connectedDebugAndroidTest || test_status=$?
python3 ci/test_report.py app/build/outputs/androidTest-results/connected
adb pull /sdcard/Download/NoteNoteEvidence device-evidence/ui-flow || true
mkdir -p device-evidence
# UTP uninstalls the app after testing. Reinstall for an actual clean-launch screenshot.
adb install -r app/build/outputs/apk/debug/app-debug.apk || true
adb shell am start -W -n app.notenote.todo.test/app.notenote.todo.MainActivity || true
sleep 3
adb exec-out screencap -p > device-evidence/android-home.png
adb logcat -d -s chromium:E AndroidRuntime:E > device-evidence/runtime-errors.log
exit "$test_status"
