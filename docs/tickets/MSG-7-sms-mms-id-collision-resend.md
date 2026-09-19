# MSG-7: Resend / jump-to-message key only on numeric id (SMS vs MMS)

- **Severity:** High
- **Status:** Open
- **Area:** ThreadActivity / Message identity

## Problem
`handleItemClick` / `jumpToMessage` match `it.id == messageId` only. `ThreadError` lacks `isMMS`. `sendMessageCompat` already splits sms/mms resend ids, but lookup can hand the wrong `Message` when SMS and MMS share a numeric id.

## Evidence
`ThreadActivity.handleItemClick`, `jumpToMessage`; contrast `Messaging.kt` smsId/mmsId split and `getStableId()`.

## Acceptance
- [ ] `ThreadError` and all id lookups carry `isMMS` (or use `getStableId()`)
- [ ] Resend/delete cannot target the wrong provider row when ids collide

## Fix direction
Thread `isMMS` through error/jump paths; prefer stable ids everywhere (see also MSG-12).
