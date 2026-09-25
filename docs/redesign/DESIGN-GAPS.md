# Design gaps: Open Line inbox

This lists every view, component and state that the Compose inbox needed but `Messages-Signal.pen` did not specify. For each one it records what was built as a stand-in and what the design has to decide. The stand-ins are guesses; replace them once the design exists.

Status: **missing** means the design has nothing. **partial** means a component exists but no screen shows it in this context. **conflict** means the design shows one thing and the build needed another.

Code: `ui/inbox/InboxScreen.kt`, `ui/inbox/InboxState.kt` and `ui/OpenLineTheme.kt`, under `app/src/main/kotlin/org/fossify/messages/`.

## Screens and layouts

| # | Gap | Status | Built as | Design needs to decide |
| --- | --- | --- | --- | --- |
| L1 | Landscape inbox | missing | When the window is under 480 dp tall, navigation moves to a 96 dp side rail and the band drops its subtitle | Rail or bottom bar in landscape; band layout; where the FAB goes |
| L2 | Short windows (split-screen, large font) | missing | Same as L1; the rail scrolls when too short | Minimum band; whether the band collapses into a one-line top bar |
| L3 | Split-screen / dual app, both panes | missing | Rail below 480 dp tall; bottom bar otherwise | Behaviour at 50/50 and at the smallest split, in each orientation |
| L4 | Wide windows (tablet, unfolded, desktop) | missing | Side rail at 600 dp wide and up; the list stays single-pane | Rail vs list-detail (inbox + thread side by side); maximum row width |
| L5 | Dark inbox | missing | Dark roles derived from the tokens (night, nightSurface, pine2, lilac-dark) | A dark inbox frame; unread dot, avatar and chip colours on dark |
| L6 | Band scroll behaviour | missing | The 211 dp band scrolls away with the list; a pine strip stays under the status bar | Fixed, collapsing to a small top bar, or scrolling away |
| L7 | Loading state | partial (`loading result` component, skeleton tokens) | Linear progress plus "Loading messages…" under the filters | Skeleton rows vs progress; first launch vs refresh |
| L8 | Refresh failed / error | missing | Existing toast text `inbox_refresh_failed` | Inline notice (the `attention notice` component?) and retry |
| L9 | Empty state per filter | partial (34 covers All only) | Same card, with the title "You're all caught up" (Unread) or "No conversations here" (others); body text only for All | Copy and illustration per filter; whether New message appears |
| L10 | Library destination | missing as a screen for this flow (17–22 exist) | Stop-gap bottom sheet listing Starred, Archive, Spam (badge) and Recycle Bin, opening the old screens. `TODO(library)` | Library as a real tab (screen 17) and its back behaviour |
| L11 | Search from the Compose inbox | partial (03, 04, 53) | The field opens the old View `SearchView` overlay in the old styling | Hand-off animation from band field to search screen |
| L12 | Default-SMS and permission prompts over the new inbox | partial (32, 33) | Old system role dialog and Fossify permission dialogs | Whether 32/33 replace them, and the Open Line versions |

## Rows

| # | Gap | Status | Built as | Design needs to decide |
| --- | --- | --- | --- | --- |
| R1 | Pinned section | conflict | The design says no pinned section, but pinned rows sort first, so a "PINNED" header was added | Header, pin icon on the row, or no grouping |
| R2 | Scheduled (future-dated) section | missing | "SCHEDULED" header above Today | Section vs badge; row styling for scheduled sends |
| R3 | Earlier section | missing | Anything before yesterday goes under "EARLIER" | Week/month grouping for long inboxes |
| R4 | Date and time format for older days | missing | Commons format: "22 September". Long; it squeezes the preview | Short forms ("Mon", "22 Sep", "22/09/25"); Persian calendar (dates now show Gregorian months in Persian) |
| R5 | Muted row | missing | 16 dp bell-off icon next to the unread dot | Icon placement; dimmed row? |
| R6 | Scheduled row | missing | Preview in italics | Clock icon or label |
| R7 | Unread count above 1 | missing | Dot only; the count goes into the band subtitle "N new messages" (the old app showed a number badge) | Dot vs number badge; the "4 messages •" prefix seen on a Book club row in design 01 |
| R8 | Avatar for unknown numbers / letter-less titles | missing | Person glyph on a lilac or lime circle | Glyph, "#", or a neutral colour |
| R9 | Group avatar | conflict | Initials, as design 01 does for "Book club" | Review flagged that initials make groups look like people; group glyph or badge? |
| R10 | Contact photo avatar | missing | Circular photo | Ring or border on dark; fallback timing |
| R11 | Avatar colour rule | missing | Hash of the title picks lilac or lime | Rule and palette size |
| R12 | Long-press affordance | missing | Long-press selects; no hint | Discoverability (hint, tooltip, onboarding) |
| R13 | Mixed-script rows (Persian name + Latin preview, or the reverse) | missing | Text uses first-strong direction | Alignment per field in LTR and RTL shells |

## Selection mode (design 41 is a summary card, not a mode)

| # | Gap | Status | Built as | Design needs to decide |
| --- | --- | --- | --- | --- |
| S1 | Selected row in the list | missing | The avatar becomes a checkbox; the row fills with `secondary-container` | Selected-row visual; checkbox vs avatar overlay |
| S2 | Selection header | partial | The band title becomes "N selected" with the hint "Act on conversations together". Close (✕) and Select all replace the search field at the same 56 dp height, so rows do not jump | Header layout; where Select all lives |
| S3 | Bulk action bar | missing | The bottom bar is replaced by the first 3 applicable actions + Delete + "More options" | Which actions are primary; icon + label style |
| S4 | Overflow menu | partial (`menu item` component) | M3 dropdown: dial, add to contact, copy number, rename, details, block, and whatever does not fit | Menu styling in the Open Line palette |
| S5 | Action availability rules | missing | Pin or Unpin, Mute or Unmute, and Read or Unread depend on the mix selected; dial only for 1:1 non-short-code senders; rename only for a single group | Confirm the rules and the labels |
| S6 | Confirmation dialogs (archive, delete, block) | partial (`confirmation dialog` component) | Old Fossify dialogs | Open Line dialogs; whether bulk archive confirms or uses Undo (it confirms today, same as the old app) |
| S7 | Rename dialog | partial (39 rename) | Old Fossify dialog | Reuse 39 from the inbox |

## Gestures and feedback

| # | Gap | Status | Built as | Design needs to decide |
| --- | --- | --- | --- | --- |
| G1 | Swipe actions | missing | Background colour + icon: archive `primary`, delete `error`, mute `on-surface-variant`. Directions come from the user setting and stay physical in RTL | Swipe visuals, icon and label, threshold feedback, haptics |
| G2 | Undo snackbars (archive, mute, delete) | partial (23, `undo snackbar`) | One Compose host for all undos; a newer one replaces the older | Copy per action; stacking; the timeout follows accessibility settings |
| G3 | Undo on other screens | missing | Other screens still use the View snackbar | Snackbar on non-Compose screens during migration |

## Navigation and chrome

| # | Gap | Status | Built as | Design needs to decide |
| --- | --- | --- | --- | --- |
| N1 | Library badge | missing | Coral count badge (unread spam) on the Library nav item; compact text so Persian leading doesn't clip it; localized digits | Badge meaning and cap (99+) |
| N2 | Spam entry point | conflict | The old inbox had a Spam button with a count; now only the Library badge and sheet | Is a badge enough? |
| N3 | New message button | **resolved in design** | Floating extended FAB (lime) that collapses to icon-only after scrolling. The owner chose it on 2026-09-25. The .pen now uses the `extended compose FAB` on 01, 34, 57 and 61 instead of the inline button, adds `extended compose FAB · RTL` and `compose FAB · collapsed`, and has a `Spec / New message FAB` note | Only the position next to the rail (L1/N4) remains open |
| N4 | Navigation rail | missing | 96 dp rail, same items and badge, when short or wide | Rail spec (width, selected shape, badge, FAB inside the rail?) |
| N5 | Filter chips | conflict | All, Unread, Personal, Business & OTP, Unknown (the app's categories); horizontal scroll; 48 dp tall | Labels (design: All/Unread/People/Services); an Unknown bucket; overflow when chips don't fit |

## Persian, RTL and accessibility

| # | Gap | Status | Built as | Design needs to decide |
| --- | --- | --- | --- | --- |
| P1 | Persian inbox | partial (57 exists) | Vazirmatn at 1.7 leading; mirrored layout; translated copy for every string | Check 57 against the real row states above |
| P2 | Taller Persian leading in fixed-height controls | missing | The search field and selection controls are fixed at 56 dp so they can swap in place | Heights that fit both scripts |
| P3 | Digits | missing | Localized digits in counts, badges and dates (۱۱۷۱) | Persian digits everywhere, or Latin for numbers inside messages? |
| P4 | TalkBack labels | missing | Row merges name, preview, time, "Unread" and "Muted"; long-press label "Select" | Announcement order and wording |
| P5 | Large font / display size | missing | The user font-size setting scales the type; layouts wrap or ellipsize | Behaviour at 1.3× and 2× |

## Not a gap

Design 01 fully specifies the inbox band, search field, chips, section header style, and the row's name, preview, time, draft label and unread dot. These were built as designed; the unread dot uses `$primary` because the literal `$pine` disappears on dark.
