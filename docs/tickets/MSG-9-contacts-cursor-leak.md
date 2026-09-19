# MSG-9: Contacts cursor not closed in `getBlockReason`

- **Severity:** Medium
- **Status:** Open
- **Area:** SmsReceiver / MmsReceiver

## Problem
`getMyContactsCursor(...)` passed to `existsSync` without `.use {}` (unlike `handleMessageSync`). Same pattern in `MmsReceiver.isAddressBlocked`. Cursor leak on every incoming SMS while filters run.

## Acceptance
- [ ] `cursor.use { … }` around lookups in both receivers
- [ ] No leak under repeated incoming SMS (profiler / lint if available)

## Fix direction
Match the `.use {}` pattern already used later in `SmsReceiver`.
