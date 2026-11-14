# AFSH design system

Personal messaging as a clear, living line between people. The inbox favors sender, latest meaning, and urgency over decorative conversation cards. Cool mineral paper, deep pine ink, sharp lime for new activity, lilac for media, coral for attention. A fine route line, round contact markers, flat slabs, and strong typography. No literal vintage hardware.

The design source is `design/afsh.pen` (encrypted; edit only through the pen CLI or pencil MCP). PNG exports in `design/exports/` are previews only; the canvas is the truth. Screens are fixed 390 × 844 frames and the design decides open questions through issues labeled `design-gap`.

Theme: `ui/AfshTheme.kt`. The palette lives in `res/values/colors.xml` (`afsh_*`) because `tests/check_design_tokens.py` rejects inline colors. Roles and type are in Kotlin. Light or dark follows the app's background setting. The Commons accent and custom colors do not apply to Compose screens. Fonts are bundled from google/fonts (OFL): Funnel Sans, Atkinson Hyperlegible, and Vazirmatn for RTL labels. Message content, previews, drafts, and search excerpts use Atkinson for Latin and Vazirmatn for Arabic-script runs regardless of app language, preserving weight and link styling. Content containing Arabic script uses 1.7em leading; other content uses 1.45em.

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

- **Attention:** unread rows use a small pine signal and remain in the Unread filter until opened or explicitly marked read. Marking a conversation unread by hand marks only its newest received message, as Google Messages does: the row shows a dot, not a count, and the thread opens with no new messages divider. The thread offers a new messages jump control when the reader is away from the latest message.
- **Drafts:** unfinished text stays in its conversation. View 54 shows the retained entry and a draft notice; the component board includes the compact draft treatment.
- **Sending:** the composer distinguishes enabled and disabled states. Sending, sent/delivered, failed, and blocked each have visible text; sending and blocked also use distinct status lines. A failed message offers recovery through its attention state.
- **Keep at top:** the current app's pin action remains as **Keep at top** in conversation actions (view 40). A future implementation should persist its order in the inbox; the concept does not show a special pinned section.
- **Destructive actions:** conversation removal first exposes Undo. Recycle Bin offers restoration, and a separate confirmation surface covers permanent deletion.
- **Unwanted traffic:** Spam review, block rules, blocked numbers, and phrase rules are reachable from Library and settings. Blocked content remains reviewable and recoverable.
- **Search:** entry, populated results, and no matches are separate views. Search spans words, people, and media as expressed in the entry view.
- **Device realities:** dual SIM choice, SMS/MMS limits, group MMS, delivery reports, default SMS role, permissions, local backup, notifications, and app lock have dedicated surfaces or settings.

## Components and variations

In the app, the reusable parts of board `00b` live in `ui/components/`: primary navigation (bar or rail), page app bar, buttons, destination card, info panel, empty state, attention notice, decision option, confirmation dialog, setting rows (section heading, navigation, switch, radio, checkbox), snackbar and `PageScaffold`, which composes a secondary page from them. Compose pages outside the inbox extend `ComposePageActivity`, which applies the theme and shows deletion Undo on the page's snackbar.

### Dialogs

Every dialog, popup and picker in the app is drawn by one component, `AfshDialog` in `ui/components/AfshDialog.kt`; the archive confirmation is the reference. The frame is a 24dp-cornered `surfaceContainer` card at most 400dp wide, 24dp padding, a `headlineSmall` title, body text in `bodyLarge`, 16dp between parts, and the actions last, stacked full width through `DialogActions`: the confirming button above (SECONDARY for an ordinary decision, PRIMARY when the dialog collects a value, DANGER for a permanent action), then a QUIET dismiss button. Pickers pass `wide = true`, which keeps 12dp at the sides so the 360dp calendar fits.

Use the dialog that fits before writing a new one: `ConfirmationDialog` (decide), `MessageDialog` (inform), `ChoiceDialog` (pick one), `RenameDialog` and `TextInputDialog` (type a value). An activity outside one Compose tree calls `confirm`, `inform`, `choose` or `showAfshDialog` from `DialogHost.kt`. Never use Compose `Dialog`, `AlertDialog`, `DatePickerDialog`, Android or Material dialog builders, or a Fossify Commons dialog. `tests/check_dialogs.py` fails on any of them; the one exception is Commons' `SecurityDialog` for app lock, until it has an AFSH version.

Selection mode has one source of truth too. Every selection bar is `SelectionBar` in `ui/components/SelectionBar.kt`: icon-only (each label is the TalkBack description and the long-press tooltip, never visible text), extra actions behind More options, and exactly the navigation bar's height so swapping the two never moves the list. A page built on `PageScaffold` passes a `PageSelection` (count, clear, select all, actions) and gets the whole mode: the "n selected" app bar with Close and Select all, the bar rising over the navigation, and Back clearing the selection. The inbox band and the conversation header keep their own top bars but use the same `SelectionBar` and `selectionTitle`. People are picked with `PeoplePicker` (New group and the conversation's Manage people).

Board `00b` holds navigation, composer, button hierarchy, filter chips, type scale, snackbar, attention notice, empty state, attachment tile, and concrete draft, sending, blocked, disabled, focused, and loading examples. Board `00` holds the palette, type, line language, and system principles. The phone views show light, dark, and RTL conversation variations. The RTL header reverses action order, right-aligns identity, and keeps the message entry at the right edge.

Every numbered phone frame is a fixed 390 × 844 design viewport. Long lists and settings are represented as scrollable content regions in the concept; the exported frame shows the first viewport. Actual scroll physics, keyboard insets, animation, TalkBack traversal, and Android SMS transport behavior still require implementation and device testing.


## Asset provenance

`design/afsh.pen` contains fictional message content and names for visual demonstration.

The family-album image in the media message and full-screen viewer was generated in Pendev with this prompt:

> Documentary overhead photograph of a well-loved family photo album open on a wooden dining table, a few candid printed photographs of ordinary family gatherings tucked into the pages, soft afternoon window light, realistic subtle wear and paper texture, warm natural colors, no readable text, no logos, landscape composition with the album centered.

The generated asset is saved as `design/generated.png` beside the `.pen` file so the image fills can resolve when the canvas is reopened.

The interface icons are editable vector paths from `lucide-static` 1.48.0 (ISC license), replacing font-backed icon nodes that failed to render in the headless Pendev preview. The app's copies are `res/drawable/ic_lucide_*.xml`, generated by `python3 tools/export_ui_icons.py <lucide-static>/icons` (`--check` verifies them); arrows and chevrons mirror under RTL. Material Symbols (Outlined, weight 400, Apache 2.0) sit beside them as `res/drawable/ic_msym_*.xml`, written by `python3 tools/export_material_symbols.py` (`--check` verifies them). `ui/components/AfshIcon.kt` is the one entry point: call sites request an icon by meaning and `LocalIconSet` picks the set (Lucide until the redesign switches it); `tests/check_material_symbols.py` keeps every Lucide icon paired with a symbol.

The app bundles the design's fonts in `app/src/main/res/font/`, copied unmodified from [google/fonts](https://github.com/google/fonts) under the SIL Open Font License 1.1: Funnel Sans (variable), Atkinson Hyperlegible Regular and Bold, and Vazirmatn (variable). The Library nav icon `ic_library_vector.xml` is Lucide `layers` (ISC).


## Commons resources

Every screen is Compose and the app has no XML layouts. Fossify Commons still draws the app-lock dialog, widgets and system surfaces from these resources. Android resources are the source of truth; this document does not keep a second palette or sizing table.

Android resources are the source of truth. This document describes how to use them; it does not maintain a second palette or a second sizing table.

### Ownership

| Definition | Source |
| --- | --- |
| AFSH palette values and the Commons default names that point at them | `app/src/main/res/values/colors.xml` |
| Color roles (Material slots and `Afsh.colors`) | `afshColorScheme` and `AfshTheme` in `app/src/main/kotlin/com/afsh/messages/ui/AfshTheme.kt` |
| Light and dark XML themes for Commons screens, mirroring the roles | `app/src/main/res/values/styles.xml` |
| Component spacing, sizes and corner radii | `app/src/main/res/values/dimens.xml` |
| Shared opacity and user-font-size multipliers | `app/src/main/res/values/design_values.xml` |
| XML theme selection, the primary role for Commons and notifications, and API-compatible float resource access | `app/src/main/kotlin/com/afsh/messages/helpers/Design.kt` |
| Launcher shape | `app/src/main/res/drawable/ic_launcher_foreground.xml` |

### Color

The theme is the one color source. `colors.xml` holds the palette values (paper, ink, pine, lime, lilac, coral and their dark partners); screens never read them directly. `afshColorScheme(dark)` in `ui/AfshTheme.kt` maps them to roles, and everything else reads roles:

- Compose screens read Material slots through `MaterialTheme.colorScheme` and the AFSH roles through `Afsh.colors`.
- XML widgets and Commons screens (the app-lock dialog and screen) use `AppTheme.Afsh.Light` and `AppTheme.Afsh.Dark` in `styles.xml`, whose items mirror the same roles. `SimpleActivity.setTheme()` and Commons' `getThemeId()` (through `App.themeId`) always pick one of them by `afshDark()`.
- Notifications and Commons' stored accent use `primaryRoleColor()`. `Config.applyThemeDefaults()` points the stored accent at the primary role on every start, and Settings calls `syncAccentWithTheme()` after the light or dark choice changes. With the system theme on, `you_primary_color` is overridden to the same role, so the wallpaper never recolors the app.

| Role | Light | Dark | Used for |
| --- | --- | --- | --- |
| `primary` / `onPrimary` | pine / white | `afsh_primary_dark` / ink | Actions, controls, notification accent, Commons accent |
| `primaryContainer` | pine / white | `afsh_pine_2` / white | Only inside the theme; screens use the job roles below |
| `secondaryContainer` | lilac / ink | `afsh_lilac_dark` / lilac | Media and lilac panels |
| `surface`, `background` / `onSurface` | paper / ink | night / paper | Pages |
| `surfaceContainer`, `surfaceContainerHigh` | white, `afsh_surface_high` | `afsh_night_surface`, `afsh_surface_high_dark` | Cards, dialogs, raised rows |
| `onSurfaceVariant`, `outlineVariant` | muted, line | muted dark, line dark | Secondary text, dividers |
| `error`, `errorContainer` | `afsh_error`, `afsh_error_container` | coral, `afsh_error_container_dark` | Destructive actions and failures |
| `inverseSurface`, `inversePrimary` | ink, lime | paper, pine | Snackbar |
| `Afsh.colors.accent` / `onAccent` | lime / ink | lime / ink | New activity, unread |
| `Afsh.colors.header`, `sentBubble`, `secondaryButton`, `swipeAction`, `selected`, `badge` | each a `ColorRole`, today all the pine container | each the dark pine container | One job each; recolor a job by giving its role its own value |
| `Afsh.colors.onSecondaryContainerVariant`, `inverseAccent`, `scrim`, `focusRing`, `avatars`, `onAvatar`, `onBandVariant` | see `AfshTheme` | see `AfshTheme` | Secondary text on lilac, snackbar action, scrims, focus outline, avatars, band text |

Light or dark follows the app's background setting (`afshDark()`), not the system night mode. `tests/check_design_tokens.py` rejects inline colors and opacities, and `primaryContainer` outside the theme. `DesignChecks` asserts that the XML themes, Commons' accent and the Commons screens' theme match these roles. White icon paths are tint masks, not palette choices.

### Spacing

One scale for gaps and padding, read through the theme as `Afsh.spacing.<step>` (defined in `ui/AfshTheme.kt`, like `Afsh.colors`). The steps are the values the screens already use, so adopting them changes nothing visible. Existing literals migrate screen by screen; components built new use the tokens from the start. Sizes (icons, touch targets, avatars) and corner radii are not spacing.

| Step | Value | Use |
| --- | --- | --- |
| `xxs` | 2dp | Hairline offsets, such as a badge nudge or a line gap inside one text block |
| `xs` | 4dp | Tight gaps between an icon and its label or between stacked text lines |
| `sm` | 8dp | Gaps between related controls and the inner padding of compact chips |
| `md` | 12dp | Standard gap between items in a row or list, and inner padding of small cards |
| `lg` | 16dp | Page side padding, default card padding, and gaps between groups |
| `xl` | 20dp | Roomy row padding and gaps between sections |
| `xxl` | 24dp | Dialog padding and gaps between major blocks |
| `xxxl` | 32dp | Empty-state and onboarding breathing room |

### Components

Keep surface-colored app bars, one filled icon family, letter avatars for individual contacts, and group icons for groups. Reserve accent for actions, unread indicators, selected filters and sent messages. Attachment actions share a neutral tinted background.

Use the named bubble and tail radii in `dimens.xml`, the shared minimum touch target for composer actions, and the shared attachment action dimensions. Component-local constraint geometry can remain in its layout; reusable sizes belong in resources.

Keep system typography and the user's font-size choice. List primary, secondary and metadata text use the multipliers in `design_values.xml`. Read and unread rows share their geometry; unread emphasis changes weight and opacity.

### Icon exports and checks

The monochrome launcher icon aliases the foreground vector. `python3 tools/export_icon.py` regenerates the SVG, WebP, store PNGs and `ic_message_bubble.xml` from that vector and `colors.xml`. The notification and new-conversation icons use this bubble without the launcher's adaptive-icon padding. Run the exporter with system Python containing PyGObject/Rsvg and Pillow. Exported assets are generated copies, not editable design sources.

`python3 tests/check_design_tokens.py` rejects inline colors and opacity values. Android `DesignChecks` verifies readable light/dark accents, matching XML/runtime colors, migration and preserved customization. The existing `InboxChecks` covers layout and message behavior.

Conversation day dividers, the temporary sticky date and in-card metadata follow [the thread date behavior](docs/thread-dates.md).
