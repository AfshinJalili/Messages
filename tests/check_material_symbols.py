#!/usr/bin/env python3
"""Every interface icon is a Material Symbol (Rounded) behind ui/components/AfshIcon.kt.

Fails when:
- an AfshIcon entry lacks a shipped ic_msym_* drawable in commons (including its filled variant),
- a shipped ic_msym_* drawable is unused by AfshIcon,
- a symbol hardcodes a color instead of @color/m3_on_primary,
- app Kotlin outside AfshIcon.kt names an ic_msym_* drawable id,
- any ic_msym_* exists under app/src (single copy lives in commons only),
- a non-ic_msym_ <vector> drawable ships outside the allowed exceptions (launcher / brand mark only).

Self-tests against tests/fixtures/material_symbols before checking the repo.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
app_drawables = root / 'app/src/main/res/drawable'
commons_drawables = root / 'commons/src/main/res/drawable'
kotlin = root / 'app/src/main/kotlin'
entry = kotlin / 'com/afsh/messages/ui/components/AfshIcon.kt'
fixtures = root / 'tests/fixtures/material_symbols'

# Non-icon art that may remain as vectors. Shortcut layers are not <vector> files.
VECTOR_EXCEPTIONS = {
    'ic_launcher_foreground',   # adaptive / themed / splash brand mark (single source)
    'ic_message_bubble',        # notification small-icon; generated from the foreground
}


def is_allowed_vector(stem: str) -> bool:
    if stem.startswith('ic_msym_'):
        return True
    return stem in VECTOR_EXCEPTIONS


def parse_entries(source: str):
    body = source.split('enum class AfshIcon', 1)[1]
    # AfshIcon references commons drawables: com.afsh.commons.R.drawable… or R.drawable… after importing commons R.
    return re.findall(
        r'^\s+(\w+)\((?:com\.afsh\.commons\.)?R\.drawable\.(\w+)(?:,\s*(?:com\.afsh\.commons\.)?R\.drawable\.(\w+))?\)[,;]',
        body,
        re.M,
    )


def stray_vectors(drawable_dirs):
    found = []
    for directory in drawable_dirs:
        if not directory.exists():
            continue
        for path in sorted(directory.glob('*.xml')):
            text = path.read_text()
            if '<vector' not in text:
                continue
            if not is_allowed_vector(path.stem):
                found.append(str(path.relative_to(root) if root in path.parents else path))
    return found


def self_test():
    violations_dir = fixtures / 'Violations' / 'drawable'
    clean_dir = fixtures / 'Clean' / 'drawable'
    assert violations_dir.exists() and clean_dir.exists(), 'material_symbols fixtures missing'
    bad = stray_vectors([violations_dir])
    assert bad, 'self-test: Violations fixture must flag a stray vector'
    assert not stray_vectors([clean_dir]), 'self-test: Clean fixture must not flag allowed vectors'
    sample = (
        'enum class AfshIcon(x: Int) {\n'
        '    Star(R.drawable.ic_msym_star, R.drawable.ic_msym_star_fill),\n'
        '    Unpin(com.afsh.commons.R.drawable.ic_msym_keep_off),\n'
        '    Add(R.drawable.ic_msym_add);\n}'
    )
    parsed = parse_entries(sample)
    assert ('Star', 'ic_msym_star', 'ic_msym_star_fill') in parsed
    assert ('Unpin', 'ic_msym_keep_off', '') in parsed
    assert ('Add', 'ic_msym_add', '') in parsed


self_test()

problems = []
source = entry.read_text()
if 'com.afsh.commons.R' not in source and 'import com.afsh.commons.R' not in source:
    # After the commons-only move, AfshIcon must resolve drawables from commons.
    if 'com.afsh.messages.R' in source:
        problems.append('AfshIcon.kt must reference commons drawables (com.afsh.commons.R), not app R')

entries = parse_entries(source)
assert entries, 'no AfshIcon entries found'

for name, drawable, filled in entries:
    if not drawable.startswith('ic_msym_'):
        problems.append(f'AfshIcon.{name} draws {drawable}; every icon is a Material Symbol (ic_msym_*)')
    elif not (commons_drawables / f'{drawable}.xml').exists():
        problems.append(f'AfshIcon.{name} references missing commons drawable {drawable}')
    if filled:
        if not filled.startswith('ic_msym_'):
            problems.append(f'AfshIcon.{name} draws {filled}; every icon is a Material Symbol (ic_msym_*)')
        elif not (commons_drawables / f'{filled}.xml').exists():
            problems.append(f'AfshIcon.{name} references missing filled drawable {filled}')

used = {drawable for _, drawable, _ in entries}
used |= {filled for _, _, filled in entries if filled}

# No duplicates in the app module.
for path in sorted(app_drawables.glob('ic_msym_*.xml')):
    problems.append(f'{path.relative_to(root)} duplicates the commons Material Symbols set; keep icons only in commons')

for path in sorted(commons_drawables.glob('ic_msym_*.xml')):
    text = path.read_text()
    if path.stem not in used:
        problems.append(f'{path.name} is not used by AfshIcon.kt; unused symbols must not ship')
    if re.search(r'#[0-9a-fA-F]{3,8}\b|@android:color|\?attr/', text):
        problems.append(f'{path.name} hardcodes a color or theme attribute; tint through @color/m3_on_primary')
    if '@color/m3_on_primary' not in text:
        problems.append(f'{path.name} is not tinted by @color/m3_on_primary')

for path in kotlin.rglob('*.kt'):
    if path != entry and 'ic_msym_' in path.read_text():
        problems.append(f'{path.relative_to(kotlin)} names a Material Symbols drawable; request an AfshIcon instead')

for stray in stray_vectors([app_drawables, commons_drawables]):
    problems.append(f'{stray} is a non-Material-Symbols vector; use ic_msym_* or add a documented exception')

filled_names = {filled for _, _, filled in entries if filled}
has_filled_call = any(
    'filled = true' in p.read_text() or 'filled=true' in p.read_text()
    for p in kotlin.rglob('*.kt') if p != entry
)
if filled_names and not has_filled_call:
    problems.append('filled Material Symbol variants ship but no call site passes filled = true')

assert not problems, '\n'.join(problems)
print(f'Material Symbols checks passed ({len(entries)} icons, {len(filled_names)} filled variants, commons-only)')
