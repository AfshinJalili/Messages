#!/usr/bin/env bash
# Seed Telephony via the debug androidTest seeder, warm the benchmark app's Room
# from that provider data, then run :macrobenchmark. Used by macrobenchmark.yml
# and for local emulator runs.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
export PATH="$SDK/emulator:$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$PATH"

DEBUG_APP=com.afsh.messages.debug
BENCH_APP=com.afsh.messages
DEBUG_RUNNER=$DEBUG_APP.test/androidx.test.runner.AndroidJUnitRunner
out=.scratch/macrobenchmark
mkdir -p "$out"

[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] || {
  echo "refusing: not an emulator"; exit 1
}

# Cached AVD snapshots may retain older APKs signed with a different debug key.
echo "== uninstall stale packages"
adb uninstall "$DEBUG_APP" >/dev/null 2>&1 || true
adb uninstall "$DEBUG_APP.test" >/dev/null 2>&1 || true
adb uninstall "$BENCH_APP" >/dev/null 2>&1 || true
adb uninstall com.afsh.messages.benchmark >/dev/null 2>&1 || true

echo "== install debug (seeder) + benchmark app"
adb install -r -g app/build/outputs/apk/debug/*.apk
adb install -r -g app/build/outputs/apk/androidTest/debug/*.apk
adb install -r -g app/build/outputs/apk/benchmark/*.apk

grant_perms() {
  local pkg=$1
  for p in \
    android.permission.READ_SMS \
    android.permission.RECEIVE_SMS \
    android.permission.SEND_SMS \
    android.permission.READ_CONTACTS \
    android.permission.POST_NOTIFICATIONS \
    android.permission.READ_PHONE_STATE
  do
    adb shell pm grant "$pkg" "$p" 2>/dev/null || true
  done
  adb shell appops set "$pkg" WRITE_SMS allow 2>/dev/null || true
}

echo "== default SMS → debug for provider writes"
grant_perms "$DEBUG_APP"
adb shell settings put secure sms_default_application $DEBUG_APP
sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [ "$sdk" -ge 29 ]; then
  adb shell cmd role add-role-holder --user 0 android.app.role.SMS $DEBUG_APP
fi
echo "role holders: $(adb shell cmd role get-role-holders android.app.role.SMS | tr -d '\r')"
echo "settings default: $(adb shell settings get secure sms_default_application | tr -d '\r')"

echo "== wipe prior Telephony SMS so seed delta is exactly TARGET_MESSAGES"
# content://sms delete is a no-op without WRITE_SMS + default role in some builds,
# and still leaves provider-cached rows. On the emulator, clear mmssms.db directly.
adb shell 'su 0 sqlite3 /data/data/com.android.providers.telephony/databases/mmssms.db "
  DELETE FROM sms;
  DELETE FROM pdu;
  DELETE FROM part;
  DELETE FROM addr;
  DELETE FROM threads;
  DELETE FROM canonical_addresses;
"' >/dev/null
adb shell 'su 0 am force-stop com.android.providers.telephony' >/dev/null
sms_left=$(adb shell content query --uri content://sms --projection _id 2>/dev/null | grep -c '^Row:' || true)
echo "sms rows after wipe: $sms_left"
[ "$sms_left" = "0" ] || { echo "refusing: SMS wipe left rows"; exit 1; }

echo "== seed synthetic inbox (~2k threads / ~25k messages)"
adb shell am instrument --user 0 -w -r \
  -e seedInbox 1 \
  -e class com.afsh.messages.testdata.InboxSeederChecks \
  $DEBUG_RUNNER | tee "$out/seed.txt"
grep -q "AssumptionViolatedException" "$out/seed.txt" && {
  echo "seeder was skipped (assumption)"; exit 1
}
grep -qE "FAILURES|Error in |AssertionFailed|IllegalStateException|INSTRUMENTATION_STATUS_CODE: -2" "$out/seed.txt" && {
  echo "seeder failed"; exit 1
}
grep -q "^OK (" "$out/seed.txt" || { echo "seeder failed (no OK)"; exit 1; }

echo "== hand SMS role to benchmark app + clear its data (empty Room, full Telephony)"
adb shell pm clear $BENCH_APP >/dev/null
grant_perms "$BENCH_APP"
adb shell settings put secure sms_default_application $BENCH_APP
if [ "$sdk" -ge 29 ]; then
  adb shell cmd role remove-role-holder --user 0 android.app.role.SMS $DEBUG_APP 2>/dev/null || true
  adb shell cmd role add-role-holder --user 0 android.app.role.SMS $BENCH_APP
fi
echo "role holders: $(adb shell cmd role get-role-holders android.app.role.SMS | tr -d '\r')"

# Typical cold start after the app has been used: import once, then force-stop.
# After the Room-first cold-start work the inbox LazyColumn is scrollable while
# import is still running — wait until the import overlay is gone, not just for
# scrollable=true, or the benchmark measures a half-empty inbox.
echo "== warm Room (wait until import finishes and inbox is scrollable)"
adb shell am force-stop $BENCH_APP
adb shell am start -W -n $BENCH_APP/.activities.MainActivity >/dev/null || \
  adb shell monkey -p $BENCH_APP -c android.intent.category.LAUNCHER 1 >/dev/null
deadline=$((SECONDS + ${WARM_TIMEOUT:-900}))
ready=0
while [ $SECONDS -lt $deadline ]; do
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  ui=$(adb shell cat /sdcard/ui.xml 2>/dev/null || true)
  importing=0
  # Match the import overlay title only — message bodies can contain bare "N%".
  echo "$ui" | grep -Eqi 'text="Importing your messages"|text="در حال ورود' && importing=1
  scrollable=0
  echo "$ui" | grep -q "package=\"$BENCH_APP\"[^>]*scrollable=\"true\"" && scrollable=1
  if [ "$scrollable" = 1 ] && [ "$importing" = 0 ]; then
    ready=1
    echo "inbox ready after ${SECONDS}s (import done, list scrollable)"
    break
  fi
  pct=$(echo "$ui" | tr '>' '>\n' | grep -Eo 'text="[0-9]+%"' | head -1 || true)
  echo "warm progress: scrollable=$scrollable importing=$importing $pct t=${SECONDS}s"
  # Keep the process alive while import runs.
  if ! adb shell pidof $BENCH_APP >/dev/null 2>&1; then
    echo "benchmark app died during warm; relaunching"
    adb shell am start -W -n $BENCH_APP/.activities.MainActivity >/dev/null || true
  fi
  sleep 15
done
[ "$ready" = 1 ] || {
  echo "Room warm timed out; dumping UI"
  adb shell cat /sdcard/ui.xml 2>/dev/null | head -c 4000 || true
  exit 1
}
adb shell am force-stop $BENCH_APP

echo "== run macrobenchmark"
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest --stacktrace \
  | tee "$out/benchmark-gradle.txt"

echo "== collect benchmark JSON"
find macrobenchmark/build/outputs -name '*benchmarkData.json' | tee "$out/json-files.txt" || true
mkdir -p "$out/outputs"
cp -r macrobenchmark/build/outputs/. "$out/outputs/" 2>/dev/null || true

if [ "${SKIP_BUDGET_CHECK:-0}" = 1 ]; then
  echo "== budget check skipped (SKIP_BUDGET_CHECK=1)"
else
  echo "== budget check"
  python3 tools/check_benchmark_budgets.py macrobenchmark/build/outputs
fi

echo PASSED
