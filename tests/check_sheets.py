#!/usr/bin/env python3
"""Every bottom sheet is drawn by ui/components/AfshSheet.kt: reject raw sheets anywhere else in the app."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1] / 'app/src/main/kotlin'
frame = root / 'com/afsh/messages/ui/components/AfshSheet.kt'
assert frame.exists(), 'ui/components/AfshSheet.kt is missing'
source = frame.read_text()
assert re.search(r'^fun AfshSheet\(', source, re.M), 'AfshSheet is not defined'
assert re.search(r'^fun AfshSheetItem\(', source, re.M), 'AfshSheetItem is not defined'
assert 'ModalBottomSheet(' in source, 'AfshSheet must be built on the Material 3 ModalBottomSheet'
assert 'ListItem(' in source, 'AfshSheetItem must be built on the Material 3 ListItem'
assert 'paneTitle' in source, 'AfshSheet must name its pane for TalkBack and tests'

pattern = re.compile(
    r'import androidx\.compose\.material3\.(?:ModalBottomSheet|BottomSheetScaffold|rememberModalBottomSheetState|SheetState)\b'
    r'|import com\.google\.android\.material\.bottomsheet\.'
)
violations = []
for path in root.rglob('*.kt'):
    if path == frame:
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if pattern.search(line):
            violations.append(f'{path.relative_to(root)}:{number}: raw bottom sheet; use AfshSheet')
assert not violations, '\n'.join(violations)
print('Sheet checks passed')
