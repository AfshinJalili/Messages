#!/usr/bin/env python3
"""Guards against proven background battery drains in the default SMS app path.

Fails on:
- BroadcastReceivers under receivers/ calling refreshConversations() without an explicit
  reconcile flag, or message-path receivers forcing reconcile=true / calling
  scheduleProviderReconcile / refreshInbox
- Message-path receivers that use reconcile=false without a targeted Room write
  (inbox / thread would go stale without the full reconcile)
- InboxImport aborting the whole message import on one thread failure, or finishing only
  when every thread succeeded (a permanently bad thread must be abandonable)
- InboxReconciler clearing expired scheduled messages for every conversation instead of only
  scheduled ones
- App contact observers registered on more than Contacts.CONTENT_URI, or an onChange that
  schedules a provider reconcile / refreshInbox
- WakeLock.acquire() without a timeout in app Kotlin
- Infinite Compose animations (infiniteRepeatable / rememberInfiniteTransition)
- Periodic WorkManager / AlarmManager.setRepeating / setInexactRepeating in app Kotlin
- while(true) / while (true) outside pointer/gesture await loops

Host-only; no device. Run: python3 tests/check_battery.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_KT = ROOT / "app/src/main/kotlin/com/afsh/messages"
RECEIVERS = APP_KT / "receivers"
APP_FILE = APP_KT / "App.kt"
IMPORT_FILE = APP_KT / "helpers/InboxImport.kt"
RECONCILER_FILE = APP_KT / "helpers/InboxReconciler.kt"
EVENTS_FILE = APP_KT / "helpers/MessagingEvents.kt"
SYNC_FILE = APP_KT / "helpers/ConversationSync.kt"

violations: list[str] = []


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


def kotlin_files(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*.kt") if p.is_file())


# --- MessagingEvents reconcile gating -----------------------------------------
if not EVENTS_FILE.is_file():
    violations.append(f"missing {rel(EVENTS_FILE)}: MessagingEvents owner is required")
else:
    events_text = EVENTS_FILE.read_text()
    if not re.search(
        r"data class ConversationsChanged\s*\([^)]*val reconcile\s*:\s*Boolean\s*=\s*true",
        events_text,
        re.S,
    ):
        violations.append(
            f"{rel(EVENTS_FILE)}: ConversationsChanged must take reconcile: Boolean = true "
            "so non-structural updates can skip the full provider pass"
        )
    if "event.reconcile" not in events_text or "sink.refreshInbox()" not in events_text:
        violations.append(
            f"{rel(EVENTS_FILE)}: must only call sink.refreshInbox() when reconcile is true or for ScheduledMessageSent"
        )

# Targeted single-thread sync helper used when reconcile is skipped.
if not SYNC_FILE.is_file():
    violations.append(f"missing {rel(SYNC_FILE)}: receivers need a single-thread Room sync helper")
else:
    sync_text = SYNC_FILE.read_text()
    if "fun syncThread(" not in sync_text or "insertOrUpdateConversation" not in sync_text:
        violations.append(
            f"{rel(SYNC_FILE)}: must expose syncThread that writes via insertOrUpdateConversation"
        )

# --- Receivers must not full-reconcile on every wake --------------------------
HEAVY = re.compile(r"\b(scheduleProviderReconcile|InboxRepository\.refreshInbox)\s*\(")
CONVERSATIONS_CHANGED = re.compile(r"MessagingEvent\.ConversationsChanged\s*\(([^)]*)\)")
# Rare path that deletes a temporary scheduled thread and needs a full pass.
ALLOW_RECONCILE = {"ScheduledMessageReceiver.kt"}

# Each message-path receiver must update Room itself. Patterns are the writes that keep
# inbox snippet/date/unread, notifications, and an open thread correct without a full pass.
ROOM_WRITES = {
    "SmsReceiver.kt": (
        "insertOrUpdateConversation",
        "messagesDB.insertOrUpdate",
    ),
    "MmsReceiver.kt": ("insertOrUpdateConversation",),
    "MarkAsReadReceiver.kt": ("markRead", "markThreadMessagesRead"),
    "SmsStatusSentReceiver.kt": ("updateType",),
    "MmsSentReceiver.kt": ("ConversationSync", "syncThread"),
    "DeleteSmsReceiver.kt": ("updateLastConversationMessage", "deleteMessage"),
}

for path in kotlin_files(RECEIVERS):
    text = path.read_text()
    if path.name in ALLOW_RECONCILE:
        continue
    if "ScheduledMessageSent" in text:
        violations.append(
            f"{rel(path)}: only ScheduledMessageReceiver may publish ScheduledMessageSent (triggers full reconcile)"
        )
    for match in CONVERSATIONS_CHANGED.finditer(text):
        args = match.group(1)
        if "reconcile = false" not in args:
            line = text.count("\n", 0, match.start()) + 1
            violations.append(
                f"{rel(path)}:{line}: message-path receiver must not trigger full reconcile via ConversationsChanged"
            )
    for match in HEAVY.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: receivers must not call {match.group(1)}; use a targeted Room write"
        )
    if path.name in ROOM_WRITES:
        needed = ROOM_WRITES[path.name]
        if not any(token in text for token in needed):
            violations.append(
                f"{rel(path)}: message-path receiver requires a targeted Room write "
                f"({' / '.join(needed)}); otherwise inbox/thread stay stale"
            )

# --- InboxImport: bounded per-thread failures ---------------------------------
import_text = IMPORT_FILE.read_text()
if "ImportFailureLedger" not in import_text:
    violations.append(
        f"{rel(IMPORT_FILE)}: use ImportFailureLedger so permanently failing threads are abandoned"
    )
if "MAX_ATTEMPTS" not in import_text:
    violations.append(f"{rel(IMPORT_FILE)}: ImportFailureLedger must define MAX_ATTEMPTS")
if not re.search(r"ImportFailureLedger\.pending\s*\(", import_text):
    violations.append(
        f"{rel(IMPORT_FILE)}: import must visit ImportFailureLedger.pending only "
        "(resume failed threads; skip abandoned, handled, and already-imported)"
    )
if not re.search(r"ImportFailureLedger\.finished\s*\(", import_text):
    violations.append(
        f"{rel(IMPORT_FILE)}: set messagesImported only via ImportFailureLedger.finished "
        "(has messages, handled, or abandoned), never 'all succeeded'"
    )
if "handled" not in import_text or "importHandledThreads" not in import_text:
    violations.append(
        f"{rel(IMPORT_FILE)}: persist handled thread ids so empty successful reads do not re-run forever"
    )
if "if (!failed) app.config.messagesImported = true" in import_text:
    violations.append(
        f"{rel(IMPORT_FILE)}: do not gate messagesImported on a run-wide failed flag; "
        "a forever-bad thread would restart import on every reconcile"
    )
# Keep #282's single rising percent model.
if "enum class Phase" in import_text:
    violations.append(f"{rel(IMPORT_FILE)}: keep ImportProgress as one rising percent (no Phase)")
if "ImportProgressMath" not in import_text:
    violations.append(f"{rel(IMPORT_FILE)}: keep ImportProgressMath from the unified first-run card")
if re.search(r'fun conversationsDone\(\)\s*\{[^}]*state\.value\s*=\s*null', import_text, re.S):
    violations.append(
        f"{rel(IMPORT_FILE)}: conversationsDone must not clear progress between stages"
    )

# --- Reconciler: scheduled cleanup only on scheduled threads ------------------
reconciler = RECONCILER_FILE.read_text()
if not re.search(
    r"allCached\.filter\s*\{\s*it\.isScheduled\s*\}\s*\.forEach\s*\{[^}]*clearExpiredScheduledMessages",
    reconciler,
    re.S,
):
    violations.append(
        f"{rel(RECONCILER_FILE)}: clearExpiredScheduledMessages must run only for isScheduled "
        "conversations, not every inbox row on each reconcile"
    )
if re.search(
    r"cachedConversations\.forEach\s*\{[^}]*clearExpiredScheduledMessages",
    reconciler,
    re.S,
):
    violations.append(
        f"{rel(RECONCILER_FILE)}: still clearing expired scheduled messages for every cached conversation"
    )

# --- Contacts observer: one URI, debounced, no reconcile ----------------------
app_text = APP_FILE.read_text()
observer_uris = re.findall(
    r"registerContentObserver\s*\(\s*(ContactsContract\.\w+\.CONTENT_URI)",
    app_text,
)
if observer_uris != ["ContactsContract.Contacts.CONTENT_URI"]:
    violations.append(
        f"{rel(APP_FILE)}: register only ContactsContract.Contacts.CONTENT_URI "
        f"(found {observer_uris or 'none'}); Data/DisplayPhoto multiply sync callbacks"
    )
if not re.search(r"postDelayed\s*\(\s*clearContactCaches", app_text):
    violations.append(f"{rel(APP_FILE)}: contacts onChange must debounce cache clears")
if re.search(
    r"(contactsObserver|clearContactCaches)[\s\S]*?(refreshInbox|scheduleProviderReconcile|refreshConversations)",
    app_text,
):
    violations.append(f"{rel(APP_FILE)}: contacts observer must not schedule inbox reconcile")

# --- App-wide patterns --------------------------------------------------------
WAKE_ACQUIRE = re.compile(r"\.acquire\s*\(\s*\)")
INFINITE_ANIM = re.compile(r"\b(infiniteRepeatable|rememberInfiniteTransition)\b")
PERIODIC = re.compile(
    r"\b(PeriodicWorkRequest|setRepeating\s*\(|setInexactRepeating\s*\()\b"
)
WHILE_TRUE = re.compile(r"\bwhile\s*\(\s*true\s*\)")
AWAIT_OK = re.compile(r"\bawait(PointerEvent|FirstDown|EachGesture|PointerSlop)\b")

for path in kotlin_files(APP_KT):
    text = path.read_text()
    for match in WAKE_ACQUIRE.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: WakeLock.acquire() needs a timeout; bare acquire can hold the CPU forever"
        )
    for match in INFINITE_ANIM.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: {match.group(1)} keeps the UI clock running off-screen"
        )
    for match in PERIODIC.finditer(text):
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: periodic/repeating work wakes the device; use one-shot work with a real deadline"
        )
    for match in WHILE_TRUE.finditer(text):
        window = text[max(0, match.start() - 400) : match.start() + 400]
        if AWAIT_OK.search(window):
            continue
        line = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{rel(path)}:{line}: while(true) outside a pointer await loop looks like a spin/poll"
        )

if violations:
    print("Battery check failed:")
    for item in violations:
        print(f"  {item}")
    sys.exit(1)

print("PASS: battery guards")
