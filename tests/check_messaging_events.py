#!/usr/bin/env python3
"""Enforces the Messaging Events, App Presence, and Attention Policy contract (ADR-0001).

Fails on:
- Any EventBus / org.greenrobot.eventbus reference in app Kotlin, Gradle, version catalog, or ProGuard
- Revived legacy Events.kt or refreshMessages / refreshConversations helpers
- Direct calls to showMessageNotification or showSendingFailedNotification outside
  NotificationHelper.kt and MessagingEvents.kt
- DirectReplyReceiver calling showMessageNotification instead of appendDirectReplyNotification
- Ad-hoc ProcessLifecycleOwner checks inside receivers/
- Android framework imports leaking into pure AppPresence.kt or AttentionPolicy.kt
- Missing MessagingEvents.init(this) in App.kt

Host-only; no device. Run: python3 tests/check_messaging_events.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_KT = ROOT / "app/src/main/kotlin/com/afsh/messages"
RECEIVERS = APP_KT / "receivers"
APP_FILE = APP_KT / "App.kt"
PRESENCE_FILE = APP_KT / "helpers/AppPresence.kt"
POLICY_FILE = APP_KT / "helpers/AttentionPolicy.kt"
EVENTS_FILE = APP_KT / "helpers/MessagingEvents.kt"
LEGACY_EVENTS_FILE = APP_KT / "models/Events.kt"
DIRECT_REPLY_FILE = RECEIVERS / "DirectReplyReceiver.kt"

violations: list[str] = []


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def kotlin_files(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*.kt") if p.is_file())


# --- Legacy EventBus must stay gone -------------------------------------------
if LEGACY_EVENTS_FILE.exists():
    violations.append(f"{rel(LEGACY_EVENTS_FILE)}: legacy Events.kt must stay removed")

EVENTBUS_RE = re.compile(r"\b(EventBus|org\.greenrobot\.eventbus|libs\.eventbus)\b")
for path in [
    *kotlin_files(APP_KT),
    ROOT / "app/build.gradle.kts",
    ROOT / "gradle/libs.versions.toml",
    ROOT / "app/proguard-rules.pro",
]:
    if not path.is_file():
        continue
    text = path.read_text()
    for match in EVENTBUS_RE.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: EventBus is retired (ADR-0001); use MessagingEvents instead"
        )

LEGACY_HELPERS_RE = re.compile(r"\bfun\s+(refreshMessages|refreshConversations)\s*\(")
for path in kotlin_files(APP_KT):
    text = path.read_text()
    for match in LEGACY_HELPERS_RE.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: legacy {match.group(1)}() helper is retired; publish a typed MessagingEvent"
        )

# --- Required owners & wiring -------------------------------------------------
for required in (PRESENCE_FILE, POLICY_FILE, EVENTS_FILE):
    if not required.is_file():
        violations.append(f"missing {rel(required)}: required by ADR-0001")

app_text = APP_FILE.read_text()
if "MessagingEvents.init(this)" not in app_text:
    violations.append(f"{rel(APP_FILE)}: must initialize MessagingEvents.init(this) in onCreate")

# --- Pure domain helpers stay Android-free ------------------------------------
ANDROID_IMPORT_RE = re.compile(r"^\s*import\s+(android|androidx)\.", re.M)
for pure_file in (PRESENCE_FILE, POLICY_FILE):
    if not pure_file.is_file():
        continue
    text = pure_file.read_text()
    for match in ANDROID_IMPORT_RE.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(pure_file)}:{line}: pure domain module must not import Android framework types"
        )

# --- Notification posting routes only through MessagingEvents -----------------
DIRECT_NOTIF_RE = re.compile(r"\b(showMessageNotification|showSendingFailedNotification|showReceivedMessageNotification)\s*\(")
ALLOWED_NOTIF_FILES = {"NotificationHelper.kt", "MessagingEvents.kt"}

for path in kotlin_files(APP_KT):
    if path.name in ALLOWED_NOTIF_FILES:
        continue
    text = path.read_text()
    for match in DIRECT_NOTIF_RE.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: do not call {match.group(1)}() directly; "
            "publish a MessagingEvent so AttentionPolicy governs alerting"
        )

if DIRECT_REPLY_FILE.is_file():
    direct_reply_text = DIRECT_REPLY_FILE.read_text()
    if "appendDirectReplyNotification(" not in direct_reply_text:
        violations.append(
            f"{rel(DIRECT_REPLY_FILE)}: must update the shade via appendDirectReplyNotification()"
        )

# --- No ad-hoc ProcessLifecycleOwner foreground checks in receivers -----------
for path in kotlin_files(RECEIVERS):
    text = path.read_text()
    if "ProcessLifecycleOwner" in text:
        violations.append(
            f"{rel(path)}: do not check ProcessLifecycleOwner in receivers; "
            "route through MessagingEvents and AppPresence instead"
        )

if violations:
    print("Messaging events contract check failed:")
    for item in violations:
        print(f"  {item}")
    sys.exit(1)

print("PASS: messaging events contract")
