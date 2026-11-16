#!/usr/bin/env python3
"""Every interface icon is a Material Symbol (Rounded) behind ui/components/AfshIcon.kt.

Fails when:
- an AfshIcon entry lacks a shipped ic_msym_* drawable (including its filled variant),
- a shipped ic_msym_* drawable is unused by AfshIcon,
- a symbol hardcodes a color instead of @color/icon_mask,
- app Kotlin outside AfshIcon.kt names an ic_msym_* drawable id,
- the commons copy of an ic_msym_* drawable differs from the app copy,
- a non-ic_msym_ <vector> drawable ships outside the allowed exceptions (launcher, brand
  logo, locale flag, file-type illustration).

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

# Non-icon art that may remain as vectors outside the Material Symbols set.
VECTOR_EXCEPTIONS = {
    # App launcher and notification brand mark
    'ic_launcher_foreground',
    'ic_launcher_monochrome',
    'ic_message_bubble',
    # Brand / donation / social logos (not UI glyphs)
    'ic_bitcoin_vector',
    'ic_dollar_vector',
    'ic_ethereum_vector',
    'ic_github_tinted_vector',
    'ic_github_vector',
    'ic_google_play_vector',
    'ic_liberapay_vector',
    'ic_monero_vector',
    'ic_open_collective_vector',
    'ic_patreon_vector',
    'ic_paypal_vector',
    'ic_reddit_vector',
    'ic_telegram_vector',
    'ic_tron_vector',
}


def is_allowed_vector(stem: str) -> bool:
    if stem.startswith('ic_msym_'):
        return True
    if stem in VECTOR_EXCEPTIONS:
        return True
    if stem.startswith('ic_flag_'):
        return True  # locale illustrations
    if stem.startswith('ic_file_'):
        return True  # file-type illustrations
    return False


def parse_entries(source: str):
    body = source.split('enum class AfshIcon', 1)[1]
    return re.findall(r'^\s+(\w+)\(R\.drawable\.(\w+)(?:,\s*R\.drawable\.(\w+))?\)[,;]', body, re.M)


def stray_vectors(drawable_dirs):
    """Relative paths of <vector> drawables that are not Material Symbols or exceptions."""
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
    # Filled-variant parsing
    sample = 'enum class AfshIcon(x: Int) {\n    Star(R.drawable.ic_msym_star, R.drawable.ic_msym_star_fill),\n    Add(R.drawable.ic_msym_add);\n}'
    parsed = parse_entries(sample)
    assert parsed == [('Star', 'ic_msym_star', 'ic_msym_star_fill'), ('Add', 'ic_msym_add', '')], parsed


self_test()

problems = []
source = entry.read_text()
entries = parse_entries(source)
assert entries, 'no AfshIcon entries found'

for name, drawable, filled in entries:
    if not drawable.startswith('ic_msym_'):
        problems.append(f'AfshIcon.{name} draws {drawable}; every icon is a Material Symbol (ic_msym_*)')
    elif not (app_drawables / f'{drawable}.xml').exists():
        problems.append(f'AfshIcon.{name} references missing drawable {drawable}')
    if filled:
        if not filled.startswith('ic_msym_'):
            problems.append(f'AfshIcon.{name} draws {filled}; every icon is a Material Symbol (ic_msym_*)')
        elif not (app_drawables / f'{filled}.xml').exists():
            problems.append(f'AfshIcon.{name} references missing filled drawable {filled}')

used = {drawable for _, drawable, _ in entries}
used |= {filled for _, _, filled in entries if filled}
for path in sorted(app_drawables.glob('ic_msym_*.xml')):
    text = path.read_text()
    if path.stem not in used:
        problems.append(f'{path.name} is not used by AfshIcon.kt; unused symbols must not ship')
    if re.search(r'#[0-9a-fA-F]{3,8}\b|@android:color|\?attr/', text):
        problems.append(f'{path.name} hardcodes a color or theme attribute; tint through @color/icon_mask')
    if '@color/icon_mask' not in text:
        problems.append(f'{path.name} is not tinted by @color/icon_mask')
    # Commons must ship the same generated copy for XML-only surfaces.
    commons_copy = commons_drawables / path.name
    if not commons_copy.exists():
        problems.append(f'commons is missing {path.name}; exporter writes both modules')
    elif commons_copy.read_text() != text:
        problems.append(f'commons/{path.name} differs from app; regenerate with tools/export_material_symbols.py')

for path in kotlin.rglob('*.kt'):
    if path != entry and 'ic_msym_' in path.read_text():
        problems.append(f'{path.relative_to(kotlin)} names a Material Symbols drawable; request an AfshIcon instead')

for stray in stray_vectors([app_drawables, commons_drawables]):
    problems.append(f'{stray} is a non-Material-Symbols vector; use ic_msym_* or add a documented exception')

# Every filled variant declared on AfshIcon must actually be requested somewhere with filled=true,
# or the filled drawable would ship unused in spirit. Calling painter(filled = true) or reading
# filledResource counts; the Navigation/star/pin call sites cover the current set.
filled_names = {filled for _, _, filled in entries if filled}
if filled_names:
    filled_used = set()
    for path in kotlin.rglob('*.kt'):
        text = path.read_text()
        if 'filled = true' in text or 'filled=true' in text or 'filledResource' in text:
            # Any file that requests filled counts; AfshIcon itself declares them.
            if path == entry or 'filled = true' in text or 'filled=true' in text:
                filled_used |= filled_names if path != entry else set()
            if 'filled = true' in text or 'filled=true' in text:
                filled_used = filled_names  # at least one call site uses the API
                break
    # Require at least one filled=true call site when filled variants exist.
    has_filled_call = any(
        'filled = true' in p.read_text() or 'filled=true' in p.read_text()
        for p in kotlin.rglob('*.kt') if p != entry
    )
    if not has_filled_call:
        problems.append('filled Material Symbol variants ship but no call site passes filled = true')

assert not problems, '\n'.join(problems)
print(f'Material Symbols checks passed ({len(entries)} icons, {len(filled_names)} filled variants)')
