#!/usr/bin/env bash
set -uo pipefail
mkdir -p device-evidence
adb shell dumpsys webviewupdate > device-evidence/webview.txt
cat device-evidence/webview.txt
test_status=0
gradle --no-daemon :app:connectedDebugAndroidTest || test_status=$?
python3 ci/test_report.py app/build/outputs/androidTest-results/connected
adb pull /sdcard/Download/NoteNoteEvidence device-evidence/ui-flow || true
adb logcat -d -s chromium:E AndroidRuntime:E > device-evidence/runtime-errors.log
exit "$test_status"
