# MSG-1: Build broken — unresolved `R` in adapters

- **Severity:** Critical (ship-blocker)
- **Status:** Done (verified 2026-09-12 — compile succeeds)
- **Area:** Adapters / build

## Problem
Three adapters call `resources.designFloat(R.dimen…)` without `import org.fossify.messages.R`.  
`./gradlew :app:compileFossDebugKotlin --offline` fails with `Unresolved reference 'R'` (×8).

## Evidence
- `BaseConversationsAdapter.kt` (~164, 169, 179, 187)
- `ContactsAdapter.kt` (~70)
- `SearchResultsAdapter.kt` (~74, 80, 86)

## Acceptance
- [ ] Missing `org.fossify.messages.R` imports added (or fully qualified)
- [ ] `:app:compileFossDebugKotlin` succeeds

## Fix direction
Add `import org.fossify.messages.R` in each adapter (or qualify `org.fossify.messages.R.dimen…`).
