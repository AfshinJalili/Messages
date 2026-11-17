#!/usr/bin/env python3
"""Screen and tab navigation motion stays in the design-system motion files.

Fails on:
- `<alpha` in any app `res/anim/screen_*.xml` or `res/anim-ldrtl/screen_*.xml` (no crossfade),
- leftover zoom tokens (`motion_zoom_small` / `motion_zoom_large`) anywhere under the app,
- `Crossfade`, navigation `fadeIn` / `fadeOut`, or ad-hoc `slideIntoContainer` /
  `slideOutOfContainer` / `scaleIn` / `scaleOut` used for screen or tab switches outside
  `ui/components/Motion.kt`,
- `overridePendingTransition` / `overrideActivityTransition` outside `helpers/ScreenMotion.kt`,
- missing motion tokens in `res/values/motion.xml` or missing emphasized-decelerate interpolator.

In-component fades (selection bars, sticky dates, search field expand) are allowed; this check
only guards navigation surfaces: activities, `ScreenStack` / `TabSwitch` call sites, and the
shared motion module itself.
"""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
app = root / 'app'
kotlin = app / 'src/main/kotlin/com/afsh/messages'
motion_kt = kotlin / 'ui/components/Motion.kt'
screen_motion_kt = kotlin / 'helpers/ScreenMotion.kt'
motion_xml = app / 'src/main/res/values/motion.xml'
interpolator = app / 'src/main/res/interpolator/emphasized_decelerate.xml'
anim_dirs = [
    app / 'src/main/res/anim',
    app / 'src/main/res/anim-ldrtl',
]

REQUIRED_TOKENS = (
    'motion_open_ms',
    'motion_close_ms',
    'motion_tab_ms',
    'motion_parallax',
    'motion_tab_shift',
)

NAV_FADE = re.compile(r'\b(Crossfade|fadeIn|fadeOut)\b')
NAV_SLIDE = re.compile(r'\b(slideIntoContainer|slideOutOfContainer|scaleIn|scaleOut)\b')
ACTIVITY_OVERRIDE = re.compile(r'\b(overridePendingTransition|overrideActivityTransition)\b')
ZOOM_TOKEN = re.compile(r'motion_zoom_(?:small|large)')
ALPHA_TAG = re.compile(r'<alpha\b')

# Kotlin files that host screen/tab navigation and must not define their own transition specs.
# InboxScreen may still fade the search field expand; only ban Crossfade and container slides there.
NAV_SURFACES = {
    kotlin / 'activities/SettingsActivity.kt',
    kotlin / 'activities/SimpleActivity.kt',
    kotlin / 'activities/MainActivity.kt',
    kotlin / 'activities/ComposePageActivity.kt',
    kotlin / 'helpers/ScreenMotion.kt',
}
INBOX = kotlin / 'ui/inbox/InboxScreen.kt'
CROSSFADE = re.compile(r'\bCrossfade\b')
CONTAINER_SLIDE = re.compile(r'\b(slideIntoContainer|slideOutOfContainer)\b')

violations = []


def rel(path: Path) -> str:
    return str(path.relative_to(root))


if not motion_kt.is_file():
    violations.append(f'missing {rel(motion_kt)}')
if not screen_motion_kt.is_file():
    violations.append(f'missing {rel(screen_motion_kt)}')
if not motion_xml.is_file():
    violations.append(f'missing {rel(motion_xml)}')
else:
    text = motion_xml.read_text()
    for token in REQUIRED_TOKENS:
        if token not in text:
            violations.append(f'{rel(motion_xml)}: missing token {token}')
    if ZOOM_TOKEN.search(text):
        violations.append(f'{rel(motion_xml)}: zoom tokens must be removed; use parallax/tab shift')
if not interpolator.is_file():
    violations.append(f'missing {rel(interpolator)}')

for anim_dir in anim_dirs:
    if not anim_dir.is_dir():
        violations.append(f'missing {rel(anim_dir)}')
        continue
    for path in sorted(anim_dir.glob('screen_*.xml')):
        text = path.read_text()
        if ALPHA_TAG.search(text):
            violations.append(f'{rel(path)}: screen transitions must not use alpha')
        if 'scale' in text and 'translate' not in text:
            violations.append(f'{rel(path)}: screen transitions must slide, not zoom')

for path in kotlin.rglob('*.kt'):
    text = path.read_text()
    if ZOOM_TOKEN.search(text):
        violations.append(f'{rel(path)}: zoom motion token leftover')
    if path == motion_kt:
        if NAV_FADE.search(text):
            violations.append(f'{rel(path)}: shared motion must not fade navigation')
        if 'ScreenStack' not in text or 'TabSwitch' not in text:
            violations.append(f'{rel(path)}: must define ScreenStack and TabSwitch')
        if 'EmphasizedDecelerate' not in text:
            violations.append(f'{rel(path)}: must define EmphasizedDecelerate')
        continue
    if path == INBOX:
        for number, line in enumerate(text.splitlines(), 1):
            if CROSSFADE.search(line) or CONTAINER_SLIDE.search(line) or NAV_SLIDE.search(line):
                # scaleIn/Out and container slides are navigation; search may still fadeIn/Out.
                if CROSSFADE.search(line) or CONTAINER_SLIDE.search(line) or re.search(r'\b(scaleIn|scaleOut)\b', line):
                    violations.append(
                        f'{rel(path)}:{number}: tab/screen transition belongs in ui/components/Motion.kt'
                    )
            if 'ScreenStack(' in line:
                violations.append(
                    f'{rel(path)}:{number}: Inbox/Library peers must use TabSwitch, not ScreenStack'
                )
        continue
    if path in NAV_SURFACES or path.parent.name == 'activities':
        for number, line in enumerate(text.splitlines(), 1):
            if NAV_FADE.search(line) or NAV_SLIDE.search(line):
                violations.append(
                    f'{rel(path)}:{number}: navigation fade/slide belongs in ui/components/Motion.kt'
                )
            if ACTIVITY_OVERRIDE.search(line) and path != screen_motion_kt:
                violations.append(
                    f'{rel(path)}:{number}: activity transitions belong in helpers/ScreenMotion.kt'
                )

if violations:
    print('\n'.join(violations), file=sys.stderr)
    sys.exit(1)
print('Motion checks passed')
