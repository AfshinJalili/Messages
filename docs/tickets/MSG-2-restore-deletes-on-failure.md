# MSG-2: `restoreBlockedMessage` deletes local copy after failed Telephony insert

- **Severity:** Critical (data loss)
- **Status:** Open
- **Area:** Spam / restore

## Problem
`restoreBlockedMessage` ignores `insertNewSMS` return value (`0L` on exception) and always `blockedMessagesDB.delete(...)`. Failed restore permanently drops the only stored copy.

## Evidence
`Context.kt` — `restoreBlockedMessage`: insert then unconditional delete.

## Acceptance
- [ ] Delete from `blocked_messages` only after successful non-zero insert
- [ ] User sees a clear failure if restore fails
- [ ] Manual test: force insert failure → message still in Spam

## Fix direction
Gate delete on successful insert URI/id; optionally refresh conversation only on success.
