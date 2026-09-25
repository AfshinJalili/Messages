# Agent instructions

Skills for this repo live in `.agents/skills`. Cursor and Codex read that folder directly. Claude reads the same files through symlinks in `.claude/skills`.

Read the matching `SKILL.md` before editing code it covers, and follow it wherever it applies.

## Kotlin and UI

- `android-clean-architecture` — Android layering (data, domain, UI), modules, ViewModels
- `compose-multiplatform-patterns` — Jetpack Compose and Compose Multiplatform UI patterns
- `kotlin-patterns` — common ways to write Kotlin
- `kotlin-coroutines-flows` — coroutines, Flow, StateFlow
- `kotlin-testing` — Kotest, MockK, Kover coverage

## Android tooling

Use these when the task matches:

`adaptive`, `agp-9-upgrade`, `android-cli`, `android-intent-security`, `android-profiler`, `appfunctions`, `camerax`, `display-glasses-with-jetpack-compose-glimmer`, `edge-to-edge`, `engage-sdk-integration`, `leanback-to-compose-tv-migration`, `media3-cast-integration`, `migrate-xml-views-to-jetpack-compose`, `ml-kit-genai-prompt-api`, `navigation-3`, `navigation-event`, `play-billing-library-version-upgrade`, `play-policy-insights`, `r8-analyzer`, `restore-credentials`, `styles`, `testing-setup`, `verified-email`, `wear-compose-m3`

## Review and QA workflow

The coding agent writes code only. Review and device QA go to other agents:

- **Code review:** Cursor `grok-4.7-high`, read-only, applying the matching skills above. `cursor-agent -p --trust --model grok-4.7-high "<prompt>" < /dev/null`
- **Manual QA:** OpenCode `opencode/mimo-v2.6-flash-free` on the phone over adb. `opencode run --auto -m opencode/mimo-v2.6-flash-free "<prompt>" < /dev/null`. QA covers every scenario in `docs/qa/inbox-scenarios.md` in LTR and RTL/Persian, portrait and landscape, a short screen, and split-screen. It records scenarios and findings as it goes.

Always pass `< /dev/null` to these CLIs, or they wait on stdin.

### QA runs on the owner's real phone

The device (adb `RFCRB02MPSR`) holds the owner's real messages, and the debug build is the default SMS app. On it, swipe left = **Delete** and swipe right = Archive.

- Never send, delete, block, or swipe left. Never mark an unread conversation read. Never change app settings, the system language, or other apps. Never empty Archive, Recycle Bin or Spam. Never confirm a destructive dialog.
- Restore anything toggled: mute, pin, rotation, font scale, display size, split-screen. The app language may be switched with `adb shell cmd locale set-app-locales org.fossify.messages.debug --user 0 --locales <tag>`, and must be set back to `fa-IR` afterwards.
- A committed archive is restored through Library → Archive → long-press → Unarchive.
- Never test swipe-right (Archive) on real rows either. Even a pre-staged Undo tap has missed, and archives committed five times on 2026-09-25. Test archive and undo with the instrumented tests in `InboxChecks`/`DeletionChecks` instead.
- Back with nothing selected leaves the app. Relaunch before the next tap.
