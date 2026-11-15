#!/usr/bin/env python3
"""Screens moved to the spacing tokens and AfshIcon stay there: no raw drawable icon and no literal spacing dp.

Add a screen's files to MIGRATED once its migration lands. Sizes (icons, touch targets, widths) are not spacing and
may stay literal; only padding and arrangement gaps are checked. #241 widens this to the whole app.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
ui = root / 'app/src/main/kotlin/com/afsh/messages/ui'

MIGRATED = [
    'inbox/InboxScreen.kt', 'inbox/InboxState.kt', 'inbox/InboxHeaderScroll.kt',
    'search/SearchScreen.kt', 'search/SearchState.kt',
    'details/ConversationDetailsScreen.kt',
    'library/LibraryScreen.kt', 'library/ArchiveScreen.kt', 'library/RecycleBinScreen.kt',
    'library/StarredScreen.kt', 'library/LibraryState.kt', 'library/LibraryPreviewData.kt',
    'spam/SpamScreen.kt',
    'settings/SettingsScreen.kt', 'settings/SettingsPages.kt', 'settings/BackupPages.kt', 'settings/SettingsState.kt',
    'firstrun/FirstRunScreen.kt',
    'spam/AddRuleScreen.kt', 'spam/BlockRulesScreen.kt', 'spam/BlockedNumbersScreen.kt', 'spam/MoveRulesScreen.kt',
    'spam/SpamPatternsScreen.kt',
    'schedule/ScheduleScreens.kt',
    'media/MediaViewerScreen.kt',
]

SPACING_CALL = re.compile(r'\b(padding|spacedBy|PaddingValues)\(')
LITERAL_DP = re.compile(r'(?<![\w.])\d+(\.\d+)?\.dp\b')


def spacing_args(text):
    """The argument text of every padding, spacedBy and PaddingValues call, nested calls included."""
    for match in SPACING_CALL.finditer(text):
        depth, end = 1, match.end()
        while depth:
            depth += {'(': 1, ')': -1}.get(text[end], 0)
            end += 1
        yield text.count('\n', 0, match.start()) + 1, text[match.end():end - 1]


problems = []
for name in MIGRATED:
    path = ui / name
    text = path.read_text()
    for number, line in enumerate(text.splitlines(), 1):
        if re.search(r'\bR\.drawable\.', line):
            problems.append(f'{name}:{number} names a drawable; request an AfshIcon instead')
    for number, args in spacing_args(text):
        if LITERAL_DP.search(args):
            problems.append(f'{name}:{number} uses a literal spacing dp; use Afsh.spacing')

assert not problems, '\n'.join(problems)
print(f'Migrated screen checks passed ({len(MIGRATED)} files)')
