# MSG-15: Telegram-style scroll-down FAB with unread badge in an open thread

- **Severity:** High
- **Status:** Implemented; device acceptance pending (2026-09-12). See [verification notes](MSG-14-16-verification.md).
- **Blocked by:** None (can start immediately; coordinate read-marking with MSG-16 if both land in parallel)

## Study first (mandatory)

**Do not change the FAB until you have read and checked off the study doc:**

→ [MSG-15-existing-scroll-fab-behavior.md](./MSG-15-existing-scroll-fab-behavior.md)

The app **already ships** a scroll-down arrow FAB. It is scroll-position-based (within 20 items of bottom), has **no unread badge**, and behaves differently from Telegram. Extend this control; do not duplicate it. Run the repro steps in the study doc and trace `setupScrollListener`, `setupAdapter`, and `scrollToBottom` before writing code.

## What to build

When the user is inside a conversation and scrolled away from the bottom, newly arriving messages must **not** auto-scroll (keep current behaviour). Instead, show a round floating button at the bottom-right — arrow-down icon plus an unread counter badge, matching Telegram’s pattern.

On tap:

1. Smooth-scroll to the bottom of the thread.
2. Clear that thread’s message notification from the notification shade.
3. Remove the “new messages” unread divider after a short delay (fade or animate out; ~300–500 ms feels right).

Extend the existing mini FAB (see study doc) — add badge and Telegram-aligned triggers; do not add a second scroll control.

## Demo path

1. Open a thread with history; scroll up so the latest messages are off-screen.
2. Receive a new message in that thread (second device or test inject).
3. List does not jump; FAB appears with badge showing unread count since the user left the bottom.
4. Tap FAB → scroll to latest message, notification for that thread disappears, “new messages” divider fades out shortly after.
5. FAB hides when the user is already at the bottom (existing scroll proximity behaviour can stay).

## Acceptance criteria

- [ ] Agent completed study doc checklist before opening a PR.
- [ ] FAB visible when user is not near the bottom and there are unread messages below the viewport.
- [ ] FAB shows unread count on a badge (cap display at 99+ if that is inbox convention).
- [ ] Tap scrolls to the latest message; FAB hides at bottom.
- [ ] Thread notification cleared from notification center on FAB tap (same effect as opening the thread today).
- [ ] Unread separator removed with a slight delay after FAB tap, not instantly mid-scroll.
- [ ] New messages while at bottom still do not force scroll (regression check).
- [ ] FAB uses existing theme/colors; content description for accessibility.

## Out of scope (MSG-16)

- Two-stage scroll (first unread vs bottom).
- Viewport-only read marking; until MSG-16 lands, avoid reintroducing “mark entire thread read” when the user has not reached the bottom.

## Context (for implementers)

- Layout: `activity_thread.xml` — `scroll_to_bottom_fab`
- Scroll listener: `ThreadActivity.setupScrollListener`, `SCROLL_TO_BOTTOM_FAB_LIMIT`
- Unread divider: `ThreadUnreadSeparator` in `getThreadItems()`
- Notification cancel: already done in `refreshMessages` when activity visible; FAB tap should mirror `onResume` cancel for that `threadId`

## Reference

- Product brief: `docs/SMS_UIUX_RESEARCH.md` — “Unread separator + jump-to-bottom” (P0)
