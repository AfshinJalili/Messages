# AFSH Messages

A private SMS/MMS messaging client for Android.

## Language

### Events & Attention

**Messaging Event**:
A messaging-domain occurrence such as an incoming message, a send outcome, a delivery update, or a read-state change.
_Avoid_: App event, refresh signal, UI event

**App Presence**:
Where the user's attention currently is — backgrounded, locked, viewing a specific conversation, or elsewhere inside the app.
_Avoid_: App state, foreground flag, visible screen

**Attention Policy**:
The rule that decides how a Messaging Event interrupts or cues the user given current App Presence and conversation state (such as spam or muted).
_Avoid_: Notification guard, suppression logic
