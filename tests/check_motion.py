#!/usr/bin/env python3
"""Screen and tab navigation motion stays in the design-system motion files.

Fails on:
- `<alpha` in any app `res/anim/screen_*.xml` or `res/anim-ldrtl/screen_*.xml` (no crossfade),
- leftover zoom tokens (`motion_zoom_small` / `motion_zoom_large`) anywhere under the app,
- `Crossfade`, navigation `fadeIn` / `fadeOut`, or ad-hoc `slideIntoContainer` /
  `slideOutOfContainer` / `scaleIn` / `scaleOut` used for screen or tab switches outside
  `ui/components/Motion.kt`,
- `overridePendingTransition` / `overrideActivityTransition` or a `ScreenMotion` helper
  anywhere in the app (window animations belong in the theme),
- missing `AfshWindowAnimation` / `windowAnimationStyle` on the AFSH themes, or missing
  root themes that clear window animation for the task root,
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
styles_xml = app / 'src/main/res/values/styles.xml'
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
SETTINGS_ACTIVITY = kotlin / 'activities/SettingsActivity.kt'

# Kotlin files that host screen/tab navigation and must not define their own transition specs.
NAV_SURFACES = {
    kotlin / 'activities/SimpleActivity.kt',
    kotlin / 'activities/MainActivity.kt',
    kotlin / 'activities/ComposePageActivity.kt',
}
INBOX = kotlin / 'ui/inbox/InboxScreen.kt'
CROSSFADE = re.compile(r'\bCrossfade\b')
CONTAINER_SLIDE = re.compile(r'\b(slideIntoContainer|slideOutOfContainer)\b')

violations = []


def rel(path: Path) -> str:
    return str(path.relative_to(root))


if screen_motion_kt.is_file():
    violations.append(f'{rel(screen_motion_kt)}: remove ScreenMotion; use theme windowAnimationStyle')
if SETTINGS_ACTIVITY.is_file():
    violations.append(f'{rel(SETTINGS_ACTIVITY)}: Settings must be a MainActivity tab, not its own Activity')
if not motion_kt.is_file():
    violations.append(f'missing {rel(motion_kt)}')
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
if not styles_xml.is_file():
    violations.append(f'missing {rel(styles_xml)}')
else:
    styles = styles_xml.read_text()
    for needed in (
        'name="AfshWindowAnimation"',
        'android:activityOpenEnterAnimation',
        'android:activityOpenExitAnimation',
        'android:activityCloseEnterAnimation',
        'android:activityCloseExitAnimation',
        '@anim/screen_open_enter',
        '@anim/screen_open_exit',
        '@anim/screen_close_enter',
        '@anim/screen_close_exit',
        'name="AppTheme.Afsh.Root.Dark"',
        'name="AppTheme.Afsh.Root.Light"',
        'android:windowAnimationStyle">@style/AfshWindowAnimation',
        'android:windowAnimationStyle">@null',
        'android:windowBackground">@color/m3_surface',
        'android:windowBackground">@color/m3_surface_dark',
        'android:windowIsTranslucent">false',
    ):
        if needed not in styles:
            violations.append(f'{rel(styles_xml)}: missing {needed}')

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
        if 'android:fillEnabled="true"' not in text or 'android:fillAfter="true"' not in text:
            violations.append(
                f'{rel(path)}: must set fillEnabled/fillAfter so the exit does not snap back for a frame'
            )
        if path.name.endswith('_enter.xml') and 'android:fillBefore="true"' not in text:
            violations.append(f'{rel(path)}: enter anims must set fillBefore for the first frame')

for path in kotlin.rglob('*.kt'):
    text = path.read_text()
    if ZOOM_TOKEN.search(text):
        violations.append(f'{rel(path)}: zoom motion token leftover')
    if re.search(r'\bScreenMotion\b', text):
        violations.append(f'{rel(path)}: ScreenMotion leftover; use theme windowAnimationStyle')
    if ACTIVITY_OVERRIDE.search(text):
        for number, line in enumerate(text.splitlines(), 1):
            if ACTIVITY_OVERRIDE.search(line):
                violations.append(
                    f'{rel(path)}:{number}: activity transitions belong in theme windowAnimationStyle'
                )
    if path == motion_kt:
        if NAV_FADE.search(text):
            violations.append(f'{rel(path)}: shared motion must not fade navigation')
        if 'ScreenStack' not in text or 'TabSwitch' not in text:
            violations.append(f'{rel(path)}: must define ScreenStack and TabSwitch')
        if 'EmphasizedDecelerate' not in text:
            violations.append(f'{rel(path)}: must define EmphasizedDecelerate')
        if 'clipToBounds' not in text:
            violations.append(f'{rel(path)}: ScreenStack/TabSwitch must clipToBounds to drop off-host exit frames')
        # TabSwitch: opaque cover over a stationary outgoing pane (not a mutual tabShift slide).
        if 'rememberSaveableStateHolder' not in text or 'SaveableStateProvider' not in text:
            violations.append(
                f'{rel(path)}: TabSwitch must keep saveable state per tab (SaveableStateHolder)'
            )
        if 'colorScheme.surface' not in text or 'Surface(Modifier.fillMaxSize(), color = surface)' not in text:
            violations.append(
                f'{rel(path)}: TabSwitch panes must paint an opaque Material Surface so the old tab cannot show through'
            )
        if 'targetOffset = { 0 }' not in text:
            violations.append(
                f'{rel(path)}: TabSwitch exit must stay put (targetOffset 0) under the opaque incoming cover'
            )
        if re.search(
            r'targetOffset\s*=\s*\{\s*fullWidth\s*->\s*\(fullWidth\s*\*\s*tokens\.tabShift\)',
            text,
        ):
            violations.append(
                f'{rel(path)}: TabSwitch must not exit by tabShift; that leaves ~70% of the old pane on screen'
            )
        if 'targetContentZIndex = 1f' not in text:
            violations.append(f'{rel(path)}: TabSwitch incoming pane must sit above the outgoing pane')
        continue
    if path == INBOX:
        for number, line in enumerate(text.splitlines(), 1):
            if CROSSFADE.search(line) or CONTAINER_SLIDE.search(line) or re.search(r'\b(scaleIn|scaleOut)\b', line):
                violations.append(
                    f'{rel(path)}:{number}: tab/screen transition belongs in ui/components/Motion.kt'
                )
            if 'ScreenStack(' in line:
                violations.append(
                    f'{rel(path)}:{number}: top-level destinations must use TabSwitch, not ScreenStack'
                )
        if 'settingsContent' not in text or 'selectedTab' not in text:
            violations.append(f'{rel(path)}: must host all TopDestination tabs via selectedTab/settingsContent')
        continue
    if path in NAV_SURFACES or path.parent.name == 'activities':
        for number, line in enumerate(text.splitlines(), 1):
            if NAV_FADE.search(line) or NAV_SLIDE.search(line):
                violations.append(
                    f'{rel(path)}:{number}: navigation fade/slide belongs in ui/components/Motion.kt'
                )

if violations:
    print('\n'.join(violations), file=sys.stderr)
    sys.exit(1)
print('Motion checks passed')
