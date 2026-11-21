# AFSH Messages

A private SMS/MMS client for Android. Kotlin and Jetpack Compose. The launcher name is Messages. AFSH is the product and design-system name. Package and app ID `com.afsh.messages`. Languages are English (`values/`) and Persian (`values-fa/`). GitHub: `AfshinJalili/Messages` (private). Push to remote `github`.

This file is how the project is run. Update it in the same change that changes the process.

## Constraints

When two constraints conflict, pick the lowest future maintenance cost and name the tradeoff in the commit.

- Separation of concerns. Policy lives in focused Kotlin helpers. Persistence lives in DAOs, database migrations and provider access. Presentation lives in Compose and UI state. Android entry points coordinate calls. Put a concern where this repo already puts it.
- Encapsulation. Public contracts only. Read another owner's tables and caches through its operations. Keep mutation and invalidation together. Do not add a second cache or writer.
- One rule change touches one owner. An owner is a focused file or package, not necessarily a Gradle module.
- Grep before writing logic. One home per rule, threshold, format or schema fact. Do not abstract coincidental similarity.
- Simplest working shape. Function over class. No speculative hooks, flags or frameworks.
- If you describe a type with "and", split it. Names say intent. Comments explain constraints and non-obvious decisions.
- Domain code takes and returns plain values. Android types stay at the boundary that already uses them.
- Composition over inheritance. Open a type for extension only after the same change has happened twice. No `a.b.c.d`. Validate at the edge. A failed read or write stays a failure. Prefer boring, existing platform APIs.

### One owner

Extend the existing owner and route callers through it.

- Incoming spam: `IncomingSpamClassifier` and `RuleBasedFilter`.
- Inbox category and ordering: `InboxFilter.kt`.
- Inbox refresh and reconciliation: `InboxRepository` and `InboxReconciler`.
- Search normalization and matching: `SearchText.kt`.
- Pending removal and Undo: `UndoDeletion`.
- Shared Compose presentation: `ui/components/`.
- Fonts and typography: `ui/AfshTheme.kt` (all 15 Material 3 typography slots, `tabular()`, `forPattern()`, `forFieldContent()`, `ContentFontVisualTransformation`, `PatternFontVisualTransformation`) and `ui/components/ContentText.kt` (`ContentText`, `PatternText`, `MixedLocaleText`). Never use `FontFamily` outside `ui/AfshTheme.kt`; see Fonts in `DESIGN.md`.
- Design tokens: `DESIGN.md`.

Before changing a rule, find its implementation and every caller, including SMS/MMS receivers and notification actions. Consolidate copies of that rule first. A small fix stays a small fix.

A new rule goes in its owner's file. Create a new file only when that owner cannot hold it. A function-level import used to dodge a cycle means the logic is in the wrong file.

### One definition

On the second use of existing logic: move it to the shared owner, switch the original caller with tests green and no behaviour change, then build the new use. Grep the expression first and list every copy. The move switches them all, or the commit names each copy left and why. Guards, ordering, identity and results stay. A behaviour change is its own commit. The same applies to a schema fact and to a repeated piece of UI.

Telephony records, Room caches and app-local metadata have different owners. For changed state, name the authoritative store, its writers and how the cache refreshes. Reconciliation keeps app-owned metadata.

SMS and MMS IDs can overlap. Preserve transport identity in selection, read tracking and actions. Preserve the original text when normalizing it for search.

### Rendering displays policy

Composables render state and emit events. They do not write provider or database state, and they do not decide spam, retention, read-state or sending. UI state may derive labels, grouping, selection and layout. Who sees a value is decided in the owner. Action visibility does not replace validation in the operation that performs the action.

Validate external inputs at Android entry points and import boundaries. Propagate cancellation.

### Changes

Refactor first, then change. Keep a behaviour-preserving refactor and a behaviour change out of one unverifiable diff.

A rule that matters is enforced by CI or a hook. Extend the smallest existing check that detects the regression. State which gates ran and which acceptance conditions remain unverified.

Use synthetic data or a disposable device for destructive cases. Wake and unlock the phone before instrumentation.

## Run

- Build: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` (one variant; no flavors).
- Install: `adb install -r` both APKs from `app/build/outputs/apk`. The debug app is `com.afsh.messages.debug`.
- Instrumented class: `adb shell am instrument --user 0 -w -e class com.afsh.messages.<Class> com.afsh.messages.debug.test/androidx.test.runner.AndroidJUnitRunner`. Read the `OK (n tests)` line; the exit code does not show failures. Wake and unlock the phone first.
- Instrumented checks (`app/src/androidTest`) find nodes by role, label, text or test tag, never by layout position or palette value. Shared helpers: `Fixtures.kt`, `Matchers.kt`, `TestScreens.kt` (`TestTheme`, `TestInbox`), `DesignVariants.kt` (`VariantCapture`), `Waits.kt`, `SchemaDatabase.kt`, `IsolatedPreferences.kt`. A check that writes settings uses `IsolatedPreferences`, never `context.config`. Logic that needs no device goes in the unit tests.
- Unit tests: `./gradlew :app:testDebugUnitTest` (JVM, `app/src/test`). State holders such as `ThreadStateHolder` are tested through their interface with a fake host. Spam-filter accuracy (`SpamFilterAccuracyTest`) downloads PSSC v0.3 (Salari & Shayegan 2023, DOI 10.5281/zenodo.7832188, md5-checked) and UCI SMS Spam Collection (CC BY 4.0, sha256-checked) into `~/.cache/afsh-messages/spam-corpora` (override with `SPAM_CORPORA_CACHE`); see `app/src/test/resources/spam-corpora/README.md`. Do not commit those corpora.
- Host checks, all run by `tools/verify.sh`: `tests/check_design_tokens.py` (no hardcoded colors outside `colors.xml` / `AfshTheme.kt`), `tests/check_ui_contract.py` (no Lucide, no literal spacing dp, no hand-built corner radius, no direct color resource lookup, no `primaryContainer` outside the theme, no `FontFamily` outside `AfshTheme.kt`, no manual `.withContentFonts()`/`.forLabel()` outside `ContentText.kt`, and every `BasicTextField`/`OutlinedTextField` must pass `visualTransformation`), `tests/check_inbox_database.py`, `tests/check_dialogs.py` (`AfshDialog`), `tests/check_sheets.py` (`AfshSheet`), `tests/check_spacing_tokens.py`, `tests/check_material_symbols.py`, `tests/check_material_components.py`, `tests/check_motion.py`, `tests/check_battery.py` (the default-SMS wake path must not full-reconcile on every message). Dialog, sheet, component, font and motion rules are specified in `DESIGN.md`.
- Icons: `python3 tools/export_icon.py` regenerates `graphics/*`, `icon.stamp` and `ic_message_bubble.xml` from `ic_launcher_foreground.xml`. `--check` is stdlib-only.
- Release signing: `./gradlew :app:assembleRelease` signs only when `AFSH_STORE_FILE`, `AFSH_STORE_PASSWORD`, `AFSH_KEY_ALIAS` and `AFSH_KEY_PASSWORD` are set in `~/.gradle/gradle.properties` or the environment. Create the key once with `keytool -genkeypair -v -keystore ~/keys/afsh.jks -alias afsh -keyalg RSA -keysize 4096 -validity 10000`. Keep the key and its passwords outside the repo. `*.jks` and `*.keystore` are git-ignored. Losing the key means updates need an uninstall.
- CI (`.github/workflows/apk.yml` and `testing-build.yml`): each PR gets a debug APK on a `pr-<n>` pre-release, linked from a PR comment and deleted when the PR closes. `assembleRelease` runs as a parallel `pr-r8` job. `testing-build.yml` runs unit tests, `tests/check_*.py`, instrumentation and screenshots on an emulator with a cached AVD snapshot. Set `VERIFY_SKIP_BUILD=1` when calling `tools/verify.sh` after Gradle has already built. Gradle build cache, parallel execution and configuration cache are on. `setup-gradle` writes cache on PRs and on pushes to `main` / `redesign-groundwork`. Each push to `main` publishes a release APK, version code `VERSION_CODE` plus the run number. Secrets: `AFSH_KEYSTORE_BASE64` (`base64 -w0 ~/keys/afsh.jks`), `AFSH_STORE_PASSWORD`, `AFSH_KEY_ALIAS`, `AFSH_KEY_PASSWORD`. Without them the release is signed with the debug key and marked pre-release. `DEBUG_KEYSTORE_BASE64` (`base64 -w0 ~/.android/debug.keystore`) lets CI debug builds install over local ones.
- Commons: `commons/` is vendored Fossify Commons 6.1.6 (GPL-3.0) under package `com.afsh.commons`, with its package-name, signature, sideload and donation checks removed. Do not reintroduce them; they would reject this app id.
- Design file: `design/afsh.pen` is encrypted. Open it only through the pen CLI or pencil MCP, never with Read or Grep.
