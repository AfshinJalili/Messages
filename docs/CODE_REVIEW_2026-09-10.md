# Review and fixes — 2026-09-10

Scope: uncommitted changes and new source files against HEAD `c7215163`, as
confirmed by the user. Existing work was retained; nothing was committed.
Standards and spec reviews ran independently. `DESIGN.md` is the local design
contract; `docs/SMS_UIUX_RESEARCH.md` also contains optional backlog proposals.

## Confirmed defects fixed

1. **Build failure:** Room KSP reported `MissingType` on `BlockedMessage`.
   `./gradlew :app:kspFossDebugKotlin --offline` failed twice, then passed after
   declaring the `Int` return type of `reasonLabel()`. No dependency replacement
   was needed.
2. **Recycled messages leaked into Starred and search:** those DAO queries did
   not join/exclude recycle-bin entries. The synthetic SQL regression failed
   with `getMessagesWithIds exposes recycled messages`; both queries now use
   the same exclusion as ordinary thread queries. Active hits remain visible.
3. **SMS/MMS stars and reactions collided:** preferences used only numeric
   provider IDs. The device regression reproduced an SMS star appearing on an
   unrelated MMS. Keys and all callers now include the provider type; Starred
   also filters cached rows by that identity.
4. **Reply quote disappeared after recreation:** the device recreation test
   reproduced the loss. Android instance state now preserves the quote and
   restores its strip. The test also checks outgoing text and that cancelling
   a quote remains cancelled after another recreation. No SMS is sent.
5. **Selection left the inbox header visible:** the device visibility test
   failed before the change. The header now uses `INVISIBLE` during selection,
   preserving row positions, and becomes visible when selection ends.

## Standards

- **Documented mismatch, fixed:** `DESIGN.md` requires hiding the header during
  selection. `MainActivity.onInboxSelectionChanged` now complies without
  collapsing layout space.
- **Heuristic, clarified:** the spam thread reused the recycle-bin adapter's
  restore/delete mode with a misleading callback parameter. The local parameter
  now says `restore`, and a short comment explains reuse.

Standards: 2 findings, both addressed; the strongest was selection-header behavior.

## Spec

- **Fixed:** research requires the reply quote to persist across rotation;
  activity recreation is now covered by a passing device test.
- **Backlog, unchanged:** Images/Links search filters are not implemented.
  The research explicitly permits independent delivery; this is incomplete
  feature work, not a regression to fix in this pass.
- **Design reconciliation, unchanged:** attachment icons use on-surface color;
  `DESIGN.md` calls for accent while the research also recommends monochrome.
  This is not a demonstrated functional defect.

Spec: 3 findings, 1 fixed and 2 deferred; the most consequential was reply-state loss.

## Validation and limits

- App and instrumentation APK builds passed.
- All 10 device tests passed in 3.799 seconds, including native LTR/RTL row
  layouts, migration preservation, metadata isolation, reply recreation, and
  selection visibility.
- `python3 tests/check_inbox_database.py` passed with 1,500 conversations and
  30,000 synthetic messages, including migration preservation and recycled-hit
  exclusion.
- `git diff --check` passed. No temporary debug logging was added.
- `./gradlew :app:lintFossDebug` passed after online tooling resolution in
  4m49s: 89 warnings remain, with 5 errors and 208 warnings suppressed by the
  existing baseline. The baseline was not modified.
- Debug APKs were installed on the connected device. No message-send test ran.
- Old metadata did not record provider type. Numeric legacy keys remain SMS
  keys; old MMS stars/reactions may need to be added again. No database schema
  migration was introduced by these fixes. The pre-existing Room cache still
  uses a numeric primary key, so this is not a redesign of cache identity.
