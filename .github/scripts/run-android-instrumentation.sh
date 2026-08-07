#!/usr/bin/env bash
set -u

mkdir -p build/instrumentation-evidence
adb logcat -c

gradle --no-daemon connectedDebugAndroidTest
status=$?

adb logcat -d > build/instrumentation-evidence/logcat.txt || true

if [[ "$status" -ne 0 ]]; then
  adb exec-out screencap -p > build/instrumentation-evidence/failure-screen.png || true
  adb shell dumpsys activity > build/instrumentation-evidence/activity.txt || true
  adb shell dumpsys notification > build/instrumentation-evidence/notifications.txt || true
fi

exit "$status"
