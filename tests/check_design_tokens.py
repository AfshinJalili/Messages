#!/usr/bin/env python3
"""Reject hardcoded colors outside the single GM3 token file.

The only color token file is commons/src/main/res/values/colors.xml (m3_* hex).
No other module may define <color> names. No @android:color/* except transparent.
Compose/Kotlin must not use Color(0x…), brand Color.Red/…, or parseColor("#…").

Allowed non-colors: Color.Transparent, Color.Unspecified, android.graphics
Color.BLACK / Color.WHITE / Color.TRANSPARENT (contrast / chrome only).

Carve-out: multi-color brand, flag, or file-type drawables under commons drawable*
that are still referenced.
"""
from pathlib import Path
import re
from collections import defaultdict

root = Path(__file__).resolve().parents[1]
token_file = (root / 'commons/src/main/res/values/colors.xml').resolve()
app_main = root / 'app/src/main'
commons_main = root / 'commons/src/main'
theme_kt = app_main / 'kotlin/com/afsh/messages/ui/AfshTheme.kt'
violations = []

HEX = re.compile(r'#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?\b')
ANDROID_COLOR = re.compile(r'@android:color/(\w+)')
COLOR_NAME = re.compile(r'<color\s+name="(\w+)"')

# --- Exactly one definition per color name; only the token file may hold hex colors. ---
defs = defaultdict(list)
for path in root.rglob('**/src/main/res/**/colors.xml'):
    if 'build' in path.parts:
        continue
    text = path.read_text()
    for number, line in enumerate(text.splitlines(), 1):
        for name in COLOR_NAME.findall(line):
            defs[name].append(f'{path.relative_to(root)}:{number}')
        if path.resolve() != token_file and HEX.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: hex outside the single GM3 token file')
        for m in ANDROID_COLOR.finditer(line):
            if m.group(1) != 'transparent':
                violations.append(
                    f'{path.relative_to(root)}:{number}: framework color @{m.group(0)}; use an m3_* token'
                )

for name, places in sorted(defs.items()):
    if len(places) > 1:
        violations.append(f'color name "{name}" defined in multiple places: {", ".join(places)}')

# Non-colors.xml XML under app + commons: no hex; no framework colors except transparent.
for main in (app_main, commons_main):
    res = main / 'res'
    if not res.exists():
        continue
    for path in res.rglob('*.xml'):
        if path.resolve() == token_file:
            continue
        if path.name in {'strings.xml', 'donottranslate.xml'}:
            continue
        if any(part.startswith(('drawable', 'mipmap', 'raw')) for part in path.parts):
            # Framework color ban still applies inside drawables; hex carve-out below.
            text = path.read_text()
            for number, line in enumerate(text.splitlines(), 1):
                for m in ANDROID_COLOR.finditer(line):
                    if m.group(1) != 'transparent':
                        violations.append(
                            f'{path.relative_to(root)}:{number}: framework color @{m.group(0)}; use an m3_* token'
                        )
            continue
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if 'tools:' in line:
                continue
            if HEX.search(line):
                violations.append(f'{path.relative_to(root)}:{number}: inline color')
            for m in ANDROID_COLOR.finditer(line):
                if m.group(1) != 'transparent':
                    violations.append(
                        f'{path.relative_to(root)}:{number}: framework color @{m.group(0)}; use an m3_* token'
                    )

KOTLIN_HARDCODED = re.compile(
    r'0[xX][0-9a-fA-F]{6,8}\b'
    r'|Color\.parseColor\s*\(\s*"#'
    r'|Color\.(?:rgb|argb)\s*\('
    r'|Color\.(?:Red|Blue|Green|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b'
    r'|android\.graphics\.Color\.(?:RED|BLUE|GREEN|YELLOW|CYAN|MAGENTA|GRAY|DKGRAY|LTGRAY)\b'
    r'|adjustAlpha\([0-9.]'
    r'|(?:\.alpha|\balpha)\s*=\s*0\.'
)

for path in (app_main / 'kotlin').rglob('*.kt'):
    if path == theme_kt:
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if re.search(
                r'0[xX][0-9a-fA-F]{6,8}\b|Color\.parseColor\s*\(\s*"#|Color\.(?:Red|Blue|Green|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b',
                line,
            ):
                violations.append(f'{path.relative_to(root)}:{number}: hardcoded color in theme file; use color resources')
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if KOTLIN_HARDCODED.search(line):
            violations.append(
                f'{path.relative_to(root)}:{number}: hardcoded color or opacity; use Afsh.colors / MaterialTheme.colorScheme'
            )

COMMONS_KT_HARDCODED = re.compile(
    r'Color\(0[xX][0-9a-fA-F]{6,8}\)'
    r'|Color\.parseColor\s*\(\s*"#[0-9a-fA-F]'
    r'|0[xX][0-9a-fA-F]{6,8}\.toInt\(\)'
    r'|Color\.(?:Red|Blue|Green|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b'
)

for path in (commons_main / 'kotlin').rglob('*.kt'):
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if COMMONS_KT_HARDCODED.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: hardcoded color in commons Kotlin')

# Drawable hex carve-out: only still-referenced assets.
drawable_refs = set()
ref_re = re.compile(r'@drawable/(\w+)|R\.drawable\.(\w+)')
scan_paths = (
    list((commons_main / 'kotlin').rglob('*.kt'))
    + list((commons_main / 'res').rglob('*.xml'))
    + list((app_main / 'kotlin').rglob('*.kt'))
    + list((app_main / 'res').rglob('*.xml'))
)
for path in scan_paths:
    try:
        text = path.read_text()
    except Exception:
        continue
    for m in ref_re.finditer(text):
        drawable_refs.add(m.group(1) or m.group(2))

carved = []
for path in (commons_main / 'res').rglob('*.xml'):
    if not any(part.startswith('drawable') for part in path.parts):
        continue
    text = path.read_text()
    if not HEX.search(text):
        continue
    if path.stem not in drawable_refs:
        violations.append(f'{path.relative_to(root)}:1: unreferenced drawable still has hex; delete it')
    else:
        carved.append(str(path.relative_to(root)))

assert not violations, '\n'.join(violations)
print('Design token checks passed')
print(f'Token file: {token_file.relative_to(root)} ({sum(1 for _ in COLOR_NAME.finditer(token_file.read_text()))} colors)')
if carved:
    print('Carved-out referenced multi-color drawables (%d):' % len(carved))
    for c in sorted(carved):
        print(' ', c)
