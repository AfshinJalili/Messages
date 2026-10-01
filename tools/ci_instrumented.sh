#!/usr/bin/env bash
# Runs on a clean emulator: install both APKs, make the app the default SMS
# app, run every instrumented class. The adb exit code does not show test
# failures, so the result is read from the "OK (n tests)" line.
set -uo pipefail
app_id=org.fossify.messages.debug

adb install -r app/build/outputs/apk/foss/debug/*.apk
adb install -r app/build/outputs/apk/androidTest/foss/debug/*.apk

adb shell cmd role add-role-holder --user 0 android.app.role.SMS "$app_id" 0
for permission in READ_CONTACTS POST_NOTIFICATIONS READ_PHONE_STATE; do
  adb shell pm grant "$app_id" "android.permission.$permission" || true
done

adb shell am instrument --user 0 -w "$app_id.test/androidx.test.runner.AndroidJUnitRunner" \
  | tee instrumented-output.txt
grep -q '^OK (' instrumented-output.txt
