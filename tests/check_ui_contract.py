#!/usr/bin/env python3
"""The design-system contract: the app draws only from the theme's tokens, and the removed Lucide set stays gone.

Fails on, anywhere in the app's Kotlin outside ui/AfshTheme.kt:
- a literal dp in padding, spacedBy or PaddingValues (use Afsh.spacing; 0.dp is allowed),
- a hand-built corner radius: any RoundedCornerShape or CornerRadius, or a literal dp in CutCornerShape or CornerSize
  (use MaterialTheme.shapes, AfshCorners or CircleShape),
- primaryContainer or onPrimaryContainer (read the job's role through Afsh.colors),
- colorResource, and in Compose code (ui/) also R.color and getColor (use Afsh.colors or MaterialTheme.colorScheme),
- Icons.* from material-icons, ImageVector.Builder / inline path icons, and painterResource(R.drawable…) for icons
  outside AfshIcon.kt (request AfshIcon instead);
and any Lucide drawable, reference or tool in the app, its resources, Gradle files or tools/.
Also scans commons Kotlin for Icons.* and ImageVector.Builder.

Sizes (icons, touch targets, avatars) are not spacing and may stay literal. Before checking the app, the script checks
itself against tests/fixtures/ui_contract: every rule must fire on Violations.kt and none on Clean.kt.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
kotlin = root / 'app/src/main/kotlin/com/afsh/messages'
commons_kotlin = root / 'commons/src/main/kotlin'
theme = kotlin / 'ui/AfshTheme.kt'
afsh_icon = kotlin / 'ui/components/AfshIcon.kt'
fixtures = root / 'tests/fixtures/ui_contract'

SPACING_CALL = re.compile(r'\b(padding|absolutePadding|spacedBy|PaddingValues)\(')
SHAPE_CALL = re.compile(r'\b(?:Absolute)?RoundedCornerShape\(|\bCornerRadius\(')
RADIUS_CALL = re.compile(r'\b(CutCornerShape|CornerSize)\(')
LITERAL_DP = re.compile(r'(?<![\w.])(?!0+(\.0+)?\.dp)\d+(\.\d+)?\.dp\b')
COLOR_RESOURCE = re.compile(r'\bcolorResource\s*\(')
COLOR_LOOKUP = re.compile(r'\bR\.color\.|\bgetColor\(')
PRIMARY_CONTAINER = re.compile(r'\b(?:on)?[pP]rimaryContainer\b')
LUCIDE = re.compile(r'lucide', re.IGNORECASE)
MATERIAL_ICONS = re.compile(r'\bIcons\.(Default|Filled|Outlined|Rounded|Sharp|TwoTone|AutoMirrored)\b')
IMAGE_VECTOR_BUILDER = re.compile(r'\bImageVector\.Builder\b|\bPathBuilder\s*\(')
PAINTER_DRAWABLE = re.compile(r'\bpainterResource\s*\(\s*(?:R|com\.afsh\.commons\.R)\.drawable\.')

RULES = {
    'lucide': 'references Lucide; request an AfshIcon (Material Symbols) instead',
    'spacing': 'uses a literal spacing dp; use Afsh.spacing',
    'radius': 'builds its own corner radius; use MaterialTheme.shapes, AfshCorners or CircleShape',
    'color': 'looks up a color resource directly; use Afsh.colors or MaterialTheme.colorScheme',
    'primaryContainer': 'reads primaryContainer outside the theme; use its Afsh.colors role',
    'materialIcons': 'uses material-icons Icons.*; request an AfshIcon (Material Symbols) instead',
    'imageVector': 'builds an inline ImageVector/path icon; request an AfshIcon instead',
    'painterDrawable': 'loads a drawable painter outside AfshIcon; request an AfshIcon instead',
}


def call_args(text, pattern):
    """(line, argument text) of every call matching pattern, nested parentheses included."""
    for match in pattern.finditer(text):
        depth, end = 1, match.end()
        while depth and end < len(text):
            depth += {'(': 1, ')': -1}.get(text[end], 0)
            end += 1
        yield text.count('\n', 0, match.start()) + 1, text[match.end():end - 1]


def kotlin_problems(text, compose=True, allow_painter=False):
    """(line, rule) for every contract break in one Kotlin source."""
    found = []
    for number, args in call_args(text, SPACING_CALL):
        if LITERAL_DP.search(args):
            found.append((number, 'spacing'))
    for number, args in call_args(text, RADIUS_CALL):
        if LITERAL_DP.search(args):
            found.append((number, 'radius'))
    for number, line in enumerate(text.splitlines(), 1):
        if LUCIDE.search(line):
            found.append((number, 'lucide'))
        if SHAPE_CALL.search(line):
            found.append((number, 'radius'))
        if COLOR_RESOURCE.search(line) or (compose and COLOR_LOOKUP.search(line)):
            found.append((number, 'color'))
        if PRIMARY_CONTAINER.search(line):
            found.append((number, 'primaryContainer'))
        if MATERIAL_ICONS.search(line):
            found.append((number, 'materialIcons'))
        if IMAGE_VECTOR_BUILDER.search(line):
            found.append((number, 'imageVector'))
        if not allow_painter and PAINTER_DRAWABLE.search(line):
            found.append((number, 'painterDrawable'))
    return found


def self_test():
    violations = {rule for _, rule in kotlin_problems((fixtures / 'Violations.kt').read_text())}
    missing = set(RULES) - violations
    assert not missing, f'self-test: rules that no longer catch their violation: {sorted(missing)}'
    clean = kotlin_problems((fixtures / 'Clean.kt').read_text())
    assert not clean, f'self-test: Clean.kt flagged: {clean}'


self_test()
problems = []
for path in sorted(kotlin.rglob('*.kt')):
    if path == theme:
        continue
    compose = (kotlin / 'ui') in path.parents
    allow_painter = path == afsh_icon
    for number, rule in kotlin_problems(path.read_text(), compose, allow_painter=allow_painter):
        problems.append(f'{path.relative_to(root)}:{number} {RULES[rule]}')

# Commons must not bring back material-icons Icons.* or inline ImageVector builders.
for path in sorted(commons_kotlin.rglob('*.kt')):
    text = path.read_text()
    for number, line in enumerate(text.splitlines(), 1):
        if MATERIAL_ICONS.search(line):
            problems.append(f'{path.relative_to(root)}:{number} {RULES["materialIcons"]}')
        if IMAGE_VECTOR_BUILDER.search(line):
            problems.append(f'{path.relative_to(root)}:{number} {RULES["imageVector"]}')

# Lucide outside Kotlin: drawables, other resources, Gradle files and tools.
others = [p for p in (root / 'app/src').rglob('*') if p.is_file() and p.suffix != '.kt']
others += list(root.glob('*.gradle.kts')) + list(root.glob('app/*.gradle.kts')) + list((root / 'gradle').rglob('*.toml'))
others += [p for p in (root / 'tools').rglob('*') if p.is_file()]
for path in sorted(p for p in others if p.is_file()):
    if LUCIDE.search(path.name):
        problems.append(f'{path.relative_to(root)} {RULES["lucide"]}')
        continue
    try:
        text = path.read_text()
    except UnicodeDecodeError:
        continue
    for number, line in enumerate(text.splitlines(), 1):
        if LUCIDE.search(line):
            problems.append(f'{path.relative_to(root)}:{number} {RULES["lucide"]}')

assert not problems, '\n'.join(problems)
print(f'UI contract checks passed ({len(RULES)} rules)')
