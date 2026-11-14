#!/usr/bin/env python3
"""Reject new inline color and opacity values outside the design resources, and shared color slots
outside the theme: each job (header, sent bubble, button) reads its own role from `Afsh.colors`, and only
the theme looks up color resources, so a recolor never has to hunt through screens."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'app/src/main'
violations = []
for path in (root / 'res').rglob('*.xml'):
    if path.name == 'colors.xml' and path.parent.name.startswith('values'):
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?\b|android:alpha="[0-9.]', line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color or opacity')
for path in (root / 'kotlin').rglob('*.kt'):
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'0[xX][0-9a-fA-F]{8}\b|Color\.(?:parseColor|rgb|argb|WHITE|BLACK)|adjustAlpha\([0-9.]|(?:\.alpha|\balpha)\s*=\s*0\.', line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color or opacity')
theme = root / 'kotlin/com/afsh/messages/ui/AfshTheme.kt'
for path in (root / 'kotlin').rglob('*.kt'):
    if path == theme:
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'\b(?:on)?[pP]rimaryContainer\b', line):
            violations.append(f'{path.relative_to(root)}:{number}: primaryContainer outside the theme; use a role from Afsh.colors')
        in_ui = (root / 'kotlin/com/afsh/messages/ui') in path.parents
        if re.search(r'\bcolorResource\s*\(', line) or (in_ui and re.search(r'\bR\.color\.', line)):
            violations.append(f'{path.relative_to(root)}:{number}: color resource outside the theme; use a role from MaterialTheme or Afsh.colors')
assert not violations, '\n'.join(violations)
print('Design token checks passed')
