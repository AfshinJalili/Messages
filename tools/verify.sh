#!/usr/bin/env bash
# Host checks, build, emulator instrumentation and screenshots. Runs locally and in CI.
# Usage: tools/verify.sh [TestClass ...]   (HEADLESS=0 shows the emulator, API/AVD override the image)
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
API=${API:-35}
AVD=${AVD:-openline-api$API}
IMAGE="system-images;android-$API;google_apis;x86_64"
APP=org.fossify.messages.debug
RUNNER=$APP.test/androidx.test.runner.AndroidJUnitRunner
export PATH="$SDK/emulator:$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$PATH"

branch=$(git rev-parse --abbrev-ref HEAD)
out=.scratch/qa/${branch//\//-}
rm -rf "$out" && mkdir -p "$out/screenshots"

failed=()
echo "== host checks"
python3 tests/check_design_tokens.py > "$out/design-tokens.txt" 2>&1 || { failed+=(design-tokens); tail -1 "$out/design-tokens.txt"; }
python3 tests/check_inbox_database.py > "$out/inbox-database.txt" 2>&1 || { failed+=(inbox-database); tail -1 "$out/inbox-database.txt"; }

echo "== build"
./gradlew -q :app:assembleCoreDebug :app:assembleCoreDebugAndroidTest

emulators() { adb devices | awk '/^emulator-[0-9]+\tdevice/{print $1}'; }
matching() { for e in $(emulators); do [ "$(adb -s "$e" emu avd name 2>/dev/null | head -n1 | tr -d '\r')" = "$AVD" ] && echo "$e"; done; true; }
serial=$(matching | head -n1)
[ -z "$serial" ] && [ -n "${CI:-}" ] && serial=$(emulators | head -n1)
if [ -z "$serial" ]; then
  if [ ! -d "$SDK/system-images/android-$API/google_apis/x86_64" ] || [ ! -x "$SDK/emulator/emulator" ]; then
    echo "== installing $IMAGE"
    { yes || true; } | sdkmanager --sdk_root="$SDK" emulator platform-tools "$IMAGE" >/dev/null
  fi
  if ! avdmanager list avd -c | grep -qx "$AVD"; then
    echo "== creating AVD $AVD"
    echo no | avdmanager create avd -n "$AVD" -k "$IMAGE" -d pixel_7 >/dev/null
  fi
  echo "== booting $AVD"
  flags=(-no-snapshot-save -no-audio -no-boot-anim -gpu swiftshader_indirect)
  [ "${HEADLESS:-1}" = 1 ] && flags+=(-no-window)
  emulator -avd "$AVD" "${flags[@]}" >"$out/emulator.log" 2>&1 &
  for _ in $(seq 120); do serial=$(matching | head -n1); [ -n "$serial" ] && break; sleep 2; done
  [ -n "$serial" ] || { echo "emulator did not start, see $out/emulator.log"; exit 1; }
fi
export ANDROID_SERIAL=$serial
adb wait-for-device
deadline=$((SECONDS + ${BOOT_TIMEOUT:-600}))
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  [ $SECONDS -lt $deadline ] || { echo "emulator did not finish booting"; [ -f "$out/emulator.log" ] && tail -20 "$out/emulator.log"; exit 1; }
  sleep 2
done

# Never touch a real phone: the target must report itself as an emulator.
[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || { echo "refusing: $serial is not an emulator"; exit 1; }
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[ "$sdk" = "$API" ] || { echo "refusing: $serial runs API $sdk, expected $API (delete or rename AVD $AVD)"; exit 1; }

echo "== preparing $serial"
for s in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $s 0; done
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb install -r -g app/build/outputs/apk/core/debug/*.apk >/dev/null
adb install -r -g app/build/outputs/apk/androidTest/core/debug/*.apk >/dev/null
adb shell cmd role add-role-holder android.app.role.SMS $APP 0
shots=/sdcard/Android/data/$APP/files/screenshots
adb shell rm -rf "$shots"

classes=()
for c in "$@"; do classes+=("org.fossify.messages.$c"); done
args=()
[ ${#classes[@]} -gt 0 ] && args=(-e class "$(IFS=,; echo "${classes[*]}")")

echo "== instrumenting ${*:-all classes}"
adb shell am instrument --user 0 -w -r "${args[@]}" $RUNNER | tee "$out/instrument.txt" | grep -E "^(OK|FAILURES|Tests run)|INSTRUMENTATION_STATUS: (class|test)=" | grep -v "INSTRUMENTATION_STATUS: class" | uniq || true
adb pull "$shots/." "$out/screenshots" >/dev/null 2>&1 || true
adb logcat -d > "$out/logcat.txt"

echo "== results in $out ($(ls "$out/screenshots" | wc -l) screenshots)"
if grep -q "^OK (" "$out/instrument.txt"; then grep "^OK (" "$out/instrument.txt"; else
  failed+=(instrumentation); grep -E "^(FAILURES|Tests run)" "$out/instrument.txt" || tail -20 "$out/instrument.txt"; fi
[ ${#failed[@]} -eq 0 ] || { echo "FAILED: ${failed[*]}"; exit 1; }
echo PASSED
