#!/usr/bin/env python3
"""Guards cold-start performance fixes against regression.

Fails on:
- MainActivity calling scheduleProviderReconcile(..., immediate = true) without force=false
  (or missing scheduleLaunchReconcile) so every launch redoes a full Telephony pass
- ConversationsDao inbox list queries that reintroduce correlated subqueries over messages
  / spam_messages / recycle_bin_messages
- ensureBackgroundThread spawning an unbounded Thread instead of a bounded executor
- SplashActivity still present as a launcher hop
- Loading gate that waits on provider reconcile while Room is empty
- baseline-prof.txt method rules that omit a (...) descriptor (breaks
  :app:expandReleaseArtProfileWildcards / profgen)

Host-only; no device. Run: python3 tests/check_cold_start.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP_KT = ROOT / "app/src/main/kotlin/com/afsh/messages"
MAIN = APP_KT / "activities/MainActivity.kt"
DAO = APP_KT / "interfaces/ConversationsDao.kt"
REPO = APP_KT / "helpers/InboxRepository.kt"
COMMONS_CONSTANTS = ROOT / "commons/src/main/kotlin/org/fossify/commons/helpers/Constants.kt"
SPLASH = APP_KT / "activities/SplashActivity.kt"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
BASELINE_PROF = ROOT / "app/src/main/baseline-prof.txt"

violations: list[str] = []


def rel(path: Path) -> str:
    return str(path.relative_to(ROOT))


# --- Launch must not force a full reconcile ---------------------------------
main = MAIN.read_text()
if "scheduleLaunchReconcile" not in main:
    violations.append(f"{rel(MAIN)}: cold start must call scheduleLaunchReconcile (dirty-check path)")
if re.search(
    r"scheduleProviderReconcile\s*\(\s*this\s*,\s*immediate\s*=\s*true\s*\)",
    main,
):
    violations.append(
        f"{rel(MAIN)}: do not schedule an immediate full provider reconcile on every launch; "
        "use scheduleLaunchReconcile / force=false"
    )
if "roomSnapshotReceived" not in main or "config.messagesImported" not in main:
    violations.append(
        f"{rel(MAIN)}: Loading messages must wait only for a true first import "
        "(room snapshot + empty + !messagesImported), not providerReconcileActive"
    )
if "providerReconcileActive && inboxConversations.isEmpty()" in main:
    violations.append(
        f"{rel(MAIN)}: loading gate must not block on providerReconcileActive"
    )
if "installSplashScreen" not in main or "setKeepOnScreenCondition" not in main:
    violations.append(
        f"{rel(MAIN)}: launcher must use Theme.SplashScreen keep-on-screen until the first Room snapshot"
    )

repo = REPO.read_text()
if "fun scheduleLaunchReconcile" not in repo:
    violations.append(f"{rel(REPO)}: missing scheduleLaunchReconcile")
if "needsFullReconcile" not in repo or "telephonyThreadDigest" not in repo:
    violations.append(f"{rel(REPO)}: launch reconcile must dirty-check via telephonyThreadDigest")
if "force: Boolean = true" not in repo and "force: Boolean =" not in repo:
    violations.append(f"{rel(REPO)}: scheduleProviderReconcile needs a force flag for dirty-check launches")

# --- Inbox DAO: no correlated message subqueries ---------------------------
dao = DAO.read_text()
inbox_queries = re.findall(
    r'@Query\("(SELECT .*?)"\)\s+fun (?:getNonArchivedWithLatestSnippet|observeNonArchivedWithLatestSnippet)\b',
    dao,
    flags=re.S,
)
if len(inbox_queries) < 2:
    violations.append(
        f"{rel(DAO)}: expected get/observeNonArchivedWithLatestSnippet inbox queries"
    )
for sql in inbox_queries:
    expanded = sql
    for name, value in re.findall(r'private const val (\w+) = "(.*?)"', dao, flags=re.S):
        expanded = expanded.replace(f"${name}", value.replace("\n", " "))
    lowered = expanded.lower()
    if "from messages" in lowered or "spam_messages" in lowered or "recycle_bin_messages" in lowered:
        violations.append(
            f"{rel(DAO)}: inbox list query must not join/subquery messages/spam/recycle_bin; "
            "read denormalized preview_* / all_spam columns"
        )
    if "preview_snippet" not in expanded and "PREVIEW_COLUMNS" not in sql and "preview_snippet as new_snippet" not in dao:
        violations.append(f"{rel(DAO)}: inbox query must select denormalized preview columns")

if "all_spam = 0" not in dao and "all_spam=0" not in dao:
    violations.append(f"{rel(DAO)}: inbox must filter all_spam = 0")

# --- Bounded background executor -------------------------------------------
constants = COMMONS_CONSTANTS.read_text()
if "ExecutorService" not in constants and "Executors.newFixedThreadPool" not in constants:
    violations.append(
        f"{rel(COMMONS_CONSTANTS)}: ensureBackgroundThread must use a bounded executor, not raw Thread"
    )
if re.search(r"Thread\s*\{[^}]*\}\s*\.start\s*\(\s*\)", constants):
    violations.append(
        f"{rel(COMMONS_CONSTANTS)}: unbounded Thread {{ }}.start() reintroduced in ensureBackgroundThread"
    )

# --- No SplashActivity launcher hop ----------------------------------------
if SPLASH.is_file():
    violations.append(f"{rel(SPLASH)}: remove SplashActivity; MainActivity is the launcher")
manifest = MANIFEST.read_text()
if "SplashActivity" in manifest:
    violations.append(f"{rel(MANIFEST)}: SplashActivity must not remain in the manifest")
if 'android:name=".activities.MainActivity"' not in manifest:
    violations.append(f"{rel(MANIFEST)}: MainActivity must be declared")
if not re.search(
    r'android:name="\.activities\.MainActivity"[\s\S]*?android\.intent\.action\.MAIN',
    manifest,
):
    violations.append(f"{rel(MANIFEST)}: MainActivity must hold the MAIN/LAUNCHER intent-filter")
if "Theme.Afsh.Splash" not in manifest:
    violations.append(f"{rel(MANIFEST)}: MainActivity must use Theme.Afsh.Splash")

# --- Baseline profile: every method rule needs a (...) descriptor ----------
# profgen RuleFragmentParser.wildCard/parseMethodName throws
# StringIndexOutOfBoundsException on `->Name*` without `(...)`.
if not BASELINE_PROF.is_file():
    violations.append(f"{rel(BASELINE_PROF)}: missing baseline profile")
else:
    method_rule = re.compile(r"^[HSP]*L[^;]+;->(.+)$")
    for lineno, raw in enumerate(BASELINE_PROF.read_text().splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#") or ";->" not in line:
            continue
        match = method_rule.match(line)
        if match is None:
            violations.append(f"{rel(BASELINE_PROF)}:{lineno}: unparseable method rule: {line}")
            continue
        rest = match.group(1)
        if "(" not in rest:
            violations.append(
                f"{rel(BASELINE_PROF)}:{lineno}: method rule missing (...) descriptor "
                f"(use e.g. ->Name*(**)**): {line}"
            )

if violations:
    print("Cold-start check failed:")
    for item in violations:
        print(f"  {item}")
    sys.exit(1)

print("PASS: cold-start guards")
