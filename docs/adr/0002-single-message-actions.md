---
status: proposed
---

# Single-message actions: keep the lifted sheet; converge on the selection bar if tap should stop taking over

Tapping a message opens `MessageSheet` (an `AfshSheet`) and floats the bubble above it with `LiftOverlay` (R6-48). Long-pressing selects the message, and `ThreadSelectionBar` offers `selectionActions`. #308 fixed the sheet's dismissal glitch: the bubble now follows `AfshSheet(onLeavingChange)` and leaves with the sheet instead of waiting for `onDismiss`, and a bubble taller than the screen keeps its height on the way back. #308 also asked whether a sheet is the right surface for single-message actions at all. Two alternatives were compared: an anchored popup menu (Telegram) and long-press actions in the selection bar only (Google Messages).

## Current shape

- **Tap.** `tapActions(message, state)` returns Copy, Forward, Star/Unstar, Share, Select text and Delete. A scheduled message gets Edit, Send now and Delete instead. The sheet's first line is date · time · SIM, the only place a grouped message's exact time and SIM appear.
- **Long-press.** `selectionActions(state)` works on one or more selected messages. It has no Select text, Share or scheduled actions, and it adds Details.
- These are two policies answering the same question: what can be done to this message.

## Considered options

### A. Keep `AfshSheet` + `LiftOverlay` (current, with the #308 fix)

- Pros:
  - Stock Material 3 modal sheet: Back, predictive back, drag to dismiss, insets and TalkBack pane titles come for free.
  - It holds the date · time · SIM line and the scheduled actions.
  - It scrolls at large font sizes (`largeTextMenuKeepsLastActionReachable`).
  - Its rows sit within thumb reach.
- Cons:
  - Every tap takes over the bottom half of the screen and moves the eye from the message to the screen's edge.
  - `LiftOverlay` is about 150 lines of bespoke geometry. #308 shows how fragile that is.

### B. Anchored popup menu (Telegram style)

- Pros:
  - The actions stay next to the message.
  - Less screen is covered, and no lift is needed.
- Cons:
  - A stock M3 `DropdownMenu` anchored to a bubble has no good anchor for a bubble taller than the screen. It also has no natural place for the date · SIM line or a reaction-style header.
  - Seven rows at 200% font overflow the space above or below the anchor.
  - Telegram's version (a blurred backdrop, a scaled bubble and a menu joined to it) is a hand-built surface. `DESIGN.md` and `tests/check_material_components.py` steer away from that.
  - It needs its own RTL, inset and keyboard placement rules, which the sheet already gets for free.
  - It is a third action surface beside the sheet and the selection bar.

### C. Long-press contextual actions only (Google Messages style)

- Pros:
  - One action surface and one policy. `selectionActions` would absorb `tapActions`: with a single message selected it would also offer Share, Select text and the scheduled actions.
  - `LiftOverlay` and `MessageSheet` could be deleted.
  - Tap becomes cheap: it reveals the bubble's time · SIM footer inline. C-M1 hides that footer inside a grouped run, which is why the sheet's info line exists today.
  - Reading and scrolling are never interrupted.
- Cons:
  - Actions are less discoverable: a tap no longer shows them.
  - Delete moves into the bar's overflow, one more step away.
  - The scheduled-message flow (84) must move into the bar or into tap-to-edit.
  - Instrumented checks that open the sheet would have to be rewritten.

## Decision (proposed)

- Keep option A for now. With #308 fixed, it has no open defect, and replacing it is a product choice rather than a bug fix.
- Do not adopt B. It adds a third surface and a hand-built component, and its edge cases (tall bubbles, large text, RTL) are where the sheet is strongest.
- If the takeover on tap is to go, do C, in this order:
  1. Merge `tapActions` into `selectionActions`, so the single-selection case is a superset. This is a refactor with no behaviour change.
  2. Make tap reveal the inline time · SIM footer.
  3. Delete `MessageSheet` and `LiftOverlay` (and `AfshSheet.onLeavingChange`, which only they use).

  That leaves one owner for "what can be done to this message" and removes the most fragile code in the conversation screen.

## Consequences

- Under A, `LiftOverlay` motion stays guarded by `MessageActionChecks.liftedBubbleLeavesWithTheSheet`. That check fails if the bubble waits for the sheet's dismissal to settle again.
- Under C, `tapActions` and the R6-48 references go away. `MessageActionChecks`, `ThreadDateChecks` (the sheet info line) and the screenshot tour must move to the selection bar and the inline footer.
