#!/usr/bin/env python3
"""The spacing scale is defined in the theme, reproduces today's values, and is documented."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
theme = (root / 'app/src/main/kotlin/com/afsh/messages/ui/AfshTheme.kt').read_text()
design = (root / 'DESIGN.md').read_text()

# Values the app uses today for gaps and padding (each appears 4+ times in ui/).
expected = {'xxs': 2, 'xs': 4, 'sm': 8, 'md': 12, 'lg': 16, 'xl': 20, 'xxl': 24, 'xxxl': 32}

assert re.search(r'data class AfshSpacing\b', theme), 'AfshSpacing is not defined in AfshTheme.kt'
assert 'LocalAfshSpacing' in theme, 'AfshSpacing is not provided through the theme'
assert re.search(r'val spacing: AfshSpacing\s+@Composable get\(\) = LocalAfshSpacing\.current', theme), \
    'Afsh.spacing is not readable like Afsh.colors'
for name, dp in expected.items():
    assert re.search(rf'val {name}: Dp = {dp}\.dp\b', theme), f'spacing step {name} must be {dp}.dp'
assert len(re.findall(r'val \w+: Dp = \d+\.dp', theme)) == len(expected), 'spacing scale has unexpected steps'

assert '### Spacing' in design, 'DESIGN.md has no Spacing section'
section = design.split('### Spacing', 1)[1].split('\n### ', 1)[0]
for name, dp in expected.items():
    assert re.search(rf'`{name}`[^\n]*{dp}dp', section), f'DESIGN.md does not document {name} = {dp}dp'
print('Spacing token checks passed')
