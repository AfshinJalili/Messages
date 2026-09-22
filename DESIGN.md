# Messages design system

Android resources are the source of truth. This document describes how to use them; it does not maintain a second palette or a second sizing table.

## Ownership

| Definition | Source |
| --- | --- |
| Brand palette, surface colors, semantic action colors and Commons defaults | `app/src/main/res/values/colors.xml` |
| Light and dark widget themes, shared component styles | `app/src/main/res/values/styles.xml` |
| Component spacing, sizes and corner radii | `app/src/main/res/values/dimens.xml` |
| Shared opacity and user-font-size multipliers | `app/src/main/res/values/design_values.xml` |
| Runtime theme selection and API-compatible float resource access | `app/src/main/kotlin/org/fossify/messages/helpers/Design.kt` |
| Launcher shape | `app/src/main/res/drawable/ic_launcher_foreground.xml` |

## Color

Cobalt is the default accent. The launcher and light theme use `brand_cobalt`; the dark theme uses `brand_cobalt_dark` for readable accent text and controls. Both themes use neutral backgrounds. Destructive actions use `design_error`, archive uses cobalt, and mute uses a neutral slate. White icon paths are tint masks, not independent palette choices.

Kotlin screens obtain the current accent, foreground and background through Commons' `getProperPrimaryColor()`, `getProperTextColor()` and `getProperBackgroundColor()`. These respect customization. Use `getContrastColor()` for content on a user-selected fill. Static action colors come from the semantic resources in `colors.xml`. Do not add hex colors or RGB calls to activities, adapters, layouts or drawables.

`SimpleActivity.setTheme()` selects our cobalt theme when the configured accent is cobalt. This keeps XML controls consistent with runtime colors and avoids Commons' green fallback for custom colors. Explicit custom palettes and Material You continue to use Commons' theme selection.

`Config.applyCobaltDefaults()` runs once at application startup. It replaces the old default green, opts implicit system-theme defaults into cobalt, and retains explicit custom color or system-theme selections. Later settings changes are not reset on startup.

## Components

Keep surface-colored app bars, one filled icon family, letter avatars for individual contacts, and group icons for groups. Reserve accent for actions, unread indicators, selected filters and sent messages. Attachment actions share a neutral tinted background.

Use the named bubble and tail radii in `dimens.xml`, the shared minimum touch target for composer actions, and the shared attachment action dimensions. Component-local constraint geometry can remain in its layout; reusable sizes belong in resources.

Keep system typography and the user's font-size choice. List primary, secondary and metadata text use the multipliers in `design_values.xml`. Read and unread rows share their geometry; unread emphasis changes weight and opacity.

## Icon exports and checks

The monochrome launcher icon aliases the foreground vector. `python3 tools/export_icon.py` regenerates the SVG, WebP, store PNGs and `ic_message_bubble.xml` from that vector and `colors.xml`. The notification and new-conversation icons use this bubble without the launcher's adaptive-icon padding. Run the exporter with system Python containing PyGObject/Rsvg and Pillow. Exported assets are generated copies, not editable design sources.

`python3 tests/check_design_tokens.py` rejects inline colors and opacity values. Android `DesignChecks` verifies readable light/dark accents, matching XML/runtime colors, migration and preserved customization. The existing `InboxChecks` covers layout and message behavior.

Conversation day dividers, the temporary sticky date and in-card metadata follow [the thread date behavior](docs/thread-dates.md).
