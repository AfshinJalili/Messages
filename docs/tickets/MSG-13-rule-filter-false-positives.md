# MSG-13: Rule filter easy false positives

- **Severity:** Medium
- **Status:** Open
- **Area:** RuleBasedFilter

## Problem
Any `unsubscribe` / `لغو` → BLOCK; marketing keywords + URL/alpha sender → BLOCK for unknown non-contacts. Legitimate service SMS land in Spam (then hit MSG-2 on restore).

## Acceptance
- [ ] Narrower rules and/or multiple-signal requirement
- [ ] Defaults remain off (fail-open) unless product says otherwise
- [ ] Documented examples of what still blocks

## Fix direction
Tighten heuristics; keep fail-open defaults; coordinate with MSG-2/MSG-3.
