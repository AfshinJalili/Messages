#!/usr/bin/env python3
"""Reject new inline color and opacity values outside the design resources."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / 'app/src/main'
violations = []
for path in (root / 'res').rglob('*.xml'):
    if path.name == 'colors.xml' and path.parent.name.startswith('values'):
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?\b|android:alpha="[0-9.]', line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color or opacity')
for path in (root / 'kotlin').rglob('*.kt'):
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'0[xX][0-9a-fA-F]{8}\b|Color\.(?:parseColor|rgb|argb|WHITE|BLACK)|adjustAlpha\([0-9.]|(?:\.alpha|\balpha)\s*=\s*0\.', line):
            violations.append(f'{path.relative_to(root)}:{number}: inline color or opacity')
assert not violations, '\n'.join(violations)
print('Design token checks passed')

# Check text contrast using the shipped roles, including the legacy XML aliases.
colors = {node.attrib['name']: node.text.strip() for node in ET.parse(root / 'res/values/colors.xml').getroot() if node.tag == 'color'}
def luminance(name):
    value = colors[name]
    if value.startswith('@color/'):
        return luminance(value.removeprefix('@color/'))
    channels = [int(value[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    return sum(weight * (c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4)
               for c, weight in zip(channels, (0.2126, 0.7152, 0.0722)))
for foreground, background in [
    ('on_surface_light', 'surface_light'), ('on_surface_dark', 'surface_dark'),
    ('color_primary', 'surface_light'), ('color_primary_dark', 'surface_dark'),
    ('on_action', 'color_primary'), ('design_ink', 'color_primary_dark'),
    ('brand_on_container', 'brand_container'), ('brand_on_container_dark', 'brand_container_dark'),
    ('open_line_on_secondary_variant', 'open_line_lilac'),
    ('open_line_on_secondary_variant_dark', 'open_line_lilac_dark'),
    ('open_line_error', 'open_line_error_container'), ('open_line_coral', 'open_line_error_container_dark'),
    ('open_line_ink', 'open_line_lime'), ('open_line_ink', 'open_line_coral'),
]:
    a, b = sorted((luminance(foreground), luminance(background)))
    ratio = (b + 0.05) / (a + 0.05)
    assert ratio >= 4.5, f'{foreground} on {background}: {ratio:.2f}:1'
print('PASS: light/dark semantic text roles meet 4.5:1 contrast')
