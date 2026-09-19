# 02: Telegram-style scroll-down FAB with unread badge in an open thread

**What to build:** In an open thread, when the user is scrolled up and a new message arrives, show a bottom-right round FAB (down arrow + unread count). Tap scrolls to bottom, clears the thread notification, and fades out the “new messages” divider after a short delay. Do not auto-scroll on receive.

**Blocked by:** None (can start immediately; align read-marking with 03 if parallel)

**Status:** ready-for-agent

**Study first:** `docs/tickets/MSG-15-existing-scroll-fab-behavior.md` — app already has scroll-down FAB; extend it, do not duplicate.

**Tracker alias:** MSG-15 — see `docs/tickets/MSG-15-thread-scroll-fab-unread-badge.md`

- [ ] Study doc checklist completed before coding.
- [ ] FAB with unread badge when not near bottom and unread exist below viewport.
- [ ] Tap → scroll to latest, clear notification, delayed separator removal.
- [ ] FAB hides at bottom; no auto-scroll regression at bottom.
- [ ] Accessible content description; badge caps at 99+ if inbox does.
