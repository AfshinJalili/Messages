# Open Line — Messages redesign

The editable Pendev source is [`Messages-Signal.pen`](../../Messages-Signal.pen). It contains two foundations and component boards plus 54 numbered phone views at 390 × 844. This is a visual product concept for everyday personal SMS/MMS, not an Android implementation.

## Product idea

The inbox acts like an open line: sender, latest meaning, and urgency are visible in one continuous list. Conversations put message text first and leave the composer at the bottom of the phone viewport. The navigation has three destinations: Inbox, Library, and Settings. Library collects saved, archived, spam, and recently deleted conversations so those tasks stay findable without crowding the inbox.

The palette uses mineral paper (`$paper`), deep pine (`$pine`, `$ink`), lime for new or active activity (`$lime`), lilac for media and retained drafts (`$lilac`), and coral for attention (`$coral`). Color also appears with words, shape, or line weight so status does not depend on hue alone. Sender initials identify contacts when a photo is unavailable.

## Screen map

| Area | Views in the canvas |
| --- | --- |
| Inbox and finding people | 01 everyday inbox, 02 unread, 03 search entry, 04 results, 05 new recipient, 23 delete with Undo, 34 empty inbox, 41 multi-select, 42 invalid number, 53 no search matches |
| Conversations and composing | 06 personal, 07 media, 08 group, 09 details, 10 selected message, 11 attachments, 12 schedule, 13 message details, 14 contact card, 15 thread search, 16 new group, 35 failed send, 36 dark theme, 37 Persian RTL, 39 rename, 40 thread actions, 43 delete confirmation, 44 selectable text, 45 media viewer, 47 scheduled edit, 52 jump to new messages, 54 saved draft |
| Library and safety | 17 Library, 18 Starred, 19 Archive, 20 Spam review, 21 block rules, 22 Recycle Bin, 38 blocked numbers, 50 add blocked phrase, 51 move block rules |
| Settings and setup | 24 overview, 25 appearance, 26 writing and sending, 27 notifications, 28 privacy and lock, 29 backup and restore, 30 export, 31 import review, 32 default SMS setup, 33 permissions, 46 app lock, 48 About, 49 gestures and dates |

## Behavior decisions

- **Attention:** unread rows use a small pine signal and remain in the Unread filter until opened or explicitly marked read. The thread offers a new messages jump control when the reader is away from the latest message.
- **Drafts:** unfinished text stays in its conversation. View 54 shows the retained entry and a draft notice; the component board includes the compact draft treatment.
- **Sending:** the composer distinguishes enabled and disabled states. Sending, sent/delivered, failed, and blocked each have visible text; sending and blocked also use distinct status lines. A failed message offers recovery through its attention state.
- **Keep at top:** the current app's pin action remains as **Keep at top** in conversation actions (view 40). A future implementation should persist its order in the inbox; the concept does not show a special pinned section.
- **Destructive actions:** conversation removal first exposes Undo. Recycle Bin offers restoration, and a separate confirmation surface covers permanent deletion.
- **Unwanted traffic:** Spam review, block rules, blocked numbers, and phrase rules are reachable from Library and settings. Blocked content remains reviewable and recoverable.
- **Search:** entry, populated results, and no matches are separate views. Search spans words, people, and media as expressed in the entry view.
- **Device realities:** dual SIM choice, SMS/MMS limits, group MMS, delivery reports, default SMS role, permissions, local backup, notifications, and app lock have dedicated surfaces or settings.

## Components and variations

Board `00b` holds navigation, composer, button hierarchy, filter chips, type scale, snackbar, attention notice, empty state, attachment tile, and concrete draft, sending, blocked, disabled, focused, and loading examples. Board `00` holds the palette, type, line language, and system principles. The phone views show light, dark, and RTL conversation variations. The RTL header reverses action order, right-aligns identity, and keeps the message entry at the right edge.

Every numbered phone frame is a fixed 390 × 844 design viewport. Long lists and settings are represented as scrollable content regions in the concept; the exported frame shows the first viewport. Actual scroll physics, keyboard insets, animation, TalkBack traversal, and Android SMS transport behavior still require implementation and device testing.

## Handoff

Open or reload the `.pen` file in Pendev to inspect and edit layers. [Asset provenance](ASSETS.md) records the generated family album and editable icon source. The PNG exports in `exports/` are quick visual previews; the `.pen` canvas is the source of truth.
