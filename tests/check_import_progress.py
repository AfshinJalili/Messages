#!/usr/bin/env python3
"""First-run setup shows one continuous import card: fixed label + percent, never X-of-Y counts.

Fails when:
- count-based import string resources remain (import_progress_conversations / _messages),
- ImportNotice still branches on a Phase or formats done/total into the label,
- InboxImport.conversationsDone clears progress (that gap spawned a second progress UI),
- ImportProgress is still a two-phase (done, total) model instead of one rising percent,
- the percent string or ImportNotice percent row is missing.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
app = root / 'app/src/main'
kotlin = app / 'kotlin/com/afsh/messages'
notice = (kotlin / 'ui/components/ImportNotice.kt').read_text()
inbox_import = (kotlin / 'helpers/InboxImport.kt').read_text()
strings = list((app / 'res').glob('**/strings.xml'))

violations = []

for path in strings:
    text = path.read_text()
    rel = path.relative_to(root)
    for name in ('import_progress_conversations', 'import_progress_messages'):
        if re.search(rf'<string\s+name="{name}"', text):
            violations.append(f'{rel}: remove count-based {name}; use a fixed label and import_progress_percent')
    if re.search(r'<string\s+name="import_progress_title"', text) and 'import_progress_percent' not in text:
        violations.append(f'{rel}: import_progress_title without import_progress_percent')

if 'import_progress_percent' not in (app / 'res/values/strings.xml').read_text():
    violations.append('values/strings.xml: missing import_progress_percent')

if 'ImportProgress.Phase' in notice or 'progress.done' in notice or 'progress.total' in notice:
    violations.append('ImportNotice.kt: must not show phase- or count-based detail text')
if 'import_progress_percent' not in notice:
    violations.append('ImportNotice.kt: must show import_progress_percent beside the bar')
if 'fontFeatureSettings' not in notice and 'FontFamily.Monospace' not in notice:
    violations.append('ImportNotice.kt: percent digits must be tabular or monospace')
if not re.search(r'\bLinearProgressIndicator\s*\(', notice):
    violations.append('ImportNotice.kt: must draw one LinearProgressIndicator')
if len(re.findall(r'\bLinearProgressIndicator\s*\(', notice)) != 1:
    violations.append('ImportNotice.kt: exactly one progress indicator on the import card')

done_fn = re.search(r'fun conversationsDone\(\)\s*\{(.*?)\n    \}', inbox_import, re.S)
if not done_fn:
    violations.append('InboxImport.kt: conversationsDone() is missing')
elif re.search(r'state\.value\s*=\s*null', done_fn.group(1)):
    violations.append('InboxImport.kt: conversationsDone must not clear progress between stages')
if 'enum class Phase' in inbox_import:
    violations.append('InboxImport.kt: remove Phase; first-run is one rising percent')
if not re.search(r'data class ImportProgress\(val percent:\s*Int\)', inbox_import):
    violations.append('InboxImport.kt: ImportProgress must be a single percent')
if 'ImportProgressMath' not in inbox_import:
    violations.append('InboxImport.kt: weight conversation and message work through ImportProgressMath')
if 'fun abort(' not in inbox_import:
    violations.append('InboxImport.kt: abort() must clear a held bar when reconcile fails')

design = (root / 'DESIGN.md').read_text()
if 'ImportNotice' not in design or 'import_progress_percent' not in design:
    violations.append('DESIGN.md: document the single first-run ImportNotice (fixed label + percent)')

assert not violations, '\n'.join(violations)
print('Import progress checks passed')
