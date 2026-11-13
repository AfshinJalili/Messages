#!/usr/bin/env python3
"""Material Symbols sit beside Lucide behind ui/components/AfshIcon.kt: every Lucide icon in use has a mapped symbol."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
drawables = root / 'app/src/main/res/drawable'
kotlin = root / 'app/src/main/kotlin'
entry = kotlin / 'com/afsh/messages/ui/components/AfshIcon.kt'
ANDROID = '{http://schemas.android.com/apk/res/android}'

# Lucide drawables that share a meaning with another one and map through the same entry.
aliases = {'share': 'share_2', 'trash': 'trash_2'}

problems = []
source = entry.read_text()
pairs = re.findall(r'R\.drawable\.ic_lucide_(\w+),\s*R\.drawable\.ic_msym_(\w+)', source)
mapped = {lucide for lucide, _ in pairs}

for lucide in sorted(p.stem.removeprefix('ic_lucide_') for p in drawables.glob('ic_lucide_*.xml')):
    if aliases.get(lucide, lucide) not in mapped:
        problems.append(f'ic_lucide_{lucide} has no Material Symbols mapping in AfshIcon.kt')

for lucide, symbol in pairs:
    for name in (f'ic_lucide_{lucide}', f'ic_msym_{symbol}'):
        if not (drawables / f'{name}.xml').exists():
            problems.append(f'AfshIcon.kt references missing drawable {name}')
    if not (drawables / f'ic_msym_{symbol}.xml').exists() or not (drawables / f'ic_lucide_{lucide}.xml').exists():
        continue
    mirrored = {n: ET.parse(drawables / f'{n}.xml').getroot().get(ANDROID + 'autoMirrored') == 'true'
                for n in (f'ic_lucide_{lucide}', f'ic_msym_{symbol}')}
    if len(set(mirrored.values())) != 1:
        problems.append(f'ic_lucide_{lucide} and ic_msym_{symbol} disagree on RTL mirroring')

used = {symbol for _, symbol in pairs}
for path in sorted(drawables.glob('ic_msym_*.xml')):
    name = path.stem.removeprefix('ic_msym_')
    text = path.read_text()
    if name not in used:
        problems.append(f'{path.name} is not used by AfshIcon.kt; unused symbols must not ship')
    if re.search(r'#[0-9a-fA-F]{3,8}\b|@android:color|\?attr/', text):
        problems.append(f'{path.name} hardcodes a color or theme attribute; tint through @color/icon_mask')
    if '@color/icon_mask' not in text:
        problems.append(f'{path.name} is not tinted by @color/icon_mask')

for path in kotlin.rglob('*.kt'):
    if path != entry and 'ic_msym_' in path.read_text():
        problems.append(f'{path.relative_to(kotlin)} names a Material Symbols drawable; request an AfshIcon instead')

assert not problems, '\n'.join(problems)
print(f'Material Symbols checks passed ({len(pairs)} icons mapped)')
