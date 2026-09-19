# MSG-10: AI verdict parsing uses `contains("BLOCK")`

- **Severity:** Medium
- **Status:** Open
- **Area:** AiNotificationFilter

## Problem
`text.trim().uppercase().contains("BLOCK")` false-positives on longer replies (e.g. “DO NOT BLOCK”).

## Acceptance
- [ ] Accept only exact first-token `BLOCK` / `ALLOW` (or structured response)
- [ ] Anything else → fail open

## Fix direction
Strict token parse; never substring match.
