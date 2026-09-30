#!/usr/bin/env python3
"""Rasterize graphics/icon.svg into the store and README icon assets.

Requires the system Python packages gi (Rsvg 2.0) and Pillow.
"""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]


def export():
    import gi
    gi.require_version('Rsvg', '2.0')
    from gi.repository import Rsvg
    from PIL import Image

    svg = (ROOT / 'graphics/icon.svg').read_text()

    def render(size, path):
        sized = svg.replace('width="512" height="512"', f'width="{size}" height="{size}"', 1)
        Rsvg.Handle.new_from_data(sized.encode()).get_pixbuf().savev(str(path), 'png', [], [])

    render(1024, ROOT / 'graphics/icon-1024.png')
    render(512, ROOT / 'graphics/icon-512.png')
    shutil.copy(ROOT / 'graphics/icon-512.png', ROOT / 'app/src/main/ic_launcher-playstore.png')
    shutil.copy(ROOT / 'graphics/icon-512.png', ROOT / 'fastlane/metadata/android/en-US/images/icon.png')
    with Image.open(ROOT / 'graphics/icon-1024.png') as image:
        image.resize((192, 192), Image.Resampling.LANCZOS).save(ROOT / 'graphics/icon.webp', lossless=True)


if __name__ == '__main__':
    export()
