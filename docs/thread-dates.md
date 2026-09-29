# Thread dates and message metadata

## Goal

Make conversation history easy to scan, with Telegram-style day dividers and a temporary date bubble while scrolling. Keep each message's time and SIM information inside its card instead of interrupting the conversation with repeated timestamps.

## Expected behavior

- Insert one centered divider before the first message of each local calendar day. Show `11 September`, using the device's language for month names. Include the year when it differs from the current year, for example `11 September 2025`.
- A gap between messages or a change of SIM must not create another divider on the same day. Midnight starts a new divider even when messages are only seconds apart. Use the device timezone, including daylight-saving transitions.
- While scrolling, show a date bubble at the top of the message viewport, below the toolbar. Its date follows the messages at the top, in either scroll direction.
- Hide the duplicate sticky bubble while that day's inline divider is still visible. The next day's divider pushes the old sticky bubble away as it approaches the top.
- After scrolling stops, wait 700 ms and fade the sticky bubble over 180 ms. Scrolling makes it visible again. With system animations disabled, hide it without animating. It must not capture taps or cause repeated accessibility announcements.
- Show the time in tiny text beside a numbered SIM-card icon at the bottom end of every message card, including unstarred messages and attachment-only messages. Respect the user's 12/24-hour setting and text-size setting.
- Map SIM identifiers to physical SIM slots. Use blue for SIM 1 and green for SIM 2. Use a neutral `?` icon when the message's subscription cannot be mapped; never guess a slot for an old or removed SIM.
- Keep sent/delivered checks beside the time on outgoing sent messages. Do not imply delivery for incoming, scheduled, failed or pending messages. Retain the existing failed/retry and sending rows. Retain starred indicators.
- Metadata shares the final text line when measured text and metadata fit with an 8dp gap. Short single-line text may widen the bubble to fit the metadata. Otherwise the footer occupies its own row inside the bubble. Attachment, OTP and scheduled-message cards retain a separate footer. Long text, large fonts, RTL text, photos, videos and files must not overlap it. Preserve readable foreground/background contrast in both themes.
- Loading older messages, deleting/undoing messages and refreshing status must preserve correct day grouping and footer content. The unread divider and scroll-to-unread behavior remain available.

## Scope

This changes conversation presentation. It does not change SMS/MMS timestamps, sending, provider data, or delivery semantics. Telegram is the interaction reference; the app retains its existing theme, avatars and message actions.

## Verification

`ThreadDateChecks` covers calendar grouping, formatted labels, footer rendering, delivery changes and sticky date transitions with synthetic messages. Run with the debug app and test APK installed:

```sh
adb shell am instrument --user 0 -w -e class org.fossify.messages.ThreadDateChecks org.fossify.messages.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Also run the existing message-action and unread-navigation checks after changes to the thread list. Device-rendered captures are useful for checking spacing, but do not by themselves prove Telegram pixel or motion parity.

### Results on 22 September 2026

The app and test APK builds, `lintFossDebug`, design-token checks and diff checks passed. Lint retains 102 warnings with the existing baseline unchanged. The initial connected-device run passed eight tests: two date/footer checks, four message-action checks and two unaffected unread checks. The updated debug app is installed on the connected phone.

Native captures were inspected for message/footer placement. The tests also measure received, attachment-only and large RTL cards. A frame-by-frame comparison with Telegram and exhaustive photo/video, theme and SIM combinations were not performed.

### Baseline failures

On 22 September 2026, the broader `RuntimeUxTicketChecks` run exposed two failures that also reproduced with an untouched build of starting commit `55e867bc`:

- `msg14_inboundSmsUpdatesInboxBadgeWhileOnInbox` refers to a removed `MainActivity.refreshInProgress` field.
- `msg15And16_openAtFirstUnreadAndMarkReadAtBottom` fails its initial unread-position assertion.

These existing issues are outside this date/metadata change. They prevent claiming that the whole unread-navigation suite passes.

### Footer refinement

Received cards use their original untinted bubble background, keeping them distinct from the thread. Time text is 65% of message text size, down from 80%. The SIM-card icon contains its number, so color is not the only identifier. Accessibility still announces the full SIM label and time.

### Compact bubble layout

Date pills have equal 12dp gaps to neighboring message cards. Metadata has 12dp horizontal and 8dp bottom clearance. SIM icons are 20% smaller than their original size. Inline placement uses measured text bounds, including Persian and English, and falls back to a separate row when space is insufficient.

The compact-layout follow-up passed the app build, lint, design-token checks and six device tests. Native rendering confirmed inline Persian text, last-line metadata, smaller SIM icons and edge clearance. Large RTL text and attachment fallbacks are included in the layout checks.
