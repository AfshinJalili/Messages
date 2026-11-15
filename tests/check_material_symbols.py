#!/usr/bin/env python3
"""Every interface icon is a Material Symbol behind ui/components/AfshIcon.kt, and only used symbols ship.

Each AfshIcon entry names one ic_msym_ drawable; no call site outside AfshIcon.kt names a symbol directly.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
drawables = root / 'app/src/main/res/drawable'
kotlin = root / 'app/src/main/kotlin'
entry = kotlin / 'com/afsh/messages/ui/components/AfshIcon.kt'

problems = []
source = entry.read_text()
body = source.split('enum class AfshIcon', 1)[1]
entries = re.findall(r'^\s+(\w+)\(R\.drawable\.(\w+)\)[,;]', body, re.M)
assert entries, 'no AfshIcon entries found'

for name, drawable in entries:
    if not drawable.startswith('ic_msym_'):
        problems.append(f'AfshIcon.{name} draws {drawable}; every icon is a Material Symbol (ic_msym_*)')
    elif not (drawables / f'{drawable}.xml').exists():
        problems.append(f'AfshIcon.{name} references missing drawable {drawable}')

used = {drawable for _, drawable in entries}
for path in sorted(drawables.glob('ic_msym_*.xml')):
    text = path.read_text()
    if path.stem not in used:
        problems.append(f'{path.name} is not used by AfshIcon.kt; unused symbols must not ship')
    if re.search(r'#[0-9a-fA-F]{3,8}\b|@android:color|\?attr/', text):
        problems.append(f'{path.name} hardcodes a color or theme attribute; tint through @color/icon_mask')
    if '@color/icon_mask' not in text:
        problems.append(f'{path.name} is not tinted by @color/icon_mask')

for path in kotlin.rglob('*.kt'):
    if path != entry and 'ic_msym_' in path.read_text():
        problems.append(f'{path.relative_to(kotlin)} names a Material Symbols drawable; request an AfshIcon instead')

assert not problems, '\n'.join(problems)
print(f'Material Symbols checks passed ({len(entries)} icons)')
