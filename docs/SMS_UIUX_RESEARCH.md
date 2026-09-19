# SMS / Messaging App UI/UX: Research Report

*Generated: 2026-09-10 | Sources: 30+ | Confidence: High for patterns, Medium for 2026 trend blogs*

**Purpose:** Implementation brief for agents improving Fossify Messages (this repo). Copy this file into a new agent chat.

**Constraint:** This app is an **offline-first, open-source Android SMS/MMS client**. Do **not** vendor Stream, CometChat, or Rivium as a chat backend. Study their component inventory, then rebuild UI with **Material 3 Expressive**, targeting Google Messages — **not** Fossify Commons chrome.

**Theming:** Do **not** preserve the Fossify look (primary-colored toolbars, Simple-Mobile settings rows, `MySearchMenu` as the product identity). Keep Commons only for plumbing (permissions, dialogs, config). Visual source of truth is Google Messages M3E.

---

## Executive Summary

A complete SMS app is judged in two seconds on the **inbox**, then for hours in the **thread**. Google Messages, WhatsApp, iMessage, QUIK (QKSMS successor), and OpenPhone all share the same skeleton:

1. Inbox: search, filters, unread + bold, pin, swipe, FAB compose
2. Thread: grouped bubbles, quiet timestamps, swipe-to-reply, reactions, persistent composer
3. Composer: one primary send action; attachments behind `+`; empty field shows mic
4. Search: chips (Unread, Starred, Images, Videos, Links, Unknown)
5. Conversation details: call, mute, search, theme, media, block

Fossify already has the **functional** skeleton (inbox, thread, archive, recycle bin, spam, schedule, attachments, pin, search, block keywords). What makes Google Messages and WhatsApp feel “complete” is **density, gesture speed, visual hierarchy, and recovery** — not more overflow-menu items.

Highest-ROI work for this codebase, in order:

1. Inbox swipe actions (archive / delete / mute) + snackbar undo
2. Thread: swipe-to-reply, message grouping, unread separator, jump-to-bottom
3. Composer: send/mic swap, long-press-send to schedule, reply preview bar, SIM chip
4. Search with filter chips; OTP copy chip on messages and notifications
5. Conversation details as a proper screen (not only overflow)
6. Material 3 SearchBar + bottom sheets (replace buried menus)
7. Replace Fossify chrome with Material 3 Expressive (see § Theming)

---

## 1. What Fossify already has

Map this before adding screens. Do not duplicate.

| Screen / object | Files | Notes |
|---|---|---|
| Inbox | `MainActivity`, `activity_main.xml`, `item_conversation.xml` | Large title, tabs, spam shortcut, `MySearchMenu`, unread badge, pin |
| Thread | `ThreadActivity`, `activity_thread.xml` | Toolbar: call, archive; overflow: people, details, unread, block, delete |
| Composer | `layout_thread_send_message_holder.xml` | Schedule banner, attachment strip |
| Attachment tray | `layout_attachment_picker.xml` | Photo, video, camera, record, file, contact, schedule — grid of 8 |
| New conversation | `NewConversationActivity` | Contact/number chips |
| Conversation details | `ConversationDetailsActivity` | Exists |
| Archive / recycle bin | dedicated activities | Overflow on main menu |
| Spam | `BlockedMessagesActivity` | New in this branch |
| Settings | `activity_settings.xml` | Long vertical list, Fossify Commons style |
| Message CAB | `cab_thread.xml` | Copy, share, save, delete, forward, select text, properties |
| Inbox CAB | `cab_conversations.xml` | Pin, call, archive, delete, read/unread, block |

**Play Store complaints to treat as UX bugs, not features:** failed send retry forwarding to the wrong person; group notification reply sending to one member; MMS/GIF send failures. Reliability beats new chrome.

---

## 2. Canonical screens (build these)

Agents should treat this as the **complete screen list**. Items marked **HAVE** exist; **GAP** is the work.

### 2.1 Inbox / conversation list

**References**

- Google Messages homepage (Material 3 Expressive container, larger avatars) — [9to5Google homepage](https://9to5google.com/2025/06/04/google-messages-expressive-redesign-expands/), [Daily Tech Feed](https://thedailytechfeed.com/google-messages-unveils-material-3-expressive-redesign-with-dual-homepage-variants/)
- Ethora inbox patterns — [ethora.com/blog/chat-app-ui-ux-design](https://ethora.com/blog/chat-app-ui-ux-design/)
- OpenPhone iOS conversation list — [Mobbin](https://mobbin.com/explore/screens/23fb80b7-3258-465c-8651-b31bc81e0e73)
- Material 3 lists — [m3.material.io/components/lists](https://m3.material.io/components/lists/overview)

**Layout (top → bottom)**

1. Collapsing large title **or** Material `SearchBar` (Google Messages uses a search field, not a hidden icon)
2. Filter chips in a horizontal `ChipGroup`: `All` | `Unread` | `Personal` | `Business/OTP` | `Unknown`  
   Fossify already has `inbox_tabs` — keep them, make them chips if they overflow
3. Pinned section (slightly tinted, pin icon, never reorders with recency)
4. Conversation rows (M3 list item heights: 72dp two-line, 88dp three-line)
5. FAB bottom-end: “Start chat” (extended on first launch, icon-only after scroll)

**Row anatomy (must all be present)**

- 40–56dp avatar (initials color-hashed from number if no photo)
- **Bold name + unread count** together (Ethora: either alone is weaker)
- One-line snippet, ellipsis; prefix `Draft:` in accent if draft (Fossify already has `draft_indicator`)
- Relative time top-end (`12m`, `Yesterday`, `Mon`)
- Unread badge (HAVE)
- Optional: muted bell, spam shield, SIM badge, OTP chip

**Gestures**

- Tap → thread
- Long-press → CAB (HAVE — keep Android convention)
- **Swipe left / right:** configurable Archive / Delete / Mute / Off  
  Mirror Gmail + Google Messages settings ([9to5Google swipe](https://9to5google.com/2022/07/28/google-messages-swipe-actions/))
- After swipe: Snackbar **Undo** 5s

**Empty / loading**

- Skeleton rows matching 72dp height, not a centered spinner
- Empty: illustration + one CTA “Start a conversation” (string already exists)

**Settings for this screen**

- Swipe left action, swipe right action (Archive / Delete / Mute / Off)
- Compact vs comfortable density

### 2.2 Thread / conversation

**References**

- Google Messages Play Store screenshots (captured 2026-09-10): group RCS with reaction bar + suggested reply “Incredible”; 1:1 with avatars + typing dots; spam banner; video bubble with duration + reaction
- WhatsApp Android chat — [Mobbin](https://mobbin.com/explore/screens/b6295dd8-08ba-4665-aa34-60c011876554)
- Ethora thread patterns; BioCraftr conversation UX — [biocraftr.com/designing-mobile-conversations](https://biocraftr.com/designing-mobile-conversations/)

**Header**

- Back, avatar + name (subtitle: number or “RCS / SMS” or member count)
- Trailing: Search, Call, Overflow  
  Google Messages 1:1 header: **video, call, search, overflow**. Fossify currently shows call + archive as always-visible. Archive is high-value but archive-in-header fights with call. Prefer: **Search + Call** always; Archive in overflow or swipe on inbox.

**Message list rules**

| Rule | Spec |
|---|---|
| Alignment | Incoming start, outgoing end. Color **and** alignment. Do not rely on color alone. |
| Grouping | Same sender within ~60s: one avatar, one timestamp, tighter vertical gap (4dp vs 12dp) |
| Timestamps | Date separators (“Wednesday, Nov 4”). Per-message time on tap, not on every bubble |
| Unread separator | “New messages” pill after last-read — Stream documents this slot |
| Failed send | Red error on bubble + “Not sent. Tap to retry.” (strings exist — make retry **in-thread**, never change recipient) |
| Sending | Optimistic insert + faint “Sending…” |
| Media | Rounded 16dp, tap → pager viewer, pinch zoom, swipe down to dismiss |
| Video | Thumbnail + play + duration overlay (see Google screenshot) |
| Link preview | Optional, settings toggle |
| System | Centered small text: “SMS / RCS”, “Scheduled”, encryption note |
| Spam | Sticky banner at top: “Suspected spam” + Report (Google screenshot). Fossify can reuse blocked-reason copy |
| Reactions | Long-press → emoji row under bubble. For SMS, store locally; if iPhone reaction texts arrive, parse into emoji (Google does this) |
| Reply | Swipe incoming **toward start→end** (WhatsApp/iMessage). Quote bar above composer with X |
| Jump to bottom | FAB appears when scrolled up; unread count on it |
| Keyboard | `adjustResize`; composer docks above IME; last message stays visible |

**Long-press / CAB order (keep stable across Android)**

1. React  
2. Reply  
3. Copy  
4. Forward  
5. Star  
6. Select text  
7. Details  
8. Delete  

Fossify `cab_thread.xml` is missing **Reply, React, Star**. Add those; keep copy/share/delete.

### 2.3 Composer

**Google Messages composer (from screenshots):**

`[ + ] [ gallery ] [  pill: "RCS message" / "Chat message"  ] [ emoji ] [ mic ]`

When text present: mic → **send** (filled, primary color).

**Fossify should match this contract:**

| State | Trailing control |
|---|---|
| Empty, no attachment | Mic (speech-to-text). Optional: long-press = voice note |
| Text or attachment | Send |
| Long-press Send | Schedule (Google: [support.google.com schedule](https://support.google.com/messages/answer/10456318)) — Fossify already has schedule in the attachment grid; **also** bind long-press send |
| Replying | Quote strip above input, X to cancel, persists across rotation |
| Dual-SIM | Small SIM chip left of send; remember last SIM per conversation |

**Attachment `+`**

Fossify already has a 2-row icon grid. Google’s 2025 Expressive version uses **monochrome icons in pill containers**: Gallery, Camera, GIFs, Stickers, Files, Location, Contacts, Schedule, Selfie GIF ([9to5Google chat redesign](https://9to5google.com/2025/08/26/google-messages-chat-redesign/)).

For Fossify (no RCS stickers/GIFs required): keep current actions, restyle as **BottomSheet** with 48dp+ targets, not a cramped overlay. Order: Gallery, Camera, Files, Audio, Contact, Schedule.

**Growth:** 1 line → max ~5 lines then internal scroll.

**Placeholder:** “Message” or existing `type_a_message` — not clever copy.

**Haptics:** short tick on send, on reaction, on swipe-archive.

### 2.4 Search

**Google Messages search** ([support](https://support.google.com/messages/answer/10456318), [iGeeksBlog](https://www.igeeksblog.com/google-messages-hidden-features/)):

- Global search from inbox
- In-thread search from header
- Filter chips: Starred, Unknown, Images, Videos, Links, Places, Unread
- 2025 Expressive: 2×4 chip grid, larger targets; results in cards ([9to5Google search](https://9to5google.com/2025/06/06/google-messages-material-3-expressive-more/))

**Implement with MDC `SearchBar` + `SearchView`** (XML):  
[Material Search docs](https://github.com/material-components/material-components-android/blob/master/docs/components/Search.md)

Debounce ~200ms. Recent queries on first open. Clear button when query non-empty.

### 2.5 New conversation

- “To” field with contact chips (HAVE)
- Suggested contacts
- Create group as full-width row (Google M3E)
- Selected contact morphs rectangle → pill

### 2.6 Conversation details

Google M3E details: pill buttons **Call | Video | Contact | Search** ([Daily Tech Feed](https://thedailytechfeed.com/google-messages-unveils-material-3-expressive-redesign-with-dual-homepage-variants/)).

Then a list:

- Notifications / mute / custom sound (HAVE `enable_custom_notifications`)
- Theme / bubble color / wallpaper (opt-in wallpaper only — Ethora)
- Media, links, files grid
- Starred in this chat
- Add to contacts / view contact
- Block & report
- Archive
- Search

### 2.7 Other screens that make the app feel complete

| Screen | Why |
|---|---|
| Starred messages | Cross-inbox list ([Google star docs](https://support.google.com/messages/answer/10930955)) |
| Scheduled messages list | Fossify has per-thread schedule; add a global “Scheduled” from main overflow |
| Spam / blocked | HAVE — keep “Not spam”, “Always allow”, reason chips |
| Archived | HAVE |
| Recycle bin | HAVE |
| Media viewer | Full-screen pager, react, share, save |
| Settings IA | Group: Appearance, Inbox (swipe, categories), Notifications, Privacy (lock screen), SMS (SIM, MMS size, char counter), Backup |

---

## 3. Flows (happy path + recovery)

Agents should implement and test these end-to-end.

1. **Inbox → thread → send → back.** Unread clears; snippet + timestamp update; scroll position of inbox restored.
2. **Swipe archive → Undo.** Conversation returns.
3. **Swipe reply → quote visible → send → quote appears on bubble.**
4. **Long-press send → pick time → scheduled banner in thread → edit/send now/delete.**
5. **Failed send:** airplane mode → bubble error → tap retry → same recipient.
6. **OTP:** incoming 6-digit code → `Copy 123456` chip on bubble and notification action.
7. **Search Images → tap result → jumps to message in thread, highlighted.**
8. **Spam incoming:** no notification (if filter on) → appears in Spam → Not spam restores to inbox.
9. **Attachment:** pick photo → preview with X → send → progress → tappable in thread.
10. **Keyboard:** composer never covers last message; IME hide restores.

Google’s own reply gesture: swipe message left→right ([support](https://support.google.com/messages/answer/10456318)).

---

## 4. Menu information architecture

### Inbox overflow (today)

Recycle bin, Archived, Spam, Settings, About — all `showAsAction="never"`.

**Recommended**

- Keep overflow short: Settings, About
- Archive / Spam / Recycle / Scheduled as **inbox destinations** (tabs, chips, or a small leading nav icon / profile sheet like Google Messages)
- Google Messages uses **profile avatar top-end** opening a sheet: Settings, spam & blocked, archived. That is more satisfying than a 5-item kebab

### Thread overflow (today)

Add person, add to contacts, copy number, rename, details, mark unread, block, delete, restore.

**Recommended always-visible:** Search, Call  
**Overflow:** Details, Mute, Theme, Add to contacts, Mark unread, Block, Archive, Delete

### Composer `+` vs overflow

Do not put Gallery as a separate icon **and** inside `+`. Google uses `+` plus a dedicated gallery shortcut because photo send is the #2 action. Fossify can keep both if targets stay ≥48dp.

---

## 4.5 Theming: do not keep Fossify’s look

The Fossify/Simple Mobile look is a **product identity**, not a requirement. It is the colored `color_primary` toolbar (`activity_thread.xml` paints the bar with `@color/color_primary`), Commons settings rows, and a 2016-era Material app-bar. Google Messages, QUIK, and WhatsApp all left that behind.

**Visual target:** Google Messages Material 3 Expressive (2025–2026).

| Fossify today | Replace with |
|---|---|
| Primary-colored `MaterialToolbar` | Surface-colored top bar, merged with status bar; icons in on-surface |
| Inbox as a flat list under a giant title | Conversation list inside a **rounded container** under a search bar |
| `MySearchMenu` icon | Persistent M3 `SearchBar` |
| Settings: Commons `SettingsHolder*Style` rows | M3 list items / preference fragments with sectioned surfaces |
| Circle FAB + primary fill | Pill FAB; morph on scroll |
| Attachment grid of colored circles | Monochrome icons in pill containers, bottom sheet |
| Bubbles = `color_primary` fill | Dynamic/user surface + distinct incoming/outgoing; contrast 4.5:1 |
| Overflow kebab as primary IA | Profile/account sheet + chips; kebab only for rare actions |

**Still keep from Commons:** permission flows, `BaseSimpleActivity` lifecycle if needed, import/export helpers, color-picker *as an optional user setting* — not as the default skin.

**How to implement without a full Compose rewrite:** restyle XML onto `Theme.Material3.DynamicColors` (or a fixed M3 seed), stop setting `android:background="@color/color_primary"` on toolbars, use `MaterialToolbar` with transparent/surface, `SearchBar`, `Chip`, `BottomSheet`. Compose is optional later; M3 Views still ships SearchBar/Chip/FAB.

References: [9to5Google M3E chat](https://9to5google.com/2025/08/26/google-messages-chat-redesign/), [m3.material.io lists](https://m3.material.io/components/lists/overview), [MDC Search](https://github.com/material-components/material-components-android/blob/master/docs/components/Search.md).

---

## 5. Visual / motion details that increase satisfaction

From Ethora, BioCraftr, Material 3, Google M3 Expressive:

- **Containers:** thread and inbox sit in a rounded surface under the app bar (Google 2025). Optional; if staying Fossify-flat, at least unify bubble radii.
- **Unread = bold name + badge**, never color-only
- **Accent only on send, links, mentions, unread badge** — not every incoming bubble
- Wallpaper opt-in; default is solid surface (Google dropped bubbly wallpaper in M3E)
- Touch targets ≥48dp; reaction chips currently fail this in most apps
- `prefers-reduced-motion`: static “typing…” instead of bouncing dots
- Contrast 4.5:1 bubble text vs bubble fill (Play reviews: black-on-black bubbles)
- RTL: `start/end`, not left/right
- Haptic on send / archive / reaction
- Optimistic send
- Dynamic type: bubbles grow, never clip

---

## 6. Features worth copying (SMS-realistic)

Prioritized for **this** app. Skip cloud-only RCS toys unless you add RCS later.

| Feature | Source | Priority |
|---|---|---|
| Swipe archive/delete, configurable | Google Messages, QUIK, Pulse | P0 |
| Swipe-to-reply | Google, WhatsApp, iMessage | P0 |
| Reply quote on bubble | Google support | P0 |
| OTP copy chip | Google, MySMS, Flutter Messages | P0 |
| Search filter chips | Google | P0 |
| Message grouping + date separators | iMessage, Telegram, Ethora | P0 |
| Unread separator + jump-to-bottom | Stream, WhatsApp | P0 |
| Send/mic swap | WhatsApp, iMessage, Telegram | P0 |
| Long-press send = schedule | Google | P1 |
| Star message + starred list | Google | P1 |
| Suggested replies (on-device or existing AI filter infra) | Google; insert don’t auto-send | P1 |
| Dual-SIM chip per thread | QUIK, MySMS | P1 |
| Per-conversation mute | all majors | P1 |
| Categories Personal / OTP / Unknown | Google; Najeer Flutter app | P1 |
| Delayed send (few seconds, cancel) | QUIK | P1 |
| TTS/STT in composer | QUIK | P1 |
| iPhone-reaction text parsing | Google | P1 |
| Per-chat bubble color | QUIK, Google themes | P2 |
| Nudges / birthday banner | Google blog 2022 | P2 |
| OTP auto-delete after 24h | Google | P2 |
| Screen effects (“Happy birthday”) | Google | P3 skip unless trivial |
| Photomoji, Remix, Gemini Magic Compose | Google AI, needs network | P3 — Fossify is offline-first; if AI, keep behind existing settings and never auto-send |

---

## 7. Libraries and kits

### Use in this repo (XML Views)

| Library | Why | Link |
|---|---|---|
| **Fossify Commons** | Already the design system (`MySearchMenu`, `MyAppBarLayout`, settings rows) | existing dependency |
| **Material Components Android** | `SearchBar`, `SearchView`, `Chip`, `Badge`, `FloatingActionButton`, `BottomSheet`, `MaterialToolbar`, `LinearProgressIndicator` | [github.com/material-components/material-components-android](https://github.com/material-components/material-components-android) |
| **Material 3 lists** | 56/72/88dp rows, middle vs top align | [m3.material.io lists](https://m3.material.io/components/lists/overview) |
| **RecyclerView `ItemTouchHelper`** | Inbox swipe; thread swipe-to-reply | AndroidX; Gmail-style gist; [shainsingh89/SwipeToReply](https://github.com/shainsingh89/SwipeToReply) |
| **androidx.emoji2** | Emoji rendering in bubbles | AndroidX |
| **PhotoView** (or equivalent) | Pinch-zoom media | common OSS |
| Existing image loader | Don’t add a second | keep current |

MDC-Android Views is in **maintenance mode**; Google wants Compose. For Fossify, stay on Views until a deliberate Compose migration. New screens can still use MDC XML components.

### Study, do not vendor

These are **chat SDKs** with their own servers. Useful as a **checklist of slots**, not as a Gradle dependency.

| Kit | What to steal | Link |
|---|---|---|
| Stream Chat Compose `ChatComponentFactory` | Exhaustive slots: header, list item, composer, attachments, menus, reactions, pinned, typing | [getstream.io component factory](https://getstream.io/chat/docs/sdk/android/compose/general-customization/component-factory/) |
| CometChat UI Kit | Conversations + Header + List + Composer composition | [cometchat.com Android UI kit](https://www.cometchat.com/android-chat-ui-kit) |
| QUIK (QKSMS fork) | **Best SMS-specific open source reference** — swipe, schedule, STT, SIM, delayed send, theming | [github.com/octoshrimpy/quik](https://github.com/octoshrimpy/quik) |
| Figma: Android M3 Messaging App | Inbox, messaging, search, contacts, settings; 100+ components; CC BY 4.0 | [Figma community](https://www.figma.com/community/file/1169726503071187057/android-material-design-3-messaging-app) |
| Figma: Sceyt Chat UI Kit Android | Full Android chat kit | [Figma](https://www.figma.com/community/file/1408471773948751501/sceyt-chat-ui-kit-for-android) |
| Figma: Telegram Material 3 | Profile, group info, chat | [Figma](https://www.figma.com/community/file/1291851837898457904/telegram-material-3) |

### Avoid as a drop-in for Fossify

- Stream, CometChat, Rivium, Ethora React kits — **wrong product class** (realtime IM, not SMS)
- Random JitPack swipe libraries with no maintenance — copy the `ItemTouchHelper` idea, don’t add an abandoned artifact

---

## 8. Mobbin and gallery URLs (for visual agents)

Mobbin **paywalls** full-resolution flows unless logged in. Use these as starting points; Play Store and 9to5Google have public screenshots.

| Resource | URL |
|---|---|
| Mobbin: Chatting & Sending Messages flows | https://mobbin.com/explore/mobile/flows/chatting-sending-messages |
| Mobbin: WhatsApp Android chat | https://mobbin.com/explore/screens/b6295dd8-08ba-4665-aa34-60c011876554 |
| Mobbin: WhatsApp iOS chat | https://mobbin.com/explore/screens/7c4e84b3-6155-4502-bc49-5cab2025b28e |
| Mobbin: OpenPhone iOS conversation list | https://mobbin.com/explore/screens/23fb80b7-3258-465c-8651-b31bc81e0e73 |
| Mobbin: OpenPhone web messaging | https://mobbin.com/explore/screens/0ed67306-9765-407e-b03d-bc21eab36375 |
| Google Messages Play Store (official screenshots) | https://play.google.com/store/apps/details?id=com.google.android.apps.messaging |
| Fossify Messages Play Store | https://play.google.com/store/apps/details?id=org.fossify.messages |
| Google Messages marketing | https://www.android.com/google-messages/ |
| M3E chat UI writeup + photos | https://9to5google.com/2025/08/26/google-messages-chat-redesign/ |
| M3E homepage writeup + photos | https://9to5google.com/2025/06/04/google-messages-expressive-redesign-expands/ |
| Samsung coverage of M3E Messages | https://www.sammobile.com/news/messaging-app-galaxy-phone-expressive-design/ |

### Screenshot notes captured 2026-09-10 from Play Store lightbox

**Group RCS thread**

- Header: back, title “Vacay Crew”, search, overflow
- Incoming name above bubble (“Lin”), photo with rounded corners, **reaction pill** under media: 😍 👍 😂 😮 👎
- Suggested reply chip “Incredible” above composer
- Composer: `+`, gallery, pill field “RCS message”, emoji, mic
- Typing: stacked avatars + three dots

**1:1 RCS thread**

- Header: back, “Zach”, **video, call, search, overflow**
- Centered system chip: lock + “RCS chat with Zach”
- Incoming avatar on every ungrouped message
- Timestamp on incoming (“6:30 PM”); outgoing “Read” + lock
- Same composer pattern

**Spam**

- Banner card: warning icon, “Suspected spam”, helper text, “Report spam”
- Unknown number as title
- Composer label “Chat message” (SMS fallback)

**Video bubble**

- Date separator “Wednesday, Nov 4”
- Play overlay + duration `0:31`
- Small reaction on corner of media

---

## 9. Agent implementation backlog

Work in this order. Each item is independently shippable.

### P0 — “Feels like a real messenger”

0. **Replace Fossify chrome** with M3E: surface toolbars (no `@color/color_primary` bars), SearchBar, chips, pill FAB, rounded inbox/thread containers. Commons stays for plumbing only.
1. **Inbox `ItemTouchHelper` swipe** with colored underlay (archive teal, delete red). Settings: left/right = Archive | Delete | Mute | Off. Snackbar undo.
2. **Thread swipe-to-reply** + quote bar on composer + quoted header on sent bubble.
3. **Group consecutive messages**; date headers; tap bubble for time.
4. **Unread separator** + **scroll-to-bottom FAB**.
5. **Composer send/mic swap**; long-press send → existing schedule dialog.
6. **OTP regex chip** “Copy ######” on bubble + notification action.
7. **Fix retry/forward recipient bug** (Play reviews). UX is worthless if send is wrong.

### P1 — “Complete”

8. Material `SearchBar`/`SearchView` with chips: All, Unread, Starred, Images, Links, Unknown.
9. Star on CAB + Starred destination.
10. Thread header: Search always visible; move Archive to overflow or inbox swipe.
11. Conversation details: pill actions Call / Contact / Search / Mute.
12. Dual-SIM indicator in composer.
13. Attachment tray → `ModalBottomSheet` with 48dp pills.
14. Per-conversation mute.
15. Parse iPhone reaction SMS into emoji reactions.

### P2 — Polish

16. Skeleton inbox.
17. Density setting.
18. Jump to date in thread.
19. Global scheduled-messages list.
20. OTP auto-delete 24h (opt-in).
21. Per-chat color (QUIK).
22. Foldable two-pane (list | thread) — Google already does this.

### Do not do yet

- RCS, typing indicators, read receipts (need carrier stack)
- Gemini / Photomoji / Remix
- Replacing Fossify Commons with a third-party chat UI kit
- Inventing non-platform gestures

---

## 10. Copy-paste prompt for an implementing agent

```text
You are improving Fossify Messages (Kotlin, XML Views, Fossify Commons).
Read docs/SMS_UIUX_RESEARCH.md.

Constraints:
- Do not add Stream/CometChat/Rivium.
- Do not break SMS send/retry recipient identity.
- Do **not** follow Fossify/Simple Mobile theming. Visual target is Google Messages Material 3 Expressive: surface toolbars, SearchBar, chips, pill FAB, rounded list/thread containers. Keep Commons for plumbing only.
- Reuse existing strings where they still fit; add new ones for new chrome. Touch targets >= 48dp. RTL start/end. Snackbar undo for destructive swipes.

Implement P0 items 1–6 from the backlog, one PR-sized change at a time.
Start with inbox swipe actions using ItemTouchHelper, matching Google Messages
(archive/delete/off, left and right independently).
Visual reference: Google Messages Play Store screenshots and
https://9to5google.com/2025/08/26/google-messages-chat-redesign/
```

---

## Sources

1. [Chat App UI/UX: Design Patterns That Make Users Stay](https://ethora.com/blog/chat-app-ui-ux-design/) — 40+ patterns; inbox, thread, composer, a11y. Vendor blog; patterns corroborated elsewhere.
2. [Designing Mobile Conversations](https://biocraftr.com/designing-mobile-conversations/) — hierarchy, feedback, AI control.
3. [Google Messages: more features](https://support.google.com/messages/answer/10456318) — swipe reply, search filters, schedule via long-press send, OTP copy.
4. [Star messages & pin](https://support.google.com/messages/answer/10930955)
5. [Expressive features / reactions](https://support.google.com/messages/answer/9827088)
6. [Google Messages Play Store](https://play.google.com/store/apps/details?id=com.google.android.apps.messaging) — live screenshots 2026-09-10.
7. [android.com/google-messages](https://www.android.com/google-messages/)
8. [Google blog: categories, nudges, OTP delete](https://blog.google/products-and-platforms/products/messages/updates-march-2022/)
9. [9to5Google swipe actions](https://9to5google.com/2022/07/28/google-messages-swipe-actions/)
10. [9to5Google M3E chat](https://9to5google.com/2025/08/26/google-messages-chat-redesign/)
11. [9to5Google M3E homepage](https://9to5google.com/2025/06/04/google-messages-expressive-redesign-expands/)
12. [9to5Google M3E search/viewer](https://9to5google.com/2025/06/06/google-messages-material-3-expressive-more/)
13. [SamMobile M3E Messages](https://www.sammobile.com/news/messaging-app-galaxy-phone-expressive-design/)
14. [MakeUseOf Google Messages features](https://www.makeuseof.com/google-messages-features-worth-using/)
15. [iGeeksBlog hidden features](https://www.igeeksblog.com/google-messages-hidden-features/)
16. [Android Authority: how to use Messages](https://www.androidauthority.com/what-is-google-messages-3242467/)
17. [Material 3 Lists](https://m3.material.io/components/lists/overview)
18. [MDC SearchBar](https://github.com/material-components/material-components-android/blob/master/docs/components/Search.md)
19. [Stream ChatComponentFactory](https://getstream.io/chat/docs/sdk/android/compose/general-customization/component-factory/)
20. [CometChat Android UI Kit](https://www.cometchat.com/android-chat-ui-kit)
21. [QUIK / QKSMS](https://github.com/octoshrimpy/quik)
22. [Figma M3 Messaging App](https://www.figma.com/community/file/1169726503071187057/android-material-design-3-messaging-app)
23. [Fossify Messages Play Store](https://play.google.com/store/apps/details?id=org.fossify.messages)
24. [Pulse SMS swipe/folders](https://www.androidpolice.com/2018/05/15/pulse-sms-v3-0-beta-includes-folders-code-copying-custom-swipe-actions-apk-download/)
25. [Apple HIG lists](https://developer.apple.com/design/human-interface-guidelines/lists-and-tables)
26. [Mobbin messaging flows](https://mobbin.com/explore/mobile/flows/chatting-sending-messages)
27. [MDC-Android maintenance / Compose-first](https://github.com/material-components/material-components-android)

## Methodology

Sub-questions: (1) inbox/thread/composer patterns, (2) Google Messages / QUIK / WhatsApp feature set, (3) Mobbin/Figma visual galleries, (4) Android libraries usable in Fossify, (5) menu IA and gestures.

Firecrawl and Exa MCP were **not configured** in this environment. Research used web search, full-page fetches, and a browser pass of Mobbin + Google Play screenshot lightbox. Mobbin screen pixels are login-gated; Play Store and 9to5Google supplied public screenshots.

**Gaps:** No live device screenshots of Fossify or QUIK. RCS-specific UI is documented but not in scope for Fossify unless RCS is added. Some 2026 “trend” blogs are thin; those claims were not used in Key Takeaways unless corroborated.
