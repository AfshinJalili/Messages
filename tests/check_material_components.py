#!/usr/bin/env python3
"""Message bubbles and the composer are built from stock Material 3 components, not hand-drawn boxes.

- The composer files draw no surface of their own: no `BasicTextField`, `.background(` or `.border(`;
  they use `TextField`, `Surface`, `IconButton` and `FilledIconButton` instead.
- Every bubble is a `MessageBubble` (a Material 3 `Surface`), and only `MessageBubble.kt` reads the
  sent-bubble color role, so a screen cannot paint a bubble by hand.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'app/src/main/kotlin/com/afsh/messages'
composer_files = [
    root / 'ui/thread/ThreadComposer.kt',
    root / 'ui/components/DisabledComposer.kt',
    root / 'ui/components/MessageBubble.kt',
]
bubble_screens = [
    root / 'ui/thread/ThreadScreen.kt',
    root / 'ui/spam/SpamScreen.kt',
    root / 'ui/message/MessagePages.kt',
]
hand_built = re.compile(r'\bBasicTextField\b|\.background\(|\.border\(')
violations = []
for path in composer_files:
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if hand_built.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: hand-built surface; use a Material 3 component')
for path in bubble_screens:
    text = path.read_text()
    if 'MessageBubble(' not in text:
        violations.append(f'{path.relative_to(root)}: draws a bubble without MessageBubble')
for path in root.rglob('*.kt'):
    if path.name in ('MessageBubble.kt', 'AfshTheme.kt'):
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if 'sentBubble' in line:
            violations.append(f'{path.relative_to(root)}:{number}: sent-bubble role outside MessageBubble')
assert not violations, '\n'.join(violations)
print('Material component checks passed')
