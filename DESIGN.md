# AFSH design system

Personal messaging as a clear, living line between people. The inbox favors sender, latest meaning, and urgency over decorative conversation cards. The palette is Google Material 3 (GM3) system colors — primary blue `#0B57D0` and its tonal surface and container roles — matching Google Messages when dynamic color is off. A fine route line, round contact markers, flat slabs, and strong typography.

The design source is `design/afsh.pen` (encrypted; edit only through the pen CLI or pencil MCP). PNG exports in `design/exports/` are previews only; the canvas is the truth. Screens are fixed 390 × 844 frames and the design decides open questions through issues labeled `design-gap`.

Theme: `ui/AfshTheme.kt`. The palette is Google Material 3 (GM3) system colors defined once in `commons/src/main/res/values/colors.xml` (`m3_*`, with `gm3-sys-color-*` sources cited in that file) because `tests/check_design_tokens.py` rejects inline colors, framework `@android:color/*` (except transparent), and duplicate color names across modules. Roles and type are in Kotlin, and only the theme looks up color resources: screens read `MaterialTheme.colorScheme` or `Afsh.colors` (for example `liftScrim` and `themePreview`), and `tests/check_ui_contract.py` rejects `colorResource` elsewhere. Light or dark follows Appearance → System (device night mode) or the explicit light/dark choice. There is no Material You / dynamic-color path — `afshColorScheme` always reads the static GM3 tokens. The Commons accent and custom color pickers do not apply to Compose screens. Fonts are bundled from google/fonts (OFL): Funnel Sans, Atkinson Hyperlegible, and Vazirmatn for RTL labels. Message content, previews, drafts, and search excerpts use Atkinson for Latin and Vazirmatn for Arabic-script runs regardless of app language, preserving weight and link styling. Content containing Arabic script uses 1.7em leading; other content uses 1.45em.

## Product idea

The inbox acts like an open line: sender, latest meaning, and urgency are visible in one continuous list. Conversations put message text first and leave the composer at the bottom of the phone viewport. The navigation has three destinations: Inbox, Library, and Settings. Library collects saved, archived, spam, and recently deleted conversations so those tasks stay findable without crowding the inbox.

The palette is Google Material 3 (GM3) static system colors (primary blue `#0B57D0` and its tonal roles), the same tokens Google Messages / Gmail / Drive use when dynamic color is off. Color also appears with words, shape, or line weight so status does not depend on hue alone. Sender initials identify contacts when a photo is unavailable.

## Screen map (numbers are frames in the design file)

| Area | Views in the canvas |
| --- | --- |
| Inbox and finding people | 01 everyday inbox, 02 unread, 03 search entry, 04 results, 05 new recipient, 23 delete with Undo, 34 empty inbox, 41 multi-select, 42 invalid number, 53 no search matches |
| Conversations and composing | 06 personal, 07 media, 08 group, 09 details, 10 selected message, 11 attachments, 12 schedule, 13 message details, 14 contact card, 15 thread search, 16 new group, 35 failed send, 36 dark theme, 37 Persian RTL, 39 rename, 40 thread actions, 43 delete confirmation, 44 selectable text, 45 media viewer, 47 scheduled edit, 52 jump to new messages, 54 saved draft |
| Library and safety | 17 Library, 18 Starred, 19 Archive, 20 Spam review, 21 block rules, 22 Recycle Bin, 38 blocked numbers, 50 add blocked phrase, 51 move block rules |
| Settings and setup | 24 overview, 25 appearance, 26 writing and sending, 27 notifications, 28 privacy and lock, 29 backup and restore, 30 export, 31 import review, 32 default SMS setup, 33 permissions, 46 app lock, 48 About, 49 gestures and dates |

## Behavior decisions

- **Attention:** unread rows use a small primary signal and remain in the Unread filter until opened or explicitly marked read. Marking a conversation unread by hand marks only its newest received message, as Google Messages does: the row shows a dot, not a count, and the thread opens with no new messages divider. The thread offers a new messages jump control when the reader is away from the latest message.
- **Drafts:** unfinished text stays in its conversation. View 54 shows the retained entry and a draft notice; the component board includes the compact draft treatment.
- **Sending:** the composer distinguishes enabled and disabled states. Sending, sent/delivered, failed, and blocked each have visible text; sending and blocked also use distinct status lines. A failed message offers recovery through its attention state.
- **Keep at top:** **Pin to the top** and **Unpin** actions persist the pinned order at the top of the inbox. Pinned conversations appear in a dedicated section above unpinned ones.
- **Destructive actions:** conversation removal first exposes Undo. Recycle Bin offers restoration, and a separate confirmation surface covers permanent deletion.
- **Unwanted traffic:** Spam review, block rules, blocked numbers, and phrase rules are reachable from Library and settings. Blocked content remains reviewable and recoverable.
- **Search:** entry, populated results, and no matches are separate views. Search spans words, people, and media as expressed in the entry view.
- **Device realities:** dual SIM choice, SMS/MMS limits, group MMS, delivery reports, default SMS role, permissions, local backup, notifications, and app lock have dedicated surfaces or settings.

## Components and variations

In the app, the reusable parts of board `00b` live in `ui/components/`: primary navigation (Material 3's navigation bar, or its navigation rail on wide and short windows; items are tabs and icons come from `AfshIcon`), page app bar, buttons, destination card, info panel, empty state, attention notice, confirmation dialog, setting rows (section heading, navigation, switch, radio), snackbar and `PageScaffold`, which composes a secondary page from them. Buttons and setting rows sit on stock Material 3 parts with colours from theme roles: `AfshButton` on `Button` (primary, danger) and `FilledTonalButton` (secondary, quiet), `FloatingButton` on `ExtendedFloatingActionButton` (new message, jump to latest), `SendButton` on `FilledIconButton`, and every setting row on `ListItem` with a stock `Switch` or `RadioButton`. The whole row is the target, found in tests by its label and role. The page app bar is the stock Material 3 `TopAppBar` in the header role, with pinned scroll behavior wired by `PageScaffold`; screens never build their own top bar. Compose pages outside the inbox extend `ComposePageActivity`, which applies the theme and shows Undo on the page's snackbar (deletion Undo and other undoable actions). `ThreadActivity` hosts its own snackbar for message actions and manages its Compose content directly. Shared UI components include `ConversationRow` (inbox list item) and `Avatar` (contact/group photo or initials with `AfshIcon.Person`/`AfshIcon.People` fallback icons).

### Dialogs

Every dialog, popup and picker in the app is drawn by one component, `AfshDialog` in `ui/components/AfshDialog.kt`, which is the stock Material 3 `AlertDialog` with its theme roles: a `surfaceContainerHigh` card in the `extraLarge` shape (28dp), 24dp padding, a `headlineSmall` title and `bodyLarge` text, 16dp between parts of the content, and the content scrolling when short of room. The actions are Material text buttons at the end: the dismiss label, then the confirming action passed as a `DialogConfirm` (painted in the `error` role when `destructive`, disabled until the dialog has a valid value). Fields in a dialog are the stock `OutlinedTextField`, with the explanation or the error as its supporting text. The calendar is `AfshDatePickerDialog`, the stock Material 3 `DatePickerDialog` with the same actions; the clock is a `TimePicker` inside `AfshDialog`.

Use the dialog that fits before writing a new one: `ConfirmationDialog` (decide), `MessageDialog` (inform), `ChoiceDialog` (pick one), `RenameDialog` and `TextInputDialog` (type a value). An activity outside one Compose tree calls `confirm`, `inform`, `choose` or `showAfshDialog` from `DialogHost.kt`. Never use Compose `Dialog`, `AlertDialog`, `DatePickerDialog`, `TimePickerDialog`, Android or Material dialog builders outside `AfshDialog.kt`, or a Fossify Commons dialog. `tests/check_dialogs.py` fails on any of them, and on a hand-built frame (window `Dialog`, `Surface` card or fixed corner) inside `AfshDialog.kt`. App lock uses `SecuritySetupDialog` (Compose, in `ui/applock/`), and `SplashActivity` uses the AndroidX SplashScreen API.

System bar icons follow the theme, never a constant. Each activity calls `AfshSystemBars(window)` (`ui/SystemBars.kt`) inside its `AfshTheme`: the status bar icons take whichever of black or white has more contrast on the `header` role, and the navigation bar icons do the same on `surface`. Recoloring the header needs no status bar change.

### Sheets

Every bottom sheet is drawn by one component, `AfshSheet` in `ui/components/AfshSheet.kt`, on the stock Material 3 modal bottom sheet: a 28dp-topped `surfaceContainer` sheet that opens fully expanded, with the stock drag handle in `outlineVariant`, the scrim, drag and Back to dismiss, and system bar and keyboard insets. Its content scrolls when short of room. The sheet's `title` names the pane for TalkBack and tests and shows as a `titleLarge` heading; the message sheet hides it because its first line is the date · time · SIM line, and drops the scrim because the lifted bubble dims the page itself. Rows are `AfshSheetItem` on the Material 3 list item: a leading icon in `primary` (on a `secondaryContainer` tile in the attach sheet), the label, an optional supporting line, and the error colour for Delete. A row acts through `dismissThen`, so the sheet slides away before a dialog, picker or page opens, and a cancelled hide never acts. Never use `ModalBottomSheet` or `BottomSheetScaffold` directly; `tests/check_sheets.py` fails on them.


Selection mode has one source of truth too. Every selection bar is `SelectionBar` in `ui/components/SelectionBar.kt`, the stock Material 3 `BottomAppBar` on `surfaceContainer` (`tests/check_material_components.py` fails on a hand-built frame): icon-only (each label is the TalkBack description and the long-press tooltip, never visible text), extra actions behind More options, and exactly the navigation bar's height so swapping the two never moves the list (over the composer it takes the composer frame's height instead). That height is written once, as `BottomBarHeight` in `Navigation.kt`; every bar that stands in for the navigation reads it. A page built on `PageScaffold` passes a `PageSelection` (count, clear, select all, actions) and gets the whole mode: the "n selected" app bar with Close and Select all, the bar rising over the navigation, and Back clearing the selection. The inbox band keeps its own top bar, and the conversation's top bar is `ThreadTopBar` in `ui/thread/ThreadTopBar.kt`, a stock Material 3 `TopAppBar` on the header color role that grows with the font scale; both use the same `SelectionBar` and `selectionTitle`. People are picked with `PeoplePicker` (New group and the conversation's Manage people).

Board `00b` holds navigation, composer, button hierarchy, filter chips, type scale, snackbar, attention notice, empty state, attachment tile, and concrete draft, sending, blocked, disabled, focused, and loading examples. Board `00` holds the palette, type, line language, and system principles. The phone views show light, dark, and RTL conversation variations. The RTL header reverses action order, right-aligns identity, and keeps the message entry at the right edge.

Every numbered phone frame is a fixed 390 × 844 design viewport. Long lists and settings are represented as scrollable content regions in the concept; the exported frame shows the first viewport. Actual scroll physics, keyboard insets, animation, TalkBack traversal, and Android SMS transport behavior still require implementation and device testing.


## Asset provenance

`design/afsh.pen` contains fictional message content and names for visual demonstration.

The family-album image in the media message and full-screen viewer was generated in Pendev with this prompt:

> Documentary overhead photograph of a well-loved family photo album open on a wooden dining table, a few candid printed photographs of ordinary family gatherings tucked into the pages, soft afternoon window light, realistic subtle wear and paper texture, warm natural colors, no readable text, no logos, landscape composition with the album centered.

The generated asset is saved as `design/generated.png` beside the `.pen` file so the image fills can resolve when the canvas is reopened.

### Icons

Every interface icon is a Material Symbol (Rounded, weight 400, grade 0, optical size 24, Apache 2.0) in `commons/.../res/drawable/ic_msym_*.xml`, written by `python3 tools/export_material_symbols.py` (`--check` verifies them against upstream). One copy only: commons holds them and the app depends on commons. Arrows and chevrons mirror under RTL. Icons use FILL 0 by default; filled variants (FILL 1, `ic_msym_*_fill`) are used for selected/active states as in Google Messages: selected bottom navigation destinations, pinned row signals, and starred messages. Unpin uses `keep_off`; the unstar action uses `StarFilled`. `ui/components/AfshIcon.kt` is the Compose entry point (`org.fossify.commons.R.drawable`): call sites request an icon by meaning (`AfshIcon.Search.painter()`), never a drawable id, and pass `filled = true` where applicable; XML-only surfaces (notifications, shortcuts, commons menus) use the same `ic_msym_*` drawables. To add an icon, add its symbol to the exporter, run it, and add the `AfshIcon` entry. `tests/check_material_symbols.py` fails on an entry without a shipped symbol, a shipped symbol no entry uses, any `ic_msym_*` under `app/res`, or a non-`ic_msym_` vector outside the documented exceptions (launcher foreground/monochrome and the notification message-bubble brand mark). `tests/check_ui_contract.py` fails on Lucide, material-icons `Icons.*`, inline `ImageVector.Builder`, or `painterResource(R.drawable…)` outside `AfshIcon`. The design file still draws the earlier icon paths until it is redrawn.

### Fonts

The app bundles the design's fonts in `app/src/main/res/font/`, copied unmodified from [google/fonts](https://github.com/google/fonts) under the SIL Open Font License 1.1: Funnel Sans (variable), Atkinson Hyperlegible Regular and Bold, and Vazirmatn (variable).


## Commons resources

Every screen is Compose and the app has no XML layouts. Fossify Commons provides XML theme definitions and resources for internal helper screens; the app's Compose UI does not use Commons layouts. Android resources are the source of truth; this document does not keep a second palette or sizing table.

Android resources are the source of truth. This document describes how to use them; it does not maintain a second palette or a second sizing table.

### Ownership

| Definition | Source |
| --- | --- |
| GM3 palette values (`m3_*` light and `m3_*_dark`) | `commons/src/main/res/values/colors.xml` |
| Color roles (Material slots and `Afsh.colors`) | `afshColorScheme` and `AfshTheme` in `app/src/main/kotlin/com/afsh/messages/ui/AfshTheme.kt` |
| Light and dark XML themes for Commons screens, mirroring the roles | `app/src/main/res/values/styles.xml` |
| Component spacing | `AfshSpacing` in `app/src/main/kotlin/com/afsh/messages/ui/AfshTheme.kt` (`Afsh.spacing`) |
| Component sizes | `app/src/main/res/values/dimens.xml` (notification icon, keyboard height) |
| Corner radii (Material shape scale and named component corners) | `AfshShapes` and `AfshCorners` in `app/src/main/kotlin/com/afsh/messages/ui/AfshTheme.kt` |
| Shared opacity and font scale | `app/src/main/res/values/design_values.xml` (`opacity_surface_tint`); font scale via `afshTextScale()` in `ui/AfshTheme.kt` |
| XML theme selection, the primary role for Commons and notifications, and API-compatible float resource access | `app/src/main/kotlin/com/afsh/messages/helpers/Design.kt` |
| Launcher shape | `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| Screen and tab motion (durations, parallax, tab shift) | `app/src/main/res/values/motion.xml` |
| Screen and tab motion easing | `app/src/main/res/interpolator/emphasized_decelerate.xml` and `EmphasizedDecelerate` in `ui/components/Motion.kt` |
| Activity screen transitions | `AfshWindowAnimation` in `app/src/main/res/values/styles.xml` → `res/anim/screen_*.xml` (and `res/anim-ldrtl/` for RTL); exit/enter anims use `fillAfter`/`fillBefore` so the old window never snaps back for a frame |
| Opaque window chrome | `android:windowBackground` = surface role on `AppTheme.Afsh.Light` / `Dark` (incoming window solid before Compose draws) |
| Task-root (inbox) theme | `AppTheme.Afsh.Root.Light` / `Root.Dark` (`windowAnimationStyle` null) via `afshXmlTheme(root = true)` |
| Compose screen and tab transitions | `ScreenStack` and `TabSwitch` in `ui/components/Motion.kt` |

### Motion

Screen and tab navigation share one motion language: fast, directional, and without alpha crossfade (Telegram / Google Messages style). Dialogs and bottom sheets keep Material 3 motion and are out of scope here.

Inbox, Library, and Settings are three tabs of `MainActivity` (`TabSwitch` + `SettingsController`). Pushed screens (conversation, Library pages, block rules, …) remain Activities and use the theme window animations. Leaving a pushed screen for a top-level tab uses `MainActivity.topLevelIntent` (`CLEAR_TOP` / `SINGLE_TOP`), which is a screen close — not a tab switch — and then shows the chosen tab.

| Motion | Behaviour | Duration | Easing |
| --- | --- | --- | --- |
| Open screen (Activity theme or in-Activity `ScreenStack`) | New screen slides in from the end edge; the screen underneath shifts toward the start by `motion_parallax` (25% of width) | `motion_open_ms` (220ms) | Emphasized decelerate |
| Close screen (Back, up, predictive back) | Exact reverse of open | `motion_close_ms` (220ms) | Emphasized decelerate |
| Switch bottom-nav / rail tabs (`TabSwitch`) | Both panes shift by `motion_tab_shift` (30% of width) in the tab-order direction | `motion_tab_ms` (120ms) | Emphasized decelerate |

RTL mirrors start/end automatically: Compose uses `SlideDirection.Start`/`End`, and Activity XML ships mirrored sets under `anim-ldrtl/`. Predictive back on Android 14+ drives the same close motion for Activities (theme `windowAnimationStyle`) and for in-Activity stacks (`SeekableTransitionState` in `ScreenStack`).

Do not invent per-screen transition specs, `Crossfade`, or `fadeIn`/`fadeOut` for navigation, and do not call `overridePendingTransition` / `overrideActivityTransition`. Change tokens in `motion.xml`, the shared specs in `Motion.kt` / `res/anim`, and `AfshWindowAnimation` only. `tests/check_motion.py` fails on navigation fades, ad-hoc screen transition specs outside `Motion.kt`, alpha in `screen_*.xml`, a revived `ScreenMotion` / `SettingsActivity`, or missing theme window animations.

### Color

The theme is the one color source. `commons/.../colors.xml` holds the Material 3 static baseline (`m3_*` light and `m3_*_dark`); screens never invent new names. `afshColorScheme(dark)` in `ui/AfshTheme.kt` maps them to every Material slot, and everything else reads roles or the same `m3_*` names:

- Compose screens read Material slots through `MaterialTheme.colorScheme` and the AFSH job roles through `Afsh.colors`.
- XML widgets and Commons screens (the app-lock dialog and screen) use `AppTheme.Afsh.Light` and `AppTheme.Afsh.Dark` in `styles.xml`, whose items mirror the same roles. `SimpleActivity.setTheme()` and Commons' `getThemeId()` (through `App.themeId`) always pick one of them by `afshDark()`.
- Notifications and Commons' stored accent use `primaryRoleColor()`. `Config.applyThemeDefaults()` defaults Appearance to System and points the stored accent at the primary role on every start; Settings calls `syncAccentWithTheme()` after the light or dark choice changes. Commons' `isDynamicTheme()` is always false, so the wallpaper never recolors the app.

| Role | Light | Dark | Used for |
| --- | --- | --- | --- |
| `primary` / `onPrimary` | `#0B57D0` / `#FFFFFF` | `#A8C7FA` / `#062E6F` | Actions, FAB/send accent, notification, Commons accent |
| `primaryContainer` / `onPrimaryContainer` | `#D3E3FD` / `#0842A0` | `#0842A0` / `#D3E3FD` | Sent bubbles, swipe archive panel |
| `secondary` / `secondaryContainer` | `#00639B` / `#C2E7FF` | `#7FCFFF` / `#004A77` | Less-emphasis controls; selected chips |
| `tertiary` / `tertiaryContainer` | `#146C2E` / `#C4EED0` | `#6DD58C` / `#0F5223` | Accent balance; avatar fills |
| `surface` / `onSurface` | `#FFFFFF` / `#1F1F1F` | `#131314` / `#E3E3E3` | Pages, conversation list, top bars (`Afsh.colors.header`) |
| `surfaceContainer*` | GM3 lowest→highest | GM3 lowest→highest | Cards, sheets, nav; received bubbles use `surfaceContainerHighest` |
| `outline` / `outlineVariant` | `#747775` / `#C4C7C5` | `#8E918F` / `#444746` | Borders, dividers |
| `error` family | `#B3261E` … | `#F2B8B5` … | Destructive actions and failures |
| `inverseSurface` / `inversePrimary` | `#303030` / `#A8C7FA` | `#E3E3E3` / `#0B57D0` | Snackbar |
| `Afsh.colors.accent` / `onAccent` | primary / onPrimary | primary / onPrimary | Send, FAB, primary buttons |
| `Afsh.colors.badge` | primary / onPrimary | primary / onPrimary | Unread count |
| `Afsh.colors.avatars` | secondary/tertiary/primary containers | same roles | Letter avatars |

**No dynamic color.** `afshColorScheme` has a single static path. `ColorSourceTest` locks the GM3 primary / onPrimary accent pair.

Light or dark: `afshDark()` follows system night mode when Appearance is System, otherwise the stored light/dark background. `tests/check_design_tokens.py` rejects hardcoded colors outside `colors.xml` / `AfshTheme.kt`, and `tests/check_ui_contract.py` rejects `primaryContainer` outside the theme. `DesignChecks` asserts that the XML themes, Commons' accent and the Commons screens' theme match these roles. White icon paths are tint masks, not palette choices.

### Spacing

One scale for gaps and padding, read through the theme as `Afsh.spacing.<step>` (defined in `ui/AfshTheme.kt`, like `Afsh.colors`). The steps are the values the screens already used; the one off-scale gap left (6dp between a row's pin, mute and unread signals) became `xs`. Every `padding`, `spacedBy` and `PaddingValues` in the app takes a step (`0.dp` is allowed), and `tests/check_ui_contract.py` fails on a literal dp in one anywhere outside `ui/AfshTheme.kt`. A padding shared at file level is a `@Composable get()` property, since the steps are read through the theme. A geometry value off the scale inside a drawn miniature (such as the theme swatch) is a named size, not spacing. Sizes (icons, touch targets, avatars) and corner radii are not spacing.

| Step | Value | Use |
| --- | --- | --- |
| `xxs` | 2dp | Hairline offsets, such as a badge nudge or a line gap inside one text block |
| `xs` | 4dp | Tight gaps between an icon and its label or between stacked text lines |
| `sm` | 8dp | Gaps between related controls and the inner padding of compact chips |
| `md` | 12dp | Standard gap between items in a row or list, and inner padding of small cards |
| `lg` | 16dp | Page side padding, default card padding, and gaps between groups |
| `xl` | 20dp | Roomy row padding and gaps between sections |
| `xxl` | 24dp | Gaps between major blocks |
| `xxxl` | 32dp | Empty-state and onboarding breathing room |

### Components

App bars use the header color role (deep pine). Every interface icon comes from Material Symbols Outlined (weight 400). Lime accents the primary action button, send button, new message FAB, selected schedule chip, and new activity signals. Pine colors badges, selected items, and sent message bubbles. Attachment action tiles use `secondaryContainer`. Letter avatars identify individual contacts; group icons mark groups.

Corners come from the theme's shape scale, never a literal `RoundedCornerShape` in UI code (`tests/check_ui_contract.py` fails on one). Use `MaterialTheme.shapes` (extraSmall 4dp, small 12dp, medium 16dp, large 22dp, extraLarge 28dp) for Material roles, and `AfshCorners` for corners without a slot: message bubbles (16dp, 4dp tail), every bottom sheet top (28dp) and the SIM badge. Use the shared minimum touch target for composer actions, and the shared attachment action dimensions. Component-local constraint geometry can remain in its layout; reusable sizes belong in resources.

Every shared component in `ui/components/` is stock Material 3 or a thin wrapper over it, and draws no surface of its own (`tests/check_material_components.py` fails on `BasicTextField`, `.background(` or `.border(` there). Destination card is a `Card` holding a `ListItem`; info panel, empty state, attention notice, import notice and identity card are `Card`s; action tiles are clickable `Card`s; search field and text field are the filled `TextField` with `afshFieldColors` (no indicator at rest, the `focusRing` role as the focused indicator); count badges are `Badge`; the date pill is a round `Surface`; the unread line is two `HorizontalDivider`s; snackbars are `Snackbar`. The first-run import notice (`ImportNotice`) is one continuous card for the whole setup after the app becomes the default SMS app: a fixed label (`import_progress_title`), one progress bar, and a tabular `import_progress_percent` sized for `100%` on the same row. Conversation reconcile and message/search indexing share that single weighted percent (it only rises); the card never shows an X-of-Y count, never clears between stages, and disappears once at 100% when the inbox is ready (`tests/check_import_progress.py`). The few without a stock equivalent, each for one reason:

- `SelectionCheck`: Material 3's `Checkbox` is square and the design's selection check is round.
- `DetailRow`: `ListItem`'s trailing slot cannot let a long value wrap at the row's end.
- `LabeledField`: its caption and value line up with the identity card's text inset, which `ListItem`'s 16dp inset breaks.
- `DisabledComposer`: a switched-off copy of the composer's own `Surface`s; Material 3 has no disabled composer.
- `SwipeableRow`, `SelectableBubbleRow`, `PageScaffold`, `Motion` (`ScreenStack`, `TabSwitch`): layout, gesture and navigation-motion wiring around stock `SwipeToDismissBox`, `Scaffold` and Compose animation, not surfaces.

Message bubbles and the composer are built from stock Material 3 components (`tests/check_material_components.py` fails on a hand-drawn one). Every bubble, in the conversation, Spam and message details, is `MessageBubble`: a `Surface` whose color role comes from `BubbleTone` (received, sent, failed, selected; selection wins) and whose shape comes from `AfshCorners.bubble`. Only `MessageBubble.kt` reads the sent-bubble role. The composer band is a `Surface`; the field is a filled `TextField` without an indicator, the attach and SIM controls are `IconButton`s, send is a `FilledIconButton`, the length pill, tiles and remove dot are `Surface`s; the attach sheet is the shared `AfshSheet`. Compose tests find composer controls by their label and role, as TalkBack does.

Keep system typography and the user's font-size choice via `afshTextScale()`. Read and unread rows share their geometry; unread emphasis changes weight and opacity. The inbox can show an optional unread count badge on the navigation tab (controlled by `show_inbox_unread_count` in config; `NavBadges` and `NavCounts` in `Navigation.kt`).

### Icon exports and checks

The adaptive foreground vector (`ic_launcher_foreground.xml`) is the only hand-edited logo shape. The adaptive monochrome layer references that same drawable. `python3 tools/export_icon.py` regenerates `graphics/icon.svg`, the WebP/store PNGs, `graphics/icon.stamp` and `ic_message_bubble.xml` from that vector and `commons/.../colors.xml`. `--check` (stdlib only) diffs the text outputs and compares the stamp's input hash so stale rasters are caught without pixel-diffing or Rsvg. The notification small icon is the same mark without adaptive-icon padding. Writing rasters needs system Python with PyGObject/Rsvg and Pillow. Exported assets are generated copies, not editable design sources.

`python3 tests/check_design_tokens.py` rejects inline colors and opacity values. `python3 tests/check_spacing_tokens.py` verifies that every spacing step in `AfshSpacing` is documented and used. `python3 tests/check_import_progress.py` keeps the first-run import notice as one rising percent with a fixed label. Android `DesignChecks` asserts only on theme roles: the XML themes and Commons' accent match the Compose roles in light and dark, an older stored accent moves to the primary role, and primary stays readable on the surface. No check names a palette value.

Conversation day dividers, the temporary sticky date and in-card metadata follow [the thread date behavior](docs/thread-dates.md).
