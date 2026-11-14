#!/usr/bin/env python3
"""Every dialog is drawn by ui/components/AfshDialog.kt on the stock Material 3 dialogs: reject raw
dialogs anywhere else in the app, and a hand-built frame inside it."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'app/src/main/kotlin'
frame = root / 'com/afsh/messages/ui/components/AfshDialog.kt'
# Commons' SecurityDialog (app lock PIN, pattern and fingerprint) has no AFSH replacement yet.
allowed = {'org.fossify.commons.dialogs.SecurityDialog'}
pattern = re.compile(
    r'import androidx\.compose\.ui\.window\.Dialog\b'
    r'|import androidx\.compose\.material3\.(?:AlertDialog|BasicAlertDialog|DatePickerDialog|TimePickerDialog)\b'
    r'|import (?:androidx\.appcompat\.app|android\.app)\.(?:AlertDialog|DatePickerDialog|TimePickerDialog)\b'
    r'|import com\.google\.android\.material\.dialog\.'
    r'|import org\.fossify\.commons\.dialogs\.(\w+)'
    r'|\bgetAlertDialogBuilder\(|\bmaybeShowNumberPickerDialog\('
)
violations = []
for path in root.rglob('*.kt'):
    if path == frame:
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        match = pattern.search(line)
        if match and line.strip().removeprefix('import ') not in allowed:
            violations.append(f'{path.relative_to(root)}:{number}: raw dialog; use AfshDialog or a dialog built on it')

# The frame itself is the stock Material 3 alert dialog (and date picker dialog), styled by theme
# roles: no window-level Dialog, no hand-drawn Surface card, no fixed corner shape.
source = frame.read_text()
for needed in ('import androidx.compose.material3.AlertDialog', 'import androidx.compose.material3.DatePickerDialog'):
    if needed not in source:
        violations.append(f'{frame.relative_to(root)}: must build on {needed.removeprefix("import ")}')
for banned in ('import androidx.compose.ui.window.Dialog', 'import androidx.compose.material3.Surface', 'AfshCorners'):
    if banned in source:
        violations.append(f'{frame.relative_to(root)}: hand-built frame ({banned}); use the Material 3 dialog')
assert not violations, '\n'.join(violations)
print('Dialog checks passed')
