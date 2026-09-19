# MSG-16: Viewport-aware read state and two-stage scroll-to-unread

- **Severity:** Medium
- **Status:** Implemented; device acceptance pending (2026-09-12). See [verification notes](MSG-14-16-verification.md).
- **Blocked by:** MSG-15 (scroll FAB and divider behaviour should exist first)

## Study first (mandatory)

Read the existing FAB and scroll semantics before changing read marking or scroll targets:

→ [MSG-15-existing-scroll-fab-behavior.md](./MSG-15-existing-scroll-fab-behavior.md)

Pay special attention to `setupAdapter` auto-scroll rules, `stackFromEnd`, and whole-thread `markThreadMessagesRead` — MSG-16 replaces those assumptions.

## What to build

**Read marking:** Only messages that have actually entered the viewport (or passed a defined visibility threshold) may be marked read. Scrolling past unread content without those bubbles on screen must not mark them read. If the user closes the conversation while still scrolled up, off-screen unread messages remain unread in Telephony, Room, and the inbox badge.

**Two-stage scroll FAB** (Telegram-plus behaviour):

1. **First tap** when many unread messages do not fit on one screen: scroll to the **first unread** message and position it in the **middle-upper** area of the viewport (not flush to top).
2. **Second tap** (while still not at bottom): scroll all the way to the latest message.
3. Reset stage when the user manually reaches the bottom, when new messages arrive, or when leaving the thread.

Single-screen unread batches can skip stage 1 and go straight to bottom on first tap.

## Demo path

### Viewport read

1. Thread with 10+ unread messages; open thread scrolled to older read history.
2. Tap scroll FAB once (MSG-15) or scroll manually partway — messages still below the fold stay unread.
3. Back out to inbox → badge still shows correct unread count.
4. Scroll until unread bubbles are fully visible → those messages mark read; count decreases accordingly.

### Two-stage scroll

1. Many unread messages (taller than one screen).
2. First FAB tap lands on first unread, centered upper-third.
3. Second FAB tap lands on latest message.
4. One-screen unread: single tap goes to bottom.

## Acceptance criteria

- [ ] Agent completed study doc checklist before opening a PR.
- [ ] `markThreadMessagesRead` (or equivalent) is not called for the whole thread merely because the activity is visible or `getThreadItems()` ran.
- [ ] Opening a thread from inbox may still mark visible/history as read per product choice — document chosen behaviour in ticket PR.
- [ ] Read updates driven by scroll idle + visible item range (RecyclerView layout manager), not by adapter rebuild alone.
- [ ] Leaving thread mid-scroll preserves unread state for messages never shown.
- [ ] FAB first tap → first unread, upper-middle placement; second tap → bottom when unread span exceeds one viewport height.
- [ ] FAB stage resets appropriately on new inbound messages and manual scroll-to-bottom.
- [ ] Inbox badge (MSG-14) and thread FAB badge counts stay consistent with viewport read rules.
- [ ] Automated test or documented manual matrix for SMS + MMS unread rows.

## Context (for implementers)

Known hotspots to refactor:

- `ThreadActivity.getThreadItems()` currently calls `markThreadMessagesRead(threadId)` when `hadUnreadItems && isActivityVisible`
- `ThreadActivity.onResume()` unconditionally marks thread read on background thread
- `ReadStateChecks` androidTest may need updating for new semantics

## Related

- MSG-14: inbox badge live update
- MSG-15: scroll FAB + notification + divider delay
