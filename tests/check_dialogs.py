#!/usr/bin/env python3
"""Every dialog is drawn by ui/components/AfshDialog.kt: reject raw dialogs anywhere else in the app."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'app/src/main/kotlin'
frame = root / 'com/afsh/messages/ui/components/AfshDialog.kt'
# Commons' SecurityDialog (app lock PIN, pattern and fingerprint) has no AFSH replacement yet.
allowed = {'org.fossify.commons.dialogs.SecurityDialog'}
pattern = re.compile(
    r'import androidx\.compose\.ui\.window\.Dialog\b'
    r'|import androidx\.compose\.material3\.(?:AlertDialog|BasicAlertDialog|DatePickerDialog)\b'
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
assert not violations, '\n'.join(violations)
print('Dialog checks passed')
