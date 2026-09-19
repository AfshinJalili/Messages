# MSG-3: AI/rule filters divert SMS from the system inbox

- **Severity:** Critical (wrong SMS behavior)
- **Status:** Open
- **Area:** SmsReceiver / spam

## Problem
On any block reason, receiver inserts `BlockedMessage` and returns **without** `insertNewSMS`. Settings copy still sounds notification-oriented (`AiNotificationFilter`, “What to notify me about”), but behavior is full divert from Telephony.

## Evidence
`SmsReceiver.getBlockReason` → insert blocked + early return. Contrast `NotificationHelper` mute (notify-only).

## Acceptance
- [ ] Product decision documented: (a) always persist to Telephony + spam metadata / mute notify, or (b) explicit “full divert” UX
- [ ] Implemented path matches that decision
- [ ] False-positive OTP/bank case: message still recoverable and visible per chosen model
- [ ] AI timeout/uncertain → fail open (no divert)

## Fix direction
Prefer persist-first then classify for notify/spam folder; never block solely on uncertain AI.
