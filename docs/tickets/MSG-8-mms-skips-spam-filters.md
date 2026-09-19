# MSG-8: MMS path ignores rule/AI spam divert

- **Severity:** Medium
- **Status:** Open
- **Area:** MmsReceiver

## Problem
`MmsReceiver` only checks blocked numbers / unknown / keywords — no `RuleBasedFilter` / `AiNotificationFilter` / `blocked_messages` insert. Marketing MMS bypasses Spam folder.

## Acceptance
- [ ] Shared filter gate for SMS+MMS, **or** settings explicitly document MMS out of scope

## Fix direction
Extract shared gate; keep behavior consistent with MSG-3 decision.
