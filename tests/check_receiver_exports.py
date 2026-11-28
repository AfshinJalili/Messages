#!/usr/bin/env python3
"""Keeps the app's broadcast receivers private unless the system must reach them.

Fails on:
- A <receiver> in app/src/main/AndroidManifest.xml that is exported (android:exported="true",
  or an intent-filter with no android:exported) and is not in SYSTEM_RECEIVERS below. Any other
  app could send it an explicit broadcast: notification actions send SMS, delete messages and mark
  threads read, and MmsSentReceiver deletes files. Our own PendingIntents and in-app broadcasts
  reach a receiver with android:exported="false".
- A SYSTEM_RECEIVERS entry that does not require the permission the system sender holds.
- DefaultSmsChangedReceiver handling any action but the protected DEFAULT_SMS_PACKAGE_CHANGED.

Host-only; no device. Run: python3 tests/check_receiver_exports.py
"""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
RECEIVERS = ROOT / "app/src/main/kotlin/com/afsh/messages/receivers"
PACKAGE = "com.afsh.messages"
ANDROID = "{http://schemas.android.com/apk/res/android}"

# Receivers the system delivers to, with the permission its sender holds (None: see the reason).
SYSTEM_RECEIVERS: dict[str, tuple[str | None, str]] = {
    f"{PACKAGE}.receivers.SmsReceiver": (
        "android.permission.BROADCAST_SMS",
        "Telephony delivers SMS_DELIVER",
    ),
    "com.android.mms.transaction.PushReceiver": (
        "android.permission.BROADCAST_WAP_PUSH",
        "Telephony delivers WAP_PUSH_DELIVER",
    ),
    f"{PACKAGE}.receivers.DefaultSmsChangedReceiver": (
        None,
        "DEFAULT_SMS_PACKAGE_CHANGED is a protected broadcast that only the system can send, "
        "and onReceive ignores every other action",
    ),
}
DEFAULT_SMS_ACTION_GUARD = "intent.action != ACTION_DEFAULT_SMS_PACKAGE_CHANGED"

violations: list[str] = []


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def class_name(name: str) -> str:
    if name.startswith("."):
        return PACKAGE + name
    return name if "." in name else f"{PACKAGE}.{name}"


def is_exported(receiver: ET.Element) -> bool:
    exported = receiver.get(f"{ANDROID}exported")
    if exported is not None:
        return exported == "true"
    return receiver.find("intent-filter") is not None


application = ET.parse(MANIFEST).getroot().find("application")
for receiver in application.iter("receiver"):
    name = class_name(receiver.get(f"{ANDROID}name", ""))
    if not is_exported(receiver):
        continue
    permission = receiver.get(f"{ANDROID}permission")
    if name not in SYSTEM_RECEIVERS:
        violations.append(
            f"{rel(MANIFEST)}: {name} is exported, so any app can send it a broadcast; set "
            'android:exported="false" (our own PendingIntents still reach it), or add it to '
            "SYSTEM_RECEIVERS with the permission its system sender holds"
        )
        continue
    expected, _ = SYSTEM_RECEIVERS[name]
    if permission != expected:
        violations.append(
            f"{rel(MANIFEST)}: {name} must require android:permission=\"{expected}\", "
            f"found {permission!r}"
        )

default_sms = RECEIVERS / "DefaultSmsChangedReceiver.kt"
if DEFAULT_SMS_ACTION_GUARD not in default_sms.read_text():
    violations.append(
        f"{rel(default_sms)}: exported without a permission, so it must return early "
        f"unless {DEFAULT_SMS_ACTION_GUARD.split(' != ')[1]} is the action"
    )

if violations:
    print("Receiver export check failed:")
    for item in violations:
        print(f"  {item}")
    sys.exit(1)

print("PASS: receiver exports")
