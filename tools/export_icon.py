#!/usr/bin/env python3
"""Export the launcher vector and brand palette to the repository's icon assets.

Requires the system Python packages gi (Rsvg 2.0) and Pillow.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def export():
    import gi
    gi.require_version('Rsvg', '2.0')
    from gi.repository import Rsvg
    from PIL import Image

    colors = {e.attrib['name']: e.text for e in ET.parse(RES / 'values/colors.xml').getroot()}

    def color(value):
        return color(colors[value.removeprefix('@color/')]) if value.startswith('@color/') else value

    vector = ET.parse(RES / 'drawable/ic_launcher_foreground.xml').getroot()
    paths = '\n'.join(
        f'  <path fill="{color(p.attrib[ANDROID + "fillColor"])}" fill-rule="evenodd" d="{p.attrib[ANDROID + "pathData"]}" />'
        for p in vector.findall('path')
    )
    # Action/status icons use the launcher path without its adaptive-icon padding.
    action_icon = ET.fromstring(ET.tostring(vector))
    action_icon.set(ANDROID + 'width', '24dp')
    action_icon.set(ANDROID + 'height', '24dp')
    action_icon.set(ANDROID + 'viewportWidth', '60')
    action_icon.set(ANDROID + 'viewportHeight', '60')
    group = ET.Element('group', {ANDROID + 'translateX': '-24', ANDROID + 'translateY': '-25'})
    for path in list(action_icon):
        action_icon.remove(path)
        group.append(path)
    action_icon.append(group)
    ET.register_namespace('android', ANDROID[1:-1])
    ET.indent(action_icon, space='    ')
    (RES / 'drawable/ic_message_bubble.xml').write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated from ic_launcher_foreground.xml by tools/export_icon.py. -->\n'
        + ET.tostring(action_icon, encoding='unicode') + '\n'
    )
    background = color('@color/launcher_cobalt')
    circle = f'<circle cx="54" cy="54" r="36" fill="{background}" />'
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="18 18 72 72">\n  {circle}\n{paths}\n</svg>\n'
    (ROOT / 'graphics/icon.svg').write_text(svg)
    pixbuf = Rsvg.Handle.new_from_data(svg.encode()).get_pixbuf()
    image = Image.frombytes('RGBA', (pixbuf.get_width(), pixbuf.get_height()), pixbuf.get_pixels(), 'raw', 'RGBA', pixbuf.get_rowstride())
    image.resize((192, 192), Image.Resampling.LANCZOS).save(ROOT / 'graphics/icon.webp', lossless=True)
    square = svg.replace(circle, f'<rect x="18" y="18" width="72" height="72" fill="{background}" />')
    Rsvg.Handle.new_from_data(square.encode()).get_pixbuf().savev(str(ROOT / 'app/src/main/ic_launcher-playstore.png'), 'png', [], [])
    (ROOT / 'fastlane/metadata/android/en-US/images/icon.png').write_bytes((ROOT / 'app/src/main/ic_launcher-playstore.png').read_bytes())


if __name__ == '__main__':
    export()
