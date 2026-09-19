# MSG-14–16 implementation and verification

Implemented 2026-09-12 in the existing worktree. Device acceptance remains pending.

## Behavior

- Inbox read styling and numeric badge use unread inbox SMS/MMS counts, without gating on the provider's thread-level read flag. An incoming refresh invalidates older in-flight UI snapshots.
- The existing mini FAB has a themed, 99+-capped badge and a spoken unread count. No second scroll control was added. Position-based visibility remains available for navigating long, fully read history.
- Inbound refreshes preserve the viewport, including loaded older history. Sending retains its explicit scroll-to-bottom behavior. `stackFromEnd` is retained for initial positioning.
- Opening/resuming marks only received bubbles visible while scrolling is idle. A normal bubble must be fully visible; an oversized bubble must fill the available viewport. The initial visible bottom/history is eligible; offscreen history is not. Cache-only layouts and unfocused/background activities are ineligible.
- Read writes target individual provider IDs and transport types. Room and the inbox receive the remaining provider count. Smooth scrolling alone does not read skipped bubbles.
- The provider load includes the entire unread range and messages between unread bubbles, so pagination cannot truncate the first-unread target.
- A batch taller than one viewport takes two taps: first unread at approximately one-third of viewport height, then latest. A batch that fits uses one tap. Measurement stops as soon as the viewport is exceeded.
- New inbound messages, reaching bottom, and leaving the activity reset the tap stage. FAB taps cancel the thread notification. Divider removal waits until scrolling stops, then removes it on a 400 ms delayed check using the existing item animator.

## Automated verification

Final local results (2026-09-12): app and instrumentation APK assembly passed;
lint passed with 107 warnings and the existing baseline unchanged; database
regressions, design-token checks, and `git diff --check` passed. Device tests
were not run. Logs are in `.scratch/msg14-16-verified.log` and
`.scratch/msg14-16-test-final.log`.

Commands:

```sh
./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest :app:lintFossDebug --offline
python3 tests/check_inbox_database.py
python3 tests/check_design_tokens.py
git diff --check
```

The SQL check exercises the actual DAO statements: partial reads preserve unseen rows, transport IDs do not alias, and decreasing counts correctly reach zero. Existing migration, inbox query, and recycle-bin checks remain included.

`RuntimeUxTicketChecks` now asserts the desired behavior instead of documenting the old defects. It covers live inbox increments, preserving the viewport on arrival, two-stage navigation, unread preservation after exit, and selected SMS/MMS provider writes. Fixtures insert local test rows and remove them in `finally`; they never send messages. `ReadStateChecks` now expects opening an empty thread to preserve its unread state.

Device test command after installing both APKs and granting the default SMS role:

```sh
adb shell am instrument --user 0 -w -e class org.fossify.messages.RuntimeUxTicketChecks,org.fossify.messages.ReadStateChecks org.fossify.messages.debug.test/androidx.test.runner.AndroidJUnitRunner
```

No connected device and no configured AVD were available during implementation. Instrumentation compilation is not a device test pass. Do not close device acceptance until the following matrix passes.

## Pending device matrix

Run each message scenario with SMS and MMS (including a tall attachment):

| Scenario | Expected |
|---|---|
| Read inbox row receives one, then another message | Badge 1, then 2; bold styling; one row; Unread filter includes it without reopening |
| Scroll >20 rows through fully read history | Existing arrow appears; no numeric badge |
| Receive messages while scrolled up, both near and far from bottom | Anchor stays put; unread badge appears even within the old 20-item threshold |
| Receive while already at bottom | No forced scroll; only bubbles meeting the visibility threshold become read |
| Many unread bubbles, first FAB tap | First unread approximately upper-third; unseen lower bubbles remain unread |
| Second FAB tap | Latest message visible; FAB hides; skipped unread bubbles remain unread |
| Small unread batch | One tap reaches bottom |
| New arrival between taps, or manual bottom then scroll away | First-stage targeting resets |
| FAB tap with an active thread notification | That notification clears; unrelated notifications remain |
| FAB tap during a long smooth scroll | Divider survives the scroll and disappears on the delayed idle check |
| Exit or background midway; then return to inbox | Provider, Room and inbox retain the remaining count |
| Rotate/resume, open from search, load older history | Visible-only read rules hold; no whole-thread read on construction/resume |
| 100+ unread, dark/light themes, large font, RTL | 99+ cap, legible themed badge, correct layout and spoken count |

The pre-change study checklist's three runtime reproductions also remain pending; code tracing was completed before edits.
