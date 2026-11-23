---
status: accepted
---

# Route Messaging Events through one SharedFlow owner and pure Attention Policy

Legacy message receivers called `NotificationHelper` directly without knowing what screen was open, while `ThreadActivity` raced to cancel notifications via untyped GreenRobot `EventBus` signals that also forced unrelated open threads to reload. We retire `EventBus` in favor of a single Kotlin `SharedFlow`-backed Messaging Events owner paired with an `AppPresence` tracker and a pure `AttentionPolicy` that maps `(MessagingEvent, AppPresence, conversation flags)` to system notifications, in-thread cues, or suppression.

## Considered Options

- **Keep `EventBus` and add payloads / inline guards**: Keeps reflection-based third-party plumbing and leaves notification policy scattered across receivers.
- **Pure Room / ContentObserver observation everywhere**: Works for the Inbox list, but cannot represent transient attention events (incoming message alerts, send failures with carrier error codes) or active-conversation presence.

## Consequences

- Message receivers and activities must publish `MessagingEvent`s instead of invoking system message/failure notifications or `ProcessLifecycleOwner` checks directly (enforced by CI host checks).
- `AppPresence` must account for Activity Embedding split view (tablet list-detail where Inbox and a conversation are both resumed), App Lock, device keyguard/screen-off, and Recycle Bin mode.
- Message-path receivers remain responsible for targeted Room writes without triggering full Telephony reconciles.
