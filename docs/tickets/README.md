# Messages — bug tickets (2026-09-11)

From local-change review: [BUG_REVIEW_2026-09-11.md](../BUG_REVIEW_2026-09-11.md).

| ID | Sev | Title |
|----|-----|-------|
| MSG-1 | Critical | Build broken: unresolved `R` in adapters |
| MSG-2 | Critical | `restoreBlockedMessage` deletes copy after failed Telephony insert |
| MSG-3 | Critical | AI/rule filters divert SMS from system inbox |
| MSG-4 | Critical | SMS bodies + API key leave device; plaintext key |
| MSG-5 | High | Sync AI HTTP inside `SmsReceiver` before store |
| MSG-6 | High | `allowedNumbers` exact string match |
| MSG-7 | High | Resend/jump keys only on numeric id (SMS/MMS collision) |
| MSG-8 | Medium | MMS path ignores rule/AI spam divert |
| MSG-9 | Medium | Contacts cursor leak in `getBlockReason` |
| MSG-10 | Medium | AI verdict `contains("BLOCK")` brittle |
| MSG-11 | Medium | Starred list only sees Room-cached rows |
| MSG-12 | Medium | `Message` DiffUtil/selection ignores `isMMS` |
| MSG-13 | Medium | Rule filter easy false positives |
| MSG-14 | High | Inbox unread badge live update on inbound SMS |
| MSG-15 | High | Telegram-style scroll FAB + unread badge in thread |
| MSG-16 | Medium | Viewport read marking + two-stage scroll-to-unread |

### Audit (2026-09-12)

Verified by **code review + build** and **on-device instrumentation** (Samsung SM-M526BR, Android 13, `org.fossify.messages.debug` default SMS app). See `RuntimeUxTicketChecks` in androidTest.

| ID | Status | Notes |
|----|--------|-------|
| MSG-1 | **Done** | Build compiles; adapters import `org.fossify.messages.R` |
| MSG-2 | Open | `restoreBlockedMessage` always deletes blocked row after `insertNewSMS`, ignores result |
| MSG-3 | Open | Blocked SMS never reaches Telephony inbox |
| MSG-4 | Open | Plaintext API key + body sent off-device |
| MSG-5 | Open | Sync AI HTTP on receive path before store |
| MSG-6 | Open | `allowedNumbers.contains(address)` exact match |
| MSG-7 | Open | Thread error/jump/resend keys omit `isMMS` |
| MSG-8 | Open | `MmsReceiver` skips rule/AI filters |
| MSG-9 | Open | Contact cursor not closed in `getBlockReason` |
| MSG-10 | Open | `contains("BLOCK")` verdict parsing |
| MSG-11 | Open | Starred list Room-only |
| MSG-12 | Partial | `getStableId()` encodes MMS; DiffUtil/selection still id-only |
| MSG-13 | Partial | Heuristics unchanged; defaults off |
| MSG-14 | **Open** | **Device confirmed:** inbound SMS + `refreshConversations()` leaves inbox row `read=true`, no badge (`RuntimeUxTicketChecks.msg14` fails) |
| MSG-15 | **Open** | **Device confirmed:** scroll FAB has no unread badge (`RuntimeUxTicketChecks.msg15` passes as “not implemented yet”) |
| MSG-16 | **Open** | **Device confirmed:** opening thread marks whole thread read (`RuntimeUxTicketChecks.msg16` passes as “not implemented yet”) |

**Done: 1/16.** Thread UX frontier unchanged: **MSG-14, MSG-15** still open.

Suggested order: **MSG-1 → MSG-2 → MSG-3/5 → MSG-4 → MSG-6 → MSG-7 → rest**.

### Thread unread UX (2026-09-12)

**Study first:** [MSG-15-existing-scroll-fab-behavior.md](./MSG-15-existing-scroll-fab-behavior.md) — mandatory before MSG-15/16; documents the FAB that already exists.

Tracer bullets for agents (matt pocock `to-tickets` layout also under `.scratch/thread-unread-ux/issues/`):

| Order | ID | Blocked by | Start when |
|-------|-----|------------|------------|
| 1 | MSG-14 | — | Immediately |
| 2 | MSG-15 | — | Immediately (coordinate read rules with MSG-16) |
| 3 | MSG-16 | MSG-15 | After scroll FAB lands |

**Frontier:** MSG-14 and MSG-15 can run in parallel in separate sessions. MSG-16 waits for MSG-15.
