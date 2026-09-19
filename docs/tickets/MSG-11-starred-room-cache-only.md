# MSG-11: Starred list only sees Room-cached rows

- **Severity:** Medium
- **Status:** Open
- **Area:** StarredMessagesActivity

## Problem
`loadStarredMessages` uses `messagesDB.getMessagesWithIds` then star prefs. Stars for threads never bulk-cached never appear.

## Acceptance
- [ ] Missing ids resolved from Telephony (SMS/MMS URIs) **or** cache filled on star
- [ ] Star in uncached thread → appears in Starred

## Fix direction
Telephony fallback or ensure cache write on star.
