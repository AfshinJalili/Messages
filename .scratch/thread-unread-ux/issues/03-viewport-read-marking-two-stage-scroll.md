# 03: Viewport-aware read state and two-stage scroll-to-unread

**What to build:** Mark messages read only when they enter the viewport. Off-screen unread stay unread if the user backs out. Extend the scroll FAB: first tap jumps to first unread (upper-middle of screen) when unread span exceeds one screen; second tap goes to bottom.

**Blocked by:** 02 (scroll FAB and divider behaviour)

**Status:** ready-for-agent

**Study first:** `docs/tickets/MSG-15-existing-scroll-fab-behavior.md`

**Tracker alias:** MSG-16 — see `docs/tickets/MSG-16-viewport-read-marking-two-stage-scroll.md`

- [ ] Study doc checklist completed before coding.
- [ ] No whole-thread mark-read on visibility or adapter rebuild alone.
- [ ] Scroll-idle + visible range drives read updates.
- [ ] Back out mid-scroll → inbox badge reflects still-unread messages.
- [ ] Two-stage FAB: first unread (upper-middle) then bottom; single tap when unread fits one screen.
- [ ] FAB stage resets on new messages and manual scroll-to-bottom.
