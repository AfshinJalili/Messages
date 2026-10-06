# Open Line

A private, independent SMS/MMS client for Android (a fork of Fossify Messages). Kotlin. Inbox, search and conversation are Jetpack Compose; everything else is still XML views. Package `org.fossify.messages`. GitHub: `AfshinJalili/Messages` (private). Push to remote `github`; `origin` is upstream Fossify and is read-only.

This file is the single source of truth for how the project is run. Update it in the same PR that changes the process. Product intent: `PRODUCT.md`. Design system: `DESIGN.md`. Behavior specs: `docs/`. Domain language: `GLOSSARY.md` and `docs/adr/` (created by the skills when needed). Skill configuration: `docs/agents/`.

## Workflow

Every change is a GitHub issue and a pull request. `main` is protected; nobody pushes to it.

1. **Shape.** Sharpen the idea with `/grill-with-docs` (records terms in `GLOSSARY.md`, decisions in `docs/adr/`), then publish it with `/to-spec` as a spec issue.
2. **Ticket.** `/to-tickets` splits the spec into small vertical tickets. Each ticket states its acceptance criteria, its blockers, and the emulator evidence that proves it: which instrumented classes run and which screenshots are captured.
3. **Triage.** `/triage` moves every issue from `needs-triage` to `needs-info`, `ready-for-agent`, `ready-for-human` or `wontfix` (see `docs/agents/triage-labels.md`). Agents only pick up `ready-for-agent`.
4. **Build.** Branch `<issue-number>-<slug>` from `main`; open a draft PR with `Closes #<n>` at once. Implement with `/tdd` or `/implement`, or run one `/goal` per ticket whose goal is the ticket's acceptance criteria. Never one goal for many tickets.
5. **Verify.** `tools/verify.sh [TestClass ...]` runs host checks, builds, boots the local emulator, runs instrumented tests and pulls screenshots into `.scratch/qa/<branch>/`. CI runs the same on every PR and attaches screenshots and reports as artifacts.
6. **Review.** `tools/review.sh` runs `codex review` with `gpt-6.1-sol` against `main`, read-only, on the laptop. Post the findings on the PR, fix, and repeat until no blocking findings remain.
7. **PR.** Write the PR body with the `pr` skill: summary visual, before/after evidence (emulator screenshots for visual changes, test output otherwise), merge danger. Mark the PR ready.
8. **QA and merge.** The owner checks the screenshots or the emulator, then squash-merges. The issue closes itself. Closing a PR unmerged means won't do; say why in a comment.

State is the triage label on the issue plus the PR state: draft = in progress, ready = review and QA, merged or closed = done.

Labels on every issue: one triage label, one type (`bug`, `feature`, `enhancement`, `chore`, `design-gap`), one or more `area:*`.

## Run

- Build: `./gradlew :app:assembleCoreDebug :app:assembleCoreDebugAndroidTest` (flavors `core`, `foss`, `gplay`). The debug app is `org.fossify.messages.debug`.
- Host checks: `python3 tests/check_design_tokens.py` (no inline colors or opacity), `python3 tests/check_inbox_database.py` (inbox SQL against the Room schema).
- Emulator: `tools/verify.sh` runs every instrumented class; `tools/verify.sh InboxChecks SearchChecks` runs some. The first run installs the system image and creates the `openline` AVD. `HEADLESS=0` shows the emulator window.
- Screenshots in tests: `Screenshots.capture("name")` in androidTest captures the whole screen. Files land in `.scratch/qa/<branch>/screenshots/` locally and in the `emulator-qa` CI artifact.
- Review: `tools/review.sh [base]`, default base `main`.
- Icons: `python3 tools/export_icon.py`.
- Design file: `design/open-line.pen` is encrypted. Open it only through the pen CLI or pencil MCP, never with Read or Grep. Design gaps use the `design-gaps` skill.

## Devices

All automated testing and agent QA runs on the emulator, locally or in CI. Never run instrumentation, adb input or UI automation against the owner's phone (adb `RFCRB02MPSR`): it holds real messages and the debug build is its default SMS app. `tools/verify.sh` refuses to run unless the target is an emulator. Screenshots and UI dumps stay in `.scratch/` (git-ignored) and are never committed.

## Code ownership and correctness

Choose the lowest future maintenance cost when constraints conflict, and explain a material tradeoff in the commit.

### Boundaries

- Policy belongs in focused Kotlin helpers; persistence in DAOs, database migrations and provider access; presentation in Compose, XML, adapters and UI state; Android entry points coordinate calls.
- Use existing owners before creating files. An owner is a focused file or package, not necessarily a Gradle module.
- Keep rule calculations independent of Android UI and lifecycle objects. Android-dependent operations may use Context at their existing boundary.
- Access another owner's state through its operations. Keep mutation and invalidation together; do not create a second cache or writer.
- Prefer functions, existing helpers and platform APIs. Introduce interfaces, use cases or new dependencies only for a concrete need.
- Split responsibilities when they change for different reasons. Names express intent; comments explain constraints and non-obvious decisions.

### Hard invariants

1. **One owner per rule.** Extend the existing owner and route callers through it. Current owners:
   - Incoming spam decisions: `IncomingSpamClassifier`, `RuleBasedFilter`.
   - Inbox category and ordering: `InboxFilter.kt`.
   - Inbox refresh and reconciliation: `InboxRepository`, `InboxReconciler`.
   - Search queries and matching: `SearchRepository`.
   - Pending removal and Undo lifecycle: `UndoDeletion`.
   - Design tokens: `DESIGN.md`, `OpenLineTheme.kt`.

   Before changing a rule, find its implementation and every caller, including SMS/MMS receivers, notification actions and legacy XML screens. Consolidate copies of the rule before extending it. Do not turn a small fix into an unrelated migration.
2. **One definition, with explicit data authority.** Search before writing logic: the expression, threshold, format and existing helper. When extracting duplicated behavior, switch all equivalent callers and preserve their guards, ordering, identity and results. Telephony records, Room caches and app-local metadata have different owners; for changed state, name the authoritative store, its writers and how caches refresh. Reconciliation preserves app-owned metadata. SMS and MMS IDs can overlap: preserve transport identity in selection, read tracking and actions. Preserve originals when normalizing text for search.
3. **Rendering consumes policy; operations enforce it.** Composables, XML views and adapters render state and emit events. They do not write provider or database state, or decide spam, retention, read-state or sending policy. Action visibility does not replace validation in the operation. Validate external input at Android entry points and import boundaries. Propagate cancellation. Never disguise failed reads or writes as empty results.

### Proof

Separate behavior-preserving refactors from behavior changes in reviewable commits. Extend the smallest existing check that detects the regression, with synthetic data. Enforce mechanically checkable rules in CI. Every PR states which gates ran and which acceptance criteria remain unverified.

## Skills

Read the matching `SKILL.md` before the work it covers. Repo skills live in `.agents/skills` (Claude reads them through `.claude/skills`).

- Workflow: `grill-with-docs`, `to-spec`, `to-tickets`, `triage`, `tdd`, `implement`, `code-review`, `pr`, `domain-modeling`.
- Repo: `design-gaps`.
- Kotlin and UI: `android-clean-architecture`, `compose-multiplatform-patterns`, `kotlin-patterns`, `kotlin-coroutines-flows`, `kotlin-testing`.
- Android tooling, when the task matches: `adaptive`, `agp-9-upgrade`, `android-cli`, `android-intent-security`, `android-profiler`, `edge-to-edge`, `migrate-xml-views-to-jetpack-compose`, `navigation-3`, `navigation-event`, `r8-analyzer`, `styles`, `testing-setup`, and the other folders there.
