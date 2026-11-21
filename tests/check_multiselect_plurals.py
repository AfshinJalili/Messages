#!/usr/bin/env python3
"""Multi-select actions whose labels name a sender/number must use <plurals> + labelIsQuantity.

Only the count-sensitive spam/block/allow actions are gated: verb-only labels (Mute, Archive,
Delete, …) may stay as plain strings even when the resource id contains a noun.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]

# Actions that offer a countable sender/number label when several rows are selected.
COUNT_SENSITIVE_ACTIONS = {
    'BLOCK',
    'ALLOW_SENDER',
    'ALWAYS_SPAM',
    'NEVER_SPAM',
    'BLOCK_ON_PHONE',
    'ANDROID_BLOCK',
}

ENUMS = {
    root / 'app/src/main/kotlin/com/afsh/messages/ui/inbox/InboxState.kt': 'InboxAction',
    root / 'app/src/main/kotlin/com/afsh/messages/ui/thread/ThreadState.kt': 'ThreadMenuAction',
}

ENTRY = re.compile(
    r'(?P<name>[A-Z_]+)\(\s*'
    r'(?:(?:com\.afsh\.commons\.)?R\.(?P<kind>string|plurals)\.(?P<res>\w+))'
    r'(?P<rest>[^)]*)\)',
)

violations = []
for path, enum_name in ENUMS.items():
    text = path.read_text()
    m = re.search(rf'enum class {enum_name}\b[^{{]*\{{(?P<body>.*?)^\}}', text, re.S | re.M)
    if not m:
        violations.append(f'{path.relative_to(root)}: missing enum {enum_name}')
        continue
    for entry in ENTRY.finditer(m.group('body')):
        name, kind, res, rest = entry.group('name'), entry.group('kind'), entry.group('res'), entry.group('rest')
        if name not in COUNT_SENSITIVE_ACTIONS:
            continue
        quantity = 'labelIsQuantity' in rest and 'true' in rest
        if kind != 'plurals' or not quantity:
            violations.append(
                f'{path.relative_to(root)}: {enum_name}.{name} must use R.plurals '
                f'(got R.{kind}.{res}) with labelIsQuantity = true'
            )

assert not violations, '\n'.join(violations)
print('Multi-select plurals checks passed')
