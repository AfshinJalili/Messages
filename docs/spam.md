# Spam: keep every SMS in Telephony, hide spam in the app

## Goal

Every incoming SMS is saved in Telephony like any other message, including spam. The app hides spam from the inbox and collects it on one Spam screen where the user can check it, restore it or delete it. A wrong spam decision must never lose a message or hide it permanently. Only an explicit delete removes a message from Telephony.

SMS only. MMS keeps its current behavior (see Out of scope).

## Current state

- `SmsReceiver` writes spam to Telephony, Room `messages` and Room `conversations`, then writes a second copy to `blocked_messages`. Spam threads appear in the inbox because nothing hides them. The Spam screen reads the copy.
- `restoreBlockedMessage` puts the copy back into Telephony. A 60-second body/date match prevents a duplicate insert.
- `getConversations` and `getMessages` skip threads and messages from numbers on the system block list (`BlockedNumberContract`). Android drops SMS from those numbers before the app receives them.
- `BlockedMessagesThreadActivity` renders the copies as fake `Message` objects (`BlockedMessage.toMessage`).

## Design

### Data

New Room table `spam_messages`:

| Column | Type | Meaning |
|---|---|---|
| `id` | INTEGER PRIMARY KEY | Telephony `Sms._ID` |
| `thread_id` | INTEGER NOT NULL, indexed | Telephony thread |
| `reason` | INTEGER NOT NULL | `BLOCK_REASON_*` |
| `marked_ts` | INTEGER NOT NULL | when it was marked, millis |

The marker is a separate table rather than a `spam` column on `messages`, because:

- `messagesDB.insertOrUpdate` and `insertMessages` use `REPLACE`, and 7 call sites write `Message` objects built from Telephony cursors. A column would reset to 0 on every thread load.
- `messages` is a partial cache. It is fully filled only on the first run, and a marker must exist even when no cached row does.
- `clearAllMessagesIfNeeded` wipes `messages`.

`recycle_bin_messages` uses a separate table for the same reasons.

New column `conversations.message_count INTEGER NOT NULL DEFAULT 0`. `getConversations` fills it from `Threads.MESSAGE_COUNT`, which that query already uses in its selection. It is provider data, so reconciler overwrites are correct. Add it to `Conversation.areContentsTheSame` so the reconciler writes changes.

### Rules

All hiding is derived at read time. Nothing on the `conversations` row records that a thread is hidden, so the reconciler can overwrite rows freely without making spam visible again.

- A message is spam if its `id` is in `spam_messages` and it is not MMS.
- A thread is hidden from the inbox if `message_count > 0 AND (spam markers in thread) >= message_count`. When the result is unclear, the thread stays visible: a count that has not been filled in, an MMS in the thread, or a new real message all keep it visible.
- A thread with both spam and real messages stays in the inbox. Its snippet and date come from the newest non-spam, non-recycled Room message. When Room has no such message, the provider values are used.
- Per-thread unread counts exclude spam. The Spam screen counts unread spam separately.
- Search still returns spam messages, with a spam label, so a lost message can always be found.

### Write paths

1. **`SmsReceiver.handleMessageSync`**: classify (already done) → `insertNewSMS` → if spam, insert marker `(id, threadId, reason)` → upsert conversation → `messagesDB.insertOrUpdate`. The marker goes in before the conversation upsert so the thread never briefly appears in the inbox. Spam still gets no notification. Remove the `blocked_messages` insert.
2. **`deleteMessage` and `deleteConversation`** also delete markers. Emptying the recycle bin goes through `deleteMessage`, so no separate change is needed there.
3. **`InboxReconciler`** removes orphan markers: one `Sms._ID IN (...)` query per reconcile, in chunks of 900 arguments, deleting markers whose message is gone. Without this, spam deleted by another app would leave markers behind. The marker count would then exceed the real spam count and could hide a thread that has real messages.

### Read paths

- **`ConversationsDao`** non-archived and archived queries: exclude spam in the `new_snippet` subquery, add a matching `new_date` subquery, and add the hidden-thread `WHERE` clause. `ConversationWithSnippetOverride` gains `new_date`.
- **`getUnreadCountsByThread`**: project `Sms._ID` and skip spam IDs, loading the marker set once per call. Add `getUnreadSpamCounts(): Map<Long, Int>` for the Spam screen and its badge.
- **`markThreadMessagesRead`**: exclude spam IDs, so marking an inbox thread read does not clear the Spam badge. `markVisibleMessagesRead` needs no change because collapsed spam is not a visible `Message` item.
- **`MessagesDao`**: add a spam-marker join helper, following the existing recycle-bin join pattern.

### Classifier

- Move the `allowedNumbers` check above the keyword check in `IncomingSpamClassifier.silenceReason`, so "Never spam from this sender" always applies. Today a keyword match wins over the allowlist.
- **"Block number" in `ThreadActivity`** becomes app-level: add the number to a new `config.spamNumbers` set, which the classifier maps to `BLOCK_REASON_NUMBER`. Stop writing to `BlockedNumberContract`, because Android drops SMS from those numbers and they can never be kept. Numbers already on the system block list remain as they are; the user manages them in system settings.

## UI

### Normal thread (`ThreadActivity`)

- `getThreadItems` merges each run of consecutive spam messages into a new `ThreadItem.ThreadSpamGroup(messages, expanded)`. When collapsed, the row reads "2 messages marked as spam" with a chevron, and tapping toggles it. The activity stores expanded state in a set keyed by the group's first message ID, so it survives reloads.
- Expanded spam messages render as normal received bubbles with a "Spam · <reason>" label.
- Message action bar: a new "Not spam" action shows when every selected message is spam. Delete works as today, including the recycle bin.
- Overflow menu: a new "Never spam from this sender" item shows when the thread contains spam and has one participant.
- The new `OPEN_SPAM` intent extra, sent from the Spam screen, makes all groups start expanded and scrolls to the first unread spam message, or to the newest one if none are unread.

### Spam screen (rework `BlockedMessagesActivity`)

- One row per thread with at least one marker, including threads that also have real messages. Room keeps `conversations` rows for spam-only threads; the inbox query just filters them out. Title and photo come from that row.
- The row shows the newest spam message's text and date, the unread spam count as a badge, the total spam count, and a chip with the newest marker's reason.
- Tapping a row opens `ThreadActivity` with `OPEN_SPAM`.
- Row actions:
  - **Not spam**: delete the thread's markers.
  - **Never spam**: `config.addAllowedNumber` and delete the thread's markers.
  - **Delete**: delete only the thread's spam messages from Telephony, with confirmation and the existing `UndoDeletion` flow. Real messages in the same thread are untouched.
- The "Clear spam" menu item deletes every spam message from Telephony, with confirmation and undo.
- The main-menu Spam entry shows the total unread spam count.

`BlockedMessagesThread` changes from grouping by address to grouping by `threadId`.

### Removed

`BlockedMessagesThreadActivity`, the `BlockedMessage` entity and `BlockedMessagesDao`, `BlockedMessage.toMessage`, `restoreBlockedMessage`, and the `blocked_thread_menu` resource.

## Migration and backfill

**`MIGRATION_12_13`** (plain SQL):

```sql
CREATE TABLE IF NOT EXISTS `spam_messages` (`id` INTEGER NOT NULL PRIMARY KEY, `thread_id` INTEGER NOT NULL, `reason` INTEGER NOT NULL, `marked_ts` INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS `index_spam_messages_thread_id` ON `spam_messages` (`thread_id`);
ALTER TABLE conversations ADD COLUMN message_count INTEGER NOT NULL DEFAULT 0;
```

Remove `BlockedMessage` from `@Database(entities)`, but do not drop `blocked_messages` in the migration. Room checks only the tables of its own entities, so an extra table is ignored.

**Backfill**: a one-time background job that runs until `config.spamBackfillDone` is set, then triggers a reconcile.

1. Read `blocked_messages` with a raw query.
2. For each row, find the matching Telephony SMS: same thread, same body, date within 60 seconds. This is the existing `restoreBlockedMessage` match. If none exists, insert one with `insertNewSMS` (inbox, unread).
3. Insert the marker for that SMS.
4. After every row succeeds, run `DROP TABLE blocked_messages` and set the flag. If any row fails, keep the table and retry on the next launch. The job can safely run more than once: the match in step 2 prevents duplicates, and the marker primary key prevents double marking.

## Phases

Each phase builds and can be checked on its own.

1. **Data and hiding**: migration, entity and DAO, the `message_count` column, receiver write order, marker cleanup on delete, DAO hiding and snippet/date queries. After this phase, spam no longer appears in the inbox.
2. **Unread**: spam excluded from thread counts, `getUnreadSpamCounts`, and `markThreadMessagesRead`.
3. **Spam screen**: rework onto markers and add the badge. Temporarily open threads in plain `ThreadActivity`.
4. **Thread UI**: `ThreadSpamGroup`, spam labels, "Not spam", "Never spam", and `OPEN_SPAM`.
5. **Backfill and cleanup**: migrate existing `blocked_messages` rows and delete the legacy code.
6. **Classifier**: move the allowlist check first, and make block-number app-level.
7. **Reconciler**: remove orphan markers.

### Phase 1 status

Done. Differences from the design above:

- `SmsReceiver` still writes the `blocked_messages` copy, so the current Spam screen keeps working until phase 3. `restoreBlockedMessage` now removes the marker of the Telephony SMS it matches, so "Restore" and "Allow" make the message visible again. Deleting a copy there leaves the SMS hidden; the phase 3 screen will show it.
- `InboxReconciler` reads raw rows through `ConversationsDao.getAll()`. The inbox queries now hide spam-only threads, and reading through them would re-insert hidden threads on every run and never remove them when Telephony deletes them.
- The archive list, including "Empty archive", also hides spam-only threads.
- The Room `date` replaces the provider date only in threads that have markers, so drafts and scheduled dates are unchanged elsewhere.

`SpamChecks` (6 tests) and `InboxChecks`, `ReadStateChecks` and `DeletionChecks` were run on 24 September 2026: 19 of 20 passed. `completedReadUpdatesInboxFromRoom` fails at `HEAD` too, because commit `05e07fec` removed every `@Subscribe` method from `MainActivity`. The receiver path was not exercised on a device; that needs a real SMS from a second phone.

### Phase 2 status

Done. Differences from the design above:

- `markThreadMessagesUnread` also leaves spam SMS alone. Otherwise marking a thread unread would raise the Spam badge.
- The Room `messages.read` flag follows the same rule: `markThreadRead` skips SMS with markers.
- Unread counts for the Spam screen come from `getUnreadSpamCounts()`, which nothing calls until phase 3.

`SpamChecks` gained `threadReadLeavesSpamUnread` (Room fixtures) and `unreadSpamMovesFromThreadCountToSpamCount`. The second test only reads Telephony: it puts a temporary Room marker on an existing unread SMS and removes it afterwards. It skips itself when the device has no unread SMS. On 24 September 2026, 21 of 22 passed, with the same `completedReadUpdatesInboxFromRoom` failure as in phase 1. The Telephony writes in `markThreadMessagesRead` and `markThreadMessagesUnread` were not exercised, because the tests do not write to Telephony.

### Phase 3 status

Done. Differences from the design above:

- `getSpamThreads()` reads the marked SMS from Telephony by id instead of from Room `messages`, so the backfill in phase 5 does not have to fill the cache. It also skips orphan markers until phase 7 removes them.
- The chip shows the reason and the total spam count ("Keyword · 3"). The badge shows unread spam.
- After deleting spam, an immediate reconcile removes the conversation rows of threads that became empty.
- The main-screen Spam button shows the unread spam count ("Spam · 2"). It updates on resume and whenever the inbox query is invalidated, which includes marker changes.
- `BlockedMessagesThreadActivity`, `restoreBlockedMessage` and the `blocked_messages` copy in `SmsReceiver` are now unused on this screen. They are removed in phase 5. Spam received before phase 1 has no marker and still shows in the inbox until the backfill.
- The spam filter setting no longer says messages stay in the inbox.

`SpamChecks` gained `spamScreenListsMarkedSmsAndSkipsOrphans`, which reads Telephony only. On 24 September 2026, 22 of 23 passed, with the same `completedReadUpdatesInboxFromRoom` failure. On the device, the Spam screen listed two real spam SMS received that day through the phase 1 receiver path, and the button read "Spam · 2". The row actions (Not spam, Allow sender, Delete, Clear all) were not tapped, because they change real messages.

### Phase 4 status

Done. Differences from the design above:

- `ThreadSpamGroup` holds message ids rather than messages: `(key, messageIds, expanded)`. The key is the first message id. The list is built by `buildThreadItems` in `ThreadItems.kt`, which `ThreadActivity` calls, so the grouping can be tested without Telephony.
- Opening a group marks its spam read. Thread-wide read state, the unread separator and the initial unread scroll all ignore spam. `OPEN_SPAM` marks all spam in the thread read after scrolling to it. In `OPEN_SPAM` mode the expanded-group set holds collapsed groups instead, so every group starts open and can still be collapsed.
- Jumping to a search match inside a collapsed group opens the group first.
- The spam label goes in the message footer: "Spam · Keyword · 12:44".
- "Never spam from this sender" uses the existing "Always allow this sender" string. It adds each spam sender address in the thread to the allowlist, then removes the thread's markers.
- The view-type bits went from 3 to 4 for `THREAD_SPAM_GROUP = 8`.

`ThreadSpamGroupChecks` (3 tests) covers grouping, expansion, the unread separator and hidden messages. On 24 September 2026, 29 of 30 passed across `ThreadSpamGroupChecks`, `SpamChecks`, `InboxChecks`, `ReadStateChecks`, `DeletionChecks` and `MessageActionChecks`, with the same `completedReadUpdatesInboxFromRoom` failure. The thread UI was not opened on the device, because opening a thread marks real messages read.

### Phase 5 status

Done. Differences from the design above:

- Removing the `BlockedMessage` entity changes the Room schema, so the database is now version 14. `MIGRATION_13_14` does nothing and keeps `blocked_messages` for the backfill.
- There is no `spamBackfillDone` flag. `SpamBackfill.run` (started from `MainActivity.initMessenger`) checks whether the table exists, removes each row once it is handled, and drops the table when no row failed. A process-wide guard stops two runs from overlapping.
- The match uses `getMessages(threadId, dateFrom = date + 61 s)`, so old rows are found; the old `restoreBlockedMessage` only searched the newest 50 messages. It matches SMS and MMS. A row that matches an MMS gets no marker and is dropped, because `MmsReceiver` also wrote copies there and the MMS is already in Telephony.
- Rows from senders on the allowlist are dropped without a marker.
- `MmsReceiver` no longer writes copies. MMS spam is only silenced and stays in the inbox.
- `InboxChecks` and `SpamChecks` read `blocked_messages` with raw queries. `SpamChecks.backfillMatchesTheStoredSmsInsteadOfInsertingACopy` is read-only.

On 24 September 2026, before the backfill ran on the device, a read-only dry run matched 45 of 122 rows to existing SMS; 77 had no SMS. The user chose to run it with the plan's unread inserts. Afterwards: database version 14, `blocked_messages` gone, 122 markers in 60 threads, 77 new unread SMS in Telephony (all marked), every marker pointing at an SMS in the right thread, no duplicate created, and the Spam button read "Spam · 87". 30 of 31 tests passed, with the same `completedReadUpdatesInboxFromRoom` failure.

### Phase 6 status

Done. Differences from the design above:

- The allowlist is checked first, then the new `spamNumbers`, then keywords and the rest.
- Numbers match through `containsNumber` (`Config.kt`), which compares with commons `trimToComparableNumber`: phone numbers by their last 9 digits, sender names such as "HAMRAH_AVAL" exactly. The allowlist used an exact string match before, so "+98912…" did not match "0912…". The backfill uses the same check.
- Allowing and blocking replace each other: `addAllowedNumber` removes the number from `spamNumbers`, and `addSpamNumber` removes it from `allowedNumbers`. "Always allow this sender" is therefore also the unblock action. In a one-person thread it shows when the sender is blocked, and "Block number" is then hidden. Commons has no "Unblock number" string.
- Blocking from the inbox selection (`ConversationsAdapter`) is app-level too. The plan only named `ThreadActivity`.
- Blocking only affects new messages. Existing messages are not marked, and the conversation stays in the inbox. `ThreadActivity` no longer closes after blocking, and the inbox no longer removes the row.

`SpamClassifierChecks` (3 tests, throwaway preferences) covers allowlist over keyword, blocking across number formats, unblocking and re-blocking, and exact sender-name matching. On 24 September 2026, 33 of 34 passed, with the same `completedReadUpdatesInboxFromRoom` failure. Lint has one new warning, 106 in total: `UseKtx` on the `spamNumbers` setter. The setter is written like every other setter in `Config`.

### Phase 7 status

Done. Differences from the design above:

- `Context.removeOrphanSpamMarkers()` runs one `Sms._ID IN (...)` query with the ids inlined, so there is no 999-argument limit and no chunking.
- It fails safe: when the query returns no cursor it removes nothing, so a failed read cannot wipe every marker. `InboxReconciler` calls it inside its own try/catch, so a failure here does not fail the reconcile; it is retried next time.
- The receiver inserts the SMS before the marker, so a marker read here always has its SMS already in Telephony, and a concurrent receive cannot lose its marker.

`SpamChecks.orphanMarkersAreRemovedAndRealOnesKept` only reads Telephony: it checks that a marker without an SMS is removed and every other marker stays. On 24 September 2026, 34 of 35 passed, with the same `completedReadUpdatesInboxFromRoom` failure. The first run had 5 UI failures because the phone was locked and dozing; they passed after waking it. After a reconcile on the device, all 122 real markers in 60 threads were still there. Lint unchanged at 106.

## Verification

Add `SpamChecks.kt` next to `InboxChecks.kt`. Insert SMS through `insertNewSMS` and markers through the DAO.

- A spam SMS is present in Telephony, absent from the inbox query, and present on the Spam screen.
- In a thread with both kinds, the thread stays in the inbox, the snippet and date come from the real message, and the unread count excludes spam.
- When `message_count` is 0, the thread shows. When an MMS is in a spam-only SMS thread, the thread shows.
- "Not spam" makes the thread visible. "Never spam" adds the number to the allowlist, removes the markers, and the next SMS from that sender, even one with a blocked keyword, is not spam.
- The reconciler removes a marker whose SMS was deleted directly from Telephony, and the thread becomes visible.
- Deleting from the Spam screen removes the message from Telephony and removes the marker. Real messages in the same thread are unaffected.
- Reconciler overwrites of `conversations` do not make a hidden thread visible.
- `migration12to13`: build the v12 schema from `12.json` using the `migration11to12` pattern, add a `blocked_messages` row, run the migration and backfill, and check for exactly one matching Telephony SMS with a marker. A second backfill run changes nothing, and the table is gone afterwards.
- Update `migration11to12`, which reads `BlockedMessagesDao`.

```sh
adb shell am instrument --user 0 -w -e class org.fossify.messages.SpamChecks org.fossify.messages.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Also run `InboxChecks`, `ReadStateChecks` and `DeletionChecks`. On a device, text the phone from a second phone using a blocked keyword and check that it gets no notification, does not appear in the inbox, appears on the Spam screen, and appears in Telephony (visible to another SMS app).

## Out of scope

- **MMS.** The MMS library (`MmsReceivedReceiver`) deletes an MMS before saving it when `isAddressBlocked` or `isContentBlocked` returns true, and `MmsReceiver` returns true for blocked numbers, keyword matches and unknown numbers. Those MMS are lost. This plan does not change that.
- **Manual "Mark as spam"** on a normal message. Add it when users need it; the marker table already supports it.
- **Re-classifying after reinstall.** Clearing app data loses the markers, so old spam shows in the inbox as normal messages. This is the intended failure direction. Re-running the classifier during reconcile would fix it if it becomes a problem.
- **SMS lost when `insertNewSMS` fails.** The receiver saves nothing today when that insert fails, spam or not. It is a separate issue.
