# AFSH Messages

A private, independent SMS/MMS client for Android. Kotlin. The launcher name is just "Messages"; AFSH is the product and design-system name. Started as a modified version of Fossify Messages (GPL-3.0); Fossify Commons is vendored as the `commons/` module and `mmslib` and `IndicatorFastScroll` remain dependencies, and the license and credit stay. Every screen is Jetpack Compose; the app has no XML layouts. Package and app ID `com.afsh.messages`. GitHub: `AfshinJalili/Messages` (private). Push to remote `github`; `origin` is upstream Fossify and is read-only.

This file is the single source of truth for how the project is run. Update it in the same PR that changes the process. Product intent is in `PRODUCT.md`; design system in `DESIGN.md`; behavior specs in `docs/`. Do not add other Markdown files.

## Run

- Build: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` (one variant; no flavors).
- Install: `adb install -r` both APKs from `app/build/outputs/apk`. The debug app is `com.afsh.messages.debug`.
- Instrumented class: `adb shell am instrument --user 0 -w -e class com.afsh.messages.<Class> com.afsh.messages.debug.test/androidx.test.runner.AndroidJUnitRunner`. Read the `OK (n tests)` line; the exit code does not show failures. Wake and unlock the phone first.
- Host checks: `python3 tests/check_design_tokens.py` (no inline colors or opacity), `python3 tests/check_inbox_database.py` (inbox SQL against the Room schema), `python3 tests/check_dialogs.py` (every dialog uses `AfshDialog`; see Dialogs in `DESIGN.md`), `python3 tests/check_material_symbols.py` (every Lucide icon has a mapped Material Symbols icon in `AfshIcon`).
- Icons: `python3 tools/export_icon.py`.
- Release signing: `./gradlew :app:assembleRelease` signs only when `AFSH_STORE_FILE`, `AFSH_STORE_PASSWORD`, `AFSH_KEY_ALIAS` and `AFSH_KEY_PASSWORD` are set in `~/.gradle/gradle.properties` or the environment. Make the key once with `keytool -genkeypair -v -keystore ~/keys/afsh.jks -alias afsh -keyalg RSA -keysize 4096 -validity 10000`, keep it and its passwords outside the repo and backed up, and never commit a keystore (`*.jks` and `*.keystore` are git-ignored). Losing the key means updates need an uninstall.
- CI builds (`.github/workflows/apk.yml`): each PR gets a debug APK on a `pr-<n>` pre-release, linked from a PR comment and deleted when the PR closes, and also builds (but does not publish) the release APK so R8 and baseline-profile failures show up before merge. Each push to `main` publishes a release APK as a GitHub Release, version code `VERSION_CODE` + run number. Repository secrets: `AFSH_KEYSTORE_BASE64` (`base64 -w0 ~/keys/afsh.jks`), `AFSH_STORE_PASSWORD`, `AFSH_KEY_ALIAS`, `AFSH_KEY_PASSWORD` sign releases; without them the release is signed with the debug key and marked pre-release. `DEBUG_KEYSTORE_BASE64` (`base64 -w0 ~/.android/debug.keystore`) makes CI debug builds install over local ones.
- Commons: `commons/` is Fossify Commons 6.1.6 (GPL-3.0) with its package-name, signature, sideload and donation checks removed. Do not reintroduce them; they show a "fake version" dialog to any app id outside `org.fossify.*`.
- Design file: `design/afsh.pen` is encrypted. Open it only through the pen CLI or pencil MCP, never with Read or Grep.

## Workflow

Every change is an issue and a pull request. Nobody pushes to `main`.

1. **Issue.** Use a template (design gap, feature, enhancement, bug). Labels: one type (`design-gap`, `feature`, `enhancement`, `bug`, `chore`), one or more `area:*`, and `needs-design` if the design file does not decide it yet.
2. **Branch and draft PR.** Branch `<issue-number>-<slug>` from `main`. Open a draft PR at once with `Closes #<n>` and label `status:in-progress`. Commits are short imperative sentences.
3. **Build it.** A design gap is designed first in `design/afsh.pen` (`design-gaps` skill) and the owner reviews the design before the app change. Everything else goes straight to code.
4. **Review.** Mark the PR ready and swap the label to `status:in-review`. The reviewer comments on the PR; the coder fixes.
5. **Test.** The tester builds, installs and runs the classes it is given, and comments pass or fail with traces on the PR.
6. **QA.** Label `status:qa`. The coder writes the checklist in the PR body (what to look at, LTR and RTL/Persian where it matters). The owner checks the phone.
7. **Merge.** The owner squash-merges. The issue closes itself. Closing a PR unmerged means won't do; say why in a comment.

Kanban states are labels plus the PR state: no status label = backlog; draft + `status:in-progress`; ready + `status:in-review`; `status:qa`; merged or closed = done. Remove the status label when the PR closes.

## Roles

A role is a job with fixed rules. The model is only a default; the owner or the issue may name another, and the rules stay the same.

| Role | Does | Default |
| --- | --- | --- |
| Coder | Writes code on the issue branch and opens the PR. Does not run test suites. | Claude |
| Designer | Closes design-gap issues in `design/afsh.pen`, then comments the screens and components changed. | Claude (pen CLI) |
| Reviewer | Read-only review of the PR diff against the matching skills. | Grok 4.7 high: `cursor-agent -p --trust --model grok-4.7-high "<prompt>" < /dev/null` |
| Tester | Builds, installs, runs the given test classes, reports pass or fail. | Codex gpt-6-luna, xhigh: `codex exec -m gpt-6-luna -c model_reasoning_effort='"xhigh"' -s danger-full-access -C /home/af/codes/Messages "<prompt>" < /dev/null` |
| QA | Manual check on the phone. Only the owner does this. No agent does manual or exploratory QA. | Owner |

- Always pass `< /dev/null` to agent CLIs, or they wait on stdin.
- No handoffs. Whoever is given build or test work does it directly with gradle and adb. It never starts another agent CLI or passes the work to another model.

## Skills

Read the matching `SKILL.md` in `.agents/skills` (Claude reads the same files through `.claude/skills`) before editing code it covers.

- Repo: `design-gaps`
- Kotlin and UI: `android-clean-architecture`, `compose-multiplatform-patterns`, `kotlin-patterns`, `kotlin-coroutines-flows`, `kotlin-testing`
- Android tooling, when the task matches: `adaptive`, `agp-9-upgrade`, `android-cli`, `android-intent-security`, `android-profiler`, `appfunctions`, `camerax`, `edge-to-edge`, `migrate-xml-views-to-jetpack-compose`, `navigation-3`, `navigation-event`, `r8-analyzer`, `styles`, `testing-setup`, and the other folders there

## The phone is the owner's real phone

adb `RFCRB02MPSR` holds the owner's real messages, and the debug build is the default SMS app. On it, swipe left = **Delete** and swipe right = Archive.

- Test conversations (owner approval, 2026-09-28): in the conversations named `مادربزرگ` and `ماشین` only, tests may send messages, archive (including swipe right), and mark as spam. Restore them afterwards: unarchive, and mark not spam. Delete and block stay forbidden there too.
- Never send, delete, block, or swipe left in any other conversation. Never mark an unread conversation read. Never change app settings, the system language, or other apps. Never empty Archive, Recycle Bin or Spam. Never confirm a destructive dialog.
- Restore anything toggled: mute, pin, rotation, font scale, display size, split-screen. The app language may be switched with `adb shell cmd locale set-app-locales com.afsh.messages.debug --user 0 --locales <tag>`, and must be set back to `fa-IR` afterwards.
- A committed archive is restored through Library → Archive → long-press → Unarchive.
- Never test swipe-right (Archive) on other real rows either. Even a pre-staged Undo tap has missed, and archives committed five times on 2026-09-25. Test archive and undo with the instrumented tests in `InboxChecks` and `DeletionChecks` instead.
- Back with nothing selected leaves the app. Relaunch before the next tap.
- Screenshots and UI dumps contain private messages. Keep them in `.scratch/` (git-ignored). Never commit them.
