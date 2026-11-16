#!/usr/bin/env python3
"""Reject hardcoded colors outside the design-system token files.

Hex literals belong only in app/src/main/res/values*/colors.xml (GM3 tokens).
Compose/Kotlin must use MaterialTheme.colorScheme or Afsh.colors — never Color(0x…),
Color.Black/White/Red/…, or literal parseColor("#…"). Color.Transparent and
Color.Unspecified are allowed (not brand colors).

Also fails on hex in app XML outside colors.xml, and on Color(0x…) in commons Kotlin
outside compose/theme/Colors.kt. Commons multi-color drawables/flags are out of scope
(icons PR); commons layout hex still fails.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
app_main = root / 'app/src/main'
theme_kt = app_main / 'kotlin/com/afsh/messages/ui/AfshTheme.kt'
violations = []

HEX = re.compile(r'#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?\b')

# XML in the app: no #RRGGBB outside values*/colors.xml
for path in (app_main / 'res').rglob('*.xml'):
    if path.name == 'colors.xml' and path.parent.name.startswith('values'):
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if HEX.search(line) or re.search(r'android:alpha="[0-9.]', line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color or opacity')

# Kotlin in the app. Transparent/Unspecified are non-colors and are allowed.
KOTLIN_HARDCODED = re.compile(
    r'0[xX][0-9a-fA-F]{6,8}\b'
    r'|Color\.parseColor\s*\(\s*"#'
    r'|Color\.(?:rgb|argb)\s*\('
    r'|Color\.(?:White|Black|Red|Blue|Green|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b'
    r'|android\.graphics\.Color\.(?:WHITE|BLACK|RED|BLUE|GREEN|YELLOW|CYAN|MAGENTA|GRAY|DKGRAY|LTGRAY)\b'
    r'|adjustAlpha\([0-9.]'
    r'|(?:\.alpha|\balpha)\s*=\s*0\.'
)

for path in (app_main / 'kotlin').rglob('*.kt'):
    text = path.read_text()
    if path == theme_kt:
        for number, line in enumerate(text.splitlines(), 1):
            if re.search(
                r'0[xX][0-9a-fA-F]{6,8}\b|Color\.parseColor\s*\(\s*"#|Color\.(?:White|Black|Red|Blue|Green|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b',
                line,
            ):
                violations.append(f'{path.relative_to(root)}:{number}: hardcoded color in theme file; use color resources')
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if KOTLIN_HARDCODED.search(line):
            violations.append(
                f'{path.relative_to(root)}:{number}: hardcoded color or opacity; use Afsh.colors / MaterialTheme.colorScheme'
            )

# Commons: layout XML must not embed hex (drawables/flags left for the icons work).
# Kotlin Color(0x…) only in compose/theme/Colors.kt (legacy Fossify palette dump).
commons = root / 'commons/src/main'
commons_palette_kt = commons / 'kotlin/org/fossify/commons/compose/theme/Colors.kt'
for path in (commons / 'res').rglob('*.xml'):
    if path.name == 'colors.xml' and path.parent.name.startswith('values'):
        continue
    if path.name == 'strings.xml':
        continue
    # Skip drawables / mipmaps / raw — multi-color icons and flags.
    if any(part.startswith(('drawable', 'mipmap', 'raw')) for part in path.parts):
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if 'tools:' in line:
            continue
        if HEX.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color outside commons colors.xml')

for path in (commons / 'kotlin').rglob('*.kt'):
    if path == commons_palette_kt:
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'Color\(0[xX][0-9a-fA-F]{6,8}\)|Color\.parseColor\s*\(\s*"#[0-9a-fA-F]', line):
            violations.append(f'{path.relative_to(root)}:{number}: hardcoded Color(0x…) outside commons palette file')

assert not violations, '\n'.join(violations)
print('Design token checks passed')
