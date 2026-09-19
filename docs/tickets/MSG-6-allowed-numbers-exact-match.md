# MSG-6: `allowedNumbers` uses exact string equality

- **Severity:** High
- **Status:** Open
- **Area:** SmsReceiver / allow-list

## Problem
`allowedNumbers.contains(address)` is exact string match. “Allow sender” fails across `+98…` vs `0…` / formatting variants; sender keeps hitting rule/AI divert.

## Evidence
`SmsReceiver.getBlockReason`; `addAllowedNumber` stores raw blocked-row address.

## Acceptance
- [ ] Allow-list match uses `PhoneNumberUtils.compare` (or same normalization as blocked numbers)
- [ ] Manual: allow `+98…`, receive `0…` variant → not diverted

## Fix direction
Normalize / compare like existing blocked-number checks.
