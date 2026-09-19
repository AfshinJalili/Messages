# MSG-14: Inbox unread badge updates when a message arrives on the thread list

- **Severity:** High
- **Status:** Implemented; device acceptance pending (2026-09-12). See [verification notes](MSG-14-16-verification.md).
- **Blocked by:** None (can start immediately)

## What to build

While the user is on the main conversation list (thread list), a newly received message must update that conversation row immediately: bold/unread styling and the numeric unread badge beside the row. Today the push notification fires, but the badge often does not appear until the user leaves and returns or triggers a full refresh.

## Demo path

1. Open the app to the inbox with at least one conversation visible and already read (no badge).
2. From another device or emulator, send an SMS to that thread while the inbox stays on screen.
3. Within one refresh cycle, the row shows unread styling and a badge with the correct count (1, or incremented if already partially unread).
4. Badge and styling stay correct after filter changes (All / Unread) without restarting the app.

## Acceptance criteria

- [ ] New inbound SMS/MMS updates the affected inbox row’s unread badge count without navigating away from the inbox.
- [ ] Badge shows a positive integer when unread; hides when the thread is read (count 0).
- [ ] Unread filter includes the thread as soon as the message lands.
- [ ] No duplicate rows or flicker that clears the badge immediately after it appears.
- [ ] Instrumented or manual test note documents the repro steps above.

## Context (for implementers)

Suspected causes to verify, not prescriptive fixes:

- Event-driven refresh may show Room cache before Telephony/provider sync completes.
- Read state events may reset counts but never increment on inbound messages.
- Badge visibility is gated on `read == false`; provider `Threads.READ` may lag behind new inbox messages.
- A refresh already in progress may defer or skip updating the visible list.

## Related

- Inbox row badge UI: `item_conversation.xml`, `BaseConversationsAdapter.setupBadgeCount`
- Inbound path: `SmsReceiver` → `refreshConversations()` / `insertOrUpdateConversation`
- Inbox refresh: `MainActivity.getCachedConversations`, `Events.RefreshConversations`
