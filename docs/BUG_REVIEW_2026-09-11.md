# Bug & smell review — Fossify Messages (local changes)

Scope: uncommitted local changes on `main` (behind `origin/main` by ~66; upstream lag not treated as local bugs). Reviewed helpers/activities for spam, swipe, AI filter, starring, Room, receivers, and related adapters. `./gradlew :app:compileFossDebugKotlin --offline` was run and **failed**.

## Summary

The local tree does not currently compile: three adapters reference `R.dimen.*` without importing `org.fossify.messages.R`. Beyond that, the highest-risk area is the SMS receive path: rule/AI “filters” divert messages out of the Telephony provider into a local `blocked_messages` table, API keys and message bodies leave the device in clear SharedPreferences / HTTPS POST form, and `restoreBlockedMessage` can delete the only copy after a failed insert. Swipe undo and Room migrations look mostly deliberate; remaining issues are ID/identity collisions, allow-list matching, and MMS bypassing the new spam path.

## Critical (bugs that can crash, lose data, wrong SMS behavior, security)

### 1. Build broken: unresolved `R` in inbox/search/contact adapters
- **Severity:** Critical (ship-blocker)
- **Evidence:** `BaseConversationsAdapter.kt:164,169,179,187`, `ContactsAdapter.kt:70`, `SearchResultsAdapter.kt:74,80,86` — `resources.designFloat(R.dimen…)` with no `import org.fossify.messages.R`. Compile log: `Unresolved reference 'R'` ×8; `:app:compileFossDebugKotlin FAILED`.
- **Why it matters:** Debug Kotlin does not build; prior review’s “APK builds passed” state is regressed.
- **Suggested fix direction:** Add the missing `org.fossify.messages.R` imports (or fully qualify `org.fossify.messages.R.dimen…`).

### 2. `restoreBlockedMessage` deletes local copy even when Telephony insert fails
- **Severity:** Critical (data loss)
- **Evidence:** `Context.kt` `restoreBlockedMessage` — `insertNewSMS(...)` return value ignored; `insertNewSMS` returns `0L` on exception; then unconditionally `blockedMessagesDB.delete(blockedMessage.id)`.
- **Why it matters:** Restore/Allow from Spam can permanently drop the only stored copy (default-SMS insert can fail for permission/provider issues).
- **Suggested fix direction:** Only delete from `blocked_messages` after a non-zero insert URI/id (and ideally after conversation refresh succeeds); surface failure to the user.

### 3. AI/rule filters divert SMS from the system inbox (not “notification only”)
- **Severity:** Critical (wrong SMS behavior / silent loss relative to Telephony)
- **Evidence:** `SmsReceiver.getBlockReason` → on any reason, insert `BlockedMessage` and `return` without `insertNewSMS`. Settings copy still frames AI as notification-oriented (`ai_filter_instructions` = “What to notify me about”; class `AiNotificationFilter`), while `ai_filter_enable` admits filtering. Compare `NotificationHelper` mute, which only skips notify.
- **Why it matters:** As default SMS app, skipping the provider means the message exists only in app DB. False-positive rule/AI blocks hide OTP/bank SMS from the system inbox, backups, and other readers until manual restore (see #2).
- **Suggested fix direction:** Either (a) always persist to Telephony then suppress notification / move to spam metadata, or (b) rename + hard UX that this is full divert; never block on AI timeout/uncertain verdict (fail open is already partial).

### 4. SMS bodies + API key leave the device; key stored in plaintext prefs
- **Severity:** Critical (security / privacy)
- **Evidence:** `AndroidManifest.xml` adds `INTERNET`; `Config.aiApiKey` in ordinary `SharedPreferences`; `AiNotificationFilter.classify` POSTs `From: $sender\nMessage: $body` with `Authorization: Bearer $apiKey` to configurable `aiBaseUrl` (defaults to OpenAI). Settings dialog is a normal `AppCompatEditText` (not password/masked input while editing). Custom base URL can be `http://…` with no cleartext policy guard.
- **Why it matters:** Default-SMS traffic is highly sensitive; a compromised backup, debuggable build, or malicious base URL exfiltrates OTPs and conversations.
- **Suggested fix direction:** Encrypt-at-rest or AcccountManager/Keystore for the key; password inputType; pin HTTPS-only hosts; consider on-device-only filtering; document that enabling AI uploads unknown-sender SMS.

## High

### 5. Synchronous AI HTTP inside `SmsReceiver` `goAsync` window before store
- **Severity:** High
- **Evidence:** `AiNotificationFilter` connect 3s + read 4s; called from `getBlockReason` on the receive background thread before any Telephony write; comments admit the ~10s `goAsync` risk.
- **Why it matters:** Slow/offline network delays or kills receive work; combined with divert-on-BLOCK, timing failures interact badly with process death (message never written anywhere if killed mid-classify before local insert completes).
- **Suggested fix direction:** Persist first, classify async for notification/spam folder; or hard timeout ≪ goAsync with fail-open and no network on the critical path.

### 6. `allowedNumbers` uses exact string equality
- **Severity:** High
- **Evidence:** `SmsReceiver.getBlockReason`: `context.config.allowedNumbers.contains(address)`; `addAllowedNumber` stores whatever address the blocked row had.
- **Why it matters:** “Allow sender” can fail for `+98…` vs `0…` vs formatting variants; sender keeps hitting rule/AI divert.
- **Suggested fix direction:** Match with `PhoneNumberUtils.compare` / normalized numbers (same approach as blocked-number checks).

### 7. Resend / jump-to-message still key only on numeric id (SMS vs MMS collision)
- **Severity:** High (regression risk next to the intentional Messaging.kt SMS/MMS split)
- **Evidence:** `ThreadActivity.handleItemClick`: `messages.firstOrNull { it.id == any.messageId }` with `ThreadError(messageId, messageText)` lacking `isMMS`; `jumpToMessage` similarly matches `id` only. `sendMessageCompat` correctly splits `smsIdToResend` / `mmsIdToResend`, but lookup can hand it the wrong `Message`.
- **Why it matters:** Prior local work already proved SMS/MMS id overlap corrupts stars; the same collision can resend/delete the wrong provider row.
- **Suggested fix direction:** Carry `isMMS` on `ThreadError` and all id lookups; prefer `getStableId()`.

## Medium (real smells / maintainability that will bite)

### 8. MMS path ignores rule/AI spam divert
- **Evidence:** `MmsReceiver` only `isNumberBlocked` / `blockUnknownNumbers` / keyword `isContentBlocked`. No `RuleBasedFilter` / `AiNotificationFilter` / `blocked_messages` insert.
- **Why it matters:** Marketing MMS bypasses the new Spam folder; behavior inconsistent with SMS.
- **Suggested fix direction:** Shared filter gate for SMS+MMS, or explicitly document MMS as out of scope in settings.

### 9. Contacts cursor not closed in `getBlockReason`
- **Evidence:** `SmsReceiver.getBlockReason` opens `getMyContactsCursor(...)` and passes it to `existsSync` without `.use {}` (unlike `handleMessageSync` later in the same file). Same pattern remains in `MmsReceiver.isAddressBlocked`.
- **Why it matters:** Cursor leak on every incoming SMS while filters run.
- **Suggested fix direction:** `cursor.use { … }` around the lookup.

### 10. AI verdict parsing uses `contains("BLOCK")`
- **Evidence:** `AiNotificationFilter.classify` → `text.trim().uppercase().contains("BLOCK")`.
- **Why it matters:** Any longer model reply that mentions the word (e.g. “DO NOT BLOCK”) falsely diverts; brittle vs exact token match.
- **Suggested fix direction:** Accept only exact `BLOCK` / `ALLOW` (first token), else fail open.

### 11. Starred list only sees Room-cached rows
- **Evidence:** `StarredMessagesActivity.loadStarredMessages` → `messagesDB.getMessagesWithIds` then `isMessageStarred`. Stars live in prefs; uncached threads never appear.
- **Why it matters:** User stars a message in a thread that was never bulk-cached → empty/missing Starred entries.
- **Suggested fix direction:** Resolve missing ids from Telephony (SMS/MMS URIs) or ensure cache fill on star.

### 12. `Message.areItemsTheSame` / `getSelectionKey` ignore provider type
- **Evidence:** `Message.kt` `areItemsTheSame` compares `id` only; `getSelectionKey` hashes `id` only; stable id already encodes MMS bit for list identity elsewhere.
- **Why it matters:** DiffUtil/selection can conflate SMS+MMS with the same numeric id (same class of bug as stars, partially fixed elsewhere).
- **Suggested fix direction:** Include `isMMS` in equality and selection keys.

### 13. Rule filter easy false positives
- **Evidence:** `RuleBasedFilter`: any `unsubscribe`/`لغو` → `BLOCK`; marketing keywords + URL/alpha sender → `BLOCK`. Runs for all non-contact unknown senders when enabled.
- **Why it matters:** Legitimate service SMS with “unsubscribe” land in Spam (then face restore bug #2).
- **Suggested fix direction:** Narrow rules; require multiple signals; keep fail-open defaults (already off by default — good).

## Low / notes (DESIGN drift, nits — keep short)

- **DESIGN.md swipe colors** (`#00796B` / `#B3261E`) vs `colors.xml` (`brand_cobalt` / `design_error`) — visual contract drift, not functional.
- **Unread bold:** DESIGN says bold the name only; `BaseConversationsAdapter` still bolds address via typeface style while body stays normal — closer than before, OK.
- **`applyCobaltDefaults()`** in `App.onCreate` rewrites primary/accent once — surprising theme mutation for existing installs that still had Commons green.
- **AI slop / casts:** `as ArrayList<…>` after `toMutableList()` in blocked activities; broad `catch (e: Exception) { false }` in AI filter (fail-open is intentional); “ponytail” comments are noisy but not harmful.
- **SharedPreferences `StringSet`:** getters return live sets then `.plus`/`.minus` (copy-on-write) — OK pattern; still worth copying defensively if anyone mutates in place later.

## Gaps (what you couldn't verify)

- No device/UI run; instrumentation tests not re-executed this pass (prior `CODE_REVIEW_2026-09-10.md` claims they passed then).
- Did not audit full `ThreadActivity` / layout XML for every NPE; reply quote persistence covered by existing test intent.
- Did not verify Room migration on a real v10→v12 device DB beyond reading `MIGRATION_10_11` / `MIGRATION_11_12` (look structurally correct: `blocked_messages` table + messages index matching `@Entity` index).
- Did not review icon/graphics churn.
- Upstream 66 commits not diffed for conflict with local behavior.
- Network/AI behavior not live-tested against a real API.

