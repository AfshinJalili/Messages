#!/usr/bin/env python3
"""Guards against proven background battery drains in the default SMS app path.

Fails on:
- BroadcastReceivers under receivers/ calling refreshConversations() without an explicit
  reconcile flag, or message-path receivers forcing reconcile=true / calling
  scheduleProviderReconcile / refreshInbox
- Message-path receivers that use reconcile=false without a targeted Room write
  (inbox / thread would go stale without the full reconcile)
- MessagingEvent.ScheduledMessageSent (a full reconcile) published anywhere but the
  due-scheduled-message sender in messaging/ScheduledMessage.kt
- InboxImport aborting the whole message import on one thread failure, or finishing only
  when every thread succeeded (a permanently bad thread must be abandonable)
- InboxReconciler clearing expired scheduled messages for every conversation instead of only
  scheduled ones
- The launch/resume dirty-check counting rows a reconcile never keeps (Room's temporary scheduled
  rows, Telephony threads the inbox leaves out), so the counts never match and every launch,
  resume and send runs the full reconcile
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
CONVERSATION_REPO_FILE = APP_KT / "helpers/ConversationRepository.kt"
CONVERSATION_TELEPHONY_FILE = APP_KT / "helpers/ConversationTelephony.kt"
CONVERSATIONS_DAO_FILE = APP_KT / "interfaces/ConversationsDao.kt"
# The one sender of a due scheduled message; its ScheduledMessageSent triggers a full reconcile.
SCHEDULED_SENDER_FILE = APP_KT / "messaging/ScheduledMessage.kt"

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
    if "event.reconcile" not in events_text or "host.refreshInbox()" not in events_text:
        violations.append(
            f"{rel(EVENTS_FILE)}: must only call host.refreshInbox() when reconcile is true or for ScheduledMessageSent"
        )

# Targeted single-thread sync helper used when reconcile is skipped.
if not SYNC_FILE.is_file():
    violations.append(f"missing {rel(SYNC_FILE)}: receivers need a single-thread Room sync helper")
else:
    sync_text = SYNC_FILE.read_text()
    if "fun syncThread(" not in sync_text or "conversations.insertOrUpdate(" not in sync_text:
        violations.append(
            f"{rel(SYNC_FILE)}: must expose syncThread that writes via ConversationRepository.insertOrUpdate"
        )

# --- Receivers must not full-reconcile on every wake --------------------------
HEAVY = re.compile(r"\b(scheduleProviderReconcile|InboxRepository\.refreshInbox)\s*\(")
CONVERSATIONS_CHANGED = re.compile(r"MessagingEvent\.ConversationsChanged\s*\(([^)]*)\)")
# Rare path that deletes a temporary scheduled thread and needs a full pass.
ALLOW_RECONCILE = {"ScheduledMessageReceiver.kt"}

# Each message-path receiver must update Room itself. Every listed pattern must appear: they are
# the writes that keep inbox snippet/date/unread, notifications, and an open thread correct
# without a full pass.
ROOM_WRITES = {
    "SmsReceiver.kt": (
        "conversations.insertOrUpdate(",
        "messagesDB.insertOrUpdate",
    ),
    "MmsReceiver.kt": ("conversations.insertOrUpdate(",),
    "MarkAsReadReceiver.kt": ("markThreadRead",),
    "SmsStatusSentReceiver.kt": ("updateType", "ConversationSync"),
    "MmsSentReceiver.kt": ("ConversationSync", "syncThread"),
    "DeleteSmsReceiver.kt": ("refreshFromTelephony", "deleteMessage"),
}

for path in kotlin_files(RECEIVERS):
    text = path.read_text()
    if path.name in ALLOW_RECONCILE:
        continue
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
        missing = [token for token in needed if token not in text]
        if missing:
            violations.append(
                f"{rel(path)}: message-path receiver requires a targeted Room write "
                f"({' + '.join(missing)}); otherwise inbox/thread stay stale"
            )

# ScheduledMessageSent triggers a full reconcile, so only the due-scheduled-message sender may publish it.
for path in kotlin_files(APP_KT):
    if path != SCHEDULED_SENDER_FILE and "MessagingEvent.ScheduledMessageSent(" in path.read_text():
        violations.append(
            f"{rel(path)}: only {rel(SCHEDULED_SENDER_FILE)} may publish ScheduledMessageSent (triggers full reconcile)"
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

# --- Dirty-check: both counts hold only what a reconcile keeps ----------------
def member_body(text: str, signature: str) -> str:
    """The text of the member starting at signature, up to the next member at the same indent."""
    start = text.find(signature)
    if start < 0:
        return ""
    end = re.search(r"\n    (?:override |private |internal )?fun ", text[start + len(signature):])
    return text[start : start + len(signature) + end.start()] if end else text[start:]


dirty_check = member_body(CONVERSATION_REPO_FILE.read_text(), "fun needsFullReconcile()")
if "conversations.getTelephonyThreadCount()" not in dirty_check or "telephony.inboxThreadCount()" not in dirty_check:
    violations.append(
        f"{rel(CONVERSATION_REPO_FILE)}: needsFullReconcile must compare getTelephonyThreadCount() "
        "with inboxThreadCount(), the counts of what a reconcile keeps"
    )
room_count = re.search(r'@Query\("([^"]*)"\)\s*fun getTelephonyThreadCount\(', CONVERSATIONS_DAO_FILE.read_text())
if not room_count or "is_scheduled = 0" not in room_count.group(1):
    violations.append(
        f"{rel(CONVERSATIONS_DAO_FILE)}: getTelephonyThreadCount must leave out is_scheduled rows, "
        "which Telephony never has"
    )
telephony_text = CONVERSATION_TELEPHONY_FILE.read_text()
for member in ("override fun conversations(", "override fun inboxThreadCount()"):
    if "inboxShows(" not in member_body(telephony_text, member):
        violations.append(
            f"{rel(CONVERSATION_TELEPHONY_FILE)}: {member.split('fun ')[1].rstrip('(')} must keep threads "
            "through inboxShows, so the count and the reconcile leave out the same threads"
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
