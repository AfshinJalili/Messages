# Existing scroll-to-bottom FAB — study before MSG-15 / MSG-16

**Mandatory read** for any agent touching thread scroll UX. Do not extend or replace the FAB until you can explain each bullet from the running app and code.

> This document records the pre-change behavior. Code study completed on 2026-09-12; the three runtime reproductions remain unverified because no device or emulator was available. See [implementation and validation](MSG-14-16-verification.md).

## What existed before MSG-14–16

A **mini FAB** (`scroll_to_bottom_fab`) sits bottom-right over the message list. Down-arrow icon. No unread badge.

| Aspect | Current behavior |
|--------|------------------|
| **Visibility trigger** | Scroll position only — not unread count. FAB **shows** when `lastCompletelyVisibleItemPosition < itemCount - 20`. FAB **hides** when within 20 items of the bottom. |
| **Initial state** | `android:visibility="invisible"` in layout; first show/hide comes from scroll listener after load. |
| **Tap** | `smoothScrollToPosition(lastIndex)` — always the very last adapter item. |
| **Theming** | Background tint = bottom bar color; icon tint = proper text color. |
| **List anchor** | RecyclerView uses `stackFromEnd="true"` (messages grow upward from composer). |
| **Send path** | `sendMessage()` calls `scrollToBottom()` before dispatch. `insertOrUpdateMessage()` always passes `scrollPosition = lastIndex` on send. |
| **Inbound refresh** | `setupAdapter()` auto-scrolls **only** when the last item changed **and** the user was exactly **one** item from the bottom (`lastPosition - lastVisiblePosition == 1`). Otherwise no scroll — this is why new inbound messages do not jump the list when scrolled up. |
| **Notification** | `refreshMessages()` cancels thread notification when `isActivityVisible`. `onResume()` also cancels. FAB tap does **not** cancel today. |
| **Unread divider** | `ThreadUnreadSeparator` inserted in `getThreadItems()` before first unread block. No delayed removal on FAB tap. |
| **Read marking** | `getThreadItems()` calls `markThreadMessagesRead(threadId)` when any unread exist and activity is visible. `onResume()` marks whole thread read on background thread. Conflicts with viewport-read goals in MSG-16. |

## Constants and entry points

- `SCROLL_TO_BOTTOM_FAB_LIMIT = 20` — proximity threshold (items, not dp).
- `setupScrollListener()` — wires show/hide on scroll.
- `scrollToBottom()` — FAB click and send helper.
- `ThreadAdapter.updateMessages(..., scrollPosition, smoothScroll)` — optional post-submit scroll.

## Gaps vs desired Telegram behavior (MSG-15)

1. FAB appears when **far from bottom**, not when **new unread below viewport**.
2. No **badge count** on FAB.
3. FAB tap does not **clear notification** or **fade divider**.
4. Read marking is **whole-thread**, not viewport-aware (MSG-16).

## Agent checklist before coding

- [ ] Reproduce: open thread, scroll up past 20 items → FAB appears even with no new messages.
- [ ] Reproduce: stay near bottom, receive message → list does not jump (unless 1 item from end).
- [ ] Reproduce: scroll up, receive message → no auto-scroll; confirm whether FAB appears (position-based today).
- [x] Trace `setupAdapter` → `shouldScrollToBottom` condition in one session.
- [x] Note interaction with `stackFromEnd` when changing scroll targets (MSG-16 two-stage).
- [x] Plan extends existing FAB — do not add a second control without removing overlap.

## Files to read (in order)

1. `app/src/main/res/layout/activity_thread.xml` — FAB layout
2. `ThreadActivity.kt` — `setupScrollListener`, `scrollToBottom`, `setupAdapter`, `refreshMessages`, `getThreadItems`
3. `ThreadAdapter.kt` — `updateMessages`
4. `item_thread_unread_separator.xml` — divider UI
