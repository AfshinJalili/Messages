#!/usr/bin/env python3
"""Multi-select action labels that name a countable noun must use <plurals> and labelIsQuantity.

Scans InboxAction and ThreadMenuAction enums. An entry whose label is R.string.<name> where
<name> looks count-sensitive (sender, number, message, conversation, or Persian-facing block/
allow sender ids) must instead use R.plurals and labelIsQuantity = true. Verb-only actions may
keep plain strings.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
sources = [
    root / 'app/src/main/kotlin/com/afsh/messages/ui/inbox/InboxState.kt',
    root / 'app/src/main/kotlin/com/afsh/messages/ui/thread/ThreadState.kt',
]

# Resource name fragments that imply a countable noun in the label.
SENSITIVE = re.compile(
    r'(sender|number|message|conversation|block_number|block_sender|allow_sender|always_spam|never_spam)',
    re.I,
)

# ENTRY(R.string.foo) or ENTRY(R.plurals.foo, ..., labelIsQuantity = true)
ENTRY = re.compile(
    r'(?P<name>[A-Z_]+)\(\s*'
    r'(?:(?:org\.fossify\.commons\.)?R\.(?P<kind>string|plurals)\.(?P<res>\w+))'
    r'(?P<rest>[^)]*)\)',
)

violations = []
for path in sources:
    text = path.read_text()
    # Limit to the enum bodies we care about.
    for enum_name in ('InboxAction', 'ThreadMenuAction'):
        m = re.search(rf'enum class {enum_name}\b[^{{]*\{{(?P<body>.*?)^\}}', text, re.S | re.M)
        if not m:
            violations.append(f'{path.name}: missing enum {enum_name}')
            continue
        body = m.group('body')
        for entry in ENTRY.finditer(body):
            name, kind, res, rest = entry.group('name'), entry.group('kind'), entry.group('res'), entry.group('rest')
            if not SENSITIVE.search(res) and not SENSITIVE.search(name):
                continue
            quantity = 'labelIsQuantity' in rest and 'true' in rest
            if kind == 'string':
                violations.append(
                    f'{path.relative_to(root)}: {enum_name}.{name} uses R.string.{res}; '
                    f'count-sensitive labels must use R.plurals and labelIsQuantity = true'
                )
            elif kind == 'plurals' and not quantity:
                violations.append(
                    f'{path.relative_to(root)}: {enum_name}.{name} uses R.plurals.{res} '
                    f'without labelIsQuantity = true'
                )

assert not violations, '\n'.join(violations)
print('Multi-select plurals checks passed')
