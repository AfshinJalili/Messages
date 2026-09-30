# Open Line design system

The final v2 implementation reference is [Open Line Design System v2](design/reference/Open%20Line%20Design%20System%20v2.dc.html), supplied by the owner on 2026-09-30. Its token, component, screen, dark/RTL and accessibility sections define the implementation target. The already shipped 4a Corner · Pine logo stays in place.

Personal messaging as a clear, living line between people. The inbox favors sender, latest meaning, and urgency over decorative conversation cards. Cool mineral paper, deep pine ink, sharp lime for new activity, lilac for media, coral for attention. A fine route line, round contact markers, flat slabs, and strong typography replace the current cobalt styling. No literal vintage hardware.

The design source is `design/open-line.pen` (encrypted; edit only through the pen CLI or pencil MCP). PNG exports in `design/exports/` are previews only; the canvas is the truth. Screens are fixed 390 × 844 frames and the design decides open questions through issues labeled `design-gap`.

Theme: `ui/OpenLineTheme.kt`. The palette lives in `res/values/colors.xml` (`open_line_*`) because `tests/check_design_tokens.py` rejects inline colors. Roles and type are in Kotlin. Light or dark follows the app's background setting. The Commons accent and custom colors do not apply to Compose screens. Fonts are bundled from google/fonts (OFL): Funnel Sans, Atkinson Hyperlegible, and Vazirmatn for RTL labels. Message content, previews, drafts, and search excerpts use Atkinson for Latin and Vazirmatn for Arabic-script runs regardless of app language, preserving weight and link styling. Content containing Arabic script uses 1.7em leading; other content uses 1.45em.

## Product idea

The inbox acts like an open line: sender, latest meaning, and urgency are visible in one continuous list. Conversations put message text first and leave the composer at the bottom of the phone viewport. The navigation has three destinations: Inbox, Library, and Settings. Library collects saved, archived, spam, and recently deleted conversations so those tasks stay findable without crowding the inbox.

The palette uses mineral paper (`$paper`), deep pine (`$pine`, `$ink`), lime for new or active activity (`$lime`), lilac for media and retained drafts (`$lilac`), and coral for attention (`$coral`). Color also appears with words, shape, or line weight so status does not depend on hue alone. Sender initials identify contacts when a photo is unavailable.

## Screen map (numbers are frames in the design file)

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


## Asset provenance

`design/open-line.pen` contains fictional message content and names for visual demonstration.

The family-album image in the media message and full-screen viewer was generated in Pendev with this prompt:

> Documentary overhead photograph of a well-loved family photo album open on a wooden dining table, a few candid printed photographs of ordinary family gatherings tucked into the pages, soft afternoon window light, realistic subtle wear and paper texture, warm natural colors, no readable text, no logos, landscape composition with the album centered.

The generated asset is saved as `design/generated.png` beside the `.pen` file so the image fills can resolve when the canvas is reopened.

The interface icons are editable vector paths from `lucide-static` 1.48.0 (ISC license), replacing font-backed icon nodes that failed to render in the headless Pendev preview.

The app bundles the design's fonts in `app/src/main/res/font/`, copied unmodified from [google/fonts](https://github.com/google/fonts) under the SIL Open Font License 1.1: Funnel Sans (variable), Atkinson Hyperlegible Regular and Bold, and Vazirmatn (variable). The Library nav icon `ic_library_vector.xml` is Lucide `layers` (ISC).


## Legacy XML screens

Screens not yet moved to Compose still use the Fossify theme and this system. Android resources are the source of truth; this document does not keep a second palette or sizing table.

Android resources are the source of truth. This document describes how to use them; it does not maintain a second palette or a second sizing table.

### Ownership

| Definition | Source |
| --- | --- |
| Brand palette, surface colors, semantic action colors and Commons defaults | `app/src/main/res/values/colors.xml` |
| Light and dark widget themes, shared component styles | `app/src/main/res/values/styles.xml` |
| Component spacing, sizes and corner radii | `app/src/main/res/values/dimens.xml` |
| Shared opacity and user-font-size multipliers | `app/src/main/res/values/design_values.xml` |
| Runtime theme selection and API-compatible float resource access | `app/src/main/kotlin/org/fossify/messages/helpers/Design.kt` |
| Launcher shape | `app/src/main/res/drawable/ic_launcher_foreground.xml` |

### Color

Cobalt is the default accent. The launcher and light theme use `brand_cobalt`; the dark theme uses `brand_cobalt_dark` for readable accent text and controls. Both themes use neutral backgrounds. Destructive actions use `design_error`, archive uses cobalt, and mute uses a neutral slate. White icon paths are tint masks, not independent palette choices.

Kotlin screens obtain the current accent, foreground and background through Commons' `getProperPrimaryColor()`, `getProperTextColor()` and `getProperBackgroundColor()`. These respect customization. Use `getContrastColor()` for content on a user-selected fill. Static action colors come from the semantic resources in `colors.xml`. Do not add hex colors or RGB calls to activities, adapters, layouts or drawables.

`SimpleActivity.setTheme()` selects our cobalt theme when the configured accent is cobalt. This keeps XML controls consistent with runtime colors and avoids Commons' green fallback for custom colors. Explicit custom palettes and Material You continue to use Commons' theme selection.

`Config.applyCobaltDefaults()` runs once at application startup. It replaces the old default green, opts implicit system-theme defaults into cobalt, and retains explicit custom color or system-theme selections. Later settings changes are not reset on startup.

### Components

Keep surface-colored app bars, one filled icon family, letter avatars for individual contacts, and group icons for groups. Reserve accent for actions, unread indicators, selected filters and sent messages. Attachment actions share a neutral tinted background.

Use the named bubble and tail radii in `dimens.xml`, the shared minimum touch target for composer actions, and the shared attachment action dimensions. Component-local constraint geometry can remain in its layout; reusable sizes belong in resources.

Keep system typography and the user's font-size choice. List primary, secondary and metadata text use the multipliers in `design_values.xml`. Read and unread rows share their geometry; unread emphasis changes weight and opacity.

### Icon exports and checks

The monochrome launcher icon aliases the foreground vector. `python3 tools/export_icon.py` regenerates the SVG, WebP, store PNGs and `ic_message_bubble.xml` from that vector and `colors.xml`. The notification and new-conversation icons use this bubble without the launcher's adaptive-icon padding. Run the exporter with system Python containing PyGObject/Rsvg and Pillow. Exported assets are generated copies, not editable design sources.

`python3 tests/check_design_tokens.py` rejects inline colors and opacity values. Android `DesignChecks` verifies readable light/dark accents, matching XML/runtime colors, migration and preserved customization. The existing `InboxChecks` covers layout and message behavior.

Conversation day dividers, the temporary sticky date and in-card metadata follow [the thread date behavior](docs/thread-dates.md).
