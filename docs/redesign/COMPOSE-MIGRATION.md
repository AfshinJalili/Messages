# Compose migration

The Open Line redesign ships one screen at a time in Jetpack Compose, inside the existing activities.

| Screen | Design | State |
| --- | --- | --- |
| Inbox (`MainActivity`) | 01, 02, 23, 34, 41, 57 | Compose, including swipe, selection, drafts, pinning, muting and undo |
| Search | 03, 04, 53 | Old View-based `SearchView` overlay, opened from the Compose search field |
| Library | 17–22 | **Stop-gap:** bottom sheet opening the old Starred/Archive/Spam/Recycle Bin screens. Search `TODO(library)` |
| Everything else | — | XML, Fossify theme |

Theme: `ui/OpenLineTheme.kt`. The palette lives in `res/values/colors.xml` (`open_line_*`) because `tests/check_design_tokens.py` rejects inline colors. Roles and type are in Kotlin. Light or dark follows the app's background setting. The Commons accent and custom colors do not apply to Compose screens. Fonts are bundled from google/fonts (OFL): Funnel Sans, Atkinson Hyperlegible, and Vazirmatn for RTL.

## Open work the inbox needs

- **Library screen (TODO(library)).** Replace the sheet with design screen 17 as a real destination. Then retire the old activities it opens.
- **Search.** Screens 03, 04 and 53 still use the old overlay and old styling.
- **Theme boundary.** Opening a thread, settings or archive switches back to the Fossify look. Decide whether Commons color customization and Material You survive the redesign.
- **Undo snackbars.** Mute and archive use Compose snackbars. Delete goes through `UndoDeletion`, a View snackbar that covers the bottom nav. Move `UndoDeletion` behind an interface the Compose host can render.
- **Dropped behaviour.** Drag-to-select ranges, the fast scroller, the numeric unread badge (only the dot and the subtitle count remain) and the Commons custom font.
- **Known failing test.** `DeletionChecks.conversationStaysHiddenAcrossRefreshAndUndoSurvivesRecreation` fails on the owner's phone. It started after deletion Undo moved into the Compose snackbar host. Logs show Undo is re-offered after recreation, and one run passed while pressing it. The current failure is the `actionLabel` check, and the test also races the 5 s undo window on a large inbox. Fix the test before relying on it.
- **Translations.** The new `inbox_*` strings, `new_message` and the plurals are English only.

## Gaps in the design

Every view, component and state the build had to improvise is listed in [DESIGN-GAPS.md](DESIGN-GAPS.md), with the stand-in that shipped and the decision the design still owes.
