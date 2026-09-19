# 01: Inbox unread badge updates when a message arrives on the thread list

**What to build:** While the user stays on the conversation list, a new inbound message updates that row’s unread badge and styling immediately — notification already works; the badge does not.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

**Tracker alias:** MSG-14 — see `docs/tickets/MSG-14-inbox-unread-badge-live-update.md`

- [ ] New inbound SMS/MMS updates the affected inbox row’s unread badge count without navigating away.
- [ ] Badge shows a positive integer when unread; hides when read.
- [ ] Unread filter includes the thread as soon as the message lands.
- [ ] No flicker that clears the badge right after it appears.
- [ ] Repro steps documented (read thread on inbox → send SMS → badge appears with count).
