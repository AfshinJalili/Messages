# MSG-12: `Message` DiffUtil / selection ignores `isMMS`

- **Severity:** Medium
- **Status:** Open
- **Area:** Message model

## Problem
`areItemsTheSame` and `getSelectionKey` use numeric `id` only; stable id already encodes MMS bit elsewhere. DiffUtil/selection can conflate SMS+MMS with same id.

## Acceptance
- [ ] `isMMS` included in equality and selection keys (or use `getStableId()`)
- [ ] Aligns with MSG-7 identity fix

## Fix direction
Same identity model as list stable ids / Messaging split.
