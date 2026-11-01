#!/usr/bin/env bash
set -uo pipefail
set +e
gradle --no-daemon --stacktrace -PneuralTestEmulator=true connectedDebugAndroidTest > "$RUNNER_TEMP/neural-test.log" 2>&1
code=$?
adb logcat -d -s TestRunner:E AndroidRuntime:E DEBUG:E > "$RUNNER_TEMP/neural-logcat.log" 2>&1
if [ "$code" -eq 0 ]; then
  adb shell ls -la /sdcard/Download >> "$RUNNER_TEMP/neural-test.log" 2>&1
  adb pull /sdcard/Download/gossipaudio-neural-sample.wav "$RUNNER_TEMP/neural-sample.wav" >> "$RUNNER_TEMP/neural-test.log" 2>&1
  code=$?
fi
tail -n 70 "$RUNNER_TEMP/neural-test.log"
exit "$code"
