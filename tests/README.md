# Inbox regression checks

- `python3 tests/check_inbox_database.py`: executes the inbox DAO SQL against the
  exported Room schema with 1,500 conversations / 30,000 synthetic messages.
  Rejects full message-table scans, checks recycled-message exclusion, and
  verifies the index migration preserves rows and is idempotent. Also checks
  that Starred and search exclude recycled messages while retaining active hits.
- `./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest`
- `ReadStateChecks` exercises the shared read operation while the inbox refresh is
  busy, then opens and immediately closes a synthetic thread. Both paths must
  clear the inbox's unread state and count within one second. It changes only a
  disposable Room row; it does not insert or send provider messages. Run with
  `adb shell am instrument --user 0 -w -e class org.fossify.messages.ReadStateChecks org.fossify.messages.debug.test/androidx.test.runner.AndroidJUnitRunner`.
- Install both APKs from `app/build/outputs/apk`, then run
  `adb shell am instrument --user 0 -w -e class org.fossify.messages.InboxChecks org.fossify.messages.debug.test/androidx.test.runner.AndroidJUnitRunner`.
  This validates Room's actual 11→12 migration on a separate disposable database,
  preserves seeded drafts/spam, checks conversation state diffs, and measures
  native row layouts at 360dp / 130% text size in LTR and RTL. Metadata tests use
  isolated preferences. Activity checks open the normal inbox and a synthetic
  thread to verify selection visibility;
  they never send messages. Look for `OK (9 tests)`; Android's command exit code
  alone does not indicate whether the assertions passed.

Schema 12 adds only `messages(thread_id, date)`. There are no table rewrites.
Do not roll back by installing a schema-11 APK over migrated app data: the
existing destructive-migration fallback can clear local data. Restore the
pre-update app-data backup together with an older APK if rollback is needed.

Verification on 2026-09-10: builds and device checks passed. Local replay of the
phone's pre-update cache (1,774 conversations / 15,657 messages) took 4.465s
before the index and 0.009s after it. This is SQL timing on the development
machine, not end-to-end phone startup. Full interactive visual/startup validation
requires an unlocked phone. The review rerun on 2026-09-10 resolved the lint
tooling online: `:app:lintFossDebug` passed with 89 warnings and the existing
baseline unchanged. All 10 instrumentation tests passed after the review fixes.

For an optional capture of the actual rendered inbox, run the selection test with
`-e capture_inbox true`. It saves `cache/inbox-screen.png` in the target app's
private storage. This can inspect the app view while the device is locked, but
it does not capture system bars or prove touch interactions. The image contains
real inbox previews; keep it private. Omit the argument for ordinary checks.

## Design checks

- `python3 tests/check_design_tokens.py` rejects literal colors outside the palette
  and inline opacity in app code/layouts.
- `python3 tools/export_icon.py` regenerates icon exports from the launcher vector
  and palette. It requires system Python with PyGObject/Rsvg and Pillow.
- Build/install the normal and test APKs as above, then run
  `adb shell am instrument --user 0 -w -e class org.fossify.messages.DesignChecks,org.fossify.messages.InboxChecks org.fossify.messages.debug.test/androidx.test.runner.AndroidJUnitRunner`.
  The four design checks cover old-green migration, idempotence, explicit theme
  preferences, light/dark contrast and agreement between widget/runtime colors.
  The live activity check expects the installed app to be using the cobalt theme.
  Preference migration tests use a separate disposable preferences file.

Verification on 2026-09-11: all 14 combined tests passed on the connected Samsung
phone. The populated dark inbox was visually checked with the cobalt theme.

The branding/read-state follow-up passed all 15 tests (`ReadStateChecks`,
`DesignChecks`, and `InboxChecks`). The new read-state check reproduced the stale
unread row before the fix, then passed for both a completed background update and
a quick thread open/close. App/test APK builds, SQL checks, design-token checks and
lint passed; lint still reports warnings. The installed compose bubble was also
visually checked on the phone.

## Deletion and reply removal

`DeletionChecks` uses a synthetic conversation and fake provider callbacks. It
checks immediate removal, suppression during refresh, Undo after recreation,
cancellation of the provider action, the five-second delay and single commit,
and hidden checkmarks on selected category chips. It does not delete real SMS.

Run with `adb shell am instrument --user 0 -w -e class org.fossify.messages.DeletionChecks org.fossify.messages.debug.test/androidx.test.runner.AndroidJUnitRunner`.

Reply gesture, quote composer/rendering and selection reply have been removed.
Notification quick-reply is retained. The former quote tests were removed with the feature.

Deletion follow-up verification on 2026-09-11: all 17 combined device tests
passed. App/test APK builds, database checks, design-token checks and lint passed.
Lint reports 105 warnings with the existing baseline unchanged.

## MSG-14–16 unread state and scroll navigation

See [implementation and device matrix](../docs/tickets/MSG-14-16-verification.md).
`RuntimeUxTicketChecks` now checks desired inbox increments, unread FAB behavior,
two-stage navigation, and partial SMS/MMS reads. It inserts disposable local
provider rows, sends nothing, and removes its fixtures afterward. Run it only
with the debug app installed as the default SMS app.

The earlier whole-thread-read-on-open expectation is superseded: opening an
empty thread does not clear its unread state. Visible received bubbles are read
only at scroll idle. The SQL regression script also checks partial reads and
exact unread-count updates.
