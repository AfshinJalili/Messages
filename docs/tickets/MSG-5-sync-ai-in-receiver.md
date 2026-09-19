# MSG-5: Synchronous AI HTTP inside `SmsReceiver` before store

- **Severity:** High
- **Status:** Open
- **Area:** SmsReceiver / AiNotificationFilter

## Problem
Classify (3s connect + 4s read) runs on receive path inside `goAsync` before Telephony/local store completes. Slow network or process death can drop the message.

## Evidence
`AiNotificationFilter` timeouts; called from `getBlockReason` before write; comments note ~10s `goAsync` risk.

## Acceptance
- [ ] Message persisted before any network classify
- [ ] Classify is async for notify/spam only, or hard timeout ≪ goAsync with fail-open
- [ ] Kill mid-classify cannot lose the only copy

## Fix direction
Persist first; classify off the critical path (ties to MSG-3).
