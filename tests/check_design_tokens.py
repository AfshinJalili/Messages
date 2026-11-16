#!/usr/bin/env python3
"""Reject hardcoded colors outside the design-system token file.

Hex literals belong only in app/src/main/res/values/colors.xml (and its values-night*
companions for you_* overrides that only @-reference tokens — those files must not
introduce new hex either). Compose/Kotlin must use MaterialTheme.colorScheme or
Afsh.colors — never Color(0x…), brand Color.Red/…, or literal parseColor("#…").

Allowed non-colors: Color.Transparent, Color.Unspecified, and android.graphics
Color.BLACK / Color.WHITE / Color.TRANSPARENT (contrast / chrome only).

Commons Kotlin, layouts, and values XML follow the same rule. Carve-out: multi-color
brand, flag, or file-type drawables under commons/.../drawable* that are still
referenced; unreferenced ones must be deleted (reported by this check when hex remains
in an unreferenced drawable).
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
app_main = root / 'app/src/main'
theme_kt = app_main / 'kotlin/com/afsh/messages/ui/AfshTheme.kt'
violations = []

HEX = re.compile(r'#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?\b')

TOKEN_COLOR_FILES = {
    app_main / 'res/values/colors.xml',
}

def is_token_colors_xml(path: Path) -> bool:
    return path.resolve() == (app_main / 'res/values/colors.xml').resolve()

# App XML: no #RRGGBB outside the token colors.xml. you_* override files may only @-ref.
for path in (app_main / 'res').rglob('*.xml'):
    if is_token_colors_xml(path):
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if HEX.search(line) or re.search(r'android:alpha="[0-9.]', line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color or opacity')

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
    text = path.read_text()
    if path == theme_kt:
        for number, line in enumerate(text.splitlines(), 1):
            if re.search(
                r'0[xX][0-9a-fA-F]{6,8}\b|Color\.parseColor\s*\(\s*"#|Color\.(?:Red|Blue|Green|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b',
                line,
            ):
                violations.append(f'{path.relative_to(root)}:{number}: hardcoded color in theme file; use color resources')
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if KOTLIN_HARDCODED.search(line):
            violations.append(
                f'{path.relative_to(root)}:{number}: hardcoded color or opacity; use Afsh.colors / MaterialTheme.colorScheme'
            )

# Commons: same Kotlin + layout/values rules. Drawables: only multi-color brand/flag/file-type
# carve-out when still referenced.
commons = root / 'commons/src/main'
COMMONS_KT_HARDCODED = re.compile(
    r'Color\(0[xX][0-9a-fA-F]{6,8}\)'
    r'|Color\.parseColor\s*\(\s*"#[0-9a-fA-F]'
    r'|0[xX][0-9a-fA-F]{6,8}\.toInt\(\)'
    r'|Color\.(?:Red|Blue|Green|Yellow|Cyan|Magenta|Gray|Grey|DarkGray|LightGray)\b'
)

for path in (commons / 'kotlin').rglob('*.kt'):
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if COMMONS_KT_HARDCODED.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: hardcoded color in commons Kotlin')

for path in (commons / 'res').rglob('*.xml'):
    if path.name == 'strings.xml' or path.name == 'donottranslate.xml':
        continue
    if any(part.startswith(('drawable', 'mipmap', 'raw')) for part in path.parts):
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if 'tools:' in line:
            continue
        if HEX.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color outside app token colors.xml')

# Drawable carve-out: hex allowed only in still-referenced multi-color assets; list them.
# Unreferenced drawables with hex are violations (delete them).
drawable_refs = set()
ref_re = re.compile(r'@drawable/(\w+)|R\.drawable\.(\w+)')
for path in list((commons / 'kotlin').rglob('*.kt')) + list((commons / 'res').rglob('*.xml')) + list((app_main).rglob('*.{kt,xml}'.replace('{kt,xml}', ''))):
    pass
for path in list((commons / 'kotlin').rglob('*.kt')) + list((commons / 'res').rglob('*.xml')):
    try:
        text = path.read_text()
    except Exception:
        continue
    for m in ref_re.finditer(text):
        drawable_refs.add(m.group(1) or m.group(2))
for path in (app_main / 'kotlin').rglob('*.kt'):
    for m in ref_re.finditer(path.read_text()):
        drawable_refs.add(m.group(1) or m.group(2))
for path in (app_main / 'res').rglob('*.xml'):
    for m in ref_re.finditer(path.read_text()):
        drawable_refs.add(m.group(1) or m.group(2))

carved = []
for path in (commons / 'res').rglob('*.xml'):
    if not any(part.startswith('drawable') for part in path.parts):
        continue
    text = path.read_text()
    if not HEX.search(text):
        continue
    name = path.stem
    if name not in drawable_refs:
        violations.append(f'{path.relative_to(root)}:1: unreferenced drawable still has hex; delete it')
    else:
        carved.append(str(path.relative_to(root)))

assert not violations, '\n'.join(violations)
print('Design token checks passed')
if carved:
    print('Carved-out referenced multi-color drawables (%d):' % len(carved))
    for c in sorted(carved):
        print(' ', c)
