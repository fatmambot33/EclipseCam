#!/usr/bin/env bash
set -u

mkdir -p build/instrumentation-evidence
adb logcat -c

gradle --no-daemon connectedDebugAndroidTest
status=$?

adb logcat -d > build/instrumentation-evidence/logcat.txt || true

if [[ "$status" -eq 0 ]]; then
  package="com.fatmambo33.eclipsecam"
  activity="$package/.MainActivity"
  apk="app/build/outputs/apk/debug/app-debug.apk"
  evidence_dir="build/instrumentation-evidence/accessibility"
  mkdir -p "$evidence_dir"

  adb install -r "$apk" >/dev/null

  physical_density="$(adb shell wm density | sed -n 's/^Physical density: //p' | tr -d '\r')"
  if [[ -z "$physical_density" ]]; then
    physical_density="420"
  fi
  large_density=$(( physical_density * 120 / 100 ))

  capture_variant() {
    local name="$1"
    local font_scale="$2"
    local density="$3"
    local night_mode="$4"

    adb shell settings put system font_scale "$font_scale"
    adb shell wm density "$density"
    adb shell cmd uimode night "$night_mode"
    adb shell am force-stop "$package"
    adb shell am start -W -n "$activity" >/dev/null
    sleep 2
    adb exec-out screencap -p > "$evidence_dir/$name.png"
  }

  capture_variant "normal-system-day" "1.0" "$physical_density" "no"
  capture_variant "normal-system-night" "1.0" "$physical_density" "yes"
  capture_variant "large-font" "1.5" "$physical_density" "yes"
  capture_variant "large-font-display" "1.5" "$large_density" "yes"

  adb shell settings put system font_scale 1.0 || true
  adb shell wm density reset || true
  adb shell cmd uimode night auto || true

  cat > "$evidence_dir/README.txt" <<EOF
EclipseCam accessibility screenshot matrix
normal-system-day: font scale 1.0, physical density $physical_density, system night mode off
normal-system-night: font scale 1.0, physical density $physical_density, system night mode on
large-font: font scale 1.5, physical density $physical_density, system night mode on
large-font-display: font scale 1.5, density $large_density (120% of physical), system night mode on

The application intentionally uses its EclipseCam dark colour scheme in both Android system day/night modes. These emulator captures provide repository evidence only; Pixel 7 Pro sunlight/glare validation remains a physical release gate.
EOF
fi

if [[ "$status" -ne 0 ]]; then
  adb exec-out screencap -p > build/instrumentation-evidence/failure-screen.png || true
  adb shell dumpsys activity > build/instrumentation-evidence/activity.txt || true
  adb shell dumpsys notification > build/instrumentation-evidence/notifications.txt || true
fi

exit "$status"
