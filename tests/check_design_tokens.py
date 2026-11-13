#!/usr/bin/env python3
"""Reject new inline color and opacity values outside the design resources."""
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
# Corner radii come from the theme's shape scale (ui/AfshTheme.kt); UI code uses MaterialTheme.shapes or AfshCorners.
theme_file = root / 'kotlin/com/afsh/messages/ui/AfshTheme.kt'
radius = re.compile(r'\b(?:Absolute)?RoundedCornerShape\(|\bCornerRadius\(')
for path in (root / 'kotlin').rglob('*.kt'):
    if path == theme_file:
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if radius.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: literal corner radius; use the theme shapes (AfshCorners or MaterialTheme.shapes)')
assert not violations, '\n'.join(violations)
print('Design token checks passed')
